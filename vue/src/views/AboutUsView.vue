<template>
  <div class="page-wrap">
    <div class="card about-us-card">
      <div class="profile-avatar-wrap">
        <img class="profile-avatar" :src="avatarUrl" alt="avatar" />
      </div>

      <div class="profile-name">{{ name }}</div>
      <div class="profile-signature">{{ signature }}</div>

      <div class="profile-menu">
        <router-link class="profile-menu-item" to="/all">
          全部文章
        </router-link>
        <router-link class="profile-menu-item" to="/changelog">
          更新日志
        </router-link>
      </div>

      <div class="about-us-intro">
        <p class="about-us-intro-text">
          你好，我是 yyycode，这个站点的作者。这里记录我在开发与学习中的经历、踩坑和思考，欢迎通过下面的方式联系我交流。
        </p>
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
import { computed, inject, onMounted, ref } from 'vue'
import { fetchOwnerProfile } from '../api/profile'

const siteConfig = inject('siteConfig')

const owner = ref(null)

const avatarUrl = computed(() => {
  const a = owner.value?.avatarUrl
  if (a) return a
  return (
    (siteConfig.value && siteConfig.value.default_avatar_url) ||
    'https://via.placeholder.com/120x120.png?text=OpenBlog'
  )
})
const name = computed(() => (owner.value && owner.value.username) || '—')
const signature = computed(() => (owner.value && owner.value.bio) || '平凡的一枚程序员')

const githubUrl = computed(() =>
  (siteConfig.value && siteConfig.value.github_url) || 'https://github.com/yyyCode'
)
const csdnUrl = computed(() =>
  (siteConfig.value && siteConfig.value.csdn_url) || 'https://blog.csdn.net/2301_80044822'
)
const nowcoderUrl = computed(() =>
  (siteConfig.value && siteConfig.value.nowcoder_url) || 'https://www.nowcoder.com/users/597303882'
)

onMounted(async () => {
  try {
    owner.value = await fetchOwnerProfile()
  } catch {
    owner.value = null
  }
})
</script>

<style scoped>
.page-wrap {
  display: flex;
  justify-content: center;
  padding: 28px 16px;
}
.about-us-card {
  width: 100%;
  max-width: 520px;
  padding: 28px 18px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.about-us-intro {
  margin-top: 26px;
}
.about-us-intro-text {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.9;
}
.about-us-card .links-row {
  margin-top: 22px;
}
</style>
