// 화면 점검 전체 실행: node e2e/run-all.mjs   (BASE로 대상 주소를 바꿀 수 있어요)
// 점검마다 ✅/❌를 보여주고, 하나라도 실패하면 종료 코드 1이에요
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { DIR } from './common.mjs'

const CHECKS = ['auth', 'main', 'read', 'write', 'settings', 'capsule', 'links', 'universe', 'activity', 'expired', 'extra', 'audit', 'guide', 'social']
const only = process.argv.slice(2)   // 예: node e2e/run-all.mjs links extra
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const setup = spawnSync(process.execPath, [join(DIR, 'setup.mjs')], { stdio: 'inherit' })
if (setup.status !== 0) process.exit(1)

let passed = 0, failed = 0
for (const name of CHECKS.filter((c) => !only.length || only.includes(c))) {
  // 점검 하나가 멈춰도 전체가 멈추지 않게 4분 제한 (넘으면 실패로 세요)
  const res = spawnSync(process.execPath, [join(DIR, `check-${name}.mjs`)], { encoding: 'utf8', timeout: 240_000, killSignal: 'SIGKILL' })
  let out = (res.stdout ?? '') + (res.stderr ?? '')
  if (res.error?.code === 'ETIMEDOUT') out += `\n❌ ${name}: 4분 안에 끝나지 않아 중단했어요`
  const ok = (out.match(/✅/g) ?? []).length
  const bad = (out.match(/❌/g) ?? []).length
  passed += ok
  failed += bad
  console.log(`\n▶ ${name}: ✅ ${ok}  ❌ ${bad}`)
  console.log(out.trim().split('\n').filter((l) => l.startsWith('✅') || l.startsWith('❌')).map((l) => `  ${l}`).join('\n'))
  await sleep(1500)   // 앞 점검의 Chrome이 완전히 꺼질 때까지
}
console.log(`\n합계: ✅ ${passed}  ❌ ${failed}`)
process.exit(failed ? 1 : 0)
