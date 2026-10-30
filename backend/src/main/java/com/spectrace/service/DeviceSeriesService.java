package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.config.CacheConfig;
import com.spectrace.dto.DeviceLastUpdate;
import com.spectrace.dto.SeriesDeviceVO;
import com.spectrace.dto.SeriesMissingDeviceVO;
import com.spectrace.dto.SeriesSuggestVO;
import com.spectrace.dto.SeriesVO;
import com.spectrace.dto.UnassignedDeviceVO;
import com.spectrace.entity.AgentSubscription;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.DeviceParamValue;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.mapper.AgentSubscriptionMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceParamValueMapper;
import com.spectrace.mapper.DeviceSeriesMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final DeviceParamValueMapper valueMapper;
    /** 订阅挂在系列上：系列删/并后订阅必须一起处理，否则留下谁也看不到的孤儿配置。 */
    private final AgentSubscriptionMapper subscriptionMapper;
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
    @CacheEvict(cacheNames = {CacheConfig.SERIES_LIST, CacheConfig.DEVICE_DETAIL}, allEntries = true)
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
    @CacheEvict(cacheNames = {CacheConfig.SERIES_LIST, CacheConfig.DEVICE_DETAIL}, allEntries = true)
    public void delete(Long id) {
        if (seriesMapper.selectById(id) == null) {
            throw new BizException("系列不存在");
        }
        Long attached = deviceMapper.selectCount(Wrappers.<Device>lambdaQuery().eq(Device::getSeriesId, id));
        if (attached != null && attached > 0) {
            throw new BizException("系列下还有 " + attached + " 台机型，请先合并到其它系列或改为未归类");
        }
        seriesMapper.deleteById(id);
        // 订阅只挂在系列详情下访问：系列一删这条配置就再也点不到，留着只会在调度里变成孤儿
        int removedSubscriptions = subscriptionMapper.delete(Wrappers.<AgentSubscription>lambdaQuery()
                .eq(AgentSubscription::getSeriesId, id));
        log.info("删除系列: id={}，一并清理订阅 {} 条", id, removedSubscriptions);
    }

    /**
     * 系列概览：带品牌名、机型数与起止年份，后台管理与前台筛选共用。
     *
     * @param publishedOnly 前台口径只统计已上架机型；后台完善度看板含草稿（草稿同样需要补参数）
     */
    public List<SeriesVO> listOverview(Long brandId, Integer status, boolean publishedOnly) {
        List<DeviceSeries> series = list(brandId, status);
        if (series.isEmpty()) {
            return List.of();
        }
        List<Long> seriesIds = series.stream().map(DeviceSeries::getId).toList();
        var deviceQuery = Wrappers.<Device>lambdaQuery()
                .select(Device::getId, Device::getSeriesId, Device::getReleaseDate)
                .in(Device::getSeriesId, seriesIds);
        if (publishedOnly) {
            deviceQuery.eq(Device::getStatus, 1);
        }
        Map<Long, List<Device>> devicesBySeries = deviceMapper.selectList(deviceQuery).stream()
                .collect(Collectors.groupingBy(Device::getSeriesId));

        // 完善度口径：与 deviceCount 同集合（含草稿），统计已有至少一条参数值的机型
        List<Long> allDeviceIds = devicesBySeries.values().stream()
                .flatMap(List::stream)
                .map(Device::getId)
                .toList();
        Set<Long> deviceIdsWithParams = deviceIdsWithParams(allDeviceIds);

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
            vo.setParamDeviceCount((int) devices.stream()
                    .filter(d -> deviceIdsWithParams.contains(d.getId()))
                    .count());
            vo.setStartYear(years.isEmpty() ? null : years.get(0));
            vo.setEndYear(years.isEmpty() ? null : years.get(years.size() - 1));
            result.add(vo);
        }
        return result;
    }

    /** 已有至少一条参数值的机型 ID 集合，供完善度统计与缺参清单共用。 */
    private Set<Long> deviceIdsWithParams(List<Long> deviceIds) {
        if (deviceIds.isEmpty()) {
            return Set.of();
        }
        var query = Wrappers.<DeviceParamValue>query()
                .select("DISTINCT device_id")
                .in("device_id", deviceIds);
        return valueMapper.selectObjs(query).stream()
                .filter(Objects::nonNull)
                .map(id -> ((Number) id).longValue())
                .collect(Collectors.toSet());
    }

    /**
     * 系列内缺参数的机型（含草稿），供「补抓缺参机型」圈定目标。
     * 草稿机型是系列发现链路的产物，同样需要抓详情补参数，因此不按上架状态过滤。
     */
    public List<SeriesMissingDeviceVO> devicesWithoutParams(Long seriesId) {
        DeviceSeries series = requireSeries(seriesId);
        List<Device> devices = seriesDevices(seriesId);
        Set<Long> withParams = deviceIdsWithParams(devices.stream().map(Device::getId).toList());
        return devices.stream()
                .filter(d -> !withParams.contains(d.getId()))
                .map(this::toMissingVO)
                .toList();
    }

    /**
     * 系列内参数陈旧的机型（T87）：无参数，或参数最近更新早于 N 天前。
     * updated_at 无法解析的按陈旧处理——宁多抓不漏抓；days 传 0 可圈定全部有机型的目标（冒烟用）。
     */
    public List<SeriesMissingDeviceVO> staleDevices(Long seriesId, int days) {
        requireSeries(seriesId);
        List<Device> devices = seriesDevices(seriesId);
        if (devices.isEmpty()) {
            return List.of();
        }
        Map<Long, LocalDateTime> lastUpdates = new HashMap<>();
        for (DeviceLastUpdate row : valueMapper.selectLastUpdateByDevice(devices.stream().map(Device::getId).toList())) {
            LocalDateTime parsed = parseStorageTime(row.getLastUpdated());
            if (parsed != null) {
                lastUpdates.put(row.getDeviceId(), parsed);
            }
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return devices.stream()
                .filter(d -> lastUpdates.getOrDefault(d.getId(), LocalDateTime.MIN).isBefore(cutoff))
                .map(this::toMissingVO)
                .toList();
    }

    private DeviceSeries requireSeries(Long seriesId) {
        DeviceSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在：" + seriesId);
        }
        return series;
    }

    private List<Device> seriesDevices(Long seriesId) {
        return deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .eq(Device::getSeriesId, seriesId)
                .orderByDesc(Device::getReleaseDate));
    }

    private SeriesMissingDeviceVO toMissingVO(Device device) {
        SeriesMissingDeviceVO vo = new SeriesMissingDeviceVO();
        vo.setId(device.getId());
        vo.setName(device.getName());
        vo.setStatus(device.getStatus());
        vo.setReleaseDate(device.getReleaseDate());
        vo.setThumbnail(device.getThumbnail());
        return vo;
    }

    /** SQLite DATETIME 读回的字符串兼容 T 与空格两种分隔符；解析失败返回 null 由调用方兜底。 */
    private LocalDateTime parseStorageTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim().replace(' ', 'T').substring(0, 19));
        } catch (Exception e) {
            log.warn("参数更新时间无法解析，按陈旧处理: raw={}", raw);
            return null;
        }
    }

    /** 系列内机型清单：后台据此刻对归属，把误分机型移到其它系列（移动仍走 assign）。 */
    public List<SeriesDeviceVO> devices(Long seriesId) {
        requireSeries(seriesId);
        return seriesDevices(seriesId).stream().map(device -> {
            SeriesDeviceVO vo = new SeriesDeviceVO();
            vo.setId(device.getId());
            vo.setName(device.getName());
            vo.setBrandId(device.getBrandId());
            vo.setSeriesId(device.getSeriesId());
            vo.setStatus(device.getStatus());
            vo.setReleaseDate(device.getReleaseDate());
            vo.setThumbnail(device.getThumbnail());
            return vo;
        }).toList();
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
    @CacheEvict(cacheNames = {CacheConfig.SERIES_LIST, CacheConfig.DEVICE_DETAIL}, allEntries = true)
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
        Map<Long, String> before = seriesNamesOf(devices);
        int updated = deviceMapper.update(null, Wrappers.<Device>lambdaUpdate()
                .in(Device::getId, deviceIds)
                .set(Device::getSeriesId, seriesId));
        String target = series == null ? "未归类" : series.getName();
        // 逐台记录原系列 → 新系列：系列分错后要能查清是谁、什么时候移的
        log.info("机型换系列: 目标={} 影响 {} 台，明细：{}", target, updated, describeMoves(devices, before, target));
        return updated;
    }

    /** 机型当前系列名映射，供换系列日志标注「原系列」。 */
    private Map<Long, String> seriesNamesOf(List<Device> devices) {
        List<Long> seriesIds = devices.stream()
                .map(Device::getSeriesId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (seriesIds.isEmpty()) {
            return Map.of();
        }
        return seriesMapper.selectBatchIds(seriesIds).stream()
                .collect(Collectors.toMap(DeviceSeries::getId, DeviceSeries::getName));
    }

    private String describeMoves(List<Device> devices, Map<Long, String> before, String target) {
        return devices.stream()
                .map(device -> {
                    // 未归类机型没有原系列；Map.of() 不接受 null 键，不能直接用 getOrDefault
                    String from = device.getSeriesId() == null ? null : before.get(device.getSeriesId());
                    return device.getName() + "（" + (from == null ? "未归类" : from) + "→" + target + "）";
                })
                .collect(Collectors.joining("、"));
    }

    /** 合并系列：机型迁到目标系列后删除源系列，两个系列必须同品牌。 */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.SERIES_LIST, CacheConfig.DEVICE_DETAIL}, allEntries = true)
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
        migrateSubscription(sourceId, targetId);
        seriesMapper.deleteById(sourceId);
        log.info("合并系列: {} -> {}，迁移机型 {} 台", source.getName(), target.getName(), moved);
        return moved;
    }

    /**
     * 合并后源系列消失，订阅跟着走：目标系列已有订阅时以目标为准
     * （{@code agent_subscription.series_id} 唯一，一个系列只能留一条），否则把源订阅迁过去。
     */
    private void migrateSubscription(Long sourceId, Long targetId) {
        AgentSubscription source = subscriptionMapper.selectOne(Wrappers.<AgentSubscription>lambdaQuery()
                .eq(AgentSubscription::getSeriesId, sourceId));
        if (source == null) {
            return;
        }
        if (subscriptionMapper.selectOne(Wrappers.<AgentSubscription>lambdaQuery()
                .eq(AgentSubscription::getSeriesId, targetId)) != null) {
            subscriptionMapper.deleteById(source.getId());
            log.info("合并系列: 目标系列已有订阅，丢弃源订阅 subscriptionId={} source={}", source.getId(), sourceId);
            return;
        }
        source.setSeriesId(targetId);
        subscriptionMapper.updateById(source);
        log.info("合并系列: 订阅已迁移 subscriptionId={} {} -> {}", source.getId(), sourceId, targetId);
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
                1. 只输出一个 JSON 对象，格式：{"series":[{"name":"系列名","reason":"一句话说明定位与大致价位段"}]}
                2. 推荐 4~8 个系列；名称用品牌官方或社区通用叫法（中文品牌用中文，国际品牌用英文），不要自造词。
                3. 只列该品牌官方自有的产品线：子品牌与独立品牌严禁混入（如荣耀不属于华为、Redmi 不属于小米、iQOO 不属于 vivo）。
                4. 不要把其它品牌的命名习惯套用到该品牌上（如华为没有「数字系列」，苹果没有「畅享系列」）。
                5. 不确定某产品线是否真实属于该品牌时，直接不输出——宁缺毋滥，错误候选比缺失更糟糕。
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
