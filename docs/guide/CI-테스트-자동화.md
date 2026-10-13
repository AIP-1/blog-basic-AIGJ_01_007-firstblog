# CI: 코드를 올릴 때마다 자동으로 테스트하기 (`ci.yml` 분석)

> `.github/workflows/ci.yml` 한 파일을 처음부터 끝까지 뜯어보는 문서예요.
> "GitHub Actions가 뭔지는 들어 봤는데, 우리 프로젝트에서 **언제, 무엇을, 왜** 돌리는지"를 알고 싶은 사람을 위해 썼어요.
> 배포(`deploy.yml`)와 무중단 배포는 [배포와-CI-CD.md](배포와-CI-CD.md)에 있어요.

---

## 1. 한 줄 요약

> **`ci.yml`은 "합쳐도 안전한지"만 확인해요. 배포는 하지 않아요.**
> `develop`이나 작업 브랜치에 코드를 올리면, GitHub이 빌려준 컴퓨터에서 **백엔드 테스트 193개 + 프론트엔드 린트·빌드**를 자동으로 돌려요.

| | `ci.yml` (이 문서) | `deploy.yml` |
|---|---|---|
| 이름 (Actions 탭에 보이는 이름) | **CI** | **Deploy** |
| 언제 | `develop`·작업 브랜치에 push, `main`·`develop`으로 PR | `main`에 push |
| 하는 일 | 테스트만 | 테스트 → 이미지 빌드 → 서버 전송 → 배포 |
| 필요한 비밀값 | **없음** | DB·SSH 등 Secrets |
| 실패하면 | 커밋 옆에 ❌. 코드는 그대로 | 배포가 시작되지 않음 (운영은 예전 버전 유지) |

### 용어 짚고 가기

| 용어 | 뜻 |
|---|---|
| **CI** (Continuous Integration, 지속적 통합) | 여러 사람(또는 여러 브랜치)의 코드를 **자주 합치되, 합칠 때마다 자동으로 검사**하는 습관. "내 컴퓨터에선 됐는데"를 막아요 |
| **워크플로(workflow)** | `.github/workflows/` 안의 YAML 파일 하나. "언제(`on`) 무엇을(`jobs`) 할지" 적어요 |
| **이벤트(event)** | 워크플로를 깨우는 일. `push`, `pull_request` 등 |
| **잡(job)** | 한 대의 컴퓨터(러너)에서 이어서 실행되는 작업 묶음. `ci.yml`은 잡이 `test` 하나예요 |
| **스텝(step)** | 잡 안의 한 단계. `uses:`(남이 만든 액션 가져다 쓰기) 또는 `run:`(셸 명령) |
| **러너(runner)** | 워크플로를 실제로 실행하는 컴퓨터. 우리는 GitHub이 빌려주는 `ubuntu-latest`를 써요. 매번 **새 컴퓨터**라 이전 실행의 흔적이 없어요 |

---

## 2. 파일 전체

```yaml
 1  # develop·작업 브랜치에 푸시하거나 PR을 열면 테스트만 돌려요 (배포는 main만: deploy.yml)
 2  name: CI
 3
 4  on:
 5    push:
 6      branches: [develop, "feature/**", "fix/**", "chore/**", "refactor/**", "test/**", "release/**", "hotfix/**"]
 7      paths-ignore:      # 문서만 바뀌면 테스트하지 않아요
 8        - "**.md"
 9        - "docs/**"
10    pull_request:
11      branches: [main, develop]
12
13  jobs:
14    test:
15      runs-on: ubuntu-latest
16      steps:
17        - uses: actions/checkout@v4
18
19        - uses: actions/setup-java@v4
20          with:
21            distribution: temurin
22            java-version: "21"
23            cache: gradle
24        - name: 백엔드 테스트 (Testcontainers로 진짜 PostgreSQL 사용)
25          working-directory: backend
26          run: ./gradlew test --no-daemon
27
28        - uses: actions/setup-node@v4
29          with:
30            node-version: "24"
31            cache: npm
32            cache-dependency-path: frontend/package-lock.json
33        - name: 프론트엔드 린트·빌드
34          working-directory: frontend
35          run: |
36            npm ci
37            npm run lint
38            npm run build
```

38줄이 전부예요. 크게 **언제(4~11줄)** 와 **무엇을(13~38줄)** 두 부분이에요.

---

## 3. 언제 실행되나요 (`on`, 4~11줄)

### 3.1 `push` + `branches` (5~6줄)

이 브랜치들에 커밋을 **올리면(push)** 실행돼요.

| 브랜치 | 왜 넣었나요 |
|---|---|
| `develop` | 다음 릴리스를 모으는 통합 브랜치. 작업 브랜치를 병합한 **결과**가 깨지지 않았는지 확인해요 (각각은 통과해도 합치면 깨질 수 있어요) |
| `feature/**`, `fix/**` | 기능·버그 작업 중에도 올릴 때마다 확인해요. `**`는 `feature/social-login`처럼 그 아래 모든 이름이에요 |
| `chore/**`, `refactor/**`, `test/**` | 설정 변경·리팩터링·테스트 추가도 기능을 깨뜨릴 수 있어요 |
| `release/**` | 릴리스 직전 마지막 확인 (버전 올리기, CHANGELOG 정리 뒤) |
| `hotfix/**` | 운영 긴급 수정. 급할수록 자동 확인이 필요해요 |

**일부러 뺀 브랜치**
- **`main`**: `main`에 올리면 `deploy.yml`이 **똑같은 테스트를 먼저** 돌리고 나서 배포해요. 여기에도 넣으면 같은 테스트가 두 번 돌아요.
- **`docs/**`**: 문서 작업 브랜치라 코드가 바뀌지 않아요. (넣어도 아래 `paths-ignore` 때문에 대부분 건너뛰어요.)

> 브랜치 규칙은 [git-브랜치와-버전관리.md](git-브랜치와-버전관리.md)와 `CLAUDE.md` 11장(Git Flow)에 있어요. 새 종류의 브랜치(예: `perf/**`)를 쓰기 시작하면 여기에도 추가해야 CI가 돌아요.

### 3.2 `paths-ignore` (7~9줄)

```yaml
paths-ignore:
  - "**.md"      # 어느 폴더든 .md 파일
  - "docs/**"    # docs 폴더 안의 모든 파일
```

push한 커밋들이 바꾼 파일이 **전부** 이 목록에 해당하면 실행하지 않아요. 하나라도 코드가 섞여 있으면 실행돼요.

| 바꾼 파일 | CI 실행? |
|---|---|
| `README.md`만 | ❌ 건너뜀 |
| `docs/guide/소셜-로그인.md` + `docs/ERD.md` | ❌ 건너뜀 |
| `README.md` + `SecurityConfig.java` | ✅ 실행 (코드가 섞임) |
| `e2e/check-social.mjs`만 | ✅ 실행 (`e2e/`는 목록에 없어요) |

이유: 문서만 고쳤는데 테스트에 몇 분씩 쓰면 러너 시간이 아깝고, 결과도 항상 같아요.

> ⚠️ **`paths-ignore`는 `push`에만 붙어 있어요.** 아래 `pull_request`에는 없어서, **PR은 문서만 바뀌어도 항상 실행**돼요. (합치기 전 마지막 확인이라 일부러 그대로 뒀어요.)

### 3.3 `pull_request` (10~11줄)

`main`이나 `develop`으로 **합쳐 달라는 PR을 열거나, PR에 커밋을 더 올리면** 실행돼요.
결과가 PR 화면에 "All checks have passed ✅ / Some checks were not successful ❌"로 떠서, 리뷰어가 합치기 전에 볼 수 있어요.

> 이 프로젝트는 `gh` CLI가 없어서 지금은 PR 대신 로컬에서 `--no-ff` 병합 후 푸시해요. 그래서 실제로는 **`develop` push 때 도는 CI**가 병합 결과 확인 역할을 해요. 팀원과 PR로 일하게 되면 이 설정이 바로 쓰여요.

### 3.4 Git Flow에서 언제 무엇이 도는지

```
feature/x 에 push ─────────▶ CI
feature/x → develop 병합, push ─▶ CI  (병합 결과 확인)
release/1.2.0 에 push ─────▶ CI
release → main 병합, push ──▶ Deploy (테스트 → 배포)   ※ CI는 안 돌아요
태그 v1.2.0 push ──────────▶ 아무것도 안 돌아요 (태그는 on에 없음)
docs/x 에 push ────────────▶ 아무것도 안 돌아요
```

---

## 4. 무엇을 하나요 (`jobs`, 13~38줄)

### 4.1 `runs-on: ubuntu-latest` (15줄)

GitHub이 리눅스 가상 컴퓨터를 하나 새로 만들어 줘요. 여기에는 **Docker가 미리 설치돼 있어서** 아래 Testcontainers가 바로 동작해요. 실행이 끝나면 컴퓨터는 버려져요.

### 4.2 `actions/checkout@v4` (17줄)

새 컴퓨터에는 우리 코드가 없어요. 이 스텝이 **push된 그 커밋**을 내려받아요.
`@v4`는 액션의 버전이에요. 버전을 고정해야 액션 제작자가 바꿔도 우리 CI가 갑자기 깨지지 않아요.

### 4.3 Java 준비 — `actions/setup-java@v4` (19~23줄)

| 설정 | 뜻 |
|---|---|
| `distribution: temurin` | Eclipse Temurin JDK (Dockerfile의 `eclipse-temurin:21`과 같은 배포판) |
| `java-version: "21"` | `build.gradle`의 `toolchain`과 같은 Java 21 |
| `cache: gradle` | Gradle이 내려받은 라이브러리(`~/.gradle/caches`)를 **다음 실행에 재사용**해요. 키는 `*.gradle`·`gradle-wrapper.properties` 파일 내용이라, 의존성을 바꾸면 캐시도 새로 만들어요 |

캐시가 없으면 Spring Boot·Hibernate·Testcontainers 같은 라이브러리를 매번 인터넷에서 다시 받아요. 캐시는 **속도용**일 뿐, 결과에는 영향을 주지 않아요.

### 4.4 백엔드 테스트 — `./gradlew test --no-daemon` (24~26줄)

- `working-directory: backend`: `backend/` 폴더에서 실행해요 (`cd backend`와 같아요).
- `./gradlew`: 저장소에 들어 있는 **Gradle Wrapper**(9.7.1)를 써요. 러너에 Gradle을 따로 설치하지 않아도 되고, 모두가 같은 버전을 써요.
- `--no-daemon`: Gradle은 보통 다음 빌드를 빠르게 하려고 백그라운드 프로세스(데몬)를 남겨요. CI는 한 번 돌고 끝나니 남길 이유가 없어요.

**이 한 줄에서 실제로 일어나는 일**

1. 테스트 코드 컴파일 → 테스트 193개 실행 (2026-10-13 기준).
2. 통합 테스트는 `TestcontainersConfiguration`이 **Docker로 진짜 PostgreSQL 17**을 띄워요.
   ```java
   @Bean @ServiceConnection
   PostgreSQLContainer postgresContainer() {
       return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
   }
   ```
   `@ServiceConnection`이 컨테이너의 주소·계정을 Spring에 자동으로 연결해 줘서, DB 비밀번호 같은 설정이 필요 없어요.
3. 빈 DB에 **Flyway가 `V1`~`V6` 마이그레이션을 처음부터 전부 실행**해요. 그래서 CI가 통과하면 "새 서버의 빈 DB에도 마이그레이션이 깨끗하게 적용된다"는 것까지 확인돼요. `ddl-auto: validate`가 엔티티와 테이블이 맞는지도 검사해요.
4. JWT 비밀키 같은 값은 `backend/src/test/resources/application-test.yaml`의 **테스트 전용 값**을 써요.

> 💡 **CI에는 GitHub Secrets가 하나도 필요 없어요.** 진짜 DB는 Testcontainers가 즉석에서 만들고, 비밀키는 테스트 전용 값이라서예요. 그래서 비밀값이 새어 나갈 걱정 없이 어느 브랜치·PR에서나 돌릴 수 있어요.

**H2 같은 메모리 DB 대신 진짜 PostgreSQL을 쓰는 이유**
우리 코드에는 PostgreSQL에만 있는 기능이 많아요. 부분 인덱스, `CHECK` 제약, `ON CONFLICT`, 네이티브 SQL(잔디·관련 글 계산), `TIMESTAMPTZ` 등이에요. 메모리 DB로 테스트하면 "테스트는 통과, 운영은 실패"가 생길 수 있어요.

**테스트 193개가 확인하는 것 (예)**
- 회원가입·로그인·JWT, 소셜 로그인 (코드 교환, 이메일 충돌, 위조 state)
- 권한: 남의 글 수정, 비공개 글, 비로그인 접근 → 403/404/401
- 공개 조건: 비공개·봉인 캡슐·받는 사람 캡슐이 목록·검색·링크·그래프로 새지 않는지
- 동시성: 좋아요·조회수 동시 100명
- 트랜잭션: 회원가입·탈퇴 중간 실패 시 롤백
- 보안 회귀: 인코딩된 주소로 인증 우회, 남의 파일 삭제 (트러블슈팅 2·3번)

### 4.5 Node 준비 — `actions/setup-node@v4` (28~32줄)

| 설정 | 뜻 |
|---|---|
| `node-version: "24"` | 프론트엔드 개발·Dockerfile(`node:24-alpine`)과 같은 Node 24 |
| `cache: npm` | npm이 내려받은 패키지(`~/.npm`)를 재사용해요 |
| `cache-dependency-path` | 캐시 키로 쓸 파일. `package-lock.json`이 루트가 아니라 `frontend/` 안에 있어서 알려 줘야 해요 |

### 4.6 프론트엔드 린트·빌드 (33~38줄)

`run: |`은 여러 줄 명령이에요. 한 줄이라도 실패하면 그 자리에서 멈춰요.

| 명령 | 하는 일 | 무엇을 잡나요 |
|---|---|---|
| `npm ci` | `package-lock.json`에 적힌 **정확한 버전**으로 설치 (`node_modules`를 새로 만들어요) | `package.json`과 lock 파일이 어긋나면 실패 → "내 컴퓨터만 다른 버전" 방지 |
| `npm run lint` | `oxlint`로 코드 규칙 검사 | 쓰지 않는 변수, React 훅 규칙 위반, 렌더링 중 `Date.now()` 같은 실수 |
| `npm run build` | `vite build`로 배포용 파일 생성 | import 오타, 없는 파일, 문법 오류 (린트가 못 잡는 "빌드가 안 되는" 문제) |

> `npm install`이 아니라 `npm ci`인 이유: `install`은 상황에 따라 lock 파일을 **고쳐 버릴** 수 있어요. CI는 "올린 그대로"를 검사해야 해서 `ci`를 써요.

### 4.7 순서와 실패

스텝은 **위에서부터 차례로** 실행되고, 하나가 실패하면 **뒤 스텝은 건너뛰어요.**

```
checkout → Java 준비 → 백엔드 테스트 ❌ → (Node 준비, 프론트 린트·빌드: 건너뜀)
```

그래서 백엔드 테스트가 실패하면 그 실행에서는 프론트엔드 결과를 알 수 없어요. 백엔드를 고쳐 다시 올리면 둘 다 확인돼요. ([7장](#7-더-좋게-만들-수-있는-점-제안-아직-적용-안-함)에 나눠 돌리는 방법이 있어요.)

---

## 5. CI가 확인하지 **않는** 것

| 확인 안 하는 것 | 이유 | 대신 어디서 |
|---|---|---|
| **화면 점검 (`e2e/`, 125개)** | 실행 중인 서버 + Chrome이 필요하고 약 9분 걸려요 | 로컬에서 `node e2e/run-all.mjs` (릴리스 전 필수, `CLAUDE.md` 7장) |
| **Docker 이미지 빌드** | 이미지는 배포할 때만 필요해요 | `deploy.yml`이 `main`에서 빌드 |
| **실제 배포·운영 DB** | CI는 서버에 접속하지 않아요 (Secrets 없음) | `deploy.yml` + `deploy.sh`의 DB 연결 확인·헬스 체크 |
| **소셜 로그인의 진짜 카카오·구글 연결** | 외부 서비스를 테스트에서 부르지 않아요 | 운영에서 키를 넣고 직접 확인 |

즉 CI ✅는 "**코드 수준에서는** 합쳐도 안전하다"는 뜻이에요. 화면과 배포까지 안전하다는 뜻은 아니에요.

---

## 6. 결과 보는 법

1. GitHub 저장소 → **Actions** 탭 → 왼쪽에서 **CI** 선택.
2. 실행 목록에서 커밋 메시지·브랜치를 보고 하나를 눌러요.
3. `test` 잡 → 스텝별 로그. 실패한 스텝은 ❌로 표시되고 펼쳐져 있어요.

| 표시 | 뜻 |
|---|---|
| 🟡 (노란 원) | 실행 중 |
| ✅ | 모든 스텝 통과 |
| ❌ | 어느 스텝이 실패 (로그 확인) |
| ⊘ 회색 | 취소됨 |

저장소의 **커밋 목록이나 브랜치 화면**에서도 커밋 옆에 ✅/❌가 붙어요.

**실패 로그에서 찾을 것**
- 백엔드: `FAILED` 가 붙은 테스트 이름과 그 아래 `expected: … but was: …` 또는 `Caused by:`
- 프론트: `oxlint`가 알려 주는 `파일:줄:칸` 위치, 또는 `vite build`의 `error`

> 테스트 리포트(HTML)는 지금 파일로 남기지 않아서 로그로만 봐요. 같은 리포트를 로컬에서 `backend/build/reports/tests/test/index.html`로 볼 수 있어요.

---

## 7. 더 좋게 만들 수 있는 점 (제안, 아직 적용 안 함)

지금 설정은 2주짜리 1인 프로젝트에 맞게 단순하게 뒀어요. 팀으로 커지면 고려할 것들이에요. **배포 설정 변경이라 적용하려면 따로 결정이 필요해요.**

| 제안 | 왜 | 예시 |
|---|---|---|
| **같은 브랜치의 이전 실행 취소** | 연달아 push하면 예전 커밋 테스트는 의미가 없어요 | `concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }` |
| **시간 제한** | 기본 제한이 6시간이라, 테스트가 멈추면 오래 붙잡혀요 (e2e에서 겪은 문제와 같은 종류, 트러블슈팅 15번) | 잡에 `timeout-minutes: 20` |
| **최소 권한** | CI는 코드를 읽기만 하면 돼요 | `permissions: { contents: read }` |
| **백엔드·프론트를 별도 잡으로** | 동시에 돌아서 더 빨리 끝나고, 한쪽이 실패해도 다른 쪽 결과를 볼 수 있어요 | `jobs: { backend: …, frontend: … }` |
| **실패하면 테스트 리포트 업로드** | 로그보다 보기 쉬운 HTML 리포트 | `actions/upload-artifact` + `if: failure()` |
| **브랜치 보호에서 CI 필수** | ❌인 코드가 `main`·`develop`에 들어가지 못하게 | GitHub Settings → Branches → "Require status checks" ([github-organization과-readme.md](github-organization과-readme.md)) |
| **`deploy.yml`과 테스트 잡 공유** | 지금 두 파일에 같은 테스트 스텝이 복사돼 있어요. 고칠 때 한쪽만 고치기 쉬워요 | 재사용 워크플로(`workflow_call`) |

---

## 8. 자주 묻는 것

**Q. README만 고쳐서 올렸는데 Actions에 아무것도 안 떠요.**
정상이에요. `paths-ignore` 때문에 건너뛴 거예요 (3.2).

**Q. 로컬에서는 테스트가 통과하는데 CI에서 실패해요.**
흔한 원인은 세 가지예요.
1. 커밋하지 않은 파일이 있어요 (로컬에만 있는 파일을 테스트가 쓰고 있음) → `git status` 확인.
2. `.env`에만 있는 값에 기대고 있어요. CI에는 `.env`가 없어요 → 테스트에 필요한 값은 `application-test.yaml`에.
3. 시간·시간대에 기대는 테스트예요. 러너는 UTC예요 → 날짜 계산은 `KstTime`·`Instant`로.

**Q. `npm ci`에서 실패해요.**
`package.json`을 고쳤는데 `package-lock.json`을 함께 커밋하지 않았을 가능성이 커요. 로컬에서 `npm install` 후 두 파일을 같이 커밋해요.

**Q. Testcontainers가 Docker를 못 찾는다고 해요.**
CI(`ubuntu-latest`)에서는 Docker가 있어서 생기지 않아요. 로컬에서 생기면 Docker Desktop이 켜져 있는지 확인해요 ([개발환경.md](개발환경.md)).

**Q. CI가 통과하면 배포해도 되나요?**
코드 수준 확인만 끝난 거예요. 릴리스 전에는 화면 점검(e2e)까지 돌려요 (5장, `CLAUDE.md` 7장 완료 기준).

---

## 9. 로컬에서 CI와 똑같이 돌려 보기

CI가 하는 일을 그대로 따라 하면 올리기 전에 미리 확인할 수 있어요.

```bash
# 백엔드 (Docker Desktop이 켜져 있어야 해요)
cd backend && ./gradlew test --no-daemon

# 프론트엔드
cd frontend && npm ci && npm run lint && npm run build
```

둘 다 통과하면 CI도 거의 확실히 통과해요 (8장의 "로컬에만 있는 것"만 조심하면 돼요).

---

## 10. 이력

| 날짜 | 변경 |
|---|---|
| 2026-10-12 | Git Flow 도입과 함께 `ci.yml` 추가 (커밋 `577ac72`): 작업 브랜치는 테스트만, 배포는 `main`만 |
| 2026-10-13 | v1.2.0: `refactor/**`, `test/**` 브랜치 추가 |
