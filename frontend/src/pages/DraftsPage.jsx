import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { errorMessage } from '../api/client'
import { deleteDraft, getDrafts } from '../api/drafts'
import Pagination from '../components/Pagination'
import { useAsync } from '../hooks/useAsync'
import { formatDate, formatTime } from '../utils/format'

/** 임시저장 목록 (D-04): 최근 저장 순, 이어 쓰기·삭제 */
export default function DraftsPage() {
  const [searchParams] = useSearchParams()
  const page = Math.max(1, Math.floor(Number(searchParams.get('page'))) || 1)
  const [version, setVersion] = useState(0)
  const { data, error, loading } = useAsync(() => getDrafts(page), `${page}|${version}`)

  const remove = async (id) => {
    if (!window.confirm('임시저장 글을 삭제할까요?')) return
    try {
      await deleteDraft(id)
      setVersion((v) => v + 1)
    } catch (err) {
      window.alert(errorMessage(err))
    }
  }

  return (
    <section className="mx-auto max-w-3xl space-y-4">
      <h1 className="text-xl font-bold">임시저장 글</h1>
      {loading && <p className="py-10 text-center text-gray-500">불러오는 중…</p>}
      {error && <p className="py-10 text-center text-red-600">{error}</p>}
      {data && !data.content.length && <p className="py-10 text-center text-gray-500">임시저장한 글이 없어요.</p>}
      <ul className="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white">
        {data?.content.map((d) => (
          <li key={d.id} className="flex items-center gap-4 p-4">
            <Link to={`/write/${d.id}`} className="min-w-0 flex-1">
              <p className={`truncate font-medium ${d.title ? '' : 'text-gray-400'}`}>{d.title ?? '제목 없음'}</p>
              {d.summary && <p className="truncate text-sm text-gray-500">{d.summary}</p>}
              <p className="text-xs text-gray-400">
                {formatDate(d.updatedAt)} {formatTime(d.updatedAt)} 저장
              </p>
            </Link>
            <button type="button" onClick={() => remove(d.id)} className="shrink-0 text-sm text-gray-500 hover:text-red-600">
              삭제
            </button>
          </li>
        ))}
      </ul>
      {data && <Pagination page={data.page} totalPages={data.totalPages} />}
    </section>
  )
}
