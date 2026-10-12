# OwnLog (온로그)

> "나만의(Own) 기록(Log)". 누구나 가입해서 자기 블로그를 운영하는 **멀티유저 블로그 플랫폼** (티스토리형)

- 서비스 주소: https://ownlog.java21.net
- 개발 기간: 2026-10-01 ~ 2026-10-14 (2주, MVP)

## ✨ 독창 기능: 기록 유니버스

> **"기록이 시간과 연결로 자라는 블로그"**. 글이 목록으로 쌓이기만 하는 대신, 시간 축과 연결 축으로 이어져요. ([설계서](docs/기록유니버스-설계.md))

| 기능 | 내용 |
|---|---|
| ⏳ **타임캡슐** | 정한 시각(1분 ~ 10년 뒤)에 열리는 글. 봉인 중에는 잠금 카드와 카운트다운만 보이고, 0이 되면 새로고침 없이 열려요 |
| 🔗 **`[[글]]` 연결** | 본문에서 `[[`를 입력하면 글 자동완성. 상세 하단에 "이 글을 언급한 글"(백링크). 볼 수 없는 글은 제목도 숨겨요 |
| 🌌 **기록 우주** | `/@아이디/universe`: 글은 별, 링크와 같은 태그는 선. 끌고, 확대하고, 별을 누르면 글로 가요 |
| 🌱 **기록 잔디** | 블로그 홈에 1년 잔디와 🔥 연속 기록. 칸을 누르면 그날의 기록 |
| 💌 **받는 사람 타임캡슐** | 다른 회원에게 보내는 캡슐. 둘만 볼 수 있고, 열리면 받는 사람만 읽어요. "💌 받은 캡슐"에서 모아 봐요 |
| 🎞️ **우주 타임랩스** | ▶를 누르면 첫 기록부터 오늘까지 별이 태어나며 우주가 자라나요 |
| 🌌 **OwnLog 우주** | `/universe`: 모든 블로그의 공개 글이 블로그별 은하로 모이고, 블로그 사이 링크가 다리처럼 이어져요 |
| 🔍 **링크 미리보기** | 본문의 🔗 링크에 마우스를 올리면 그 글의 요약 카드 |

## 주요 기능

| 구분 | 기능 |
|---|---|
| 회원 | 회원가입(아이디 실시간 중복 확인), 로그인(JWT), 프로필·비밀번호 변경, 탈퇴 |
| 블로그 | 가입하면 내 블로그(`/@아이디`) 자동 생성, 블로그 이름·소개, 카테고리 관리(순서·공개 범위) |
| 글 | 마크다운 에디터, 이미지·썸네일 업로드, 태그, 토픽, 공개/비공개, 수정·삭제 |
| 임시저장 | 새 글을 수동으로 임시저장하고 목록에서 이어 쓰기 |
| 탐색 | 메인 최신·인기(최근 7일 좋아요) 탭, 토픽 필터, 검색, 태그별 글, 페이지 번호 |
| 소통 | 댓글·답글, 좋아요, 북마크, 글·댓글 신고 |

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway, JWT(jjwt) |
| 프론트엔드 | React 19, Vite 8, React Router, Tailwind CSS 4, axios, react-markdown(+ rehype-sanitize), d3-force(기록 우주) |
| 데이터베이스 | PostgreSQL 17 |
| 테스트 | JUnit 5, MockMvc, Testcontainers(실제 PostgreSQL), 헤드리스 Chrome 화면 확인 |
| 배포 | Docker, GitHub Actions, Nginx (수업 공용 서버) |

## 폴더 구조

```
├── backend/                  Spring Boot REST API
│   └── src/main/
│       ├── java/com/ownlog/  auth, user, blog, category, post, tag, topic,
│       │                     comment, reaction(좋아요·북마크), report, image,
│       │                     link([[글]] 연결), universe(기록 우주·잔디), global
│       └── resources/
│           ├── application.yaml        로컬 설정 (.env 읽음)
│           ├── application-prod.yaml   배포 설정
│           └── db/migration/     V1 테이블 생성, V2 타임캡슐, V3 글 연결, V4 받는 사람 캡슐, V5 요약 정리 (Flyway)
├── frontend/                 React SPA
│   └── src/                  api, auth, components, hooks, pages, utils
├── deploy/deploy.sh          서버에서 컨테이너를 실행하는 스크립트
├── .github/workflows/        자동 배포 (GitHub Actions)
├── Dockerfile                화면 + API를 이미지 하나로 빌드
├── docker-compose.yml        로컬 개발용 PostgreSQL
├── 요구사항.md               요구사항 명세서
└── docs/                     ERD.md, API명세서.md, 기록유니버스-설계.md
```

## 문서

- [요구사항 명세서](요구사항.md)
- [ERD](docs/ERD.md)
- [API 명세서](docs/API명세서.md)

## 로컬에서 실행하기

준비물: **Java 21, Node.js 24, Docker**

```bash
# 1. 환경 변수 파일 만들기 (DB_PASSWORD, JWT_SECRET 값을 바꿔 주세요)
cp .env.example .env
#    JWT_SECRET은 이 명령으로 만들 수 있어요: openssl rand -base64 48

# 2. PostgreSQL 실행
docker compose up -d

# 3. 백엔드 실행 (http://localhost:8080, 처음 실행하면 Flyway가 테이블을 만들어요)
cd backend && ./gradlew bootRun

# 4. 프론트엔드 실행 (새 터미널, http://localhost:5173)
cd frontend && npm install && npm run dev
```

개발 중에는 Vite가 `/api`, `/uploads` 요청을 8080으로 넘겨 줘서 CORS 설정 없이 동작해요.

### 시연용 데이터

```bash
LOCAL_SQL=1 node scripts/seed-demo.mjs        # 로컬 (기록 잔디용 예전 날짜까지 채워요)
BASE=https://ownlog.java21.net node scripts/seed-demo.mjs   # 서버 (API로만 넣어요)
```
회원 5명(`codewave`, `tripnote`, `cookbook`, `bookworm`, `runner`, 이메일은 `아이디@demo.ownlog`, 비밀번호 `ownlog1234`)과 글 33개(타임캡슐 4개, 받는 사람 캡슐 3개, `[[링크]]`, 비공개 글·카테고리), 임시저장, 댓글·답글·삭제된 댓글, 좋아요, 북마크, 신고가 들어가요. "3분 뒤에 열리는 기록"은 넣고 3분 뒤에 열려요.

### 테스트

```bash
cd backend && ./gradlew test          # 통합·동시성·롤백 테스트 (Docker 필요)
cd frontend && npm run lint && npm run build
```

## 배포

`main` 브랜치에 푸시하면 GitHub Actions가 자동으로 배포해요.

```
push → 테스트(백엔드 테스트, 프론트 린트·빌드)
     → Docker 이미지 빌드 → 서버로 전송(scp)
     → deploy/deploy.sh 실행(SSH): DB 연결 확인 → 컨테이너 교체 → 응답 확인
     → Nginx(ownlog.java21.net) → 앱(8360)
```

- **이미지 하나로 배포해요.** Spring Boot가 React 빌드 결과를 함께 서빙해서 화면과 API의 주소가 같아요.
- **테이블은 Flyway가 만들어요.** 배포 DB의 스키마는 비워 두면 돼요.
- **업로드한 이미지는 서버의 `~/ownlog-uploads`에 보관해요.** 새로 배포해도 남아요.
- 컨테이너는 `--network host`로 실행해요. 이 서버에서는 Docker 기본 네트워크로 내부망 DB에 접속할 수 없기 때문이에요.

### GitHub Repository secrets

| 이름 | 내용 |
|---|---|
| `DB_ADDRESS`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_SCHEMA` | 수업 DB(crowfoot) 접속 정보 |
| `SSH_ADDRESS`, `SSH_ID`, `SSH_PASSWORD`, `SSH_PORT` | 배포 서버 접속 정보 |
| `JWT_SECRET` (선택) | 없으면 서버에서 한 번 만들어 계속 사용해요 |
