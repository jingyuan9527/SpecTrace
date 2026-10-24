package com.spectrace.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 批量上架/下架请求。status：1 上架（前台可见）、0 下架。 */
@Data
public class DeviceBatchStatusRequest {
    @NotEmpty
    private List<Long> ids;
    @NotNull
    private Integer status;
}
