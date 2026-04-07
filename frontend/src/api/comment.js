import { request } from './http'

export function listComments(articleId, page = 0, size = 20) {
  return request(`/api/v1/articles/${articleId}/comments?page=${page}&size=${size}`, {
    method: 'GET'
  })
}

export function createComment(articleId, content) {
  return request(`/api/v1/articles/${articleId}/comments`, {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify({ content })
  })
}

export function replyComment(commentId, content) {
  return request(`/api/v1/comments/${commentId}/replies`, {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify({ content })
  })
}

export function deleteComment(commentId) {
  return request(`/api/v1/comments/${commentId}`, {
    method: 'DELETE',
    withAuth: true
  })
}

