package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 机型归类请求：seriesId 为空表示取消归类。
 */
@Data
public class AssignSeriesRequest {

    private List<Long> deviceIds;

    private Long seriesId;
}
