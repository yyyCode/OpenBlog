/**
 * 设备令牌单例：向网关签发端点换取服务端签名令牌（X-Device-Token）。
 *
 * 背景：设备指纹是客户端自报 header，脚本可每请求伪造一个新指纹换限流桶；网关由此引入
 * 「签名设备令牌」——只有持有效令牌的请求才按令牌内随机 deviceId 分桶，无令牌一律纯 IP。
 * 本模块负责首次取令牌、按服务端口径的过期时间提前续签、并缓存到 sessionStorage
 * （令牌 7 天有效、与账号无关，存 session 足够本次会话复用；关标签页即弃）。
 *
 * 失败一律返回 null 由调用方静默降级：网关对无令牌请求回退纯 IP 限流，不拦真实用户。
 */
import { buildUrl } from '../api/base'

const STORAGE_KEY = 'device_token'
const STORAGE_EXPIRES_KEY = 'device_token_expires_at'
// 剩余有效期低于该值即提前续签：防服务端口径过期 + 两端时钟偏差，避免请求用到已失效令牌
const REFRESH_AHEAD_MS = 5 * 60 * 1000

let inflight = null

/**
 * @returns {Promise<string|null>} 有效设备令牌；取不到（未部署/签发失败/网络异常）返回 null。
 *   并发调用共享同一 in-flight 请求，避免首次进入时多个请求同时去签发。
 */
export async function getDeviceToken() {
  const cached = readCached()
  if (cached && cached.expiresAt - Date.now() > REFRESH_AHEAD_MS) {
    return cached.token
  }
  if (!inflight) {
    inflight = fetchToken()
  }
  return inflight
}

async function fetchToken() {
  try {
    const res = await fetch(buildUrl('/api/v1/devices/token'), { method: 'POST' })
    if (!res.ok) return null
    const json = await res.json()
    const token = json?.data?.token
    const expiresAt = json?.data?.expiresAt
    if (typeof token !== 'string' || !token || typeof expiresAt !== 'number') {
      return null
    }
    writeCached(token, expiresAt)
    return token
  } catch {
    // 网络异常/网关未部署该端点 → 返回 null，按无令牌处理（网关回退纯 IP）
    return null
  } finally {
    inflight = null
  }
}

function readCached() {
  try {
    const token = window.sessionStorage.getItem(STORAGE_KEY)
    const expiresAt = Number(window.sessionStorage.getItem(STORAGE_EXPIRES_KEY))
    if (token && Number.isFinite(expiresAt) && expiresAt > 0) {
      return { token, expiresAt }
    }
    return null
  } catch {
    return null
  }
}

function writeCached(token, expiresAt) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, token)
    window.sessionStorage.setItem(STORAGE_EXPIRES_KEY, String(expiresAt))
  } catch {
    // 隐私模式禁用存储 → 忽略，本次内存由 inflight 承担
  }
}
