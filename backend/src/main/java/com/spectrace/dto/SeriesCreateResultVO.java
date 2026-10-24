package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/** 从系列发现结果创建草稿机型的结果：已存在同名机型的按跳过处理，保证重复点击幂等。 */
@Data
public class SeriesCreateResultVO {
    private int created;
    private List<String> skippedExisting;
    private List<Long> deviceIds;
}
