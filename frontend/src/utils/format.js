const dateFormat = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** "2026-10-08T14:30:00+09:00" → "2026.10.08" (한국 시간 기준) */
export function formatDate(iso) {
  if (!iso) return ''
  const parts = Object.fromEntries(dateFormat.formatToParts(new Date(iso)).map((p) => [p.type, p.value]))
  return `${parts.year}.${parts.month}.${parts.day}`
}

const timeFormat = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hour12: false })

/** "…T15:02:00+09:00" → "15:02" ("임시저장됨 (15:02)", D-07) */
export function formatTime(iso) {
  return iso ? timeFormat.format(new Date(iso)) : ''
}

const kstParts = (date) =>
  Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
    })
      .formatToParts(date)
      .map((p) => [p.type, p.value]),
  )

/** "…T09:00:00+09:00" → "2027.10.12 09:00" (한국 시간) */
export function formatDateTime(iso) {
  if (!iso) return ''
  const p = kstParts(new Date(iso))
  return `${p.year}.${p.month}.${p.day} ${p.hour}:${p.minute}`
}

/** Date → datetime-local 입력값 "2027-10-12T09:00" (한국 시간) */
export function toKstInput(date) {
  const p = kstParts(date)
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`
}

/** datetime-local 입력값(한국 시간으로 봐요) → "2027-10-12T09:00:00+09:00" */
export function kstInputToIso(value) {
  return value ? `${value}:00+09:00` : null
}

/** 남은 시간 → "D-23", 하루 안이면 "3시간 12분", 1시간 안이면 "12분 05초" */
export function remainingText(ms) {
  if (ms <= 0) return '곧 열려요'
  const sec = Math.floor(ms / 1000)
  const days = Math.floor(sec / 86400)
  if (days >= 1) return `D-${days}`
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = String(sec % 60).padStart(2, '0')
  return h > 0 ? `${h}시간 ${m}분` : `${m}분 ${s}초`
}
