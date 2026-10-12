// [[글]] 연결 화면 확인: 자동완성 → 삽입 → 발행 → 링크 이동 → 백링크, 볼 수 없는 글 표시
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const A = 'qa45650', B = 'ui792202'
const TS = Date.now().toString().slice(-6)
const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })
const login = async (u) => (await (await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: `${u}@test.com`, password: 'ownlog1234' }) })).json()).accessToken
const create = async (token, body) => (await (await fetch(`${API}/posts`, { method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify(body) })).json()).id
const tA = await login(A), tB = await login(B)
const targetTitle = `링크대상${TS} JWT 정리`
const targetId = await create(tA, { title: targetTitle, content: '연결될 글' })
const secretId = await create(tB, { title: `비밀${TS}`, content: 'x', visibility: 'PRIVATE' })
const editor = 'textarea[aria-label="본문"]'

const page = await launch({ port: 9339 })
try {
  await page.goto(`${BASE}/`)
  await page.eval(`localStorage.setItem('ownlog.accessToken', ${JSON.stringify(tA)})`)
  await page.goto(`${BASE}/write`)
  await page.waitFor(`document.querySelector('${editor}')`, 10000)

  // 1. [[ + 검색어 → 자동완성에 내 글
  await page.type('input[aria-label="제목"]', `연결하는 글 ${TS}`)
  await page.typeArea(editor, `참고한 글: [[링크대상${TS}`)
  await page.waitFor(`[...document.querySelectorAll('[role=option]')].some(o => o.innerText.includes(${JSON.stringify(targetTitle)}))`, 5000)
  const optionText = await page.eval(`document.querySelector('[role=option]').innerText`)
  check('1. [[ + 제목 입력 → 자동완성에 내 글("내 글" 표시)', optionText.includes('내 글'), optionText.replace(/\n/g, ' '))
  await page.screenshot(join(SHOTS, '30-link-suggest.png'))

  // 2. Enter → [[번호|제목]] 삽입
  await page.eval(`document.querySelector('${editor}').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))`)
  await page.waitFor(`document.querySelector('${editor}').value.includes('[[${targetId}|')`)
  const value = await page.eval(`document.querySelector('${editor}').value`)
  check('2. Enter → 본문에 [[번호|제목]] 삽입, 자동완성 닫힘',
    value === `참고한 글: [[${targetId}|${targetTitle}]]` && !(await page.eval(`!!document.querySelector('[role=listbox]')`)), value)

  // 3. 툴바 "🔗 글 연결" → 자동완성 열림 (검색어 없음 → 내 최근 글)
  await page.clickButton('🔗 글 연결')
  await page.waitFor(`document.querySelectorAll('[role=option]').length > 0`, 5000)
  check('3. 툴바 "🔗 글 연결" → 자동완성 창 열림', true)
  await page.eval(`document.querySelector('${editor}').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))`)
  await page.waitFor(`!document.querySelector('[role=listbox]')`)

  // 4. 볼 수 없는 글 링크도 넣고 발행 → 상세에서 링크/잠금 표시
  await page.typeArea(editor, `참고한 글: [[${targetId}|${targetTitle}]]\n\n남의 비공개 글: [[${secretId}|몰래 본 글]]\n\n\`[[${targetId}|코드 안]]\``)
  await page.clickButton('발행')
  await page.waitFor(`/\\/posts\\/\\d+$/.test(location.pathname) && document.querySelector('.markdown')`, 10000)
  const sourcePath = await page.path()
  const detail = await page.eval(`({
    link: document.querySelector('.markdown a.post-link')?.getAttribute('href'),
    linkText: document.querySelector('.markdown a.post-link')?.innerText,
    locked: document.querySelector('.markdown').innerText.includes('🔒 볼 수 없는 글'),
    leaked: document.body.innerText.includes('비밀${TS}'),
    code: document.querySelector('.markdown code')?.innerText })`)
  check('4. 상세: 볼 수 있는 글은 🔗 링크, 남의 비공개 글은 "🔒 볼 수 없는 글"(제목 노출 없음), 코드 안은 그대로',
    detail.link === `/@${A}/posts/${targetId}` && detail.locked && !detail.leaked && detail.code === `[[${targetId}|코드 안]]`,
    JSON.stringify(detail))
  await page.screenshot(join(SHOTS, '31-link-detail.png'))

  // 5. 링크 클릭 → 대상 글로 이동(새로고침 없이), 하단 "이 글을 언급한 글"
  await page.eval(`window.__spa = true; document.querySelector('.markdown a.post-link').click()`)
  await page.waitFor(`location.pathname === '/@${A}/posts/${targetId}' && document.body.innerText.includes('이 글을 언급한 글')`, 8000)
  const back = await page.eval(`({ spa: window.__spa === true,
    item: document.querySelector('[aria-label="이 글을 언급한 글"] a')?.getAttribute('href') })`)
  check('5. 링크 클릭 → 화면 안 이동, 대상 글에 "이 글을 언급한 글"로 원래 글 표시', back.spa && back.item === sourcePath, JSON.stringify(back))
  await page.screenshot(join(SHOTS, '32-backlinks.png'))

  // 6. 방문자(로그아웃)도 백링크를 봐요
  await page.eval(`localStorage.removeItem('ownlog.accessToken')`)
  await page.goto(`${BASE}/@${A}/posts/${targetId}`)
  await page.waitFor(`document.body.innerText.includes('이 글을 언급한 글')`)
  check('6. 로그아웃 방문자도 백링크 표시', true)

  // 7. 휴대폰 글쓰기에서 자동완성 창 가로 스크롤 없음
  await page.eval(`localStorage.setItem('ownlog.accessToken', ${JSON.stringify(tA)})`)
  await page.viewport(375, 800)
  await page.goto(`${BASE}/write`)
  await page.waitFor(`document.querySelector('${editor}')`, 10000)
  await page.typeArea(editor, '[[링크대상')
  await page.waitFor(`document.querySelectorAll('[role=option]').length > 0`, 5000)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('7. 375px 자동완성 → 가로 스크롤 없음', sw <= 375, `scrollWidth ${sw}`)
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}
for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
