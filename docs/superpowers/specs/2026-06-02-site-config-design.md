# Site Config 动态化设计

## 目标

将前端硬编码的个人信息/站点信息（GitHub、CSDN、牛客、博客名称等）改为后端数据库可配置，通过后台管理页面自由编辑。

## 范围

### 配置项清单

**社交链接类：**
| key | 说明 | 默认值 |
|-----|------|--------|
| `github_url` | GitHub 个人主页 | https://github.com/yyyCode |
| `csdn_url` | CSDN 博客地址 | https://blog.csdn.net/2301_80044822 |
| `nowcoder_url` | 牛客地址 | https://www.nowcoder.com/users/597303882 |
| `source_code_url` | 源码仓库地址 | https://github.com/yyyCode/OpenBlog.git |
| `ai_platform_url` | AI 工作平台地址 | http://ai.wecode.xin/#/chat/default |

**站点展示类：**
| key | 说明 | 默认值 |
|-----|------|--------|
| `blog_name` | 博客名称 | 烧仙草冰室 |
| `hero_title` | 首页 Hero 标题 | 设计，创造，思考未来 |
| `hero_subtitle` | 首页 Hero 副标题 | 探索 AI、设计与技术的交集... |
| `about_text` | 关于页面介绍 | 这里是个人博客，用来记录设计、技术与思考。 |
| `default_avatar_url` | 默认头像 URL | https://via.placeholder.com/... |
| `site_start_date` | 站点起始日期 | 2026-03-20 |

**页脚类：**
| key | 说明 | 默认值 |
|-----|------|--------|
| `footer_copyright` | 页脚版权信息 | © 2026 OpenBlog |

## 数据库设计

### 表：`site_config`

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT AUTO_INCREMENT PK | — |
| config_key | VARCHAR(64) UNIQUE NOT NULL | 配置键 |
| config_value | TEXT | 配置值 |
| updated_at | DATETIME | 更新时间 |

## 后端设计

### 实体：`SiteConfig`
- 标准 Entity，MyBatis-Plus 映射

### Mapper：`SiteConfigMapper`
- 继承 `BaseMapper<SiteConfig>`

### Service：`SiteConfigService`
- `getAllConfigs()` → Map<String, String>，读取全量配置
- `updateConfigs(Map<String, String>)` → void，批量 upsert

### Controller：扩展 `SiteMetaController`
- `GET /api/v1/site/config` — 公开
- `PUT /api/v1/site/config` — 需 ADMIN/AUTHOR 角色

## 前端设计

### 数据流

App.vue 初始化时 `fetchSiteConfig()` 获取全量配置 → provide 给所有子组件。各组件通过 inject 读取对应 key 的值。

### API

```js
// vue/src/api/site.js 新增
export function fetchSiteConfig()  // GET /api/v1/site/config
export function updateSiteConfig(payload)  // PUT /api/v1/site/config
```

### 后台管理页面（新增）

`ConsoleSiteConfigView.vue`：
- 路由 `/console/site-config`，侧边栏增加入口
- 表单分区域：社交链接 / 站点展示 / 页脚
- 保存时调用 `updateSiteConfig` 接口

### 改造组件

| 组件 | 读取 config key | 替换内容 |
|------|----------------|---------|
| ProfileCard.vue | github_url, csdn_url, nowcoder_url, default_avatar_url | 链接与头像 |
| BlogHeader.vue | blog_name, source_code_url | 博客名称与源码链接 |
| RightDock.vue | ai_platform_url | AI平台链接 |
| BlogInfoCard.vue | site_start_date | 站点起始日期 |
| HomeView.vue | hero_title, hero_subtitle | Hero 文案 |
| AboutView.vue | about_text | 关于介绍 |
| 页脚（新增或现有） | footer_copyright | 版权信息 |

### 数据初始化

启动时 `SiteConfigService` 检查表是否为空，若为空则插入默认值。无需 SQL 初始化脚本。

## 向后兼容

- 各前端组件使用 config 值的同时保留默认值 fallback
- API 返回为空时前端降级到当前硬编码的默认值
- 不影响现有 SEO 配置（siteName 等继续走 SeoProperties）
