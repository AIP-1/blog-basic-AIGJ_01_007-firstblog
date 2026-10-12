// 기록 잔디 확인: 칸 수, 연속 기록, 칸 클릭 → 그날의 기록, 휴대폰 가로 넘김
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'
const U = process.env.UNI_USER ?? 'unidemo'
const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const act = await (await fetch(`${BASE}/api/blogs/${U}/activity`)).json()
const page = await launch({ port: 9345 })
try {
  await page.goto(`${BASE}/@${U}`)
  await page.waitFor(`document.querySelector('[aria-label="기록 잔디"] a')`)
  const info = await page.eval(`(() => { const s = document.querySelector('[aria-label="기록 잔디"]')
    return { filled: s.querySelectorAll('a').length, text: s.innerText.split('\\n')[0] + ' ' + s.innerText.split('\\n')[1] } })()`)
  check('1. 블로그 홈 잔디: 글 쓴 날 칸 수 = API, "🔥 연속 기록 N일"', info.filled === act.days.length && info.text.includes(`연속 기록 ${act.currentStreak}일`), JSON.stringify(info))
  await page.screenshot(join(SHOTS, '50-activity.png'))

  const day = act.days.at(-1)
  await page.eval(`document.querySelector('[aria-label="기록 잔디"] a[href$="date=${day.date}"]').click()`)
  await page.waitFor(`location.search === '?date=${day.date}' && document.body.innerText.includes('의 기록')`)
  const sealed = (await (await fetch(`${BASE}/api/blogs/${U}/time-capsules`)).json()).filter((c) => c.sealedAt.slice(0, 10) === day.date).length
  const expected = Math.min(day.count - sealed, 12)   // 한 페이지에 12개
  await page.waitFor(`document.querySelectorAll('main article').length === ${expected}`)
  if (sealed) await page.waitFor(`document.body.innerText.includes('이날 봉인한 타임캡슐 ${sealed}')`)
  check(`2. 잔디 칸(${day.date}, ${day.count}개) 클릭 → "그날의 기록" 카드 ${expected}개(한 페이지) + 이날 봉인한 캡슐 ${sealed}개`, true)
  await page.screenshot(join(SHOTS, '52-activity-day.png'))
  await page.clickText('전체 보기')
  await page.waitFor(`location.search === '' && !document.body.innerText.includes('📅')`)
  check('3. "전체 보기" → 날짜 필터 해제', true)

  await page.viewport(375, 800)
  await page.goto(`${BASE}/@${U}`)
  await page.waitFor(`document.querySelector('[aria-label="기록 잔디"] a')`)
  const m = await page.eval(`(() => { const box = document.querySelector('[aria-label="기록 잔디"] .overflow-x-auto')
    return { page: document.documentElement.scrollWidth, scrolled: box.scrollLeft > 0, inner: box.scrollWidth > box.clientWidth } })()`)
  check('4. 375px: 화면 가로 스크롤 없음, 잔디만 가로로 넘기고 최근 쪽부터 보임', m.page <= 375 && m.scrolled && m.inner, JSON.stringify(m))
  await page.screenshot(join(SHOTS, '51-activity-mobile.png'))
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}
for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
