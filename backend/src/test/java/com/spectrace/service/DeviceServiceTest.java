package com.spectrace.service;

import com.spectrace.common.NotFoundException;
import com.spectrace.dto.DeviceVariantDTO;
import com.spectrace.entity.Device;
import com.spectrace.entity.DeviceVariant;
import com.spectrace.mapper.DeviceVariantMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 详情页「N 人待核实」的聚合行转换。
 *
 * <p>selectMaps 的 COUNT 类型随驱动变化，转错一处前端就匹配不到参数行，
 * 提示会静默消失——这种失败没有报错，只能靠测试锁住。</p>
 */
class DeviceServiceTest {

    @Test
    @DisplayName("按 paramKey 归并，兼容 Integer / Long / BigDecimal 三种计数类型")
    void toFeedbackCountsAcceptsNumericVariants() {
        Map<String, Integer> counts = DeviceService.toFeedbackCounts(List.of(
                row("screen_size", 2),
                row("battery", 3L),
                row("weight", new BigDecimal("4"))));

        assertThat(counts)
                .containsEntry("screen_size", 2)
                .containsEntry("battery", 3)
                .containsEntry("weight", 4);
    }

    @Test
    @DisplayName("空 key 与非数字计数一律跳过，不让脏数据污染参数行")
    void toFeedbackCountsSkipsDirtyRows() {
        Map<String, Integer> counts = DeviceService.toFeedbackCounts(List.of(
                row(null, 1),
                row("  ", 2),
                row("screen_size", "2"),
                row("battery", 5)));

        assertThat(counts).containsExactly(Map.entry("battery", 5));
    }

    @Test
    @DisplayName("无反馈时返回空表，详情接口不多出空键")
    void toFeedbackCountsIsEmptyWithoutRows() {
        assertThat(DeviceService.toFeedbackCounts(List.of())).isEmpty();
    }

    @Test
    @DisplayName("公开详情：草稿与下架一律按不存在处理")
    void requirePublishedRejectsUnpublished() {
        DeviceService service = new DeviceService(null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.requirePublished(device(0)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.requirePublished(device(null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("公开详情：已上架机型正常放行")
    void requirePublishedAcceptsPublished() {
        DeviceService service = new DeviceService(null, null, null, null, null, null, null, null, null);

        assertThatCode(() -> service.requirePublished(device(1))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("版本重复：按 storage+ram 去重后只插一条，后出现者覆盖价格")
    void saveVariantsDedupesDuplicateStorageRam() {
        DeviceVariantMapper variantMapper = mock(DeviceVariantMapper.class);
        when(variantMapper.selectList(any())).thenReturn(List.of());
        when(variantMapper.insert(any(DeviceVariant.class))).thenAnswer(invocation -> {
            ((DeviceVariant) invocation.getArgument(0)).setId(1L);
            return 1;
        });

        List<DeviceVariantDTO> variants = new ArrayList<>();
        variants.add(variant(12, "256GB", 5999));
        variants.add(variant(12, "256GB", 6499));
        variants.add(variant(16, "512GB", 6999));

        new DeviceService(null, null, variantMapper, null, null, null, null, null, null)
                .saveVariants(10L, variants);

        ArgumentCaptor<DeviceVariant> captor = ArgumentCaptor.forClass(DeviceVariant.class);
        verify(variantMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DeviceVariant::getStorage)
                .containsExactly("256GB", "512GB");
        assertThat(captor.getAllValues().get(0).getPrice()).isEqualByComparingTo("6499");
    }

    @Test
    @DisplayName("版本已存在：复用既有行更新，不再插入新行")
    void saveVariantsReusesExistingRowBySpec() {
        DeviceVariantMapper variantMapper = mock(DeviceVariantMapper.class);
        DeviceVariant existing = new DeviceVariant();
        existing.setId(5L);
        existing.setDeviceId(10L);
        existing.setStorage("256GB");
        existing.setRam("12GB");
        when(variantMapper.selectList(any())).thenReturn(List.of(existing));

        new DeviceService(null, null, variantMapper, null, null, null, null, null, null)
                .saveVariants(10L, List.of(variant(12, "256GB", 6499)));

        verify(variantMapper, never()).insert(any(DeviceVariant.class));
        verify(variantMapper).updateById(any(DeviceVariant.class));
    }

    private static DeviceVariantDTO variant(int ramGb, String storage, int price) {
        DeviceVariantDTO dto = new DeviceVariantDTO();
        dto.setRam(ramGb + "GB");
        dto.setStorage(storage);
        dto.setPrice(BigDecimal.valueOf(price));
        return dto;
    }

    private static Device device(Integer status) {
        Device device = new Device();
        device.setId(1L);
        device.setStatus(status);
        return device;
    }

    /** Map.of 不接受 null value，脏数据行只能手工拼。 */
    private static Map<String, Object> row(String key, Object count) {
        Map<String, Object> row = new HashMap<>();
        row.put("param_key", key);
        row.put("cnt", count);
        return row;
    }
}
