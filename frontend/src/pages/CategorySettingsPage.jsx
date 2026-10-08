import { useState } from 'react'
import { Link } from 'react-router'
import { getSidebar } from '../api/blogs'
import { createCategory, deleteCategory, reorderCategories, updateCategory } from '../api/categories'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/useAuth'
import { useAsync } from '../hooks/useAsync'

/** 카테고리 관리 (B-04, B-05): 생성(최대 30개), 이름·공개 범위 수정, 순서 변경, 삭제("미분류"로 이동) */
export default function CategorySettingsPage() {
  const { user } = useAuth()
  const blogId = user.blog.id
  const [version, setVersion] = useState(0)
  const { data, error, loading } = useAsync(() => getSidebar(user.username), `${user.username}|${version}`)
  const reload = () => setVersion((v) => v + 1)
  const [message, setMessage] = useState({ text: '', tone: 'green' })
  const [newName, setNewName] = useState('')
  const [newPrivate, setNewPrivate] = useState(false)

  const act = async (action, success) => {
    try {
      const result = await action()
      setMessage({ text: typeof success === 'function' ? success(result) : success, tone: 'green' })
      reload()
    } catch (err) {
      setMessage({ text: errorMessage(err), tone: 'red' })
    }
  }

  const categories = data?.categories ?? []
  const move = (index, delta) => {
    const ids = categories.map((c) => c.id)
    ;[ids[index], ids[index + delta]] = [ids[index + delta], ids[index]]
    act(() => reorderCategories(blogId, ids), '순서를 바꿨어요.')
  }

  return (
    <div className="mx-auto max-w-xl space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">카테고리 관리</h1>
        <Link to={`/@${user.username}`} className="text-sm text-indigo-600 hover:underline">
          내 블로그 →
        </Link>
      </div>

      <form
        className="flex flex-wrap items-center gap-2 rounded-lg border border-gray-200 bg-white p-4"
        onSubmit={(e) => {
          e.preventDefault()
          if (!newName.trim()) return
          act(() => createCategory(blogId, newName.trim(), newPrivate ? 'PRIVATE' : 'PUBLIC'), '카테고리를 만들었어요.')
          setNewName('')
          setNewPrivate(false)
        }}
      >
        <input value={newName} onChange={(e) => setNewName(e.target.value)} maxLength={30} placeholder="새 카테고리 이름" aria-label="새 카테고리 이름"
          className="min-w-0 flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm" />
        <label className="flex items-center gap-1 text-sm">
          <input type="checkbox" checked={newPrivate} onChange={(e) => setNewPrivate(e.target.checked)} /> 비공개
        </label>
        <button type="submit" className="rounded-md bg-indigo-600 px-4 py-2 text-sm text-white">
          추가
        </button>
      </form>

      {message.text && <p className={`text-sm ${message.tone === 'red' ? 'text-red-600' : 'text-green-600'}`}>{message.text}</p>}
      {loading && <p className="text-center text-gray-500">불러오는 중…</p>}
      {error && <p className="text-center text-red-600">{error}</p>}

      <ul className="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
        {categories.map((c, i) => (
          <CategoryRow
            key={`${c.id}|${c.name}|${c.visibility}`}
            category={c}
            first={i === 0}
            last={i === categories.length - 1}
            onMove={(delta) => move(i, delta)}
            onSave={(name, visibility) => act(() => updateCategory(c.id, name, visibility), '저장했어요.')}
            onDelete={() => {
              if (!window.confirm(`"${c.name}"을(를) 삭제할까요? 안의 글은 "미분류"로 옮겨져요.${c.visibility === 'PRIVATE' ? ' 비공개 카테고리라 옮겨지는 글은 비공개가 돼요.' : ''}`)) return
              act(() => deleteCategory(c.id), (r) => `삭제했어요. 글 ${r.movedPostCount}개를 "미분류"로 옮겼어요.`)
            }}
          />
        ))}
      </ul>
      <p className="text-xs text-gray-500">블로그당 최대 30개 · "미분류"는 삭제할 수 없지만 이름과 공개 범위는 바꿀 수 있어요.</p>
    </div>
  )
}

function CategoryRow({ category, first, last, onMove, onSave, onDelete }) {
  const [name, setName] = useState(category.name)
  const [isPrivate, setIsPrivate] = useState(category.visibility === 'PRIVATE')
  const changed = name.trim() !== category.name || isPrivate !== (category.visibility === 'PRIVATE')
  const iconButton = 'rounded border border-gray-300 px-2 py-0.5 text-xs disabled:opacity-30'

  return (
    <li className="flex flex-wrap items-center gap-2 p-3">
      <div className="flex flex-col gap-1">
        <button type="button" aria-label={`${category.name} 위로`} disabled={first} onClick={() => onMove(-1)} className={iconButton}>▲</button>
        <button type="button" aria-label={`${category.name} 아래로`} disabled={last} onClick={() => onMove(1)} className={iconButton}>▼</button>
      </div>
      <input value={name} onChange={(e) => setName(e.target.value)} maxLength={30} aria-label={`${category.name} 이름`}
        className="min-w-0 flex-1 rounded-md border border-gray-300 px-2 py-1.5 text-sm" />
      <span className="text-xs text-gray-400">글 {category.postCount}</span>
      <label className="flex items-center gap-1 text-sm">
        <input type="checkbox" checked={isPrivate} onChange={(e) => setIsPrivate(e.target.checked)} aria-label={`${category.name} 비공개`} /> 비공개
      </label>
      <button type="button" disabled={!changed || !name.trim()} onClick={() => onSave(name.trim(), isPrivate ? 'PRIVATE' : 'PUBLIC')}
        className="rounded-md bg-indigo-600 px-3 py-1.5 text-xs text-white disabled:opacity-30">
        저장
      </button>
      {category.isDefault ? (
        <span className="px-2 text-xs text-gray-400">기본</span>
      ) : (
        <button type="button" onClick={onDelete} className="px-2 text-xs text-gray-500 hover:text-red-600">
          삭제
        </button>
      )}
    </li>
  )
}
