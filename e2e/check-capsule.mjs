// 타임캡슐 화면 확인: 봉인 발행 → 주인/방문자 화면 → 목록 숨김 → 개봉 순간 자동 열림 → 메인 "최근 열린"
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS, sql } from './common.mjs'

const A = 'qa45650', B = 'ui792202'
const TITLE = '캡슐 ' + Date.now().toString().slice(-6)
const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const login = async (u) => (await (await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: `${u}@test.com`, password: 'ownlog1234' }) })).json()).accessToken
const tA = await login(A), tB = await login(B)

const page = await launch({ port: 9338 })
const asUser = async (token) => { await page.goto(`${BASE}/`); await page.eval(`localStorage.setItem('ownlog.accessToken', ${JSON.stringify(token)})`) }
try {
  // 1. 글쓰기: 타임캡슐 켜고 "1분 뒤" → 봉인하기
  await asUser(tA)
  await page.goto(`${BASE}/write`)
  await page.waitFor(`document.querySelector('textarea[aria-label="본문"]')`, 10000)
  await page.type('input[aria-label="제목"]', TITLE)
  await page.typeArea('textarea[aria-label="본문"]', '미래의 나에게 쓰는 비밀 본문')
  await page.click('input[aria-label="타임캡슐로 보내기"]')
  await page.clickButton('1분 뒤')
  const btn = await page.eval(`[...document.querySelectorAll('button')].some(b => b.innerText === '⏳ 봉인하기')`)
  await page.screenshot(join(SHOTS, '20-capsule-write.png'))
  await page.eval(`window.confirm = () => true`)
  await page.clickButton('⏳ 봉인하기')
  await page.waitFor(`/\\/posts\\/\\d+$/.test(location.pathname)`, 8000)
  const postPath = await page.path()
  const id = postPath.split('/').pop()
  await page.waitFor(`document.body.innerText.includes('봉인 중인 타임캡슐이에요')`)
  const ownerView = await page.text()
  check('1. 타임캡슐 켜기 → 버튼 "⏳ 봉인하기" → 발행 후 주인은 본문 + 봉인 안내, 수정 버튼 없음',
    btn && ownerView.includes('미래의 나에게') && !(await page.eval(`[...document.querySelectorAll('a')].some(a => a.innerText === '수정')`)))

  // 2. 주인이 /write/{id} 직접 열면 수정 불가 안내
  await page.goto(`${BASE}/write/${id}`)
  await page.waitFor(`document.body.innerText.includes('열리기 전까지 수정할 수 없어요')`, 8000)
  check('2. 봉인 중 /write/{id} → "열리기 전까지 수정할 수 없어요"', true)

  // 3. 방문자: 잠금 카드 + 카운트다운, 본문 없음
  await asUser(tB)
  await page.goto(`${BASE}${postPath}`)
  await page.waitFor(`document.body.innerText.includes('TIME CAPSULE')`)
  const locked = await page.text()
  check('3. 방문자 → 잠금 카드(TIME CAPSULE, 남은 시간), 본문·댓글 없음',
    !locked.includes('비밀 본문') && !locked.includes('댓글') && /\d+분 \d{2}초|D-\d+|\d+시간/.test(locked), locked.match(/\d+분 \d{2}초/)?.[0])
  await page.screenshot(join(SHOTS, '21-capsule-locked.png'))

  // 4. 메인·검색에 없음, 블로그 홈에 "곧 열리는 타임캡슐"
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.querySelectorAll('main article').length > 0`)
  const mainHas = (await page.text()).includes(TITLE)
  await page.goto(`${BASE}/@${A}`)
  await page.waitFor(`document.body.innerText.includes('곧 열리는 타임캡슐')`)
  check('4. 메인 목록에 없음, 블로그 홈 "곧 열리는 타임캡슐"에 제목과 남은 시간', !mainHas && (await page.text()).includes(TITLE))
  await page.screenshot(join(SHOTS, '22-capsule-blog.png'))

  // 5. 개봉 순간: 방문자가 잠금 화면을 보고 있는 동안 개봉 시각을 6초 뒤로 당김 → 자동으로 열림
  sql(`UPDATE posts SET sealed_at = now() - interval '1 day', published_at = now() + interval '6 seconds' WHERE id = ${id}`)
  await page.goto(`${BASE}${postPath}`)
  await page.waitFor(`document.body.innerText.includes('TIME CAPSULE')`)
  await page.waitFor(`document.body.innerText.includes('비밀 본문')`, 20000)
  const opened = await page.text()
  check('5. 카운트다운 0 → 새로고침 없이 자동으로 열림, "봉인된 기록" 배지·댓글 영역',
    opened.includes('봉인된 기록') && opened.includes('댓글'))
  await page.screenshot(join(SHOTS, '23-capsule-opened.png'))

  // 6. 메인 "최근 열린 타임캡슐", 최신 탭 맨 위
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.body.innerText.includes('최근 열린 타임캡슐')`)
  const firstCard = await page.eval(`document.querySelector('main article h2').innerText`)
  check('6. 메인 "최근 열린 타임캡슐"에 표시, 최신 글 맨 위', (await page.text()).includes(TITLE) && firstCard === TITLE, firstCard)
  await page.screenshot(join(SHOTS, '24-capsule-main.png'))

  // 7. 휴대폰: 잠금 카드 가로 스크롤 없음
  const r = await fetch(`${API}/posts`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tA}` },
    body: JSON.stringify({ title: TITLE + ' 2', content: 'x', openAt: new Date(Date.now() + 86400_000 * 30).toISOString() }) })
  const id2 = (await r.json()).id
  await page.viewport(375, 800)
  await page.goto(`${BASE}/@${A}/posts/${id2}`)
  await page.waitFor(`document.body.innerText.includes('TIME CAPSULE')`)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('7. 375px 잠금 카드(D-29) → 가로 스크롤 없음', sw <= 375 && (await page.text()).includes('D-29'), `scrollWidth ${sw}`)
  await page.screenshot(join(SHOTS, '25-capsule-mobile.png'))
  await fetch(`${API}/posts/${id2}`, { method: 'DELETE', headers: { Authorization: `Bearer ${tA}` } })
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}
for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
