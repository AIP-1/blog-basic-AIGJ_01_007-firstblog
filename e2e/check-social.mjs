// 소셜 로그인 (U-09): 키가 있는 서비스만 버튼, 동의 화면으로 보내기, 실패 안내, 잘못된 코드
// 점검용 서버는 가짜 키(OAUTH_KAKAO_*, OAUTH_GITHUB_*)로 띄워요. 진짜 카카오·깃허브로는 요청하지 않아요
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { BASE, SHOTS } from './common.mjs'

const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const page = await launch({ port: 9373 })
try {
  const { providers } = await (await fetch(`${BASE}/api/auth/oauth/providers`)).json()
  if (providers.length === 0) {
    check('0. 점검용 서버에 소셜 로그인 키가 없어요 (e2e/README.md의 OAUTH_* 값으로 띄워 주세요)', false)
    throw new Error('키 없음')
  }

  await page.goto(`${BASE}/login`)
  await page.eval(`localStorage.removeItem('ownlog.accessToken')`)
  await page.goto(`${BASE}/login`)
  await page.waitFor(`document.querySelectorAll('[data-social-login] a').length > 0`, 10000)
  const buttons = await page.eval(`[...document.querySelectorAll('[data-social-login] a')].map(a => a.getAttribute('href') + '|' + a.innerText.trim())`)
  check('1. 로그인 화면: 키가 있는 서비스만, 정해진 순서로 버튼', JSON.stringify(buttons.map((b) => b.split('|')[0]))
    === JSON.stringify(providers.map((p) => `/oauth2/authorization/${p}`)), buttons.join(', '))
  await page.screenshot(join(SHOTS, '96-social-login.png'))

  // 버튼이 가는 주소 → 서비스 동의 화면으로 302 (브라우저로 따라가지 않고 주소만 확인)
  const res = await fetch(`${BASE}/oauth2/authorization/${providers[0]}`, { redirect: 'manual' })
  const location = res.headers.get('location') ?? ''
  const q = new URL(location).searchParams
  check(`2. 버튼 → ${providers[0]} 동의 화면 (state, 돌아올 주소 /login/oauth2/code/${providers[0]})`,
    res.status === 302 && q.get('state') && q.get('redirect_uri')?.endsWith(`/login/oauth2/code/${providers[0]}`),
    `${res.status} ${location.split('?')[0]}`)

  await page.goto(`${BASE}/signup`)
  await page.waitFor(`document.querySelectorAll('[data-social-login] a').length > 0`, 10000)
  check('3. 회원가입 화면에도 같은 버튼', (await page.eval(`document.querySelectorAll('[data-social-login] a').length`)) === providers.length)

  await page.goto(`${BASE}/login?oauthError=email_in_use`)
  await page.waitFor(`document.body.innerText.includes('이미 가입한 계정')`)
  check('4. 이메일이 겹쳐 돌아오면 → "이메일과 비밀번호로 로그인" 안내', true)

  await page.goto(`${BASE}/oauth/callback#code=forged-code`)
  await page.waitFor(`document.body.innerText.includes('로그인 시간이 지났어요')`)
  const hash = await page.eval('location.hash')
  check('5. 위조·만료된 코드 → 안내 + 주소에서 코드를 지움', hash === '', `hash="${hash}"`)

  await page.viewport(375, 800)
  await page.goto(`${BASE}/login`)
  await page.waitFor(`document.querySelectorAll('[data-social-login] a').length > 0`, 10000)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('6. 375px 로그인 화면 → 가로 스크롤 없음', sw <= 375, `scrollWidth ${sw}`)
} catch (e) {
  if (e.message !== '키 없음') check('실행 중 오류', false, e.message)
} finally {
  page.close()
}
for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
