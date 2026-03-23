const baseUrl = import.meta.env.VITE_API_BASE || 'http://8.138.32.217:8082'

function getAuthHeader() {
  const token = localStorage.getItem('accessToken')
  if (!token) return {}
  return { Authorization: `Bearer ${token}` }
}

export async function uploadMedia(file) {
  const fd = new FormData()
  fd.append('file', file)

  const res = await fetch(`${baseUrl}/api/v1/media/upload`, {
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

