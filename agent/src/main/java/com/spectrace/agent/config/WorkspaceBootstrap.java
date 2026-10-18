package com.spectrace.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 初始化 HarnessAgent 工作区：确保目录存在，并在缺少 {@code AGENTS.md} 时写入内置的 persona 模板。
 *
 * <p>工作区是 Harness 的「智能体定义与演化的唯一事实来源」，把 persona 以文件形式下发，
 * 之后可在运行环境中直接编辑，无需改代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceBootstrap {

    private static final String PERSONA_FILE = "AGENTS.md";

    private final AgentProperties props;

    @PostConstruct
    public void init() throws Exception {
        Path workspace = Path.of(props.getWorkspace());
        Files.createDirectories(workspace);

        Path persona = workspace.resolve(PERSONA_FILE);
        if (Files.notExists(persona)) {
            try (InputStream in = new ClassPathResource(PERSONA_FILE).getInputStream()) {
                Files.copy(in, persona);
            }
            log.info("工作区已初始化：{}（写入内置 {}）", workspace.toAbsolutePath(), PERSONA_FILE);
        } else {
            log.info("工作区已就绪：{}", workspace.toAbsolutePath());
        }
    }
}
