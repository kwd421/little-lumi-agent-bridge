package io.github.kwd421.lumitoolbridge;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class Config {
    public static final String VERSION = "0.3.0";

    private final Path source;
    private final Path baseDirectory;
    private final Properties properties;

    private Config(Path source, Properties properties) {
        this.source = source.toAbsolutePath().normalize();
        Path parent = this.source.getParent();
        this.baseDirectory = parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
        this.properties = properties;
    }

    public static Config load(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return new Config(path, properties);
    }

    public Path source() { return source; }
    public Path baseDirectory() { return baseDirectory; }
    public String host() { return get("server.host", "127.0.0.1"); }
    public int port() { return boundedInt("server.port", 11435, 1, 65535); }
    public int threads() { return boundedInt("server.threads", 16, 2, 128); }
    public int maxRequestBytes() { return boundedInt("server.maxRequestBytes", 8 * 1024 * 1024, 1024, 64 * 1024 * 1024); }
    public boolean allowRemoteClients() { return bool("server.allowRemoteClients", false); }

    public String upstreamChatBase() { return trimSlash(get("upstream.chatBase", "https://ollama.com/v1")); }
    public String upstreamWebBase() { return trimSlash(get("upstream.webBase", "https://ollama.com/api")); }
    public Duration upstreamTimeout() { return Duration.ofSeconds(boundedInt("upstream.timeoutSeconds", 180, 5, 1800)); }
    public String apiKeyEnvironmentVariable() { return get("upstream.apiKeyEnv", "OLLAMA_API_KEY"); }

    public int maxToolRounds() { return boundedInt("agent.maxToolRounds", 5, 1, 12); }
    public int maxToolCalls() { return boundedInt("agent.maxToolCalls", 10, 1, 40); }
    public int maxToolOutputChars() { return boundedInt("agent.maxToolOutputChars", 24000, 1000, 200000); }
    public boolean injectToolInstructions() { return bool("agent.injectToolInstructions", true); }
    public boolean compactionGuardEnabled() { return bool("agent.compactionGuard.enabled", true); }
    public boolean autoSearchEnabled() { return bool("agent.autoSearch.enabled", true); }
    public boolean autoSearchCurrentInfo() { return bool("agent.autoSearch.currentInfo", true); }
    public boolean autoSearchEternalReturnEntities() { return bool("agent.autoSearch.eternalReturnEntities", true); }
    public boolean verboseLogging() { return bool("logging.verbose", false); }
    public boolean logToolArguments() { return bool("logging.toolArguments", false); }

    public boolean webSearchEnabled() { return bool("tools.webSearch.enabled", true); }
    public int webSearchMaxResults() { return boundedInt("tools.webSearch.maxResults", 5, 1, 10); }
    public int webSearchMaxSnippetChars() { return boundedInt("tools.webSearch.maxSnippetChars", 6000, 500, 30000); }
    public boolean webFetchEnabled() { return bool("tools.webFetch.enabled", true); }
    public int webFetchMaxContentChars() { return boundedInt("tools.webFetch.maxContentChars", 24000, 1000, 200000); }
    public int webFetchMaxLinks() { return boundedInt("tools.webFetch.maxLinks", 30, 0, 200); }
    public boolean eternalReturnSearchEnabled() { return bool("tools.eternalReturn.enabled", true); }
    public List<String> eternalReturnDomains() {
        String raw = get("tools.eternalReturn.officialDomains", "playeternalreturn.com,eternalreturn.com");
        List<String> result = new ArrayList<>();
        for (String item : raw.split(",")) {
            String value = item.strip().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) result.add(value);
        }
        return List.copyOf(result);
    }

    public boolean localFilesEnabled() { return bool("tools.files.enabled", false); }
    public List<Path> localFileRoots() {
        String raw = get("tools.files.roots", "");
        List<Path> result = new ArrayList<>();
        for (String item : raw.split(";")) {
            String value = item.strip();
            if (!value.isEmpty()) result.add(resolvePath(value));
        }
        return List.copyOf(result);
    }
    public int localFilesMaxReadChars() { return boundedInt("tools.files.maxReadChars", 32000, 1000, 500000); }
    public int localFilesMaxEntries() { return boundedInt("tools.files.maxEntries", 200, 10, 5000); }
    public int localFilesSearchMaxFiles() { return boundedInt("tools.files.searchMaxFiles", 2000, 10, 50000); }
    public int localFilesSearchMaxBytesPerFile() { return boundedInt("tools.files.searchMaxBytesPerFile", 512000, 4096, 10000000); }

    public boolean mcpEnabled() { return bool("tools.mcp.enabled", false); }
    public Path mcpConfigPath() { return resolvePath(get("tools.mcp.config", "config/mcp.json")); }
    public Duration mcpTimeout() { return Duration.ofSeconds(boundedInt("tools.mcp.timeoutSeconds", 30, 2, 300)); }
    public boolean mcpAllowWrite() { return bool("tools.mcp.allowWrite", false); }
    public int mcpMaxTools() { return boundedInt("tools.mcp.maxTools", 64, 1, 256); }

    public boolean codexEnabled() { return bool("tools.codex.enabled", false); }
    public String codexCommand() { return get("tools.codex.command", "codex"); }
    public Path codexWorkspace() {
        String raw = get("tools.codex.workspace", "");
        return raw.isBlank() ? null : resolvePath(raw);
    }
    public boolean codexWriteEnabled() { return bool("tools.codex.writeEnabled", false); }
    public boolean codexEphemeral() { return bool("tools.codex.ephemeral", true); }
    public boolean codexIgnoreUserConfig() { return bool("tools.codex.ignoreUserConfig", true); }
    public boolean codexUseChatgptOAuth() { return bool("tools.codex.useChatgptOAuth", true); }
    public boolean codexIgnoreRules() { return bool("tools.codex.ignoreRules", false); }
    public boolean codexScrubSensitiveEnvironment() { return bool("tools.codex.scrubSensitiveEnvironment", true); }
    public Duration codexTimeout() { return Duration.ofSeconds(boundedInt("tools.codex.timeoutSeconds", 240, 10, 3600)); }
    public int codexMaxOutputChars() { return boundedInt("tools.codex.maxOutputChars", 32000, 1000, 200000); }
    public String codexModel() { return get("tools.codex.model", ""); }

    public boolean evidenceEnabled() { return bool("memory.evidence.enabled", false); }
    public boolean evidenceRecallEnabled() { return bool("memory.evidence.recall", false); }
    public Path evidencePath() { return resolvePath(get("memory.evidence.path", "data/evidence.jsonl")); }
    public int evidenceMaxEntries() { return boundedInt("memory.evidence.maxEntries", 300, 10, 5000); }
    public int evidenceRecallCount() { return boundedInt("memory.evidence.recallCount", 2, 0, 10); }
    public int evidenceMaxEntryChars() { return boundedInt("memory.evidence.maxEntryChars", 16000, 1000, 100000); }

    public String authorizationForUpstream(String incomingAuthorization) {
        String environmentName = apiKeyEnvironmentVariable();
        if (!environmentName.isBlank()) {
            String key = System.getenv(environmentName);
            if (key != null && !key.isBlank()) {
                return key.regionMatches(true, 0, "Bearer ", 0, 7) ? key.trim() : "Bearer " + key.trim();
            }
        }
        return incomingAuthorization == null ? "" : incomingAuthorization.trim();
    }

    private Path resolvePath(String raw) {
        String expanded = raw.replace("${user.home}", System.getProperty("user.home", ""));
        Path path = Path.of(expanded);
        if (!path.isAbsolute()) path = baseDirectory.resolve(path);
        return path.normalize().toAbsolutePath();
    }

    private String get(String key, String defaultValue) {
        String environment = environmentOverride(key);
        if (environment != null && !environment.isBlank()) return environment.strip();
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String environmentOverride(String key) {
        String name = switch (key) {
            case "server.host" -> "LUMI_BRIDGE_HOST";
            case "server.port" -> "LUMI_BRIDGE_PORT";
            case "upstream.chatBase" -> "LUMI_UPSTREAM_BASE";
            case "agent.autoSearch.enabled" -> "LUMI_AUTO_SEARCH";
            case "tools.files.enabled" -> "LUMI_FILES_ENABLED";
            case "tools.files.roots" -> "LUMI_FILES_ROOTS";
            case "tools.mcp.enabled" -> "LUMI_MCP_ENABLED";
            case "tools.mcp.config" -> "LUMI_MCP_CONFIG";
            case "tools.codex.enabled" -> "LUMI_CODEX_ENABLED";
            case "tools.codex.command" -> "LUMI_CODEX_COMMAND";
            case "tools.codex.workspace" -> "LUMI_CODEX_WORKSPACE";
            case "tools.codex.writeEnabled" -> "LUMI_CODEX_WRITE_ENABLED";
            default -> null;
        };
        return name == null ? null : System.getenv(name);
    }

    private boolean bool(String key, boolean defaultValue) {
        String value = get(key, "");
        if (value.isBlank()) return defaultValue;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> defaultValue;
        };
    }

    private int boundedInt(String key, int defaultValue, int minimum, int maximum) {
        String value = get(key, "");
        int parsed = defaultValue;
        if (!value.isBlank()) {
            try { parsed = Integer.parseInt(value); } catch (NumberFormatException ignored) { parsed = defaultValue; }
        }
        return Math.max(minimum, Math.min(maximum, parsed));
    }

    private static String trimSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
