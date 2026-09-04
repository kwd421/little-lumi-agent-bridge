package io.github.kwd421.lumitoolbridge.mcp;

import io.github.kwd421.lumitoolbridge.Config;
import io.github.kwd421.lumitoolbridge.Json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal MCP stdio client for local servers. One request is in flight per server. */
final class McpProcess implements AutoCloseable {
    record ToolInfo(String name, String title, String description, Map<String, Object> inputSchema,
                    boolean readOnly, boolean destructive) {}

    private final String serverName;
    private final Duration timeout;
    private final boolean verbose;
    private final Process process;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final ExecutorService readerExecutor;
    private final Thread stderrThread;
    private final AtomicLong nextId = new AtomicLong(1);

    McpProcess(String serverName, String command, List<String> args, Map<String, String> env,
               Duration timeout, boolean verbose) throws Exception {
        this.serverName = serverName;
        this.timeout = timeout;
        this.verbose = verbose;
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        commandLine.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(commandLine);
        scrubInheritedSecrets(builder.environment());
        if (!env.isEmpty()) builder.environment().putAll(env);
        this.process = builder.start();
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.readerExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lumi-mcp-reader-" + serverName);
            thread.setDaemon(true);
            return thread;
        });
        this.stderrThread = new Thread(() -> drainStderr(process, serverName, verbose), "lumi-mcp-stderr-" + serverName);
        this.stderrThread.setDaemon(true);
        this.stderrThread.start();
        initialize();
    }

    private void initialize() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", "2026-07-28");
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "little-lumi-agent-bridge", "version", Config.VERSION));
        Map<String, Object> result = request("initialize", params);
        if (verbose) System.out.println("MCP " + serverName + " initialized: " + result.get("protocolVersion"));
        notify("notifications/initialized", Map.of());
    }

    synchronized List<ToolInfo> listTools(int maximum) throws Exception {
        List<ToolInfo> result = new ArrayList<>();
        String cursor = null;
        do {
            Map<String, Object> params = cursor == null ? Map.of() : Map.of("cursor", cursor);
            Map<String, Object> page = request("tools/list", params);
            Object tools = page.get("tools");
            if (tools instanceof List<?> list) {
                for (Object value : list) {
                    if (!(value instanceof Map<?, ?> raw)) continue;
                    Map<String, Object> tool = Json.object(raw);
                    String name = string(tool.get("name"));
                    if (name.isBlank()) continue;
                    String title = string(tool.get("title"));
                    String description = string(tool.get("description"));
                    Map<String, Object> schema = tool.get("inputSchema") instanceof Map<?, ?> map
                            ? Json.object(Json.deepCopy(map))
                            : Map.of("type", "object", "properties", Map.of());
                    Map<String, Object> annotations = tool.get("annotations") instanceof Map<?, ?> map
                            ? Json.object(map) : Map.of();
                    Boolean readOnlyHint = boolOrNull(annotations.get("readOnlyHint"));
                    Boolean destructiveHint = boolOrNull(annotations.get("destructiveHint"));
                    boolean readOnly = readOnlyHint != null ? readOnlyHint : guessReadOnly(name);
                    boolean destructive = destructiveHint != null ? destructiveHint : !readOnly && guessDestructive(name);
                    result.add(new ToolInfo(name, title, description, schema, readOnly, destructive));
                    if (result.size() >= maximum) return List.copyOf(result);
                }
            }
            cursor = string(page.get("nextCursor"));
            if (cursor.isBlank()) cursor = null;
        } while (cursor != null && result.size() < maximum);
        return List.copyOf(result);
    }

    synchronized Map<String, Object> callTool(String name, Map<String, Object> arguments) throws Exception {
        Map<String, Object> result = request("tools/call", Map.of("name", name, "arguments", arguments));
        Map<String, Object> output = new LinkedHashMap<>();
        boolean isError = Boolean.TRUE.equals(result.get("isError"));
        output.put("ok", !isError);
        output.put("mcp_server", serverName);
        output.put("mcp_tool", name);
        if (result.containsKey("structuredContent")) output.put("structured_content", Json.deepCopy(result.get("structuredContent")));
        if (result.get("content") instanceof List<?> blocks) {
            List<Object> compact = new ArrayList<>();
            for (Object block : blocks) {
                if (!(block instanceof Map<?, ?> raw)) continue;
                Map<String, Object> item = Json.object(raw);
                String type = string(item.get("type"));
                if ("text".equals(type)) compact.add(Map.of("type", "text", "text", string(item.get("text"))));
                else if ("resource_link".equals(type) || "resource".equals(type)) compact.add(Json.deepCopy(item));
                else compact.add(Map.of("type", type.isBlank() ? "unsupported" : type, "note", "binary MCP content omitted"));
            }
            output.put("content", compact);
        }
        if (isError) output.put("error", "MCP tool returned isError=true");
        return output;
    }

    private synchronized Map<String, Object> request(String method, Map<String, Object> params) throws Exception {
        long id = nextId.getAndIncrement();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        writeLine(request);

        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw timeout(method);
            String line = readLine(Duration.ofNanos(remaining));
            if (line == null) throw new IOException("MCP server exited while waiting for " + method);
            if (line.isBlank()) continue;
            Object parsed;
            try { parsed = Json.parse(line); }
            catch (RuntimeException ignored) { continue; }
            if (!(parsed instanceof Map<?, ?> raw)) continue;
            Map<String, Object> message = Json.object(raw);
            Object incomingId = message.get("id");
            if (message.get("method") != null && incomingId != null) {
                replyUnsupported(incomingId, string(message.get("method")));
                continue;
            }
            if (!sameId(incomingId, id)) continue;
            if (message.get("error") instanceof Map<?, ?> error) {
                throw new IllegalStateException("MCP " + serverName + " " + method + " error: " + Json.write(error));
            }
            if (message.get("result") instanceof Map<?, ?> result) return Json.object(result);
            if (message.get("result") == null) return new LinkedHashMap<>();
            return new LinkedHashMap<>(Map.of("value", Json.deepCopy(message.get("result"))));
        }
    }

    private void notify(String method, Map<String, Object> params) throws IOException {
        writeLine(Map.of("jsonrpc", "2.0", "method", method, "params", params));
    }

    private void replyUnsupported(Object id, String method) throws IOException {
        Map<String, Object> error = Map.of("code", -32601, "message", "Client method not supported: " + method);
        writeLine(Map.of("jsonrpc", "2.0", "id", id, "error", error));
    }

    private void writeLine(Map<String, Object> message) throws IOException {
        writer.write(Json.write(message));
        writer.newLine();
        writer.flush();
    }

    private String readLine(Duration remaining) throws Exception {
        Future<String> future = readerExecutor.submit(reader::readLine);
        try {
            return future.get(Math.max(1, remaining.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw timeout("response");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception e) throw e;
            throw new IllegalStateException(cause);
        }
    }

    private IllegalStateException timeout(String operation) {
        close();
        return new IllegalStateException("MCP server " + serverName + " timed out during " + operation);
    }

    private static boolean sameId(Object value, long expected) {
        if (value instanceof Number number) return number.longValue() == expected;
        return String.valueOf(expected).equals(String.valueOf(value));
    }

    private static Boolean boolOrNull(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    private static boolean guessReadOnly(String name) {
        String n = name.toLowerCase();
        return !(n.contains("write") || n.contains("edit") || n.contains("delete") || n.contains("remove")
                || n.contains("move") || n.contains("rename") || n.contains("create") || n.contains("mkdir")
                || n.contains("apply") || n.contains("commit") || n.contains("push") || n.contains("send"));
    }

    private static boolean guessDestructive(String name) {
        String n = name.toLowerCase();
        return n.contains("delete") || n.contains("remove") || n.contains("move") || n.contains("rename")
                || n.contains("overwrite") || n.contains("reset") || n.contains("force");
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }

    private static void scrubInheritedSecrets(Map<String, String> environment) {
        List<String> names = new ArrayList<>(environment.keySet());
        for (String name : names) {
            String upper = name.toUpperCase(java.util.Locale.ROOT);
            boolean explicit = upper.equals("GH_TOKEN")
                    || upper.equals("GITHUB_TOKEN")
                    || upper.equals("NPM_TOKEN")
                    || upper.equals("PYPI_TOKEN")
                    || upper.equals("SSH_AUTH_SOCK")
                    || upper.equals("GIT_ASKPASS")
                    || upper.equals("GOOGLE_APPLICATION_CREDENTIALS")
                    || upper.startsWith("AWS_")
                    || upper.startsWith("AZURE_");
            boolean secretLike = upper.endsWith("_API_KEY")
                    || upper.endsWith("_TOKEN")
                    || upper.endsWith("_SECRET")
                    || upper.endsWith("_PASSWORD")
                    || upper.endsWith("_CREDENTIALS");
            if (explicit || secretLike) environment.remove(name);
        }
    }

    private static void drainStderr(Process process, String serverName, boolean verbose) {
        try (BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = stderr.readLine()) != null) {
                if (verbose && !line.isBlank()) System.err.println("MCP[" + serverName + "] " + line);
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        try { writer.close(); } catch (Exception ignored) {}
        try { reader.close(); } catch (Exception ignored) {}
        if (process.isAlive()) {
            process.destroy();
            try { if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); process.destroyForcibly(); }
        }
        readerExecutor.shutdownNow();
    }
}
