import { useEffect, useEffectEvent, useState } from 'react'
import { formatDate } from '../utils/format'

const PLAY_MS = 8000   // 처음부터 끝까지 재생하는 데 걸리는 시간

/**
 * 우주 타임랩스 (GR-05): 첫 기록부터 오늘까지 날짜를 움직이면 그때까지 쓴 별만 보여요.
 * ▶를 누르면 8초 동안 우주가 자라나요. 끝나면(또는 "전체") onChange(null)로 모든 별을 보여줘요.
 * times: 별이 태어난 시각(ms) 목록
 */
export default function Timelapse({ times, onChange }) {
  const start = Math.min(...times)
  const [end] = useState(() => Date.now()) // 화면을 연 시각까지
  const [value, setValue] = useState(null) // null = 전체 (타임랩스 꺼짐)
  const [playing, setPlaying] = useState(false)
  const notify = useEffectEvent((v) => onChange(v))

  useEffect(() => notify(value), [value])

  useEffect(() => {
    if (!playing) return
    const startedAt = performance.now()
    const from = start - 1   // 처음에는 별이 하나도 없는 빈 우주에서 시작해요
    let raf
    const step = (now) => {
      const progress = Math.min(1, (now - startedAt) / PLAY_MS)
      setValue(from + (end - from) * progress)
      if (progress < 1) raf = requestAnimationFrame(step)
      else {
        setPlaying(false)
        setValue(null)
      }
    }
    raf = requestAnimationFrame(step)
    return () => cancelAnimationFrame(raf)
  }, [playing, start, end])

  const shown = value == null ? times.length : times.filter((t) => t <= value).length
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-lg border border-gray-200 bg-white px-4 py-3 text-sm">
      <button type="button" onClick={() => setPlaying(!playing)} aria-label={playing ? '타임랩스 멈추기' : '타임랩스 재생'}
        className="rounded-full bg-indigo-600 px-4 py-1.5 font-medium text-white hover:bg-indigo-700">
        {playing ? '⏸ 멈춤' : '▶ 타임랩스'}
      </button>
      <input
        type="range"
        min={start - 1}
        max={end}
        step={60_000}
        value={value ?? end}
        onChange={(e) => {
          setPlaying(false)
          setValue(Number(e.target.value) >= end - 60_000 ? null : Number(e.target.value))
        }}
        aria-label="타임랩스 날짜"
        className="min-w-40 flex-1 accent-indigo-600"
      />
      <span className="w-44 text-right tabular-nums text-gray-600" aria-live="polite">
        {value == null ? '오늘' : formatDate(new Date(Math.max(value, start)).toISOString())} · 별 {shown}개
      </span>
      {value != null && !playing && (
        <button type="button" onClick={() => setValue(null)} className="text-gray-500 hover:text-indigo-600">전체</button>
      )}
    </div>
  )
}
