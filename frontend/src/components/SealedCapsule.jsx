import { useEffect, useEffectEvent } from 'react'
import { useNow } from '../hooks/useNow'
import { formatDateTime, remainingText } from '../utils/format'

/** 방문자가 보는 봉인된 타임캡슐 카드 (TC-03). 남은 시간이 0이 되면 onOpen()으로 다시 불러와요 */
export default function SealedCapsule({ capsule, onOpen, attempt = 0 }) {
  const openAt = new Date(capsule.openAt).getTime()
  const now = useNow(1000)
  const remaining = openAt - now
  const opened = remaining <= 0
  const fireOpen = useEffectEvent(onOpen)

  useEffect(() => {
    if (!opened) return
    // 서버 시계와 어긋날 수 있어서 조금 기다렸다가 열어요. 아직 잠겨 있으면 1.5초 → 3초 → 6초… (최대 30초) 뒤에 다시 물어봐요
    const timer = setTimeout(() => fireOpen(), Math.min(1500 * 2 ** attempt, 30_000))
    return () => clearTimeout(timer)
  }, [opened, attempt])

  return (
    <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-slate-900 via-violet-950 to-slate-900 px-6 py-12 text-center text-white shadow-lg">
      <div className="text-5xl" aria-hidden="true">{opened ? '✨' : '🔒'}</div>
      <p className="mt-4 text-sm tracking-widest text-violet-200">TIME CAPSULE</p>
      <p className="mt-2 text-lg">{opened ? '타임캡슐이 열리고 있어요…' : '아직 봉인된 기록이에요'}</p>
      <p className="mt-6 font-mono text-4xl font-bold tabular-nums" aria-label="남은 시간">
        {remainingText(remaining)}
      </p>
      <p className="mt-4 text-sm text-violet-200">
        {formatDateTime(capsule.sealedAt)}에 봉인 · <strong className="text-white">{formatDateTime(capsule.openAt)}</strong>에 열려요
      </p>
    </div>
  )
}
