package com.spectrace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量导入的单个机型行，解析结果同时用于预览与错误提示。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceImportRow {

    /** CSV 中的行号（含表头，从 1 开始），便于用户定位 */
    private Integer rowNo;

    private String name;

    private String brandName;

    /** 所属系列名称，空表示未归类 */
    private String seriesName;

    private String releaseDate;

    /** paramKey -> value */
    private Map<String, String> params = new LinkedHashMap<>();

    /** 阻断性错误，存在时该行不会被导入 */
    private List<String> errors = new ArrayList<>();

    /** 非阻断提示，例如未知参数键被忽略 */
    private List<String> warnings = new ArrayList<>();

    public boolean isValid() {
        return errors.isEmpty();
    }
}
