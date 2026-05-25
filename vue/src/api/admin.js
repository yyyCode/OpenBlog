import { API_BASE, downloadWithAuth, request } from './http'
import { getStoredAccessToken } from '../auth/session'

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

export function importArticleMd(file, { mode = 'create', articleId } = {}) {
  const token = getStoredAccessToken()
  if (!token) {
    const err = new Error('未登录')
    err.httpStatus = 401
    throw err
  }
  const form = new FormData()
  form.append('file', file)
  form.append('mode', mode)
  if (articleId != null) {
    form.append('articleId', String(articleId))
  }
  const base = API_BASE
  const url = `${base}/api/v1/articles/import`
  return fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form
  }).then(async (res) => {
    const text = await res.text()
    let json = null
    try {
      json = text ? JSON.parse(text) : null
    } catch {
      // ignore
    }
    if (json && typeof json.code === 'number' && json.code !== 0) {
      const err = new Error(json.message || '导入失败')
      err.code = json.code
      err.httpStatus = res.status
      err.traceId = json.traceId
      throw err
    }
    if (!res.ok) {
      const err = new Error(`HTTP ${res.status}`)
      err.httpStatus = res.status
      throw err
    }
    return json?.data ?? json
  })
}

export function exportArticleMd(articleId) {
  return downloadWithAuth(`/api/v1/users/me/articles/${articleId}/export`)
}

