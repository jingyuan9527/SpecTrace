package com.spectrace.agent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 指向 SpringBoot 后端的 RestClient，默认携带内部令牌 {@code X-Agent-Token}。
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final AgentProperties props;

    @Bean
    public RestClient backendRestClient() {
        return RestClient.builder()
                .baseUrl(props.getBackendUrl())
                .defaultHeader("X-Agent-Token", props.getToken())
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
