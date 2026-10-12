// 4단계 기반 화면 확인: 회원가입 → 자동 로그인 → 새로고침 유지 → 로그아웃 → 로그인 실패/성공 → 404 → 리다이렉트 → 모바일
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const user = 'ui' + Date.now().toString().slice(-6)
const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })

const page = await launch({ port: 9333 })
try {
  // 1~2. 아이디 실시간 확인 (디바운스)
  await page.goto(`${BASE}/signup`)
  await page.type('input[name=username]', 'admin')
  await page.waitFor(`document.body.innerText.includes('사용할 수 없는 아이디예요.')`, 3000)
  check('1. admin 입력 → "사용할 수 없는 아이디예요."', true)

  await page.type('input[name=username]', user)
  await page.waitFor(`document.body.innerText.includes('사용할 수 있는 아이디예요.')`, 3000)
  check(`2. ${user} 입력 → "사용할 수 있는 아이디예요."`, true)

  // 3. 짧은 비밀번호 → 입력칸 아래 에러
  await page.type('input[name=email]', `${user}@test.com`)
  await page.type('input[name=nickname]', '화면테스트')
  await page.type('input[name=password]', 'short')
  await page.type('input[name=passwordConfirm]', 'short')
  await page.click('button[type=submit]')
  await page.waitFor(`document.body.innerText.includes('비밀번호는 8~64자이고 영문과 숫자를 포함해야 해요.')`)
  check('3. 짧은 비밀번호 → 비밀번호 칸 아래 에러', (await page.path()) === '/signup')
  await page.screenshot(join(SHOTS, '1-signup-error.png'))

  // 4. 정상 가입 → 자동 로그인 → 메인
  await page.type('input[name=password]', 'ownlog1234')
  await page.type('input[name=passwordConfirm]', 'ownlog1234')
  await page.click('button[type=submit]')
  await page.waitFor(`location.pathname === '/' && document.body.innerText.includes('로그아웃')`)
  const header = await page.text()
  check('4. 가입 → 자동 로그인 → 메인, 헤더에 글쓰기·내 블로그·로그아웃',
    ['글쓰기', '내 블로그', '로그아웃'].every((t) => header.includes(t)))
  const token = await page.eval(`localStorage.getItem('ownlog.accessToken')`)
  check('   토큰이 localStorage에 저장됨', Boolean(token))
  await page.screenshot(join(SHOTS, '2-main-logged-in.png'))

  // 5. 새로고침해도 로그인 유지
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.body.innerText.includes('로그아웃')`)
  check('5. 새로고침 → 로그인 유지', true)

  // 6. 로그아웃
  await page.clickText('로그아웃')
  await page.waitFor(`document.body.innerText.includes('회원가입') && !document.body.innerText.includes('로그아웃')`)
  check('6. 로그아웃 → 헤더가 로그인·회원가입으로', (await page.eval(`localStorage.getItem('ownlog.accessToken')`)) === null)

  // 7. 틀린 비밀번호 → 에러, 화면 이동 없음
  await page.goto(`${BASE}/login`)
  await page.type('input[name=email]', `${user}@test.com`)
  await page.type('input[name=password]', 'wrong1234')
  await page.click('button[type=submit]')
  await page.waitFor(`document.body.innerText.includes('이메일 또는 비밀번호가 올바르지 않아요.')`)
  check('7. 틀린 비밀번호 → 에러 표시, 로그인 화면 유지', (await page.path()) === '/login')
  await page.screenshot(join(SHOTS, '3-login-error.png'))

  // 8. 올바른 로그인 → 메인
  await page.type('input[name=password]', 'ownlog1234')
  await page.click('button[type=submit]')
  await page.waitFor(`location.pathname === '/' && document.body.innerText.includes('로그아웃')`)
  check('8. 올바른 로그인 → 메인', true)

  // 9. 없는 주소 → 404 화면
  await page.goto(`${BASE}/없는주소`)
  await page.waitFor(`document.body.innerText.includes('존재하지 않는 페이지예요')`)
  check('9. 없는 주소 → "존재하지 않는 페이지예요"', true)

  // + 로그인 후 원래 주소로 돌아가기, 외부 주소 차단 (U-08, 오픈 리다이렉트)
  await page.clickText('로그아웃')
  await page.goto(`${BASE}/login?redirect=${encodeURIComponent('/@' + user)}`)
  await page.type('input[name=email]', `${user}@test.com`)
  await page.type('input[name=password]', 'ownlog1234')
  await page.click('button[type=submit]')
  await page.waitFor(`location.pathname !== '/login'`)
  check('+ ?redirect=/@아이디 → 로그인 후 그 주소로 이동', decodeURIComponent(await page.path()) === `/@${user}`,
    decodeURIComponent(await page.path()))

  await page.clickText('로그아웃')
  await page.goto(`${BASE}/login?redirect=${encodeURIComponent('//evil.example')}`)
  await page.type('input[name=email]', `${user}@test.com`)
  await page.type('input[name=password]', 'ownlog1234')
  await page.click('button[type=submit]')
  await page.waitFor(`location.pathname !== '/login'`)
  check('+ ?redirect=//evil.example → 외부로 가지 않고 메인', (await page.eval('location.href')).startsWith(BASE + '/'),
    await page.eval('location.href'))

  // 10. 좁은 화면: 검색창이 로고 아래 줄로
  await page.viewport(375, 800)
  await page.goto(`${BASE}/`)
  const layout = await page.eval(`(() => {
    const logo = document.querySelector('header a').getBoundingClientRect()
    const search = document.querySelector('header form').getBoundingClientRect()
    return { logoBottom: logo.bottom, searchTop: search.top, scrollWidth: document.documentElement.scrollWidth }
  })()`)
  check('10. 375px 화면 → 검색창이 아래 줄, 가로 스크롤 없음',
    layout.searchTop >= layout.logoBottom && layout.scrollWidth <= 375, JSON.stringify(layout))
  await page.screenshot(join(SHOTS, '4-mobile.png'))
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}

for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
