package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.BackupFileVO;
import com.spectrace.dto.BackupOverviewVO;
import com.spectrace.dto.RetentionOverviewVO;
import com.spectrace.service.BackupService;
import com.spectrace.service.DataRetentionService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统运维：数据库备份与增长数据清理的查看与手动触发。
 * 仅超级管理员可操作，避免普通管理员误触磁盘写入。
 */
@Tag(name = "后台-系统运维")
@RestController
@RequestMapping("/api/admin/maintenance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminMaintenanceController {

    private final BackupService backupService;
    private final DataRetentionService retentionService;
    private final OperationLogService logService;

    @Operation(summary = "备份概览与列表")
    @GetMapping("/backups")
    public R<BackupOverviewVO> backups() {
        return R.ok(backupService.overview());
    }

    @Operation(summary = "立即备份")
    @PostMapping("/backups")
    public R<BackupFileVO> backup(@RequestAttribute Long adminId, @RequestAttribute String adminName) {
        BackupFileVO vo = backupService.trigger();
        logService.record(adminId, adminName, "BACKUP_DATABASE", "sqlite", null, "手动备份 " + vo.getFileName());
        return R.ok(vo);
    }

    @Operation(summary = "数据保留策略概览（各增长表行数与清理配置）")
    @GetMapping("/retention")
    public R<RetentionOverviewVO> retention() {
        return R.ok(retentionService.overview());
    }

    @Operation(summary = "立即执行一次保留策略清理")
    @PostMapping("/retention/cleanup")
    public R<Map<String, Long>> cleanup(@RequestAttribute Long adminId, @RequestAttribute String adminName) {
        Map<String, Long> removed = retentionService.cleanup();
        logService.record(adminId, adminName, "RETENTION_CLEANUP", "sqlite", null,
                "手动清理 " + removed.values().stream().mapToLong(Long::longValue).sum() + " 行");
        return R.ok(removed);
    }
}
