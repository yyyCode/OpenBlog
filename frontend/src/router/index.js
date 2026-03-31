import { createRouter, createWebHistory } from 'vue-router'

import HomeView from '../views/HomeView.vue'
import ArticleDetailView from '../views/ArticleDetailView.vue'
import AllArticlesView from '../views/AllArticlesView.vue'
import AdminLoginView from '../views/AdminLoginView.vue'
import AdminDashboardView from '../views/AdminDashboardView.vue'
import ChangelogListView from '../views/ChangelogListView.vue'
import ChangelogDetailView from '../views/ChangelogDetailView.vue'

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
    path: '/admin/login',
    name: 'adminLogin',
    component: AdminLoginView
  },
  {
    path: '/admin',
    name: 'adminDashboard',
    component: AdminDashboardView
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
 * 本标签页内是否已访问过「前台」任意页面（非 /admin*）。
 * 用于避免地址栏直接敲 /admin、/admin/login：须先访问本站前台一次。
 * sessionStorage：关标签即失效；新标签需重新从本站进入。
 */
const SITE_ENTRY_KEY = 'openblog_site_entry_ok'

router.afterEach((to) => {
  if (!to.path.startsWith('/admin')) {
    sessionStorage.setItem(SITE_ENTRY_KEY, '1')
  }
})

router.beforeEach((to) => {
  if (to.path.startsWith('/admin')) {
    if (!sessionStorage.getItem(SITE_ENTRY_KEY)) {
      return { path: '/' }
    }
    if (to.path !== '/admin/login' && !localStorage.getItem('accessToken')) {
      return { path: '/admin/login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

export default router

