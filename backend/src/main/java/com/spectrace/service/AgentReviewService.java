package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.common.CollectedDate;
import com.spectrace.common.ParamValueParser;
import com.spectrace.common.PendingStatus;
import com.spectrace.common.TaskStatus;
import com.spectrace.dto.ApproveRequest;
import com.spectrace.dto.DeviceSaveRequest;
import com.spectrace.dto.DeviceVariantDTO;
import com.spectrace.dto.PendingDataVO;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.ParamDef;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 待审核数据的读与审：列表/详情呈现、风险预审打标、审核通过入库与丢弃。
 *
 * <p>与回调落库拆开的原因：这里是「人做决策」的一侧，改动多是排序、提示、批量策略；
 * 回调侧是 Agent 的唯一入口，几乎不动。两者混在一起时，任何审核规则调整都在拿采集链路做赌注。</p>
 *
 * <p>批量通过/丢弃按条独立事务提交：单条失败不能留下半截写入，也不能拖累其余记录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentReviewService {

    private static final double CONFIDENCE_VERIFY_THRESHOLD = 0.6;
    private static final double CONFIDENCE_PASS_THRESHOLD = 0.9;

    /** 风险排序的候选上限：超出的历史记录不再进入内存打标 */
    private static final int RISK_SCAN_LIMIT = 1000;

    private final AgentPendingDataMapper pendingMapper;
    private final BrandMapper brandMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceService deviceService;
    private final ParamService paramService;
    private final OperationLogService logService;
    private final AgentTaskStatusService taskStatusService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DeviceImageFetchService deviceImageFetchService;

    public IPage<AgentPendingData> pagePending(long current, long size, String status, String keyword) {
        var query = Wrappers.<AgentPendingData>lambdaQuery().orderByDesc(AgentPendingData::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(AgentPendingData::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            query.and(q -> q.like(AgentPendingData::getModelName, kw).or().like(AgentPendingData::getBrandName, kw));
        }
        // 待审核队列走「风险优先」排序（T86）：可疑项置顶，审核员的注意力按风险分配而非提交顺序。
        // 风险规则依赖参数内容，无法下推成 SQL；改为有界扫描（最近 N 条候选）后内存排序，
        // 保证积压增长时内存与耗时恒定。超过扫描窗口时打日志，避免「分页看着正常、内容被截断」无声发生。
        if (PendingStatus.PENDING_REVIEW.name().equals(status)) {
            long total = pendingMapper.selectCount(query);
            List<AgentPendingData> rows = pendingMapper.selectList(query.last("LIMIT " + RISK_SCAN_LIMIT));
            if (total > rows.size()) {
                log.warn("待审核风险排序只扫描最近 {} 条（实际 {} 条），更早的记录按创建时间排在后面: keyword={}",
                        RISK_SCAN_LIMIT, total, keyword);
            }
            rows.forEach(row -> row.setRiskLevel(riskLevelOf(row)));
            Comparator<AgentPendingData> byRisk = Comparator
                    .comparingInt((AgentPendingData row) -> riskOrder(row.getRiskLevel()))
                    .thenComparing(AgentPendingData::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            rows.sort(byRisk);
            int from = (int) Math.min((current - 1) * size, rows.size());
            int to = (int) Math.min(from + size, rows.size());
            Page<AgentPendingData> page = new Page<>(current, size, total);
            page.setRecords(rows.subList(from, to));
            return page;
        }
        return pendingMapper.selectPage(new Page<>(current, size), query);
    }

    public AgentPendingData getPending(Long id) {
        AgentPendingData data = pendingMapper.selectById(id);
        if (data == null) {
            throw new BizException("待审核数据不存在");
        }
        return data;
    }

    @SneakyThrows
    public PendingDataVO toVO(AgentPendingData data) {
        PendingDataVO vo = new PendingDataVO();
        vo.setId(data.getId());
        vo.setTaskId(data.getTaskId());
        vo.setModelName(data.getModelName());
        vo.setBrandName(data.getBrandName());
        vo.setSourceUrl(data.getSourceUrl());
        vo.setStatus(data.getStatus());
        vo.setRiskLevel(data.getRiskLevel());
        vo.setCreatedAt(data.getCreatedAt());
        Map<String, Object> params = data.getParamsJson() == null
                ? new LinkedHashMap<>()
                : objectMapper.readValue(data.getParamsJson(), new TypeReference<>() {
        });
        // 保留键在返回前摘掉，避免前端把它们当成普通参数展示
        Object rawVariants = params.remove("_variants");
        Object rawConfidence = params.remove("_confidence");
        Object rawImageUrl = params.remove("_imageUrl");
        if (rawImageUrl != null) {
            vo.setImageUrl(String.valueOf(rawImageUrl));
        }
        vo.setParams(params);
        if (rawVariants != null) {
            vo.setVariants(objectMapper.convertValue(rawVariants, new TypeReference<List<DeviceVariantDTO>>() {
            }));
        }
        vo.setParamConfidence(toConfidenceMap(rawConfidence));
        vo.setParamNames(paramService.keyToNameMap());
        return vo;
    }

    /** 审核详情：在基础 VO 上补充库中现有数据，前端据此标出「旧值 → 新值」。 */
    public PendingDataVO toDetailVO(AgentPendingData data) {
        PendingDataVO vo = toVO(data);
        Device existing = deviceService.findByName(vo.getBrandName(), vo.getModelName());
        vo.setNewDevice(existing == null);
        if (existing != null) {
            vo.setExistingDeviceId(existing.getId());
            vo.setExistingParams(deviceService.paramValuesOf(existing.getId()));
            vo.setExistingVariants(deviceService.variantsOf(existing.getId()));
        }
        vo.setParamWarnings(unitWarnings(vo.getParams()));
        return vo;
    }

    /** 审核通过：把待审核数据迁移到正式机型库。 */
    @Transactional
    @SneakyThrows
    public Long approve(Long pendingId, ApproveRequest req, Long adminId, String adminName) {
        AgentPendingData pending = pendingMapper.selectById(pendingId);
        if (pending == null) {
            throw new BizException("待审核数据不存在");
        }
        if (!PendingStatus.PENDING_REVIEW.name().equals(pending.getStatus())) {
            throw new BizException("该数据已处理，无法重复审核");
        }

        DeviceSaveRequest save = new DeviceSaveRequest();
        save.setBrandId(req.getBrandId());
        save.setName(req.getName() != null ? req.getName() : pending.getModelName());
        save.setReleaseDate(req.getReleaseDate());
        save.setThumbnail(req.getThumbnail());
        save.setDescription(req.getDescription());
        save.setStatus(1);
        save.setParams(req.getParams());
        save.setParamConfidence(confidenceForApprove(pending, req.getParams()));

        // 库中已有同品牌同名机型时按覆盖更新处理，避免重复抓取产生重复机型；
        // 审核表单未回填的图片/简介/日期不回写，防止把已有数据清空
        Device existing = deviceService.findByName(req.getBrandId(), save.getName());
        if (existing != null) {
            save.setId(existing.getId());
            if (!StringUtils.hasText(save.getThumbnail())) {
                save.setThumbnail(existing.getThumbnail());
            }
            if (!StringUtils.hasText(save.getDescription())) {
                save.setDescription(existing.getDescription());
            }
            if (save.getReleaseDate() == null) {
                save.setReleaseDate(existing.getReleaseDate());
            }
            // 审核表单不含系列字段：覆盖更新必须带上原归属，否则 save 的「取消归类需显式置空」逻辑会误清系列
            save.setSeriesId(existing.getSeriesId());
        }

        // 版本数据：优先管理员确认后的，否则回落到采集结果里的 _variants；顺带摘出 _imageUrl（T90）
        List<DeviceVariantDTO> variants = req.getVariants();
        String pendingImageUrl = null;
        if (pending.getParamsJson() != null) {
            Map<String, Object> stored = objectMapper.readValue(pending.getParamsJson(), new TypeReference<>() {
            });
            Object raw = stored.get("_variants");
            if (variants == null && raw != null) {
                variants = objectMapper.convertValue(raw, new TypeReference<List<DeviceVariantDTO>>() {
                });
            }
            Object rawImage = stored.get("_imageUrl");
            if (rawImage != null) {
                pendingImageUrl = String.valueOf(rawImage);
            }
        }
        save.setVariants(variants);

        // 参数级溯源：审核通过时把抓取来源写入每个参数值
        var device = deviceService.save(save, adminId, adminName, pending.getSourceUrl());

        // T90 产品图本地化（硬约束：与主流程解耦）：事务内只写待下载 URL，零网络请求；
        // 下载在事务提交后由独立线程执行。已有真实上传图（非 SVG 占位）不覆盖；
        // 下载失败只降级为保留占位图并写日志，绝不影响已入库的参数与上架状态。
        if (StringUtils.hasText(pendingImageUrl)) {
            String currentThumbnail = device.getThumbnail();
            if (!StringUtils.hasText(currentThumbnail) || currentThumbnail.toLowerCase().endsWith(".svg")) {
                deviceMapper.update(null, Wrappers.<Device>lambdaUpdate()
                        .eq(Device::getId, device.getId())
                        .set(Device::getThumbnailPendingUrl, pendingImageUrl));
                registerImageFetchAfterCommit(device.getId());
            } else {
                log.info("机型已有真实图片，跳过采集图下载: deviceId={}", device.getId());
            }
        }

        pending.setStatus(PendingStatus.APPROVED.name());
        pending.setUpdatedAt(LocalDateTime.now());
        pendingMapper.updateById(pending);

        if (pending.getTaskId() != null) {
            taskStatusService.updateStatus(pending.getTaskId(), TaskStatus.APPROVED, null);
        }
        logService.record(adminId, adminName, "APPROVE_PENDING", "agent_pending_data", pendingId,
                "迁移为机型: " + device.getName() + " (id=" + device.getId() + ")");
        log.info("审核通过: pendingId={} -> deviceId={} 机型 {}（{}）admin={}",
                pendingId, device.getId(), device.getName(), pending.getBrandName(), adminName);
        return device.getId();
    }

    /**
     * 批量通过：直接使用抓取值入库，不经过人工修正。
     *
     * <p>每条走独立事务提交，单条失败不影响其余记录。不能在外层包一个大事务再 catch：
     * 自调用不走事务代理，失败条目已执行的半截写入会随外层事务一起提交，产生「半入库」脏数据。
     */
    public Map<String, Object> batchApprove(List<Long> ids, Long adminId, String adminName) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择待审核数据");
        }
        int success = 0;
        List<String> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                AgentPendingData pending = pendingMapper.selectById(id);
                if (pending == null) {
                    failures.add("#" + id + " 不存在");
                    continue;
                }
                if (!PendingStatus.PENDING_REVIEW.name().equals(pending.getStatus())) {
                    failures.add("#" + id + " 已处理");
                    continue;
                }
                ApproveRequest req = buildApproveRequest(pending);
                transactionTemplate.executeWithoutResult(tx -> approve(id, req, adminId, adminName));
                success++;
            } catch (Exception e) {
                log.error("批量审核失败: pendingId={}", id, e);
                failures.add("#" + id + " " + e.getMessage());
            }
        }
        log.info("批量审核完成: 请求 {} 条，成功 {} 条，失败 {} 条", ids.size(), success, failures.size());
        return batchResult(success, failures);
    }

    /** 批量丢弃，与批量通过一致按独立事务逐条提交。 */
    public Map<String, Object> batchDiscard(List<Long> ids, Long adminId, String adminName) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException("请选择待审核数据");
        }
        int success = 0;
        List<String> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                AgentPendingData pending = pendingMapper.selectById(id);
                if (pending == null) {
                    failures.add("#" + id + " 不存在");
                    continue;
                }
                if (!PendingStatus.PENDING_REVIEW.name().equals(pending.getStatus())) {
                    failures.add("#" + id + " 已处理");
                    continue;
                }
                transactionTemplate.executeWithoutResult(tx -> discard(id, adminId, adminName));
                success++;
            } catch (Exception e) {
                log.error("批量丢弃失败: pendingId={}", id, e);
                failures.add("#" + id + " " + e.getMessage());
            }
        }
        log.info("批量丢弃完成: 请求 {} 条，成功 {} 条，失败 {} 条", ids.size(), success, failures.size());
        return batchResult(success, failures);
    }

    private Map<String, Object> batchResult(int success, List<String> failures) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
    }

    /** 丢弃待审核数据。 */
    @Transactional
    public void discard(Long pendingId, Long adminId, String adminName) {
        AgentPendingData pending = pendingMapper.selectById(pendingId);
        if (pending == null) {
            throw new BizException("待审核数据不存在");
        }
        pending.setStatus(PendingStatus.DISCARDED.name());
        pending.setUpdatedAt(LocalDateTime.now());
        pendingMapper.updateById(pending);
        if (pending.getTaskId() != null) {
            taskStatusService.updateStatus(pending.getTaskId(), TaskStatus.DISCARDED, null);
        }
        logService.record(adminId, adminName, "DISCARD_PENDING", "agent_pending_data", pendingId, pending.getModelName());
    }

    /**
     * 机型列表标注：同品牌同名机型各有几条待审核抓取数据，供管理端「抓取待审」徽标。
     * 待审核记录的品牌为空时按机型名兜底匹配（可能来自不限品牌的任务）。
     */
    public Map<Long, Integer> pendingCountByDevice(List<Device> devices) {
        if (devices == null || devices.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> brandNames = brandMapper.selectBatchIds(
                        devices.stream().map(Device::getBrandId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Brand::getId, Brand::getName));
        List<AgentPendingData> rows = pendingMapper.selectList(Wrappers.<AgentPendingData>lambdaQuery()
                .eq(AgentPendingData::getStatus, PendingStatus.PENDING_REVIEW.name())
                .in(AgentPendingData::getModelName, devices.stream().map(Device::getName).distinct().toList()));
        Map<Long, Integer> result = new java.util.HashMap<>();
        for (AgentPendingData row : rows) {
            String pendingBrand = StringUtils.hasText(row.getBrandName()) ? row.getBrandName().trim() : null;
            for (Device device : devices) {
                if (!device.getName().equals(row.getModelName())) {
                    continue;
                }
                if (pendingBrand != null && !pendingBrand.equals(brandNames.get(device.getBrandId()))) {
                    continue;
                }
                result.merge(device.getId(), 1, Integer::sum);
            }
        }
        return result;
    }

    /** 待审核条数与最老一条的等待小时数（T100 侧栏徽标与监控页共用）。 */
    public long countPendingReview() {
        return pendingMapper.selectCount(Wrappers.<AgentPendingData>lambdaQuery()
                .eq(AgentPendingData::getStatus, PendingStatus.PENDING_REVIEW.name()));
    }

    public long oldestPendingHours() {
        AgentPendingData oldest = pendingMapper.selectOne(Wrappers.<AgentPendingData>lambdaQuery()
                .eq(AgentPendingData::getStatus, PendingStatus.PENDING_REVIEW.name())
                .orderByAsc(AgentPendingData::getCreatedAt)
                .last("LIMIT 1"));
        if (oldest == null || oldest.getCreatedAt() == null) {
            return 0;
        }
        return java.time.Duration.between(oldest.getCreatedAt(), LocalDateTime.now()).toHours();
    }

    /** 用采集结果拼装入库请求，供批量通过使用。 */
    @SneakyThrows
    private ApproveRequest buildApproveRequest(AgentPendingData pending) {
        if (!StringUtils.hasText(pending.getBrandName())) {
            throw new BizException("缺少品牌，无法自动入库");
        }
        Brand brand = brandMapper.selectOne(Wrappers.<Brand>lambdaQuery()
                .eq(Brand::getName, pending.getBrandName().trim())
                .last("LIMIT 1"));
        if (brand == null) {
            throw new BizException("品牌不存在：" + pending.getBrandName());
        }

        Map<String, Object> stored = pending.getParamsJson() == null
                ? new LinkedHashMap<>()
                : objectMapper.readValue(pending.getParamsJson(), new TypeReference<>() {
        });

        Map<String, String> params = new LinkedHashMap<>();
        stored.forEach((key, value) -> {
            // 下划线开头的是保留键（_variants / _confidence），不是参数
            if (!key.startsWith("_") && value != null) {
                params.put(key, String.valueOf(value));
            }
        });

        List<DeviceVariantDTO> variants = null;
        Object rawVariants = stored.get("_variants");
        if (rawVariants != null) {
            variants = objectMapper.convertValue(rawVariants, new TypeReference<List<DeviceVariantDTO>>() {
            });
        }

        ApproveRequest req = new ApproveRequest();
        req.setBrandId(brand.getId());
        req.setName(pending.getModelName());
        req.setParams(params);
        req.setVariants(variants);
        LocalDate releaseDate = CollectedDate.parse(params.get("release_date"));
        if (releaseDate != null) {
            req.setReleaseDate(releaseDate);
        }
        return req;
    }

    /**
     * 审核通过时的置信度：沿用采集结果，但管理员在审核页改动过的参数视为人工确认，去掉其置信度。
     */
    @SneakyThrows
    private Map<String, Double> confidenceForApprove(AgentPendingData pending, Map<String, String> submitted) {
        Map<String, Double> confidence = new LinkedHashMap<>();
        if (pending.getParamsJson() == null) {
            return confidence;
        }
        Map<String, Object> stored = objectMapper.readValue(pending.getParamsJson(), new TypeReference<>() {
        });
        confidence.putAll(toConfidenceMap(stored.get("_confidence")));
        if (confidence.isEmpty() || submitted == null) {
            return confidence;
        }
        confidence.keySet().removeIf(key -> {
            Object crawled = stored.get(key);
            String crawledValue = crawled == null ? null : String.valueOf(crawled);
            return !java.util.Objects.equals(crawledValue, submitted.get(key));
        });
        return confidence;
    }

    /** 图片下载必须在事务提交后启动：批量路径在 transactionTemplate 内、单条路径在代理事务内，都能收到 afterCommit。 */
    private void registerImageFetchAfterCommit(Long deviceId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deviceImageFetchService.enqueue(deviceId);
                }
            });
        } else {
            deviceImageFetchService.enqueue(deviceId);
        }
    }

    private int riskOrder(String riskLevel) {
        return switch (riskLevel == null ? "NORMAL" : riskLevel) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            case "PASS" -> 3;
            default -> 2;
        };
    }

    /**
     * AI 预审打标（T86）：不发外部请求，只依据抓取结果自身数据评估——
     * 低置信度（&lt;0.6）或单位冲突判 HIGH；与库中现有值有差异判 MEDIUM；
     * 全部参数置信度 ≥0.9 且无任何冲突判 PASS（可信项，可放心批量处理）；其余 NORMAL。
     */
    private String riskLevelOf(AgentPendingData data) {
        Map<String, Object> params;
        Map<String, Double> confidence;
        try {
            Map<String, Object> stored = data.getParamsJson() == null
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(data.getParamsJson(), new TypeReference<>() {
                    });
            params = new LinkedHashMap<>(stored);
            confidence = toConfidenceMap(params.remove("_confidence"));
        } catch (Exception e) {
            log.warn("预审打标解析失败，按 NORMAL 处理: pendingId={}", data.getId(), e);
            return "NORMAL";
        }
        params.keySet().removeIf(key -> key.startsWith("_"));
        boolean lowConfidence = confidence.values().stream().anyMatch(c -> c < CONFIDENCE_VERIFY_THRESHOLD);
        boolean unitConflict = !unitWarnings(params).isEmpty();

        boolean diffConflict = false;
        Device existing = deviceService.findByName(data.getBrandName(), data.getModelName());
        if (existing != null && !params.isEmpty()) {
            Map<String, String> existingParams = deviceService.paramValuesOf(existing.getId());
            diffConflict = params.entrySet().stream().anyMatch(entry -> {
                String oldValue = existingParams.get(entry.getKey());
                return oldValue != null && entry.getValue() != null && !String.valueOf(entry.getValue()).equals(oldValue);
            });
        }

        if (lowConfidence || unitConflict) {
            return "HIGH";
        }
        if (diffConflict) {
            return "MEDIUM";
        }
        if (!confidence.isEmpty() && confidence.values().stream().allMatch(c -> c >= CONFIDENCE_PASS_THRESHOLD)) {
            return "PASS";
        }
        return "NORMAL";
    }

    /** 审核页提示单位冲突，避免把「6.1寸」这类与参数定义不一致的值直接入库。 */
    private Map<String, String> unitWarnings(Map<String, Object> params) {
        Map<String, String> warnings = new LinkedHashMap<>();
        if (params == null || params.isEmpty()) {
            return warnings;
        }
        Map<String, ParamDef> defs = paramService.keyToDefMap();
        params.forEach((key, value) -> {
            ParamDef def = defs.get(key);
            if (def == null || value == null || value.toString().isBlank()) {
                return;
            }
            var parsed = ParamValueParser.parse(value.toString(), def.getUnit());
            if (parsed.conflict()) {
                warnings.put(key, "单位与参数定义不一致：值含「" + parsed.unit() + "」，定义为「" + def.getUnit() + "」");
            }
        });
        return warnings;
    }

    /** 把存储的置信度对象统一转成 paramKey -> 0~1 的 double 映射。 */
    private Map<String, Double> toConfidenceMap(Object raw) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        Map<String, Object> source = objectMapper.convertValue(raw, new TypeReference<>() {
        });
        source.forEach((key, value) -> {
            Double parsed = parseConfidenceValue(value);
            if (parsed != null) {
                result.put(key, parsed);
            }
        });
        return result;
    }

    private Double parseConfidenceValue(Object value) {
        if (value == null) {
            return null;
        }
        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else {
            try {
                parsed = Double.parseDouble(String.valueOf(value).replace("%", "").trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
            return null;
        }
        if (parsed > 1 && parsed <= 100) {
            parsed = parsed / 100;
        }
        return Math.max(0, Math.min(1, parsed));
    }
}
