<template>
  <div class="container" style="max-width: 400px; margin-top: 100px;">
    <div class="card">
      <h2 style="text-align: center;">后台登录</h2>
      <p v-if="error" class="error">{{ error }}</p>
      <form @submit.prevent="handleLogin">
        <div>
          <label>用户名</label>
          <input v-model="username" type="text" required placeholder="请输入用户名" />
        </div>
        <div>
          <label>密码</label>
          <input v-model="password" type="password" required placeholder="请输入密码" />
        </div>
        <button type="submit" style="width: 100%;">登录</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import api from '../api';

const username = ref('');
const password = ref('');
const error = ref('');
const router = useRouter();

const handleLogin = async () => {
  try {
    const response = await api.post('/login', {
      username: username.value,
      password: password.value,
    });
    localStorage.setItem('token', response.data.token);
    router.push('/');
  } catch (err) {
    error.value = err.response?.data?.error || '登录失败';
  }
};
</script>
