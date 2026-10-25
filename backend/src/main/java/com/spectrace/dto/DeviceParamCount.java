package com.spectrace.dto;

import lombok.Data;

/**
 * 机型参数统计行：该机型已有取值的参数个数。
 */
@Data
public class DeviceParamCount {

    private Long deviceId;

    private Long paramCount;
}
