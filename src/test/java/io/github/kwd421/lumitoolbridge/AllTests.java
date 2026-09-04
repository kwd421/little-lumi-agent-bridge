package io.github.kwd421.lumitoolbridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.kwd421.lumitoolbridge.security.UrlGuard;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class AllTests {
    private static int tests;

    public static void main(String[] args) throws Exception {
        jsonRoundTrip();
        urlGuard();
        requestRouting();
        modelToolLoop();
        eternalReturnPreflight();
        localFiles();
        mcpReadOnly();
        codexDelegation();
        bridgeHealth();
        System.out.println("PASS: " + tests + " tests");
    }

    private static void jsonRoundTrip() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("text", "루미\n\\\"");
        value.put("number", 42);
        value.put("array", java.util.Arrays.asList("a", 2, null));
        Map<String, Object> parsed = Json.object(Json.parse(Json.write(value)));
        assertEquals("루미\n\\\"", parsed.get("text"), "json string");
        assertEquals(42L, parsed.get("number"), "json number");
        tests++;
    }

    private static void urlGuard() {
        assertEquals("https", UrlGuard.requirePublicHttpUrl("https://example.com/a").getScheme(), "public URL");
        assertThrows(() -> UrlGuard.requirePublicHttpUrl("http://127.0.0.1/admin"), "loopback blocked");
        assertThrows(() -> UrlGuard.requirePublicHttpUrl("file:///etc/passwd"), "file scheme blocked");
        assertThrows(() -> UrlGuard.requirePublicHttpUrl("https://example.com:8443/a"), "nonstandard port blocked");
        tests++;
    }

    private static void requestRouting() {
        List<Object> er = List.of(
                Map.of("role", "system", "content", "너는 이터널 리턴 전문 AI 루미다."),
                Map.of("role", "user", "content", "루치아 스킬 뭐야"));
        RequestContext erContext = RequestContext.from(Map.of("messages", er), er);
        assertTrue(erContext.preferEternalReturnSearch(), "ER skill uses official search");

        List<Object> weather = List.of(Map.of("role", "user", "content", "오늘 날씨 어때?"));
        RequestContext weatherContext = RequestContext.from(Map.of("messages", weather), weather);
        assertTrue(weatherContext.currentInfo(), "weather is current");
        assertTrue(!weatherContext.preferEternalReturnSearch(), "weather is not ER");

        List<Object> project = List.of(Map.of("role", "user", "content", "이 프로젝트 오류 원인 찾아줘"));
        RequestContext projectContext = RequestContext.from(Map.of("messages", project), project);
        assertTrue(projectContext.codingIntent(), "project coding intent");
        assertTrue(!projectContext.allowsCodexWrite(), "inspection is read-only");

        List<Object> write = List.of(Map.of("role", "user", "content", "이 프로젝트 버그 고쳐줘"));
        RequestContext writeContext = RequestContext.from(Map.of("messages", write), write);
        assertTrue(writeContext.allowsCodexWrite(), "explicit edit grants write intent");
        tests++;
    }

    private static void modelToolLoop() throws Exception {
        AtomicInteger chatCalls = new AtomicInteger();
        HttpServer mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mock.createContext("/v1/chat/completions", exchange -> {
            Map<String, Object> request = Json.object(Json.parse(read(exchange)));
            if (chatCalls.incrementAndGet() == 1) {
                assertTrue(Json.write(request.get("tools")).contains("web_search"), "web tool injected");
                send(exchange, 200, """
                        {"id":"one","choices":[{"index":0,"message":{"role":"assistant","content":"","tool_calls":[{"id":"call1","type":"function","function":{"name":"web_search","arguments":"{\\"query\\":\\"example current fact\\"}"}}]}}]}
                        """.strip());
            } else {
                assertTrue(Json.write(request.get("messages")).contains("Verified result"), "tool result returned");
                send(exchange, 200, completion("two", "확인 완료."));
            }
        });
        mock.createContext("/api/web_search", exchange -> send(exchange, 200,
                Json.write(Map.of("results", List.of(Map.of(
                        "title", "Verified result", "url", "https://example.com/a", "content", "fresh fact"))))));
        mock.start();
        Path config = Files.createTempFile("lumi-test", ".properties");
        int port = mock.getAddress().getPort();
        Files.writeString(config, """
                upstream.chatBase=http://127.0.0.1:%d/v1
                upstream.webBase=http://127.0.0.1:%d/api
                tools.webFetch.enabled=false
                tools.eternalReturn.enabled=false
                agent.autoSearch.enabled=false
                """.formatted(port, port), StandardCharsets.UTF_8);
        try (AgentBridge bridge = new AgentBridge(Config.load(config))) {
            AgentBridge.Result result = bridge.handle(request(List.of(
                    Map.of("role", "user", "content", "웹으로 확인해줘"))), "Bearer test");
            assertEquals(200, result.statusCode(), "agent status");
            assertEquals(2, chatCalls.get(), "two model rounds");
            tests++;
        } finally {
            mock.stop(0);
            Files.deleteIfExists(config);
        }
    }

    private static void eternalReturnPreflight() throws Exception {
        AtomicInteger searches = new AtomicInteger();
        HttpServer mock = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mock.createContext("/api/web_search", exchange -> {
            searches.incrementAndGet();
            send(exchange, 200, Json.write(Map.of("results", List.of(Map.of(
                    "title", "Official Lucia", "url", "https://playeternalreturn.com/posts/news/1", "content", "루치아 스킬 정보")))));
        });
        mock.createContext("/v1/chat/completions", exchange -> {
            Map<String, Object> body = Json.object(Json.parse(read(exchange)));
            assertTrue(Json.write(body.get("messages")).contains("Official Lucia"), "official evidence preflighted");
            send(exchange, 200, completion("er", "공식 자료 확인 완료."));
        });
        mock.start();
        Path config = Files.createTempFile("lumi-er", ".properties");
        int port = mock.getAddress().getPort();
        Files.writeString(config, """
                upstream.chatBase=http://127.0.0.1:%d/v1
                upstream.webBase=http://127.0.0.1:%d/api
                tools.webFetch.enabled=false
                """.formatted(port, port), StandardCharsets.UTF_8);
        try (AgentBridge bridge = new AgentBridge(Config.load(config))) {
            AgentBridge.Result result = bridge.handle(request(List.of(
                    Map.of("role", "system", "content", "이터널 리턴 전문 AI"),
                    Map.of("role", "user", "content", "루치아 스킬 뭐야"))), "Bearer test");
            assertEquals(200, result.statusCode(), "ER status");
            assertEquals(1, searches.get(), "one official preflight");
            tests++;
        } finally {
            mock.stop(0);
            Files.deleteIfExists(config);
        }
    }

    private static void localFiles() throws Exception {
        Path directory = Files.createTempDirectory("lumi-files");
        Path source = directory.resolve("src/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Main { String lumi = \"hello\"; }\n", StandardCharsets.UTF_8);
        Path config = directory.resolve("bridge.properties");
        Files.writeString(config, """
                tools.webSearch.enabled=false
                tools.webFetch.enabled=false
                tools.eternalReturn.enabled=false
                tools.files.enabled=true
                tools.files.roots=%s
                """.formatted(directory), StandardCharsets.UTF_8);
        try (ToolRegistry registry = new ToolRegistry(Config.load(config),
                new HttpJsonClient(java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1)))) {
            List<Object> messages = List.of(Map.of("role", "user", "content", "내 프로젝트 파일에서 lumi 찾아줘"));
            RequestContext context = RequestContext.from(Map.of("messages", messages), messages);
            assertTrue(Json.write(registry.definitions(context)).contains("local_search"), "local tools exposed");
            assertTrue(registry.execute("local_search", Map.of("query", "lumi"), "", context).contains("Main.java"), "local search");
            assertTrue(registry.execute("local_read", Map.of("path", source.toString()), "", context).contains("hello"), "local read");
            tests++;
        } finally {
            deleteTree(directory);
        }
    }

    private static void mcpReadOnly() throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        Path directory = Files.createTempDirectory("lumi-mcp");
        Path server = directory.resolve("server.py");
        Files.writeString(server, """
                import json, sys
                for line in sys.stdin:
                    m=json.loads(line); i=m.get('id'); method=m.get('method')
                    if i is None: continue
                    if method=='initialize': r={'protocolVersion':'2026-07-28','capabilities':{'tools':{}},'serverInfo':{'name':'fake','version':'1'}}
                    elif method=='tools/list': r={'tools':[{'name':'echo','description':'Echo','inputSchema':{'type':'object','properties':{'text':{'type':'string'}}},'annotations':{'readOnlyHint':True}},{'name':'write_file','inputSchema':{'type':'object'},'annotations':{'readOnlyHint':False}}]}
                    elif method=='tools/call': r={'content':[{'type':'text','text':'echo:'+m['params']['arguments'].get('text','')}], 'isError':False}
                    else: continue
                    print(json.dumps({'jsonrpc':'2.0','id':i,'result':r}), flush=True)
                """, StandardCharsets.UTF_8);
        Path mcp = directory.resolve("mcp.json");
        Files.writeString(mcp, Json.write(Map.of("mcpServers", Map.of("fake", Map.of(
                "command", "python3", "args", List.of(server.toString()), "allowWrite", false)))), StandardCharsets.UTF_8);
        Path config = directory.resolve("bridge.properties");
        Files.writeString(config, """
                tools.webSearch.enabled=false
                tools.webFetch.enabled=false
                tools.eternalReturn.enabled=false
                tools.mcp.enabled=true
                tools.mcp.config=%s
                tools.mcp.timeoutSeconds=5
                """.formatted(mcp), StandardCharsets.UTF_8);
        try (ToolRegistry registry = new ToolRegistry(Config.load(config),
                new HttpJsonClient(java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1)))) {
            List<Object> messages = List.of(Map.of("role", "user", "content", "내 프로젝트 파일 도구로 확인해줘"));
            RequestContext context = RequestContext.from(Map.of("messages", messages), messages);
            String defs = Json.write(registry.definitions(context));
            assertTrue(defs.contains("mcp_fake_echo"), "read MCP tool exposed");
            assertTrue(!defs.contains("write_file"), "write MCP tool hidden by default");
            assertTrue(registry.execute("mcp_fake_echo", Map.of("text", "hello"), "", context).contains("echo:hello"), "MCP call");
            tests++;
        } finally {
            deleteTree(directory);
        }
    }

    private static void codexDelegation() throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        Path directory = Files.createTempDirectory("lumi-codex");
        Path script = directory.resolve("fake-codex.sh");
        Files.writeString(script, """
                #!/bin/sh
                out=""
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--output-last-message" ]; then out="$2"; shift 2; else shift; fi
                done
                cat >/dev/null
                printf 'Codex test result' > "$out"
                """, StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        Path config = directory.resolve("bridge.properties");
        Files.writeString(config, """
                tools.webSearch.enabled=false
                tools.webFetch.enabled=false
                tools.eternalReturn.enabled=false
                tools.codex.enabled=true
                tools.codex.command=%s
                tools.codex.workspace=%s
                tools.codex.timeoutSeconds=30
                tools.codex.useChatgptOAuth=false
                """.formatted(script, directory), StandardCharsets.UTF_8);
        Config loaded = Config.load(config);
        try (ToolRegistry registry = new ToolRegistry(loaded,
                new HttpJsonClient(java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1)))) {
            List<Object> messages = List.of(Map.of("role", "user", "content", "이 프로젝트 코드 구조 분석해줘"));
            RequestContext context = RequestContext.from(Map.of("messages", messages), messages);
            assertTrue(Json.write(registry.definitions(context)).contains("codex_task"), "Codex exposed for coding");
            String result = registry.execute("codex_task", Map.of("task", "inspect workspace", "write", false), "", context);
            assertTrue(result.contains("Codex test result"), "Codex result returned");
            tests++;
        } finally {
            deleteTree(directory);
        }
    }

    private static void bridgeHealth() throws Exception {
        int port = freePort();
        Path config = Files.createTempFile("lumi-health", ".properties");
        Files.writeString(config, """
                server.port=%d
                tools.webSearch.enabled=false
                tools.webFetch.enabled=false
                tools.eternalReturn.enabled=false
                """.formatted(port), StandardCharsets.UTF_8);
        BridgeServer server = new BridgeServer(Config.load(config));
        try {
            server.start();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "health status");
            assertTrue(response.body().contains("little-lumi-agent-bridge"), "health body");
            tests++;
        } finally {
            server.close();
            Files.deleteIfExists(config);
        }
    }

    private static String request(List<Object> messages) {
        return Json.write(Map.of("model", "gemma4:31b-cloud", "stream", false, "messages", messages));
    }

    private static String completion(String id, String content) {
        return Json.write(Map.of(
                "id", id,
                "object", "chat.completion",
                "choices", List.of(Map.of(
                        "index", 0,
                        "message", Map.of("role", "assistant", "content", content),
                        "finish_reason", "stop"))));
    }

    private static int freePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var stream = Files.walk(directory)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        }
    }

    private static void assertThrows(ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
            throw new AssertionError(message + ": expected exception");
        } catch (IllegalArgumentException expected) {
            // expected
        } catch (Exception exception) {
            throw new AssertionError(message + ": wrong exception", exception);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
