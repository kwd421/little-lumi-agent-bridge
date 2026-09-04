# Changelog

## 0.3.1 - 2026-09-04

- Fixed GUI launcher argument quoting on Windows paths containing spaces such as `Program Files` and `Little LUMI`.
- Added visible GUI error reporting and manager launch logs instead of failing silently behind the VBS launcher.

## 0.3.0 - 2026-09-04

- Added a Swing GUI Manager as the primary installation and configuration experience.
- Added toggle switches for web search, page fetch, automatic fresh-info search, official Eternal Return lookup, local files, MCP, Codex, evidence memory, and verbose logging.
- Added GUI folder/file pickers for Little LUMI, local roots, MCP config, and Codex workspace.
- Added GUI start/stop/doctor/log/open-folder/uninstall controls.
- Added a console-free VBS launcher that reuses Little LUMI's bundled `javaw.exe`.
- Added an installed desktop shortcut for reopening the Manager.
- Added GUI Codex login/status flow using the installed Codex CLI without reading OAuth credentials directly.
- Added a configuration-preservation test for GUI property editing; total mock/integration tests are now 16.

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
