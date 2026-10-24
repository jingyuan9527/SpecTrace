package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/** 系列发现任务详情：发现清单 + 库中是否已存在同名机型，供前端勾选创建草稿档案。 */
@Data
public class SeriesDiscoveryVO {

    private Long taskId;

    private String brandName;

    private String seriesName;

    private String sourceUrl;

    private String status;

    private List<Item> models;

    @Data
    public static class Item {
        private String name;
        private String releaseDate;
        /** 库中已存在同品牌同名机型时为 true，前端标注且默认不勾选 */
        private boolean existing;
        private Long deviceId;
    }
}
