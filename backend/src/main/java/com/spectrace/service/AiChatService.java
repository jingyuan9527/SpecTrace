package com.spectrace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spectrace.common.BizException;
import com.spectrace.dto.ResolvedModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 单轮对话调用：面向「读一批数据 → 写一段话」这类轻量场景，与抓取 Agent 的 Harness 是两条独立链路。
 *
 * <p>模型复用后台配置的默认模型，不额外维护第二套配置；调用失败统一转成 {@link BizException}，
 * 由调用方决定降级方式（对比摘要选择静默降级，不打扰访客）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 是否具备可用模型；配置缺失属于运维问题，访客端只需知道「不可用」。 */
    public boolean available() {
        try {
            aiConfigService.resolve(null);
            return true;
        } catch (Exception e) {
            log.debug("AI 不可用：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 发送一轮对话并返回助手文本。
     *
     * @param maxTokens 输出上限，避免模型写长文把成本推高
     */
    public String complete(String systemPrompt, String userPrompt, int maxTokens) {
        ResolvedModel model = aiConfigService.resolve(null);
        String baseUrl = model.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new BizException("AI 接口地址未配置");
        }
        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model.getModel());
        body.put("temperature", model.getTemperature() == null ? 0.3 : model.getTemperature());
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        long startedAt = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (StringUtils.hasText(model.getApiKey())) {
                builder.header("Authorization", "Bearer " + model.getApiKey());
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long cost = System.currentTimeMillis() - startedAt;
            if (response.statusCode() >= 400) {
                log.warn("AI 调用失败: model={} status={} cost={}ms body={}",
                        model.getModel(), response.statusCode(), cost, truncate(response.body(), 300));
                throw new BizException("AI 调用失败：HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                log.warn("AI 返回内容为空: model={} cost={}ms", model.getModel(), cost);
                throw new BizException("AI 返回内容为空");
            }
            log.info("AI 调用完成: model={} cost={}ms in={} out={}", model.getModel(), cost,
                    count(root.path("usage").path("prompt_tokens")), count(root.path("usage").path("completion_tokens")));
            return content;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 调用异常: model={} url={}", model.getModel(), url, e);
            throw new BizException("AI 调用异常，请稍后再试");
        }
    }

    private int count(JsonNode node) {
        return node.isMissingNode() ? 0 : node.asInt(0);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
