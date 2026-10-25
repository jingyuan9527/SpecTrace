package com.spectrace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.PendingStatus;
import com.spectrace.dto.DataHealthMissingDeviceVO;
import com.spectrace.dto.DataHealthParamCoverageVO;
import com.spectrace.dto.DataHealthVO;
import com.spectrace.dto.DeviceParamCount;
import com.spectrace.dto.ParamCoverage;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.entity.ParamDef;
import com.spectrace.entity.ParamGroup;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceParamValueMapper;
import com.spectrace.mapper.DeviceSeriesMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据健康度：全库带参率、参数覆盖排行、缺参机型排行与运营计数。
 * 全部为只读聚合，结果不缓存——管理端低频访问，SQLite 直查足够快。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataHealthService {

    /** 缺参机型排行条数上限 */
    private static final int MISSING_DEVICE_LIMIT = 50;

    private final DeviceMapper deviceMapper;
    private final DeviceParamValueMapper valueMapper;
    private final DeviceSeriesMapper seriesMapper;
    private final BrandMapper brandMapper;
    private final AgentPendingDataMapper pendingMapper;
    private final ParamService paramService;
    private final DeviceViewService deviceViewService;
    private final FeedbackService feedbackService;

    /**
     * @param brandId  缺参机型排行的品牌筛选，空为全部
     * @param seriesId 缺参机型排行的系列筛选，0 约定为「未归类」，空为全部
     */
    public DataHealthVO overview(Long brandId, Long seriesId) {
        DataHealthVO vo = new DataHealthVO();
        vo.setTotalDevices(deviceMapper.selectCount(null));
        vo.setPublishedDevices(deviceMapper.selectCount(Wrappers.<Device>lambdaQuery().eq(Device::getStatus, 1)));
        vo.setDevicesWithParams(valueMapper.countDevicesWithParams());
        vo.setCoverageRate(vo.getTotalDevices() == 0 ? 0
                : BigDecimal.valueOf(vo.getDevicesWithParams())
                        .divide(BigDecimal.valueOf(vo.getTotalDevices()), 4, RoundingMode.HALF_UP)
                        .doubleValue());
        vo.setApprovedLast30d(pendingMapper.selectCount(Wrappers.<AgentPendingData>lambdaQuery()
                .eq(AgentPendingData::getStatus, PendingStatus.APPROVED.name())
                .ge(AgentPendingData::getCreatedAt, LocalDateTime.now().minusDays(30))));
        vo.setOpenFeedback(feedbackService.countOpen());

        List<ParamDef> defs = paramService.listDefs(null, 1);
        vo.setEnabledParamDefs(defs.size());
        vo.setParamCoverage(paramCoverage(defs));

        List<Device> devices = deviceMapper.selectList(deviceQuery(brandId, seriesId));
        vo.setMissingDevices(missingDevices(devices, defs));
        return vo;
    }

    /** 参数覆盖排行：升序（最缺的在前），同为 0 的按定义顺序稳定排列。 */
    private List<DataHealthParamCoverageVO> paramCoverage(List<ParamDef> defs) {
        Map<Long, Long> counts = valueMapper.selectParamCoverage().stream()
                .collect(Collectors.toMap(ParamCoverage::getParamDefId, ParamCoverage::getDeviceCount, (a, b) -> a));
        Map<Long, String> groupNames = paramService.listGroups().stream()
                .collect(Collectors.toMap(ParamGroup::getId, ParamGroup::getName));
        return defs.stream()
                .map(def -> {
                    DataHealthParamCoverageVO item = new DataHealthParamCoverageVO();
                    item.setParamDefId(def.getId());
                    item.setParamKey(def.getParamKey());
                    item.setParamName(def.getParamName());
                    item.setGroupName(groupNames.get(def.getGroupId()));
                    item.setDeviceCount(counts.getOrDefault(def.getId(), 0L));
                    return item;
                })
                .sorted(Comparator.comparingLong(DataHealthParamCoverageVO::getDeviceCount)
                        .thenComparingInt(item -> orderOf(item.getParamDefId(), defs)))
                .toList();
    }

    private int orderOf(Long paramDefId, List<ParamDef> defs) {
        return defs.stream().filter(d -> d.getId().equals(paramDefId)).findFirst().map(ParamDef::getSortOrder).orElse(0);
    }

    private LambdaQueryWrapper<Device> deviceQuery(Long brandId, Long seriesId) {
        var query = Wrappers.<Device>lambdaQuery().orderByDesc(Device::getReleaseDate);
        if (brandId != null) {
            query.eq(Device::getBrandId, brandId);
        }
        if (seriesId != null) {
            // 与机型管理同口径：seriesId=0 约定为「未归类」
            if (seriesId == 0L) {
                query.isNull(Device::getSeriesId);
            } else {
                query.eq(Device::getSeriesId, seriesId);
            }
        }
        return query;
    }

    /** 缺参机型排行：缺参的机型里，谁浏览量高谁先补；无浏览数据时按发布时间靠前的优先。 */
    private List<DataHealthMissingDeviceVO> missingDevices(List<Device> devices, List<ParamDef> defs) {
        if (devices.isEmpty() || defs.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> haveParams = valueMapper.selectParamCountByDevice().stream()
                .collect(Collectors.toMap(DeviceParamCount::getDeviceId, DeviceParamCount::getParamCount, (a, b) -> a));
        Map<Long, Long> pv = deviceViewService.pvOf(devices.stream().map(Device::getId).toList(), DeviceViewService.HOT_WINDOW_DAYS);
        Map<Long, String> brandNames = brandMapper.selectBatchIds(
                        devices.stream().map(Device::getBrandId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Brand::getId, Brand::getName));
        Map<Long, String> seriesNames = seriesMapper.selectBatchIds(
                        devices.stream().map(Device::getSeriesId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(DeviceSeries::getId, DeviceSeries::getName));

        return devices.stream()
                .filter(d -> haveParams.getOrDefault(d.getId(), 0L) < defs.size())
                .map(d -> {
                    DataHealthMissingDeviceVO item = new DataHealthMissingDeviceVO();
                    item.setId(d.getId());
                    item.setName(d.getName());
                    item.setBrandId(d.getBrandId());
                    item.setBrandName(d.getBrandId() == null ? null : brandNames.get(d.getBrandId()));
                    item.setSeriesId(d.getSeriesId());
                    item.setSeriesName(d.getSeriesId() == null ? null : seriesNames.get(d.getSeriesId()));
                    item.setStatus(d.getStatus());
                    item.setReleaseDate(d.getReleaseDate());
                    item.setPv30d(pv.get(d.getId()));
                    item.setHaveParams(haveParams.getOrDefault(d.getId(), 0L));
                    item.setTotalParams(defs.size());
                    return item;
                })
                .sorted(Comparator
                        .comparing((DataHealthMissingDeviceVO item) -> item.getPv30d() == null ? 0 : item.getPv30d()).reversed()
                        .thenComparing(item -> item.getReleaseDate() == null ? LocalDate.MAX : item.getReleaseDate()))
                .limit(MISSING_DEVICE_LIMIT)
                .toList();
    }
}
