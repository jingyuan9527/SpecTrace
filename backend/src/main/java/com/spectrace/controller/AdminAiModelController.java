package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.AiModelVO;
import com.spectrace.service.AiConfigService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后台-AI 模型")
@RestController
@RequestMapping("/api/admin/ai-models")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminAiModelController {

    private final AiConfigService aiConfigService;
    private final OperationLogService logService;

    @Operation(summary = "全部启用模型（用于任务选择下拉）")
    @GetMapping
    public R<List<AiModelVO>> list() {
        return R.ok(aiConfigService.listEnabledModels());
    }

    @Operation(summary = "设为默认模型")
    @PostMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable Long id,
                              @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        aiConfigService.setDefaultModel(id);
        logService.record(adminId, adminName, "SET_DEFAULT_AI_MODEL", "ai_model", id, null);
        return R.ok();
    }

    @Operation(summary = "修改模型温度 / 启用状态")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @RequestParam(required = false) Double temperature,
                          @RequestParam(required = false) Integer enabled,
                          @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        aiConfigService.updateModel(id, temperature, enabled);
        logService.record(adminId, adminName, "UPDATE_AI_MODEL", "ai_model", id,
                "temperature=" + temperature + ", enabled=" + enabled);
        return R.ok();
    }

    @Operation(summary = "删除模型")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        aiConfigService.deleteModel(id);
        logService.record(adminId, adminName, "DELETE_AI_MODEL", "ai_model", id, null);
        return R.ok();
    }
}
