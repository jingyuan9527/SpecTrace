package com.spectrace.common;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 采集结果里的发布日期解析。
 *
 * <p>Agent 输出的日期格式不可控（同一次抓取里可能混杂 `2025-09`、`2025年9月`），
 * 解析失败必须降级为空值继续入库——一个字段格式不对不该拖垮整条数据。</p>
 */
@Slf4j
public final class CollectedDate {

    private CollectedDate() {
    }

    public static LocalDate parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            log.warn("采集结果的发布日期无法解析，按空值处理: {}", value);
            return null;
        }
    }
}
