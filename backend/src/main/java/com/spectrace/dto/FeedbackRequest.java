package com.spectrace.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 访客提交参数纠错。suggestedValue 与 description 至少填一项，由服务端校验。
 */
@Data
public class FeedbackRequest {

    @NotNull
    private Long deviceId;

    private Long paramDefId;

    private String paramKey;

    private String currentValue;

    private String suggestedValue;

    private String description;

    /** 选填，便于回访；不做格式校验以免误伤 */
    private String contact;
}
