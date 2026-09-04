import { buildUrl } from './base'
// API_BASE 仍从 http.js 再导出：既有模块（api/*.js、ImageUploadField 等）都从 './http' 引入，保持不破坏
export { API_BASE } from './base'
import { clearAuth, getStoredAccessToken } from '../auth/session'
import { getDeviceFingerprint } from '../utils/deviceFingerprint'
import { getDeviceToken } from '../utils/deviceToken'

const FINGERPRINT_TIMEOUT_MS = 300

/** 尽力附带设备指纹头：300ms 内取不到（超时/失败/无指纹）则不带，网关降级 IP 兜底。 */
async function attachFingerprint(headers) {
  try {
    const fp = await withTimeout(getDeviceFingerprint(), FINGERPRINT_TIMEOUT_MS)
    if (fp) headers['X-Device-Fingerprint'] = fp
  } catch {
    // 超时或异常：不阻塞请求，交由网关 IP 兜底
  }
  return headers
}

const TOKEN_TIMEOUT_MS = 800

/** 尽力附带设备令牌头（X-Device-Token）：800ms 内取不到（未部署签发端点/超时/异常）则不带，
 * 网关对无令牌请求回退纯 IP 限流——仍优于"客户端自报指纹即可换桶"的旧语义。 */
async function attachToken(headers) {
  try {
    const token = await withTimeout(getDeviceToken(), TOKEN_TIMEOUT_MS)
    if (token) headers['X-Device-Token'] = token
  } catch {
    // 超时或异常：不阻塞请求，网关降级纯 IP
  }
  return headers
}

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('fingerprint timeout')), ms)
    promise.then(
      (v) => { clearTimeout(timer); resolve(v) },
      (e) => { clearTimeout(timer); reject(e) }
    )
  })
}

export async function request(path, options = {}) {
  const isFormData = options.body instanceof FormData
  const headers = {
    ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
    ...(options.headers || {})
  }

  await attachFingerprint(headers)
  await attachToken(headers)

  if (options.withAuth) {
    const token = getStoredAccessToken()
    if (!token) {
      const err = new Error('未登录')
      err.httpStatus = 401
      throw err
    }
    headers.Authorization = `Bearer ${token}`
  }

  const res = await fetch(buildUrl(path), {
    ...options,
    headers
  })

  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    // ignore
  }

  if (res.status === 401 && options.withAuth) {
    clearAuth()
    if (typeof window !== 'undefined' && window.location.pathname.startsWith('/console')) {
      const redirect = window.location.pathname + window.location.search
      window.location.assign('/console/login?redirect=' + encodeURIComponent(redirect))
    }
    const err = new Error('登录已失效')
    err.httpStatus = 401
    throw err
  }

  // API 统一返回 ApiResponse，HTTP 层可能仍为 200
  if (json && typeof json.code === 'number' && json.code !== 0) {
    const msg = json.message || '请求失败'
    const err = new Error(msg)
    err.code = json.code
    err.httpStatus = res.status
    err.traceId = json.traceId
    throw err
  }

  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`)
    err.httpStatus = res.status
    err.traceId = json?.traceId
    throw err
  }

  return json?.data ?? json
}

/**
 * 下载二进制响应（如 Markdown 导出）。鉴权失败或业务错误时抛出与 request 类似的 Error。
 */
export async function downloadWithAuth(path) {
  const token = getStoredAccessToken()
  if (!token) {
    const err = new Error('未登录')
    err.httpStatus = 401
    throw err
  }

  const headers = { Authorization: `Bearer ${token}` }
  await attachFingerprint(headers)
  await attachToken(headers)

  const res = await fetch(buildUrl(path), {
    method: 'GET',
    headers
  })

  if (res.status === 401) {
    clearAuth()
    if (typeof window !== 'undefined' && window.location.pathname.startsWith('/console')) {
      const redirect = window.location.pathname + window.location.search
      window.location.assign('/console/login?redirect=' + encodeURIComponent(redirect))
    }
    const err = new Error('登录已失效')
    err.httpStatus = 401
    throw err
  }

  const contentType = res.headers.get('Content-Type') || ''
  if (contentType.includes('application/json')) {
    const text = await res.text()
    let json = null
    try {
      json = text ? JSON.parse(text) : null
    } catch {
      // ignore
    }
    if (json && typeof json.code === 'number' && json.code !== 0) {
      const err = new Error(json.message || '请求失败')
      err.code = json.code
      err.httpStatus = res.status
      err.traceId = json.traceId
      throw err
    }
  }

  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`)
    err.httpStatus = res.status
    throw err
  }

  const blob = await res.blob()
  let filename = 'article.md'
  const disposition = res.headers.get('Content-Disposition')
  if (disposition) {
    const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    if (utf8?.[1]) {
      try {
        filename = decodeURIComponent(utf8[1].trim())
      } catch {
        filename = utf8[1].trim()
      }
    } else {
      const plain = /filename="?([^";]+)"?/i.exec(disposition)
      if (plain?.[1]) filename = plain[1].trim()
    }
  }
  return { blob, filename }
}

