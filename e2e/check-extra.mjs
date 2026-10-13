// 추가 기능 확인: 우주 타임랩스, 서비스 전체 우주, 링크 미리보기, 받는 사람 타임캡슐
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const login = async (u) => (await (await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: `${u}@demo.ownlog`, password: 'ownlog1234' }) })).json()).accessToken
const tokens = Object.fromEntries(await Promise.all(['codewave', 'tripnote', 'cookbook', 'runner'].map(async (u) => [u, await login(u)])))
const authed = (u) => ({ headers: { Authorization: `Bearer ${tokens[u]}` } })

const page = await launch({ port: 9348 })
const asUser = async (u) => { await page.goto(`${BASE}/`); await page.eval(u ? `localStorage.setItem('ownlog.accessToken', ${JSON.stringify(tokens[u])})` : `localStorage.removeItem('ownlog.accessToken')`) }
const starCount = () => page.eval(`Number(document.querySelector('[data-timelapse-status]').innerText.match(/별 (\\d+)개/)[1])`)
try {
  // 1. 타임랩스
  await asUser(null)
  await page.goto(`${BASE}/@codewave/universe`)
  await page.waitFor(`document.querySelector('canvas')?.dataset.settled === 'true'`, 15000)
  const total = await starCount()
  await page.clickButton('▶ 타임랩스')
  await sleep(300)
  const early = await starCount()
  await sleep(3500)
  const mid = await starCount()
  await page.screenshot(join(SHOTS, '80-timelapse-mid.png'))
  await page.waitFor(`document.querySelector('[data-timelapse-status]').innerText.startsWith('오늘')`, 9000)
  const end = await starCount()
  check('1. 타임랩스 ▶ → 별이 0개 근처에서 점점 늘고, 끝나면 "오늘 · 전체"', early < mid && mid <= total && end === total && early < total,
    `처음 ${early} → 중간 ${mid} → 끝 ${end} / 전체 ${total}`)

  // 1-2. 글이 몇 초 안에 몰려 있어도 별이 하나씩 태어남 (예전에는 첫 프레임에 다 나타났어요: 트러블슈팅 13번)
  await page.goto(`${BASE}/@unidemo/universe`)   // seed-universe는 날짜를 옮기지 않아서 모든 글이 몇 초 안에 쓰였어요
  await page.waitFor(`document.querySelector('canvas')?.dataset.settled === 'true'`, 15000)
  const clusteredTotal = await starCount()
  await page.clickButton('▶ 타임랩스')
  await sleep(1200)
  const clusteredEarly = await starCount()
  await sleep(2500)
  const clusteredMid = await starCount()
  check('1-2. 글이 몇 초 안에 몰린 우주도 별이 하나씩 (1.2초에 일부만, 이후 늘어남)',
    clusteredEarly > 0 && clusteredEarly < clusteredTotal / 2 && clusteredMid > clusteredEarly,
    `1.2초 ${clusteredEarly} → 3.7초 ${clusteredMid} / 전체 ${clusteredTotal}`)
  await page.waitFor(`document.querySelector('[data-timelapse-status]').innerText.startsWith('오늘')`, 12000)

  // 2. 서비스 전체 우주 (헤더 "🌌 우주")
  const uni = await (await fetch(`${API}/universe`)).json()
  await page.eval(`[...document.querySelectorAll('header a')].find(a => a.getAttribute('href') === '/universe').click()`)
  await page.waitFor(`location.pathname === '/universe' && document.body.innerText.includes('OwnLog 우주') && document.querySelector('canvas')?.dataset.settled === 'true'`, 15000)
  const g = await page.eval(`({ nodes: Number(document.querySelector('canvas').dataset.nodes),
    galaxies: document.querySelectorAll('[aria-label="은하 목록"] a').length, head: document.querySelector('main header').innerText })`)
  const blogs = new Set(uni.nodes.map((n) => n.username)).size
  check(`2. 헤더 "🌌 우주" → 전체 우주: 별 수 = API, 은하 ${blogs}개(블로그 수), 블로그를 잇는 연결 수 표시`,
    g.nodes === uni.nodes.length && g.galaxies === blogs && /블로그를 잇는 연결 [1-9]/.test(g.head), JSON.stringify(g))
  await sleep(1200)
  await page.screenshot(join(SHOTS, '81-global-universe.png'))
  const target = uni.nodes.find((n) => n.username === 'tripnote')
  await page.eval(`(() => { const c = document.querySelector('canvas'); const r = c.getBoundingClientRect(); const p = c.__universe.screenOf(${target.id})
    const o = { clientX: r.left + p.x, clientY: r.top + p.y, pointerId: 1, pointerType: 'mouse', bubbles: true }
    c.dispatchEvent(new PointerEvent('pointerdown', o)); c.dispatchEvent(new PointerEvent('pointerup', o)) })()`)
  await page.waitFor(`location.pathname === '/@tripnote/posts/${target.id}'`, 5000)
  check('   다른 블로그의 별 클릭 → 그 블로그의 글로 이동', true)

  // 3. 링크 미리보기
  const sec = (await (await fetch(`${API}/blogs/codewave/posts?size=50`)).json()).content.find((p) => p.title === 'Spring Security 적용기')
  await page.goto(`${BASE}/@codewave/posts/${sec.id}`)
  await page.waitFor(`document.querySelector('.markdown a.post-link')`)
  await page.eval(`document.querySelector('.markdown a.post-link').parentElement.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))`)
  await page.waitFor(`document.querySelector('[role=tooltip]')`, 3000)
  const tip = await page.eval(`document.querySelector('[role=tooltip]').innerText`)
  check('3. 🔗 링크에 마우스 → 미리보기 카드(제목·닉네임·날짜·요약)', tip.includes('JWT 인증 정리') && tip.includes('코드파도') && tip.includes('토큰 구조'), tip.replace(/\n/g, ' / '))
  await page.screenshot(join(SHOTS, '82-link-preview.png'))

  // 4. 받은 타임캡슐 목록 (codewave: bookworm·runner가 보낸 2개)
  await asUser('codewave')
  await page.goto(`${BASE}/capsules`)
  await page.waitFor(`document.querySelectorAll('main li').length > 0`)
  const inbox = await page.eval(`[...document.querySelectorAll('main li')].map(li => li.innerText.replace(/\\n/g, ' '))`)
  // 시드의 3분 캡슐이 열렸는지에 따라 순서가 바뀌어서 순서 대신 내용을 봐요 (안 열린 것 → 열린 것)
  check('4. 💌 받은 캡슐 (codewave) → 보낸 사람(책벌레, 아침러너)·남은 시간 또는 "읽기"',
    inbox.length >= 2 && inbox.some((t) => t.includes('책벌레')) && inbox.some((t) => t.includes('아침러너')), inbox.join(' | '))
  await page.screenshot(join(SHOTS, '83-inbox.png'))

  // 5. 받는 사람(tripnote)이 봉인 중인 편지 열기 → 잠금 카드, 제3자(cookbook)는 404
  const letter = (await (await fetch(`${API}/users/me/time-capsules`, authed('tripnote'))).json())[0]
  await asUser('tripnote')
  await page.goto(`${BASE}/@codewave/posts/${letter.id}`)
  await page.waitFor(`document.body.innerText.includes('TIME CAPSULE')`)
  check('5. 받는 사람 → "💌 나에게 온 타임캡슐" 잠금 카드', (await page.text()).includes('나에게 온 타임캡슐'))
  await page.screenshot(join(SHOTS, '84-letter-locked.png'))
  await asUser('cookbook')
  await page.goto(`${BASE}/@codewave/posts/${letter.id}`)
  await page.waitFor(`document.body.innerText.includes('존재하지 않는 글')`)
  check('   제3자 → "존재하지 않는 글"', true)

  // 6. 글쓰기에서 받는 사람 캡슐 보내기 (없는 아이디 → 에러, runner → 성공)
  await asUser('codewave')
  await page.goto(`${BASE}/write`)
  await page.waitFor(`document.querySelector('textarea[aria-label="본문"]')`, 10000)
  await page.type('input[aria-label="제목"]', '러너님께 보내는 응원')
  await page.typeArea('textarea[aria-label="본문"]', '내일도 화이팅!')
  await page.click('input[aria-label="타임캡슐로 보내기"]')
  await page.clickButton('1분 뒤')
  await page.type('input[aria-label="받는 사람 아이디"]', 'nobody123')
  await page.eval(`window.confirm = () => true`)
  await page.clickButton('⏳ 봉인하기')
  await page.waitFor(`document.body.innerText.includes('받는 사람을 찾을 수 없어요')`)
  check('6. 없는 아이디로 보내기 → "받는 사람을 찾을 수 없어요"', true)
  await page.type('input[aria-label="받는 사람 아이디"]', '@runner')
  await page.clickButton('⏳ 봉인하기')
  await page.waitFor(`/\\/posts\\/\\d+$/.test(location.pathname) && document.body.innerText.includes('님에게 보낸 캡슐')`, 8000)
  const runnerInbox = await (await fetch(`${API}/users/me/time-capsules`, authed('runner'))).json()
  check('   @runner로 봉인 → 상세에 "💌 아침러너님에게 보낸 캡슐", runner 받은 목록에 추가',
    runnerInbox.some((c) => c.title === '러너님께 보내는 응원'))
  await page.screenshot(join(SHOTS, '85-letter-sent.png'))

  // 7. 헤더: 넓은 화면 한 줄, 휴대폰 메뉴에 "받은 캡슐"
  const header = await page.eval(`document.querySelector('header').getBoundingClientRect().height`)
  await page.viewport(375, 800)
  await page.goto(`${BASE}/`)
  await page.clickText('메뉴')
  await page.waitFor(`[...document.querySelectorAll('header a')].some(a => a.innerText.includes('받은 캡슐') && a.offsetParent)`)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('7. 1280px 헤더 한 줄, 375px 메뉴에 "💌 받은 캡슐", 가로 스크롤 없음', header < 70 && sw <= 375, `header ${header}px, scrollWidth ${sw}`)
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}
for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
