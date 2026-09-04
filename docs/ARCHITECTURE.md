# Architecture

## Request path

```text
Little LUMI
  POST http://127.0.0.1:11435/v1/chat/completions
        │
        ▼
Little LUMI Agent Bridge
  ├─ classify freshness / Eternal Return facts / file & coding intent / compaction
  ├─ optionally preflight high-confidence fresh or Eternal Return questions
  ├─ expose relevant OpenAI-compatible function definitions
  │    ├─ web_search / web_fetch / eternal_return_search
  │    ├─ local_list / local_search / local_read
  │    ├─ stdio MCP tools
  │    └─ codex_task for coding/project turns
  ├─ execute bounded model-selected tool calls
  ├─ append role=tool results
  └─ return normal non-streaming chat.completion
        │
        ▼
Ollama Cloud
  POST https://ollama.com/v1/chat/completions
```

The bridge does not patch `Shimeji-ee.jar`. It changes only Little LUMI's Ollama base URL and
uses the same OpenAI-compatible request shape already emitted by the application.

## Autonomous agent loop

1. Parse the request and locate the latest user message.
2. Detect Little LUMI memory compaction. If detected, remove tools and forward one guarded request.
3. Classify the latest turn for freshness, Eternal Return facts, local-file intent, coding intent, and explicit write intent.
4. Add tool-use instructions and only the capabilities relevant to that request.
5. For high-confidence current facts or Eternal Return facts, optionally execute a search preflight so a small model cannot silently skip verification.
6. Send the augmented request to Ollama Cloud.
7. If the model returns `tool_calls`, execute them locally, append `role=tool` results, and call the model again.
8. Stop at configured round/call limits and force one final tool-free synthesis if necessary.

Duplicate calls with identical tool name and arguments are blocked within one request.

## Why preflight exists

Function-calling models occasionally answer from weights instead of calling a tool. That is especially undesirable for
new Eternal Return characters such as a recently released experiment subject. The bridge therefore treats questions
like `루치아 스킬 뭐야` or a current patch question as high-confidence verification cases and inserts official search
evidence before the model composes the final Lumi response.

This is complementary to autonomous tool calling: the model can still call `web_fetch` or perform a follow-up search.

## Local files

The built-in file tools are deliberately read-only. `LocalFileAccess` resolves an existing path to its real path and
checks it against configured real root paths. This blocks simple `..` traversal and symlink escapes outside the roots.

Local file definitions are only shown when the latest turn looks like a file/project/coding request.

## MCP

`McpManager` starts configured stdio MCP processes, performs `initialize`, reads `tools/list`, and adapts each accepted
tool into an OpenAI function definition. Read-only annotations are respected when present; conservative name heuristics
are used as a fallback.

Write-capable MCP tools are not loaded unless allowed in local configuration. Even when loaded, execution additionally
requires explicit write intent in the current user message.

## Codex

`codex_task` is only visible on turns classified as coding/repository/local-project work. The bridge launches the
installed Codex CLI as a child process; it does not read OAuth files or tokens. Read-only is the default sandbox. A
workspace-write invocation requires both local opt-in and explicit current-turn modification intent.

## Components

- `BridgeServer`: loopback HTTP endpoint and request-size limits.
- `AgentBridge`: classification, prompt injection, search preflight, tool loop, and completion passthrough.
- `RequestContext`: per-turn intent and compaction classification.
- `ToolRegistry`: capability exposure and per-call authorization.
- `WebSearchTool`: Ollama Cloud Web Search, including Eternal Return official-domain filtering.
- `WebFetchTool`: Ollama Cloud Web Fetch with public-URL validation.
- `Local*Tool`: root-confined read-only local files.
- `McpManager`: generic local stdio MCP client and tool adapter.
- `CodexTool`: subprocess delegation to the official Codex CLI.
- `EvidenceStore`: optional timestamped JSONL cache for retrieved evidence.
- `Json`: minimal dependency-free JSON parser/writer.

## Compatibility boundary

Version 0.2.0 targets:

- Little LUMI's non-streaming OpenAI-compatible Ollama request
- `POST /v1/chat/completions`
- Ollama Cloud Web Search/Web Fetch
- local stdio MCP tool servers
- Java 17 or newer

No original Little LUMI classes or assets are packaged in the bridge.
