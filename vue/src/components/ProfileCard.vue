<template>
  <div class="card profile-card">
    <div class="profile-card-body">
      <div class="profile-avatar-row">
        <div v-if="siteVersionLabel" class="profile-site-version" :title="'站点版本 ' + siteVersionLabel">
          {{ siteVersionLabel }}
        </div>
        <div v-else class="profile-site-version profile-site-version--empty" aria-hidden="true" />

        <div class="profile-avatar-wrap">
          <img
            class="profile-avatar"
            :src="profile?.avatarUrl || defaultAvatar"
            alt="avatar"
          />
        </div>

        <div class="profile-avatar-spacer" aria-hidden="true" />
      </div>

      <div class="profile-name">{{ profile?.username || '—' }}</div>
      <div class="profile-signature">{{ profile?.bio || '平凡的一枚程序员' }}</div>

      <div class="profile-menu">
        <router-link class="profile-menu-item" to="/all">
          全部文章
        </router-link>
        <router-link class="profile-menu-item" to="/changelog">
          更新日志
        </router-link>
      </div>

      <div class="links-row">
        <a class="icon-link" :href="githubUrl" target="_blank" rel="noreferrer">
          GitHub
        </a>
        <a class="icon-link" :href="csdnUrl" target="_blank" rel="noreferrer">
          CSDN
        </a>
        <a class="icon-link" :href="nowcoderUrl" target="_blank" rel="noreferrer">
          牛客
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchSiteVersion } from '../api/site'

defineProps({
  profile: {
    type: Object,
    required: false
  }
})

const siteVersion = ref('')

const defaultAvatar = 'https://via.placeholder.com/120x120.png?text=OpenBlog'
const githubUrl = 'https://github.com/yyyCode'
const csdnUrl = 'https://blog.csdn.net/2301_80044822'
const nowcoderUrl = 'https://www.nowcoder.com/users/597303882'

const siteVersionLabel = computed(() => {
  const v = (siteVersion.value || '').trim()
  if (!v) return ''
  return v.startsWith('v') ? v : `v${v}`
})

onMounted(async () => {
  try {
    const data = await fetchSiteVersion()
    if (data?.version) siteVersion.value = String(data.version)
  } catch {
    siteVersion.value = ''
  }
})
</script>
