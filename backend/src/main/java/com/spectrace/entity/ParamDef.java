package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态参数定义。新增参数只需在此表插入记录，无需 DDL。
 */
@Data
@TableName("param_def")
public class ParamDef {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 稳定标识，Agent 抽取 Schema 与对比逻辑均以此为准 */
    private String paramKey;

    private String paramName;

    private Long groupId;

    /** text / number / image / date / select */
    private String paramType;

    /** select 类型的枚举选项，JSON 数组字符串 */
    private String enumOptions;

    private String unit;

    private Integer sortOrder;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
