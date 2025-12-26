<template>
  <div class="home">
    <div class="hero">
      <h1>欢迎来到我的博客</h1>
      <p>分享技术，记录生活</p>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    
    <div v-else class="post-list">
      <div v-if="posts.length === 0" class="empty">暂无文章</div>
      <article v-for="post in posts" :key="post.id" class="post-card">
        <router-link :to="'/post/' + post.id" class="post-title-link">
          <h2>{{ post.title }}</h2>
        </router-link>
        <div class="post-meta">
          <span>{{ formatDate(post.created_at) }}</span>
        </div>
        <p class="post-summary">{{ post.summary }}</p>
        <router-link :to="'/post/' + post.id" class="read-more">阅读全文 →</router-link>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { getPosts } from '../api';

const posts = ref([]);
const loading = ref(true);
const error = ref(null);

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  });
};

onMounted(async () => {
  try {
    const response = await getPosts();
    posts.value = response.data;
  } catch (err) {
    console.error(err);
    error.value = '获取文章列表失败，请检查后端服务是否启动';
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.hero {
  text-align: center;
  padding: 3rem 0;
  margin-bottom: 2rem;
  background-color: #eef9f5;
  border-radius: 12px;
}

.hero h1 {
  margin-bottom: 0.5rem;
  color: #42b883;
}

.post-card {
  background: white;
  border: 1px solid #eee;
  border-radius: 12px;
  padding: 2rem;
  margin-bottom: 2rem;
  transition: transform 0.2s, box-shadow 0.2s;
}

.post-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.post-title-link {
  text-decoration: none;
  color: inherit;
}

.post-title-link h2 {
  margin-bottom: 0.5rem;
  font-size: 1.5rem;
}

.post-title-link:hover h2 {
  color: #42b883;
}

.post-meta {
  color: #999;
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

.post-summary {
  color: #555;
  line-height: 1.6;
  margin-bottom: 1.5rem;
}

.read-more {
  font-weight: 500;
  font-size: 0.9rem;
}

.loading, .error, .empty {
  text-align: center;
  padding: 2rem;
  color: #666;
}

.error {
  color: #e53e3e;
}
</style>
