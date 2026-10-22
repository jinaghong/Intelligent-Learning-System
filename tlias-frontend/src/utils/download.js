export function resolveFileName(headers, fallback = 'download') {
  const disposition = headers?.['content-disposition'] || ''
  const encoded = disposition.match(/filename\*=utf-8''([^;]+)/i)
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1].replace(/\+/g, '%20'))
    } catch {
      // 编码不规范时退回下面的普通文件名解析
    }
  }
  const plain = disposition.match(/filename="?([^";]+)"?/i)
  return plain?.[1] || fallback
}

export async function saveBlob(response, fallback = 'download') {
  const blob = response.data instanceof Blob ? response.data : new Blob([response.data])

  // 后端出错时会返回 JSON，axios 也按 blob 收下了；这里拆开给出真正的错误提示
  if (blob.type.includes('application/json')) {
    let message = '导出失败'
    try {
      const body = JSON.parse(await blob.text())
      message = body.msg || message
    } catch {
      // 不是合法 JSON 就用默认提示
    }
    throw new Error(message)
  }

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = resolveFileName(response.headers, fallback)
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
