import { api } from './client'

/** API 11.1 이미지 업로드 → "/uploads/2026/10/....png" */
export function uploadImage(file) {
  const form = new FormData()
  form.append('file', file)
  return api.post('/images', form).then((r) => r.data.url)
}
