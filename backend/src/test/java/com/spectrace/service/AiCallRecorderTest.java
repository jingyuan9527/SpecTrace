package com.spectrace.service;

import com.spectrace.common.AiCallScene;
import com.spectrace.entity.AiCallLog;
import com.spectrace.entity.AiImageProvider;
import com.spectrace.mapper.AiCallLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 统计写入是旁路能力：场景、成败与延迟口径必须正确，写库失败也不能影响调用方。 */
@ExtendWith(MockitoExtension.class)
class AiCallRecorderTest {

    @Mock
    private AiCallLogMapper callLogMapper;

    @InjectMocks
    private AiCallRecorder recorder;

    @Test
    @DisplayName("绘图调用落到 IMAGE 场景并保留耗时")
    void recordsImageCall() {
        AiImageProvider provider = new AiImageProvider();
        provider.setId(2L);
        provider.setName("OpenAI 绘图");
        provider.setModel("gpt-image-1");

        recorder.recordImage(AiCallTarget.image(provider), null, LocalDateTime.now(), 1200L, 200, null, null);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(callLogMapper).insert(captor.capture());
        AiCallLog row = captor.getValue();
        assertThat(row.getScene()).isEqualTo(AiCallScene.IMAGE.name());
        assertThat(row.getProviderKind()).isEqualTo(AiCallTarget.KIND_IMAGE);
        assertThat(row.getProviderId()).isEqualTo(2L);
        assertThat(row.getModelName()).isEqualTo("gpt-image-1");
        assertThat(row.getSuccess()).isEqualTo(1);
        assertThat(row.getLatencyMs()).isEqualTo(1200L);
    }

    @Test
    @DisplayName("失败调用保留分类与状态码，不以成功落库")
    void recordsFailureCall() {
        AiCallTarget target = AiCallTarget.chat(null, "qwen-max");

        recorder.recordChat(target, AiCallScene.AGENT_SCRAPE, "88", LocalDateTime.now(),
                5000L, 429, "RATE_LIMIT", "too many requests", null, null);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(callLogMapper).insert(captor.capture());
        AiCallLog row = captor.getValue();
        assertThat(row.getSuccess()).isZero();
        assertThat(row.getStatusCode()).isEqualTo(429);
        assertThat(row.getErrorType()).isEqualTo("RATE_LIMIT");
        assertThat(row.getBizId()).isEqualTo("88");
        // 没有后台连接时回退环境变量的调用也要有可读的供应商标签
        assertThat(row.getProviderName()).isEqualTo("环境变量兜底");
    }

    @Test
    @DisplayName("统计写库异常不影响模型调用主流程")
    void swallowsPersistenceFailure() {
        when(callLogMapper.insert(any(AiCallLog.class))).thenThrow(new RuntimeException("database is locked"));

        assertThatCode(() -> recorder.recordChat(AiCallTarget.chat(null, "qwen-max"),
                AiCallScene.INSIGHT, null, LocalDateTime.now(), 100L, null, null, null, null, null))
                .doesNotThrowAnyException();
    }
}
