package com.spectrace.controller;

import com.spectrace.common.ClientIps;
import com.spectrace.common.R;
import com.spectrace.dto.FeedbackRequest;
import com.spectrace.dto.FeedbackStatusVO;
import com.spectrace.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访客提交参数纠错，无需登录；提交返回编号，可凭编号查询处理进度。
 */
@Slf4j
@Tag(name = "访客端-参数纠错")
@RestController
@RequestMapping("/api/public/feedback")
@RequiredArgsConstructor
public class PublicFeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "提交参数纠错反馈")
    @PostMapping
    public R<Long> submit(@Valid @RequestBody FeedbackRequest req, HttpServletRequest request) {
        return R.ok(feedbackService.submit(req, clientKey(request)));
    }

    @Operation(summary = "按编号查询反馈处理进度")
    @GetMapping("/{id}")
    public R<FeedbackStatusVO> status(@PathVariable Long id) {
        return R.ok(FeedbackStatusVO.from(feedbackService.requireById(id)));
    }

    /** 取真实来源 IP 作为限流维度；反向代理下优先读 X-Forwarded-For 首个地址。 */
    private String clientKey(HttpServletRequest request) {
        return ClientIps.resolve(request);
    }
}
