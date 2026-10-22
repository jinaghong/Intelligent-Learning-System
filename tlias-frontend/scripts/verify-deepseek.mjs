#!/usr/bin/env node
/**
 * 真 key 验证：AI 页面经真实 DeepSeek profile 生成学习建议与题目
 * 前置：后端以 dev,deepseek 启动；前端 dev/preview 任选；Chrome 9222
 * 用法：node scripts/verify-deepseek.mjs <URL> <截图目录> [CDP 地址]
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

async function waitFor(expression, timeout = 10000, interval = 350) {
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
  writeFileSync(resolve(OUT_DIR, name), Buffer.from(img.data, 'base64'))
}

async function fillInput(placeholder, value) {
  return evaluate(`
    const input = [...document.querySelectorAll('input')].find(
      (el) => el.placeholder === ${JSON.stringify(placeholder)} && el.getBoundingClientRect().width > 0
    );
    if (!input) return false;
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
    setter.call(input, ${JSON.stringify(value)});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  `)
}

async function clickButton(text) {
  return evaluate(`
    const target = ${JSON.stringify(text.replace(/\s+/g, ''))};
    const button = [...document.querySelectorAll('button')].find(
      (el) => el.getBoundingClientRect().width > 0 && el.textContent.replace(/\\s+/g, '') === target
    );
    if (!button) return false;
    button.click();
    return true;
  `)
}

async function clickMenu(text) {
  return evaluate(`
    const target = ${JSON.stringify(text)};
    const item = [...document.querySelectorAll('.el-menu-item')].find(
      (el) => el.getBoundingClientRect().width > 0 && el.textContent.trim() === target
    );
    if (!item) return false;
    item.click();
    return true;
  `)
}

async function login() {
  await navigate(`${URL_}/login`, 3000)
  await evaluate('localStorage.clear(); sessionStorage.clear(); return 1;')
  await navigate(`${URL_}/login`, 3000)
  const inputCount = await waitFor(
    "[...document.querySelectorAll('input')].filter((el) => el.getBoundingClientRect().width > 0).length >= 2",
    8000
  )
  if (!inputCount) {
    const state = await evaluate(`
      return JSON.stringify({
        path: location.pathname,
        bodyLength: document.body.innerText.length,
        inputs: document.querySelectorAll('input').length,
        app: !!document.querySelector('#app[data-v-app]'),
        appHtml: document.getElementById('app')?.innerHTML.slice(0, 300) || '',
        scripts: [...document.scripts].map((s) => s.src).filter(Boolean),
        resources: performance.getEntriesByType('resource').slice(-8).map((e) => ({
          name: e.name.split('/').slice(-2).join('/'),
          duration: Math.round(e.duration),
          size: e.transferSize
        }))
      });
    `)
    throw new Error(
      `登录页未渲染：${state}\n控制台: ${consoleErrors.slice(-5).join(' || ')}\n异常: ${exceptions.slice(-3).join(' || ')}\n失败请求: ${failedRequests.slice(-5).join(' || ')}`
    )
  }
  if (!(await fillInput('请输入用户名', 'liwei'))) throw new Error('未找到用户名输入框')
  if (!(await fillInput('请输入密码', '123456'))) throw new Error('未找到密码输入框')
  if (!(await clickButton('登录'))) throw new Error('未找到登录按钮')
  if (!(await waitFor("location.pathname === '/dashboard'", 20000))) {
    const state = await evaluate(`
      return JSON.stringify({
        path: location.pathname,
        body: document.body.innerText.slice(0, 200).replace(/\\s+/g, ' ')
      });
    `)
    throw new Error(`登录失败未跳转：${state}`)
  }
  await sleep(3200)
}

async function openPage(menuText, title) {
  if (!(await clickMenu(menuText))) throw new Error(`未找到「${menuText}」菜单`)
  if (!(await waitFor(`document.querySelector('.page-header h2')?.innerText === ${JSON.stringify(title)}`, 8000))) {
    throw new Error(`${title}页未渲染`)
  }
  await sleep(800)
}

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })

  const page = (await (await fetch(`${CDP}/json/list`)).json()).find((t) => t.type === 'page')
  if (!page) throw new Error('未找到 page target')

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
        failedRequests.push(params.errorText)
      }
    }
  })

  for (const domain of ['Page.enable', 'Runtime.enable', 'Log.enable', 'Network.enable', 'DOM.enable']) {
    await send(domain)
  }
  await send('Network.clearBrowserCache')
  await send('Network.setCacheDisabled', { cacheDisabled: true })

  await login()

  // 登录前的旧页面可能残留临时 profile 的缓存错误，只统计登录后的 AI 流程
  consoleErrors.length = 0
  exceptions.length = 0
  badResponses.length = 0
  failedRequests.length = 0

  await openPage('AI 助手', 'AI 助手')
  await waitFor("document.querySelector('.ai-card .el-select') !== null", 8000)
  await sleep(1500)

  const results = []

  // ============ 学习建议（真实模型） ============
  if (!(await clickButton('生成建议'))) throw new Error('未找到生成建议按钮')
  const adviceReady = await waitFor(
    "document.querySelector('.advice-text')?.innerText.trim().length > 0 || document.querySelector('.el-message--error') !== null",
    120000,
    800
  )
  if (!adviceReady) throw new Error('学习建议 120 秒内未返回')
  const adviceInfo = await evaluate(`
    return JSON.stringify({
      model: document.querySelector('.result-meta .el-tag')?.innerText.replace(/\\s+/g, ' ').trim() || '',
      text: document.querySelector('.advice-text')?.innerText || '',
      error: document.querySelector('.el-message--error')?.innerText || ''
    });
  `)
  const advice = JSON.parse(adviceInfo)
  if (advice.error) throw new Error(`学习建议请求失败：${advice.error}`)
  if (!advice.model.includes('deepseek')) throw new Error(`建议模型不是 deepseek：${advice.model}`)
  if (advice.text.includes('[MOCK]')) throw new Error('学习建议仍为 Mock 内容')
  if (advice.text.length < 40) throw new Error(`学习建议内容过短：${advice.text.length} 字`)
  await screenshot('23-ai-deepseek.png')
  results.push(`学习建议：${advice.model}，正文 ${advice.text.length} 字，无 [MOCK] 标记`)

  // ============ 智能出题（真实模型） ============
  if (!(await clickButton('生成题目'))) throw new Error('未找到生成题目按钮')
  const questionsReady = await waitFor(
    "document.querySelectorAll('.question-card').length === 3 || document.querySelector('.el-message--error') !== null",
    150000,
    800
  )
  if (!questionsReady) throw new Error('智能出题 150 秒内未返回')
  const questionsInfo = await evaluate(`
    return JSON.stringify({
      error: document.querySelector('.el-message--error')?.innerText || '',
      cards: [...document.querySelectorAll('.question-card')].map((card) => ({
        stem: card.querySelector('.q-stem')?.innerText.replace(/\\s+/g, ' ').trim() || '',
        options: card.querySelectorAll('.q-options li').length
      }))
    });
  `)
  const questions = JSON.parse(questionsInfo)
  if (questions.error) throw new Error(`智能出题请求失败：${questions.error}`)
  if (questions.cards.length !== 3) throw new Error(`题目数量不正确：${questions.cards.length}`)
  if (questions.cards.some((q) => q.options !== 4)) {
    throw new Error(`选项数量不正确：${questions.cards.map((q) => q.options).join(',')}`)
  }
  if (questions.cards.some((q) => q.stem.length < 8)) throw new Error('存在题干过短的题目')
  if (questions.cards.some((q) => q.stem.includes('[MOCK]'))) throw new Error('题目仍为 Mock 内容')

  if (!(await clickButton('查看答案与解析'))) throw new Error('未找到查看答案解析按钮')
  if (!(await waitFor("Boolean(document.querySelector('.q-analysis'))", 5000))) {
    throw new Error('答案与解析未展开')
  }
  const answerText = await evaluate(
    "return document.querySelector('.q-analysis')?.innerText || '';"
  )
  if (answerText.trim().length < 10) throw new Error('解析内容过短')
  await screenshot('24-ai-deepseek-questions.png', true)
  results.push(`智能出题：3 题 × 4 选项，题干均来自真实模型，答案解析 ${answerText.trim().length} 字`)

  const realErrors = consoleErrors.filter((e) => !/favicon/i.test(e))
  const realBad = badResponses.filter((b) => !/favicon/i.test(b))
  const ok =
    exceptions.length === 0 &&
    realErrors.length === 0 &&
    realBad.length === 0 &&
    failedRequests.length === 0

  console.log('\n=== 真 key AI 页面验收 ===')
  results.forEach((line) => console.log('✓', line))
  console.log('\n未捕获异常:', exceptions.length, exceptions.slice(0, 3).join(' || '))
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
