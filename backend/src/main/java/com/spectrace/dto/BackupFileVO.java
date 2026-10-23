package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单个备份文件的展示信息。
 */
@Data
public class BackupFileVO {

    private String fileName;

    /** 字节数 */
    private Long size;

    private LocalDateTime createdAt;
}
