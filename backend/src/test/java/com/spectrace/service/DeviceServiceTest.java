package com.spectrace.service;

import com.spectrace.common.NotFoundException;
import com.spectrace.entity.Device;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
