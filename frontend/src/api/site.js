import { request } from './http'

export function fetchSiteVersion() {
  return request('/api/v1/site/version')
}
