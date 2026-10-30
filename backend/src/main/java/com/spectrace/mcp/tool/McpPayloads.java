package com.spectrace.mcp.tool;

import com.spectrace.dto.DeviceVariantDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入参到业务 DTO 的转换。
 *
 * <p>机型保存与审核通过都要写版本数据，转换规则放一处，避免两条路径对 priceType 的校验不一致。</p>
 */
public final class McpPayloads {

    private static final Set<String> PRICE_TYPES = Set.of("LAUNCH", "CURRENT");

    private McpPayloads() {
    }

    public static List<DeviceVariantDTO> toVariants(List<Map<String, Object>> raw) {
        if (raw == null) {
            return null;
        }
        return raw.stream().map(item -> {
            McpArgs args = new McpArgs(item);
            DeviceVariantDTO dto = new DeviceVariantDTO();
            dto.setId(args.optLong("id"));
            dto.setStorage(args.requireString("storage"));
            dto.setRam(args.optString("ram"));
            dto.setPrice(args.optDecimal("price"));
            dto.setCurrency(args.optString("currency"));
            String priceType = args.optString("priceType");
            if (priceType != null && !PRICE_TYPES.contains(priceType)) {
                throw new McpArgumentException("variants[].priceType", "priceType 只能是 LAUNCH 或 CURRENT");
            }
            dto.setPriceType(priceType);
            dto.setSortOrder(args.optInt("sortOrder", null));
            return dto;
        }).toList();
    }
}
