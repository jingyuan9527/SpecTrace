package com.spectrace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.RateLimitedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AI 摘要的公共护栏：解析兜底、缓存过期与限流阈值是两类摘要共用的口径。 */
class AiInsightSupportTest {

    private AiInsightSupport support;

    @BeforeEach
    void setUp() {
        support = new AiInsightSupport(new ObjectMapper());
        // 配置项由 Spring 注入，单测里直接给定缓存时长
        ReflectionTestUtils.setField(support, "cacheHours", 24L);
    }

    @Test
    @DisplayName("合法 JSON 解析为 summary 与 points")
    void parsesJson() {
        AiInsightSupport.Parsed parsed = support.parse("""
                {"summary": "屏幕变大", "points": ["屏幕 6.1→6.3 英寸", "电池 +39mAh"]}
                """);
        assertThat(parsed.summary()).isEqualTo("屏幕变大");
        assertThat(parsed.points()).containsExactly("屏幕 6.1→6.3 英寸", "电池 +39mAh");
    }

    @Test
    @DisplayName("模型返回纯文本时退化为摘要，不抛异常")
    void fallsBackToPlainText() {
        AiInsightSupport.Parsed parsed = support.parse("  这几台机型差异不大。  ");
        assertThat(parsed.summary()).isEqualTo("这几台机型差异不大。");
        assertThat(parsed.points()).isEmpty();
        assertThat(support.parse(null).summary()).isEmpty();
    }

    @Test
    @DisplayName("超长摘要与多余要点被裁剪")
    void truncates() {
        String longSummary = "长".repeat(300);
        AiInsightSupport.Parsed parsed = support.parse(
                "{\"summary\":\"" + longSummary + "\",\"points\":[\"a\",\"b\",\"c\",\"d\"]}");
        assertThat(parsed.summary()).hasSize(200);
        assertThat(parsed.points()).hasSize(3);
    }

    @Test
    @DisplayName("超过缓存时长不再命中")
    void expires() {
        support.store("expired", new AiInsightSupport.Entry("旧", List.of(), LocalDateTime.now().minusHours(25)));
        assertThat(support.cached("expired")).isNull();

        support.store("fresh", new AiInsightSupport.Entry("新", List.of(), LocalDateTime.now()));
        assertThat(support.cached("fresh")).isNotNull();
        assertThat(support.cached("missing")).isNull();
    }

    @Test
    @DisplayName("同来源每分钟第 6 次生成被限流，来源之间互不影响")
    void rateLimits() {
        for (int i = 0; i < 5; i++) {
            support.checkRate("1.2.3.4");
        }
        assertThatThrownBy(() -> support.checkRate("1.2.3.4"))
                .isInstanceOf(RateLimitedException.class)
                .hasMessageContaining("过于频繁");
        support.checkRate("5.6.7.8");
    }

    @Test
    @DisplayName("社媒文案：合法 JSON 解析出标题/正文/要点/话题")
    void parsesSocialCopyJson() {
        AiInsightSupport.ParsedCopy parsed = support.parseCopy("""
                {"title": "小米 6 代电池翻倍", "summary": "从 4610mAh 到 8000mAh。",
                 "points": ["电池 +73%"], "tags": ["#小米", "数码", "#手机"]}
                """);
        assertThat(parsed.title()).isEqualTo("小米 6 代电池翻倍");
        assertThat(parsed.summary()).contains("8000mAh");
        assertThat(parsed.points()).containsExactly("电池 +73%");
        // 裸词自动补 #
        assertThat(parsed.tags()).containsExactly("#小米", "#数码", "#手机");
    }

    @Test
    @DisplayName("社媒文案：纯文本兜底为正文，标题与话题为空")
    void fallsBackSocialCopyToPlainText() {
        AiInsightSupport.ParsedCopy parsed = support.parseCopy("  这条产品线整体在进步。  ");
        assertThat(parsed.title()).isNull();
        assertThat(parsed.summary()).isEqualTo("这条产品线整体在进步。");
        assertThat(parsed.points()).isEmpty();
        assertThat(parsed.tags()).isEmpty();
        assertThat(support.parseCopy(null).summary()).isEmpty();
    }

    @Test
    @DisplayName("社媒文案：标题超长被裁剪，要点上限 3 条")
    void truncatesSocialCopy() {
        AiInsightSupport.ParsedCopy parsed = support.parseCopy(
                "{\"title\":\"" + "长".repeat(80) + "\",\"summary\":\"ok\",\"points\":[\"a\",\"b\",\"c\",\"d\"],\"tags\":[]}");
        assertThat(parsed.title()).hasSize(60);
        assertThat(parsed.points()).hasSize(3);
    }
}
