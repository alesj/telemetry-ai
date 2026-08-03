package io.quarkus.telemetry.ai.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CompanionApps {

    private static final Path PROJECT_ROOT = Path.of("..").toAbsolutePath().normalize();

    private static final String MVN = PROJECT_ROOT.resolve("mvnw").toFile().exists()
            ? PROJECT_ROOT.resolve("mvnw").toString() : "mvn";

    public static DevModeProcess startDevMode(String module, int port, String... extraProps) {
        String label = module.toUpperCase();
        System.out.println("=== Starting " + label + " ...");

        List<String> command = new ArrayList<>(List.of(
                MVN, "quarkus:dev",
                "-pl", module,
                "-Ddebug=false",
                "-Dquarkus.http.port=" + port,
                "-Dquarkus.console.enabled=false",
                "-Dquarkus.test.continuous-testing=disabled"
        ));
        for (String prop : extraProps) {
            command.add("-D" + prop);
        }

        try {
            Process process = new ProcessBuilder(command)
                    .directory(PROJECT_ROOT.toFile())
                    .redirectErrorStream(true)
                    .start();
            forwardOutput(process.getInputStream(), label);

            waitForHttp("http://localhost:" + port + "/poke?value=200", label, 120);

            System.out.println("=== " + label + " started ...");
            return new DevModeProcess(label, process);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start " + label, e);
        }
    }

    public static int pokeHttp(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            int status = conn.getResponseCode();
            conn.disconnect();
            return status;
        } catch (Exception e) {
            return -1;
        }
    }

    public static void waitForHttp(String url, String label, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                int code = conn.getResponseCode();
                conn.disconnect();
                if (code >= 200 && code < 400) {
                    System.out.println("[" + label + "] Ready");
                    return;
                }
            } catch (Exception ignored) {
            }
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted waiting for " + label);
            }
        }
        throw new RuntimeException(label + " did not become ready within " + timeoutSeconds + "s");
    }

    public static String httpGet(String url, String user, String password) {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            String auth = Base64.getEncoder().encodeToString(
                    (user + ":" + password).getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + auth);
            try (InputStream is = conn.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static void forwardOutput(InputStream inputStream, String label) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[" + label + "] " + line);
                }
            } catch (Exception ignored) {
            }
        });
        thread.setDaemon(true);
        thread.setName(label + "-output");
        thread.start();
    }
}
