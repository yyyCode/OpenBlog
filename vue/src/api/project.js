import { API_BASE, request } from './http'

export function fetchProjects(page = 0, size = 20) {
  return request(`/api/v1/projects?page=${page}&size=${size}`)
}

export function fetchProjectDetail(id) {
  return request(`/api/v1/projects/${id}`)
}

/** 控制台管理列表：含草稿，仅 ADMIN */
export function fetchAdminProjects(page = 0, size = 50) {
  return request(`/api/v1/projects/admin?page=${page}&size=${size}`, { withAuth: true })
}

/** 控制台取单条（含草稿正文），仅 ADMIN */
export function fetchAdminProjectDetail(id) {
  return request(`/api/v1/projects/${id}`, { withAuth: true })
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
  return `${API_BASE}/api/v1/media/files/${key}`
}
