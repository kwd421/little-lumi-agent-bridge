package io.github.kwd421.lumitoolbridge.manager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ManagerFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private transient final ManagerService service;

    private final JLabel statusLabel = new JLabel();
    private final JLabel installLabel = new JLabel();
    private final JTextArea logArea = new JTextArea(7, 50);

    private final ToggleSwitch webSearch = new ToggleSwitch();
    private final ToggleSwitch webFetch = new ToggleSwitch();
    private final ToggleSwitch autoSearch = new ToggleSwitch();
    private final ToggleSwitch eternalReturn = new ToggleSwitch();
    private final ToggleSwitch localFiles = new ToggleSwitch();
    private final ToggleSwitch mcp = new ToggleSwitch();
    private final ToggleSwitch mcpWrite = new ToggleSwitch();
    private final ToggleSwitch codex = new ToggleSwitch();
    private final ToggleSwitch codexWrite = new ToggleSwitch();
    private final ToggleSwitch evidence = new ToggleSwitch();
    private final ToggleSwitch evidenceRecall = new ToggleSwitch();
    private final ToggleSwitch verbose = new ToggleSwitch();
    private final ToggleSwitch autoStart = new ToggleSwitch();

    private final JTextField lumiRoot = new JTextField();
    private final JTextField localRoots = new JTextField();
    private final JTextField mcpConfig = new JTextField();
    private final JTextField codexWorkspace = new JTextField();
    private final JLabel codexStatusLabel = new JLabel("상태 확인 안 됨");
    private final JButton codexAuthButton = new JButton("로그인 / 상태 확인");

    private final JButton applyButton = new JButton("설치 / 적용");
    private final JButton startButton = new JButton("브리지 시작");
    private final JButton stopButton = new JButton("브리지 중지");
    private final JButton doctorButton = new JButton("진단");
    private final JButton logsButton = new JButton("로그 폴더");
    private final JButton openInstallButton = new JButton("설치 폴더");
    private final JButton launchLumiButton = new JButton("Little LUMI 실행");
    private final JButton uninstallButton = new JButton("제거");

    public ManagerFrame(ManagerService service) {
        super("Little LUMI Agent Manager");
        this.service = service;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(780, 760));
        setSize(new Dimension(860, 860));
        setLocationRelativeTo(null);
        buildUi();
        wireEvents();
        loadOptions();
        refreshStatus();
        new Timer(2500, event -> refreshStatus()).start();
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Little LUMI Agent Manager");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitle = new JLabel("웹검색 · 이터널 리턴 공식검색 · 로컬파일 · MCP · Codex를 GUI에서 관리합니다.");
        subtitle.setForeground(new Color(95, 95, 100));
        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitle);
        header.add(titleBox, BorderLayout.WEST);

        JPanel state = new JPanel();
        state.setLayout(new BoxLayout(state, BoxLayout.Y_AXIS));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        statusLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        installLabel.setForeground(new Color(100, 100, 105));
        installLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        state.add(statusLabel);
        state.add(Box.createVerticalStrut(3));
        state.add(installLabel);
        header.add(state, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(section("자동 도구", buildFeaturePanel()));
        content.add(Box.createVerticalStrut(10));
        content.add(section("경로와 연결", buildPathPanel()));
        content.add(Box.createVerticalStrut(10));
        content.add(section("동작", buildBehaviorPanel()));
        content.add(Box.createVerticalStrut(10));
        content.add(section("상태 / 로그", buildLogPanel()));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(uninstallButton);
        buttons.add(doctorButton);
        buttons.add(stopButton);
        buttons.add(startButton);
        applyButton.setFont(applyButton.getFont().deriveFont(Font.BOLD));
        buttons.add(applyButton);
        root.add(buttons, BorderLayout.SOUTH);
    }

    private JPanel buildFeaturePanel() {
        JPanel panel = rowsPanel();
        int y = 0;
        addToggleRow(panel, y++, "웹 검색", "필요할 때 Ollama 웹 검색 도구를 사용할 수 있게 합니다.", webSearch);
        addToggleRow(panel, y++, "검색 결과 원문 읽기", "검색 결과의 실제 페이지를 열어 내용까지 확인할 수 있게 합니다.", webFetch);
        addToggleRow(panel, y++, "최신 정보 자동 확인", "오늘, 최신, 최근, 현재 같은 질문은 모델이 놓쳐도 검색을 선행합니다.", autoSearch);
        addToggleRow(panel, y++, "이터널 리턴 공식 정보 우선", "실험체, 스킬, 패치 질문은 공식 사이트를 먼저 확인합니다.", eternalReturn);
        addSeparator(panel, y++);
        addToggleRow(panel, y++, "로컬 파일 읽기", "허용한 폴더 안에서 파일 목록·검색·읽기 도구를 제공합니다. 쓰기는 하지 않습니다.", localFiles);
        addToggleRow(panel, y++, "MCP 도구", "등록한 stdio MCP 서버의 도구를 Gemma가 필요할 때 자동 호출합니다.", mcp);
        addToggleRow(panel, y++, "MCP 쓰기 허용", "위험도가 높습니다. MCP 서버가 쓰기 도구를 제공할 때만 노출합니다.", mcpWrite);
        addToggleRow(panel, y++, "Codex 자동 위임", "코드·프로젝트 분석 요청을 Codex CLI에 자동으로 맡길 수 있게 합니다.", codex);
        addToggleRow(panel, y++, "Codex 파일 수정 허용", "사용자가 명시적으로 고쳐달라고 요청한 경우에만 workspace-write를 허용합니다.", codexWrite);
        return panel;
    }

    private JPanel buildPathPanel() {
        JPanel panel = rowsPanel();
        int y = 0;
        addPathRow(panel, y++, "Little LUMI 설치 폴더", lumiRoot, "찾기", () -> chooseDirectory(lumiRoot));
        addPathRow(panel, y++, "로컬 파일 허용 폴더", localRoots, "폴더 추가", this::appendLocalRoot);
        addPathRow(panel, y++, "MCP 설정 JSON", mcpConfig, "찾기", () -> chooseFile(mcpConfig));
        addPathRow(panel, y++, "Codex 작업 폴더", codexWorkspace, "찾기", () -> chooseDirectory(codexWorkspace));
        addCodexAuthRow(panel, y++);
        return panel;
    }

    private void addCodexAuthRow(JPanel panel, int y) {
        GridBagConstraints c = base(y);
        c.gridx = 0;
        c.weightx = 0;
        JLabel label = new JLabel("Codex 계정");
        label.setPreferredSize(new Dimension(155, 26));
        panel.add(label, c);
        c = base(y);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        codexStatusLabel.setForeground(new Color(95, 95, 100));
        panel.add(codexStatusLabel, c);
        c = base(y);
        c.gridx = 2;
        c.weightx = 0;
        panel.add(codexAuthButton, c);
    }

    private JPanel buildBehaviorPanel() {
        JPanel panel = rowsPanel();
        int y = 0;
        addToggleRow(panel, y++, "Windows 시작 시 브리지 실행", "로그인할 때 브리지를 백그라운드로 자동 시작합니다.", autoStart);
        addToggleRow(panel, y++, "검색 근거 저장", "검증한 웹 검색 결과를 별도 evidence 캐시에 저장합니다.", evidence);
        addToggleRow(panel, y++, "저장한 근거 재사용", "관련 질문에서 evidence 캐시를 다시 참고합니다. 오래된 정보일 수 있습니다.", evidenceRecall);
        addToggleRow(panel, y++, "자세한 로그", "문제 해결용 상세 로그를 활성화합니다. Authorization 헤더는 기록하지 않습니다.", verbose);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(logsButton);
        buttons.add(openInstallButton);
        buttons.add(launchLumiButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel section(String title, JPanel body) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 222, 226)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(label, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private JPanel rowsPanel() {
        return new JPanel(new GridBagLayout());
    }

    private void addToggleRow(JPanel panel, int y, String title, String description, ToggleSwitch toggle) {
        GridBagConstraints c = base(y);
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(title);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        JLabel desc = new JLabel("<html><span style='color:#666666'>" + description + "</span></html>");
        text.add(name);
        text.add(Box.createVerticalStrut(2));
        text.add(desc);
        panel.add(text, c);
        c = base(y);
        c.gridx = 1;
        c.weightx = 0;
        c.anchor = GridBagConstraints.EAST;
        panel.add(toggle, c);
    }

    private void addPathRow(JPanel panel, int y, String title, JTextField field, String buttonText, Runnable action) {
        GridBagConstraints c = base(y);
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        JLabel label = new JLabel(title);
        label.setPreferredSize(new Dimension(155, 26));
        panel.add(label, c);

        c = base(y);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        field.setMinimumSize(new Dimension(100, 28));
        panel.add(field, c);

        JButton button = new JButton(buttonText);
        button.addActionListener(event -> action.run());
        c = base(y);
        c.gridx = 2;
        c.weightx = 0;
        panel.add(button, c);
    }

    private void addSeparator(JPanel panel, int y) {
        GridBagConstraints c = base(y);
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(7, 0, 7, 0);
        panel.add(new JSeparator(), c);
    }

    private GridBagConstraints base(int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = y;
        c.insets = new Insets(6, 4, 6, 4);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private void wireEvents() {
        applyButton.addActionListener(event -> runTask("설정 적용", () -> service.installOrApply(collectOptions()), true));
        startButton.addActionListener(event -> runTask("브리지 시작", service::start, false));
        stopButton.addActionListener(event -> runTask("브리지 중지", service::stop, false));
        doctorButton.addActionListener(event -> runTask("진단", () -> appendLog(service.doctor()), false));
        logsButton.addActionListener(event -> safeUi(service::openLogs));
        openInstallButton.addActionListener(event -> safeUi(service::openInstallDir));
        launchLumiButton.addActionListener(event -> safeUi(service::launchLittleLumi));
        uninstallButton.addActionListener(event -> uninstall());
        codexAuthButton.addActionListener(event -> codexAuth());

        codex.addActionListener(event -> {
            if (!codex.isSelected()) codexWrite.setSelected(false);
            updateDependencies();
        });
        mcp.addActionListener(event -> {
            if (!mcp.isSelected()) mcpWrite.setSelected(false);
            updateDependencies();
        });
        evidence.addActionListener(event -> {
            if (!evidence.isSelected()) evidenceRecall.setSelected(false);
            updateDependencies();
        });
        localFiles.addActionListener(event -> updateDependencies());
        webSearch.addActionListener(event -> {
            if (!webSearch.isSelected()) {
                autoSearch.setSelected(false);
                eternalReturn.setSelected(false);
            }
            updateDependencies();
        });
    }

    private void loadOptions() {
        try {
            ManagerOptions options = service.loadOptions();
            lumiRoot.setText(options.littleLumiRoot() == null ? "" : options.littleLumiRoot().toString());
            autoStart.setSelected(options.autoStart());
            webSearch.setSelected(options.webSearch());
            webFetch.setSelected(options.webFetch());
            autoSearch.setSelected(options.autoSearch());
            eternalReturn.setSelected(options.eternalReturnSearch());
            localFiles.setSelected(options.localFiles());
            localRoots.setText(join(options.localRoots()));
            mcp.setSelected(options.mcp());
            mcpConfig.setText(options.mcpConfig() == null ? "" : options.mcpConfig().toString());
            mcpWrite.setSelected(options.mcpWrite());
            codex.setSelected(options.codex());
            codexWorkspace.setText(options.codexWorkspace() == null ? "" : options.codexWorkspace().toString());
            codexWrite.setSelected(options.codexWrite());
            evidence.setSelected(options.evidence());
            evidenceRecall.setSelected(options.evidenceRecall());
            verbose.setSelected(options.verboseLogging());
            updateDependencies();
            appendLog("GUI 설정을 불러왔습니다. 명령어 없이 토글을 바꾸고 설치 / 적용을 누르면 됩니다.");
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private ManagerOptions collectOptions() {
        Path lumi = pathOrNull(lumiRoot.getText());
        List<Path> roots = splitPaths(localRoots.getText());
        return new ManagerOptions(
                lumi,
                autoStart.isSelected(),
                webSearch.isSelected(),
                webFetch.isSelected(),
                autoSearch.isSelected(),
                eternalReturn.isSelected(),
                localFiles.isSelected(),
                roots,
                mcp.isSelected(),
                pathOrNull(mcpConfig.getText()),
                mcpWrite.isSelected(),
                codex.isSelected(),
                pathOrNull(codexWorkspace.getText()),
                codexWrite.isSelected(),
                evidence.isSelected(),
                evidenceRecall.isSelected(),
                verbose.isSelected());
    }

    private void refreshStatus() {
        boolean installed = service.installed();
        boolean running = service.running();
        statusLabel.setText(installed ? (running ? "● 실행 중" : "● 중지됨") : "● 설치 안 됨");
        statusLabel.setForeground(running ? new Color(36, 145, 78) : installed ? new Color(190, 126, 30) : new Color(120, 120, 125));
        installLabel.setText(installed ? "설치 위치: " + service.installDir() : "설치 후 Little LUMI가 로컬 브리지를 사용합니다.");
        applyButton.setText(installed ? "설정 적용" : "설치 / 적용");
        startButton.setEnabled(installed && !running);
        stopButton.setEnabled(installed && running);
        doctorButton.setEnabled(installed);
        logsButton.setEnabled(installed);
        uninstallButton.setEnabled(installed);
    }

    private void updateDependencies() {
        localRoots.setEnabled(localFiles.isSelected());
        mcpConfig.setEnabled(mcp.isSelected());
        mcpWrite.setEnabled(mcp.isSelected());
        codexWorkspace.setEnabled(codex.isSelected());
        codexWrite.setEnabled(codex.isSelected());
        evidenceRecall.setEnabled(evidence.isSelected());
        autoSearch.setEnabled(webSearch.isSelected());
        eternalReturn.setEnabled(webSearch.isSelected());
    }

    private void runTask(String title, ThrowingRunnable runnable, boolean reload) {
        setBusy(true);
        appendLog(title + " 시작...");
        new SwingWorker<Void, Void>() {
            private Exception error;
            @Override protected Void doInBackground() {
                try { runnable.run(); } catch (Exception exception) { error = exception; }
                return null;
            }
            @Override protected void done() {
                setBusy(false);
                if (error != null) {
                    appendLog(title + " 실패: " + error.getMessage());
                    showError(error);
                } else {
                    appendLog(title + " 완료.");
                    if (reload) loadOptions();
                }
                refreshStatus();
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        applyButton.setEnabled(!busy);
        startButton.setEnabled(!busy);
        stopButton.setEnabled(!busy);
        doctorButton.setEnabled(!busy);
        uninstallButton.setEnabled(!busy);
        setCursor(busy ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : java.awt.Cursor.getDefaultCursor());
    }

    private void codexAuth() {
        setBusy(true);
        codexStatusLabel.setText("확인 중...");
        new SwingWorker<String, Void>() {
            private Exception error;
            @Override protected String doInBackground() {
                try {
                    String status = service.codexStatus();
                    if (status.toLowerCase().contains("not logged in") || status.toLowerCase().contains("로그인")) {
                        return status;
                    }
                    return status;
                } catch (Exception statusError) {
                    try { return service.codexLogin(); } catch (Exception loginError) { error = loginError; return ""; }
                }
            }
            @Override protected void done() {
                setBusy(false);
                if (error != null) {
                    codexStatusLabel.setText("Codex 확인 실패");
                    showError(error);
                    return;
                }
                try {
                    String output = get();
                    appendLog(output);
                    String low = output.toLowerCase();
                    if (low.contains("logged in using chatgpt")) {
                        codexStatusLabel.setText("ChatGPT 로그인됨");
                    } else if (low.contains("not logged in")) {
                        int choice = JOptionPane.showConfirmDialog(ManagerFrame.this,
                                "Codex가 아직 로그인되어 있지 않습니다. 브라우저 로그인을 시작할까요?",
                                "Codex 로그인", JOptionPane.YES_NO_OPTION);
                        if (choice == JOptionPane.YES_OPTION) runCodexLogin();
                        else codexStatusLabel.setText("로그인 필요");
                    } else {
                        codexStatusLabel.setText(output.isBlank() ? "상태 확인 완료" : output.strip().replace('\n', ' '));
                    }
                } catch (Exception exception) { showError(exception); }
            }
        }.execute();
    }

    private void runCodexLogin() {
        setBusy(true);
        codexStatusLabel.setText("브라우저 로그인 진행 중...");
        new SwingWorker<String, Void>() {
            private Exception error;
            @Override protected String doInBackground() {
                try { return service.codexLogin(); } catch (Exception exception) { error = exception; return ""; }
            }
            @Override protected void done() {
                setBusy(false);
                if (error != null) { codexStatusLabel.setText("로그인 실패"); showError(error); return; }
                try {
                    String output = get();
                    appendLog(output);
                    codexStatusLabel.setText("Codex 로그인 완료");
                } catch (Exception exception) { showError(exception); }
            }
        }.execute();
    }

    private void uninstall() {
        int result = JOptionPane.showConfirmDialog(this,
                "브리지를 제거하고 Little LUMI의 원래 Ollama 주소를 복구할까요?",
                "브리지 제거", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) return;
        try {
            service.uninstallAsync();
            dispose();
            System.exit(0);
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void appendLocalRoot() {
        JFileChooser chooser = directoryChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        String current = localRoots.getText().strip();
        String selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString();
        localRoots.setText(current.isEmpty() ? selected : current + ";" + selected);
    }

    private void chooseDirectory(JTextField field) {
        JFileChooser chooser = directoryChooser();
        Path current = pathOrNull(field.getText());
        if (current != null) chooser.setCurrentDirectory(current.toFile());
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
            if (field == lumiRoot) service.setLittleLumiRoot(chooser.getSelectedFile().toPath());
        }
    }

    private void chooseFile(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
        }
    }

    private JFileChooser directoryChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }

    private void safeUi(ThrowingRunnable runnable) {
        try { runnable.run(); } catch (Exception exception) { showError(exception); }
    }

    private void appendLog(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendLog(text));
            return;
        }
        logArea.append(text == null ? "" : text.stripTrailing());
        logArea.append("\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showError(Exception exception) {
        JOptionPane.showMessageDialog(this, exception.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
    }

    private static Path pathOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        return Path.of(text.strip()).toAbsolutePath().normalize();
    }

    private static List<Path> splitPaths(String text) {
        if (text == null || text.isBlank()) return List.of();
        ArrayList<Path> result = new ArrayList<>();
        for (String part : text.split(";")) {
            if (!part.isBlank()) result.add(Path.of(part.strip()).toAbsolutePath().normalize());
        }
        return List.copyOf(result);
    }

    private static String join(List<Path> paths) {
        return paths.stream().map(Path::toString).reduce((a, b) -> a + ";" + b).orElse("");
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
