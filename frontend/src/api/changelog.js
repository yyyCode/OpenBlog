import { request } from './http'

export function fetchChangelogList(page = 0, size = 20) {
  return request(`/api/v1/changelog?page=${page}&size=${size}`)
}

export function fetchChangelogDetail(id) {
  return request(`/api/v1/changelog/${id}`)
}

export function createChangelog(payload) {
  return request('/api/v1/changelog', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateChangelog(id, payload) {
  return request(`/api/v1/changelog/${id}`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function deleteChangelog(id) {
  return request(`/api/v1/changelog/${id}`, {
    method: 'DELETE',
    withAuth: true
  })
}
