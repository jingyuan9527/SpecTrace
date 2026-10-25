package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.BadgeVO;
import com.spectrace.service.AgentTaskService;
import com.spectrace.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台侧栏徽标计数：轻量聚合接口，侧栏每次进入后台时拉一次。
 */
@Tag(name = "后台-徽标汇总")
@RestController
@RequestMapping("/api/admin/badges")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminBadgeController {

    private final FeedbackService feedbackService;
    private final AgentTaskService agentTaskService;

    @Operation(summary = "侧栏徽标计数")
    @GetMapping
    public R<BadgeVO> badges() {
        return R.ok(BadgeVO.of(feedbackService.countOpen(),
                agentTaskService.countPendingReview(),
                agentTaskService.oldestPendingHours()));
    }
}
