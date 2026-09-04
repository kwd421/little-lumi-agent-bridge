package io.github.kwd421.lumitoolbridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional local JSONL cache for evidence actually returned by web tools. */
public final class EvidenceStore {
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}_-]{2,}");
    private static final Set<String> STOPWORDS = Set.of(
            "그리고", "하지만", "그러면", "이거", "저거", "그거", "대한", "대해", "어떻게",
            "무엇", "뭐야", "알려줘", "해주세요", "있어요", "합니다", "the", "and", "for",
            "with", "what", "about", "this", "that", "from");

    private final Config config;
    private final Path path;
    private final Object lock = new Object();

    public EvidenceStore(Config config) {
        this.config = config;
        this.path = config.evidencePath();
    }

    public void record(String tool, Map<String, Object> output) {
        if (!config.evidenceEnabled()) return;
        List<Map<String, Object>> entries = extract(tool, output);
        if (entries.isEmpty()) return;
        synchronized (lock) {
            try {
                Files.createDirectories(path.getParent());
                StringBuilder lines = new StringBuilder();
                for (Map<String, Object> entry : entries) {
                    lines.append(Json.write(entry)).append(System.lineSeparator());
                }
                Files.writeString(path, lines.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                trimIfNeeded();
            } catch (IOException exception) {
                if (config.verboseLogging()) {
                    System.err.println("Evidence cache write failed: " + safe(exception));
                }
            }
        }
    }

    public String recall(String query) {
        if (!config.evidenceEnabled() || !config.evidenceRecallEnabled() || config.evidenceRecallCount() <= 0) {
            return "";
        }
        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty() || !Files.isRegularFile(path)) return "";
        synchronized (lock) {
            try {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                List<Scored> scored = new ArrayList<>();
                int start = Math.max(0, lines.size() - config.evidenceMaxEntries());
                for (int i = start; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (line.isEmpty()) continue;
                    Map<String, Object> item;
                    try { item = Json.object(Json.parse(line)); }
                    catch (RuntimeException ignored) { continue; }
                    String searchable = value(item.get("query")) + " " + value(item.get("title")) + " " + value(item.get("content"));
                    Set<String> itemTokens = tokens(searchable);
                    int overlap = 0;
                    for (String token : queryTokens) if (itemTokens.contains(token)) overlap++;
                    if (overlap == 0) continue;
                    int officialBonus = value(item.get("url")).contains("eternalreturn") ? 1 : 0;
                    scored.add(new Scored(overlap * 10 + officialBonus, i, item));
                }
                scored.sort(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(Comparator.comparingInt(Scored::index).reversed()));
                if (scored.isEmpty()) return "";
                StringBuilder result = new StringBuilder("[과거 웹 도구로 확인한 근거 캐시]\n");
                int count = Math.min(config.evidenceRecallCount(), scored.size());
                for (int i = 0; i < count; i++) {
                    Map<String, Object> item = scored.get(i).item();
                    result.append(i + 1).append(". 확인 시각 ").append(value(item.get("retrieved_at")))
                            .append("; 제목 ").append(value(item.get("title")))
                            .append("; 출처 ").append(value(item.get("url")))
                            .append("; 내용 ").append(truncate(value(item.get("content")), 2500)).append('\n');
                }
                result.append("이 캐시는 오래되었을 수 있습니다. 최신성이 중요하면 다시 검색하세요.");
                return result.toString();
            } catch (IOException exception) {
                return "";
            }
        }
    }

    private List<Map<String, Object>> extract(String tool, Map<String, Object> output) {
        List<Map<String, Object>> entries = new ArrayList<>();
        String retrievedAt = value(output.getOrDefault("searched_at", output.getOrDefault("fetched_at", Instant.now().toString())));
        String query = value(output.getOrDefault("query", output.get("url")));
        if (tool.equals("web_search") || tool.equals("eternal_return_search")) {
            Object raw = output.get("results");
            if (raw instanceof List<?> results) {
                for (Object value : results) {
                    if (!(value instanceof Map<?, ?> item)) continue;
                    entries.add(entry(retrievedAt, tool, query,
                            value(item.get("title")), value(item.get("url")), value(item.get("content"))));
                }
            }
        } else if (tool.equals("web_fetch")) {
            entries.add(entry(retrievedAt, tool, query,
                    value(output.get("title")), value(output.get("url")), value(output.get("content"))));
        }
        return entries;
    }

    private Map<String, Object> entry(String retrievedAt, String tool, String query,
                                      String title, String url, String content) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("retrieved_at", retrievedAt);
        entry.put("tool", tool);
        entry.put("query", truncate(query, 1000));
        entry.put("title", truncate(title, 500));
        entry.put("url", truncate(url, 2000));
        entry.put("content", truncate(content, config.evidenceMaxEntryChars()));
        return entry;
    }

    private void trimIfNeeded() throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        int max = config.evidenceMaxEntries();
        if (lines.size() <= max + 20) return;
        List<String> kept = lines.subList(Math.max(0, lines.size() - max), lines.size());
        Files.write(path, kept, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = TOKEN.matcher(text == null ? "" : text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOPWORDS.contains(token)) result.add(token);
        }
        return result;
    }

    private static String value(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, Math.max(1, max - 1)) + "…";
    }
    private static String safe(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
    private record Scored(int score, int index, Map<String, Object> item) {}
}
