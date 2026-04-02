import { createRouter, createWebHistory } from 'vue-router'

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

/**
 * 本标签页内是否已访问过「前台」任意页面（非 /console*）。
 * 用于避免地址栏直接敲 /console、/console/login：须先访问本站前台一次。
 */
const SITE_ENTRY_KEY = 'openblog_site_entry_ok'

router.afterEach((to) => {
  if (!to.path.startsWith('/console')) {
    sessionStorage.setItem(SITE_ENTRY_KEY, '1')
  }
})

router.beforeEach((to) => {
  if (to.path.startsWith('/console')) {
    if (!sessionStorage.getItem(SITE_ENTRY_KEY)) {
      return { path: '/' }
    }
    if (to.path !== '/console/login' && !localStorage.getItem('accessToken')) {
      return { path: '/console/login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

export default router
