package com.spectrace.controller;

import com.spectrace.common.BizException;
import com.spectrace.common.R;
import com.spectrace.dto.AgentCallbackRequest;
import com.spectrace.dto.AgentTaskPayload;
import com.spectrace.dto.ResolvedModel;
import com.spectrace.entity.ParamDef;
import com.spectrace.service.AgentTaskService;
import com.spectrace.service.AiConfigService;
import com.spectrace.service.ParamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供 AgentScope 调用的内部接口。
 * 通过 X-Agent-Token 校验调用方身份，避免回调接口被外部滥用。
 */
@Tag(name = "AgentScope 交互接口")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentTaskService agentTaskService;
    private final ParamService paramService;
    private final AiConfigService aiConfigService;

    @Value("${spectrace.agent.callback-token}")
    private String callbackToken;

    private void verify(String token) {
        if (token == null || !token.equals(callbackToken)) {
            throw new BizException(401, "非法调用方令牌");
        }
    }

    @Operation(summary = "AgentScope 拉取下一个抓取任务")
    @GetMapping("/tasks/pull")
    public ResponseEntity<R<AgentTaskPayload>> pull(@RequestHeader("X-Agent-Token") String token) {
        verify(token);
        AgentTaskPayload payload = agentTaskService.pullNext();
        if (payload == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(R.ok(payload));
    }

    @Operation(summary = "标记任务为抓取中")
    @PostMapping("/tasks/{id}/running")
    public R<Void> running(@PathVariable Long id, @RequestHeader("X-Agent-Token") String token) {
        verify(token);
        agentTaskService.markRunning(id);
        return R.ok();
    }

    @Operation(summary = "AgentScope 抓取结果回调")
    @PostMapping("/callback")
    public R<Void> callback(@RequestBody AgentCallbackRequest req,
                            @RequestHeader("X-Agent-Token") String token) {
        verify(token);
        agentTaskService.handleCallback(req);
        return R.ok();
    }

    @Operation(summary = "获取参数抽取 Schema（供 LLM 结构化输出约束）")
    @GetMapping("/schema")
    public R<List<ParamDef>> schema(@RequestHeader("X-Agent-Token") String token) {
        verify(token);
        return R.ok(paramService.listDefs(null, 1));
    }

    @Operation(summary = "获取 AI 模型调用参数（可指定 modelId，缺省用默认模型）")
    @GetMapping("/ai-config")
    public R<ResolvedModel> aiConfig(@RequestHeader("X-Agent-Token") String token,
                                     @RequestParam(required = false) Long modelId) {
        verify(token);
        return R.ok(aiConfigService.resolve(modelId));
    }
}
