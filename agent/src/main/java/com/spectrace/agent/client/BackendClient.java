package com.spectrace.agent.client;

import com.spectrace.agent.dto.AgentCallbackRequest;
import com.spectrace.agent.dto.AgentTaskPayload;
import com.spectrace.agent.dto.ApiResponse;
import com.spectrace.agent.dto.ParamDef;
import com.spectrace.agent.dto.ResolvedModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 与 SpringBoot 业务后端的 HTTP 通信封装。
 *
 * <p>本类只做「拉任务 / 报状态 / 回传结果 / 取 Schema / 取模型参数」，不直接访问任何数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackendClient {

    private static final ParameterizedTypeReference<ApiResponse<AgentTaskPayload>> TASK_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ApiResponse<List<ParamDef>>> SCHEMA_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ApiResponse<ResolvedModel>> MODEL_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient backendRestClient;

    /** 获取参数抽取 Schema（启用中的参数定义）。 */
    public List<ParamDef> fetchSchema() {
        ApiResponse<List<ParamDef>> resp =
                backendRestClient.get().uri("/api/agent/schema").retrieve().body(SCHEMA_TYPE);
        return resp == null || resp.getData() == null ? List.of() : resp.getData();
    }

    /**
     * 获取实际使用的 AI 模型参数（provider / baseUrl / model / apiKey / temperature）。
     * 传入 modelId 则使用任务指定的模型，否则后端返回默认模型。
     */
    public ResolvedModel fetchAiConfig(Long modelId) {
        ApiResponse<ResolvedModel> resp = backendRestClient.get()
                .uri(uri -> {
                    uri.path("/api/agent/ai-config");
                    if (modelId != null) {
                        uri.queryParam("modelId", modelId);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(MODEL_TYPE);
        return resp == null ? null : resp.getData();
    }

    /** HTTP 轮询模式下拉取下一个任务；无任务（204）返回空。 */
    public Optional<AgentTaskPayload> pullTask() {
        ApiResponse<AgentTaskPayload> resp = backendRestClient.get()
                .uri("/api/agent/tasks/pull")
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 204) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new IllegalStateException("拉取任务失败: " + response.getStatusCode());
                    }
                    return response.bodyTo(TASK_TYPE);
                });
        if (resp == null || resp.getData() == null) {
            return Optional.empty();
        }
        return Optional.of(resp.getData());
    }

    /** 通知后端任务已开始抓取（Redis 消费模式下使用）。 */
    public void markRunning(Long taskId) {
        backendRestClient.post()
                .uri("/api/agent/tasks/{id}/running", taskId)
                .retrieve()
                .toBodilessEntity();
    }

    /** 回传抓取结果，由 SpringBoot 写入待审核库。 */
    public void callback(AgentCallbackRequest payload) {
        backendRestClient.post()
                .uri("/api/agent/callback")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
