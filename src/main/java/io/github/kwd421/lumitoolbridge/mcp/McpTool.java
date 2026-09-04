package io.github.kwd421.lumitoolbridge.mcp;

import io.github.kwd421.lumitoolbridge.Tool;
import io.github.kwd421.lumitoolbridge.ToolContext;

import java.util.LinkedHashMap;
import java.util.Map;

final class McpTool implements Tool {
    private final String exposedName;
    private final String serverName;
    private final McpProcess.ToolInfo info;
    private final McpProcess process;
    private final boolean writeAllowed;

    McpTool(String exposedName, String serverName, McpProcess.ToolInfo info,
            McpProcess process, boolean writeAllowed) {
        this.exposedName = exposedName;
        this.serverName = serverName;
        this.info = info;
        this.process = process;
        this.writeAllowed = writeAllowed;
    }

    @Override public String name() { return exposedName; }

    @Override
    public Map<String, Object> definition() {
        String description = info.description().isBlank() ? info.title() : info.description();
        if (description.isBlank()) description = "MCP tool " + info.name();
        String safety = info.readOnly()
                ? " This tool is read-only."
                : " This tool can change local/external state and may only be used when the user explicitly asked for a modification.";
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", exposedName);
        function.put("description", "MCP server " + serverName + ", tool " + info.name() + ": " + description + safety);
        function.put("parameters", info.inputSchema());
        return Map.of("type", "function", "function", function);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) throws Exception {
        if (!info.readOnly()) {
            if (!writeAllowed) throw new SecurityException("MCP write-capable tool is disabled by configuration");
            if (!context.request().explicitWriteIntent()) {
                throw new SecurityException("MCP write-capable tool requires an explicit user request to modify something");
            }
        }
        return process.callTool(info.name(), arguments);
    }
}
