import { request } from './http'

export function fetchAdminUsers({ keyword, status, role, page = 0, size = 24 } = {}) {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(size))
  if (keyword) params.set('keyword', keyword)
  if (status) params.set('status', status)
  if (role) params.set('role', role)
  return request(`/api/v1/admin/users?${params.toString()}`, { method: 'GET', withAuth: true })
}

export function fetchPendingReaders() {
  return request('/api/v1/admin/users/pending', { method: 'GET', withAuth: true })
}

export function approveReader(userId) {
  return request(`/api/v1/admin/users/${userId}/approve`, { method: 'POST', withAuth: true })
}

export function fetchUserDetail(userId) {
  return request(`/api/v1/admin/users/${userId}`, { method: 'GET', withAuth: true })
}

export function changeUserStatus(userId, status) {
  return request(`/api/v1/admin/users/${userId}/status`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify({ status })
  })
}

export function changeUserRole(userId, role) {
  return request(`/api/v1/admin/users/${userId}/role`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify({ role })
  })
}
