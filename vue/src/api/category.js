import { request } from './http'

export function fetchCategoryTree() {
  return request('/api/v1/categories/tree')
}

export function fetchCategories() {
  return request('/api/v1/categories')
}

export function createCategory(payload) {
  return request('/api/v1/categories', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateCategory(id, payload) {
  return request(`/api/v1/categories/${id}`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function deleteCategory(id) {
  return request(`/api/v1/categories/${id}`, {
    method: 'DELETE',
    withAuth: true
  })
}
