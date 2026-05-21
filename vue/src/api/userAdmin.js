import { request } from './http'

export function fetchAdminUsers(page = 0, size = 24) {
  return request(`/api/v1/admin/users?page=${page}&size=${size}`, { method: 'GET', withAuth: true })
}

export function fetchPendingReaders() {
  return request('/api/v1/admin/users/pending', { method: 'GET', withAuth: true })
}

export function approveReader(userId) {
  return request(`/api/v1/admin/users/${userId}/approve`, { method: 'POST', withAuth: true })
}
