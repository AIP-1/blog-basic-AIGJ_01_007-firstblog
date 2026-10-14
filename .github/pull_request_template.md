## 무엇을 바꿨나요
<!-- 한두 문장으로. 관련 요구사항 ID가 있으면 함께 (예: TC-07) -->

## 왜 바꿨나요
<!-- 문제 상황, 원인 -->

## 변경 종류
- [ ] ✨ feat: 새 기능
- [ ] 🐛 fix: 버그 수정
- [ ] 🔒 보안
- [ ] ♻️ refactor / ⚡ perf
- [ ] 📝 docs / 🔧 chore / ✅ test

## 확인한 것 (완료 기준, CLAUDE.md 7장)
- [ ] `./gradlew test` 전체 통과 (개수: )
- [ ] `npm run lint` 경고 0, `npm run build`
- [ ] 화면 변경이면 `node e2e/run-all.mjs` (관련 점검 추가·갱신)
- [ ] 권한 케이스 (남의 것·비공개·비로그인) 테스트
- [ ] 문서 동기화: 요구사항 · ERD · API 명세 · README
- [ ] `CHANGELOG.md`의 `[Unreleased]`에 추가
- [ ] 의미 있는 문제를 해결했다면 문서 저장소(ownlog-docs)의 `docs/트러블슈팅.md`에 사례 추가

## DB·배포 영향
- [ ] 새 Flyway 마이그레이션 있음 (`V?__...sql`, 배포된 파일은 수정하지 않음)
- [ ] 배포 설정(.github/workflows, deploy/, Dockerfile) 변경 있음

## 스크린샷 (화면 변경 시)
