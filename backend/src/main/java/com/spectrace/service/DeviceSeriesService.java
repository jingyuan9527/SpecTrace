package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.config.CacheConfig;
import com.spectrace.dto.SeriesSuggestVO;
import com.spectrace.dto.SeriesVO;
import com.spectrace.dto.UnassignedDeviceVO;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceSeriesMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产品线（系列）维护。
 *
 * <p>系列是机型的可选归类：有机型在册时不允许直接删除，需先合并或迁移，避免机型被静默解绑。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceSeriesService {

    private final DeviceSeriesMapper seriesMapper;
    private final DeviceMapper deviceMapper;
    private final BrandMapper brandMapper;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public DeviceSeries get(Long id) {
        return id == null ? null : seriesMapper.selectById(id);
    }

    public List<DeviceSeries> list(Long brandId, Integer status) {
        var query = Wrappers.<DeviceSeries>lambdaQuery()
                .orderByAsc(DeviceSeries::getSortOrder)
                .orderByAsc(DeviceSeries::getId);
        if (brandId != null) {
            query.eq(DeviceSeries::getBrandId, brandId);
        }
        if (status != null) {
            query.eq(DeviceSeries::getStatus, status);
        }
        return seriesMapper.selectList(query);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SERIES_LIST, allEntries = true)
    public DeviceSeries save(DeviceSeries series) {
        if (!StringUtils.hasText(series.getName())) {
            throw new BizException("系列名称不能为空");
        }
        if (series.getBrandId() == null) {
            throw new BizException("请选择所属品牌");
        }
        Brand brand = brandMapper.selectById(series.getBrandId());
        if (brand == null) {
            throw new BizException("品牌不存在：" + series.getBrandId());
        }
        String name = series.getName().trim();
        if (existsName(series.getBrandId(), name, series.getId())) {
            throw new BizException("品牌「" + brand.getName() + "」下已存在系列：" + name);
        }
        series.setName(name);
        if (series.getStatus() == null) {
            series.setStatus(1);
        }
        if (series.getSortOrder() == null) {
            series.setSortOrder(0);
        }

        if (series.getId() == null) {
            seriesMapper.insert(series);
        } else {
            DeviceSeries existing = seriesMapper.selectById(series.getId());
            if (existing == null) {
                throw new BizException("系列不存在");
            }
            // 品牌是系列的身份：允许改品牌会让已归类机型挂到别的品牌下，且前台按品牌筛选时凭空消失
            if (!Objects.equals(existing.getBrandId(), series.getBrandId())) {
                throw new BizException("系列所属品牌不可修改，如需调整请新建系列后把机型重新归类");
            }
            seriesMapper.updateById(series);
        }
        return series;
    }

    /** 删除系列：有机型在册时拒绝，引导先合并或迁移，避免机型被静默解绑。 */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SERIES_LIST, allEntries = true)
    public void delete(Long id) {
        if (seriesMapper.selectById(id) == null) {
            throw new BizException("系列不存在");
        }
        Long attached = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery().eq(Device::getSeriesId, id));
        if (attached != null && attached > 0) {
            throw new BizException("系列下还有 " + attached + " 台机型，请先合并到其它系列或改为未归类");
        }
        seriesMapper.deleteById(id);
        log.info("删除系列: id={}", id);
    }

    /** 系列概览：带品牌名、机型数与起止年份，后台管理与前台筛选共用。 */
    public List<SeriesVO> listOverview(Long brandId, Integer status) {
        List<DeviceSeries> series = list(brandId, status);
        if (series.isEmpty()) {
            return List.of();
        }
        List<Long> seriesIds = series.stream().map(DeviceSeries::getId).toList();
        Map<Long, List<Device>> devicesBySeries = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                        .select(Device::getId, Device::getSeriesId, Device::getReleaseDate)
                        .in(Device::getSeriesId, seriesIds)).stream()
                .collect(Collectors.groupingBy(Device::getSeriesId));

        Map<Long, Brand> brandMap = brandMapper.selectBatchIds(
                        series.stream().map(DeviceSeries::getBrandId).distinct().toList()).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity(), (a, b) -> a));

        List<SeriesVO> result = new ArrayList<>();
        for (DeviceSeries item : series) {
            List<Device> devices = devicesBySeries.getOrDefault(item.getId(), List.of());
            List<Integer> years = devices.stream()
                    .map(Device::getReleaseDate)
                    .filter(Objects::nonNull)
                    .map(LocalDate::getYear)
                    .sorted()
                    .toList();

            SeriesVO vo = new SeriesVO();
            vo.setId(item.getId());
            vo.setBrandId(item.getBrandId());
            Brand brand = brandMap.get(item.getBrandId());
            vo.setBrandName(brand == null ? null : brand.getName());
            vo.setName(item.getName());
            vo.setSortOrder(item.getSortOrder());
            vo.setStatus(item.getStatus());
            vo.setDeviceCount(devices.size());
            vo.setStartYear(years.isEmpty() ? null : years.get(0));
            vo.setEndYear(years.isEmpty() ? null : years.get(years.size() - 1));
            result.add(vo);
        }
        return result;
    }

    /** 未归类机型：后台逐个选择系列即时归类。 */
    public List<UnassignedDeviceVO> unassigned(Long brandId) {
        var query = Wrappers.<Device>lambdaQuery()
                .isNull(Device::getSeriesId)
                .orderByDesc(Device::getReleaseDate);
        if (brandId != null) {
            query.eq(Device::getBrandId, brandId);
        }
        List<Device> devices = deviceMapper.selectList(query);
        if (devices.isEmpty()) {
            return List.of();
        }
        Map<Long, Brand> brandMap = brandMapper.selectBatchIds(
                        devices.stream().map(Device::getBrandId).distinct().toList()).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity(), (a, b) -> a));

        return devices.stream().map(device -> {
            UnassignedDeviceVO vo = new UnassignedDeviceVO();
            vo.setId(device.getId());
            vo.setName(device.getName());
            vo.setBrandId(device.getBrandId());
            Brand brand = brandMap.get(device.getBrandId());
            vo.setBrandName(brand == null ? null : brand.getName());
            vo.setReleaseDate(device.getReleaseDate());
            vo.setThumbnail(device.getThumbnail());
            return vo;
        }).toList();
    }

    /** 归类：seriesId 为空表示取消归类；系列必须与机型同品牌。 */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SERIES_LIST, allEntries = true)
    public int assign(List<Long> deviceIds, Long seriesId) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new BizException("请选择要归类的机型");
        }
        DeviceSeries series = seriesId == null ? null : seriesMapper.selectById(seriesId);
        if (seriesId != null && series == null) {
            throw new BizException("系列不存在：" + seriesId);
        }
        List<Device> devices = deviceMapper.selectBatchIds(deviceIds);
        if (devices.isEmpty()) {
            throw new BizException("机型不存在");
        }
        if (series != null) {
            for (Device device : devices) {
                if (!series.getBrandId().equals(device.getBrandId())) {
                    throw new BizException("机型「" + device.getName() + "」不属于系列所在品牌");
                }
            }
        }
        int updated = deviceMapper.update(null, Wrappers.<Device>lambdaUpdate()
                .in(Device::getId, deviceIds)
                .set(Device::getSeriesId, seriesId));
        log.info("机型归类: seriesId={} 影响 {} 台，机型 {}", seriesId, updated, deviceIds);
        return updated;
    }

    /** 合并系列：机型迁到目标系列后删除源系列，两个系列必须同品牌。 */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.SERIES_LIST, allEntries = true)
    public int merge(Long sourceId, Long targetId) {
        if (targetId == null || Objects.equals(sourceId, targetId)) {
            throw new BizException("请选择不同的目标系列");
        }
        DeviceSeries source = seriesMapper.selectById(sourceId);
        DeviceSeries target = seriesMapper.selectById(targetId);
        if (source == null) {
            throw new BizException("源系列不存在");
        }
        if (target == null) {
            throw new BizException("目标系列不存在");
        }
        if (!source.getBrandId().equals(target.getBrandId())) {
            throw new BizException("只能合并同一品牌下的系列");
        }
        int moved = deviceMapper.update(null, Wrappers.<Device>lambdaUpdate()
                .eq(Device::getSeriesId, sourceId)
                .set(Device::getSeriesId, targetId));
        seriesMapper.deleteById(sourceId);
        log.info("合并系列: {} -> {}，迁移机型 {} 台", source.getName(), target.getName(), moved);
        return moved;
    }

    /**
     * 导入时按「品牌 + 系列名」匹配，不存在则创建。
     *
     * @param cache 调用方在同批次内复用的缓存，key 为 {@link #key(Long, String)}
     */
    @Transactional
    public DeviceSeries resolveOrCreate(Long brandId, String name, Map<String, DeviceSeries> cache) {
        String trimmed = name == null ? "" : name.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        String cacheKey = key(brandId, trimmed);
        DeviceSeries cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        DeviceSeries existing = seriesMapper.selectOne(Wrappers.<DeviceSeries>lambdaQuery()
                .eq(DeviceSeries::getBrandId, brandId)
                .eq(DeviceSeries::getName, trimmed)
                .last("LIMIT 1"));
        if (existing != null) {
            cache.put(cacheKey, existing);
            return existing;
        }

        DeviceSeries created = new DeviceSeries();
        created.setBrandId(brandId);
        created.setName(trimmed);
        created.setStatus(1);
        created.setSortOrder(nextSortOrder(brandId));
        seriesMapper.insert(created);
        cache.put(cacheKey, created);
        log.info("导入时新建系列: brandId={} name={} id={}", brandId, trimmed, created.getId());
        return created;
    }

    /** 导入预览用：一次性取出现有系列，key 与 {@link #key(Long, String)} 一致。 */
    public Map<String, DeviceSeries> nameIndex() {
        Map<String, DeviceSeries> index = new HashMap<>();
        for (DeviceSeries series : list(null, null)) {
            index.putIfAbsent(key(series.getBrandId(), series.getName()), series);
        }
        return index;
    }

    public static String key(Long brandId, String name) {
        return brandId + "|" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * AI 推荐某品牌的产品线划分：单轮对话生成候选系列名，已存在的标注 existing 供前端禁选。
     * AI 不可用或返回异常时抛业务异常，由前端提示降级为手动新建。
     */
    public List<SeriesSuggestVO> suggestByAi(Long brandId) {
        Brand brand = brandMapper.selectById(brandId);
        if (brand == null) {
            throw new BizException("品牌不存在：" + brandId);
        }
        List<String> existingNames = list(brandId, null).stream()
                .map(DeviceSeries::getName)
                .toList();

        String systemPrompt = """
                你是手机产品线专家。根据品牌名称，推荐该品牌手机产品线的系列划分方案（如官方常用产品线命名）。

                要求：
                1. 只输出一个 JSON 对象，格式：{"series":[{"name":"系列名","reason":"一句话说明定位"}]}
                2. 推荐 4~8 个系列；名称用品牌官方或社区通用叫法（中文品牌用中文，国际品牌用英文），不要自造词。
                3. 覆盖该品牌真实存在的产品线定位（旗舰/中端/入门/电竞/折叠/影像等），宁缺毋滥。
                """;
        String userPrompt = "品牌：" + brand.getName()
                + (existingNames.isEmpty() ? "" : "。以下系列名已存在，不要重复推荐：" + String.join("、", existingNames));

        String content = aiChatService.complete(systemPrompt, userPrompt, 800);
        List<SeriesSuggestVO> suggestions = parseSuggestions(content);
        for (SeriesSuggestVO item : suggestions) {
            item.setExisting(existingNames.contains(item.getName()));
        }
        log.info("AI 推荐系列: brand={} 生成 {} 个候选（已存在 {}）",
                brand.getName(), suggestions.size(), suggestions.stream().filter(SeriesSuggestVO::isExisting).count());
        return suggestions;
    }

    /** 容错解析模型输出：提取 JSON 对象里的 series 数组，空结果按异常处理提示重试。 */
    private List<SeriesSuggestVO> parseSuggestions(String content) {
        try {
            String cleaned = content == null ? "" : content.trim()
                    .replaceAll("(?m)^```(?:json)?", "")
                    .replaceAll("(?m)```$", "")
                    .trim();
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalStateException("模型未输出 JSON");
            }
            JsonNode root = objectMapper.readTree(cleaned.substring(start, end + 1));
            List<SeriesSuggestVO> result = new ArrayList<>();
            for (JsonNode item : root.path("series")) {
                String name = item.path("name").asText("").trim();
                if (!name.isEmpty() && result.stream().noneMatch(s -> s.getName().equals(name))) {
                    SeriesSuggestVO vo = new SeriesSuggestVO();
                    vo.setName(name);
                    vo.setReason(item.path("reason").asText(null));
                    result.add(vo);
                }
            }
            if (result.isEmpty()) {
                throw new IllegalStateException("模型未给出任何系列名");
            }
            return result;
        } catch (Exception e) {
            log.warn("AI 系列推荐解析失败: {}", e.getMessage());
            throw new BizException("AI 返回格式异常，请重试或手动新建系列");
        }
    }

    private boolean existsName(Long brandId, String name, Long excludeId) {
        return seriesMapper.selectCount(Wrappers.<DeviceSeries>lambdaQuery()
                .eq(DeviceSeries::getBrandId, brandId)
                .eq(DeviceSeries::getName, name)
                .ne(excludeId != null, DeviceSeries::getId, excludeId)) > 0;
    }

    private int nextSortOrder(Long brandId) {
        List<DeviceSeries> all = list(brandId, null);
        return all.stream()
                .map(DeviceSeries::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(1);
    }
}
