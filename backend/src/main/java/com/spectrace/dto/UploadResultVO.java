package com.spectrace.dto;

import lombok.Data;

/**
 * 图片上传结果：除访问地址外返回尺寸与体积，前端可直接展示校验信息。
 */
@Data
public class UploadResultVO {

    private String url;

    /** ImageIO 不支持的格式（如 WebP）可能为空 */
    private Integer width;

    private Integer height;

    private long size;

    private String contentType;
}
