import { getStoredAccessToken } from '../auth/session'
import { API_BASE, refreshSessionTokens } from './http'

function getAuthHeader() {
  const token = getStoredAccessToken()
  if (!token) return {}
  return { Authorization: `Bearer ${token}` }
}

export async function uploadMedia(file) {
  let { res, json } = await doUploadWithFile(file)

  if (res.status === 401) {
    try {
      await refreshSessionTokens()
      ;({ res, json } = await doUploadWithFile(file))
    } catch {
      const err = new Error('登录已失效')
      err.httpStatus = 401
      throw err
    }
  }

  if (!res.ok) {
    const msg = json?.message || `HTTP ${res.status}`
    const err = new Error(msg)
    err.httpStatus = res.status
    throw err
  }

  if (json && typeof json.code === 'number' && json.code !== 0) {
    throw new Error(json.message || '请求失败')
  }

  return json?.data ?? json
}

function doUploadWithFile(file) {
  const fd = new FormData()
  fd.append('file', file)

  return fetch(`${API_BASE}/api/v1/media/upload`, {
    method: 'POST',
    headers: {
      ...getAuthHeader()
    },
    body: fd
  }).then(async (res) => {
    const text = await res.text()
    let json = null
    try {
      json = text ? JSON.parse(text) : null
    } catch {
      json = null
    }
    return { res, json }
  })
}

