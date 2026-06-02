import { request } from './http'

export function fetchSiteVersion() {
  return request('/api/v1/site/version')
}

export function fetchSiteStats() {
  return request('/api/v1/site/stats')
}

/** 记录一次全站访问（按 IP 去重），应用入口调用一次即可 */
export function postSiteVisit() {
  return request('/api/v1/site/visit', { method: 'POST' })
}

export function fetchSiteConfig() {
  return request('/api/v1/site/config')
}

/** payload: { github_url: '...', blog_name: '...', ... } */
export function updateSiteConfig(payload) {
  return request('/api/v1/site/config', {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}
