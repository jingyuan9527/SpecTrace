package com.spectrace.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 公开侧准静态数据的进程内缓存：品牌、参数元信息、系列列表、机型详情。
 *
 * <p>SQLite 是单文件库，公开页每次请求都打库在机型库变大后会明显放大延迟；
 * 管理端写路径用 @CacheEvict(allEntries) 主动失效，TTL 只兜底漏网场景，因此可以设得较短。
 * 只缓存读多写少的聚合入口，对比 / 历史 / 列表这类高组合参数查询不缓存。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String BRANDS = "brands";
    public static final String PARAM_META = "paramMeta";
    public static final String SERIES_LIST = "seriesList";
    public static final String DEVICE_DETAIL = "deviceDetail";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(BRANDS, PARAM_META, SERIES_LIST, DEVICE_DETAIL);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(2_000));
        return manager;
    }
}
