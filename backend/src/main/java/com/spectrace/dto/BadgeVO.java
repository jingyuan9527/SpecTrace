package com.spectrace.dto;

import lombok.Data;

/**
 * 后台侧栏徽标计数汇总。字段随运营需要逐步扩充（如抓取审核待处理数）。
 */
@Data
public class BadgeVO {

    /** 待处理（OPEN）纠错反馈数 */
    private long openFeedback;

    /** 待审核抓取数据条数（T100） */
    private long pendingReview;

    /** 最老一条待审核数据已等待的小时数（T100，积压时效比条数更痛；无积压为 0） */
    private long pendingOldestHours;

    public static BadgeVO of(long openFeedback, long pendingReview, long pendingOldestHours) {
        BadgeVO vo = new BadgeVO();
        vo.setOpenFeedback(openFeedback);
        vo.setPendingReview(pendingReview);
        vo.setPendingOldestHours(pendingOldestHours);
        return vo;
    }
}
