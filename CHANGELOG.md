# 변경 기록 (Changelog)

OwnLog의 버전별 변경 사항이에요. 형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르고, 버전은 [유의적 버전(SemVer)](https://semver.org/lang/ko/)을 써요.

- **MAJOR** (1.0.0 → 2.0.0): API 응답 형식처럼 기존 사용 방식이 깨지는 변경
- **MINOR** (1.0.0 → 1.1.0): 기존을 깨지 않는 새 기능
- **PATCH** (1.0.0 → 1.0.1): 버그 수정
- 분류: `추가`(Added) · `변경`(Changed) · `수정`(Fixed) · `보안`(Security) · `삭제`(Removed)

## [Unreleased]

## [1.0.1] - 2026-10-13

### 수정
- 기록 우주 타임랩스가 운영 서버에서 사실상 동작하지 않던 문제: 글이 짧은 시간에 몰려 있으면 ▶를 누르자마자 별이 거의 다 나타나고 나머지 8초는 변화가 없었어요. 시간 비례 대신 **쓴 순서대로 하나씩** 태어나게 바꿨어요 (트러블슈팅 13번)

## [1.0.0] - 2026-10-12

> 첫 정식 버전 (발표·제출 버전). 0.x에서 만든 MVP와 기록 유니버스, 보안 점검 결과를 모두 담았어요.

### 추가
- Git Flow 브랜치 전략(`main`·`develop`·`feature/*`·`release/*`·`hotfix/*`)과 버전 관리 규칙 (CLAUDE.md 11장)
- 이 변경 기록(`CHANGELOG.md`), PR 템플릿, 작업 브랜치용 CI(`ci.yml`: 테스트만, 배포는 `main`만)
- 지난 이력에 버전 태그 `v0.1.0` ~ `v0.6.0`
- 버전 표시: 백엔드 `0.0.1-SNAPSHOT`, 프론트 `0.0.0` → `1.0.0`

## [0.6.0] - 2026-10-12

### 보안
- 인코딩한 주소(`/%61pi/...`)로 로그인 검사를 건너뛰던 문제 수정 (운영에서 재현 후 수정)
- 프로필 사진 주소로 남의 업로드 파일을 지울 수 있던 문제 수정
- 로그인 후 외부 주소로 보내지던 오픈 리다이렉트 수정
- 태그 존재 노출, 받는 사람에게 비공개 카테고리 이름 노출, `http://` 썸네일 수정

### 수정
- 72바이트 넘는 한글 비밀번호 500 → 400
- 비공개가 된 글의 좋아요·북마크 취소, 삭제 중 동시 요청 FK 위반 → 404
- 글쓰기: 글 전환 시 이전 상태 남음, 첫 임시저장 뒤 덮어쓰기, 업로드 중 입력 손실, 자동완성 이전 결과, 한글 Enter
- 오류 경계(배포 뒤 화면 파일 못 불러오면 새로고침), 네트워크 오류 시 로그아웃 방지, 댓글·신고 두 번 등록 등

### 변경
- 기록 유니버스 컨셉 디자인 개편: 브랜드 색·글꼴·로고, 메인 첫 화면 히어로, 헤더·푸터, 블로그 표지
- 글 상세 지연 로딩으로 메인 번들 492KB → 354KB

### 추가
- 화면 점검 113개를 저장소로 (`e2e/`), 발표 리허설 스크립트와 대본
- 문서: `CLAUDE.md`, 전체 코드 점검 보고서, 트러블슈팅 기록

## [0.5.0] - 2026-10-12

### 추가
- 💌 받는 사람이 있는 타임캡슐: 둘만 보는 편지, "받은 캡슐" 화면 (V4)
- 🔍 `[[글]]` 링크 미리보기 카드
- 🎞️ 기록 우주 타임랩스 (별이 쓴 순서대로 태어나요)
- 🌌 서비스 전체 우주 `/universe` (블로그별 은하)
- 시연용 데이터 스크립트 (`scripts/seed-demo.mjs`)

### 수정
- 글 요약에 `[[번호|글자]]` 문법이 그대로 보이던 문제 (요약 규칙 + V5 데이터 정리)

## [0.4.0] - 2026-10-12

### 추가
- 독창 기능 **기록 유니버스**
  - ⏳ 타임캡슐 글: 정한 시각까지 봉인, 자동 개봉 (V2)
  - 🔗 `[[글]]` 연결과 백링크 (V3)
  - 🌌 블로그 기록 우주 그래프 (d3-force + Canvas)
  - 🌱 기록 잔디와 연속 기록, 그날의 기록

### 수정
- 만료된 토큰으로 접속하면 공개 화면에서도 로그인 화면으로 튕기던 문제

## [0.3.0] - 2026-10-12

### 추가
- 화면 + API를 하나로 묶은 Docker 이미지, GitHub Actions 자동 배포 (수업 서버, https://ownlog.java21.net)
- 배포 전 DB 연결 진단 (서버·컨테이너 양쪽)
- README

### 수정
- 컨테이너가 학교 내부망 DB에 연결되지 않던 문제 (`--network host`)

## [0.2.0] - 2026-10-08

### 추가
- 프론트엔드 화면 15개 (React 19 + Vite): 메인·검색·태그, 블로그 홈, 글 상세·댓글, 글쓰기·임시저장, 설정, 카테고리 관리, 북마크

## [0.1.0] - 2026-10-08

### 추가
- 기획(요구사항 명세), ERD, API 명세서 (2026-10-01 ~ 10-06)
- 백엔드 REST API 38개 (Spring Boot 4.1, JWT, Flyway), 통합·동시성·롤백 테스트 121개

[Unreleased]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/compare/v1.0.1...develop
[1.0.1]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/compare/v0.6.0...v1.0.0
[0.6.0]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog/releases/tag/v0.1.0
