package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.AiModelVO;
import com.spectrace.dto.ConnectivityTestVO;
import com.spectrace.dto.ImportModelsRequest;
import com.spectrace.entity.AiConfig;
import com.spectrace.service.AiConfigService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后台-AI 连接与模型")
@RestController
@RequestMapping("/api/admin/ai-configs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminAiConfigController {

    private final AiConfigService aiConfigService;
    private final OperationLogService logService;

    // ---------------- 连接 ----------------

    @Operation(summary = "连接列表")
    @GetMapping
    public R<List<AiConfig>> list() {
        List<AiConfig> configs = aiConfigService.listConfigs();
        configs.forEach(c -> {
            c.setApiKeySet(c.getApiKey() != null && !c.getApiKey().isEmpty());
            c.setApiKey(null);
        });
        return R.ok(configs);
    }

    @Operation(summary = "新增连接")
    @PostMapping
    public R<AiConfig> create(@RequestBody AiConfig config,
                              @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        config.setId(null);
        AiConfig saved = aiConfigService.saveConfig(config);
        logService.record(adminId, adminName, "CREATE_AI_CONFIG", "ai_config", saved.getId(), saved.getName());
        saved.setApiKey(null);
        return R.ok(saved);
    }

    @Operation(summary = "编辑连接（apiKey 留空表示不修改）")
    @PutMapping("/{id}")
    public R<AiConfig> update(@PathVariable Long id, @RequestBody AiConfig config,
                              @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        config.setId(id);
        AiConfig saved = aiConfigService.saveConfig(config);
        logService.record(adminId, adminName, "UPDATE_AI_CONFIG", "ai_config", id, saved.getName());
        saved.setApiKey(null);
        return R.ok(saved);
    }

    @Operation(summary = "删除连接（同时删除其下模型）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        aiConfigService.deleteConfig(id);
        logService.record(adminId, adminName, "DELETE_AI_CONFIG", "ai_config", id, null);
        return R.ok();
    }

    // ---------------- 连接下的模型 ----------------

    @Operation(summary = "连接下的模型列表")
    @GetMapping("/{id}/models")
    public R<List<AiModelVO>> models(@PathVariable Long id) {
        return R.ok(aiConfigService.listModels(id));
    }

    @Operation(summary = "拉取远端模型列表（调用 {baseUrl}/models）")
    @GetMapping("/{id}/remote-models")
    public R<List<String>> remoteModels(@PathVariable Long id) {
        return R.ok(aiConfigService.pullRemoteModels(id));
    }

    @Operation(summary = "从远端批量导入模型")
    @PostMapping("/{id}/import-models")
    public R<List<AiModelVO>> importModels(@PathVariable Long id, @RequestBody ImportModelsRequest req,
                                           @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        List<AiModelVO> models = aiConfigService.importModels(id, req);
        logService.record(adminId, adminName, "IMPORT_AI_MODELS", "ai_config", id,
                "导入 " + req.getModels().size() + " 个模型");
        return R.ok(models);
    }

    @Operation(summary = "连通性测试（向 {baseUrl}/chat/completions 发最小请求，model 不传用该连接首个启用模型）")
    @PostMapping("/{id}/test")
    public R<ConnectivityTestVO> test(@PathVariable Long id,
                                      @RequestParam(required = false) String model,
                                      @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        ConnectivityTestVO result = aiConfigService.testConnection(id, model);
        logService.record(adminId, adminName, "TEST_AI_CONFIG", "ai_config", id,
                result.getModel() + (result.isSuccess() ? " 成功 " + result.getLatencyMs() + "ms" : " 失败"));
        return R.ok(result);
    }
}
