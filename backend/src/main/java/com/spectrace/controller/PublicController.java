package com.spectrace.controller;

import com.spectrace.common.ClientIps;
import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.dto.CompareInsightRequest;
import com.spectrace.dto.CompareInsightVO;
import com.spectrace.dto.CompareResultVO;
import com.spectrace.dto.DeviceSuggestVO;
import com.spectrace.dto.EvolutionVO;
import com.spectrace.dto.HistorySeriesVO;
import com.spectrace.dto.SeriesInsightRequest;
import com.spectrace.dto.SeriesInsightVO;
import com.spectrace.dto.SeriesOverviewResult;
import com.spectrace.dto.SeriesVO;
import com.spectrace.entity.Brand;
import com.spectrace.entity.Device;
import com.spectrace.entity.ParamDef;
import com.spectrace.entity.ParamGroup;
import com.spectrace.service.BrandService;
import com.spectrace.service.CompareInsightService;
import com.spectrace.service.DeviceService;
import com.spectrace.service.EvolutionService;
import com.spectrace.service.ParamService;
import com.spectrace.service.SeriesInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 访客端接口，无需登录。
 */
@Tag(name = "访客端接口")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final BrandService brandService;
    private final ParamService paramService;
    private final DeviceService deviceService;
    private final EvolutionService evolutionService;
    private final CompareInsightService compareInsightService;
    private final SeriesInsightService seriesInsightService;

    @Operation(summary = "品牌列表")
    @GetMapping("/brands")
    public R<List<Brand>> brands() {
        return R.ok(brandService.list(1));
    }

    @Operation(summary = "参数分组")
    @GetMapping("/param-groups")
    public R<List<ParamGroup>> paramGroups() {
        return R.ok(paramService.listGroups());
    }

    @Operation(summary = "参数定义（启用中）")
    @GetMapping("/param-defs")
    public R<List<ParamDef>> paramDefs() {
        return R.ok(paramService.listDefs(null, 1));
    }

    @Operation(summary = "机型分页列表")
    @GetMapping("/devices")
    public R<PageResult<Device>> devices(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "12") long size,
                                         @RequestParam(required = false) Long brandId,
                                         @RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) String keyword) {
        // size 收敛上限：无上限时公开端可一次拉全库
        return R.ok(PageResult.of(deviceService.page(current, Math.min(size, MAX_PAGE_SIZE), brandId, year, keyword)));
    }

    @Operation(summary = "机型搜索联想")
    @GetMapping("/devices/suggest")
    public R<List<DeviceSuggestVO>> suggest(@RequestParam String keyword,
                                            @RequestParam(defaultValue = "8") int limit) {
        return R.ok(deviceService.suggest(keyword, Math.min(limit, 50)));
    }

    @Operation(summary = "热门机型")
    @GetMapping("/devices/hot")
    public R<List<Device>> hot(@RequestParam(defaultValue = "8") int limit) {
        return R.ok(deviceService.hot(Math.min(limit, 50)));
    }

    @Operation(summary = "机型详情")
    @GetMapping("/devices/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(deviceService.detail(id));
    }

    @Operation(summary = "机型对比")
    @GetMapping("/compare")
    public R<CompareResultVO> compare(@RequestParam("ids") String ids) {
        List<Long> deviceIds = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
        return R.ok(deviceService.compare(deviceIds));
    }

    @Operation(summary = "对比差异摘要（只读缓存，用于判定入口是否展示）")
    @GetMapping("/compare/insight")
    public R<CompareInsightVO> compareInsight(@RequestParam("ids") String ids,
                                              HttpServletRequest request) {
        return R.ok(compareInsightService.insight(parseIds(ids), clientKey(request), false));
    }

    @Operation(summary = "生成对比差异摘要（按需调用模型，同组合 24 小时缓存）")
    @PostMapping("/compare/insight")
    public R<CompareInsightVO> generateCompareInsight(@RequestBody CompareInsightRequest req,
                                                      HttpServletRequest request) {
        return R.ok(compareInsightService.insight(req.getIds(), clientKey(request), true));
    }

    /** 单次分页/列表返回的行数上限，防止公开端一次拉全库 */
    private static final long MAX_PAGE_SIZE = 100;

    /** 取真实来源 IP 作为限流维度；反向代理下优先读 X-Forwarded-For 首个地址。 */
    private String clientKey(HttpServletRequest request) {
        return ClientIps.resolve(request);
    }

    @Operation(summary = "参数历史演变（支持多品牌叠加）")
    @GetMapping("/history")
    public R<HistorySeriesVO> history(@RequestParam("brandIds") String brandIds,
                                      @RequestParam Long paramDefId) {
        List<Long> ids = Arrays.stream(brandIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
        return R.ok(deviceService.historySeries(ids, paramDefId));
    }

    @Operation(summary = "机型发布年份")
    @GetMapping("/years")
    public R<List<Integer>> years(@RequestParam(required = false) Long brandId) {
        return R.ok(deviceService.releaseYears(brandId));
    }

    @Operation(summary = "系列列表（含机型数与起止年份）")
    @GetMapping("/series")
    public R<List<SeriesVO>> series(@RequestParam(required = false) Long brandId) {
        return R.ok(evolutionService.seriesList(brandId));
    }

    @Operation(summary = "系列总览（机型库系列视图：收录规模 + 最新一代 + 迷你趋势 + 未归类计数）")
    @GetMapping("/series/overview")
    public R<SeriesOverviewResult> seriesOverview(@RequestParam(required = false) Long brandId) {
        return R.ok(evolutionService.overview(brandId));
    }

    @Operation(summary = "世代回顾：系列机型序列与参数趋势")
    @GetMapping("/series/{id}/evolution")
    public R<EvolutionVO> evolution(@PathVariable Long id,
                                    @RequestParam(required = false) String paramDefIds) {
        return R.ok(evolutionService.evolution(id, parseIds(paramDefIds)));
    }

    @Operation(summary = "世代回顾文案（只读缓存，用于判定入口是否展示）")
    @GetMapping("/series/{id}/insight")
    public R<SeriesInsightVO> seriesInsight(@PathVariable Long id,
                                           @RequestParam(required = false) String deviceIds,
                                           @RequestParam(required = false) String paramDefIds,
                                           HttpServletRequest request) {
        return R.ok(seriesInsightService.insight(id, parseIds(deviceIds), parseIds(paramDefIds),
                clientKey(request), false));
    }

    @Operation(summary = "生成世代回顾文案（按需调用模型）")
    @PostMapping("/series/{id}/insight")
    public R<SeriesInsightVO> generateSeriesInsight(@PathVariable Long id,
                                                    @RequestBody SeriesInsightRequest req,
                                                    HttpServletRequest request) {
        return R.ok(seriesInsightService.insight(id, req.getDeviceIds(), req.getParamDefIds(),
                clientKey(request), true));
    }

    /** 逗号分隔的 ID 串转列表，空串与非法项直接忽略。 */
    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
