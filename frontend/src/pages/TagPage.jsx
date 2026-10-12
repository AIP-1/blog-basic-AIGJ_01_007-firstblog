import { useParams, useSearchParams } from 'react-router'
import { getTagPosts } from '../api/posts'
import Pagination from '../components/Pagination'
import PostGrid from '../components/PostGrid'
import { useAsync } from '../hooks/useAsync'

/** 태그 모아보기 (S-06): /tags/:tagName */
export default function TagPage() {
  const { tagName } = useParams()
  const [searchParams] = useSearchParams()
  const page = Math.max(1, Math.floor(Number(searchParams.get('page'))) || 1)
  const result = useAsync(() => getTagPosts({ tag: tagName, page }), `${tagName}|${page}`)

  return (
    <section className="space-y-5">
      <h1 className="text-2xl font-bold">#{result.data?.tag ?? tagName}</h1>
      <PostGrid loading={result.loading} error={result.error} posts={result.data?.posts.content} emptyText="이 태그가 붙은 글이 없어요." />
      {!result.loading && result.data && <Pagination page={result.data.posts.page} totalPages={result.data.posts.totalPages} />}
    </section>
  )
}
