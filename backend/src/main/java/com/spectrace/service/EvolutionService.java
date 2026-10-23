package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.BizException;
import com.spectrace.common.ParamValueParser;
import com.spectrace.dto.EvolutionDeviceVO;
import com.spectrace.dto.EvolutionParamVO;
import com.spectrace.dto.EvolutionPointVO;
import com.spectrace.dto.EvolutionVO;
import com.spectrace.dto.SeriesVO;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 世代回顾聚合：把一个系列看作「代」的序列，逐一取出所选参数并计算涨跌幅。
 *
 * <p>价格是机型变体维度而非参数值，{@code launch_price} 统一取变体最低价，
 * 与机型详情页展示的起售价保持一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvolutionService {

    /** 允许同时查看的参数上限，超过后图表不可读 */
    private static final int MAX_PARAMS = 8;
    private static final String LAUNCH_PRICE_KEY = "launch_price";

    private final DeviceSeriesService seriesService;
    private final DeviceMapper deviceMapper;
    private final BrandMapper brandMapper;
    private final DeviceParamValueMapper valueMapper;
    private final DeviceVariantMapper variantMapper;
    private final ParamService paramService;

    /** 系列列表：带机型数与起止年份，供筛选下拉直接展示规模。 */
    public List<SeriesVO> seriesList(Long brandId) {
        return seriesService.listOverview(brandId, 1);
    }

    /**
     * @param paramDefIds 参数定义 ID，顺序即返回顺序；空表示只要机型序列
     */
    public EvolutionVO evolution(Long seriesId, List<Long> paramDefIds) {
        DeviceSeries series = seriesService.get(seriesId);
        if (series == null || series.getStatus() == null || series.getStatus() != 1) {
            throw new BizException("系列不存在或已停用：" + seriesId);
        }

        List<Long> defIds = paramDefIds == null ? List.of() : paramDefIds.stream().distinct().toList();
        if (defIds.size() > MAX_PARAMS) {
            throw new BizException("最多同时查看 " + MAX_PARAMS + " 个参数");
        }

        Brand brand = brandMapper.selectById(series.getBrandId());
        List<Device> devices = devicesOf(seriesId);
        List<Long> deviceIds = devices.stream().map(Device::getId).toList();
        Map<Long, BigDecimal> lowestPrices = lowestPrices(deviceIds);

        EvolutionVO result = new EvolutionVO();
        result.setSeriesId(series.getId());
        result.setSeriesName(series.getName());
        result.setBrandId(series.getBrandId());
        result.setBrandName(brand == null ? null : brand.getName());
        result.setDevices(withPrices(devices, lowestPrices));

        if (devices.isEmpty() || defIds.isEmpty()) {
            result.setParams(List.of());
            return result;
        }

        Map<Long, ParamDef> defMap = paramService.listDefs(null, 1).stream()
                .collect(Collectors.toMap(ParamDef::getId, Function.identity(), (a, b) -> a));
        Map<Long, Map<Long, String>> values = paramValuesOf(deviceIds, defIds);

        List<EvolutionParamVO> params = new ArrayList<>();
        for (Long defId : defIds) {
            ParamDef def = defMap.get(defId);
            if (def == null) {
                log.debug("世代回顾跳过不存在或已停用的参数: seriesId={} paramDefId={}", seriesId, defId);
                continue;
            }
            params.add(LAUNCH_PRICE_KEY.equals(def.getParamKey())
                    ? priceParam(def, devices, lowestPrices)
                    : paramTrend(def, devices, values));
        }
        result.setParams(params);
        log.debug("世代回顾: seriesId={} 机型 {} 台，参数 {} 个", seriesId, devices.size(), params.size());
        return result;
    }

    private List<Device> devicesOf(Long seriesId) {
        List<Device> devices = new ArrayList<>(deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .eq(Device::getSeriesId, seriesId)));
        devices.sort(Comparator.comparing(Device::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Device::getId));
        return devices;
    }

    private List<EvolutionDeviceVO> withPrices(List<Device> devices, Map<Long, BigDecimal> prices) {
        List<EvolutionDeviceVO> result = new ArrayList<>(devices.size());
        for (Device device : devices) {
            result.add(toDeviceVO(device, prices));
        }
        return result;
    }

    private EvolutionDeviceVO toDeviceVO(Device device, Map<Long, BigDecimal> prices) {
        EvolutionDeviceVO vo = new EvolutionDeviceVO();
        vo.setId(device.getId());
        vo.setName(device.getName());
        vo.setThumbnail(device.getThumbnail());
        vo.setReleaseDate(device.getReleaseDate());
        vo.setLaunchPrice(prices.get(device.getId()));
        return vo;
    }

    /** deviceId -> 最低变体价格 */
    private Map<Long, BigDecimal> lowestPrices(List<Long> deviceIds) {
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> prices = new HashMap<>();
        variantMapper.selectList(Wrappers.<DeviceVariant>lambdaQuery()
                        .select(DeviceVariant::getDeviceId, DeviceVariant::getPrice)
                        .in(DeviceVariant::getDeviceId, deviceIds))
                .forEach(variant -> {
                    if (variant.getPrice() == null) {
                        return;
                    }
                    prices.merge(variant.getDeviceId(), variant.getPrice(),
                            (current, candidate) -> current.compareTo(candidate) <= 0 ? current : candidate);
                });
        return prices;
    }

    /** deviceId -> paramDefId -> 原始值 */
    private Map<Long, Map<Long, String>> paramValuesOf(List<Long> deviceIds, List<Long> defIds) {
        Map<Long, Map<Long, String>> values = new HashMap<>();
        valueMapper.selectList(Wrappers.<DeviceParamValue>lambdaQuery()
                        .in(DeviceParamValue::getDeviceId, deviceIds)
                        .in(DeviceParamValue::getParamDefId, defIds))
                .forEach(value -> values
                        .computeIfAbsent(value.getDeviceId(), key -> new HashMap<>())
                        .put(value.getParamDefId(), value.getParamValue()));
        return values;
    }

    /** 起售价走势：直接取变体最低价，不读参数值表。 */
    private EvolutionParamVO priceParam(ParamDef def, List<Device> devices, Map<Long, BigDecimal> prices) {
        List<EvolutionPointVO> points = new ArrayList<>(devices.size());
        for (Device device : devices) {
            BigDecimal price = prices.get(device.getId());
            EvolutionPointVO point = new EvolutionPointVO();
            point.setDeviceId(device.getId());
            point.setRaw(price == null ? null : price.stripTrailingZeros().toPlainString());
            point.setValue(price);
            points.add(point);
        }
        boolean hasPrice = points.stream().anyMatch(point -> point.getValue() != null);
        // 全系列都没有价格时按「无数据参数」返回空点列，前端不必渲染空折线
        EvolutionParamVO vo = baseParam(def, hasPrice ? points : List.of());
        if (!hasPrice) {
            vo.setMissingCount(devices.size());
        }
        vo.setNumeric(hasPrice);
        return vo;
    }

    private EvolutionParamVO paramTrend(ParamDef def, List<Device> devices, Map<Long, Map<Long, String>> values) {
        List<EvolutionPointVO> points = new ArrayList<>(devices.size());
        for (Device device : devices) {
            String raw = values.getOrDefault(device.getId(), Map.of()).get(def.getId());
            EvolutionPointVO point = new EvolutionPointVO();
            point.setDeviceId(device.getId());
            point.setRaw(StringUtils.hasText(raw) ? raw.trim() : null);
            if (point.getRaw() != null) {
                ParamValueParser.Parsed parsed = ParamValueParser.parse(point.getRaw(), def.getUnit());
                point.setValue(parsed.numeric() ? parsed.number() : null);
                point.setConflict(parsed.conflict());
            }
            points.add(point);
        }

        List<EvolutionPointVO> present = points.stream()
                .filter(point -> point.getRaw() != null)
                .toList();
        boolean numeric = !present.isEmpty() && present.stream().allMatch(point -> point.getValue() != null);

        // 全系列都没有该参数值时返回空点列，缺失计数仍按整段世代给出
        EvolutionParamVO vo = baseParam(def, present.isEmpty() ? List.of() : points);
        if (present.isEmpty()) {
            vo.setMissingCount(devices.size());
        }
        vo.setNumeric(numeric);
        return vo;
    }

    /**
     * 公共部分：缺失计数。
     *
     * <p>涨跌幅与趋势刻意不在这里算：世代区间由前端裁剪，按全系列算出的值与折线对不上，
     * 统一交给前端按可见区间计算。</p>
     */
    private EvolutionParamVO baseParam(ParamDef def, List<EvolutionPointVO> points) {
        EvolutionParamVO vo = new EvolutionParamVO();
        vo.setParamDefId(def.getId());
        vo.setParamKey(def.getParamKey());
        vo.setParamName(def.getParamName());
        vo.setUnit(def.getUnit());
        vo.setPoints(points);
        vo.setMissingCount((int) points.stream().filter(point -> point.getRaw() == null).count());
        return vo;
    }
}
