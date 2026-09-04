# MCP integration

Little LUMI Agent Bridge can act as a small local MCP client. It starts configured stdio servers and exposes their
tools to the same Gemma/Ollama function-calling loop as web search and local file tools.

## Basic configuration

Copy `config/mcp.example.json` and edit it. A Windows filesystem example:

```json
{
  "mcpServers": {
    "filesystem": {
      "enabled": true,
      "command": "cmd",
      "args": [
        "/c",
        "npx",
        "-y",
        "@modelcontextprotocol/server-filesystem",
        "C:/Users/me/Projects"
      ],
      "allowWrite": false
    }
  }
}
```

Then install with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1 `
  -EnableMcp `
  -McpConfig ".\my-mcp.json"
```

Or edit the installed `bridge.properties` manually:

```properties
tools.mcp.enabled=true
tools.mcp.config=config/mcp.json
tools.mcp.allowWrite=false
```

Restart the bridge after changing MCP configuration.

## Automatic use

There is no MCP chat command. The server tool schemas are passed to the model and it chooses them when useful.
For example a filesystem MCP server can answer:

```text
내 프로젝트에서 설정 파일 찾아줘
src 아래에서 LumiClient 쓰는 곳 찾아봐
README 읽고 현재 구조 설명해줘
```

For simple read-only files, the bridge's built-in `local_*` tools are lighter and do not require Node.js. MCP is useful
when you want other ecosystems or richer server-specific capabilities.

## Tool names

To avoid collisions, exposed MCP function names are namespaced and sanitized, approximately:

```text
mcp_<server>_<tool>
```

The original server/tool name is preserved in the tool result metadata.

## Write safety

By default, write-capable MCP tools are filtered out. A server may be configured with `allowWrite: true` (or the global
`tools.mcp.allowWrite=true`) to load them, but execution still requires explicit write intent in the current user turn.
This reduces accidental modification from a model choosing an overly powerful tool.

Do not enable destructive MCP servers you do not trust. Tool output is untrusted input to the model and can contain
prompt-injection text.

## Environment variables

An MCP server entry may include an `env` object. The bridge removes common secret-like variables inherited from its parent process before starting an MCP server; credentials a server actually needs should be supplied explicitly in this `env` object. Values are passed only to that child process. Do not commit a real MCP configuration containing secrets to this repository.

```json
{
  "mcpServers": {
    "example": {
      "command": "example-mcp",
      "args": [],
      "env": {
        "EXAMPLE_TOKEN": "put-your-local-secret-here"
      },
      "allowWrite": false
    }
  }
}
```
