import { createRouter, createWebHistory } from 'vue-router'

import { clearAuth, isConsoleSessionValid, canAccessConsole } from '../auth/session'

import HomeView from '../views/HomeView.vue'
import ArticleDetailView from '../views/ArticleDetailView.vue'
import AllArticlesView from '../views/AllArticlesView.vue'
import AdminLoginView from '../views/AdminLoginView.vue'
import ChangelogListView from '../views/ChangelogListView.vue'
import ChangelogDetailView from '../views/ChangelogDetailView.vue'
import AboutView from '../views/AboutView.vue'
import AboutUsView from '../views/AboutUsView.vue'
import FeedbackView from '../views/FeedbackView.vue'
import SearchResultsView from '../views/SearchResultsView.vue'
import SiteAuthView from '../views/SiteAuthView.vue'
import ConsoleLayout from '../layouts/ConsoleLayout.vue'
import ConsoleDashboardView from '../views/ConsoleDashboardView.vue'
import ConsoleProfileView from '../views/ConsoleProfileView.vue'
import ConsoleArticleManageView from '../views/ConsoleArticleManageView.vue'
import ConsoleArticleComposeView from '../views/ConsoleArticleComposeView.vue'
import ConsoleCategoriesView from '../views/ConsoleCategoriesView.vue'
import ConsoleChangelogView from '../views/ConsoleChangelogView.vue'
import ConsoleAttachmentsView from '../views/ConsoleAttachmentsView.vue'
import ConsoleCommentsView from '../views/ConsoleCommentsView.vue'
import ConsoleSystemView from '../views/ConsoleSystemView.vue'
import ConsolePendingUsersView from '../views/ConsolePendingUsersView.vue'
import ConsoleUsersView from '../views/ConsoleUsersView.vue'
import ConsoleUserDetailView from '../views/ConsoleUserDetailView.vue'
import ConsoleFeedbackView from '../views/ConsoleFeedbackView.vue'
import ConsoleSiteConfigView from '../views/ConsoleSiteConfigView.vue'
import JobNavView from '../views/JobNavView.vue'
import ProjectsListView from '../views/ProjectsListView.vue'
import ProjectDetailView from '../views/ProjectDetailView.vue'
import ConsoleProjectsView from '../views/ConsoleProjectsView.vue'
import ForumListView from '../views/ForumListView.vue'
import ProfileView from '../views/ProfileView.vue'
import ForumTopicView from '../views/ForumTopicView.vue'
import ForumComposeView from '../views/ForumComposeView.vue'
import ConsoleForumView from '../views/ConsoleForumView.vue'

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
    path: '/about-us',
    name: 'aboutUs',
    component: AboutUsView
  },
  {
    path: '/search',
    name: 'search',
    component: SearchResultsView
  },
  {
    path: '/jobs',
    name: 'jobNav',
    component: JobNavView
  },
  {
    path: '/projects',
    name: 'projectsList',
    component: ProjectsListView
  },
  {
    path: '/project/:id',
    name: 'projectDetail',
    component: ProjectDetailView,
    props: true
  },
  {
    path: '/feedback',
    name: 'feedback',
    component: FeedbackView
  },
  {
    path: '/forum',
    name: 'forumList',
    component: ForumListView
  },
  {
    path: '/forum/new',
    name: 'forumCompose',
    component: ForumComposeView
  },
  {
    path: '/forum/topic/:id',
    name: 'forumTopic',
    component: ForumTopicView,
    props: true
  },
  {
    path: '/login',
    name: 'siteAuth',
    component: SiteAuthView
  },
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView
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
      { path: 'categories', name: 'consoleCategories', component: ConsoleCategoriesView },
      { path: 'changelog', name: 'consoleChangelog', component: ConsoleChangelogView },
      { path: 'projects', name: 'consoleProjects', component: ConsoleProjectsView },
      { path: 'feedback', name: 'consoleFeedback', component: ConsoleFeedbackView },
      { path: 'attachments', name: 'consoleAttachments', component: ConsoleAttachmentsView },
      { path: 'comments', name: 'consoleComments', component: ConsoleCommentsView },
      { path: 'forum', name: 'consoleForum', component: ConsoleForumView },
      { path: 'users', name: 'consoleUsers', component: ConsoleUsersView },
      { path: 'users/:userId', name: 'consoleUserDetail', component: ConsoleUserDetailView },
      { path: 'users/pending', name: 'consolePendingUsers', component: ConsolePendingUsersView },
      { path: 'site-config', name: 'consoleSiteConfig', component: ConsoleSiteConfigView },
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
  // 个人中心：需登录，未登录跳登录页（登录成功后经 redirect 回跳）
  if (to.path === '/profile') {
    if (!isConsoleSessionValid()) {
      clearAuth()
      return { path: '/login', query: { redirect: '/profile' } }
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
