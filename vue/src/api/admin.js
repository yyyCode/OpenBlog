import { request } from './http'

export function login(account, password) {
  return request('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ account, password })
  })
}

/** 前台账号注册（与控制台管理员登录入口分离） */
export function register(payload) {
  const { username, email, password } = payload
  return request('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, email, password })
  })
}

/** 通过注册邮箱验证后修改密码（无需登录） */
export function changePassword(email, newPassword) {
  return request('/api/v1/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ email, newPassword })
  })
}

export function fetchMe() {
  return request('/api/v1/users/me', { method: 'GET', withAuth: true })
}

export function updateMe(payload) {
  return request('/api/v1/users/me', {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function fetchMyArticles(page = 0, size = 50) {
  return request(`/api/v1/users/me/articles?page=${page}&size=${size}`, { method: 'GET', withAuth: true })
}

export function fetchMyArticleDetail(id) {
  return request(`/api/v1/users/me/articles/${id}`, { method: 'GET', withAuth: true })
}

export function createDraft(payload) {
  return request('/api/v1/articles', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateArticle(id, payload) {
  return request(`/api/v1/articles/${id}`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function publishArticle(id) {
  return request(`/api/v1/articles/${id}/publish`, { method: 'POST', withAuth: true })
}

export function publishArticleWithTime(id, payload) {
  return request(`/api/v1/articles/${id}/publish`, {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload || {})
  })
}

export function deleteMyArticle(id) {
  return request(`/api/v1/articles/${id}`, { method: 'DELETE', withAuth: true })
}

