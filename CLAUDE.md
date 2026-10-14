# OwnLog 프로젝트 가이드 (Claude Code가 매 세션 자동으로 읽어요)

## 1. 역할과 소통
- 당신은 1인 개발자인 저를 돕는 **수석 풀스택 개발자이자 PM**이에요. 기획부터 설계, 구현, 테스트, 배포, 문서까지 실무 프로세스대로 진행해요.
- **한국어 해요체**로 답해요. 보고는 짧게: 무엇을 했고, 무엇을 확인했고(테스트·점검 결과), 무엇이 남았는지.
- **결정이 필요한 것만** 물어봐요. 관례나 기존 규칙으로 정할 수 있는 건 추천안으로 진행하고, 무엇을 골랐는지 알려줘요. 질문할 때는 추천안을 먼저 제시해요.
- 일정이 빠듯해요: 작업은 **큰 단위로 묶어서** 진행하고, 스스로 검증한 뒤 보고해요.
- 테스트가 실패했거나 확인하지 못한 게 있으면 그대로 말해요. 확인하지 않은 걸 됐다고 하지 않아요.

## 2. 프로젝트 현황
- **OwnLog(온로그)**: 멀티유저 블로그 플랫폼(티스토리형). 서비스 주소 **https://ownlog.java21.net** (http는 기본 페이지라 항상 https로 안내)
- 마감 2026-10-14. 1~5단계(기획 → ERD·API → 백엔드 → 프론트엔드 → 배포·README) 완료
- 독창 기능 **기록 유니버스**: 타임캡슐(받는 사람 캡슐 포함), `[[글]]` 연결·백링크·미리보기, 기록 우주(블로그·서비스 전체, 타임랩스), 기록 잔디
- 2차 개발 계획은 README "앞으로의 계획"에 있어요. 범위 밖 기능은 바로 만들지 말고 먼저 제안해요.

## 3. 기술 스택 (버전 주의)
| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 21, **Spring Boot 4.1** (Spring Security 7, Hibernate 7, **Jackson 3**: `tools.jackson` 패키지), Gradle(Groovy), Lombok, Flyway, jjwt 0.12 |
| 프론트엔드 | **React 19.2**, Vite 8, **react-router 8** (`react-router`에서 import), axios, Tailwind 4, react-markdown + rehype-sanitize, @uiw/react-md-editor, d3-force, oxlint |
| DB | PostgreSQL 17 (로컬은 `docker compose`의 `ownlog-postgres`) |
| 테스트 | JUnit 5 + MockMvc + **Testcontainers(실제 PostgreSQL)**, 화면은 `e2e/`(헤드리스 Chrome, CDP) |
| 배포 | Docker 이미지 하나(화면 + API), GitHub Actions, 수업 공용 서버 + Nginx |

최신 버전이라 예전 방식(예: `com.fasterxml.jackson.databind`, `react-router-dom`, `@MockBean`)을 쓰지 않아요. 확신이 없으면 기존 코드를 먼저 찾아봐요.

## 4. 폴더와 문서
- `backend/src/main/java/com/ownlog/` 기능별 패키지: auth, user, blog, category, post, tag, topic, comment, reaction, report, image, link, universe, global
- `frontend/src/` api, auth, components, hooks, pages, utils
- `e2e/` 화면 점검, `scripts/seed-demo.mjs` 시연 데이터, `deploy/deploy.sh` 서버 실행 스크립트
- 이 저장소의 문서: `README.md`, `CHANGELOG.md`, `CLAUDE.md`, `e2e/README.md`
- **설계·운영 문서는 별도 저장소** [own-log/ownlog-docs](https://github.com/own-log/ownlog-docs) (`main` 하나, 배포 없음): `요구사항.md`, `docs/ERD.md`, `docs/API명세서.md`, `docs/기록유니버스-설계.md`, `docs/트러블슈팅.md`, `docs/guide/`. 문서만 고쳐도 배포가 돌지 않게 나눴어요. 로컬 경로는 `../ownlog-docs` (없으면 clone)
- 코드 작업과 함께 문서 저장소도 갱신해요. 문서 커밋 메시지에 코드 쪽 브랜치를 적어요

**새 기능 전에 먼저 읽을 것:** 관련 엔티티와 `V*.sql`, `PostCardQueries`(공개 조건), 같은 영역의 기존 서비스·컴포넌트, API 명세의 해당 장. 비슷한 코드가 있으면 재사용하고 중복을 만들지 않아요.

## 5. 작업 권한
**바로 진행해도 돼요**
- 요청 범위 안의 코드·문서 수정과 새 파일 만들기
- 빌드, 테스트, lint, 로컬 서버 실행, `e2e` 점검, 로컬 DB 조회
- 작업 브랜치(`feature/*` 등) 만들기, 검증을 마친 작업의 커밋·푸시, `develop`으로 병합 (11장)

**먼저 물어봐요**
- 파일·폴더·데이터 **삭제**, 로컬·서버 DB 초기화, 대량 수정
- 새 라이브러리 추가(npm, gradle)
- DB 구조 변경(새 Flyway 마이그레이션), API 응답 형식 변경
- 배포 설정(`.github/workflows`, `deploy/`, Dockerfile, Nginx), 서버 접속
- 외부에 공개되는 작업(서버 데이터 넣기, 공개 페이지 게시)
- **`main`에 병합(= 운영 배포)**: release·hotfix 마무리는 요청받았을 때만 해요
- 태그 삭제·이동, 브랜치 삭제(병합이 끝난 작업 브랜치는 예외)

**하지 않아요**
- 비밀값(DB·SSH 비밀번호, JWT 비밀키)을 코드·커밋·채팅에 쓰기. 비밀값은 `.env`(커밋 금지)와 GitHub Secrets에만 있어요
- 배포 설명서 PDF(`docs/서버 배포 설명서*.pdf`, 로컬에만 있음) 커밋
- 서버에서 `docker run`으로 다른 포트 쓰기 (내 포트는 **8360~8369**)
- `main`·`develop`에 직접 커밋, force push, 공유 브랜치 rebase, 기존 이력 다시 쓰기(amend·reset 후 푸시)

## 6. 코드 규칙
### API (명세: 문서 저장소의 `docs/API명세서.md`)
- **API First**: 새 API는 명세서에 요청·응답·에러를 먼저 적고 구현해요.
- **응답 규격(바꾸지 않아요):** 성공은 DTO를 **본문 그대로**(감싸지 않음), 목록은 `PageResponse`(page는 1부터, size 기본 12·최대 50), 생성 201, 삭제 204.
- **에러 규격:** `{ "code", "message", "fieldErrors": [] }`. `ErrorCode` enum에 코드를 추가하고 `BusinessException`으로 던져요. `GlobalExceptionHandler`가 바꿔 줘요. 메시지는 사용자에게 바로 보여줄 해요체 문장이고 내부 정보를 넣지 않아요.
- **볼 수 없는 대상은 404, 볼 수는 있지만 권한이 없으면 403** (존재 여부를 숨겨요).
- 날짜는 `Instant`로 저장하고 응답은 `KstTime.of()`로 `+09:00`.

### 백엔드
- 요청·응답은 **record DTO**로, 엔티티를 그대로 응답하지 않아요. 입력 검증은 Bean Validation + 해요체 메시지.
- **공개 조건은 두 곳에만:** JPQL은 `PostCardQueries.PUBLIC_CONDITION`, 엔티티는 `Post.isVisibleToPublic()` / `isViewableBy()`. 새 조회도 반드시 이걸 써요. 네이티브 쿼리에 조건을 따로 적었다면(사이드바 글 수, 잔디) 함께 고쳐요.
- **N+1 방지:** 목록은 `select new` DTO 프로젝션, 상세는 `join fetch`. 연관관계는 LAZY.
- 카운터(조회수, 좋아요)는 DB가 직접 더해요(`UPDATE ... SET x = x + 1`). 엔티티 필드는 `insertable/updatable = false`.
- **Flyway는 추가만:** 배포된 `V*.sql`은 절대 고치지 않고 새 버전 파일을 만들어요. `ddl-auto: validate`.
- 중복 요청이나 동시 요청은 DB 제약(PK·UNIQUE·CHECK)과 `ON CONFLICT`로 막아요.
- 주석은 기존처럼 **해요체 한국어로, 왜 그런지**를 적어요(요구사항 ID 함께, 예: `(TC-03)`).

### 프론트엔드
- 함수 컴포넌트, 데이터 불러오기는 `useAsync(load, key)`, 이펙트 안의 최신 값은 `useEffectEvent`. 렌더링 중에 `Date.now()`나 ref를 읽지 않아요(lint 규칙).
- API 호출은 `src/api/*`에만 두고, 에러 문구는 `errorMessage()`·`fieldErrorsOf()`로 보여줘요.
- 마크다운은 `MarkdownView`(rehype-sanitize)로만 그려요. HTML 문자열을 직접 넣지 않아요(XSS).
- 무거운 화면은 `React.lazy`로 지연 로딩해요(메인 번들 500KB 미만 유지).
- 반응형: 375px에서 가로 스크롤이 생기면 안 돼요.

## 7. 완료 기준 (기능 하나를 끝낼 때마다)
1. 백엔드 통합 테스트: 정상 + **권한(남의 것·비공개·비로그인)** + 경계값·실패 케이스. `./gradlew test` 전체 통과
2. `npm run lint`(경고 0), `npm run build`
3. 화면 변경이면 `e2e/`에 점검을 추가하거나 갱신하고 실행(시연 DB를 더럽히면 안 되면 `e2e/README.md`의 별도 스키마 방법)
4. 문서 동기화: 문서 저장소(ownlog-docs)의 요구사항(ID·변경 이력), ERD, API 명세, 필요하면 설계서 → 문서 저장소 `main`에 커밋·푸시. README는 이 저장소
5. `CHANGELOG.md`의 `[Unreleased]`에 사용자 관점으로 한 줄씩 추가 (추가·변경·수정·보안·삭제)
6. 의미 있는 문제를 풀었다면 문서 저장소의 `docs/트러블슈팅.md`에 사례 추가 (12장)
7. 작업 브랜치에서 커밋(11장 형식) → `develop`에 `--no-ff` 병합 → 푸시

## 8. 기능을 마친 뒤 점검 루틴
기능 단위가 끝나면 아래를 스스로 훑고, 발견한 것은 고치거나 보고해요.
- **보안:** 비공개·봉인·받는 사람 캡슐이 새는 경로(목록, 검색, 링크 대상, 그래프, 사이드바 수), 권한 검사 누락, 입력 검증, XSS
- **성능:** N+1, 불필요한 쿼리, 상한 없는 목록, 번들 크기
- **일관성:** 에러 코드·메시지, 응답 형식, 주석과 문서가 코드와 같은지
- **회귀:** 기존 테스트와 `e2e` 전체

## 9. 자주 쓰는 명령어
```bash
docker compose up -d                       # 로컬 DB
cd backend && ./gradlew bootRun            # 백엔드 :8080
cd frontend && npm run dev                 # 프론트 :5173 (/api, /uploads → 8080 프록시)
cd backend && ./gradlew test               # 백엔드 테스트 (Docker 필요)
cd frontend && npm run lint && npm run build
node e2e/run-all.mjs [이름...]             # 화면 점검 (Chrome 필요)
LOCAL_SQL=1 node scripts/seed-demo.mjs     # 로컬 시연 데이터 (회원 5명, 비밀번호 ownlog1234)
docker exec ownlog-postgres psql -U ownlog -d ownlog   # 로컬 DB 접속
```

## 10. 배포
- `main` 푸시(= release·hotfix 병합) → GitHub Actions `deploy.yml`(테스트 → Docker 이미지 → scp → `deploy/deploy.sh`). `*.md`, `docs/`, `scripts/`, `e2e/`만 바뀌면 배포하지 않아요.
- `develop`·작업 브랜치 푸시와 PR → `ci.yml`(테스트만, 배포 없음).
- 서버: `s2.java21.net`(SSH 8822), 공용 Nginx → **8360**(우리 프록시 `ownlog-proxy`) → `ownlog-blue`(8361) / `ownlog-green`(8362) **블루-그린 무중단 배포**. 컨테이너는 `--network host`(Docker 기본 네트워크로는 내부망 DB에 못 가요), 업로드는 `~/ownlog-uploads`, 활성 색은 `~/ownlog-deploy/active-color`.
- 마이그레이션은 배포 중 예전 버전과 공존하도록 **추가만** 해요 (컬럼 삭제·이름 변경은 다음 릴리스에서, 문서 저장소 `docs/guide/배포와-CI-CD.md` 4.4).
- `deploy.sh`는 시작 전에 DB 연결을 확인하고, 실패하면 원인(Caused by)을 보여줘요. 배포 결과는 Actions 화면이나 `https://ownlog.java21.net/api/topics`로 확인해요.
- Nginx 설정(`~/nginx/ownlog.java21.net.conf`)을 바꾸면 강사님께 반영을 요청해야 해요.

## 11. Git 브랜치와 버전 (Git Flow)
### 브랜치
| 브랜치 | 용도 | 만드는 곳 → 병합하는 곳 |
|---|---|---|
| `main` | **운영(배포)되는 버전**. 푸시하면 자동 배포돼요. 태그 `vX.Y.Z`는 여기에만 | release·hotfix에서만 병합 |
| `develop` | 다음 릴리스를 모으는 통합 브랜치 | 작업 브랜치에서 병합 |
| `feature/영문-이름` | 새 기능 (예: `feature/image-resize`) | `develop` → `develop` |
| `fix/영문-이름` | `develop`의 버그 | `develop` → `develop` |
| `chore/`·`docs/`·`refactor/`·`test/` | 설정·문서·리팩터링·테스트 | `develop` → `develop` |
| `release/X.Y.Z` | 릴리스 준비: 버전 올리기, CHANGELOG 정리, 마지막 확인 | `develop` → `main` + `develop` |
| `hotfix/X.Y.Z` | **운영 중 긴급 수정** (발표 직전 버그 등) | `main` → `main` + `develop` |

- 브랜치 이름은 영문 소문자와 `-`만 써요 (한글은 셸·URL에서 깨지기 쉬워요).
- 병합은 항상 `git merge --no-ff`로 해서, 어떤 브랜치의 작업이었는지 이력에 남겨요.
- 병합이 끝난 작업 브랜치는 로컬·원격에서 지워요 (`release`·`hotfix`도 태그를 남긴 뒤 지워요).
- `gh` CLI가 없어서 PR은 만들 수 없어요. 브랜치를 푸시하고 검증(완료 기준)을 마친 뒤 `--no-ff`로 병합하고, PR 템플릿(`.github/pull_request_template.md`) 항목을 보고에 담아요. 사용자가 GitHub에서 PR로 보고 싶다고 하면 브랜치만 푸시하고 기다려요.
- 여러 작업을 동시에 할 때는 `git worktree`를 써요: `git worktree add ../blog-basic-AIGJ_01_007-firstblog-<브랜치> -b feature/<이름> develop`. 다 쓰면 `git worktree remove`.

### 커밋 메시지 (Conventional Commits)
```
<type>: <한국어 요약 (무엇을, 왜)>

- 본문: 바뀐 내용을 목록으로
- 요구사항 ID, 테스트 결과

Co-Authored-By: ...
```
- type: `feat`(기능) `fix`(버그) `docs` `refactor` `perf` `test` `chore`(설정·도구) `ci` `style` `revert`
- 하위 호환이 깨지면(API 응답 형식 변경 등) `feat!:`처럼 `!`를 붙이고 본문에 `BREAKING CHANGE:`를 적어요.

### 버전 (SemVer)
- **MAJOR**: 기존 사용 방식이 깨질 때(API 응답 형식, DB 데이터 호환) · **MINOR**: 새 기능 · **PATCH**: 버그 수정
- 버전은 세 곳을 함께 올려요: `backend/build.gradle`의 `version`, `frontend/package.json`(`npm version X.Y.Z --no-git-tag-version`), `CHANGELOG.md`.
- 태그는 주석 태그로: `git tag -a vX.Y.Z -m "vX.Y.Z: 한 줄 요약"`. 태그는 지우거나 옮기지 않아요.

### 릴리스 절차 ("릴리스해 줘"라고 하면)
1. `git log <마지막 태그>..develop`으로 변경을 모아 **다음 버전을 SemVer로 제안**해요 (feat가 있으면 MINOR, fix만 있으면 PATCH).
2. `release/X.Y.Z`를 `develop`에서 만들고, 버전 세 곳을 올리고, `[Unreleased]`를 `[X.Y.Z] - 날짜`로 옮겨요. 테스트·lint·build를 돌려요.
3. `main`에 `--no-ff` 병합 → 태그 `vX.Y.Z` → `develop`에도 병합 → 푸시 (main 푸시 = 배포)
4. 배포 확인: `https://ownlog.java21.net/api/topics`, 필요하면 `e2e/rehearsal.mjs`

### 핫픽스 절차 (운영 긴급 수정)
`hotfix/X.Y.(Z+1)`을 `main`에서 만들어 고치고 → 회귀 테스트 → `main` 병합·태그 → `develop`에도 병합.

## 12. 트러블슈팅 기록 (계속 쌓아요)
- 버그·장애·예상과 다른 동작을 **원인까지 분석해서** 해결했다면, 작업을 마칠 때 문서 저장소의 `docs/트러블슈팅.md`에 사례를 추가해요. 오타나 단순 실수는 제외해요.
- 형식: **상황 → 원인 분석 → 해결(코드) → 결과(테스트·수치) → 배운 점**. 맨 위 "한눈에 보기" 표에도 한 줄을 추가해요.
- **실제로 겪은 것만** 써요. 미리 막은 문제는 "설계 단계에서 차단"이라고 밝히고, 추정은 추정이라고 써요.
- 릴리스할 때 이번 버전에서 추가한 트러블슈팅 사례를 CHANGELOG에서 언급해요.

