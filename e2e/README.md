# 화면 점검 (E2E)

헤드리스 Chrome으로 실제 화면을 눌러 보며 기능을 확인해요. **113개 항목**, 한 번 도는 데 약 8분 걸려요.
Playwright 같은 추가 설치 없이 Chrome DevTools Protocol(`cdp.mjs`)로 동작해요. Node.js 24, Chrome이 필요해요.

| 파일 | 확인하는 것 |
|---|---|
| `check-auth` | 회원가입(아이디 실시간 확인), 로그인 유지, 로그아웃, 로그인 후 돌아가기, 외부 주소 차단, 404, 휴대폰 헤더 |
| `check-main` | 메인 카드·페이지 번호, 인기 탭, 토픽, 새로고침 유지, 휴대폰 1열·메뉴 |
| `check-read` | 블로그 홈, 글 상세, 댓글·답글, 좋아요·북마크·신고, 검색, 태그 |
| `check-write` | 글쓰기 로그인 보호, 임시저장, 태그, 이미지·썸네일 업로드, 발행, 수정 |
| `check-settings` | 프로필·블로그 설정, 비밀번호 변경, 카테고리 관리, 북마크, 탈퇴 |
| `check-capsule` | ⏳ 타임캡슐 봉인·잠금 카드·**카운트다운 0이면 자동 개봉**·최근 열린 캡슐 |
| `check-links` | 🔗 `[[` 자동완성, 링크·잠금 표시, 백링크 |
| `check-universe` | 🌌 블로그 기록 우주, 별 찾기·클릭 |
| `check-activity` | 🌱 기록 잔디, 그날의 기록 |
| `check-expired` | 만료·잘못된 토큰으로 접속해도 공개 화면에서 튕기지 않음 |
| `check-extra` | 🎞️ 타임랩스, 🌌 서비스 전체 우주, 🔍 링크 미리보기, 💌 받는 사람 캡슐 |
| `check-audit` | 전체 점검에서 고친 문제: 인코딩 주소 인증 우회, 외부 주소 리다이렉트, 글쓰기 화면 상태, 임시저장 덮어쓰기, 댓글 두 번 등록 |

## 발표 리허설

```bash
BASE=https://ownlog.java21.net node e2e/rehearsal.mjs
```
발표 동선([docs/발표-리허설.md](../docs/발표-리허설.md))을 실제 서버에서 순서대로 따라가며 단계마다 걸린 시간과 스크린샷(`screenshots/rehearsal-*.png`)을 남겨요. 글을 쓰거나 지우지 않아요.

## 실행

```bash
node e2e/run-all.mjs                 # 개발 서버(http://localhost:5173) 대상, 전체
node e2e/run-all.mjs links extra     # 일부만
```

- 처음에 `setup.mjs`가 점검용 회원(`qa45650`, `ui792202`), 글, 시연 회원 5명을 **없을 때만** 만들어요.
- 점검하면서 회원 가입·글 작성·탈퇴를 실제로 해서 **DB에 테스트 데이터가 남아요.** 시연 데이터만 남겨야 하는 DB라면 아래처럼 별도 스키마에서 돌려요.
- `check-capsule`은 개봉 시각을 당기려고 로컬 Docker PostgreSQL(`ownlog-postgres`)에 SQL을 실행해요.
- 결과는 ✅/❌로 나오고, 하나라도 실패하면 종료 코드 1이에요. 스크린샷은 `e2e/screenshots/`에 남아요 (Git에는 올리지 않아요).

### 시연 DB를 건드리지 않고 돌리기 (배포 이미지 + 별도 스키마)

배포와 같은 Docker 이미지를 `e2e` 스키마로 띄워서 점검해요. 화면과 API가 한 주소(8301)라서 실제 서버와 같은 조건이에요.

```bash
docker build -t ownlog:e2e .
docker exec ownlog-postgres psql -U ownlog -d ownlog -c "DROP SCHEMA IF EXISTS e2e CASCADE; CREATE SCHEMA e2e;"
set -a; . ./.env; set +a
docker run -d --name ownlog-e2e -p 8301:8080 \
  -e DB_ADDRESS=host.docker.internal -e DB_PORT=5432 -e DB_NAME=${DB_NAME:-ownlog} \
  -e DB_USERNAME=${DB_USERNAME:-ownlog} -e DB_PASSWORD="$DB_PASSWORD" -e DB_SCHEMA=e2e \
  -e JWT_SECRET="$(openssl rand -base64 48)" ownlog:e2e

BASE=http://localhost:8301 E2E_DB_SCHEMA=e2e node e2e/run-all.mjs

# 정리
docker rm -f ownlog-e2e && docker exec ownlog-postgres psql -U ownlog -d ownlog -c "DROP SCHEMA e2e CASCADE"
```

## 환경 변수
| 이름 | 기본값 | 설명 |
|---|---|---|
| `BASE` | `http://localhost:5173` | 점검할 화면 주소 (API는 `BASE/api`) |
| `CHROME_PATH` | 운영체제별 기본 설치 경로 | Chrome 실행 파일 |
| `E2E_DB_SCHEMA` | (없음 = public) | SQL을 실행할 스키마 |
| `E2E_DB_CONTAINER` / `E2E_DB_USER` / `E2E_DB_NAME` | `ownlog-postgres` / `ownlog` / `ownlog` | 로컬 PostgreSQL 컨테이너 정보 |
| `E2E_NO_SQL` | (없음) | 값이 있으면 시연 데이터 날짜 옮기기를 하지 않아요 (원격 서버 대상) |
