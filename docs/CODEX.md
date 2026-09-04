# Codex CLI integration

## Authentication model

The bridge does not implement OpenAI OAuth and does not read Codex credential files. Install the official Codex CLI
and run `codex login` yourself. The bridge launches the authenticated `codex exec` process only when the current user
turn is a coding/repository/local-project task and the parent model chooses that tool.

When `tools.codex.useChatgptOAuth=true`, the child process is launched without inherited `OPENAI_API_KEY`,
`CODEX_API_KEY`, `OPENAI_BASE_URL`, or unrelated secret-like environment variables. `CODEX_HOME` and platform
credential storage remain available so the CLI can use the authentication state it manages.

## Capability boundary

There is no required chat command. Examples that make the Codex tool available automatically:

```text
이 프로젝트 구조 분석해줘
이 저장소에서 테스트 실패 원인 찾아줘
Main.java 오류 흐름 조사해줘
```

The working directory is fixed in `bridge.properties`; a chat message cannot select a different Codex workspace.

## Read-only default

```properties
tools.codex.enabled=true
tools.codex.workspace=C:/Users/me/source/project
tools.codex.writeEnabled=false
```

A delegated task runs approximately as a non-interactive `codex exec` with a read-only sandbox, an ephemeral session,
a fixed working directory, and a temporary last-message output file. The task is passed through standard input rather
than interpolated into a shell command.

## Workspace-write mode

Writing requires both:

1. `tools.codex.writeEnabled=true` in local bridge configuration; and
2. explicit modification intent in the **current user message**, such as `고쳐줘`, `수정해`, `구현해`, `fix`, or `edit`.

A prior write request does not permanently unlock later turns. When both gates are true, the Codex child may use the
`workspace-write` sandbox. The bridge never supplies the dangerous sandbox-bypass option.

## Windows command discovery

A global npm install often exposes `codex.cmd`. The installer records the command path when PowerShell can find it.
Set it explicitly if process lookup fails:

```properties
tools.codex.command=C:/Users/me/AppData/Roaming/npm/codex.cmd
```

## Rules and user configuration

By default the bridge ignores general Codex user configuration while preserving project execution-policy rules.
Authentication still uses the normal Codex authentication state.

```properties
tools.codex.ignoreUserConfig=true
tools.codex.ignoreRules=false
tools.codex.scrubSensitiveEnvironment=true
```

Change these only when you understand the effect. The bridge asks the subprocess to remain non-interactive, stay
inside the configured workspace, respect its sandbox, and return a concise result to the parent Lumi model.
