package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 世代回顾中的单台机型（一代）。
 */
@Data
public class EvolutionDeviceVO {

    private Long id;

    private String name;

    private String thumbnail;

    private LocalDate releaseDate;

    /** 起售价：取该机型所有变体价格的最低值，无价格时为 null */
    private BigDecimal launchPrice;
}
