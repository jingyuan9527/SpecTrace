package com.spectrace.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 单位解析直接决定对比、历史与导入的正确性，这里按分支逐条锁住行为。 */
class ParamValueParserTest {

    @Test
    @DisplayName("同量纲按换算系数统一到参数定义单位")
    void convertsWithinSameDimension() {
        ParamValueParser.Parsed inches = ParamValueParser.parse("6.1英寸", "cm");
        assertThat(inches.numeric()).isTrue();
        assertThat(inches.conflict()).isFalse();
        assertThat(inches.number()).isEqualByComparingTo(new BigDecimal("15.494"));

        // 裸数值默认按参数定义的单位理解，不产生单位文本
        ParamValueParser.Parsed bare = ParamValueParser.parse("3600", "mAh");
        assertThat(bare.number()).isEqualByComparingTo("3600");
        assertThat(bare.unit()).isNull();
        assertThat(bare.conflict()).isFalse();
    }

    @Test
    @DisplayName("寸 / 英寸 / in 视为同一量纲")
    void treatsAliasesAsSameDimension() {
        assertThat(ParamValueParser.parse("6.1寸", "英寸").number()).isEqualByComparingTo("6.1");
        assertThat(ParamValueParser.parse("6.1in", "英寸").number()).isEqualByComparingTo("6.1");
        assertThat(ParamValueParser.parse("6.1英寸", "英寸").conflict()).isFalse();
    }

    @Test
    @DisplayName("单字母 g 按参数定义消歧：克与 GB")
    void disambiguatesSingleLetterG() {
        ParamValueParser.Parsed grams = ParamValueParser.parse("188g", "g");
        assertThat(grams.conflict()).isFalse();
        assertThat(grams.number()).isEqualByComparingTo("188");

        ParamValueParser.Parsed storage = ParamValueParser.parse("128g", "GB");
        assertThat(storage.conflict()).isFalse();
        assertThat(storage.number()).isEqualByComparingTo("128");
    }

    @Test
    @DisplayName("量纲不一致与未知单位标记冲突，仍保留原数值")
    void flagsDimensionMismatchAndUnknownUnit() {
        ParamValueParser.Parsed mismatch = ParamValueParser.parse("3600mAh", "g");
        assertThat(mismatch.conflict()).isTrue();
        assertThat(mismatch.number()).isEqualByComparingTo("3600");

        assertThat(ParamValueParser.parse("6.1foo", "cm").conflict()).isTrue();
    }

    @Test
    @DisplayName("参数未定义单位时不做冲突判定")
    void skipsConflictWhenExpectedUnitMissing() {
        ParamValueParser.Parsed parsed = ParamValueParser.parse("8核", null);
        assertThat(parsed.numeric()).isTrue();
        assertThat(parsed.conflict()).isFalse();
        assertThat(parsed.unit()).isEqualTo("核");
    }

    @Test
    @DisplayName("非数值与空值退化为文本")
    void fallsBackToText() {
        assertThat(ParamValueParser.parse("OLED 屏幕", "cm").numeric()).isFalse();
        assertThat(ParamValueParser.parse("   ", "cm").numeric()).isFalse();
        assertThat(ParamValueParser.parse(null, "cm").numeric()).isFalse();
    }

    @Test
    @DisplayName("频率按量级换算")
    void convertsFrequency() {
        assertThat(ParamValueParser.parse("2.4GHz", "MHz").number()).isEqualByComparingTo("2400");
    }
}
