package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量操作的目标 ID 集合。
 */
@Data
public class BatchIdsRequest {

    private List<Long> ids;
}
