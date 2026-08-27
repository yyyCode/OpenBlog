import { getStoredAccessToken } from '../auth/session'
import { request } from './http'

export function fetchPublicProfile() {
  // 已登录时也希望前台右侧头像/昵称/签名随当前用户更新
  const token = getStoredAccessToken()
  if (token) {
    return request(`/api/v1/profile`, { withAuth: true })
  }
  return request(`/api/v1/profile`)
}

export function fetchOwnerProfile() {
  // 关于我们页：始终匿名请求，确保返回站点作者（而非当前登录用户）
  return request(`/api/v1/profile`)
}

