package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long brandId;

    /** 所属产品线，可空：未归类机型照常展示，只是不进世代回顾 */
    private Long seriesId;

    private String name;

    private LocalDate releaseDate;

    private String thumbnail;

    /** 待下载的产品图 URL（T90）：审核通过只落 URL，下载由独立线程异步完成，失败静默清空 */
    private String thumbnailPendingUrl;

    private String description;

    /** 1 上架 0 下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /**
     * 同品牌同名机型的待审核抓取数据条数（非表字段，管理端列表「抓取待审」徽标用）。
     * 0 或 null 都表示没有待处理结果。
     */
    @TableField(exist = false)
    private Integer pendingReviewCount;

    /**
     * 近 30 天访客浏览量（非表字段，管理端列表展示与数据健康度排行用）。
     * 统计不可用时为 null，前端按「—」展示。
     */
    @TableField(exist = false)
    private Long pv30d;
}
