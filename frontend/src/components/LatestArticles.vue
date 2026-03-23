<template>
  <div class="card">
    <div class="card-body">
      <div style="font-weight: 900; margin-bottom: 12px">最新文章</div>
      <div class="mini-list">
        <div
          v-for="a in items"
          :key="a.id"
          class="mini-item"
        >
          <div class="mini-cover">
            <img v-if="a.coverMediaKey" :src="coverUrl(a.coverMediaKey)" alt="thumb" />
          </div>
          <div>
            <div class="mini-title" @click="go(a.id)">{{ a.title }}</div>
            <div style="color: var(--muted); font-size: 12px; margin-top: 4px">
              {{ formatDate(a.publishedAt) }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { coverUrl } from '../api/article'

const props = defineProps({
  items: {
    type: Array,
    required: true
  }
})

const router = useRouter()

function go(id) {
  router.push(`/article/${id}`)
}

function formatDate(v) {
  if (!v) return ''
  try {
    return new Date(v).toISOString().slice(0, 10)
  } catch {
    return ''
  }
}
</script>

