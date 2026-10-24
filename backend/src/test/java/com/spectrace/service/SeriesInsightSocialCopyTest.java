package com.spectrace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.dto.EvolutionDeviceVO;
import com.spectrace.dto.EvolutionParamVO;
import com.spectrace.dto.EvolutionPointVO;
import com.spectrace.dto.EvolutionVO;
import com.spectrace.dto.SocialCopyVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 社媒文案：数据与站内回顾同源，关键是风格隔离（同一区间小红书稿与公众号稿互不覆盖缓存）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeriesInsightSocialCopyTest {

    @Mock
    private EvolutionService evolutionService;
    @Mock
    private AiChatService aiChatService;
    @Mock
    private OperationLogService logService;

    private SeriesInsightService service;
    private AiInsightSupport insightSupport;

    @BeforeEach
    void setUp() {
        insightSupport = new AiInsightSupport(new ObjectMapper());
        ReflectionTestUtils.setField(insightSupport, "cacheHours", 24L);
        service = new SeriesInsightService(evolutionService, aiChatService, insightSupport);
        ReflectionTestUtils.setField(service, "enabled", true);

        EvolutionVO evolution = new EvolutionVO();
        evolution.setSeriesId(2L);
        evolution.setSeriesName("小米数字系列");
        evolution.setBrandId(2L);
        evolution.setBrandName("小米");
        EvolutionDeviceVO device = new EvolutionDeviceVO();
        device.setId(5L);
        device.setName("小米 14");
        device.setReleaseDate(LocalDate.of(2023, 10, 26));
        evolution.setDevices(List.of(device));
        EvolutionParamVO param = new EvolutionParamVO();
        param.setParamDefId(3L);
        param.setParamKey("screen_size");
        param.setParamName("屏幕尺寸");
        param.setUnit("英寸");
        param.setNumeric(true);
        EvolutionPointVO point = new EvolutionPointVO();
        point.setDeviceId(5L);
        point.setRaw("6.36英寸");
        point.setValue(new BigDecimal("6.36"));
        param.setPoints(List.of(point));
        evolution.setParams(List.of(param));
        when(evolutionService.evolution(anyLong(), any())).thenReturn(evolution);
        when(aiChatService.available()).thenReturn(true);
        // 单点无法比较：trendLines 要求 ≥2 个取值，这里补一个端点外的机型取值不会出现，直接用双点数据
        EvolutionDeviceVO device2 = new EvolutionDeviceVO();
        device2.setId(11L);
        device2.setName("Xiaomi 17");
        device2.setReleaseDate(LocalDate.of(2025, 9, 25));
        EvolutionPointVO point2 = new EvolutionPointVO();
        point2.setDeviceId(11L);
        point2.setRaw("6.3英寸");
        point2.setValue(new BigDecimal("6.3"));
        param.setPoints(List.of(point, point2));
        evolution.setDevices(List.of(device, device2));
    }

    private void chatReturns(String content) {
        lenient().when(aiChatService.complete(anyString(), anyString(), anyInt())).thenReturn(content);
    }

    @Test
    @DisplayName("小红书风格：标题/正文/要点/话题全部映射到 VO")
    void mapsXiaohongshuStyle() {
        chatReturns("""
                {"title": "小米 3 代屏幕进化", "summary": "从 6.36 英寸到 6.3 英寸。",
                 "points": ["尺寸基本持平"], "tags": ["#小米", "数码"]}
                """);

        SocialCopyVO vo = service.socialCopy(2L, null, List.of(3L), "xiaohongshu", "ip", true);

        assertThat(vo.isAvailable()).isTrue();
        assertThat(vo.isFailed()).isFalse();
        assertThat(vo.getStyle()).isEqualTo("xiaohongshu");
        assertThat(vo.getTitle()).isEqualTo("小米 3 代屏幕进化");
        assertThat(vo.getTags()).containsExactly("#小米", "#数码");
        // 非法风格归一化为小红书
        assertThat(service.socialCopy(2L, null, List.of(3L), "unknown", "ip", true).getStyle())
                .isEqualTo("xiaohongshu");
    }

    @Test
    @DisplayName("同一区间不同风格走不同缓存，各自调用一次模型")
    void isolatesCacheByStyle() {
        chatReturns("{\"title\":\"A\",\"summary\":\"正文\",\"points\":[],\"tags\":[]}");
        service.socialCopy(2L, null, List.of(3L), "xiaohongshu", "ip", true);
        service.socialCopy(2L, null, List.of(3L), "wechat", "ip", true);
        // 若缓存未按 style 隔离，第二次调用会命中缓存而不再调模型
        verify(aiChatService, times(2)).complete(anyString(), contains("小米"), anyInt());

        // 同风格重复生成命中缓存，不再调模型
        service.socialCopy(2L, null, List.of(3L), "xiaohongshu", "ip", true);
        verify(aiChatService, times(2)).complete(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("模型异常时 failed=true 且不抛出")
    void degradesOnModelFailure() {
        when(aiChatService.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("模型不可达"));

        SocialCopyVO vo = service.socialCopy(2L, null, List.of(3L), "wechat", "ip", true);

        assertThat(vo.isFailed()).isTrue();
        assertThat(vo.isAvailable()).isTrue();
    }
}
