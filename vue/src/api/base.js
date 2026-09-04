/**
 * 后端根地址与 URL 拼接（独立小模块）。
 * 供 http.js 与 utils/deviceToken.js 共享，避免「http ↔ deviceToken」循环依赖：
 * http.js 需要附设备令牌、deviceToken 需要拼后端地址，二者都只依赖本模块。
 *
 * - 显式 `VITE_API_BASE=` 空字符串：请求走当前站点同域 `/api/...`（生产需 Nginx 反代；开发需 Vite proxy）。
 * - 未设置：开发环境默认连仓库里的远程后端；生产环境默认同域（与上面空字符串一致）。
 * 注意：不要用 `||`，否则空字符串会被当成假值而误用默认 IP。
 */
function resolveApiBase() {
  const v = import.meta.env.VITE_API_BASE
  if (v === '') return ''
  if (v != null && v !== '') return v
  // 开发环境默认走同域 /api（由 Vite proxy 转到本机后端），避免误连远端导致 token/权限不一致
  return ''
}

export const API_BASE = resolveApiBase()

const baseUrl = API_BASE

export function buildUrl(path) {
  // 后端统一是 /api/v1 前缀
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${baseUrl}${path}`
}
