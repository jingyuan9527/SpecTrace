package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.common.ParamValueParser;
import com.spectrace.common.PendingStatus;
import com.spectrace.common.TaskStatus;
import com.spectrace.common.TaskType;
import com.spectrace.dto.*;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.AgentTask;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.ParamDef;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final AgentTaskLogService taskLogService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DeviceImageFetchService deviceImageFetchService;

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
            task.setStatus(TaskStatus.FAILED.name());
            task.setErrorMsg("应用重启，任务中断");
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
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
        return page;
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
        Map<Long, Long> pendingIds = new java.util.HashMap<>();
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
                Device existing = findExistingDevice(task.getBrandName(), model.getName());
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
            if (findExistingDevice(brand.getId(), name) != null) {
                skipped.add(name);
                continue;
            }
            DeviceSaveRequest save = new DeviceSaveRequest();
            save.setBrandId(brand.getId());
            save.setName(name);
            save.setReleaseDate(parseDate(releaseDates.get(name)));
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
                updateStatus(previousTaskId, TaskStatus.DISCARDED, null);
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
        // 队列规模有限（只筛 PENDING_REVIEW），整体载入内存打标后分页；其他状态保持创建时间倒序。
        if (PendingStatus.PENDING_REVIEW.name().equals(status)) {
            List<AgentPendingData> rows = pendingMapper.selectList(query);
            rows.forEach(row -> row.setRiskLevel(riskLevelOf(row)));
            Comparator<AgentPendingData> byRisk = Comparator
                    .comparingInt((AgentPendingData row) -> riskOrder(row.getRiskLevel()))
                    .thenComparing(AgentPendingData::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            rows.sort(byRisk);
            long total = rows.size();
            int from = (int) Math.min((current - 1) * size, total);
            int to = (int) Math.min(from + size, total);
            Page<AgentPendingData> page = new Page<>(current, size, total);
            page.setRecords(rows.subList(from, to));
            return page;
        }
        return pendingMapper.selectPage(new Page<>(current, size), query);
    }

    private static final double CONFIDENCE_VERIFY_THRESHOLD = 0.6;
    private static final double CONFIDENCE_PASS_THRESHOLD = 0.9;

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
        Device existing = findExistingDevice(data.getBrandName(), data.getModelName());
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
        vo.setParamWarnings(unitWarnings(vo.getParams()));
        return vo;
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
        save.setParamConfidence(confidenceForApprove(pending, req.getParams()));

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
            updateStatus(pending.getTaskId(), TaskStatus.APPROVED, null);
        }
        logService.record(adminId, adminName, "APPROVE_PENDING", "agent_pending_data", pendingId,
                "迁移为机型: " + device.getName() + " (id=" + device.getId() + ")");
        log.info("审核通过: pendingId={} -> deviceId={} 机型 {}（{}）admin={}",
                pendingId, device.getId(), device.getName(), pending.getBrandName(), adminName);
        return device.getId();
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failures.size());
        result.put("failures", failures);
        return result;
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
        LocalDate releaseDate = parseDate(params.get("release_date"));
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
        updateStatus(taskId, TaskStatus.DISCARDED, null);
        logService.record(adminId, adminName, "DISCARD_DISCOVERY", "agent_task", taskId, task.getModelName());
        log.info("拒绝系列发现结果: taskId={} 系列={} admin={}", taskId, task.getModelName(), adminName);
    }

    /** 丢弃待审核数据。 */
    @Transactional
    public void discard(Long pendingId, Long adminId, String adminName) {        AgentPendingData pending = pendingMapper.selectById(pendingId);
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
