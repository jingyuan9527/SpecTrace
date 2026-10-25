package com.spectrace.dto;

import lombok.Data;

/**
 * 单日全站浏览量（T103）。
 */
@Data
public class DailyPv {

    private String date;

    private long pv;
}
