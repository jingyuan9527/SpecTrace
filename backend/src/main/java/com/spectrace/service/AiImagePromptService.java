package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.BizException;
import com.spectrace.entity.AiImagePrompt;
import com.spectrace.mapper.AiImagePromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板维护与变量渲染。
 *
 * <p>模板正文用 {@code {{变量}}} 占位，变量值全部由服务端按库内数据生成。
 * 渲染后仍残留的未知变量会被清掉并记日志：美术模型会照抄 {@code {{xxx}}} 这类字面量，
 * 留在提示词里会直接污染出图结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImagePromptService {

    /** 模板正文长度上限：足够写清版式与风格，又能挡住把整篇文章塞进提示词 */
    public static final int MAX_CONTENT_LENGTH = 4000;

    private static final Pattern UNRESOLVED_PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*[\\w\\u4e00-\\u9fa5]+\\s*}}");

    private final AiImagePromptMapper promptMapper;

    public List<AiImagePrompt> list() {
        return promptMapper.selectList(Wrappers.<AiImagePrompt>lambdaQuery()
                .orderByDesc(AiImagePrompt::getIsDefault)
                .orderByDesc(AiImagePrompt::getBuiltin)
                .orderByAsc(AiImagePrompt::getId));
    }

    public List<AiImagePrompt> listEnabled() {
        return promptMapper.selectList(Wrappers.<AiImagePrompt>lambdaQuery()
                .eq(AiImagePrompt::getEnabled, 1)
                .orderByDesc(AiImagePrompt::getIsDefault)
                .orderByAsc(AiImagePrompt::getId));
    }

    public AiImagePrompt save(AiImagePrompt prompt) {
        if (prompt.getId() == null) {
            validate(prompt);
            // 内置标记与默认标记只能由系统维护，不接受前端传值
            prompt.setBuiltin(0);
            prompt.setIsDefault(0);
            prompt.setEnabled(prompt.getEnabled() == null ? 1 : prompt.getEnabled());
            promptMapper.insert(prompt);
            if (promptMapper.selectCount(Wrappers.<AiImagePrompt>lambdaQuery()
                    .eq(AiImagePrompt::getIsDefault, 1)) == 0) {
                setDefault(prompt.getId());
            }
        } else {
            AiImagePrompt existing = promptMapper.selectById(prompt.getId());
            if (existing == null) {
                throw new BizException("提示词模板不存在");
            }
            // 未提交的字段沿用原值：只改启用状态时不该被名称/正文校验拦下
            if (!StringUtils.hasText(prompt.getName())) {
                prompt.setName(existing.getName());
            }
            if (!StringUtils.hasText(prompt.getContent())) {
                prompt.setContent(existing.getContent());
            }
            if (prompt.getEnabled() == null) {
                prompt.setEnabled(existing.getEnabled());
            }
            validate(prompt);
            prompt.setBuiltin(null);
            prompt.setIsDefault(null);
            promptMapper.updateById(prompt);
        }
        log.info("AI 绘图提示词已保存: id={} name={} length={}",
                prompt.getId(), prompt.getName(), prompt.getContent().length());
        // 回读一次：新增的模板可能已被提为默认，返回体应与库内一致
        AiImagePrompt latest = promptMapper.selectById(prompt.getId());
        return latest == null ? prompt : latest;
    }

    private void validate(AiImagePrompt prompt) {
        if (!StringUtils.hasText(prompt.getName())) {
            throw new BizException("请填写模板名称");
        }
        if (!StringUtils.hasText(prompt.getContent())) {
            throw new BizException("请填写提示词正文");
        }
        if (prompt.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new BizException("提示词正文不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
    }

    @Transactional
    public void delete(Long id) {
        AiImagePrompt existing = promptMapper.selectById(id);
        if (existing == null) {
            throw new BizException("提示词模板不存在");
        }
        if (existing.getBuiltin() != null && existing.getBuiltin() == 1) {
            throw new BizException("内置模板不可删除，可复制一份后修改");
        }
        promptMapper.deleteById(id);
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            AiImagePrompt next = promptMapper.selectOne(Wrappers.<AiImagePrompt>lambdaQuery()
                    .eq(AiImagePrompt::getEnabled, 1)
                    .orderByAsc(AiImagePrompt::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                setDefault(next.getId());
            }
        }
        log.info("AI 绘图提示词已删除: id={} name={}", id, existing.getName());
    }

    @Transactional
    public void setDefault(Long id) {
        AiImagePrompt target = promptMapper.selectById(id);
        if (target == null) {
            throw new BizException("提示词模板不存在");
        }
        AiImagePrompt reset = new AiImagePrompt();
        reset.setIsDefault(0);
        promptMapper.update(reset, Wrappers.<AiImagePrompt>lambdaUpdate()
                .eq(AiImagePrompt::getIsDefault, 1));

        AiImagePrompt update = new AiImagePrompt();
        update.setId(id);
        update.setIsDefault(1);
        update.setEnabled(1);
        promptMapper.updateById(update);
        log.info("AI 绘图默认提示词已切换: id={} name={}", id, target.getName());
    }

    /** 本次使用的模板：指定 ID 优先，否则取默认，最后取第一条启用模板。 */
    public AiImagePrompt resolve(Long promptId) {
        AiImagePrompt prompt = promptId != null ? promptMapper.selectById(promptId) : null;
        if (prompt != null && prompt.getEnabled() != null && prompt.getEnabled() != 1) {
            throw new BizException("所选提示词模板已停用");
        }
        if (prompt == null) {
            prompt = promptMapper.selectOne(Wrappers.<AiImagePrompt>lambdaQuery()
                    .eq(AiImagePrompt::getIsDefault, 1)
                    .eq(AiImagePrompt::getEnabled, 1)
                    .last("LIMIT 1"));
        }
        if (prompt == null) {
            prompt = promptMapper.selectOne(Wrappers.<AiImagePrompt>lambdaQuery()
                    .eq(AiImagePrompt::getEnabled, 1)
                    .orderByAsc(AiImagePrompt::getId)
                    .last("LIMIT 1"));
        }
        if (prompt == null) {
            throw new BizException("尚未配置可用的提示词模板");
        }
        return prompt;
    }

    public boolean hasEnabled() {
        return promptMapper.selectCount(Wrappers.<AiImagePrompt>lambdaQuery()
                .eq(AiImagePrompt::getEnabled, 1)) > 0;
    }

    /**
     * 按变量表渲染模板。变量值可能来自访客所选机型，统一做长度与换行收敛，
     * 避免一条超长参数值把整段提示词淹没。
     */
    public String render(String template, Map<String, String> variables) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        Matcher matcher = UNRESOLVED_PLACEHOLDER.matcher(result);
        StringBuilder unknown = new StringBuilder();
        while (matcher.find()) {
            if (unknown.length() > 0) {
                unknown.append(',');
            }
            unknown.append(matcher.group());
        }
        if (unknown.length() > 0) {
            log.warn("提示词模板存在未定义变量，已清空: {}", unknown);
            result = UNRESOLVED_PLACEHOLDER.matcher(result).replaceAll("");
        }
        return result.trim();
    }
}
