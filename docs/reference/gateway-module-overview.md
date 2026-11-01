# 网关模块整体设计总结（Spring Cloud Gateway）

> **模块定位**：`damai-gateway-service` 是整个大麦高并发购票系统的**统一流量入口**，承载所有请求的**安全校验、鉴权、限流防刷、灰度路由、链路追踪、统一异常处理**等横切能力，让下游业务微服务专注于业务本身。
>
> **文档目的**：抽取本网关模块的完整设计，供其他项目参照实现一套"能力等价"的网关层。
> **配套文档**：参数加密与签名细节见《网关参数安全-非对称加密与签名验证-设计文档.md》。

---

## 目录

1. [模块定位与职责](#1-模块定位与职责)
2. [技术栈与依赖](#2-技术栈与依赖)
3. [整体架构与请求处理链路](#3-整体架构与请求处理链路)
4. [全局过滤器体系](#4-全局过滤器体系)
5. [安全能力：参数加密 + 签名验证](#5-安全能力参数加密--签名验证)
6. [安全能力：Token 鉴权与用户身份还原](#6-安全能力token-鉴权与用户身份还原)
7. [三层限流与防刷体系](#7-三层限流与防刷体系)
8. [灰度发布路由](#8-灰度发布路由)
9. [全链路追踪与日志](#9-全链路追踪与日志)
10. [统一异常处理与降级](#10-统一异常处理与降级)
11. [API 调用数据采集](#11-api-调用数据采集)
12. [其他能力（CORS / 接口文档聚合 / 配置加密）](#12-其他能力cors--接口文档聚合--配置加密)
13. [配置汇总](#13-配置汇总)
14. [关键设计原则总结](#14-关键设计原则总结)
15. [在其他项目落地的移植清单](#15-在其他项目落地的移植清单)

---

## 1. 模块定位与职责

网关是系统的**北向流量出入口**，职责按"校验 → 增强 → 转发 → 兜底"四段划分：

```
                        ┌────────────────────────────────────────────┐
 客户端  ────────────▶  │  Gateway (damai-gateway-service :6085)     │
                        │                                            │
                        │  【校验】参数解密/验签 → Token 鉴权 → 防刷   │
                        │  【增强】traceId 生成 / 灰度标识 / userId    │
                        │          透传、调用数据采集                  │
                        │  【转发】Sentinel 流控 → 灰度负载均衡 → 路由  │
                        │  【兜底】统一异常 / 熔断降级 / CORS          │
                        └────────────────────┬───────────────────────┘
                                             │ 内网（Feign/LoadBalancer）
              ┌──────────────┬──────────────┼──────────────┐
              ▼              ▼              ▼              ▼
         user-service   program-service  order-service  pay-service ...
```

**核心职责**：
1. **统一安全收口**：参数非对称加密 + 签名验证、Token 鉴权在网关一次性完成，业务服务零侵入。
2. **统一流量治理**：全局限流、API 防刷、Sentinel 流控降级，多维度保护后端。
3. **统一上下文**：traceId（全链路追踪）、灰度标识、userId、渠道 code 的统一透传。
4. **统一兜底**：异常统一包装、熔断降级响应、跨域、接口文档聚合。

---

## 2. 技术栈与依赖

| 能力 | 技术选型 |
|------|----------|
| 网关框架 | **Spring Cloud Gateway**（WebFlux 响应式、非阻塞） |
| 服务发现 | Nacos（`spring-cloud-starter-alibaba-nacos-discovery`） |
| 客户端调用 | OpenFeign + Feign OkHttp |
| 限流 | Sentinel（`sentinel-spring-cloud-gateway-adapter`）+ 自研信号量 + Redis Lua |
| 缓存 | Redis（`damai-redis-framework`） |
| 消息 | Kafka（`spring-kafka`，API 调用数据采集） |
| 追踪 | SkyWalking（apm-toolkit-trace / log4j2） |
| 文档 | knife4j 网关聚合（`knife4j-gateway-spring-boot-starter`） |
| 配置加密 | jasypt |
| 灰度 | 自研 `damai-service-gray-transition-gateway-framework` |
| 唯一ID | `damai-id-generator-framework`（UidGenerator，traceId 等） |

启动类：`@EnableDiscoveryClient + @EnableFeignClients + @SpringBootApplication`。

---

## 3. 整体架构与请求处理链路

一条请求在网关的完整生命周期：

```
请求进入
  │
  ├─ [order=HIGHEST]   GatewayWorkRouteFilter    写入当前 exchange 到线程上下文
  │
  ├─ [order=-2]        RequestValidationFilter   ① traceId 生成/透传 → MDC
  │                                                ② 全局信号量限流（可选）
  │                                                ③ 读 body（JSON 才处理）
  │                                                ④ 参数解密/验签（encrypt=v2）
  │                                                ⑤ Token 校验 → userId
  │                                                ⑥ API 防刷（Lua 滑动窗口）
  │                                                ⑦ 重写 body（明文透传）＋
  │                                                    透传 code/userId/gray/traceId 头
  ├─ [order=-2]        ResponseValidationFilter  装饰响应：encrypt=v2 时加密 data
  │
  ├─ [order=-1]        SentinelGatewayFilter     Sentinel 网关流控（QPS/线程数）
  │
  ├─ 路由转发 ─────────▶ 负载均衡（灰度过滤）──▶ 下游微服务
  │                           │
  │                           └─ FilterLoadBalance：按 Nacos 元数据 gray 匹配实例
  │
  ├─ 异常路径：GatewayDefaultExceptionHandler  统一包装为 ApiResponse 返回
  │
  └─ [order=LOWEST-1]  GatewayWorkClearFilter   清理线程上下文
```

> **要点**：`RequestValidationFilter` 和 `ResponseValidationFilter` 的 `order` 均为 **-2**，优先于 Sentinel（-1）与路由执行，保证"先校验后放行"；`GatewayWorkClearFilter` 在链路末端清理上下文，防止响应式线程复用导致数据串号。

---

## 4. 全局过滤器体系

| 过滤器 | order | 作用 |
|--------|-------|------|
| `GatewayWorkRouteFilter` | `HIGHEST_PRECEDENCE` | 把当前 `ServerWebExchange` 存入 `GatewayContextHolder`（网关上下文入口） |
| `RequestValidationFilter` | `-2` | **请求核心过滤器**：安全校验 + 鉴权 + 防刷 + body 重写 + 头部透传 |
| `ResponseValidationFilter` | `-2` | **响应核心过滤器**：`data` 字段 RSA 加密（可选） |
| `SentinelGatewayFilter` | `-1` | Sentinel 网关流控 |
| `GatewayWorkClearFilter` | `LOWEST_PRECEDENCE - 1` | 清理 `GatewayContextHolder`（防上下文泄漏） |

### 4.1 RequestValidationFilter（请求核心过滤器）

`GlobalFilter` 实现，`order = -2`。核心能力与处理顺序（详见第 5、6、7 章）：

```
doFilter:
  ① 信号量限流（rate.switch=true 时）tryAcquire(1,1s)，失败抛"操作过于频繁"
  ② traceId：请求头没有则用 UidGenerator 生成，写入 MDC，透传下游
  ③ Content-Type 判断：
       application/json → 读取 body → execute() → doExecute(body) → 重写 body
       其它             → doExecute("")（不验签）→ 仅透传头
  ④ doExecute 核心：
       - 强制签名开关（allow-normal-access=false && no_verify=true → 拒绝）
       - 进入安全校验（body 非空 && 非白名单路径）：
           渠道数据(含密钥) → 解密(businessBody) → 验签 → Token校验 → userId还原
       - API 防刷 apiRestrict(userId, url, request)
       - 返回 { body: 明文业务参数, code, userId } 透传下游
```

**body 只读一次问题的解法**：WebFlux 中 `request.getBody()` 是流，只能读一次。用 `ServerRequest.bodyToMono(String.class)` 读取原始 body，经 `CachedBodyOutputMessage` 缓存后重写，再用 `ServerHttpRequestDecorator` 覆盖 `getBody()/getHeaders()`，实现"读取-校验-重写-透传"。

### 4.2 ResponseValidationFilter（响应加密过滤器）

`GlobalFilter` 实现，`order = -2`。参照 Spring Cloud Gateway 官方 `ModifyResponseBodyGatewayFilterFactory`：用 `ServerHttpResponseDecorator` 拦截响应流，读取完整响应体，满足条件（请求头 `encrypt=v2` 且非 `no_verify`）时对 `ApiResponse.data` 做 RSA 加密。

---

## 5. 安全能力：参数加密 + 签名验证

> **一句话**：在网关层实现 **参数非对称加密（RSA）与签名验证（SHA256withRSA）**，对请求参数加解密处理与签名校验，防止数据篡改与重放攻击。

完整协议、密钥管理、代码与前端实现见**独立文档**《网关参数安全-非对称加密与签名验证-设计文档.md》。这里只列与本模块设计相关的骨架：

| 子能力 | 实现 | 落点 |
|--------|------|------|
| 签名 | SHA256withRSA，参数字典序拼接 | `RsaSignTool`（common） |
| 加密 | RSA 2048 分段加解密 | `RsaTool`（common） |
| 渠道密钥 | 每渠道独立密钥对，DB 存储 + Redis 缓存 + Feign 回源 | `ChannelDataService` |
| 请求处理 | 解密 → 验签 → 鉴权 → 防刷 | `RequestValidationFilter` |
| 响应处理 | data 字段 RSA 加密 | `ResponseValidationFilter` |
| 强制开关 | `allow-normal-access=false` 时只允许签名访问 | `GatewayProperty` |

---

## 6. 安全能力：Token 鉴权与用户身份还原

**设计目标**：网关统一完成身份认证，把**用户身份**以 `userId` 头透传下游，下游业务无需再解析 Token。

**流程**：

```
请求带 header: token（JWT）
  │
  ├─ 白名单路径（skip.check.token.paths）→ 跳过 Token 校验
  │    （如：下单 /v1~v4、购票人管理、订单查询、支付回调等）
  │
  └─ 需鉴权路径：
       TokenService.parseToken(token, 渠道tokenSecret)
         └─ TokenUtil 解析 JWT → 取出 userId
       Redis: USER_LOGIN:{code}:{userId} 取用户会话
         ├─ 命中 → 返回 UserVo，userId 透传下游
         └─ 未命中 → 抛 LOGIN_USER_NOT_EXIST（登录用户不存在）
```

**关键点**：
- **JWT 密钥按渠道隔离**（`tokenSecret`），不同渠道互不可解析。
- Token 解析后**必须回查 Redis 会话**，支持服务端主动踢下线/改密码失效。
- 部分"游客可访问但登录后个性化"的路径（如节目详情）配置 `userId.paths`，有 token 时补拉 userId，没有则放行。

---

## 7. 三层限流与防刷体系

网关从**全局 → API 维度 → 流控规则**三个层次逐级保护后端：

```
第1层  全局信号量限流  RateLimiter(Semaphore)     整网关并发上限
第2层  API 防刷        Redis Lua 滑动窗口         按 IP[_userId]_url 频控
第3层  Sentinel 流控   SentinelGatewayFilter      QPS/线程数/熔断，规则可下钻
```

### 7.1 全局信号量限流（RateLimiter）

- 实现：`java.util.concurrent.Semaphore`，构造时给定 `maxPermitsPerSecond`。
- 用法：请求进来 `tryAcquire(1, 1s)`，获取不到立刻抛"操作过于频繁"异常，处理完 `release()`。
- 配置：`rate.switch`（开关，默认 false）、`rate.permits`（许可数，默认 200）。
- **定位**：保护网关自身进程不被打垮，是进程内粗粒度保护。

### 7.2 API 防刷（Redis Lua 滑动窗口）—— 核心亮点

`ApiRestrictService` + `ApiRestrictCacheOperate`（执行 `lua/apiLimit.lua`）+ Redis ZSet 实现**固定规则 + 深度分时规则**两档防刷。

**统计维度**：`IP[_userId]_url`（`userId` 有则拼上），即"同 IP 下同一用户的同一接口"。

**固定规则（apiRuleType=1）**：
- `INCR rule_key` 计数 → 超阈值后设置 `rule_limit_key`（TTL = 有效时间），进入封锁期。
- 封锁期内请求直接拒绝，形成"计数超限 → 封禁一段时间"的闸门。

**深度分时规则（apiRuleType=2）**：
- 在固定规则之上，支持**一天内多个时间窗口各自独立限流**（如：高峰期 19:00-20:00 限 1000，平峰限 200）。
- 用 **ZSet 存命中时间戳**，按时间窗口范围 `ZCOUNT` 统计窗口内命中次数，超过窗口阈值即触发对应窗口封锁。

**原子性**：整个判定在 **Lua 脚本内一次 Redis 调用完成**，天然原子，无并发超卖误判；结果返回 `{triggerResult, triggerCallStat, apiCount, threshold, messageIndex}`。

**触发后的动作**：命中规则 → `saveApiData` 通过 **Kafka** 异步上报调用记录 → 抛 `API_RULE_TRIGGER` 异常（可携带渠道自定义提示语）。

### 7.3 Sentinel 网关流控

- `SentinelGatewayBlockExceptionHandler`（`@Order(HIGHEST_PRECEDENCE)`）处理被 Sentinel 拦截的请求，返回统一降级文案。
- `SentinelGatewayFilter`（`order=-1`）执行流控逻辑。
- 规则可静态配置，也可接入 `sentinel-datasource-nacos` 从配置中心动态下发（仓库中已带依赖，按需启用）。

---

## 8. 灰度发布路由

**设计目标**：请求按 `gray` 标识路由到"灰度实例"或"生产实例"，支持新版本先在小流量上验证。

**全链路灰度**（网关 → 负载均衡 → 下游实例）：

```
客户端/负载 携带 header: gray=true/false
  │
  ▼
RequestValidationFilter：读取 gray 头 → 透传下游（gray 不丢失）
  │
  ▼
负载均衡 FilterLoadBalance：
  ServerGrayFilter（order=HIGHEST_PRECEDENCE）
    ① 取请求灰度值：优先 header → 线程上下文 → 本服务配置 server.gray
    ② 遍历候选实例，比对 Nacos 元数据 gray 字段
       元数据 gray == 请求 gray → 保留（路由到灰度/生产）
       不匹配 → 移除
    ③ 若灰度请求但不存在灰度实例 → 兜底路由到生产实例
    ④ 若服务列表为空 → 抛 SERVER_LIST_NOT_EXIST
```

**关键点**：
- 灰度标识存储于 **Nacos 实例元数据** `gray`（false=生产 / true=灰度），随实例注册上报。
- 使用**自定义负载均衡 Filter 链**（`FilterLoadBalance` → `AbstractServerFilter` 模板方法），灰度过滤作为其中一环，可扩展其他过滤规则。
- 网关自身也通过 `spring.cloud.nacos.discovery.metadata.gray` 标记自己是灰度网关还是生产网关。
- 线程上下文（`BaseParameterHolder` / `GatewayContextHolder`）负责在响应式链路上传递 gray 值。

---

## 9. 全链路追踪与日志

**设计目标**：一次请求在网关、多个微服务、线程池切换、MQ 之间拥有唯一 traceId，可全链路串联排查。

**实现层次**：

| 层次 | 实现 |
|------|------|
| traceId 生成 | `UidGenerator`（百度 uid）生成，写入 MDC + 请求头透传 |
| 响应式链路传播 | `LogHooks` 通过 Reactor `Hooks.onEachOperator` 在每个 operator 挂载 `MdcSubscriber`，从 Reactor Context 恢复 SkyWalking traceId 写入 MDC |
| 线程池传播 | `BaseParameterHolder` 提供 ThreadLocal 参数（traceId/gray）随上下文传递 |
| 外部系统 | 集成 SkyWalking（apm-toolkit），`SKY_WALKING_TRACE_ID` 入日志 |
| 上下文清理 | `GatewayWorkClearFilter` 链路末尾清理，防止线程复用串号 |

**MDC 常用字段**：`traceId`（业务自定义）、`skyWalkingTraceId`（SkyWalking）。

---

## 10. 统一异常处理与降级

### 10.1 统一异常处理器（GatewayDefaultExceptionHandler）

实现 `ErrorWebExceptionHandler`（`@Order(-2)`），网关内所有异常统一包装为 `ApiResponse`：

| 异常类型 | 响应 |
|----------|------|
| `ResponseStatusException` + 404 | `ApiResponse.error(NOT_FOUND)`（含 method + path） |
| `DaMaiFrameException`（业务异常） | 返回对应 code/msg，HTTP 200 |
| `ArgumentException`（参数异常） | code/msg + `data` 携带参数错误明细列表 |
| 其它 `Exception` | 统一 `-100 网络异常` |

- 业务异常返回 **HTTP 200 + 业务 code**（保持与前端 axios 拦截器判断一致）；
- 系统异常返回 HTTP 500；
- 响应已提交（`response.isCommitted()`）时直接放行，避免二次写入。

### 10.2 熔断降级

- `HystrixFallBackController` 提供 `/fallBackHandler` 兜底接口（返回"熔断器执行"）。
- 结合 Sentinel 的降级规则与 Feign 的 Hystrix/Sentinel 开关（`feign.hystrix.enabled` / `feign.sentinel.enabled`）。

---

## 11. API 调用数据采集

**设计目标**：将网关的调用行为（尤其防刷命中等关键事件）异步采集，供监控/风控分析。

- 触发点：`ApiRestrictService.saveApiData`（防刷命中时）。
- 数据：`ApiDataDto`（id / ip / url / 调用时间戳(日/时/分/秒粒度) / type）。
- 通道：**Kafka** 异步发送，topic 可配置（`spring.kafka.topic`），`ApiDataMessageSend` 组装前缀后发送，不影响主链路延迟。
- 生产者装配：`ProducerConfig` 通过 `@ConditionalOnProperty("spring.kafka.bootstrap-servers")` 条件装配，无 Kafka 环境自动跳过，**组件可选可降级**。

---

## 12. 其他能力（CORS / 接口文档聚合 / 配置加密）

| 能力 | 实现 |
|------|------|
| 跨域 | `Config.addCorsMappings`：`/**` 全放开，`allowCredentials(true)`，maxAge 3600 |
| 接口文档聚合 | knife4j 网关聚合（`strategy: discover`），自动聚合 Nacos 下所有微服务 OpenAPI 文档 |
| 配置加密 | jasypt（`PBEWithMD5AndDES`）加密敏感配置 |
| 通用 Bean | RestTemplate、HttpMessageConverters、线程池（`listen-start-thread-*`） |
| 限流信号量 | RateLimiter（见 7.1） |

---

## 13. 配置汇总

`application.yml` 关键配置及说明：

| 配置 | 默认 | 说明 |
|------|------|------|
| `server.port` | 6085 | 网关端口 |
| `spring.cloud.gateway.default-filters` | DedupeResponseHeader | 响应头去重（CORS 头冲突） |
| `api.limit.paths` | 空 | 需要 API 防刷的路径 |
| `skip.check.token.paths` | 下单/订单/购票人等 | 跳过 Token 校验白名单 |
| `skip.check.parmeter.paths` | `/**/alipay/notify` | 跳过参数验签白名单 |
| `allow.normal.access` | true | false 时强制只允许签名访问 |
| `userId.paths` | 节目详情 | 需要补拉 userId 的路径 |
| `rate.switch` / `rate.permits` | false / 200 | 全局信号量限流 |
| `spring.cloud.nacos.discovery.metadata.gray` | false | 网关自身灰度标记 |
| `spring.kafka.topic` | save_api_data | API 采集 topic |

---

## 14. 关键设计原则总结

1. **横切能力网关化，业务零侵入**：安全、鉴权、限流、追踪全部沉淀在网关，业务服务只收明文参数。
2. **过滤器按"先校验、后放行、末清理"排序**：安全过滤器 `-2` 优先，Sentinel `-1`，上下文清理在链路最末。
3. **多维度限流叠加**：全局信号量（进程级）→ API 防刷（分布式 Lua 滑动窗口）→ Sentinel（规则化流控），各司其职。
4. **原子化防刷**：Lua 脚本一次 Redis 调用完成"计数-判定-封禁"，杜绝并发误判。
5. **灰度贯穿负载均衡**：灰度不是入口放行，而是实例路由过滤，落点在 Nacos 元数据。
6. **响应式上下文管理**：ThreadLocal 在 Reactor 线程池下必须显式"入栈 + 清栈"，否则线程复用串号。
7. **组件可降级**：Kafka、Sentinel 数据源、限流开关均条件装配/可配置，弱化依赖。
8. **链路可观测**：traceId + SkyWalking + API 采集，全链路可追踪、可审计。

---

## 15. 在其他项目落地的移植清单

> 目标项目若使用 Spring Cloud Gateway，可将以下模块/代码整体搬移；若为 Nginx + Lua / Go 网关，需按"能力等价"翻译。

### 必做（安全底线）
| # | 事项 | 参考实现 |
|---|------|----------|
| 1 | 参数加密 + 签名验证 | 见《网关参数安全-非对称加密与签名验证-设计文档.md》 |
| 2 | Token 鉴权 + userId 透传 | `RequestValidationFilter` + `TokenService` |
| 3 | 统一异常处理 | `GatewayDefaultExceptionHandler` |
| 4 | 白名单/开关配置 | `GatewayProperty` |

### 强烈建议（高并发场景）
| # | 事项 | 参考实现 |
|---|------|----------|
| 5 | API 防刷（Redis Lua 滑动窗口） | `ApiRestrictService` + `apiLimit.lua` |
| 6 | 全局信号量限流 | `RateLimiter` |
| 7 | 灰度发布路由 | `ServerGrayFilter` + Nacos 元数据 |
| 8 | traceId 全链路透传 | `RequestValidationFilter` + MDC |

### 按需
| # | 事项 | 参考实现 |
|---|------|----------|
| 9 | Sentinel 流控 | `GatewaySentinelConfiguration` |
| 10 | API 调用数据采集（Kafka） | `ApiDataMessageSend` |
| 11 | knife4j 文档聚合 | 配置开启 |
| 12 | CORS / 配置加密 | `Config` / jasypt |

### 落地三步走建议
1. **阶段一（接入安全）**：验签 + Token 鉴权 + 统一异常，`allow-normal-access=false` 强制签名访问。
2. **阶段二（流量治理）**：开启 API 防刷、全局限流、Sentinel 规则，接入灰度。
3. **阶段三（可观测）**：traceId + SkyWalking + API 采集，监控大盘。

---

## 附录：过滤器 order 一览

| 顺序 | order | 过滤器 |
|------|-------|--------|
| 1 | `Integer.MIN_VALUE` | GatewayWorkRouteFilter（上下文入栈） |
| 2 | -2 | RequestValidationFilter（安全+鉴权+防刷+透传） |
| 3 | -2 | ResponseValidationFilter（响应加密） |
| 4 | -1 | SentinelGatewayFilter（流控） |
| 5 | 路由 + 灰度负载均衡 | Spring Cloud Gateway Router |
| 6 | `Integer.MAX_VALUE - 1` | GatewayWorkClearFilter（上下文清栈） |
| 兜底 | ErrorWebExceptionHandler(-2) | GatewayDefaultExceptionHandler（统一异常） |
