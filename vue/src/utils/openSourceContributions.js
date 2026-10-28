/**
 * 开源贡献列表：来自站点配置 site_config 的 about_opensource（JSON 数组字符串），
 * 在后台「站点设置 › 开源贡献」维护；url 指向该仓库「我提交的 PR 列表」。
 * 「关于我」页第 2 屏与首页 PR 入口屏共用同一份数据。
 */

// 配置项还没写入（未跑迁移 / 后台未保存过）时的兜底，同时也是后台首次打开时的初始值
export const DEFAULT_CONTRIBUTIONS = [
  { name: 'Apache Dubbo', role: 'Maintainer', url: 'https://github.com/apache/dubbo/pulls?q=author%3AyyyCode' },
  { name: 'Higress', role: 'Contributor', url: 'https://github.com/alibaba/higress/pulls?q=author%3AyyyCode' },
  { name: 'Nacos', role: 'Contributor', url: 'https://github.com/alibaba/nacos/pulls?q=author%3AyyyCode' },
  { name: 'MyBatis-Plus', role: 'Contributor', url: 'https://github.com/baomidou/mybatis-plus/pulls?q=author%3AyyyCode' },
  { name: 'Redisson', role: 'Contributor', url: 'https://github.com/redisson/redisson/pulls?q=author%3AyyyCode' },
  { name: 'Sa-Token', role: 'Contributor', url: 'https://github.com/dromara/Sa-Token/pulls?q=author%3AyyyCode' }
]

/** 逐条挑出合法字段，脏数据（缺 name / 后台手改坏）直接跳过，不整块崩 */
function normalizeContributions(raw) {
  if (!Array.isArray(raw)) return []
  return raw
    .filter((it) => it && typeof it.name === 'string' && it.name.trim())
    .map((it) => ({
      name: it.name.trim(),
      role: (typeof it.role === 'string' && it.role.trim()) || 'Contributor',
      url: typeof it.url === 'string' ? it.url.trim() : ''
    }))
}

/**
 * 把 siteConfig 对象解析成贡献列表。
 * 配置项缺失 → 用兜底；配置项存在（含后台清空后存下的 "[]"）→ 以后台为准，允许清空。
 */
export function resolveOpenSourceContributions(siteConfigValue) {
  const raw = siteConfigValue && siteConfigValue.about_opensource
  if (raw == null || String(raw).trim() === '') return DEFAULT_CONTRIBUTIONS
  try {
    return normalizeContributions(JSON.parse(raw))
  } catch {
    // JSON 手改坏了：退回兜底，页面不至于开天窗
    return DEFAULT_CONTRIBUTIONS
  }
}
