import { API_BASE, request } from './http'

export function fetchArticles(params = {}) {
  const size = params.size ?? 20
  const page = params.page ?? 0
  const categoryId = params.categoryId
  const q = categoryId != null ? `&categoryId=${encodeURIComponent(categoryId)}` : ''
  return request(`/api/v1/articles?page=${page}&size=${size}${q}`)
}

export function fetchArticleDetail(id) {
  return request(`/api/v1/articles/${id}`)
}

export function coverUrl(key) {
  if (!key) return ''
  return `${API_BASE}/api/v1/media/files/${key}`
}

