import { useEffect, useEffectEvent, useMemo, useState } from 'react'
import { formatDate } from '../utils/format'

const MS_PER_STAR = 450   // 별 하나가 태어나는 간격
const MIN_PLAY_MS = 3000  // 별이 적어도 너무 빨리 끝나지 않게
const MAX_PLAY_MS = 10000 // 별이 많아도 10초 안에 끝나게

/**
 * 우주 타임랩스 (GR-05): 별이 **쓴 순서대로 하나씩** 태어나요. ▶를 누르면 빈 우주에서 시작해 전체까지 재생하고,
 * 슬라이더로 "몇 번째 기록까지"를 고를 수 있어요. 끝나면(또는 "전체") onChange(null)로 모든 별을 보여줘요.
 *
 * 처음에는 "첫 기록 시각 → 지금"을 시간에 비례해 재생했는데, 글이 짧은 시간에 몰려 있으면 첫 프레임에 거의 모든 별이
 * 나타나고 나머지 시간은 아무 변화가 없었어요 (트러블슈팅 13번). 그래서 시간 대신 순서로 재생해요.
 *
 * times: 별이 태어난 시각(ms) 목록. onChange(until): until 시각까지 쓴 별만 보이게 (null = 전체, -Infinity = 없음)
 */
export default function Timelapse({ times, onChange }) {
  const sorted = useMemo(() => [...times].sort((a, b) => a - b), [times])
  const total = sorted.length
  const [count, setCount] = useState(null) // 보여줄 별 개수 (null = 전체, 타임랩스 꺼짐)
  const [playing, setPlaying] = useState(false)
  const notify = useEffectEvent((until) => onChange(until))

  useEffect(() => {
    notify(count == null ? null : count === 0 ? Number.NEGATIVE_INFINITY : sorted[count - 1])
  }, [count, sorted])

  useEffect(() => {
    if (!playing) return
    const duration = Math.min(MAX_PLAY_MS, Math.max(MIN_PLAY_MS, total * MS_PER_STAR))
    const startedAt = performance.now()
    let raf
    const step = (now) => {
      const progress = Math.min(1, (now - startedAt) / duration)
      setCount(Math.floor(progress * total))   // 0개(빈 우주)에서 시작해 하나씩 늘어요
      if (progress < 1) raf = requestAnimationFrame(step)
      else {
        setPlaying(false)
        setCount(null)
      }
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [playing, total])

  // 같은 시각에 쓴 별은 함께 보여서, 실제로 보이는 수는 그 시각까지 쓴 별 수예요
  const until = count == null || count === 0 ? null : sorted[count - 1]
  const shown = count == null ? total : count === 0 ? 0 : sorted.filter((t) => t <= until).length
  const label = count == null ? '오늘' : count === 0 ? '시작' : formatDate(new Date(until).toISOString())

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm">
      <button type="button" onClick={() => setPlaying(!playing)} aria-label={playing ? '타임랩스 멈추기' : '타임랩스 재생'}
        className="rounded-full bg-indigo-600 px-4 py-1.5 font-medium text-white hover:bg-indigo-700">
        {playing ? '⏸ 멈춤' : '▶ 타임랩스'}
      </button>
      <input
        type="range"
        min={0}
        max={total}
        step={1}
        value={count ?? total}
        onChange={(e) => {
          setPlaying(false)
          const next = Number(e.target.value)
          setCount(next >= total ? null : next)
        }}
        aria-label="타임랩스: 몇 번째 기록까지"
        className="min-w-40 flex-1 accent-indigo-600"
      />
      {/* 재생 중에는 매 프레임 바뀌어서 화면 읽기 프로그램에 알리지 않아요 */}
      <span data-timelapse-status className="w-44 text-right tabular-nums text-gray-600" aria-live={playing ? 'off' : 'polite'}>
        {label} · 별 {shown}개
      </span>
      {count != null && !playing && (
        <button type="button" onClick={() => setCount(null)} className="text-gray-500 hover:text-indigo-600">전체</button>
      )}
    </div>
  )
}
