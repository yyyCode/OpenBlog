# framework-idempotent 通用幂等组件设计

> **一句话亮点**：通过 `@RepeatExecuteLimit` 注解 + AOP 切面，在业务方法外层组合「本地锁（Caffeine）+ 分布式锁（Redisson）+ Redis 幂等标识」三层防护，配合**双重检测**（double-check），防止用户重复点击 / RPC 超时重试导致的重复执行。支持两种失败策略：**缓存拒绝（CACHE_REJECT）** 与 **返回相同结果（RETURN_SAME_RESULT）**。
>
> 参考来源：`docs/幂等组件设计-防重复执行详解.md`（来自 damai 项目），按 OpenBlog 技术栈适配：Redisson 代替原 Redisson + 自研锁框架，`BizException` 代替 `DaMaiFrameException`，Redis Key 沿用 `RedisKeys` 规范。

---

## 1. 背景与问题

OpenBlog 中以下场景存在真实的"重复执行"风险（基于现有代码确认）：

- **评论 / 回复**（`CommentService.createTopLevel` / `reply`）：直接 `insert`，双击"发表评论"会发两条重复评论。
- **论坛发帖**：大概率同样问题（后续接入）。
- **点赞 / 收藏 / 关注**（`InteractionService`）：`selectOne` 查重 → `insert` 的 check-then-act 竞态，并发下可能插入重复行，且 `count` 字段"读出来 +1 再写回"存在丢更新（后续单独一轮接入）。

用户已确认**第一批只接入评论 / 回复**，聚焦验证组件后再推广。

## 2. 目标与范围

### 目标
- 提供 `@RepeatExecuteLimit` 注解，业务零侵入（打注解即生效）。
- 同时保证**串行重复不重**（重复点击）与**并发不重**（双重检测）。
- 支持 `CACHE_REJECT`（抛 `BizException`）与 `RETURN_SAME_RESULT`（返回上次结果）两种失败策略。

### 非目标（YAGNI）
- 不实现文档的 `EXECUTE_DOWNGRADE`（执行降级逻辑）策略。
- 不做 MQ 消费幂等场景（OpenBlog 主链路无 MQ，预留后续）。
- 不实现自研分布式锁框架，直接使用 Redisson（watchdog 续期 / 可重入交给它）。

## 3. 技术决策

| 决策点 | 结论 | 理由 |
|--------|------|------|
| 分布式锁 | **Redisson**（`redisson-spring-boot-starter`） | 用户选定；watchdog 续期、可重入、非阻塞 `tryLock` 开箱即用 |
| 本地锁 | **Caffeine 锁池**（`LocalLockCache`） | 同 JVM 同 Key 串行，减少 Redis 锁往返；项目已用 Caffeine |
| 幂等标识 | Redisson `RBucket`（TTL） | 读写简单，同 Redis 连接 |
| 失败策略 | `CACHE_REJECT` + `RETURN_SAME_RESULT` | 用户选定；评论场景返回上次结果 UX 更好 |
| 幂等 Key | **前端 requestId**（`userId + articleId + requestId`） | 用户选定；不误伤"同一内容再发一次" |
| 异常 | `BizException`（common 模块） | 与项目统一，全局异常处理已装配 |
| 环境隔离 | Key 前缀取 active profile | dev/test/prod 共用 Redis 时天然隔离 |

## 4. 架构与模块

新模块：`OpenBlog-framework/framework-idempotent`

```
OpenBlog-framework/framework-idempotent/
├── annotation/RepeatExecuteLimit.java          ← 注解
├── strategy/IdempotentStrategy.java            ← CACHE_REJECT / RETURN_SAME_RESULT
├── aspect/RepeatExecuteLimitAspect.java        ← 核心切面，@Order(-11)
├── lock/LocalLockCache.java                    ← Caffeine 锁池（同 Key 复用 ReentrantLock）
├── lock/RepeatExecuteLockSupport.java          ← Redisson 锁获取/释放封装
├── core/RepeatExecuteKeyBuilder.java           ← 锁名 + 标识 Key 生成
├── handle/RepeatExecuteFlagStore.java          ← Redis 标识读写（RBucket），接口化便于测试
├── config/IdempotentAutoConfiguration.java     ← 自动装配
├── config/IdempotentProperties.java            ← durationTime / envPrefix 等配置
└── resources/META-INF/spring/AutoConfiguration.imports
```

**依赖**：`redisson-spring-boot-starter`、`spring-boot-starter-aop`、`caffeine`、`OpenBlog-common`、`OpenBlog-framework-redis`（复用 `RedisKeys` 规范与 profile 前缀）。

**只被 business 依赖**：gateway / message 不引该模块，不引入 Redisson。

## 5. 注解与 Key 设计

```java
public @interface RepeatExecuteLimit {
    String name() default "";                    // 业务名，如 comment_create / comment_reply
    String[] keys();                             // SpEL，如 {"#articleId", "#uid", "#req.requestId"}
    long durationTime() default 30L;             // 幂等窗口（秒）
    IdempotentStrategy strategy() default IdempotentStrategy.CACHE_REJECT;
    String message() default "提交频繁，请稍后重试";
}
```

Key 规范（沿用 `RedisKeys`：`openblog:{领域}:{用途}:{...}`，环境用 profile 前缀隔离）：

- 锁名：`openblog:{profile}:repeat:lock:{name}:{key1}:{key2}`
- 标识 Key：`openblog:{profile}:repeat:flag:{name}:{key1}:{key2}`，值 `success`，TTL=`durationTime`
- 结果 Key（仅 `RETURN_SAME_RESULT`）：`openblog:{profile}:repeat:result:{name}:{key1}:{key2}`，序列化返回值，同 TTL

SpEL 解析复用 `AuditLogAspect` 既有模式（`DefaultParameterNameDiscoverer` + `SpelExpressionParser`，pom 已开 `<parameters>true</parameters>`）。

## 6. 切面流程

`RepeatExecuteLimitAspect`（`@Order(-11)`，在 `@Transactional` 外层）：

```
① 生成 lockName / flagKey / resultKey（KeyBuilder）
② 快路径：flagKey = "success"？
     ├─ 是 → RETURN_SAME_RESULT ? 返回 resultKey 反序列化结果
     │                       : 抛 BizException(message)
     └─ 否 → 继续
③ 本地锁 tryLock()：抢不到 → 按策略处理（拒绝/返回缓存结果）
④ Redisson RLock.tryLock(0)：抢不到 → 释放本地锁，按策略处理
⑤ 双重检测：再查 flagKey
     ├─ 是 "success" → 按策略处理（防 ③④ 排队期间首个请求已执行完）
     └─ 否 → 继续
⑥ obj = joinPoint.proceed()   ← 业务执行（事务在最内层，返回即已提交）
⑦ 成功后才写：flagKey=success（+ RETURN_SAME_RESULT 时 resultKey=序列化 obj），TTL=durationTime
    写失败仅记日志，不阻断业务（降级为"本次不强防重"）
⑧ finally：释放 Redisson 锁 → 释放本地锁
```

**关键机制**：
- **双重检测**：请求 A 执行完写 flag 并释放锁后，B 才获锁，若不做 ⑤，B 会再执行一次 → 重复。有 ⑤ 则 B 查 flag 已是 `success` → 拦截。
- **切面顺序**：幂等 `-11` 最外层 → `@Transactional` 最内层。`proceed()` 返回时事务已提交，⑦ 在事务提交后写标识，正常路径不会出现"标识写了但事务回滚"的不一致。
- **写失败降级**：⑦ 写失败仅记日志，业务可继续但失去后续防重，可接受。
- **业务异常**：`proceed()` 抛出的异常原样上抛，不写 flag，不吞异常。

## 7. 业务接入（第一批：评论 / 回复）

1. `CommentCreateRequest` 新增字段 `requestId`（String）。
2. `CommentService.createTopLevel(articleId, uid, req)`：
   `@RepeatExecuteLimit(name="comment_create", keys={"#articleId", "#uid", "#req.requestId"}, durationTime=30, strategy=RETURN_SAME_RESULT)`
3. `CommentService.reply(commentId, uid, req)`：
   `@RepeatExecuteLimit(name="comment_reply", keys={"#commentId", "#uid", "#req.requestId"}, durationTime=30, strategy=RETURN_SAME_RESULT)`
4. 前端：`vue/src` 评论组件提交时用 `crypto.randomUUID()` 生成 requestId，**每次提交（成功/失败）后重新生成**。

## 8. 测试策略（TDD）

- **可测性**：`RepeatExecuteFlagStore` 接口化，单测注入内存假实现；锁用 mock（`RLock`、`ReentrantLock`）。
- 单测覆盖：
  - 快路径命中 → `CACHE_REJECT` 抛 `BizException`；`RETURN_SAME_RESULT` 返回缓存结果
  - 本地锁 / 分布式锁抢不到 → 拒绝
  - 双重检测：获锁后 flag 已是 success → 拒绝
  - 成功路径：执行后写 flag（+ 结果），TTL 正确
  - `proceed()` 抛异常 → 不写 flag，异常上抛
- `RepeatExecuteKeyBuilder` Key 生成单测。
- 集成测试：`CommentService` 双击提交 → 第二次返回同一评论对象，DB 仅一条。

## 9. 风险与注意事项

| 风险 | 说明与应对 |
|------|-----------|
| Redisson auto-config 读不到现有 Redis 配置 | 先在 `IdempotentAutoConfiguration` 手动声明 `RedissonClient` Bean（读同一份 `spring.data.redis` 属性），以实测为准 |
| Redisson 与 `spring-boot-starter` 版本匹配 | 选与 Spring Boot 3.5 兼容的 Redisson 3.4x+ |
| 锁 Key 粒度 | `keys` 必须组合 `userId + articleId + requestId`，粒度太粗会误伤正常请求 |
| 单实例部署 | 分布式锁当前是"为多实例扩展预留"，面试如实说明 |
| 本地锁只在本 JVM 生效 | 多实例必须靠 Redisson 锁兜底互斥 |
| 结果序列化 | `RETURN_SAME_RESULT` 缓存的对象需 Jackson 可序列化（评论返回 `CommentThreadResponse` 纯 DTO，满足） |

## 10. 参考

- `docs/幂等组件设计-防重复执行详解.md`（原始设计，damai 项目）
- `OpenBlog-framework/framework-audit/.../aop/AuditLogAspect.java`（SpEL 参数名解析模式）
- `OpenBlog-framework/framework-redis/.../limiter/SlidingWindowLimiter.java`（Lua 原子性先例）
