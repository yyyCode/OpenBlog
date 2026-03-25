import { API_BASE, request } from './http'

export function fetchArticles(params = {}) {
  // GET /api/v1/articles?page&size&sort... 简化：只支持 size
  const size = params.size ?? 20
  const page = params.page ?? 0
  return request(`/api/v1/articles?page=${page}&size=${size}`)
}

export function fetchArticleDetail(id) {
  return request(`/api/v1/articles/${id}`)
}

export function coverUrl(key) {
  if (!key) return ''
  return `${API_BASE}/api/v1/media/files/${key}`
}

