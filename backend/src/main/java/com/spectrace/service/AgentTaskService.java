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
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.AgentTaskMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.task.TaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskMapper taskMapper;
    private final AgentPendingDataMapper pendingMapper;
    private final BrandMapper brandMapper;
    private final DeviceMapper deviceMapper;
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

    /** 失败任务重试：按原参数新建一条任务，保留历史失败记录便于对比。 */
    public AgentTask retry(Long taskId, Long adminId, String adminName) {
        AgentTask origin = taskMapper.selectById(taskId);
        if (origin == null) {
            throw new BizException("抓取任务不存在");
        }
        if (!TaskStatus.FAILED.name().equals(origin.getStatus())) {
            throw new BizException("仅失败的任务支持重试");
        }
        TaskCreateRequest req = new TaskCreateRequest();
        req.setModelName(origin.getModelName());
        req.setBrandName(origin.getBrandName());
        req.setAiModelId(origin.getAiModelId());
        AgentTask task = createTask(req, adminId, adminName);
        log.info("重试抓取任务: originId={} newId={} model={} admin={}", taskId, task.getId(), task.getModelName(), adminName);
        logService.record(adminId, adminName, "RETRY_AGENT_TASK", "agent_task", task.getId(),
                "重试自任务 #" + taskId + "：" + task.getModelName());
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

    /** 审核详情：在基础 VO 上补充库中现有数据，前端据此标出「旧值 → 新值」。 */
    public PendingDataVO toDetailVO(AgentPendingData data) {
        PendingDataVO vo = toVO(data);
        Device existing = findExistingDevice(vo.getBrandName(), vo.getModelName());
        vo.setNewDevice(existing == null);
        if (existing != null) {
            vo.setExistingDeviceId(existing.getId());
            vo.setExistingParams(deviceService.paramValuesOf(existing.getId()));
            vo.setExistingVariants(deviceService.variantsOf(existing.getId()));
        }
        return vo;
    }

    /** 按「品牌名 + 机型名」定位库中已有记录，忽略首尾空格与大小写。 */
    private Device findExistingDevice(String brandName, String modelName) {
        if (!StringUtils.hasText(brandName) || !StringUtils.hasText(modelName)) {
            return null;
        }
        Brand brand = brandMapper.selectOne(Wrappers.<Brand>lambdaQuery()
                .eq(Brand::getName, brandName.trim())
                .last("LIMIT 1"));
        if (brand == null) {
            return null;
        }
        return findExistingDevice(brand.getId(), modelName);
    }

    private Device findExistingDevice(Long brandId, String modelName) {
        if (brandId == null || !StringUtils.hasText(modelName)) {
            return null;
        }
        return deviceMapper.selectOne(Wrappers.<Device>lambdaQuery()
                .eq(Device::getBrandId, brandId)
                .eq(Device::getName, modelName.trim())
                .last("LIMIT 1"));
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

        // 库中已有同品牌同名机型时按覆盖更新处理，避免重复抓取产生重复机型；
        // 审核表单未回填的图片/简介/日期不回写，防止把已有数据清空
        Device existing = findExistingDevice(req.getBrandId(), save.getName());
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
        }

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

        // 参数级溯源：审核通过时把抓取来源写入每个参数值
        var device = deviceService.save(save, adminId, adminName, pending.getSourceUrl());

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

    /**
     * 批量通过：直接使用抓取值入库，不经过人工修正。
     * 单条失败不影响其余记录，失败原因原样返回给前端。
     */
    @Transactional
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
                approve(id, buildApproveRequest(pending), adminId, adminName);
                success++;
            } catch (Exception e) {
                log.error("批量审核失败: pendingId={}", id, e);
                failures.add("#" + id + " " + e.getMessage());
            }
        }
        log.info("批量审核完成: 请求 {} 条，成功 {} 条，失败 {} 条", ids.size(), success, failures.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
    }

    /** 批量丢弃。 */
    @Transactional
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
                discard(id, adminId, adminName);
                success++;
            } catch (Exception e) {
                log.error("批量丢弃失败: pendingId={}", id, e);
                failures.add("#" + id + " " + e.getMessage());
            }
        }
        log.info("批量丢弃完成: 请求 {} 条，成功 {} 条，失败 {} 条", ids.size(), success, failures.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
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
            if (!"_variants".equals(key) && value != null) {
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
        LocalDate releaseDate = parseDate(params.get("release_date"));
        if (releaseDate != null) {
            req.setReleaseDate(releaseDate);
        }
        return req;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            log.warn("采集结果的发布日期无法解析，按空值入库: {}", value);
            return null;
        }
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
