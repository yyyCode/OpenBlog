<script setup>
import { ref, onMounted } from 'vue';

const isDarkMode = ref(false);

const toggleTheme = () => {
  isDarkMode.value = !isDarkMode.value;
  const theme = isDarkMode.value ? 'dark' : 'light';
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem('theme', theme);
};

onMounted(() => {
  const savedTheme = localStorage.getItem('theme');
  if (savedTheme === 'dark') {
    isDarkMode.value = true;
    document.documentElement.setAttribute('data-theme', 'dark');
  } else if (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    // 默认跟随系统
    isDarkMode.value = true;
    document.documentElement.setAttribute('data-theme', 'dark');
  } else {
    document.documentElement.setAttribute('data-theme', 'light');
  }
});
</script>

<template>
  <div class="app-layout">
    <!-- 主内容区域 (左侧) -->
    <main class="main-content">
      <div class="content-container">
        <router-view />
      </div>
    </main>

    <!-- 侧边栏 (右侧) -->
    <aside class="sidebar">
      <div class="sidebar-wrapper">
        <!-- 个人资料区域 -->
        <div class="profile-section">
          <div class="avatar-wrapper">
            <img src="https://github.com/yyyCode.png" alt="yyyCode" class="avatar" />
          </div>
          <h2 class="blog-name">yyyCode's Blog</h2>
          
          <!-- GitHub 链接直接放在头像下方 -->
          <a href="https://github.com/yyyCode" target="_blank" class="github-btn">
            <svg height="20" width="20" viewBox="0 0 16 16" class="github-icon">
              <path fill="currentColor" fill-rule="evenodd" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"></path>
            </svg>
            Follow on GitHub
          </a>
        </div>

        <!-- 导航菜单 -->
        <nav class="nav-menu">
          <router-link to="/" class="nav-item">
            <span class="nav-text">Home</span>
          </router-link>
        </nav>

        <div class="sidebar-footer">
          <!-- 主题切换按钮 -->
          <button @click="toggleTheme" class="theme-toggle-btn" :title="isDarkMode ? 'Switch to Light Mode' : 'Switch to Dark Mode'">
            <span v-if="isDarkMode">🌞 Light Mode</span>
            <span v-else>🌙 Dark Mode</span>
          </button>
          
          <div class="copyright">
            &copy; 2025 OpenBlog
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>

<style>
/* 定义 CSS 变量：默认 Light 模式 */
:root {
  --bg-color: #f6f8fa;
  --text-color: #24292f;
  --sidebar-bg: #ffffff;
  --border-color: #d0d7de;
  --link-color: #0969da;
  --link-hover-bg: #f6f8fa;
  --text-secondary: #57606a;
  --btn-bg: #24292f;
  --btn-text: #ffffff;
  --btn-hover: #000000;
  --nav-active-color: #0969da;
}

/* Dark 模式 */
[data-theme='dark'] {
  --bg-color: #0d1117;
  --text-color: #c9d1d9;
  --sidebar-bg: #161b22;
  --border-color: #30363d;
  --link-color: #58a6ff;
  --link-hover-bg: #21262d;
  --text-secondary: #8b949e;
  --btn-bg: #21262d;
  --btn-text: #c9d1d9;
  --btn-hover: #30363d;
  --nav-active-color: #58a6ff;
}

/* 全局样式重置，使用变量 */
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
  background-color: var(--bg-color);
  color: var(--text-color);
  transition: background-color 0.3s ease, color 0.3s ease;
}

a {
  color: var(--link-color);
  text-decoration: none;
}
</style>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  flex-direction: row; /* 左内容，右侧边栏 */
}

.main-content {
  flex: 1;
  padding: 2rem;
  min-width: 0; /* 防止内容溢出 */
}

.content-container {
  max-width: 900px;
  margin: 0 auto; /* 居中显示内容 */
}

.sidebar {
  width: 280px;
  background-color: var(--sidebar-bg);
  border-left: 1px solid var(--border-color); /* 左边框 */
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  transition: background-color 0.3s ease, border-color 0.3s ease;
}

.sidebar-wrapper {
  padding: 2rem;
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
}

.profile-section {
  text-align: center;
  margin-bottom: 2rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid var(--border-color);
}

.avatar-wrapper {
  margin-bottom: 1rem;
}

.avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 4px solid var(--sidebar-bg);
  box-shadow: 0 0 15px rgba(0,0,0,0.1);
  object-fit: cover;
  transition: transform 0.3s ease, border-color 0.3s ease;
}

.avatar:hover {
  transform: scale(1.05);
}

.blog-name {
  font-size: 1.25rem;
  font-weight: 700;
  margin: 0.5rem 0 1rem;
  color: var(--text-color);
}

.github-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background-color: var(--btn-bg);
  color: var(--btn-text);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 600;
  transition: all 0.2s;
  width: 80%;
  margin: 0 auto;
}

.github-btn:hover {
  background-color: var(--btn-hover);
  transform: translateY(-1px);
}

.github-icon {
  fill: currentColor;
}

.nav-menu {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  text-decoration: none;
  color: var(--text-secondary);
  font-weight: 500;
  transition: all 0.2s ease;
}

.nav-item:hover {
  background-color: var(--link-hover-bg);
  color: var(--text-color);
}

.nav-item.router-link-active {
  background-color: var(--link-hover-bg);
  color: var(--nav-active-color);
  font-weight: 600;
  border-right: 3px solid var(--nav-active-color);
}

.nav-item.disabled {
  opacity: 0.5;
  cursor: default;
}

.sidebar-footer {
  margin-top: auto;
  text-align: center;
  padding-top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: center;
}

.theme-toggle-btn {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-color);
  padding: 0.5rem 1rem;
  border-radius: 20px;
  cursor: pointer;
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  transition: all 0.2s ease;
  width: 80%;
  justify-content: center;
}

.theme-toggle-btn:hover {
  background-color: var(--link-hover-bg);
}

.copyright {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

@media (max-width: 768px) {
  .app-layout {
    flex-direction: column; /* 移动端上下布局 */
  }
  
  .sidebar {
    width: 100%;
    height: auto;
    position: relative;
    border-left: none;
    border-bottom: 1px solid var(--border-color);
    order: -1; /* 移动端把侧边栏放到最上面 */
  }
  
  .sidebar-wrapper {
    padding: 1.5rem;
  }
  
  .avatar {
    width: 80px;
    height: 80px;
  }
}
</style>
