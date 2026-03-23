const baseUrl = import.meta.env.VITE_API_BASE || 'http://8.138.32.217:8082'

function buildUrl(path) {
  // 后端统一是 /api/v1 前缀
  if (path.startsWith('http://') || path.startsWith('https://')) return path
  return `${baseUrl}${path}`
}

export async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }

  if (options.withAuth) {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      const err = new Error('未登录')
      err.httpStatus = 401
      throw err
    }
    headers.Authorization = `Bearer ${token}`
  }

  const res = await fetch(buildUrl(path), {
    headers,
    ...options
  })

  const text = await res.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    // ignore
  }

  // API 统一返回 ApiResponse，HTTP 层可能仍为 200
  if (json && typeof json.code === 'number' && json.code !== 0) {
    const msg = json.message || '请求失败'
    const err = new Error(msg)
    err.code = json.code
    err.httpStatus = res.status
    throw err
  }

  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`)
    err.httpStatus = res.status
    throw err
  }

  return json?.data ?? json
}

