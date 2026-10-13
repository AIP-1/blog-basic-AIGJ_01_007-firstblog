// 기능 가이드 /guide: 메인·푸터에서 들어가기, 섹션, 바로 해 보기 링크, 휴대폰
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { BASE, SHOTS } from './common.mjs'

const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const page = await launch({ port: 9372 })
try {
  await page.goto(`${BASE}/`)
  await page.eval(`localStorage.removeItem('ownlog.accessToken')`)
  await page.goto(`${BASE}/`)
  await page.waitFor(`[...document.querySelectorAll('main a')].some(a => a.innerText.includes('기능 가이드'))`, 10000)
  await page.eval(`[...document.querySelectorAll('main a')].find(a => a.innerText.includes('기능 가이드')).click()`)
  await page.waitFor(`location.pathname === '/guide' && document.body.innerText.includes('OwnLog에서 할 수 있는 일')`)
  const text = await page.text()
  const sections = ['시작하기', '기록 유니버스', '기본 기능', '자주 묻는 것'].filter((s) => text.includes(s))
  const features = ['타임캡슐', '받는 사람이 있는 타임캡슐', '글 연결', '기록 우주와 타임랩스', '기록 잔디', '테스터 계정'].filter((s) => text.includes(s))
  check('1. 메인 "📖 기능 가이드" → /guide, 섹션 4개와 기록 유니버스 기능 소개', sections.length === 4 && features.length === 6,
    `${sections.join(',')} / ${features.length}개`)
  await page.screenshot(join(SHOTS, '95-guide.png'))

  await page.eval(`[...document.querySelectorAll('main a')].find(a => a.innerText.includes('OwnLog 우주 열기')).click()`)
  await page.waitFor(`location.pathname === '/universe'`)
  check('2. "OwnLog 우주 열기" → /universe', true)

  await page.goto(`${BASE}/guide`)
  await page.waitFor(`document.body.innerText.includes('OwnLog에서 할 수 있는 일')`)
  await page.eval(`[...document.querySelectorAll('main a')].find(a => a.innerText.includes('1분 뒤 열리는 캡슐')).click()`)
  await page.waitFor(`location.pathname === '/login'`)
  check('3. 로그인 전 "1분 뒤 열리는 캡슐 만들기" → 로그인 화면 (돌아올 주소 /write)', decodeURIComponent(await page.path()).includes('redirect=/write'))

  const footer = await page.eval(`[...document.querySelectorAll('footer a')].some(a => a.getAttribute('href') === '/guide')`)
  check('4. 푸터에 "📖 기능 가이드" 링크', footer)

  await page.viewport(375, 800)
  await page.goto(`${BASE}/guide`)
  await page.waitFor(`document.body.innerText.includes('OwnLog에서 할 수 있는 일')`)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('5. 375px 가이드 → 가로 스크롤 없음', sw <= 375, `scrollWidth ${sw}`)
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}
for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
