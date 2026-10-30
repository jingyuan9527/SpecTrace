package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部 Agent 接入凭据。
 *
 * <p>明文 Key 只在创建/轮换时返回一次，库里只留哈希与前缀：数据库泄露也无法反推凭据。
 * 授权实际由 {@link #scopes} 决定，{@link #role} 仅作审计标签与后台展示口径。</p>
 */
@Data
@TableName("mcp_agent")
public class McpAgent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @JsonIgnore
    private String apiKeyHash;

    /** 明文 Key 的前缀，便于在后台核对是哪一个凭据，不含足够熵可直接伪造 */
    private String apiKeyPrefix;

    /** READ_ONLY / EDITOR / ADMIN，仅作标签，权限以 scopes 为准 */
    private String role;

    /** 逗号分隔的 scope 列表；* 表示全部 */
    private String scopes;

    private Integer status;

    private Long callCount;

    private LocalDateTime lastUsedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
