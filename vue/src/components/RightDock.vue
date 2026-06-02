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

    <div class="right-dock-sep" aria-hidden="true" />

    <div class="right-dock-menu" @click.stop>
      <button
        type="button"
        class="right-dock-btn"
        :aria-expanded="aiOpen ? 'true' : 'false'"
        aria-haspopup="menu"
        @click="toggleAi"
      >
        <span class="right-dock-ico" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10 2v4" />
            <path d="M14 2v4" />
            <path d="M8 4h8" />
            <path d="M7 22h10" />
            <path d="M9 22v-4h6v4" />
            <path d="M8 18a4 4 0 0 1 8 0" />
            <path d="M12 8v6" />
            <path d="M10 10h4" />
          </svg>
        </span>
        <span class="right-dock-text">AI实验室</span>
        <span class="right-dock-caret" aria-hidden="true">◂</span>
      </button>

      <div v-if="aiOpen" class="right-dock-dropdown" role="menu" aria-label="AI实验室菜单">
        <a
          class="right-dock-item"
          role="menuitem"
          :href="(siteConfig && siteConfig.ai_platform_url) || 'http://ai.wecode.xin/#/chat/default'"
          target="_blank"
          rel="noopener noreferrer"
          @click="closeAi"
        >
          ai工作平台
        </a>
      </div>
    </div>

    <div class="right-dock-menu" @click.stop>
      <button
        type="button"
        class="right-dock-btn"
        :aria-expanded="projectOpen ? 'true' : 'false'"
        aria-haspopup="menu"
        @click="toggleProject"
      >
        <span class="right-dock-ico" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M4 4h6v6H4z" />
            <path d="M14 4h6v6h-6z" />
            <path d="M4 14h6v6H4z" />
            <path d="M14 14h6v6h-6z" />
          </svg>
        </span>
        <span class="right-dock-text">个人项目</span>
        <span class="right-dock-caret" aria-hidden="true">◂</span>
      </button>

      <div v-if="projectOpen" class="right-dock-dropdown" role="menu" aria-label="个人项目菜单">
        <button class="right-dock-item right-dock-item--disabled" type="button" role="menuitem" disabled>敬请期待</button>
      </div>
    </div>

  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const emit = defineEmits(['toggle-profile'])
const siteConfig = inject('siteConfig')
const expanded = ref(false)
const aiOpen = ref(false)
const projectOpen = ref(false)

function toggleAi() {
  expanded.value = true
  projectOpen.value = false
  aiOpen.value = !aiOpen.value
}

function closeAi() {
  aiOpen.value = false
}

function toggleProject() {
  expanded.value = true
  aiOpen.value = false
  projectOpen.value = !projectOpen.value
}

function closeProject() {
  projectOpen.value = false
}

function onLeave() {
  aiOpen.value = false
  projectOpen.value = false
  expanded.value = false
}

function onDocPointerDown() {
  closeAi()
  closeProject()
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocPointerDown)
})
</script>

