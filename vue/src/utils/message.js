/**
 * 类似 Element Plus ElMessage 的顶部轻提示（非阻塞、自动消失）。
 * @param {string} text
 * @param {{ duration?: number }} [options]
 * @returns {() => void} 可提前关闭
 */
export function showMessage(text, options = {}) {
  const duration = options.duration ?? 3000

  let container = document.querySelector('.ob-message-container')
  if (!container) {
    container = document.createElement('div')
    container.className = 'ob-message-container'
    document.body.appendChild(container)
  }

  const el = document.createElement('div')
  el.className = 'ob-message'
  el.setAttribute('role', 'status')
  el.setAttribute('aria-live', 'polite')
  el.textContent = text
  container.appendChild(el)

  requestAnimationFrame(() => {
    requestAnimationFrame(() => el.classList.add('ob-message--show'))
  })

  const remove = () => {
    el.classList.remove('ob-message--show')
    window.setTimeout(() => {
      el.remove()
      if (container && container.children.length === 0) {
        container.remove()
      }
    }, 280)
  }

  const timer = window.setTimeout(remove, duration)
  return () => {
    window.clearTimeout(timer)
    remove()
  }
}
