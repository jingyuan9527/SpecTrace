package com.spectrace.controller;

import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.dto.ApproveRequest;
import com.spectrace.dto.BatchIdsRequest;
import com.spectrace.dto.PendingDataVO;
import com.spectrace.dto.TaskCreateRequest;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.AgentTask;
import com.spectrace.service.AgentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "后台-Agent抓取任务管理")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminAgentController {

    private final AgentTaskService agentTaskService;

    @Operation(summary = "新建抓取任务")
    @PostMapping("/agent-tasks")
    public R<AgentTask> createTask(@Valid @RequestBody TaskCreateRequest req,
                                   @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        return R.ok(agentTaskService.createTask(req, adminId, adminName));
    }

    @Operation(summary = "重试失败的抓取任务")
    @PostMapping("/agent-tasks/{id}/retry")
    public R<AgentTask> retry(@PathVariable Long id,
                              @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        return R.ok(agentTaskService.retry(id, adminId, adminName));
    }

    @Operation(summary = "抓取任务列表")
    @GetMapping("/agent-tasks")
    public R<PageResult<AgentTask>> tasks(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String status) {
        return R.ok(PageResult.of(agentTaskService.pageTasks(current, size, status)));
    }

    @Operation(summary = "待审核数据列表")
    @GetMapping("/pending-data")
    public R<PageResult<PendingDataVO>> pending(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String status) {
        var page = agentTaskService.pagePending(current, size, status);
        var records = page.getRecords().stream().map(agentTaskService::toVO).toList();
        return R.ok(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records));
    }

    @Operation(summary = "待审核数据详情（含库中现有值，供 diff）")
    @GetMapping("/pending-data/{id}")
    public R<PendingDataVO> pendingDetail(@PathVariable Long id) {
        return R.ok(agentTaskService.toDetailVO(agentTaskService.getPending(id)));
    }

    @Operation(summary = "审核通过，迁移至正式机型库")
    @PostMapping("/pending-data/{id}/approve")
    public R<Long> approve(@PathVariable Long id, @RequestBody ApproveRequest req,
                           @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        return R.ok(agentTaskService.approve(id, req, adminId, adminName));
    }

    @Operation(summary = "批量通过待审核数据（使用抓取值直接入库）")
    @PostMapping("/pending-data/batch-approve")
    public R<java.util.Map<String, Object>> batchApprove(@RequestBody BatchIdsRequest req,
                                                         @RequestAttribute Long adminId,
                                                         @RequestAttribute String adminName) {
        return R.ok(agentTaskService.batchApprove(req.getIds(), adminId, adminName));
    }

    @Operation(summary = "批量丢弃待审核数据")
    @PostMapping("/pending-data/batch-discard")
    public R<java.util.Map<String, Object>> batchDiscard(@RequestBody BatchIdsRequest req,
                                                         @RequestAttribute Long adminId,
                                                         @RequestAttribute String adminName) {
        return R.ok(agentTaskService.batchDiscard(req.getIds(), adminId, adminName));
    }

    @Operation(summary = "丢弃待审核数据")
    @PostMapping("/pending-data/{id}/discard")
    public R<Void> discard(@PathVariable Long id,
                           @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        agentTaskService.discard(id, adminId, adminName);
        return R.ok();
    }
}
