# Little LUMI Agent Bridge

Little LUMI의 Ollama Cloud 채팅 앞에 두는 비공식 로컬 에이전트 브리지입니다. 기존 캐릭터,
말풍선, TTS, 친밀도, 페르소나, 대화 기록은 그대로 두고 **웹 검색, 로컬 파일, MCP, Codex를
자연어 대화에서 자동으로 선택해서 쓰는 기능**을 추가합니다.

사용자가 도구 이름이나 `/` 명령을 외울 필요는 없습니다.

```text
루치아 스킬 뭐야?
→ 이터널 리턴 공식 사이트 검색 → 필요하면 원문 읽기 → 루미 말투로 답변

오늘 패치 뭐 바뀌었어?
→ 최신 웹 검색 → 공식 패치 노트 확인 → 답변

내 프로젝트에서 로그인 오류 원인 찾아줘
→ 허용된 로컬 폴더 검색/파일 읽기 → 답변

이 프로젝트 테스트 실패 고쳐줘
→ Codex가 활성화되어 있으면 작업 위임 → 결과를 루미가 설명
```

원본 `Shimeji-ee.jar`나 Little LUMI의 이미지/음성/페르소나는 교체하지 않습니다. Little LUMI가
보내는 `POST /v1/chat/completions`를 `127.0.0.1`에서 받아 도구 호출을 중계한 뒤 Ollama Cloud로
보냅니다.

> 이 저장소에는 Little LUMI 실행 파일, 이미지, 음성, 페르소나, API 키가 포함되지 않습니다.
> STUDIO LUMI 및 이터널 리턴 관계사와 무관한 커뮤니티 프로젝트입니다.

## 주요 기능

- Gemma/Ollama의 OpenAI 호환 function calling을 이용한 자동 agent loop
- 최신 정보 자동 웹 검색 및 웹페이지 원문 확인
- 이터널 리턴 실험체/스킬/아이템/패치/대회 질문의 공식 사이트 우선 검색
- 허용한 폴더만 대상으로 하는 내장 읽기 전용 `local_list`, `local_search`, `local_read`
- 표준 입출력(stdio) MCP 서버의 도구 자동 로딩
- 선택적 Codex CLI 위임 및 Codex가 관리하는 ChatGPT 로그인 사용
- Little LUMI 메모리 컴팩션 요청에서는 외부 도구를 제거하고 환각이 사용자 사실로 굳는 것을 완화
- 원본 앱 파일은 `llm.base.ollama` 한 줄 외에는 수정하지 않는 외부 브리지 방식

## 요구 사항

- Windows용 Little LUMI
- Little LUMI 설정의 LLM 제공자가 `Ollama`이며 Ollama Cloud로 정상 대화 가능
- 실행은 Little LUMI에 포함된 Java 런타임을 재사용하므로 별도 Java 설치는 보통 불필요
- 소스 빌드 시 JDK 17 이상
- MCP 서버에 따라 Node.js/Python 등 해당 서버의 런타임
- Codex 기능 사용 시 공식 Codex CLI. 로그인/상태 확인은 GUI에서도 시작 가능

## 기본 설치: GUI Manager

0.3.0부터는 터미널 명령이 기본 설치 방법이 아닙니다.

1. 릴리스 ZIP을 풉니다.
2. `Little LUMI Agent Manager.vbs`를 더블클릭합니다.
3. 원하는 기능을 토글로 켜고 경로는 `찾기` 버튼으로 지정합니다.
4. `설치 / 적용`을 누릅니다.

설치 후에는 바탕화면의 `Little LUMI Agent Manager` 바로가기로 다시 열 수 있습니다. 웹검색, 이터널 리턴 공식검색, 로컬파일, MCP, Codex, 자동 시작, 근거 캐시 등을 전부 GUI에서 바꿀 수 있습니다.

자세한 설명은 [GUI Manager](docs/GUI.md)를 참고하세요.

### 고급/자동 설치용 PowerShell

스크립트 방식도 자동화나 개발 용도로 계속 지원합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1
```

설치기는 다음을 수행합니다.

1. `%LOCALAPPDATA%\LittleLumiAgentBridge`에 브리지 JAR과 설정을 복사합니다.
2. Little LUMI의 기존 `llm.base.ollama` 줄을 복구용 상태 파일에 저장합니다.
3. 브리지 상태 확인이 성공한 뒤 그 주소만 `http://127.0.0.1:11435/v1`로 바꿉니다.
4. 자동 시작 및 Little LUMI와 함께 실행하는 바탕화면 바로가기를 만듭니다.

Ollama API 키 값은 복사하거나 로그에 출력하지 않습니다. Little LUMI가 기존처럼 보내는
Authorization 헤더를 메모리에서 Ollama Cloud 요청에 전달합니다.

Steam 경로 자동 탐색이 실패하면:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1 `
  -LittleLumiRoot "D:\SteamLibrary\steamapps\common\Little LUMI"
```

## 로컬 파일 자동 읽기

가장 간단한 로컬 파일 기능은 MCP 없이 내장 도구만 켜면 됩니다. **지정한 루트 밖은 읽지 못하고,
쓰기/삭제 기능은 아예 없습니다.**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1 `
  -EnableLocalFiles `
  -LocalFileRoots "C:\Users\me\Projects"
```

그 뒤 평범하게 말하면 됩니다.

```text
내 Projects 폴더에서 LumiBridge 설정 읽어봐
이 프로젝트에서 web_search 호출하는 코드 어디야?
README랑 설정 파일 비교해줘
```

여러 루트는 설치 후 `bridge.properties`에서 세미콜론으로 추가할 수 있습니다.

```properties
tools.files.enabled=true
tools.files.roots=C:/Users/me/Projects;D:/work
```

## MCP 연결

브리지는 로컬 stdio MCP 서버의 `tools/list`를 읽어 OpenAI function tool로 변환합니다.
일반 대화에서 Gemma가 필요한 MCP 도구를 스스로 선택합니다.

`config/mcp.example.json`을 복사해 경로를 수정합니다. 예시는 공식 filesystem MCP 서버입니다.

```json
{
  "mcpServers": {
    "filesystem": {
      "enabled": true,
      "command": "cmd",
      "args": ["/c", "npx", "-y", "@modelcontextprotocol/server-filesystem", "C:/Users/me/Projects"],
      "allowWrite": false
    }
  }
}
```

설치 시:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1 `
  -EnableMcp `
  -McpConfig ".\my-mcp.json"
```

기본적으로 읽기 전용으로 표시된 MCP 도구만 노출합니다. 쓰기 도구를 허용하려면 별도 설정이
필요하고, 그래도 현재 사용자 발화에 `고쳐줘`, `수정해`, `만들어`처럼 명시적인 변경 의도가
있어야 실행됩니다. 자세한 내용은 [docs/MCP.md](docs/MCP.md)를 참고하세요.

## Codex CLI 자동 위임

브리지는 OpenAI OAuth 토큰을 직접 읽지 않습니다. GUI의 `로그인 / 상태 확인` 버튼은 설치된 Codex CLI의 로그인 상태를 확인하고, 필요하면 브라우저 OAuth 흐름을 시작합니다. 브리지는 로그인된 `codex exec` 프로세스만 호출합니다.

GUI에서는 `Codex 자동 위임` 토글을 켜고 작업 폴더를 지정하면 됩니다. PowerShell 자동 설치를 쓸 경우 읽기 전용으로 활성화:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1 `
  -EnableCodex `
  -CodexWorkspace "C:\Users\me\source\my-project"
```

이후 명령어 없이 말합니다.

```text
이 프로젝트 구조 분석해줘
빌드 오류가 어디서 나는지 찾아줘
이 저장소 테스트가 왜 깨지는지 봐줘
```

파일 수정까지 허용하려면 설치 시 로컬 권한을 별도로 켭니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1 `
  -EnableCodex `
  -EnableCodexWrite `
  -CodexWorkspace "C:\Users\me\source\my-project"
```

그 상태에서도 단순히 `코드 봐줘`라고 하면 읽기 전용입니다. 현재 발화에서 `버그 고쳐줘`,
`테스트 수정해줘`처럼 변경을 명시했을 때만 `workspace-write`를 허용합니다. 자세한 내용은
[docs/CODEX.md](docs/CODEX.md)를 참고하세요.

## 자동 웹 검색 방식

모델에는 웹 검색/가져오기 도구가 항상 제공됩니다. 추가로 다음과 같은 질문은 모델이 도구 호출을
놓치더라도 브리지가 검색을 먼저 수행해 결과를 대화에 넣습니다.

```text
루치아 스킬 뭐야
루치아는 실험체야?
이번 패치에서 뭐 바뀌었어?
오늘 마스터즈 결과 알려줘
```

이터널 리턴 질문은 `playeternalreturn.com`, `eternalreturn.com` 공식 도메인을 우선합니다.
검색 결과가 없다고 해서 존재하지 않는다고 단정하지 말라는 규칙도 system 메시지에 추가합니다.

자동 사전검색만 끄고 모델의 자율 tool calling은 유지하려면:

```properties
agent.autoSearch.enabled=false
```

## 메모리와 컴팩션

브리지는 Little LUMI의 `chat_history.jsonl`, `episodes.jsonl`, `user_facts.txt`를 대체하지 않습니다.
관찰한 빌드에서는 활성 메시지가 일정량 쌓이면 오래된 대화를 별도의 LLM 요청으로 압축합니다.
브리지는 이 요청을 감지하면 웹/파일/MCP/Codex 도구를 제거하고, 어시스턴트가 말한 게임 정보나
추측을 `user_facts`로 저장하지 말라는 규칙을 추가합니다.

자세한 내용은 [docs/MEMORY.md](docs/MEMORY.md)를 참고하세요.

## 점검 / 실행 / 제거

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\doctor.ps1
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-bridge.ps1
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\uninstall.ps1
```

제거 시 저장해 둔 원래 `llm.base.ollama` 줄을 복원합니다. 다른 AI 설정과 API 키는 수정하지 않습니다.

## 직접 빌드

의존성이나 Gradle/Maven 없이 JDK만 사용합니다.

```bash
./scripts/test.sh
./scripts/build.sh
```

```powershell
.\scripts\test.ps1
.\scripts\build.ps1
```

결과물은 `dist/little-lumi-agent-bridge-0.3.0.jar`입니다.

## 보안 경계

- HTTP 서버는 기본적으로 loopback(`127.0.0.1`)만 허용합니다.
- 로컬 내장 파일 도구는 설정한 root의 실경로 밖으로 나갈 수 없고 읽기 전용입니다.
- 웹 페이지/검색 결과/파일/MCP 출력 안의 명령은 신뢰하지 말라는 규칙을 모델에 주입합니다.
- MCP write 도구는 기본 비활성화입니다.
- Codex는 기본 `read-only`; 쓰기는 로컬 설정 + 현재 발화의 명시적인 수정 요청이 둘 다 필요합니다.
- 브리지는 Codex OAuth 자격증명을 추출하지 않습니다.
- Little LUMI에서 받은 Authorization 헤더는 로그에 기록하지 않습니다.

자세한 내용은 [SECURITY.md](SECURITY.md)를 참고하세요.

## 0.3.0 상태

Java 17 빌드 및 16개 모의/통합 테스트에서 웹 tool loop, 이터널 리턴 자동 공식 검색, 컴팩션 보호,
로컬 파일 root 탈출 차단, stdio MCP 읽기 도구, Codex 자동 위임을 검증합니다. 다만 이 개발 환경은
Windows가 아니므로 실제 Little LUMI 설치 스크립트의 최종 Windows 실행 검증은 사용자 환경에서
`doctor.ps1`로 확인해야 합니다.

## 관련 문서

- [GUI Manager](docs/GUI.md)
- [Architecture](docs/ARCHITECTURE.md)
- [MCP](docs/MCP.md)
- [Codex](docs/CODEX.md)
- [Memory](docs/MEMORY.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)

## 라이선스

브리지 자체는 MIT 라이선스입니다. 제품명과 원본 자산에 관한 고지는 [NOTICE.md](NOTICE.md)를
참고하세요.
