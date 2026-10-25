package com.spectrace.dto;

import lombok.Data;

/**
 * 单机型近 N 天浏览量合计（机型管理列表与数据健康度排行用）。
 */
@Data
public class DevicePv {

    private Long deviceId;

    private Long pv;
}
