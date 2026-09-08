# 幂等组件设计详解：本地锁 + 分布式锁 + 缓存标识符 双重保障

> **一句话亮点**：通过 `@RepeatExecuteLimit` 注解 + AOP 切面，在业务方法外层组合「本地锁（LocalLockCache）+ 分布式锁（Redisson FairLock）+ Redis 幂等标识符（repeat_flag）」三层防护，配合**双重检测**（double-check），从根上防止用户重复点击与 MQ 重复消费导致的脏数据与重复执行。支持按业务场景扩展三种失败策略：**缓存拒绝 / 返回相同结果 / 执行降级逻辑**。

---

## 目录

- [1. 组件定位](#1-组件定位)
- [2. 设计思想与整体架构](#2-设计思想与整体架构)
- [3. 模块与依赖结构](#3-模块与依赖结构)
- [4. 核心代码详解](#4-核心代码详解)
  - [4.1 注解 `@RepeatExecuteLimit`](#41-注解-repeatexecutelimit)
  - [4.2 核心切面 `RepeatExecuteLimitAspect`](#42-核心切面-repeatexecutelimitaspect)
  - [4.3 锁名生成：`AbstractLockInfoHandle`（SpEL 解析）](#43-锁名生成-abstractlockinfohandlespel-解析)
  - [4.4 本地锁缓存 `LocalLockCache`](#44-本地锁缓存-locallockcache)
  - [4.5 分布式锁框架 `ServiceLock`（`ManageLocker` / `ServiceLockFactory`）](#45-分布式锁框架-servicelockmanagelocker--servicelockfactory)
  - [4.6 缓存标识操作 `RedissonDataHandle`](#46-缓存标识操作-redissondatahandle)
  - [4.7 自动装配 `RepeatExecuteLimitAutoConfiguration`](#47-自动装配-repeatexecutelimitautoconfiguration)
- [5. 三种失败策略](#5-三种失败策略)
- [6. 关键机制深度解析](#6-关键机制深度解析)
- [7. 业务落地场景](#7-业务落地场景)
- [8. 时序图](#8-时序图)
- [9. 移植到其他项目指南](#9-移植到其他项目指南)
- [10. 注意事项与边界问题](#10-注意事项与边界问题)
- [附录：源码文件索引](#附录源码文件索引)

---

## 1. 组件定位

| 维度 | 说明 |
|------|------|
| **解决什么问题** | 用户快速重复点击（前端防抖失效/绕过）、MQ 消息重复投递、RPC 超时重试等导致**同一业务方法被执行多次** |
| **后果** | 重复下单、重复扣库存、重复支付、重复发放权益、API 重复入库 |
| **组件形式** | Spring 注解 + AOP 切面，**业务零侵入**，打上 `@RepeatExecuteLimit` 即可 |
| **关键指标** | 幂等校验必须是 **O(1) 快路径**（先查缓存），并发下必须**绝对不重**（锁串行化 + 双重检测） |

---

## 2. 设计思想与整体架构

核心设计用一个词概括：**把「并发控制」和「结果标记」拆开，再组合**。

- **并发控制（防同时执行）**：`本地锁` 管单 JVM 内的并发，`分布式锁` 管跨 JVM 的并发。二者串行后，任意时刻只有一个线程能真正执行业务。
- **结果标记（防重复提交）**：业务执行成功后，在 Redis 写入一个带 TTL 的幂等标识 `repeat_flag`，后续相同请求（Key 相同）一查即拒绝，不需要再进锁。

```
用户请求 / MQ 消息
        │
        ▼
┌─────────────────────────────────────────────────────────────┐
│              RepeatExecuteLimitAspect（@Order(-11)）          │
│                                                             │
│  ① 快路径：Redis 幂等标识检查（已 success → 拒绝）            │
│        │                                                     │
│  ② 本地锁：LocalLockCache 取 Caffeine 缓存 ReentrantLock      │
│        │   （同一 JVM 内串行，减少对 Redis 锁的压力）           │
│        ▼                                                     │
│  ③ 分布式锁：Redisson FairLock，tryLock(wait=0) 非阻塞         │
│        │   （跨 JVM 串行，获取失败 → 拒绝）                    │
│        ▼                                                     │
│  ④ 双重检测：再查一次 Redis 幂等标识                          │
│        │   （防止 ②③ 排队期间首个请求已执行完并写标）          │
│        ▼                                                     │
│  ⑤ 执行业务方法 joinPoint.proceed()                          │
│        │                                                     │
│  ⑥ 成功 → 写 Redis 幂等标识，TTL = durationTime               │
│        ▼                                                     │
│  finally：释放分布式锁 + 释放本地锁                            │
└─────────────────────────────────────────────────────────────┘
```

**"双重保障"的两层含义**：

1. **锁的双层保障**：本地锁（单机内快、省 Redis）→ 分布式锁（跨机可靠）。本地锁先挡掉大部分同机并发，只有"本地锁争到"的请求才去碰 Redis 分布式锁，大幅降低 Redis 锁的争用。
2. **锁 + 标识的双重检测**：① 前置快路径查标识（免锁拒绝）、④ 获锁后二次查标识（防竞争窗口）。即使两个请求同时过了 ①，也会在 ④ 被拦截，**绝不重复执行**。

---

## 3. 模块与依赖结构

组件位于独立 Maven 模块，天然可被任意服务引用复用：

```
damai-redisson-framework
└── damai-redisson-service-framework
    ├── damai-redisson-common-framework    ← 基础能力：RedissonClient、LocalLockCache、RedissonDataHandle、LockInfoHandle 抽象
    ├── damai-service-lock-framework       ← 分布式锁：ManageLocker、ServiceLockFactory、4 种锁实现、ServiceLockAspect
    └── damai-repeat-execute-limit-framework  ← 本组件（防重复执行幂等）
        ├── annotation/RepeatExecuteLimit.java
        ├── aspect/RepeatExecuteLimitAspect.java
        ├── constant/RepeatExecuteLimitConstant.java
        ├── core/RepeatExecuteLimitConstants.java         ← 业务名常量（隔离业务）
        ├── lockinfo/impl/RepeatExecuteLimitLockInfoHandle.java
        ├── config/RepeatExecuteLimitAutoConfiguration.java
        └── resources/META-INF/spring/AutoConfiguration.imports  ← Spring Boot 自动装配入口
```

`pom.xml` 依赖关系：`repeat-execute-limit` → `redisson-common` + `service-lock`。

---

## 4. 核心代码详解

### 4.1 注解 `@RepeatExecuteLimit`

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatExecuteLimit {

    /** 业务名称：用于生成锁/标识的 Key，区分不同业务 */
    String name() default "";

    /** 幂等 Key（SpEL 表达式）：定位"哪次请求算重复" */
    String [] keys();

    /** 幂等保持时长（秒）。>0 时：执行成功后写 Redis 标识并保留 durationTime 秒；=0 时：仅防"并发重复执行" */
    long durationTime() default 0L;

    /** 命中防重时的提示语 */
    String message() default "提交频繁，请稍后重试";
}
```

**Key 设计原则**：`keys` 必须能**唯一标识一次业务动作**。例如"同一用户对同一节目下单"用 `{"#dto.userId", "#dto.programId"}`；"同一个订单号消费"用 `{"#orderMq.orderNumber"}`。

### 4.2 核心切面 `RepeatExecuteLimitAspect`

> 完整源码（`@Order(-11)`，保证在事务切面之外、分布式锁切面 `@Order(-10)` 之外执行）：

```java
@Slf4j
@Aspect
@Order(-11)
@AllArgsConstructor
public class RepeatExecuteLimitAspect {

    private final LocalLockCache localLockCache;                 // 本地锁缓存
    private final LockInfoHandleFactory lockInfoHandleFactory;   // 锁名工厂
    private final ServiceLockFactory serviceLockFactory;         // 分布式锁工厂
    private final RedissonDataHandle redissonDataHandle;         // Redis 缓存操作

    @Around("@annotation(repeatLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatLimit) throws Throwable {
        long durationTime = repeatLimit.durationTime();
        String message = repeatLimit.message();
        Object obj;

        // 1. 根据业务名 + SpEL keys 生成锁名（跨环境前缀 + 业务前缀 + name + 动态key）
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.REPEAT_EXECUTE_LIMIT);
        String lockName = lockInfoHandle.getLockName(joinPoint, repeatLimit.name(), repeatLimit.keys());

        // 2. 拼出 Redis 幂等标识 key：repeat_flag + lockName
        String repeatFlagName = PREFIX_NAME + lockName;
        String flagObject = redissonDataHandle.get(repeatFlagName);

        // 3. 【快路径】幂等标识已存在 → 直接拒绝（不抢锁，O(1)）
        if (SUCCESS_FLAG.equals(flagObject)) {
            throw new DaMaiFrameException(message);
        }

        // 4. 【本地锁】同一 JVM 内串行（非阻塞 tryLock，抢不到直接拒绝）
        ReentrantLock localLock = localLockCache.getLock(lockName, true);
        boolean localLockResult = localLock.tryLock();
        if (!localLockResult) {
            throw new DaMaiFrameException(message);
        }

        try {
            // 5. 【分布式锁】跨 JVM 串行（公平锁，waitTime=0 非阻塞）
            ServiceLocker lock = serviceLockFactory.getLock(LockType.Fair);
            boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, 0);
            if (result) {
                try {
                    // 6. 【双重检测】获锁后二次检查幂等标识，杜绝竞争窗口
                    flagObject = redissonDataHandle.get(repeatFlagName);
                    if (SUCCESS_FLAG.equals(flagObject)) {
                        throw new DaMaiFrameException(message);
                    }
                    // 7. 执行业务
                    obj = joinPoint.proceed();
                    // 8. 成功后才写幂等标识（TTL = durationTime）
                    if (durationTime > 0) {
                        try {
                            redissonDataHandle.set(repeatFlagName, SUCCESS_FLAG, durationTime, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.error("getBucket error", e);
                        }
                    }
                    return obj;
                } finally {
                    lock.unlock(lockName);
                }
            } else {
                throw new DaMaiFrameException(message);
            }
        } finally {
            localLock.unlock();
        }
    }
}
```

**逐行解析**：

| 步骤 | 说明 |
|------|------|
| ③ 快路径 | 先查 Redis 标识，`success` 直接抛异常。绝大多数重复请求在这一步就被拒，**不产生任何锁开销** |
| ④ 本地锁 | `LocalLockCache` 用 Caffeine 缓存 `ReentrantLock`（公平锁），`tryLock()` 非阻塞；同一 JVM 内同 Key 只能进一个，其余直接拒绝 |
| ⑤ 分布式锁 | 经 `ServiceLockFactory` 取公平锁，`tryLock(lockName, SECONDS, 0)` **等待时间为 0**，拿不到就说明别的节点正在执行 → 拒绝（不排队、不空转） |
| ⑥ 双重检测 | 关键！在拿到分布式锁后**再查一次标识**：若并发请求 A 先执行完并写了标，此时才获锁的 B 会在此处被拦下，保证「并发也不重复」 |
| ⑦ 执行 | `joinPoint.proceed()` 真正执行业务方法 |
| ⑧ 写标识 | 执行成功后才写 `repeat_flag=success`，TTL=`durationTime` 秒；写标识失败仅记日志，**不阻断业务**（降级为"本次没标记"） |
| 释放 | `finally` 保证分布式锁、本地锁都释放 |

### 4.3 锁名生成：`AbstractLockInfoHandle`（SpEL 解析）

锁名 = `环境前缀-业务前缀:业务名:key1:key2`，其中 key 由 Spring SpEL 从方法参数动态解析。

```java
public abstract class AbstractLockInfoHandle implements LockInfoHandle {

    private static final String LOCK_DISTRIBUTE_ID_NAME_PREFIX = "LOCK_DISTRIBUTE_ID";
    private final ParameterNameDiscoverer nameDiscoverer = new ExtParameterNameDiscoverer();
    private final ExpressionParser parser = new SpelExpressionParser();

    protected abstract String getLockPrefixName();

    @Override
    public String getLockName(JoinPoint joinPoint, String name, String[] keys) {
        // 形如：damai-REPEAT_EXECUTE_LIMIT:create_program_order:1001:50001
        return SpringUtil.getPrefixDistinctionName() + "-" + getLockPrefixName() + SEPARATOR
                + name + getRelKey(joinPoint, keys);
    }

    private List<String> getSpElKey(String[] definitionKeys, Method method, Object[] parameterValues) {
        List<String> definitionKeyList = new ArrayList<>();
        for (String definitionKey : definitionKeys) {
            if (!ObjectUtils.isEmpty(definitionKey)) {
                // 用 Spring 表达式上下文解析 #xxx.yyy
                EvaluationContext context = new MethodBasedEvaluationContext(
                        null, method, parameterValues, nameDiscoverer);
                Object objKey = parser.parseExpression(definitionKey).getValue(context);
                definitionKeyList.add(ObjectUtils.nullSafeToString(objKey));
            }
        }
        return definitionKeyList;
    }
}
```

- `RepeatExecuteLimitLockInfoHandle.getLockPrefixName()` 返回 `REPEAT_EXECUTE_LIMIT`。
- `ExtParameterNameDiscoverer` 在 Spring 默认发现器基础上**追加 LocalVariableTable 发现器**，保证 `-parameters` 未开启时也能解析 `#对象.属性`。
- 最终 Redis 幂等标识 Key：`repeat_flagdamai-REPEAT_EXECUTE_LIMIT:create_program_order:1001:50001`。

### 4.4 本地锁缓存 `LocalLockCache`

用 Caffeine 做**锁池**：一个业务 Key 复用同一把 `ReentrantLock`，避免每次 new 一把锁导致锁失效。

```java
public class LocalLockCache {

    private Cache<String, ReentrantLock> localLockCache;

    /** 本地锁对象缓存时长（小时），默认 48 */
    @Value("${durationTime:48}")
    private Integer durationTime;

    @PostConstruct
    public void localLockCacheInit() {
        localLockCache = Caffeine.newBuilder()
                .expireAfterWrite(durationTime, TimeUnit.HOURS)
                .build();
    }

    /** Caffeine 的 get 是线程安全的：同 key 只创建一次锁 */
    public ReentrantLock getLock(String lockKey, boolean fair) {
        return localLockCache.get(lockKey, key -> new ReentrantLock(fair));
    }
}
```

> 设计要点：本地锁对象有 48h 过期，冷 Key 自动淘汰；同 Key 复用同一实例，`tryLock` 才能互斥。`fair=true` 保证同机内先到先得。

### 4.5 分布式锁框架 `ServiceLock`（`ManageLocker` / `ServiceLockFactory`）

幂等组件复用项目的通用分布式锁框架，本组件使用**公平锁（Fair）**。

```java
/** 锁类型 */
public enum LockType { Reentrant, Fair, Read, Write }

/** 享元缓存：四种锁各实例化一次 */
public class ManageLocker {
    private final Map<LockType, ServiceLocker> cacheLocker = new HashMap<>();
    public ManageLocker(RedissonClient redissonClient) {
        cacheLocker.put(Reentrant, new RedissonReentrantLocker(redissonClient));
        cacheLocker.put(Fair,     new RedissonFairLocker(redissonClient));
        cacheLocker.put(Write,    new RedissonWriteLocker(redissonClient));
        cacheLocker.put(Read,     new RedissonReadLocker(redissonClient));
    }
}

/** 工厂：按 LockType 选择锁实现 */
public class ServiceLockFactory {
    public ServiceLocker getLock(LockType lockType) {
        switch (lockType) {
            case Fair:   return manageLocker.getFairLocker();
            case Write:  return manageLocker.getWriteLocker();
            case Read:   return manageLocker.getReadLocker();
            default:     return manageLocker.getReentrantLocker();
        }
    }
}
```

本组件用 `RedissonFairLocker`（底层 `redissonClient.getFairLock(lockKey)`），`tryLock(waitTime=0)` 为**非阻塞**获取：

```java
public boolean tryLock(String lockKey, TimeUnit unit, long waitTime) {
    RLock lock = redissonClient.getFairLock(lockKey);
    try {
        return lock.tryLock(waitTime, unit);   // waitTime=0 → 立刻返回
    } catch (InterruptedException e) {
        return false;
    }
}
```

> 为什么用公平锁？幂等场景同 Key 并发度高，公平锁避免饥饿，保证"先到先得"，配合非阻塞 tryLock 让重复请求快速失败、不排队。

### 4.6 缓存标识操作 `RedissonDataHandle`

对 Redisson 的薄封装，用于读写幂等标识：

```java
public class RedissonDataHandle {
    private final RedissonClient redissonClient;

    public String get(String key) {
        return (String) redissonClient.getBucket(key).get();
    }

    public void set(String key, String value, long timeToLive, TimeUnit timeUnit) {
        redissonClient.getBucket(key).set(value, getDuration(timeToLive, timeUnit));  // RBucket + TTL
    }
}
```

### 4.7 自动装配 `RepeatExecuteLimitAutoConfiguration`

```java
public class RepeatExecuteLimitAutoConfiguration {

    /** 注册幂等专用 LockInfoHandle，Bean 名即类型标识 */
    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle() {
        return new RepeatExecuteLimitLockInfoHandle();
    }

    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(
            LocalLockCache localLockCache,
            LockInfoHandleFactory lockInfoHandleFactory,
            ServiceLockFactory serviceLockFactory,
            RedissonDataHandle redissonDataHandle) {
        return new RepeatExecuteLimitAspect(localLockCache, lockInfoHandleFactory,
                serviceLockFactory, redissonDataHandle);
    }
}
```

- `LocalLockCache` / `RedissonDataHandle` / `LockInfoHandleFactory` 由 `RedissonCommonAutoConfiguration` 提供；
- `ServiceLockFactory` / `ManageLocker` 由 `ServiceLockAutoConfiguration` 提供；
- 通过 `META-INF/spring/AutoConfiguration.imports` 注册，引入依赖即自动生效。

---

## 5. 三种失败策略

命中防重（缓存标识已存在 / 锁获取失败）后，组件可以采取不同策略。项目源码直接实现的是 **缓存拒绝**；另外两种作为**扩展设计**给出，可自行扩展切面支持。

| 策略 | 行为 | 适用场景 | 当前项目状态 |
|------|------|----------|-------------|
| **`CACHE_REJECT`（缓存拒绝）** | 直接抛异常，提示"提交频繁，请稍后重试" | 下单、支付、扣库存等**必须只执行一次**的关键业务 | ✅ 已实现（`throw new DaMaiFrameException(message)`） |
| **`RETURN_SAME_RESULT`（返回相同结果）** | 上次执行结果存入 Redis，重复请求直接返回上次结果 | 查询类、幂等可重放接口（如状态查询） | ⚙️ 扩展点 |
| **`EXECUTE_DOWNGRADE`（执行降级逻辑）** | 命中防重时执行一段降级/兜底逻辑（如返回空数据、触发补偿） | 非关键业务、节日秒杀弱依赖 | ⚙️ 扩展点 |

**缓存拒绝（现状实现）**：命中即抛 `DaMaiFrameException`，由全局异常处理器包装成统一响应。适用于下单、支付、扣库存这类**重复执行会产生脏数据**的强一致场景。

**扩展：返回相同结果 / 执行降级逻辑的切面改造示例**（在 `around` 中给注解增加 `strategy` 字段即可）：

```java
// 注解新增字段
IdempotentStrategy strategy() default IdempotentStrategy.CACHE_REJECT;
// 扩展点：标识已存在或获锁失败时
switch (strategy) {
    case RETURN_SAME_RESULT -> {
        Object cachedResult = redissonDataHandle.get(repeatFlagName + "_result", Object.class);
        return cachedResult;                       // 直接返回上次结果
    }
    case EXECUTE_DOWNGRADE -> {
        return invokeDowngrade(joinPoint, repeatLimit.downgradeMethod()); // 反射调用降级方法
    }
    default -> throw new DaMaiFrameException(message);  // CACHE_REJECT
}
```

> 参考：项目 `ServiceLockAspect` 已实现同款"降级逻辑"能力（`customLockTimeoutStrategy` 反射调用目标类方法），移植时可复用该模式。

---

## 6. 关键机制深度解析

### 6.1 双重检测（Double-Check）为什么必不可少？

两个并发请求 A、B 同时通过步骤③（都未看到标识）：

- A 抢到本地锁 + 分布式锁 → 执行 → 写标识 → 释放锁；
- B 在 A 释放锁后拿到分布式锁，此时**若不二次检查**，B 会再次执行业务 → 重复！
- 有了步骤⑥，B 获锁后一查标识发现已是 `success` → 直接拒绝。

> 结论：**只有锁（并发控制）+ 标识（结果标记）配合双重检测，才能同时保证"并发不重"和"串行重复不重"。**

### 6.2 切面执行顺序（`@Order`）

| 切面 | Order | 说明 |
|------|-------|------|
| `RepeatExecuteLimitAspect` | `-11` | 最外层，先做幂等检查 |
| `ServiceLockAspect` | `-10` | 次外层，分布式锁 |
| `@Transactional` | 默认最低 | 最内层，事务 |

幂等检查在最外层：**重复请求根本不会进入锁和事务**，最快拒绝。二者可叠加使用（见 7.4）。

### 6.3 环境隔离前缀 `prefix.distinction.name`

```java
public static final String PREFIX_DISTINCTION_NAME = "prefix.distinction.name";
public static final String DEFAULT_PREFIX_DISTINCTION_NAME = "damai";
```

锁名开头带环境前缀（默认 `damai`），不同环境（dev/test/prod）配不同前缀即可**天然隔离**，避免共用 Redis 时跨环境互相干扰。Kafka Topic 名同样复用了该前缀（`SPRING_INJECT_PREFIX_DISTINCTION_NAME`）。

### 6.4 幂等时间窗口 `durationTime`

- `durationTime > 0`：执行成功后写标识并保留 N 秒 → **窗口期内重复请求被拒绝**（防"短时间重复点击"）。
- `durationTime = 0`：不写标识 → 只防**并发同时执行**（执行期间重复请求被锁拦住），执行完后再来不拦（适合天然带唯一约束、仅需防并发的场景）。

### 6.5 为什么"本地锁 + 分布式锁"而不是只有分布式锁？

分布式锁就能保证跨机互斥，但每次都要打 Redis；本地锁在**单机内先把重复请求拦掉**，只有"本机第一个"才去抢 Redis 锁。同一 JVM 内 1000 并发同 Key，Redis 锁只被抢 1 次，**大幅降低 Redis 压力**。这是"双重保障"在性能上的意义。

---

## 7. 业务落地场景

### 7.1 防重复点击：下单接口

```java
@RepeatExecuteLimit(
        name = RepeatExecuteLimitConstants.CREATE_PROGRAM_ORDER,
        keys = {"#programOrderCreateDto.userId", "#programOrderCreateDto.programId"})
@Override
public String createOrder(ProgramOrderCreateDto programOrderCreateDto) {
    // 同一用户对同一节目在 durationTime 内重复点击 → 直接拒绝
    return baseProgramOrder.localLockCreateOrder(...);
}
```

### 7.2 防 MQ 重复消费：Kafka 创建订单

```java
// Kafka 消费端 → OrderService.createMq
@KafkaListener(topics = {"${spring.inject.prefix.distinction.name}-${spring.kafka.topic:create_order}"})
public void consumerOrderMessage(ConsumerRecord<String, String> consumerRecord) {
    ...
    String orderNumber = orderService.createMq(orderCreateMq);
}

// 消费方法：以订单号为幂等 Key
@RepeatExecuteLimit(name = CREATE_PROGRAM_ORDER_MQ, keys = {"#orderCreateMq.orderNumber"})
@Transactional(rollbackFor = Exception.class)
public String createMq(OrderCreateMq orderCreateMq) {
    // 即使 Kafka 重复投递同一条消息，orderNumber 相同 → 只执行一次
}
```

> Kafka 默认 At-Least-Once 语义，重启/重平衡会重复投递；这里以 `orderNumber` 做幂等 Key，**重复投递直接被拦**，配合 DB 唯一索引形成双重兜底。

### 7.3 API 调用记录入库幂等

```java
@RepeatExecuteLimit(name = CONSUMER_API_DATA_MESSAGE, keys = {"#apiData.id"})
public void saveApiData(ApiData apiData) {
    ApiData dbApiData = apiDataMapper.selectById(apiData.getId());
    if (Objects.isNull(dbApiData)) {
        apiDataMapper.insert(apiData);
    }
}
```

### 7.4 与 `@ServiceLock` 组合：取消订单

```java
@RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER, keys = {"#orderCancelDto.orderNumber"})  // 外层：防重复取消
@ServiceLock(name = UPDATE_ORDER_STATUS_LOCK, keys = {"#orderCancelDto.orderNumber"})      // 内层：改状态互斥
@Transactional(rollbackFor = Exception.class)
public boolean initiateCancel(OrderCancelDto orderCancelDto) { ... }
```

### 7.5 库存/座位操作幂等

```java
@RepeatExecuteLimit(name = REDUCE_REMAIN_NUMBER,
        keys = {"#reduceRemainNumberDto.programId", "#reduceRemainNumberDto.seatIdList"})
@Transactional(rollbackFor = Exception.class)
public Boolean operateSeatLockAndTicketCategoryRemainNumber(ReduceRemainNumberDto dto) { ... }

@RepeatExecuteLimit(name = PAY_OR_CANCEL_PROGRAM_ORDER,
        keys = {"#programOperateDataDto.programId", "#programOperateDataDto.seatIdList"})
@Transactional(rollbackFor = Exception.class)
public Boolean operateProgramData(ProgramOperateDataDto dto) { ... }
```

业务名统一在 `RepeatExecuteLimitConstants` 管理：

```java
public class RepeatExecuteLimitConstants {
    public static final String CONSUMER_API_DATA_MESSAGE = "consumer_api_data_message";
    public static final String CREATE_PROGRAM_ORDER      = "create_program_order";
    public static final String CANCEL_PROGRAM_ORDER      = "cancel_program_order";
    public static final String CREATE_PROGRAM_ORDER_MQ   = "create_program_order_mq";
    public static final String PROGRAM_CACHE_REVERSE_MQ  = "program_cache_reverse_mq";
    public static final String PAY_OR_CANCEL_PROGRAM_ORDER = "pay_or_cancel_program_order";
    public static final String REDUCE_REMAIN_NUMBER      = "reduce_remain_number";
}
```

---

## 8. 时序图

```
请求1(RequestA)                请求2(RequestB)              Redis               JVM本地锁
     │                              │                        │                    │
     │ ① 查 repeat_flag            │                        │                    │
     │────────────────────────────>│                        │                    │
     │<──────── null ──────────────│                        │                    │
     │ ② tryLock 本地锁            │                        │                    │
     │<──────── 成功 ──────────────│                        │                    │
     │ ③ tryLock 分布式锁(0s)      │                        │                    │
     │─────────────────────────────────────────────────────>│                    │
     │<──────── 成功 ──────────────│                        │                    │
     │ ④ 再查 repeat_flag          │                        │                    │
     │─────────────────────────────────────────────────────>│                    │
     │<──────── null ──────────────│                        │                    │
     │ ⑤ 执行业务                  │                        │                    │
     │ ⑥ set repeat_flag=success   │                        │                    │
     │    (TTL=durationTime)       │                        │                    │
     │─────────────────────────────────────────────────────>│                    │
     │ ⑦ 释放分布式锁/本地锁        │                        │                    │
     │                             │ ① 查 repeat_flag       │                    │
     │                             │────────────────────────>│                    │
     │                             │<──── success ──────────│  ← 直接拒绝（快路径）│
```

---

## 9. 移植到其他项目指南

### 9.1 引入依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>damai-repeat-execute-limit-framework</artifactId>
    <version>${revision}</version>
</dependency>
```

自动装配会带入 `redisson-common`、`service-lock`，项目只需配置好 Redisson/Redis 连接即可。

### 9.2 使用三步

1. **定义业务名常量**（建议集中管理，见 `RepeatExecuteLimitConstants`）；
2. **在方法上打注解**，选好幂等 Key：
   ```java
   @RepeatExecuteLimit(name = "create_order", keys = {"#dto.orderNumber"}, durationTime = 60)
   public String createOrder(CreateOrderDto dto) { ... }
   ```
3. **处理异常**：命中防重会抛 `DaMaiFrameException`，确保你的全局异常处理器能捕获并返回友好提示（项目有 `GlobalExceptionHandler` 统一处理）。

### 9.3 需要自行扩展的点

| 需求 | 做法 |
|------|------|
| 需要「返回相同结果」 | 在切面命中分支缓存方法返回值（序列化到 Redis），重复时反序列化返回；给注解加 `strategy` 字段 |
| 需要「执行降级逻辑」 | 参照 `ServiceLockAspect.handleCustomLockTimeoutStrategy` 的反射模式，命中时调用目标类降级方法 |
| 幂等窗口不同 | 调整 `durationTime`；短窗口（秒级）适合下单，长窗口（天级）适合发券防薅 |
| 自定义锁类型 | 把 `serviceLockFactory.getLock(LockType.Fair)` 改成可配置（`Reentrant` 等） |

### 9.4 最小可运行骨架（不含分布式环境时）

单机开发环境也可只保留**本地锁 + 缓存标识**：

```java
public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatLimit) throws Throwable {
    String lockName = ...;                              // 生成锁名
    String flag = redissonDataHandle.get(flagKey);      // 快路径
    if (SUCCESS.equals(flag)) throw ...;
    ReentrantLock local = localLockCache.getLock(lockName, true);
    if (!local.tryLock()) throw ...;
    try {
        flag = redissonDataHandle.get(flagKey);         // 双重检测
        if (SUCCESS.equals(flag)) throw ...;
        Object result = joinPoint.proceed();
        if (repeatLimit.durationTime() > 0) setFlag(flagKey, durationTime);
        return result;
    } finally {
        local.unlock();
    }
}
```

---

## 10. 注意事项与边界问题

| 风险点 | 说明与建议 |
|--------|-----------|
| **Redis 不可用** | 幂等组件强依赖 Redis：写标识失败仅记日志（业务可继续，但失去后续防重）；取锁失败则直接拒绝。生产建议 Redis 高可用（哨兵/集群），或用「DB 唯一索引」做最终兜底 |
| **标识与事务的一致性** | 标识在 `proceed()` 成功返回后写入，位于事务提交之后（切面在事务切面之外），正常路径一致；极端情况下（写标识失败）会退化为"本次不强防重"，可接受 |
| **锁 Key 设计** | `keys` 粒度决定幂等范围：粒度太粗会误伤正常请求（如只用 `programId` 会让该节目所有下单互斥），太细则失去防重意义。下单应组合 `userId + programId`（或 `orderNumber`） |
| **跨环境共用 Redis** | 务必通过 `prefix.distinction.name` 区分环境，否则 dev 的幂等标识会污染 prod |
| **本地锁与水平扩展** | 本地锁只在本 JVM 生效，多实例必须靠分布式锁保证互斥；不要只配本地锁上线多实例 |
| **`@ServiceLock` 组合顺序** | 幂等切面 `-11` 在分布式锁切面 `-10` 外层，重复请求不会进入锁；二者叠加时注意业务方法是否会被两个切面同时拦两次 |
| **方法的幂等 Key 参数** | SpEL 依赖参数名解析，编译时需保留参数名（`-parameters`）或依赖 `LocalVariableTableParameterNameDiscoverer` 兜底 |
| **超时释放** | 分布式锁使用 `tryLock(wait=0)` 非阻塞，业务执行时间超长时注意锁 watchDog 续期策略，避免锁提前过期导致并发进入 |

---

## 附录：源码文件索引

| 文件 | 作用 |
|------|------|
| `damai-repeat-execute-limit-framework/.../annotation/RepeatExecuteLimit.java` | 幂等注解 |
| `damai-repeat-execute-limit-framework/.../aspect/RepeatExecuteLimitAspect.java` | **核心切面** |
| `damai-repeat-execute-limit-framework/.../lockinfo/impl/RepeatExecuteLimitLockInfoHandle.java` | 幂等锁名前缀 |
| `damai-repeat-execute-limit-framework/.../config/RepeatExecuteLimitAutoConfiguration.java` | 自动装配 |
| `damai-redisson-common-framework/.../locallock/LocalLockCache.java` | 本地锁缓存（Caffeine 锁池） |
| `damai-redisson-common-framework/.../handle/RedissonDataHandle.java` | Redis Bucket 读写 |
| `damai-redisson-common-framework/.../lockinfo/AbstractLockInfoHandle.java` | 锁名生成 + SpEL 解析 |
| `damai-service-lock-framework/.../servicelock/factory/ServiceLockFactory.java` | 分布式锁工厂 |
| `damai-service-lock-framework/.../servicelock/impl/RedissonFairLocker.java` | 公平锁实现 |
| `damai-service-lock-framework/.../servicelock/aspect/ServiceLockAspect.java` | 分布式锁切面（降级策略参考） |
| 业务使用示例 | `ProgramOrderV4Strategy.createOrder` / `OrderService.createMq` / `ApiDataService.saveApiData` / `ProgramService.operateSeatLockAndTicketCategoryRemainNumber` / `OrderService.initiateCancel` |
