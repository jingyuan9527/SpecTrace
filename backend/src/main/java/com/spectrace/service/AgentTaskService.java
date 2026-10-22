package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.common.PendingStatus;
import com.spectrace.common.TaskStatus;
import com.spectrace.dto.*;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.AgentTask;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.AgentTaskMapper;
import com.spectrace.task.TaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskMapper taskMapper;
    private final AgentPendingDataMapper pendingMapper;
    private final TaskQueue taskQueue;
    private final DeviceService deviceService;
    private final ParamService paramService;
    private final OperationLogService logService;
    private final ObjectMapper objectMapper;

    /** 管理员提交抓取任务。 */
    public AgentTask createTask(TaskCreateRequest req, Long adminId, String adminName) {
        AgentTask task = new AgentTask();
        task.setModelName(req.getModelName());
        task.setBrandName(req.getBrandName());
        task.setAiModelId(req.getAiModelId());
        task.setStatus(TaskStatus.QUEUED.name());
        taskMapper.insert(task);

        AgentTaskPayload payload = new AgentTaskPayload();
        payload.setTaskId(task.getId());
        payload.setModelName(task.getModelName());
        payload.setBrandName(task.getBrandName());
        payload.setAiModelId(task.getAiModelId());
        taskQueue.push(payload);

        logService.record(adminId, adminName, "CREATE_AGENT_TASK", "agent_task", task.getId(), task.getModelName());
        return task;
    }

    public IPage<AgentTask> pageTasks(long current, long size, String status) {
        var query = Wrappers.<AgentTask>lambdaQuery().orderByDesc(AgentTask::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(AgentTask::getStatus, status);
        }
        return taskMapper.selectPage(new Page<>(current, size), query);
    }

    /** 标记任务为抓取中（内嵌 Agent 开始执行时调用，便于后台展示进度）。 */
    public void markRunning(Long taskId) {
        updateStatus(taskId, TaskStatus.RUNNING, null);
    }

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
            task.setStatus(TaskStatus.FAILED.name());
            task.setErrorMsg(req.getError());
            task.setDurationMs(req.getDurationMs());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
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

        AgentPendingData pending = new AgentPendingData();
        pending.setTaskId(task.getId());
        pending.setModelName(req.getModelName() != null ? req.getModelName() : task.getModelName());
        pending.setBrandName(req.getBrandName() != null ? req.getBrandName() : task.getBrandName());
        pending.setSourceUrl(req.getSourceUrl());
        pending.setParamsJson(objectMapper.writeValueAsString(params));
        pending.setStatus(PendingStatus.PENDING_REVIEW.name());
        pendingMapper.insert(pending);

        task.setStatus(TaskStatus.PENDING_REVIEW.name());
        task.setSourceUrl(req.getSourceUrl());
        task.setResult(pending.getParamsJson());
        task.setDurationMs(req.getDurationMs());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public IPage<AgentPendingData> pagePending(long current, long size, String status) {
        var query = Wrappers.<AgentPendingData>lambdaQuery().orderByDesc(AgentPendingData::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(AgentPendingData::getStatus, status);
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
        vo.setCreatedAt(data.getCreatedAt());
        Map<String, Object> params = data.getParamsJson() == null
                ? new LinkedHashMap<>()
                : objectMapper.readValue(data.getParamsJson(), new TypeReference<>() {
        });
        vo.setParams(params);
        Object rawVariants = params.remove("_variants");
        if (rawVariants != null) {
            vo.setVariants(objectMapper.convertValue(rawVariants, new TypeReference<List<DeviceVariantDTO>>() {
            }));
        }
        vo.setParamNames(paramService.keyToNameMap());
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

        // 版本数据：优先管理员确认后的，否则回落到采集结果里的 _variants
        List<DeviceVariantDTO> variants = req.getVariants();
        if (variants == null && pending.getParamsJson() != null) {
            Map<String, Object> stored = objectMapper.readValue(pending.getParamsJson(), new TypeReference<>() {
            });
            Object raw = stored.get("_variants");
            if (raw != null) {
                variants = objectMapper.convertValue(raw, new TypeReference<List<DeviceVariantDTO>>() {
                });
            }
        }
        save.setVariants(variants);

        var device = deviceService.save(save, adminId, adminName);

        pending.setStatus(PendingStatus.APPROVED.name());
        pending.setUpdatedAt(LocalDateTime.now());
        pendingMapper.updateById(pending);

        if (pending.getTaskId() != null) {
            updateStatus(pending.getTaskId(), TaskStatus.APPROVED, null);
        }
        logService.record(adminId, adminName, "APPROVE_PENDING", "agent_pending_data", pendingId,
                "迁移为机型: " + device.getName() + " (id=" + device.getId() + ")");
        return device.getId();
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
            updateStatus(pending.getTaskId(), TaskStatus.DISCARDED, null);
        }
        logService.record(adminId, adminName, "DISCARD_PENDING", "agent_pending_data", pendingId, pending.getModelName());
    }

    private void updateStatus(Long taskId, TaskStatus status, String error) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(status.name());
        if (error != null) {
            task.setErrorMsg(error);
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }
}
