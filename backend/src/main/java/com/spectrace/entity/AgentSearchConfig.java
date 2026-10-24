package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 采集搜索源配置（单行，id=1）。 */
@Data
@TableName("agent_search_config")
public class AgentSearchConfig {

    public static final long SINGLETON_ID = 1L;

    @TableId
    private Long id;

    /** system（默认 DuckDuckGo/Serper）/ tavily / searxng */
    private String provider;

    private String tavilyApiKey;

    private String searxngBaseUrl;

    private String searxngToken;

    private String serperApiKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
