import { request } from './http'

export function fetchAllComments(page = 0, size = 50, status) {
  let url = `/api/v1/admin/comments?page=${page}&size=${size}`
  if (status) url += `&status=${encodeURIComponent(status)}`
  return request(url, { method: 'GET', withAuth: true })
}
