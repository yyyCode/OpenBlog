import { getStoredAccessToken } from '../auth/session'
import { API_BASE } from './http'

function getAuthHeader() {
  const token = getStoredAccessToken()
  if (!token) return {}
  return { Authorization: `Bearer ${token}` }
}

export async function uploadMedia(file, folderId) {
  const fd = new FormData()
  fd.append('file', file)
  if (folderId != null) fd.append('folderId', folderId)

  const res = await fetch(`${API_BASE}/api/v1/media/upload`, {
    method: 'POST',
    headers: {
      ...getAuthHeader()
    },
    body: fd
  })

  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
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

export async function fetchMediaList(page = 0, size = 20, folderId) {
  let url = `${API_BASE}/api/v1/media?page=${page}&size=${size}`
  if (folderId != null) url += `&folderId=${folderId}`
  const res = await fetch(url, {
    method: 'GET',
    headers: {
      ...getAuthHeader()
    }
  })

  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
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

export async function deleteMedia(key) {
  const res = await fetch(`${API_BASE}/api/v1/media/${encodeURIComponent(key)}`, {
    method: 'DELETE',
    headers: {
      ...getAuthHeader()
    }
  })

  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
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

  return true
}

export async function fetchMediaCategories() {
  const res = await fetch(`${API_BASE}/api/v1/media/categories`, {
    method: 'GET',
    headers: {
      ...getAuthHeader()
    }
  })

  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    json = null
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

