<template>
  <div class="blog-info-wrap admin-entry-wrap">
    <div class="blog-info-heading">AI平台</div>
    <div class="card blog-info-card">
      <div class="blog-info-card-body admin-entry-card-body">
        <button
          class="profile-menu-item admin-entry-btn"
          type="button"
          @click="onAiPlatformClick"
        >
          <svg class="admin-entry-icon" viewBox="0 0 24 24" aria-hidden="true">
            <!-- auto_awesome / 星芒，表示 AI -->
            <path
              fill="currentColor"
              d="M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z"
            />
          </svg>
          <span>AI平台</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { showMessage } from '../utils/message'

function onAiPlatformClick() {
  showMessage('功能暂未开放')
}
</script>
