import { request } from './http'

export function fetchFolderTree() {
  return request('/api/v1/media-folders/tree')
}

export function fetchFolderList() {
  return request('/api/v1/media-folders')
}

export function createFolder(payload) {
  return request('/api/v1/media-folders', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateFolder(id, payload) {
  return request(`/api/v1/media-folders/${id}`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function deleteFolder(id) {
  return request(`/api/v1/media-folders/${id}`, {
    method: 'DELETE',
    withAuth: true
  })
}
