// 12일차 화면 확인: 설정(프로필·사진·블로그·비밀번호·탈퇴), 카테고리 관리, 북마크
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const C = 'set' + Date.now().toString().slice(-6)
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

// 확인 전용 회원 C, 북마크할 A의 글
await api('POST', '/auth/signup', null, { email: `${C}@test.com`, password: 'ownlog1234', passwordConfirm: 'ownlog1234', username: C, nickname: '설정테스트' })
const tC = (await api('POST', '/auth/login', null, { email: `${C}@test.com`, password: 'ownlog1234' })).accessToken
const aPosts = await api('GET', '/blogs/qa45650/posts')
await api('PUT', `/posts/${aPosts.content[0].id}/bookmark`, tC)

const page = await launch({ port: 9337 })
const text = () => page.text()
const waitText = (t, ms) => page.waitFor(`document.body.innerText.includes(${JSON.stringify(t)})`, ms)
try {
  await page.goto(`${BASE}/`)
  await page.eval(`localStorage.setItem('ownlog.accessToken', ${JSON.stringify(tC)})`)

  // 1. 프로필: 사진 업로드, 닉네임·소개 저장 → 블로그 홈에 반영
  await page.goto(`${BASE}/settings`)
  await page.waitFor(`document.querySelector('input[name=nickname]')`)
  await page.uploadFile('input[aria-label="프로필 사진 파일"]', join(SHOTS, 'upload-test.png'))
  await page.waitFor(`document.querySelector('img[alt="프로필 사진"]')`, 10000)
  await page.type('input[name=nickname]', '새닉네임')
  await page.type('input[name=bio]', '프론트엔드 테스트 중')
  await page.clickButton('프로필 저장')
  await waitText('프로필을 저장했어요.')
  await page.type('input[name=blogName]', '설정 테스트 블로그')
  await page.type('input[name=description]', '블로그 소개 문장')
  await page.clickButton('블로그 저장')
  await waitText('블로그 정보를 저장했어요.')
  await page.goto(`${BASE}/@${C}`)
  await page.waitFor(`document.querySelector('main h1')`)
  const home = await page.eval(`({ name: document.querySelector('main h1').innerText, body: document.querySelector('main header').innerText,
      img: !!document.querySelector('main header img[src^="/uploads/"]') })`)
  check('1. 프로필 사진·닉네임·소개, 블로그 이름·소개 저장 → 블로그 홈에 반영',
    home.name === '설정 테스트 블로그' && home.body.includes('블로그 소개 문장') && home.body.includes('새닉네임') && home.body.includes('프론트엔드 테스트 중') && home.img,
    JSON.stringify(home))

  // 2. 비밀번호: 틀린 현재 비밀번호 → 에러(로그아웃 안 됨), 맞으면 변경
  await page.goto(`${BASE}/settings`)
  await page.waitFor(`document.querySelector('input[name=currentPassword]')`)
  await page.type('input[name=currentPassword]', 'wrong1234')
  await page.type('input[name=newPassword]', 'newpass5678')
  await page.type('input[name=newPasswordConfirm]', 'newpass5678')
  await page.clickButton('비밀번호 변경')
  await waitText('현재 비밀번호가 올바르지 않아요.')
  check('2. 틀린 현재 비밀번호 → 에러 표시, 로그인 유지 (401로 튕기지 않음)', (await page.path()) === '/settings' && (await text()).includes('로그아웃'))
  await page.type('input[name=currentPassword]', 'ownlog1234')
  await page.clickButton('비밀번호 변경')
  await waitText('비밀번호를 바꿨어요.')
  check('   올바르면 "비밀번호를 바꿨어요."', true)
  await page.screenshot(join(SHOTS, '11-settings.png'))

  // 3. 카테고리 관리: 추가(비공개 포함), 순서 변경, "미분류" 이름 변경, 비공개 카테고리 삭제 → 글 이동 안내
  await page.goto(`${BASE}/settings/categories`)
  await page.waitFor(`document.body.innerText.includes('미분류')`)
  for (const [name, priv] of [['개발', false], ['일기', true]]) {
    await page.type('input[aria-label="새 카테고리 이름"]', name)
    if (priv) await page.eval(`document.querySelector('form input[type=checkbox]').click()`)
    await page.clickButton('추가')
    await waitText('카테고리를 만들었어요.')
    await page.waitFor(`document.querySelector('input[aria-label="${name} 이름"]')`)
  }
  const names = () => page.eval(`[...document.querySelectorAll('ul input[aria-label$=" 이름"]')].map(i => i.value)`)
  check('3. 카테고리 "개발", "일기"(비공개) 추가', (await names()).join() === '미분류,개발,일기')
  await page.click('button[aria-label="일기 위로"]')
  await page.waitFor(`[...document.querySelectorAll('ul input[aria-label$=" 이름"]')].map(i => i.value).join() === '미분류,일기,개발'`)
  check('   "일기" 위로 → 순서 미분류, 일기, 개발', true)
  await page.type('input[aria-label="미분류 이름"]', '기타')
  await page.eval(`[...document.querySelector('input[aria-label="미분류 이름"]').closest('li').querySelectorAll('button')].find(b => b.innerText === '저장').click()`)
  await page.waitFor(`document.querySelector('input[aria-label="기타 이름"]')`)
  check('   기본 카테고리 이름 "미분류" → "기타" (삭제 버튼 대신 "기본")', (await page.eval(`document.querySelector('input[aria-label="기타 이름"]').closest('li').innerText`)).includes('기본'))

  const me = await api('GET', '/users/me', tC)
  const side = await api('GET', `/blogs/${C}/categories`, tC)
  const diary = side.categories.find((c) => c.name === '일기')
  const diaryPost = await api('POST', '/posts', tC, { title: '일기 글', content: '비밀 일기', categoryId: diary.id })
  await page.goto(`${BASE}/settings/categories`)
  await page.waitFor(`document.querySelector('input[aria-label="일기 이름"]')`)
  await page.eval(`window.confirm = () => true`)
  await page.eval(`[...document.querySelector('input[aria-label="일기 이름"]').closest('li').querySelectorAll('button')].find(b => b.innerText === '삭제').click()`)
  await waitText('글 1개를 "미분류"로 옮겼어요.')
  const moved = await api('GET', `/blogs/${C}/posts/${diaryPost.id}`, tC)
  check('   비공개 카테고리 "일기" 삭제 → "글 1개를 옮겼어요", 옮긴 글은 기본 카테고리·비공개',
    moved.category.name === '기타' && moved.visibility === 'PRIVATE', `${moved.category.name} / ${moved.visibility}`)
  void me

  // 4. 북마크 목록
  await page.goto(`${BASE}/bookmarks`)
  await page.waitFor(`document.querySelectorAll('main article').length > 0`)
  check('4. 북마크 → 북마크한 글 카드', (await page.eval(`document.querySelectorAll('main article').length`)) === 1)

  // 4-1. 보호 화면(/settings)에서 헤더 로그아웃 → 메인으로 (로그인 화면으로 튕기지 않음)
  await page.goto(`${BASE}/settings`)
  await page.waitFor(`document.querySelector('input[name=withdrawPassword]')`)
  await page.clickButton('로그아웃')
  await page.waitFor(`document.body.innerText.includes('회원가입')`)
  await page.sleep(500)
  check('4-1. /settings에서 로그아웃 → 메인 (로그인 화면으로 가지 않음)', (await page.path()) === '/', await page.path())
  const tC2 = (await api('POST', '/auth/login', null, { email: `${C}@test.com`, password: 'newpass5678' })).accessToken
  await page.eval(`localStorage.setItem('ownlog.accessToken', ${JSON.stringify(tC2)})`)

  // 5. 탈퇴: 틀린 비밀번호 → 에러, 맞으면 로그아웃 + 메인, 다시 로그인 불가
  await page.goto(`${BASE}/settings`)
  await page.waitFor(`document.querySelector('input[name=withdrawPassword]')`)
  await page.eval(`window.confirm = () => true`)
  await page.type('input[name=withdrawPassword]', 'ownlog1234') // 바뀌기 전 비밀번호
  await page.clickButton('탈퇴하기')
  await waitText('현재 비밀번호가 올바르지 않아요.')
  check('5. 탈퇴: 틀린 비밀번호 → 에러', true)
  await page.type('input[name=withdrawPassword]', 'newpass5678')
  await page.clickButton('탈퇴하기')
  await page.waitFor(`location.pathname === '/' && document.body.innerText.includes('회원가입')`)
  check('   맞는 비밀번호 → 로그아웃되고 메인으로', (await page.eval(`localStorage.getItem('ownlog.accessToken')`)) === null)
  const relogin = await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: `${C}@test.com`, password: 'newpass5678' }) })
  check('   탈퇴 후 다시 로그인 → 401', relogin.status === 401)

  // 6. 휴대폰 설정 화면
  await page.viewport(375, 800)
  await page.eval(`localStorage.setItem('ownlog.accessToken', ${JSON.stringify((await api('POST', '/auth/login', null, { email: 'qa45650@test.com', password: 'ownlog1234' })).accessToken)})`)
  await page.goto(`${BASE}/settings/categories`)
  await page.waitFor(`document.querySelector('ul li')`)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('6. 375px 카테고리 관리 → 가로 스크롤 없음', sw <= 375, `scrollWidth ${sw}`)
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}

for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
