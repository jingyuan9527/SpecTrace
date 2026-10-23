package com.spectrace.dto;

import lombok.Data;

/**
 * 系列合并请求：把源系列的机型迁到目标系列后删除源系列。
 */
@Data
public class MergeSeriesRequest {

    private Long targetId;
}
