export function buildTocAndInjectIds(inputHtml) {
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(String(inputHtml || ''), 'text/html')
    const headings = Array.from(doc.querySelectorAll('h1,h2,h3,h4,h5,h6'))
    const used = new Set()

    const toc = headings
      .map((h) => {
        const level = Number(String(h.tagName || '').slice(1)) || 1
        const text = String(h.textContent || '').replace(/\s+/g, ' ').trim()
        if (!text) return null

        let id = String(h.getAttribute('id') || '').trim()
        if (!id) id = slugify(text)
        id = dedupeId(id, used)
        used.add(id)
        h.setAttribute('id', id)

        return { id, level, text }
      })
      .filter(Boolean)

    return { html: doc.body.innerHTML, toc }
  } catch {
    return { html: inputHtml, toc: [] }
  }
}

export function jumpToHeading(id) {
  requestAnimationFrame(() => {
    const el = document.getElementById(id)
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function slugify(s) {
  const base = String(s || '')
    .toLowerCase()
    .trim()
    .replace(/[\s]+/g, '-')
    .replace(/[^\p{L}\p{N}\-_.~]/gu, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
  return base || `h-${Date.now().toString(36)}`
}

function dedupeId(id, used) {
  let out = id
  let i = 2
  while (used.has(out)) {
    out = `${id}-${i}`
    i += 1
  }
  return out
}
