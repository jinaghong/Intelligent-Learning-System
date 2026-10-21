#!/usr/bin/env node
/**
 * 批次 1 预览验收：登录 → 看板 → 占位页 → 404 → 路由守卫
 * 零依赖，使用本机 Chrome 的 DevTools Protocol。
 *
 * 用法：node scripts/verify-preview.mjs <URL> <截图目录> [CDP 地址]
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const URL_ = process.argv[2] || 'http://127.0.0.1:5173'
const OUT_DIR = process.argv[3] || './preview'
const CDP = process.argv[4] || 'http://127.0.0.1:9222'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
let ws
let msgId = 0
const pending = new Map()
const consoleErrors = []
const exceptions = []
const badResponses = []
const failedRequests = []
const apiCalls = []

function send(method, params = {}) {
  const id = ++msgId
  return new Promise((res, rej) => {
    pending.set(id, { res, rej })
    ws.send(JSON.stringify({ id, method, params }))
    setTimeout(() => {
      if (pending.has(id)) {
        pending.delete(id)
        rej(new Error(`CDP 超时: ${method}`))
      }
    }, 30000)
  })
}

async function evaluate(body) {
  const r = await send('Runtime.evaluate', {
    expression: `(() => { ${body} })()`,
    awaitPromise: true,
    returnByValue: true
  })
  if (r.exceptionDetails) {
    throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text)
  }
  return r.result.value
}

async function navigate(url, wait = 2500) {
  await send('Page.navigate', { url })
  await sleep(wait)
}

async function waitFor(expression, timeout = 15000, interval = 400) {
  const started = Date.now()
  while (Date.now() - started < timeout) {
    try {
      const value = await evaluate(`return (${expression})`)
      if (value) return value
    } catch {
      // 页面切换中，忽略
    }
    await sleep(interval)
  }
  return null
}

async function screenshot(name, fullPage = false) {
  let params = { format: 'png' }
  if (fullPage) {
    const metrics = await send('Page.getLayoutMetrics')
    params = {
      format: 'png',
      captureBeyondViewport: true,
      clip: {
        x: 0,
        y: 0,
        width: Math.ceil(metrics.cssContentSize.width),
        height: Math.ceil(metrics.cssContentSize.height),
        scale: 1
      }
    }
  }
  const img = await send('Page.captureScreenshot', params)
  const file = resolve(OUT_DIR, name)
  writeFileSync(file, Buffer.from(img.data, 'base64'))
  return file
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })

  const targets = await (await fetch(`${CDP}/json/list`)).json()
  const page = targets.find((t) => t.type === 'page')
  if (!page) throw new Error('未找到 page target —— Chrome 是否带 --remote-debugging-port 启动？')

  await new Promise((res, rej) => {
    ws = new WebSocket(page.webSocketDebuggerUrl)
    ws.onopen = res
    ws.onerror = (e) => rej(new Error('WebSocket 连接失败: ' + (e.message || e.type)))
    ws.onmessage = (ev) => {
      const m = JSON.parse(ev.data)
      if (m.id) {
        const p = pending.get(m.id)
        if (!p) return
        pending.delete(m.id)
        m.error ? p.rej(new Error(m.error.message)) : p.res(m.result)
        return
      }
      const { method, params } = m
      if (method === 'Runtime.consoleAPICalled' && params.type === 'error') {
        consoleErrors.push(params.args.map((a) => a.value ?? a.description ?? a.type).join(' '))
      } else if (method === 'Runtime.exceptionThrown') {
        exceptions.push(params.exceptionDetails.exception?.description || params.exceptionDetails.text)
      } else if (method === 'Log.entryAdded' && params.entry.level === 'error') {
        consoleErrors.push(`[log] ${params.entry.text}`)
      } else if (method === 'Network.responseReceived' && params.response.status >= 400) {
        badResponses.push(`${params.response.status} ${params.response.url}`)
      } else if (method === 'Network.loadingFailed') {
        failedRequests.push(`${params.errorText} ${params.type}`)
      } else if (method === 'Network.requestWillBeSent' && params.request.url.includes('/api/')) {
        apiCalls.push({
          url: params.request.url,
          method: params.request.method,
          headers: params.request.headers
        })
      }
    }
  })

  for (const domain of ['Page.enable', 'Runtime.enable', 'Log.enable', 'Network.enable']) {
    await send(domain)
  }

  await navigate(`${URL_}/login`, 2500)
  await evaluate('localStorage.clear(); sessionStorage.clear(); return 1;')
  await navigate(`${URL_}/login`, 3000)
  const loginShot = await screenshot('01-login.png')

  const filled = await evaluate(`
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    const inputs = [...document.querySelectorAll('input')].filter((el) => el.offsetParent !== null);
    const user = inputs.find((el) => el.type !== 'password');
    const pwd = inputs.find((el) => el.type === 'password');
    const fire = (el, value) => {
      setter.call(el, value);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    };
    fire(user, 'liwei');
    fire(pwd, '123456');
    return { username: user.value, passwordLength: pwd.value.length };
  `)

  const clicked = await evaluate(`
    const buttons = [...document.querySelectorAll('button')].filter((el) => el.offsetParent !== null);
    const button = buttons.find((el) => /登\\s*录/.test(el.textContent));
    if (!button) return false;
    button.click();
    return true;
  `)
  if (!clicked) throw new Error('未找到登录按钮')

  const reachedDashboard = await waitFor("location.pathname === '/dashboard'", 15000)
  if (!reachedDashboard) throw new Error('登录后未跳转到 /dashboard')

  await waitFor("document.querySelectorAll('.chart-card canvas').length >= 8", 20000)
  await sleep(3600)

  const dashboardState = await evaluate(`
    const text = (el) => (el?.innerText || '').replace(/\\s+/g, ' ').trim();
    return {
      title: document.title,
      path: location.pathname,
      kpis: [...document.querySelectorAll('.kpi-card')].map((el) => text(el)),
      charts: [...document.querySelectorAll('.chart-card')].map((el) => text(el.querySelector('h3'))),
      canvasCount: document.querySelectorAll('.chart-card canvas').length,
      hasToken: Boolean(localStorage.getItem('tlias_token')),
      user: text(document.querySelector('.user-meta'))
    };
  `)

  const dashboardShot = await screenshot('02-dashboard.png')
  const dashboardFullShot = await screenshot('03-dashboard-full.png', true)

  const menuClicked = await evaluate(`
    const items = [...document.querySelectorAll('.el-menu-item')].filter((el) => el.offsetParent !== null);
    const item = items.find((el) => el.textContent.trim() === '课程管理');
    if (!item) return false;
    item.click();
    return true;
  `)
  if (!menuClicked) throw new Error('未找到「课程管理」菜单')

  const reachedPlaceholder = await waitFor("Boolean(document.querySelector('.placeholder-card'))", 8000)
  if (!reachedPlaceholder) throw new Error('课程管理占位页未渲染')
  await sleep(500)
  const placeholderShot = await screenshot('04-placeholder.png')
  const placeholderText = await evaluate(
    "return (document.querySelector('.placeholder-card')?.innerText || '').replace(/\\s+/g, ' ').trim();"
  )

  await navigate(`${URL_}/this-path-does-not-exist`, 1800)
  const notFoundText = await evaluate(
    "return (document.body.innerText || '').replace(/\\s+/g, ' ').trim().slice(0, 120);"
  )
  const notFoundShot = await screenshot('05-not-found.png')

  await evaluate('localStorage.clear(); sessionStorage.clear(); return 1;')
  await navigate(`${URL_}/dashboard`, 2200)
  const guardPath = await evaluate('return location.pathname')

  const realErrors = consoleErrors.filter((e) => !/favicon/i.test(e))
  const realBad = badResponses.filter((b) => !/favicon/i.test(b))
  const protectedCalls = apiCalls.filter((c) =>
    ['/api/report/', '/api/students', '/api/clazzs', '/api/emps', '/api/courses'].some((part) =>
      c.url.includes(part)
    )
  )
  const tokenHeaderCalls = protectedCalls.filter((c) =>
    Object.keys(c.headers || {}).some((key) => key.toLowerCase() === 'tlias-token')
  )

  const ok =
    dashboardState.path === '/dashboard' &&
    dashboardState.canvasCount >= 8 &&
    dashboardState.hasToken &&
    guardPath === '/login' &&
    protectedCalls.length > 0 &&
    tokenHeaderCalls.length === protectedCalls.length &&
    exceptions.length === 0 &&
    realErrors.length === 0 &&
    realBad.length === 0

  console.log('=== 登录页 ===')
  console.log('截图:', loginShot)
  console.log('填写:', JSON.stringify(filled), '按钮点击:', clicked)
  console.log('\n=== 首页看板 ===')
  console.log('标题:', dashboardState.title)
  console.log('KPI:', dashboardState.kpis.join(' || '))
  console.log('图表:', dashboardState.charts.join(' / '))
  console.log('Canvas 数:', dashboardState.canvasCount)
  console.log('当前用户:', dashboardState.user)
  console.log('截图:', dashboardShot)
  console.log('整页截图:', dashboardFullShot)
  console.log('\n=== 占位页 ===')
  console.log('文本:', placeholderText.slice(0, 160))
  console.log('截图:', placeholderShot)
  console.log('\n=== 404 ===')
  console.log('文本:', notFoundText)
  console.log('截图:', notFoundShot)
  console.log('\n=== 路由守卫 ===')
  console.log('清 token 后访问 /dashboard →', guardPath)
  console.log('\n=== 网络 ===')
  console.log('受保护 API 请求:', protectedCalls.length, '带 tlias-token 的:', tokenHeaderCalls.length)
  console.log('未捕获异常:', exceptions.length, exceptions.slice(0, 3).join(' || '))
  console.log('控制台 error:', realErrors.length, realErrors.slice(0, 3).join(' || '))
  console.log('4xx/5xx:', realBad.length, realBad.slice(0, 5).join(' || '))
  console.log('请求失败:', failedRequests.length, failedRequests.slice(0, 3).join(' || '))
  console.log(`\n结论：${ok ? 'PASS' : 'FAIL'}`)

  ws.close()
  process.exit(ok ? 0 : 1)
}

main().catch((e) => {
  console.error('执行失败:', e.message)
  try {
    ws?.close()
  } catch {
    // ignore
  }
  process.exit(2)
})
