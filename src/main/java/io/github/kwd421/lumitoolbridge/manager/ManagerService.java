package io.github.kwd421.lumitoolbridge.manager;

import io.github.kwd421.lumitoolbridge.Config;
import io.github.kwd421.lumitoolbridge.Json;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class ManagerService {
    private final Path installDir;
    private final Path releaseRoot;
    private Path littleLumiRoot;

    public ManagerService(Path installDir, Path releaseRoot, Path littleLumiRoot) {
        this.installDir = installDir.toAbsolutePath().normalize();
        this.releaseRoot = releaseRoot == null ? null : releaseRoot.toAbsolutePath().normalize();
        this.littleLumiRoot = littleLumiRoot == null ? null : littleLumiRoot.toAbsolutePath().normalize();
        Path stateRoot = littleLumiRootFromState();
        if (stateRoot != null) this.littleLumiRoot = stateRoot;
    }

    public Path installDir() { return installDir; }
    public Path releaseRoot() { return releaseRoot; }
    public Path littleLumiRoot() { return littleLumiRoot; }
    public void setLittleLumiRoot(Path path) { this.littleLumiRoot = path == null ? null : path.toAbsolutePath().normalize(); }
    public Path configPath() { return installDir.resolve("bridge.properties"); }
    public Path statePath() { return installDir.resolve("install-state.json"); }
    public boolean installed() { return Files.isRegularFile(statePath()) && Files.isRegularFile(configPath()); }

    public ManagerOptions loadOptions() throws IOException {
        Path source = installed() ? configPath() : defaultTemplate();
        Properties p = new Properties();
        if (source != null && Files.isRegularFile(source)) {
            try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                p.load(reader);
            }
        }
        return new ManagerOptions(
                littleLumiRoot,
                installed() ? startupShortcutExists() : true,
                bool(p, "tools.webSearch.enabled", true),
                bool(p, "tools.webFetch.enabled", true),
                bool(p, "agent.autoSearch.enabled", true),
                bool(p, "tools.eternalReturn.enabled", true),
                bool(p, "tools.files.enabled", false),
                pathList(p.getProperty("tools.files.roots", ""), source),
                bool(p, "tools.mcp.enabled", false),
                resolveMaybe(p.getProperty("tools.mcp.config", ""), source),
                bool(p, "tools.mcp.allowWrite", false),
                bool(p, "tools.codex.enabled", false),
                resolveMaybe(p.getProperty("tools.codex.workspace", ""), source),
                bool(p, "tools.codex.writeEnabled", false),
                bool(p, "memory.evidence.enabled", false),
                bool(p, "memory.evidence.recall", false),
                bool(p, "logging.verbose", false));
    }

    public void installOrApply(ManagerOptions options) throws Exception {
        validate(options);
        if (!installed()) install(options);
        applyConfig(options);
        setStartup(options.autoStart());
        restart();
    }

    private void install(ManagerOptions options) throws Exception {
        if (releaseRoot == null) throw new IOException("설치용 릴리스 폴더를 찾지 못했습니다. 배포 ZIP 안의 GUI 런처로 실행해 주세요.");
        Path script = releaseRoot.resolve("scripts").resolve("install.ps1");
        if (!Files.isRegularFile(script)) throw new IOException("설치 스크립트를 찾을 수 없습니다: " + script);
        List<String> args = new ArrayList<>();
        args.add("-LittleLumiRoot"); args.add(options.littleLumiRoot().toString());
        args.add("-InstallDir"); args.add(installDir.toString());
        if (!options.autoStart()) args.add("-NoAutoStart");
        if (options.localFiles()) {
            args.add("-EnableLocalFiles");
            args.add("-LocalFileRoots");
            for (Path root : options.localRoots()) args.add(root.toString());
        }
        if (options.mcp()) {
            args.add("-EnableMcp");
            args.add("-McpConfig"); args.add(options.mcpConfig().toString());
        }
        if (options.codex()) {
            args.add("-EnableCodex");
            args.add("-CodexWorkspace"); args.add(options.codexWorkspace().toString());
            if (options.codexWrite()) args.add("-EnableCodexWrite");
        }
        runPowerShell(script, args, true);
    }

    private void applyConfig(ManagerOptions options) throws Exception {
        if (!installed()) throw new IOException("브리지가 설치되지 않았습니다.");
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("tools.webSearch.enabled", boolString(options.webSearch()));
        values.put("tools.webFetch.enabled", boolString(options.webFetch()));
        values.put("agent.autoSearch.enabled", boolString(options.autoSearch()));
        values.put("agent.autoSearch.currentInfo", boolString(options.autoSearch()));
        values.put("agent.autoSearch.eternalReturnEntities", boolString(options.eternalReturnSearch()));
        values.put("tools.eternalReturn.enabled", boolString(options.eternalReturnSearch()));
        values.put("tools.files.enabled", boolString(options.localFiles()));
        values.put("tools.files.roots", joinPaths(options.localRoots()));
        values.put("tools.mcp.enabled", boolString(options.mcp()));
        values.put("tools.mcp.allowWrite", boolString(options.mcpWrite()));
        values.put("tools.codex.enabled", boolString(options.codex()));
        values.put("tools.codex.workspace", options.codexWorkspace() == null ? "" : slash(options.codexWorkspace()));
        values.put("tools.codex.writeEnabled", boolString(options.codexWrite()));
        values.put("memory.evidence.enabled", boolString(options.evidence()));
        values.put("memory.evidence.recall", boolString(options.evidenceRecall()));
        values.put("logging.verbose", boolString(options.verboseLogging()));

        if (options.mcp() && options.mcpConfig() != null) {
            Path destination = installDir.resolve("config").resolve("mcp.json");
            Files.createDirectories(destination.getParent());
            if (!options.mcpConfig().toAbsolutePath().normalize().equals(destination.toAbsolutePath().normalize())) {
                Files.copy(options.mcpConfig(), destination, StandardCopyOption.REPLACE_EXISTING);
            }
            values.put("tools.mcp.config", "config/mcp.json");
        }
        updatePropertyFile(configPath(), values);
        setLittleLumiRoot(options.littleLumiRoot());
    }

    public boolean running() {
        if (!installed()) return false;
        try {
            Config config = Config.load(configPath());
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + config.port() + "/health"))
                    .timeout(Duration.ofMillis(700)).GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200 && response.body().contains("little-lumi-agent-bridge");
        } catch (Exception ignored) {
            return false;
        }
    }

    public String statusText() {
        if (!installed()) return "설치 안 됨";
        return running() ? "실행 중" : "중지됨";
    }

    public void start() throws Exception {
        requireInstalled();
        runPowerShell(installDir.resolve("scripts").resolve("start-bridge.ps1"), List.of("-InstallDir", installDir.toString()), true);
    }

    public void stop() throws Exception {
        requireInstalled();
        runPowerShell(installDir.resolve("scripts").resolve("stop-bridge.ps1"), List.of("-InstallDir", installDir.toString()), true);
    }

    public void restart() throws Exception {
        requireInstalled();
        try { stop(); } catch (Exception ignored) {}
        start();
    }

    public String doctor() throws Exception {
        requireInstalled();
        return runPowerShell(installDir.resolve("scripts").resolve("doctor.ps1"), List.of("-InstallDir", installDir.toString()), true);
    }

    public void openLogs() throws IOException {
        Path logs = installDir.resolve("logs");
        Files.createDirectories(logs);
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(logs.toFile());
    }

    public void openInstallDir() throws IOException {
        Files.createDirectories(installDir);
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(installDir.toFile());
    }

    public void launchLittleLumi() throws IOException {
        if (littleLumiRoot == null) throw new IOException("Little LUMI 경로가 설정되지 않았습니다.");
        Path exe = littleLumiRoot.resolve("Little LUMI.exe");
        if (!Files.isRegularFile(exe)) throw new IOException("Little LUMI.exe를 찾지 못했습니다: " + exe);
        new ProcessBuilder(exe.toString()).directory(littleLumiRoot.toFile()).start();
    }

    public void uninstallAsync() throws Exception {
        requireInstalled();
        Path script = installDir.resolve("scripts").resolve("uninstall.ps1");
        List<String> command = List.of(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                "-Command", "Start-Sleep -Milliseconds 800; & '" + escapePowerShell(script.toString()) + "' -InstallDir '" + escapePowerShell(installDir.toString()) + "'");
        new ProcessBuilder(command).start();
    }

    public void setStartup(boolean enabled) throws Exception {
        requireInstalled();
        Path script = installDir.resolve("scripts").resolve("set-startup.ps1");
        if (!Files.isRegularFile(script)) {
            if (releaseRoot != null && Files.isRegularFile(releaseRoot.resolve("scripts").resolve("set-startup.ps1"))) {
                Files.copy(releaseRoot.resolve("scripts").resolve("set-startup.ps1"), script, StandardCopyOption.REPLACE_EXISTING);
            } else {
                throw new IOException("set-startup.ps1을 찾지 못했습니다.");
            }
        }
        List<String> args = new ArrayList<>(List.of("-InstallDir", installDir.toString()));
        if (!enabled) args.add("-Disable");
        runPowerShell(script, args, true);
    }

    public String codexStatus() throws Exception {
        Path script = helperScript("codex-auth.ps1");
        return runPowerShell(script, List.of("-Status"), true);
    }

    public String codexLogin() throws Exception {
        Path script = helperScript("codex-auth.ps1");
        return runPowerShell(script, List.of(), true);
    }

    private Path helperScript(String name) throws IOException {
        Path installedScript = installDir.resolve("scripts").resolve(name);
        if (Files.isRegularFile(installedScript)) return installedScript;
        if (releaseRoot != null) {
            Path releaseScript = releaseRoot.resolve("scripts").resolve(name);
            if (Files.isRegularFile(releaseScript)) return releaseScript;
        }
        throw new IOException("도우미 스크립트를 찾지 못했습니다: " + name);
    }

    public boolean startupShortcutExists() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) return false;
        return Files.exists(Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "Little LUMI Agent Bridge.lnk"));
    }

    public Path defaultTemplate() {
        if (releaseRoot == null) return null;
        return releaseRoot.resolve("config").resolve("bridge.properties.example");
    }

    public static void updatePropertyFile(Path path, Map<String, String> updates) throws IOException {
        List<String> lines = Files.isRegularFile(path)
                ? new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8))
                : new ArrayList<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String prefix = entry.getKey() + "=";
            int found = -1;
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).stripLeading();
                if (trimmed.startsWith(prefix)) {
                    if (found >= 0) throw new IOException("중복 설정 키: " + entry.getKey());
                    found = i;
                }
            }
            String line = prefix + entry.getValue();
            if (found >= 0) lines.set(found, line); else lines.add(line);
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = path.resolveSibling(path.getFileName() + ".manager.tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }

    private void validate(ManagerOptions options) throws IOException {
        if (options.littleLumiRoot() == null || !Files.isRegularFile(options.littleLumiRoot().resolve("Little LUMI.exe"))) {
            throw new IOException("Little LUMI 설치 폴더를 선택해 주세요.");
        }
        if (!Files.isRegularFile(options.littleLumiRoot().resolve("app").resolve("conf").resolve("ai.properties"))) {
            throw new IOException("선택한 폴더에 app\\conf\\ai.properties가 없습니다.");
        }
        if (options.localFiles()) {
            if (options.localRoots().isEmpty()) throw new IOException("로컬 파일 기능을 켰다면 읽을 폴더를 하나 이상 추가해 주세요.");
            for (Path root : options.localRoots()) if (!Files.isDirectory(root)) throw new IOException("로컬 파일 폴더가 없습니다: " + root);
        }
        if (options.mcp() && (options.mcpConfig() == null || !Files.isRegularFile(options.mcpConfig()))) {
            throw new IOException("MCP를 켰다면 mcp.json 파일을 선택해 주세요.");
        }
        if (options.codex() && (options.codexWorkspace() == null || !Files.isDirectory(options.codexWorkspace()))) {
            throw new IOException("Codex를 켰다면 작업 폴더를 선택해 주세요.");
        }
        if (options.codexWrite() && !options.codex()) throw new IOException("Codex 파일 수정은 Codex가 켜져 있어야 합니다.");
        if (options.mcpWrite() && !options.mcp()) throw new IOException("MCP 쓰기 허용은 MCP가 켜져 있어야 합니다.");
    }

    private String runPowerShell(Path script, List<String> arguments, boolean wait) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IOException("GUI 설치/제어는 Windows에서만 지원됩니다.");
        }
        if (!Files.isRegularFile(script)) throw new IOException("스크립트를 찾지 못했습니다: " + script);
        List<String> command = new ArrayList<>();
        command.add("powershell.exe"); command.add("-NoProfile"); command.add("-NonInteractive");
        command.add("-WindowStyle"); command.add("Hidden"); command.add("-ExecutionPolicy"); command.add("Bypass");
        command.add("-File"); command.add(script.toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        if (!wait) return "";
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append('\n');
        }
        int exit = process.waitFor();
        if (exit != 0) throw new IOException("PowerShell 작업 실패 (" + exit + ")\n" + output);
        return output.toString();
    }

    private Path littleLumiRootFromState() {
        if (!Files.isRegularFile(statePath())) return null;
        try {
            Map<String, Object> state = Json.object(Json.parse(Files.readString(statePath(), StandardCharsets.UTF_8)));
            String appRoot = String.valueOf(state.getOrDefault("appRoot", ""));
            if (!appRoot.isBlank()) {
                Path path = Path.of(appRoot).toAbsolutePath().normalize();
                if (Files.isDirectory(path)) return path;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.strip());
    }

    private static Path resolveMaybe(String raw, Path source) {
        if (raw == null || raw.isBlank()) return null;
        Path path = Path.of(raw.replace("${user.home}", System.getProperty("user.home", "")));
        if (!path.isAbsolute() && source != null && source.toAbsolutePath().getParent() != null) path = source.toAbsolutePath().getParent().resolve(path);
        return path.normalize().toAbsolutePath();
    }

    private static List<Path> pathList(String raw, Path source) {
        if (raw == null || raw.isBlank()) return List.of();
        ArrayList<Path> paths = new ArrayList<>();
        for (String part : raw.split(";")) {
            Path path = resolveMaybe(part.strip(), source);
            if (path != null) paths.add(path);
        }
        return List.copyOf(paths);
    }

    private static String joinPaths(List<Path> paths) {
        return paths.stream().map(ManagerService::slash).reduce((a, b) -> a + ";" + b).orElse("");
    }

    private static String slash(Path path) { return path.toAbsolutePath().normalize().toString().replace('\\', '/'); }
    private static String boolString(boolean value) { return value ? "true" : "false"; }
    private static String escapePowerShell(String value) { return value.replace("'", "''"); }

    private void requireInstalled() throws IOException {
        if (!installed()) throw new IOException("브리지가 아직 설치되지 않았습니다.");
    }
}
