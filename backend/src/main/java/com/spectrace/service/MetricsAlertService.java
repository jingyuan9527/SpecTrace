package com.spectrace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 运行告警（T104）：待审核积压、磁盘水位两类周期评估；备份失败由 BackupService 触发。
 *
 * <p>阈值全部可配、0 关闭；每小时评估一次；同一规则 24h 只发一次（见 {@link AlertSender}）。
 * 备份目录所在盘就是 SQLite 数据所在的盘，水位告警以此为准。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsAlertService {

    private final AgentReviewService agentReviewService;
    private final AlertSender alertSender;

    /** 待审核条数阈值，0 关闭该规则 */
    @Value("${spectrace.alert.pending-count:20}")
    private int pendingCountThreshold;

    /** 待审核最老滞留小时数阈值，0 关闭该规则 */
    @Value("${spectrace.alert.pending-oldest-hours:48}")
    private int pendingOldestHours;

    /** 数据目录所在盘剩余空间阈值（MB），0 关闭该规则 */
    @Value("${spectrace.alert.disk-free-mb:1024}")
    private long diskFreeMb;

    @Value("${spectrace.backup.dir:./data/backups}")
    private String backupDir;

    /** 启动后 1 分钟做一次首查：进程重启不该等满一小时才知道积压/磁盘异常。 */
    @EventListener(ApplicationReadyEvent.class)
    public void firstCheck() {
        try {
            check();
        } catch (Exception e) {
            log.error("启动首查告警失败（不影响启动）", e);
        }
    }

    /** 每小时整点评估；排在整点避免与 03:30/04:00/05:00 的例行任务挤在一起。 */
    @Scheduled(cron = "${spectrace.alert.cron:0 0 * * * *}")
    public void scheduledCheck() {
        try {
            check();
        } catch (Exception e) {
            log.error("告警评估失败", e);
        }
    }

    void check() {
        checkPendingBacklog();
        checkDiskFree();
    }

    private void checkPendingBacklog() {
        if (pendingCountThreshold <= 0 && pendingOldestHours <= 0) {
            return;
        }
        long count = agentReviewService.countPendingReview();
        long oldestHours = count == 0 ? 0 : agentReviewService.oldestPendingHours();
        boolean overCount = pendingCountThreshold > 0 && count >= pendingCountThreshold;
        boolean overAge = pendingOldestHours > 0 && oldestHours >= pendingOldestHours;
        if (overCount || overAge) {
            alertSender.sendOncePerDay("pending_backlog",
                    "待审核数据积压：" + count + " 条，最老一条已等待 " + oldestHours + " 小时，请尽快处理（后台「抓取审核」）");
        }
    }

    private void checkDiskFree() {
        if (diskFreeMb <= 0) {
            return;
        }
        try {
            Path dir = Paths.get(backupDir).toAbsolutePath();
            Files.createDirectories(dir);
            long freeMb = Files.getFileStore(dir).getUsableSpace() / (1024 * 1024);
            if (freeMb < diskFreeMb) {
                alertSender.sendOncePerDay("disk_free",
                        "数据目录所在磁盘剩余 " + freeMb + "MB，低于阈值 " + diskFreeMb + "MB，备份与写入随时可能失败（dir=" + dir + "）");
            }
        } catch (Exception e) {
            log.warn("磁盘水位检查失败（不影响运行）", e);
        }
    }
}
