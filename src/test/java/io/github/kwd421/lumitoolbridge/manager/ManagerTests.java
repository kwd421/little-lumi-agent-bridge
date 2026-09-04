package io.github.kwd421.lumitoolbridge.manager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ManagerTests {
    private ManagerTests() {}

    public static void main(String[] args) throws Exception {
        propertyEditorPreservesExistingConfig();
        System.out.println("PASS: 1 manager test");
    }

    private static void propertyEditorPreservesExistingConfig() throws Exception {
        Path directory = Files.createTempDirectory("lumi-manager-config-test");
        Path config = directory.resolve("bridge.properties");
        Files.writeString(config,
                "# keep comment\ntools.webSearch.enabled=true\ntools.codex.enabled=false\n",
                StandardCharsets.UTF_8);
        try {
            ManagerService.updatePropertyFile(config, Map.of(
                    "tools.webSearch.enabled", "false",
                    "tools.codex.enabled", "true",
                    "tools.files.enabled", "true"));
            String text = Files.readString(config, StandardCharsets.UTF_8);
            assertTrue(text.contains("# keep comment"), "GUI settings preserve existing comments");
            assertTrue(text.contains("tools.webSearch.enabled=false"), "GUI can disable web search");
            assertTrue(text.contains("tools.codex.enabled=true"), "GUI can enable Codex");
            assertTrue(text.contains("tools.files.enabled=true"), "GUI can append new settings");
        } finally {
            deleteTree(directory);
        }
    }

    private static void deleteTree(Path path) throws Exception {
        if (!Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            for (Path item : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
