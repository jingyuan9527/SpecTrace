package com.spectrace.dto;

import lombok.Data;

/** AI 推荐的系列名。existing 表示同名系列已存在（前端标注并禁选）。 */
@Data
public class SeriesSuggestVO {
    private String name;
    private String reason;
    private boolean existing;
}
