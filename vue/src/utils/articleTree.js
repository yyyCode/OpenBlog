function sortArticles(items) {
  return items
    .slice()
    .sort((x, y) => new Date(y?.publishedAt || 0) - new Date(x?.publishedAt || 0))
}

function mapCategoryNode(node, byCategory) {
  const articles = sortArticles(byCategory.get(node.id) || [])
  const children = (node.children || []).map((child) => mapCategoryNode(child, byCategory))
  const childArticleCount = children.reduce((sum, c) => sum + (c.articleCount || 0), 0)

  return {
    key: `cat-${node.id}`,
    label: node.name,
    categoryId: node.id,
    articles,
    children,
    articleCount: articles.length + childArticleCount
  }
}

/** 将分类树与文章列表合并为左侧导航树 */
export function buildCategoryArticleTree(categories, articles) {
  const byCategory = new Map()
  const uncategorized = []

  for (const a of articles || []) {
    if (a?.categoryId) {
      if (!byCategory.has(a.categoryId)) byCategory.set(a.categoryId, [])
      byCategory.get(a.categoryId).push(a)
    } else {
      uncategorized.push(a)
    }
  }

  const tree = (categories || []).map((node) => mapCategoryNode(node, byCategory))

  if (uncategorized.length > 0) {
    tree.push({
      key: 'uncategorized',
      label: '未分类',
      categoryId: null,
      articles: sortArticles(uncategorized),
      children: [],
      articleCount: uncategorized.length
    })
  }

  return tree
}

function filterNode(node, keyword) {
  const matchedArticles = node.articles.filter((a) =>
    String(a?.title || '').toLowerCase().includes(keyword)
  )
  const matchedChildren = node.children
    .map((child) => filterNode(child, keyword))
    .filter(Boolean)

  const labelMatch = String(node.label || '').toLowerCase().includes(keyword)
  if (!labelMatch && matchedArticles.length === 0 && matchedChildren.length === 0) {
    return null
  }

  return {
    ...node,
    articles: labelMatch ? node.articles : matchedArticles,
    children: labelMatch ? node.children : matchedChildren,
    articleCount: (labelMatch ? node.articles.length : matchedArticles.length)
      + (labelMatch ? node.children : matchedChildren).reduce((sum, c) => sum + (c.articleCount || 0), 0)
  }
}

export function filterCategoryArticleTree(tree, keyword) {
  const k = String(keyword || '').trim().toLowerCase()
  if (!k) return tree
  return tree.map((node) => filterNode(node, k)).filter(Boolean)
}

function findInNode(node, articleId) {
  if (node.articles.some((a) => String(a.id) === articleId)) return node.key
  for (const child of node.children) {
    const found = findInNode(child, articleId)
    if (found) return found
  }
  return null
}

export function findGroupKeyForArticle(tree, articleId) {
  const id = String(articleId ?? '')
  if (!id) return null
  for (const node of tree) {
    const found = findInNode(node, id)
    if (found) return found
  }
  return null
}

export function collectExpandedKeysForArticle(tree, articleId) {
  const id = String(articleId ?? '')
  if (!id) return []

  function walk(node, path) {
    const nextPath = [...path, node.key]
    if (node.articles.some((a) => String(a.id) === id)) return nextPath
    for (const child of node.children) {
      const found = walk(child, nextPath)
      if (found) return found
    }
    return null
  }

  for (const node of tree) {
    const found = walk(node, [])
    if (found) return found
  }
  return []
}
