package com.spectrace.agent.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 初始化 HarnessAgent 工作区，并按内容哈希同步内置 persona。
 *
 * <p>工作区 {@code AGENTS.md} 是 Harness 的「智能体定义与演化的唯一事实来源」，允许在运行环境中直接编辑。
 * 但内置 persona 升级（例如新增输出字段）后，旧工作区不会自动跟随，导致代码与提示词不一致。
 * 因此这里记录「已应用的内置版本哈希」：</p>
 * <ul>
 *   <li>哈希一致：保留工作区文件（含手工修改），不覆盖；</li>
 *   <li>哈希不一致或缺失：把旧文件备份为 {@code AGENTS.md.bak-<时间戳>} 后覆盖为内置版本；</li>
 *   <li>缺少 AGENTS.md：直接写入内置版本。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceBootstrap {

    private static final String PERSONA_FILE = "AGENTS.md";
    private static final String VERSION_FILE = "AGENTS.md.sha256";
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AgentProperties props;

    @PostConstruct
    public void init() throws Exception {
        Path workspace = Path.of(props.getWorkspace());
        Files.createDirectories(workspace);

        Path persona = workspace.resolve(PERSONA_FILE);
        Path versionFile = workspace.resolve(VERSION_FILE);
        byte[] builtin = readBuiltin();
        String builtinHash = sha256(builtin);

        if (Files.notExists(persona)) {
            Files.write(persona, builtin);
            Files.writeString(versionFile, builtinHash, StandardCharsets.UTF_8);
            log.info("采集工作区已初始化：{}（写入内置 {}，版本 {}）",
                    workspace.toAbsolutePath(), PERSONA_FILE, shortHash(builtinHash));
            return;
        }

        String appliedHash = readHash(versionFile);
        if (builtinHash.equals(appliedHash)) {
            log.info("采集工作区已就绪：{}（persona 版本 {} 未变化，保留工作区文件）",
                    workspace.toAbsolutePath(), shortHash(appliedHash));
            return;
        }
        if (appliedHash == null && isSameContent(persona, builtin)) {
            // 老版本部署：文件就是内置内容，只补版本标记，避免无谓备份
            Files.writeString(versionFile, builtinHash, StandardCharsets.UTF_8);
            log.info("采集工作区 persona 版本标记已补齐：{}", shortHash(builtinHash));
            return;
        }

        Path backup = backup(workspace, persona);
        Files.write(persona, builtin);
        Files.writeString(versionFile, builtinHash, StandardCharsets.UTF_8);
        log.warn("内置 persona 已更新，工作区文件被覆盖：workspace={} 旧版本={} 新版本={} 备份={}",
                workspace.toAbsolutePath(),
                appliedHash == null ? "未知" : shortHash(appliedHash),
                shortHash(builtinHash),
                backup == null ? "无" : backup.getFileName().toString());
    }

    private byte[] readBuiltin() throws IOException {
        try (InputStream in = new ClassPathResource(PERSONA_FILE).getInputStream()) {
            return in.readAllBytes();
        }
    }

    private String readHash(Path versionFile) {
        if (Files.notExists(versionFile)) {
            return null;
        }
        try {
            return Files.readString(versionFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            log.warn("读取 persona 版本标记失败，按未应用处理: {}", versionFile, e);
            return null;
        }
    }

    private boolean isSameContent(Path persona, byte[] builtin) {
        try {
            return java.util.Arrays.equals(Files.readAllBytes(persona), builtin);
        } catch (IOException e) {
            log.warn("读取工作区 persona 失败，按不一致处理: {}", persona, e);
            return false;
        }
    }

    /** 覆盖前留一份带时间戳的备份，避免管理员的自定义内容直接丢失。 */
    private Path backup(Path workspace, Path persona) {
        Path backup = workspace.resolve(PERSONA_FILE + ".bak-" + LocalDateTime.now().format(BACKUP_STAMP));
        try {
            Files.copy(persona, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            log.warn("备份工作区 persona 失败，仍将继续覆盖: {}", persona, e);
            return null;
        }
    }

    private String sha256(byte[] content) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private String shortHash(String hash) {
        return hash == null ? "未知" : hash.substring(0, Math.min(12, hash.length()));
    }
}
