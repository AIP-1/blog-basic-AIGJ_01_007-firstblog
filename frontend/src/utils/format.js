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
