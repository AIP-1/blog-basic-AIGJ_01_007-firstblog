import { api } from './client'

/** API 4.5 프로필 수정 → MeResponse */
export const updateProfile = (body) => api.put('/users/me', body).then((r) => r.data)

/** API 4.6 비밀번호 변경 */
export const changePassword = (body) => api.patch('/users/me/password', body)

/** API 4.7 탈퇴 (DELETE에 본문: axios는 data 옵션). 비밀번호가 없는 소셜 회원은 아이디로 확인해요 */
export const withdraw = (confirm) => api.delete('/users/me', { data: confirm })

/** API 4.8 내 북마크 목록 */
export const getMyBookmarks = (page) => api.get('/users/me/bookmarks', { params: { page } }).then((r) => r.data)

/** API 5.2 블로그 이름·소개 수정 */
export const updateBlog = (blogId, body) => api.put(`/blogs/${blogId}`, body).then((r) => r.data)
