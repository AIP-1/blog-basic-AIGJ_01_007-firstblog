// 시연용 데이터 넣기: 모든 기능을 바로 써 볼 수 있게 API로 회원 5명과 글·댓글·좋아요·받는 사람 캡슐 등을 만들어요.
//
//   node scripts/seed-demo.mjs                         (로컬: http://localhost:8080)
//   BASE=https://ownlog.java21.net node scripts/seed-demo.mjs   (서버)
//   LOCAL_SQL=1 node scripts/seed-demo.mjs             (로컬 Docker DB면 예전 날짜로 옮겨서 기록 잔디를 채워요)
//
// 모든 회원의 비밀번호는 ownlog1234 예요. 같은 회원이 이미 있으면 중복을 막으려고 멈춰요.
import { execSync } from 'node:child_process'
import { crc32, deflateSync } from 'node:zlib'

const API = (process.env.BASE ?? 'http://localhost:8080') + '/api'
const PASSWORD = 'ownlog1234'

async function call(method, path, token, body, isForm = false) {
  const res = await fetch(API + path, {
    method,
    headers: { ...(token && { Authorization: `Bearer ${token}` }), ...(!isForm && body && { 'Content-Type': 'application/json' }) },
    body: isForm ? body : body && JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`)
  return text ? JSON.parse(text) : null
}

/** 가로 그라데이션 PNG (썸네일·프로필 사진용, 외부 이미지 없이 만들어요) */
function png(width, height, [r1, g1, b1], [r2, g2, b2]) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length)
    const td = Buffer.concat([Buffer.from(type), data])
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td))
    return Buffer.concat([len, td, crc])
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr[8] = 8; ihdr[9] = 2
  const rows = []
  for (let y = 0; y < height; y++) {
    const row = Buffer.alloc(1 + width * 3)
    for (let x = 0; x < width; x++) {
      const t = (x / width + y / height) / 2
      row[1 + x * 3] = r1 + (r2 - r1) * t; row[2 + x * 3] = g1 + (g2 - g1) * t; row[3 + x * 3] = b1 + (b2 - b1) * t
    }
    rows.push(row)
  }
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(Buffer.concat(rows))), chunk('IEND', Buffer.alloc(0))])
}
const COLORS = {
  indigo: [[79, 70, 229], [167, 139, 250]], sea: [[14, 165, 233], [52, 211, 153]], sunset: [[249, 115, 22], [236, 72, 153]],
  forest: [[22, 163, 74], [190, 242, 100]], night: [[30, 27, 75], [124, 58, 237]], warm: [[234, 179, 8], [249, 115, 22]],
}
async function upload(token, color, w = 640, h = 360) {
  const form = new FormData()
  form.append('file', new Blob([png(w, h, ...COLORS[color])], { type: 'image/png' }), `${color}.png`)
  return (await call('POST', '/images', token, form, true)).url
}

const inMinutes = (m) => new Date(Date.now() + m * 60_000).toISOString()
const TOPIC = { it: 1, daily: 2, travel: 3, food: 4, sports: 5, game: 6, movie: 7, music: 8, book: 9, pet: 10, finance: 11 }

// ---------------------------------------------------------------------------------
// 회원 5명 (가상의 인물이에요)
// ---------------------------------------------------------------------------------
const PEOPLE = [
  { username: 'codewave', nickname: '코드파도', bio: '백엔드 개발자를 꿈꾸는 학생', blog: '파도치는 코드', about: 'Java·Spring·React 공부 기록', color: 'indigo' },
  { username: 'tripnote', nickname: '여행노트', bio: '주말마다 어디론가 떠나요', blog: '발길 닿는 대로', about: '국내외 여행 기록', color: 'sea' },
  { username: 'cookbook', nickname: '오늘의부엌', bio: '집밥 연구가', blog: '오늘의 부엌', about: '쉽고 맛있는 집밥 레시피', color: 'sunset' },
  { username: 'bookworm', nickname: '책벌레', bio: '한 달에 네 권', blog: '책 읽는 밤', about: '읽고, 쓰고, 남기기', color: 'forest' },
  { username: 'runner', nickname: '아침러너', bio: '매일 5km', blog: '오늘도 달린다', about: '운동과 습관 만들기', color: 'warm' },
]

const users = {}
for (const p of PEOPLE) {
  const email = `${p.username}@demo.ownlog`
  try {
    await call('POST', '/auth/signup', null, { email, password: PASSWORD, passwordConfirm: PASSWORD, username: p.username, nickname: p.nickname })
  } catch (e) {
    if (String(e.message).includes('409')) {
      console.error(`이미 "${p.username}" 회원이 있어요. 시연 데이터가 이미 들어간 것 같아서 멈출게요.`)
      process.exit(1)
    }
    throw e
  }
  const { accessToken, user } = await call('POST', '/auth/login', null, { email, password: PASSWORD })
  const profileImageUrl = await upload(accessToken, p.color, 200, 200)
  await call('PUT', '/users/me', accessToken, { nickname: p.nickname, bio: p.bio, profileImageUrl })
  await call('PUT', `/blogs/${user.blogId}`, accessToken, { name: p.blog, description: p.about })
  users[p.username] = { ...p, token: accessToken, blogId: user.blogId, posts: {} }
}
const U = users

async function category(u, name, visibility = 'PUBLIC') {
  return (await call('POST', `/blogs/${u.blogId}/categories`, u.token, { name, visibility })).id
}
/** 글 발행. links: [[번호|글자]]로 넣을 다른 글 키 목록 ("작성자:키" 형식이면 다른 사람 글) */
async function post(u, key, { title, body, topic, tags = [], categoryId, thumb, visibility, links = [], openAt }) {
  const linkText = links.map((ref) => {
    const [owner, k] = ref.includes(':') ? ref.split(':') : [u.username, ref]
    const target = U[owner].posts[k]
    return `- [[${target.id}|${target.title}]]`
  })
  const content = `${body}${linkText.length ? `\n\n### 함께 보면 좋은 기록\n${linkText.join('\n')}` : ''}`
  const thumbnailUrl = thumb ? await upload(u.token, thumb) : null
  const { id } = await call('POST', '/posts', u.token, {
    title, content, topicId: topic && TOPIC[topic], tags, categoryId, thumbnailUrl, visibility, openAt,
  })
  u.posts[key] = { id, title }
  return id
}
const comment = async (u, postId, content, parentId) => (await call('POST', `/posts/${postId}/comments`, u.token, { content, parentId })).id

// ---------------------------------------------------------------------------------
// codewave: 개발 (링크가 촘촘한 "기록 우주"의 중심)
// ---------------------------------------------------------------------------------
const cw = U.codewave
const cwSpring = await category(cw, 'Spring')
const cwReact = await category(cw, 'React')
const cwRetro = await category(cw, '회고')
const cwSecret = await category(cw, '비밀노트', 'PRIVATE')

await post(cw, 'java', { title: 'Java 기초 문법 정리', topic: 'it', tags: ['Java', '기초'], categoryId: cwSpring, thumb: 'indigo',
  body: '## 변수와 타입\n\n자바는 **정적 타입** 언어예요.\n\n```java\nint count = 10;\nString name = "OwnLog";\n```\n\n- 기본형: `int`, `long`, `boolean`\n- 참조형: `String`, 배열, 클래스' })
await post(cw, 'jvm', { title: 'JVM 메모리 구조', topic: 'it', tags: ['Java', 'JVM'], categoryId: cwSpring, links: ['java'],
  body: '## Heap과 Stack\n\n객체는 Heap, 지역 변수는 Stack에 저장돼요.\n\n> GC는 Heap의 쓰지 않는 객체를 정리해요.' })
await post(cw, 'spring', { title: 'Spring Boot 시작하기', topic: 'it', tags: ['Spring', 'Java'], categoryId: cwSpring, thumb: 'night', links: ['java'],
  body: '## 프로젝트 만들기\n\nstart.spring.io에서 의존성을 고르고 시작해요.\n\n```bash\n./gradlew bootRun\n```' })
await post(cw, 'jpa', { title: 'JPA 연관관계 정리', topic: 'it', tags: ['Spring', 'JPA'], categoryId: cwSpring, links: ['spring'],
  body: '## 연관관계의 주인\n\n외래키를 가진 쪽이 주인이에요. `mappedBy`는 주인이 아닌 쪽에 써요.' })
await post(cw, 'jwt', { title: 'JWT 인증 정리', topic: 'it', tags: ['Spring', 'JWT', '보안'], categoryId: cwSpring, links: ['spring'],
  body: '## 토큰 구조\n\nHeader.Payload.Signature 세 부분으로 나뉘어요.\n\n본문 예시 코드에 있는 `[[1|이건 링크가 아니에요]]`는 코드라서 링크로 바뀌지 않아요.' })
await post(cw, 'security', { title: 'Spring Security 적용기', topic: 'it', tags: ['Spring', 'JWT', '보안'], categoryId: cwSpring, thumb: 'indigo', links: ['jwt', 'spring'],
  body: '## 필터 체인\n\nJWT 필터를 `UsernamePasswordAuthenticationFilter` 앞에 넣었어요.' })
await post(cw, 'react', { title: 'React 19 훑어보기', topic: 'it', tags: ['React', '프론트'], categoryId: cwReact, thumb: 'sea',
  body: '## useEffectEvent\n\n이펙트 안에서 최신 값을 읽되, 의존성에는 넣지 않을 수 있어요.' })
await post(cw, 'vite', { title: 'Vite로 프로젝트 만들기', topic: 'it', tags: ['React', 'Vite', '프론트'], categoryId: cwReact, links: ['react'],
  body: '## 개발 서버\n\n`npm run dev` 한 줄이면 끝이에요. 프록시로 `/api`를 백엔드에 넘겨요.' })
await post(cw, 'secret', { title: '면접 예상 질문 (비공개)', topic: 'it', tags: ['면접'], categoryId: cwSecret, links: ['security'],
  body: '나만 보는 메모예요. 다른 사람에게는 보이지 않아요.' })
await post(cw, 'private', { title: '연봉 협상 메모', tags: ['메모'], visibility: 'PRIVATE', body: '비공개 글이에요.' })

// ---------------------------------------------------------------------------------
// tripnote: 여행
// ---------------------------------------------------------------------------------
const tn = U.tripnote
const tnKorea = await category(tn, '국내 여행')
const tnAbroad = await category(tn, '해외 여행')
await post(tn, 'busan1', { title: '부산 여행 1일차: 해운대', topic: 'travel', tags: ['부산', '여행', '바다'], categoryId: tnKorea, thumb: 'sea',
  body: '## 해운대 산책\n\n아침 바다가 정말 예뻤어요. 🌊\n\n1. 해운대 해수욕장\n2. 동백섬\n3. 더베이101 야경' })
await post(tn, 'busan2', { title: '부산 여행 2일차: 감천문화마을', topic: 'travel', tags: ['부산', '여행'], categoryId: tnKorea, thumb: 'sunset', links: ['busan1'],
  body: '## 알록달록한 골목\n\n어린왕자 동상 앞은 줄이 길었어요.' })
await post(tn, 'jeju', { title: '제주 한 달 살기 준비', topic: 'travel', tags: ['제주', '여행'], categoryId: tnKorea, thumb: 'forest',
  body: '## 준비물\n\n- 렌터카 예약\n- 숙소 (한 달 단위가 저렴해요)' })
await post(tn, 'tokyo', { title: '도쿄 3박 4일 코스', topic: 'travel', tags: ['도쿄', '여행', '해외'], categoryId: tnAbroad, thumb: 'night',
  body: '## 시부야 → 아사쿠사\n\n지하철 패스를 꼭 사세요.' })

// ---------------------------------------------------------------------------------
// cookbook: 요리 (여행 글과 서로 연결)
// ---------------------------------------------------------------------------------
const cb = U.cookbook
await post(cb, 'gukbap', { title: '부산식 돼지국밥 집에서 끓이기', topic: 'food', tags: ['부산', '국밥', '레시피'], thumb: 'warm', links: ['tripnote:busan1'],
  body: '## 재료\n\n- 돼지 앞다리살 600g\n- 사골 육수\n\n부산 여행에서 먹은 맛을 떠올리며 만들었어요.' })
await post(cb, 'ramen', { title: '집에서 라멘 만들기', topic: 'food', tags: ['라멘', '레시피'], thumb: 'sunset',
  body: '## 차슈부터\n\n삼겹살을 말아서 간장에 졸여요.' })
await post(cb, 'kimchi', { title: '김치찌개 황금 비율', topic: 'food', tags: ['김치찌개', '레시피'],
  body: '## 비율\n\n김치 2 : 돼지고기 1 : 물 3' })
await post(tn, 'busanfood', { title: '부산에서 먹은 것들', topic: 'food', tags: ['부산', '맛집'], categoryId: tnKorea, links: ['busan2', 'cookbook:gukbap'],
  body: '## 돼지국밥, 밀면, 씨앗호떡\n\n집에서도 만들 수 있다길래 레시피를 찾아봤어요.' })

// ---------------------------------------------------------------------------------
// bookworm: 독서 (개발 글과 연결)
// ---------------------------------------------------------------------------------
const bw = U.bookworm
const bwReading = await category(bw, '읽는 중', 'PRIVATE')
await post(bw, 'cleancode', { title: '『클린 코드』를 읽고', topic: 'book', tags: ['독서', '개발', 'Java'], thumb: 'forest', links: ['codewave:java'],
  body: '## 함수는 한 가지만\n\n> 깨끗한 코드는 한 가지를 제대로 한다.\n\n코드파도님의 자바 정리 글과 같이 읽으면 좋아요.' })
await post(bw, 'demian', { title: '『데미안』 다시 읽기', topic: 'book', tags: ['독서', '고전'],
  body: '## 알을 깨고 나오기\n\n스무 살에 읽었을 때와 느낌이 달라요.' })
await post(bw, 'reading', { title: '읽는 중: 『객체지향의 사실과 오해』', topic: 'book', tags: ['독서'], categoryId: bwReading,
  body: '비공개 카테고리의 글이에요.' })

// ---------------------------------------------------------------------------------
// runner: 운동·일상
// ---------------------------------------------------------------------------------
const rn = U.runner
await post(rn, 'run30', { title: '아침 러닝 30일 챌린지', topic: 'sports', tags: ['러닝', '습관', '운동'], thumb: 'warm',
  body: '## 첫 주가 제일 힘들어요\n\n5km를 35분 → 29분으로 줄였어요.' })
await post(rn, 'habit', { title: '기록하는 습관 만들기', topic: 'daily', tags: ['습관', '회고'], links: ['run30', 'codewave:spring'],
  body: '## 매일 한 줄\n\n잔디가 끊기지 않게 매일 짧게라도 써요. 🌱' })
await post(rn, 'game', { title: '요즘 하는 인디 게임', topic: 'game', tags: ['게임', '인디'], body: '## 하데스 2\n\n한 판만 하려다 새벽 2시...' })
await post(rn, 'movie', { title: '이번 주 본 영화', topic: 'movie', tags: ['영화'], body: '## 인사이드 아웃 2\n\n불안이 귀여웠어요.' })

// 여러 블로그를 잇는 회고 글 (기록 우주의 큰 별)
await post(cw, 'retro', { title: 'OwnLog 2주 개발 회고', topic: 'it', tags: ['회고', 'Spring', 'React'], categoryId: cwRetro, thumb: 'night',
  links: ['security', 'jpa', 'vite', 'bookworm:cleancode', 'runner:habit'],
  body: '## 잘한 것\n\n- 기획부터 배포까지 단계별로 진행\n- 테스트를 먼저 챙겼어요\n\n## 아쉬운 것\n\n- 처음에 일정이 조금 밀렸어요' })

// ---------------------------------------------------------------------------------
// 타임캡슐: 곧 열리는 것(시연용), 며칠 뒤, 1년 뒤, 비공개
// ---------------------------------------------------------------------------------
await post(cw, 'capsule3', { title: '3분 뒤에 열리는 기록', tags: ['타임캡슐'], openAt: inMinutes(3), links: ['retro'],
  body: '## 3분 전의 나에게서\n\n이 글은 발행하고 3분 뒤에 열렸어요. 메인 "최근 열린 타임캡슐"에서도 보여요. ✨' })
await post(cw, 'capsuleDay', { title: '수료하는 날의 나에게', topic: 'daily', tags: ['회고', '타임캡슐'], openAt: inMinutes(60 * 48), links: ['retro'],
  body: '2주 동안 고생 많았어. 배포까지 해냈다니!' })
await post(tn, 'capsuleYear', { title: '1년 뒤 다시 갈 부산', topic: 'travel', tags: ['부산', '타임캡슐'], openAt: inMinutes(60 * 24 * 365),
  body: '1년 뒤에도 해운대 아침 바다를 보러 가자.' })
await post(rn, 'capsulePrivate', { title: '100일 뒤 체중 (나만)', tags: ['운동'], visibility: 'PRIVATE', openAt: inMinutes(60 * 24 * 100), body: '비공개 캡슐' })

// 받는 사람이 있는 타임캡슐 (보낸 사람과 받는 사람만 봐요): 3분 뒤 열리는 것, 이틀 뒤, 한 달 뒤
await call('POST', '/posts', bw.token, { title: '코드파도님께, 발표 끝나고 열어 보세요', content: '## 2주 동안 정말 수고 많았어요\n\n책 한 권 추천할게요: [[' + bw.posts.cleancode.id + '|클린 코드 독후감]]',
  tags: ['편지'], openAt: inMinutes(3), recipientUsername: 'codewave' })
await call('POST', '/posts', rn.token, { title: '수료 축하 편지', content: '같이 달려서 즐거웠어요! 🏃', tags: ['편지'], openAt: inMinutes(60 * 48), recipientUsername: 'codewave' })
await call('POST', '/posts', cw.token, { title: '여행노트님께 드리는 부산 맛집 지도', content: '다음 부산 여행 때 열어 보세요.', tags: ['편지', '부산'], openAt: inMinutes(60 * 24 * 30), recipientUsername: 'tripnote' })

// ---------------------------------------------------------------------------------
// 임시저장, 수정한 글
// ---------------------------------------------------------------------------------
await call('POST', '/drafts', cw.token, { content: '제목 없이 쓰다 만 글이에요. 임시저장 목록에서 "제목 없음"으로 보여요.' })
await call('POST', '/drafts', cw.token, { title: 'Docker로 배포하기 (작성 중)', content: '## 이미지 빌드\n\n멀티 스테이지 빌드로...', topicId: TOPIC.it, tags: ['Docker'] })
await call('POST', '/drafts', tn.token, { title: '오사카 여행 계획', content: '- 유니버설 스튜디오\n- 도톤보리' })
await call('PUT', `/posts/${cw.posts.jpa.id}`, cw.token, { title: 'JPA 연관관계 정리', topicId: TOPIC.it, tags: ['Spring', 'JPA'], categoryId: cwSpring,
  content: '## 연관관계의 주인\n\n외래키를 가진 쪽이 주인이에요. `mappedBy`는 주인이 아닌 쪽에 써요.\n\n**추가:** 지연 로딩(LAZY)을 기본으로 써요.\n\n### 함께 보면 좋은 기록\n- [[' + cw.posts.spring.id + '|Spring Boot 시작하기]]' })

// ---------------------------------------------------------------------------------
// 댓글(답글, 수정, 삭제), 좋아요(인기 탭), 북마크, 신고
// ---------------------------------------------------------------------------------
const sec = cw.posts.security.id
const c1 = await comment(tn, sec, '필터 순서 때문에 저도 한참 헤맸어요!')
await comment(cw, sec, '맞아요, 순서가 제일 중요하더라고요 😂', c1)
await comment(bw, sec, '클린 코드 관점에서도 필터를 나눈 게 좋아 보여요.', c1)
const c2 = await comment(rn, sec, '저도 따라 해 볼게요')
await call('PATCH', `/comments/${c2}`, rn.token, { content: '저도 따라 해 볼게요. 감사합니다! (수정)' })
const c3 = await comment(cb, sec, '광고 댓글처럼 보이는 테스트 댓글')
const c4 = await comment(cw, sec, '이 댓글은 답글이 있어서 지우면 "삭제된 댓글"로 남아요')
await comment(tn, sec, '정말요?', c4)
await call('DELETE', `/comments/${c4}`, cw.token)
const busan = tn.posts.busan1.id
await comment(cb, busan, '해운대 근처 국밥집 추천해요!')
await comment(rn, busan, '아침 해변 러닝 코스로도 최고예요 🏃')
await comment(cw, cb.posts.gukbap.id, '레시피 따라 해 봤는데 진짜 맛있어요')
await comment(tn, cw.posts.retro.id, '2주 만에 배포까지 대단해요 👏')

const likes = {
  codewave: ['security', 'retro', 'jwt', 'spring', 'react'], tripnote: ['busan1', 'busan2', 'jeju', 'tokyo'],
  cookbook: ['gukbap', 'ramen'], bookworm: ['cleancode'], runner: ['run30', 'habit'],
}
for (const [owner, keys] of Object.entries(likes)) {
  for (const key of keys) {
    for (const liker of Object.values(U)) {
      // 글마다 좋아요 수가 다르게 (인기 탭 순서 확인용), 본인 글 좋아요도 허용 (C-03)
      if ((key.length + liker.username.length) % 3 !== 0 || key === 'security' || key === 'retro') {
        await call('PUT', `/posts/${U[owner].posts[key].id}/like`, liker.token)
      }
    }
  }
}
for (const [who, refs] of Object.entries({ codewave: ['tripnote:busan1', 'cookbook:gukbap', 'bookworm:cleancode'], tripnote: ['codewave:security', 'runner:run30'], bookworm: ['codewave:java', 'codewave:retro'] })) {
  for (const ref of refs) {
    const [owner, key] = ref.split(':')
    await call('PUT', `/posts/${U[owner].posts[key].id}/bookmark`, U[who].token)
  }
}
await call('POST', `/posts/${rn.posts.game.id}/reports`, cb.token, { reason: 'ETC', detail: '시연용 신고: 주제와 맞지 않아요' })
await call('POST', `/comments/${c3}/reports`, cw.token, { reason: 'SPAM' })

// 조회수 (방문자 조회는 +1)
for (const key of ['security', 'retro', 'spring']) {
  for (let i = 0; i < 3; i++) await fetch(`${API}/blogs/codewave/posts/${cw.posts[key].id}`)
}

// ---------------------------------------------------------------------------------
// (로컬 전용) 예전 날짜로 옮겨서 기록 잔디·연속 기록 채우기: codewave는 최근 5개, 나머지는 최근 2개를 그대로 둬서 인기 탭(최근 7일)이 살아 있어요
// ---------------------------------------------------------------------------------
if (process.env.LOCAL_SQL) {
  const sql = `
    WITH ranked AS (
      SELECT p.id, us.username, row_number() OVER (PARTITION BY us.username ORDER BY p.id DESC) AS rn
        FROM posts p JOIN blogs b ON b.id = p.blog_id JOIN users us ON us.id = b.user_id
       WHERE us.username IN ('codewave','tripnote','cookbook','bookworm','runner')
         AND p.status = 'PUBLISHED' AND p.sealed_at IS NULL
    )
    UPDATE posts p SET published_at = CASE WHEN r.username = 'codewave'
               THEN date_trunc('day', now()) - (r.rn - 5) * interval '1 day' + interval '10 hours'
               ELSE date_trunc('day', now()) - (r.rn - 2) * 3 * interval '1 day' + interval '10 hours' END
      FROM ranked r
     WHERE p.id = r.id AND ((r.username = 'codewave' AND r.rn > 5) OR (r.username <> 'codewave' AND r.rn > 2));
    UPDATE posts p SET published_at = date_trunc('day', now()) - (sub.k * interval '1 day') + interval '9 hours'
      FROM (SELECT p2.id, row_number() OVER (ORDER BY p2.id DESC) - 1 AS k
              FROM posts p2 JOIN blogs b ON b.id = p2.blog_id JOIN users us ON us.id = b.user_id
             WHERE us.username = 'runner' AND p2.status = 'PUBLISHED' AND p2.sealed_at IS NULL) sub
     WHERE p.id = sub.id;`
  execSync(`docker exec -i ownlog-postgres psql -U ownlog -d ownlog -q`, { input: sql })
  console.log('기록 잔디용 날짜를 예전으로 옮겼어요 (로컬)')
}

const total = Object.values(U).reduce((n, u) => n + Object.keys(u.posts).length, 0)
console.log(`완료: 회원 ${PEOPLE.length}명, 발행 글 ${total + 3}개 (타임캡슐 4개, 받는 사람 캡슐 3개 포함), 임시저장 3개. 비밀번호는 모두 ${PASSWORD}`)
console.log(`곧 열리는 타임캡슐: ${API.replace('/api', '')} → /@codewave/posts/${cw.posts.capsule3.id} (3분 뒤 열려요)`)
