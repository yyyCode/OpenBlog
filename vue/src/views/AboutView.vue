<template>
  <div class="blog-container">
    <main>
      <div class="card">
        <div class="card-body" style="padding: 22px">
          <h1 style="margin: 0; font-size: 26px; font-weight: 900; letter-spacing: -0.02em">关于</h1>
          <p style="margin: 12px 0 0; color: var(--muted); line-height: 1.8">
            {{ aboutText }}
          </p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import { SITE_CONFIG_KEY } from '../App.vue'

const siteConfig = inject(SITE_CONFIG_KEY)

const aboutText = computed(() =>
  (siteConfig && siteConfig.value && siteConfig.value.about_text) || '这里是个人博客，用来记录设计、技术与思考。'
)
</script>

