# Changelog

## 0.2.0 - 2026-09-04

- Changed the primary UX to autonomous natural-language tool selection; slash commands are no longer required.
- Added high-confidence official Eternal Return preflight for skill/passive/weapon/character questions such as `루치아 스킬 뭐야`.
- Added root-confined, read-only local file list/search/read tools.
- Added a generic local stdio MCP client with tool discovery and function-call adaptation.
- Added conservative MCP write gating: disabled by default and additionally requires explicit current-turn write intent.
- Changed Codex exposure to automatic coding/repository/project intent instead of requiring a command prefix.
- Kept Codex read-only by default; workspace writes require local opt-in plus explicit modification intent.
- Added installer flags for local-file roots and MCP configuration.
- Expanded mock integration tests for local files, MCP, Codex auto delegation, and Eternal Return natural-language search.

## 0.1.0 - 2026-09-04

- Added an external OpenAI-compatible loopback proxy for Little LUMI's Ollama provider.
- Added Ollama Cloud `web_search` and `web_fetch` tools.
- Added official-domain-first Eternal Return search and fresh entity preflight.
- Added optional Codex CLI delegation using the CLI's own authentication state.
- Added compaction detection that disables tools and injects memory-safety rules.
- Added an optional timestamped JSONL evidence cache.
- Added Windows install, launch, doctor, and uninstall scripts.
- Added dependency-free Java 17 build and mock integration tests.
