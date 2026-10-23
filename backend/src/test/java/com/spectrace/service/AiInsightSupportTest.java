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
}
