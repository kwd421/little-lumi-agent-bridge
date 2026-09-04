package io.github.kwd421.lumitoolbridge.tools.local;

import io.github.kwd421.lumitoolbridge.Config;
import io.github.kwd421.lumitoolbridge.Json;
import io.github.kwd421.lumitoolbridge.Tool;
import io.github.kwd421.lumitoolbridge.ToolContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class LocalSearchTool implements Tool {
    private final Config config;
    private final LocalFileAccess access;

    public LocalSearchTool(Config config, LocalFileAccess access) {
        this.config = config;
        this.access = access;
    }

    @Override public String name() { return "local_search"; }

    @Override
    public Map<String, Object> definition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name(),
                        "description", "Search file names and text contents inside configured local roots. Use this to locate source files, symbols, config values, or documents before local_read. Read-only.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string", "description", "Case-insensitive literal text to find in file names or file contents."),
                                        "path", Map.of("type", "string", "description", "Optional directory or file path inside an allowed root."),
                                        "glob", Map.of("type", "string", "description", "Optional filename glob such as **/*.java or *.json."),
                                        "max_results", Map.of("type", "integer", "minimum", 1, "maximum", 100)),
                                "required", List.of("query"),
                                "additionalProperties", false)));
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) throws Exception {
        String query = Json.string(arguments.get("query"));
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        if (query.length() > 500) throw new IllegalArgumentException("query is too long");
        String rawPath = Json.string(arguments.get("path"));
        String glob = Json.string(arguments.get("glob"));
        int maxResults = Math.max(1, Math.min(100, Json.integer(arguments.get("max_results"), 30)));
        List<Path> bases;
        if (rawPath == null || rawPath.isBlank()) bases = access.roots();
        else bases = List.of(access.resolveExisting(rawPath));
        if (bases.isEmpty()) throw new IllegalStateException("no usable local-file roots are configured");

        PathMatcher matcher = null;
        if (glob != null && !glob.isBlank()) matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob.strip());
        String needle = query.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> results = new ArrayList<>();
        int visited = 0;
        outer:
        for (Path base : bases) {
            try (Stream<Path> stream = Files.isDirectory(base) ? Files.walk(base) : Stream.of(base)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    if (++visited > config.localFilesSearchMaxFiles()) break outer;
                    Path relative = Files.isDirectory(base) ? base.relativize(path) : path.getFileName();
                    if (matcher != null && relative != null && !matcher.matches(relative) && !matcher.matches(path.getFileName())) continue;
                    String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
                    if (fileName.toLowerCase(Locale.ROOT).contains(needle)) {
                        results.add(Map.of("path", path.toString(), "kind", "filename", "snippet", fileName));
                        if (results.size() >= maxResults) break outer;
                    }
                    long size;
                    try { size = Files.size(path); } catch (Exception ignored) { continue; }
                    if (size > config.localFilesSearchMaxBytesPerFile()) continue;
                    byte[] bytes;
                    try { bytes = Files.readAllBytes(path); } catch (Exception ignored) { continue; }
                    boolean binary = false;
                    for (byte value : bytes) if (value == 0) { binary = true; break; }
                    if (binary) continue;
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    String[] lines = text.split("\\R", -1);
                    for (int line = 0; line < lines.length; line++) {
                        String value = lines[line];
                        int index = value.toLowerCase(Locale.ROOT).indexOf(needle);
                        if (index < 0) continue;
                        int from = Math.max(0, index - 120);
                        int to = Math.min(value.length(), index + query.length() + 180);
                        results.add(Map.of(
                                "path", path.toString(),
                                "kind", "content",
                                "line", line + 1,
                                "snippet", value.substring(from, to)));
                        if (results.size() >= maxResults) break outer;
                    }
                }
            }
        }
        return Map.of(
                "ok", true,
                "query", query,
                "results", results,
                "files_scanned", Math.min(visited, config.localFilesSearchMaxFiles()),
                "truncated", visited > config.localFilesSearchMaxFiles() || results.size() >= maxResults);
    }
}
