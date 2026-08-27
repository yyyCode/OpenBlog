<template>
  <span class="user-avatar" :style="{ width: size + 'px', height: size + 'px' }">
    <img v-if="url" class="user-avatar-img" :src="url" alt="avatar" />
    <svg v-else class="user-avatar-fallback" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
    </svg>
  </span>
</template>

<script setup>
defineProps({
  url: { type: String, default: '' },
  size: { type: Number, default: 32 }
})
</script>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--surface);
  border: 1px solid var(--border);
}
.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.user-avatar-fallback {
  width: 62%;
  height: 62%;
  color: var(--muted);
}
</style>
