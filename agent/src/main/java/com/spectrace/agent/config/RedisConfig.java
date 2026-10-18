package com.spectrace.agent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.net.URI;

/**
 * 把 {@code REDIS_URL} 连接串解析为 Spring Data Redis 连接工厂。
 *
 * <p>Lettuce 连接是懒建立的，因此即便运行在 http 模式（不消费队列）也不会有额外开销。
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final AgentProperties props;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        URI uri = URI.create(props.getRedisUrl());

        String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
        int port = uri.getPort() > 0 ? uri.getPort() : 6379;

        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);

        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int idx = userInfo.indexOf(':');
            String username = idx >= 0 ? userInfo.substring(0, idx) : "";
            String password = idx >= 0 ? userInfo.substring(idx + 1) : userInfo;
            if (!username.isEmpty()) {
                cfg.setUsername(username);
            }
            if (!password.isEmpty()) {
                cfg.setPassword(RedisPassword.of(password));
            }
        }

        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            try {
                cfg.setDatabase(Integer.parseInt(path.substring(1)));
            } catch (NumberFormatException ignored) {
                // 非法 db 段时保持默认 0
            }
        }
        return new LettuceConnectionFactory(cfg);
    }
}
