package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 工具调用审计。
 *
 * <p>operation_log 记录的是「数据被改成了什么」，这里记录「哪个 Agent 用什么参数调了什么工具」，
 * 两侧通过时间与 targetId 对上，失败调用也只有这里留痕。</p>
 */
@Data
@TableName("mcp_call_log")
public class McpCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    private String agentName;

    private String toolName;

    /** 入参 JSON，超过配置长度会被截断 */
    private String argumentsJson;

    /** SUCCESS / ERROR / DENIED */
    private String status;

    private String errorCode;

    private String errorMessage;

    private Long durationMs;

    private String clientName;

    private String sessionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
