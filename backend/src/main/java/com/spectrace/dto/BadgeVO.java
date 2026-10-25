package com.spectrace.dto;

import lombok.Data;

/**
 * 后台侧栏徽标计数汇总。字段随运营需要逐步扩充（如抓取审核待处理数）。
 */
@Data
public class BadgeVO {

    /** 待处理（OPEN）纠错反馈数 */
    private long openFeedback;

    public static BadgeVO of(long openFeedback) {
        BadgeVO vo = new BadgeVO();
        vo.setOpenFeedback(openFeedback);
        return vo;
    }
}
