import { API_BASE, request } from './http'

export function fetchSmallCompanies(page = 0, size = 20) {
  return request(`/api/v1/small-companies?page=${page}&size=${size}`)
}

export function fetchSmallCompanyDetail(id) {
  return request(`/api/v1/small-companies/${id}`)
}

/** 控制台管理列表：含草稿，仅 ADMIN */
export function fetchAdminSmallCompanies(page = 0, size = 50) {
  return request(`/api/v1/small-companies/admin?page=${page}&size=${size}`, { withAuth: true })
}

/** 控制台取单条（含草稿），仅 ADMIN */
export function fetchAdminSmallCompanyDetail(id) {
  return request(`/api/v1/small-companies/${id}`, { withAuth: true })
}

export function createSmallCompany(payload) {
  return request('/api/v1/small-companies', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateSmallCompany(id, payload) {
  return request(`/api/v1/small-companies/${id}`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function deleteSmallCompany(id) {
  return request(`/api/v1/small-companies/${id}`, {
    method: 'DELETE',
    withAuth: true
  })
}

export function logoUrl(key) {
  if (!key) return ''
  return `${API_BASE}/api/v1/media/files/${key}`
}

/** 员工规模格式化：100-499；仅下限则 100+；均无则空串 */
export function formatScale(c) {
  if (!c) return ''
  const min = c.scaleMin ?? c.scale_min
  const max = c.scaleMax ?? c.scale_max
  if (min == null && max == null) return ''
  if (max == null) return `${min}+`
  if (min == null) return `${max}以内`
  return `${min}-${max}`
}
