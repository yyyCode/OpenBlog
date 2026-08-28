<template>
  <div class="blog-container jobs-page">
    <div class="page-header">
      <h1 class="page-title">{{ title }}</h1>
      <p class="page-subtitle">求职导航 · {{ title }}</p>
    </div>

    <!-- 空子页面占位：内容后续填充（原展示/跳转代码保留在 JobNavView，未删除） -->
    <div class="card">
      <div class="card-body empty-state">敬请期待，内容筹备中…</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' }
})
</script>

<style scoped>
.jobs-page {
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Helvetica Neue', sans-serif;
}

.page-header {
  margin-bottom: 32px;
}

.page-title {
  margin: 0;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.15;
}

.page-subtitle {
  color: var(--muted);
  font-size: 18px;
  font-weight: 450;
  margin-top: 10px;
}

.empty-state {
  padding: 48px 22px;
  text-align: center;
  color: var(--muted);
}
</style>
