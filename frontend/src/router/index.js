import { createRouter, createWebHistory } from 'vue-router'

import { clearAuth, isConsoleSessionValid } from '../auth/session'

import HomeView from '../views/HomeView.vue'
import ArticleDetailView from '../views/ArticleDetailView.vue'
import AllArticlesView from '../views/AllArticlesView.vue'
import AdminLoginView from '../views/AdminLoginView.vue'
import ChangelogListView from '../views/ChangelogListView.vue'
import ChangelogDetailView from '../views/ChangelogDetailView.vue'
import ConsoleLayout from '../layouts/ConsoleLayout.vue'
import ConsoleDashboardView from '../views/ConsoleDashboardView.vue'
import ConsoleProfileView from '../views/ConsoleProfileView.vue'
import ConsoleArticlesView from '../views/ConsoleArticlesView.vue'
import ConsoleChangelogView from '../views/ConsoleChangelogView.vue'
import ConsoleAttachmentsView from '../views/ConsoleAttachmentsView.vue'
import ConsoleCommentsView from '../views/ConsoleCommentsView.vue'
import ConsoleSystemView from '../views/ConsoleSystemView.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/all',
    name: 'allArticles',
    component: AllArticlesView
  },
  {
    path: '/console/login',
    name: 'consoleLogin',
    component: AdminLoginView
  },
  {
    path: '/console',
    component: ConsoleLayout,
    children: [
      { path: '', name: 'consoleDashboard', component: ConsoleDashboardView },
      { path: 'profile', name: 'consoleProfile', component: ConsoleProfileView },
      { path: 'articles', name: 'consoleArticles', component: ConsoleArticlesView },
      { path: 'changelog', name: 'consoleChangelog', component: ConsoleChangelogView },
      { path: 'attachments', name: 'consoleAttachments', component: ConsoleAttachmentsView },
      { path: 'comments', name: 'consoleComments', component: ConsoleCommentsView },
      { path: 'system', name: 'consoleSystem', component: ConsoleSystemView }
    ]
  },
  {
    path: '/admin/login',
    redirect: '/console/login'
  },
  {
    path: '/admin',
    redirect: '/console'
  },
  {
    path: '/article/:id',
    name: 'articleDetail',
    component: ArticleDetailView,
    props: true
  },
  {
    path: '/changelog',
    name: 'changelogList',
    component: ChangelogListView
  },
  {
    path: '/changelog/:id',
    name: 'changelogDetail',
    component: ChangelogDetailView,
    props: true
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 仅允许站内相对路径，防止 open redirect */
function safeConsoleRedirect(raw) {
  if (typeof raw !== 'string') return '/console'
  const t = raw.trim()
  if (!t.startsWith('/') || t.startsWith('//')) return '/console'
  if (t.startsWith('/console/login')) return '/console'
  return t
}

router.beforeEach((to) => {
  if (!to.path.startsWith('/console')) return true

  const isLoginPage = to.path === '/console/login'

  if (isLoginPage) {
    if (isConsoleSessionValid()) {
      const r = to.query.redirect
      return { path: safeConsoleRedirect(typeof r === 'string' ? r : '') }
    }
    return true
  }

  if (!isConsoleSessionValid()) {
    clearAuth()
    return { path: '/console/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
