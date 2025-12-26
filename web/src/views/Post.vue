<template>
  <div class="post-detail">
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error">{{ error }}</div>
    
    <article v-else-if="post" class="post-content">
      <header class="post-header">
        <h1>{{ post.title }}</h1>
        <div class="post-meta">
          <span>发布于 {{ formatDate(post.created_at) }}</span>
        </div>
      </header>
      
      <div class="markdown-body" v-html="renderedContent"></div>
      
      <div class="post-footer">
        <router-link to="/" class="back-link">← 返回列表</router-link>
      </div>
    </article>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { getPost } from '../api';
import { marked } from 'marked';

const route = useRoute();
const post = ref(null);
const loading = ref(true);
const error = ref(null);

const renderedContent = computed(() => {
  if (!post.value || !post.value.content) return '';
  return marked(post.value.content);
});

const formatDate = (dateString) => {
  return new Date(dateString).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
};

onMounted(async () => {
  try {
    const id = route.params.id;
    const response = await getPost(id);
    post.value = response.data;
  } catch (err) {
    console.error(err);
    error.value = '获取文章详情失败';
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.post-detail {
  background: white;
  padding: 2rem;
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.post-header {
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #eee;
}

.post-header h1 {
  font-size: 2rem;
  margin-bottom: 0.5rem;
  color: #333;
}

.post-meta {
  color: #999;
  font-size: 0.9rem;
}

.markdown-body {
  line-height: 1.8;
  color: #333;
}

/* Add some basic markdown styles since we are not importing a css file for it */
:deep(.markdown-body h1), :deep(.markdown-body h2), :deep(.markdown-body h3) {
  margin-top: 1.5em;
  margin-bottom: 0.5em;
}

:deep(.markdown-body p) {
  margin-bottom: 1em;
}

:deep(.markdown-body code) {
  background-color: #f3f4f6;
  padding: 0.2em 0.4em;
  border-radius: 4px;
  font-family: monospace;
}

:deep(.markdown-body pre) {
  background-color: #f3f4f6;
  padding: 1rem;
  border-radius: 8px;
  overflow-x: auto;
  margin-bottom: 1em;
}

:deep(.markdown-body blockquote) {
  border-left: 4px solid #42b883;
  padding-left: 1rem;
  color: #666;
  margin: 1em 0;
}

.post-footer {
  margin-top: 3rem;
  padding-top: 1rem;
  border-top: 1px solid #eee;
}

.back-link {
  display: inline-block;
  color: #666;
}

.back-link:hover {
  color: #42b883;
}
</style>
