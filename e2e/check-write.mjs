// 11일차 쓰기 화면 확인: 로그인 보호, 임시저장(주소 변경·시각), 목록·이어 쓰기, 태그, 이미지·썸네일 업로드, 발행, 수정, 검증
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { crc32, deflateSync } from 'node:zlib'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS } from './common.mjs'

const B = 'ui792202'
const results = []
const check = (name, ok, detail = '') => results.push({ name, ok, detail })

// 업로드용 실제 PNG 파일
function png(w, h, [r, g, b]) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4); len.writeUInt32BE(data.length)
    const td = Buffer.concat([Buffer.from(type), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td))
    return Buffer.concat([len, td, crc])
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 2
  const row = Buffer.concat([Buffer.from([0]), Buffer.alloc(w * 3).map((_, i) => [r, g, b][i % 3])])
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(Buffer.concat(Array.from({ length: h }, () => row)))), chunk('IEND', Buffer.alloc(0))])
}
const IMAGE = join(SHOTS, 'upload-test.png')
writeFileSync(IMAGE, png(320, 180, [236, 72, 153]))

const page = await launch({ port: 9336 })
const editor = 'textarea[aria-label="본문"]'
try {
  await page.goto(`${BASE}/`)
  await page.eval(`localStorage.removeItem('ownlog.accessToken')`)

  // 1. 방문자는 글쓰기 → 로그인 화면 (redirect=/write)
  await page.goto(`${BASE}/write`)
  await page.waitFor(`location.pathname === '/login'`)
  check('1. 방문자가 /write → 로그인 화면 (돌아올 주소 /write)', decodeURIComponent(await page.path()).includes('redirect=/write'))
  await page.type('input[name=email]', `${B}@test.com`)
  await page.type('input[name=password]', 'ownlog1234')
  await page.click('button[type=submit]')
  await page.waitFor(`location.pathname === '/write' && document.querySelector('${editor}')`, 10000)
  check('   로그인 후 글쓰기 화면으로 돌아옴, 편집기 표시 (지연 로딩)', true)

  // 2. 제목 없이 임시저장 → 주소 /write/{id}, "임시저장됨 (HH:mm)"
  await page.typeArea(editor, '쓰다 만 글')
  await page.clickButton('임시저장')
  await page.waitFor(`/^\\/write\\/\\d+$/.test(location.pathname) && document.body.innerText.includes('임시저장됨 (')`)
  const draftPath = await page.path()
  check('2. 제목 없이 임시저장 → 주소가 /write/{id}로, "임시저장됨 (HH:mm)" 표시', /임시저장됨 \(\d{2}:\d{2}\)/.test(await page.text()), draftPath)

  // 3. 임시저장 목록 → "제목 없음" → 이어 쓰기
  await page.goto(`${BASE}/drafts`)
  await page.waitFor(`document.body.innerText.includes('제목 없음')`)
  await page.eval(`[...document.querySelectorAll('main a')].find(a => a.innerText.includes('제목 없음')).click()`)
  await page.waitFor(`location.pathname === '${draftPath}' && document.querySelector('${editor}') && document.querySelector('${editor}').value === '쓰다 만 글'`, 10000)
  check('3. 임시저장 목록 "제목 없음" → 이어 쓰기 (본문 복원, 임시저장 버튼 있음)',
    (await page.eval(`[...document.querySelectorAll('button')].some(b => b.innerText === '임시저장')`)))

  // 4. 태그 입력: Enter로 추가, 대소문자 중복 무시, 공백 태그 거부
  for (const t of ['Java', 'java', 'React']) {
    await page.type('input[aria-label="태그"]', t)
    await page.eval(`document.querySelector('input[aria-label="태그"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))`)
  }
  await page.type('input[aria-label="태그"]', 'spring boot')
  await page.eval(`document.querySelector('input[aria-label="태그"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))`)
  await page.waitFor(`document.body.innerText.includes('태그는 한글, 영문, 숫자')`)
  const chips = await page.eval(`[...document.querySelectorAll('button[aria-label$=" 삭제"]')].map(b => b.getAttribute('aria-label').replace(' 삭제', ''))`)
  check('4. 태그 Java·java·React 입력 → 2개(대소문자 중복 무시), "spring boot" 거부', chips.join() === 'Java,React', chips.join())
  await page.type('input[aria-label="태그"]', '')

  // 5. 본문 이미지 넣기, 썸네일 올리기 (실제 업로드)
  await page.uploadFile('input[aria-label="본문 이미지 파일"]', IMAGE)
  await page.waitFor(`document.querySelector('${editor}').value.includes('![](/uploads/')`, 10000)
  await page.uploadFile('input[aria-label="썸네일 파일"]', IMAGE)
  await page.waitFor(`document.querySelector('img[alt="썸네일"]')`, 10000)
  check('5. 본문 이미지 업로드 → ![](/uploads/...) 삽입, 썸네일 업로드 → 미리보기', true)

  // 6. 빈 제목으로 발행 → 에러
  await page.clickButton('발행')
  await page.waitFor(`document.body.innerText.includes('제목을 입력해 주세요.')`)
  check('6. 빈 제목으로 발행 → "제목을 입력해 주세요." (임시저장 글 그대로)', (await page.path()) === draftPath)

  // 7. 제목 입력 후 발행 → 상세로, 태그·이미지 표시, 임시저장 목록에서 사라짐
  await page.type('input[aria-label="제목"]', '글쓰기 화면 테스트')
  const stale = await page.eval(`document.body.innerText.includes('제목을 입력해 주세요.') || document.body.innerText.includes('태그는 한글, 영문')`)
  check('   제목을 입력하고 태그 칸을 다시 쓰면 에러가 사라짐', !stale)
  await page.screenshot(join(SHOTS, '10-write.png'))
  await page.clickButton('발행')
  await page.waitFor(`location.pathname.startsWith('/@${B}/posts/') && document.querySelector('.markdown')`, 10000)
  const detail = await page.eval(`({ title: document.querySelector('h1').innerText,
      img: !!document.querySelector('.markdown img[src^="/uploads/"]'),
      tags: [...document.querySelectorAll('a[href^="/tags/"]')].map(a => a.innerText).join() })`)
  check('7. 발행 → 상세로 이동, 제목·본문 이미지·태그 표시', detail.title === '글쓰기 화면 테스트' && detail.img && detail.tags === '#Java,#React',
    JSON.stringify(detail))
  const postPath = await page.path()
  await page.goto(`${BASE}/drafts`)
  await page.waitFor(`document.body.innerText.includes('임시저장한 글이 없어요.') || !document.body.innerText.includes('제목 없음')`)
  check('   임시저장 목록에서 사라짐', !(await page.text()).includes('제목 없음'))

  // 8. 발행 글 수정: 임시저장 버튼 없음(D-01), 수정 완료 → 수정일 표시
  await page.goto(`${BASE}${postPath}`)
  await page.waitFor(`document.querySelector('.markdown')`)
  await page.clickText('수정')
  await page.waitFor(`document.querySelector('${editor}')`, 10000)
  const buttons = await page.eval(`[...document.querySelectorAll('button')].map(b => b.innerText)`)
  check('8. 발행 글 수정 화면: [수정 완료]만 있고 [임시저장] 없음 (D-01)', buttons.includes('수정 완료') && !buttons.includes('임시저장'))
  await page.typeArea(editor, (await page.eval(`document.querySelector('${editor}').value`)) + '\n\n수정한 문단')
  await page.clickButton('수정 완료')
  await page.waitFor(`document.body.innerText.includes('수정한 문단') && /\\(수정 \\d{4}\\.\\d{2}\\.\\d{2}\\)/.test(document.body.innerText)`, 10000)
  check('   수정 완료 → 상세에 "(수정 YYYY.MM.DD)" 표시', true)

  // 9. 휴대폰 글쓰기 화면
  await page.viewport(375, 800)
  await page.goto(`${BASE}/write`)
  await page.waitFor(`document.querySelector('${editor}')`, 10000)
  const sw = await page.eval('document.documentElement.scrollWidth')
  check('9. 375px 글쓰기 화면 → 가로 스크롤 없음', sw <= 375, `scrollWidth ${sw}`)
} catch (e) {
  check('실행 중 오류', false, e.message)
} finally {
  page.close()
}

for (const r of results) console.log(`${r.ok ? '✅' : '❌'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`)
process.exit(results.every((r) => r.ok) ? 0 : 1)
