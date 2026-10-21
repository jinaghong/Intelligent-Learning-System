import { createRouter, createWebHistory } from 'vue-router'

import Layout from '@/layout/index.vue'
import { getToken } from '@/utils/token'

export const menuRoutes = [
  {
    path: 'dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '首页', icon: 'DataBoard' }
  },
  {
    path: 'depts',
    name: 'Dept',
    component: () => import('@/views/placeholder/index.vue'),
    meta: { title: '部门管理', icon: 'OfficeBuilding' }
  },
  {
    path: 'emps',
    name: 'Emp',
    component: () => import('@/views/placeholder/index.vue'),
    meta: { title: '员工管理', icon: 'User' }
  },
  {
    path: 'courses',
    name: 'Course',
    component: () => import('@/views/placeholder/index.vue'),
    meta: { title: '课程管理', icon: 'Reading' }
  },
  {
    path: 'clazzs',
    name: 'Clazz',
    component: () => import('@/views/placeholder/index.vue'),
    meta: { title: '班级管理', icon: 'School' }
  },
  {
    path: 'students',
    name: 'Student',
    component: () => import('@/views/placeholder/index.vue'),
    meta: { title: '学员管理', icon: 'UserFilled' }
  },
  {
    path: 'logs',
    name: 'Log',
    component: () => import('@/views/placeholder/index.vue'),
    meta: { title: '操作日志', icon: 'Document' }
  }
]

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    name: 'Root',
    component: Layout,
    redirect: '/dashboard',
    children: menuRoutes
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · tlias` : 'tlias 智能学习辅助系统'

  const token = getToken()
  if (!token && !to.meta.public) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (token && to.path === '/login') {
    return { path: '/dashboard' }
  }
  return true
})

export default router
