package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备份概览：配置 + 下次执行时间 + 现有备份。
 */
@Data
public class BackupOverviewVO {

    private boolean enabled;

    private String dir;

    private int keep;

    /** 下次定时执行时间；备份关闭或 cron 非法时为 null */
    private LocalDateTime nextRunAt;

    private List<BackupFileVO> backups;
}
