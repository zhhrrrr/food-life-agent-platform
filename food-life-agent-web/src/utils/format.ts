export function money(value?: number | null) {
  if (value === undefined || value === null) {
    return '¥0'
  }
  return `¥${(value / 100).toFixed(2)}`
}

export function score(value?: number | null) {
  if (value === undefined || value === null) {
    return '暂无评分'
  }
  return (value / 10).toFixed(1)
}

export function shortDate(value?: string | null) {
  if (!value) {
    return ''
  }
  return value.replace('T', ' ').slice(0, 16)
}
