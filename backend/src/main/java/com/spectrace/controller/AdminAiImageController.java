package com.spectrace.controller;

import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.dto.BatchIdsRequest;
import com.spectrace.entity.AiImageJob;
import com.spectrace.entity.AiImagePrompt;
import com.spectrace.entity.AiImageProvider;
import com.spectrace.service.AiImageGenerationService;
import com.spectrace.service.AiImagePromptService;
import com.spectrace.service.AiImageProviderService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后台-AI 绘图")
@RestController
@RequestMapping("/api/admin/ai-image")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminAiImageController {

    private final AiImageProviderService providerService;
    private final AiImagePromptService promptService;
    private final AiImageGenerationService generationService;
    private final OperationLogService logService;

    // ---------------- 供应商 ----------------

    @Operation(summary = "绘图供应商列表（API Key 只回是否已配置）")
    @GetMapping("/providers")
    public R<List<AiImageProvider>> providers() {
        List<AiImageProvider> providers = providerService.list();
        providers.forEach(p -> {
            p.setApiKeySet(p.getApiKey() != null && !p.getApiKey().isEmpty());
            p.setApiKey(null);
        });
        return R.ok(providers);
    }

    @Operation(summary = "新增绘图供应商")
    @PostMapping("/providers")
    public R<AiImageProvider> createProvider(@RequestBody AiImageProvider provider,
                                             @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        provider.setId(null);
        AiImageProvider saved = providerService.save(provider);
        logService.record(adminId, adminName, "CREATE_AI_IMAGE_PROVIDER", "ai_image_provider",
                saved.getId(), saved.getName() + " / " + saved.getModel());
        saved.setApiKeySet(StringUtils.hasText(saved.getApiKey()));
        saved.setApiKey(null);
        return R.ok(saved);
    }

    @Operation(summary = "编辑绘图供应商（apiKey 留空表示不修改）")
    @PutMapping("/providers/{id}")
    public R<AiImageProvider> updateProvider(@PathVariable Long id, @RequestBody AiImageProvider provider,
                                             @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        provider.setId(id);
        AiImageProvider saved = providerService.save(provider);
        logService.record(adminId, adminName, "UPDATE_AI_IMAGE_PROVIDER", "ai_image_provider",
                id, saved.getName() + " / " + saved.getModel());
        saved.setApiKeySet(StringUtils.hasText(saved.getApiKey()));
        saved.setApiKey(null);
        return R.ok(saved);
    }

    @Operation(summary = "删除绘图供应商")
    @DeleteMapping("/providers/{id}")
    public R<Void> deleteProvider(@PathVariable Long id,
                                  @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        providerService.delete(id);
        logService.record(adminId, adminName, "DELETE_AI_IMAGE_PROVIDER", "ai_image_provider", id, null);
        return R.ok();
    }

    @Operation(summary = "设为默认绘图供应商")
    @PostMapping("/providers/{id}/default")
    public R<Void> setDefaultProvider(@PathVariable Long id,
                                      @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        providerService.setDefault(id);
        logService.record(adminId, adminName, "DEFAULT_AI_IMAGE_PROVIDER", "ai_image_provider", id, null);
        return R.ok();
    }

    // ---------------- 提示词模板 ----------------

    @Operation(summary = "提示词模板列表")
    @GetMapping("/prompts")
    public R<List<AiImagePrompt>> prompts() {
        return R.ok(promptService.list());
    }

    @Operation(summary = "新增提示词模板")
    @PostMapping("/prompts")
    public R<AiImagePrompt> createPrompt(@RequestBody AiImagePrompt prompt,
                                         @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        prompt.setId(null);
        AiImagePrompt saved = promptService.save(prompt);
        logService.record(adminId, adminName, "CREATE_AI_IMAGE_PROMPT", "ai_image_prompt",
                saved.getId(), saved.getName());
        return R.ok(saved);
    }

    @Operation(summary = "编辑提示词模板（内置标记与默认标记不受入参影响）")
    @PutMapping("/prompts/{id}")
    public R<AiImagePrompt> updatePrompt(@PathVariable Long id, @RequestBody AiImagePrompt prompt,
                                         @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        prompt.setId(id);
        AiImagePrompt saved = promptService.save(prompt);
        logService.record(adminId, adminName, "UPDATE_AI_IMAGE_PROMPT", "ai_image_prompt", id, saved.getName());
        return R.ok(saved);
    }

    @Operation(summary = "删除提示词模板（内置模板不可删）")
    @DeleteMapping("/prompts/{id}")
    public R<Void> deletePrompt(@PathVariable Long id,
                                @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        promptService.delete(id);
        logService.record(adminId, adminName, "DELETE_AI_IMAGE_PROMPT", "ai_image_prompt", id, null);
        return R.ok();
    }

    @Operation(summary = "设为默认提示词模板")
    @PostMapping("/prompts/{id}/default")
    public R<Void> setDefaultPrompt(@PathVariable Long id,
                                    @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        promptService.setDefault(id);
        logService.record(adminId, adminName, "DEFAULT_AI_IMAGE_PROMPT", "ai_image_prompt", id, null);
        return R.ok();
    }

    // ---------------- 绘制历史 ----------------

    @Operation(summary = "绘制历史（成功与失败都记录）")
    @GetMapping("/jobs")
    public R<PageResult<AiImageJob>> jobs(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "20") long size,
                                          @RequestParam(required = false) String status) {
        return R.ok(generationService.history(current, size, status));
    }

    @Operation(summary = "删除绘制历史（同时删除图片文件）")
    @PostMapping("/jobs/batch-delete")
    public R<Integer> deleteJobs(@RequestBody BatchIdsRequest request,
                                 @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        int deleted = generationService.deleteJobs(request.getIds());
        logService.record(adminId, adminName, "DELETE_AI_IMAGE_JOBS", "ai_image_job", null,
                "删除 " + deleted + " 条绘图历史");
        return R.ok(deleted);
    }
}
