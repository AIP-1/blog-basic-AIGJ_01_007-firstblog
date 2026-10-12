import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { errorMessage } from '../api/client'
import { deletePost, getPostDetail, setBookmark, setLike } from '../api/posts'
import { useRequireLogin } from '../auth/useRequireLogin'
import { useAuth } from '../auth/useAuth'
import Backlinks from '../components/Backlinks'
import CommentSection from '../components/CommentSection'
import MarkdownView from '../components/MarkdownView'
import ReportDialog from '../components/ReportDialog'
import SealedCapsule from '../components/SealedCapsule'
import { useAsync } from '../hooks/useAsync'
import { useNow } from '../hooks/useNow'
import { formatDate, formatDateTime } from '../utils/format'
import NotFoundPage from './NotFoundPage'

/** 글 상세 (P-05, P-06): 마크다운 본문, 작성일·수정일, 태그, 좋아요·북마크·신고, 댓글 */
export default function PostDetailPage() {
  const { handle, postId } = useParams()
  const { user } = useAuth()
  const username = handle.startsWith('@') ? handle.slice(1) : null
  const [reloadCount, setReloadCount] = useState(0) // 타임캡슐이 열리는 순간 다시 불러올 때 써요
  const { data: post, error, status, loading, setData } = useAsync(
    () => getPostDetail(username, postId),
    `${username}|${postId}|${user?.id}|${reloadCount}`,
  )
  const navigate = useNavigate()
  const requireLogin = useRequireLogin()
  const [reporting, setReporting] = useState(false)
  const now = useNow(30_000)

  if (!username) return <NotFoundPage />
  if (status === 404) return <NotFoundPage message="존재하지 않는 글이에요" />
  if (loading) return <p className="py-16 text-center text-gray-500">불러오는 중…</p>
  if (error) return <p className="py-16 text-center text-red-600">{error}</p>

  const toggleLike = async () => {
    if (!requireLogin()) return
    try {
      const { liked, likeCount } = await setLike(post.id, !post.liked)
      setData((p) => ({ ...p, liked, likeCount }))
    } catch (err) {
      window.alert(errorMessage(err))
    }
  }

  const toggleBookmark = async () => {
    if (!requireLogin()) return
    try {
      const { bookmarked } = await setBookmark(post.id, !post.bookmarked)
      setData((p) => ({ ...p, bookmarked }))
    } catch (err) {
      window.alert(errorMessage(err))
    }
  }

  const remove = async () => {
    if (!window.confirm('글을 삭제할까요? 댓글과 좋아요도 함께 지워져요.')) return
    try {
      await deletePost(post.id)
      navigate(`/@${username}`, { replace: true })
    } catch (err) {
      window.alert(errorMessage(err))
    }
  }

  const capsule = post.timeCapsule
  // 방문자가 보는 봉인된 타임캡슐: 제목과 카운트다운만 (TC-03)
  if (capsule?.locked) {
    return (
      <article className="mx-auto max-w-3xl space-y-6">
        <header className="space-y-2">
          <p className="text-sm font-medium text-violet-600">⏳ 타임캡슐</p>
          <h1 className="text-2xl font-bold sm:text-3xl">{post.title}</h1>
          <p className="text-sm text-gray-500">
            <Link to={`/@${username}`} className="font-medium text-gray-800 hover:text-indigo-600">
              {post.author.nickname}
            </Link>
            의 기록
          </p>
        </header>
        <SealedCapsule capsule={capsule} onOpen={() => setReloadCount((n) => n + 1)} />
      </article>
    )
  }
  const sealedForOwner = capsule && new Date(capsule.openAt).getTime() > now

  const hidden = post.visibility === 'PRIVATE' || post.category.visibility === 'PRIVATE'
  return (
    <article className="mx-auto max-w-3xl">
      <header className="mb-8 space-y-3">
        <div className="flex flex-wrap gap-2 text-xs">
          <Link to={`/@${username}?category=${post.category.id}`} className="rounded bg-gray-100 px-2 py-0.5 text-gray-600">
            {post.category.name}
          </Link>
          {post.topic && (
            <Link to={`/?topic=${post.topic.slug}`} className="rounded bg-indigo-50 px-2 py-0.5 text-indigo-700">
              {post.topic.name}
            </Link>
          )}
          {hidden && <span className="rounded bg-gray-100 px-2 py-0.5 text-gray-600">🔒 비공개</span>}
          {capsule && (
            <span className="rounded bg-violet-50 px-2 py-0.5 text-violet-700">⏳ {formatDate(capsule.sealedAt)}에 봉인된 기록</span>
          )}
        </div>
        <h1 className="text-2xl font-bold sm:text-3xl">{post.title}</h1>
        <p className="text-sm text-gray-500">
          <Link to={`/@${username}`} className="font-medium text-gray-800 hover:text-indigo-600">
            {post.author.nickname}
          </Link>
          {' · '}
          {capsule ? `${formatDate(capsule.sealedAt)} 봉인 → ${formatDate(capsule.openAt)} 개봉` : formatDate(post.publishedAt)}
          {post.editedAt && ` (수정 ${formatDate(post.editedAt)})`}
          {' · 조회 '}
          {post.viewCount}
        </p>
        {sealedForOwner && (
          <p className="rounded-md bg-violet-50 px-3 py-2 text-sm text-violet-800">
            🔒 봉인 중인 타임캡슐이에요. {formatDateTime(capsule.openAt)}에 공개되고, 그때까지는 나만 볼 수 있어요.
          </p>
        )}
        {post.isOwner && (
          <div className="flex gap-3 text-sm">
            {!sealedForOwner && (
              <Link to={`/write/${post.id}`} className="text-gray-600 hover:text-indigo-600">
                수정
              </Link>
            )}
            <button type="button" onClick={remove} className="text-gray-600 hover:text-red-600">
              삭제
            </button>
          </div>
        )}
      </header>

      <MarkdownView content={post.content} linkedPosts={post.linkedPosts} />

      {post.tags.length > 0 && (
        <div className="mt-8 flex flex-wrap gap-2">
          {post.tags.map((tag) => (
            <Link key={tag} to={`/tags/${encodeURIComponent(tag)}`} className="rounded-full bg-gray-100 px-3 py-1 text-sm text-gray-700 hover:bg-gray-200">
              #{tag}
            </Link>
          ))}
        </div>
      )}

      <div className="mt-8 flex items-center gap-2">
        <button
          type="button"
          onClick={toggleLike}
          aria-pressed={post.liked}
          className={`rounded-full border px-4 py-1.5 text-sm ${post.liked ? 'border-red-300 bg-red-50 text-red-600' : 'border-gray-300'}`}
        >
          {post.liked ? '♥' : '♡'} {post.likeCount}
        </button>
        <button
          type="button"
          onClick={toggleBookmark}
          aria-pressed={post.bookmarked}
          className={`rounded-full border px-4 py-1.5 text-sm ${post.bookmarked ? 'border-indigo-300 bg-indigo-50 text-indigo-600' : 'border-gray-300'}`}
        >
          {post.bookmarked ? '북마크됨' : '북마크'}
        </button>
        {!post.isOwner && (
          <button type="button" onClick={() => requireLogin() && setReporting(true)} className="ml-auto text-sm text-gray-400 hover:text-red-600">
            신고
          </button>
        )}
      </div>

      <Backlinks postId={post.id} viewerKey={user?.id} />
      <CommentSection postId={post.id} />
      {reporting && <ReportDialog target={{ postId: post.id }} onClose={() => setReporting(false)} />}
    </article>
  )
}
