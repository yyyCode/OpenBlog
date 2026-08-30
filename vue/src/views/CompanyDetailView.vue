<template>
  <div class="blog-container company-detail-page">
    <router-link to="/jobs/companies" class="detail-back">← 返回推荐</router-link>

    <div v-if="loading" class="card">
      <div class="card-body" style="padding: 22px; color: var(--muted)">加载中…</div>
    </div>

    <div v-else-if="!company" class="card">
      <div class="card-body" style="padding: 22px; color: var(--muted)">未找到该公司</div>
    </div>

    <template v-else>
      <!-- 头部：头像 + 名称 + 类型/规模/城市标签 -->
      <div class="detail-head">
        <div class="detail-avatar" :style="{ backgroundColor: color }">
          <img v-if="logo" :src="logo" :alt="company.name" class="detail-avatar-img" />
          <span v-else class="detail-avatar-text">{{ initial }}</span>
        </div>
        <div class="detail-head-body">
          <div class="detail-name">{{ company.name }}</div>
          <div class="detail-tags">
            <span v-if="company.type" class="detail-tag">{{ company.type }}</span>
            <span v-if="scaleText" class="detail-tag">{{ scaleText }}人</span>
            <span v-if="company.city" class="detail-tag">{{ company.city }}</span>
          </div>
        </div>
      </div>

      <div class="detail-cards">
        <!-- 基本信息 -->
        <div class="card detail-card">
          <h3 class="detail-card-title">基本信息</h3>
          <dl class="detail-rows">
            <div class="detail-row">
              <dt>公司类型</dt>
              <dd>{{ company.type || '—' }}</dd>
            </div>
            <div class="detail-row">
              <dt>公司规模</dt>
              <dd>{{ scaleText ? scaleText + ' 人' : '—' }}</dd>
            </div>
            <div class="detail-row">
              <dt>所在城市</dt>
              <dd>{{ company.city || '—' }}</dd>
            </div>
            <div class="detail-row">
              <dt>成立年份</dt>
              <dd>{{ company.founded || '—' }}</dd>
            </div>
            <div class="detail-row">
              <dt>办公地址</dt>
              <dd>{{ company.address || '—' }}</dd>
            </div>
          </dl>
        </div>

        <!-- 主营业务 -->
        <div class="card detail-card">
          <h3 class="detail-card-title">主营业务</h3>
          <p class="detail-biz">{{ company.business || '—' }}</p>
          <p v-if="company.description" class="detail-desc">{{ company.description }}</p>
        </div>
      </div>

      <div v-if="company.website" class="detail-footer">
        <a
          :href="'https://' + company.website"
          target="_blank"
          rel="noopener noreferrer"
          class="detail-site"
        >🌐 官网：{{ company.website }}</a>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { fetchSmallCompanyDetail, formatScale, logoUrl } from '../api/smallCompany'

const route = useRoute()
const company = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    company.value = await fetchSmallCompanyDetail(route.params.id)
  } catch {
    company.value = null
  } finally {
    loading.value = false
  }
})

const initial = computed(() => {
  const name = (company.value && company.value.name) || '?'
  return name.trim().charAt(0).toUpperCase()
})

const logo = computed(() => logoUrl(company.value?.logoMediaKey))

const color = computed(() => company.value?.color || '#4f7cff')

const scaleText = computed(() => formatScale(company.value))
</script>

<style scoped>
.company-detail-page {
  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Helvetica Neue', sans-serif;
  max-width: 720px;
}

.detail-back {
  display: inline-block;
  margin-bottom: 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--accent);
  text-decoration: none;
}
.detail-back:hover {
  text-decoration: underline;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 28px;
}
.detail-avatar {
  width: 84px;
  height: 84px;
  border-radius: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.14);
  flex: none;
  overflow: hidden;
}
.detail-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 6px;
  background: #fff;
}
.detail-avatar-text {
  font-size: 38px;
  font-weight: 700;
  color: #fff;
  user-select: none;
}
.detail-head-body {
  min-width: 0;
}
.detail-name {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text);
  line-height: 1.2;
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.detail-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  background: rgba(51, 112, 255, 0.08);
  border: 1px solid rgba(51, 112, 255, 0.2);
}
[data-theme='dark'] .detail-tag {
  background: rgba(74, 127, 255, 0.14);
  border-color: rgba(74, 127, 255, 0.35);
}

.detail-cards {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
}
.detail-card {
  padding: 22px 24px;
  border-radius: 14px;
}
.detail-card-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text);
}
.detail-rows {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
}
.detail-row dt {
  flex: none;
  width: 72px;
  font-size: 13px;
  color: var(--muted);
}
.detail-row dd {
  margin: 0;
  font-size: 14px;
  color: var(--text);
  line-height: 1.6;
}

.detail-biz {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  line-height: 1.6;
}
.detail-desc {
  margin: 12px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--muted);
}

.detail-footer {
  margin-top: 24px;
  text-align: center;
}
.detail-site {
  display: inline-block;
  padding: 10px 20px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  color: var(--accent);
  text-decoration: none;
  border: 1px solid var(--accent);
  transition: background 0.18s ease, color 0.18s ease;
}
.detail-site:hover {
  background: var(--accent);
  color: #fff;
}
</style>
