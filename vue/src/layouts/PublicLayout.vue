<template>
  <div class="public-shell" :class="{ 'public-shell--full': hideAside }">
    <aside v-if="!hideAside" class="public-aside" aria-label="站点侧栏">
      <ProfileCard :profile="profile" />
      <BlogInfoCard />
    </aside>
    <main class="public-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ProfileCard from '../components/ProfileCard.vue'
import BlogInfoCard from '../components/BlogInfoCard.vue'
import { fetchPublicProfile } from '../api/profile'

const route = useRoute()
const profile = ref(null)

const hideAside = computed(() => {
  const p = route.path
  return p === '/login' || p.startsWith('/article/')
})

onMounted(async () => {
  try {
    profile.value = await fetchPublicProfile()
  } catch {
    profile.value = null
  }
})
</script>
