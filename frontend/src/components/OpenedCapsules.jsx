import { Link } from 'react-router'
import { getOpenedCapsules } from '../api/timeCapsules'
import { useAsync } from '../hooks/useAsync'
import { formatDate } from '../utils/format'

/** 메인 "최근 열린 타임캡슐" (TC-06, API 12.2). 최근 7일 안에 열린 게 없으면 숨겨요 */
export default function OpenedCapsules() {
  const { data } = useAsync(() => getOpenedCapsules(), 'opened')
  if (!data?.length) return null

  return (
    <section aria-label="최근 열린 타임캡슐" className="rounded-xl bg-gradient-to-r from-slate-900 via-violet-950 to-slate-900 p-4 text-white">
      <h2 className="mb-3 text-sm font-semibold">✨ 최근 열린 타임캡슐</h2>
      <ul className="flex gap-3 overflow-x-auto pb-1">
        {data.map((post) => (
          <li key={post.id} className="shrink-0">
            <Link to={`/@${post.author.username}/posts/${post.id}`}
              className="flex w-56 flex-col rounded-md bg-white/10 px-3 py-2 hover:bg-white/20">
              <span className="truncate font-medium">{post.title}</span>
              <span className="mt-1 text-xs text-violet-200">
                {post.author.nickname} · {formatDate(post.sealedAt)} 봉인 → {formatDate(post.publishedAt)} 개봉
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
