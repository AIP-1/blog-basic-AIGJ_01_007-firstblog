import { api } from './client'

/** API 12.1 곧 열리는 타임캡슐 → [{ id, title, sealedAt, openAt }] */
export const getSealedCapsules = (username) => api.get(`/blogs/${username}/time-capsules`).then((r) => r.data)

/** API 12.2 최근 열린 타임캡슐 → PostCard[] */
export const getOpenedCapsules = () => api.get('/time-capsules/opened').then((r) => r.data)
