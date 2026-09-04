package io.github.kwd421.lumitoolbridge.tools.local;

import io.github.kwd421.lumitoolbridge.Config;
import io.github.kwd421.lumitoolbridge.Json;
import io.github.kwd421.lumitoolbridge.Tool;
import io.github.kwd421.lumitoolbridge.ToolContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class LocalListTool implements Tool {
    private final Config config;
    private final LocalFileAccess access;

    public LocalListTool(Config config, LocalFileAccess access) {
        this.config = config;
        this.access = access;
    }

    @Override public String name() { return "local_list"; }

    @Override
    public Map<String, Object> definition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name(),
                        "description", "List files and folders inside the user's explicitly configured local roots. Use this to discover project structure before reading files. Read-only.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "path", Map.of("type", "string", "description", "Absolute path inside an allowed root, or a relative path. Omit to list configured roots."),
                                        "depth", Map.of("type", "integer", "minimum", 1, "maximum", 5, "description", "Directory traversal depth. Default 1."),
                                        "max_entries", Map.of("type", "integer", "minimum", 1, "maximum", 1000)),
                                "additionalProperties", false)));
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) throws Exception {
        String raw = Json.string(arguments.get("path"));
        if ((raw == null || raw.isBlank()) && access.roots().size() != 1) {
            List<String> roots = access.roots().stream().map(Path::toString).toList();
            return Map.of("ok", true, "roots", roots, "note", "Choose one root and call local_list again.");
        }
        Path path = access.resolveExisting(raw == null ? "" : raw);
        int depth = Math.max(1, Math.min(5, Json.integer(arguments.get("depth"), 1)));
        int maxEntries = Math.max(1, Math.min(config.localFilesMaxEntries(),
                Json.integer(arguments.get("max_entries"), Math.min(200, config.localFilesMaxEntries()))));
        if (!Files.isDirectory(path)) {
            return Map.of("ok", true, "path", path.toString(), "entries", List.of(entry(path)));
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(path, depth)) {
            stream.filter(item -> !item.equals(path))
                    .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                    .limit(maxEntries)
                    .forEach(item -> entries.add(entry(item)));
        }
        return Map.of(
                "ok", true,
                "path", path.toString(),
                "depth", depth,
                "entries", entries,
                "truncated", entries.size() >= maxEntries);
    }

    private static Map<String, Object> entry(Path path) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path.toString());
        result.put("name", path.getFileName() == null ? path.toString() : path.getFileName().toString());
        result.put("type", Files.isDirectory(path) ? "directory" : Files.isRegularFile(path) ? "file" : "other");
        if (Files.isRegularFile(path)) {
            try { result.put("size", Files.size(path)); } catch (Exception ignored) {}
        }
        return result;
    }
}
