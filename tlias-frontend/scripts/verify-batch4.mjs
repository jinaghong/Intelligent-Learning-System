#!/usr/bin/env node
/**
 * 批次 4 验收：操作日志（登录日志/脱敏/写操作详情）+ AI 建议与出题（Mock）
 * 用法：node scripts/verify-batch4.mjs <URL> <截图目录> [CDP 地址]
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const URL_ = process.argv[2] || 'http://127.0.0.1:5173'
const OUT_DIR = process.argv[3] || './preview'
const CDP = process.argv[4] || 'http://127.0.0.1:9222'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const DIALOG_OPEN =
  "[...document.querySelectorAll('.el-dialog')].some((el) => el.getBoundingClientRect().width > 0)"
const DIALOG_CLOSED =
  "![...document.querySelectorAll('.el-dialog')].some((el) => el.getBoundingClientRect().width > 0)"
const MESSAGE_BOX_OPEN =
  "[...document.querySelectorAll('.el-message-box')].some((el) => el.getBoundingClientRect().width > 0)"
const NO_VISIBLE_MESSAGES =
  "![...document.querySelectorAll('.el-message')].some((el) => el.getBoundingClientRect().width > 0)"

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

async function fillTextarea(placeholder, value) {
  return evaluate(`
    const input = [...document.querySelectorAll('textarea')].find(
      (el) => el.placeholder === ${JSON.stringify(placeholder)} && el.getBoundingClientRect().width > 0
    );
    if (!input) return false;
    const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set;
    setter.call(input, ${JSON.stringify(value)});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  `)
}

async function fillByLabel(fieldLabel, value, scope = '.el-dialog') {
  return evaluate(`
    const root = ${
      scope === 'body'
        ? 'document'
        : "[...document.querySelectorAll('.el-dialog')].find((el) => el.getBoundingClientRect().width > 0)"
    };
    if (!root) return false;
    const item = [...root.querySelectorAll('.el-form-item')].find(
      (el) => el.getBoundingClientRect().width > 0 &&
        el.querySelector('.el-form-item__label')?.innerText.trim() === ${JSON.stringify(fieldLabel)}
    );
    const input = item?.querySelector('input, textarea');
    if (!input) return false;
    const proto = input instanceof HTMLTextAreaElement
      ? window.HTMLTextAreaElement.prototype
      : window.HTMLInputElement.prototype;
    const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    setter.call(input, ${JSON.stringify(String(value))});
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    input.blur();
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

async function clickDialogButton(text) {
  return evaluate(`
    const target = ${JSON.stringify(text.replace(/\s+/g, ''))};
    const dialog = [...document.querySelectorAll('.el-dialog')].find((el) => el.getBoundingClientRect().width > 0);
    if (!dialog) return false;
    const button = [...dialog.querySelectorAll('button')].find(
      (el) => el.getBoundingClientRect().width > 0 && el.textContent.replace(/\\s+/g, '') === target
    );
    if (!button) return false;
    button.click();
    return true;
  `)
}

async function clickMessageBoxButton(text) {
  return evaluate(`
    const target = ${JSON.stringify(text.replace(/\s+/g, ''))};
    const box = [...document.querySelectorAll('.el-message-box')].find((el) => el.getBoundingClientRect().width > 0);
    if (!box) return false;
    const button = [...box.querySelectorAll('button')].find(
      (el) => el.textContent.replace(/\\s+/g, '') === target
    );
    if (!button) return false;
    button.click();
    return true;
  `)
}

async function clickRowButton(rowText, buttonText) {
  return evaluate(`
    const preferred = ${JSON.stringify(rowText)};
    const target = ${JSON.stringify(buttonText.replace(/\s+/g, ''))};
    const rows = [...document.querySelectorAll('.el-table__body tr')].filter(
      (tr) => tr.getBoundingClientRect().width > 0 && tr.textContent.includes(preferred)
    );
    for (const row of rows) {
      const button = [...row.querySelectorAll('button')].find(
        (el) => el.textContent.replace(/\\s+/g, '') === target
      );
      if (button) {
        button.click();
        return true;
      }
    }
    return false;
  `)
}

async function clickFirstRowButton(buttonText) {
  return evaluate(`
    const target = ${JSON.stringify(buttonText.replace(/\s+/g, ''))};
    const row = [...document.querySelectorAll('.el-table__body tr')].find(
      (tr) => tr.getBoundingClientRect().width > 0
    );
    if (!row) return false;
    const button = [...row.querySelectorAll('button')].find(
      (el) => el.textContent.replace(/\\s+/g, '') === target
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

async function tableText() {
  return evaluate(`
    return [...document.querySelectorAll('.el-table__body')]
      .map((el) => el.innerText)
      .join(' ')
      .replace(/\\s+/g, ' ');
  `)
}

async function waitForTableText(text, exists = true, timeout = 10000) {
  const started = Date.now()
  while (Date.now() - started < timeout) {
    const value = await tableText()
    if (value.includes(text) === exists) return true
    await sleep(400)
  }
  return false
}

async function rowCount() {
  return evaluate(`
    return [...document.querySelectorAll('.el-table__body tr')].filter(
      (el) => el.getBoundingClientRect().width > 0
    ).length;
  `)
}

async function totalHint() {
  return evaluate(`
    const el = document.querySelector('.total-hint');
    return el ? el.innerText.replace(/\\s+/g, ' ').trim() : '';
  `)
}

async function waitForRowCount(count, timeout = 10000) {
  return waitFor(
    `[...document.querySelectorAll('.el-table__body tr')].filter(
      (el) => el.getBoundingClientRect().width > 0
    ).length === ${count}`,
    timeout
  )
}

async function closeDialog() {
  return evaluate(`
    const dialog = [...document.querySelectorAll('.el-dialog')].find(
      (el) => el.getBoundingClientRect().width > 0
    );
    const button = dialog?.querySelector('.el-dialog__headerbtn');
    if (!button) return false;
    button.click();
    return true;
  `)
}

async function dialogText() {
  return evaluate(`
    const dialog = [...document.querySelectorAll('.el-dialog')].find(
      (el) => el.getBoundingClientRect().width > 0
    );
    return dialog ? dialog.innerText.replace(/\\s+/g, ' ') : '';
  `)
}

async function login() {
  await navigate(`${URL_}/login`, 2500)
  await evaluate('localStorage.clear(); sessionStorage.clear(); return 1;')
  await navigate(`${URL_}/login`, 2500)
  await fillInput('请输入用户名', 'liwei')
  await fillInput('请输入密码', '123456')
  if (!(await clickButton('登录'))) throw new Error('未找到登录按钮')
  if (!(await waitFor("location.pathname === '/dashboard'", 15000))) {
    throw new Error('登录失败，未跳转到 /dashboard')
  }
  await sleep(3200)
}

async function openPage(menuText, title) {
  if (!(await clickMenu(menuText))) throw new Error(`未找到「${menuText}」菜单`)
  if (!(await waitFor(`document.querySelector('.page-header h2')?.innerText === ${JSON.stringify(title)}`, 8000))) {
    throw new Error(`${title}页未渲染`)
  }
  await sleep(700)
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

  await login()

  const suffix = Date.now().toString().slice(-6)
  const courseName = `P9日志课程${suffix}`
  const courseNo = `P9L${suffix}`
  const results = []

  // ================= 操作日志 =================
  await openPage('操作日志', '操作日志')
  await waitForRowCount(10, 10000)
  const logTotal = await totalHint()
  if (!/共 \d+ 条日志/.test(logTotal)) throw new Error(`日志总数提示不正确：${logTotal}`)
  const firstPageText = await tableText()
  if (!firstPageText.includes('LoginController.login')) {
    throw new Error('登录日志不在第一页首行')
  }
  if (!firstPageText.includes('登录接口')) throw new Error('登录日志操作人显示不正确')
  await screenshot('19-log-page.png')
  results.push(`日志列表：${logTotal}，登录日志位于第一页`)

  if (!(await clickFirstRowButton('详情'))) throw new Error('未找到日志详情按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('日志详情弹窗未打开')
  await sleep(600)
  const loginLogText = await dialogText()
  if (!loginLogText.includes('LoginController.login')) throw new Error('登录日志方法名不正确')
  if (!loginLogText.includes('******')) throw new Error('登录日志密码未脱敏')
  if (loginLogText.includes('123456')) throw new Error('登录日志泄漏了明文密码')
  await screenshot('20-log-detail.png')
  results.push('登录日志详情：密码已脱敏为 ******')
  if (!(await closeDialog())) throw new Error('未找到日志详情关闭按钮')
  if (!(await waitFor(DIALOG_CLOSED, 5000))) throw new Error('日志详情弹窗未关闭')

  // 造一条写操作日志：新增课程
  await openPage('课程管理', '课程管理')
  if (!(await clickButton('新增课程'))) throw new Error('未找到新增课程按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('新增课程弹窗未打开')
  await sleep(400)
  if (!(await fillInput('请输入课程名称', courseName))) throw new Error('未找到课程名称输入框')
  if (!(await fillInput('如 C009', courseNo))) throw new Error('未找到课程编号输入框')
  if (!(await fillByLabel('课程价格', '100'))) throw new Error('未找到课程价格输入框')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到课程保存按钮')
  if (!(await waitForTableText(courseName, true, 10000))) throw new Error('日志测试课程未新增成功')
  await waitFor(NO_VISIBLE_MESSAGES, 6000)

  await openPage('操作日志', '操作日志')
  if (!(await waitForTableText('CourseController.save', true, 10000))) {
    throw new Error('课程新增日志未出现在第一页')
  }
  if (!(await clickRowButton('CourseController.save', '详情'))) throw new Error('未找到写操作日志详情按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('写操作日志详情未打开')
  await sleep(600)
  const saveLogText = await dialogText()
  if (!saveLogText.includes(courseName)) throw new Error('写操作日志参数未包含课程名称')
  if (!saveLogText.includes('李伟')) throw new Error('写操作日志操作人不正确')
  if (!saveLogText.includes('成功')) throw new Error('写操作日志状态不正确')
  results.push('写操作日志：CourseController.save / 李伟 / 参数含课程名')
  if (!(await closeDialog())) throw new Error('未找到日志详情关闭按钮')
  await waitFor(DIALOG_CLOSED, 5000)

  // 清理测试课程
  await openPage('课程管理', '课程管理')
  if (!(await clickRowButton(courseName, '删除'))) throw new Error('未找到测试课程删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('课程删除确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  if (!(await waitForTableText(courseName, false, 10000))) throw new Error('测试课程未删除')
  await waitFor(NO_VISIBLE_MESSAGES, 6000)
  results.push('日志测试课程已清理')

  // ================= AI 助手 =================
  await openPage('AI 助手', 'AI 助手')
  await waitFor("document.querySelector('.ai-card .el-select') !== null", 8000)
  await sleep(1200)

  if (!(await clickButton('生成建议'))) throw new Error('未找到生成建议按钮')
  const adviceReady = await waitFor(
    "document.querySelector('.advice-text')?.innerText.includes('[MOCK]')",
    12000
  )
  if (!adviceReady) throw new Error('学习建议未返回 Mock 内容')
  const modelTag = await evaluate(`
    return document.querySelector('.result-meta .el-tag')?.innerText.replace(/\\s+/g, ' ').trim() || '';
  `)
  if (!modelTag.includes('mock')) throw new Error(`建议模型标签不正确：${modelTag}`)
  await screenshot('21-ai-advice.png', true)
  results.push(`AI 学习建议：${modelTag}`)

  if (!(await clickButton('生成题目'))) throw new Error('未找到生成题目按钮')
  const questionsReady = await waitFor(
    "document.querySelectorAll('.question-card').length === 3",
    15000
  )
  if (!questionsReady) throw new Error('智能出题未返回 3 道题')
  const optionCounts = await evaluate(`
    return [...document.querySelectorAll('.question-card')].map(
      (card) => card.querySelectorAll('.q-options li').length
    );
  `)
  if (optionCounts.length !== 3 || optionCounts.some((count) => count !== 4)) {
    throw new Error(`题目选项数量不正确：${optionCounts.join(',')}`)
  }
  if (!(await clickButton('查看答案与解析'))) throw new Error('未找到查看答案解析按钮')
  const analysisReady = await waitFor("Boolean(document.querySelector('.q-analysis'))", 5000)
  if (!analysisReady) throw new Error('答案与解析未展开')
  const analysisText = await evaluate(
    "return document.querySelector('.q-analysis')?.innerText || '';"
  )
  if (!analysisText.includes('解析')) throw new Error('解析内容不正确')
  await screenshot('22-ai-questions.png', true)
  results.push('AI 智能出题：3 道题 × 4 选项，答案解析可展开')

  await waitFor(NO_VISIBLE_MESSAGES, 6000)

  const realErrors = consoleErrors.filter((e) => !/favicon/i.test(e))
  const realBad = badResponses.filter((b) => !/favicon/i.test(b))
  const ok =
    exceptions.length === 0 &&
    realErrors.length === 0 &&
    realBad.length === 0 &&
    failedRequests.length === 0

  console.log('\n=== 批次 4 验收 ===')
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
