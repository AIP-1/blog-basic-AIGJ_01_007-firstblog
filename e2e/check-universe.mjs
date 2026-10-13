// 기록 우주 화면 확인: 별 수, 자리 잡기, 별 클릭 이동, 검색 강조, 블로그 홈 진입, 휴대폰
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const U = process.env.UNI_USER ?? 'unidemo'
const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const graph = await (await fetch(`${BASE}/api/blogs/${U}/graph`)).json()
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const page = await launch({ port: 9344 })
try {
  await page.goto(`${BASE}/`)
  await page.eval(`localStorage.removeItem('ownlog.accessToken')`)

  // 1. 블로그 홈 → "🌌 기록 우주"
  await page.goto(`${BASE}/@${U}`)
  await page.waitFor(`[...document.querySelectorAll('a')].some(a => a.innerText.includes('기록 우주'))`)
  await page.eval(`[...document.querySelectorAll('a')].find(a => a.innerText.includes('기록 우주')).click()`)
  await page.waitFor(`location.pathname === '/@${U}/universe' && document.querySelector('canvas')`, 10000)
  const nodes = await page.eval(`Number(document.querySelector('canvas').dataset.nodes)`)
  check('1. 블로그 홈 "🌌 기록 우주" → 캔버스, 별 수 = API 별 수', nodes === graph.nodes.length, `${nodes}개, 선 ${graph.edges.length}개`)

  // 2. 별들이 퍼지며 자리를 잡음
  await page.waitFor(`document.querySelector('canvas').dataset.settled === 'true'`, 15000)
  const text = await page.text()
  check('2. 자리 잡기 완료, 헤더에 "기록 N개 · 연결 M개", 범례', text.includes(`기록 ${graph.nodes.length}개`) && text.includes('관련 있는 글') && text.includes('봉인된 타임캡슐'))
  await page.screenshot(join(SHOTS, '40-universe.png'))

  // 3. 검색 → 강조 + 그 별로 이동 → 그 자리 클릭하면 글로 이동
  const target = graph.nodes.find((n) => n.title === 'Spring Security 적용기')
  await page.type('input[aria-label="별 찾기"]', 'Security')
  await page.clickButton('찾기')
  await sleep(400)
  await page.screenshot(join(SHOTS, '41-universe-search.png'))
  const pos = await page.eval(`document.querySelector('canvas').__universe.screenOf(${target.id})`)
  const rect = await page.eval(`(() => { const r = document.querySelector('canvas').getBoundingClientRect(); return { x: r.left, y: r.top, w: r.width, h: r.height } })()`)
  const centered = Math.abs(pos.x - rect.w / 2) < 30 && Math.abs(pos.y - rect.h / 2) < 30
  await page.eval(`(() => {
    const c = document.querySelector('canvas'); const r = c.getBoundingClientRect()
    const p = c.__universe.screenOf(${target.id}); const opt = { clientX: r.left + p.x, clientY: r.top + p.y, pointerId: 1, pointerType: 'mouse', bubbles: true }
    c.dispatchEvent(new PointerEvent('pointerdown', opt)); c.dispatchEvent(new PointerEvent('pointerup', opt))
  })()`)
  await page.waitFor(`location.pathname === '/@${U}/posts/${target.id}'`, 5000)
  check('3. 검색 "Security" → 그 별로 이동(가운데), 별 클릭 → 글 상세', centered, JSON.stringify(pos))

  // 4. 잠긴 캡슐 별은 방문자에게 선이 없음 (API) + 목록 보기에 없음
  const locked = graph.nodes.filter((n) => n.locked)
  const lockedEdges = graph.edges.filter((e) => locked.some((n) => n.id === e.source || n.id === e.target))
  await page.goto(`${BASE}/@${U}/universe`)
  await page.waitFor(`document.querySelector('canvas')`)
  const listHasCapsule = await page.eval(`document.querySelector('details').innerText.includes('수료하는 날의 나에게')`)
  check('4. 봉인 캡슐: 잠긴 별, 선 0개, 목록 보기에 없음', locked.length === 1 && lockedEdges.length === 0 && !listHasCapsule)

  // 5. 휴대폰
  await page.viewport(375, 800)
  await page.goto(`${BASE}/@${U}/universe`)
  await page.waitFor(`document.querySelector('canvas')?.dataset.settled === 'true'`, 15000)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('5. 375px → 가로 스크롤 없음', sw <= 375, `scrollWidth ${sw}`)
  await page.screenshot(join(SHOTS, '42-universe-mobile.png'))

  // 6. 글이 없는 블로그
  await page.viewport(1280, 800)
  const fresh = 'empty' + Date.now().toString().slice(-6)
  await fetch(`${BASE}/api/auth/signup`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `${fresh}@test.com`, password: 'ownlog1234', passwordConfirm: 'ownlog1234', username: fresh, nickname: '새내기' }) })
  await page.goto(`${BASE}/@${fresh}/universe`)
  await page.waitFor(`document.body.innerText.includes('기록이 쌓이면 우주가 시작돼요')`)
  check('6. 글이 없는 블로그 → "기록이 쌓이면 우주가 시작돼요"', true)
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}
for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
