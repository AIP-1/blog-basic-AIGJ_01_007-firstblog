import { api } from './client'

/** API 8.1 → { totalCount, comments } */
export const getComments = (postId) => api.get(`/posts/${postId}/comments`).then((r) => r.data)

/** API 8.2 (parentId가 있으면 답글) */
export const createComment = (postId, content, parentId) =>
  api.post(`/posts/${postId}/comments`, { content, parentId: parentId ?? null }).then((r) => r.data)

/** API 8.3 */
export const updateComment = (commentId, content) => api.patch(`/comments/${commentId}`, { content }).then((r) => r.data)

/** API 8.4 */
export const deleteComment = (commentId) => api.delete(`/comments/${commentId}`)

/** API 10.1, 10.2 신고. target: { postId } 또는 { commentId } */
export const report = ({ postId, commentId }, reason, detail) =>
  api.post(postId ? `/posts/${postId}/reports` : `/comments/${commentId}/reports`, { reason, detail: detail || null })
