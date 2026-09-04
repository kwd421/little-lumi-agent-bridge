package io.github.kwd421.lumitoolbridge.tools.local;

import io.github.kwd421.lumitoolbridge.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Resolves user/model supplied paths without allowing access outside configured roots. */
public final class LocalFileAccess {
    private final List<Path> roots;

    public LocalFileAccess(Config config) {
        List<Path> resolved = new ArrayList<>();
        for (Path root : config.localFileRoots()) {
            try {
                if (Files.isDirectory(root)) resolved.add(root.toRealPath());
            } catch (IOException ignored) {
                // Missing roots are ignored. `--check` reports the configured values separately.
            }
        }
        this.roots = List.copyOf(resolved);
    }

    public List<Path> roots() { return roots; }
    public boolean available() { return !roots.isEmpty(); }

    public Path resolveExisting(String raw) throws IOException {
        if (raw == null || raw.isBlank() || ".".equals(raw.strip())) {
            if (roots.size() == 1) return roots.get(0);
            throw new IllegalArgumentException("path is required when more than one local-file root is configured");
        }
        Path supplied = Path.of(raw.strip());
        if (supplied.isAbsolute()) return checkedRealPath(supplied);
        for (Path root : roots) {
            Path candidate = root.resolve(supplied).normalize();
            if (Files.exists(candidate)) return checkedRealPath(candidate);
        }
        throw new IllegalArgumentException("path does not exist inside an allowed root: " + raw);
    }

    private Path checkedRealPath(Path candidate) throws IOException {
        Path real = candidate.toRealPath();
        for (Path root : roots) {
            if (real.equals(root) || real.startsWith(root)) return real;
        }
        throw new SecurityException("path is outside configured local-file roots");
    }
}
