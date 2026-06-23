import { request } from './http'

export function searchArticles(keyword, page = 0, size = 20) {
  return request(
    `/api/v1/articles/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`
  )
}
