<template>
  <Transition name="back-to-top-fade">
    <div v-show="visible" class="back-to-top-wrap">
      <button
        type="button"
        class="back-to-top-btn"
        aria-label="返回顶部"
        title="返回顶部"
        @click="scrollTop"
      >
        <svg
          class="back-to-top-icon"
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          width="22"
          height="22"
          fill="none"
          stroke="currentColor"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <polyline points="6 15 12 9 18 15" />
        </svg>
      </button>
    </div>
  </Transition>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const visible = ref(false)
const THRESHOLD = 240

function updateVisible() {
  visible.value = window.scrollY > THRESHOLD
}

function scrollTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  updateVisible()
  window.addEventListener('scroll', updateVisible, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', updateVisible)
})
</script>

<style scoped>
.back-to-top-fade-enter-active,
.back-to-top-fade-leave-active {
  transition: opacity 0.2s ease;
}

.back-to-top-fade-enter-from,
.back-to-top-fade-leave-to {
  opacity: 0;
}
</style>
