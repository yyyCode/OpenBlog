import { request } from './http'

export function fetchForumTopics(params = {}) {
  const page = params.page ?? 0
  const size = params.size ?? 20
  return request(`/api/v1/forum/topics?page=${page}&size=${size}`)
}

export function fetchForumTopicDetail(id) {
  return request(`/api/v1/forum/topics/${id}`)
}

export function createForumTopic(data) {
  return request('/api/v1/forum/topics', {
    method: 'POST',
    body: JSON.stringify(data),
    withAuth: true
  })
}

export function fetchForumComments(topicId, params = {}) {
  const page = params.page ?? 0
  const size = params.size ?? 50
  return request(`/api/v1/forum/topics/${topicId}/comments?page=${page}&size=${size}`)
}

export function createForumComment(topicId, data) {
  return request(`/api/v1/forum/topics/${topicId}/comments`, {
    method: 'POST',
    body: JSON.stringify(data),
    withAuth: true
  })
}

export function deleteForumComment(commentId) {
  return request(`/api/v1/forum/comments/${commentId}`, {
    method: 'DELETE',
    withAuth: true
  })
}

// Admin
export function fetchAdminForumTopics(params = {}) {
  const page = params.page ?? 0
  const size = params.size ?? 20
  const status = params.status
  let url = `/api/v1/admin/forum/topics?page=${page}&size=${size}`
  if (status) url += `&status=${encodeURIComponent(status)}`
  return request(url, { method: 'GET', withAuth: true })
}

export function hideForumTopic(id) {
  return request(`/api/v1/admin/forum/topics/${id}/hide`, {
    method: 'PUT',
    withAuth: true
  })
}

export function publishForumTopic(id) {
  return request(`/api/v1/admin/forum/topics/${id}/publish`, {
    method: 'PUT',
    withAuth: true
  })
}
