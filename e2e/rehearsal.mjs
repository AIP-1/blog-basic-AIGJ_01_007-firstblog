// 발표 리허설: 발표 동선(문서 저장소 ownlog-docs의 docs/발표-리허설.md)을 실제 서버에서 순서대로 따라가며 단계마다 걸린 시간과 스크린샷을 남겨요.
// 글을 쓰거나 지우지 않아요 (글쓰기 화면은 자동완성만 띄워 보고 발행하지 않아요).
//
//   BASE=https://ownlog.java21.net node e2e/rehearsal.mjs
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const steps = []
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const token = (await (await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'codewave@demo.ownlog', password: 'ownlog1234' }) })).json()).accessToken

const page = await launch({ port: 9360 })
async function step(name, fn) {
  const started = Date.now()
  try {
    const detail = await fn()
    steps.push({ name, ok: true, ms: Date.now() - started, detail })
  } catch (e) {
    steps.push({ name, ok: false, ms: Date.now() - started, detail: e.message })
  }
}
const shot = (file) => page.screenshot(join(SHOTS, `rehearsal-${file}.png`))

try {
  await page.goto(`${BASE}/`)
  await page.eval(`localStorage.setItem('ownlog.accessToken', ${JSON.stringify(token)})`)

  await step('0. OwnLog 우주 열기 → 별이 자리 잡을 때까지', async () => {
    await page.goto(`${BASE}/universe`)
    await page.waitFor(`document.querySelector('canvas')?.dataset.settled === 'true'`, 20000)
    await shot('0-universe')
    return await page.eval(`document.querySelector('main header').innerText.split('\\n').find(l => l.includes('기록'))`)
  })
  await step('   ▶ 타임랩스 재생 (8초) → 오늘까지', async () => {
    await page.clickButton('▶ 타임랩스')
    await sleep(3000)
    await shot('0-timelapse')
    await page.waitFor(`document.querySelector('[data-timelapse-status]').innerText.startsWith('오늘')`, 12000)
    return '끝까지 재생됨'
  })
  await step('1. 코드파도의 기록 우주 → 별 찾기 "회고"', async () => {
    await page.goto(`${BASE}/@codewave/universe`)
    await page.waitFor(`document.querySelector('canvas')?.dataset.settled === 'true'`, 20000)
    await page.type('input[aria-label="별 찾기"]', '회고')
    await page.clickButton('찾기')
    await sleep(600)
    await shot('1-blog-universe')
    return await page.eval(`document.querySelector('main header').innerText.split('\\n').find(l => l.includes('기록'))`)
  })
  await step('   회고 글 → 🔗 링크 미리보기', async () => {
    const posts = await (await fetch(`${API}/blogs/codewave/posts?size=50`)).json()
    const retro = posts.content.find((p) => p.title === 'OwnLog 2주 개발 회고')
    await page.goto(`${BASE}/@codewave/posts/${retro.id}`)
    await page.waitFor(`document.querySelector('.markdown a.post-link')`, 10000)
    await page.eval(`document.querySelector('.markdown a.post-link').parentElement.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))`)
    await page.waitFor(`document.querySelector('[role=tooltip]')`, 3000)
    await shot('1-link-preview')
    return await page.eval(`document.querySelector('[role=tooltip]').innerText.split('\\n')[0]`)
  })
  await step('2. 💌 받은 캡슐 → 발표용 편지 카운트다운', async () => {
    await page.goto(`${BASE}/capsules`)
    await page.waitFor(`document.body.innerText.includes('발표를 마친 코드파도님께')`, 10000)
    await shot('2-inbox')
    return await page.eval(`[...document.querySelectorAll('main li')].find(li => li.innerText.includes('발표를 마친')).innerText.replace(/\\n/g, ' ')`)
  })
  await step('   편지 열기 → 잠금 카드(15:20에 자동으로 열려요)', async () => {
    await page.eval(`[...document.querySelectorAll('main li a')].find(a => a.innerText.includes('발표를 마친')).click()`)
    await page.waitFor(`document.body.innerText.includes('TIME CAPSULE')`, 10000)
    await shot('2-letter-locked')
    return await page.eval(`document.querySelector('[aria-label="남은 시간"]').innerText`)
  })
  await step('3. 블로그 홈 → 기록 잔디, 곧 열리는 타임캡슐', async () => {
    await page.goto(`${BASE}/@codewave`)
    await page.waitFor(`document.querySelector('[aria-label="기록 잔디"]') && document.body.innerText.includes('곧 열리는 타임캡슐')`, 10000)
    await shot('3-blog-home')
    return await page.eval(`document.querySelector('[aria-label="기록 잔디"]').innerText.split('\\n').slice(0, 3).join(' ')`)
  })
  await step('4. 메인 → 최근 열린 타임캡슐', async () => {
    await page.goto(`${BASE}/`)
    await page.waitFor(`document.querySelectorAll('main article').length > 0`, 10000)
    await shot('4-main')
    return (await page.text()).includes('최근 열린 타임캡슐') ? '최근 열린 타임캡슐 있음' : '최근 열린 캡슐 없음 (7일 안에 열린 게 없으면 숨겨져요)'
  })
  // 글쓰기는 마지막: 입력하면 "저장하지 않은 내용" 경고가 생겨서 다른 화면으로 이동하지 않아요
  await step('5. 글쓰기 → [[ 자동완성 (발행하지 않음)', async () => {
    await page.goto(`${BASE}/write`)
    await page.waitFor(`document.querySelector('textarea[aria-label="본문"]')`, 15000)
    await page.typeArea('textarea[aria-label="본문"]', '오늘 발표에서 소개한 [[Spring')
    await page.waitFor(`document.querySelectorAll('[role=option]').length > 0`, 5000)
    await shot('5-write-link')
    return `${await page.eval(`document.querySelectorAll('[role=option]').length`)}개 후보`
  })
} finally {
  page.close()
}

let total = 0
for (const s of steps) {
  total += s.ms
  console.log(`${s.ok ? '✅' : '❌'} ${s.name}  [${(s.ms / 1000).toFixed(1)}초]${s.detail ? `  ${s.detail}` : ''}`)
}
console.log(`\n화면 로딩·조작 합계 ${(total / 1000).toFixed(1)}초 · 스크린샷: e2e/screenshots/rehearsal-*.png`)
process.exit(steps.every((s) => s.ok) ? 0 : 1)
