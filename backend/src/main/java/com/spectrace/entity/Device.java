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
}
