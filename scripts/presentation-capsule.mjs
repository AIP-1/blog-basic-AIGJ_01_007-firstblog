// 발표용 타임캡슐 만들기: 발표가 끝나는 순간 열리는 캡슐 2개를 시연 계정으로 만들어요.
//   ① 아침러너(runner) → 코드파도(codewave) 편지: codewave로 로그인한 화면에서 잠금 카드가 0초가 되며 열려요
//   ② 코드파도의 공개 캡슐: 열리면 메인 "최근 열린 타임캡슐"과 최신 글 맨 위에 나타나요
//
//   OPEN_AT=2026-10-14T15:20 BASE=https://ownlog.java21.net node scripts/presentation-capsule.mjs
//   OPEN_IN=5 BASE=https://ownlog.java21.net node scripts/presentation-capsule.mjs   (지금부터 5분 뒤, 발표 시간이 바뀌었을 때)
//
// OPEN_AT은 한국 시간이에요. 시연 데이터(scripts/seed-demo.mjs)가 먼저 들어가 있어야 해요.
const API = (process.env.BASE ?? 'http://localhost:8080') + '/api'

async function call(method, path, token, body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: body && JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`)
  return text ? JSON.parse(text) : null
}
const login = async (u) => (await call('POST', '/auth/login', null, { email: `${u}@demo.ownlog`, password: 'ownlog1234' })).accessToken

const openAt = process.env.OPEN_IN
  ? new Date(Date.now() + Number(process.env.OPEN_IN) * 60_000)
  : new Date(`${process.env.OPEN_AT ?? ''}:00+09:00`)
if (Number.isNaN(openAt.getTime())) {
  console.error('OPEN_AT(예: 2026-10-14T15:20) 또는 OPEN_IN(분)을 정해 주세요.')
  process.exit(1)
}
const openIso = openAt.toISOString()
const kst = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', dateStyle: 'medium', timeStyle: 'short' }).format(openAt)

const runner = await login('runner')
const codewave = await login('codewave')
const retro = (await call('GET', '/blogs/codewave/posts?size=50')).content.find((p) => p.title === 'OwnLog 2주 개발 회고')

const letter = await call('POST', '/posts', runner, {
  title: '발표를 마친 코드파도님께',
  content: [
    '## 발표 수고 많았어요! 🎉',
    '',
    '이 편지는 발표 전에 봉인해서, **발표가 끝나는 바로 이 순간** 열리도록 보냈어요.',
    '',
    '2주 동안 기획부터 배포까지, 그리고 기록 유니버스까지 정말 멋졌어요.',
    '',
    '오늘도 기록 하나 남기기로 해요. 🌱',
  ].join('\n'),
  tags: ['편지', '발표'],
  openAt: openIso,
  recipientUsername: 'codewave',
})

const capsule = await call('POST', '/posts', codewave, {
  title: '발표가 끝나는 순간 열리는 기록',
  topicId: 1,
  tags: ['회고', '타임캡슐', '발표'],
  content: [
    '## 발표 전의 내가, 발표를 마친 나에게',
    '',
    '이 글은 발표 전에 봉인한 **타임캡슐**이에요. 발표가 끝나는 시각이 되자 누구나 볼 수 있게 열렸어요.',
    '',
    '### OwnLog로 만든 것',
    '- 2주 만에 기획 → 설계 → 백엔드 → 프론트엔드 → 배포',
    '- 기록이 시간과 연결로 자라는 **기록 유니버스**',
    '',
    retro ? `### 함께 보면 좋은 기록\n- [[${retro.id}|OwnLog 2주 개발 회고]]` : '',
  ].join('\n'),
  openAt: openIso,
})

const site = API.replace(/\/api$/, '')
console.log(`발표용 타임캡슐 2개를 만들었어요. 열리는 시각: ${kst}`)
console.log(`① 편지 (runner → codewave, codewave로 로그인해서 열기): ${site}/@runner/posts/${letter.id}`)
console.log(`② 공개 캡슐 (누구나): ${site}/@codewave/posts/${capsule.id}`)
