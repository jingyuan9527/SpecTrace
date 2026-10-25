package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 同代横比矩阵：某品牌指定年份各兄弟系列的代表机型与参数单点值。
 */
@Data
public class GenerationMatrixVO {

    private Long brandId;

    private String brandName;

    private Integer year;

    private List<GenerationSeriesVO> items;
}
