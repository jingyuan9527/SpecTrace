package com.spectrace.controller;

import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.entity.Feedback;
import com.spectrace.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台-纠错反馈")
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "纠错反馈列表")
    @GetMapping
    public R<PageResult<Feedback>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String status) {
        return R.ok(PageResult.of(feedbackService.page(current, size, status)));
    }

    @Operation(summary = "标记反馈处理状态")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id,
                                @RequestParam String status,
                                @RequestParam(required = false) String note,
                                @RequestAttribute Long adminId,
                                @RequestAttribute String adminName) {
        feedbackService.updateStatus(id, status, note, adminId, adminName);
        return R.ok();
    }
}
