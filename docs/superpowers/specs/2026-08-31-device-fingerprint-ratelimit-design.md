# 基于设备指纹的网关限流设计（FingerprintJS + 指纹·IP 复合）

> 日期：2026-08-31
> 目标：解决「网关限流仅按 IP」的明显缺陷——IP 可伪造、NAT 共享、轮换代理都能打穿 per-IP 限流。引入**前端设备指纹**作为限流维度，网关按「指纹 + IP」复合 key 限流，对抗黑灰产脚本刷量。
> 范围：`vue/`（前端指纹计算与透传）+ `OpenBlog-gateway`（限流维度扩展）。business / message / Nginx **零改动**。

---

## 1. 背景与问题

### 1.1 现有限流实现（事实）

- 网关 `RateLimitFilter`（order=-1）：按 `openblog.gateway.rate-limit.rules` 匹配路径，复用 `framework-redis` 的 `SlidingWindowLimiter`（Redis ZSET + Lua 原子滑动窗口）。
- 限流 key：`gateway:rl:{IP}[_{uid}]_{path}`，scope 枚举仅 `IP` / `IP_UID` 两档。
- 现规则：login(IP,10/分)、email-code(IP,5/分)、refresh(IP,20/分)、comments(IP_UID,30/分)、forum(IP_UID,30/分)、feedback(IP,20/分)。

### 1.2 IP 限流的缺陷（业界共识）

1. **转发头可伪造**：`X-Forwarded-For` 首跳是客户端可控的；当前优先信任 `X-Real-IP`，但若边缘 Nginx 未覆写，攻击者可注入任意值轮换"IP"。
2. **旋转代理**：旋转住宅代理每请求换 IP，per-IP 桶形同虚设。
3. **NAT 共享误伤**：同一公网出口（公司/校园网/运营商 NAT）的合法用户共享一个 IP 桶，互相挤占配额。
4. **IPv6 隐私扩展**：OS 自动轮换地址。
5. 结论：**「基于可伪造转发地址的限流 = 客户端可控的限流」**。

### 1.3 业界方案调研（2026-08）

| 层 | 方案 | 取舍 |
|----|------|------|
| 客户端指纹 | FingerprintJS（Canvas/WebGL/字体/音频等 100+ 信号哈希，无痕/清 Cookie 也稳定） | 开源版纯前端计算、免费无第三方；跨浏览器稳定性约 50%（对本场景足够） |
| 服务端头部指纹 | UA + Accept-Language + Client Hints 哈希 | 零前端改动，但熵值低、同环境大量撞车 |
| 传输层 | TLS/JA4 指纹 | 最强但需 DPI，本项目不适用 |

业界已验证的落地形态：前端算指纹 → 每次请求带 header（`X-Device-ID` 类）→ 网关按指纹限流 → Nginx 透传 → 无指纹降级 IP 兜底 → 高频触发可入 Redis 黑名单。

---

## 2. 目标与非目标

### 目标

- 前端（FingerprintJS 开源版）计算稳定设备指纹，随请求透传。
- 网关新增 `FP_IP` scope：按「指纹 + IP」复合 key 滑动窗口限流。
- 无指纹 / 非法指纹 / Redis 故障全部 fail-safe 降级，**绝不因此产生 500**。
- 覆盖未登录敏感接口（login / email-code / refresh / feedback），已登录写接口保持 `IP_UID`。

### 非目标

- 不承诺对抗专业指纹伪造（见 §6 安全边界）。
- 不引入 FingerprintJS Pro（数据送第三方云 + 付费，博客规模是过度投资）。
- 不做行为分析 / 鼠标轨迹熵值 / 服务端 ML 风控。
- 不增加新响应码：复用现有 `4290`，前端拦截器零改动。

---

## 3. 整体链路

```
浏览器（前端 JS）
  │  FingerprintJS.load() → get() → visitorId（32 位 hex，硬件信号哈希，稳定且免 Cookie）
  │  main.js 入口处预热 getDeviceFingerprint()，后续请求通常已就绪
  │  每次请求（request / downloadWithAuth）带 header:
  │      X-Device-Fingerprint: <visitorId>
  ▼
Nginx（vue/nginx.conf，/api/ → 网关 8090）
  │  proxy_pass 默认透传所有请求头，无需改配置
  ▼
Gateway RateLimitFilter（8090，order=-1）
  │  scope=FP_IP：
  │      取 X-Device-Fingerprint → 正则校验 ^[A-Za-z0-9-]{16,64}$
  │      合法 → key = gateway:rl:{fp}_{ip}_{path}
  │      缺失/非法 → 降级  key = gateway:rl:{ip}_{path}
  ▼
Redis ZSET 滑动窗口（SlidingWindowLimiter，复用现有 Lua，原子计数）
```

---

## 4. 前端设计（vue/）

### 4.1 依赖

`package.json` 新增：`@fingerprintjs/fingerprintjs`（社区版，MIT，纯前端计算，无第三方网络调用）。

### 4.2 新增 `src/utils/deviceFingerprint.js`

职责：指纹单例计算 + 缓存。对外只暴露 `getDeviceFingerprint(): Promise<string|null>`。

```
设计要点：
- 模块内持 fpPromise（FingerprintJS.load() 单例，只 load 一次）+ 内存缓存 cached
- getDeviceFingerprint()：
    ① 内存缓存命中 → 直接返回
    ② sessionStorage 命中（键 dfp_visitor_id）→ 写入内存缓存并返回
       （visitorId 由硬件信号算出，存 sessionStorage 只是"免重算"优化，
         清掉 storage 会重算成同值，不破坏稳定性；用 session 而非 local，
         不把标识符永久落盘，隐私友好）
    ③ 否则 load() → get() → visitorId → 写入内存 + sessionStorage → 返回
    ④ 任何异常（隐私模式禁 API 等）→ resolve(null)，调用方不带该头
```

> 注：FingerprintJS 首次计算约 100ms，故需入口预热（见 4.4），避免登录这类首个敏感请求等不到指纹。

### 4.3 `src/api/http.js` 集成

- `request()` 与 `downloadWithAuth()` 在组装 headers 后，统一 `await attachFingerprint(headers)`。
- `attachFingerprint`：调 `getDeviceFingerprint()`，**带 300ms 超时**；拿到合法值则写 `X-Device-Fingerprint`，超时/为 null 则照常发请求（网关降级 IP 兜底，不影响可用性）。

### 4.4 入口预热

在应用入口（`main.js`）调用一次 `getDeviceFingerprint()`（fire-and-forget）。用户填写登录表单的几秒内指纹通常已缓存，首个敏感请求即带指纹。

### 4.5 前端降级矩阵

| 场景 | 行为 |
|------|------|
| 指纹计算就绪 | 全部请求带 `X-Device-Fingerprint` |
| 计算超时（>300ms） | 该请求不带，网关降级 IP |
| 计算失败（禁 API/隐私模式） | 一直不带，网关持续 IP 兜底 |
| 网关尚未支持该头（先于网关部署） | 多余 header 被忽略，无影响 |

---

## 5. 网关设计（OpenBlog-gateway）

### 5.1 `GatewayProperties`

`Scope` 枚举新增 `FP_IP`（保持 `IP` / `IP_UID` 不变）。

### 5.2 `RateLimitFilter` 改动

新增指纹提取方法：

```java
/** 提取并校验设备指纹头；非法（空/超长/含非法字符）一律视为无指纹。 */
private String deviceFingerprint(ServerWebExchange exchange) {
    String raw = exchange.getRequest().getHeaders().getFirst("X-Device-Fingerprint");
    if (raw == null) return null;
    String fp = raw.trim();
    // 32 位 hex 是常态；限 16~64 位字母数字-，防 header 注入与超长 Redis key
    return fp.matches("^[A-Za-z0-9-]{16,64}$") ? fp : null;
}
```

`applyRule` 内 key 构建三分支：

| scope | key |
|-------|-----|
| `IP`（不变） | `gateway:rl:{ip}_{path}` |
| `IP_UID`（不变） | `gateway:rl:{ip}_{uid}_{path}`，无 token 降级 `gateway:rl:{ip}_{path}` |
| `FP_IP`（新增） | 有合法指纹 → `gateway:rl:{fp}_{ip}_{path}`；无/非法 → `gateway:rl:{ip}_{path}` |

> 校验正则不含 `.`，所以伪造 `X-Device-Fingerprint: 1.2.3.4` 会被判非法 → 降级 IP，杜绝"指纹长得像 IP"的 key 语义歧义。

其余（fail-open、boundedElastic、4290 响应、规则匹配）全部保持不变。

### 5.3 `application.yaml` 规则变更

| path | scope（原 → 新） | limit/window |
|------|------------------|--------------|
| `/api/v1/auth/login` | IP → **FP_IP** | 10/分 不变 |
| `/api/v1/auth/register` | **新增 FP_IP**（匿名注册，原无规则） | 5/分 |
| `/api/v1/auth/email-code` | IP → **FP_IP** | 5/分 不变 |
| `/api/v1/auth/refresh` | IP → **FP_IP** | 20/分 不变 |
| `/api/v1/feedback/**` | IP → **FP_IP** | 20/分 不变 |
| `/api/v1/comments/**` | IP_UID（**保持**） | 30/分 不变 |
| `/api/v1/forum/**` | IP_UID（**保持**） | 30/分 不变 |

理由：未登录接口无 UID 维度，指纹是唯一强标识，最需要防 IP 轮换；已登录写接口用 UID 即可精准限流，指纹是冗余维度。

---

## 6. 安全边界与降级策略（诚实声明）

1. **指纹是"客户端可控的 header 值"**：前端算出的 visitorId 理论上可被逆向者伪造/轮换。本方案对抗的是**脚本刷量、换 IP 轮换、共享 NAT 误伤**这类黑灰产，**不承诺**对抗专业指纹伪造。
2. **复合 key 的价值**：指纹 + IP 一起限，爬虫换 IP 但指纹不变会撞桶；同 IP 的合法用户指纹不同各算各的桶。防御强度 = IP 维度 × 指纹维度叠加，而非单一信任。
3. **降级矩阵（全部 fail-safe）**：

| 触发点 | 行为 |
|--------|------|
| 无指纹头 | 按纯 IP 限流（等同现状） |
| 指纹头非法（空/超长/非法字符） | 按纯 IP 限流 |
| Redis 故障 | fail-open 放行（现有契约，绝不 500） |
| 规则未匹配 | 放行（现状） |

4. **Redis key 爆炸防护**：指纹长度被正则限制在 ≤64，ZSET 自带 TTL（`windowMs + 10s`），恶意注入超长/海量指纹无法拖垮 Redis。

---

## 7. 测试计划

### 7.1 网关单测（`RateLimitFilterTest` 扩展）

| 用例 | 断言 |
|------|------|
| `FP_IP` + 合法指纹头 | key = `gateway:rl:{fp}_{ip}_/api/...` |
| `FP_IP` + 无指纹头 | key = `gateway:rl:{ip}_/api/...`（降级） |
| `FP_IP` + 指纹超长（>64） | key 降级为纯 IP |
| `FP_IP` + 指纹含非法字符（含 `.`/换行） | key 降级为纯 IP |
| `FP_IP` + 指纹过短（<16） | key 降级为纯 IP |
| `FP_IP` + Redis 异常 | fail-open 放行（回归） |
| 既有 `IP` / `IP_UID` / disabled / 未匹配路径 | 全部回归不变 |

### 7.2 前端

- `cd vue && npm run build`（dist 生成即通过）——验证指纹库引入 + http.js 改动可打包。
- 手动冒烟：控制台看请求头带 `X-Device-Fingerprint`；开发者工具清 sessionStorage 后刷新，值不变（稳定性）；无痕窗口值仍稳定。

---

## 8. 部署与兼容性

| 项 | 结论 |
|----|------|
| 涉及部署单元 | 前端容器（vue/）+ 网关（8090） |
| business / message | 零改动，**无需三服务同步**（不涉及 Dubbo 签名 / JWT 密钥变更） |
| Nginx | 零改动（`proxy_pass` 默认透传所有请求头）。注：若未来 Nginx 配置改为显式剥离未知头，需补一行 `proxy_set_header X-Device-Fingerprint $http_x_device_fingerprint;` |
| 前后兼容 | 网关先上（读不到头 → IP 兜底）或前端先上（网关忽略多余头）均可独立部署，无顺序要求 |

---

## 9. 参考来源

- 小傅哥「手机号+验证码撞接口」防刷方案（指纹 ID 限流 + 黑名单）：https://www.cnblogs.com/xiaofuge/p/17884402.html
- Laravel 基于 X-Device-ID 头的设备指纹限流（Nginx 透传 + IP 兜底）：https://www.php.cn/faq/2947798.html
- Cloudflare Workers 反 DDoS（UA|Accept-Language|ASN|/24 复合指纹）：https://dev.to/phantam38/chong-ddos-layer-7-voi-cloudflare-workers-kv-4kgd
- 速率限制绕过手法（IP 轮换/header 篡改/多端点散布）：https://redteams.ai/topics/zh-TW/walkthroughs/attacks/api-rate-limit-bypass
- 「可伪造转发地址 = 客户端可控限流」（Peakhour）：https://www.peakhour.io/blog/proxy-bot-decisions-existing-cdn/
- FingerprintJS 开源方案介绍：https://blog.csdn.net/huangshanchu955/article/details/162471803
- FingerprintJS 官方 use-case 教程仓库：https://github.com/fingerprintjs/use-case-tutorials
