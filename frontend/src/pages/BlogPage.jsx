import { Link, useParams, useSearchParams } from 'react-router'
import { getBlogHome, getBlogPosts, getSidebar } from '../api/blogs'
import { useAuth } from '../auth/useAuth'
import Pagination from '../components/Pagination'
import PostGrid from '../components/PostGrid'
import UpcomingCapsules from '../components/UpcomingCapsules'
import { useAsync } from '../hooks/useAsync'
import NotFoundPage from './NotFoundPage'

/** 블로그 홈 /@아이디 (B-01 ~ B-03, S-07): 프로필, 카테고리 사이드바(글 수), 글 목록 */
export default function BlogPage() {
  const { handle } = useParams()
  const username = handle.startsWith('@') ? handle.slice(1) : null
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  const categoryId = searchParams.get('category')
  const page = Number(searchParams.get('page')) || 1
  const viewer = user?.id // 주인에게는 비공개 글·카테고리까지 보여서 로그인 상태가 바뀌면 다시 불러와요

  const home = useAsync(() => getBlogHome(username), `${username}|${viewer}`)
  const sidebar = useAsync(() => getSidebar(username), `${username}|${viewer}`)
  const posts = useAsync(() => getBlogPosts(username, { categoryId, page }), `${username}|${categoryId}|${page}|${viewer}`)

  if (!username) return <NotFoundPage /> // @로 시작하지 않으면 블로그 주소가 아니에요
  if (home.status === 404) return <NotFoundPage message="존재하지 않는 블로그예요" />
  if (home.loading) return <p className="py-16 text-center text-gray-500">불러오는 중…</p>
  if (home.error) return <p className="py-16 text-center text-red-600">{home.error}</p>

  const blog = home.data
  const categories = sidebar.data?.categories ?? []
  const current = categories.find((c) => String(c.id) === categoryId)
  const linkClass = (active) =>
    `flex shrink-0 justify-between gap-3 rounded-md px-3 py-1.5 text-sm ${active ? 'bg-indigo-50 font-semibold text-indigo-700' : 'text-gray-700 hover:bg-gray-100'}`

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 rounded-lg border border-gray-200 bg-white p-5 sm:flex-row sm:items-center">
        {blog.owner.profileImageUrl ? (
          <img src={blog.owner.profileImageUrl} alt="" className="h-16 w-16 rounded-full object-cover" />
        ) : (
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-indigo-100 text-xl font-bold text-indigo-600">
            {blog.owner.nickname.slice(0, 1)}
          </div>
        )}
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{blog.name}</h1>
          {blog.description && <p className="mt-1 text-gray-600">{blog.description}</p>}
          <p className="mt-1 text-sm text-gray-500">
            {blog.owner.nickname} @{blog.owner.username}
            {blog.owner.bio && ` · ${blog.owner.bio}`}
          </p>
        </div>
        {blog.isOwner && (
          <div className="flex gap-2 text-sm">
            <Link to="/write" className="rounded-md bg-indigo-600 px-3 py-1.5 text-white">
              글쓰기
            </Link>
            <Link to="/settings/categories" className="rounded-md border border-gray-300 px-3 py-1.5">
              카테고리 관리
            </Link>
          </div>
        )}
      </header>

      <div className="flex flex-col gap-6 lg:flex-row">
        <aside className="lg:w-56 lg:shrink-0">
          <nav className="flex gap-1 overflow-x-auto lg:flex-col" aria-label="카테고리">
            <Link to={`/@${username}`} className={linkClass(!categoryId)}>
              <span>전체</span>
              <span className="text-gray-400">{sidebar.data?.totalPostCount ?? ''}</span>
            </Link>
            {categories.map((c) => (
              <Link key={c.id} to={`/@${username}?category=${c.id}`} className={linkClass(String(c.id) === categoryId)}>
                <span>
                  {c.visibility === 'PRIVATE' && '🔒 '}
                  {c.name}
                </span>
                <span className="text-gray-400">{c.postCount}</span>
              </Link>
            ))}
          </nav>
        </aside>

        <section className="min-w-0 flex-1 space-y-4">
          {!categoryId && page === 1 && <UpcomingCapsules username={username} viewerKey={viewer} />}
          {current && <h2 className="mb-4 text-lg font-semibold">{current.name}</h2>}
          <PostGrid loading={posts.loading} error={posts.error} posts={posts.data?.content} />
          {!posts.loading && posts.data && <Pagination page={posts.data.page} totalPages={posts.data.totalPages} />}
        </section>
      </div>
    </div>
  )
}
