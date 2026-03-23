import { request } from './http'

export function fetchPublicProfile() {
  // 已登录时也希望前台右侧头像/昵称/签名随当前用户更新
  const token = localStorage.getItem('accessToken')
  if (token) {
    return request(`/api/v1/profile`, { withAuth: true })
  }
  return request(`/api/v1/profile`)
}

