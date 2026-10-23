package com.spectrace.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.entity.Device;
import com.spectrace.mapper.DeviceMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 站点级 SEO 入口：sitemap.xml 与 robots.txt。
 *
 * <p>站点对外域名优先取 {@code spectrace.public-base-url}（反向代理后必须配置），
 * 未配置时回退到当前请求的 scheme + host，便于本地与单机部署直接可用。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SiteController {

    private final DeviceMapper deviceMapper;

    @Value("${spectrace.public-base-url:}")
    private String publicBaseUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        String base = baseUrl(request);
        List<Device> devices = deviceMapper.selectList(Wrappers.<Device>lambdaQuery()
                .eq(Device::getStatus, 1)
                .select(Device::getId, Device::getUpdatedAt)
                .orderByDesc(Device::getReleaseDate));

        StringBuilder xml = new StringBuilder(512 + devices.size() * 120);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, base + "/", null, "1.0", "daily");
        appendUrl(xml, base + "/devices", null, "0.9", "daily");
        appendUrl(xml, base + "/history", null, "0.7", "weekly");
        for (Device device : devices) {
            String lastmod = device.getUpdatedAt() == null ? null : device.getUpdatedAt().toLocalDate().toString();
            appendUrl(xml, base + "/devices/" + device.getId(), lastmod, "0.8", "weekly");
        }
        xml.append("</urlset>");

        log.debug("生成 sitemap: base={} 机型数={}", base, devices.size());
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/xml;charset=UTF-8"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(xml.toString());
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots(HttpServletRequest request) {
        String base = baseUrl(request);
        String content = """
                User-agent: *
                Allow: /
                Disallow: /admin/
                Disallow: /api/
                Disallow: /uploads/

                Sitemap: %s/sitemap.xml
                """.formatted(base);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(content);
    }

    private void appendUrl(StringBuilder xml, String loc, String lastmod, String priority, String changefreq) {
        xml.append("  <url><loc>").append(escape(loc)).append("</loc>");
        if (StringUtils.hasText(lastmod)) {
            xml.append("<lastmod>").append(lastmod).append("</lastmod>");
        }
        xml.append("<changefreq>").append(changefreq).append("</changefreq>")
                .append("<priority>").append(priority).append("</priority></url>\n");
    }

    /** 配置了对外域名就用它，否则回退请求域名（反代场景需显式配置）。 */
    private String baseUrl(HttpServletRequest request) {
        if (StringUtils.hasText(publicBaseUrl)) {
            String configured = publicBaseUrl.trim();
            return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        }
        String detected = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        log.debug("未配置 spectrace.public-base-url，sitemap 回退请求域名: {}", detected);
        return detected;
    }

    /** URL 中只需转义 XML 特殊字符，机型信息不出现在链接里。 */
    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
