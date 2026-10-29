package com.spectrace.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spectrace.common.AiCallFailure;
import com.spectrace.common.BizException;
import com.spectrace.dto.ModelCallMetricsVO;
import com.spectrace.entity.AiCallLog;
import com.spectrace.mapper.AiCallLogMapper;
import com.spectrace.mapper.AiConfigMapper;
import com.spectrace.mapper.AiImageProviderMapper;
import com.spectrace.mapper.AiModelMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 调用统计的分桶与分位口径：趋势点数量、补零、失败归类和成功率都直接影响面板结论。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelCallMetricsServiceTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiCallLog.class);
    }

    @Mock
    private AiCallLogMapper callLogMapper;
    @Mock
    private AiConfigMapper configMapper;
    @Mock
    private AiModelMapper modelMapper;
    @Mock
    private AiImageProviderMapper imageProviderMapper;

    @InjectMocks
    private ModelCallMetricsService service;

    @Test
    @DisplayName("短窗口按分钟补零，分位值包含失败调用")
    void aggregatesMinuteBucketsWithFailureLatency() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T10:00:00");
        LocalDateTime end = LocalDateTime.parse("2020-01-01T12:00:00");
        when(callLogMapper.selectList(any())).thenReturn(List.of(
                row("2020-01-01T10:05:00", 1, 100L, null),
                row("2020-01-01T10:05:30", 0, 5000L, AiCallFailure.TIMEOUT.name()),
                row("2020-01-01T11:30:00", 1, 300L, null)));

        ModelCallMetricsVO vo = service.metrics("CHAT", 1L, 2L, start, end);

        assertThat(vo.getTotal()).isEqualTo(3);
        assertThat(vo.getSuccess()).isEqualTo(2);
        assertThat(vo.getFailed()).isEqualTo(1);
        assertThat(vo.getSuccessRate()).isEqualTo(2.0 / 3);
        assertThat(vo.getAvgMs()).isEqualTo(1800);
        assertThat(vo.getP50Ms()).isEqualTo(300);
        assertThat(vo.getP95Ms()).isEqualTo(5000);
        assertThat(vo.getMaxMs()).isEqualTo(5000);
        // 10:00 到 12:00 共 121 个分钟桶，无调用的 12:00 也补零
        assertThat(vo.getSeries()).hasSize(121);
        assertThat(vo.getSeries().get(0).getTotal()).isZero();
        // 两条调用都发生在 10:05，落到第 6 个分钟桶
        assertThat(vo.getSeries().get(5).getBucket()).isEqualTo("2020-01-01 10:05");
        assertThat(vo.getSeries().get(5).getTotal()).isEqualTo(2);
        assertThat(vo.getSeries().get(5).getFailed()).isEqualTo(1);
        assertThat(vo.getSeries().get(120).getTotal()).isZero();
        assertThat(vo.getFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.getReason()).isEqualTo(AiCallFailure.TIMEOUT.name());
            assertThat(failure.getLabel()).isEqualTo(AiCallFailure.TIMEOUT.label());
            assertThat(failure.getCount()).isEqualTo(1);
            assertThat(failure.getShare()).isEqualTo(1.0);
        });
    }

    @Test
    @DisplayName("24 小时窗口按小时分桶，空数据也返回连续时间轴")
    void usesHourBucketsForOneDayWindow() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T10:00:00");
        LocalDateTime end = LocalDateTime.parse("2020-01-02T10:00:00");
        when(callLogMapper.selectList(any())).thenReturn(List.of());

        ModelCallMetricsVO vo = service.metrics(null, null, null, start, end);

        assertThat(vo.getBucketUnit()).isEqualTo("HOUR");
        assertThat(vo.getSeries()).hasSize(25);
        assertThat(vo.getSuccessRate()).isNull();
        assertThat(vo.getFailures()).isEmpty();
    }

    @Test
    @DisplayName("开始晚于结束、超过 90 天或供应商类型非法直接拒绝")
    void rejectsInvalidRanges() {
        LocalDateTime start = LocalDateTime.parse("2020-01-01T10:00:00");
        LocalDateTime earlier = start.minusHours(1);
        assertThatThrownBy(() -> service.metrics(null, null, null, start, earlier))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开始时间");
        assertThatThrownBy(() -> service.metrics(null, null, null, start.minusDays(91), start))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("90 天");
        assertThatThrownBy(() -> service.metrics("VIDEO", null, null, earlier, start))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("CHAT / IMAGE");
    }

    private AiCallLog row(String createdAt, int success, long latencyMs, String errorType) {
        AiCallLog row = new AiCallLog();
        row.setCreatedAt(LocalDateTime.parse(createdAt));
        row.setSuccess(success);
        row.setLatencyMs(latencyMs);
        row.setErrorType(errorType);
        return row;
    }
}
