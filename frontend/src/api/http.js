/**
 * 后端根地址（不带末尾 /）。
 * - 显式 `VITE_API_BASE=` 空字符串：请求走当前站点同域 `/api/...`（生产需 Nginx 反代；开发需 Vite proxy）。
 * - 未设置：开发环境默认连仓库里的远程后端；生产环境默认同域（与上面空字符串一致）。
 * 注意：不要用 `||`，否则空字符串会被当成假值而误用默认 IP。
 */
import { clearAuth, getStoredAccessToken } from '../auth/session'

function resolveApiBase() {
  const v = import.meta.env.VITE_API_BASE
  if (v === '') return ''
  if (v != null && v !== '') return v
  // 开发环境默认走同域 /api（由 Vite proxy 转到本机后端），避免误连远端导致 token/权限不一致
  return ''
}

export const API_BASE = resolveApiBase()

const baseUrl = API_BASE

function buildUrl(path) {
  // 后端统一是 /api/v1 前缀
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${baseUrl}${path}`
}

export async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }

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
    headers,
    ...options
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

