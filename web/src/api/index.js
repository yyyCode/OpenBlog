import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8081/api',
  timeout: 5000,
});

export const getPosts = () => {
  return api.get('/posts');
};

export const getPost = (id) => {
  return api.get(`/posts/${id}`);
};

export const createPost = (data, apiKey) => {
  return api.post('/posts', data, {
    headers: {
      'X-API-Key': apiKey,
    },
  });
};

export default api;
