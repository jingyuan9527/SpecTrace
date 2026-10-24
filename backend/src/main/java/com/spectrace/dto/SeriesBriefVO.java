package com.spectrace.dto;

import lombok.Data;

/**
 * 机型详情附带的最小系列信息：仅用于展示归属徽章与跳转世代回顾，不携带统计字段。
 */
@Data
public class SeriesBriefVO {

    private Long id;

    private String name;
}
