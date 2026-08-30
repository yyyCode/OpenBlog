<template>
  <div class="company-card" @click="emit('select', company)">
    <div class="company-avatar" :style="{ backgroundColor: color }">
      <img v-if="logo" :src="logo" :alt="company.name" class="company-avatar-img" />
      <span v-else class="company-avatar-text">{{ initial }}</span>
    </div>
    <div class="company-name" :title="company.name">{{ company.name }}</div>
    <div class="company-meta">{{ scaleText }}<template v-if="scaleText && company.type"> · </template>{{ company.type }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatScale, logoUrl } from '../api/smallCompany'

const props = defineProps({
  company: { type: Object, required: true }
})
const emit = defineEmits(['select'])

// 无 logo 时的首字占位（中文取首字，英文取首字母）
const initial = computed(() => {
  const name = (props.company && props.company.name) || '?'
  return name.trim().charAt(0).toUpperCase()
})

const logo = computed(() => logoUrl(props.company.logoMediaKey))

const scaleText = computed(() => formatScale(props.company))

const color = computed(() => props.company.color || '#4f7cff')
</script>

<style scoped>
/* 九宫格方块卡片：头像 / 名称 / 规模·类型，无需文字标注 */
.company-card {
  aspect-ratio: 4 / 3;
  border-radius: 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.company-card:hover {
  transform: translateY(-4px);
  border-color: var(--accent);
  box-shadow: 0 12px 28px rgba(51, 112, 255, 0.16);
}
[data-theme='dark'] .company-card:hover {
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.5);
}

.company-avatar {
  width: 68px;
  height: 68px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.14);
  overflow: hidden;
  flex: none;
}
.company-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 6px;
  background: #fff;
}
.company-avatar-text {
  color: #fff;
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  user-select: none;
}

.company-name {
  font-size: 17px;
  font-weight: 650;
  color: var(--text);
  text-align: center;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.company-meta {
  font-size: 13px;
  color: var(--muted);
  text-align: center;
  white-space: nowrap;
}
</style>
