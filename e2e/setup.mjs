// 화면 점검에 필요한 데이터 준비 (여러 번 실행해도 돼요: 이미 있으면 건너뛰어요)
//  - 점검용 회원 qa45650(글 주인), ui792202(방문자), 비밀번호 ownlog1234, 이메일 @test.com
//  - qa45650·ui792202의 글 15개(seed-posts), 기록 우주용 unidemo(seed-universe), 시연 회원 5명(scripts/seed-demo.mjs)
//  - 업로드 점검용 이미지 screenshots/upload-test.png
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { crc32, deflateSync } from 'node:zlib'
import { API, BASE, DIR, SHOTS } from './common.mjs'

const run = (file, env = {}) => execFileSync(process.execPath, [file], { stdio: 'inherit', env: { ...process.env, BASE, ...env } })
const exists = async (username) => (await fetch(`${API}/blogs/${username}`)).ok

for (const u of ['qa45650', 'ui792202']) {
  if (await exists(u)) continue
  const res = await fetch(`${API}/auth/signup`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `${u}@test.com`, password: 'ownlog1234', passwordConfirm: 'ownlog1234', username: u, nickname: u }) })
  if (!res.ok) throw new Error(`점검용 회원 ${u} 만들기 실패: ${res.status} ${await res.text()}`)
}
const qaPosts = (await (await fetch(`${API}/blogs/qa45650/posts`)).json()).totalElements
if (qaPosts < 6) run(join(DIR, 'seed-posts.mjs'))
if (!(await exists('unidemo'))) run(join(DIR, 'seed-universe.mjs'))
// 시연 회원: 로컬 Docker DB면 날짜를 예전으로 옮겨서 잔디·타임랩스에 시간 차이가 생기게 해요
const localDb = !process.env.E2E_NO_SQL
if (!(await exists('codewave'))) {
  run(join(DIR, '..', 'scripts', 'seed-demo.mjs'),
    localDb ? { LOCAL_SQL: '1', ...(process.env.E2E_DB_SCHEMA && { DB_SCHEMA: process.env.E2E_DB_SCHEMA }) } : {})
}

// 320×180 분홍 PNG
const chunk = (type, data) => {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length)
  const td = Buffer.concat([Buffer.from(type), data])
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td))
  return Buffer.concat([len, td, crc])
}
const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(320, 0); ihdr.writeUInt32BE(180, 4); ihdr[8] = 8; ihdr[9] = 2
const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(320 * 3).map((_, i) => [236, 72, 153][i % 3])])
writeFileSync(join(SHOTS, 'upload-test.png'), Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', ihdr), chunk('IDAT', deflateSync(Buffer.concat(Array.from({ length: 180 }, () => row)))), chunk('IEND', Buffer.alloc(0))]))
console.log('점검 데이터 준비 완료')
