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

export default createRouter({
  history: createWebHistory(),
  routes
})

