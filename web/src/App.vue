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
          &copy; 2025 OpenBlog
        </div>
      </div>
    </aside>
  </div>
</template>

<style>
/* 全局样式重置，确保背景色覆盖 */
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
  background-color: #f6f8fa; /* 内容区浅灰背景 */
  color: #24292f;
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
  background-color: #fff;
  border-left: 1px solid #d0d7de; /* 左边框 */
  flex-shrink: 0;
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
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
  border-bottom: 1px solid #eaeaea;
}

.avatar-wrapper {
  margin-bottom: 1rem;
}

.avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 4px solid #fff;
  box-shadow: 0 0 15px rgba(0,0,0,0.1);
  object-fit: cover;
  transition: transform 0.3s ease;
}

.avatar:hover {
  transform: scale(1.05);
}

.blog-name {
  font-size: 1.25rem;
  font-weight: 700;
  margin: 0.5rem 0 1rem;
  color: #24292f;
}

.github-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background-color: #24292f;
  color: #fff;
  border-radius: 20px;
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 600;
  transition: all 0.2s;
  width: 80%;
  margin: 0 auto;
}

.github-btn:hover {
  background-color: #000;
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
  color: #57606a;
  font-weight: 500;
  transition: all 0.2s ease;
}

.nav-item:hover {
  background-color: #f6f8fa;
  color: #24292f;
}

.nav-item.router-link-active {
  background-color: #f6f8fa;
  color: #0969da;
  font-weight: 600;
  border-right: 3px solid #0969da; /* 右侧高亮条，呼应右侧边栏 */
}

.nav-item.disabled {
  opacity: 0.5;
  cursor: default;
}

.sidebar-footer {
  margin-top: auto;
  text-align: center;
  font-size: 0.8rem;
  color: #8c959f;
  padding-top: 1rem;
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
    border-bottom: 1px solid #d0d7de;
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
