import { useState } from 'react'

const MAX_TAGS = 10
const TAG_PATTERN = /^[가-힣a-zA-Z0-9_-]{1,20}$/

/** 태그 입력 (P-02): Enter·쉼표로 추가, ×로 삭제. 최대 10개, 한글·영문·숫자·_·-만, 대소문자 무시 중복 제거 */
export default function TagInput({ tags, onChange }) {
  const [text, setText] = useState('')
  const [error, setError] = useState('')

  const add = () => {
    const tag = text.trim().replace(/^#/, '')
    if (!tag) return
    if (!TAG_PATTERN.test(tag)) return setError('태그는 한글, 영문, 숫자, _, -만 쓸 수 있고 20자 이하예요.')
    if (tags.some((t) => t.toLowerCase() === tag.toLowerCase())) return setText('')
    if (tags.length >= MAX_TAGS) return setError('태그는 최대 10개까지 붙일 수 있어요.')
    onChange([...tags, tag])
    setText('')
    setError('')
  }

  const keyDown = (e) => {
    // 한글 입력 중(조합 중) Enter는 글자 확정용이라 무시해요. 안 그러면 "여행"이 "여행" + "행"으로 두 번 들어가요
    if (e.nativeEvent.isComposing || e.keyCode === 229) return
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault() // Enter가 폼 제출이 되지 않게
      add()
    } else if (e.key === 'Backspace' && !text && tags.length) {
      onChange(tags.slice(0, -1))
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2 rounded-md border border-gray-300 bg-white px-2 py-1.5">
        {tags.map((tag) => (
          <span key={tag} className="flex items-center gap-1 rounded-full bg-gray-100 px-2.5 py-0.5 text-sm">
            #{tag}
            <button type="button" aria-label={`${tag} 삭제`} onClick={() => onChange(tags.filter((t) => t !== tag))} className="text-gray-400 hover:text-gray-700">
              ×
            </button>
          </span>
        ))}
        <input
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            setError('') // 다시 입력하면 에러를 지워요
          }}
          onKeyDown={keyDown}
          onBlur={add}
          placeholder={tags.length ? '' : '태그 입력 후 Enter'}
          className="min-w-24 flex-1 py-0.5 text-sm focus:outline-none"
          aria-label="태그"
        />
      </div>
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}
