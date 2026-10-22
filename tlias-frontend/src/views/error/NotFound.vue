<template>
  <div class="not-found">
    <div class="code">404</div>
    <h2>页面走丢了</h2>
    <p>你访问的地址不存在，或者已经被移动。</p>
    <el-button type="primary" @click="$router.replace('/dashboard')">回到首页</el-button>
  </div>
</template>

<style scoped>
.not-found {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  min-height: 100vh;
  padding: 24px;
  background: radial-gradient(circle at 50% 30%, #eef2ff 0%, #f8fafc 55%);
  text-align: center;
}

.code {
  font-size: clamp(90px, 16vw, 150px);
  font-weight: 800;
  line-height: 1;
  letter-spacing: -6px;
  color: transparent;
  background: linear-gradient(135deg, #6366f1, #8b5cf6 50%, #06b6d4);
  -webkit-background-clip: text;
  background-clip: text;
}

.not-found h2 {
  font-size: 24px;
  letter-spacing: -0.5px;
  color: var(--tl-ink);
}

.not-found p {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--tl-muted);
}
</style>
