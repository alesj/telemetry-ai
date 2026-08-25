package io.quarkus.telemetry.ai;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class AnalysisMetrics implements ChatModelListener {

    private final AtomicInteger inputTokens = new AtomicInteger();
    private final AtomicInteger outputTokens = new AtomicInteger();
    private final AtomicInteger totalTokens = new AtomicInteger();
    private final AtomicInteger llmCalls = new AtomicInteger();

    public void reset() {
        inputTokens.set(0);
        outputTokens.set(0);
        totalTokens.set(0);
        llmCalls.set(0);
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        llmCalls.incrementAndGet();
        TokenUsage usage = context.chatResponse().tokenUsage();
        if (usage != null) {
            if (usage.inputTokenCount() != null) inputTokens.addAndGet(usage.inputTokenCount());
            if (usage.outputTokenCount() != null) outputTokens.addAndGet(usage.outputTokenCount());
            if (usage.totalTokenCount() != null) totalTokens.addAndGet(usage.totalTokenCount());
        }
    }

    public AnalysisResult.PerfInfo snapshot(long durationMs) {
        return new AnalysisResult.PerfInfo(
                durationMs,
                inputTokens.get(),
                outputTokens.get(),
                totalTokens.get(),
                llmCalls.get()
        );
    }
}
