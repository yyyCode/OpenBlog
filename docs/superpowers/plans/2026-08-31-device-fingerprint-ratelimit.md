# 基于设备指纹的网关限流实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 前端用 FingerprintJS 计算设备指纹并随请求透传，网关按「指纹 + IP」复合 key 限流，解决现有限流仅按 IP、可被换 IP 轮换/NAT 误伤打穿的缺陷。

**Architecture:** 前端 `deviceFingerprint.js` 单例计算并缓存 visitorId，`http.js` 统一附 `X-Device-Fingerprint` 头；网关 `Scope` 新增 `FP_IP`，`RateLimitFilter` 校验指纹头并构建 `gateway:rl:{fp}_{ip}_{path}` key，缺失/非法降级纯 IP。复用现有 `SlidingWindowLimiter`（Redis ZSET + Lua），fail-open 契约不变。

**Tech Stack:** `@fingerprintjs/fingerprintjs`（前端，MIT 社区版）、Spring Cloud Gateway（Java 17）、Redis ZSET 滑动窗口。

**设计文档:** `docs/superpowers/specs/2026-08-31-device-fingerprint-ratelimit-design.md`

---

### Task 1: 网关限流支持 FP_IP 设备指纹维度（TDD）

**Files:**
- Modify: `OpenBlog-gateway/src/test/java/com/yqz/openblog/gateway/filter/RateLimitFilterTest.java`
- Modify: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/config/GatewayProperties.java`
- Modify: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/filter/RateLimitFilter.java`

- [ ] **Step 1: 写失败测试**

在 `RateLimitFilterTest.java` 的 `@BeforeEach setUp()` 之后加一个私有常量与一个请求构造 helper，并追加 3 个测试方法（放在 `ipUidScope_degradesToIpWithoutToken` 之后、`redisError_failsOpen` 之前）：

```java
    /** 合法设备指纹：32 位 hex（FingerprintJS visitorId 形态）。 */
    private static final String FP_VALID = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";

    private MockServerWebExchange exchangeWithFp(String path, String fp) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("X-Forwarded-For", "1.2.3.4")
                        .header("X-Device-Fingerprint", fp)
                        .build());
    }

    @Test
    void fpIpScope_usesFingerprintAndIp() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchangeWithFp("/api/v1/auth/login", FP_VALID);
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue())
                .isEqualTo("gateway:rl:" + FP_VALID + "_1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fpIpScope_degradesToIpWithoutFingerprint() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchange("/api/v1/auth/login"); // 无指纹头
        filter.filter(ex, c -> Mono.empty()).block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
    }

    @Test
    void fpIpScope_degradesToIpOnInvalidFingerprint() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.FP_IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        String[] invalidFps = {
                "",      // 空
                "   ",   // 空白
                "short", // 过短（<16）
                // 超长（72 字符 >64）
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6",
                // 含非法字符（正则仅允许字母数字-，`;` 拒绝）
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6;",
                // 含点号（避免指纹长得像 IP 的语义歧义）
                "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4.123"
        };
        for (String fp : invalidFps) {
            MockServerWebExchange ex = exchangeWithFp("/api/v1/auth/login", fp);
            // 关键：每轮先清 mock 调用记录，否则 verify(tryAcquire) 默认"总共恰好1次"会在第 2 轮起报 Wanted 1 but was 2
            clearInvocations(limiter);
            filter.filter(ex, c -> Mono.empty()).block();
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(limiter).tryAcquire(keyCaptor.capture(), anyLong(), anyInt(), anyLong(), anyString());
            assertThat(keyCaptor.getValue()).isEqualTo("gateway:rl:1.2.3.4_/api/v1/auth/login");
        }
    }
```

> 需在测试类 import 区补 `import static org.mockito.Mockito.clearInvocations;`

- [ ] **Step 2: 跑测试确认编译失败**

Run: `mvn -pl OpenBlog-gateway -am test -Dtest=RateLimitFilterTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`

Expected: **编译失败**，报 `GatewayProperties.Scope` 中没有 `FP_IP`（`cannot find symbol`）。

- [ ] **Step 3: 最小改动让测试能编译——Scope 枚举加 FP_IP**

`GatewayProperties.java` 中：

```java
    public enum Scope {
        IP, IP_UID, FP_IP
    }
```

- [ ] **Step 4: 跑测试确认新行为红灯**

Run: `mvn -pl OpenBlog-gateway -am test -Dtest=RateLimitFilterTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`

Expected: `fpIpScope_usesFingerprintAndIp` **FAIL**（当前 FP_IP 落入 IP 分支，key 缺指纹前缀）；`fpIpScope_degradesToIpWithoutFingerprint` 与 `fpIpScope_degradesToIpOnInvalidFingerprint` **PASS**（降级行为与 IP 分支一致）。这是"新功能未实现"的正确红灯。

- [ ] **Step 5: 实现指纹提取与 key 构建**

`RateLimitFilter.java`：

把 `applyRule` 里 key 构建抽成 `buildKey`，新增 `deviceFingerprint` 方法：

```java
    private Mono<Void> applyRule(ServerWebExchange exchange, GatewayFilterChain chain,
                                 GatewayProperties.Rule rule) {
        return Mono.fromCallable(() -> {
            String key = buildKey(exchange, rule);
            try {
                return limiter.tryAcquire(key, rule.getWindowMs(), rule.getLimit(),
                        System.currentTimeMillis(), UUID.randomUUID().toString());
            } catch (RuntimeException e) {
                // Redis 不可用时放行（fail open），避免把每个限流路径都打成 500
                log.warn("rate limit backend unavailable, allowing request: key={} err={}", key, e.toString());
                return true;
            }
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(allowed -> {
            if (allowed) {
                return chain.filter(exchange);
            }
            log.warn("gateway rate limited: path={}", exchange.getRequest().getPath());
            return GatewayResponses.writeJson(exchange, objectMapper,
                    HttpStatus.TOO_MANY_REQUESTS, 4290, "请求过于频繁，请稍后再试");
        });
    }

    /** 按 scope 构建限流 key：IP / IP_UID / FP_IP（指纹缺失或非法时降级纯 IP）。 */
    private String buildKey(ServerWebExchange exchange, GatewayProperties.Rule rule) {
        String ip = clientIp(exchange);
        if (rule.getScope() == GatewayProperties.Scope.IP_UID) {
            String uid = resolveUid(exchange);
            return "gateway:rl:" + ip + (uid != null ? "_" + uid : "") + "_" + rule.getPath();
        }
        if (rule.getScope() == GatewayProperties.Scope.FP_IP) {
            String fp = deviceFingerprint(exchange);
            return "gateway:rl:" + (fp != null ? fp + "_" : "") + ip + "_" + rule.getPath();
        }
        return "gateway:rl:" + ip + "_" + rule.getPath();
    }

    /** 提取并校验设备指纹头；缺失或非法（空/超长/含非法字符）一律视为无指纹。 */
    private String deviceFingerprint(ServerWebExchange exchange) {
        String raw = exchange.getRequest().getHeaders().getFirst("X-Device-Fingerprint");
        if (raw == null) {
            return null;
        }
        String fp = raw.trim();
        // 32 位 hex 是常态；限 16~64 位字母数字-，防 header 注入与超长 Redis key
        return fp.matches("^[A-Za-z0-9-]{16,64}$") ? fp : null;
    }
```

> 说明：`clientIp`、`resolveUid`、`bearerToken` 方法保持原样不动。

- [ ] **Step 6: 跑测试确认全绿**

Run: `mvn -pl OpenBlog-gateway -am test -Dtest=RateLimitFilterTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`

Expected: 全部通过（新增 3 个 + 既有 8 个全绿）。

- [ ] **Step 7: 提交**

```bash
git add OpenBlog-gateway/src/test/java/com/yqz/openblog/gateway/filter/RateLimitFilterTest.java \
        OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/config/GatewayProperties.java \
        OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/filter/RateLimitFilter.java
git commit -m "feat(gateway): 限流支持 FP_IP 设备指纹维度

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: 网关启用 FP_IP 规则并全量构建

**Files:**
- Modify: `OpenBlog-gateway/src/main/resources/application.yaml`

- [ ] **Step 1: 未登录敏感接口规则切换为 FP_IP**

`application.yaml` 中，把以下 4 条规则的 `scope` 从 `IP` 改为 `FP_IP`（`limit`/`window-ms` 不动）：

```yaml
        - path: /api/v1/auth/login
          window-ms: 60000
          limit: 10
          scope: FP_IP
        - path: /api/v1/auth/email-code
          window-ms: 60000
          limit: 5
          scope: FP_IP
        - path: /api/v1/auth/refresh
          window-ms: 60000
          limit: 20
          scope: FP_IP
        - path: /api/v1/feedback/**
          window-ms: 60000
          limit: 20
          scope: FP_IP
```

`/api/v1/comments/**` 与 `/api/v1/forum/**` 保持 `scope: IP_UID`，**不要改**。

- [ ] **Step 2: 网关模块全量构建验证**

Run: `mvn -pl OpenBlog-gateway -am package`

Expected: `BUILD SUCCESS`（含既有测试，验证 yaml 绑定 `FP_IP` 不破坏上下文加载）。

- [ ] **Step 3: 提交**

```bash
git add OpenBlog-gateway/src/main/resources/application.yaml
git commit -m "feat(gateway): 登录/反馈等未登录接口启用 FP_IP 指纹限流

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 前端设备指纹计算与请求透传

**Files:**
- Modify: `vue/package.json`
- Create: `vue/src/utils/deviceFingerprint.js`
- Modify: `vue/src/api/http.js`
- Modify: `vue/src/main.js`

- [ ] **Step 1: 安装 FingerprintJS 依赖**

Run: `cd vue && npm install @fingerprintjs/fingerprintjs`

Expected: `package.json` 的 `dependencies` 新增 `"@fingerprintjs/fingerprintjs": "^4.x.x"`，`node_modules` 更新。

- [ ] **Step 2: 新增指纹单例模块**

创建 `vue/src/utils/deviceFingerprint.js`：

```js
/**
 * 设备指纹单例：基于 @fingerprintjs/fingerprintjs 计算稳定 visitorId。
 * visitorId 由浏览器硬件信号（Canvas/WebGL/字体/音频等）哈希得出，与 Cookie/存储无关，
 * 无痕窗口、清空存储后计算仍得同值。
 * sessionStorage 仅作"免重算"缓存——值来自计算而非存储，删掉重算同值，不破坏稳定性；
 * 用 session 而非 local，避免把标识符永久落盘，隐私友好。
 */
import FingerprintJS from '@fingerprintjs/fingerprintjs'

const STORAGE_KEY = 'dfp_visitor_id'

let fpPromise = null
let cached = null

function load() {
  if (!fpPromise) {
    fpPromise = FingerprintJS.load()
  }
  return fpPromise
}

/**
 * @returns {Promise<string|null>} 设备指纹；计算失败/不可用（隐私模式禁 API 等）时返回 null，
 *   由调用方按"无指纹"处理（网关降级 IP 限流）。
 */
export async function getDeviceFingerprint() {
  if (cached) return cached
  try {
    const stored = readStored()
    if (stored) {
      cached = stored
      return stored
    }
    const fp = await load()
    const result = await fp.get()
    cached = result.visitorId
    writeStored(cached)
    return cached
  } catch {
    return null
  }
}

function readStored() {
  try {
    return window.sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function writeStored(value) {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, value)
  } catch {
    // 隐私模式禁用存储 → 忽略，仅本次内存缓存
  }
}
```

- [ ] **Step 3: http.js 统一附指纹头**

`vue/src/api/http.js` 顶部 import 区加：

```js
import { getDeviceFingerprint } from '../utils/deviceFingerprint'
```

在 `buildUrl` 函数之后新增两个 helper：

```js
const FINGERPRINT_TIMEOUT_MS = 300

/** 尽力附带设备指纹头：300ms 内取不到（超时/失败/无指纹）则不带，网关降级 IP 兜底。 */
async function attachFingerprint(headers) {
  try {
    const fp = await withTimeout(getDeviceFingerprint(), FINGERPRINT_TIMEOUT_MS)
    if (fp) headers['X-Device-Fingerprint'] = fp
  } catch {
    // 超时或异常：不阻塞请求，交由网关 IP 兜底
  }
  return headers
}

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('fingerprint timeout')), ms)
    promise.then(
      (v) => { clearTimeout(timer); resolve(v) },
      (e) => { clearTimeout(timer); reject(e) }
    )
  })
}
```

在 `request()` 中，`headers` 对象构建之后、`if (options.withAuth)` 之前插入 `await attachFingerprint(headers)`。改后 `request()` 开头应为：

```js
export async function request(path, options = {}) {
  const isFormData = options.body instanceof FormData
  const headers = {
    ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
    ...(options.headers || {})
  }

  await attachFingerprint(headers)

  if (options.withAuth) {
    const token = getStoredAccessToken()
    if (!token) {
      const err = new Error('未登录')
      err.httpStatus = 401
      throw err
    }
    headers.Authorization = `Bearer ${token}`
  }
  // ...后续 fetch 逻辑不变
```

`downloadWithAuth()` 中，把内联 headers 改为变量并附指纹。现有代码：

```js
  const res = await fetch(buildUrl(path), {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` }
  })
```

改为：

```js
  const headers = { Authorization: `Bearer ${token}` }
  await attachFingerprint(headers)

  const res = await fetch(buildUrl(path), {
    method: 'GET',
    headers
  })
```

- [ ] **Step 4: 入口预热指纹**

`vue/src/main.js` 顶部加：

```js
import { getDeviceFingerprint } from './utils/deviceFingerprint'
```

在 `applyStoredTheme()` 之后、`createApp(...).mount(...)` 之前加一行（fire-and-forget，预热使登录等首个敏感请求大概率已带指纹头）：

```js
getDeviceFingerprint()
```

改完后 `main.js` 应为：

```js
import { createApp } from 'vue'
import './style.css'
import './assets/blog.css'
import App from './App.vue'
import router from './router/index'
import { applyStoredTheme } from './theme'
import { getDeviceFingerprint } from './utils/deviceFingerprint'

applyStoredTheme()
getDeviceFingerprint()

createApp(App).use(router).mount('#app')
```

- [ ] **Step 5: 前端构建验证**

Run: `cd vue && npm run build`

Expected: `dist/` 生成，无报错（FingerprintJS 依赖 + http.js/main.js 改动可正常打包）。

> 手动冒烟（可选，部署后）：控制台 Network 看请求头带 `X-Device-Fingerprint`；开发者工具清 sessionStorage 后刷新，值不变（稳定性）；无痕窗口值仍稳定。

- [ ] **Step 6: 提交**

```bash
git add vue/package.json vue/package-lock.json vue/src/utils/deviceFingerprint.js \
        vue/src/api/http.js vue/src/main.js
git commit -m "feat(frontend): 设备指纹计算与请求头透传（FingerprintJS）

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

> 注：若 `npm install` 生成了 `vue/package-lock.json`，一并提交；若该文件之前不存在则照常 add。

---

### 收尾验证

- [ ] **Step 1: git status 确认无意外文件**

Run: `git status`

Expected: 只剩设计文档/计划文档相关或本计划提交的文件，无 `docs/designs/` 下未跟踪文件被误 add。

- [ ] **Step 2: 全量回归（网关）**

Run: `mvn -pl OpenBlog-gateway -am package`

Expected: `BUILD SUCCESS`。

> 前端无测试框架，以 `npm run build` 通过为准（CLAUDE.md 约定）。部署时前端容器与网关需同时或先后上线均可（前后兼容，见设计文档 §8）。
