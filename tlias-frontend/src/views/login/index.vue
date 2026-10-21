<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="glow glow-a"></div>
      <div class="glow glow-b"></div>

      <div class="brand-top">
        <div class="brand-mark">
          <el-icon :size="22"><Reading /></el-icon>
        </div>
        <span>tlias · 智能学习辅助系统</span>
      </div>

      <div class="brand-copy">
        <p class="eyebrow">TRAINING MANAGEMENT PLATFORM</p>
        <h1>让教学管理<br /><span>更聪明一点</span></h1>
        <p class="lede">
          部门、员工、班级、学员、成绩与违纪数据在一个后台里打通，AI 负责把数据变成建议。
        </p>
        <ul class="feature-list">
          <li><el-icon><CircleCheck /></el-icon>全模块数据实时联动</li>
          <li><el-icon><CircleCheck /></el-icon>8 张统计看板一屏总览</li>
          <li><el-icon><CircleCheck /></el-icon>AI 学习建议 + 智能出题</li>
        </ul>
      </div>

      <div class="brand-foot">
        <span>P9 · Vue 3 + Element Plus</span>
        <span>v0.1 预览</span>
      </div>
    </section>

    <section class="form-panel">
      <div class="login-card">
        <div class="login-head">
          <p class="eyebrow">WELCOME BACK</p>
          <h2>登录管理后台</h2>
          <p>使用员工账号继续</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent="submit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
              :prefix-icon="Lock"
              autocomplete="current-password"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button type="primary" class="submit-button" :loading="loading" @click="submit">
            登录
            <el-icon class="submit-icon"><Right /></el-icon>
          </el-button>
        </el-form>

        <div class="demo-tip">
          <el-icon><InfoFilled /></el-icon>
          <span>演示账号 <b>liwei</b> / 密码 <b>123456</b></span>
        </div>
        <p class="login-foot">登录即表示你已阅读并同意内部使用规范</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled, Lock, Right, User } from '@element-plus/icons-vue'

import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: '',
  password: ''
})
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  if (loading.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    ElMessage.success(`欢迎回来，${auth.displayName}`)
    const redirect =
      typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
        ? route.query.redirect
        : '/dashboard'
    router.replace(redirect)
  } catch {
    // 拦截器已弹出错误提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(420px, 0.85fr);
  min-height: 100vh;
  background: #f8fafc;
}

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: 48px 56px;
  color: #e2e8f0;
  background: radial-gradient(circle at 15% 10%, #312e81 0%, #0f172a 42%, #070b16 100%);
}

.glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  opacity: 0.55;
  filter: blur(10px);
}

.glow-a {
  top: -100px;
  right: -120px;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.55), transparent 65%);
}

.glow-b {
  bottom: -120px;
  left: -140px;
  width: 360px;
  height: 360px;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.35), transparent 65%);
}

.brand-top {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  letter-spacing: 0.3px;
  color: #cbd5e1;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  color: #fff;
  background: linear-gradient(135deg, #818cf8, #4f46e5 60%, #7c3aed);
  box-shadow: 0 14px 30px rgba(79, 70, 229, 0.45);
}

.brand-copy {
  position: relative;
  max-width: 560px;
  margin: 40px 0;
}

.eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 2.4px;
  color: #818cf8;
}

.brand-copy h1 {
  margin-top: 18px;
  font-size: clamp(40px, 4.4vw, 62px);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -1px;
  color: #f8fafc;
}

.brand-copy h1 span {
  color: transparent;
  background: linear-gradient(120deg, #a5b4fc, #67e8f9 55%, #c4b5fd);
  -webkit-background-clip: text;
  background-clip: text;
}

.lede {
  max-width: 470px;
  margin-top: 22px;
  font-size: 15px;
  line-height: 1.8;
  color: #94a3b8;
}

.feature-list {
  display: grid;
  gap: 14px;
  margin: 34px 0 0;
  padding: 0;
  list-style: none;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: #cbd5e1;
}

.feature-list .el-icon {
  font-size: 18px;
  color: #34d399;
}

.brand-foot {
  position: relative;
  display: flex;
  justify-content: space-between;
  padding-top: 18px;
  border-top: 1px solid rgba(148, 163, 184, 0.15);
  font-size: 12px;
  color: #64748b;
}

.form-panel {
  display: grid;
  place-items: center;
  padding: 48px 40px;
  background: #f8fafc;
}

.login-card {
  width: min(400px, 100%);
  padding: 40px 38px 32px;
  border: 1px solid var(--tl-border);
  border-radius: 24px;
  background: #fff;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.1);
}

.login-head {
  margin-bottom: 28px;
}

.login-head h2 {
  margin-top: 10px;
  font-size: 28px;
  letter-spacing: -0.5px;
  color: var(--tl-ink);
}

.login-head p:last-child {
  margin-top: 8px;
  font-size: 13px;
  color: var(--tl-muted);
}

.submit-button {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  box-shadow: 0 14px 28px rgba(79, 70, 229, 0.28);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
}

.submit-button:hover {
  background: linear-gradient(135deg, #4f46e5, #4338ca);
}

.submit-icon {
  margin-left: 8px;
}

.demo-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 22px;
  padding: 11px 13px;
  border-radius: 11px;
  background: #eef2ff;
  font-size: 12.5px;
  color: #4338ca;
}

.demo-tip .el-icon {
  font-size: 15px;
}

.login-foot {
  margin-top: 20px;
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
}

:deep(.el-form-item__label) {
  padding-bottom: 6px;
  font-size: 13px;
  color: var(--tl-text);
}

:deep(.el-input__wrapper) {
  padding: 4px 13px;
  border-radius: 11px;
  background: #fbfcfe;
  box-shadow: 0 0 0 1px var(--tl-border) inset;
}

:deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1px var(--tl-primary) inset, 0 0 0 4px rgba(99, 102, 241, 0.12);
}

@media (max-width: 980px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .form-panel {
    min-height: 100vh;
  }
}
</style>
