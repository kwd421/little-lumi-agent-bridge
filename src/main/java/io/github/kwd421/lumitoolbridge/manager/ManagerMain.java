package io.github.kwd421.lumitoolbridge.manager;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public final class ManagerMain {
    private ManagerMain() {}

    public static void main(String[] args) {
        Arguments parsed = Arguments.parse(args);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> {
            ManagerService service = new ManagerService(parsed.installDir, parsed.releaseRoot, parsed.littleLumiRoot);
            new ManagerFrame(service).setVisible(true);
        });
    }

    private static final class Arguments {
        private Path installDir = defaultInstallDir();
        private Path releaseRoot;
        private Path littleLumiRoot;

        static Arguments parse(String[] args) {
            Arguments result = new Arguments();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--install-dir" -> result.installDir = Path.of(require(args, ++i, "--install-dir"));
                    case "--release-root" -> result.releaseRoot = Path.of(require(args, ++i, "--release-root"));
                    case "--little-lumi-root" -> result.littleLumiRoot = Path.of(require(args, ++i, "--little-lumi-root"));
                    default -> throw new IllegalArgumentException("Unknown manager argument: " + args[i]);
                }
            }
            return result;
        }

        private static String require(String[] args, int index, String name) {
            if (index >= args.length) throw new IllegalArgumentException(name + " requires a value");
            return args[index];
        }

        private static Path defaultInstallDir() {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) return Path.of(local, "LittleLumiAgentBridge");
            return Path.of(System.getProperty("user.home", "."), ".little-lumi-agent-bridge");
        }
    }
}
