package com.spectrace.controller;

import com.spectrace.common.ClientIps;
import com.spectrace.common.R;
import com.spectrace.dto.AiImageGenerateRequest;
import com.spectrace.dto.AiImageGenerateVO;
import com.spectrace.dto.AiImagePromptOptionVO;
import com.spectrace.dto.AiImageStatusVO;
import com.spectrace.entity.AiImagePrompt;
import com.spectrace.service.AiImageGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 访客端 AI 绘图：入口在社媒导出弹窗，限额与开关在服务端收口。
 *
 * <p>三个接口都走 {@code /api/public/**}，因此额外叠加按来源 IP 的分钟级限流，
 * 避免匿名访客把绘图额度刷穿。</p>
 */
@Tag(name = "访客端-AI 绘图")
@RestController
@RequestMapping("/api/public/ai-image")
@RequiredArgsConstructor
public class PublicAiImageController {

    private final AiImageGenerationService imageService;

    @Operation(summary = "AI 绘图可用性（未配置时前端隐藏入口）")
    @GetMapping("/status")
    public R<AiImageStatusVO> status() {
        return R.ok(imageService.status());
    }

    @Operation(summary = "可选提示词模板")
    @GetMapping("/prompts")
    public R<List<AiImagePromptOptionVO>> prompts() {
        List<AiImagePromptOptionVO> options = imageService.promptOptions().stream().map(prompt -> {
            AiImagePromptOptionVO vo = new AiImagePromptOptionVO();
            vo.setId(prompt.getId());
            vo.setName(prompt.getName());
            vo.setContent(prompt.getContent());
            vo.setIsDefault(prompt.getIsDefault());
            return vo;
        }).toList();
        return R.ok(options);
    }

    @Operation(summary = "按当前系列与所选范围生成海报（单 IP 分钟级限流）")
    @PostMapping("/generate")
    public R<AiImageGenerateVO> generate(@RequestBody AiImageGenerateRequest request,
                                         HttpServletRequest httpRequest) {
        return R.ok(imageService.generate(request, ClientIps.resolve(httpRequest)));
    }
}
