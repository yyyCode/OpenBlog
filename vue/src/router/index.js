import { createRouter, createWebHistory } from 'vue-router'

import { clearAuth, isConsoleSessionValid, canAccessConsole } from '../auth/session'

import HomeView from '../views/HomeView.vue'
import ArticleDetailView from '../views/ArticleDetailView.vue'
import AllArticlesView from '../views/AllArticlesView.vue'
import AdminLoginView from '../views/AdminLoginView.vue'
import ChangelogListView from '../views/ChangelogListView.vue'
import ChangelogDetailView from '../views/ChangelogDetailView.vue'
import AboutView from '../views/AboutView.vue'
import FeedbackView from '../views/FeedbackView.vue'
import SiteAuthView from '../views/SiteAuthView.vue'
import ConsoleLayout from '../layouts/ConsoleLayout.vue'
import ConsoleDashboardView from '../views/ConsoleDashboardView.vue'
import ConsoleProfileView from '../views/ConsoleProfileView.vue'
import ConsoleArticleManageView from '../views/ConsoleArticleManageView.vue'
import ConsoleArticleComposeView from '../views/ConsoleArticleComposeView.vue'
import ConsoleChangelogView from '../views/ConsoleChangelogView.vue'
import ConsoleAttachmentsView from '../views/ConsoleAttachmentsView.vue'
import ConsoleCommentsView from '../views/ConsoleCommentsView.vue'
import ConsoleSystemView from '../views/ConsoleSystemView.vue'
import ConsoleFeedbackView from '../views/ConsoleFeedbackView.vue'

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
    path: '/about',
    name: 'about',
    component: AboutView
  },
  {
    path: '/feedback',
    name: 'feedback',
    component: FeedbackView
  },
  {
    path: '/login',
    name: 'siteAuth',
    component: SiteAuthView
  },
  {
    path: '/register',
    name: 'siteRegister',
    redirect: (to) => ({
      path: '/login',
      query: { ...to.query, tab: 'register' }
    })
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
      { path: 'articles', redirect: '/console/articles/manage' },
      { path: 'articles/manage', name: 'consoleArticleManage', component: ConsoleArticleManageView },
      { path: 'articles/new', name: 'consoleArticleCompose', component: ConsoleArticleComposeView },
      { path: 'changelog', name: 'consoleChangelog', component: ConsoleChangelogView },
      { path: 'feedback', name: 'consoleFeedback', component: ConsoleFeedbackView },
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
    if (isConsoleSessionValid() && canAccessConsole()) {
      const r = to.query.redirect
      return { path: safeConsoleRedirect(typeof r === 'string' ? r : '') }
    }
    if (isConsoleSessionValid() && !canAccessConsole()) {
      return { path: '/' }
    }
    return true
  }

  if (!isConsoleSessionValid()) {
    clearAuth()
    return { path: '/console/login', query: { redirect: to.fullPath } }
  }
  if (!canAccessConsole()) {
    return { path: '/' }
  }
  return true
})

export default router
