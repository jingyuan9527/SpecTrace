package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 访客搜索词按天聚合（T102）：只记词与次数，不记 IP 与访客标识。
 * 「空结果率」回答访客在找什么而库里没有什么，直接指导补录方向。
 */
@Data
@TableName("search_log")
public class SearchLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyword;

    private LocalDate searchDate;

    private Integer searchCount;

    private Integer zeroCount;
}
