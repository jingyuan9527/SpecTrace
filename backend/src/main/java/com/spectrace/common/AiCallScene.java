package com.spectrace.common;

/**
 * 模型调用所属业务场景：抓取与系列发现按轮次各记一条，摘要与绘图各记一条。
 */
public enum AiCallScene {
    AGENT_SCRAPE,
    AGENT_DISCOVERY,
    INSIGHT,
    IMAGE
}
