package com.spectrace.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.agent.config.AgentProperties;
import com.spectrace.agent.dto.AgentCallbackRequest;
import com.spectrace.agent.dto.AgentTaskPayload;
import com.spectrace.agent.dto.ParamDef;
import com.spectrace.agent.dto.ResolvedModel;
import com.spectrace.agent.tool.CollectTools;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 手机参数采集 Agent（AgentScope Java 2.0 · Harness 模式）。
 *
 * <p>基于 {@link HarnessAgent}：persona/输出规约来自工作区 {@code AGENTS.md}，
 * 自定义工具经 {@link Toolkit} 注入。链路：搜索 → 抓取网页 → LLM 结构化抽取 → 校验/附来源 → 输出 JSON。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectService {

    private final CollectTools collectTools;
    private final AgentProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 执行一次采集，返回可回传后端的结构化结果（对齐 {@code /api/agent/callback} 请求体）。
     */
    public AgentCallbackRequest collect(AgentTaskPayload payload, List<ParamDef> schemaDefs,
                                        ResolvedModel aiConfig) {
        String modelName = payload.getModelName() == null ? "" : payload.getModelName();
        String brandName = payload.getBrandName() == null ? "" : payload.getBrandName();

        try {
            Model model = buildModel(aiConfig);

            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(collectTools);

            // persona / 输出规约由工作区 AGENTS.md 提供；此处仅注入随任务变化的参数 Schema
            String sysPrompt = schemaPrompt(schemaDefs);
            String query = (brandName + " " + modelName + " 手机 完整参数 规格 官网").trim();
            String prompt = "请检索并抽取机型「" + modelName + "」"
                    + (brandName.isEmpty() ? "" : "（品牌：" + brandName + "）")
                    + " 的全部可获取参数，按系统 Schema 输出 JSON。";

            // 每个任务使用独立的会话身份，任务之间互不共享上下文 / 记忆
            String identity = "task-" + payload.getTaskId();
            RuntimeContext ctx = RuntimeContext.builder()
                    .userId(identity)
                    .sessionId(identity)
                    .build();

            String text;
            try (HarnessAgent agent = HarnessAgent.builder()
                    .name("phone_spec_collector")
                    .agentId("phone_spec_collector")
                    .sysPrompt(sysPrompt)
                    .model(model)
                    .toolkit(toolkit)
                    .workspace(Path.of(props.getWorkspace()))
                    .maxIters(20)
                    // 一次性抽取任务：关闭与本场景无关的能力，保持上下文聚焦
                    .disableShellTool()
                    .disableFilesystemTools()
                    .disableWebTools()
                    .disableSubagents()
                    .disableDynamicSkills()
                    .disableDefaultWorkspaceSkills()
                    .disableToolsConfig()
                    .disableMemoryHooks()
                    .disableMemoryTools()
                    .build()) {
                Msg msg = agent.call(List.<Msg>of(new UserMessage(prompt)), ctx)
                        .block(Duration.ofSeconds(props.getTaskTimeoutSeconds()));
                text = msg == null ? "" : msg.getTextContent();
            }

            JsonNode data = parseJson(text);

            Set<String> allowed = schemaDefs.stream()
                    .map(ParamDef::getParamKey)
                    .collect(Collectors.toSet());
            Map<String, Object> params = new LinkedHashMap<>();
            List<String> dropped = new java.util.ArrayList<>();
            JsonNode paramsNode = data.path("params");
            if (paramsNode.isObject()) {
                paramsNode.fields().forEachRemaining(e -> {
                    if (allowed.contains(e.getKey()) && !e.getValue().isNull()) {
                        params.put(e.getKey(), e.getValue().asText());
                    } else if (!allowed.contains(e.getKey())) {
                        dropped.add(e.getKey());
                    }
                });
            }

            AgentCallbackRequest result = new AgentCallbackRequest();
            result.setTaskId(payload.getTaskId());
            result.setModelName(textOrFallback(data, "modelName", modelName));
            result.setBrandName(textOrFallback(data, "brandName", brandName.isEmpty() ? null : brandName));
            result.setSourceUrl(nullableText(data, "sourceUrl"));
            result.setStatus("SUCCESS");
            result.setParams(params);
            result.setLogs(objectMapper.writeValueAsString(Map.of(
                    "queries", List.of(query),
                    "confidence", nullableText(data, "confidence"),
                    "notes", nullableText(data, "notes"),
                    "droppedUnknownKeys", dropped)));
            return result;
        } catch (Exception e) {
            log.error("采集失败 taskId={}", payload.getTaskId(), e);
            String message = e.getMessage() == null ? "" : e.getMessage();
            String error = message.contains("Timeout") || message.contains("timeout")
                    ? "抓取超时（>" + props.getTaskTimeoutSeconds() + "s）"
                    : e.getClass().getSimpleName() + ": " + message;
            AgentCallbackRequest result = new AgentCallbackRequest();
            result.setTaskId(payload.getTaskId());
            result.setModelName(modelName);
            result.setBrandName(brandName.isEmpty() ? null : brandName);
            result.setStatus("FAILED");
            result.setError(error);
            return result;
        }
    }

    /**
     * 根据后台 AI 配置构建 AgentScope 大模型对象。
     *
     * <p>provider=dashscope 且未配置「OpenAI 兼容」端点时使用原生 DashScopeChatModel；
     * 其余情况（含 dashscope 的 compatible-mode 端点、openai/deepseek/moonshot/ollama/vllm 等）
     * 统一走 OpenAI 兼容接口。未在后台配置时回退到环境变量中的 DASHSCOPE_* 配置。
     */
    private Model buildModel(ResolvedModel cfg) {
        String provider = cfg == null || cfg.getProvider() == null ? "dashscope" : cfg.getProvider().toLowerCase();
        String apiKey = cfg != null && notBlank(cfg.getApiKey()) ? cfg.getApiKey() : props.getDashscopeApiKey();
        String modelName = cfg != null && notBlank(cfg.getModel()) ? cfg.getModel() : props.getDashscopeModel();
        String baseUrl = cfg == null ? null : cfg.getBaseUrl();
        Double temperature = cfg == null ? null : cfg.getTemperature();

        if (!notBlank(apiKey)) {
            throw new IllegalStateException("未配置 AI API Key，请在后台「AI 配置」中填写");
        }

        GenerateOptions options = temperature == null
                ? null
                : GenerateOptions.builder().temperature(temperature).build();

        if ("dashscope".equals(provider) && !looksOpenAiCompatible(baseUrl)) {
            var builder = DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .stream(false)
                    .formatter(new DashScopeChatFormatter());
            if (notBlank(baseUrl)) {
                builder.baseUrl(baseUrl);
            }
            if (options != null) {
                builder.defaultOptions(options);
            }
            return builder.build();
        }

        var builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(false)
                .formatter(new OpenAIChatFormatter());
        if (notBlank(baseUrl)) {
            builder.baseUrl(baseUrl);
        }
        if (options != null) {
            builder.generateOptions(options);
        }
        return builder.build();
    }

    /**
     * 判断 baseUrl 是否为 OpenAI 兼容端点（如 DashScope 的 {@code /compatible-mode/v1}、各家的 {@code /v1}）。
     * 命中时应改用 OpenAIChatModel，否则原生 DashScopeChatModel 会把原生路径拼到兼容端点后导致 404。
     */
    private boolean looksOpenAiCompatible(String baseUrl) {
        if (!notBlank(baseUrl)) {
            return false;
        }
        String b = baseUrl.toLowerCase(java.util.Locale.ROOT);
        return b.contains("/compatible-mode") || b.endsWith("/v1") || b.contains("/v1/");
    }

    /** 把后端返回的参数定义转成给 LLM 的 Schema 说明。 */
    private String schemaPrompt(List<ParamDef> schemaDefs) {
        StringBuilder sb = new StringBuilder("参数 Schema（param_key | 名称 | 类型 | 单位 | 枚举）：");
        for (ParamDef d : schemaDefs) {
            sb.append('\n').append(String.join(" | ",
                    nvl(d.getParamKey()), nvl(d.getParamName()),
                    nvl(d.getParamType() == null ? "text" : d.getParamType()),
                    nvl(d.getUnit()), nvl(d.getEnumOptions())));
        }
        return sb.toString();
    }

    /** 从模型输出中提取 JSON 对象（容忍代码块与多余文字）。 */
    private JsonNode parseJson(String text) throws Exception {
        String cleaned = text == null ? "" : text.trim()
                .replaceAll("(?m)^```(?:json)?", "")
                .replaceAll("(?m)```$", "")
                .trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("模型未输出 JSON");
        }
        return objectMapper.readTree(cleaned.substring(start, end + 1));
    }

    private String textOrFallback(JsonNode node, String field, String fallback) {
        String v = nullableText(node, field);
        return v != null && !v.isBlank() ? v : fallback;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private String nvl(String s) {
        return s == null ? "-" : s;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
