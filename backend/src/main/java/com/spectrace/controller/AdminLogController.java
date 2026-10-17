package com.spectrace.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.entity.OperationLog;
import com.spectrace.mapper.OperationLogMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "后台-操作日志")
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminLogController {

    private final OperationLogMapper operationLogMapper;

    @GetMapping
    public R<PageResult<OperationLog>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String targetType) {
        var query = Wrappers.<OperationLog>lambdaQuery().orderByDesc(OperationLog::getCreatedAt);
        if (targetType != null && !targetType.isBlank()) {
            query.eq(OperationLog::getTargetType, targetType);
        }
        IPage<OperationLog> page = operationLogMapper.selectPage(new Page<>(current, size), query);
        return R.ok(PageResult.of(page));
    }
}
