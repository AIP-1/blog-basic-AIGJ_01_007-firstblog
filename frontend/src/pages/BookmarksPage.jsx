import { useSearchParams } from 'react-router'
import { getMyBookmarks } from '../api/users'
import Pagination from '../components/Pagination'
import PostGrid from '../components/PostGrid'
import { useAsync } from '../hooks/useAsync'

/** 내 북마크 (K-02): 북마크한 최신순, 지금 볼 수 없게 된 글은 빠져요 */
export default function BookmarksPage() {
  const [searchParams] = useSearchParams()
  const page = Number(searchParams.get('page')) || 1
  const { data, error, loading } = useAsync(() => getMyBookmarks(page), String(page))

  return (
    <section className="space-y-5">
      <h1 className="text-xl font-bold">북마크</h1>
      <PostGrid loading={loading} error={error} posts={data?.content} emptyText="북마크한 글이 없어요." />
      {data && <Pagination page={data.page} totalPages={data.totalPages} />}
    </section>
  )
}
