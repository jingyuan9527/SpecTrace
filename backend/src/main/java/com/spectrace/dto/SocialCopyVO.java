package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社媒文案：按平台风格生成的可直接发布内容。
 * xiaohongshu 带话题标签，wechat 不带；title/points 供图文卡片直接引用。
 */
@Data
public class SocialCopyVO {

    private boolean available;

    private boolean failed;

    private boolean cached;

    private String style;

    private String title;

    private String summary;

    private List<String> points;

    private List<String> tags;

    private LocalDateTime generatedAt;
}
