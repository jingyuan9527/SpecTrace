package com.spectrace.service;

import com.spectrace.entity.AgentSubscription;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.mapper.AgentSubscriptionMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceParamValueMapper;
import com.spectrace.mapper.DeviceSeriesMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spectrace.entity.AgentSubscription;
import com.spectrace.entity.Device;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系列删/并时的订阅归属：订阅只挂在系列详情下，系列没了就再也点不到，必须随系列一起处理。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceSeriesSubscriptionCleanupTest {

    @Mock
    private DeviceSeriesMapper seriesMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private DeviceParamValueMapper valueMapper;
    @Mock
    private AgentSubscriptionMapper subscriptionMapper;
    @Mock
    private AiChatService aiChatService;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private DeviceSeriesService seriesService;

    /** Lambda 条件构造器要按实体解析列名，没有表信息会在拼条件时直接抛异常。 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Device.class);
        TableInfoHelper.initTableInfo(assistant, DeviceSeries.class);
        TableInfoHelper.initTableInfo(assistant, AgentSubscription.class);
    }

    @Test
    @DisplayName("删除系列时一并清理其订阅")
    void deleteRemovesSubscription() {
        when(seriesMapper.selectById(1L)).thenReturn(series(1L, "iPhone 数字系列"));
        when(deviceMapper.selectCount(any())).thenReturn(0L);

        seriesService.delete(1L);

        verify(seriesMapper).deleteById(1L);
        verify(subscriptionMapper).delete(any());
    }

    @Test
    @DisplayName("系列下仍有在册机型时拒绝删除，且不动订阅")
    void deleteRejectedKeepsSubscription() {
        when(seriesMapper.selectById(1L)).thenReturn(series(1L, "iPhone 数字系列"));
        when(deviceMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> seriesService.delete(1L)).hasMessageContaining("还有 2 台机型");
        verify(seriesMapper, never()).deleteById(1L);
        verify(subscriptionMapper, never()).delete(any());
    }

    @Test
    @DisplayName("合并系列时把源订阅迁到目标（目标没有订阅）")
    void mergeMovesSubscriptionWhenTargetHasNone() {
        when(seriesMapper.selectById(1L)).thenReturn(series(1L, "iPhone 17 系列"));
        when(seriesMapper.selectById(2L)).thenReturn(series(2L, "iPhone 18 系列"));
        when(deviceMapper.update(any(), any())).thenReturn(3);
        when(subscriptionMapper.selectOne(any())).thenReturn(subscription(7L, 1L), null);

        seriesService.merge(1L, 2L);

        ArgumentCaptor<AgentSubscription> captor = ArgumentCaptor.forClass(AgentSubscription.class);
        verify(subscriptionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(7L);
        assertThat(captor.getValue().getSeriesId()).isEqualTo(2L);
        verify(subscriptionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("合并时目标已有订阅则以目标为准，丢弃源订阅")
    void mergeDropsSourceSubscriptionWhenTargetHasOne() {
        when(seriesMapper.selectById(1L)).thenReturn(series(1L, "iPhone 17 系列"));
        when(seriesMapper.selectById(2L)).thenReturn(series(2L, "iPhone 18 系列"));
        when(deviceMapper.update(any(), any())).thenReturn(3);
        when(subscriptionMapper.selectOne(any())).thenReturn(subscription(7L, 1L), subscription(9L, 2L));

        seriesService.merge(1L, 2L);

        verify(subscriptionMapper).deleteById(7L);
        verify(subscriptionMapper, never()).updateById(any(AgentSubscription.class));
    }

    private DeviceSeries series(Long id, String name) {
        DeviceSeries series = new DeviceSeries();
        series.setId(id);
        series.setName(name);
        series.setBrandId(1L);
        series.setStatus(1);
        return series;
    }

    private AgentSubscription subscription(Long id, Long seriesId) {
        AgentSubscription subscription = new AgentSubscription();
        subscription.setId(id);
        subscription.setSeriesId(seriesId);
        subscription.setIntervalMonths(3);
        subscription.setEnabled(1);
        return subscription;
    }
}
