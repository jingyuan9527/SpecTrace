package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.SearchConfigSaveRequest;
import com.spectrace.dto.SearchConfigVO;
import com.spectrace.dto.SearchTestResultVO;
import com.spectrace.service.OperationLogService;
import com.spectrace.service.SearchConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台-采集搜索源")
@RestController
@RequestMapping("/api/admin/search-config")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminSearchController {

    private final SearchConfigService searchConfigService;
    private final OperationLogService logService;

    @Operation(summary = "搜索源配置（Key 只回是否已配置）")
    @GetMapping
    public R<SearchConfigVO> get() {
        return R.ok(searchConfigService.getVO());
    }

    @Operation(summary = "保存搜索源配置（Key 留空表示不修改）")
    @PutMapping
    public R<SearchConfigVO> save(@RequestBody SearchConfigSaveRequest req,
                                  @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        SearchConfigVO vo = searchConfigService.save(req);
        logService.record(adminId, adminName, "SAVE_SEARCH_CONFIG", "agent_search_config", null,
                "搜索引擎：" + vo.getProvider());
        return R.ok(vo);
    }

    @Operation(summary = "用已保存配置真实搜索一次，验证可用性")
    @PostMapping("/test")
    public R<SearchTestResultVO> test(@RequestAttribute Long adminId, @RequestAttribute String adminName) {
        SearchTestResultVO result = searchConfigService.test();
        logService.record(adminId, adminName, "TEST_SEARCH_CONFIG", "agent_search_config", null,
                result.getEngine() + (result.isSuccess()
                        ? " 成功 " + result.getCount() + " 条 " + result.getLatencyMs() + "ms"
                        : " 失败：" + result.getError()));
        return R.ok(result);
    }
}
