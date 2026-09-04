package io.github.kwd421.lumitoolbridge.tools;

import io.github.kwd421.lumitoolbridge.Config;
import io.github.kwd421.lumitoolbridge.Json;
import io.github.kwd421.lumitoolbridge.Tool;
import io.github.kwd421.lumitoolbridge.ToolContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Invokes the official Codex CLI; authentication remains owned by that CLI. */
public final class CodexTool implements Tool {
    private final Config config;

    public CodexTool(Config config) { this.config = config; }

    @Override public String name() { return "codex_task"; }

    @Override
    public Map<String, Object> definition() {
        String workspace = config.codexWorkspace() == null ? "not configured" : config.codexWorkspace().toString();
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name(),
                        "description", "Delegate a coding or repository task to the installed OpenAI Codex CLI when local multi-file inspection or implementation is useful. Fixed workspace: " + workspace + ". Write access is only possible when the user explicitly asked to modify code and local write opt-in is enabled.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "task", Map.of("type", "string", "description", "A self-contained coding or repository task."),
                                        "write", Map.of("type", "boolean", "description", "Set true only when the user explicitly asked to modify, create, delete, or refactor files.")),
                                "required", List.of("task"),
                                "additionalProperties", false)));
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolContext context) throws Exception {
        if (!context.request().allowsCodex()) {
            throw new IllegalStateException("Codex requires an explicit coding, repository, or local-project request");
        }
        String task = Json.string(arguments.get("task"));
        if (task == null || task.isBlank()) throw new IllegalArgumentException("task is required");
        if (task.length() > 50000) throw new IllegalArgumentException("task is too long");

        boolean requestedWrite = Json.bool(arguments.get("write"), false);
        if (requestedWrite && !context.request().allowsCodexWrite()) {
            throw new IllegalStateException("Write access requires an explicit user request to modify the project");
        }
        if (requestedWrite && !config.codexWriteEnabled()) {
            throw new IllegalStateException("Codex workspace-write is disabled in bridge.properties");
        }
        String sandbox = requestedWrite ? "workspace-write" : "read-only";

        Path configured = config.codexWorkspace();
        if (configured == null) throw new IllegalStateException("Codex workspace is not configured");
        Path workspace = configured.toRealPath();
        if (!Files.isDirectory(workspace)) throw new IllegalStateException("Codex workspace does not exist: " + workspace);

        Path tempDirectory = Files.createTempDirectory("lumi-codex-");
        Path outputFile = tempDirectory.resolve("last-message.txt");
        try {
            List<String> codexArguments = new ArrayList<>();
            // Approval/sandbox are root CLI options in current Codex releases; place them before `exec`.
            codexArguments.add("--ask-for-approval");
            codexArguments.add("never");
            codexArguments.add("--sandbox");
            codexArguments.add(sandbox);
            codexArguments.add("exec");
            codexArguments.add("--skip-git-repo-check");
            codexArguments.add("--color");
            codexArguments.add("never");
            if (config.codexEphemeral()) codexArguments.add("--ephemeral");
            if (config.codexIgnoreUserConfig()) codexArguments.add("--ignore-user-config");
            if (config.codexIgnoreRules()) codexArguments.add("--ignore-rules");
            if (!config.codexModel().isBlank()) {
                codexArguments.add("--model");
                codexArguments.add(config.codexModel());
            }
            codexArguments.add("--output-last-message");
            codexArguments.add(outputFile.toString());
            codexArguments.add("-C");
            codexArguments.add(workspace.toString());
            codexArguments.add("-");

            String executable = resolveExecutable(config.codexCommand());
            List<String> processCommand = buildProcessCommand(executable, codexArguments);
            ProcessBuilder builder = new ProcessBuilder(processCommand)
                    .directory(workspace.toFile())
                    .redirectErrorStream(true);
            if (config.codexScrubSensitiveEnvironment()) {
                scrubSensitiveEnvironment(builder.environment());
            }
            if (config.codexUseChatgptOAuth()) {
                builder.environment().remove("OPENAI_API_KEY");
                builder.environment().remove("CODEX_API_KEY");
                builder.environment().remove("OPENAI_BASE_URL");
            }

            Process process;
            try {
                process = builder.start();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start Codex CLI. Install it and run codex login first: " + exception.getMessage());
            }

            LimitedCollector collector = new LimitedCollector(process.getInputStream(), config.codexMaxOutputChars());
            Thread collectorThread = new Thread(collector, "lumi-codex-output");
            collectorThread.setDaemon(true);
            collectorThread.start();

            String prompt = "You are a sub-agent called by a desktop companion. Complete only the task below inside the configured workspace. "
                    + "Do not ask interactive questions. Respect the active sandbox. Return a concise, factual result for the parent assistant.\n\nTASK:\n"
                    + task.strip();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            }

            boolean completed = process.waitFor(config.codexTimeout().toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                terminateTree(process);
                collectorThread.join(2000);
                return Map.of(
                        "ok", false,
                        "error", "Codex timed out after " + config.codexTimeout().toSeconds() + " seconds",
                        "log_tail", collector.text());
            }
            collectorThread.join(3000);
            int exitCode = process.exitValue();
            String result = Files.isRegularFile(outputFile)
                    ? Files.readString(outputFile, StandardCharsets.UTF_8).strip()
                    : "";
            if (result.isBlank()) result = collector.text().strip();
            result = truncate(result, config.codexMaxOutputChars());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("ok", exitCode == 0 && !result.isBlank());
            output.put("completed_at", Instant.now().toString());
            output.put("workspace", workspace.toString());
            output.put("sandbox", sandbox);
            output.put("exit_code", exitCode);
            if (!result.isBlank()) output.put("result", result);
            if (exitCode != 0) output.put("error", "Codex exited with code " + exitCode);
            return output;
        } finally {
            try { Files.deleteIfExists(outputFile); } catch (IOException ignored) {}
            try { Files.deleteIfExists(tempDirectory); } catch (IOException ignored) {}
        }
    }

    public static void scrubSensitiveEnvironment(Map<String, String> environment) {
        // Authentication managed by Codex itself remains reachable through CODEX_HOME and the
        // platform keyring. Unrelated credentials from the parent desktop process are not needed.
        List<String> names = new ArrayList<>(environment.keySet());
        for (String name : names) {
            String upper = name.toUpperCase(Locale.ROOT);
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

    /** Resolve npm-style codex.cmd launchers on Windows without invoking a shell for discovery. */
    public static String resolveExecutable(String configured) {
        if (configured == null || configured.isBlank()) throw new IllegalArgumentException("Codex command is empty");
        String command = configured.strip();
        Path direct;
        try { direct = Path.of(command); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid Codex command path"); }
        if ((direct.isAbsolute() || command.contains("/") || command.contains("\\")) && Files.isRegularFile(direct)) {
            return direct.toAbsolutePath().normalize().toString();
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (!windows) return command;
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) return command;
        List<String> suffixes = command.contains(".")
                ? List.of("")
                : List.of(".exe", ".cmd", ".bat", "");
        for (String directory : pathValue.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            String clean = directory.strip();
            if (clean.length() >= 2 && clean.startsWith("\"") && clean.endsWith("\"")) {
                clean = clean.substring(1, clean.length() - 1);
            }
            if (clean.isBlank()) continue;
            for (String suffix : suffixes) {
                try {
                    Path candidate = Path.of(clean).resolve(command + suffix);
                    if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize().toString();
                } catch (RuntimeException ignored) {}
            }
        }
        return command;
    }

    private static List<String> buildProcessCommand(String executable, List<String> arguments) {
        String lower = executable.toLowerCase(Locale.ROOT);
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
            List<String> all = new ArrayList<>();
            all.add(executable);
            all.addAll(arguments);
            StringBuilder commandLine = new StringBuilder();
            for (String value : all) {
                if (containsCmdMeta(value)) {
                    throw new IllegalArgumentException("Codex command or workspace contains unsupported Windows cmd metacharacters");
                }
                if (!commandLine.isEmpty()) commandLine.append(' ');
                commandLine.append('"').append(value.replace("\"", "\"\"")).append('"');
            }
            return List.of("cmd.exe", "/d", "/s", "/c", commandLine.toString());
        }
        List<String> result = new ArrayList<>();
        result.add(executable);
        result.addAll(arguments);
        return result;
    }

    private static boolean containsCmdMeta(String value) {
        for (char ch : value.toCharArray()) {
            if (ch == '\r' || ch == '\n' || ch == '&' || ch == '|' || ch == '<' || ch == '>'
                    || ch == '^' || ch == '%' || ch == '!') return true;
        }
        return false;
    }

    private static void terminateTree(Process process) {
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(child -> { try { child.destroyForcibly(); } catch (Exception ignored) {} });
        try { handle.destroyForcibly(); } catch (Exception ignored) {}
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            try {
                new ProcessBuilder("taskkill", "/PID", Long.toString(process.pid()), "/T", "/F")
                        .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 16)) + "...[truncated]";
    }

    private static final class LimitedCollector implements Runnable {
        private final InputStream input;
        private final int limit;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private LimitedCollector(InputStream input, int limit) { this.input = input; this.limit = limit; }
        @Override public void run() {
            byte[] chunk = new byte[4096];
            try (InputStream stream = input) {
                int read;
                while ((read = stream.read(chunk)) >= 0) {
                    int remaining = limit - buffer.size();
                    if (remaining > 0) buffer.write(chunk, 0, Math.min(read, remaining));
                }
            } catch (IOException ignored) {}
        }
        private String text() { return buffer.toString(StandardCharsets.UTF_8); }
    }
}
