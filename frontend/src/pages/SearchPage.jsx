import { useSearchParams } from 'react-router'
import { searchPosts } from '../api/posts'
import Pagination from '../components/Pagination'
import PostGrid from '../components/PostGrid'
import { useAsync } from '../hooks/useAsync'

/** 검색 결과 (S-05): /search?q=...&page= */
export default function SearchPage() {
  const [searchParams] = useSearchParams()
  const q = (searchParams.get('q') ?? '').trim()
  const page = Math.max(1, Math.floor(Number(searchParams.get('page'))) || 1)
  const result = useAsync(() => (q ? searchPosts({ q, page }) : Promise.resolve(null)), `${q}|${page}`)

  if (!q) return <p className="py-16 text-center text-gray-500">검색어를 입력해 주세요.</p>
  return (
    <section className="space-y-5">
      <h1 className="text-lg">
        <span className="font-semibold">‘{q}’</span> 검색 결과{' '}
        {result.data && <span className="text-gray-500">{result.data.totalElements}개</span>}
      </h1>
      <PostGrid loading={result.loading} error={result.error} posts={result.data?.content} emptyText="검색 결과가 없어요." />
      {!result.loading && result.data && <Pagination page={result.data.page} totalPages={result.data.totalPages} />}
    </section>
  )
}
