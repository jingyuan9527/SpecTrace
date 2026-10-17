package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 从远端导入模型：把拉取到的模型名称批量写入某个连接。
 */
@Data
public class ImportModelsRequest {

    private List<String> models;

    /** 可为导入的模型统一指定温度，缺省 0.2 */
    private Double temperature;
}
