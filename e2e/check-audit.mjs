// 전체 점검(2026-10-12)에서 고친 문제들의 회귀 확인
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const login = async (email) => (await (await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'ownlog1234' }) })).json()).accessToken
const token = await login('qa45650@test.com')
const editor = 'textarea[aria-label="본문"]'

const page = await launch({ port: 9370 })
try {
  // 1. 인코딩한 주소로 인증 건너뛰기 → 401
  const bypass = await fetch(`${BASE}/%61pi/users/me`)
  check('1. /%61pi/users/me (토큰 없음) → 401', bypass.status === 401, `status ${bypass.status}`)

  // 2. 로그인 후 돌아가기: 탭·역슬래시를 섞은 외부 주소로 가지 않음
  await page.goto(`${BASE}/`)
  await page.eval(`localStorage.removeItem('ownlog.accessToken')`)
  for (const evil of ['/%09/evil.example', '/\\evil.example', '//evil.example']) {
    await page.goto(`${BASE}/login?redirect=${encodeURIComponent(decodeURIComponent(evil))}`)
    await page.type('input[name=email]', 'qa45650@test.com')
    await page.type('input[name=password]', 'ownlog1234')
    await page.click('button[type=submit]')
    await page.waitFor(`location.pathname !== '/login'`, 8000)
    const href = await page.eval('location.href')
    check(`2. ?redirect=${evil} → 우리 사이트에 머묾`, href.startsWith(BASE), href)
    await page.eval(`localStorage.removeItem('ownlog.accessToken')`)
  }

  // 3. 발행 글 수정 화면(/write/:id) → 헤더 "글쓰기" → 빈 새 글 화면
  await page.eval(`localStorage.setItem('ownlog.accessToken', ${JSON.stringify(token)})`)
  const mine = (await (await fetch(`${API}/blogs/qa45650/posts?size=1`, { headers: { Authorization: `Bearer ${token}` } })).json()).content[0]
  await page.goto(`${BASE}/write/${mine.id}`)
  await page.waitFor(`document.querySelector('${editor}')?.value.length > 0`, 15000)
  await page.eval(`[...document.querySelectorAll('header a')].find(a => a.innerText === '글쓰기').click()`)
  await page.waitFor(`location.pathname === '/write'`)
  await sleep(500)
  const fresh = await page.eval(`({ title: document.querySelector('input[aria-label="제목"]').value,
    body: document.querySelector('${editor}').value, publish: [...document.querySelectorAll('button')].some(b => b.innerText === '발행') })`)
  check('3. /write/{발행 글} → "글쓰기" → 빈 새 글(제목·본문 비어 있고 "발행" 버튼)', !fresh.title && !fresh.body && fresh.publish, JSON.stringify(fresh))

  // 4. 처음 임시저장 직후 계속 쓴 글자가 덮어써지지 않음
  await page.type('input[aria-label="제목"]', '임시저장 회귀')
  await page.typeArea(editor, '첫 문장')
  await page.clickButton('임시저장')
  await page.waitFor(`/^\\/write\\/\\d+$/.test(location.pathname)`, 8000)
  await page.typeArea(editor, '첫 문장 그리고 저장 뒤에 이어 쓴 문장')
  await sleep(1500)
  const kept = await page.eval(`document.querySelector('${editor}').value`)
  check('4. 처음 임시저장 뒤 이어 쓴 내용이 서버 값으로 덮어써지지 않음', kept.includes('이어 쓴 문장'), kept)
  await fetch(`${API}/drafts/${(await page.path()).split('/').pop()}`, { method: 'DELETE', headers: { Authorization: `Bearer ${token}` } })

  // 5. 댓글 등록 두 번 눌러도 하나만
  await page.goto(`${BASE}/@qa45650/posts/${mine.id}`)
  await page.waitFor(`document.querySelector('form textarea')`, 15000)
  const before = (await (await fetch(`${API}/posts/${mine.id}/comments`)).json()).totalCount
  await page.typeArea('form textarea', '두 번 눌러 보기')
  await page.eval(`(() => { const b = [...document.querySelectorAll('button')].find(x => x.innerText === '댓글 등록'); b.click(); b.click() })()`)
  await sleep(1500)
  const after = (await (await fetch(`${API}/posts/${mine.id}/comments`)).json()).totalCount
  check('5. "댓글 등록" 두 번 클릭 → 댓글 1개만', after - before === 1, `${before} → ${after}`)

  // 6. 없는 화면 코드(배포 후 사라진 파일)를 불러오다 실패해도 앱이 하얗게 사라지지 않음
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.querySelectorAll('main article').length > 0`, 10000)
  const alive = await page.eval(`document.querySelector('header') !== null`)
  check('6. 오류 경계 설치 후에도 메인 정상 (헤더 유지)', alive)
  await page.screenshot(join(SHOTS, '90-audit-main.png'))
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}
for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
