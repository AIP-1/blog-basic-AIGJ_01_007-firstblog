// 10일차 읽기 화면 확인: 블로그 홈, 글 상세(마크다운·XSS 차단·좋아요·북마크·댓글·신고), 검색, 태그, 404
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const A = 'qa45650' // 글 주인
const B = 'ui792202' // 방문자
const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })

async function api(method, path, token, body) {
  const res = await fetch(API + path, {
    method,
    headers: { ...(token && { Authorization: `Bearer ${token}` }), ...(body && { 'Content-Type': 'application/json' }) },
    body: body && JSON.stringify(body),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text}`)
  return text ? JSON.parse(text) : null
}
const tokenOf = async (u) => (await api('POST', '/auth/login', null, { email: `${u}@test.com`, password: 'ownlog1234' })).accessToken

// 준비: A의 카테고리 "개발", 마크다운 + 태그 + XSS 시도 글, 비공개 글
const tA = await tokenOf(A)
const meA = await api('GET', '/users/me', tA)
const cat = await api('POST', `/blogs/${meA.blog.id}/categories`, tA, { name: '개발' + Date.now().toString().slice(-4) })
const xssPost = await api('POST', '/posts', tA, {
  title: '마크다운과 XSS 확인',
  content: '## 소제목\n\n**굵게**와 `코드`\n\n<script>window.__xss = 1</script>\n\n<img src=x onerror="window.__xss = 2">\n\n[링크](javascript:window.__xss=3)',
  categoryId: cat.id, topicId: 1, tags: ['Java', 'React'],
})
const privatePost = await api('POST', '/posts', tA, { title: '비공개 글', content: '비밀', visibility: 'PRIVATE' })

const page = await launch({ port: 9335 })
const login = async (u) => {
  await page.goto(`${BASE}/login`)
  await page.type('input[name=email]', `${u}@test.com`)
  await page.type('input[name=password]', 'ownlog1234')
  await page.click('button[type=submit]')
  await page.waitFor(`location.pathname === '/' && document.body.innerText.includes('로그아웃')`)
}
const logout = async () => {
  await page.eval(`localStorage.removeItem('ownlog.accessToken')`)
}
const detailUrl = `${BASE}/@${A}/posts/${xssPost.id}`

try {
  // 이전 실행에서 남은 로그인 토큰을 지우고 방문자로 시작해요
  await page.goto(`${BASE}/`)
  await logout()

  // 1. 블로그 홈 (방문자)
  await page.goto(`${BASE}/@${A}`)
  await page.waitFor(`document.querySelectorAll('main article').length > 0`)
  const home = await page.eval(`({ title: document.querySelector('main h1').innerText,
      cats: [...document.querySelectorAll('nav[aria-label="카테고리"] a')].map(a => a.innerText.replace(/\\n/g, ' ')),
      owner: document.body.innerText.includes('카테고리 관리') })`)
  check('1. 블로그 홈: 블로그 이름, 카테고리 사이드바(글 수), 방문자에게 관리 버튼 없음',
    home.title.endsWith('의 블로그') && home.cats[0].startsWith('전체') && !home.owner, home.cats.slice(0, 3).join(' | '))
  await page.eval(`[...document.querySelectorAll('nav[aria-label="카테고리"] a')].find(a => a.innerText.startsWith('${cat.name}')).click()`)
  await page.waitFor(`location.search === '?category=${cat.id}' && document.querySelectorAll('main article').length === 1`)
  check('   카테고리 클릭 → 그 카테고리 글만', true)
  await page.screenshot(join(SHOTS, '7-blog-home.png'))

  // 2. 글 상세 (방문자): 마크다운, 태그, 조회수, XSS 차단
  await page.goto(detailUrl)
  await page.waitFor(`document.querySelector('.markdown h2')`)
  const d = await page.eval(`({ h2: document.querySelector('.markdown h2').innerText,
      strong: !!document.querySelector('.markdown strong'),
      scripts: document.querySelectorAll('.markdown script').length,
      onerror: !!document.querySelector('.markdown [onerror]'),
      jsLink: [...document.querySelectorAll('.markdown a')].some(a => (a.getAttribute('href') || '').startsWith('javascript')),
      xss: window.__xss ?? null,
      tags: [...document.querySelectorAll('a[href^="/tags/"]')].map(a => a.innerText),
      text: document.body.innerText })`)
  check('2. 상세: 마크다운이 h2·굵게로 표시, 태그 이름순', d.h2 === '소제목' && d.strong && d.tags.join() === '#Java,#React', d.tags.join())
  check('   XSS 차단: <script>·onerror·javascript: 링크 모두 제거, 실행 안 됨',
    d.scripts === 0 && !d.onerror && !d.jsLink && d.xss === null, `script ${d.scripts}, onerror ${d.onerror}, js링크 ${d.jsLink}, 실행 ${d.xss}`)
  check('   조회수 1 이상, 방문자에게 신고 버튼, 수정/삭제 없음',
    /조회 [1-9]/.test(d.text) && d.text.includes('신고') && !d.text.includes('삭제'))
  await page.screenshot(join(SHOTS, '8-post-detail.png'))

  // 3. 방문자가 좋아요 → 로그인 화면 (돌아올 주소 포함)
  await page.eval(`[...document.querySelectorAll('button')].find(b => b.innerText.startsWith('♡')).click()`)
  await page.waitFor(`location.pathname === '/login'`)
  check('3. 방문자가 좋아요 → 로그인 화면으로 (redirect에 글 주소)', decodeURIComponent(await page.path()).includes(`/posts/${xssPost.id}`))

  // 4. B로 로그인 → 좋아요·북마크, 새로고침 유지
  await login(B)
  await page.goto(detailUrl)
  await page.waitFor(`document.querySelector('.markdown h2')`)
  await page.eval(`[...document.querySelectorAll('button')].find(b => b.innerText.startsWith('♡')).click()`)
  await page.waitFor(`[...document.querySelectorAll('button')].some(b => b.innerText === '♥ 1')`)
  await page.eval(`[...document.querySelectorAll('button')].find(b => b.innerText === '북마크').click()`)
  await page.waitFor(`[...document.querySelectorAll('button')].some(b => b.innerText === '북마크됨')`)
  await page.goto(detailUrl)
  await page.waitFor(`document.querySelector('.markdown h2')`)
  const kept = await page.eval(`[...document.querySelectorAll('button')].map(b => b.innerText)`)
  check('4. 로그인 후 좋아요 ♥1·북마크 → 새로고침해도 유지', kept.includes('♥ 1') && kept.includes('북마크됨'))

  // 5. 댓글 → 답글 → 수정(수정됨)
  await page.eval(`(() => { const t = document.querySelector('textarea'); const s = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set; s.call(t, '<b>굵게 안 됨</b> 첫 댓글'); t.dispatchEvent(new Event('input', { bubbles: true })) })()`)
  await page.clickText('댓글 등록')
  await page.waitFor(`document.body.innerText.includes('첫 댓글')`)
  const raw = await page.eval(`!document.querySelector('section b') && document.body.innerText.includes('<b>굵게 안 됨</b>')`)
  check('5. 댓글 등록, HTML은 글자 그대로 보임 (일반 텍스트)', raw)
  await page.clickText('답글')
  await page.eval(`(() => { const ts = document.querySelectorAll('textarea'); const t = ts[ts.length - 1]; const s = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set; s.call(t, '답글입니다'); t.dispatchEvent(new Event('input', { bubbles: true })) })()`)
  await page.clickText('답글 등록')
  await page.waitFor(`document.querySelector('ul ul li') && document.querySelector('ul ul li').innerText.includes('답글입니다')`)
  check('   답글 → 들여쓰기된 목록에 표시', true)
  await page.eval(`[...document.querySelectorAll('button')].filter(b => b.innerText === '수정').pop().click()`)
  await page.eval(`(() => { const ts = document.querySelectorAll('textarea'); const t = ts[ts.length - 1]; const s = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set; s.call(t, '고친 답글'); t.dispatchEvent(new Event('input', { bubbles: true })) })()`)
  await page.eval(`[...document.querySelectorAll('button[type=submit]')].find(b => b.innerText === '수정').click()`)
  await page.waitFor(`document.body.innerText.includes('고친 답글') && document.body.innerText.includes('(수정됨)')`)
  check('   답글 수정 → "(수정됨)" 표시', true)

  // 6. 글 신고 → 접수, 다시 신고 → 이미 신고
  for (const expected of ['신고가 접수되었어요.', '이미 신고했어요.']) {
    await page.eval(`[...document.querySelectorAll('button')].find(b => b.innerText === '신고' && !b.closest('li')).click()`)
    await page.waitFor(`document.querySelector('[role=dialog]')`)
    await page.eval(`document.querySelector('[role=dialog] button[type=submit]').click()`)
    await page.waitFor(`document.querySelector('[role=dialog]').innerText.includes('${expected}')`)
    await page.eval(`(document.querySelector('[role=dialog] button[type=button]')).click()`)
    await page.waitFor(`!document.querySelector('[role=dialog]')`)
  }
  check('6. 글 신고 → "접수", 다시 신고 → "이미 신고했어요."', true)

  // 7. 주인 A: 남의 댓글 삭제 가능, 신고 버튼 없음, 수정·삭제 링크 있음
  await logout()
  await login(A)
  await page.goto(detailUrl)
  await page.waitFor(`document.body.innerText.includes('첫 댓글')`)
  const ownerText = await page.text()
  check('7. 주인: 수정·삭제 링크 있음, 글 신고 버튼 없음', ownerText.includes('수정') &&
    !(await page.eval(`[...document.querySelectorAll('button')].some(b => b.innerText === '신고' && !b.closest('li'))`)))
  await page.eval(`window.confirm = () => true`)
  await page.eval(`[...[...document.querySelectorAll('li')].find(li => li.innerText.includes('첫 댓글')).querySelectorAll('button')].find(b => b.innerText === '삭제').click()`)
  await page.waitFor(`document.body.innerText.includes('삭제된 댓글입니다')`)
  check('   주인이 답글 달린 남의 댓글 삭제 → "삭제된 댓글입니다"', true)

  // 8. 검색, 태그, 404, 비공개 글
  await page.goto(`${BASE}/`)
  await page.type('header input[type=search]', '부산')
  await page.eval(`document.querySelector('header form').requestSubmit()`)
  await page.waitFor(`location.pathname === '/search' && document.body.innerText.includes('검색 결과')`)
  await page.waitFor(`document.querySelectorAll('main article').length > 0`)
  check('8. 헤더 검색 "부산" → /search 결과 카드', true, `${await page.eval(`document.querySelectorAll('main article').length`)}개`)
  await page.goto(`${BASE}/search?q=${encodeURIComponent('%')}`)
  await page.waitFor(`document.body.innerText.includes('검색 결과가 없어요.')`)
  check('   검색 "%" → 결과 없음 (이스케이프)', true)
  await page.goto(`${BASE}/tags/java`)
  await page.waitFor(`document.querySelector('main h1').innerText === '#Java' && document.querySelectorAll('main article').length > 0`)
  check('9. /tags/java → 대표 표기 #Java, 글 목록', true)
  await page.goto(`${BASE}/abc`)
  await page.waitFor(`document.body.innerText.includes('존재하지 않는 페이지예요')`)
  check('10. @ 없는 주소 /abc → "존재하지 않는 페이지예요"', true)
  await page.goto(`${BASE}/@nobody_here`)
  await page.waitFor(`document.body.innerText.includes('존재하지 않는 블로그예요')`)
  check('    없는 아이디 /@nobody_here → "존재하지 않는 블로그예요"', true)
  await logout()
  await page.goto(`${BASE}/@${A}/posts/${privatePost.id}`)
  await page.waitFor(`document.body.innerText.includes('존재하지 않는 글이에요')`)
  check('11. 방문자가 비공개 글 주소 → "존재하지 않는 글이에요"', true)

  // 12. 휴대폰 상세
  await page.viewport(375, 800)
  await page.goto(detailUrl)
  await page.waitFor(`document.querySelector('.markdown h2')`)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('12. 375px 글 상세 → 가로 스크롤 없음', sw <= 375, `scrollWidth ${sw}`)
  await page.screenshot(join(SHOTS, '9-post-detail-mobile.png'))
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}

for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
