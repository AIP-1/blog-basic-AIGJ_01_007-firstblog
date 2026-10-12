// 메인 글 목록 확인: 카드 수, 페이지, 인기 탭, 토픽, 새로고침 유지, 카드 이동, 휴대폰 메뉴
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const cards = 'document.querySelectorAll("main article").length'
const likesOf = `[...document.querySelectorAll('main article')].map(a => Number(a.innerText.match(/♥ (\\d+)/)[1]))`

const page = await launch({ port: 9334 })
try {
  // 로그인 (휴대폰 메뉴 확인용)
  await page.goto(`${BASE}/login`)
  await page.type('input[name=email]', 'qa45650@test.com')
  await page.type('input[name=password]', 'ownlog1234')
  await page.click('button[type=submit]')
  await page.waitFor(`location.pathname === '/' && ${cards} > 0`)

  const total = await page.eval(cards)
  const pager = await page.eval(`[...document.querySelectorAll('nav[aria-label="페이지"] a')].map(a => a.innerText)`)
  check('1. 메인: 카드 12개 + 페이지 번호', total === 12 && pager.includes('2'), `카드 ${total}, 페이지 ${pager.join(' ')}`)
  const thumbs = await page.eval(`document.querySelectorAll('main article img').length`)
  check('   썸네일 있는 카드와 없는 카드가 섞여 보임', thumbs > 0 && thumbs < 12, `썸네일 ${thumbs}개`)
  await page.screenshot(join(SHOTS, '5-main-desktop.png'))

  // 2페이지
  await page.eval(`[...document.querySelectorAll('nav[aria-label="페이지"] a')].find(a => a.innerText === '2').click()`)
  const totalPosts = (await (await fetch(`${BASE}/api/posts`)).json()).totalElements
  const expected = Math.min(12, totalPosts - 12)
  await page.waitFor(`location.search === '?page=2' && ${cards} === ${expected}`)
  check(`2. 2페이지 → 카드 ${expected}개 (전체 ${totalPosts}개), 주소 ?page=2`, true)

  // 인기 탭: 1페이지로, 좋아요 내림차순
  await page.clickText('인기')
  await page.waitFor(`location.search === '?tab=popular' && document.body.innerText.includes('최근 7일')`)
  await page.waitFor(`${cards} > 0`)
  const likes = await page.eval(likesOf)
  const sorted = likes.every((v, i) => i === 0 || likes[i - 1] >= v)
  check('3. 인기 탭 → ?tab=popular, 1페이지, 좋아요 많은 순', sorted, `좋아요 ${likes.join(',')}`)

  // 토픽: 여행 (탭은 유지)
  await page.clickText('여행')
  await page.waitFor(`new URLSearchParams(location.search).get('topic') === 'travel' && ${cards} > 0`)
  const topicsShown = await page.eval(`[...document.querySelectorAll('main article')].map(a => a.querySelector('span').innerText)`)
  const params = await page.path()
  check('4. 토픽 "여행" → 여행 글만, 인기 탭 유지', topicsShown.every((t) => t === '여행') && params.includes('tab=popular'),
    `${params} / ${topicsShown.length}개`)

  // 새로고침 유지
  await page.goto(`${BASE}${params}`)
  await page.waitFor(`${cards} > 0`)
  const activeChip = await page.eval(`[...document.querySelectorAll('[role=tablist] button')].find(b => b.className.includes('bg-indigo-600')).innerText`)
  check('5. 새로고침 → 탭·토픽 그대로', activeChip === '여행' && (await page.path()) === params, `선택된 칩: ${activeChip}`)

  // 카드 클릭 → 상세 주소
  await page.eval(`document.querySelector('main a[href*="/posts/"]').click()`)
  await page.waitFor(`location.pathname.includes('/posts/')`)
  check('6. 카드 클릭 → /@아이디/posts/{id}', /^\/@[a-z0-9_]+\/posts\/\d+$/.test(decodeURIComponent(await page.path())),
    decodeURIComponent(await page.path()))

  // 휴대폰: 1열, 토픽 가로 스크롤, 메뉴로 설정 진입
  await page.viewport(375, 800)
  await page.goto(`${BASE}/`)
  await page.waitFor(`${cards} > 0`)
  const mobile = await page.eval(`(() => {
    const [a, b] = document.querySelectorAll('main article')
    const chips = document.querySelector('[role=tablist]')
    return { oneColumn: b.getBoundingClientRect().top > a.getBoundingClientRect().top,
             chipsScroll: chips.scrollWidth > chips.clientWidth,
             pageScroll: document.documentElement.scrollWidth }
  })()`)
  check('7. 375px → 카드 1열, 토픽 칩 가로 스크롤, 화면 가로 스크롤 없음',
    mobile.oneColumn && mobile.chipsScroll && mobile.pageScroll <= 375, JSON.stringify(mobile))

  await page.clickText('메뉴')
  await page.waitFor(`[...document.querySelectorAll('header a')].some(a => a.innerText === '설정' && a.offsetParent)`)
  await page.screenshot(join(SHOTS, '6-mobile-menu.png'))
  await page.eval(`[...document.querySelectorAll('header a')].find(a => a.innerText === '설정' && a.offsetParent).click()`)
  await page.waitFor(`location.pathname === '/settings'`)
  check('8. 휴대폰 "메뉴" → 설정 진입 가능', true)
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}

for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
