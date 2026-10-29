package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.dto.AiModelVO;
import com.spectrace.dto.ConnectivityTestVO;
import com.spectrace.dto.ImportModelsRequest;
import com.spectrace.dto.ResolvedModel;
import com.spectrace.entity.AiConfig;
import com.spectrace.entity.AiModel;
import com.spectrace.mapper.AiConfigMapper;
import com.spectrace.mapper.AiModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigService {

    private final AiConfigMapper configMapper;
    private final AiModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ---------------- 连接 ----------------

    public List<AiConfig> listConfigs() {
        return configMapper.selectList(Wrappers.<AiConfig>lambdaQuery().orderByAsc(AiConfig::getId));
    }

    public AiConfig saveConfig(AiConfig config) {
        if (config.getEnabled() == null) {
            config.setEnabled(1);
        }
        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
                AiConfig existing = configMapper.selectById(config.getId());
                if (existing != null) {
                    config.setApiKey(existing.getApiKey());
                }
            }
            configMapper.updateById(config);
        }
        // Key 属敏感信息，只记配置元数据与是否更换，不落 Key 内容
        log.info("AI 连接已保存: id={} name={} provider={} key{}",
                config.getId(), config.getName(), config.getProvider(),
                config.getApiKey() == null || config.getApiKey().isEmpty() ? "未变更" : "已更新");
        return config;
    }

    @Transactional
    public void deleteConfig(Long id) {
        configMapper.deleteById(id);
        modelMapper.delete(Wrappers.<AiModel>lambdaQuery().eq(AiModel::getConfigId, id));
        log.info("AI 连接已删除（连同其下模型）: id={}", id);
    }

    // ---------------- 模型 ----------------

    public List<AiModelVO> listModels(Long configId) {
        var query = Wrappers.<AiModel>lambdaQuery()
                .orderByDesc(AiModel::getIsDefault)
                .orderByAsc(AiModel::getId);
        if (configId != null) {
            query.eq(AiModel::getConfigId, configId);
        }
        List<AiModel> models = modelMapper.selectList(query);
        return toVO(models);
    }

    public List<AiModelVO> listEnabledModels() {
        List<AiModel> models = modelMapper.selectList(Wrappers.<AiModel>lambdaQuery()
                .eq(AiModel::getEnabled, 1)
                .orderByDesc(AiModel::getIsDefault)
                .orderByAsc(AiModel::getId));
        return toVO(models);
    }

    /**
     * 拉取远端模型列表：调用 {baseUrl}/models（OpenAI 兼容），
     * 兼容 Ollama 的 /api/tags（解析 models[].name）。
     */
    public List<String> pullRemoteModels(Long configId) {
        AiConfig config = configMapper.selectById(configId);
        if (config == null) {
            throw new BizException("连接不存在");
        }
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BizException("请先为该连接配置接口地址");
        }
        String apiKey = config.getApiKey() == null ? "" : config.getApiKey();
        String url = baseUrl.replaceAll("/+$", "") + "/models";

        JsonNode root = httpGetJson(url, apiKey);
        List<String> result = new ArrayList<>();
        if (root != null) {
            if (root.has("data") && root.get("data").isArray()) {
                root.get("data").forEach(n -> {
                    JsonNode id = n.get("id");
                    if (id != null) {
                        result.add(id.asText());
                    }
                });
            } else if (root.has("models") && root.get("models").isArray()) {
                root.get("models").forEach(n -> {
                    JsonNode name = n.get("name");
                    if (name != null) {
                        result.add(name.asText());
                    }
                });
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    /** 批量导入模型到指定连接，首个导入的模型自动设为默认（若当前无默认）。 */
    @Transactional
    public List<AiModelVO> importModels(Long configId, ImportModelsRequest req) {
        AiConfig config = configMapper.selectById(configId);
        if (config == null) {
            throw new BizException("连接不存在");
        }
        if (req.getModels() == null || req.getModels().isEmpty()) {
            throw new BizException("请选择要导入的模型");
        }
        // 默认模型全局唯一（resolve 时全表取第一个），跨连接判断，避免每个连接各有一个「默认」
        boolean hasDefault = modelMapper.selectCount(Wrappers.<AiModel>lambdaQuery()
                .eq(AiModel::getIsDefault, 1)) > 0;

        for (String name : req.getModels()) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Long exists = modelMapper.selectCount(Wrappers.<AiModel>lambdaQuery()
                    .eq(AiModel::getConfigId, configId)
                    .eq(AiModel::getModel, name));
            if (exists != null && exists > 0) {
                continue;
            }
            AiModel model = new AiModel();
            model.setConfigId(configId);
            model.setModel(name);
            model.setTemperature(req.getTemperature() == null ? 0.2 : req.getTemperature());
            model.setEnabled(1);
            model.setIsDefault(hasDefault ? 0 : 1);
            modelMapper.insert(model);
            hasDefault = true;
        }
        return listModels(configId);
    }

    @Transactional
    public void setDefaultModel(Long modelId) {
        AiModel target = modelMapper.selectById(modelId);
        if (target == null) {
            throw new BizException("模型不存在");
        }
        AiModel reset = new AiModel();
        reset.setIsDefault(0);
        modelMapper.update(reset, Wrappers.<AiModel>lambdaUpdate().eq(AiModel::getIsDefault, 1));

        AiModel update = new AiModel();
        update.setId(modelId);
        update.setIsDefault(1);
        update.setEnabled(1);
        modelMapper.updateById(update);
    }

    public void updateModel(Long modelId, Double temperature, Integer enabled) {
        AiModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BizException("模型不存在");
        }
        if (temperature != null) {
            model.setTemperature(temperature);
        }
        if (enabled != null) {
            model.setEnabled(enabled);
        }
        modelMapper.updateById(model);
    }

    public void deleteModel(Long modelId) {
        modelMapper.deleteById(modelId);
    }

    /**
     * 连通性测试：向 {baseUrl}/chat/completions 发一条最小对话请求，验证 Key 能真正调用该模型。
     * 配置类错误（连接缺失 / 未配地址 / 无可用模型）抛业务异常；请求层面的失败返回 success=false，
     * 前端拿到结构化结果展示延迟或错误详情，而不是一律弹错误提示。
     */
    public ConnectivityTestVO testConnection(Long configId, String modelName) {
        AiConfig config = configMapper.selectById(configId);
        if (config == null) {
            throw new BizException("连接不存在");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new BizException("请先为该连接配置接口地址");
        }
        String model = resolveTestModel(configId, modelName);
        String url = config.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        String apiKey = config.getApiKey() == null ? "" : config.getApiKey();

        ConnectivityTestVO vo = new ConnectivityTestVO();
        vo.setModel(model);
        long start = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", "ping")),
                    "max_tokens", 16,
                    "stream", false));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            vo.setLatencyMs(System.currentTimeMillis() - start);
            if (resp.statusCode() >= 400) {
                vo.setSuccess(false);
                vo.setError("HTTP " + resp.statusCode() + " " + truncate(resp.body()));
                log.warn("连通性测试失败 {} model={}: HTTP {}", url, model, resp.statusCode());
                return vo;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            String reply = root.path("choices").path(0).path("message").path("content").asText("");
            vo.setSuccess(true);
            vo.setReply(truncate(reply));
            log.info("连通性测试成功 {} model={} latency={}ms", url, model, vo.getLatencyMs());
            return vo;
        } catch (Exception e) {
            vo.setSuccess(false);
            vo.setLatencyMs(System.currentTimeMillis() - start);
            vo.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("连通性测试异常 {} model={}: {}", url, model, e.getMessage());
            return vo;
        }
    }

    /** 测试模型解析：未指定时回落到该连接下第一个启用模型（默认优先）。 */
    private String resolveTestModel(Long configId, String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            return modelName.trim();
        }
        AiModel model = modelMapper.selectOne(Wrappers.<AiModel>lambdaQuery()
                .eq(AiModel::getConfigId, configId)
                .eq(AiModel::getEnabled, 1)
                .orderByDesc(AiModel::getIsDefault)
                .orderByAsc(AiModel::getId)
                .last("LIMIT 1"));
        if (model == null) {
            throw new BizException("该连接下暂无启用模型，请先导入或在测试时指定模型名");
        }
        return model.getModel();
    }

    // ---------------- 解析（供 Agent） ----------------

    /** 解析实际调用参数：指定 modelId 则用该模型，否则用默认模型。 */
    public ResolvedModel resolve(Long modelId) {
        AiModel model = modelId != null ? modelMapper.selectById(modelId) : null;
        if (model == null) {
            model = modelMapper.selectOne(Wrappers.<AiModel>lambdaQuery()
                    .eq(AiModel::getIsDefault, 1)
                    .eq(AiModel::getEnabled, 1)
                    .last("LIMIT 1"));
        }
        if (model == null) {
            model = modelMapper.selectOne(Wrappers.<AiModel>lambdaQuery()
                    .eq(AiModel::getEnabled, 1)
                    .orderByAsc(AiModel::getId)
                    .last("LIMIT 1"));
        }
        if (model == null) {
            throw new BizException("尚未配置可用的 AI 模型");
        }
        AiConfig config = configMapper.selectById(model.getConfigId());
        if (config == null) {
            throw new BizException("模型所属连接不存在");
        }
        ResolvedModel resolved = new ResolvedModel();
        resolved.setConfigId(config.getId());
        resolved.setConfigName(config.getName());
        resolved.setModelId(model.getId());
        resolved.setProvider(config.getProvider());
        resolved.setBaseUrl(config.getBaseUrl());
        resolved.setApiKey(config.getApiKey());
        resolved.setModel(model.getModel());
        resolved.setTemperature(model.getTemperature());
        return resolved;
    }

    // ---------------- 内部 ----------------

    private List<AiModelVO> toVO(List<AiModel> models) {
        if (models.isEmpty()) {
            return List.of();
        }
        Map<Long, AiConfig> configMap = configMapper.selectBatchIds(
                        models.stream().map(AiModel::getConfigId).distinct().toList()).stream()
                .collect(Collectors.toMap(AiConfig::getId, Function.identity()));
        return models.stream().map(m -> {
            AiModelVO vo = new AiModelVO();
            vo.setId(m.getId());
            vo.setConfigId(m.getConfigId());
            vo.setModel(m.getModel());
            vo.setTemperature(m.getTemperature());
            vo.setIsDefault(m.getIsDefault());
            vo.setEnabled(m.getEnabled());
            AiConfig c = configMap.get(m.getConfigId());
            if (c != null) {
                vo.setConfigName(c.getName());
                vo.setProvider(c.getProvider());
                vo.setBaseUrl(c.getBaseUrl());
            }
            return vo;
        }).toList();
    }

    private JsonNode httpGetJson(String url, String apiKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .GET();
            if (!apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new BizException("拉取模型失败：HTTP " + resp.statusCode() + " " + truncate(resp.body()));
            }
            return objectMapper.readTree(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("拉取远端模型失败 {}: {}", url, e.getMessage());
            throw new BizException("拉取模型失败：" + e.getMessage());
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
