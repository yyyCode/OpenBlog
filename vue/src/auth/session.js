/**
 * 控制台访问：仅依赖登录态（JWT），不依赖「是否先访问过前台」等弱门槛。
 */

function b64UrlDecode(segment) {
  let b64 = segment.replace(/-/g, '+').replace(/_/g, '/')
  while (b64.length % 4) b64 += '='
  return atob(b64)
}

export function getStoredAccessToken() {
  const t = localStorage.getItem('accessToken')
  if (typeof t !== 'string') return null
  const s = t.trim()
  return s.length > 0 ? s : null
}

/** 是否为标准 JWT 外形（header.payload.sig） */
export function isLikelyJwt(token) {
  return typeof token === 'string' && token.split('.').length === 3
}

export function parseJwtPayload(token) {
  if (!isLikelyJwt(token)) return null
  try {
    const json = b64UrlDecode(token.split('.')[1])
    return JSON.parse(json)
  } catch {
    return null
  }
}

export function isJwtExpired(token) {
  const payload = parseJwtPayload(token)
  if (!payload) return true
  if (typeof payload.exp !== 'number') return false
  return Date.now() >= payload.exp * 1000
}

export function clearAuth() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
}

export function isConsoleSessionValid() {
  const token = getStoredAccessToken()
  if (!token || !isLikelyJwt(token) || isJwtExpired(token)) return false
  return true
}

/** JWT payload 中的 role（ADMIN / AUTHOR / READER） */
export function getAccessTokenRole() {
  const token = getStoredAccessToken()
  if (!token) return null
  const payload = parseJwtPayload(token)
  const r = payload?.role
  return typeof r === 'string' ? r : null
}

/** 是否可进入 /console（与后台发文能力一致） */
export function canAccessConsole() {
  const r = getAccessTokenRole()
  return r === 'ADMIN' || r === 'AUTHOR'
}
