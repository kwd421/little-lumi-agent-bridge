package io.github.kwd421.lumitoolbridge;

import io.github.kwd421.lumitoolbridge.tools.CodexTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        Arguments parsed = Arguments.parse(args);
        if (parsed.help) {
            printHelp();
            return;
        }
        if (parsed.version) {
            System.out.println(Config.VERSION);
            return;
        }

        Config config = Config.load(parsed.config);
        if (parsed.check) {
            printCheck(config);
            return;
        }

        BridgeServer server = new BridgeServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "little-lumi-agent-bridge-shutdown"));
        server.start();
        System.out.println("Little LUMI Agent Bridge " + Config.VERSION + " listening on " + server.address());
        System.out.println("Enabled tools: " + String.join(", ", server.toolNames()));
        new CountDownLatch(1).await();
    }

    private static void printCheck(Config config) {
        System.out.println("Little LUMI Agent Bridge " + Config.VERSION);
        System.out.println("Config: " + config.source());
        System.out.println("Config exists: " + Files.isRegularFile(config.source()));
        System.out.println("Listen: http://" + config.host() + ":" + config.port());
        System.out.println("Upstream chat: " + config.upstreamChatBase());
        System.out.println("Upstream web: " + config.upstreamWebBase());
        try (AgentBridge agent = new AgentBridge(config)) {
            System.out.println("Tools: " + String.join(", ", agent.toolNames()));
        }
        if (config.codexEnabled()) {
            System.out.println("Codex command: " + config.codexCommand());
            System.out.println("Codex resolved command: " + CodexTool.resolveExecutable(config.codexCommand()));
            System.out.println("Codex workspace: " + config.codexWorkspace());
            System.out.println("Codex workspace exists: " + (config.codexWorkspace() != null && Files.isDirectory(config.codexWorkspace())));
            System.out.println("Codex default sandbox: read-only");
            System.out.println("Codex write enabled: " + config.codexWriteEnabled());
        }
        String envName = config.apiKeyEnvironmentVariable();
        boolean hasEnvironmentKey = envName != null && !envName.isBlank()
                && System.getenv(envName) != null && !System.getenv(envName).isBlank();
        System.out.println("Environment API key present: " + hasEnvironmentKey + " (incoming Little LUMI key is used otherwise)");
    }

    private static void printHelp() {
        System.out.println("""
                Little LUMI Agent Bridge

                Usage:
                  java -jar little-lumi-agent-bridge.jar [--config FILE]
                  java -jar little-lumi-agent-bridge.jar --check [--config FILE]
                  java -jar little-lumi-agent-bridge.jar --version

                Default config file: ./bridge.properties
                """);
    }

    private static final class Arguments {
        private Path config = Path.of("bridge.properties");
        private boolean check;
        private boolean help;
        private boolean version;

        private static Arguments parse(String[] args) {
            Arguments result = new Arguments();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--config" -> {
                        if (++i >= args.length) throw new IllegalArgumentException("--config requires a file path");
                        result.config = Path.of(args[i]);
                    }
                    case "--check" -> result.check = true;
                    case "--help", "-h" -> result.help = true;
                    case "--version", "-V" -> result.version = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i] + " in " + Arrays.toString(args));
                }
            }
            return result;
        }
    }
}
