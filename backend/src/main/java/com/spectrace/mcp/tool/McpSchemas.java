package com.spectrace.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具入参 JSON Schema 构造器。
 *
 * <p>Agent 只能看到这里生成的 schema，字段说明就是它的唯一文档来源；描述写清楚单位、枚举与默认值，
 * 比在服务端猜参数意图更可靠。</p>
 */
public final class McpSchemas {

    private McpSchemas() {
    }

    /** 有序键值对构造，避免 Map.of 超过 10 项后另写 Map.ofEntries */
    public static Map<String, Object> props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    public static McpSchema.JsonSchema object(Map<String, Object> properties, String... required) {
        // additionalProperties 放开：Agent 客户端常在入参里带自己的说明字段，严格拒绝只会让调用无谓失败
        return new McpSchema.JsonSchema("object", properties, List.of(required), true, null, null);
    }

    public static Map<String, Object> string(String description) {
        return props("type", "string", "description", description);
    }

    public static Map<String, Object> integer(String description) {
        return props("type", "integer", "description", description);
    }

    public static Map<String, Object> integer(String description, int minimum, Integer maximum) {
        Map<String, Object> schema = integer(description);
        schema.put("minimum", minimum);
        if (maximum != null) {
            schema.put("maximum", maximum);
        }
        return schema;
    }

    public static Map<String, Object> number(String description) {
        return props("type", "number", "description", description);
    }

    public static Map<String, Object> enumString(String description, String... values) {
        Map<String, Object> schema = string(description);
        schema.put("enum", List.of(values));
        return schema;
    }

    public static Map<String, Object> integerEnum(String description, Integer... values) {
        Map<String, Object> schema = integer(description);
        schema.put("enum", List.of(values));
        return schema;
    }

    public static Map<String, Object> arrayOf(String description, Map<String, Object> items) {
        return props("type", "array", "description", description, "items", items);
    }

    public static Map<String, Object> longArray(String description) {
        return arrayOf(description, integer("元素 id"));
    }

    /** 键固定为字符串的映射，如 paramKey -> 参数值 */
    public static Map<String, Object> stringMap(String description) {
        return props("type", "object", "description", description,
                "additionalProperties", props("type", "string"));
    }

    /** 嵌套对象用 Map 表示，才能作为数组元素或属性值嵌入父级 schema */
    public static Map<String, Object> objectMap(String description, Map<String, Object> properties,
                                                String... required) {
        return props("type", "object", "description", description,
                "properties", properties,
                "required", List.of(required),
                "additionalProperties", true);
    }

    /** 存储版本数组的元素结构，与 DeviceVariantDTO 对齐 */
    public static Map<String, Object> variantArray(String description) {
        return arrayOf(description, objectMap("存储版本", props(
                "id", integer("已有版本 id，更新时可用于匹配；新增时留空"),
                "storage", string("存储容量，如 256GB"),
                "ram", string("运行内存，如 12GB；无区分时留空"),
                "price", number("价格；币种由 currency 决定"),
                "currency", string("币种，默认 CNY"),
                "priceType", enumString("价格类型", "LAUNCH", "CURRENT"),
                "sortOrder", integer("展示顺序，越小越靠前")
        ), "storage"));
    }
}
