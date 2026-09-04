# Troubleshooting

## Little LUMI says the API request failed

Run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\doctor.ps1
```

Check that:

- the bridge health endpoint answers at `http://127.0.0.1:11435/health`;
- `llm.provider=ollama` is still selected;
- `llm.base.ollama` points at the local bridge;
- Little LUMI's Ollama key is still configured in its own settings;
- no other program occupies port 11435.

Bridge logs are stored under `%LOCALAPPDATA%\LittleLumiAgentBridge\logs`. Authorization headers are not logged.

## A current Eternal Return question was not searched

Try a direct factual formulation such as `루치아 스킬 뭐야`, `이번 패치 뭐 바뀌었어`, or `오늘 마스터즈 결과 알려줘`.
Check that these settings are enabled:

```properties
agent.autoSearch.enabled=true
agent.autoSearch.eternalReturnEntities=true
tools.eternalReturn.enabled=true
```

The official page may not be indexed immediately. An empty official result is treated as unverified rather than as proof
that a character or patch does not exist. The model can still perform a broader web search or fetch a discovered page.

## Web search returns HTTP 401 or 403

The Ollama credential sent by Little LUMI was missing or not authorized for the cloud web API. Confirm that normal Ollama
Cloud chat works in Little LUMI. You may alternatively define `OLLAMA_API_KEY` for the bridge process, but a user-level
environment variable is available to other programs running as that user.

## Local files are not visible

Confirm:

```properties
tools.files.enabled=true
tools.files.roots=C:/Users/me/Projects
```

The path must already exist when the bridge starts. Restart the bridge after changing roots. Paths outside configured
roots, including symlink escapes, are intentionally rejected.

## MCP tools do not appear

1. Confirm `tools.mcp.enabled=true`.
2. Confirm `tools.mcp.config` points to the copied JSON file.
3. Run the configured MCP command manually to ensure its runtime exists (`node`, `npx`, `python`, etc.).
4. Start the bridge with `logging.verbose=true` temporarily to see MCP initialization errors.
5. Remember that write-capable MCP tools are hidden when `allowWrite=false`.

## Codex cannot start

1. Run `codex --version` in PowerShell.
2. Run `codex login` yourself.
3. Put the full `codex.exe` or `codex.cmd` path in `tools.codex.command` if discovery fails.
4. Confirm `tools.codex.workspace` exists.
5. Ask a natural coding/project question such as `이 프로젝트 빌드 오류 원인 찾아줘`.
6. Keep `tools.codex.writeEnabled=false` until read-only delegation works.

## Port conflict

Re-run installation with another port, for example:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1 -Port 11436
```

The installer updates both the bridge configuration and Little LUMI base URL while preserving the original cloud URL in
its install state.

## Restore immediately

Run `uninstall.ps1`. If the script cannot restore the line, close Little LUMI and replace only `llm.base.ollama` with the
`originalBaseLine` recorded in `%LOCALAPPDATA%\LittleLumiAgentBridge\install-state.json`.
