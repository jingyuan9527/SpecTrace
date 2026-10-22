package com.spectrace.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 静态资源与 SPA history 路由支持。
 */
@Configuration
public class ResourceConfig implements WebMvcConfigurer {

    private static final String INDEX_HTML = "/static/index.html";

    @Value("${spectrace.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 资源目录 URL 必须以 / 结尾，否则 createRelative 会拼错路径（目录可能尚未创建）
        String location = Path.of(uploadDir).toAbsolutePath().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);

        // history 模式下 /devices/7 这类前端路由没有对应静态文件，回退到 index.html；
        // 带扩展名的资源与 /api 路径不参与回退，避免把 HTML 当 JS/CSS 或接口响应返回
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("v3/")
                                || resourcePath.startsWith("swagger-ui") || resourcePath.equals("error")
                                || hasExtension(resourcePath)) {
                            return null;
                        }
                        Resource index = new ClassPathResource(INDEX_HTML);
                        return index.exists() ? index : null;
                    }
                });
    }

    /** 最后一段带点的视为静态文件（如 app.js / logo.png），不做 SPA 回退。 */
    private boolean hasExtension(String resourcePath) {
        int slash = resourcePath.lastIndexOf('/');
        return resourcePath.indexOf('.', slash + 1) > 0;
    }
}
