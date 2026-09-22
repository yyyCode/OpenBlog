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
  const { username, email, password, code } = payload
  return request('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, email, password, code })
  })
}

/**
 * 发送邮箱验证码。purpose: 'register'（默认，邮箱需未注册）| 'reset'（邮箱需已注册）。
 * sliderChallengeId 为滑块通过后的一次性凭证；服务端滑块关闭时可不传。
 */
export function sendEmailCode(email, purpose = 'register', sliderChallengeId = null) {
  const payload = { email, purpose }
  if (sliderChallengeId) payload.sliderChallengeId = sliderChallengeId
  return request('/api/v1/auth/email-code', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

/** 取滑块挑战。data.enabled=false 表示服务端未开启滑块，直接发码即可。 */
export function fetchSliderChallenge() {
  return request('/api/v1/auth/slider-challenge', { method: 'GET' })
}

/** 提交滑块落点与拖动轨迹（trail: [{x, t}]，t 为相对拖动开始的毫秒数）。 */
export function completeSlider(challengeId, x, trail) {
  return request('/api/v1/auth/slider-complete', {
    method: 'POST',
    body: JSON.stringify({ challengeId, x, trail })
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

