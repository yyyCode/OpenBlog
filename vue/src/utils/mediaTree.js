export function mapFolderNode(node) {
  const children = (node.children || []).map(mapFolderNode)
  const childFileCount = children.reduce((sum, c) => sum + (c.fileCount || 0), 0)
  return {
    key: `folder-${node.id}`,
    label: node.name,
    folderId: node.id,
    fileCount: (node.fileCount || 0) + childFileCount,
    children
  }
}

/** 将后端返回的树节点转为前端树（添加 key、递归处理） */
export function buildFolderTree(folders) {
  return (folders || []).map(mapFolderNode)
}

/** 收集从根到指定文件夹的展开路径 */
export function collectExpandedKeysForFolder(tree, folderId) {
  if (!folderId) return []

  function walk(node, path) {
    const nextPath = [...path, node.key]
    if (node.folderId === Number(folderId)) return nextPath
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
