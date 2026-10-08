import { api } from './client'

/** API 5.4 → CategoryItem */
export const createCategory = (blogId, name, visibility) =>
  api.post(`/blogs/${blogId}/categories`, { name, visibility }).then((r) => r.data)

/** API 5.5 바뀐 순서대로 모든 카테고리 id */
export const reorderCategories = (blogId, categoryIds) => api.put(`/blogs/${blogId}/categories/order`, { categoryIds })

/** API 5.6 → CategoryItem */
export const updateCategory = (categoryId, name, visibility) =>
  api.put(`/categories/${categoryId}`, { name, visibility }).then((r) => r.data)

/** API 5.7 → { movedPostCount, movedToCategoryId } */
export const deleteCategory = (categoryId) => api.delete(`/categories/${categoryId}`).then((r) => r.data)
