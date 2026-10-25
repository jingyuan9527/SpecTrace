package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.DataHealthVO;
import com.spectrace.service.DataHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据健康度看板：补录优先级的数据依据（T85）。
 */
@Tag(name = "后台-数据健康度")
@RestController
@RequestMapping("/api/admin/data-health")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminDataHealthController {

    private final DataHealthService dataHealthService;

    @Operation(summary = "数据健康度总览（brandId/seriesId 只作用于缺参机型排行）")
    @GetMapping
    public R<DataHealthVO> overview(@RequestParam(required = false) Long brandId,
                                    @RequestParam(required = false) Long seriesId) {
        return R.ok(dataHealthService.overview(brandId, seriesId));
    }
}
