package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.BizException;
import com.spectrace.entity.AiImageProvider;
import com.spectrace.mapper.AiImageProviderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 绘图供应商的维护与解析。
 *
 * <p>默认供应商全局唯一：绘图请求没有「按模型指定」的需求，多一个指定入口只会让访客侧多一层选择。
 * 新建第一条启用供应商时自动设为默认，删除默认供应商后把默认让给剩余的第一条，避免出现
 * 「有可用供应商但没有任何默认」的悬空状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageProviderService {

    private final AiImageProviderMapper providerMapper;

    public List<AiImageProvider> list() {
        return providerMapper.selectList(Wrappers.<AiImageProvider>lambdaQuery()
                .orderByDesc(AiImageProvider::getIsDefault)
                .orderByAsc(AiImageProvider::getId));
    }

    public AiImageProvider save(AiImageProvider provider) {
        if (provider.getId() == null) {
            validate(provider);
            provider.setEnabled(provider.getEnabled() == null ? 1 : provider.getEnabled());
            // 默认只由 setDefault 维护，新增时统一按「非默认」落库再决定是否晋升
            provider.setIsDefault(0);
            providerMapper.insert(provider);
            if (providerMapper.selectCount(Wrappers.<AiImageProvider>lambdaQuery()
                    .eq(AiImageProvider::getIsDefault, 1)) == 0) {
                setDefault(provider.getId());
            }
        } else {
            AiImageProvider existing = providerMapper.selectById(provider.getId());
            if (existing == null) {
                throw new BizException("绘图供应商不存在");
            }
            // 未提交的字段沿用原值：编辑弹窗只改画质时，不该因为没带接口地址而被校验拦下
            if (!StringUtils.hasText(provider.getName())) {
                provider.setName(existing.getName());
            }
            if (!StringUtils.hasText(provider.getBaseUrl())) {
                provider.setBaseUrl(existing.getBaseUrl());
            }
            if (!StringUtils.hasText(provider.getModel())) {
                provider.setModel(existing.getModel());
            }
            if (!StringUtils.hasText(provider.getSize())) {
                provider.setSize(existing.getSize());
            }
            if (provider.getQuality() == null) {
                provider.setQuality(existing.getQuality());
            }
            if (provider.getEnabled() == null) {
                provider.setEnabled(existing.getEnabled());
            }
            // Key 属敏感信息：编辑时留空表示沿用原值
            if (!StringUtils.hasText(provider.getApiKey())) {
                provider.setApiKey(existing.getApiKey());
            }
            validate(provider);
            provider.setIsDefault(null);
            providerMapper.updateById(provider);
        }
        log.info("AI 绘图供应商已保存: id={} name={} model={} key{}",
                provider.getId(), provider.getName(), provider.getModel(),
                StringUtils.hasText(provider.getApiKey()) ? "已配置" : "未配置");
        // 回读一次：新增后可能被晋升为默认、地址也可能被归一化，返回体应与库内一致
        AiImageProvider latest = providerMapper.selectById(provider.getId());
        return latest == null ? provider : latest;
    }

    @Transactional
    public void delete(Long id) {
        AiImageProvider existing = providerMapper.selectById(id);
        if (existing == null) {
            throw new BizException("绘图供应商不存在");
        }
        providerMapper.deleteById(id);
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            AiImageProvider next = providerMapper.selectOne(Wrappers.<AiImageProvider>lambdaQuery()
                    .eq(AiImageProvider::getEnabled, 1)
                    .orderByAsc(AiImageProvider::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                setDefault(next.getId());
            }
        }
        log.info("AI 绘图供应商已删除: id={} name={}", id, existing.getName());
    }

    @Transactional
    public void setDefault(Long id) {
        AiImageProvider target = providerMapper.selectById(id);
        if (target == null) {
            throw new BizException("绘图供应商不存在");
        }
        AiImageProvider reset = new AiImageProvider();
        reset.setIsDefault(0);
        providerMapper.update(reset, Wrappers.<AiImageProvider>lambdaUpdate()
                .eq(AiImageProvider::getIsDefault, 1));

        AiImageProvider update = new AiImageProvider();
        update.setId(id);
        update.setIsDefault(1);
        update.setEnabled(1);
        providerMapper.updateById(update);
        log.info("AI 绘图默认供应商已切换: id={} name={}", id, target.getName());
    }

    /** 解析本次使用的供应商：默认优先，其次第一条启用记录。 */
    public AiImageProvider resolve() {
        AiImageProvider provider = providerMapper.selectOne(Wrappers.<AiImageProvider>lambdaQuery()
                .eq(AiImageProvider::getIsDefault, 1)
                .eq(AiImageProvider::getEnabled, 1)
                .last("LIMIT 1"));
        if (provider == null) {
            provider = providerMapper.selectOne(Wrappers.<AiImageProvider>lambdaQuery()
                    .eq(AiImageProvider::getEnabled, 1)
                    .orderByAsc(AiImageProvider::getId)
                    .last("LIMIT 1"));
        }
        if (provider == null) {
            throw new BizException("尚未配置可用的 AI 绘图供应商");
        }
        return provider;
    }

    public boolean configured() {
        return providerMapper.selectCount(Wrappers.<AiImageProvider>lambdaQuery()
                .eq(AiImageProvider::getEnabled, 1)) > 0;
    }

    private void validate(AiImageProvider provider) {
        if (!StringUtils.hasText(provider.getName())) {
            throw new BizException("请填写供应商名称");
        }
        if (!StringUtils.hasText(provider.getBaseUrl())) {
            throw new BizException("请填写接口地址");
        }
        // 允许直接粘贴完整端点：存库统一成 base_url，调用处只负责拼一段后缀
        String baseUrl = provider.getBaseUrl().trim().replaceAll("/+$", "");
        String endpointSuffix = "/images/generations";
        if (baseUrl.endsWith(endpointSuffix)) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - endpointSuffix.length());
            log.info("绘图接口地址已归一化为 base_url: {}", baseUrl);
        }
        provider.setBaseUrl(baseUrl);
        if (!StringUtils.hasText(provider.getModel())) {
            throw new BizException("请填写绘图模型");
        }
        if (!StringUtils.hasText(provider.getSize())) {
            provider.setSize("1024x1536");
        }
    }
}
