package io.github.kwd421.lumitoolbridge;

import io.github.kwd421.lumitoolbridge.mcp.McpManager;
import io.github.kwd421.lumitoolbridge.tools.CodexTool;
import io.github.kwd421.lumitoolbridge.tools.WebFetchTool;
import io.github.kwd421.lumitoolbridge.tools.WebSearchTool;
import io.github.kwd421.lumitoolbridge.tools.local.LocalFileAccess;
import io.github.kwd421.lumitoolbridge.tools.local.LocalListTool;
import io.github.kwd421.lumitoolbridge.tools.local.LocalReadTool;
import io.github.kwd421.lumitoolbridge.tools.local.LocalSearchTool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ToolRegistry implements AutoCloseable {
    private final Config config;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final int maxOutputChars;
    private final EvidenceStore evidence;
    private final McpManager mcp;

    public ToolRegistry(Config config, HttpJsonClient http) {
        this.config = config;
        this.maxOutputChars = config.maxToolOutputChars();
        this.evidence = new EvidenceStore(config);
        if (config.webSearchEnabled()) register(new WebSearchTool(config, http, evidence, false));
        if (config.eternalReturnSearchEnabled()) register(new WebSearchTool(config, http, evidence, true));
        if (config.webFetchEnabled()) register(new WebFetchTool(config, http, evidence));

        if (config.localFilesEnabled()) {
            LocalFileAccess access = new LocalFileAccess(config);
            if (access.available()) {
                register(new LocalListTool(config, access));
                register(new LocalReadTool(config, access));
                register(new LocalSearchTool(config, access));
            } else if (config.verboseLogging()) {
                System.err.println("Local file tools enabled but no configured root exists.");
            }
        }

        if (config.codexEnabled()) register(new CodexTool(config));

        this.mcp = new McpManager(config);
        for (Tool tool : mcp.tools()) register(tool);
        if (config.verboseLogging()) {
            for (String error : mcp.errors()) System.err.println(error);
        }
    }

    public ToolRegistry(int maxOutputChars, List<Tool> customTools) {
        this.config = null;
        this.maxOutputChars = maxOutputChars;
        this.evidence = null;
        this.mcp = null;
        customTools.forEach(this::register);
    }

    private void register(Tool tool) {
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("Duplicate tool: " + tool.name());
        }
    }

    public List<Map<String, Object>> definitions(RequestContext context) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        if (context.compaction()) return definitions;
        for (Tool tool : tools.values()) {
            if (tool.name().equals("codex_task") && !context.allowsCodex()) continue;
            if (tool.name().startsWith("local_") && !context.localFileIntent() && !context.codingIntent()) continue;
            definitions.add(tool.definition());
        }
        return definitions;
    }

    public List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(tools.keySet()));
    }

    public boolean contains(String name) { return tools.containsKey(name); }

    public String recallEvidence(String query) {
        return evidence == null ? "" : evidence.recall(query);
    }

    public String execute(String name, Map<String, Object> arguments, String authorization, RequestContext request) {
        Tool tool = tools.get(name);
        Object result;
        if (tool == null) {
            result = Map.of("ok", false, "error", "Unknown or disabled tool: " + name);
        } else if (name.equals("codex_task") && !request.allowsCodex()) {
            result = Map.of("ok", false, "error", "Codex is only available for an explicit coding, repository, or local-project request.");
        } else {
            try {
                if (config != null && config.verboseLogging()) {
                    System.out.println("Tool call: " + name
                            + (config.logToolArguments() ? " " + Json.write(arguments) : ""));
                }
                result = tool.execute(arguments, new ToolContext(authorization, Instant.now(), request));
            } catch (Exception exception) {
                result = Map.of("ok", false, "error", safeMessage(exception));
            }
        }
        String encoded = Json.write(result);
        if (encoded.length() <= maxOutputChars) return encoded;
        int keep = Math.max(1, maxOutputChars - 200);
        return Json.write(Map.of(
                "ok", true,
                "truncated", true,
                "content", encoded.substring(0, keep),
                "note", "Tool output was truncated by the local bridge."));
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    @Override
    public void close() {
        if (mcp != null) mcp.close();
    }
}
