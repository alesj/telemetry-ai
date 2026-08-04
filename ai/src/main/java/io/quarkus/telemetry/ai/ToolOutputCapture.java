package io.quarkus.telemetry.ai;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@ApplicationScoped
public class ToolOutputCapture implements Consumer<String> {

    public record CapturedOutput(String tool, String key, Object data) {}

    private final List<CapturedOutput> outputs = new CopyOnWriteArrayList<>();
    private volatile boolean capturing;
    private volatile String systemPrompt;

    public void start() {
        outputs.clear();
        capturing = true;
    }

    public void stop() {
        capturing = false;
    }

    public void recordSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    @Override
    public void accept(String s) {
        recordSystemPrompt(s);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void record(String tool, String key, Object data) {
        if (capturing) {
            outputs.add(new CapturedOutput(tool, key, data));
        }
    }

    public List<CapturedOutput> getOutputs() {
        return List.copyOf(outputs);
    }

    public String toFormattedString() {
        StringBuilder sb = new StringBuilder();
        for (CapturedOutput output : outputs) {
            sb.append("=== ").append(output.tool());
            if (output.key() != null) {
                sb.append(" [").append(output.key()).append("]");
            }
            sb.append(" ===\n");
            sb.append(output.data()).append("\n\n");
        }
        return sb.toString();
    }
}
