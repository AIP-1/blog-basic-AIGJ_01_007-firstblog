// 메인 목록 확인용 테스트 글 15개 만들기 (API를 Vite 프록시로 호출)
import { crc32, deflateSync } from 'node:zlib'

const BASE = (process.env.BASE ?? 'http://localhost:5173') + '/api'   // 화면 주소 + /api
const USERS = ['qa45650', 'ui792202']

async function call(method, path, token, body, isForm = false) {
  const res = await fetch(BASE + path, {
    method,
    headers: { ...(token && { Authorization: `Bearer ${token}` }), ...(!isForm && body && { 'Content-Type': 'application/json' }) },
    body: isForm ? body : body && JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`)
  return text ? JSON.parse(text) : null
}

/** 단색 PNG (썸네일 확인용) */
function png(width, height, [r, g, b]) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length)
    const td = Buffer.concat([Buffer.from(type), data])
    const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td))
    return Buffer.concat([len, td, crc])
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4); ihdr[8] = 8; ihdr[9] = 2
  const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(width * 3).map((_, i) => [r, g, b][i % 3])])
  const raw = Buffer.concat(Array.from({ length: height }, () => row))
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))])
}

const tokens = []
for (const u of USERS) {
  tokens.push((await call('POST', '/auth/login', null, { email: `${u}@test.com`, password: 'ownlog1234' })).accessToken)
}

const colors = [[99, 102, 241], [16, 185, 129], [245, 158, 11], [239, 68, 68]]
const topics = [1, 3, 4, 2, 3, null, 1, 5, 3, 9, 1, 3, 10, 4, 3] // it, travel, food, daily, ...
const ids = []
for (let i = 0; i < 15; i++) {
  const owner = i % 2
  let thumbnailUrl = null
  if (i % 3 === 0) {
    const form = new FormData()
    form.append('file', new Blob([png(160, 90, colors[i % colors.length])], { type: 'image/png' }), `thumb${i}.png`)
    thumbnailUrl = (await call('POST', '/images', tokens[owner], form, true)).url
  }
  const { id } = await call('POST', '/posts', tokens[owner], {
    title: `테스트 글 ${i + 1}${topics[i] === 3 ? ' 부산 여행 기록' : ''}`,
    content: `## 본문 ${i + 1}\n\n**마크다운** 요약이 카드에 두 줄까지 보이는지 확인하는 글이에요. `.repeat(3),
    topicId: topics[i],
    thumbnailUrl,
  })
  ids.push(id)
}
// 좋아요: 글마다 0~2개 (인기 탭 순서 확인용)
for (let i = 0; i < ids.length; i++) {
  if (i % 3 !== 0) await call('PUT', `/posts/${ids[i]}/like`, tokens[0])
  if (i % 2 === 0) await call('PUT', `/posts/${ids[i]}/like`, tokens[1])
}
console.log(`글 ${ids.length}개 생성: ${ids[0]} ~ ${ids.at(-1)}`)
