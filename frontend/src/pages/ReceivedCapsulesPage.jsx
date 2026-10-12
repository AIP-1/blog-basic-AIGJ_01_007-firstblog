import { Link } from 'react-router'
import { getReceivedCapsules } from '../api/timeCapsules'
import { useAsync } from '../hooks/useAsync'
import { useNow } from '../hooks/useNow'
import { formatDate, formatDateTime, remainingText } from '../utils/format'

/** 받은 타임캡슐 /capsules (TC-09): 아직 안 열린 것(남은 시간)과 열린 것 */
export default function ReceivedCapsulesPage() {
  const { data, error, loading } = useAsync(() => getReceivedCapsules(), 'received')
  const now = useNow(1000, Boolean(data?.some((c) => !c.opened)))

  if (loading) return <p className="py-16 text-center text-gray-500">불러오는 중…</p>
  if (error) return <p className="py-16 text-center text-red-600">{error}</p>

  const href = (c) => `/@${c.sender.username}/posts/${c.id}`
  return (
    <section className="mx-auto max-w-3xl space-y-4">
      <h1 className="text-2xl font-bold">💌 받은 타임캡슐</h1>
      <p className="text-sm text-gray-500">다른 사람이 나에게 보낸 타임캡슐이에요. 정해진 시각이 되면 나만 열어 볼 수 있어요.</p>
      {data.length === 0 && <p className="rounded-lg border border-dashed border-gray-300 py-12 text-center text-gray-500">아직 받은 타임캡슐이 없어요.</p>}
      <ul className="space-y-3">
        {data.map((c) => {
          const remaining = new Date(c.openAt) - now
          const opened = c.opened || remaining <= 0
          return (
            <li key={c.id}>
              <Link to={href(c)}
                className={`flex items-center gap-4 rounded-lg border p-4 hover:shadow-sm ${opened ? 'border-violet-200 bg-white' : 'border-slate-700 bg-gradient-to-r from-slate-900 to-violet-950 text-white'}`}>
                <span className="text-3xl" aria-hidden="true">{opened ? '💌' : '🔒'}</span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold">{c.title}</span>
                  <span className={`block text-xs ${opened ? 'text-gray-500' : 'text-violet-200'}`}>
                    {c.sender.nickname}님이 {formatDate(c.sealedAt)}에 보냄 · {formatDateTime(c.openAt)} {opened ? '열림' : '개봉'}
                  </span>
                </span>
                <span className={`shrink-0 font-mono text-sm tabular-nums ${opened ? 'text-violet-700' : 'text-white'}`}>
                  {opened ? '읽기 →' : remainingText(remaining)}
                </span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
