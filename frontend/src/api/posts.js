import { api } from './client'

/** API 6.1 메인 최신·인기 (+ 토픽 필터). sort: 'latest' | 'popular' */
export function getMainPosts({ sort, topic, page }) {
  return api.get('/posts', { params: { sort, topic: topic ?? undefined, page } }).then((r) => r.data)
}

let topicsCache
/** API 6.9 토픽 목록. 거의 바뀌지 않아서 앱을 켠 동안 한 번만 불러와요 */
export function getTopics() {
  topicsCache ??= api
    .get('/topics')
    .then((r) => r.data)
    .catch((error) => {
      topicsCache = undefined // 실패하면 다음에 다시 시도해요
      throw error
    })
  return topicsCache
}

/** API 6.2 검색 */
export function searchPosts({ q, page }) {
  return api.get('/posts/search', { params: { q, page } }).then((r) => r.data)
}

/** API 6.3 태그 모아보기 → { tag, posts } */
export function getTagPosts({ tag, page }) {
  return api.get(`/tags/${encodeURIComponent(tag)}/posts`, { params: { page } }).then((r) => r.data)
}

/** API 6.5 글 상세 (방문자가 보면 조회수 +1) */
export function getPostDetail(username, postId) {
  return api.get(`/blogs/${username}/posts/${postId}`).then((r) => r.data)
}

/** API 6.8 글 삭제 */
export function deletePost(postId) {
  return api.delete(`/posts/${postId}`)
}

/** API 9.1 좋아요 누르기·취소 → { liked, likeCount } */
export function setLike(postId, liked) {
  return (liked ? api.put : api.delete)(`/posts/${postId}/like`).then((r) => r.data)
}

/** API 9.2 북마크 추가·해제 → { bookmarked } */
export function setBookmark(postId, bookmarked) {
  return (bookmarked ? api.put : api.delete)(`/posts/${postId}/bookmark`).then((r) => r.data)
}
