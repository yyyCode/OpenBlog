import { request } from './http'

/** 提交反馈：需登录（后端按账号每天限一次），提交人取自 JWT */
export function createFeedback(payload) {
  return request('/api/v1/feedback', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

