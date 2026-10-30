package com.spectrace.mcp.tool;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具入参的强类型读取。
 *
 * <p>所有失败都带字段名与期望类型：Agent 拿到 "参数 brandId 必须是正整数" 能自己改，
 * 拿到 ClassCastException 只能整轮重来。</p>
 */
public final class McpArgs {

    private final Map<String, Object> raw;

    public McpArgs(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    public boolean has(String field) {
        Object value = raw.get(field);
        return value != null && !(value instanceof String text && text.isBlank());
    }

    public String requireString(String field) {
        String value = optString(field);
        if (value == null || value.isBlank()) {
            throw new McpArgumentException(field, "缺少必填参数 " + field);
        }
        return value.trim();
    }

    public String optString(String field) {
        Object value = raw.get(field);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** 布尔开关：兼容 true/false 与 "true"/"false"/1/0，缺省取 defaultValue。 */
    public Boolean optBoolean(String field, Boolean defaultValue) {
        Object value = raw.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        throw new McpArgumentException(field, "参数 " + field + " 必须是布尔值，实际值：" + text);
    }

    public Long requireLong(String field) {
        Long value = optLong(field);
        if (value == null) {
            throw new McpArgumentException(field, "缺少必填参数 " + field);
        }
        return value;
    }

    public Long optLong(String field) {
        Object value = raw.get(field);
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        if (value instanceof Number number) {
            return requireIntegral(field, number.longValue(), number);
        }
        String text = String.valueOf(value).trim();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new McpArgumentException(field, "参数 " + field + " 必须是整数，实际值：" + text);
        }
    }

    public Integer optInt(String field, Integer defaultValue) {
        Long value = optLong(field);
        if (value == null) {
            return defaultValue;
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new McpArgumentException(field, "参数 " + field + " 超出整型范围");
        }
        return value.intValue();
    }

    public Integer requireInt(String field) {
        Long value = requireLong(field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new McpArgumentException(field, "参数 " + field + " 超出整型范围");
        }
        return value.intValue();
    }

    public BigDecimal optDecimal(String field) {
        Object value = raw.get(field);
        if (value == null || (value instanceof String text && text.isBlank())) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new McpArgumentException(field, "参数 " + field + " 必须是数字");
        }
    }

    public LocalDate optDate(String field) {
        String text = optString(field);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new McpArgumentException(field, "参数 " + field + " 必须是 yyyy-MM-dd 格式，实际值：" + text);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> optStringMap(String field) {
        Object value = raw.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new McpArgumentException(field, "参数 " + field + " 必须是对象（键值对）");
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (key == null || item == null) {
                return;
            }
            result.put(String.valueOf(key), String.valueOf(item));
        });
        return result;
    }

    public List<Long> optLongList(String field) {
        Object value = raw.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> list)) {
            throw new McpArgumentException(field, "参数 " + field + " 必须是数组");
        }
        List<Long> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            try {
                result.add(Long.parseLong(String.valueOf(item).trim()));
            } catch (NumberFormatException e) {
                throw new McpArgumentException(field, "参数 " + field + " 只能包含整数 id");
            }
        }
        return result;
    }

    public List<Long> requireLongList(String field) {
        List<Long> value = optLongList(field);
        if (value == null || value.isEmpty()) {
            throw new McpArgumentException(field, "参数 " + field + " 不能为空");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> optObjectList(String field) {
        Object value = raw.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> list)) {
            throw new McpArgumentException(field, "参数 " + field + " 必须是数组");
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            } else {
                throw new McpArgumentException(field, "参数 " + field + " 的元素必须是对象");
            }
        }
        return result;
    }

    private Long requireIntegral(String field, long parsed, Object original) {
        if (original instanceof Double || original instanceof Float || original instanceof BigDecimal) {
            BigDecimal decimal = new BigDecimal(String.valueOf(original));
            if (decimal.stripTrailingZeros().scale() > 0) {
                throw new McpArgumentException(field, "参数 " + field + " 必须是整数，实际值：" + original);
            }
            try {
                return decimal.longValueExact();
            } catch (ArithmeticException e) {
                throw new McpArgumentException(field, "参数 " + field + " 超出整型范围：" + original);
            }
        }
        return parsed;
    }
}
