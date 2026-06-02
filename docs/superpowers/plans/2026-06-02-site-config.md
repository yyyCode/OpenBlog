# Site Config 动态化 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将前端硬编码的个人信息/站点信息改为后端数据库可配置，通过后台管理页面自由编辑。

**Architecture:** 后端新建 `site_config` 表（key-value 结构），通过 `GET /api/v1/site/config`（公开）和 `PUT /api/v1/site/config`（需认证）读写。前端 App.vue 初始化时拉取全量配置，通过 provide/inject 共享给所有子组件，各组件读取对应 key 替换硬编码值。

**Tech Stack:** Spring Boot + MyBatis-Plus + MySQL, Vue 3 + Vue Router, 纯 CSS（跟随项目风格）

---

## File Structure

```
后端（新建/修改）:
  Create: OpenBlog-business/src/main/java/com/yqz/openblog/site/entity/SiteConfig.java
  Create: OpenBlog-business/src/main/java/com/yqz/openblog/site/repo/SiteConfigMapper.java
  Create: OpenBlog-business/src/main/java/com/yqz/openblog/site/SiteConfigService.java
  Modify: OpenBlog-business/src/main/java/com/yqz/openblog/controller/SiteMetaController.java
  Modify: OpenBlog-business/src/main/java/com/yqz/openblog/security/SecurityConfig.java
  Create: OpenBlog-business/src/main/resources/sql/site-config.sql

前端（新建/修改）:
  Modify: vue/src/api/site.js
  Create: vue/src/views/ConsoleSiteConfigView.vue
  Modify: vue/src/router/index.js
  Modify: vue/src/layouts/ConsoleLayout.vue
  Modify: vue/src/App.vue
  Modify: vue/src/components/ProfileCard.vue
  Modify: vue/src/components/BlogHeader.vue
  Modify: vue/src/components/BlogInfoCard.vue
  Modify: vue/src/components/RightDock.vue
  Modify: vue/src/views/HomeView.vue
  Modify: vue/src/views/AboutView.vue
  Modify: vue/src/assets/blog.css
```

---

### Task 1: 创建数据库表 SQL

**Files:**
- Create: `OpenBlog-business/src/main/resources/sql/site-config.sql`

- [ ] **Step 1: 创建 site_config 建表 SQL**

```sql
-- 站点配置表（key-value 结构，支持后台编辑）
CREATE TABLE IF NOT EXISTS site_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    config_key  VARCHAR(64)  NOT NULL COMMENT '配置键',
    config_value TEXT        NULL     COMMENT '配置值',
    updated_at  DATETIME     NULL     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点配置表';

-- 初始默认数据
INSERT IGNORE INTO site_config (config_key, config_value) VALUES
('github_url',       'https://github.com/yyyCode'),
('csdn_url',         'https://blog.csdn.net/2301_80044822'),
('nowcoder_url',     'https://www.nowcoder.com/users/597303882'),
('source_code_url',  'https://github.com/yyyCode/OpenBlog.git'),
('ai_platform_url',  'http://ai.wecode.xin/#/chat/default'),
('blog_name',        '烧仙草冰室'),
('hero_title',       '设计，创造，思考未来'),
('hero_subtitle',    '探索 AI、设计与技术的交集\n分享关于智能交互、AI 驱动产品与数字创新的实战经验。'),
('about_text',       '这里是个人博客，用来记录设计、技术与思考。'),
('default_avatar_url','https://via.placeholder.com/120x120.png?text=OpenBlog'),
('site_start_date',  '2026-03-20'),
('footer_copyright', '© 2026 OpenBlog');
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/resources/sql/site-config.sql
git commit -m "feat: 添加 site_config 表 SQL"
```

---

### Task 2: 创建后端实体 SiteConfig

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/site/entity/SiteConfig.java`

- [ ] **Step 1: 创建实体类**

```java
package com.yqz.openblog.site.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 站点配置表（key-value 结构，支持后台编辑）。
 */
@TableName("site_config")
public class SiteConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String configValue;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/site/entity/SiteConfig.java
git commit -m "feat: 添加 SiteConfig 实体"
```

---

### Task 3: 创建后端 Mapper

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/site/repo/SiteConfigMapper.java`

- [ ] **Step 1: 创建 Mapper 接口**

```java
package com.yqz.openblog.site.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yqz.openblog.site.entity.SiteConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * site_config 表 Mapper
 */
@Mapper
public interface SiteConfigMapper extends BaseMapper<SiteConfig> {

    @Update("INSERT INTO site_config (config_key, config_value) VALUES (#{key}, #{value}) "
          + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), updated_at = NOW()")
    int upsert(String key, String value);
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/site/repo/SiteConfigMapper.java
git commit -m "feat: 添加 SiteConfigMapper"
```

---

### Task 4: 创建后端 SiteConfigService

**Files:**
- Create: `OpenBlog-business/src/main/java/com/yqz/openblog/site/SiteConfigService.java`

- [ ] **Step 1: 创建 Service**

```java
package com.yqz.openblog.site;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.site.entity.SiteConfig;
import com.yqz.openblog.site.repo.SiteConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点配置 Service。
 * key-value 结构，getAllConfigs 返回全量 Map，updateConfigs 批量 upsert。
 */
@Service
public class SiteConfigService {

    private static final Logger log = LoggerFactory.getLogger(SiteConfigService.class);

    private final SiteConfigMapper siteConfigMapper;

    public SiteConfigService(SiteConfigMapper siteConfigMapper) {
        this.siteConfigMapper = siteConfigMapper;
    }

    /**
     * 读取全量配置，以 configKey → configValue 返回，保持插入顺序。
     */
    public Map<String, String> getAllConfigs() {
        List<SiteConfig> rows = siteConfigMapper.selectList(
                Wrappers.lambdaQuery(SiteConfig.class).orderByAsc(SiteConfig::getId));
        Map<String, String> map = new LinkedHashMap<>();
        for (SiteConfig row : rows) {
            map.put(row.getConfigKey(), row.getConfigValue() != null ? row.getConfigValue() : "");
        }
        return map;
    }

    /**
     * 批量 upsert 配置。
     * 只处理传入的 key，不影响其他已有配置。
     */
    @Transactional
    public void updateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) return;
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank()) continue;
            try {
                siteConfigMapper.upsert(key.trim(), value != null ? value : "");
            } catch (Exception e) {
                log.warn("upsert site_config failed: key={}", key, e);
                // 失败不中断其他 key 的更新
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/site/SiteConfigService.java
git commit -m "feat: 添加 SiteConfigService"
```

---

### Task 5: 扩展 SiteMetaController 增加 config 端点

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/controller/SiteMetaController.java`

- [ ] **Step 1: 在构造函数中添加 SiteConfigService，新增两个端点**

在 `SiteMetaController.java` 中：

```java
// 新增 import
import com.yqz.openblog.site.SiteConfigService;
import org.springframework.security.access.prepost.PreAuthorize;

// 构造函数新增参数：
private final SiteConfigService siteConfigService;

public SiteMetaController(SiteProperties siteProperties, SiteStatsService siteStatsService,
                          SiteVisitService siteVisitService, SiteConfigService siteConfigService) {
    this.siteProperties = siteProperties;
    this.siteStatsService = siteStatsService;
    this.siteVisitService = siteVisitService;
    this.siteConfigService = siteConfigService;
}

// 在类末尾新增两个方法：

@GetMapping("/config")
public ApiResponse<Map<String, String>> config() {
    return ApiResponse.ok(siteConfigService.getAllConfigs());
}

@PutMapping("/config")
@PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
public ApiResponse<Map<String, Object>> updateConfig(@RequestBody Map<String, String> payload) {
    siteConfigService.updateConfigs(payload);
    return ApiResponse.ok(Map.of("updated", payload != null ? payload.size() : 0));
}
```

同时需要添加 `import org.springframework.web.bind.annotation.PutMapping;` 和 `import org.springframework.web.bind.annotation.RequestBody;`。

- [ ] **Step 2: 更新 SecurityConfig 允许 PUT /site/config 需要认证**

`SecurityConfig.java` 已配置 `.anyRequest().authenticated()`，加上 `@PreAuthorize` 注解即可（需要 `@EnableMethodSecurity`，现有配置已启用）。无需额外修改 SecurityConfig。

- [ ] **Step 3: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/controller/SiteMetaController.java
git commit -m "feat: 添加 /api/v1/site/config 读写端点"
```

---

### Task 6: 前端 API 层新增站点配置请求方法

**Files:**
- Modify: `vue/src/api/site.js`

- [ ] **Step 1: 在 site.js 末尾添加两个方法**

```js
export function fetchSiteConfig() {
  return request('/api/v1/site/config')
}

/** payload: { github_url: '...', blog_name: '...', ... } */
export function updateSiteConfig(payload) {
  return request('/api/v1/site/config', {
    method: 'PUT',
    withAuth: true,
    body: JSON.stringify(payload)
  })
}
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/api/site.js
git commit -m "feat: 前端添加 fetchSiteConfig / updateSiteConfig API"
```

---

### Task 7: App.vue 全局加载站点配置

**Files:**
- Modify: `vue/src/App.vue`

- [ ] **Step 1: 在 App.vue 中拉取站点配置并通过 provide 共享**

将 App.vue 的 `<script setup>` 部分修改为（追加 siteConfig 相关代码）：

在原 `onMounted` 中追加加载 siteConfig 的逻辑。完整改造后的 script：

```js
import { computed, onMounted, provide, ref } from 'vue'
import { useRoute } from 'vue-router'
import BlogHeader from './components/BlogHeader.vue'
import WelcomeGate from './components/WelcomeGate.vue'
import BackToTop from './components/BackToTop.vue'
import WidgetsDrawer from './components/WidgetsDrawer.vue'
import RightDock from './components/RightDock.vue'
import Live2dCharacter from './components/Live2dCharacter.vue'
import { postSiteVisit } from './api/site'
import { fetchSiteConfig } from './api/site'
import { fetchPublicProfile } from './api/profile'

const route = useRoute()
const isConsole = computed(() => route.path.startsWith('/console'))
const widgetsOpen = ref(false)
const profile = ref(null)
const siteConfig = ref({})

// 提供 siteConfig 给所有子组件
const SITE_CONFIG_KEY = Symbol('siteConfig')
provide(SITE_CONFIG_KEY, siteConfig)

function toggleWidgets() {
  widgetsOpen.value = !widgetsOpen.value
}

onMounted(() => {
  postSiteVisit().catch(() => {})
})

onMounted(async () => {
  if (isConsole.value) return
  try {
    profile.value = await fetchPublicProfile()
  } catch {
    profile.value = null
  }
  // 加载站点配置
  try {
    siteConfig.value = await fetchSiteConfig()
  } catch {
    siteConfig.value = {}
  }
})
```

然后在模板中，需要给 `WidgetsDrawer` 也传递 `siteConfig`（或其他组件需要）。

**注意**：为了各组件能方便读取 siteConfig，项目规模小，不使用复杂的 store。在非 Console 页面下 siteConfig 已在 App.vue 中加载并 provide。各子组件使用 `inject` 读取。

- [ ] **Step 2: Commit**

```bash
git add vue/src/App.vue
git commit -m "feat: App.vue 全局加载站点配置并 provide"
```

---

### Task 8: 创建后台管理页面 ConsoleSiteConfigView

**Files:**
- Create: `vue/src/views/ConsoleSiteConfigView.vue`

- [ ] **Step 1: 创建管理页面组件**

```vue
<template>
  <div class="console-page">
    <header class="console-page-header">
      <div class="console-page-title">
        <h1>站点设置</h1>
      </div>
    </header>

    <div class="console-card console-inner-card">
      <div v-if="loading" style="color: var(--console-muted, var(--muted))">加载中...</div>
      <template v-else>
        <!-- 社交链接 -->
        <div class="admin-section">
          <h2 class="admin-section-title">社交链接</h2>
          <div class="field">
            <div class="label">GitHub 主页</div>
            <input v-model="form.github_url" class="input" type="url" placeholder="https://github.com/..." />
          </div>
          <div class="field">
            <div class="label">CSDN 博客</div>
            <input v-model="form.csdn_url" class="input" type="url" placeholder="https://blog.csdn.net/..." />
          </div>
          <div class="field">
            <div class="label">牛客主页</div>
            <input v-model="form.nowcoder_url" class="input" type="url" placeholder="https://www.nowcoder.com/..." />
          </div>
          <div class="field">
            <div class="label">源码仓库</div>
            <input v-model="form.source_code_url" class="input" type="url" placeholder="https://github.com/..." />
          </div>
          <div class="field">
            <div class="label">AI 工作平台</div>
            <input v-model="form.ai_platform_url" class="input" type="url" placeholder="http://..." />
          </div>
        </div>

        <!-- 站点展示 -->
        <div class="admin-section">
          <h2 class="admin-section-title">站点展示</h2>
          <div class="field">
            <div class="label">博客名称</div>
            <input v-model="form.blog_name" class="input" type="text" placeholder="博客名称" />
          </div>
          <div class="field">
            <div class="label">首页 Hero 标题</div>
            <input v-model="form.hero_title" class="input" type="text" placeholder="标题" />
          </div>
          <div class="field">
            <div class="label">首页 Hero 副标题</div>
            <textarea v-model="form.hero_subtitle" class="textarea" rows="3" placeholder="副标题（支持 \n 换行）"></textarea>
          </div>
          <div class="field">
            <div class="label">关于页面介绍</div>
            <textarea v-model="form.about_text" class="textarea" rows="4" placeholder="关于页面介绍文字"></textarea>
          </div>
          <div class="field">
            <div class="label">默认头像 URL</div>
            <input v-model="form.default_avatar_url" class="input" type="url" placeholder="https://..." />
          </div>
          <div class="field">
            <div class="label">站点起始日期</div>
            <input v-model="form.site_start_date" class="input" type="date" />
          </div>
        </div>

        <!-- 页脚 -->
        <div class="admin-section">
          <h2 class="admin-section-title">页脚</h2>
          <div class="field">
            <div class="label">版权信息</div>
            <input v-model="form.footer_copyright" class="input" type="text" placeholder="© 2026 OpenBlog" />
          </div>
        </div>

        <div v-if="error" class="error" style="margin-top: 10px">{{ error }}</div>
        <div v-else-if="success" class="success" style="margin-top: 10px">{{ success }}</div>
        <button class="btn primary" style="margin-top: 14px" :disabled="saving" @click="save">
          {{ saving ? '保存中...' : '保存配置' }}
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchSiteConfig, updateSiteConfig } from '../api/site'

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')

const form = ref({
  github_url: '',
  csdn_url: '',
  nowcoder_url: '',
  source_code_url: '',
  ai_platform_url: '',
  blog_name: '',
  hero_title: '',
  hero_subtitle: '',
  about_text: '',
  default_avatar_url: '',
  site_start_date: '',
  footer_copyright: ''
})

onMounted(async () => {
  loading.value = true
  try {
    const config = await fetchSiteConfig()
    if (config) {
      // 将后端返回的配置合并到 form 中
      Object.keys(form.value).forEach((key) => {
        if (config[key] != null) {
          form.value[key] = String(config[key])
        }
      })
    }
  } catch {
    error.value = '加载配置失败'
  }
  loading.value = false
})

async function save() {
  error.value = ''
  success.value = ''
  saving.value = true
  try {
    // 构建要提交的 payload（清理空白）
    const payload = {}
    Object.entries(form.value).forEach(([k, v]) => {
      payload[k] = (v || '').trim()
    })
    await updateSiteConfig(payload)
    success.value = '配置已保存'
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e) {
    error.value = e.message || '保存失败'
  }
  saving.value = false
}
</script>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/views/ConsoleSiteConfigView.vue
git commit -m "feat: 创建站点设置后台管理页面"
```

---

### Task 9: 注册路由和侧边栏入口

**Files:**
- Modify: `vue/src/router/index.js`
- Modify: `vue/src/layouts/ConsoleLayout.vue`

- [ ] **Step 1: 在 router/index.js 中添加 import 和路由**

在文件顶部 import 区域添加：
```js
import ConsoleSiteConfigView from '../views/ConsoleSiteConfigView.vue'
```

在 `/console` 的 children 数组中，在 `{ path: 'system', ... }` 之前添加：
```js
{ path: 'site-config', name: 'consoleSiteConfig', component: ConsoleSiteConfigView },
```

- [ ] **Step 2: 在 ConsoleLayout.vue 侧边栏添加入口**

在 "概览" 链接之前（`<router-link to="/console/system"` 之前），添加：

```html
<router-link to="/console/site-config" class="console-nav-item" active-class="active">
  <span class="console-nav-ico" aria-hidden="true">
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="12" cy="12" r="3" />
      <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
    </svg>
  </span>
  站点设置
</router-link>
```

- [ ] **Step 3: Commit**

```bash
git add vue/src/router/index.js vue/src/layouts/ConsoleLayout.vue
git commit -m "feat: 注册站点设置路由和侧边栏入口"
```

---

### Task 10: 改造 ProfileCard 使用站点配置

**Files:**
- Modify: `vue/src/components/ProfileCard.vue`

- [ ] **Step 1: 将硬编码 URL 改为从 inject 读取**

将 `<script setup>` 部分改为：

```js
import { computed, inject, onMounted, ref } from 'vue'
import { fetchSiteVersion } from '../api/site'

defineProps({
  profile: {
    type: Object,
    required: false
  }
})

const siteConfig = inject(Symbol('siteConfig'), ref({}))

const siteVersion = ref('')

const defaultAvatar = computed(() =>
  siteConfig.value?.default_avatar_url || 'https://via.placeholder.com/120x120.png?text=OpenBlog'
)
const githubUrl = computed(() => siteConfig.value?.github_url || 'https://github.com/yyyCode')
const csdnUrl = computed(() => siteConfig.value?.csdn_url || 'https://blog.csdn.net/2301_80044822')
const nowcoderUrl = computed(() => siteConfig.value?.nowcoder_url || 'https://www.nowcoder.com/users/597303882')

const siteVersionLabel = computed(() => {
  const v = (siteVersion.value || '').trim()
  if (!v) return ''
  return v.startsWith('v') ? v : `v${v}`
})

onMounted(async () => {
  try {
    const data = await fetchSiteVersion()
    if (data?.version) siteVersion.value = String(data.version)
  } catch {
    siteVersion.value = ''
  }
})
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/components/ProfileCard.vue
git commit -m "feat: ProfileCard 从站点配置读取社交链接和默认头像"
```

---

### Task 11: 改造 BlogHeader 使用站点配置

**Files:**
- Modify: `vue/src/components/BlogHeader.vue`

- [ ] **Step 1: 博客名称、源码链接改为从 inject 读取**

在 `<script setup>` 中添加：

```js
import { inject, ref } from 'vue'
// ... 其他 imports 不变

const siteConfig = inject(Symbol('siteConfig'), ref({}))
```

在模板中：
- 博客名称：将 `烧仙草冰室` 替换为 `{{ siteConfig?.blog_name || '烧仙草冰室' }}`
- 源码链接：将 `https://github.com/yyyCode/OpenBlog.git` 替换为 `:href="siteConfig?.source_code_url || 'https://github.com/yyyCode/OpenBlog.git'"`

完整 `<template>` 修改两处：

```html
<!-- 第 5 行 -->
<span class="site-brand-text">{{ siteConfig?.blog_name || '烧仙草冰室' }}</span>

<!-- 第 40 行 -->
<a
  class="site-nav-link site-nav-link-sm-hide"
  :href="siteConfig?.source_code_url || 'https://github.com/yyyCode/OpenBlog.git'"
  target="_blank"
  rel="noopener noreferrer"
>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/components/BlogHeader.vue
git commit -m "feat: BlogHeader 从站点配置读取博客名称和源码链接"
```

---

### Task 12: 改造 RightDock 使用站点配置

**Files:**
- Modify: `vue/src/components/RightDock.vue`

- [ ] **Step 1: AI 平台链接改为从 inject 读取**

在 `<script setup>` 中添加：

```js
import { inject, ref } from 'vue'

const siteConfig = inject(Symbol('siteConfig'), ref({}))
```

模板中第 43 行，将：
```html
href="http://ai.wecode.xin/#/chat/default"
```
改为：
```html
:href="siteConfig?.ai_platform_url || 'http://ai.wecode.xin/#/chat/default'"
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/components/RightDock.vue
git commit -m "feat: RightDock AI平台链接从站点配置读取"
```

---

### Task 13: 改造 BlogInfoCard 使用站点配置

**Files:**
- Modify: `vue/src/components/BlogInfoCard.vue`

- [ ] **Step 1: 站点起始日期改为从 inject 读取**

在 `<script setup>` 中添加：

```js
import { computed, inject, onMounted, ref } from 'vue'
import { fetchArticles } from '../api/article'
import { fetchSiteStats } from '../api/site'

const siteConfig = inject(Symbol('siteConfig'), ref({}))

/** 从站点配置读取起始日期，fallback 到硬编码 */
const SITE_START = computed(() => {
  const dateStr = siteConfig.value?.site_start_date
  if (dateStr) {
    const d = new Date(dateStr + 'T00:00:00')
    if (!isNaN(d.getTime())) return d
  }
  return new Date(2026, 2, 20, 0, 0, 0, 0)
})
```

然后将 `formatRunningSince(SITE_START)` 调用处改为 `formatRunningSince(SITE_START.value)`，并将 `SITE_START` 从顶层常量改为 computed 引用。

完整修改：删除原来的 `const SITE_START = new Date(2026, 2, 20, 0, 0, 0, 0)`，添加上面的 `import { inject, ref } from 'vue'` 合并到已有 import，添加 `const siteConfig = ...` 和 `const SITE_START = computed(...)`。将 `runningLabel` computed 中的 `SITE_START` 改为 `SITE_START.value`。

- [ ] **Step 2: Commit**

```bash
git add vue/src/components/BlogInfoCard.vue
git commit -m "feat: BlogInfoCard 站点起始日期从配置读取"
```

---

### Task 14: 改造 HomeView 使用站点配置

**Files:**
- Modify: `vue/src/views/HomeView.vue`

- [ ] **Step 1: Hero 标题和副标题改为从 inject 读取**

在 `<script setup>` 中添加：

```js
import { inject, ref } from 'vue'

const siteConfig = inject(Symbol('siteConfig'), ref({}))
```

模板中：
- Hero 标题：将 `设计，创造，<br />思考未来` 替换为动态绑定。注意换行符处理：

```html
<h1 class="home-hero-title">{{ heroTitleLines[0] || '' }}<br />{{ heroTitleLines[1] || '' }}</h1>
<p class="home-hero-sub">{{ heroSubtitleHtml }}</p>
```

添加 computed：

```js
const heroTitle = computed(() => siteConfig.value?.hero_title || '设计，创造，思考未来')
const heroSubtitle = computed(() => siteConfig.value?.hero_subtitle || '探索 AI、设计与技术的交集\n分享关于智能交互、AI 驱动产品与数字创新的实战经验。')

const heroTitleLines = computed(() => {
  const text = heroTitle.value
  // 按 \n 分割或自然按逗号分割
  const parts = text.split('\n').filter(Boolean)
  if (parts.length >= 2) return parts.slice(0, 2)
  // fallback: 尝试按中文逗号分割
  const commaParts = text.split('，')
  if (commaParts.length >= 2) return commaParts.slice(0, 2)
  return [text, '']
})

const heroSubtitleHtml = computed(() => {
  return heroSubtitle.value.replace(/\n/g, '<br />')
})
```

模板中 Hero 区块改为：

```html
<h1 class="home-hero-title">{{ heroTitleLines[0] || '' }}<br />{{ heroTitleLines[1] || '' }}</h1>
<p class="home-hero-sub" v-html="heroSubtitleHtml"></p>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/views/HomeView.vue
git commit -m "feat: HomeView Hero 文案从站点配置读取"
```

---

### Task 15: 改造 AboutView 使用站点配置

**Files:**
- Modify: `vue/src/views/AboutView.vue`

- [ ] **Step 1: 关于文字改为从 inject 读取**

在 `<script setup>` 中添加：

```js
import { computed, inject, ref } from 'vue'

const siteConfig = inject(Symbol('siteConfig'), ref({}))

const aboutText = computed(() =>
  siteConfig.value?.about_text || '这里是个人博客，用来记录设计、技术与思考。'
)
```

模板中将硬编码文字替换为：
```html
<p style="margin: 12px 0 0; color: var(--muted); line-height: 1.8">
  {{ aboutText }}
</p>
```

- [ ] **Step 2: Commit**

```bash
git add vue/src/views/AboutView.vue
git commit -m "feat: AboutView 介绍文字从站点配置读取"
```

---

### Task 16: 添加页脚组件并在 App.vue 中使用

**Files:**
- Modify: `vue/src/App.vue`
- Modify: `vue/src/assets/blog.css`

- [ ] **Step 1: 在 App.vue 模板中添加页脚**

在 `App.vue` 模板中，在 `<router-view />` 之后添加：

```html
<footer v-if="!isConsole" class="site-footer">
  <div class="site-footer-inner">
    <span>{{ siteConfig?.footer_copyright || '© 2026 OpenBlog' }}</span>
  </div>
</footer>
```

- [ ] **Step 2: 在 blog.css 末尾添加页脚样式**

```css
/* ---- Footer ---- */
.site-footer {
  margin-top: 48px;
  padding: 24px 16px;
  text-align: center;
  border-top: 1px solid var(--border-light, #e5e7eb);
}

.site-footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  font-size: 13px;
  color: var(--muted);
}

[data-theme='dark'] .site-footer {
  border-top-color: var(--border-dark, #334155);
}
```

- [ ] **Step 3: Commit**

```bash
git add vue/src/App.vue vue/src/assets/blog.css
git commit -m "feat: 添加页脚组件显示版权信息"
```

---

### Task 17: 验证和测试

- [ ] **Step 1: 执行建表 SQL**

```bash
# 连接 MySQL 执行 sql/site-config.sql
```

- [ ] **Step 2: 启动后端确认 API 正常**

```bash
# 启动 Spring Boot 应用
# 验证 GET /api/v1/site/config 返回默认配置
curl http://localhost:8082/api/v1/site/config
```

- [ ] **Step 3: 构建前端并验证**

```bash
cd vue && npm run build
# 验证前端各页面能正常显示配置内容
```

- [ ] **Step 4: 登录后台修改配置并确认前端实时生效**

---

### Task 18: 最终提交

- [ ] **Step 1: 如有遗漏文件一并提交**

```bash
git status
git add -A
git commit -m "feat: 站点配置动态化 — 个人信息从后端可编辑"
```
