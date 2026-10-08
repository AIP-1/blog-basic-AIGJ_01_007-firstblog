import { api } from './client'

/** API 7.1 처음 임시저장 → { id, updatedAt } */
export const createDraft = (body) => api.post('/drafts', body).then((r) => r.data)

/** API 7.2 다시 임시저장 (덮어쓰기) → { id, updatedAt } */
export const saveDraft = (id, body) => api.put(`/drafts/${id}`, body).then((r) => r.data)

/** API 7.3 임시저장 목록 */
export const getDrafts = (page) => api.get('/drafts', { params: { page } }).then((r) => r.data)

/** API 7.4 불러오기 */
export const getDraft = (id) => api.get(`/drafts/${id}`).then((r) => r.data)

/** API 7.5 삭제 */
export const deleteDraft = (id) => api.delete(`/drafts/${id}`)

/** API 7.6 발행 (화면의 최신 내용과 함께) → { id, username } */
export const publishDraft = (id, body) => api.post(`/drafts/${id}/publish`, body).then((r) => r.data)
