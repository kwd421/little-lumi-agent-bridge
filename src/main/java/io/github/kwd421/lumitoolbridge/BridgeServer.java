package io.github.kwd421.lumitoolbridge;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BridgeServer implements AutoCloseable {
    private final Config config;
    private final AgentBridge agent;
    private final Instant startedAt = Instant.now();
    private final HttpServer server;
    private final ExecutorService executor;

    public BridgeServer(Config config) throws IOException {
        this.config = config;
        this.agent = new AgentBridge(config);
        InetAddress bindAddress = InetAddress.getByName(config.host());
        if (!config.allowRemoteClients() && !bindAddress.isLoopbackAddress()) {
            throw new IllegalArgumentException("Refusing non-loopback bind address without server.allowRemoteClients=true");
        }
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, config.port()), 32);
        this.executor = Executors.newFixedThreadPool(config.threads(), runnable -> {
            Thread thread = new Thread(runnable, "little-lumi-agent-bridge-http");
            thread.setDaemon(false);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/health", this::health);
        server.createContext("/v1/health", this::health);
        server.createContext("/v1/chat/completions", this::chat);
        server.createContext("/chat/completions", this::chat);
    }

    public void start() {
        server.start();
    }

    public String address() {
        return "http://" + config.host() + ":" + config.port();
    }

    public java.util.List<String> toolNames() {
        return agent.toolNames();
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, Json.write(Map.of("error", "Method not allowed")));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("name", "little-lumi-agent-bridge");
        body.put("version", Config.VERSION);
        body.put("started_at", startedAt.toString());
        body.put("tools", agent.toolNames());
        body.put("upstream", config.upstreamChatBase());
        send(exchange, 200, Json.write(body));
    }

    private void chat(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, Json.write(Map.of("error", Map.of("message", "Method not allowed"))));
            return;
        }
        String requestBody;
        try {
            requestBody = readLimited(exchange.getRequestBody(), config.maxRequestBytes());
        } catch (RequestTooLargeException exception) {
            send(exchange, 413, Json.write(Map.of("error", Map.of("message", exception.getMessage()))));
            return;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        AgentBridge.Result result = agent.handle(requestBody, authorization);
        send(exchange, result.statusCode(), result.body());
    }

    private static String readLimited(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 64 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximum) throw new RequestTooLargeException("Request exceeds " + maximum + " bytes");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdownNow();
        agent.close();
    }

    private static final class RequestTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
        private RequestTooLargeException(String message) { super(message); }
    }
}
