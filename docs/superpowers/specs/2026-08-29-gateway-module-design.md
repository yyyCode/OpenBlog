# OpenBlog API 网关模块设计（Spring Cloud Gateway）

> 更新日期：2026-08-29
> 关联文档：`docs/designs/api-gateway-security-ha.md`（阶段 1：按需引入 SCG）、
> `docs/designs/gateway/网关模块整体设计总结.md` 与 `docs/designs/gateway/网关参数安全-非对称加密与签名验证-设计文档.md`（参考项目「大麦」能力参考，本设计取其**子集**）
> 开发分支：`feat/gateway-module`（合入 master 走 PR，项目既有约定）

---

## 1. 背景与目标

OpenBlog 当前边缘是 Nginx 容器（静态 + `/api/**` 反代到 business:8082），business 单点承载全部核心 API。`api-gateway-security-ha.md` 阶段 1 给出的触发条件是「出现第二个对外 HTTP 服务」，现 message 尚未对外提供 HTTP（仅 Dubbo），按该文档结论网关属于**提前投资**——但用户决策：**现在引入，作为独立的 gateway 服务模块**，先把统一安全收口 / 限流 / 可观测能力沉淀到入口层。

本次目标：新增 **`OpenBlog-gateway`** 独立模块（Spring Cloud Gateway，WebFlux），承接 `/api/**` 的**核心安全 + 治理**能力，让 business 专注于业务。

### 目标清单

1. **统一入口**：`/api/**` 由 Nginx 反代到网关，网关再路由到 business（静态 URI），业务拓扑对前端隐藏 8082。
2. **统一认证粗校验**：网关对白名单外路径校验 JWT 有效性/过期，无效在边缘即 401；business 保留 Spring Security 完整角色授权不变。
3. **限流防刷**：复用 `framework-redis` 的 `SlidingWindowLimiter`，对登录/验证码/评论/论坛/反馈等敏感路径按 IP[_uid] 滑动窗口限流。
4. **可观测**：traceId 生成/透传，错误响应统一携带 traceId。
5. **统一兜底**：网关级 `ErrorWebExceptionHandler`，响应契约与 business 的 `ApiResponse` 完全一致（前端零改动）。
6. **CORS 收敛**：网关用 SCG `globalcors` 按真实域名白名单收敛；business 侧 `*` 同 PR 配套收紧（网关成为唯一浏览器入口后）。
7. **部署独立**：gateway 自身 Docker 化 + 接入 CI 独立链（与 business/message 流水线互不影响）。

### 非目标（Out of scope，v1 明确不做）

- **参数 RSA 加密 + SHA256withRSA 验签**、渠道密钥管理、响应加密——参考文档的核心安全能力，但需改前端（jsrsasign）+ 引入渠道/密钥表，博客站 HTTPS 下收益低，列为后续可选阶段。
- **灰度发布 / Sentinel 流控 / knife4j 文档聚合 / Kafka API 采集**——高并发场景能力，博客站规模用不上。
- **userId 透传、下游零鉴权**——business 保留授权，网关不做身份还原。
- **Nacos 服务发现 `lb://` 路由**——business 单实例 + 项目未引入 Spring Cloud Discovery（Dubbo 的 Nacos 注册 SCG 解析不到），v1 用静态 URI。
- **改动前端**——前端 `/api/...` 同域 + `Authorization: Bearer` 不变。

---

## 2. 现状（代码事实，已核实）

- **前端**：`vue/src/api/http.js` 用 `fetch`，base 为同域 `/api/...`，token 头 `Authorization: Bearer <accessToken>`；响应按 `ApiResponse{code, message, data, traceId}` 处理，401 触发重新登录跳转。
- **Nginx**（`vue/nginx.conf`）：`/api/` → `host.docker.internal:8082`；`/sitemap.xml`、`/robots.txt`、`/seo/**`、爬虫 `article/N` → 直连 business:8082。
- **business**：Spring Security + JWT（jjwt，HS256，claims `uid`/`role`，secret ≥32 字符，env `OPENBLOG_JWT_SECRET`），`@EnableMethodSecurity` 三级角色；CORS `allowed-origin-patterns: ["*"]`。
- **限流基础设施**：`framework-redis` 已有 `SlidingWindowLimiter.tryAcquire(key, windowMs, limit, nowMs, member)`（Redis ZSET + Lua 原子滑动窗口），当前仅用于阅读量/访问量计数。
- **common 自动装配**：`CommonAutoConfiguration` 注册 servlet 风格 `@RestControllerAdvice`（`@ConditionalOnWebApplication` 不区分类型）——**WebFlux 网关必须 exclude，只复用 `ApiResponse` 纯 POJO**。
- **模块结构**：根 pom `<modules>` = common / framework / api / message / business；无 Spring Cloud 依赖，仅 Boot 3.5.12 + Dubbo + Nacos client。

---

## 3. 目标架构

```
浏览器 (Vue3 dist)
   │  同域 /api/...（前端零改动，Bearer token 不变）
   ▼
Nginx 容器（边缘，18088→80）
   ├─ 静态资源 / index.html                                   （原样）
   ├─ /sitemap.xml、/robots.txt、/seo/**、/article/N(爬虫) → business:8082  （原样直连，绕过网关）
   └─ /api/**  ──►  OpenBlog-gateway:8090（新模块，WebFlux）
                        │
   ┌────────────────────┴───────────────────────────────────┐
   │  GlobalFilter 链（按 order 依次执行）                     │
   │   1. TraceIdFilter     order=-3  生成/透传 X-Trace-Id → MDC → 下游  │
   │   2. JwtCheckFilter    order=-2  白名单外校验 Bearer token → 401    │
   │   3. RateLimitFilter   order=-1  敏感路径按 IP[_uid] 滑动窗口限流    │
   │   └─ GatewayErrorHandler（ErrorWebExceptionHandler）统一兜底       │
   │  Route: /api/** → http://host.docker.internal:8082（静态 URI）     │
   └──────────────────────────────────────────────────────────┘
```

### 关键决策

| 决策 | 结论 | 理由 |
|------|------|------|
| 能力范围 | 核心安全+治理子集（见非目标） | 博客站规模，参考文档多数能力闲置；参数加密需改前端，成本 > 收益 |
| 认证职责 | 网关粗校验（有效性/过期）→ 401，**透传原 Authorization**；business 保留完整授权 | 改动最小、可回退；角色授权（ADMIN/AUTHOR/READER）仍由 business 权威判定 |
| 路由 | 静态 URI `http://host.docker.internal:8082` | 单实例 + 无 Spring Cloud Discovery；扩容后再切 `lb://` |
| 限流 | 复用 `framework-redis` `SlidingWindowLimiter`（Redis ZSET+Lua） | 零新依赖、已生产验证、原子滑动窗口 |
| 技术栈 | Spring Cloud Gateway 4.3.x（Spring Cloud 2025.0.x + Boot 3.5.12），WebFlux | 与现有 Java 17 栈同代际，官方网关 |
| 端口 | 8090 | 网关容器对外端口 |
| common 复用 | 仅 `ApiResponse` POJO，`exclude=CommonAutoConfiguration` | 避免 servlet `@RestControllerAdvice` 污染 WebFlux |
| Nginx 路径 | 仅 `/api/` 改指网关；sitemap/robots/seo/爬虫保持直连 business | 最小改动；SEO 路径不经过鉴权/限流，直连更稳 |

---

## 4. 组件设计

### 4.1 模块 `OpenBlog-gateway`

根 pom `<modules>` 新增 `OpenBlog-gateway`；父 pom 引入 Spring Cloud 2025.0.x BOM 管理依赖版本。

`pom.xml` 依赖：

| 依赖 | 说明 |
|------|------|
| `spring-cloud-starter-gateway-server-webflux` | SCG 4.3.x（响应式） |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | JWT 解析（版本对齐 business） |
| `framework-redis` | `SlidingWindowLimiter`、`RedisOps` |
| `OpenBlog-common` | **仅 `ApiResponse`**，见 4.6 排除策略 |
| `spring-boot-starter-test` + `spring-cloud-gateway-server-webflux` 测试支持 | WebTestClient |

### 4.2 GatewayApplication

```java
@SpringBootApplication(exclude = CommonAutoConfiguration.class)
@EnableConfigurationProperties({GatewayProperties.class, JwtProperties.class})
public class GatewayApplication { ... }
```

- `JwtProperties`：从 business 平移（`openblog.jwt.*`：secret / issuer / 过期秒数），网关与 business 用同一 JWT 密钥体系验签。
- 扫描包限定 `com.yqz.openblog.gateway.*`，不扫 common。

### 4.3 TraceIdFilter（order = -3）

- 取请求头 `X-Trace-Id`，无则生成（UUID 短串）。
- 写入 `MDC`（key `traceId`）与响应头。
- 向下游透传 `X-Trace-Id`。
- 链路末尾清理 MDC（响应式线程复用防串号）。

### 4.4 JwtCheckFilter（order = -2）

- **白名单**（`gateway.auth.skip.paths`）：登录、注册、验证码、文章/评论等公共读路径、静态鉴权外路径——直接放行（透传原 token）。
- 白名单外路径：`Authorization: Bearer <token>` 缺失 / jjwt 解析失败（签名/过期/格式）→ 401 `ApiResponse{4010, "登录已失效"}`。
- 校验通过：**原样透传**（不改 body、不额外加头），business 自己解析 `uid`/`role`。
- 解析逻辑：`Jwts.parser().verifyWith(Keys.hmacShaKeyFor(secret)).build().parseSignedClaims(token)`（与 `JwtService.parseToken` 一致）。

### 4.5 RateLimitFilter（order = -1）

- 规则（`gateway.rate-limit.rules`）：`path` / `windowMs` / `limit` / `scope`（IP | IP_UID），覆盖：
  - `/api/v1/auth/**`（登录、发送验证码）
  - `/api/v1/comments/**`、`/api/v1/forum/**`（发帖/回复）
  - `/api/v1/feedback/**`
- 判定：`SlidingWindowLimiter.tryAcquire(key, windowMs, limit, nowMs, member)`，key = `gateway:rl:{IP}[_{uid}]_{path}`，member = UUID 唯一值。
- **scope=IP_UID 的 uid 来源**：RateLimitFilter 执行时 token 已由 JwtCheckFilter（order -2 < -1）校验通过；对 IP_UID 规则，本过滤器用同一 jjwt 逻辑从 `Authorization: Bearer` 解析出 `uid`（无 token 的请求该规则退化为纯 IP 维度）。
- 超限 → 429 `ApiResponse{4290, "请求过于频繁，请稍后再试"}`。
- **响应式适配**：阻塞 Redis 调用包 `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`，禁止在 Netty 事件循环线程同步阻塞。

### 4.6 GatewayErrorHandler

`ErrorWebExceptionHandler`（order = -1）统一兜底：

| 场景 | HTTP | body（ApiResponse） |
|------|------|---------------------|
| token 缺失/无效/过期（白名单外） | 401 | `{code:4010, message:"登录已失效"}` |
| 限流命中 | 429 | `{code:4290, message:"请求过于频繁，请稍后再试"}` |
| 下游不可达 / 路由无匹配 | 502 | `{code:5000, message:"服务暂不可用"}` |
| 其它异常 | 500 | `{code:5000, message:"系统繁忙"}`（不泄露堆栈） |

- 每个错误响应带 `traceId`（从 TraceIdFilter 的 MDC/头读取）。
- 业务正常响应**原样透传**（网关不加密、不改 body）。
- 响应已提交（`isCommitted()`）时放行，避免二次写入。

### 4.7 GatewayProperties

`openblog.gateway.*`：

| 配置 | 默认 | 说明 |
|------|------|------|
| `openblog.gateway.auth.skip-paths` | `[/api/v1/auth/**, /api/v1/articles/**, /api/v1/comments/list/**, ...]` | 跳过 JWT 校验白名单 |
| `openblog.gateway.rate-limit.enabled` | `true` | 限流总开关 |
| `openblog.gateway.rate-limit.rules[].path` | — | 限流路径 |
| `openblog.gateway.rate-limit.rules[].window-ms` / `.limit` | `60000` / `60` | 窗口与阈值 |
| `openblog.gateway.rate-limit.rules[].scope` | `IP` | `IP` 或 `IP_UID` |

> **CORS 收敛**：走 SCG 内置 `spring.cloud.gateway.server.webflux.globalcors.cors-configurations`（SCG 4.3.x / Spring Cloud 2025.0.x 实测前缀，旧 `spring.cloud.gateway.globalcors.*` 在 4.3.5 不绑定，已用 javap 验证；`allowedOrigins` 填真实域名白名单，`allowCredentials=false` 与现状一致），不另设自定义 CORS 属性/过滤器，避免双 CORS 机制。business 侧 `allowed-origin-patterns: ["*"]` 的收紧作为同 PR 的配套改动（网关成为唯一入口后，直连 8082 已不对浏览器暴露）。

---

## 5. 错误处理与响应契约

- 网关错误响应格式 = business 的 `ApiResponse`（`code` 数字 + `message` + `traceId`），前端 `http.js` 现有分支（`code!==0` 弹 message、401 跳登录）**无需改动**。
- 网关成功路径：不触碰响应体，只透传。
- 网关不产生独立错误码体系，错误码沿用业务语义（4010/4290/5000 与 common 一致）。

---

## 6. 测试与验收

**单元测试（gateway 模块，WebTestClient）**：
- TraceIdFilter：无头生成 / 有头透传 / 下游头可见。
- JwtCheckFilter：白名单放行 / 有效 token 放行 / 缺失·无效·过期 token → 401。
- RateLimitFilter：窗口内超限 → 429 / 未超限放行（mock `SlidingWindowLimiter`）。
- GatewayErrorHandler：401/429/502 响应体 = ApiResponse 结构 + traceId。
- 路由冒烟：`/api/v1/**` 路由到 mock 下游（`RouteDefinition` 指向 wiremock/本地 stub）。

**业务回归**：business 无代码改动（仅 Nginx 代理目标变化），现有测试应原样通过。

**线上验收**：
- [ ] 登录/注册/文章读路径经网关正常（白名单逐条核对）。
- [ ] 未带 token 访问需鉴权路径 → 401，前端跳登录。
- [ ] 伪造/过期 token → 401。
- [ ] 高频调用登录接口 → 429 限流文案。
- [ ] 响应头 `X-Trace-Id` 存在，错误响应带 `traceId` 字段。
- [ ] Nginx 恢复直连 8082 的**回退路径**验证可行（配置级回退）。

---

## 7. 部署与 CI

- **`deploy/gateway/`**：`Dockerfile`（`eclipse-temurin:17-jre`，fat jar）+ `docker-compose.yml`（8090:8090，`restart: always`，`JAVA_OPTS` 对齐）。
- **CI（`.github/workflows/ci.yml`）**：`changes` 增加 `gateway` 输出（过滤：`OpenBlog-gateway/**`、`OpenBlog-common/**`、`OpenBlog-framework*/**`、`deploy/gateway/**`、`pom.xml`）→ `build-gateway` → `deploy-gateway`，**无跨链 needs**（与 business/message 双链并存，互不影响）。
- **Nginx 切换节奏**：网关上线后重建 vue 容器把 `/api/` 指向 8090。**上线清单顺序**：①部署网关容器 → ②重建 vue 容器（`/api/` → 网关）→ ③验证 → ④留回退预案（改回 8082 重建）。

---

## 8. 实施步骤（计划骨架）

1. 根 pom：Spring Cloud 2025.0.x BOM + `<modules>` 加 `OpenBlog-gateway`；模块骨架可编译。
2. 最小闭环：GatewayApplication + TraceIdFilter + GatewayErrorHandler + 静态路由（`/api/**` → 8082），跑通「路由 + traceId + 兜底」。
3. JwtCheckFilter + 白名单 + `JwtProperties`（与 business 同源）。
4. RateLimitFilter + GatewayProperties + 规则配置。
5. 单测补齐（WebTestClient）。
6. `vue/nginx.conf` `/api/` 改指 8090 + `deploy/gateway/` Docker 化。
7. CI 加 gateway 独立链 + README/文档更新。
8. 上线与回退预案验证。

---

## 9. 风险与回退

| 风险 | 缓解 |
|------|------|
| 网关成为新单点 | `restart: always`；网关只做轻量校验，故障时 Nginx 改回直连 8082（配置级回退，分钟级） |
| 白名单误伤公共路由 | 白名单精确到路径 + 上线联调清单逐条核对 |
| WebFlux 阻塞事件循环 | 限流/Redis 阻塞调用走 `boundedElastic` |
| common servlet 自动装配冲突 | gateway 显式 `exclude=CommonAutoConfiguration` |
| Nginx 与网关上线不同步 | 上线清单固定顺序（先网关后 vue 容器），留回退 |
| JWT 密钥漂移（网关/business 不一致） | 同源 `openblog.jwt.secret`（env `OPENBLOG_JWT_SECRET`），docker-compose 注入同一值 |

**回滚**：Nginx `/api/` 反代目标改回 `host.docker.internal:8082` 并重建 vue 容器；网关容器可保留或停用，业务完全无感。

---

## 10. 后续（非本次范围，供参考）

- 参数加密 + 签名（`docs/designs/gateway/网关参数安全-...`）：当需要防抓包篡改/重放时，在网关叠加 RSA + 验签 + 渠道密钥，需配套前端 jsrsasign 改造。
- `lb://` 服务发现：business 扩容多实例后，给 gateway 引入 `spring-cloud-starter-alibaba-nacos-discovery` 并让业务服务注册 Spring Cloud 实例。
- Sentinel 流控 / 灰度 / knife4j 聚合：按需从参考文档移植。
