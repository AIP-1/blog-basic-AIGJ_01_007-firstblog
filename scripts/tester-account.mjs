// 베타 테스터용 공용 계정 만들기: 가입 없이 바로 기능을 써 볼 수 있게 글·연결·받은 타임캡슐을 미리 넣어 둬요.
//
//   BASE=https://ownlog.java21.net node scripts/tester-account.mjs
//   TESTER_USER=tester2 BASE=... node scripts/tester-account.mjs   (누가 탈퇴시켜서 아이디를 다시 못 쓰면 다른 아이디로)
//
// 시연 데이터(scripts/seed-demo.mjs)가 먼저 들어가 있어야 해요 (편지를 보내는 runner 계정).
const API = (process.env.BASE ?? 'http://localhost:8080') + '/api'
const USER = process.env.TESTER_USER ?? 'tester'
const PASSWORD = process.env.TESTER_PASSWORD ?? 'tester1234'

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
const inMinutes = (m) => new Date(Date.now() + m * 60_000).toISOString()

const email = `${USER}@demo.ownlog`
try {
  await call('POST', '/auth/signup', null, { email, password: PASSWORD, passwordConfirm: PASSWORD, username: USER, nickname: '베타테스터' })
} catch (e) {
  if (!String(e.message).includes('409')) throw e
  console.log(`이미 "${USER}" 계정이 있어요. 로그인만 확인할게요.`)
  await call('POST', '/auth/login', null, { email, password: PASSWORD })
  console.log('로그인 확인 완료 (데이터는 그대로 둬요)')
  process.exit(0)
}
const { accessToken: t, user } = await call('POST', '/auth/login', null, { email, password: PASSWORD })
await call('PUT', '/users/me', t, { nickname: '베타테스터', bio: 'OwnLog를 미리 써 보는 중이에요' })
await call('PUT', `/blogs/${user.blogId}`, t, { name: '베타테스터의 기록장', description: '자유롭게 써 보세요. 이 계정은 여러 사람이 함께 써요' })

const welcome = (await call('POST', '/posts', t, {
  title: '👋 테스터 계정 사용 안내', topicId: 2, tags: ['안내'],
  content: [
    '## 이 계정은 여러 사람이 함께 써요',
    '- 글쓰기, 댓글, 좋아요, 북마크 모두 자유롭게 써 보세요',
    '- **비밀번호 변경과 회원 탈퇴는 하지 말아 주세요** (다른 테스터가 못 들어와요)',
    '- 나만의 블로그를 원하면 회원가입해도 돼요',
    '',
    '## 꼭 써 볼 기능',
    '1. 🌌 헤더의 **우주** → ▶ 타임랩스',
    '2. 글쓰기에서 `[[` 입력 → 다른 글 연결',
    '3. 글쓰기 아래 **⏳ 타임캡슐로 보내기** (1분 뒤로 해 보세요)',
    '4. 헤더 **💌 받은 캡슐** → 곧 열리는 편지',
  ].join('\n'),
})).id
const first = (await call('POST', '/posts', t, {
  title: '첫 기록: 오늘 한 일', topicId: 2, tags: ['일상', '테스트'],
  content: `오늘은 OwnLog를 처음 써 봤어요.\n\n안내 글: [[${welcome}|테스터 계정 사용 안내]]`,
})).id
await call('POST', '/posts', t, {
  title: '두 번째 기록: 글끼리 연결해 보기', topicId: 1, tags: ['테스트', '연결'],
  content: `[[${first}|첫 기록]]에 이어서 써요. 글을 연결하면 🌌 기록 우주에 선이 생겨요.`,
})

// 받은 타임캡슐: 30분 뒤 열리는 편지 (테스터가 카운트다운과 개봉을 직접 볼 수 있게), 다음 날 열리는 편지
const runner = (await call('POST', '/auth/login', null, { email: 'runner@demo.ownlog', password: 'ownlog1234' })).accessToken
await call('POST', '/posts', runner, { title: '테스터님께: 30분 뒤에 열리는 편지', tags: ['편지'], openAt: inMinutes(30), recipientUsername: USER,
  content: '## 테스트에 참여해 줘서 고마워요! 🙌\n\n이 편지는 30분 동안 봉인돼 있다가, 받는 사람인 테스터 계정에게만 열렸어요.' })
await call('POST', '/posts', runner, { title: '내일 열리는 편지', tags: ['편지'], openAt: inMinutes(60 * 24), recipientUsername: USER,
  content: '하루를 기다린 편지예요. 오늘도 기록 하나 남겨 보세요 🌱' })

const site = API.replace(/\/api$/, '')
console.log(`테스터 계정을 만들었어요: ${email} / ${PASSWORD}  (블로그 ${site}/@${USER})`)
