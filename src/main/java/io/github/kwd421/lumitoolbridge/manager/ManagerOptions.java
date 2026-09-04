package io.github.kwd421.lumitoolbridge.manager;

import java.nio.file.Path;
import java.util.List;

public record ManagerOptions(
        Path littleLumiRoot,
        boolean autoStart,
        boolean webSearch,
        boolean webFetch,
        boolean autoSearch,
        boolean eternalReturnSearch,
        boolean localFiles,
        List<Path> localRoots,
        boolean mcp,
        Path mcpConfig,
        boolean mcpWrite,
        boolean codex,
        Path codexWorkspace,
        boolean codexWrite,
        boolean evidence,
        boolean evidenceRecall,
        boolean verboseLogging) {

    public ManagerOptions {
        localRoots = localRoots == null ? List.of() : List.copyOf(localRoots);
    }
}
