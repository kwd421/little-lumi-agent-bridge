package io.github.kwd421.lumitoolbridge.tools.local;

import io.github.kwd421.lumitoolbridge.Config;
import io.github.kwd421.lumitoolbridge.Json;
import io.github.kwd421.lumitoolbridge.Tool;
import io.github.kwd421.lumitoolbridge.ToolContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LocalReadTool implements Tool {
    private final Config config;
    private final LocalFileAccess access;

    public LocalReadTool(Config config, LocalFileAccess access) {
        this.config = config;
        this.access = access;
    }

    @Override public String name() { return "local_read"; }

    @Override
    public Map<String, Object> definition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name(),
                        "description", "Read a UTF-8 text file inside an explicitly configured local root. Use after local_list/local_search when the user asks about local files or source code. Read-only.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "path", Map.of("type", "string", "description", "File path inside an allowed root."),
                                        "start_line", Map.of("type", "integer", "minimum", 1, "description", "1-based first line. Default 1."),
                                        "max_lines", Map.of("type", "integer", "minimum", 1, "maximum", 2000, "description", "Maximum lines. Default 400.")),
                                "required", List.of("path"),
                                "additionalProperties", false)));
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) throws Exception {
        String raw = Json.string(arguments.get("path"));
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("path is required");
        Path path = access.resolveExisting(raw);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("path is not a regular file");
        long size = Files.size(path);
        if (size > Math.max(8L * config.localFilesMaxReadChars(), 4_000_000L)) {
            throw new IllegalArgumentException("file is too large for local_read; use local_search or a narrower file");
        }
        byte[] bytes = Files.readAllBytes(path);
        for (byte value : bytes) if (value == 0) throw new IllegalArgumentException("file appears to be binary");
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\\R", -1);
        int start = Math.max(1, Json.integer(arguments.get("start_line"), 1));
        int maxLines = Math.max(1, Math.min(2000, Json.integer(arguments.get("max_lines"), 400)));
        int from = Math.min(lines.length, start - 1);
        int to = Math.min(lines.length, from + maxLines);
        List<String> selected = new ArrayList<>();
        int chars = 0;
        for (int i = from; i < to; i++) {
            String rendered = (i + 1) + ": " + lines[i];
            if (chars + rendered.length() + 1 > config.localFilesMaxReadChars()) break;
            selected.add(rendered);
            chars += rendered.length() + 1;
        }
        return Map.of(
                "ok", true,
                "path", path.toString(),
                "start_line", start,
                "returned_lines", selected.size(),
                "total_lines", lines.length,
                "content", String.join("\n", selected),
                "truncated", from + selected.size() < lines.length);
    }
}
