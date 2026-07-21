import { request } from './http'

export function fetchProjects(page = 0, size = 20) {
  return request(`/api/v1/projects?page=${page}&size=${size}`)
}

export function fetchProjectDetail(id) {
  return request(`/api/v1/projects/${id}`)
}

export function createProject(payload) {
  return request('/api/v1/projects', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateProject(id, payload) {
  return request(`/api/v1/projects/${id}`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function deleteProject(id) {
  return request(`/api/v1/projects/${id}`, {
    method: 'DELETE',
    withAuth: true
  })
}

export function coverUrl(key) {
  if (!key) return ''
  const { API_BASE } = require('./http')
  return `${API_BASE}/api/v1/media/files/${key}`
}
