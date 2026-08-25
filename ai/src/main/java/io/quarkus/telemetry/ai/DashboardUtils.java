package io.quarkus.telemetry.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

public class DashboardUtils {
    private static final Logger log = Logger.getLogger(DashboardUtils.class);

    static String sanitizeDashboardJson(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String json = raw.strip();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            json = json.substring(firstNewline + 1);
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3).strip();
            }
        }
        try {
            JsonNode node = mapper.readTree(json);
            if (node.has("dashboard") && node.get("dashboard").isObject()) {
                JsonNode inner = node.get("dashboard");
                return mapper.writeValueAsString(inner);
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warnf("LLM returned invalid dashboard JSON, attempting repair: {}", e.getMessage());
            int firstBrace = json.indexOf('{');
            if (firstBrace < 0) {
                return raw;
            }
            json = json.substring(firstBrace);
            int depth = 0;
            int endIndex = -1;
            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        endIndex = i;
                        break;
                    }
                }
            }
            if (endIndex > 0) {
                String candidate = json.substring(0, endIndex + 1);
                try {
                    JsonNode node = mapper.readTree(candidate);
                    log.info("Repaired dashboard JSON by trimming trailing content");
                    if (node.has("dashboard") && node.get("dashboard").isObject()) {
                        return mapper.writeValueAsString(node.get("dashboard"));
                    }
                    return mapper.writeValueAsString(node);
                } catch (Exception e2) {
                    log.errorf("Dashboard JSON repair failed: {}", e2.getMessage());
                }
            }
            return raw;
        }
    }
}
