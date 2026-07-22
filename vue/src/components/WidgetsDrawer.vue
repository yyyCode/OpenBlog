<template>
  <div v-if="open" class="widgets-overlay" @click="emit('close')" />
  <aside class="widgets-drawer" :class="{ open }" aria-label="挂件抽屉">
    <div class="widgets-drawer-head">
      <div class="widgets-drawer-title">个人信息</div>
      <button type="button" class="widgets-drawer-close" @click="emit('close')" aria-label="关闭">×</button>
    </div>
    <div class="widgets-drawer-body">
      <ProfileCard :profile="profile" />

      <button type="button" class="widgets-theme-btn" @click="onToggle">
        <span aria-hidden="true">{{ isDark ? '☀' : '☽' }}</span>
        {{ isDark ? '浅色模式' : '深色模式' }}
      </button>

      <BlogInfoCard />
    </div>
  </aside>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { toggleTheme as applyToggle } from '../theme'
import ProfileCard from './ProfileCard.vue'
import BlogInfoCard from './BlogInfoCard.vue'

defineProps({
  open: { type: Boolean, default: false },
  profile: { type: Object, default: null }
})

const emit = defineEmits(['close'])
const isDark = ref(false)

function sync() {
  isDark.value = document.documentElement.getAttribute('data-theme') === 'dark'
}

function onToggle() {
  applyToggle()
  sync()
}

onMounted(() => {
  sync()
})
</script>
