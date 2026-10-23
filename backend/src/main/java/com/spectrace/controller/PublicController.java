package com.spectrace.controller;

import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.dto.CompareInsightRequest;
import com.spectrace.dto.CompareInsightVO;
import com.spectrace.dto.CompareResultVO;
import com.spectrace.dto.DeviceSuggestVO;
import com.spectrace.dto.EvolutionVO;
import com.spectrace.dto.HistorySeriesVO;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
        return R.ok(PageResult.of(deviceService.page(current, size, brandId, year, keyword)));
    }

    @Operation(summary = "机型搜索联想")
    @GetMapping("/devices/suggest")
    public R<List<DeviceSuggestVO>> suggest(@RequestParam String keyword,
                                            @RequestParam(defaultValue = "8") int limit) {
        return R.ok(deviceService.suggest(keyword, limit));
    }

    @Operation(summary = "热门机型")
    @GetMapping("/devices/hot")
    public R<List<Device>> hot(@RequestParam(defaultValue = "8") int limit) {
        return R.ok(deviceService.hot(limit));
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

    /** 取真实来源 IP 作为限流维度；反向代理下优先读 X-Forwarded-For 首个地址。 */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : "unknown";
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

    @Operation(summary = "世代回顾：系列机型序列与参数趋势")
    @GetMapping("/series/{id}/evolution")
    public R<EvolutionVO> evolution(@PathVariable Long id,
                                    @RequestParam(required = false) String paramDefIds) {
        return R.ok(evolutionService.evolution(id, parseIds(paramDefIds)));
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
