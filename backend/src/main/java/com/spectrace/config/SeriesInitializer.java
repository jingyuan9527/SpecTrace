package com.spectrace.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 产品线（系列）种子数据。
 *
 * <p>不能放进 {@code data.sql}：{@code device.series_id} 是后加列，老库要先由
 * {@link SqliteSchemaMigration} 补列，脚本阶段直接引用会因缺列导致启动失败，
 * 因此这里排在列迁移之后执行。</p>
 *
 * <p>只在系列首次创建时给种子机型归类，避免每次启动覆盖管理员在后台的调整。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SeriesInitializer implements ApplicationRunner {

    private static final String IPHONE_SERIES = "iPhone 数字系列";
    private static final String XIAOMI_SERIES = "小米数字系列";
    private static final String MATE_SERIES = "Mate 系列";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed("Apple", IPHONE_SERIES, 1, List.of(
                    "iPhone 11", "iPhone 12", "iPhone 13", "iPhone 14", "iPhone 15", "iPhone 16", "iPhone 17"));
            seed("小米", XIAOMI_SERIES, 2, List.of("小米 14"));
            seed("华为", MATE_SERIES, 3, List.of("华为 Mate 60 Pro"));
        } catch (Exception e) {
            // 种子数据只服务于演示，失败不阻断启动，但要留下可查的堆栈
            log.error("系列种子数据初始化失败", e);
        }
    }

    private void seed(String brandName, String seriesName, int sortOrder, List<String> deviceNames) {
        Long brandId = brandIdOf(brandName);
        if (brandId == null) {
            log.debug("系列种子跳过：品牌 {} 不存在", brandName);
            return;
        }
        int inserted = jdbcTemplate.update("""
                INSERT INTO device_series (brand_id, name, sort_order, status)
                SELECT ?, ?, ?, 1
                 WHERE NOT EXISTS (
                       SELECT 1 FROM device_series WHERE brand_id = ? AND name = ? AND deleted = 0
                 )
                """, brandId, seriesName, sortOrder, brandId, seriesName);
        if (inserted == 0) {
            return;
        }

        Long seriesId = jdbcTemplate.queryForObject(
                "SELECT id FROM device_series WHERE brand_id = ? AND name = ? AND deleted = 0",
                Long.class, brandId, seriesName);
        if (seriesId == null) {
            return;
        }
        String placeholders = String.join(",", deviceNames.stream().map(name -> "?").toList());
        Object[] params = new Object[deviceNames.size() + 2];
        params[0] = seriesId;
        params[1] = brandId;
        for (int i = 0; i < deviceNames.size(); i++) {
            params[i + 2] = deviceNames.get(i);
        }
        int classified = jdbcTemplate.update(
                "UPDATE device SET series_id = ? WHERE brand_id = ? AND series_id IS NULL AND name IN ("
                        + placeholders + ")", params);
        log.info("已初始化系列「{}」：归类 {} 台机型", seriesName, classified);
    }

    private Long brandIdOf(String name) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM brand WHERE name = ? AND deleted = 0", Long.class, name);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
