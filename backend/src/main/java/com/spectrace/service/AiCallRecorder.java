package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.AiCallFailure;
import com.spectrace.common.AiCallScene;
import com.spectrace.entity.AiCallLog;
import com.spectrace.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 模型调用统计的落库入口。
 *
 * <p>统计属于旁路能力：写库失败只记日志，绝不能让一条统计记录把模型调用本身搞挂。
 * 失败调用的原因与耗时都保留，面板里的分位值因此能反映「超时到底有多慢」。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallRecorder {

    private static final int MAX_ERROR_MSG = 500;

    private final AiCallLogMapper callLogMapper;

    /**
     * 记录一次对话 / 多模态调用。
     *
     * @param errorType 为空表示成功；非空取值为 {@link AiCallFailure}
     * @param startedAt 调用开始时间，趋势按它分桶而不是按落库时间
     */
    public void recordChat(AiCallTarget target, AiCallScene scene, String bizId, LocalDateTime startedAt,
                           long latencyMs, Integer statusCode, String errorType, String errorMsg,
                           Integer promptTokens, Integer completionTokens) {
        record(target, scene, bizId, startedAt, latencyMs, statusCode, errorType, errorMsg,
                promptTokens, completionTokens);
    }

    public void recordImage(AiCallTarget target, String bizId, LocalDateTime startedAt, long latencyMs,
                            Integer statusCode, String errorType, String errorMsg) {
        record(target, AiCallScene.IMAGE, bizId, startedAt, latencyMs, statusCode, errorType, errorMsg,
                null, null);
    }

    /**
     * 业务层判定失败后回写最近一次调用。
     *
     * <p>抓取 Agent 的模型最终输出无法解析成 JSON 时，传输层其实是成功的；
     * 把它标记为失败而不是新增一条调用，才能保证成功率的分母仍是真实调用次数。</p>
     */
    public void markBusinessFailure(AiCallScene scene, String bizId, String errorType, String errorMsg) {
        if (scene == null || bizId == null) {
            return;
        }
        try {
            AiCallLog last = callLogMapper.selectOne(Wrappers.<AiCallLog>lambdaQuery()
                    .eq(AiCallLog::getScene, scene.name())
                    .eq(AiCallLog::getBizId, bizId)
                    .orderByDesc(AiCallLog::getId)
                    .last("LIMIT 1"));
            // 传输层已经失败时以更具体的原因优先，不覆盖
            if (last == null || !Integer.valueOf(1).equals(last.getSuccess())) {
                return;
            }
            AiCallLog update = new AiCallLog();
            update.setId(last.getId());
            update.setSuccess(0);
            update.setErrorType(errorType);
            update.setErrorMsg(truncate(errorMsg));
            callLogMapper.updateById(update);
            log.warn("模型调用判定为业务失败: scene={} bizId={} model={} reason={} msg={}",
                    scene, bizId, last.getModelName(), errorType, truncate(errorMsg));
        } catch (Exception e) {
            log.warn("模型调用业务失败回写失败: scene={} bizId={}", scene, bizId, e);
        }
    }

    private void record(AiCallTarget target, AiCallScene scene, String bizId, LocalDateTime startedAt,
                        long latencyMs, Integer statusCode, String errorType, String errorMsg,
                        Integer promptTokens, Integer completionTokens) {
        if (target == null || scene == null) {
            return;
        }
        AiCallLog row = new AiCallLog();
        row.setScene(scene.name());
        row.setProviderKind(target.providerKind());
        row.setProviderId(target.providerId());
        row.setProviderName(target.providerName());
        row.setModelId(target.modelId());
        // model_name 非空：绘图供应商漏配模型时也不能把整条统计丢掉
        row.setModelName(target.modelName() == null || target.modelName().isBlank()
                ? "未配置模型" : target.modelName());
        row.setSuccess(errorType == null ? 1 : 0);
        row.setStatusCode(statusCode);
        row.setLatencyMs(Math.max(0, latencyMs));
        row.setErrorType(errorType);
        row.setErrorMsg(truncate(errorMsg));
        row.setPromptTokens(promptTokens);
        row.setCompletionTokens(completionTokens);
        row.setBizId(bizId);
        row.setCreatedAt(startedAt == null ? LocalDateTime.now() : startedAt);

        try {
            callLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("模型调用统计写入失败: scene={} provider={} model={} success={} latency={}ms",
                    scene, target.providerName(), target.modelName(), row.getSuccess(), latencyMs, e);
            return;
        }

        if (errorType != null) {
            log.warn("模型调用失败: scene={} provider={} model={} status={} reason={} latency={}ms msg={}",
                    scene, target.providerName(), target.modelName(), statusCode, errorType, latencyMs,
                    truncate(errorMsg));
        } else {
            log.debug("模型调用完成: scene={} provider={} model={} latency={}ms in={} out={}",
                    scene, target.providerName(), target.modelName(), latencyMs, promptTokens, completionTokens);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_ERROR_MSG ? text : text.substring(0, MAX_ERROR_MSG) + "…";
    }
}
