import MDEditor from '@uiw/react-md-editor/nohighlight'
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import rehypeSanitize from 'rehype-sanitize'
import { getSidebar } from '../api/blogs'
import { errorMessage, fieldErrorsOf } from '../api/client'
import { createDraft, getDraft, publishDraft, saveDraft } from '../api/drafts'
import { uploadImage } from '../api/images'
import { createPost, getPostDetail, getTopics, updatePost } from '../api/posts'
import { useAuth } from '../auth/useAuth'
import TagInput from '../components/TagInput'
import { formatTime } from '../utils/format'
import NotFoundPage from './NotFoundPage'

const EMPTY = { title: '', content: '', thumbnailUrl: null, categoryId: null, topicId: null, tags: [], visibility: 'PUBLIC' }

/**
 * 글쓰기 (P-01 ~ P-04, D-01 ~ D-07)
 * - /write            새 글: [임시저장] + [발행]
 * - /write/:postId    임시저장 글이면 이어 쓰기([임시저장] + [발행]), 발행 글이면 수정([수정 완료]만, D-01)
 */
export default function WritePage() {
  const { postId } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState(EMPTY)
  const [mode, setMode] = useState(postId ? 'loading' : 'new') // new | draft | published | loading | notfound
  const [draftId, setDraftId] = useState(null)
  const [savedAt, setSavedAt] = useState(null)
  const [dirty, setDirty] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [categories, setCategories] = useState([])
  const [topics, setTopics] = useState([])
  const imageInput = useRef(null)
  const thumbnailInput = useRef(null)

  // 카테고리(주인이라 비공개 포함)와 토픽 목록
  useEffect(() => {
    getSidebar(user.username).then((d) => setCategories(d.categories)).catch(() => {})
    getTopics().then(setTopics).catch(() => {})
  }, [user.username])

  // /write/:postId → 임시저장 글인지 먼저 보고, 아니면 내 발행 글로 불러와요
  useEffect(() => {
    if (!postId) return
    const toForm = (p) => ({
      title: p.title ?? '', content: p.content ?? '', thumbnailUrl: p.thumbnailUrl, categoryId: p.category.id,
      topicId: p.topic?.id ?? null, tags: p.tags, visibility: p.visibility,
    })
    getDraft(postId)
      .then((d) => {
        setForm(toForm(d))
        setDraftId(d.id)
        setSavedAt(d.updatedAt)
        setMode('draft')
      })
      .catch(() =>
        getPostDetail(user.username, postId)
          .then((p) => {
            if (!p.isOwner) throw new Error()
            setForm(toForm(p))
            setMode('published')
          })
          .catch(() => setMode('notfound')),
      )
  }, [postId, user.username])

  // 저장하지 않은 내용이 있으면 새로고침·창 닫기 전에 경고해요 (D-07)
  useEffect(() => {
    if (!dirty) return
    const warn = (e) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const update = (patch) => {
    setForm((f) => ({ ...f, ...patch }))
    setDirty(true)
    // 고친 칸의 에러는 바로 지워요
    setFieldErrors((errors) => Object.fromEntries(Object.entries(errors).filter(([field]) => !(field.split('[')[0] in patch))))
  }

  const body = () => ({ ...form, title: form.title.trim() || null })

  const handleError = (err) => {
    const errors = fieldErrorsOf(err)
    setFieldErrors(errors)
    setError(Object.keys(errors).length ? '입력값을 확인해 주세요.' : errorMessage(err))
  }

  const run = async (action) => {
    setBusy(true)
    setError('')
    setFieldErrors({})
    try {
      await action()
    } catch (err) {
      handleError(err)
    } finally {
      setBusy(false)
    }
  }

  /** 임시저장: 처음이면 만들고 주소를 /write/{id}로 바꿔서 새로고침해도 이어 쓸 수 있게 해요 */
  const saveAsDraft = () =>
    run(async () => {
      const saved = draftId ? await saveDraft(draftId, body()) : await createDraft(body())
      if (!draftId) {
        setDraftId(saved.id)
        setMode('draft')
        navigate(`/write/${saved.id}`, { replace: true })
      }
      setSavedAt(saved.updatedAt)
      setDirty(false)
    })

  const publish = () =>
    run(async () => {
      const result =
        mode === 'published'
          ? await updatePost(postId, body())
          : draftId
            ? await publishDraft(draftId, body())
            : await createPost(body())
      setDirty(false)
      navigate(`/@${user.username}/posts/${result.id ?? postId}`)
    })

  const upload = async (file, onUrl) => {
    if (!file) return
    try {
      onUrl(await uploadImage(file))
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (mode === 'loading') return <p className="py-16 text-center text-gray-500">불러오는 중…</p>
  if (mode === 'notfound') return <NotFoundPage message="수정할 수 없는 글이에요" />

  return (
    <div className="space-y-4">
      <input
        value={form.title}
        onChange={(e) => update({ title: e.target.value })}
        placeholder="제목을 입력하세요"
        maxLength={100}
        aria-label="제목"
        className="w-full border-b border-gray-200 bg-transparent py-2 text-2xl font-bold focus:border-indigo-500 focus:outline-none"
      />
      {fieldErrors.title && <p className="text-sm text-red-600">{fieldErrors.title}</p>}

      <div className="grid gap-3 sm:grid-cols-3">
        <select value={form.categoryId ?? ''} onChange={(e) => update({ categoryId: e.target.value ? Number(e.target.value) : null })} aria-label="카테고리" className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm">
          <option value="">카테고리: 미분류</option>
          {categories.filter((c) => !c.isDefault).map((c) => (
            <option key={c.id} value={c.id}>
              {c.visibility === 'PRIVATE' ? '🔒 ' : ''}
              {c.name}
            </option>
          ))}
        </select>
        <select value={form.topicId ?? ''} onChange={(e) => update({ topicId: e.target.value ? Number(e.target.value) : null })} aria-label="토픽" className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm">
          <option value="">토픽: 주제 없음</option>
          {topics.map((t) => (
            <option key={t.id} value={t.id}>
              {t.name}
            </option>
          ))}
        </select>
        <select value={form.visibility} onChange={(e) => update({ visibility: e.target.value })} aria-label="공개 범위" className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm">
          <option value="PUBLIC">공개</option>
          <option value="PRIVATE">비공개</option>
        </select>
      </div>
      {categories.find((c) => c.id === form.categoryId)?.visibility === 'PRIVATE' && (
        <p className="text-xs text-amber-700">이 카테고리는 비공개라 다른 사람에게 보이지 않아요. (B-05)</p>
      )}

      <TagInput tags={form.tags} onChange={(tags) => update({ tags })} />
      {Object.keys(fieldErrors).some((f) => f.startsWith('tags')) && <p className="text-sm text-red-600">태그를 확인해 주세요.</p>}

      <div className="flex flex-wrap items-center gap-3 text-sm">
        <button type="button" onClick={() => imageInput.current.click()} className="rounded-md border border-gray-300 bg-white px-3 py-1.5">
          본문에 이미지 넣기
        </button>
        <button type="button" onClick={() => thumbnailInput.current.click()} className="rounded-md border border-gray-300 bg-white px-3 py-1.5">
          썸네일 {form.thumbnailUrl ? '바꾸기' : '올리기'}
        </button>
        {form.thumbnailUrl && (
          <span className="flex items-center gap-2">
            <img src={form.thumbnailUrl} alt="썸네일" className="h-10 w-16 rounded object-cover" />
            <button type="button" onClick={() => update({ thumbnailUrl: null })} className="text-gray-500 hover:text-red-600">
              썸네일 빼기
            </button>
          </span>
        )}
        <span className="text-xs text-gray-400">jpg·png·gif·webp, 5MB 이하 · 썸네일이 없으면 본문 첫 이미지를 써요</span>
        <input ref={imageInput} type="file" aria-label="본문 이미지 파일" accept="image/jpeg,image/png,image/gif,image/webp" hidden
          onChange={(e) => upload(e.target.files[0], (url) => update({ content: `${form.content}\n\n![](${url})\n` }))} />
        <input ref={thumbnailInput} type="file" aria-label="썸네일 파일" accept="image/jpeg,image/png,image/gif,image/webp" hidden
          onChange={(e) => upload(e.target.files[0], (url) => update({ thumbnailUrl: url }))} />
      </div>

      <div data-color-mode="light">
        <MDEditor
          value={form.content}
          onChange={(value) => update({ content: value ?? '' })}
          height={480}
          previewOptions={{ rehypePlugins: [[rehypeSanitize]] }}
          textareaProps={{ placeholder: '마크다운으로 글을 써 보세요', 'aria-label': '본문', maxLength: 100000 }}
        />
      </div>
      {fieldErrors.content && <p className="text-sm text-red-600">{fieldErrors.content}</p>}

      <div className="sticky bottom-0 flex flex-wrap items-center justify-end gap-3 border-t border-gray-200 bg-gray-50 py-3">
        {error && <p className="mr-auto text-sm text-red-600">{error}</p>}
        {savedAt && <span className="text-sm text-gray-500">임시저장됨 ({formatTime(savedAt)})</span>}
        {mode !== 'published' && (
          <button type="button" disabled={busy} onClick={saveAsDraft} className="rounded-md border border-gray-300 bg-white px-4 py-2 disabled:opacity-50">
            임시저장
          </button>
        )}
        <button type="button" disabled={busy} onClick={publish} className="rounded-md bg-indigo-600 px-4 py-2 font-medium text-white disabled:opacity-50">
          {mode === 'published' ? '수정 완료' : '발행'}
        </button>
      </div>
    </div>
  )
}
