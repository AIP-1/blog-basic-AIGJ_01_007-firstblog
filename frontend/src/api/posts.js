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
