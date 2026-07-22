<template>
  <div class="right-dock" :class="{ open: expanded }" @mouseenter="expanded = true" @mouseleave="onLeave">
    <button type="button" class="right-dock-btn" @click="emit('toggle-profile')" :aria-label="'打开个人信息'">
      <span class="right-dock-ico" aria-hidden="true">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>
      </span>
      <span class="right-dock-text">个人信息</span>
    </button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const emit = defineEmits(['toggle-profile'])
const expanded = ref(false)

function onLeave() {
  expanded.value = false
}
</script>
