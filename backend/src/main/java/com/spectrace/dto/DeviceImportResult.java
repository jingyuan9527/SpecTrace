package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量导入结果：dryRun=true 时只返回预览与校验结果，不落库。
 */
@Data
public class DeviceImportResult {

    private boolean dryRun;

    private int total;

    private int validCount;

    private int invalidCount;

    /** 实际新增的机型数量，预览时为 0 */
    private int createdCount;

    private List<DeviceImportRow> rows;
}
