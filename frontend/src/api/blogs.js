import { api } from './client'

/** API 5.1 블로그 홈 정보 */
export function getBlogHome(username) {
  return api.get(`/blogs/${username}`).then((r) => r.data)
}

/** API 5.3 카테고리 사이드바 → { totalPostCount, categories } */
export function getSidebar(username) {
  return api.get(`/blogs/${username}/categories`).then((r) => r.data)
}

/** API 6.4 블로그 글 목록 (categoryId는 선택) */
export function getBlogPosts(username, { categoryId, page }) {
  return api.get(`/blogs/${username}/posts`, { params: { categoryId: categoryId ?? undefined, page } }).then((r) => r.data)
}
