import { useState } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '../api/client'
import { createComment, deleteComment, getComments, updateComment } from '../api/comments'
import { useAuth } from '../auth/useAuth'
import { useRequireLogin } from '../auth/useRequireLogin'
import { useAsync } from '../hooks/useAsync'
import { formatDate } from '../utils/format'
import ReportDialog from './ReportDialog'

/** 댓글 (C-01, C-02): 일반 텍스트, 답글 1단계, 작성자 수정, 작성자·글 주인 삭제, 신고 */
export default function CommentSection({ postId }) {
  const { user } = useAuth()
  const [version, setVersion] = useState(0) // 작성·수정·삭제 후 다시 불러오기
  const { data, error, loading } = useAsync(() => getComments(postId), `${postId}|${version}|${user?.id}`)
  const reload = () => setVersion((v) => v + 1)
  const [reportTarget, setReportTarget] = useState(null)

  return (
    <section className="mt-12 border-t border-gray-200 pt-8">
      <h2 className="mb-4 text-lg font-semibold">댓글 {data?.totalCount ?? ''}</h2>
      <CommentForm postId={postId} onDone={reload} />
      {loading && <p className="py-6 text-center text-gray-500">불러오는 중…</p>}
      {error && <p className="py-6 text-center text-red-600">{error}</p>}
      <ul className="mt-6 space-y-6">
        {data?.comments.map((comment) => (
          <li key={comment.id}>
            <CommentItem comment={comment} postId={postId} onChange={reload} onReport={setReportTarget} canReply />
            {comment.replies?.length > 0 && (
              <ul className="mt-4 space-y-4 border-l-2 border-gray-100 pl-4 sm:pl-6">
                {comment.replies.map((reply) => (
                  <li key={reply.id}>
                    <CommentItem comment={reply} postId={postId} onChange={reload} onReport={setReportTarget} />
                  </li>
                ))}
              </ul>
            )}
          </li>
        ))}
      </ul>
      {reportTarget && <ReportDialog target={reportTarget} onClose={() => setReportTarget(null)} />}
    </section>
  )
}

function CommentItem({ comment, postId, onChange, onReport, canReply = false }) {
  const [mode, setMode] = useState(null) // 'edit' | 'reply' | null
  const requireLogin = useRequireLogin()

  if (comment.isDeleted) return <p className="text-sm text-gray-400">삭제된 댓글입니다</p>

  const remove = async () => {
    if (!window.confirm('댓글을 삭제할까요?')) return
    try {
      await deleteComment(comment.id)
      onChange()
    } catch (err) {
      window.alert(errorMessage(err))
    }
  }

  const { author } = comment
  return (
    <div>
      <p className="text-sm">
        {author.username ? (
          <Link to={`/@${author.username}`} className="font-medium hover:text-indigo-600">
            {author.nickname}
          </Link>
        ) : (
          <span className="font-medium text-gray-500">{author.nickname}</span>
        )}
        <span className="ml-2 text-xs text-gray-400">
          {formatDate(comment.createdAt)}
          {comment.isEdited && ' (수정됨)'}
        </span>
      </p>
      {mode === 'edit' ? (
        <CommentForm
          initial={comment.content}
          submitLabel="수정"
          onSubmit={(content) => updateComment(comment.id, content)}
          onDone={() => {
            setMode(null)
            onChange()
          }}
          onCancel={() => setMode(null)}
        />
      ) : (
        // 일반 텍스트: 줄바꿈만 유지하고 HTML로 해석하지 않아요 (C-01)
        <p className="mt-1 whitespace-pre-wrap break-words text-gray-800">{comment.content}</p>
      )}
      <div className="mt-1 flex gap-3 text-xs text-gray-500">
        {canReply && (
          <button type="button" onClick={() => requireLogin() && setMode(mode === 'reply' ? null : 'reply')} className="hover:text-gray-900">
            답글
          </button>
        )}
        {comment.canEdit && mode !== 'edit' && (
          <button type="button" onClick={() => setMode('edit')} className="hover:text-gray-900">
            수정
          </button>
        )}
        {comment.canDelete && (
          <button type="button" onClick={remove} className="hover:text-red-600">
            삭제
          </button>
        )}
        {comment.canReport && (
          <button type="button" onClick={() => onReport({ commentId: comment.id })} className="hover:text-red-600">
            신고
          </button>
        )}
      </div>
      {mode === 'reply' && (
        <div className="mt-3 pl-4">
          <CommentForm
            postId={postId}
            parentId={comment.id}
            submitLabel="답글 등록"
            onDone={() => {
              setMode(null)
              onChange()
            }}
            onCancel={() => setMode(null)}
          />
        </div>
      )}
    </div>
  )
}

function CommentForm({ postId, parentId, initial = '', submitLabel = '댓글 등록', onSubmit, onDone, onCancel }) {
  const { user } = useAuth()
  const [content, setContent] = useState(initial)
  const [error, setError] = useState('')

  if (!user) {
    return (
      <p className="rounded-md bg-gray-100 p-4 text-sm text-gray-600">
        댓글을 쓰려면{' '}
        <Link to={`/login?redirect=${encodeURIComponent(window.location.pathname)}`} className="text-indigo-600 hover:underline">
          로그인
        </Link>
        해 주세요.
      </p>
    )
  }

  const submit = async (e) => {
    e.preventDefault()
    if (!content.trim()) return
    try {
      await (onSubmit ? onSubmit(content) : createComment(postId, content, parentId))
      setContent('')
      setError('')
      onDone()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <form onSubmit={submit} className="mt-2 space-y-2">
      <textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        maxLength={1000}
        rows={3}
        placeholder="댓글을 입력해 주세요"
        className="w-full rounded-md border border-gray-300 p-3 text-sm focus:border-indigo-500 focus:outline-none"
      />
      {error && <p className="text-sm text-red-600">{error}</p>}
      <div className="flex justify-end gap-2">
        {onCancel && (
          <button type="button" onClick={onCancel} className="rounded-md border border-gray-300 px-3 py-1.5 text-sm">
            취소
          </button>
        )}
        <button type="submit" disabled={!content.trim()} className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white disabled:opacity-40">
          {submitLabel}
        </button>
      </div>
    </form>
  )
}
