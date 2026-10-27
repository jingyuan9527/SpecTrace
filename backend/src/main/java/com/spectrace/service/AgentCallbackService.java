package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.common.PendingStatus;
import com.spectrace.common.TaskStatus;
import com.spectrace.common.TaskType;
import com.spectrace.dto.AgentCallbackRequest;
import com.spectrace.dto.DeviceVariantDTO;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.AgentTask;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.AgentTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集结果回流：把 Agent 回调的 JSON 落成「待审核记录」或「系列发现清单」。
 *
 * <p>边界只到「写进库、状态置为待审核」为止——入库、去重覆盖、审核通过都是下游职责。
 * 与审核拆开的原因：回调是 Agent 进程唯一入口，稳定性要求最高，改动频率却最低；
 * 混在审核链路里会让每次调审核规则都面临「改坏回调」的风险。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCallbackService {

    private final AgentTaskMapper taskMapper;
    private final AgentPendingDataMapper pendingMapper;
    private final AgentTaskLogService taskLogService;
    private final AgentTaskStatusService taskStatusService;
    private final ObjectMapper objectMapper;

    /** AgentScope 抓取结果回调。 */
    @Transactional
    @SneakyThrows
    public void handleCallback(AgentCallbackRequest req) {
        AgentTask task = taskMapper.selectById(req.getTaskId());
        if (task == null) {
            throw new BizException("任务不存在: " + req.getTaskId());
        }

        boolean failed = "FAILED".equalsIgnoreCase(req.getStatus());
        if (failed) {
            taskLogService.error(task.getId(), "抓取失败：" + (req.getError() == null ? "未知错误" : req.getError()));
            task.setStatus(TaskStatus.FAILED.name());
            task.setErrorMsg(req.getError());
            task.setDurationMs(req.getDurationMs());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return;
        }

        if (TaskType.parse(task.getTaskType()) == TaskType.SERIES_DISCOVERY) {
            handleSeriesResult(task, req);
            return;
        }

        Map<String, Object> params = req.getParams() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(req.getParams());

        // 存储版本统一收口到 params 的 _variants 键，兼容 Agent 直接放在 params.variants 的情况
        List<DeviceVariantDTO> variants = req.getVariants();
        Object rawVariants = params.remove("variants");
        if (rawVariants == null) {
            rawVariants = params.remove("_variants");
            if (variants == null && rawVariants != null) {
                variants = objectMapper.convertValue(rawVariants, new TypeReference<List<DeviceVariantDTO>>() {
                });
            }
        }
        if (variants != null && !variants.isEmpty()) {
            params.put("_variants", variants);
        }
        // 参数级置信度与版本一样收口到 params 的保留键，避免改动表结构
        if (req.getParamConfidence() != null && !req.getParamConfidence().isEmpty()) {
            params.put("_confidence", req.getParamConfidence());
        }
        // 产品图 URL 与版本/置信度一样收口保留键，审核通过后异步下载（T90）
        if (StringUtils.hasText(req.getImageUrl())) {
            params.put("_imageUrl", req.getImageUrl());
        }

        appendSummaryLogs(task.getId(), req.getLogs());
        AgentPendingData pending = savePending(task, req, params);
        taskLogService.info(task.getId(), "结果已写入待审核 #" + pending.getId());
        log.info("抓取结果写入待审核: taskId={} pendingId={} 机型 {}（{}），参数 {} 项",
                task.getId(), pending.getId(), req.getModelName(), req.getBrandName(), params.size());

        task.setStatus(TaskStatus.PENDING_REVIEW.name());
        task.setSourceUrl(req.getSourceUrl());
        task.setResult(pending.getParamsJson());
        task.setDurationMs(req.getDurationMs());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 系列发现结果落库：清单写进 task.result 供任务详情勾选，不进待审核库（那是参数集语义）。
     */
    @SneakyThrows
    private void handleSeriesResult(AgentTask task, AgentCallbackRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("brandName", StringUtils.hasText(req.getBrandName()) ? req.getBrandName() : task.getBrandName());
        result.put("seriesName", req.getModelName() != null ? req.getModelName() : task.getModelName());
        result.put("sourceUrl", req.getSourceUrl());
        result.put("models", req.getSeriesModels() == null ? List.of() : req.getSeriesModels());

        appendSummaryLogs(task.getId(), req.getLogs());
        int found = req.getSeriesModels() == null ? 0 : req.getSeriesModels().size();
        taskLogService.info(task.getId(), "系列发现完成：共识别 " + found + " 个机型");

        task.setStatus(TaskStatus.PENDING_REVIEW.name());
        task.setSourceUrl(req.getSourceUrl());
        task.setResult(objectMapper.writeValueAsString(result));
        task.setDurationMs(req.getDurationMs());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("系列发现结果落库: taskId={} 品牌 {} 系列 {}，机型 {} 个，耗时 {}ms",
                task.getId(), result.get("brandName"), result.get("seriesName"), found, req.getDurationMs());
    }

    /**
     * 写入待审核数据。
     *
     * <p>同一任务重复回调时复用原行，保证回调幂等；同品牌同机型已有待审核记录时用最新抓取覆盖，
     * 否则「同一机型抓两次」就会在待审核队列里堆成两条看起来一样的数据。
     */
    private AgentPendingData savePending(AgentTask task, AgentCallbackRequest req, Map<String, Object> params)
            throws Exception {
        String modelName = req.getModelName() != null ? req.getModelName() : task.getModelName();
        String brandName = req.getBrandName() != null ? req.getBrandName() : task.getBrandName();

        AgentPendingData reused = findReusablePending(task.getId(), modelName, brandName);
        if (reused != null) {
            Long previousTaskId = reused.getTaskId();
            if (previousTaskId != null && !previousTaskId.equals(task.getId())) {
                // 旧任务的结果已被本次抓取覆盖，状态同步置为丢弃，避免任务列表永远停在「待审核」
                taskStatusService.updateStatus(previousTaskId, TaskStatus.DISCARDED, null);
                log.info("待审核记录被同机型新抓取覆盖: pendingId={} oldTaskId={} newTaskId={}",
                        reused.getId(), previousTaskId, task.getId());
                taskLogService.info(task.getId(), "同机型已有待审核记录 #" + reused.getId()
                        + "，本次结果覆盖该记录，旧任务 #" + previousTaskId + " 已置为丢弃");
            }
            reused.setTaskId(task.getId());
            reused.setModelName(modelName);
            reused.setBrandName(brandName);
            reused.setSourceUrl(req.getSourceUrl());
            reused.setParamsJson(objectMapper.writeValueAsString(params));
            // 记录代表「最近一次抓取结果」，时间跟着刷新，列表按新鲜度排序才不会把刚抓到的沉到底部
            reused.setCreatedAt(LocalDateTime.now());
            reused.setUpdatedAt(LocalDateTime.now());
            pendingMapper.updateById(reused);
            return reused;
        }

        AgentPendingData pending = new AgentPendingData();
        pending.setTaskId(task.getId());
        pending.setModelName(modelName);
        pending.setBrandName(brandName);
        pending.setSourceUrl(req.getSourceUrl());
        pending.setParamsJson(objectMapper.writeValueAsString(params));
        pending.setStatus(PendingStatus.PENDING_REVIEW.name());
        pendingMapper.insert(pending);
        return pending;
    }

    private AgentPendingData findReusablePending(Long taskId, String modelName, String brandName) {
        AgentPendingData byTask = pendingMapper.selectOne(Wrappers.<AgentPendingData>lambdaQuery()
                .eq(AgentPendingData::getTaskId, taskId)
                .eq(AgentPendingData::getStatus, PendingStatus.PENDING_REVIEW.name())
                .last("LIMIT 1"));
        if (byTask != null) {
            return byTask;
        }
        var query = Wrappers.<AgentPendingData>lambdaQuery()
                .eq(AgentPendingData::getStatus, PendingStatus.PENDING_REVIEW.name())
                .eq(AgentPendingData::getModelName, modelName == null ? "" : modelName.trim());
        if (StringUtils.hasText(brandName)) {
            query.eq(AgentPendingData::getBrandName, brandName.trim());
        } else {
            query.isNull(AgentPendingData::getBrandName);
        }
        return pendingMapper.selectOne(query.last("LIMIT 1"));
    }

    /**
     * 把采集端回传的日志摘要拆成可读行。
     *
     * <p>摘要是一次性 JSON（搜索词 / 置信度 / 备注 / 被丢弃的未知参数），直接甩给前端不好读；
     * 格式无法识别时原样保留，避免丢信息。
     */
    private void appendSummaryLogs(Long taskId, String logs) {
        if (!StringUtils.hasText(logs)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(logs);
            if (node == null || !node.isObject()) {
                taskLogService.info(taskId, "采集摘要：" + logs);
                return;
            }
            String queries = joinTextArray(node.path("queries"));
            if (StringUtils.hasText(queries)) {
                taskLogService.info(taskId, "搜索词：" + queries);
            }
            String confidence = node.path("confidence").asText("");
            if (StringUtils.hasText(confidence)) {
                taskLogService.info(taskId, "整体置信度：" + confidence);
            }
            String notes = node.path("notes").asText("");
            if (StringUtils.hasText(notes)) {
                taskLogService.info(taskId, "采集备注：" + notes);
            }
            String dropped = joinTextArray(node.path("droppedUnknownKeys"));
            if (StringUtils.hasText(dropped)) {
                taskLogService.warn(taskId, "以下参数不在参数定义中，已丢弃：" + dropped);
            }
            String failedSources = joinTextArray(node.path("failedSources"));
            if (StringUtils.hasText(failedSources)) {
                taskLogService.warn(taskId, "以下来源域名拒绝抓取（401/403），结果未采用这些站点：" + failedSources);
            }
        } catch (Exception e) {
            taskLogService.info(taskId, "采集摘要：" + logs);
        }
    }

    private String joinTextArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (!item.isNull()) {
                values.add(item.asText());
            }
        });
        return String.join("、", values);
    }
}
