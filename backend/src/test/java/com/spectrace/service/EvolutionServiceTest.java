package com.spectrace.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.spectrace.common.BizException;
import com.spectrace.dto.EvolutionDeviceVO;
import com.spectrace.dto.EvolutionParamVO;
import com.spectrace.dto.EvolutionVO;
import com.spectrace.dto.GenerationMatrixVO;
import com.spectrace.dto.GenerationParamVO;
import com.spectrace.dto.GenerationSeriesVO;
import com.spectrace.dto.SeriesOverviewResult;
import com.spectrace.dto.SeriesOverviewVO;
import com.spectrace.dto.SeriesTrendPointVO;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.DeviceParamValue;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.entity.DeviceVariant;
import com.spectrace.entity.ParamDef;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceParamValueMapper;
import com.spectrace.mapper.DeviceVariantMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 世代回顾聚合：排序、缺失点与起售价口径直接决定图表与文案的可信度。
 * 涨跌幅不在后端计算（见 T32），这里只锁定点位与聚合结果。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvolutionServiceTest {

    /**
     * Wrappers.lambdaQuery 依赖实体元数据，脱离 Spring 容器时需要显式注册，
     * 否则会在 Entity::getId 处抛 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Device.class);
        TableInfoHelper.initTableInfo(assistant, DeviceVariant.class);
        TableInfoHelper.initTableInfo(assistant, DeviceParamValue.class);
    }

    @Mock
    private DeviceSeriesService seriesService;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private BrandMapper brandMapper;
    @Mock
    private DeviceParamValueMapper valueMapper;
    @Mock
    private DeviceVariantMapper variantMapper;
    @Mock
    private ParamService paramService;

    private EvolutionService service;

    @BeforeEach
    void setUp() {
        service = new EvolutionService(seriesService, deviceMapper, brandMapper, valueMapper, variantMapper, paramService);

        DeviceSeries series = new DeviceSeries();
        series.setId(1L);
        series.setBrandId(1L);
        series.setName("iPhone 数字系列");
        series.setStatus(1);
        when(seriesService.get(1L)).thenReturn(series);
        when(seriesService.list(isNull(), eq(1))).thenReturn(List.of(series));

        Brand brand = new Brand();
        brand.setId(1L);
        brand.setName("Apple");
        when(brandMapper.selectById(1L)).thenReturn(brand);
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(brand));

        // 故意乱序并混入发布时间为空的机型，验证排序与空值兜底
        when(deviceMapper.selectList(any())).thenReturn(List.of(
                device(7L, "iPhone 17", LocalDate.of(2025, 9, 19)),
                device(99L, "iPhone SE", null),
                device(4L, "iPhone 16", LocalDate.of(2024, 9, 20))));

        when(variantMapper.selectList(any())).thenReturn(List.of(
                variant(7L, "6999"), variant(7L, "5999"), variant(4L, "5999")));

        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(4L, 3L, "6.1英寸"), value(7L, 3L, "6.3英寸")));

        when(paramService.listDefs(isNull(), eq(1))).thenReturn(List.of(
                paramDef(3L, "screen_size", "屏幕尺寸", "英寸"),
                paramDef(2L, "launch_price", "首发价格", "元")));
    }

    @Test
    @DisplayName("机型按发布时间升序，空值排末尾")
    void sortsByReleaseDateWithNullLast() {
        EvolutionVO evolution = service.evolution(1L, List.of());

        assertThat(evolution.getDevices()).extracting(EvolutionDeviceVO::getName)
                .containsExactly("iPhone 16", "iPhone 17", "iPhone SE");
    }

    @Test
    @DisplayName("起售价取变体最低价，无价格时为 null")
    void usesLowestVariantPrice() {
        EvolutionVO evolution = service.evolution(1L, List.of());

        assertThat(evolution.getDevices()).extracting(EvolutionDeviceVO::getLaunchPrice)
                .containsExactly(new BigDecimal("5999"), new BigDecimal("5999"), null);
    }

    @Test
    @DisplayName("参数点位与缺失计数按机型序列对齐")
    void alignsPointsAndMissingCount() {
        EvolutionVO evolution = service.evolution(1L, List.of(3L));

        EvolutionParamVO screen = evolution.getParams().get(0);
        assertThat(screen.getParamKey()).isEqualTo("screen_size");
        assertThat(screen.isNumeric()).isTrue();
        assertThat(screen.getMissingCount()).isEqualTo(1);
        assertThat(screen.getPoints()).hasSize(3);
        assertThat(screen.getPoints().get(0).getValue()).isEqualByComparingTo("6.1");
        assertThat(screen.getPoints().get(1).getValue()).isEqualByComparingTo("6.3");
        assertThat(screen.getPoints().get(2).getRaw()).isNull();
    }

    @Test
    @DisplayName("起售价参数直接取变体最低价，不读参数值表")
    void buildsPriceParamFromVariants() {
        EvolutionVO evolution = service.evolution(1L, List.of(2L));

        EvolutionParamVO price = evolution.getParams().get(0);
        assertThat(price.getParamKey()).isEqualTo("launch_price");
        assertThat(price.getPoints()).extracting(point -> point.getValue())
                .containsExactly(new BigDecimal("5999"), new BigDecimal("5999"), null);
        assertThat(price.getMissingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("参数个数超过上限直接拒绝")
    void rejectsTooManyParams() {
        List<Long> tooMany = LongStream.rangeClosed(1, 9).boxed().toList();

        assertThatThrownBy(() -> service.evolution(1L, tooMany))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("最多同时查看 8 个参数");
    }

    @Test
    @DisplayName("总览：规模年份取成员极值，趋势跳过无日期与无值机型")
    void buildsOverviewWithTrends() {
        when(deviceMapper.selectCount(any())).thenReturn(2L);

        SeriesOverviewResult overview = service.overview(null);

        assertThat(overview.getUnassignedCount()).isEqualTo(2L);
        assertThat(overview.getItems()).hasSize(1);
        SeriesOverviewVO item = overview.getItems().get(0);
        assertThat(item.getDeviceCount()).isEqualTo(3);
        assertThat(item.getStartYear()).isEqualTo(2024);
        assertThat(item.getEndYear()).isEqualTo(2025);
        // 最新一代按发布时间取末位（空日期排末尾）
        assertThat(item.getLatest().getName()).isEqualTo("iPhone SE");
        // 无日期的 SE 不进趋势，屏幕尺寸按参数值换算
        assertThat(item.getScreenTrend()).extracting(SeriesTrendPointVO::getYear)
                .containsExactly(2024, 2025);
        assertThat(item.getScreenTrend()).extracting(SeriesTrendPointVO::getValue)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("6.1"), new BigDecimal("6.3"));
        // 起售价直接取变体最低价
        assertThat(item.getPriceTrend()).extracting(SeriesTrendPointVO::getValue)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("5999"), new BigDecimal("5999"));
    }

    @Test
    @DisplayName("总览：无系列时返回空列表且仍带未归类计数")
    void returnsEmptyOverviewWithUnassignedCount() {
        when(seriesService.list(isNull(), eq(1))).thenReturn(List.of());
        when(deviceMapper.selectCount(any())).thenReturn(5L);

        SeriesOverviewResult overview = service.overview(null);

        assertThat(overview.getItems()).isEmpty();
        assertThat(overview.getUnassignedCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("同代矩阵：同年多款取发布最晚，未归类忽略，无新机系列占位")
    void buildsGenerationMatrix() {
        DeviceSeries digital = new DeviceSeries();
        digital.setId(1L);
        digital.setBrandId(1L);
        digital.setName("iPhone 数字系列");
        digital.setStatus(1);
        DeviceSeries pro = new DeviceSeries();
        pro.setId(3L);
        pro.setBrandId(1L);
        pro.setName("iPhone Pro 系列");
        pro.setStatus(1);
        DeviceSeries air = new DeviceSeries();
        air.setId(4L);
        air.setBrandId(1L);
        air.setName("iPhone Air 系列");
        air.setStatus(1);
        when(seriesService.list(1L, 1)).thenReturn(List.of(digital, pro, air));

        Device unassigned = device(12L, "无系列机", LocalDate.of(2025, 6, 1));
        unassigned.setSeriesId(null);
        Device proDevice = device(11L, "iPhone 17 Pro", LocalDate.of(2025, 9, 19));
        proDevice.setSeriesId(3L);
        when(deviceMapper.selectList(any())).thenReturn(List.of(
                device(8L, "iPhone 17e", LocalDate.of(2025, 2, 19)),
                device(7L, "iPhone 17", LocalDate.of(2025, 9, 19)),
                proDevice,
                unassigned));
        when(variantMapper.selectList(any())).thenReturn(List.of(
                variant(7L, "6999"), variant(7L, "5999"), variant(11L, "8999")));
        when(valueMapper.selectList(any())).thenReturn(List.of(
                value(7L, 3L, "6.3英寸"), value(11L, 3L, "6.3英寸")));

        GenerationMatrixVO matrix = service.generationMatrix(1L, 2025, List.of(3L, 2L));

        assertThat(matrix.getYear()).isEqualTo(2025);
        assertThat(matrix.getItems()).extracting(GenerationSeriesVO::getSeriesName)
                .containsExactly("iPhone 数字系列", "iPhone Pro 系列", "iPhone Air 系列");
        // 同年两款取发布更晚的 iPhone 17，17e 被淘汰；未归类机型不参与
        assertThat(matrix.getItems().get(0).getDevice().getName()).isEqualTo("iPhone 17");
        assertThat(matrix.getItems().get(1).getDevice().getName()).isEqualTo("iPhone 17 Pro");
        // 当年无新机的系列仍占位，前端呈现「该年无新机」而不是悄悄少一列
        assertThat(matrix.getItems().get(2).getDevice()).isNull();

        List<GenerationParamVO> digitalParams = matrix.getItems().get(0).getParams();
        assertThat(digitalParams).hasSize(2);
        assertThat(digitalParams.get(0).getParamName()).isEqualTo("屏幕尺寸");
        assertThat(digitalParams.get(0).getValue()).isEqualByComparingTo("6.3");
        // launch_price 特判：取变体最低价而非参数值表
        assertThat(digitalParams.get(1).getParamName()).isEqualTo("首发价格");
        assertThat(digitalParams.get(1).getValue()).isEqualByComparingTo("5999");
        assertThat(matrix.getItems().get(1).getParams().get(1).getValue()).isEqualByComparingTo("8999");
    }

    private Device device(Long id, String name, LocalDate releaseDate) {
        Device device = new Device();
        device.setId(id);
        device.setBrandId(1L);
        device.setSeriesId(1L);
        device.setName(name);
        device.setReleaseDate(releaseDate);
        return device;
    }

    private DeviceVariant variant(Long deviceId, String price) {
        DeviceVariant variant = new DeviceVariant();
        variant.setDeviceId(deviceId);
        variant.setPrice(new BigDecimal(price));
        return variant;
    }

    private DeviceParamValue value(Long deviceId, Long paramDefId, String raw) {
        DeviceParamValue value = new DeviceParamValue();
        value.setDeviceId(deviceId);
        value.setParamDefId(paramDefId);
        value.setParamValue(raw);
        return value;
    }

    private ParamDef paramDef(Long id, String key, String name, String unit) {
        ParamDef def = new ParamDef();
        def.setId(id);
        def.setParamKey(key);
        def.setParamName(name);
        def.setUnit(unit);
        return def;
    }
}
