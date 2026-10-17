package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * 审核通过时，管理员确认后的最终入库内容（可对 Agent 结果做修改）。
 */
@Data
public class ApproveRequest {

    private Long brandId;

    private String name;

    private LocalDate releaseDate;

    private String thumbnail;

    private String description;

    /** paramKey -> value */
    private Map<String, String> params;
}
