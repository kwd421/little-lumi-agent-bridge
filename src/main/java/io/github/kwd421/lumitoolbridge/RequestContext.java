package io.github.kwd421.lumitoolbridge;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RequestContext {
    public enum Command { NONE, WEB, ETERNAL_RETURN, FETCH, CODEX_READ, CODEX_WRITE, TOOLS }

    private final String userText;
    private final String recentText;
    private final Command command;
    private final String commandArgument;
    private final boolean compaction;
    private final boolean currentInfo;
    private final boolean eternalReturnContext;
    private final boolean shortEntityQuestion;
    private final boolean eternalReturnFactQuestion;
    private final boolean preferEternalReturnSearch;
    private final boolean localFileIntent;
    private final boolean codingIntent;
    private final boolean explicitWriteIntent;

    private RequestContext(String userText, String recentText, Command command, String commandArgument,
                           boolean compaction, boolean currentInfo, boolean eternalReturnContext,
                           boolean shortEntityQuestion, boolean eternalReturnFactQuestion,
                           boolean preferEternalReturnSearch, boolean localFileIntent, boolean codingIntent,
                           boolean explicitWriteIntent) {
        this.userText = userText;
        this.recentText = recentText;
        this.command = command;
        this.commandArgument = commandArgument;
        this.compaction = compaction;
        this.currentInfo = currentInfo;
        this.eternalReturnContext = eternalReturnContext;
        this.shortEntityQuestion = shortEntityQuestion;
        this.eternalReturnFactQuestion = eternalReturnFactQuestion;
        this.preferEternalReturnSearch = preferEternalReturnSearch;
        this.localFileIntent = localFileIntent;
        this.codingIntent = codingIntent;
        this.explicitWriteIntent = explicitWriteIntent;
    }

    public static RequestContext from(Map<String, Object> request, List<Object> messages) {
        String userText = latestUserText(messages).strip();
        ParsedCommand parsed = parseCommand(userText);
        String recent = recentConversation(messages, 8);
        String latest = userText.toLowerCase(Locale.ROOT);
        String domainContext = (recent + "\n" + systemContext(messages, 4000)).toLowerCase(Locale.ROOT);

        boolean hardCurrentFact = containsAny(latest,
                "패치", "업데이트", "출시", "신규", "속보", "가격", "일정", "결과",
                "날씨", "점수", "순위", "버전", "판매", "공개", "대회", "뉴스",
                "바뀌", "변경", "환율", "주가", "금리", "대통령", "총리", "대표", "회장",
                "patch", "update", "release", "price", "schedule", "weather", "score",
                "version", "news", "tournament", "exchange rate", "stock", "president", "ceo");
        boolean relativeTime = containsAny(latest,
                "오늘", "어제", "내일", "최근", "최신", "현재", "지금", "이번주", "이번 주", "방금",
                "today", "yesterday", "tomorrow", "latest", "current", "recent", "now", "this week");
        boolean casualPresenceQuestion = latest.matches("(?s).*(루미|보리|너|넌)?\\s*(지금|오늘)?\\s*(뭐\\s*해|뭐하고|어디야|잘\\s*있|괜찮아).*?");
        boolean current = hardCurrentFact
                || (relativeTime && looksLikeInformationRequest(latest) && !casualPresenceQuestion);
        boolean explicitEr = containsAny(latest,
                "이터널 리턴", "이터널리턴", "루미아 섬", "루미아섬", "실험체", "마스터즈",
                "eternal return", "lumia island") || latest.matches("(?s).*\\bkel\\b.*");
        boolean er = explicitEr || containsAny(domainContext,
                "이터널 리턴", "이터널리턴", "루미아 섬", "루미아섬", "실험체", "마스터즈",
                "eternal return", "lumia island") || domainContext.matches("(?s).*\\bkel\\b.*");
        String compact = userText.replaceAll("\\s+", " ").strip();
        boolean entity = compact.length() >= 2 && compact.length() <= 70
                && (compact.matches(".*(은|는|이|가|도)\\s*(\\?+|뭐.*|누구.*|어때.*|어떻게.*|실험체.*|캐릭터.*)$")
                    || compact.matches("[\\p{L}\\p{N}_ .'-]{1,35}\\?+"));
        boolean erFact = looksLikeInformationRequest(latest) && containsAny(latest,
                "스킬", "패시브", "궁극기", "무기", "아이템", "특성", "실험체", "캐릭터", "스토리",
                "설정", "스킨", "패치", "출시", "상점", "마스터즈", "대회", "kel",
                "skill", "passive", "ultimate", "weapon", "item", "character", "story", "skin");
        boolean genericCompanionOrUtility = containsAny(latest,
                "날씨", "기분", "시간", "몇 시", "루미", "보리", "고마워", "안녕", "피곤",
                "weather", "time", "thanks", "thank you", "hello");
        boolean erLookup = erFact || explicitEr || (entity && er && !genericCompanionOrUtility);
        boolean fileIntent = containsAny(latest,
                "파일", "폴더", "디렉터리", "경로", "로컬", "내 컴퓨터", "내 pc", "바탕화면", "다운로드",
                "프로젝트", "저장소", "레포", "리포", "소스", "코드베이스",
                "file", "folder", "directory", "path", "local file", "workspace", "project", "repository", "repo")
                || latest.matches("(?s).*[a-z]:[\\/].*")
                || latest.matches("(?s).*[^\\s]+\\.(java|kt|py|js|ts|tsx|jsx|rs|go|cpp|c|h|cs|json|toml|yaml|yml|md|txt).*?");
        boolean coding = fileIntent || containsAny(latest,
                "코드", "코딩", "컴파일", "빌드", "테스트", "버그", "오류", "에러", "스택트레이스", "함수", "클래스",
                "자바", "파이썬", "자바스크립트", "타입스크립트", "러스트", "깃", "github", "커밋", "브랜치", "pr",
                "code", "coding", "compile", "build", "test", "bug", "error", "stack trace", "function", "class",
                "java", "python", "javascript", "typescript", "rust", "git", "commit", "branch");
        boolean write = coding && containsAny(latest,
                "수정", "고쳐", "고치", "바꿔", "변경해", "적용해", "구현해", "만들어", "작성해", "저장해", "삭제해",
                "지워", "옮겨", "이동해", "이름 바꿔", "리팩터", "리팩토", "패치해", "추가해", "생성해",
                "fix", "edit", "modify", "change", "apply", "implement", "write", "save", "delete", "remove",
                "move", "rename", "refactor", "create", "add");
        boolean compaction = looksLikeCompaction(request, messages);
        return new RequestContext(userText, recent, parsed.command(), parsed.argument(), compaction, current, er, entity, erFact, erLookup,
                fileIntent, coding, write);
    }

    public String userText() { return userText; }
    public String recentText() { return recentText; }
    public Command command() { return command; }
    public String commandArgument() { return commandArgument; }
    public boolean compaction() { return compaction; }
    public boolean currentInfo() { return currentInfo; }
    public boolean eternalReturnContext() { return eternalReturnContext; }
    public boolean shortEntityQuestion() { return shortEntityQuestion; }
    public boolean eternalReturnFactQuestion() { return eternalReturnFactQuestion; }
    public boolean preferEternalReturnSearch() { return preferEternalReturnSearch; }
    public boolean localFileIntent() { return localFileIntent; }
    public boolean codingIntent() { return codingIntent; }
    public boolean explicitWriteIntent() { return explicitWriteIntent; }
    public boolean allowsCodex() { return codingIntent || command == Command.CODEX_READ || command == Command.CODEX_WRITE; }
    public boolean allowsCodexWrite() { return explicitWriteIntent || command == Command.CODEX_WRITE; }

    private static String latestUserText(List<Object> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object value = messages.get(i);
            if (!(value instanceof Map<?, ?> raw) || !"user".equals(String.valueOf(raw.get("role")))) continue;
            return messageText(raw);
        }
        return "";
    }

    private static String recentConversation(List<Object> messages, int limit) {
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (int i = messages.size() - 1; i >= 0 && count < limit; i--) {
            Object value = messages.get(i);
            if (!(value instanceof Map<?, ?> raw)) continue;
            String role = String.valueOf(raw.get("role"));
            if (!role.equals("user") && !role.equals("assistant")) continue;
            String text = messageText(raw).replaceAll("\\s+", " ").strip();
            if (text.isBlank()) continue;
            if (text.length() > 500) text = text.substring(0, 500);
            result.insert(0, role + ": " + text + "\n");
            count++;
        }
        return result.toString();
    }

    private static String systemContext(List<Object> messages, int maxChars) {
        StringBuilder result = new StringBuilder();
        for (Object value : messages) {
            if (!(value instanceof Map<?, ?> raw) || !"system".equals(String.valueOf(raw.get("role")))) continue;
            String text = messageText(raw);
            if (text.isBlank()) continue;
            int remaining = maxChars - result.length();
            if (remaining <= 0) break;
            result.append(text, 0, Math.min(text.length(), remaining)).append('\n');
        }
        return result.toString();
    }

    private static String messageText(Map<?, ?> message) {
        Object content = message.get("content");
        if (content instanceof String text) return text;
        if (content instanceof List<?> parts) {
            StringBuilder result = new StringBuilder();
            for (Object value : parts) {
                if (!(value instanceof Map<?, ?> part)) continue;
                Object type = part.get("type");
                Object text = part.get("text");
                if (("text".equals(type) || "input_text".equals(type)) && text != null) {
                    if (!result.isEmpty()) result.append('\n');
                    result.append(text);
                }
            }
            return result.toString();
        }
        return "";
    }

    private static boolean looksLikeCompaction(Map<String, Object> request, List<Object> messages) {
        StringBuilder all = new StringBuilder();
        for (Object value : messages) {
            if (value instanceof Map<?, ?> raw) all.append(messageText(raw)).append('\n');
        }
        String text = all.toString().toLowerCase(Locale.ROOT);
        int markers = 0;
        for (String marker : List.of("\"summary\"", "\"keywords\"", "\"user_facts\"", "user_facts", "대화 요약", "장기 기억", "conversation summary")) {
            if (text.contains(marker)) markers++;
        }
        boolean structured = request.containsKey("response_format") || text.contains("json");
        return markers >= 3 && structured && text.length() > 500;
    }

    private static ParsedCommand parseCommand(String text) {
        String stripped = text.strip();
        if (!stripped.startsWith("/")) return new ParsedCommand(Command.NONE, "");
        int split = stripped.indexOf(' ');
        String name = (split < 0 ? stripped : stripped.substring(0, split)).toLowerCase(Locale.ROOT);
        String argument = split < 0 ? "" : stripped.substring(split + 1).strip();
        Command command = switch (name) {
            case "/web", "/search", "/웹", "/검색" -> Command.WEB;
            case "/er", "/이리", "/이터널리턴" -> Command.ETERNAL_RETURN;
            case "/fetch", "/읽기", "/가져오기" -> Command.FETCH;
            case "/codex", "/코덱스" -> Command.CODEX_READ;
            case "/codex-write", "/코덱스-쓰기" -> Command.CODEX_WRITE;
            case "/tools", "/도구" -> Command.TOOLS;
            default -> Command.NONE;
        };
        return new ParsedCommand(command, argument);
    }

    private static boolean looksLikeInformationRequest(String text) {
        return text.contains("?") || containsAny(text,
                "알려", "뭐", "누구", "언제", "어디", "얼마", "어때", "어떻게", "왜",
                "확인", "찾아", "검색", "설명", "바뀌", "변경", "나왔", "있어", "인가", "이야",
                "tell", "what", "who", "when", "where", "how", "why", "find", "search", "check");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private record ParsedCommand(Command command, String argument) {}
}
