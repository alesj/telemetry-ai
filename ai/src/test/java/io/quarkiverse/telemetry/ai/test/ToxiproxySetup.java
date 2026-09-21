package io.quarkiverse.telemetry.ai.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ToxiproxySetup {

    private static final String MYSQL_CONTAINER = "chaos-mysql";
    private static final String TOXIPROXY_CONTAINER = "chaos-toxiproxy";
    private static final int MYSQL_HOST_PORT = 33060;
    private static final int TOXIPROXY_API_PORT = 8474;
    private static final int PROXY_PORT = 33061;
    private static final String PROXY_NAME = "mysql";

    public static String jdbcUrl() {
        return "jdbc:mysql://localhost:" + PROXY_PORT + "/quarkus";
    }

    public static void start() {
        System.out.println("[TOXIPROXY] Starting MySQL + Toxiproxy ...");

        docker("run", "-d", "--rm", "--name", MYSQL_CONTAINER,
                "-p", MYSQL_HOST_PORT + ":3306",
                "-e", "MYSQL_ROOT_PASSWORD=root",
                "-e", "MYSQL_DATABASE=quarkus",
                "mysql:9.5");

        waitForMySql(120);

        docker("run", "-d", "--rm", "--name", TOXIPROXY_CONTAINER,
                "-p", TOXIPROXY_API_PORT + ":8474",
                "-p", PROXY_PORT + ":" + PROXY_PORT,
                "ghcr.io/shopify/toxiproxy:2.5.0");

        waitForHttp("http://localhost:" + TOXIPROXY_API_PORT + "/version", 30);

        String proxyConfig = """
                {"name":"%s","listen":"0.0.0.0:%d","upstream":"host.docker.internal:%d"}"""
                .formatted(PROXY_NAME, PROXY_PORT, MYSQL_HOST_PORT);
        httpPost("http://localhost:" + TOXIPROXY_API_PORT + "/proxies", proxyConfig);

        System.out.println("[TOXIPROXY] Ready — proxy at localhost:" + PROXY_PORT
                + " → host.docker.internal:" + MYSQL_HOST_PORT);
    }

    public static void addLatency(int latencyMs) {
        System.out.println("[TOXIPROXY] Adding " + latencyMs + "ms latency ...");
        String toxic = """
                {"name":"latency","type":"latency","stream":"downstream","attributes":{"latency":%d}}"""
                .formatted(latencyMs);
        httpPost(toxicsUrl(), toxic);
    }

    public static void removeLatency() {
        System.out.println("[TOXIPROXY] Removing latency ...");
        httpDelete(toxicsUrl() + "/latency");
    }

    public static void cutConnection() {
        System.out.println("[TOXIPROXY] Cutting connection ...");
        httpPost(toxicsUrl(),
                """
                        {"name":"cut-down","type":"bandwidth","stream":"downstream","attributes":{"rate":0}}""");
        httpPost(toxicsUrl(),
                """
                        {"name":"cut-up","type":"bandwidth","stream":"upstream","attributes":{"rate":0}}""");
    }

    public static void restoreConnection() {
        System.out.println("[TOXIPROXY] Restoring connection ...");
        httpDelete(toxicsUrl() + "/cut-down");
        httpDelete(toxicsUrl() + "/cut-up");
    }

    public static void stop() {
        System.out.println("[TOXIPROXY] Stopping ...");
        dockerQuiet("stop", TOXIPROXY_CONTAINER);
        dockerQuiet("stop", MYSQL_CONTAINER);
        System.out.println("[TOXIPROXY] Stopped");
    }

    private static String toxicsUrl() {
        return "http://localhost:" + TOXIPROXY_API_PORT + "/proxies/" + PROXY_NAME + "/toxics";
    }

    private static void waitForMySql(int timeoutSec) {
        System.out.println("[TOXIPROXY] Waiting for MySQL ...");
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                Process p = new ProcessBuilder(
                        "docker", "exec", MYSQL_CONTAINER,
                        "mysqladmin", "ping", "-uroot", "-proot", "--silent")
                        .redirectErrorStream(true)
                        .start();
                if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                    System.out.println("[TOXIPROXY] MySQL ready");
                    return;
                }
            } catch (Exception ignored) {
            }
            sleep(2);
        }
        throw new RuntimeException("MySQL did not become ready within " + timeoutSec + "s");
    }

    private static void waitForHttp(String url, int timeoutSec) {
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                if (conn.getResponseCode() < 400) {
                    conn.disconnect();
                    return;
                }
                conn.disconnect();
            } catch (Exception ignored) {
            }
            sleep(1);
        }
        throw new RuntimeException("HTTP endpoint " + url + " not ready within " + timeoutSec + "s");
    }

    private static void httpPost(String url, String json) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            if (code >= 400) {
                String body = readResponse(conn);
                throw new RuntimeException("POST " + url + " returned " + code + ": " + body);
            }
            conn.disconnect();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("POST " + url + " failed", e);
        }
    }

    private static void httpDelete(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("DELETE");
            int code = conn.getResponseCode();
            if (code >= 400) {
                String body = readResponse(conn);
                System.err.println("[TOXIPROXY] DELETE " + url + " returned " + code + ": " + body);
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[TOXIPROXY] DELETE " + url + " failed: " + e.getMessage());
        }
    }

    private static String readResponse(HttpURLConnection conn) {
        try (var is = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream();
                var reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line);
            return sb.toString();
        } catch (Exception e) {
            return "(unreadable)";
        }
    }

    private static void docker(String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "docker";
            System.arraycopy(args, 0, cmd, 1, args.length);
            Process p = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();
            try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[TOXIPROXY] " + line);
                }
            }
            p.waitFor(30, TimeUnit.SECONDS);
            if (p.exitValue() != 0) {
                throw new RuntimeException("docker " + String.join(" ", args)
                        + " failed with exit code " + p.exitValue());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("docker " + String.join(" ", args) + " failed", e);
        }
    }

    private static void dockerQuiet(String... args) {
        try {
            docker(args);
        } catch (Exception e) {
            System.err.println("[TOXIPROXY] " + e.getMessage());
        }
    }

    private static void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
