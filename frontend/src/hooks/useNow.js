import { useEffect, useState } from 'react'

/** 지금 시각(ms)을 intervalMs마다 다시 알려줘요 (카운트다운용). enabled가 false면 멈춰요 */
export function useNow(intervalMs = 1000, enabled = true) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!enabled) return
    const timer = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs, enabled])
  return now
}
