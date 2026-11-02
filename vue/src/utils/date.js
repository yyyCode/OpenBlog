/**
 * 日期展示统一入口。
 *
 * 全部按**本地时区**格式化：此前各页面各自用 `toISOString().slice(0, 10)`，
 * 那是 UTC 日期——东八区 0 点–8 点发布的文章会显示成前一天，属于展示错误。
 */

function pad(n) {
  return String(n).padStart(2, '0')
}

/** 解析为 Date；空值/非法值返回 null（而不是 1970-01-01）。 */
function toDate(value) {
  if (value == null || value === '') return null
  const d = value instanceof Date ? value : new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}

/** 日期：YYYY-MM-DD（本地时区）。空/非法返回 ''。 */
export function formatDate(value) {
  const d = toDate(value)
  if (!d) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 日期时间：YYYY-MM-DD HH:mm[:ss]（本地时区）。空/非法返回 ''。 */
export function formatDateTime(value, { seconds = false } = {}) {
  const d = toDate(value)
  if (!d) return ''
  const head = formatDate(d)
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return seconds ? `${head} ${time}:${pad(d.getSeconds())}` : `${head} ${time}`
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前，超过 7 天回退为日期。 */
export function formatRelativeTime(value) {
  const d = toDate(value)
  if (!d) return ''
  const diff = Date.now() - d.getTime()
  if (diff < 60_000) return '刚刚'
  const m = Math.floor(diff / 60_000)
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const days = Math.floor(h / 24)
  if (days < 7) return `${days} 天前`
  return formatDate(d)
}
