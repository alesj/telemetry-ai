package io.quarkus.telemetry.ai.test;

import java.util.concurrent.TimeUnit;

public class DevModeProcess {

    private final String label;
    private final Process process;

    DevModeProcess(String label, Process process) {
        this.label = label;
        this.process = process;
    }

    public String label() {
        return label;
    }

    public void stop() {
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            System.out.println("[" + label + "] Stopped");
        }
    }
}
