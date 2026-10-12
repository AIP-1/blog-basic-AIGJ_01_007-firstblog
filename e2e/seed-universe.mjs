// 기록 우주 확인용: 링크와 태그로 엮인 글 16개 + 봉인 캡슐 1개 (회원 uni_demo)
const BASE = (process.env.BASE ?? 'http://localhost:5173') + '/api'   // 화면 주소 + /api
const U = process.env.UNI_USER ?? 'unidemo'
async function call(method, path, token, body) {
  const res = await fetch(BASE + path, { method, headers: { ...(token && { Authorization: `Bearer ${token}` }), ...(body && { 'Content-Type': 'application/json' }) },
    body: body && JSON.stringify(body) })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`)
  return text ? JSON.parse(text) : null
}
await call('POST', '/auth/signup', null, { email: `${U}@test.com`, password: 'ownlog1234', passwordConfirm: 'ownlog1234', username: U, nickname: '우주탐험가' }).catch(() => {})
const t = (await call('POST', '/auth/login', null, { email: `${U}@test.com`, password: 'ownlog1234' })).accessToken
const ids = {}
const post = async (key, title, topicId, tags, links = [], extra = {}) => {
  const content = `## ${title}\n\n${links.map((k) => `관련 기록: [[${ids[k]}|${k}]]`).join('\n\n')}\n\n오늘의 기록이에요.`
  ids[key] = (await call('POST', '/posts', t, { title, content, topicId, tags, ...extra })).id
}
await post('Java 기초', 'Java 기초 문법 정리', 1, ['Java', '기초'])
await post('JVM', 'JVM 메모리 구조', 1, ['Java', 'JVM'], ['Java 기초'])
await post('Spring 시작', 'Spring Boot 시작하기', 1, ['Spring', 'Java'], ['Java 기초'])
await post('JPA', 'JPA 연관관계 정리', 1, ['Spring', 'JPA'], ['Spring 시작'])
await post('JWT', 'JWT 인증 정리', 1, ['Spring', 'JWT', '보안'], ['Spring 시작'])
await post('Security', 'Spring Security 적용기', 1, ['Spring', 'JWT', '보안'], ['JWT', 'Spring 시작'])
await post('React', 'React 19 훑어보기', 1, ['React', '프론트'])
await post('Vite', 'Vite로 프로젝트 만들기', 1, ['React', 'Vite', '프론트'], ['React'])
await post('OwnLog 회고', 'OwnLog 2주 개발 회고', 2, ['회고', 'Spring', 'React'], ['Security', 'JPA', 'Vite'])
await post('부산', '부산 여행 1일차', 3, ['부산', '여행'])
await post('부산2', '부산 여행 2일차', 3, ['부산', '여행'], ['부산'])
await post('돼지국밥', '부산 돼지국밥 맛집', 4, ['부산', '맛집'], ['부산2'])
await post('라멘', '집에서 라멘 만들기', 4, ['요리'])
await post('책', '클린 코드 읽고', 10, ['독서', 'Java'], ['Java 기초'])
await post('러닝', '아침 러닝 30일', 5, ['운동', '습관'])
await post('습관', '기록하는 습관 만들기', 2, ['습관', '회고'], ['러닝', 'OwnLog 회고'])
await post('캡슐', '수료하는 날의 나에게', 2, ['회고'], ['OwnLog 회고'], { openAt: new Date(Date.now() + 2 * 86400_000).toISOString() })
for (const k of ['Security', 'JWT', 'OwnLog 회고', 'Spring 시작']) {
  await call('PUT', `/posts/${ids[k]}/like`, (await call('POST', '/auth/login', null, { email: 'qa45650@test.com', password: 'ownlog1234' })).accessToken).catch(() => {})
}
console.log(`${U}: 글 ${Object.keys(ids).length}개`)
