package com.spectrace.service;

import com.spectrace.common.BizException;
import com.spectrace.entity.Device;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceParamValueMapper;
import com.spectrace.mapper.DeviceSeriesMapper;
import com.spectrace.mapper.DeviceVariantMapper;
import com.spectrace.mapper.FeedbackMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 公开对比的可见性：草稿 / 下架机型不能因为知道 id 就进入对比。
 *
 * <p>列表与联想都按上架口径过滤，对比若不过滤就成了绕过入口读未发布数据的通道。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceCompareVisibilityTest {

    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private DeviceParamValueMapper valueMapper;
    @Mock
    private DeviceVariantMapper variantMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private DeviceSeriesMapper seriesMapper;
    @Mock
    private DeviceViewService deviceViewService;
    @Mock
    private ParamService paramService;
    @Mock
    private OperationLogService logService;
    @Mock
    private FeedbackMapper feedbackMapper;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    @DisplayName("包含下架机型时拒绝整个对比请求，不返回部分数据")
    void rejectsUnpublishedDevices() {
        when(deviceMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(device(1L, 1), device(2L, 0)));

        assertThatThrownBy(() -> deviceService.compare(List.of(1L, 2L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已下架");
    }

    @Test
    @DisplayName("status 为空的旧数据同样按不可见处理")
    void rejectsNullStatus() {
        when(deviceMapper.selectBatchIds(anyList()))
                .thenReturn(List.of(device(1L, 1), device(2L, null)));

        assertThatThrownBy(() -> deviceService.compare(List.of(1L, 2L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已下架");
    }

    private static Device device(Long id, Integer status) {
        Device device = new Device();
        device.setId(id);
        device.setStatus(status);
        return device;
    }
}
