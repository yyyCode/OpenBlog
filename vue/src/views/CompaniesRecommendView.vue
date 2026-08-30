<template>
  <div class="blog-container companies-page">
    <div class="page-header">
      <h1 class="page-title">小而美公司</h1>
      <p class="page-subtitle">大厂竞争激烈，不如看看这些优质小厂 —— 小而美，也能做出好产品</p>
      <p class="page-note">数据主要来源于 BOSS 直聘，仅供参考</p>
    </div>

    <div v-if="loading" class="companies-empty">加载中…</div>
    <div v-else-if="companies.length === 0" class="companies-empty">暂无公司数据，敬请期待</div>

    <div v-else class="companies-grid">
      <CompanyCard
        v-for="c in companies"
        :key="c.id"
        :company="c"
        @select="onSelect"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import CompanyCard from '../components/CompanyCard.vue'
import { fetchSmallCompanies } from '../api/smallCompany'

const router = useRouter()
const companies = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await fetchSmallCompanies()
    companies.value = res?.items || []
  } catch (e) {
    companies.value = []
  } finally {
    loading.value = false
  }
})

function onSelect(c) {
  router.push(`/jobs/companies/${c.id}`)
}
</script>

<style scoped>
.companies-page {
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
.page-note {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--muted);
  opacity: 0.75;
}

.companies-empty {
  padding: 60px 0;
  text-align: center;
  color: var(--muted);
}

/* 九宫格：桌面 3 列，窄屏 2 列，移动端 1 列 */
.companies-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  max-width: 960px;
  margin: 0 auto;
}
@media (max-width: 900px) {
  .companies-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }
}
@media (max-width: 560px) {
  .companies-grid {
    grid-template-columns: 1fr;
    gap: 14px;
  }
}
</style>
