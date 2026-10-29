package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spectrace.common.BizException;
import com.spectrace.config.CacheConfig;
import com.spectrace.dto.FeedbackRequest;
import com.spectrace.entity.Device;
import com.spectrace.entity.Feedback;
import com.spectrace.entity.ParamDef;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参数纠错反馈：访客提交 + 管理端处理。
 *
 * <p>限流采用单机内存计数（同一来源 + 同一机型 1 分钟一次）。
 * 多实例部署时需换成 Redis 计数，当前项目默认单实例，故不引入额外依赖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final long MIN_INTERVAL_MS = 60_000L;
    private static final long TRACK_TTL_MS = 10 * 60_000L;
    private static final int MAX_TRACKED_KEYS = 2_000;
    private static final int MAX_TEXT = 1_000;
    private static final Set<String> ALLOWED_STATUS = Set.of("OPEN", "RESOLVED", "IGNORED");
    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;

    private final FeedbackMapper feedbackMapper;
    private final DeviceMapper deviceMapper;
    private final ParamService paramService;
    private final OperationLogService logService;
    private final SecureRandom codeRandom = new SecureRandom();

    /** key = 来源标识 + 机型 id，value = 上次提交时间戳 */
    private final Map<String, Long> lastSubmitAt = new ConcurrentHashMap<>();

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DEVICE_DETAIL, key = "#req.deviceId")
    public String submit(FeedbackRequest req, String clientKey) {
        String suggested = trimTo(req.getSuggestedValue(), 512);
        String description = trimTo(req.getDescription(), MAX_TEXT);
        if (!StringUtils.hasText(suggested) && !StringUtils.hasText(description)) {
            throw new BizException("请填写建议值或问题说明");
        }

        Device device = deviceMapper.selectById(req.getDeviceId());
        if (device == null) {
            throw new BizException("机型不存在，无法提交反馈");
        }

        String limitKey = clientKey + ":" + req.getDeviceId();
        long now = System.currentTimeMillis();
        Long last = lastSubmitAt.get(limitKey);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            log.warn("纠错反馈被限流: key={} 距上次 {}ms", limitKey, now - last);
            throw new BizException(429, "提交过于频繁，请 1 分钟后再试");
        }
        lastSubmitAt.put(limitKey, now);
        cleanupIfNeeded(now);

        ParamDef def = resolveParamDef(req);
        Feedback feedback = new Feedback();
        feedback.setDeviceId(device.getId());
        feedback.setDeviceName(device.getName());
        if (def != null) {
            feedback.setParamDefId(def.getId());
            feedback.setParamKey(def.getParamKey());
            feedback.setParamName(def.getParamName());
        } else {
            feedback.setParamDefId(req.getParamDefId());
            feedback.setParamKey(trimTo(req.getParamKey(), 64));
        }
        feedback.setCurrentValue(trimTo(req.getCurrentValue(), MAX_TEXT));
        feedback.setSuggestedValue(suggested);
        feedback.setDescription(description);
        feedback.setContact(trimTo(req.getContact(), 128));
        feedback.setStatus("OPEN");
        feedback.setPublicCode(nextCode());
        feedbackMapper.insert(feedback);

        log.info("收到参数纠错反馈: id={} code={} deviceId={} device={} param={} key={}",
                feedback.getId(), feedback.getPublicCode(), device.getId(), device.getName(),
                feedback.getParamName(), feedback.getParamKey());
        return feedback.getPublicCode();
    }

    public IPage<Feedback> page(long current, long size, String status) {
        var query = Wrappers.<Feedback>lambdaQuery().orderByDesc(Feedback::getCreatedAt);
        if (StringUtils.hasText(status)) {
            query.eq(Feedback::getStatus, status);
        }
        return feedbackMapper.selectPage(new Page<>(current, size), query);
    }

    public long countOpen() {
        return feedbackMapper.selectCount(Wrappers.<Feedback>lambdaQuery().eq(Feedback::getStatus, "OPEN"));
    }

    /**
     * 访客凭公开码查询进度用；逻辑删除的记录视为不存在。
     * 兼容历史回执：升级前发出的纯数字编号仍可按 id 查到（只回状态与时间，枚举面不变大）。
     */
    public Feedback requireByCode(String code) {
        String normalized = code == null ? "" : code.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BizException("反馈不存在，请核对编号");
        }
        Feedback feedback = feedbackMapper.selectOne(
                Wrappers.<Feedback>lambdaQuery().eq(Feedback::getPublicCode, normalized).last("LIMIT 1"));
        if (feedback == null && normalized.matches("\\d{1,10}")) {
            feedback = feedbackMapper.selectById(Long.valueOf(normalized));
        }
        if (feedback == null) {
            throw new BizException("反馈不存在，请核对编号");
        }
        return feedback;
    }

    /** 8 位 base62 随机码；唯一索引兜底撞码，重试即可（62^8 空间撞码概率可忽略）。 */
    private String nextCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_ALPHABET.charAt(codeRandom.nextInt(CODE_ALPHABET.length())));
            }
            String code = builder.toString();
            if (feedbackMapper.selectCount(
                    Wrappers.<Feedback>lambdaQuery().eq(Feedback::getPublicCode, code)) == 0) {
                return code;
            }
            log.warn("反馈公开码撞码重试: attempt={}", attempt + 1);
        }
        throw new BizException("反馈编号生成失败，请重试");
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.DEVICE_DETAIL, allEntries = true)
    public void updateStatus(Long id, String status, String note, Long adminId, String adminName) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new BizException("不支持的状态: " + status);
        }
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BizException("反馈不存在");
        }
        feedback.setStatus(status);
        feedback.setHandlerNote(trimTo(note, MAX_TEXT));
        feedbackMapper.updateById(feedback);
        logService.record(adminId, adminName, "HANDLE_FEEDBACK", "feedback", id,
                feedback.getDeviceName() + " -> " + status);
        log.info("纠错反馈已处理: id={} status={} admin={}", id, status, adminName);
    }

    private ParamDef resolveParamDef(FeedbackRequest req) {
        Map<String, ParamDef> byKey = paramService.keyToDefMap();
        if (StringUtils.hasText(req.getParamKey())) {
            ParamDef def = byKey.get(req.getParamKey());
            if (def != null) {
                return def;
            }
        }
        if (req.getParamDefId() == null) {
            return null;
        }
        return byKey.values().stream()
                .filter(def -> req.getParamDefId().equals(def.getId()))
                .findFirst()
                .orElse(null);
    }

    /** 限流表按需清理，避免长期运行无限增长。 */
    private void cleanupIfNeeded(long now) {
        if (lastSubmitAt.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        lastSubmitAt.entrySet().removeIf(entry -> now - entry.getValue() > TRACK_TTL_MS);
    }

    private String trimTo(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
