package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.entity.Device;
import com.spectrace.mapper.DeviceMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 机型产品图异步下载（T90）。
 *
 * <p><b>硬约束：与主流程完全解耦</b>——审核通过只落待下载 URL；下载在独立单线程执行，
 * 任何失败（403 / 超时 / 超限 / 非图片）只清空待下载标记并写日志，绝不回滚或阻塞
 * 已入库的参数与上架状态，不做自动重试（避免堆积），兜底是管理员手动上传。</p>
 */
@Slf4j
@Service
public class DeviceImageFetchService {

    private static final int DOWNLOAD_TIMEOUT_MS = 15_000;

    private final DeviceMapper deviceMapper;
    private final OperationLogService logService;
    private final String uploadDir;
    private final long maxBytes;

    /** 单线程串行消费：图片下载是慢速外网 IO，串行天然限速；重启丢失的队列由启动恢复补齐。 */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "device-image-fetch");
        thread.setDaemon(true);
        return thread;
    });

    public DeviceImageFetchService(DeviceMapper deviceMapper,
                                   OperationLogService logService,
                                   @Value("${spectrace.upload-dir:uploads}") String uploadDir,
                                   @Value("${spectrace.upload.max-bytes:8388608}") long maxBytes) {
        this.deviceMapper = deviceMapper;
        this.logService = logService;
        this.uploadDir = uploadDir;
        this.maxBytes = maxBytes;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public void enqueue(Long deviceId) {
        executor.execute(() -> fetch(deviceId));
    }

    /** 启动恢复：上次运行遗留的待下载设备重新入队（与采集任务恢复同一思路）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverPending() {
        try {
            List<Device> pending = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                    .isNotNull(Device::getThumbnailPendingUrl));
            if (pending.isEmpty()) {
                return;
            }
            log.info("启动恢复：{} 台机型有待下载产品图，重新入队", pending.size());
            pending.forEach(device -> executor.execute(() -> fetch(device.getId())));
        } catch (Exception e) {
            log.error("启动恢复待下载图片失败（不影响启动）", e);
        }
    }

    private void fetch(Long deviceId) {
        String pendingUrl = null;
        try {
            Device device = deviceMapper.selectById(deviceId);
            if (device == null || !StringUtils.hasText(device.getThumbnailPendingUrl())) {
                return;
            }
            pendingUrl = device.getThumbnailPendingUrl();
            byte[] bytes = download(pendingUrl);
            String ext = detectImageType(bytes);
            if (ext == null) {
                throw new IllegalStateException("响应不是支持的图片类型（JPG/PNG/WebP/GIF）");
            }
            if (bytes.length > maxBytes) {
                throw new IllegalStateException("图片超过大小上限 " + (maxBytes / 1024 / 1024) + "MB");
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), bytes);

            String savedUrl = "/uploads/" + filename;
            deviceMapper.update(null, Wrappers.<Device>lambdaUpdate()
                    .eq(Device::getId, deviceId)
                    .set(Device::getThumbnail, savedUrl)
                    .set(Device::getThumbnailPendingUrl, null));
            log.info("机型产品图下载成功: deviceId={} url={} size={}B", deviceId, savedUrl, bytes.length);
        } catch (Exception e) {
            // 失败即放弃：清掉待下载标记，保留占位图，人工需要时走手动上传
            log.warn("机型产品图下载失败（已放弃，不影响机型数据）: deviceId={} pendingUrl={}", deviceId, pendingUrl, e);
            clearPending(deviceId);
            try {
                logService.record(null, "系统", "DEVICE_IMAGE_FETCH_FAILED", "device", deviceId,
                        "采集图下载失败已放弃：" + (pendingUrl == null ? "" : pendingUrl));
            } catch (Exception logError) {
                log.warn("图片下载失败日志写入失败", logError);
            }
        }
    }

    private void clearPending(Long deviceId) {
        try {
            deviceMapper.update(null, Wrappers.<Device>lambdaUpdate()
                    .eq(Device::getId, deviceId)
                    .set(Device::getThumbnailPendingUrl, null));
        } catch (Exception e) {
            log.error("清理待下载标记失败: deviceId={}", deviceId, e);
        }
    }

    private byte[] download(String url) throws Exception {
        return Jsoup.connect(url)
                .ignoreContentType(true)
                .timeout(DOWNLOAD_TIMEOUT_MS)
                .maxBodySize((int) (maxBytes / 1024) + 1)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) SpecTrace/1.0")
                .execute()
                .bodyAsBytes();
    }

    /** 依据文件头判定真实图片类型（与 FileController 同口径，不信任 URL 或 Content-Type）。 */
    private String detectImageType(byte[] head) {
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (head.length >= 8 && head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        if (head.length >= 6 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F') {
            return "gif";
        }
        return null;
    }
}
