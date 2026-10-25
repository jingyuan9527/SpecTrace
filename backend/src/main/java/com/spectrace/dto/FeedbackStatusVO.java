package com.spectrace.dto;

import com.spectrace.entity.Feedback;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访客凭编号查询反馈进度的对外视图。
 * 只暴露状态与时间，不含建议值、说明、联系方式等提交内容。
 */
@Data
public class FeedbackStatusVO {

    private Long id;

    /** OPEN / RESOLVED / IGNORED */
    private String status;

    private LocalDateTime createdAt;

    /** 状态最后一次变更时间，OPEN 时即提交时间 */
    private LocalDateTime updatedAt;

    public static FeedbackStatusVO from(Feedback feedback) {
        FeedbackStatusVO vo = new FeedbackStatusVO();
        vo.setId(feedback.getId());
        vo.setStatus(feedback.getStatus());
        vo.setCreatedAt(feedback.getCreatedAt());
        vo.setUpdatedAt(feedback.getUpdatedAt());
        return vo;
    }
}
