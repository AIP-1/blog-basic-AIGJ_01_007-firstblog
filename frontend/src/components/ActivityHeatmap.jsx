import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import { getActivity } from '../api/blogs'
import { useAsync } from '../hooks/useAsync'

const LEVELS = ['bg-gray-100', 'bg-indigo-200', 'bg-indigo-400', 'bg-indigo-600']
const levelOf = (count) => LEVELS[Math.min(count, 3)]
const WEEKDAYS = ['', '월', '', '수', '', '금', '']

/** "2026-10-12" 날짜 문자열을 하루씩 더해요 (시간대 영향이 없게 UTC로 계산) */
function addDays(iso, n) {
  const d = new Date(`${iso}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}
const weekdayOf = (iso) => new Date(`${iso}T00:00:00Z`).getUTCDay() // 0 = 일요일

/**
 * 블로그 홈 기록 잔디 (AC-01 ~ AC-03): 최근 1년, 하루 한 칸(일~토 세로), 글 수만큼 진하게.
 * 칸을 누르면 그날의 기록(?date=)으로 가요. 휴대폰에서는 가로로 넘기고, 처음엔 최근 쪽을 보여줘요.
 */
export default function ActivityHeatmap({ username, viewerKey }) {
  const { data } = useAsync(() => getActivity(username), `${username}|${viewerKey}`)
  const scrollRef = useRef(null)

  useEffect(() => {
    if (data && scrollRef.current) scrollRef.current.scrollLeft = scrollRef.current.scrollWidth
  }, [data])

  if (!data) return null
  const counts = Object.fromEntries(data.days.map((d) => [d.date, d.count]))
  // 첫 칸은 from이 있는 주의 일요일부터 (그 앞은 빈 칸)
  const first = addDays(data.from, -weekdayOf(data.from))
  const cells = []
  for (let day = first; day <= data.to; day = addDays(day, 1)) cells.push(day)
  const weeks = Array.from({ length: Math.ceil(cells.length / 7) }, (_, i) => cells.slice(i * 7, i * 7 + 7))
  const monthLabel = (week) => (week.find((d) => d.endsWith('-01') || d === first)?.slice(5, 7) ?? '')

  return (
    <section aria-label="기록 잔디" className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="mb-3 flex flex-wrap items-baseline gap-x-4 gap-y-1 text-sm">
        <h2 className="font-semibold">🌱 기록 잔디</h2>
        <span className={data.currentStreak > 0 ? 'font-semibold text-orange-600' : 'text-gray-500'}>
          🔥 연속 기록 {data.currentStreak}일
        </span>
        <span className="text-gray-500">최장 {data.longestStreak}일 · 올해 {data.totalThisYear}개</span>
      </div>
      <div ref={scrollRef} className="overflow-x-auto pb-1">
        <div className="flex gap-[3px]">
          <div className="mr-1 grid grid-rows-[12px_repeat(7,11px)] gap-[3px] text-[9px] leading-[11px] text-gray-400">
            <span />
            {WEEKDAYS.map((w, i) => <span key={i}>{w}</span>)}
          </div>
          {weeks.map((week, i) => (
            <div key={week[0]} className="grid w-[11px] shrink-0 grid-rows-[12px_repeat(7,11px)] gap-[3px]">
              <span className="whitespace-nowrap text-[9px] leading-[12px] text-gray-400">{i === 0 || week.some((d) => d.endsWith('-01')) ? `${Number(monthLabel(week))}월` : ''}</span>
              {week.map((day) => {
                const count = counts[day] ?? 0
                const outside = day < data.from
                const label = `${day.replaceAll('-', '.')} · 기록 ${count}개`
                return count > 0 ? (
                  <Link key={day} to={`/@${username}?date=${day}`} title={label} aria-label={label}
                    className={`h-[11px] w-[11px] rounded-[2px] ${levelOf(count)} hover:ring-2 hover:ring-indigo-300`} />
                ) : (
                  <span key={day} title={outside ? '' : label} className={`h-[11px] w-[11px] rounded-[2px] ${outside ? '' : levelOf(0)}`} />
                )
              })}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
