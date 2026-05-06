import { request } from './http'

export function fetchPendingFeedback(page = 0, size = 50) {
  return request(`/api/v1/admin/feedback/pending?page=${page}&size=${size}`, {
    method: 'GET',
    withAuth: true
  })
}

