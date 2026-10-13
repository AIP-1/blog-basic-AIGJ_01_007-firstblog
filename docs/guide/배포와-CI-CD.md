# 배포와 CI/CD, 그리고 무중단 배포

> 이 문서는 "배포가 뭔지는 대충 알지만 GitHub Actions·Docker·Nginx는 처음"인 사람을 위해 썼어요.
> OwnLog가 **코드를 main에 올리는 순간부터 https://ownlog.java21.net 에 새 버전이 뜨기까지** 무슨 일이 일어나는지,
> 그리고 그동안 **사용자가 접속해도 끊기지 않게(무중단)** 어떻게 만들었는지를 순서대로 설명해요.

관련 파일

| 파일 | 하는 일 |
|---|---|
| `.github/workflows/ci.yml` | develop·작업 브랜치에 올리면 **테스트만** 돌려요 |
| `.github/workflows/deploy.yml` | main에 올리면 **테스트 → 이미지 빌드 → 서버 전송 → 배포** |
| `Dockerfile` | 프론트엔드 + 백엔드를 이미지 하나로 묶는 방법 |
| `deploy/deploy.sh` | 서버에서 실행돼요. **블루-그린 무중단 배포**의 본체 |
| `backend/src/main/resources/application-prod.yaml` | 운영 설정 (DB, 정상 종료 등) |

---

## 1. 먼저 용어부터

| 용어 | 쉬운 설명 |
|---|---|
| **CI** (Continuous Integration, 지속적 통합) | 코드를 올릴 때마다 **자동으로 테스트**해서 "합쳐도 안전한지" 확인하는 것 |
| **CD** (Continuous Delivery/Deployment, 지속적 배포) | 테스트를 통과한 코드를 **자동으로 서버에 배포**하는 것 |
| **GitHub Actions** | GitHub이 빌려주는 컴퓨터(러너)에서 정해 둔 작업을 자동 실행해 주는 기능. `.github/workflows/*.yml`에 적어요 |
| **워크플로(workflow)** | "언제(on) 무엇을(jobs) 할지" 적은 YAML 파일 하나 |
| **잡(job) / 스텝(step)** | 워크플로 안의 큰 작업 단위 / 그 안의 한 줄 한 줄 명령 |
| **Docker 이미지** | 앱과 실행에 필요한 모든 것(Java, 빌드 결과물)을 묶은 **실행 파일 꾸러미** |
| **컨테이너** | 이미지를 실제로 실행한 것. 같은 이미지로 여러 개를 띄울 수 있어요 |
| **리버스 프록시 (Nginx)** | 사용자 요청을 먼저 받아서 뒤쪽 앱에게 전달해 주는 "안내 데스크" |
| **헬스 체크** | "이 앱 지금 정상인가요?"를 확인하려고 보내는 요청. 우리는 `GET /api/topics`가 200이면 정상으로 봐요 |
| **무중단 배포** | 새 버전으로 바꾸는 동안에도 사용자의 요청이 **하나도 실패하지 않게** 배포하는 것 |

---

## 2. 전체 그림

```
 개발자                GitHub                              학교 서버
───────────────────────────────────────────────────────────────────────────────────────────
 feature/* 작업
   │ push
   ▼
 develop ──────────▶ ci.yml: 백엔드 테스트 + 프론트 린트·빌드  (배포는 안 해요)
   │ release/* → main 병합 (Git Flow, git-브랜치와-버전관리.md)
   ▼
 main ─────────────▶ deploy.yml
                      ① test 잡: ci.yml과 같은 테스트 (하나라도 실패하면 여기서 멈춤)
                      ② deploy 잡: docker build → ownlog.tar.gz
                         │ scp (SSH로 파일 복사)
                         ▼
                                                 ~/ownlog-deploy/ownlog.tar.gz, deploy.sh
                         │ ssh (SSH로 명령 실행)
                         ▼
                                                 bash deploy.sh  ── 블루-그린 무중단 배포 (4장)
```

사용자 요청은 이렇게 흘러가요.

```
 브라우저 ──https──▶ 공용 Nginx (강사님 서버, 443)
                         │ ownlog.java21.net → 127.0.0.1:8360
                         ▼
                    ownlog-proxy (우리 Nginx 컨테이너, 8360)
                         │ 지금 활성인 색으로만
               ┌─────────┴─────────┐
        ownlog-blue (8361)   ownlog-green (8362)     ← 둘 중 하나만 서비스, 배포 중에만 둘 다 떠 있어요
               └─────────┬─────────┘
                         ▼
                 PostgreSQL (수업 DB, 스키마 분리)
```

> 서버에서 쓸 수 있는 포트는 **8360~8369**예요. 공용 Nginx는 8360만 알고 있고, 우리는 그 뒤에 프록시를 하나 더 둬서
> 8361/8362를 오가요. 공용 Nginx 설정은 강사님만 바꿀 수 있어서, **공용 Nginx를 건드리지 않고** 무중단을 만든 게 핵심이에요.

---

## 3. GitHub Actions 파일 한 줄씩 읽기

### 3.1 `ci.yml` — 테스트만

> 더 자세한 분석(브랜치별 동작, Testcontainers, 실패 로그 읽기, 개선 제안)은 [CI-테스트-자동화.md](CI-테스트-자동화.md)에 있어요.

```yaml
on:
  push:
    branches: [develop, "feature/**", "fix/**", "chore/**", "refactor/**", "test/**", "release/**", "hotfix/**"]
    paths-ignore: ["**.md", "docs/**"]   # 문서만 바뀌면 테스트하지 않아요
  pull_request:
    branches: [main, develop]             # PR을 열면 합치기 전에 테스트 결과가 PR 화면에 떠요
```

- `on`: **언제** 실행할지. 위 브랜치에 push하거나, main·develop으로 PR을 열 때예요.
- `paths-ignore`: 문서만 고친 커밋은 테스트할 필요가 없어서 건너뛰어요 (러너 시간 절약).

```yaml
jobs:
  test:
    runs-on: ubuntu-latest           # GitHub이 빌려주는 리눅스 컴퓨터
    steps:
      - uses: actions/checkout@v4    # 저장소 코드를 내려받아요
      - uses: actions/setup-java@v4  # Java 21 설치 (cache: gradle → 라이브러리를 다음에 재사용)
      - run: ./gradlew test          # 백엔드 테스트. Testcontainers가 진짜 PostgreSQL을 띄워서 시험해요
      - uses: actions/setup-node@v4  # Node 24 설치 (cache: npm)
      - run: npm ci && npm run lint && npm run build   # 프론트 린트 + 빌드가 깨지지 않는지
```

- `uses`: 남이 만들어 둔 **액션**(재사용 가능한 스텝)을 가져다 써요. `run`: 셸 명령을 직접 실행해요.
- `npm ci`: `package-lock.json`에 적힌 버전 그대로 설치해요 (`npm install`과 달리 버전을 바꾸지 않아요).

### 3.2 `deploy.yml` — main에 올리면 배포

```yaml
on:
  push:
    branches: [main]
    paths-ignore: ["**.md", "docs/**", "scripts/**"]
  workflow_dispatch:     # Actions 화면의 "Run workflow" 버튼으로 직접 실행도 돼요
env:
  APP_PORT: "8360"       # 공용 Nginx가 보내 주는 포트 (= 우리 프록시가 듣는 포트)
```

**① test 잡** — `ci.yml`과 같아요. `deploy` 잡에 `needs: test`가 있어서 **테스트가 실패하면 배포가 아예 시작되지 않아요.**

**② deploy 잡**

```yaml
- name: Docker 이미지 빌드
  run: |
    docker build -t ownlog:${{ github.sha }} .              # 이미지 이름표(태그) = 커밋 해시
    docker save ownlog:${{ github.sha }} | gzip > ownlog.tar.gz   # 이미지를 파일 하나로
```

- `${{ github.sha }}`: 이번 커밋의 고유 번호. 태그로 쓰면 "서버에 떠 있는 게 어느 커밋인지" 바로 알 수 있어요.
- 이미지 저장소(Docker Hub 등)를 쓰지 않고 **파일로 만들어 직접 보내요**. 저장소 계정·권한을 따로 관리하지 않아도 돼요.

```yaml
- name: 서버로 파일 전송
  uses: appleboy/scp-action@v0.1.7
  with:
    host: ${{ secrets.SSH_ADDRESS }}   # secrets.* = GitHub에 숨겨 둔 값 (로그에는 ***로 가려져요)
    ...
    source: "ownlog.tar.gz,deploy/deploy.sh"
    target: "~/ownlog-deploy"
```

```yaml
- name: 서버에서 배포 스크립트 실행
  uses: appleboy/ssh-action@v1.2.0
  env:                                 # 러너의 환경변수로 비밀값을 올려 두고
    IMAGE_TAG: ${{ github.sha }}
    DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
    OAUTH_KAKAO_CLIENT_ID: ${{ secrets.OAUTH_KAKAO_CLIENT_ID }}   # 소셜 로그인 키 (없으면 빈 값 → 그 버튼만 숨김)
    ...
  with:
    envs: IMAGE_TAG,APP_PORT,DB_ADDRESS,...   # 이 이름들만 서버 쪽 셸로 넘겨요
    command_timeout: 15m
    script: bash ~/ownlog-deploy/deploy/deploy.sh
```

- 비밀값은 **코드에도, 커밋에도, 명령어 기록에도 남지 않아요.** GitHub Secrets → 러너 환경변수 → SSH 세션 환경변수 → `docker run -e 이름`(값 없이 이름만) 순서로만 흘러가요.
- `command_timeout: 15m`: 블루-그린은 "새 버전 기동(최대 90초) + 예전 버전 정상 종료(최대 30초)"를 기다려서 기본값(10분)보다 넉넉히 줬어요.

### 3.3 Secrets 목록 (이름만, 값은 절대 문서에 쓰지 않아요)

| 이름 | 필수 | 용도 |
|---|---|---|
| `SSH_ADDRESS`, `SSH_PORT`, `SSH_ID`, `SSH_PASSWORD` | ✅ | 서버 접속 |
| `DB_ADDRESS`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SCHEMA` | ✅ | 수업 DB |
| `JWT_SECRET` | 선택 | 없으면 서버가 한 번 만들어 `~/ownlog-deploy/jwt.secret`에 보관 |
| `OAUTH_{KAKAO,NAVER,GOOGLE,GITHUB}_CLIENT_{ID,SECRET}` | 선택 | 소셜 로그인 ([소셜-로그인.md](소셜-로그인.md)) |

---

## 4. 무중단 배포 — 왜, 그리고 어떻게

### 4.1 예전 방식의 문제

v1.1.0까지 `deploy.sh`는 이렇게 동작했어요.

```
docker rm -f ownlog        ← 서비스 중인 앱을 바로 끔   ─┐
docker run ... ownlog      ← 새 앱 시작                  │  이 사이(Spring 기동 20~40초) 동안
(뜰 때까지 기다림)                                       ─┘  모든 요청이 502 Bad Gateway
```

새 버전이 뜨기 전에 예전 버전을 먼저 꺼서, **배포할 때마다 수십 초 동안 사이트가 죽었어요.**
게다가 새 버전이 설정 실수로 아예 못 뜨면, **예전 버전도 이미 꺼진 상태라 사이트가 계속 죽어 있었어요.**

### 4.2 무중단 배포 방식 비교

| 방식 | 어떻게 | 장점 | 단점 | 우리 선택 |
|---|---|---|---|---|
| **롤링(Rolling)** | 서버 여러 대를 한 대씩 차례로 교체 | 자원을 조금만 더 씀 | 서버가 **여러 대**여야 의미가 있음. 교체 중 두 버전이 섞임 | ❌ 서버 1대 |
| **블루-그린(Blue-Green)** | 똑같은 환경 두 벌(블루·그린). 쉬는 쪽에 새 버전을 띄우고, 다 뜨면 **트래픽을 한 번에 넘김** | 전환이 순간적. 실패하면 안 넘기면 끝. 되돌리기도 쉬움 | 배포하는 동안 앱이 **두 개** 떠 있어 메모리 2배 | ✅ |
| **카나리(Canary)** | 새 버전에 사용자 일부(예: 5%)만 먼저 보내 보고 점점 늘림 | 위험을 가장 작게 | 트래픽 비율 조절·지표 모니터링 체계가 필요 | ❌ 규모에 과함 |

서버가 한 대이고 메모리 여유(앱 하나 768MB)가 있어서 **블루-그린**을 골랐어요.

### 4.3 블루-그린 4단계 (`deploy/deploy.sh`)

```
지금: proxy(8360) ──▶ blue(8361)                     ← 사용자는 blue를 써요

1) green 띄우기     proxy ──▶ blue      green(8362) 기동 중…   ← 사용자는 여전히 blue
2) green 헬스 체크  curl localhost:8362/api/topics → 200 될 때까지 (최대 90초)
      └ 실패하면? green을 지우고 끝. proxy는 그대로 blue → 사용자 영향 0
3) 프록시 전환      proxy 설정을 green으로 다시 쓰고  nginx -s reload
                    proxy ──▶ green                        ← 새 요청부터 green으로
4) blue 정리        10초 기다림(blue에 남은 요청이 끝날 시간) → docker stop -t 30 blue
```

어느 색이 활성인지는 서버의 `~/ownlog-deploy/active-color` 파일에 적어 둬요. 다음 배포는 반대 색에 띄워요.

#### 왜 `nginx -s reload`는 연결을 끊지 않나요?

Nginx는 **마스터 프로세스 1개 + 워커 프로세스 여러 개**로 동작해요. reload 신호를 받으면

1. 마스터가 새 설정 파일을 읽고 문법을 확인해요 (틀리면 예전 설정 그대로 유지).
2. **새 설정으로 새 워커**를 띄워서 새 연결은 새 워커가 받아요.
3. **예전 워커는 새 연결을 받지 않고, 처리 중이던 요청을 끝까지 응답한 뒤** 스스로 종료해요 (graceful).

그래서 전환하는 순간에도 진행 중인 요청이 잘리지 않아요. `deploy.sh`는 reload 전에 `nginx -t`로 문법을 한 번 더 확인해요.

#### 왜 Spring에도 정상 종료(graceful shutdown)가 필요한가요?

전환 뒤에도 blue에서 **처리 중인 요청**(예: 이미지 업로드)이 남아 있을 수 있어요. `docker stop`은 컨테이너에 SIGTERM을 보내는데,

```yaml
# application-prod.yaml
server:
  shutdown: graceful                     # 새 요청은 거절하고, 처리 중인 요청은 끝까지 응답한 뒤 종료
spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s      # 그래도 20초가 넘으면 종료
```

이 설정이 없으면 Spring이 SIGTERM을 받자마자 처리 중인 요청을 잘라 버려요.
`docker stop -t 30`(30초 안에 안 꺼지면 강제 종료)을 Spring 대기 시간(20초)보다 길게 둬서, Spring이 스스로 정리할 시간을 보장해요.

#### 우리 Nginx 컨테이너(`ownlog-proxy`) 설정

`deploy.sh`가 배포할 때마다 `~/ownlog-deploy/proxy/ownlog.conf`를 다시 써요.

```nginx
server {
    listen 8360;
    client_max_body_size 20m;                         # 이미지 업로드(5MB)가 막히지 않게
    location / {
        proxy_pass http://127.0.0.1:8362;             # ← 이 숫자만 8361 ↔ 8362로 바뀌어요
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $forwarded_proto;   # 공용 Nginx가 준 https 정보를 그대로 전달
    }
}
```

- `X-Forwarded-Proto`를 그대로 넘겨야 Spring이 "원래 요청은 https였다"는 걸 알아요 (소셜 로그인 돌아오는 주소, 리다이렉트에 필요).
- 설정 폴더를 컨테이너의 `/etc/nginx/conf.d`에 **폴더째** 연결해요. nginx 이미지에 들어 있는 기본 `default.conf`(80번 포트)를 덮어써서, 서버의 공용 Nginx(80번)와 부딪히지 않게 하려는 거예요.

#### 처음 한 번만 짧게 끊겨요

v1.2.0으로 처음 배포할 때는 프록시가 아직 없어서, **예전 `ownlog` 컨테이너(8360을 직접 사용)를 지우고 그 자리에 프록시를 띄우는 1~2초** 동안만 끊겨요.
그다음 배포부터는 위 4단계로 끊기지 않아요.

### 4.4 DB 마이그레이션과 무중단 — "추가만 한다"

블루-그린에서는 **새 버전과 예전 버전이 잠깐 같은 DB를 함께 써요.** 새 버전이 뜨면서 Flyway가 DB를 먼저 바꾸는데,
그 순간 예전 버전(아직 서비스 중)이 바뀐 DB를 이해하지 못하면 에러가 나요.

| 마이그레이션 | 예전 버전이 버틸까? |
|---|---|
| 테이블·컬럼 **추가** (기본값 있음) | ✅ 예전 코드는 모르는 컬럼을 그냥 무시해요 |
| 컬럼 **삭제·이름 변경**, NOT NULL 추가(기본값 없이) | ❌ 예전 코드의 SQL이 깨져요 |

그래서 **Expand → Contract(확장 → 축소)** 두 단계로 나눠요.

1. **Expand** 배포: 새 컬럼을 추가하고, 새 코드는 새 컬럼을 써요. 예전 컬럼은 그대로 둬요.
2. 모든 서버가 새 버전이 된 걸 확인한 **다음 배포**에서 **Contract**: 더는 안 쓰는 예전 컬럼을 지워요.

v1.2.0의 `V6__social_login.sql`은 테이블 하나와 기본값 있는 컬럼 하나를 **추가만** 해서 이 규칙을 지켜요.

### 4.5 직접 재 본 결과 (내 컴퓨터에서, 2026-10-13)

`deploy.sh`를 그대로 쓰되 Mac에서 돌아가게 `NETWORK=bridge` 같은 시험용 값만 바꿔서, 요청을 쉬지 않고 보내면서(페이지·API 3종 반복) 배포했어요.

| 상황 | 보낸 요청 | 실패 |
|---|---|---|
| **블루-그린 배포 2번** (blue→green→blue) | 13,308 | **0** |
| 비교: 예전 방식처럼 서비스 중인 앱을 내렸다 다시 띄움 | 3,966 | **308** (약 6초 동안 502 + 직후 시간 초과 4건) |
| **새 버전이 못 뜨는 배포** (DB 비밀번호를 일부러 틀림) | 10,395 | **0** — 90초 뒤 "전환하지 않았어요"로 멈추고 blue가 계속 서비스 |

시험 방법 (다시 해 보고 싶을 때)

```bash
# 1) 내 컴퓨터용 값으로 deploy.sh 실행 (HOME을 바꿔서 서버 폴더 구조를 흉내 내요)
DOCKER_CONFIG=$HOME/.docker HOME=/tmp/zdhome NETWORK=bridge UPSTREAM_HOST=host.docker.internal \
SKIP_LOAD=1 KEEP_IMAGES=1 APP_PORT=8360 IMAGE_TAG=<이미지 태그> \
DB_ADDRESS=host.docker.internal DB_PORT=5432 DB_NAME=... DB_USERNAME=... DB_PASSWORD=... DB_SCHEMA=... JWT_SECRET=... \
bash deploy/deploy.sh

# 2) 다른 터미널에서 요청을 계속 보내며 실패 수를 세요
while true; do curl -s -o /dev/null -w '%{http_code}\n' localhost:8360/api/topics; done | grep -v 200
```

---

## 5. 서버에서 상태 보기·되돌리기

```bash
docker ps --format '{{.Names}}\t{{.Status}}' | grep ownlog   # ownlog-proxy + ownlog-blue(또는 green)
cat ~/ownlog-deploy/active-color                               # 지금 활성인 색
cat ~/ownlog-deploy/proxy/ownlog.conf                          # 프록시가 보내는 포트
docker logs --tail 50 ownlog-blue                              # 앱 로그
docker logs --tail 20 ownlog-proxy                             # 프록시 로그 (502가 나면 여기)
```

**예전 버전으로 되돌리기(롤백)** 는 GitHub Actions에서 해요. 되돌리고 싶은 커밋으로 main을 되돌리는 커밋(`git revert`)을 올리면,
그 커밋으로 다시 블루-그린 배포가 돌아요. 서버에서 손으로 컨테이너를 바꾸지 않는 이유는, **서버 상태와 main이 항상 같게** 유지하려는 거예요.

---

## 6. 자주 묻는 것

**Q. 배포 중에 로그인 상태가 풀리나요?**
아니요. 로그인은 JWT(브라우저가 들고 있는 토큰)라서 서버를 바꿔도 그대로예요. 두 색 모두 같은 `JWT_SECRET`을 써서 어느 쪽이든 토큰을 검증할 수 있어요.

**Q. 소셜 로그인 도중에 전환되면요?**
소셜 로그인은 "동의 화면 다녀오기" 몇 초 동안만 서버 메모리(세션)에 state 값을 기억해요. 바로 그 몇 초 사이에 전환되면 그 한 번은 "다시 시도해 주세요"가 떠요. 다시 누르면 돼요. (배포 빈도와 시간을 생각하면 드문 경우라 받아들였어요.)

**Q. 메모리는 괜찮나요?**
배포하는 1~2분 동안만 앱이 두 개(768MB × 2) 떠 있고, 끝나면 하나로 돌아와요.

**Q. 새 버전이 헬스 체크는 통과했는데 사실 버그가 있으면요?**
블루-그린은 "뜨는지"까지만 막아 줘요. 기능 버그는 CI 테스트(백엔드 180여 개)와 e2e 점검(`e2e/`)이 배포 전에 걸러요. 배포 뒤 문제가 보이면 5장의 롤백을 해요.
