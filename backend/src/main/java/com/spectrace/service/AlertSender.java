package com.spectrace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警统一出口（T104）：与采集连续失败告警共用 {@code AGENT_ALERT_WEBHOOK}（钉钉文本格式）。
 *
 * <p>webhook 未配置时只写 ERROR 日志（部署侧用日志关键字也能告警）；
 * {@link #sendOncePerDay} 对同一规则 24 小时内只发一次，避免持续异常时每小时刷屏。</p>
 */
@Slf4j
@Component
public class AlertSender {

    private static final String ALERT_KEYWORD = "SPECTRACE_ALERT";

    @Value("${spectrace.agent.alert-webhook:}")
    private String webhook;

    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();

    public void send(String message) {
        log.error("{} {}", ALERT_KEYWORD, message);
        if (!StringUtils.hasText(webhook)) {
            return;
        }
        try {
            RestClient.create().post()
                    .uri(webhook)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("msgtype", "text", "text", Map.of("content", "[SpecTrace] " + message)))
                    .retrieve()
                    .toBodilessEntity();
            log.info("告警 webhook 已发送: {}", message);
        } catch (Exception e) {
            log.warn("发送告警 webhook 失败", e);
        }
    }

    /** 同一规则 24h 内至多发送一次；去重只作用于发送，ERROR 日志仍会记录。 */
    public void sendOncePerDay(String rule, String message) {
        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(rule);
        if (last != null && now - last < 24L * 3600 * 1000) {
            log.warn("{}（同规则 24h 内已发过，跳过重复发送）: rule={}", ALERT_KEYWORD, rule);
            return;
        }
        lastSentAt.put(rule, now);
        send(message);
    }
}
