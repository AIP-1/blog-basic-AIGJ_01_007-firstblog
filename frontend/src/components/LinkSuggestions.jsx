import { useEffect, useState } from 'react'
import { getLinkSuggestions } from '../api/posts'

/**
 * [[ 자동완성 창 (LK-01). 글쓰기 화면이 "[[검색어" 상태(query)를 넘겨주면 내 글 → 다른 사람 공개 글 순으로 보여줘요.
 * 키보드(↑↓ Enter Esc)는 본문 입력칸에서 받아서 active/onPick으로 넘겨줘요.
 */
export default function LinkSuggestions({ query, active, onItems, onPick, onClose, myUsername }) {
  const [items, setItems] = useState({ q: null, list: [] })

  useEffect(() => {
    let ignore = false
    const timer = setTimeout(() => {
      getLinkSuggestions(query)
        .then((list) => {
          if (ignore) return
          setItems({ q: query, list })
          onItems(list)
        })
        .catch(() => {})
    }, 150)
    return () => {
      ignore = true
      clearTimeout(timer)
    }
  }, [query, onItems])

  const loading = items.q !== query
  return (
    <div role="listbox" aria-label="연결할 글" className="rounded-lg border border-indigo-200 bg-white p-2 text-sm shadow-lg">
      <div className="flex items-center justify-between px-2 pb-1 text-xs text-gray-500">
        <span>🔗 연결할 글 {query ? `“${query}”` : '(제목을 입력해 찾아요)'}</span>
        <button type="button" onClick={onClose} className="hover:text-gray-800">닫기 Esc</button>
      </div>
      {!loading && items.list.length === 0 && <p className="px-2 py-2 text-gray-500">찾는 글이 없어요.</p>}
      <ul>
        {items.list.map((p, i) => (
          <li key={p.id}>
            <button
              type="button"
              role="option"
              aria-selected={i === active}
              onMouseDown={(e) => e.preventDefault()} // 입력칸 포커스를 잃지 않게
              onClick={() => onPick(p)}
              className={`flex w-full items-baseline gap-2 rounded px-2 py-1.5 text-left ${i === active ? 'bg-indigo-50 text-indigo-700' : 'hover:bg-gray-50'}`}
            >
              <span className="truncate font-medium">{p.title}</span>
              <span className="shrink-0 text-xs text-gray-400">{p.username === myUsername ? '내 글' : `@${p.username}`}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
