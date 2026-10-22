export function formatDateTime(value) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

export function formatDate(value) {
  return value || '—'
}

export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return '—'
  return Number(value).toFixed(2)
}

export function formatJson(value) {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
