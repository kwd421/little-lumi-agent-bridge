# Security

## Defaults

- The HTTP server binds to `127.0.0.1` only.
- Little LUMI's Ollama bearer token is forwarded in memory and is never written by the bridge.
- Authorization headers and tool arguments are not logged by default.
- Search results, fetched pages, local files, and MCP output are labeled as untrusted context for the model.
- `web_fetch` rejects credentials in URLs, local/private hosts and IP addresses, and nonstandard ports.
- Built-in local file tools are disabled by default, read-only, and confined to configured real-path roots.
- MCP is disabled by default. Write-capable MCP tools are filtered unless explicitly allowed, and execution still requires current-turn write intent. Common inherited secret-like environment variables are scrubbed before an MCP child starts; required credentials must be explicitly configured for that server.
- Codex is disabled by default and only exposed on coding/repository/local-project turns.
- Codex runs read-only by default. Workspace writes require both `tools.codex.writeEnabled=true` and explicit current-turn modification intent.
- Unrelated API keys, tokens, cloud credentials, and SSH-agent pointers are removed from the Codex child environment by default.
- The bridge never extracts, copies, prints, or directly reuses Codex OAuth credentials. It starts the official CLI.

## Local threat model

Any program running as your Windows account can contact a loopback port. Do not configure
`server.allowRemoteClients=true` unless you fully understand the consequences. Setting `OLLAMA_API_KEY` globally means
another local process may be able to read that environment variable; letting Little LUMI forward its existing key avoids
adding another persisted bridge credential.

Local-file and MCP roots should be narrow. Prefer a project directory over an entire home directory. Never point a file
server at credential stores, browser profiles, SSH directories, password managers, or other unrelated sensitive data.

## Prompt injection

Search results, fetched pages, repository files, and MCP outputs can contain malicious instructions. The bridge tells the
model to treat them as data rather than instructions, but prompt injection cannot be eliminated completely. This is one
reason write capabilities are separately gated and disabled by default.

## MCP servers

MCP processes run with your Windows account permissions. Only configure servers you trust. `allowWrite=false` reduces the
set of exposed tools but cannot make a malicious MCP executable itself safe. Review the command, arguments, package source,
and environment variables before enabling a server.

## Codex

The bridge delegates only to the executable configured in `tools.codex.command` and fixes its working directory to
`tools.codex.workspace`. Keep `tools.codex.ignoreUserConfig=true` unless you intentionally want user-level Codex config,
MCP servers, or hooks to apply to delegated runs.

## Reporting

Open a GitHub security advisory or a minimal issue that does not contain API keys, OAuth material, personal chat logs,
or proprietary Little LUMI files. Rotate any credential that has been shared.
