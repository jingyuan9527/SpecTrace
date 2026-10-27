package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.common.CollectedDate;
import com.spectrace.common.PendingStatus;
import com.spectrace.common.TaskStatus;
import com.spectrace.common.TaskType;
import com.spectrace.dto.AgentTaskPayload;
import com.spectrace.dto.DeviceSaveRequest;
import com.spectrace.dto.SeriesCreateResultVO;
import com.spectrace.dto.SeriesDiscoveryVO;
import com.spectrace.dto.TaskCreateRequest;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.AgentTask;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.AgentTaskMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.task.TaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 抓取任务的生命周期：提交、重试、列表、重启恢复，以及系列发现任务的读取与草稿建档。
 *
 * <p>本类不再承担回调落库（{@link AgentCallbackService}）与审核入库（{@link AgentReviewService}）：
 * 三者改动频率与失败代价都不同——任务是调度问题、回调是 Agent 入口、审核是人工决策，
 * 混在一个类里时任何一处改动都要求全量回归。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskMapper taskMapper;
    private final AgentPendingDataMapper pendingMapper;
    private final BrandMapper brandMapper;
    private final TaskQueue taskQueue;
    private final DeviceService deviceService;
    private final OperationLogService logService;
    private final AgentTaskLogService taskLogService;
    private final AgentTaskStatusService taskStatusService;
    private final ObjectMapper objectMapper;

    /** 管理员提交抓取任务。 */
    public AgentTask createTask(TaskCreateRequest req, Long adminId, String adminName) {
        TaskType taskType = TaskType.parse(req.getTaskType());
        if (taskType == TaskType.SERIES_DISCOVERY && !StringUtils.hasText(req.getBrandName())) {
            // 发现结果要落成机型档案，必须有归属品牌；Agent 推断可能命中库外品牌，一期不自动建品牌
            throw new BizException("系列发现任务必须选择品牌");
        }
        AgentTask task = new AgentTask();
        task.setModelName(req.getModelName());
        task.setBrandName(req.getBrandName());
        task.setAiModelId(req.getAiModelId());
        task.setTaskType(taskType.name());
        task.setSubscriptionId(req.getSubscriptionId());
        task.setStatus(TaskStatus.QUEUED.name());
        taskMapper.insert(task);

        AgentTaskPayload payload = new AgentTaskPayload();
        payload.setTaskId(task.getId());
        payload.setModelName(task.getModelName());
        payload.setBrandName(task.getBrandName());
        payload.setAiModelId(task.getAiModelId());
        payload.setTaskType(task.getTaskType());
        try {
            taskQueue.push(payload);
        } catch (Exception e) {
            // 入队失败必须把任务落为失败，否则它将永远停在 QUEUED 且无队列条目可消费
            log.error("任务入队失败 taskId={}", task.getId(), e);
            task.setStatus(TaskStatus.FAILED.name());
            task.setErrorMsg("任务入队失败：" + e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            throw new BizException("任务入队失败：" + e.getMessage());
        }

        logService.record(adminId, adminName, "CREATE_AGENT_TASK", "agent_task", task.getId(), task.getModelName());
        log.info("提交抓取任务: taskId={} type={} brand={} model={} admin={}",
                task.getId(), task.getTaskType(), task.getBrandName(), task.getModelName(), adminName);
        return task;
    }

    /**
     * 应用重启后恢复被中断的任务，避免「幽灵任务」永久卡在中间态：
     * RUNNING（进程消失，抓取必然中断）置为失败；队列不持久时（内存队列重启即丢）QUEUED 重新入队。
     * Redis 队列中的 QUEUED 重启后仍会被消费，重复入队反而会造成同任务双跑。
     */
    public void recoverInterruptedTasks(boolean queuePersistent, boolean agentEnabled) {
        List<AgentTask> running = taskMapper.selectList(Wrappers.<AgentTask>lambdaQuery()
                .eq(AgentTask::getStatus, TaskStatus.RUNNING.name()));
        for (AgentTask task : running) {
            taskStatusService.updateStatus(task.getId(), TaskStatus.FAILED, "应用重启，任务中断");
            log.warn("重启恢复：中断任务置为失败 taskId={}", task.getId());
        }
        if (queuePersistent || !agentEnabled) {
            return;
        }
        List<AgentTask> queued = taskMapper.selectList(Wrappers.<AgentTask>lambdaQuery()
                .eq(AgentTask::getStatus, TaskStatus.QUEUED.name()));
        for (AgentTask task : queued) {
            AgentTaskPayload payload = new AgentTaskPayload();
            payload.setTaskId(task.getId());
            payload.setModelName(task.getModelName());
            payload.setBrandName(task.getBrandName());
            payload.setAiModelId(task.getAiModelId());
            payload.setTaskType(task.getTaskType());
            taskQueue.push(payload);
            log.info("重启恢复：内存队列丢失，任务重新入队 taskId={} model={}", task.getId(), task.getModelName());
        }
    }

    /**
     * 失败任务重试：按原参数新建一条任务，保留历史失败记录便于对比。
     * aiModelId 非空时覆盖原模型——超时类失败常见原因是模型 API 无响应，重试时换模型是主要恢复手段。
     */
    public AgentTask retry(Long taskId, Long aiModelId, Long adminId, String adminName) {
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
        req.setAiModelId(aiModelId != null ? aiModelId : origin.getAiModelId());
        req.setTaskType(origin.getTaskType());
        AgentTask task = createTask(req, adminId, adminName);
        boolean modelChanged = aiModelId != null && !aiModelId.equals(origin.getAiModelId());
        log.info("重试抓取任务: originId={} newId={} model={} aiModelId={}{} admin={}",
                taskId, task.getId(), task.getModelName(), task.getAiModelId(),
                modelChanged ? "（已更换模型）" : "", adminName);
        logService.record(adminId, adminName, "RETRY_AGENT_TASK", "agent_task", task.getId(),
                "重试自任务 #" + taskId + "：" + task.getModelName() + (modelChanged ? "（更换模型）" : ""));
        return task;
    }

    public IPage<AgentTask> pageTasks(long current, long size, String status) {
        var query = Wrappers.<AgentTask>lambdaQuery().orderByDesc(AgentTask::getCreatedAt);
        if (status != null && !status.isBlank()) {
            query.eq(AgentTask::getStatus, status);
        }
        IPage<AgentTask> page = taskMapper.selectPage(new Page<>(current, size), query);
        fillPendingIds(page.getRecords());
        fillApprovedFlags(page.getRecords());
        return page;
    }

    /**
     * 标注失败任务对应的机型是否已审核通过，前端据此隐藏重试。
     *
     * <p>判定口径是「同品牌同名机型存在 APPROVED 的抓取记录」，而不是机型库里是否有档案：
     * 手工新增 / CSV 导入的机型没有抓取记录，重抓仍可能是有意义的。</p>
     */
    private void fillApprovedFlags(List<AgentTask> records) {
        List<AgentTask> failed = records.stream()
                .filter(task -> TaskStatus.FAILED.name().equals(task.getStatus()))
                .filter(task -> TaskType.parse(task.getTaskType()) == TaskType.DEVICE_DETAIL)
                .toList();
        if (failed.isEmpty()) {
            return;
        }
        List<String> modelNames = failed.stream()
                .map(AgentTask::getModelName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (modelNames.isEmpty()) {
            return;
        }
        // 机型名可能有大小写差异，先按 IN 收窄候选，再在内存里做归一化比对
        Map<String, Set<String>> approvedBrands = new HashMap<>();
        pendingMapper.selectList(Wrappers.<AgentPendingData>lambdaQuery()
                        .eq(AgentPendingData::getStatus, PendingStatus.APPROVED.name())
                        .in(AgentPendingData::getModelName, modelNames))
                .forEach(row -> approvedBrands
                        .computeIfAbsent(normalizeMatchValue(row.getModelName()), key -> new HashSet<>())
                        .add(normalizeMatchValue(row.getBrandName())));
        for (AgentTask task : failed) {
            Set<String> brands = approvedBrands.get(normalizeMatchValue(task.getModelName()));
            // 任务未指定品牌时（由 Agent 推断）品牌不可比，退化为按机型名匹配
            boolean matched = brands != null
                    && (StringUtils.hasText(task.getBrandName())
                        ? brands.contains(normalizeMatchValue(task.getBrandName()))
                        : !brands.isEmpty());
            task.setApproved(matched);
        }
    }

    private String normalizeMatchValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 补齐待审核任务的 pendingId，供前端一键跳到抓取审核页并直接打开该条。
     *
     * <p>只有单机型抓取会产生待审核记录（系列发现的结果只落在 task.result），
     * 因此只查 DEVICE_DETAIL 且状态为待审核的任务，避免无谓查询。
     */
    private void fillPendingIds(List<AgentTask> records) {
        List<Long> taskIds = records.stream()
                .filter(task -> TaskStatus.PENDING_REVIEW.name().equals(task.getStatus()))
                .filter(task -> TaskType.parse(task.getTaskType()) == TaskType.DEVICE_DETAIL)
                .map(AgentTask::getId)
                .toList();
        if (taskIds.isEmpty()) {
            return;
        }
        Map<Long, Long> pendingIds = new HashMap<>();
        pendingMapper.selectList(Wrappers.<AgentPendingData>lambdaQuery()
                        .in(AgentPendingData::getTaskId, taskIds)
                        .eq(AgentPendingData::getStatus, PendingStatus.PENDING_REVIEW.name()))
                .forEach(row -> {
                    if (row.getTaskId() != null) {
                        // 同一任务理论上只有一条待审核记录，万一有历史重复取最新一条
                        pendingIds.merge(row.getTaskId(), row.getId(), Math::max);
                    }
                });
        records.forEach(task -> task.setPendingId(pendingIds.get(task.getId())));
    }

    /** 系列发现任务详情：解析 result 里的机型清单，并标注库中已存在的同名机型。 */
    @SneakyThrows
    public SeriesDiscoveryVO getDiscoveryDetail(Long taskId) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("抓取任务不存在");
        }
        if (TaskType.parse(task.getTaskType()) != TaskType.SERIES_DISCOVERY) {
            throw new BizException("该任务不是系列发现任务");
        }
        SeriesDiscoveryVO vo = new SeriesDiscoveryVO();
        vo.setTaskId(task.getId());
        vo.setBrandName(task.getBrandName());
        vo.setSourceUrl(task.getSourceUrl());
        vo.setStatus(task.getStatus());
        List<SeriesDiscoveryVO.Item> models = new ArrayList<>();
        if (StringUtils.hasText(task.getResult())) {
            JsonNode root = objectMapper.readTree(task.getResult());
            vo.setSeriesName(root.path("seriesName").asText(null));
            for (JsonNode item : root.path("models")) {
                String name = item.isTextual() ? item.asText() : item.path("name").asText("");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                SeriesDiscoveryVO.Item model = new SeriesDiscoveryVO.Item();
                model.setName(name.trim());
                model.setReleaseDate(item.isTextual() ? null : item.path("releaseDate").asText(null));
                Device existing = deviceService.findByName(task.getBrandName(), model.getName());
                model.setExisting(existing != null);
                if (existing != null) {
                    model.setDeviceId(existing.getId());
                }
                models.add(model);
            }
        }
        vo.setModels(models);
        return vo;
    }

    /**
     * 把勾选的发现机型创建为草稿档案（status=0，前台不可见、参数为空）。
     *
     * <p>后续在机型管理页发起详情抓取，审核通过时现有覆盖逻辑会把 status 置 1 自动上架。
     * 已存在同名机型的跳过，保证重复点击幂等。
     */
    @Transactional
    public SeriesCreateResultVO createDevicesFromDiscovery(Long taskId, List<String> names,
                                                           Long adminId, String adminName) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("抓取任务不存在");
        }
        if (TaskType.parse(task.getTaskType()) != TaskType.SERIES_DISCOVERY) {
            throw new BizException("该任务不是系列发现任务");
        }
        if (!TaskStatus.PENDING_REVIEW.name().equals(task.getStatus())
                && !TaskStatus.APPROVED.name().equals(task.getStatus())) {
            throw new BizException(TaskStatus.DISCARDED.name().equals(task.getStatus())
                    ? "该发现结果已被拒绝，无法创建机型"
                    : "任务尚未完成发现，无法创建机型");
        }
        Brand brand = null;
        if (StringUtils.hasText(task.getBrandName())) {
            brand = brandMapper.selectOne(Wrappers.<Brand>lambdaQuery()
                    .eq(Brand::getName, task.getBrandName().trim())
                    .last("LIMIT 1"));
        }
        if (brand == null) {
            throw new BizException("品牌不存在或已删除：" + task.getBrandName());
        }

        Map<String, String> releaseDates = releaseDatesOf(task);
        List<Long> deviceIds = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String name : names.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()) {
            if (deviceService.findByName(brand.getId(), name) != null) {
                skipped.add(name);
                continue;
            }
            DeviceSaveRequest save = new DeviceSaveRequest();
            save.setBrandId(brand.getId());
            save.setName(name);
            save.setReleaseDate(CollectedDate.parse(releaseDates.get(name)));
            save.setStatus(0);
            Device device = deviceService.save(save, adminId, adminName, task.getSourceUrl());
            deviceIds.add(device.getId());
        }

        if (!deviceIds.isEmpty()) {
            task.setStatus(TaskStatus.APPROVED.name());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            logService.record(adminId, adminName, "CREATE_DEVICES_FROM_DISCOVERY", "agent_task", taskId,
                    "创建草稿机型 " + deviceIds.size() + " 个（品牌：" + brand.getName() + "）");
            taskLogService.info(taskId,
                    "已创建草稿机型 " + deviceIds.size() + " 个，跳过已存在 " + skipped.size() + " 个");
        }
        SeriesCreateResultVO vo = new SeriesCreateResultVO();
        vo.setCreated(deviceIds.size());
        vo.setSkippedExisting(skipped);
        vo.setDeviceIds(deviceIds);
        log.info("系列发现建档案: taskId={} 品牌 {} 创建 {} 个草稿机型，跳过已存在 {} 个，admin={}",
                taskId, brand.getName(), deviceIds.size(), skipped.size(), adminName);
        return vo;
    }

    /** 从系列发现结果里取 机型名 -> 发布日期 映射；结果缺失的机型名在返回 map 中没有对应键。 */
    @SneakyThrows
    private Map<String, String> releaseDatesOf(AgentTask task) {
        Map<String, String> dates = new LinkedHashMap<>();
        if (!StringUtils.hasText(task.getResult())) {
            return dates;
        }
        for (JsonNode item : objectMapper.readTree(task.getResult()).path("models")) {
            String name = item.isTextual() ? item.asText() : item.path("name").asText("");
            if (StringUtils.hasText(name)) {
                dates.put(name.trim(), item.isTextual() ? null : item.path("releaseDate").asText(null));
            }
        }
        return dates;
    }

    /**
     * 拒绝系列发现结果：任务置为已丢弃，清单不可再创建草稿机型。
     * 仅作用于发现任务本身——之前已创建的草稿机型是独立档案，由管理员在机型管理中另行处理。
     */
    @Transactional
    public void discardDiscovery(Long taskId, Long adminId, String adminName) {
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("抓取任务不存在");
        }
        if (TaskType.parse(task.getTaskType()) != TaskType.SERIES_DISCOVERY) {
            throw new BizException("只有系列发现任务可以拒绝发现结果");
        }
        if (!TaskStatus.PENDING_REVIEW.name().equals(task.getStatus())) {
            throw new BizException("仅待审核状态的发现结果可以拒绝");
        }
        taskStatusService.updateStatus(taskId, TaskStatus.DISCARDED, null);
        logService.record(adminId, adminName, "DISCARD_DISCOVERY", "agent_task", taskId, task.getModelName());
        log.info("拒绝系列发现结果: taskId={} 系列={} admin={}", taskId, task.getModelName(), adminName);
    }
}
