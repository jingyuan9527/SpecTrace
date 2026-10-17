package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 单参数历史演变数据点。
 */
@Data
public class HistoryPoint {
    private Long deviceId;
    private String deviceName;
    private LocalDate releaseDate;
    private String value;
}
