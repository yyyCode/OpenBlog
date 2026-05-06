/**
 * 后端根地址（不带末尾 /）。
 * - 显式 `VITE_API_BASE=` 空字符串：请求走当前站点同域 `/api/...`（生产需 Nginx 反代；开发需 Vite proxy）。
 * - 未设置：开发环境默认连仓库里的远程后端；生产环境默认同域（与上面空字符串一致）。
 * 注意：不要用 `||`，否则空字符串会被当成假值而误用默认 IP。
 */
import {
  clearAuth,
  getStoredAccessToken,
  getStoredRefreshToken,
  hasUsableRefreshToken,
  isJwtExpired,
  isLikelyJwt,
  parseJwtPayload
} from '../auth/session'

function resolveApiBase() {
  const v = import.meta.env.VITE_API_BASE
  if (v === '') return ''
  if (v != null && v !== '') return v
  // 开发环境默认走同域 /api（由 Vite proxy 转到本机后端），避免误连远端导致 token/权限不一致
  return ''
}

export const API_BASE = resolveApiBase()

function buildUrl(path) {
  // 后端统一是 /api/v1 前缀
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${API_BASE}${path}`
}

/** 并发刷新合并为同一 Promise */
let refreshInflight = null

/**
 * 使用 refreshToken 换发双 token（不经过 request，避免环）。
 * 失败时会 clearAuth。
 */
export function refreshSessionTokens() {
  if (refreshInflight) return refreshInflight

  refreshInflight = (async () => {
    const rt = getStoredRefreshToken()
    if (!rt || !isLikelyJwt(rt) || isJwtExpired(rt)) {
      clearAuth()
      const err = new Error('登录已失效')
      err.httpStatus = 401
      throw err
    }

    const res = await fetch(buildUrl('/api/v1/auth/refresh'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: rt })
    })

    const text = await res.text()
    let json = null
    try {
      json = text ? JSON.parse(text) : null
    } catch {
      // ignore
    }

    if (!res.ok || (json && typeof json.code === 'number' && json.code !== 0)) {
      clearAuth()
      const err = new Error(json?.message || '登录已失效')
      err.httpStatus = res.status || 401
      err.code = json?.code
      throw err
    }

    const data = json?.data
    if (!data?.accessToken || !data?.refreshToken) {
      clearAuth()
      const err = new Error('刷新返回异常')
      err.httpStatus = 401
      throw err
    }

    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    return data
  })()

  return refreshInflight.finally(() => {
    refreshInflight = null
  })
}

function accessExpiresWithinSeconds(seconds) {
  const t = getStoredAccessToken()
  if (!t || !isLikelyJwt(t)) return true
  const p = parseJwtPayload(t)
  if (typeof p.exp !== 'number') return false
  return p.exp * 1000 - Date.now() < seconds * 1000
}

function redirectConsoleLoginIfNeeded() {
  if (typeof window !== 'undefined' && window.location.pathname.startsWith('/console')) {
    const redirect = window.location.pathname + window.location.search
    window.location.assign('/console/login?redirect=' + encodeURIComponent(redirect))
  }
}

export async function request(path, options = {}) {
  const isRetry = Boolean(options._refreshRetried)
  const extraHeaders = options.headers && typeof options.headers === 'object' ? options.headers : {}
  const headers = {
    'Content-Type': 'application/json',
    ...extraHeaders
  }

  if (options.withAuth) {
    if (
      !isRetry &&
      hasUsableRefreshToken() &&
      (!getStoredAccessToken() || accessExpiresWithinSeconds(90))
    ) {
      try {
        await refreshSessionTokens()
      } catch {
        // 无 access 时交给后续分支；将过期 access 时可能仍失败，再由 401 重试
      }
    }

    const token = getStoredAccessToken()
    if (!token) {
      const err = new Error('未登录')
      err.httpStatus = 401
      throw err
    }
    headers.Authorization = `Bearer ${token}`
  }

  const fetchOpts = { ...options }
  delete fetchOpts.withAuth
  delete fetchOpts._refreshRetried
  delete fetchOpts.headers

  const res = await fetch(buildUrl(path), {
    ...fetchOpts,
    headers
  })

  const text = await res.text()
  let json = null
  if (!_skipJson) {
    try {
      json = text ? JSON.parse(text) : null
    } catch {
      // ignore
    }
  }

  if (res.status === 401 && options.withAuth && !isRetry) {
    try {
      await refreshSessionTokens()
      return request(path, { ...options, _refreshRetried: true })
    } catch {
      redirectConsoleLoginIfNeeded()
      const err = new Error('登录已失效')
      err.httpStatus = 401
      throw err
    }
  }

  if (res.status === 401 && options.withAuth && isRetry) {
    clearAuth()
    redirectConsoleLoginIfNeeded()
    const err = new Error('登录已失效')
    err.httpStatus = 401
    throw err
  }

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
