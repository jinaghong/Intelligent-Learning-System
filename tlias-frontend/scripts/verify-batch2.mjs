#!/usr/bin/env node
/**
 * 批次 2 验收：部门增删改 + 删除守卫；员工筛选/分页/增删改/工作经历回显/Excel 导出
 * 用法：node scripts/verify-batch2.mjs <URL> <截图目录> [CDP 地址]
 */
import { existsSync, mkdirSync, readdirSync, unlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const URL_ = process.argv[2] || 'http://127.0.0.1:5173'
const OUT_DIR = process.argv[3] || './preview'
const CDP = process.argv[4] || 'http://127.0.0.1:9222'
const DOWNLOAD_DIR = join(tmpdir(), 'tlias-downloads')
const AVATAR_PATH = resolve(process.cwd(), 'scripts', 'fixtures', 'avatar.png')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const DIALOG_OPEN = "[...document.querySelectorAll('.el-dialog')].some((el) => el.getBoundingClientRect().width > 0)"
const DIALOG_CLOSED = "![...document.querySelectorAll('.el-dialog')].some((el) => el.getBoundingClientRect().width > 0)"
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
let exportResponse = null

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
  const file = resolve(OUT_DIR, name)
  writeFileSync(file, Buffer.from(img.data, 'base64'))
  return file
}

async function fillInput(placeholder, value) {
  return evaluate(`
    const input = [...document.querySelectorAll('input')].find((el) => el.placeholder === ${JSON.stringify(
      placeholder
    )} && el.getBoundingClientRect().width > 0);
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
    const buttons = [...document.querySelectorAll('button')].filter((el) => el.getBoundingClientRect().width > 0);
    const button = buttons.find((el) => el.textContent.replace(/\\s+/g, '') === target);
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

  await send('Input.dispatchMouseEvent', {
    type: 'mousePressed',
    x: cell.x,
    y: cell.y,
    button: 'left',
    clickCount: 1
  })
  await send('Input.dispatchMouseEvent', {
    type: 'mouseReleased',
    x: cell.x,
    y: cell.y,
    button: 'left',
    clickCount: 1
  })
  await sleep(500)
  return true
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

async function main() {
  mkdirSync(OUT_DIR, { recursive: true })
  mkdirSync(DOWNLOAD_DIR, { recursive: true })

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
      } else if (
        method === 'Network.responseReceived' &&
        params.response.url.includes('/api/emps/export')
      ) {
        exportResponse = {
          status: params.response.status,
          mimeType: params.response.mimeType,
          headers: params.response.headers
        }
      }
    }
  })

  for (const domain of ['Page.enable', 'Runtime.enable', 'Log.enable', 'Network.enable', 'DOM.enable']) {
    await send(domain)
  }
  try {
    await send('Page.setDownloadBehavior', { behavior: 'allow', downloadPath: DOWNLOAD_DIR })
  } catch {
    // 旧版 Chrome 不支持就跳过，导出仍会通过网络响应验证
  }

  await login()

  const suffix = Date.now().toString().slice(-6)
  const deptName = `P9测试部${suffix}`
  const deptName2 = `${deptName}改`
  const empUsername = `p9test${suffix}`
  const empName = `P9测试员工${suffix}`
  const empName2 = `${empName}改`
  const results = []

  // ---------- 部门 ----------
  if (!(await clickMenu('部门管理'))) throw new Error('未找到部门管理菜单')
  if (!(await waitFor("document.querySelector('.page-header h2')?.innerText === '部门管理'", 8000))) {
    throw new Error('部门管理页未渲染')
  }
  await waitFor("document.querySelectorAll('.el-table__body tr').length > 0", 8000)
  await sleep(600)
  const deptCountBefore = await rowCount()
  await screenshot('06-dept.png')
  results.push(`部门列表初始 ${deptCountBefore} 行`)

  if (!(await clickButton('新增部门'))) throw new Error('未找到新增部门按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('新增部门弹窗未打开')
  await sleep(400)
  if (!(await fillInput('请输入部门名称', deptName))) throw new Error('未找到部门名称输入框')
  await screenshot('07-dept-dialog.png')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到部门保存按钮')
  if (!(await waitForTableText(deptName, true, 10000))) throw new Error('部门新增后未出现在表格')
  results.push('部门新增成功')

  if (!(await clickRowButton(deptName, '编辑'))) throw new Error('未找到新增部门的编辑按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('编辑部门弹窗未打开')
  await sleep(400)
  if (!(await fillInput('请输入部门名称', deptName2))) throw new Error('未找到编辑部门输入框')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到编辑保存按钮')
  if (!(await waitForTableText(deptName2, true, 10000))) throw new Error('部门编辑后未更新')
  results.push('部门编辑成功')

  if (!(await clickRowButton(deptName2, '删除'))) throw new Error('未找到部门删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('删除确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  if (!(await waitForTableText(deptName2, false, 10000))) throw new Error('部门删除后仍在表格')
  results.push('部门删除成功')

  if (!(await clickRowButton('学工部', '删除'))) throw new Error('未找到学工部删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('删除守卫确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  const guardMessage = await waitFor(
    "document.body.innerText.includes('该部门下还有员工，不能删除')",
    6000
  )
  const stillThere = (await tableText()).includes('学工部')
  if (!guardMessage || !stillThere) throw new Error('删除守卫未按预期工作')
  results.push('删除守卫：学工部被拒绝删除')
  await waitFor(NO_VISIBLE_MESSAGES, 8000)

  // ---------- 员工 ----------
  if (!(await clickMenu('员工管理'))) throw new Error('未找到员工管理菜单')
  if (!(await waitFor("document.querySelector('.page-header h2')?.innerText === '员工管理'", 8000))) {
    throw new Error('员工管理页未渲染')
  }
  if (!(await waitFor("document.querySelector('.total-hint') !== null", 8000))) {
    throw new Error('员工列表未加载')
  }
  await waitFor("document.querySelectorAll('.el-table__body tr').length > 0", 8000)
  await sleep(800)
  const empTotalBefore = await totalHint()
  const empRowsBefore = await rowCount()
  await waitFor(NO_VISIBLE_MESSAGES, 8000)
  await screenshot('08-emp.png')
  await screenshot('08-emp-full.png', true)
  results.push(`员工列表：${empTotalBefore} / 当前页 ${empRowsBefore} 行`)

  if (
    !(await evaluate(`
      const li = [...document.querySelectorAll('.el-pager li')].find((el) => el.textContent.trim() === '2');
      li?.click();
      return !!li;
    `))
  ) {
    throw new Error('未找到分页第 2 页')
  }
  if (!(await waitForRowCount(5, 10000))) throw new Error('分页第 2 页应有 5 行')
  if (
    !(await evaluate(`
      const li = [...document.querySelectorAll('.el-pager li')].find((el) => el.textContent.trim() === '1');
      li?.click();
      return !!li;
    `))
  ) {
    throw new Error('未找到分页第 1 页')
  }
  if (!(await waitForRowCount(empRowsBefore, 10000))) throw new Error('返回第 1 页后行数不正确')
  results.push('分页：第 2 页 5 行 → 第 1 页 10 行')

  if (!(await fillInput('支持模糊查询', '李伟'))) throw new Error('未找到姓名搜索框')
  if (!(await clickButton('查询'))) throw new Error('未找到查询按钮')
  if (!(await waitForRowCount(1, 10000))) throw new Error('按姓名筛选未得到 1 行')
  const filteredRows = await rowCount()
  results.push(`姓名模糊查询：李伟 ${filteredRows} 行`)

  if (!(await clickButton('重置'))) throw new Error('未找到重置按钮')
  if (!(await waitForRowCount(empRowsBefore, 10000))) throw new Error('重置后员工行数不正确')

  if (!(await clickButton('新增员工'))) throw new Error('未找到新增员工按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('新增员工弹窗未打开')
  await sleep(500)
  if (!(await fillInput('请输入用户名', empUsername))) throw new Error('未找到用户名输入框')
  if (!(await fillInput('请输入初始密码', '123456'))) throw new Error('未找到密码输入框')
  if (!(await fillInput('请输入姓名', empName))) throw new Error('未找到姓名输入框')
  if (!(await pickDate('请选择入职日期'))) throw new Error('入职日期选择失败')

  if (existsSync(AVATAR_PATH)) {
    const doc = await send('DOM.getDocument')
    const fileNode = await send('DOM.querySelector', {
      nodeId: doc.root.nodeId,
      selector: '.el-dialog input[type=file]'
    })
    if (!fileNode.nodeId) throw new Error('未找到头像上传输入框')
    await send('DOM.setFileInputFiles', { files: [AVATAR_PATH], nodeId: fileNode.nodeId })
    const avatarUploaded = await waitFor(
      "Boolean([...document.querySelectorAll('.el-dialog .avatar-preview img')].find((img) => img.src.includes('/uploads/') && img.complete && img.naturalWidth > 0))",
      10000
    )
    if (!avatarUploaded) throw new Error('头像上传后未回显')
    results.push('头像上传成功')
  }

  if (!(await clickDialogButton('添加经历'))) throw new Error('未找到添加经历按钮')
  if (!(await waitFor("document.querySelectorAll('.experience-row').length > 0", 5000))) {
    throw new Error('添加经历后未出现经历行')
  }
  await sleep(400)
  if (!(await pickDate('开始日期', '.el-dialog'))) throw new Error('工作经历开始日期选择失败')
  if (!(await pickDate('结束日期', '.el-dialog'))) throw new Error('工作经历结束日期选择失败')
  if (!(await fillInput('公司名称', 'P9测试公司'))) throw new Error('未找到公司名称输入框')
  if (!(await fillInput('职位', '测试工程师'))) throw new Error('未找到经历职位输入框')
  results.push('工作经历表单已填写')

  await waitFor(NO_VISIBLE_MESSAGES, 8000)
  await sleep(300)
  await screenshot('09-emp-dialog.png')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到员工保存按钮')
  if (!(await waitForTableText(empName, true, 12000))) {
    const errors = await evaluate(`
      return [...document.querySelectorAll('.el-form-item__error')]
        .map((el) => el.innerText)
        .filter(Boolean);
    `)
    throw new Error('员工新增后未出现在表格；表单错误：' + errors.join(' | '))
  }
  results.push('员工新增成功（含入职日期/头像/工作经历）')

  const listAvatar = await evaluate(`
    const img = document.querySelector('.el-table__body .emp-cell .el-avatar img');
    return {
      src: img?.src || '',
      loaded: Boolean(img && img.complete && img.naturalWidth > 0)
    };
  `)
  const listAvatarSrc = listAvatar.src
  if (existsSync(AVATAR_PATH) && (!listAvatarSrc.includes('/uploads/') || !listAvatar.loaded)) {
    throw new Error('员工列表未显示上传的头像')
  }

  if (!(await clickRowButton(empName, '编辑'))) throw new Error('未找到新增员工的编辑按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('编辑员工弹窗未打开')
  const usernameLoaded = await waitFor(
    `document.querySelector('input[placeholder="请输入用户名"]')?.value === ${JSON.stringify(empUsername)}`,
    8000
  )
  if (!usernameLoaded) throw new Error('编辑弹窗未回显用户名')
  const experienceLoaded = await waitFor(
    "document.querySelector('.experience-row input[placeholder=\"公司名称\"]')?.value === 'P9测试公司'",
    8000
  )
  if (!experienceLoaded) throw new Error('编辑弹窗未回显工作经历')
  if (existsSync(AVATAR_PATH)) {
    const editAvatar = await evaluate(`
      const img = document.querySelector('.el-dialog .avatar-preview img');
      return { src: img?.src || '', loaded: Boolean(img && img.complete && img.naturalWidth > 0) };
    `)
    if (!editAvatar.src.includes('/uploads/') || !editAvatar.loaded) {
      throw new Error('编辑弹窗未回显头像')
    }
  }
  await sleep(300)
  if (!(await fillInput('请输入姓名', empName2))) throw new Error('未找到编辑姓名输入框')
  if (!(await clickDialogButton('保存'))) throw new Error('未找到编辑保存按钮')
  if (!(await waitFor(DIALOG_CLOSED, 8000))) throw new Error('编辑保存后弹窗未关闭')

  await fillInput('支持模糊查询', empName2)
  if (!(await clickButton('查询'))) throw new Error('未找到查询按钮')
  if (!(await waitForTableText(empName2, true, 10000))) throw new Error('员工编辑后未更新')
  results.push('员工编辑成功（用户名/工作经历/头像回显）')

  if (!(await clickRowButton(empName2, '删除'))) throw new Error('未找到员工删除按钮')
  if (!(await waitFor(MESSAGE_BOX_OPEN, 5000))) throw new Error('员工删除确认框未出现')
  if (!(await clickMessageBoxButton('删除'))) throw new Error('未找到确认删除按钮')
  if (!(await waitForTableText(empName2, false, 10000))) throw new Error('员工删除后仍在表格')
  results.push('员工删除成功')

  // 清掉测试上传产生的头像文件，避免 uploads 目录堆积
  if (listAvatarSrc.includes('/api/uploads/')) {
    const relative = decodeURIComponent(listAvatarSrc.split('/api/uploads/')[1])
    try {
      unlinkSync(resolve(process.cwd(), '..', 'tlias-backend', 'uploads', relative))
    } catch {
      // 文件不存在不影响验收
    }
  }

  if (!(await clickButton('重置'))) throw new Error('未找到重置按钮')
  if (!(await waitForRowCount(empRowsBefore, 10000))) throw new Error('员工删除后重置行数不正确')

  // 工作经历回显：李伟有 1 条经历
  await fillInput('支持模糊查询', '李伟')
  await clickButton('查询')
  await waitForTableText('李伟', true, 10000)
  if (!(await clickRowButton('李伟', '编辑'))) throw new Error('未找到李伟编辑按钮')
  if (!(await waitFor(DIALOG_OPEN, 8000))) throw new Error('李伟编辑弹窗未打开')
  await sleep(900)
  const expRows = await evaluate(`
    return [...document.querySelectorAll('.experience-row')].filter(
      (el) => el.getBoundingClientRect().width > 0
    ).length;
  `)
  if (expRows < 1) throw new Error('编辑李伟时未回显工作经历')
  await waitFor(NO_VISIBLE_MESSAGES, 8000)
  await screenshot('10-emp-edit.png')
  if (!(await clickDialogButton('取消'))) throw new Error('未找到取消按钮')
  await waitFor(DIALOG_CLOSED, 5000)
  results.push(`工作经历回显：${expRows} 条`)
  await clickButton('重置')
  await waitForRowCount(empRowsBefore, 10000)

  // 导出 Excel
  if (!(await clickButton('导出 Excel'))) throw new Error('未找到导出按钮')
  await sleep(2500)
  if (!exportResponse || exportResponse.status !== 200) {
    throw new Error('导出请求未成功')
  }
  const downloads = readdirSync(DOWNLOAD_DIR)
  results.push(`导出响应：${exportResponse.status} ${exportResponse.mimeType}；下载文件 ${downloads.length} 个`)

  const realErrors = consoleErrors.filter((e) => !/favicon/i.test(e))
  const realBad = badResponses.filter((b) => !/favicon/i.test(b))
  const ok =
    exceptions.length === 0 &&
    realErrors.length === 0 &&
    realBad.length === 0 &&
    exportResponse?.status === 200

  console.log('\n=== 批次 2 验收 ===')
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
