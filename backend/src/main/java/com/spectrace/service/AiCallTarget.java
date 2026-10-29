package com.spectrace.service;

import com.spectrace.dto.ResolvedModel;
import com.spectrace.entity.AiImageProvider;

/**
 * 模型调用统计的维度快照。
 *
 * <p>落库的是调用发生时的供应商 / 模型名称，避免连接改名后历史统计被重命名成新口径。</p>
 */
public record AiCallTarget(String providerKind, Long providerId, String providerName,
                           Long modelId, String modelName) {

    public static final String KIND_CHAT = "CHAT";
    public static final String KIND_IMAGE = "IMAGE";

    /** 后台未配置连接时 buildModel 会回退到环境变量，此处用固定标签保证调用仍可统计。 */
    public static AiCallTarget chat(ResolvedModel resolved, String fallbackModelName) {
        if (resolved == null) {
            return new AiCallTarget(KIND_CHAT, null, "环境变量兜底", null, fallbackModelName);
        }
        return new AiCallTarget(KIND_CHAT, resolved.getConfigId(), resolved.getConfigName(),
                resolved.getModelId(), resolved.getModel());
    }

    public static AiCallTarget image(AiImageProvider provider) {
        return new AiCallTarget(KIND_IMAGE, provider.getId(), provider.getName(), null, provider.getModel());
    }
}
