package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.BizException;
import com.spectrace.dto.SubscriptionSaveRequest;
import com.spectrace.dto.TaskCreateRequest;
import com.spectrace.entity.AgentSubscription;
import com.spectrace.entity.Brand;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.mapper.AgentSubscriptionMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceSeriesMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系列订阅抓取（T88）：到期自动创建「系列发现」任务。
 *
 * <p>单条订阅失败只记日志不影响其余；创建任务失败不更新 lastRunAt，下个检查日自动重试。
 * 采集 Agent 关闭时跳过调度，避免任务只进队列无人消费。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSubscriptionService {

    private final AgentSubscriptionMapper subscriptionMapper;
    private final DeviceSeriesMapper seriesMapper;
    private final BrandMapper brandMapper;
    private final AgentTaskService agentTaskService;

    @Value("${spectrace.agent.enabled:true}")
    private boolean agentEnabled;

    /** 每天检查一次到期订阅，默认 05:00（排在备份 03:30 与保留清理 04:00 之后）。 */
    @Scheduled(cron = "${spectrace.subscription.cron:0 0 5 * * *}")
    public void scheduledCheck() {
        try {
            runDueSubscriptions();
        } catch (Exception e) {
            log.error("系列订阅抓取调度失败，请检查数据库状态", e);
        }
    }

    public AgentSubscription get(Long seriesId) {
        return subscriptionMapper.selectOne(Wrappers.<AgentSubscription>lambdaQuery()
                .eq(AgentSubscription::getSeriesId, seriesId));
    }

    public AgentSubscription save(Long seriesId, SubscriptionSaveRequest req) {
        DeviceSeries series = seriesMapper.selectById(seriesId);
        if (series == null) {
            throw new BizException("系列不存在：" + seriesId);
        }
        AgentSubscription existing = get(seriesId);
        AgentSubscription subscription = existing == null ? new AgentSubscription() : existing;
        subscription.setSeriesId(seriesId);
        subscription.setIntervalMonths(req.getIntervalMonths());
        subscription.setAiModelId(req.getAiModelId());
        subscription.setEnabled(req.isEnabled() ? 1 : 0);
        if (existing == null) {
            subscriptionMapper.insert(subscription);
        } else {
            subscriptionMapper.updateById(subscription);
        }
        log.info("系列订阅已保存: series={} interval={}个月 enabled={} model={}",
                series.getName(), req.getIntervalMonths(), req.isEnabled(), req.getAiModelId());
        return subscription;
    }

    /** 到期判定：从未跑过，或上次运行时间加周期早于当前时间。 */
    void runDueSubscriptions() {
        if (!agentEnabled) {
            log.debug("采集 Agent 已关闭，跳过订阅调度");
            return;
        }
        List<AgentSubscription> subscriptions = subscriptionMapper.selectList(
                Wrappers.<AgentSubscription>lambdaQuery().eq(AgentSubscription::getEnabled, 1));
        LocalDateTime now = LocalDateTime.now();
        for (AgentSubscription subscription : subscriptions) {
            if (!isDue(subscription, now)) {
                continue;
            }
            DeviceSeries series = seriesMapper.selectById(subscription.getSeriesId());
            if (series == null) {
                // 系列已被删除：停用订阅并记录，避免每次调度都空查
                log.warn("订阅的系列已不存在，自动停用: subscriptionId={} seriesId={}",
                        subscription.getId(), subscription.getSeriesId());
                subscription.setEnabled(0);
                subscriptionMapper.updateById(subscription);
                continue;
            }
            createDiscoveryTask(subscription, series, now);
        }
    }

    private boolean isDue(AgentSubscription subscription, LocalDateTime now) {
        if (subscription.getLastRunAt() == null) {
            return true;
        }
        return subscription.getLastRunAt().plusMonths(subscription.getIntervalMonths()).isBefore(now);
    }

    private void createDiscoveryTask(AgentSubscription subscription, DeviceSeries series, LocalDateTime now) {
        Brand brand = series.getBrandId() == null ? null : brandMapper.selectById(series.getBrandId());
        if (brand == null) {
            // 发现结果要落成机型档案，必须有归属品牌（T58 决策）；无品牌系列只能人工处理
            log.warn("订阅系列缺少归属品牌，跳过并停用: series={}", series.getName());
            subscription.setEnabled(0);
            subscriptionMapper.updateById(subscription);
            return;
        }
        TaskCreateRequest req = new TaskCreateRequest();
        // 订阅以系列名作为发现关键词：产品线命名（如「iPhone 17」）即搜索语义
        req.setModelName(series.getName());
        req.setBrandName(brand.getName());
        req.setTaskType("SERIES_DISCOVERY");
        req.setAiModelId(subscription.getAiModelId());
        req.setSubscriptionId(subscription.getId());
        try {
            agentTaskService.createTask(req, null, "系列订阅");
            subscription.setLastRunAt(now);
            subscriptionMapper.updateById(subscription);
            log.info("订阅到点已创建系列发现任务: series={} brand={} interval={}个月",
                    series.getName(), brand.getName(), subscription.getIntervalMonths());
        } catch (Exception e) {
            // 不更新 lastRunAt：下个检查日自动重试
            log.error("订阅创建发现任务失败，等待下个检查日重试: series={}", series.getName(), e);
        }
    }
}
