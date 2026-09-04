package io.github.kwd421.lumitoolbridge.mcp;

import io.github.kwd421.lumitoolbridge.Config;
import io.github.kwd421.lumitoolbridge.Json;
import io.github.kwd421.lumitoolbridge.Tool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads optional stdio MCP servers and adapts their tools to OpenAI function definitions. */
public final class McpManager implements AutoCloseable {
    private final List<McpProcess> processes = new ArrayList<>();
    private final List<Tool> tools = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    public McpManager(Config config) {
        if (!config.mcpEnabled()) return;
        Path path = config.mcpConfigPath();
        if (!Files.isRegularFile(path)) {
            errors.add("MCP config not found: " + path);
            return;
        }
        try {
            Map<String, Object> root = Json.object(Json.parse(Files.readString(path, StandardCharsets.UTF_8)));
            Object serversValue = root.get("mcpServers");
            if (!(serversValue instanceof Map<?, ?> servers)) {
                errors.add("MCP config has no mcpServers object");
                return;
            }
            int remaining = config.mcpMaxTools();
            for (Map.Entry<?, ?> entry : servers.entrySet()) {
                if (remaining <= 0) break;
                String serverName = String.valueOf(entry.getKey());
                if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                Map<String, Object> server = Json.object(raw);
                if (!bool(server.get("enabled"), true)) continue;
                String command = string(server.get("command"));
                if (command.isBlank()) {
                    errors.add("MCP server " + serverName + " has no command");
                    continue;
                }
                List<String> args = strings(server.get("args"));
                Map<String, String> env = stringMap(server.get("env"));
                boolean allowWrite = bool(server.get("allowWrite"), config.mcpAllowWrite());
                try {
                    McpProcess process = new McpProcess(serverName, command, args, env,
                            config.mcpTimeout(), config.verboseLogging());
                    processes.add(process);
                    for (McpProcess.ToolInfo info : process.listTools(remaining)) {
                        if (!info.readOnly() && !allowWrite) continue;
                        String exposed = uniqueName(serverName, info.name());
                        tools.add(new McpTool(exposed, serverName, info, process, allowWrite));
                        remaining--;
                        if (remaining <= 0) break;
                    }
                } catch (Exception exception) {
                    errors.add("MCP server " + serverName + " failed: " + safe(exception));
                }
            }
        } catch (Exception exception) {
            errors.add("Could not load MCP config: " + safe(exception));
        }
    }

    public List<Tool> tools() { return List.copyOf(tools); }
    public List<String> errors() { return List.copyOf(errors); }

    private String uniqueName(String server, String tool) {
        String base = sanitize("mcp_" + server + "__" + tool);
        String candidate = base;
        int suffix = 2;
        while (containsName(candidate)) {
            String tail = "_" + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 64 - tail.length())) + tail;
        }
        return candidate;
    }

    private boolean containsName(String name) {
        for (Tool tool : tools) if (tool.name().equals(name)) return true;
        return false;
    }

    private static String sanitize(String value) {
        String result = value.replaceAll("[^A-Za-z0-9_-]", "_");
        while (result.contains("__")) result = result.replace("__", "_");
        if (result.length() > 64) result = result.substring(0, 64);
        if (result.isBlank()) result = "mcp_tool";
        return result;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) if (item != null) result.add(String.valueOf(item));
        return List.copyOf(result);
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return Map.copyOf(result);
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        if (value == null) return fallback;
        String text = String.valueOf(value).toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "on").contains(text)) return true;
        if (List.of("false", "0", "no", "off").contains(text)) return false;
        return fallback;
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String safe(Exception exception) {
        String text = exception.getMessage();
        if (text == null || text.isBlank()) text = exception.getClass().getSimpleName();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    @Override
    public void close() {
        for (McpProcess process : processes) {
            try { process.close(); } catch (Exception ignored) {}
        }
    }
}
