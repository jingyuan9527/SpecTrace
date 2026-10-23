package com.spectrace.common;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参数值解析：把 {@code 6.1英寸} 这类「数值 + 单位」文本拆开，并与参数定义的单位比对。
 *
 * <p>同一量纲（长度 / 存储 / 电池容量 / 重量 / 频率 / 功率）可按换算系数统一到参数定义的单位；
 * 量纲不一致或单位无法识别时标记为冲突，交由前端提示，不静默改写数值。</p>
 */
public final class ParamValueParser {

    /** 数值 + 可选单位；单位部分允许空格、中英文与符号 */
    private static final Pattern NUMERIC = Pattern.compile("^([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))\\s*(.*)$");
    private static final MathContext PRECISION = new MathContext(10);

    /** 单位定义：量纲 + 换算到量纲基准单位的系数 */
    private record UnitDef(String dimension, double factor) {
    }

    /**
     * @param numeric  是否以数值开头
     * @param number   换算到 {@code expectedUnit} 后的数值；无单位时即原数值
     * @param unit     值实际携带的单位（原样，便于提示）
     * @param conflict 与参数定义的单位量纲不一致或单位无法识别
     */
    public record Parsed(boolean numeric, BigDecimal number, String unit, boolean conflict) {
        static final Parsed TEXT = new Parsed(false, null, null, false);
    }

    /** 一个别名可能对应多个量纲（如 g = 克 / GB），按参数定义的量纲消歧 */
    private static final Map<String, List<UnitDef>> UNITS = new HashMap<>();

    static {
        // 长度：基准厘米
        define(new UnitDef("length", 2.54), "英寸", "寸", "吋", "inch", "in", "\"", "″");
        define(new UnitDef("length", 1), "厘米", "cm");
        define(new UnitDef("length", 0.1), "毫米", "mm");
        // 电池容量：基准 mAh
        define(new UnitDef("battery", 1), "mah", "毫安时", "毫安");
        define(new UnitDef("battery", 1000), "ah", "安时");
        // 存储：基准 MB
        define(new UnitDef("storage", 1), "mb", "兆字节");
        define(new UnitDef("storage", 1024), "gb", "吉字节");
        define(new UnitDef("storage", 1048576), "tb", "太字节");
        // 重量：基准克
        define(new UnitDef("weight", 1), "克");
        define(new UnitDef("weight", 1000), "kg", "千克");
        // 频率：基准 Hz
        define(new UnitDef("frequency", 1), "hz", "赫兹", "赫");
        define(new UnitDef("frequency", 1_000), "khz", "千赫兹");
        define(new UnitDef("frequency", 1_000_000), "mhz", "兆赫兹");
        define(new UnitDef("frequency", 1_000_000_000), "ghz", "吉赫兹");
        // 功率：基准 W
        define(new UnitDef("power", 1), "w", "瓦", "瓦特");
        define(new UnitDef("power", 1_000), "kw", "千瓦");
        // 倍率
        define(new UnitDef("ratio", 1), "倍", "x", "×");
        // 单字母 g 同时是「克」和「GB」的简写，按参数定义的量纲消歧
        define(new UnitDef("weight", 1), "g");
        define(new UnitDef("storage", 1024), "g");
    }

    private ParamValueParser() {
    }

    private static void define(UnitDef def, String... aliases) {
        for (String alias : aliases) {
            UNITS.computeIfAbsent(alias, key -> new java.util.ArrayList<>()).add(def);
        }
    }

    /**
     * @param raw          原始值
     * @param expectedUnit 参数定义的单位，可为空
     */
    public static Parsed parse(String raw, String expectedUnit) {
        if (raw == null || raw.isBlank()) {
            return Parsed.TEXT;
        }
        Matcher matcher = NUMERIC.matcher(raw.trim());
        if (!matcher.matches()) {
            return Parsed.TEXT;
        }

        BigDecimal number = new BigDecimal(matcher.group(1));
        String unitText = normalize(matcher.group(2));
        UnitDef expectedDef = lookup(normalize(expectedUnit), null);

        if (unitText.isEmpty()) {
            // 裸数值默认按参数定义的单位理解
            return new Parsed(true, number, null, false);
        }
        UnitDef valueDef = lookup(unitText, expectedDef == null ? null : expectedDef.dimension());
        if (expectedDef == null) {
            // 参数未定义单位，无法判定冲突
            return new Parsed(true, number, matcher.group(2).trim(), false);
        }
        if (valueDef == null) {
            return new Parsed(true, number, matcher.group(2).trim(), true);
        }
        if (!valueDef.dimension().equals(expectedDef.dimension())) {
            return new Parsed(true, number, matcher.group(2).trim(), true);
        }
        double converted = number.doubleValue() * valueDef.factor() / expectedDef.factor();
        return new Parsed(true, BigDecimal.valueOf(converted).round(PRECISION), matcher.group(2).trim(), false);
    }

    /** 数值是否可直接参与比较（与单位无关）。 */
    public static boolean isNumeric(String raw) {
        return raw != null && NUMERIC.matcher(raw.trim()).matches();
    }

    private static UnitDef lookup(String unit, String preferredDimension) {
        List<UnitDef> candidates = UNITS.get(unit);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (preferredDimension == null) {
            return candidates.get(0);
        }
        // 单字母 g 等歧义单位：按参数定义量纲找匹配项
        return candidates.stream()
                .filter(def -> def.dimension().equals(preferredDimension))
                .findFirst()
                .orElse(candidates.get(0));
    }

    /** 归一化：去空格、全角转半角、小写，便于别名匹配。 */
    private static String normalize(String unit) {
        if (unit == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(unit.length());
        for (char c : unit.trim().toCharArray()) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c >= '！' && c <= '～') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
