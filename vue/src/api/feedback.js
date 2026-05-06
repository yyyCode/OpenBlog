import { request } from './http'

export function createFeedback(payload) {
  return request('/api/v1/feedback', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

