import { request } from './http'

export function fetchSliderChallenge() {
  return request('/api/v1/auth/slider-challenge', { method: 'GET' })
}

export function completeSliderChallenge(challengeId) {
  return request('/api/v1/auth/slider-complete', {
    method: 'POST',
    body: JSON.stringify({ challengeId })
  })
}

export function login(account, password, sliderChallengeId) {
  const body = { account, password }
  if (sliderChallengeId != null && sliderChallengeId !== '') body.sliderChallengeId = sliderChallengeId
  return request('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(body)
  })
}

export function fetchMe() {
  return request('/api/v1/users/me', { method: 'GET', withAuth: true })
}

export function updateMe(payload) {
  return request('/api/v1/users/me', {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function fetchMyArticles(page = 0, size = 50) {
  return request(`/api/v1/users/me/articles?page=${page}&size=${size}`, { method: 'GET', withAuth: true })
}

export function fetchMyArticleDetail(id) {
  return request(`/api/v1/users/me/articles/${id}`, { method: 'GET', withAuth: true })
}

export function createDraft(payload) {
  return request('/api/v1/articles', {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function updateArticle(id, payload) {
  return request(`/api/v1/articles/${id}`, {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}

export function publishArticle(id) {
  return request(`/api/v1/articles/${id}/publish`, { method: 'POST', withAuth: true })
}

export function deleteMyArticle(id) {
  return request(`/api/v1/articles/${id}`, { method: 'DELETE', withAuth: true })
}

