// 만료·탈퇴 토큰이 남아 있을 때 공개 화면에서 튕기지 않는지
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'
const page = await launch({ dir: dirname(fileURLToPath(import.meta.url)), port: 9343 })
const bad = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5OTk5OSIsImV4cCI6MTAwMDAwMDAwMH0.invalid'
const results = []
for (const p of ['/signup', '/', '/login']) {
  await page.goto(`${BASE}/`)
  await page.eval(`localStorage.setItem('ownlog.accessToken', '${bad}')`)
  await page.goto(BASE + p)
  await new Promise((r) => setTimeout(r, 1500))
  const path = await page.path()
  const token = await page.eval(`localStorage.getItem('ownlog.accessToken')`)
  results.push([`${p} (잘못된 토큰) → 그대로 ${p}, 토큰 삭제`, path === p && token === null, path])
}
page.close()
for (const [n, ok, d] of results) console.log(`${ok ? '✅' : '❌'} ${n}  (${d})`)
