package com.spectrace.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 采集 Agent 进程入口。
 *
 * 职责隔离：本进程不持有任何数据库连接，只负责「拉任务 → 搜索 → 抓取 → LLM 结构化抽取 → 回调」，
 * 所有入库与审核状态流转均由 SpringBoot 后端掌管。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
