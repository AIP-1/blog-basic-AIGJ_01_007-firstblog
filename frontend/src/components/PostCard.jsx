import { Link } from 'react-router'
import { useNow } from '../hooks/useNow'
import { formatDate } from '../utils/format'

/** 글 카드 (API 2.2 PostCard, S-03). 메인·블로그 홈·검색·태그·북마크 목록이 함께 써요 */
export default function PostCard({ post }) {
  const { author } = post
  const href = author.username ? `/@${author.username}/posts/${post.id}` : null
  const isHidden = post.visibility === 'PRIVATE' || post.categoryVisibility === 'PRIVATE'
  // 타임캡슐: 봉인 중(주인에게만 보여요) 또는 열린 캡슐 (TC-06)
  const now = useNow(60_000, Boolean(post.sealedAt))
  const sealed = post.sealedAt && new Date(post.publishedAt).getTime() > now

  const body = (
    <article className="flex h-full flex-col overflow-hidden rounded-xl border border-gray-200/80 bg-white shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-indigo-200 hover:shadow-lg hover:shadow-indigo-500/10">
      {post.thumbnailUrl ? (
        <img src={post.thumbnailUrl} alt="" loading="lazy" className="aspect-video w-full object-cover" />
      ) : (
        // 썸네일이 없으면 같은 크기의 대체 영역에 토픽 이름을 보여줘서 격자를 맞춰요 (P-02)
        <div className="bg-cosmos flex aspect-video w-full items-center justify-center text-lg font-semibold text-indigo-100/90">
          ✦ {post.topic?.name ?? 'OwnLog'}
        </div>
      )}
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex flex-wrap gap-1.5 text-xs">
          {post.topic && <span className="rounded bg-indigo-50 px-2 py-0.5 text-indigo-700">{post.topic.name}</span>}
          {isHidden && (
            <span className="rounded bg-gray-100 px-2 py-0.5 text-gray-600">
              🔒 {post.visibility === 'PRIVATE' ? '비공개' : '비공개 카테고리'}
            </span>
          )}
          {post.sealedAt && (
            <span className="rounded bg-violet-50 px-2 py-0.5 text-violet-700">{sealed ? '🔒 봉인 중' : '⏳ 타임캡슐'}</span>
          )}
        </div>
        <h2 className="line-clamp-2 font-semibold text-gray-900">{post.title}</h2>
        {post.summary && <p className="line-clamp-2 text-sm text-gray-600">{post.summary}</p>}
        <p className="mt-auto pt-2 text-xs text-gray-500">
          {author.nickname} · {sealed ? `${formatDate(post.publishedAt)} 개봉 예정` : formatDate(post.publishedAt)} · ♥ {post.likeCount}
        </p>
      </div>
    </article>
  )

  return href ? <Link to={href}>{body}</Link> : body
}
