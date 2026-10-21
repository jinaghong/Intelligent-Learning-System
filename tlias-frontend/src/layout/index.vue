<template>
  <el-container class="app-shell">
    <el-aside :width="collapsed ? '76px' : '236px'" class="sidebar" :class="{ 'is-collapsed': collapsed }">
      <div class="brand">
        <div class="brand-mark">
          <el-icon :size="20"><Reading /></el-icon>
        </div>
        <div v-show="!collapsed" class="brand-text">
          <strong>tlias</strong>
          <span>智能学习辅助系统</span>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="side-menu"
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="`/${item.path}`">
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <template #title>{{ item.meta.title }}</template>
        </el-menu-item>
      </el-menu>

      <el-tooltip
        v-if="!collapsed"
        content="后端 /ai/advice 与 /ai/questions 已就绪；默认 Mock 实现，切 deepseek profile 后调用真实模型。前端 AI 入口在批次 3/4 接入。"
        placement="right"
        :show-after="200"
      >
        <div class="sidebar-foot">
          <span class="dot"></span>
          AI 后端已就绪
          <el-icon class="foot-info"><InfoFilled /></el-icon>
        </div>
      </el-tooltip>
    </el-aside>

    <el-container class="workspace">
      <el-header class="navbar">
        <div class="navbar-left">
          <button class="icon-button" type="button" @click="collapsed = !collapsed">
            <el-icon :size="18"><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          </button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.path !== '/dashboard'">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="navbar-right">
          <span class="status-pill" :class="health">
            <span class="dot"></span>
            {{ healthText }}
          </span>
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-chip">
              <el-avatar :size="34" :src="auth.avatar">{{ auth.displayName.slice(0, 1) }}</el-avatar>
              <div class="user-meta">
                <strong>{{ auth.displayName }}</strong>
                <span>{{ auth.user?.username || '—' }}</span>
              </div>
              <el-icon class="chevron"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" disabled>个人信息</el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main-area">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { getHealth } from '@/api/system'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const health = ref('checking')
const menus = router.options.routes.find((item) => item.path === '/')?.children ?? []

const healthText = computed(() => {
  if (health.value === 'up') return '系统正常'
  if (health.value === 'down') return '服务异常'
  return '检测中'
})

onMounted(async () => {
  try {
    const data = await getHealth()
    health.value = data?.status === 'UP' ? 'up' : 'down'
  } catch {
    health.value = 'down'
  }

  if (auth.token && !auth.user) {
    auth.fetchMe().catch(() => {})
  }
})

async function handleCommand(command) {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('退出后需要重新登录，确定退出吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await auth.logout()
    ElMessage.success('已退出登录')
    router.replace('/login')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--tl-bg);
}

.sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: radial-gradient(circle at 20% 0%, #1e1b4b 0%, #0b1020 55%, #080b16 100%);
  color: #e2e8f0;
  transition: width 0.22s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 76px;
  padding: 0 20px;
}

.brand-mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(135deg, #818cf8, #4f46e5 60%, #7c3aed);
  box-shadow: 0 10px 24px rgba(99, 102, 241, 0.45);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  white-space: nowrap;
}

.brand-text strong {
  font-size: 20px;
  letter-spacing: 0.5px;
  color: #fff;
}

.brand-text span {
  margin-top: 3px;
  font-size: 11px;
  color: #94a3b8;
}

.side-menu {
  flex: 1;
  padding: 8px 0;
  border-right: none;
  background: transparent;
}

:deep(.side-menu .el-menu-item) {
  height: 46px;
  margin: 4px 12px;
  border-radius: 11px;
  color: #cbd5e1;
  font-size: 14px;
  line-height: 46px;
}

:deep(.side-menu .el-menu-item:hover) {
  color: #fff;
  background: rgba(148, 163, 184, 0.12);
}

:deep(.side-menu .el-menu-item.is-active) {
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  box-shadow: 0 10px 22px rgba(79, 70, 229, 0.38);
}

:deep(.side-menu .el-menu-item .el-icon) {
  font-size: 18px;
}

.is-collapsed :deep(.side-menu .el-menu-item) {
  margin: 4px 10px;
}

.sidebar-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 20px 20px;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.06);
  font-size: 12px;
  color: #94a3b8;
}

.dot {
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.15);
}

.foot-info {
  margin-left: auto;
  font-size: 13px;
  color: #64748b;
}

.workspace {
  min-width: 0;
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid var(--tl-border);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(12px);
}

.navbar-left,
.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.icon-button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--tl-border);
  border-radius: 10px;
  background: #fff;
  color: var(--tl-muted);
  cursor: pointer;
  transition: all 0.18s ease;
}

.icon-button:hover {
  color: var(--tl-primary);
  border-color: #c7d2fe;
  background: #eef2ff;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}

.status-pill.up {
  background: #ecfdf5;
  color: #059669;
}

.status-pill.checking {
  background: #f1f5f9;
  color: #64748b;
}

.status-pill.down {
  background: #fef2f2;
  color: #dc2626;
}

.status-pill.checking .dot {
  background: #94a3b8;
  box-shadow: 0 0 0 4px rgba(148, 163, 184, 0.15);
}

.status-pill.down .dot {
  background: #ef4444;
  box-shadow: 0 0 0 4px rgba(239, 68, 68, 0.15);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.18s;
}

.user-chip:hover {
  background: #f1f5f9;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-meta strong {
  font-size: 13px;
  color: var(--tl-ink);
}

.user-meta span {
  font-size: 11px;
  color: var(--tl-muted);
}

.chevron {
  color: #94a3b8;
  font-size: 12px;
}

.main-area {
  padding: 22px 24px 36px;
}

.page-enter-active,
.page-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
}

@media (max-width: 900px) {
  .sidebar {
    width: 76px !important;
  }

  .brand-text,
  .sidebar-foot,
  .user-meta,
  .status-pill {
    display: none;
  }

  .main-area {
    padding: 16px;
  }
}
</style>
