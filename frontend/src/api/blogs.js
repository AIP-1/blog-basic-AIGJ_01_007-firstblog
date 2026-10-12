import { api } from './client'

/** API 5.1 블로그 홈 정보 */
export function getBlogHome(username) {
  return api.get(`/blogs/${username}`).then((r) => r.data)
}

/** API 5.3 카테고리 사이드바 → { totalPostCount, categories } */
export function getSidebar(username) {
  return api.get(`/blogs/${username}/categories`).then((r) => r.data)
}

/** API 6.4 블로그 글 목록 (categoryId, date(YYYY-MM-DD, 그날의 기록)는 선택) */
export function getBlogPosts(username, { categoryId, date, page }) {
  return api
    .get(`/blogs/${username}/posts`, { params: { categoryId: categoryId ?? undefined, date: date ?? undefined, page } })
    .then((r) => r.data)
}

/** API 14.1 기록 그래프 → { nodes, edges, truncated } */
export function getGraph(username) {
  return api.get(`/blogs/${username}/graph`).then((r) => r.data)
}

/** API 14.2 기록 잔디 → { from, to, days: [{ date, count }], currentStreak, longestStreak, totalThisYear } */
export function getActivity(username) {
  return api.get(`/blogs/${username}/activity`).then((r) => r.data)
}

/** API 14.3 서비스 전체 우주 (모든 블로그의 공개 글) */
export function getUniverse() {
  return api.get('/universe').then((r) => r.data)
}
