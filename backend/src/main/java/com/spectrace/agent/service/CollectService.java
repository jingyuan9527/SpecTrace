package com.spectrace.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.agent.CrawlSession;
import com.spectrace.agent.TaskLogContext;
import com.spectrace.agent.config.AgentProperties;
import com.spectrace.agent.model.RecordingChatModel;
import com.spectrace.agent.tool.CollectTools;
import com.spectrace.common.AiCallFailure;
import com.spectrace.common.AiCallScene;
import com.spectrace.common.TaskType;
import com.spectrace.dto.AgentCallbackRequest;
import com.spectrace.dto.AgentTaskPayload;
import com.spectrace.dto.DeviceVariantDTO;
import com.spectrace.dto.ResolvedModel;
import com.spectrace.dto.SeriesModelDTO;
import com.spectrace.entity.ParamDef;
import com.spectrace.service.AgentTaskLogService;
import com.spectrace.service.AiCallRecorder;
import com.spectrace.service.AiCallTarget;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.OkHttpTransport;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 手机参数采集 Agent（AgentScope Java 2.0 · Harness 模式），内嵌于业务后端同进程运行。
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
    private final AgentTaskLogService taskLogService;
    private final AiCallRecorder aiCallRecorder;
    private final ObjectMapper objectMapper;

    /**
     * 系列发现模式的系统提示：显式覆盖工作区 persona 的参数抽取输出格式，
     * 避免模型沿用参数 Schema 结构输出一堆空参数。
     */
    private static final String DISCOVERY_SYS_PROMPT = """
            本次任务是「系列机型发现」，不抽取任何参数，只回答：指定手机系列下官方发布的全部机型清单。

            工作方式：
            1. 先用 web_search 搜索该系列的机型清单（品牌官网 / 发布会通稿 / 权威数码媒体优先）。
            2. 结果不全或存疑时，用 fetch_page 打开列表页核对。
            3. 宁可少列不要编造；尚未发布或只有传闻的机型不要列入。
            4. 同一机型的运营商 / 地区专属小改款不单列，并入同名机型。

            输出要求（极其重要）：
            - 只输出一个 JSON 对象，不要任何解释文字、不要 Markdown 代码块。
            - JSON 结构固定为：

            {"brandName":"品牌名称","seriesName":"系列名称","sourceUrl":"主要来源链接","confidence":"high | medium | low","notes":"对存疑项的说明","models":[{"name":"机型完整官方名称","releaseDate":"YYYY-MM-DD 或 null"}]}

            - models 按发布时间从新到旧排列；name 用完整官方名称（如 iPhone 17 Pro Max），不要缩写。
            - releaseDate 无法确认具体日期时置 null，不要猜测。
            """;

    /**
     * 执行一次采集，返回可写入待审核库的结构化结果。
     *
     * <p>采集期间把任务 id 绑定到当前线程，工具调用（搜索 / 抓网页）才能把过程日志记到对应任务上。
     */
    public AgentCallbackRequest collect(AgentTaskPayload payload, List<ParamDef> schemaDefs,
                                        ResolvedModel aiConfig) {
        TaskLogContext.bind(payload.getTaskId());
        CrawlSession.begin();
        try {
            if (TaskType.parse(payload.getTaskType()) == TaskType.SERIES_DISCOVERY) {
                taskLogService.info(payload.getTaskId(), String.format("开始系列发现：%s %s，AI 模型 %s/%s",
                        nullToEmpty(payload.getBrandName()), nullToEmpty(payload.getModelName()),
                        aiConfig == null ? "-" : aiConfig.getProvider(),
                        aiConfig == null ? "-" : aiConfig.getModel()));
                return doDiscover(payload, aiConfig);
            }
            taskLogService.info(payload.getTaskId(), String.format("开始采集：%s %s，AI 模型 %s/%s，参数定义 %d 项",
                    nullToEmpty(payload.getBrandName()), nullToEmpty(payload.getModelName()),
                    aiConfig == null ? "-" : aiConfig.getProvider(),
                    aiConfig == null ? "-" : aiConfig.getModel(),
                    schemaDefs == null ? 0 : schemaDefs.size()));
            return doCollect(payload, schemaDefs, aiConfig);
        } finally {
            CrawlSession.end();
            TaskLogContext.clear();
        }
    }

    /**
     * 系列机型发现：搜索系列下的机型清单，结果由管理员勾选后创建草稿机型档案。
     * 独立提示词与输出结构，与参数抓取互不复用。
     */
    private AgentCallbackRequest doDiscover(AgentTaskPayload payload, ResolvedModel aiConfig) {
        String seriesName = payload.getModelName() == null ? "" : payload.getModelName();
        String brandName = payload.getBrandName() == null ? "" : payload.getBrandName();
        try {
            // 每次模型请求都落一条调用记录；scene 与 taskId 让统计能回溯到具体任务
            Model model = new RecordingChatModel(buildModel(aiConfig),
                    AiCallTarget.chat(aiConfig, props.getDashscopeModel()), aiCallRecorder,
                    AiCallScene.AGENT_DISCOVERY, String.valueOf(payload.getTaskId()));

            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(collectTools);

            String query = (brandName + " " + seriesName + " 系列 全部机型 型号 列表").trim();
            String prompt = "请检索品牌「" + brandName + "」的「" + seriesName
                    + "」系列包含的全部机型清单，按系统提示的 JSON 结构输出。";

            // 每个任务独立的会话身份，任务之间互不共享上下文 / 记忆
            String identity = "task-" + payload.getTaskId();
            RuntimeContext ctx = RuntimeContext.builder()
                    .userId(identity)
                    .sessionId(identity)
                    .build();

            String text;
            try (HarnessAgent agent = HarnessAgent.builder()
                    .name("phone_series_discovery")
                    .agentId("phone_series_discovery")
                    .sysPrompt(DISCOVERY_SYS_PROMPT)
                    .model(model)
                    .toolkit(toolkit)
                    .workspace(Path.of(props.getWorkspace()))
                    // 清单发现通常 1~2 次搜索即可收敛，迭代上限低于参数抓取
                    .maxIters(10)
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
            List<SeriesModelDTO> models = new ArrayList<>();
            // 兼容两种输出：对象数组（标准）与纯字符串数组（模型偷懒时）
            for (JsonNode item : data.path("models")) {
                String name = item.isTextual() ? item.asText() : item.path("name").asText("");
                if (name.isBlank()) {
                    continue;
                }
                SeriesModelDTO dto = new SeriesModelDTO();
                dto.setName(name.trim());
                dto.setReleaseDate(item.isTextual() ? null : nullableText(item, "releaseDate"));
                models.add(dto);
            }
            if (models.isEmpty()) {
                throw new IllegalArgumentException("模型未返回任何机型");
            }

            AgentCallbackRequest result = new AgentCallbackRequest();
            result.setTaskId(payload.getTaskId());
            result.setModelName(textOrFallback(data, "seriesName", seriesName));
            result.setBrandName(textOrFallback(data, "brandName", brandName.isEmpty() ? null : brandName));
            result.setSourceUrl(nullableText(data, "sourceUrl"));
            result.setStatus("SUCCESS");
            result.setSeriesModels(models);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("queries", List.of(query));
            summary.put("confidence", nullableText(data, "confidence"));
            summary.put("notes", nullableText(data, "notes"));
            appendFailedSources(summary);
            result.setLogs(objectMapper.writeValueAsString(summary));
            taskLogService.info(payload.getTaskId(), "发现完成：共 " + models.size() + " 个机型");
            return result;
        } catch (Exception e) {
            log.error("系列发现失败 taskId={}", payload.getTaskId(), e);
            String message = e.getMessage() == null ? "" : e.getMessage();
            markModelOutputFailure(AiCallScene.AGENT_DISCOVERY, payload.getTaskId(), message);
            String error = message.contains("Timeout") || message.contains("timeout")
                    ? "发现超时（>" + props.getTaskTimeoutSeconds() + "s），" + timeoutHint(payload.getTaskId())
                    : e.getClass().getSimpleName() + ": " + message;
            taskLogService.error(payload.getTaskId(), "系列发现异常：" + error);
            AgentCallbackRequest result = new AgentCallbackRequest();
            result.setTaskId(payload.getTaskId());
            result.setModelName(seriesName);
            result.setBrandName(brandName.isEmpty() ? null : brandName);
            result.setStatus("FAILED");
            result.setError(error);
            return result;
        }
    }

    private AgentCallbackRequest doCollect(AgentTaskPayload payload, List<ParamDef> schemaDefs,
                                           ResolvedModel aiConfig) {
        String modelName = payload.getModelName() == null ? "" : payload.getModelName();
        String brandName = payload.getBrandName() == null ? "" : payload.getBrandName();

        try {
            Model model = new RecordingChatModel(buildModel(aiConfig),
                    AiCallTarget.chat(aiConfig, props.getDashscopeModel()), aiCallRecorder,
                    AiCallScene.AGENT_SCRAPE, String.valueOf(payload.getTaskId()));

            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(collectTools);

            // persona / 输出规约由工作区 AGENTS.md 提供；此处仅注入随任务变化的参数 Schema
            String sysPrompt = schemaPrompt(schemaDefs);
            String query = (brandName + " " + modelName + " 手机 完整参数 规格 官网").trim();
            String prompt = "请检索并抽取机型「" + modelName + "」"
                    + (brandName.isEmpty() ? "" : "（品牌：" + brandName + "）")
                    + " 的全部可获取参数，并按系统 Schema 输出 JSON。"
                    + "同时必须列出该机型全部存储版本及其官方发布价，写入 variants 数组"
                    + "（每项包含 storage、ram、price、currency、priceType）。";

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
            return buildCollectResult(payload, data, schemaDefs, query, null);
        } catch (Exception e) {
            log.error("采集失败 taskId={}", payload.getTaskId(), e);
            String message = e.getMessage() == null ? "" : e.getMessage();
            markModelOutputFailure(AiCallScene.AGENT_SCRAPE, payload.getTaskId(), message);
            String error = message.contains("Timeout") || message.contains("timeout")
                    ? "抓取超时（>" + props.getTaskTimeoutSeconds() + "s），" + timeoutHint(payload.getTaskId())
                    : e.getClass().getSimpleName() + ": " + message;
            taskLogService.error(payload.getTaskId(), "采集异常：" + error);
            // 失败不等于白干：已抓正文还在会话缓存里，先抢救再决定是否判失败。
            // 覆盖所有失败类型（超时、模型未输出 JSON、解析异常等），由抢救方法自身在无正文时跳过。
            try {
                AgentCallbackRequest salvaged = salvageFromFetchedPages(payload, schemaDefs, aiConfig, error);
                if (salvaged != null) {
                    return salvaged;
                }
            } catch (Exception salvageError) {
                // 抢救自身出错（例如结果序列化失败）时退回普通失败分支，不能把异常抛给消费线程
                log.warn("采集抢救流程异常 taskId={}：{}", payload.getTaskId(), salvageError.getMessage());
                taskLogService.warn(payload.getTaskId(),
                        "采集抢救流程异常：" + salvageError.getClass().getSimpleName()
                                + ": " + salvageError.getMessage());
            }
            AgentCallbackRequest result = new AgentCallbackRequest();
            result.setTaskId(payload.getTaskId());
            result.setModelName(modelName);
            result.setBrandName(brandName.isEmpty() ? null : brandName);
            result.setStatus("FAILED");
            result.setError(error);
            return result;
        }
    }

    /** 抢救语料单页上限：二次请求要快，单页过长只会让抢救本身也超时 */
    private static final int SALVAGE_MAX_PAGE_CHARS = 6000;
    /** 抢救语料总上限：控制 prompt 体积 */
    private static final int SALVAGE_MAX_TOTAL_CHARS = 24000;

    /**
     * 把模型输出的 JSON 收敛成回调结果：参数白名单过滤、版本与置信度解析、摘要与来源记录。
     * 正常采集与超时抢救共用同一条收敛逻辑，避免两条路径落库口径不一致。
     */
    private AgentCallbackRequest buildCollectResult(AgentTaskPayload payload, JsonNode data,
                                                    List<ParamDef> schemaDefs, String query,
                                                    String extraNote) throws Exception {
        String modelName = payload.getModelName() == null ? "" : payload.getModelName();
        String brandName = payload.getBrandName() == null ? "" : payload.getBrandName();

        Set<String> allowed = schemaDefs.stream()
                .map(ParamDef::getParamKey)
                .collect(Collectors.toSet());
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> dropped = new ArrayList<>();
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
        // persona 要求模型在 JSON 顶层给出官方产品图直链，此前漏接该字段，导致 T90 下载链路永远拿不到图
        String imageUrl = nullableText(data, "imageUrl");
        result.setImageUrl(imageUrl);
        // 产品图是可选字段，缺省时必须留痕，否则线上无法区分「模型没给」与「解析丢了」
        taskLogService.info(payload.getTaskId(),
                imageUrl == null ? "模型未返回产品图直链" : "产品图直链：" + imageUrl);
        result.setStatus("SUCCESS");
        result.setParams(params);
        result.setParamConfidence(parseParamConfidence(data, allowed));
        result.setVariants(parseVariants(data.path("variants")));
        // 用可变 Map 而非 Map.of：模型不返回 confidence / notes 时值为 null，Map.of 会直接抛 NPE
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("queries", List.of(query));
        summary.put("confidence", nullableText(data, "confidence"));
        summary.put("notes", extraNote == null ? nullableText(data, "notes") : extraNote);
        summary.put("droppedUnknownKeys", dropped);
        appendFailedSources(summary);
        result.setLogs(objectMapper.writeValueAsString(summary));
        taskLogService.info(payload.getTaskId(),
                "抽取完成：识别参数 " + params.size() + " 项，丢弃未知参数 " + dropped.size() + " 项");
        return result;
    }

    /**
     * 采集抢救：用本任务已抓取的网页正文再发一次「只输出 JSON」的短请求。
     *
     * <p>只抢救一次、不再联网，目的是把已经付出的抓取成本转成可审核的结果；
     * 不论失败原因是超时、模型未输出 JSON 还是解析异常，只要抓到了正文就值得复用。
     * 二次抽取也失败时不再重试，转待审核并保留来源清单，由人工补齐参数。
     *
     * @return 有可复用正文时返回结果（成功或空参数待人工），无可复用正文时返回 null 交回失败分支
     */
    private AgentCallbackRequest salvageFromFetchedPages(AgentTaskPayload payload, List<ParamDef> schemaDefs,
                                                         ResolvedModel aiConfig, String failureReason) throws Exception {
        Map<String, String> pages = CrawlSession.fetchedPages();
        if (pages.isEmpty()) {
            taskLogService.warn(payload.getTaskId(), "采集抢救跳过：本次任务没有成功抓取任何网页正文，无可复用素材");
            return null;
        }
        String corpus = buildSalvageCorpus(pages);
        List<String> sources = List.copyOf(pages.keySet());
        taskLogService.warn(payload.getTaskId(), "采集抢救启动：复用已抓取的 " + pages.size()
                + " 篇正文（" + corpus.length() + " 字）二次抽取，不再联网");

        String modelName = payload.getModelName() == null ? "" : payload.getModelName();
        String brandName = payload.getBrandName() == null ? "" : payload.getBrandName();
        String query = (brandName + " " + modelName + " 手机 完整参数 规格 官网").trim();
        // 抢救是最后一次机会，给足时间但不占用任务预算（任务预算此时已耗尽）
        long salvageSeconds = Math.max(60, props.getModelTimeoutSeconds() * 2L);
        try {
            Model model = new RecordingChatModel(buildModel(aiConfig),
                    AiCallTarget.chat(aiConfig, props.getDashscopeModel()), aiCallRecorder,
                    AiCallScene.AGENT_SCRAPE, String.valueOf(payload.getTaskId()));
            String prompt = "原采集任务因异常中断（" + failureReason + "），现在只能基于下面已抓取的网页正文抽取参数。\n"
                    + "不要联网、不要解释过程，直接输出 JSON 对象；查不到的参数置 null，不要编造。\n\n"
                    + "【已抓取网页正文】\n" + corpus;
            String identity = "task-" + payload.getTaskId() + "-salvage";
            RuntimeContext ctx = RuntimeContext.builder()
                    .userId(identity)
                    .sessionId(identity)
                    .build();
            String text;
            try (HarnessAgent agent = HarnessAgent.builder()
                    .name("phone_spec_salvage")
                    .agentId("phone_spec_salvage")
                    .sysPrompt(schemaPrompt(schemaDefs))
                    .model(model)
                    .workspace(Path.of(props.getWorkspace()))
                    // 全部素材已在 prompt 内，不允许再迭代
                    .maxIters(2)
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
                        .block(Duration.ofSeconds(salvageSeconds));
                text = msg == null ? "" : msg.getTextContent();
            }
            JsonNode data = parseJson(text);
            String note = "任务失败后改为仅基于已抓取的 " + pages.size()
                    + " 篇网页正文二次抽取，未做多来源交叉核验，请重点复核；原始失败原因：" + failureReason;
            AgentCallbackRequest result = buildCollectResult(payload, data, schemaDefs, query, note);
            taskLogService.info(payload.getTaskId(), "采集抢救成功：从已抓正文中抽取到参数 "
                    + result.getParams().size() + " 项");
            return result;
        } catch (Exception e) {
            log.warn("采集抢救失败 taskId={}：{}", payload.getTaskId(), e.getMessage());
            taskLogService.warn(payload.getTaskId(), "采集抢救失败（" + e.getClass().getSimpleName() + ": "
                    + e.getMessage() + "），转待审核并保留已抓取来源，需人工补齐参数");
            AgentCallbackRequest result = new AgentCallbackRequest();
            result.setTaskId(payload.getTaskId());
            result.setModelName(modelName);
            result.setBrandName(brandName.isEmpty() ? null : brandName);
            result.setSourceUrl(sources.get(0));
            result.setStatus("SUCCESS");
            result.setParams(new LinkedHashMap<>());
            result.setVariants(new ArrayList<>());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("queries", List.of(query));
            summary.put("notes", "任务失败且二次抽取失败（" + failureReason
                    + "），此处仅保留已抓取来源，参数需人工补齐");
            summary.put("sources", sources);
            appendFailedSources(summary);
            result.setLogs(objectMapper.writeValueAsString(summary));
            return result;
        }
    }

    /** 拼接抢救语料：按抓取顺序截断并封顶，避免二次请求本身又超时。 */
    private String buildSalvageCorpus(Map<String, String> pages) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> page : pages.entrySet()) {
            if (sb.length() >= SALVAGE_MAX_TOTAL_CHARS) {
                break;
            }
            String body = page.getValue() == null ? "" : page.getValue();
            if (body.length() > SALVAGE_MAX_PAGE_CHARS) {
                body = body.substring(0, SALVAGE_MAX_PAGE_CHARS);
            }
            sb.append("\n--- 来源：").append(page.getKey()).append(" ---\n").append(body).append('\n');
        }
        return sb.toString();
    }

    /** 采集期间被熔断的域名写入摘要，审核端能看到「结果未采用这些站点」。 */
    private static void appendFailedSources(Map<String, Object> summary) {
        List<String> failed = CrawlSession.failedDomains();
        if (!failed.isEmpty()) {
            summary.put("failedSources", failed);
        }
    }

    /**
     * 超时原因初判：结合过程日志区分「模型 API 无响应」与「工具链路卡住」，
     * 否则管理员只看到超时，不知道该换模型还是查网络。
     */
    private String timeoutHint(Long taskId) {
        try {
            int steps = taskLogService.list(taskId, null).size();
            // 首行是「开始采集」，没有后续行说明模型首轮调用都没返回
            return steps <= 1
                    ? "期间无任何工具调用记录，疑似模型 API 无响应或响应极慢，建议重试时更换模型"
                    : "超时前已有 " + (steps - 1) + " 条过程日志，可在任务日志中定位卡住的环节";
        } catch (Exception e) {
            return "原因未知";
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

        // 单次模型请求超时：模型 API 挂死时快速失败，而不是拖到外层任务超时才整体失败
        HttpTransportConfig transport = HttpTransportConfig.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(props.getModelTimeoutSeconds()))
                .build();

        if ("dashscope".equals(provider) && !looksOpenAiCompatible(baseUrl)) {
            var builder = DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .stream(false)
                    .formatter(new DashScopeChatFormatter())
                    .httpTransport(new OkHttpTransport(transport));
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
                .formatter(new OpenAIChatFormatter())
                .httpTransport(new OkHttpTransport(transport));
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

    /** 解析模型输出的 variants 数组，价格无法识别时保留版本但不填价格。 */
    private List<DeviceVariantDTO> parseVariants(JsonNode node) {
        List<DeviceVariantDTO> variants = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return variants;
        }
        for (JsonNode item : node) {
            String storage = nullableText(item, "storage");
            if (storage == null || storage.isBlank()) {
                continue;
            }
            DeviceVariantDTO dto = new DeviceVariantDTO();
            dto.setStorage(storage.trim());
            dto.setRam(nullableText(item, "ram"));
            dto.setPrice(parsePrice(item.get("price")));
            String currency = nullableText(item, "currency");
            dto.setCurrency(currency == null || currency.isBlank() ? "CNY" : currency.trim().toUpperCase());
            String priceType = nullableText(item, "priceType");
            dto.setPriceType(priceType == null || priceType.isBlank() ? "LAUNCH" : priceType.trim().toUpperCase());
            dto.setSortOrder(variants.size() + 1);
            variants.add(dto);
        }
        return variants;
    }

    /**
     * 解析置信度：优先参数级 {@code paramConfidence} 对象，
     * 缺失时用整次抓取的 {@code confidence} 作为所有参数的统一值。
     */
    private Map<String, Double> parseParamConfidence(JsonNode data, Set<String> allowedKeys) {
        Map<String, Double> confidence = new LinkedHashMap<>();
        JsonNode node = data.path("paramConfidence");
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                Double value = toConfidence(entry.getValue());
                if (allowedKeys.contains(entry.getKey()) && value != null) {
                    confidence.put(entry.getKey(), value);
                }
            });
        }
        if (confidence.isEmpty()) {
            Double overall = toConfidence(data.path("confidence"));
            if (overall != null) {
                allowedKeys.forEach(key -> confidence.put(key, overall));
            }
        }
        return confidence;
    }

    /**
     * 兼容三种置信度写法：0~1 数值、百分数、以及 persona 约定的 high/medium/low 文字等级。
     */
    private Double toConfidence(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        Double value = null;
        if (node.isNumber()) {
            value = node.asDouble();
        } else if (node.isTextual()) {
            String raw = node.asText("").replace("%", "").trim();
            switch (raw.toLowerCase(java.util.Locale.ROOT)) {
                case "high", "高" -> value = 0.9;
                case "medium", "中" -> value = 0.7;
                case "low", "低" -> value = 0.4;
                default -> {
                    try {
                        value = Double.parseDouble(raw);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        if (value > 1 && value <= 100) {
            value = value / 100;
        }
        return Math.max(0, Math.min(1, value));
    }

    private java.math.BigDecimal parsePrice(JsonNode priceNode) {
        if (priceNode == null || priceNode.isNull()) {
            return null;
        }
        if (priceNode.isNumber()) {
            return priceNode.decimalValue();
        }
        String raw = priceNode.asText("").replaceAll("[^0-9.]", "");
        if (raw.isBlank()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 把后台的参数定义转成给 LLM 的 Schema 说明。 */
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
        try {
            return objectMapper.readTree(cleaned.substring(start, end + 1));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("模型未输出合法 JSON：" + e.getOriginalMessage(), e);
        }
    }

    /**
     * 传输层成功但输出无法解析属于业务失败：把该任务最近一次调用标记为失败，
     * 而不是新增一条记录，保证成功率的分母仍是真实调用次数。
     */
    private void markModelOutputFailure(AiCallScene scene, Long taskId, String message) {
        if (message == null || !message.contains("模型未输出")) {
            return;
        }
        aiCallRecorder.markBusinessFailure(scene, String.valueOf(taskId),
                AiCallFailure.INVALID_RESPONSE.name(), message);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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
