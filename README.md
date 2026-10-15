# OwnLog (온로그)

> **"기록이 시간과 연결로 자라는 블로그"**
> 누구나 가입해서 자기 블로그를 운영하는 멀티유저 블로그 플랫폼(티스토리형)을 2주 동안 1인으로 기획·설계·구현·배포·운영했어요.

- 서비스: https://ownlog.java21.net
- 기간: 2026-10-01 ~ 2026-10-14 (2주) · 현재 버전 **v1.2.1** ([CHANGELOG](CHANGELOG.md))
- 스택: Java 21 · Spring Boot 4.1 · React 19 · PostgreSQL 17 · Docker · GitHub Actions

> 📊 의도 → 구현 → 측정 결과, AI에게 맡긴 것과 검증한 것은 **[프로젝트 보고서](https://github.com/own-log/ownlog-docs/blob/main/docs/프로젝트-보고서.md)** 에 있어요.

이 README는 **기능 목록 대신 "어떻게 만들었고, 무엇을 고민했고, 무엇이 터졌는지"** 를 보여 줘요.
각 결정은 **🤔 생각 → ✅ 결론 → 🛠 구현** 순서로 적었어요.

---

## 목차
1. [개발 프로세스](#1-개발-프로세스)
2. [문제 정의: 왜 또 블로그인가](#2-문제-정의-왜-또-블로그인가)
3. [핵심 의사결정](#3-핵심-의사결정-생각--결론--구현)
4. [트러블슈팅](#4-트러블슈팅)
5. [품질을 지키는 방법](#5-품질을-지키는-방법)
6. [구조](#6-구조)
7. [실행·테스트·배포](#7-실행테스트배포)
8. [다음 단계](#8-다음-단계)

---

## 1. 개발 프로세스

```
 기획 ──▶ 설계 ──▶ 구현 ──▶ 검증 ──▶ 배포 ──▶ 운영·개선
 요구사항   ERD·API   백엔드→    테스트·     CI/CD·      베타 테스트·
 명세       First     프론트     화면 점검   무중단      핫픽스·회고
```

| 단계 | 기간 | 한 일 | 산출물 |
|---|---|---|---|
| 1. 기획 | 10/01 | MVP 범위와 "하지 않을 것"을 먼저 정함. 팀 공통 요구사항과 비교해 빠진 것 보완 | 요구사항 명세서 (ID: `U-01`, `TC-03` …) |
| 2. 설계 | 10/06 | 테이블 11개 ERD, **API를 먼저 문서로** 확정(요청·응답·에러) 후 구현 | ERD, API 명세서 |
| 3. 백엔드 | 10/06~08 | REST API 38개, 통합 테스트 | v0.1.0 |
| 4. 프론트엔드 | 10/08 | 화면 15개, MVP 완성 | v0.2.0 |
| 5. 배포 | 10/12 | Docker 이미지 + GitHub Actions 자동 배포 | v0.3.0 |
| 6. 차별화 | 10/12 | 독창 기능 **기록 유니버스** 설계·구현 | v0.4.0 ~ v0.5.0 |
| 7. 점검 | 10/12 | 전체 코드 보안·성능 점검, 발견한 취약점 수정 | v0.6.0 → **v1.0.0** |
| 8. 운영 | 10/13~14 | 베타 테스터 피드백 → 핫픽스, 무중단 배포, 소셜 로그인, 문서 저장소 분리 | v1.0.1 ~ v1.2.1 |

**작업 방식**
- **Git Flow**: `feature/*` → `develop` → `release/*` → `main`(= 배포). 운영 버그는 `hotfix/*`. 모든 병합은 `--no-ff`로 이력을 남겨요.
- **SemVer + CHANGELOG**: 태그 `v0.1.0` ~ `v1.2.1`, 버전마다 사용자 관점 변경 기록.
- **완료 기준**: 기능 하나마다 테스트(정상·권한·경계값) → lint·build → 화면 점검 → 문서 동기화 → 트러블슈팅 기록.

---

## 2. 문제 정의: 왜 또 블로그인가

🤔 **생각**
기존 블로그는 글이 **최신순 목록으로 쌓이기만** 해요. 1년 전 글과 오늘 글이 어떻게 이어지는지, 내 기록이 어떻게 자라 왔는지 보이지 않아요. "멀티유저 블로그"라는 과제 조건만 채우면 다른 결과물과 구별되지 않아요.

✅ **결론**
기록에 **시간 축**과 **연결 축**을 더한다 → **기록 유니버스**
- 시간 축: 미래에 열리는 글(타임캡슐), 다른 사람에게 보내는 캡슐, 1년 잔디
- 연결 축: 글끼리 `[[링크]]`, 그 연결을 **별과 선으로 보여 주는 우주**, 첫 글부터 오늘까지 자라는 타임랩스

🛠 **구현에서 지킨 원칙**
새 기능이 **기존 보안 규칙을 깨지 않게** 하는 게 가장 어려웠어요. 봉인된 캡슐의 제목·태그가 링크·그래프·검색으로 새지 않도록 공개 판단을 한 곳에 모았어요([3-2](#3-2-비공개-글이-새는-경로를-구조로-막기)).

---

## 3. 핵심 의사결정 (생각 → 결론 → 구현)

### 3-1. 화면과 API를 하나의 Docker 이미지로
- 🤔 **생각:** 프론트(React)와 백엔드(Spring)를 따로 배포하면 서버·포트·CORS 설정이 두 배가 돼요. 수업 서버는 포트가 8360~8369로 제한돼 있어요.
- ✅ **결론:** React 빌드 결과를 Spring Boot가 함께 제공해서 **주소 하나, 이미지 하나, 포트 하나**로 배포해요.
- 🛠 **구현:** 멀티 스테이지 `Dockerfile`(Node 빌드 → Gradle 빌드 → JRE 실행). `/@아이디` 같은 화면 주소는 Spring이 `index.html`로 돌려줘요. 개발 중에는 Vite 프록시로 CORS 없이 동작해요.

### 3-2. 비공개 글이 새는 경로를 구조로 막기
- 🤔 **생각:** 기능이 늘 때마다(목록, 검색, 태그, 링크, 백링크, 그래프, 잔디) "비공개·봉인 캡슐·받는 사람 캡슐은 빼라"는 조건을 매번 다시 쓰면, **한 곳만 빠뜨려도** 유출이에요.
- ✅ **결론:** 공개 판단을 **딱 두 곳**에만 둬요. JPQL용 `PostCardQueries.PUBLIC_CONDITION`, 엔티티용 `Post.isViewableBy()`. 새 조회는 반드시 이걸 재사용해요.
- 🛠 **구현:** 타임캡슐을 추가할 때 이 조건 한 줄만 고쳐서 JPQL 6개가 수정 없이 따라왔어요(네이티브 SQL 1곳만 별도 수정). 목록·검색·링크·그래프별로 **누수 테스트**를 남겼어요.

### 3-3. 테스트는 진짜 PostgreSQL로
- 🤔 **생각:** H2 같은 메모리 DB는 빠르지만, 우리 코드는 부분 인덱스·`CHECK`·`ON CONFLICT`·네이티브 SQL을 써요. "테스트는 통과, 운영은 실패"가 생길 수 있어요.
- ✅ **결론:** **Testcontainers**로 테스트마다 실제 PostgreSQL 17을 띄워요.
- 🛠 **구현:** 테스트 193개. 빈 DB에 Flyway 마이그레이션을 처음부터 적용하므로, CI가 통과하면 "새 서버에도 스키마가 깨끗하게 만들어진다"까지 확인돼요. 비밀값이 필요 없어서 CI에 Secrets가 하나도 없어요.

### 3-4. 동시 요청은 DB가 책임지게
- 🤔 **생각:** 좋아요·조회수를 "읽고 → 1 더해서 → 저장"하면 동시 요청에서 값이 덮어써져요(Lost Update).
- ✅ **결론:** 애플리케이션이 계산하지 않고 **DB가 직접 더해요**(`UPDATE … SET like_count = like_count + 1`). 중복은 `UNIQUE` + `ON CONFLICT`로 막아요.
- 🛠 **구현:** 엔티티 필드는 `insertable/updatable = false`로 실수로 덮어쓰지 못하게 했어요. 동시 100명 테스트로 확인했어요.

### 3-5. 공용 Nginx를 건드리지 않는 무중단 배포
- 🤔 **생각:** 배포할 때마다 컨테이너를 내렸다 올려서 수십 초 동안 사이트가 죽었어요. 그런데 앞단 Nginx는 **강사님 서버라 설정을 바꿀 수 없어요.**
- ✅ **결론:** 공용 Nginx 뒤에 **우리 Nginx 컨테이너를 한 단 더** 두고 **블루-그린**으로 전환해요.
- 🛠 **구현:** `deploy/deploy.sh`
  ```
  쉬는 쪽(green)에 새 버전 → curl -fs 헬스 체크 → 프록시 설정만 바꾸고 nginx -s reload → 예전 버전 graceful shutdown
                                 └ 실패하면 전환하지 않음 (예전 버전이 계속 서비스)
  ```
  DB 마이그레이션은 배포 중 두 버전이 함께 쓰는 걸 전제로 **추가만**(expand-contract) 해요. 결과는 [트러블슈팅 16번](#4-트러블슈팅).

### 3-6. 소셜 로그인: 편의보다 계정 안전
- 🤔 **생각:** "같은 이메일이면 기존 계정에 자동 연결"이 편하지만, 이메일 확인이 느슨한 서비스가 하나라도 있으면 **남의 계정에 들어갈 수 있어요**(계정 탈취).
- ✅ **결론:** 자동 연결하지 않고 안내만 해요. 로그인 뒤 JWT를 주소창에 싣지 않고 **60초짜리 1회용 코드**로 교환해요.
- 🛠 **구현:** 카카오·네이버·구글·깃허브 4종. 키를 등록한 서비스만 버튼이 보여서 **키 없이 배포해도 안전**해요. 위조된 state, 재사용된 코드는 테스트로 막았어요.

### 3-7. "관련 있는 글"의 기준을 숫자로
- 🤔 **생각:** 처음에는 "태그를 하나라도 같이 쓰면 연결"이었는데, "여행" 같은 흔한 태그 하나로 우주가 거미줄이 됐어요. 연결이 많은 것과 의미 있는 것은 달라요.
- ✅ **결론:** 검색 엔진의 기본 아이디어인 **TF-IDF**로 제목·요약·태그의 유사도를 재고, 기준값(0.3) 이상 + 별마다 상위 3개만 이어요.
- 🛠 **구현:** 흔한 단어·태그는 무게를 낮추고, 선에 "왜 이어졌는지"(겹친 태그·단어)를 함께 보여 줘요. 비공개 글은 계산에도 넣지 않아요. 전체 우주의 선이 36개 → 2개로 줄었어요.

### 3-8. 코드와 문서의 저장소 분리
- 🤔 **생각:** 문서만 고쳤는데 운영 배포가 돌았어요. 문서도 코드와 같은 릴리스 흐름에 묶여서, 급히 고치려면 배포를 감수해야 했어요.
- ✅ **결론:** 바뀌는 주기가 다른 것(코드: 릴리스 단위, 문서: 수시로)은 **저장소를 나눠** 규칙이 아니라 구조로 해결해요.
- 🛠 **구현:** 설계·운영 문서를 [own-log/ownlog-docs](https://github.com/own-log/ownlog-docs)로 이력째 옮기고, 배포 제외 규칙에 `e2e/**`를 추가했어요.

---

## 4. 트러블슈팅

실제로 겪은 문제 **20건**을 **상황 → 원인 분석 → 해결 → 결과 → 배운 점**으로 기록했어요. 전체는 [트러블슈팅 문서](https://github.com/own-log/ownlog-docs/blob/main/docs/트러블슈팅.md)에 있어요.

### 대표 사례 5가지

<details>
<summary><b>🔐 #2 인코딩한 주소(<code>/%61pi/…</code>)로 로그인 없이 API 호출</b></summary>

- **상황:** 운영 서버에서 `/%61pi/images`(`a` = `%61`)로 요청하면 로그인 검사를 건너뛰었어요.
- **원인:** 인가 규칙은 **디코딩 전** 주소, Spring MVC는 **디코딩 후** 주소로 판단했어요. 보안 쪽에서는 `/api/`로 시작하지 않는 것처럼 보였어요.
- **해결:** 직접 문자열을 비교하지 않고, MVC와 같은 방식으로 경로를 해석하는 Spring Security 경로 매처로 통일했어요.
- **결과:** 운영에서 상태를 바꾸지 않는 GET으로 재현 → 수정 후 401 확인, 회귀 테스트 추가.
- **배운 점:** 보안 검사와 실제 처리가 **같은 해석**을 써야 해요.
</details>

<details>
<summary><b>🧩 #7 기능이 늘수록 비공개 글이 새는 경로가 늘어남</b></summary>

- **상황:** 타임캡슐·링크·그래프를 추가할수록 "비공개는 빼라"는 조건을 쓸 곳이 늘었어요.
- **원인:** 공개 조건이 조회마다 흩어져 있었어요.
- **해결:** 공개 판단을 JPQL 상수 하나와 엔티티 메서드 하나로 모았어요 ([3-2](#3-2-비공개-글이-새는-경로를-구조로-막기)).
- **결과:** 새 조건(봉인 캡슐)을 추가할 때 JPQL 6개는 수정 없이 반영됐어요. 경로별 누수 테스트를 남겼어요.
- **배운 점:** 빠뜨리면 사고가 나는 규칙은 **기억이 아니라 구조**로 지켜요.
</details>

<details>
<summary><b>🧪 #10 "두 번 클릭 방지"를 넣었는데 여전히 댓글이 두 번 등록됨</b></summary>

- **상황:** 버튼을 빠르게 두 번 누르면 댓글이 두 개 생겼어요. 방지 코드를 넣었는데도요.
- **원인:** `submitting` 상태(React state)는 **다음 렌더링**에 바뀌어요. 두 번째 클릭이 들어올 때는 아직 `false`였어요.
- **해결:** 즉시 바뀌는 `useRef`로 확인하도록 바꿨어요.
- **결과:** 제가 처음 넣은 수정이 틀렸다는 걸 **화면 점검(e2e)이 잡아냈어요.**
- **배운 점:** "고쳤다"는 확신보다 **재현하는 테스트**가 먼저예요.
</details>

<details>
<summary><b>🌌 #13 운영에서 타임랩스가 동작하지 않는 것처럼 보임 (→ #14 수정이 만든 버그)</b></summary>

- **상황:** 베타 테스터 피드백: ▶를 누르면 별이 거의 다 나타나고 8초 동안 변화가 없어요. 로컬에서는 멀쩡했어요.
- **원인:** 시간 비례로 재생했는데, 운영 데이터는 글이 **몇 분 안에 몰려** 있었어요. 테스트 데이터는 날짜가 고르게 퍼져 있었어요.
- **해결:** 시간 대신 **쓴 순서**로 하나씩 재생하도록 바꿔 v1.0.1 핫픽스.
- **이어진 문제 #14:** 고친 직후 가끔 오류 화면이 떴어요. `requestAnimationFrame` 시각이 시작 시각보다 **이를 수 있어** 진행률이 음수가 됐어요 → 0~1로 제한.
- **배운 점:** 테스트 데이터는 **운영 데이터의 모양**을 닮아야 해요. 가끔 실패하는 테스트는 "불안정"으로 넘기지 말고 실패한 순간을 봐요.
</details>

<details>
<summary><b>🚀 #16 배포할 때마다 사이트가 수십 초 죽음</b></summary>

- **상황:** 하루에도 여러 번 배포하는데, 그때마다 접속이 실패했어요. 새 버전이 못 뜨면 계속 죽어 있었어요.
- **원인:** 새 버전이 준비됐는지 확인하기 **전에** 예전 버전을 껐어요.
- **해결:** 블루-그린 + 헬스 체크(`curl -fs`) + Nginx reload + Spring graceful shutdown ([3-5](#3-5-공용-nginx를-건드리지-않는-무중단-배포)).
- **결과 (로컬, 같은 스크립트, 요청을 쉬지 않고 보냄):**

  | 상황 | 요청 | 실패 |
  |---|---|---|
  | 블루-그린 배포 2회 | 13,308 | **0** |
  | 새 버전이 못 뜨는 배포 | 10,395 | **0** (전환하지 않음) |
  | 예전 방식(끄고 켜기) | 3,966 | 308 |
- **배운 점:** 무중단은 "빨리 바꾸기"가 아니라 **"정상이 확인된 다음에만 바꾸기"** 예요.
</details>

### 전체 목록

| # | 분야 | 문제 | 핵심 원인 → 해결 |
|---|---|---|---|
| 1 | 배포 | 컨테이너가 학교 내부망 DB에 연결 안 됨 | Docker 기본 네트워크 경로 막힘 → 스스로 원인을 찾는 진단 추가, `--network host` |
| 2 | 보안 | `/%61pi/…`로 인증 우회 | 디코딩 전/후 주소 판단 불일치 → 경로 매처 통일 |
| 3 | 보안 | 프로필 사진 주소로 남의 파일 삭제 | 파일 소유 확인 없음 → 다른 곳에서 쓰는지 확인 후 삭제 |
| 4 | 보안 | 로그인 후 외부 사이트로 이동 | 문자열 검사의 한계 → URL로 해석해 출처 비교 |
| 5 | 백엔드 | 한글 비밀번호 가입이 500 | BCrypt 72바이트 제한 → 바이트 길이 검증 |
| 6 | 동시성 | 좋아요·조회수 Lost Update 위험 *(설계 단계에서 차단)* | 읽고 쓰기 → DB가 직접 더하기 |
| 7 | 설계 | 비공개 글이 새는 경로 증가 | 조건 분산 → 두 곳으로 모음 |
| 8 | 데이터 | 요약에 `[[17\|제목]]` 문법 노출 | 이미 저장된 데이터 → 규칙 수정 + 데이터 마이그레이션 |
| 9 | 프론트 | 토큰 만료 시 공개 화면에서도 튕김 | 전역 401 처리 범위 → 요청별 예외 옵션 |
| 10 | 프론트 | 두 번 클릭 방지 실패 | state 반영 시점 → ref |
| 11 | 성능 | 타임랩스 중 페이지 전체가 초당 60번 렌더링 | 애니메이션 값을 state로 → 캔버스에 직접 전달 |
| 12 | 테스트 | 화면 점검 결과가 매번 달라짐 | 브라우저 프로필 공유 → 실행마다 새 프로필 + 별도 스키마 |
| 13 | 프론트 | 운영에서 타임랩스가 멈춘 것처럼 보임 | 운영 데이터 분포 → 순서 기준 재생 |
| 14 | 프론트 | 13번 수정 후 가끔 오류 화면 | 음수 진행률 → 0~1 제한 |
| 15 | 테스트 | 화면 점검이 30분 멈춤 | 기다림에 제한 없음 → 명령 30초·점검 4분 제한 |
| 16 | 배포 | 배포마다 수십 초 장애 | 먼저 끄고 띄움 → 블루-그린 |
| 17 | 백엔드 | 소셜 로그인 추가 후 서버가 안 뜸 | 빈 순환 참조 → `static` 빈 |
| 18 | 설계 | 우주의 선이 거미줄처럼 엉킴 | 태그 1개 공유 기준 → TF-IDF 유사도 |
| 19 | 테스트 | 배포 스크립트 로컬 시험 중 다른 이미지 삭제 | 운영용 정리 단계 → 시험용 스위치 |
| 20 | 배포 | 문서만 고쳤는데 운영 배포가 돎 | `paths-ignore`는 하나만 섞여도 무력 → `e2e/**` 제외 + 문서 저장소 분리 |

### 문제를 대하는 방식
1. **재현부터 해요.** 운영 보안 문제도 상태를 바꾸지 않는 요청으로 먼저 재현했어요.
2. **추측 대신 진단을 자동화해요.** 같은 실패가 반복되면 값을 바꾸지 않고 확인 단계를 넣었어요(#1).
3. **위험한 규칙은 한 곳에 모아요.** 공개 조건(#7), 파일 삭제 판단(#3).
4. **고친 것마다 회귀 테스트를 남겨요.** 그 테스트가 제 수정의 오류를 잡기도 했어요(#10, #14).
5. **내 실수도 기록해요.** #14, #19는 제가 만든 문제예요.

---

## 5. 품질을 지키는 방법

| 장치 | 내용 |
|---|---|
| 백엔드 통합 테스트 **193개** | 정상 + **권한(남의 것·비공개·비로그인)** + 경계값 + 동시성 + 롤백 + 보안 회귀. 실제 PostgreSQL(Testcontainers) |
| 화면 점검 **125개** | 헤드리스 Chrome으로 가입·글쓰기·캡슐·우주·탈퇴를 실제로 눌러 봐요 (`e2e/`). 시연 DB를 더럽히지 않게 별도 스키마에서 실행 |
| CI (`ci.yml`) | 작업 브랜치·`develop` 푸시마다 테스트 + lint + build. 배포는 하지 않아요 |
| CD (`deploy.yml`) | `main` 푸시 → 테스트 통과 시에만 배포. 문서·화면 점검만 바뀌면 배포하지 않아요 |
| 보안 기본값 | 마크다운 sanitize(XSS), 존재 여부를 숨기는 404, 탈퇴 회원 토큰 거부, 오픈 리다이렉트 방지 |
| 문서 동기화 | 요구사항 ID ↔ API 명세 ↔ 테스트 이름을 같은 ID로 연결 |

---

## 6. 구조

```
브라우저 ──https──▶ 공용 Nginx (수업 서버, HTTPS 종료)
                        │ http + X-Forwarded-Proto
                        ▼
                  ownlog-proxy (우리 Nginx) :8360
                        │ 활성인 쪽으로만
              ┌─────────┴─────────┐
       ownlog-blue :8361    ownlog-green :8362      ← Spring Boot (화면 + API)
              └─────────┬─────────┘
                        ▼
                  PostgreSQL 17 (Flyway V1~V6)
```

| 영역 | 기술 | 고른 이유 |
|---|---|---|
| 백엔드 | Java 21, Spring Boot 4.1, Security 7(+ OAuth2 Client), JPA, Flyway, jjwt | 검증된 표준 스택, 스키마는 Flyway로만 변경(`ddl-auto: validate`) |
| 프론트 | React 19, Vite 8, React Router, Tailwind 4, react-markdown + rehype-sanitize, d3-force | 우주 시각화는 d3-force + Canvas(별 수백 개에도 가볍게) |
| 테스트 | JUnit 5, MockMvc, Testcontainers, CDP 기반 화면 점검 | 운영과 같은 DB로 테스트 |
| 배포 | Docker, GitHub Actions, Nginx | 이미지 하나 + 블루-그린 |

<details>
<summary>폴더 구조</summary>

```
├── backend/src/main/java/com/ownlog/   auth, user, blog, category, post, tag, topic, comment,
│                                       reaction, report, image, link, universe, global
├── backend/src/main/resources/db/migration/   V1 테이블 ~ V6 소셜 로그인 (Flyway, 추가만)
├── frontend/src/                       api, auth, components, hooks, pages, utils
├── e2e/                                화면 점검 125개
├── scripts/seed-demo.mjs               시연 데이터
├── deploy/deploy.sh                    블루-그린 배포 스크립트
├── .github/workflows/                  ci.yml(테스트), deploy.yml(배포)
├── Dockerfile                          화면 + API 이미지
└── docker-compose.yml                  로컬 PostgreSQL
```
</details>

---

## 7. 실행·테스트·배포

<details>
<summary><b>로컬에서 실행하기</b></summary>

준비물: **Java 21, Node.js 24, Docker**

```bash
cp .env.example .env                 # DB_PASSWORD, JWT_SECRET 설정 (JWT_SECRET: openssl rand -base64 48)
docker compose up -d                 # PostgreSQL
cd backend && ./gradlew bootRun      # http://localhost:8080 (처음 실행하면 Flyway가 테이블 생성)
cd frontend && npm install && npm run dev   # http://localhost:5173 (/api, /uploads → 8080 프록시)
```

시연 데이터: `LOCAL_SQL=1 node scripts/seed-demo.mjs`
회원 5명(`codewave`, `tripnote`, `cookbook`, `bookworm`, `runner` / 이메일 `아이디@demo.ownlog` / 비밀번호 `ownlog1234`)과 글 33개(타임캡슐, 받는 사람 캡슐, `[[링크]]`, 비공개 글 포함)가 들어가요.
</details>

<details>
<summary><b>테스트</b></summary>

```bash
cd backend && ./gradlew test                 # 193개 (Docker 필요)
cd frontend && npm run lint && npm run build
node e2e/run-all.mjs                         # 화면 점검 125개 (Chrome 필요)
```
화면 점검은 실제로 가입·글쓰기·탈퇴를 해요. 시연 DB를 깨끗하게 두려면 [e2e/README.md](e2e/README.md)의 "별도 스키마에서 돌리기"를 써요.
</details>

<details>
<summary><b>브랜치·버전·배포</b></summary>

```
main      ●───────────────●─────────── 운영(배포), 태그 vX.Y.Z
           \             / \
release     \     ●─────●   \           버전 올리기, CHANGELOG 정리
             \   /           \
develop   ●───●─●───●─────────●─────── 다음 릴리스 (CI 테스트)
               \   /
feature         ●─●                    feature/*, fix/*, chore/* …
```

- `develop`·작업 브랜치 푸시 → `ci.yml`(테스트만) / `main` 병합 → `deploy.yml`(테스트 → 이미지 → 서버 → `deploy.sh`)
- 커밋은 [Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/), 규칙 전체는 [CLAUDE.md 11장](CLAUDE.md#11-git-브랜치와-버전-git-flow)
- 업로드 이미지는 서버 `~/ownlog-uploads`에 보관(재배포해도 유지), 컨테이너는 `--network host`(내부망 DB 접근, 트러블슈팅 #1)

**GitHub Repository secrets** (값은 문서에 적지 않아요)

| 이름 | 내용 |
|---|---|
| `DB_ADDRESS`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SCHEMA` | 수업 DB |
| `SSH_ADDRESS`, `SSH_ID`, `SSH_PASSWORD`, `SSH_PORT` | 배포 서버 |
| `JWT_SECRET` (선택) | 없으면 서버에서 한 번 만들어 계속 사용 |
| `OAUTH_{KAKAO,NAVER,GOOGLE,GITHUB}_CLIENT_{ID,SECRET}` (선택) | 소셜 로그인 ([발급 방법](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/소셜-로그인.md#5-키-발급하기-직접-해야-하는-일)) |
</details>

### 문서 ([own-log/ownlog-docs](https://github.com/own-log/ownlog-docs))
- 설계: [요구사항](https://github.com/own-log/ownlog-docs/blob/main/요구사항.md) · [ERD](https://github.com/own-log/ownlog-docs/blob/main/docs/ERD.md) · [API 명세](https://github.com/own-log/ownlog-docs/blob/main/docs/API명세서.md) · [기록 유니버스 설계](https://github.com/own-log/ownlog-docs/blob/main/docs/기록유니버스-설계.md)
- 보고서: [프로젝트 보고서](https://github.com/own-log/ownlog-docs/blob/main/docs/프로젝트-보고서.md)
- 운영: [트러블슈팅](https://github.com/own-log/ownlog-docs/blob/main/docs/트러블슈팅.md) · [전체 코드 점검 보고서](https://github.com/own-log/ownlog-docs/blob/main/docs/점검-보고서.md) · [발표 리허설](https://github.com/own-log/ownlog-docs/blob/main/docs/발표-리허설.md)
- [개념 가이드](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/README.md): [개발환경](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/개발환경.md) · [Git·버전관리](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/git-브랜치와-버전관리.md) · [CI](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/CI-테스트-자동화.md) · [배포·무중단](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/배포와-CI-CD.md) · [파일 업로드](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/파일-업로드.md) · [소셜 로그인](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/소셜-로그인.md) · [우주 연관도](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/기록-우주-연관도.md) · [GitHub Organization·README](https://github.com/own-log/ownlog-docs/blob/main/docs/guide/github-organization과-readme.md)
- 회고: [포트폴리오 초안](https://github.com/own-log/ownlog-docs/blob/main/docs/포트폴리오.md) · [기술 면접 예상 질문 50](https://github.com/own-log/ownlog-docs/blob/main/docs/면접-예상질문.md)

---

## 8. 다음 단계

지금 구조에서 바로 이어지는 순서로 정했어요.

| 순서 | 무엇을 | 왜 / 지금 준비된 것 |
|---|---|---|
| 1 | 🔔 알림 (캡슐이 열림, 내 글이 연결됨, 댓글) | 받는 사람 캡슐·`post_links`·댓글 구조가 이미 있음 |
| 2 | 🛡️ 관리자 (신고 처리, 회원 정지) | 신고 테이블·중복 신고 방지·`users.role` |
| 3 | 🔐 인증 강화 (Refresh Token, 로그인 시도 제한, 소셜 계정 연결) | JWT 필터, 탈퇴 회원 토큰 거부 |
| 4 | 🖼️ 이미지 정리 (리사이즈, 글 삭제 시 파일 정리) | 파일 삭제 이벤트와 사용 여부 확인 로직 |
| 5 | 💾 운영 (백업, 모니터링, 배포 동시 실행 잠금) | Docker 배포, 헬스 체크 |
