package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spectrace.common.BizException;
import com.spectrace.dto.*;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.DeviceParamValue;
import com.spectrace.entity.ParamDef;
import com.spectrace.entity.ParamGroup;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceParamValueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceParamValueMapper valueMapper;
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

    public List<Device> hot(int limit) {
        return deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .eq(Device::getStatus, 1)
                .orderByDesc(Device::getReleaseDate)
                .last("LIMIT " + limit));
    }

    /** 机型详情：主表 + 参数值（paramKey -> value）。 */
    public Map<String, Object> detail(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new BizException("机型不存在");
        }
        Brand brand = brandMapper.selectById(device.getBrandId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("device", device);
        result.put("brand", brand);
        result.put("params", paramValuesOf(id));
        return result;
    }

    public Map<String, String> paramValuesOf(Long deviceId) {
        Map<Long, ParamDef> defMap = paramService.listDefs(null, null).stream()
                .collect(Collectors.toMap(ParamDef::getId, Function.identity()));
        return valueMapper.selectList(Wrappers.<DeviceParamValue>lambdaQuery()
                        .eq(DeviceParamValue::getDeviceId, deviceId)).stream()
                .filter(v -> defMap.containsKey(v.getParamDefId()))
                .collect(Collectors.toMap(
                        v -> defMap.get(v.getParamDefId()).getParamKey(),
                        v -> v.getParamValue() == null ? "" : v.getParamValue(),
                        (a, b) -> a));
    }

    @Transactional
    public Device save(DeviceSaveRequest req, Long adminId, String adminName) {
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
        saveParamValues(device.getId(), req.getParams());

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
        if (params == null) {
            return;
        }
        Map<String, ParamDef> defMap = paramService.keyToDefMap();

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
            if (current == null) {
                DeviceParamValue value = new DeviceParamValue();
                value.setDeviceId(deviceId);
                value.setParamDefId(entry.getKey());
                value.setParamValue(entry.getValue());
                valueMapper.insert(value);
            } else if (!entry.getValue().equals(current.getParamValue())) {
                current.setParamValue(entry.getValue());
                valueMapper.updateById(current);
            }
        }
    }

    public void delete(Long id, Long adminId, String adminName) {
        deviceMapper.deleteById(id);
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

    public List<HistoryPoint> history(Long brandId, Long paramDefId) {
        return valueMapper.selectHistory(brandId, paramDefId);
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
