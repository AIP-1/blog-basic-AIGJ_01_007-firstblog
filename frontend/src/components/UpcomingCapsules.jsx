import { Link } from 'react-router'
import { getSealedCapsules } from '../api/timeCapsules'
import { useAsync } from '../hooks/useAsync'
import { useNow } from '../hooks/useNow'
import { formatDateTime, remainingText } from '../utils/format'

/** 블로그 홈 "곧 열리는 타임캡슐" (TC-06, API 12.1). 없으면 아무것도 그리지 않아요 */
export default function UpcomingCapsules({ username, viewerKey }) {
  const { data } = useAsync(() => getSealedCapsules(username), `${username}|${viewerKey}`)
  const now = useNow(1000, Boolean(data?.length))
  if (!data?.length) return null

  return (
    <section aria-label="곧 열리는 타임캡슐" className="rounded-lg border border-violet-200 bg-gradient-to-r from-violet-50 to-white p-4">
      <h2 className="mb-3 text-sm font-semibold text-violet-800">⏳ 곧 열리는 타임캡슐 {data.length}</h2>
      <ul className="flex gap-3 overflow-x-auto pb-1">
        {data.map((c) => (
          <li key={c.id} className="shrink-0">
            <Link to={`/@${username}/posts/${c.id}`}
              className="flex w-52 flex-col rounded-md border border-violet-100 bg-white px-3 py-2 hover:shadow-sm">
              <span className="truncate font-medium text-gray-900">🔒 {c.title}</span>
              <span className="mt-1 font-mono text-sm text-violet-700 tabular-nums">{remainingText(new Date(c.openAt) - now)}</span>
              <span className="text-xs text-gray-500">{formatDateTime(c.openAt)} 개봉</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
