package com.spectrace.service;

import com.spectrace.mapper.AiImagePromptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提示词渲染：变量替换与未定义变量清理。
 *
 * <p>美术模型会照抄模板里的字面量，{@code {{xxx}}} 残留会直接印在成品图上，
 * 所以「未定义变量必须被清掉」是硬约束，不是美化行为。</p>
 */
@ExtendWith(MockitoExtension.class)
class AiImagePromptRenderTest {

    @Mock
    private AiImagePromptMapper promptMapper;

    private AiImagePromptService service;

    @BeforeEach
    void setUp() {
        service = new AiImagePromptService(promptMapper);
    }

    @Test
    @DisplayName("已知变量按数据替换")
    void rendersKnownVariables() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("series", "iPhone 数字系列");
        variables.put("count", "2");
        variables.put("devices", "- iPhone 17（2025 年 9 月）\n- iPhone 16（2024 年 9 月）");

        String result = service.render("{{series}} 共 {{count}} 代：\n{{devices}}", variables);

        assertThat(result).isEqualTo("iPhone 数字系列 共 2 代：\n- iPhone 17（2025 年 9 月）\n- iPhone 16（2024 年 9 月）");
    }

    @Test
    @DisplayName("未定义变量被清空，不留花括号字面量")
    void removesUnknownPlaceholders() {
        String result = service.render("海报：{{series}}{{unknownVar}}，{{ 带空格 }}结束", Map.of("series", "Mate 系列"));

        assertThat(result).isEqualTo("海报：Mate 系列，结束");
    }

    @Test
    @DisplayName("变量值为空时替换为空串，不输出 null")
    void rendersNullValueAsEmpty() {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("brand", null);

        assertThat(service.render("品牌：{{brand}}。", variables)).isEqualTo("品牌：。");
    }
}
