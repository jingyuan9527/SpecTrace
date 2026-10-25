package com.spectrace.dto;

import lombok.Data;

/**
 * 窗口期内浏览量最高的机型（T103）。
 */
@Data
public class TopDevicePv {

    private Long deviceId;

    private String deviceName;

    private long pv;
}
