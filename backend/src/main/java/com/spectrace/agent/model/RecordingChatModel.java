package com.spectrace.agent.model;

import com.spectrace.common.AiCallErrors;
import com.spectrace.common.AiCallFailure;
import com.spectrace.common.AiCallScene;
import com.spectrace.service.AiCallRecorder;
import com.spectrace.service.AiCallTarget;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.transport.HttpTransportException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Model 装饰器：只观测、不改写请求与响应。
 *
 * <p>AgentScope 每轮迭代都会调用 {@link #stream}，在这里计时才能拿到「单次模型请求」的延迟，
 * 而不是整个抓取任务的耗时。计时从订阅开始（cold Flux 实际发请求的时机），
 * 以 cancel 结束的调用也会落一条失败记录，避免任务超时在统计里凭空消失。</p>
 */
public class RecordingChatModel implements Model {

    private final Model delegate;
    private final AiCallTarget target;
    private final AiCallRecorder recorder;
    private final AiCallScene scene;
    private final String bizId;

    public RecordingChatModel(Model delegate, AiCallTarget target, AiCallRecorder recorder,
                              AiCallScene scene, String bizId) {
        this.delegate = delegate;
        this.target = target;
        this.recorder = recorder;
        this.scene = scene;
        this.bizId = bizId;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.defer(() -> {
            LocalDateTime startedAt = LocalDateTime.now();
            long startedNanos = System.nanoTime();
            CallState state = new CallState();

            Flux<ChatResponse> upstream;
            try {
                upstream = delegate.stream(messages, tools, options);
            } catch (RuntimeException e) {
                record(startedAt, startedNanos, state, e, null);
                return Flux.<ChatResponse>error(e);
            }
            if (upstream == null) {
                IllegalStateException error = new IllegalStateException("模型返回了空的响应流");
                record(startedAt, startedNanos, state, error, null);
                return Flux.error(error);
            }

            return upstream
                    .doOnNext(state::observe)
                    .doOnError(state::remember)
                    .doFinally(signal -> {
                        // cancel 不会触发 onError，单独标记；否则超时调用会被记为成功
                        if (signal == SignalType.CANCEL) {
                            state.cancelled = true;
                        }
                        record(startedAt, startedNanos, state, state.error,
                                state.cancelled ? AiCallFailure.CANCELLED.name() : null);
                    });
        });
    }

    private void record(LocalDateTime startedAt, long startedNanos, CallState state,
                        Throwable error, String forcedErrorType) {
        if (!state.recorded.compareAndSet(false, true)) {
            return;
        }
        long latencyMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
        String errorType = null;
        String errorMsg = null;
        Integer statusCode = null;

        if (forcedErrorType != null) {
            errorType = forcedErrorType;
            errorMsg = "调用被取消（可能是任务超时或上游主动终止）";
        } else if (error != null) {
            errorType = AiCallErrors.fromThrowable(error);
            errorMsg = error.getClass().getSimpleName() + ": " + error.getMessage();
            if (error instanceof HttpTransportException httpError) {
                statusCode = httpError.getStatusCode();
            }
        } else if (!state.hasContent) {
            // 200 但 content 为空且没有工具调用：业务上拿不到任何可用结果
            errorType = AiCallFailure.EMPTY_RESPONSE.name();
            errorMsg = "模型返回内容为空";
        }

        recorder.recordChat(target, scene, bizId, startedAt, latencyMs, statusCode, errorType, errorMsg,
                state.inputTokens, state.outputTokens);
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }

    @Override
    public boolean supportsNativeStructuredOutput() {
        return delegate.supportsNativeStructuredOutput();
    }

    @Override
    public boolean supportsNativeStructuredOutputWithTools() {
        return delegate.supportsNativeStructuredOutputWithTools();
    }

    @Override
    public int getContextWindowSize() {
        return delegate.getContextWindowSize();
    }

    private static final class CallState {

        private final AtomicBoolean recorded = new AtomicBoolean();
        private volatile Throwable error;
        private volatile boolean hasContent;
        private volatile boolean cancelled;
        private volatile Integer inputTokens;
        private volatile Integer outputTokens;

        private void observe(ChatResponse response) {
            if (response == null) {
                return;
            }
            if (response.getContent() != null && !response.getContent().isEmpty()) {
                hasContent = true;
            }
            String finishReason = response.getFinishReason();
            if ("tool_calls".equalsIgnoreCase(finishReason) || "function_call".equalsIgnoreCase(finishReason)) {
                hasContent = true;
            }
            ChatUsage usage = response.getUsage();
            if (usage != null) {
                inputTokens = usage.getInputTokens();
                outputTokens = usage.getOutputTokens();
            }
        }

        private void remember(Throwable error) {
            this.error = error;
        }
    }
}
