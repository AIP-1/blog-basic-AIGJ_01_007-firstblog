import { Link } from 'react-router'
import { getBacklinks } from '../api/posts'
import { useAsync } from '../hooks/useAsync'
import { formatDate } from '../utils/format'

/** 글 상세 하단 "이 글을 언급한 글" (LK-03). 없으면 숨겨요 */
export default function Backlinks({ postId, viewerKey }) {
  const { data } = useAsync(() => getBacklinks(postId), `${postId}|${viewerKey}`)
  if (!data?.length) return null

  return (
    <section aria-label="이 글을 언급한 글" className="mt-10 rounded-lg border border-indigo-100 bg-indigo-50/50 p-4">
      <h2 className="mb-3 text-sm font-semibold text-indigo-800">🔗 이 글을 언급한 글 {data.length}</h2>
      <ul className="space-y-2">
        {data.map((p) => (
          <li key={p.id}>
            <Link to={`/@${p.username}/posts/${p.id}`} className="flex flex-wrap items-baseline gap-x-2 hover:text-indigo-600">
              <span className="font-medium">{p.title}</span>
              <span className="text-xs text-gray-500">
                {p.nickname} · {formatDate(p.publishedAt)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
