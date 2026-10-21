<template>
  <div class="placeholder-page">
    <div class="placeholder-card">
      <div class="placeholder-icon">
        <el-icon :size="34"><Reading /></el-icon>
      </div>
      <h2>{{ route.meta.title }}</h2>
      <p>这个页面将在后续批次实现。</p>

      <div class="batch-list">
        <span class="done">批次 1 · 登录 / 布局 / 首页看板</span>
        <span>批次 2 · 部门 / 员工</span>
        <span>批次 3 · 课程 / 班级 / 学员</span>
        <span>批次 4 · 操作日志 / AI</span>
      </div>

      <el-button type="primary" plain @click="$router.push('/dashboard')">回到首页看板</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<style scoped>
.placeholder-page {
  display: grid;
  place-items: center;
  min-height: calc(100vh - 140px);
}

.placeholder-card {
  width: min(520px, 100%);
  padding: 44px 40px;
  border: 1px solid var(--tl-border);
  border-radius: 22px;
  background: #fff;
  box-shadow: var(--tl-shadow);
  text-align: center;
}

.placeholder-icon {
  display: grid;
  place-items: center;
  width: 68px;
  height: 68px;
  margin: 0 auto 18px;
  border-radius: 20px;
  background: #eef2ff;
  color: var(--tl-primary);
}

.placeholder-card h2 {
  font-size: 22px;
  letter-spacing: -0.4px;
  color: var(--tl-ink);
}

.placeholder-card p {
  margin-top: 8px;
  font-size: 13px;
  color: var(--tl-muted);
}

.batch-list {
  display: grid;
  gap: 8px;
  margin: 24px 0;
  padding: 16px;
  border-radius: 14px;
  background: #f8fafc;
  font-size: 12px;
  color: #94a3b8;
  text-align: left;
}

.batch-list .done {
  color: #059669;
  font-weight: 600;
}
</style>
