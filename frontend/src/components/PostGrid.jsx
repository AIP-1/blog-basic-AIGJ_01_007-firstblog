import PostCard from './PostCard'

/** 카드 그리드: 휴대폰 1열, 태블릿 2열, PC 3열 + 불러오는 중·에러·빈 목록 상태 */
export default function PostGrid({ loading, error, posts, emptyText = '아직 글이 없어요.' }) {
  if (loading) return <p className="py-16 text-center text-gray-500">불러오는 중…</p>
  if (error) return <p className="py-16 text-center text-red-600">{error}</p>
  if (!posts?.length) return <p className="py-16 text-center text-gray-500">{emptyText}</p>

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  )
}
