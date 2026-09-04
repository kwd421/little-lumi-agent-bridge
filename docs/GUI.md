# GUI Manager

0.3.0부터 기본 설치와 설정 방법은 GUI Manager입니다. 사용자는 `/web`, `/er`, `/codex` 같은 명령을 외울 필요가 없고, 기능 허용 여부만 GUI에서 정합니다. 실제 대화에서는 Gemma가 질문 문맥을 보고 필요한 도구를 선택합니다.

## 실행

릴리스 ZIP을 풀고 `Little LUMI Agent Manager.vbs`를 더블클릭합니다. 런처는 콘솔 창을 띄우지 않고 Little LUMI에 포함된 `javaw.exe`를 찾아 Manager를 실행합니다.

설치가 끝나면 바탕화면의 `Little LUMI Agent Manager` 바로가기로 다시 열 수 있습니다.

## 주요 토글

- 웹 검색: Ollama Web Search 도구를 모델에 노출합니다.
- 검색 결과 원문 읽기: 검색 결과 페이지를 직접 읽는 도구를 노출합니다.
- 최신 정보 자동 확인: 날짜 민감 질문은 모델이 검색을 놓쳐도 브리지가 검색을 선행합니다.
- 이터널 리턴 공식 정보 우선: 실험체/스킬/아이템/패치 질문은 공식 도메인을 우선 검색합니다.
- 로컬 파일 읽기: 사용자가 허용한 루트 내부에서만 list/search/read를 제공합니다.
- MCP 도구: 설정한 stdio MCP 서버의 tools/list를 읽어 모델 도구로 노출합니다.
- MCP 쓰기 허용: 기본 OFF. 서버가 write-capable 도구를 표시하고 현재 발화에 명시적 수정 의도가 있을 때만 후보가 됩니다.
- Codex 자동 위임: 코드/저장소/프로젝트 요청에서 Codex CLI 도구를 후보로 제공합니다.
- Codex 파일 수정 허용: 기본 OFF. 현재 요청이 명시적 수정 요청일 때만 workspace-write를 사용합니다.
- 검색 근거 저장/재사용: Little LUMI 자체 기억과 별도의 evidence cache입니다.
- 자세한 로그: 문제 해결용 로그를 늘립니다. Authorization 헤더는 기록하지 않습니다.

## 경로 선택

GUI의 찾아보기 버튼으로 다음을 지정할 수 있습니다.

- Little LUMI 설치 폴더
- 로컬 파일 허용 폴더(여러 개 가능)
- MCP JSON 설정
- Codex 작업 폴더

## Codex 로그인

`로그인 / 상태 확인` 버튼은 설치된 Codex CLI의 `codex login status`를 확인합니다. 로그인이 필요하면 GUI에서 브라우저 OAuth 로그인을 시작할 수 있습니다. Bridge 자체가 OAuth 토큰 파일을 읽거나 복사하지는 않습니다.

## 제어 버튼

- 설치 / 적용: 첫 실행에서는 설치, 이후에는 토글과 경로 설정 저장 후 브리지 재시작
- 브리지 시작 / 중지
- 진단: 기존 doctor 검사를 GUI 로그에 출력
- 로그 폴더 / 설치 폴더
- Little LUMI 실행
- 제거: 원래 `llm.base.ollama` 값을 복구한 뒤 Bridge 제거

## 설치 파일 변경 범위

Manager도 기존 Bridge와 같은 외부 프록시 방식을 사용합니다. Little LUMI 쪽에서는 `app/conf/ai.properties`의 `llm.base.ollama` 한 줄만 로컬 주소로 바꾸며, 제거 시 원래 값을 복구합니다.
