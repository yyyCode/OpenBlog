<template>
  <div class="container">
    <div class="header">
      <h1>文章管理</h1>
      <button @click="logout" class="logout-btn">退出登录</button>
    </div>

    <div class="card">
      <h2>发布新文章</h2>
      <p v-if="message" :style="{ color: isError ? 'red' : 'green' }">{{ message }}</p>
      <form @submit.prevent="createPost">
        <div>
          <label>标题</label>
          <input v-model="title" type="text" required placeholder="文章标题" />
        </div>
        <div>
          <label>内容 (Markdown)</label>
          <textarea v-model="content" rows="10" required placeholder="支持 Markdown 格式..."></textarea>
        </div>
        <button type="submit">发布文章</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import api from '../api';

const title = ref('');
const content = ref('');
const message = ref('');
const isError = ref(false);
const router = useRouter();

const createPost = async () => {
  try {
    await api.post('/posts', {
      title: title.value,
      content: content.value,
    });
    message.value = '发布成功！';
    isError.value = false;
    title.value = '';
    content.value = '';
  } catch (err) {
    message.value = err.response?.data?.error || '发布失败';
    isError.value = true;
    if (err.response?.status === 401) {
      router.push('/login');
    }
  }
};

const logout = () => {
  localStorage.removeItem('token');
  router.push('/login');
};
</script>
