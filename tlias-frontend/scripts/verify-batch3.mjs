#!/usr/bin/env node
/**
 * 批次 3 验收：课程增删改 + 成绩删除守卫；班级分页/增删改 + 学员删除守卫；
 * 学员增删改/详情/违纪累加/批量删除
 * 用法：node scripts/verify-batch3.mjs <URL> <截图目录> [CDP 地址]
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
const DRAWER_OPEN =
  "[...document.querySelectorAll('.el-drawer')].some((el) => el.getBoundingClientRect().width > 0)"
const DRAWER_CLOSED =
  "![...document.querySelectorAll('.el-drawer')].some((el) => el.getBoundingClientRect().width > 0)"
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

async function mouseClick(rect) {
  await send('Input.dispatchMouseEvent', {
    type: 'mousePressed',
    x: rect.x,
    y: rect.y,
    button: 'left',
    clickCount: 1
  })
  await send('Input.dispatchMouseEvent', {
    type: 'mouseReleased',
    x: rect.x,
    y: rect.y,
    button: 'left',
    clickCount: 1
  })
}

async function pickDate(placeholder, scope = 'body') {
  const rootExpr =
    scope === '.el-dialog'
      ? "[...document.querySelectorAll('.el-dialog')].find((el) => el.getBoundingClientRect().width > 0)"
      : 'document'
  const rect = await evaluate(`
    const root = ${rootExpr};
    if (!root) return null;
    const input = [...root.querySelectorAll('input')].find(
      (el) => el.placeholder === ${JSON.stringify(placeholder)} && el.getBoundingClientRect().width > 0
    );
    if (!input) return null;
    const r = input.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  `)
  if (!rect) return false

  await mouseClick(rect)
  await sleep(700)

  const cell = await evaluate(`
    const panel = [...document.querySelectorAll('.el-picker-panel')].find(
      (el) => el.getBoundingClientRect().width > 0
    );
    const cell =
      panel?.querySelector('td.today .el-date-table-cell') ||
      panel?.querySelector('td.available .el-date-table-cell');
    if (!cell) return null;
    const r = cell.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  `)
  if (!cell) return false

  await mouseClick(cell)
  await sleep(500)
  return true
}

async function selectOptionByLabel(fieldLabel, optionLabel, scope = '.el-dialog') {
  const rootExpr =
    scope === 'body'
      ? 'document'
      : "[...document.querySelectorAll('.el-dialog')].find((el) => el.getBoundingClientRect().width > 0)"
  const rect = await evaluate(`
    const root = ${rootExpr};
    if (!root) return null;
    const item = [...root.querySelectorAll('.el-form-item')].find(
      (el) => el.getBoundingClientRect().width > 0 &&
        el.querySelector('.el-form-item__label')?.innerText.trim() === ${JSON.stringify(fieldLabel)}
    );
    const wrap = item?.querySelector('.el-select');
    if (!wrap) return null;
    const r = wrap.getBoundingClientRect();
    return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
  `)
  if (!rect) return false

  await mouseClick(rect)
  await sleep(500)

  const option = await evaluate(`
    const poppers = [...document.querySelectorAll('.el-select__popper')].filter(
      (el) => el.getBoundingClientRect().width > 0
    );
    for (const popper of poppers) {
      const item = [...popper.querySelectorAll('.el-select-dropdown__item')].find(
        (el) => el.innerText.trim() === ${JSON.stringify(optionLabel)}
      );
      if (item) {
        const r = item.getBoundingClientRect();
        return { x: r.x + r.width / 2, y: r.y + r.height / 2 };
      }
    }
    return null;
  `)
  if (!option) return false

  await mouseClick(option)
  await sleep(400)
  return true
}

async function closeDrawer() {
  return evaluate(`
    const drawer = [...document.querySelectorAll('.el-drawer')].find(
      (el) => el.getBoundingClientRect().width > 0
    );
    const button = drawer?.querySelector('.el-drawer__close-btn');
    if (!button) return false;
    button.click();
    return true;
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
  await waitFor("document.querySelectorAll('.el-table__body tr').length > 0", 8000)
  await sleep(600)
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
  const courseName = `P9测试课程${suffix}`
  const courseName2 = `${courseName}改`
  const courseNo = `P9${suffix}`
  const clazzName = `P9测试班${suffix}`
  const clazzName2 = `${clazzName}改`
  const studentName = `P9测试学员${suffix}`
  const studentName2 = `${studentName}改`
  const studentNo = `P9S${suffix}`
  const batchPrefix = `P9批量${suffix}`
  const batchA = `${batchPrefix}甲`
  const batchB = `${batchPrefix}乙`
  const results = []

  // ================= 课程 =================
  await openPage('课程管理', '课程管理')
  const courseRowsBefore = await rowCount()
  if (courseRowsBefore !== 8) throw new Error(`课程初始行数应为 8，实际 ${courseRowsBefore}`)
  await screenshot('11-course.png')
  results.push(`课程列表初始 ${courseRowsBefore} 行`)

  if (!(await fillInput('按名称 / 编号筛选', 'C001'))) throw new Error('未找到课程筛选框')
  if (!(await waitForRowCount(1, 5000))) throw new Error('课程编号筛选未生效')
  if (!(await tableText()).includes('Java 企业级开发')) throw new Error('课程筛选结果不正确')
  await fillInput('按名称 / 编号筛选', '')
  if (!(await waitForRowCount(8, 5000))) throw new Error('课程筛选重置失败')
  results.push('课程客户端筛选：C001 → 1 行')

  if (!(await clickButton('新增课程'))) throw new Error('未找到新增课程按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('新增课程弹窗未打开')
  await sleep(400)
  if (!(await fillInput('请输入课程名称', courseName))) throw new Error('未找到课程名称输入框')
  if (!(await fillInput('如 C009', courseNo))) throw new Error('未找到课程编号输入框')
  if (!(await fillByLabel('课程价格', '12345.67'))) throw new Error('未找到课程价格输入框')
  if (!(await fillTextarea('课程内容、面向人群、技术栈等', 'P9 批次 3 验收课程'))) {
    throw new Error('未找到课程简介输入框')
  }
  await sleep(500)
  await screenshot('12-course-dialog.png')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到课程保存按钮')
  if (!(await waitForTableText(courseName, true, 10000))) {
    const errors = await evaluate(`
      return [...document.querySelectorAll('.el-form-item__error')].map((el) => el.innerText).filter(Boolean);
    `)
    throw new Error('课程新增后未出现在表格；表单错误：' + errors.join(' | '))
  }
  if (!(await tableText()).includes('12345.67')) throw new Error('课程金额未按两位小数展示')
  results.push('课程新增成功，金额显示 12345.67')

  if (!(await clickRowButton(courseName, '编辑'))) throw new Error('未找到课程编辑按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('编辑课程弹窗未打开')
  await sleep(600)
  if (!(await fillInput('请输入课程名称', courseName2))) throw new Error('未找到编辑课程名称输入框')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到课程编辑保存按钮')
  if (!(await waitForTableText(courseName2, true, 10000))) throw new Error('课程编辑后未更新')
  results.push('课程编辑成功')

  if (!(await clickRowButton(courseName2, '删除'))) throw new Error('未找到课程删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('课程删除确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  if (!(await waitForTableText(courseName2, false, 10000))) throw new Error('课程删除后仍在表格')
  results.push('课程删除成功')

  if (!(await clickRowButton('Java 企业级开发', '删除'))) throw new Error('未找到受保护课程删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('课程删除守卫确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  const courseGuard = await waitFor(
    "document.body.innerText.includes('该课程下已有成绩记录，不能删除')",
    6000
  )
  if (!courseGuard || !(await tableText()).includes('Java 企业级开发')) {
    throw new Error('课程删除守卫未按预期工作')
  }
  results.push('删除守卫：有成绩的课程被拒绝删除')
  await waitFor(NO_VISIBLE_MESSAGES, 8000)

  // ================= 班级 =================
  await openPage('班级管理', '班级管理')
  const clazzTotalBefore = await totalHint()
  const clazzRowsBefore = await rowCount()
  if (clazzRowsBefore !== 6) throw new Error(`班级初始行数应为 6，实际 ${clazzRowsBefore}`)
  if (!clazzTotalBefore.includes('6')) throw new Error(`班级总数提示不正确：${clazzTotalBefore}`)
  await screenshot('13-clazz.png')
  results.push(`班级列表初始 ${clazzRowsBefore} 行 / ${clazzTotalBefore}`)

  if (!(await clickButton('新增班级'))) throw new Error('未找到新增班级按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('新增班级弹窗未打开')
  await sleep(400)
  if (!(await fillInput('如 JavaEE 就业 3 期', clazzName))) throw new Error('未找到班级名称输入框')
  if (!(await fillInput('如 A103', 'D999'))) throw new Error('未找到教室输入框')
  if (!(await pickDate('请选择开班日期'))) throw new Error('开班日期选择失败')
  if (!(await pickDate('请选择结班日期'))) throw new Error('结班日期选择失败')
  if (!(await selectOptionByLabel('班主任', '王芳 · 班主任'))) throw new Error('班主任选择失败')
  if (!(await selectOptionByLabel('学科', 'Java'))) throw new Error('学科选择失败')
  await sleep(400)
  await screenshot('14-clazz-dialog.png')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到班级保存按钮')
  if (!(await waitForTableText(clazzName, true, 10000))) {
    const errors = await evaluate(`
      return [...document.querySelectorAll('.el-form-item__error')].map((el) => el.innerText).filter(Boolean);
    `)
    throw new Error('班级新增后未出现在表格；表单错误：' + errors.join(' | '))
  }
  if (!(await tableText()).includes('王芳')) throw new Error('班级列表未显示班主任姓名')
  results.push('班级新增成功（日期/班主任/学科）')

  if (!(await clickRowButton(clazzName, '编辑'))) throw new Error('未找到班级编辑按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('编辑班级弹窗未打开')
  await sleep(700)
  if (!(await fillInput('如 JavaEE 就业 3 期', clazzName2))) throw new Error('未找到编辑班级名称输入框')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到班级编辑保存按钮')
  if (!(await waitForTableText(clazzName2, true, 10000))) throw new Error('班级编辑后未更新')
  results.push('班级编辑成功')

  if (!(await clickRowButton(clazzName2, '删除'))) throw new Error('未找到班级删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('班级删除确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  if (!(await waitForTableText(clazzName2, false, 10000))) throw new Error('班级删除后仍在表格')
  results.push('班级删除成功')

  if (!(await clickButton('重置'))) throw new Error('未找到班级重置按钮')
  if (!(await waitForRowCount(6, 10000))) throw new Error('班级重置后行数不正确')

  if (!(await clickRowButton('JavaEE 就业 1 期', '删除'))) throw new Error('未找到受保护班级删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('班级删除守卫确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  const clazzGuard = await waitFor(
    "document.body.innerText.includes('该班级下还有学员，不能删除')",
    6000
  )
  if (!clazzGuard || !(await tableText()).includes('JavaEE 就业 1 期')) {
    throw new Error('班级删除守卫未按预期工作')
  }
  results.push('删除守卫：有学员的班级被拒绝删除')
  await waitFor(NO_VISIBLE_MESSAGES, 8000)

  // ================= 学员 =================
  await openPage('学员管理', '学员管理')
  const studentTotalBefore = await totalHint()
  const studentRowsBefore = await rowCount()
  if (studentRowsBefore !== 10) throw new Error(`学员首页应为 10 行，实际 ${studentRowsBefore}`)
  if (!studentTotalBefore.includes('48')) throw new Error(`学员总数提示不正确：${studentTotalBefore}`)
  await screenshot('15-student.png')
  results.push(`学员列表：${studentTotalBefore} / 首页 ${studentRowsBefore} 行`)

  if (!(await selectOptionByLabel('班级', 'JavaEE 就业 1 期', 'body'))) {
    throw new Error('班级筛选选择失败')
  }
  if (!(await clickButton('查询'))) throw new Error('未找到查询按钮')
  if (!(await waitForRowCount(8, 10000))) throw new Error('按班级筛选后应为 8 行')
  results.push('学员筛选：JavaEE 就业 1 期 → 8 行')
  if (!(await clickButton('重置'))) throw new Error('未找到重置按钮')
  if (!(await waitForRowCount(10, 10000))) throw new Error('学员重置后行数不正确')

  if (!(await clickRowButton('赵梦琪', '详情'))) throw new Error('未找到学员详情按钮')
  if (!(await waitFor(DRAWER_OPEN, 8000))) throw new Error('学员详情抽屉未打开')
  await sleep(800)
  const detailText = await evaluate(`
    const drawer = [...document.querySelectorAll('.el-drawer')].find((el) => el.getBoundingClientRect().width > 0);
    return drawer ? drawer.innerText.replace(/\\s+/g, ' ') : '';
  `)
  const violationRows = await evaluate(`
    return [...document.querySelectorAll('.el-drawer .el-table__body tr')].filter(
      (el) => el.getBoundingClientRect().width > 0
    ).length;
  `)
  if (!detailText.includes('3 次') || !detailText.includes('11 分')) {
    throw new Error('学员详情汇总字段不正确：' + detailText.slice(0, 160))
  }
  if (violationRows !== 3) throw new Error(`赵梦琪应有 3 条违纪明细，实际 ${violationRows}`)
  await screenshot('16-student-detail.png')
  results.push(`学员详情：3 条违纪明细 / 3 次 / 11 分`)
  if (!(await closeDrawer())) throw new Error('未找到详情关闭按钮')
  if (!(await waitFor(DRAWER_CLOSED, 5000))) throw new Error('详情抽屉未关闭')

  if (!(await clickButton('新增学员'))) throw new Error('未找到新增学员按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('新增学员弹窗未打开')
  await sleep(500)
  if (!(await fillInput('请输入姓名', studentName))) throw new Error('未找到学员姓名输入框')
  if (!(await fillInput('如 202601009', studentNo))) throw new Error('未找到学号输入框')
  if (!(await selectOptionByLabel('性别', '女'))) throw new Error('性别选择失败')
  if (!(await selectOptionByLabel('学历', '本科'))) throw new Error('学历选择失败')
  if (!(await selectOptionByLabel('班级', 'JavaEE 就业 1 期'))) throw new Error('班级选择失败')
  if (!(await fillInput('11 位手机号', '13700000001'))) throw new Error('未找到手机号输入框')
  if (!(await fillInput('18 位身份证号', '510104200001101099'))) throw new Error('未找到身份证输入框')
  if (!(await pickDate('请选择毕业时间'))) throw new Error('毕业时间选择失败')
  if (!(await fillTextarea('请输入家庭住址', '四川省成都市 P9 测试地址'))) {
    throw new Error('未找到家庭住址输入框')
  }
  await sleep(400)
  await screenshot('17-student-dialog.png')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到学员保存按钮')
  if (!(await waitForTableText(studentName, true, 12000))) {
    const errors = await evaluate(`
      return [...document.querySelectorAll('.el-form-item__error')].map((el) => el.innerText).filter(Boolean);
    `)
    throw new Error('学员新增后未出现在表格；表单错误：' + errors.join(' | '))
  }
  results.push('学员新增成功（性别/学历/班级/毕业时间/住址）')

  if (!(await clickRowButton(studentName, '违纪'))) throw new Error('未找到违纪按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('违纪弹窗未打开')
  await sleep(500)
  if (!(await fillByLabel('扣分', '5'))) throw new Error('未找到扣分输入框')
  if (!(await fillTextarea('如：晚自习无故缺席', 'P9 验收测试违纪'))) {
    throw new Error('未找到违纪事由输入框')
  }
  if (!(await clickDialogButton('记违纪'))) throw new Error('未找到记违纪按钮')
  if (!(await waitForTableText('1 次 / 5 分', true, 10000))) throw new Error('违纪后列表汇总未更新')
  results.push('违纪处理：1 次 / 5 分')

  if (!(await clickRowButton(studentName, '详情'))) throw new Error('未找到违纪后详情按钮')
  if (!(await waitFor(DRAWER_OPEN, 8000))) throw new Error('违纪后详情抽屉未打开')
  await sleep(800)
  const violationText = await evaluate(`
    const drawer = [...document.querySelectorAll('.el-drawer')].find((el) => el.getBoundingClientRect().width > 0);
    return drawer ? drawer.innerText.replace(/\\s+/g, ' ') : '';
  `)
  if (!violationText.includes('P9 验收测试违纪') || !violationText.includes('1 条记录')) {
    throw new Error('违纪明细未回显：' + violationText.slice(0, 160))
  }
  await screenshot('18-student-violation.png')
  results.push('违纪明细回显成功')
  if (!(await closeDrawer())) throw new Error('未找到详情关闭按钮')
  if (!(await waitFor(DRAWER_CLOSED, 5000))) throw new Error('详情抽屉未关闭')

  if (!(await clickRowButton(studentName, '编辑'))) throw new Error('未找到学员编辑按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('编辑学员弹窗未打开')
  await sleep(800)
  if (!(await fillInput('请输入姓名', studentName2))) throw new Error('未找到编辑学员姓名输入框')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到学员编辑保存按钮')
  if (!(await waitForTableText(studentName2, true, 10000))) throw new Error('学员编辑后未更新')
  results.push('学员编辑成功')

  if (!(await clickRowButton(studentName2, '删除'))) throw new Error('未找到学员删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('学员删除确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  if (!(await waitForRowCount(0, 10000))) throw new Error('学员删除后仍有数据')
  results.push('学员删除成功（违纪明细级联清理）')

  // 批量删除：造两个同前缀学员
  for (const name of [batchA, batchB]) {
    if (!(await clickButton('新增学员'))) throw new Error('未找到新增学员按钮')
    if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('批量测试新增弹窗未打开')
    await sleep(400)
    if (!(await fillInput('请输入姓名', name))) throw new Error('批量测试姓名输入失败')
    if (!(await fillInput('如 202601009', `${studentNo}${name === batchA ? 'A' : 'B'}`))) {
      throw new Error('批量测试学号输入失败')
    }
    if (!(await selectOptionByLabel('性别', '男'))) throw new Error('批量测试性别选择失败')
    if (!(await selectOptionByLabel('学历', '专科'))) throw new Error('批量测试学历选择失败')
    if (!(await clickDialogButton('保存'))) throw new Error('批量测试保存失败')
    if (!(await waitForTableText(name, true, 10000))) throw new Error(`批量测试学员 ${name} 未新增成功`)
    await waitFor(NO_VISIBLE_MESSAGES, 6000)
  }

  if (!(await fillInput('支持模糊查询', batchPrefix))) throw new Error('未找到姓名筛选框')
  if (!(await clickButton('查询'))) throw new Error('未找到查询按钮')
  if (!(await waitForRowCount(2, 10000))) throw new Error('批量筛选后应为 2 行')
  const checkAll = await evaluate(`
    const box = document.querySelector('.el-table__header .el-checkbox__original');
    if (!box) return false;
    box.click();
    return true;
  `)
  if (!checkAll) throw new Error('未找到全选复选框')
  await sleep(400)
  if (!(await clickButton('批量删除'))) throw new Error('未找到批量删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('批量删除确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  if (!(await waitForRowCount(0, 10000))) throw new Error('批量删除后仍有数据')
  results.push('学员批量删除成功（2 位）')

  if (!(await clickButton('重置'))) throw new Error('未找到重置按钮')
  if (!(await waitForRowCount(10, 10000))) throw new Error('学员重置后首页行数不正确')
  const studentTotalAfter = await totalHint()
  if (!studentTotalAfter.includes('48')) throw new Error(`清理后学员总数应为 48：${studentTotalAfter}`)
  results.push(`清理后学员总数：${studentTotalAfter}`)

  await waitFor(NO_VISIBLE_MESSAGES, 8000)

  const realErrors = consoleErrors.filter((e) => !/favicon/i.test(e))
  const realBad = badResponses.filter((b) => !/favicon/i.test(b))
  const ok =
    exceptions.length === 0 &&
    realErrors.length === 0 &&
    realBad.length === 0 &&
    failedRequests.length === 0

  console.log('\n=== 批次 3 验收 ===')
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
