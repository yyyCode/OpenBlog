import { downloadWithAuth, request } from './http'

export function login(account, password) {
  return request('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ account, password })
  })
}

/** 前台账号注册（与控制台管理员登录入口分离） */
export function register(payload) {
  const { username, email, password, code } = payload
  return request('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, email, password, code })
  })
}

/**
 * 发送邮箱验证码。purpose: 'register'（默认，邮箱需未注册）| 'reset'（邮箱需已注册）。
 */
export function sendEmailCode(email, purpose = 'register') {
  return request('/api/v1/auth/email-code', {
    method: 'POST',
    body: JSON.stringify({ email, purpose })
  })
}

/** 通过注册邮箱的验证码重置密码（无需登录） */
export function changePassword(email, code, newPassword) {
  return request('/api/v1/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ email, code, newPassword })
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

export function unpublishArticle(id) {
  return request(`/api/v1/articles/${id}/unpublish`, { method: 'POST', withAuth: true })
}

export function importArticleMd(file, { mode = 'create', articleId } = {}) {
  const form = new FormData()
  form.append('file', file)
  form.append('mode', mode)
  if (articleId != null) {
    form.append('articleId', String(articleId))
  }
  // request 会自动跳过 FormData 的 Content-Type，并统一处理鉴权/错误码/401
  return request('/api/v1/articles/import', {
    method: 'POST',
    withAuth: true,
    body: form
  })
}

export function exportArticleMd(articleId) {
  return downloadWithAuth(`/api/v1/users/me/articles/${articleId}/export`)
}

