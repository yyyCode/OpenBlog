import { downloadWithAuth, request } from './http'

const BASE = '/api/v1/admin/sensitive-words'

function query(params) {
  const search = new URLSearchParams()
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    search.set(key, value)
  })
  const s = search.toString()
  return s ? `?${s}` : ''
}

export function fetchSensitiveWords(params = {}) {
  return request(`${BASE}${query(params)}`, { method: 'GET', withAuth: true })
}

export function fetchSensitiveWordCategories() {
  return request(`${BASE}/categories`, { method: 'GET', withAuth: true })
}

export function createSensitiveWord(payload) {
  return request(BASE, { method: 'POST', withAuth: true, body: JSON.stringify(payload) })
}

export function updateSensitiveWord(id, payload) {
  return request(`${BASE}/${id}`, { method: 'PUT', withAuth: true, body: JSON.stringify(payload) })
}

export function deleteSensitiveWord(id) {
  return request(`${BASE}/${id}`, { method: 'DELETE', withAuth: true })
}

export function batchDeleteSensitiveWords(ids) {
  return request(`${BASE}/batch`, {
    method: 'DELETE',
    withAuth: true,
    body: JSON.stringify({ ids })
  })
}

export function previewSensitiveWordImport(file) {
  const form = new FormData()
  form.append('file', file)
  return request(`${BASE}/import/preview`, { method: 'POST', withAuth: true, body: form })
}

export function importSensitiveWords(file, mode = 'APPEND', confirmReplace = false) {
  const form = new FormData()
  form.append('file', file)
  return request(`${BASE}/import${query({ mode, confirmReplace })}`, {
    method: 'POST',
    withAuth: true,
    body: form
  })
}

export function exportSensitiveWords() {
  return downloadWithAuth(`${BASE}/export`)
}

export function fetchSensitiveWordPending() {
  return request(`${BASE}/pending`, { method: 'GET', withAuth: true })
}

export function publishSensitiveWords(remark = '', allowEmpty = false) {
  return request(`${BASE}/publish`, {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify({ remark, allowEmpty })
  })
}

export function fetchSensitiveWordVersions(page = 0, size = 20) {
  return request(`${BASE}/versions${query({ page, size })}`, { method: 'GET', withAuth: true })
}

export function fetchSensitiveWordVersionDiff(version) {
  return request(`${BASE}/versions/${version}/diff`, { method: 'GET', withAuth: true })
}

export function rollbackSensitiveWordVersion(version, remark = '') {
  return request(`${BASE}/versions/${version}/rollback`, {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify({ remark })
  })
}

export function syncSensitiveWordVersion(version) {
  return request(`${BASE}/versions/${version}/sync`, { method: 'POST', withAuth: true })
}

export function fetchSensitiveWordStatus() {
  return request(`${BASE}/status`, { method: 'GET', withAuth: true })
}

export function checkSensitiveText(text) {
  return request(`${BASE}/check`, {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify({ text })
  })
}
