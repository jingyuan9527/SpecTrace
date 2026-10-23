package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spectrace.common.BizException;
import com.spectrace.common.ParamValueParser;
import com.spectrace.dto.*;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.DeviceParamValue;
import com.spectrace.entity.DeviceVariant;
import com.spectrace.entity.ParamDef;
import com.spectrace.entity.ParamGroup;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceParamValueMapper;
import com.spectrace.mapper.DeviceVariantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceParamValueMapper valueMapper;
    private final DeviceVariantMapper variantMapper;
    private final BrandMapper brandMapper;
    private final ParamService paramService;
    private final OperationLogService logService;

    public IPage<Device> page(long current, long size, Long brandId, Integer year, String keyword) {
        var query = Wrappers.<Device>lambdaQuery().orderByDesc(Device::getReleaseDate);
        if (brandId != null) {
            query.eq(Device::getBrandId, brandId);
        }
        if (year != null) {
            LocalDate start = LocalDate.of(year, 1, 1);
            query.ge(Device::getReleaseDate, start).lt(Device::getReleaseDate, start.plusYears(1));
        }
        if (StringUtils.hasText(keyword)) {
            query.like(Device::getName, keyword);
        }
        return deviceMapper.selectPage(new Page<>(current, size), query);
    }

    /**
     * 搜索联想：机型名或品牌名命中即返回，最多 limit 条。
     * 拼音首字母匹配放在前端（pinyin-pro），此接口保证中文关键词与超大机型库也能命中。
     */
    public List<DeviceSuggestVO> suggest(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String kw = keyword.trim();
        int size = Math.min(Math.max(limit, 1), 20);

        List<Long> brandIds = brandMapper.selectList(Wrappers.<Brand>lambdaQuery()
                        .like(Brand::getName, kw)).stream()
                .map(Brand::getId)
                .toList();

        var query = Wrappers.<Device>lambdaQuery()
                .eq(Device::getStatus, 1)
                .and(w -> {
                    w.like(Device::getName, kw);
                    if (!brandIds.isEmpty()) {
                        w.or().in(Device::getBrandId, brandIds);
                    }
                })
                .orderByDesc(Device::getReleaseDate)
                .orderByAsc(Device::getName)
                .last("LIMIT " + size);

        List<Device> devices = deviceMapper.selectList(query);
        if (devices.isEmpty()) {
            return List.of();
        }
        Map<Long, Brand> brandMap = brandMapper.selectBatchIds(
                        devices.stream().map(Device::getBrandId).distinct().toList()).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));

        return devices.stream().map(device -> {
            DeviceSuggestVO vo = new DeviceSuggestVO();
            vo.setId(device.getId());
            vo.setName(device.getName());
            vo.setBrandId(device.getBrandId());
            vo.setThumbnail(device.getThumbnail());
            Brand brand = brandMap.get(device.getBrandId());
            vo.setBrandName(brand == null ? null : brand.getName());
            return vo;
        }).toList();
    }

    public List<Device> hot(int limit) {
        return deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .eq(Device::getStatus, 1)
                .orderByDesc(Device::getReleaseDate)
                .last("LIMIT " + limit));
    }

    /**
     * 机型详情：主表 + 参数值（paramKey -> value）+ 字段级溯源 + 最后更新时间。
     * paramMeta 与 params 并行返回，避免改变既有参数结构导致管理端编辑表单失效。
     */
    public Map<String, Object> detail(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BizException("机型不存在");
        }
        Brand brand = brandMapper.selectById(device.getBrandId());
        List<DeviceParamValue> values = rawParamValues(id);
        Map<Long, ParamDef> defMap = defMap();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("device", device);
        result.put("brand", brand);
        result.put("params", toParamValues(values, defMap));
        result.put("paramMeta", toParamMeta(values, defMap));
        result.put("lastUpdated", lastUpdated(device, values));
        result.put("variants", variantsOf(id));
        return result;
    }

    private Map<Long, ParamDef> defMap() {
        return paramService.listDefs(null, null).stream()
                .collect(Collectors.toMap(ParamDef::getId, Function.identity()));
    }

    private List<DeviceParamValue> rawParamValues(Long deviceId) {
        return valueMapper.selectList(Wrappers.<DeviceParamValue>lambdaQuery()
                .eq(DeviceParamValue::getDeviceId, deviceId));
    }

    private Map<String, String> toParamValues(List<DeviceParamValue> values, Map<Long, ParamDef> defMap) {
        Map<String, String> map = new LinkedHashMap<>();
        for (DeviceParamValue value : values) {
            ParamDef def = defMap.get(value.getParamDefId());
            if (def == null) {
                continue;
            }
            map.putIfAbsent(def.getParamKey(), value.getParamValue() == null ? "" : value.getParamValue());
        }
        return map;
    }

    private Map<String, ParamMetaVO> toParamMeta(List<DeviceParamValue> values, Map<Long, ParamDef> defMap) {
        Map<String, ParamMetaVO> meta = new LinkedHashMap<>();
        for (DeviceParamValue value : values) {
            ParamDef def = defMap.get(value.getParamDefId());
            if (def == null) {
                continue;
            }
            ParamMetaVO vo = new ParamMetaVO();
            vo.setSourceUrl(value.getSourceUrl());
            vo.setConfidence(value.getConfidence());
            vo.setUpdatedAt(value.getUpdatedAt());
            meta.putIfAbsent(def.getParamKey(), vo);
        }
        return meta;
    }

    /** 机型最后更新时间：机型主表与参数值取最大，前端据此展示数据新鲜度。 */
    private LocalDateTime lastUpdated(Device device, List<DeviceParamValue> values) {
        LocalDateTime latest = device.getUpdatedAt();
        for (DeviceParamValue value : values) {
            if (value.getUpdatedAt() != null && (latest == null || value.getUpdatedAt().isAfter(latest))) {
                latest = value.getUpdatedAt();
            }
        }
        return latest;
    }

    /** 机型全部存储版本，按 sortOrder 升序。 */
    public List<DeviceVariantDTO> variantsOf(Long deviceId) {
        return variantMapper.selectList(Wrappers.<DeviceVariant>lambdaQuery()
                        .eq(DeviceVariant::getDeviceId, deviceId)
                        .orderByAsc(DeviceVariant::getSortOrder)
                        .orderByAsc(DeviceVariant::getId)).stream()
                .map(this::toVariantDTO)
                .toList();
    }

    private DeviceVariantDTO toVariantDTO(DeviceVariant variant) {
        DeviceVariantDTO dto = new DeviceVariantDTO();
        dto.setId(variant.getId());
        dto.setStorage(variant.getStorage());
        dto.setRam(variant.getRam());
        dto.setPrice(variant.getPrice());
        dto.setCurrency(variant.getCurrency());
        dto.setPriceType(variant.getPriceType());
        dto.setSortOrder(variant.getSortOrder());
        return dto;
    }

    public Map<String, String> paramValuesOf(Long deviceId) {
        return toParamValues(rawParamValues(deviceId), defMap());
    }

    @Transactional
    public Device save(DeviceSaveRequest req, Long adminId, String adminName) {
        return save(req, adminId, adminName, null);
    }

    /** sourceUrl 非空表示本次写入来自 Agent 抓取，会落到每个参数值上用于溯源。 */
    @Transactional
    public Device save(DeviceSaveRequest req, Long adminId, String adminName, String sourceUrl) {
        Device device = new Device();
        device.setId(req.getId());
        device.setBrandId(req.getBrandId());
        device.setName(req.getName());
        device.setReleaseDate(req.getReleaseDate());
        device.setThumbnail(req.getThumbnail());
        device.setDescription(req.getDescription());
        device.setStatus(req.getStatus() == null ? 1 : req.getStatus());

        boolean isNew = req.getId() == null;
        if (isNew) {
            deviceMapper.insert(device);
        } else {
            deviceMapper.updateById(device);
        }
        saveParamValues(device.getId(), req.getParams(), sourceUrl, req.getParamConfidence());
        saveVariants(device.getId(), req.getVariants());

        logService.record(adminId, adminName, isNew ? "CREATE_DEVICE" : "UPDATE_DEVICE",
                "device", device.getId(), device.getName());
        return device;
    }

    /**
     * 以 paramKey -> value 全量覆盖机型参数值：未提交或值为空白的参数键视为删除。
     * params 为 null（未提供参数）时不改动既有参数，避免仅更新机型主表字段时误删。
     */
    @Transactional
    public void saveParamValues(Long deviceId, Map<String, String> params) {
        saveParamValues(deviceId, params, null, null);
    }

    /**
     * 以 paramKey -> value 全量覆盖机型参数值，并记录来源。
     * 值发生变化时：有 sourceUrl 覆盖为抓取来源，否则清空来源（说明已被人工修正）。
     */
    public void saveParamValues(Long deviceId, Map<String, String> params, String sourceUrl) {
        saveParamValues(deviceId, params, sourceUrl, null);
    }

    /**
     * @param confidence paramKey -> 置信度，null 表示人工录入（写入时清空置信度）
     */
    @Transactional
    public void saveParamValues(Long deviceId, Map<String, String> params, String sourceUrl,
                                Map<String, Double> confidence) {
        if (params == null) {
            return;
        }
        Map<String, ParamDef> defMap = paramService.keyToDefMap();
        Map<Long, String> keyByDefId = defMap.values().stream()
                .collect(Collectors.toMap(ParamDef::getId, ParamDef::getParamKey, (a, b) -> a));

        // 目标参数集合：仅保留已定义且值非空白的键
        Map<Long, String> desired = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            ParamDef def = defMap.get(entry.getKey());
            if (def == null || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            desired.put(def.getId(), entry.getValue().trim());
        }

        List<DeviceParamValue> existing = valueMapper.selectList(Wrappers.<DeviceParamValue>lambdaQuery()
                .eq(DeviceParamValue::getDeviceId, deviceId));

        // 删除不再提交的参数
        List<Long> removeIds = existing.stream()
                .filter(v -> !desired.containsKey(v.getParamDefId()))
                .map(DeviceParamValue::getId)
                .toList();
        if (!removeIds.isEmpty()) {
            valueMapper.deleteByIds(removeIds);
        }

        Map<Long, DeviceParamValue> existingByDef = existing.stream()
                .collect(Collectors.toMap(DeviceParamValue::getParamDefId, Function.identity(), (a, b) -> a));
        for (Map.Entry<Long, String> entry : desired.entrySet()) {
            DeviceParamValue current = existingByDef.get(entry.getKey());
            String paramKey = keyByDefId.get(entry.getKey());
            if (current == null) {
                DeviceParamValue value = new DeviceParamValue();
                value.setDeviceId(deviceId);
                value.setParamDefId(entry.getKey());
                value.setParamValue(entry.getValue());
                value.setSourceUrl(sourceUrl);
                value.setConfidence(confidenceOf(confidence, paramKey));
                valueMapper.insert(value);
            } else if (!entry.getValue().equals(current.getParamValue())) {
                // 用 LambdaUpdateWrapper 显式 set：updateById 会忽略 null，人工修正后的置信度无法清空
                valueMapper.update(null, Wrappers.<DeviceParamValue>lambdaUpdate()
                        .eq(DeviceParamValue::getId, current.getId())
                        .set(DeviceParamValue::getParamValue, entry.getValue())
                        .set(DeviceParamValue::getSourceUrl, sourceUrl)
                        .set(DeviceParamValue::getConfidence, confidenceOf(confidence, paramKey))
                        // strictUpdateFill 仅在字段为空时填充，这里显式刷新更新时间
                        .set(DeviceParamValue::getUpdatedAt, LocalDateTime.now()));
            }
        }
    }

    /** 置信度统一夹取到 [0,1] 并保留两位，匹配 DECIMAL(3,2) 列。 */
    private BigDecimal confidenceOf(Map<String, Double> confidence, String paramKey) {
        if (confidence == null || paramKey == null) {
            return null;
        }
        Double raw = confidence.get(paramKey);
        if (raw == null || raw.isNaN() || raw.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(Math.max(0, Math.min(1, raw))).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 全量覆盖机型的存储版本与价格：variants 为 null 时不改动，传空列表则清空。
     * 以 id 优先、storage+ram 次之匹配既有行，避免编辑后产生重复版本。
     */
    @Transactional
    public void saveVariants(Long deviceId, List<DeviceVariantDTO> variants) {
        if (variants == null) {
            return;
        }
        List<DeviceVariant> existing = variantMapper.selectList(Wrappers.<DeviceVariant>lambdaQuery()
                .eq(DeviceVariant::getDeviceId, deviceId));
        Map<Long, DeviceVariant> existingById = existing.stream()
                .collect(Collectors.toMap(DeviceVariant::getId, Function.identity(), (a, b) -> a));

        List<Long> keepIds = new ArrayList<>();
        int index = 0;
        for (DeviceVariantDTO dto : variants) {
            if (dto == null || !StringUtils.hasText(dto.getStorage())) {
                continue;
            }
            index++;
            String storage = dto.getStorage().trim();
            String ram = dto.getRam() == null ? "" : dto.getRam().trim();

            DeviceVariant target = dto.getId() == null ? null : existingById.get(dto.getId());
            if (target == null) {
                target = existing.stream()
                        .filter(v -> storage.equals(v.getStorage())
                                && ram.equals(v.getRam() == null ? "" : v.getRam()))
                        .findFirst().orElse(null);
            }

            if (target == null) {
                DeviceVariant variant = new DeviceVariant();
                variant.setDeviceId(deviceId);
                variant.setStorage(storage);
                variant.setRam(ram);
                variant.setPrice(dto.getPrice());
                variant.setCurrency(StringUtils.hasText(dto.getCurrency()) ? dto.getCurrency() : "CNY");
                variant.setPriceType(StringUtils.hasText(dto.getPriceType()) ? dto.getPriceType() : "LAUNCH");
                variant.setSortOrder(dto.getSortOrder() == null ? index : dto.getSortOrder());
                variantMapper.insert(variant);
                keepIds.add(variant.getId());
            } else {
                target.setStorage(storage);
                target.setRam(ram);
                target.setPrice(dto.getPrice());
                target.setCurrency(StringUtils.hasText(dto.getCurrency()) ? dto.getCurrency() : "CNY");
                target.setPriceType(StringUtils.hasText(dto.getPriceType()) ? dto.getPriceType() : "LAUNCH");
                target.setSortOrder(dto.getSortOrder() == null ? index : dto.getSortOrder());
                variantMapper.updateById(target);
                keepIds.add(target.getId());
            }
        }

        List<Long> removeIds = existing.stream()
                .filter(v -> !keepIds.contains(v.getId()))
                .map(DeviceVariant::getId)
                .toList();
        if (!removeIds.isEmpty()) {
            variantMapper.deleteByIds(removeIds);
        }
    }

    public void delete(Long id, Long adminId, String adminName) {
        deviceMapper.deleteById(id);
        variantMapper.delete(Wrappers.<DeviceVariant>lambdaQuery().eq(DeviceVariant::getDeviceId, id));
        logService.record(adminId, adminName, "DELETE_DEVICE", "device", id, null);
    }

    /**
     * 多机型横向对比：返回对齐到 device 顺序的参数矩阵，按参数分组归类。
     */
    public CompareResultVO compare(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.size() < 2) {
            throw new BizException("至少选择 2 台机型进行对比");
        }
        List<Device> devices = deviceMapper.selectBatchIds(deviceIds);
        if (devices.size() != deviceIds.size()) {
            throw new BizException("部分机型不存在");
        }
        // 按传入顺序排列
        Map<Long, Device> deviceMap = devices.stream().collect(Collectors.toMap(Device::getId, d -> d));
        List<Device> ordered = deviceIds.stream().map(deviceMap::get).toList();

        Map<Long, Brand> brandMap = brandMapper.selectBatchIds(
                        devices.stream().map(Device::getBrandId).distinct().toList()).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));

        List<CompareDeviceVO> deviceVOs = ordered.stream().map(d -> {
            CompareDeviceVO vo = new CompareDeviceVO();
            vo.setId(d.getId());
            vo.setName(d.getName());
            vo.setBrandId(d.getBrandId());
            vo.setThumbnail(d.getThumbnail());
            vo.setReleaseDate(d.getReleaseDate());
            Brand b = brandMap.get(d.getBrandId());
            vo.setBrandName(b == null ? null : b.getName());
            return vo;
        }).toList();

        // deviceId -> 存储版本列表，并回填最低发布价
        Map<Long, List<DeviceVariantDTO>> variantsByDevice = new LinkedHashMap<>();
        variantMapper.selectList(Wrappers.<DeviceVariant>lambdaQuery()
                        .in(DeviceVariant::getDeviceId, deviceIds)
                        .orderByAsc(DeviceVariant::getSortOrder)
                        .orderByAsc(DeviceVariant::getId))
                .forEach(v -> variantsByDevice
                        .computeIfAbsent(v.getDeviceId(), k -> new ArrayList<>())
                        .add(toVariantDTO(v)));
        for (CompareDeviceVO vo : deviceVOs) {
            List<DeviceVariantDTO> list = variantsByDevice.getOrDefault(vo.getId(), List.of());
            vo.setVariants(list);
            vo.setMinPrice(list.stream()
                    .map(DeviceVariantDTO::getPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(null));
        }

        // deviceId -> paramDefId -> value
        Map<Long, Map<Long, String>> valueMap = new HashMap<>();
        valueMapper.selectList(Wrappers.<DeviceParamValue>lambdaQuery()
                        .in(DeviceParamValue::getDeviceId, deviceIds))
                .forEach(v -> valueMap
                        .computeIfAbsent(v.getDeviceId(), k -> new HashMap<>())
                        .put(v.getParamDefId(), v.getParamValue()));

        List<ParamGroup> groups = paramService.listGroups();
        List<ParamDef> defs = paramService.listDefs(null, 1);

        Map<Long, List<ParamDef>> defsByGroup = defs.stream()
                .collect(Collectors.groupingBy(ParamDef::getGroupId));

        List<CompareGroupVO> groupVOs = new ArrayList<>();
        for (ParamGroup group : groups) {
            List<ParamDef> groupDefs = defsByGroup.get(group.getId());
            if (groupDefs == null || groupDefs.isEmpty()) {
                continue;
            }
            CompareGroupVO groupVO = new CompareGroupVO();
            groupVO.setGroupId(group.getId());
            groupVO.setGroupName(group.getName());
            List<CompareParamVO> paramVOs = new ArrayList<>();
            for (ParamDef def : groupDefs) {
                CompareParamVO pv = new CompareParamVO();
                pv.setParamDefId(def.getId());
                pv.setParamKey(def.getParamKey());
                pv.setParamName(def.getParamName());
                pv.setParamType(def.getParamType());
                pv.setUnit(def.getUnit());
                List<String> values = ordered.stream()
                        .map(d -> {
                            String v = valueMap.getOrDefault(d.getId(), Map.of()).get(def.getId());
                            return v == null ? "" : v;
                        })
                        .toList();
                pv.setValues(values);
                pv.setSame(values.stream().distinct().count() <= 1);
                paramVOs.add(pv);
            }
            groupVO.setParams(paramVOs);
            groupVOs.add(groupVO);
        }

        CompareResultVO result = new CompareResultVO();
        result.setDevices(deviceVOs);
        result.setGroups(groupVOs);
        return result;
    }

    /**
     * 多品牌参数历史：逐品牌取时序数据，返回统一单位与数值/文本判定，供前端画多折线。
     * 单次最多 4 个品牌，避免图表不可读。
     */
    public HistorySeriesVO historySeries(List<Long> brandIds, Long paramDefId) {
        if (brandIds == null || brandIds.isEmpty()) {
            throw new BizException("请至少选择一个品牌");
        }
        if (brandIds.size() > 4) {
            throw new BizException("最多同时对比 4 个品牌");
        }
        ParamDef def = paramService.listDefs(null, null).stream()
                .filter(d -> d.getId().equals(paramDefId))
                .findFirst()
                .orElseThrow(() -> new BizException("参数不存在"));

        List<HistorySeriesItemVO> series = new ArrayList<>();
        List<String> unitConflicts = new ArrayList<>();
        boolean numeric = true;
        for (Long brandId : brandIds.stream().distinct().toList()) {
            Brand brand = brandMapper.selectById(brandId);
            if (brand == null) {
                continue;
            }
            List<HistoryPoint> points = valueMapper.selectHistory(brandId, paramDefId);
            if (points.isEmpty()) {
                continue;
            }
            for (HistoryPoint point : points) {
                String raw = point.getValue();
                // 把「6.1英寸」换算成定义单位下的数值，图表才能跨品牌对标
                var parsed = ParamValueParser.parse(raw, def.getUnit());
                if (parsed.numeric()) {
                    point.setValue(parsed.number().stripTrailingZeros().toPlainString());
                } else {
                    numeric = false;
                }
                if (parsed.conflict()) {
                    unitConflicts.add(point.getDeviceName() + "：" + raw);
                }
            }
            HistorySeriesItemVO item = new HistorySeriesItemVO();
            item.setBrandId(brand.getId());
            item.setBrandName(brand.getName());
            item.setPoints(points);
            series.add(item);
        }

        HistorySeriesVO result = new HistorySeriesVO();
        result.setParamDefId(def.getId());
        result.setParamName(def.getParamName());
        result.setUnit(def.getUnit());
        result.setNumeric(numeric);
        result.setUnitConflicts(unitConflicts);
        result.setSeries(series);
        return result;
    }

    public List<Integer> releaseYears(Long brandId) {
        List<Device> devices = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .select(Device::getReleaseDate)
                .eq(brandId != null, Device::getBrandId, brandId));
        return devices.stream()
                .map(Device::getReleaseDate)
                .filter(Objects::nonNull)
                .map(LocalDate::getYear)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
