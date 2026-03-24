const STORAGE_KEY = 'openblog-theme'

export function getStoredTheme() {
  if (typeof localStorage === 'undefined') return 'light'
  const v = localStorage.getItem(STORAGE_KEY)
  if (v === 'dark' || v === 'light') return v
  return 'light'
}

export function applyTheme(theme) {
  const t = theme === 'dark' ? 'dark' : 'light'
  document.documentElement.setAttribute('data-theme', t)
  document.documentElement.style.colorScheme = t === 'dark' ? 'dark' : 'light'
  try {
    localStorage.setItem(STORAGE_KEY, t)
  } catch (_) {
    /* ignore */
  }
}

export function applyStoredTheme() {
  applyTheme(getStoredTheme())
}

export function toggleTheme() {
  const cur =
    document.documentElement.getAttribute('data-theme') === 'dark'
      ? 'dark'
      : 'light'
  applyTheme(cur === 'dark' ? 'light' : 'dark')
}
