<template>
  <div class="card profile-card">
    <div class="profile-card-body">
      <div class="profile-avatar-wrap">
        <img
          class="profile-avatar"
          :src="profile?.avatarUrl || defaultAvatar"
          alt="avatar"
        />
      </div>

      <div class="profile-name">{{ profile?.nickname || 'FUNKY' }}</div>
      <div class="profile-signature">{{ profile?.bio || '平凡的一枚程序员' }}</div>

      <div class="profile-menu">
        <router-link class="profile-menu-item" to="/">主页</router-link>
        <router-link class="profile-menu-item active" to="/all">全部文章</router-link>
      </div>

      <div class="profile-foot">
        所有文章 / 友情链接 / 关于我
      </div>

      <div class="links-row">
        <a class="icon-link" :href="githubUrl" target="_blank" rel="noreferrer">
          GitHub
        </a>
        <a class="icon-link" :href="csdnUrl" target="_blank" rel="noreferrer">
          CSDN
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  profile: {
    type: Object,
    required: false
  }
})

const defaultAvatar = 'https://via.placeholder.com/120x120.png?text=OpenBlog'
const githubUrl = 'https://github.com/yyyCode'
const csdnUrl = 'https://blog.csdn.net/2301_80044822'

</script>

