package com.spectrace.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 从系列发现结果创建草稿机型的请求：传勾选的机型名，后端按任务结果补齐发布日期。 */
@Data
public class SeriesCreateDevicesRequest {
    @NotEmpty
    private List<String> names;
}
