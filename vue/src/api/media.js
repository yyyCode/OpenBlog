import { API_BASE, request } from './http'

export async function uploadMedia(file, folderIdOrOptions) {
  const fd = new FormData()
  fd.append('file', file)
  let folderId = null
  let category = null
  if (folderIdOrOptions != null) {
    if (typeof folderIdOrOptions === 'object') {
      folderId = folderIdOrOptions.folderId ?? null
      category = folderIdOrOptions.category ?? null
    } else {
      folderId = folderIdOrOptions
    }
  }
  if (folderId != null) fd.append('folderId', folderId)
  if (category != null) fd.append('category', category)
  return request('/api/v1/media/upload', {
    method: 'POST',
    body: fd,
    withAuth: true
  })
}

export function fetchMediaList(page = 0, size = 20, folderId) {
  let url = `/api/v1/media?page=${page}&size=${size}`
  if (folderId != null) url += `&folderId=${folderId}`
  return request(url, { method: 'GET', withAuth: true })
}

export function deleteMedia(key) {
  return request(`/api/v1/media/${encodeURIComponent(key)}`, {
    method: 'DELETE',
    withAuth: true
  }).then(() => true)
}

export function fetchMediaCategories() {
  return request('/api/v1/media/categories', { method: 'GET', withAuth: true })
}

export { API_BASE }
