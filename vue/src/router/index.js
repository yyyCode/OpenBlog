import { createRouter, createWebHistory } from 'vue-router'

import { clearAuth, isConsoleSessionValid, canAccessConsole } from '../auth/session'

// 首页保持同步加载（首屏直出）；其余路由按需加载，避免一个入口包塞进全部页面
import HomeView from '../views/HomeView.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/all',
    name: 'allArticles',
    component: () => import('../views/AllArticlesView.vue')
  },
  {
    path: '/about',
    name: 'about',
    component: () => import('../views/AboutView.vue')
  },
  {
    path: '/about-us',
    name: 'aboutUs',
    component: () => import('../views/AboutUsView.vue')
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('../views/SearchResultsView.vue')
  },
  {
    path: '/jobs',
    name: 'jobNav',
    component: () => import('../views/JobNavView.vue')
  },
  {
    path: '/jobs/interview',
    name: 'jobInterviewTips',
    component: () => import('../views/JobSubView.vue'),
    props: { title: '面试技巧' }
  },
  {
    path: '/jobs/companies',
    name: 'jobSmallCompanies',
    component: () => import('../views/CompaniesRecommendView.vue')
  },
  {
    path: '/jobs/companies/:id',
    name: 'jobSmallCompanyDetail',
    component: () => import('../views/CompanyDetailView.vue'),
    props: true
  },
  {
    path: '/projects',
    name: 'projectsList',
    component: () => import('../views/ProjectsListView.vue')
  },
  {
    path: '/project/:id',
    name: 'projectDetail',
    component: () => import('../views/ProjectDetailView.vue'),
    props: true
  },
  {
    path: '/feedback',
    name: 'feedback',
    component: () => import('../views/FeedbackView.vue')
  },
  {
    path: '/bugs',
    name: 'bugCaseList',
    component: () => import('../views/BugCaseListView.vue')
  },
  {
    path: '/forum',
    name: 'forumList',
    component: () => import('../views/ForumListView.vue')
  },
  {
    path: '/forum/new',
    name: 'forumCompose',
    component: () => import('../views/ForumComposeView.vue')
  },
  {
    path: '/forum/topic/:id',
    name: 'forumTopic',
    component: () => import('../views/ForumTopicView.vue'),
    props: true
  },
  {
    path: '/login',
    name: 'siteAuth',
    component: () => import('../views/SiteAuthView.vue')
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue')
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
    component: () => import('../views/AdminLoginView.vue')
  },
  {
    path: '/console',
    component: () => import('../layouts/ConsoleLayout.vue'),
    children: [
      { path: '', name: 'consoleDashboard', component: () => import('../views/ConsoleDashboardView.vue') },
      { path: 'profile', name: 'consoleProfile', component: () => import('../views/ConsoleProfileView.vue') },
      { path: 'articles', redirect: '/console/articles/manage' },
      { path: 'articles/manage', name: 'consoleArticleManage', component: () => import('../views/ConsoleArticleManageView.vue') },
      { path: 'articles/new', name: 'consoleArticleCompose', component: () => import('../views/ConsoleArticleComposeView.vue') },
      { path: 'categories', name: 'consoleCategories', component: () => import('../views/ConsoleCategoriesView.vue') },
      { path: 'changelog', name: 'consoleChangelog', component: () => import('../views/ConsoleChangelogView.vue') },
      { path: 'projects', name: 'consoleProjects', component: () => import('../views/ConsoleProjectsView.vue') },
      { path: 'companies', name: 'consoleCompanies', component: () => import('../views/ConsoleCompaniesView.vue') },
      { path: 'feedback', name: 'consoleFeedback', component: () => import('../views/ConsoleFeedbackView.vue') },
      { path: 'attachments', name: 'consoleAttachments', component: () => import('../views/ConsoleAttachmentsView.vue') },
      { path: 'comments', name: 'consoleComments', component: () => import('../views/ConsoleCommentsView.vue') },
      { path: 'forum', name: 'consoleForum', component: () => import('../views/ConsoleForumView.vue') },
      { path: 'users', name: 'consoleUsers', component: () => import('../views/ConsoleUsersView.vue') },
      { path: 'users/:userId', name: 'consoleUserDetail', component: () => import('../views/ConsoleUserDetailView.vue') },
      { path: 'users/pending', name: 'consolePendingUsers', component: () => import('../views/ConsolePendingUsersView.vue') },
      { path: 'sensitive-words', name: 'consoleSensitiveWords', component: () => import('../views/ConsoleSensitiveWordsView.vue') },
      { path: 'site-config', name: 'consoleSiteConfig', component: () => import('../views/ConsoleSiteConfigView.vue') },
      { path: 'system', name: 'consoleSystem', component: () => import('../views/ConsoleSystemView.vue') }
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
    component: () => import('../views/ArticleDetailView.vue'),
    props: true
  },
  {
    path: '/changelog',
    name: 'changelogList',
    component: () => import('../views/ChangelogListView.vue')
  },
  {
    path: '/changelog/:id',
    name: 'changelogDetail',
    component: () => import('../views/ChangelogDetailView.vue'),
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
  // 个人中心：需登录，未登录跳登录页（登录成功后经 redirect 回跳）
  if (to.path === '/profile') {
    if (!isConsoleSessionValid()) {
      clearAuth()
      return { path: '/login', query: { redirect: '/profile' } }
    }
    return true
  }

  // 问题反馈：需登录（提交人取自 JWT，按账号每天限一次）
  if (to.path === '/feedback') {
    if (!isConsoleSessionValid()) {
      clearAuth()
      return { path: '/login', query: { redirect: '/feedback' } }
    }
    return true
  }

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
