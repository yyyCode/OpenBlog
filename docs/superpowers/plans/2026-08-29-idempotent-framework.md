# framework-idempotent 幂等组件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `OpenBlog-framework/framework-idempotent` 实现 `@RepeatExecuteLimit` 幂等注解组件（本地锁 + Redisson 分布式锁 + Redis 标识 + 双重检测，支持 `CACHE_REJECT` / `RETURN_SAME_RESULT`），第一批接入评论/回复，前端用 `crypto.randomUUID()` 提供 requestId。

**Architecture:** 注解 + AOP 切面（`@Order(-11)`，最外层）。流程：① 快路径查 Redis 标识 → ③ Caffeine 本地锁（`LocalLockCache`）→ ④ Redisson `RLock.tryLock(0)` 分布式锁 → ⑤ 双重检测 → ⑥ 执行业务 → ⑦ 成功后才写标识。标识用项目既有 `RedisOps`（内置容错），Redisson 仅用于分布式锁。命中防重按策略处理：`CACHE_REJECT` 抛 `BizException(4299)`；`RETURN_SAME_RESULT` 返回上次缓存结果（结果未落盘则降级为拒绝）。任一幂等 Key 为空则跳过幂等保护（防御，防错误配置锁死业务）。

**Tech Stack:** Java 17 · Spring Boot 3.5 · Spring AOP · Redisson 3.52.0（`redisson-spring-boot-starter`）· Caffeine 3.1.8 · 项目既有 `OpenBlog-framework-redis`（`RedisOps`）· JUnit 5 + Mockito（TDD）

**设计文档：** `docs/superpowers/specs/2026-08-29-idempotent-framework-design.md`

---

## 实现前必读（关键事实）

- `BizException` 只有构造器 `BizException(int code, String message)`（`OpenBlog-common/.../common/BizException.java`），**没有** message-only 构造器。命中防重用 `new BizException(4299, message)`，`4299` 定义在 `RepeatExecuteLimitConstant`。
- `RedisOps` 接口（`OpenBlog-framework/framework-redis/.../core/RedisOps.java`）已内置容错：`Optional<String> get(String key)` / `void set(String key, String value, Duration ttl)` / `void delete(String key)`。标识读写失败自动降级，无需额外 try-catch。
- 根 pom 的 `maven-compiler-plugin` 已开 `<parameters>true</parameters>`，`DefaultParameterNameDiscoverer` 能取到方法参数名，SpEL 可解析 `#articleId` 等。
- Redisson 3.52.0 的 starter 自动从 `spring.data.redis.*` 装配 `RedissonClient`（`application.yaml` 已配置 `spring.data.redis.host/port/password`）。与现有 `StringRedisTemplate` 共存不冲突。
- **与设计文档的一处实现级差异（合理收缩）**：设计文档写标识用 RBucket，实际改用项目既有 `RedisOps` 实现（接口 `RepeatExecuteFlagStore` 不变，更契合项目风格、容错免费）；Redisson 仅保留分布式锁职责。
- `@Order(-11)` 确保切面在 `@Transactional` 之外（最外层）；`proceed()` 返回时事务已提交，⑦ 写标识在提交后，正常路径无"标识写了但事务回滚"。

---

## 文件结构总览

**新建模块** `OpenBlog-framework/framework-idempotent/`：

| 文件 | 职责 |
|------|------|
| `pom.xml` | 模块定义（Redisson 3.52.0、AOP、Caffeine、common、framework-redis） |
| `src/main/java/com/yqz/openblog/idempotent/annotation/RepeatExecuteLimit.java` | 幂等注解 |
| `src/main/java/com/yqz/openblog/idempotent/strategy/IdempotentStrategy.java` | 失败策略枚举 |
| `src/main/java/com/yqz/openblog/idempotent/constant/RepeatExecuteLimitConstant.java` | 业务码 4299 / FLAG_SUCCESS |
| `src/main/java/com/yqz/openblog/idempotent/config/IdempotentProperties.java` | `openblog.idempotent.*` 配置 |
| `src/main/java/com/yqz/openblog/idempotent/config/IdempotentAutoConfiguration.java` | 自动装配 |
| `src/main/java/com/yqz/openblog/idempotent/core/RepeatExecuteKeyBuilder.java` | Key 生成 |
| `src/main/java/com/yqz/openblog/idempotent/core/RepeatExecuteKeyResolver.java` | SpEL 解析 |
| `src/main/java/com/yqz/openblog/idempotent/lock/LocalLockCache.java` | Caffeine 本地锁池 |
| `src/main/java/com/yqz/openblog/idempotent/lock/RepeatExecuteLockSupport.java` | Redisson 锁封装 |
| `src/main/java/com/yqz/openblog/idempotent/handle/RepeatExecuteFlagStore.java` | 标识存储接口 |
| `src/main/java/com/yqz/openblog/idempotent/handle/RedisRepeatExecuteFlagStore.java` | Redis 实现 |
| `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 自动装配入口 |

**测试（同一模块 `src/test/`）**：`core/RepeatExecuteKeyBuilderTest.java`、`core/RepeatExecuteKeyResolverTest.java`、`lock/LocalLockCacheTest.java`、`handle/InMemoryRepeatExecuteFlagStore.java`（测试辅助）、`aspect/RepeatExecuteLimitAspectTest.java`

**修改**：`OpenBlog-framework/pom.xml`（注册模块）、`OpenBlog-business/pom.xml`（依赖）、`CommentCreateRequest.java`（requestId）、`CommentService.java`（注解）、`vue/src/utils/uuid.js`（新建）、`vue/src/api/comment.js`、`vue/src/components/CommentSection.vue`、`vue/src/components/CommentItem.vue`

---

### Task 1: 模块骨架（pom + 父模块注册 + 自动装配占位）

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/pom.xml`
- Create: `OpenBlog-framework/framework-idempotent/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `OpenBlog-framework/pom.xml`

- [ ] **Step 1: 创建模块 pom**

创建 `OpenBlog-framework/framework-idempotent/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.yqz</groupId>
        <artifactId>OpenBlog-framework</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>OpenBlog-framework-idempotent</artifactId>
    <packaging>jar</packaging>
    <name>OpenBlog-framework-idempotent</name>
    <description>幂等框架：@RepeatExecuteLimit 注解 + AOP，本地锁 + Redisson 分布式锁 + Redis 标识双重检测</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson-spring-boot-starter</artifactId>
            <version>3.52.0</version>
        </dependency>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
            <version>3.1.8</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.yqz</groupId>
            <artifactId>OpenBlog-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.yqz</groupId>
            <artifactId>OpenBlog-framework-redis</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 注册父模块**

修改 `OpenBlog-framework/pom.xml`，在 `<modules>` 中加入 `framework-idempotent`：

```xml
    <modules>
        <module>framework-redis</module>
        <module>framework-elasticsearch</module>
        <module>framework-audit</module>
        <module>framework-idempotent</module>
    </modules>
```

- [ ] **Step 3: 创建自动装配占位**

创建 `OpenBlog-framework/framework-idempotent/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（内容下一步填，先建空文件）：

```
com.yqz.openblog.idempotent.config.IdempotentAutoConfiguration
```

（注意：此时该配置类还不存在，文件内容可先留空字符串占位，Task 9 再写入上面的类名；若 maven 报错先移除该文件，Task 9 重建。最稳妥：本步**只创建空文件**，Task 9 写入内容。）

- [ ] **Step 4: 编译验证**

Run: `cd E:\java\OpenBlog && mvn -q -pl OpenBlog-framework/framework-idempotent -am compile -DskipTests`
Expected: BUILD SUCCESS（无源码时也应通过）

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-framework/pom.xml OpenBlog-framework/framework-idempotent/pom.xml
git commit -m "build(idempotent): scaffold framework-idempotent module"
```

---

### Task 2: 注解 / 策略 / 常量 / 配置属性

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/strategy/IdempotentStrategy.java`
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/constant/RepeatExecuteLimitConstant.java`
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/annotation/RepeatExecuteLimit.java`
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/config/IdempotentProperties.java`

- [ ] **Step 1: 创建策略枚举**

```java
package com.yqz.openblog.idempotent.strategy;

public enum IdempotentStrategy {
    /** 命中防重直接抛 BizException */
    CACHE_REJECT,
    /** 命中防重时返回上次执行缓存的相同结果 */
    RETURN_SAME_RESULT
}
```

- [ ] **Step 2: 创建常量**

```java
package com.yqz.openblog.idempotent.constant;

public final class RepeatExecuteLimitConstant {
    private RepeatExecuteLimitConstant() {
    }

    /** 命中防重时的业务码 */
    public static final int REJECT_CODE = 4299;

    /** 标识写入成功的值 */
    public static final String FLAG_SUCCESS = "success";
}
```

- [ ] **Step 3: 创建注解**

```java
package com.yqz.openblog.idempotent.annotation;

import com.yqz.openblog.idempotent.strategy.IdempotentStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatExecuteLimit {

    /** 业务名：拼进锁与标识 Key，区分不同业务（如 comment_create / comment_reply） */
    String name() default "";

    /** 幂等 Key（SpEL 表达式）：定位"哪次请求算重复"，如 {"#articleId", "#uid", "#req.requestId"} */
    String[] keys();

    /** 幂等窗口（秒）。>0 时执行成功后写 Redis 标识并保留 durationTime 秒；=0 只防并发重复执行 */
    long durationTime() default 30L;

    /** 命中防重时的策略 */
    IdempotentStrategy strategy() default IdempotentStrategy.CACHE_REJECT;

    /** 命中防重（且无缓存结果可返回）时的提示语 */
    String message() default "提交频繁，请稍后重试";
}
```

- [ ] **Step 4: 创建配置属性**

```java
package com.yqz.openblog.idempotent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openblog.idempotent")
public class IdempotentProperties {

    /** 是否启用幂等组件，默认 true */
    private boolean enabled = true;

    /** Key 环境前缀；留空时自动取当前 active profile */
    private String envPrefix = "";

    /** 本地锁对象缓存时长（小时） */
    private int localLockCacheHours = 48;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnvPrefix() {
        return envPrefix;
    }

    public void setEnvPrefix(String envPrefix) {
        this.envPrefix = envPrefix;
    }

    public int getLocalLockCacheHours() {
        return localLockCacheHours;
    }

    public void setLocalLockCacheHours(int localLockCacheHours) {
        this.localLockCacheHours = localLockCacheHours;
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `cd E:\java\OpenBlog && mvn -q -pl OpenBlog-framework/framework-idempotent -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add OpenBlog-framework/framework-idempotent/src/main/java
git commit -m "feat(idempotent): add annotation, strategy, constants and properties"
```

---

### Task 3: `RepeatExecuteKeyBuilder`（TDD）

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/core/RepeatExecuteKeyBuilder.java`
- Test: `OpenBlog-framework/framework-idempotent/src/test/java/com/yqz/openblog/idempotent/core/RepeatExecuteKeyBuilderTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.yqz.openblog.idempotent.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatExecuteKeyBuilderTest {

    @Test
    void buildsKeysWithEnvPrefix() {
        RepeatExecuteKeyBuilder b = new RepeatExecuteKeyBuilder("prod");
        assertEquals("openblog:prod:repeat:lock:comment_create:42:7:abc",
                b.lockKey("comment_create", List.of("42", "7", "abc")));
        assertEquals("openblog:prod:repeat:flag:comment_create:42:7:abc",
                b.flagKey("comment_create", List.of("42", "7", "abc")));
        assertEquals("openblog:prod:repeat:result:comment_create:42:7:abc",
                b.resultKey("comment_create", List.of("42", "7", "abc")));
    }

    @Test
    void omitsEnvSegmentWhenBlank() {
        RepeatExecuteKeyBuilder b = new RepeatExecuteKeyBuilder("");
        assertEquals("openblog:repeat:lock:name:a", b.lockKey("name", List.of("a")));
    }

    @Test
    void trimsEnvPrefix() {
        RepeatExecuteKeyBuilder b = new RepeatExecuteKeyBuilder("  dev  ");
        assertEquals("openblog:dev:repeat:lock:name:a", b.lockKey("name", List.of("a")));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=RepeatExecuteKeyBuilderTest`
Expected: FAIL（编译错误：`RepeatExecuteKeyBuilder` 不存在）

- [ ] **Step 3: 实现**

```java
package com.yqz.openblog.idempotent.core;

import java.util.List;

/**
 * 幂等组件 Redis Key 生成。命名沿用 RedisKeys 规范：openblog:{env}:repeat:{用途}:{name}:{key...}
 */
public class RepeatExecuteKeyBuilder {

    private static final String PREFIX = "openblog";
    private static final String LOCK = "repeat:lock";
    private static final String FLAG = "repeat:flag";
    private static final String RESULT = "repeat:result";

    private final String envPrefix;

    public RepeatExecuteKeyBuilder(String envPrefix) {
        this.envPrefix = (envPrefix == null || envPrefix.isBlank()) ? "" : envPrefix.trim();
    }

    private String join(String kind, String name, List<String> keys) {
        StringBuilder sb = new StringBuilder(PREFIX);
        if (!envPrefix.isEmpty()) {
            sb.append(':').append(envPrefix);
        }
        sb.append(':').append(kind).append(':').append(name);
        for (String k : keys) {
            sb.append(':').append(k);
        }
        return sb.toString();
    }

    public String lockKey(String name, List<String> keys) {
        return join(LOCK, name, keys);
    }

    public String flagKey(String name, List<String> keys) {
        return join(FLAG, name, keys);
    }

    public String resultKey(String name, List<String> keys) {
        return join(RESULT, name, keys);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=RepeatExecuteKeyBuilderTest`
Expected: PASS（3 个用例）

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-framework/framework-idempotent
git commit -m "feat(idempotent): add RepeatExecuteKeyBuilder with tests"
```

---

### Task 4: `RepeatExecuteKeyResolver`（TDD）

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/core/RepeatExecuteKeyResolver.java`
- Test: `OpenBlog-framework/framework-idempotent/src/test/java/com/yqz/openblog/idempotent/core/RepeatExecuteKeyResolverTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.yqz.openblog.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepeatExecuteKeyResolverTest {

    static class TestReq {
        private String requestId;

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }

    static class Target {
        public void create(Long articleId, Long uid, TestReq req) {
        }
    }

    @Test
    void resolvesSpelKeysFromMethodArgs() throws Exception {
        Method method = Target.class.getMethod("create", Long.class, Long.class, TestReq.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);

        TestReq req = new TestReq();
        req.setRequestId("req-123");
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, req});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        List<String> keys = resolver.resolve(pjp, new String[]{"#articleId", "#uid", "#req.requestId"});

        assertEquals(List.of("42", "7", "req-123"), keys);
    }

    @Test
    void blankExpressionsAreSkipped() throws Exception {
        Method method = Target.class.getMethod("create", Long.class, Long.class, TestReq.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, new TestReq()});

        RepeatExecuteKeyResolver resolver = new RepeatExecuteKeyResolver();
        List<String> keys = resolver.resolve(pjp, new String[]{"", "#uid"});

        assertEquals(List.of("7"), keys);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=RepeatExecuteKeyResolverTest`
Expected: FAIL（编译错误：`RepeatExecuteKeyResolver` 不存在）

- [ ] **Step 3: 实现**

```java
package com.yqz.openblog.idempotent.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 @RepeatExecuteLimit#keys 中的 SpEL 表达式为实际字符串值。
 * 复用 AuditLogAspect 的解析模式：DefaultParameterNameDiscoverer + SpEL。
 * 依赖根 pom 的 &lt;parameters&gt;true&lt;/parameters&gt; 取到参数名。
 */
public class RepeatExecuteKeyResolver {

    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser parser = new SpelExpressionParser();

    public List<String> resolve(ProceedingJoinPoint pjp, String[] keys) {
        List<String> result = new ArrayList<>();
        if (keys == null || keys.length == 0) {
            return result;
        }
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Object[] args = pjp.getArgs();
        EvaluationContext context = new MethodBasedEvaluationContext(null, method, args, nameDiscoverer);
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Object value = parser.parseExpression(key).getValue(context);
            result.add(value == null ? "" : String.valueOf(value));
        }
        return result;
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=RepeatExecuteKeyResolverTest`
Expected: PASS（2 个用例）

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-framework/framework-idempotent
git commit -m "feat(idempotent): add RepeatExecuteKeyResolver (SpEL) with tests"
```

---

### Task 5: `LocalLockCache`（TDD）

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/lock/LocalLockCache.java`
- Test: `OpenBlog-framework/framework-idempotent/src/test/java/com/yqz/openblog/idempotent/lock/LocalLockCacheTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.yqz.openblog.idempotent.lock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLockCacheTest {

    @Test
    void reusesSameLockForSameKey() {
        LocalLockCache cache = new LocalLockCache(48);
        ReentrantLock a = cache.getLock("k1");
        ReentrantLock b = cache.getLock("k1");
        assertSame(a, b);
        assertTrue(a.tryLock());
        a.unlock();
    }

    @Test
    void differentKeysGetDifferentLocks() {
        LocalLockCache cache = new LocalLockCache(48);
        assertNotNull(cache.getLock("k1"));
        assertNotNull(cache.getLock("k2"));
        assertTrue(cache.getLock("k1") != cache.getLock("k2"));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=LocalLockCacheTest`
Expected: FAIL（编译错误：`LocalLockCache` 不存在）

- [ ] **Step 3: 实现**

```java
package com.yqz.openblog.idempotent.lock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

/**
 * 本地锁池：同 Key 复用同一把 ReentrantLock 实例，避免每次 new 锁导致互斥失效。
 * Caffeine get 线程安全，同 Key 只创建一次。
 */
public class LocalLockCache {

    private final Cache<String, ReentrantLock> cache;

    public LocalLockCache(int expireHours) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(expireHours, TimeUnit.HOURS)
                .build();
    }

    public ReentrantLock getLock(String key) {
        return cache.get(key, k -> new ReentrantLock());
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=LocalLockCacheTest`
Expected: PASS（2 个用例）

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-framework/framework-idempotent
git commit -m "feat(idempotent): add LocalLockCache (Caffeine lock pool) with tests"
```

---

### Task 6: `RepeatExecuteFlagStore` 接口 + Redis 实现

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/handle/RepeatExecuteFlagStore.java`
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/handle/RedisRepeatExecuteFlagStore.java`

- [ ] **Step 1: 创建接口**

```java
package com.yqz.openblog.idempotent.handle;

import java.util.Optional;

/**
 * 幂等标识存储抽象。接口化便于测试（测试注入内存实现），生产用 Redis 实现。
 * 约定：任何读/写失败都不抛异常——读返回 empty（视为未命中），写静默降级。
 */
public interface RepeatExecuteFlagStore {

    /** 幂等标识是否已写入 success */
    boolean isSuccess(String flagKey);

    /** 写入 success 标识，TTL 秒 */
    void setFlag(String flagKey, long ttlSeconds);

    /** 读取上次结果 JSON（RETURN_SAME_RESULT 用） */
    Optional<String> getResult(String resultKey);

    /** 写入结果 JSON，TTL 秒 */
    void setResult(String resultKey, String json, long ttlSeconds);
}
```

- [ ] **Step 2: 创建 Redis 实现**

```java
package com.yqz.openblog.idempotent.handle;

import com.yqz.openblog.idempotent.constant.RepeatExecuteLimitConstant;
import com.yqz.openblog.redis.core.RedisOps;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于项目既有 RedisOps（StringRedisTemplate 封装）的标识存储。
 * RedisOps 已内置容错：读返回 empty、写打 warn，组件整体对 Redis 故障降级。
 */
public class RedisRepeatExecuteFlagStore implements RepeatExecuteFlagStore {

    private final RedisOps redisOps;

    public RedisRepeatExecuteFlagStore(RedisOps redisOps) {
        this.redisOps = redisOps;
    }

    @Override
    public boolean isSuccess(String flagKey) {
        return redisOps.get(flagKey)
                .map(RepeatExecuteLimitConstant.FLAG_SUCCESS::equals)
                .orElse(false);
    }

    @Override
    public void setFlag(String flagKey, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        redisOps.set(flagKey, RepeatExecuteLimitConstant.FLAG_SUCCESS, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Optional<String> getResult(String resultKey) {
        return redisOps.get(resultKey);
    }

    @Override
    public void setResult(String resultKey, String json, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return;
        }
        redisOps.set(resultKey, json, Duration.ofSeconds(ttlSeconds));
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd E:\java\OpenBlog && mvn -q -pl OpenBlog-framework/framework-idempotent -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add OpenBlog-framework/framework-idempotent
git commit -m "feat(idempotent): add RepeatExecuteFlagStore interface + Redis impl"
```

---

### Task 7: `RepeatExecuteLockSupport`（Redisson 封装）

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/lock/RepeatExecuteLockSupport.java`

- [ ] **Step 1: 实现（三态 fail-open 契约 + 容错解锁）**

```java
package com.yqz.openblog.idempotent.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁封装：非阻塞 tryLock（等待 0 秒），watchdog 自动续期。
 * 故障语义（fail-open）：Redis/Redisson 故障时不抛异常，返回 DEGRADED，由切面降级放行（不阻断业务）。
 */
public class RepeatExecuteLockSupport {

    private static final Logger log = LoggerFactory.getLogger(RepeatExecuteLockSupport.class);

    /** 三态锁获取结果：拿到锁 / 争用（其他请求持有）/ 降级（获取失败，本次放行） */
    public enum LockResult { ACQUIRED, CONTENDED, DEGRADED }

    private final RedissonClient redissonClient;

    public RepeatExecuteLockSupport(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /** 非阻塞尝试获取锁。ACQUIRED=拿到锁，CONTENDED=其他请求正在执行，DEGRADED=锁获取失败（降级放行） */
    public LockResult tryLock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(0, TimeUnit.SECONDS) ? LockResult.ACQUIRED : LockResult.CONTENDED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LockResult.DEGRADED; // 中断：无法得知锁状态 → 降级放行
        } catch (RuntimeException e) {
            log.warn("[idempotent] 分布式锁获取失败，降级放行。lockKey={}", lockKey, e);
            return LockResult.DEGRADED;
        }
    }

    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
            } catch (RuntimeException e) {
                // watchdog 到期会自动释放锁，此处仅记日志，不向调用方传播
                log.warn("[idempotent] 分布式锁释放失败，watchdog 将自动释放。lockKey={}", lockKey, e);
            }
        }
    }
}
```

- [ ] **Step 2: 写失败测试（覆盖三态 + 故障降级路径）**

创建 `src/test/java/com/yqz/openblog/idempotent/lock/RepeatExecuteLockSupportTest.java`：

```java
package com.yqz.openblog.idempotent.lock;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepeatExecuteLockSupportTest {

    private RepeatExecuteLockSupport supportWith(RLock lock) {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getLock("k")).thenReturn(lock);
        return new RepeatExecuteLockSupport(client);
    }

    @Test
    void tryLock_acquired() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        assertEquals(RepeatExecuteLockSupport.LockResult.ACQUIRED, supportWith(lock).tryLock("k"));
    }

    @Test
    void tryLock_contended() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(false);
        assertEquals(RepeatExecuteLockSupport.LockResult.CONTENDED, supportWith(lock).tryLock("k"));
    }

    @Test
    void tryLock_redissonFailure_degrades() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new RuntimeException("connection lost"));
        assertEquals(RepeatExecuteLockSupport.LockResult.DEGRADED, supportWith(lock).tryLock("k"));
    }

    @Test
    void tryLock_interrupted_degradesAndRestoresInterruptFlag() throws InterruptedException {
        RLock lock = mock(RLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        assertEquals(RepeatExecuteLockSupport.LockResult.DEGRADED, supportWith(lock).tryLock("k"));
        assertTrue(Thread.interrupted());
    }

    @Test
    void unlock_swallowsReleaseFailure() {
        RLock lock = mock(RLock.class);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new RuntimeException("connection lost")).when(lock).unlock();
        supportWith(lock).unlock("k"); // 不得抛出
        verify(lock).unlock();
    }

    @Test
    void unlock_skipsWhenNotHeld() {
        RLock lock = mock(RLock.class);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        supportWith(lock).unlock("k");
        verify(lock, never()).unlock();
    }
}
```

- [ ] **Step 3: 运行测试 + 编译验证**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=RepeatExecuteLockSupportTest`
Expected: PASS（6/6；并确认 Redisson 3.52.0 能解析 `RLock.tryLock(long, TimeUnit)`）

- [ ] **Step 4: Commit**

```bash
git add OpenBlog-framework/framework-idempotent
git commit -m "feat(idempotent): add RepeatExecuteLockSupport (fail-open tri-state lock)"
```

---

### Task 8: 核心切面 `RepeatExecuteLimitAspect`（TDD，重点）

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/aspect/RepeatExecuteLimitAspect.java`
- Test helper: `OpenBlog-framework/framework-idempotent/src/test/java/com/yqz/openblog/idempotent/handle/InMemoryRepeatExecuteFlagStore.java`
- Test: `OpenBlog-framework/framework-idempotent/src/test/java/com/yqz/openblog/idempotent/aspect/RepeatExecuteLimitAspectTest.java`

- [ ] **Step 1: 写失败测试（先建内存标识存储辅助类）**

创建 `src/test/java/com/yqz/openblog/idempotent/handle/InMemoryRepeatExecuteFlagStore.java`：

```java
package com.yqz.openblog.idempotent.handle;

import com.yqz.openblog.idempotent.constant.RepeatExecuteLimitConstant;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用内存标识存储（含简单 TTL 判定）。
 */
public class InMemoryRepeatExecuteFlagStore implements RepeatExecuteFlagStore {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private record Entry(String value, long expireAtMillis) {
    }

    private boolean alive(Entry e) {
        return e != null && e.expireAtMillis() > System.currentTimeMillis();
    }

    @Override
    public boolean isSuccess(String flagKey) {
        Entry e = store.get(flagKey);
        return alive(e) && RepeatExecuteLimitConstant.FLAG_SUCCESS.equals(e.value());
    }

    @Override
    public void setFlag(String flagKey, long ttlSeconds) {
        store.put(flagKey, new Entry(RepeatExecuteLimitConstant.FLAG_SUCCESS,
                System.currentTimeMillis() + ttlSeconds * 1000));
    }

    @Override
    public Optional<String> getResult(String resultKey) {
        Entry e = store.get(resultKey);
        return alive(e) ? Optional.of(e.value()) : Optional.empty();
    }

    @Override
    public void setResult(String resultKey, String json, long ttlSeconds) {
        store.put(resultKey, new Entry(json, System.currentTimeMillis() + ttlSeconds * 1000));
    }
}
```

- [ ] **Step 2: 创建切面测试**

创建 `src/test/java/com/yqz/openblog/idempotent/aspect/RepeatExecuteLimitAspectTest.java`：

```java
package com.yqz.openblog.idempotent.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.idempotent.annotation.RepeatExecuteLimit;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyBuilder;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyResolver;
import com.yqz.openblog.idempotent.handle.InMemoryRepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.handle.RepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.lock.LocalLockCache;
import com.yqz.openblog.idempotent.lock.RepeatExecuteLockSupport;
import com.yqz.openblog.idempotent.strategy.IdempotentStrategy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepeatExecuteLimitAspectTest {

    static class Result {
        private String text;

        public Result() {
        }

        public Result(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    static class TestReq {
        private String requestId;

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }

    static class Target {
        @RepeatExecuteLimit(name = "comment_create", keys = {"#articleId", "#uid", "#req.requestId"},
                durationTime = 30, strategy = IdempotentStrategy.RETURN_SAME_RESULT)
        public Result create(Long articleId, Long uid, TestReq req) {
            return new Result("ok");
        }

        @RepeatExecuteLimit(name = "comment_reply", keys = {"#commentId", "#uid", "#req.requestId"},
                durationTime = 30)
        public Result reply(Long commentId, Long uid, TestReq req) {
            return new Result("ok");
        }
    }

    private static final String FLAG_CREATE = "openblog:test:repeat:flag:comment_create:42:7:r1";
    private static final String RESULT_CREATE = "openblog:test:repeat:result:comment_create:42:7:r1";
    private static final String FLAG_REPLY = "openblog:test:repeat:flag:comment_reply:42:7:r1";

    private RepeatExecuteLimitAspect aspect;
    private RepeatExecuteFlagStore flagStore;
    private RepeatExecuteLockSupport lockSupport;
    private LocalLockCache localLockCache;
    private ProceedingJoinPoint pjp;
    private MethodSignature sig;

    @BeforeEach
    void setUp() {
        flagStore = new InMemoryRepeatExecuteFlagStore();
        lockSupport = mock(RepeatExecuteLockSupport.class);
        localLockCache = new LocalLockCache(48);
        aspect = new RepeatExecuteLimitAspect(
                new RepeatExecuteKeyResolver(),
                new RepeatExecuteKeyBuilder("test"),
                lockSupport,
                localLockCache,
                flagStore,
                new ObjectMapper());
        pjp = mock(ProceedingJoinPoint.class);
        sig = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
    }

    private RepeatExecuteLimit annotationOf(String methodName, Class<?>... paramTypes) throws Exception {
        return Target.class.getMethod(methodName, paramTypes).getAnnotation(RepeatExecuteLimit.class);
    }

    private void stubInvocation(String methodName, Class<?>[] paramTypes, Object[] args,
                                Class<?> returnType, Object result) throws Exception {
        Method m = Target.class.getMethod(methodName, paramTypes);
        when(sig.getMethod()).thenReturn(m);
        when(sig.getReturnType()).thenReturn(returnType);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed()).thenReturn(result);
    }

    private TestReq reqWith(String requestId) {
        TestReq req = new TestReq();
        req.setRequestId(requestId);
        return req;
    }

    @Test
    void fastPathReject_CACHE_REJECT_throwsBizException() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        flagStore.setFlag(FLAG_REPLY, 30);

        assertThrows(BizException.class, () -> aspect.around(pjp, annotationOf("reply", params)));
        verify(lockSupport, never()).tryLock(anyString());
        verify(pjp, never()).proceed();
    }

    @Test
    void fastPathHit_RETURN_SAME_RESULT_returnsCached() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("create", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        flagStore.setFlag(FLAG_CREATE, 30);
        flagStore.setResult(RESULT_CREATE, "{\"text\":\"first\"}", 30);

        Object out = aspect.around(pjp, annotationOf("create", params));

        assertInstanceOf(Result.class, out);
        assertEquals("first", ((Result) out).text);
        verify(pjp, never()).proceed();
    }

    @Test
    void returnSame_noCachedResultYet_throws() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("create", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        flagStore.setFlag(FLAG_CREATE, 30);

        assertThrows(BizException.class, () -> aspect.around(pjp, annotationOf("create", params)));
    }

    @Test
    void doubleCheck_afterLock_flagAlreadySuccess_returnsCached() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("create", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);
        flagStore.setFlag(FLAG_CREATE, 30);
        flagStore.setResult(RESULT_CREATE, "{\"text\":\"first\"}", 30);

        Object out = aspect.around(pjp, annotationOf("create", params));

        assertEquals("first", ((Result) out).text);
        verify(pjp, never()).proceed();
        verify(lockSupport).unlock(anyString());
    }

    @Test
    void localLockContended_rejects() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));

        ReentrantLock held = localLockCache.getLock("openblog:test:repeat:lock:comment_reply:42:7:r1");
        held.lock();
        try {
            assertThrows(BizException.class, () -> aspect.around(pjp, annotationOf("reply", params)));
        } finally {
            held.unlock();
        }
        verify(pjp, never()).proceed();
    }

    @Test
    void distributedLockContended_rejects() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.CONTENDED);

        assertThrows(BizException.class, () -> aspect.around(pjp, annotationOf("reply", params)));
        verify(lockSupport, never()).unlock(anyString());
        verify(pjp, never()).proceed();
    }

    @Test
    void distributedLockDegraded_proceeds() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.DEGRADED);

        Object out = aspect.around(pjp, annotationOf("reply", params));

        assertEquals("ok", ((Result) out).text);
        verify(pjp).proceed();
        assertTrue(flagStore.isSuccess(FLAG_REPLY));
        verify(lockSupport).unlock(anyString());
    }

    @Test
    void success_RETURN_SAME_RESULT_writesFlagAndResult() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("create", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);

        Object out = aspect.around(pjp, annotationOf("create", params));

        assertEquals("ok", ((Result) out).text);
        assertTrue(flagStore.isSuccess(FLAG_CREATE));
        assertTrue(flagStore.getResult(RESULT_CREATE).isPresent());
        verify(lockSupport).unlock(anyString());
    }

    @Test
    void success_CACHE_REJECT_writesFlagOnly() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("r1")}, Result.class, new Result("ok"));
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);

        aspect.around(pjp, annotationOf("reply", params));

        assertTrue(flagStore.isSuccess(FLAG_REPLY));
        assertFalse(flagStore.getResult("openblog:test:repeat:result:comment_reply:42:7:r1").isPresent());
    }

    @Test
    void businessThrows_doesNotWriteFlag_reraises() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        Method m = Target.class.getMethod("reply", params);
        when(sig.getMethod()).thenReturn(m);
        when(sig.getReturnType()).thenReturn(Result.class);
        when(pjp.getArgs()).thenReturn(new Object[]{42L, 7L, reqWith("r1")});
        when(lockSupport.tryLock(anyString())).thenReturn(RepeatExecuteLockSupport.LockResult.ACQUIRED);
        doThrow(new IllegalStateException("boom")).when(pjp).proceed();

        assertThrows(IllegalStateException.class, () -> aspect.around(pjp, annotationOf("reply", params)));

        assertFalse(flagStore.isSuccess(FLAG_REPLY));
        verify(lockSupport).unlock(anyString());
    }

    @Test
    void blankKey_skipsIdempotency_andExecutes() throws Throwable {
        Class<?>[] params = {Long.class, Long.class, TestReq.class};
        stubInvocation("reply", params, new Object[]{42L, 7L, reqWith("")}, Result.class, new Result("ok"));

        Object out = aspect.around(pjp, annotationOf("reply", params));

        assertEquals("ok", ((Result) out).text);
        verify(lockSupport, never()).tryLock(anyString());
        assertFalse(flagStore.isSuccess(FLAG_REPLY));
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=RepeatExecuteLimitAspectTest`
Expected: FAIL（编译错误：`RepeatExecuteLimitAspect` 不存在）

- [ ] **Step 4: 实现切面**

```java
package com.yqz.openblog.idempotent.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.idempotent.annotation.RepeatExecuteLimit;
import com.yqz.openblog.idempotent.constant.RepeatExecuteLimitConstant;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyBuilder;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyResolver;
import com.yqz.openblog.idempotent.handle.RepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.lock.LocalLockCache;
import com.yqz.openblog.idempotent.lock.RepeatExecuteLockSupport;
import com.yqz.openblog.idempotent.strategy.IdempotentStrategy;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 幂等核心切面。@Order(-11) 保证在 @Transactional 之外（最外层）。
 * 流程：① 快路径查标识 → ③ 本地锁 → ④ Redisson 分布式锁 → ⑤ 双重检测 → ⑥ 执行 → ⑦ 成功写标识。
 */
@Aspect
@Order(-11)
public class RepeatExecuteLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RepeatExecuteLimitAspect.class);

    private final RepeatExecuteKeyResolver keyResolver;
    private final RepeatExecuteKeyBuilder keyBuilder;
    private final RepeatExecuteLockSupport lockSupport;
    private final LocalLockCache localLockCache;
    private final RepeatExecuteFlagStore flagStore;
    private final ObjectMapper objectMapper;

    public RepeatExecuteLimitAspect(RepeatExecuteKeyResolver keyResolver,
                                    RepeatExecuteKeyBuilder keyBuilder,
                                    RepeatExecuteLockSupport lockSupport,
                                    LocalLockCache localLockCache,
                                    RepeatExecuteFlagStore flagStore,
                                    ObjectMapper objectMapper) {
        this.keyResolver = keyResolver;
        this.keyBuilder = keyBuilder;
        this.lockSupport = lockSupport;
        this.localLockCache = localLockCache;
        this.flagStore = flagStore;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(repeatLimit)")
    public Object around(ProceedingJoinPoint pjp, RepeatExecuteLimit repeatLimit) throws Throwable {
        String name = repeatLimit.name();
        List<String> keys = keyResolver.resolve(pjp, repeatLimit.keys());
        // 防御：任一幂等 Key 为空则跳过幂等保护（记录警告），避免错误配置锁死业务
        if (name.isBlank() || keys.isEmpty() || keys.stream().anyMatch(String::isBlank)) {
            log.warn("[idempotent] 幂等 Key 为空，跳过幂等保护。name={}, keys={}", name, keys);
            return pjp.proceed();
        }

        String lockKey = keyBuilder.lockKey(name, keys);
        String flagKey = keyBuilder.flagKey(name, keys);
        String resultKey = keyBuilder.resultKey(name, keys);
        long duration = repeatLimit.durationTime();
        Class<?> returnType = ((MethodSignature) pjp.getSignature()).getReturnType();

        // ① 快路径：标识已存在 → 直接按策略处理（不抢锁，O(1)）
        if (flagStore.isSuccess(flagKey)) {
            return handleHit(repeatLimit, resultKey, returnType);
        }

        // ③ 本地锁：同 JVM 内串行
        ReentrantLock localLock = localLockCache.getLock(lockKey);
        if (!localLock.tryLock()) {
            return handleHit(repeatLimit, resultKey, returnType);
        }
        try {
            // ④ 分布式锁：跨 JVM 串行，非阻塞
            // CONTENDED → 防重命中；ACQUIRED 与 DEGRADED 都继续执行（DEGRADED = Redis 故障降级放行）
            if (lockSupport.tryLock(lockKey) == RepeatExecuteLockSupport.LockResult.CONTENDED) {
                return handleHit(repeatLimit, resultKey, returnType);
            }
            try {
                // ⑤ 双重检测：获锁后再查一次标识，杜绝竞争窗口
                if (flagStore.isSuccess(flagKey)) {
                    return handleHit(repeatLimit, resultKey, returnType);
                }
                // ⑥ 执行（事务在最内层，proceed 返回即已提交）
                Object result = pjp.proceed();
                // ⑦ 成功后才写标识
                if (duration > 0) {
                    if (repeatLimit.strategy() == IdempotentStrategy.RETURN_SAME_RESULT) {
                        try {
                            flagStore.setResult(resultKey, objectMapper.writeValueAsString(result), duration);
                        } catch (Exception e) {
                            log.warn("[idempotent] 写结果标识失败（已忽略）。name={}", name, e);
                        }
                    }
                    flagStore.setFlag(flagKey, duration);
                }
                return result;
            } finally {
                lockSupport.unlock(lockKey);
            }
        } finally {
            localLock.unlock();
        }
    }

    private Object handleHit(RepeatExecuteLimit repeatLimit, String resultKey, Class<?> returnType)
            throws IOException {
        if (repeatLimit.strategy() == IdempotentStrategy.RETURN_SAME_RESULT) {
            Optional<String> json = flagStore.getResult(resultKey);
            if (json.isPresent()) {
                return objectMapper.readValue(json.get(), returnType);
            }
            // 结果尚未落盘（首个请求仍在执行）→ 退化为拒绝
        }
        throw new BizException(RepeatExecuteLimitConstant.REJECT_CODE, repeatLimit.message());
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test -Dtest=RepeatExecuteLimitAspectTest`
Expected: PASS（11 个用例）

- [ ] **Step 6: Commit**

```bash
git add OpenBlog-framework/framework-idempotent
git commit -m "feat(idempotent): add RepeatExecuteLimitAspect with full coverage"
```

---

### Task 9: 自动装配

**Files:**
- Create: `OpenBlog-framework/framework-idempotent/src/main/java/com/yqz/openblog/idempotent/config/IdempotentAutoConfiguration.java`
- Modify: `OpenBlog-framework/framework-idempotent/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（写入类名）

- [ ] **Step 1: 写入自动装配入口**

将 `OpenBlog-framework/framework-idempotent/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 内容写为：

```
com.yqz.openblog.idempotent.config.IdempotentAutoConfiguration
```

- [ ] **Step 2: 创建自动装配类**

```java
package com.yqz.openblog.idempotent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.idempotent.aspect.RepeatExecuteLimitAspect;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyBuilder;
import com.yqz.openblog.idempotent.core.RepeatExecuteKeyResolver;
import com.yqz.openblog.idempotent.handle.RepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.handle.RedisRepeatExecuteFlagStore;
import com.yqz.openblog.idempotent.lock.LocalLockCache;
import com.yqz.openblog.idempotent.lock.RepeatExecuteLockSupport;
import com.yqz.openblog.redis.core.RedisOps;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(IdempotentProperties.class)
@ConditionalOnProperty(prefix = "openblog.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteKeyBuilder repeatExecuteKeyBuilder(IdempotentProperties props, Environment env) {
        String envPrefix = props.getEnvPrefix();
        if (envPrefix == null || envPrefix.isBlank()) {
            String[] profiles = env.getActiveProfiles();
            envPrefix = profiles.length > 0 ? profiles[0] : "dev";
        }
        return new RepeatExecuteKeyBuilder(envPrefix);
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteKeyResolver repeatExecuteKeyResolver() {
        return new RepeatExecuteKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalLockCache localLockCache(IdempotentProperties props) {
        return new LocalLockCache(props.getLocalLockCacheHours());
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteLockSupport repeatExecuteLockSupport(RedissonClient redissonClient) {
        return new RepeatExecuteLockSupport(redissonClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteFlagStore repeatExecuteFlagStore(RedisOps redisOps) {
        return new RedisRepeatExecuteFlagStore(redisOps);
    }

    @Bean
    @ConditionalOnMissingBean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(
            RepeatExecuteKeyResolver keyResolver,
            RepeatExecuteKeyBuilder keyBuilder,
            RepeatExecuteLockSupport lockSupport,
            LocalLockCache localLockCache,
            RepeatExecuteFlagStore flagStore,
            ObjectMapper objectMapper) {
        return new RepeatExecuteLimitAspect(
                keyResolver, keyBuilder, lockSupport, localLockCache, flagStore, objectMapper);
    }
}
```

- [ ] **Step 3: 编译 + business 依赖注入验证**

先给 `OpenBlog-business/pom.xml` 加依赖（否则无法验证自动装配是否影响 business 启动）：

```xml
        <dependency>
            <groupId>com.yqz</groupId>
            <artifactId>OpenBlog-framework-idempotent</artifactId>
            <version>${project.version}</version>
        </dependency>
```

Run: `cd E:\java\OpenBlog && mvn -q -pl OpenBlog-business -am package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: 运行全部框架模块测试确认无回归**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-framework/framework-idempotent test`
Expected: PASS（Task 3/4/5/8 全部用例）

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-framework/pom.xml OpenBlog-framework/framework-idempotent OpenBlog-business/pom.xml
git commit -m "feat(idempotent): auto-configuration + wire into business module"
```

---

### Task 10: 接入 CommentService（后端）

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/comment/dto/CommentCreateRequest.java`
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/comment/service/CommentService.java`
- Test: `OpenBlog-business/src/test/java/com/yqz/openblog/comment/service/CommentIdempotencyWiringTest.java`

- [ ] **Step 1: `CommentCreateRequest` 新增 requestId**

修改 `OpenBlog-business/src/main/java/com/yqz/openblog/comment/dto/CommentCreateRequest.java`：

```java
package com.yqz.openblog.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommentCreateRequest {
    @NotBlank
    @Size(max = 2000)
    private String content;

    /**
     * 客户端生成的幂等 ID（前端每次提交生成 UUID，作为幂等 Key 的一部分）。
     */
    private String requestId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
```

- [ ] **Step 2: `CommentService` 两个写方法加注解**

修改 `CommentService.java`：

`createTopLevel` 方法（`public CommentThreadResponse createTopLevel(Long articleId, Long uid, CommentCreateRequest req)`）上方加：

```java
    @com.yqz.openblog.idempotent.annotation.RepeatExecuteLimit(
            name = "comment_create",
            keys = {"#articleId", "#uid", "#req.requestId"},
            durationTime = 30,
            strategy = com.yqz.openblog.idempotent.strategy.IdempotentStrategy.RETURN_SAME_RESULT)
    public CommentThreadResponse createTopLevel(Long articleId, Long uid, CommentCreateRequest req) {
```

`reply` 方法（`public CommentThreadResponse reply(Long commentId, Long uid, CommentCreateRequest req)`）上方加：

```java
    @com.yqz.openblog.idempotent.annotation.RepeatExecuteLimit(
            name = "comment_reply",
            keys = {"#commentId", "#uid", "#req.requestId"},
            durationTime = 30)
    public CommentThreadResponse reply(Long commentId, Long uid, CommentCreateRequest req) {
```

（用全限定名写注解，避免在 business 里加 import；也可以加 import 后写短名，任选。）

- [ ] **Step 3: 写装配测试（无 Spring 上下文，纯反射）**

创建 `OpenBlog-business/src/test/java/com/yqz/openblog/comment/service/CommentIdempotencyWiringTest.java`：

```java
package com.yqz.openblog.comment.service;

import com.yqz.openblog.comment.dto.CommentCreateRequest;
import com.yqz.openblog.idempotent.annotation.RepeatExecuteLimit;
import com.yqz.openblog.idempotent.strategy.IdempotentStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommentIdempotencyWiringTest {

    @Test
    void createTopLevel_hasRepeatExecuteLimitWithCommentCreateKey() throws Exception {
        Method m = CommentService.class.getMethod(
                "createTopLevel", Long.class, Long.class, CommentCreateRequest.class);
        RepeatExecuteLimit ann = m.getAnnotation(RepeatExecuteLimit.class);
        assertNotNull(ann, "createTopLevel 应标注 @RepeatExecuteLimit");
        assertEquals("comment_create", ann.name());
        assertArrayEquals(new String[]{"#articleId", "#uid", "#req.requestId"}, ann.keys());
        assertEquals(IdempotentStrategy.RETURN_SAME_RESULT, ann.strategy());
        assertEquals(30L, ann.durationTime());
    }

    @Test
    void reply_hasRepeatExecuteLimitWithCommentReplyKey() throws Exception {
        Method m = CommentService.class.getMethod(
                "reply", Long.class, Long.class, CommentCreateRequest.class);
        RepeatExecuteLimit ann = m.getAnnotation(RepeatExecuteLimit.class);
        assertNotNull(ann, "reply 应标注 @RepeatExecuteLimit");
        assertEquals("comment_reply", ann.name());
        assertArrayEquals(new String[]{"#commentId", "#uid", "#req.requestId"}, ann.keys());
        assertEquals(30L, ann.durationTime());
    }
}
```

- [ ] **Step 4: 运行装配测试 + 业务编译**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-business -am test -Dtest=CommentIdempotencyWiringTest -DfailIfNoTests=false`
Expected: PASS（2 个用例；`-am` 会先构建依赖模块）

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-business/src/main/java/com/yqz/openblog/comment OpenBlog-business/src/test
git commit -m "feat(comment): add @RepeatExecuteLimit idempotency to comment create/reply"
```

---

### Task 11: 前端 requestId

**Files:**
- Create: `vue/src/utils/uuid.js`
- Modify: `vue/src/api/comment.js`
- Modify: `vue/src/components/CommentSection.vue`
- Modify: `vue/src/components/CommentItem.vue`

- [ ] **Step 1: 创建 UUID 工具**

创建 `vue/src/utils/uuid.js`：

```js
export function newRequestId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // 降级：浏览器无 crypto.randomUUID 时生成简单 v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
```

- [ ] **Step 2: 改 `vue/src/api/comment.js`**

```js
import { request } from './http'

export function listComments(articleId, page = 0, size = 20) {
  return request(`/api/v1/articles/${articleId}/comments?page=${page}&size=${size}`, {
    method: 'GET'
  })
}

export function createComment(articleId, content, requestId) {
  return request(`/api/v1/articles/${articleId}/comments`, {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify({ content, requestId })
  })
}

export function replyComment(commentId, content, requestId) {
  return request(`/api/v1/comments/${commentId}/replies`, {
    method: 'POST',
    withAuth: true,
    body: JSON.stringify({ content, requestId })
  })
}

export function deleteComment(commentId) {
  return request(`/api/v1/comments/${commentId}`, {
    method: 'DELETE',
    withAuth: true
  })
}
```

- [ ] **Step 3: 改 `vue/src/components/CommentSection.vue`**

顶部评论输入框逻辑（`postTopLevel`）加 requestId 生命周期。修改 `<script setup>` 引入处与 `postTopLevel`：

在 import 区加：

```js
import { newRequestId } from '../utils/uuid'
```

在 `postTopLevel` 所在的 setup 作用域，加一个 `topLevelRequestId` 状态并替换 `postTopLevel` 实现：

```js
// 当前"编辑会话"的幂等 ID：成功后重新生成，失败保持不变（保证重试不误伤、双击去重）
let topLevelRequestId = newRequestId()

async function postTopLevel() {
  const content = text.value.trim()
  if (!content) return
  posting.value = true
  try {
    await createComment(props.articleId, content, topLevelRequestId)
    topLevelRequestId = newRequestId()
    text.value = ''
    showMessage('评论已发布')
    await Promise.all([loadMeIfNeeded(), reload()])
  } catch (e) {
    showMessage(e?.message || '评论失败')
  } finally {
    posting.value = false
  }
}
```

- [ ] **Step 4: 改 `vue/src/components/CommentItem.vue`（回复带上 requestId + 修复回复框打不开）**

> **规划时发现的既有 bug**：回复按钮 `@click="$emit('reply', comment)"`，而父组件 `CommentSection.onReplyClick` 是空操作（只 `await nextTick()`），`defineExpose` 的 `openReply` 从未被调用 → **回复框实际打不开**。本步把按钮改为直接调组件内部 `openReply()`（生成新 requestId + 打开回复框），顺带修复此 bug。不改动 `emit('reply')` 之外的父组件逻辑（`onReplyClick` 保留为空操作，无害）。

`<script setup>` 顶部引入：

```js
import { newRequestId } from '../utils/uuid'
```

模板「回复」按钮（原 `@click="$emit('reply', comment)"`）改为：

```html
<button type="button" class="comment-link" @click="openReply">回复</button>
```

`<script setup>` 中：新增 `replyRequestId` 状态、`openReply`（生成新 ID + 开框）、`submitReply` 带 requestId；`cancelReply` 保持原样：

```js
const replying = ref(false)
const replyText = ref('')
let replyRequestId = ''

function cancelReply() {
  replying.value = false
  replyText.value = ''
}

function openReply() {
  replyRequestId = newRequestId()   // 每次打开回复框生成新 ID，双击复用同一 ID
  replying.value = true
}

function submitReply() {
  const text = replyText.value.trim()
  if (!text) return
  emit('submit-reply', { parent: props.comment, content: text, requestId: replyRequestId, done: cancelReply })
}

defineExpose({
  openReply
})
```

同时把 `CommentSection.vue` 的 `onSubmitReply` 签名改为接收并透传 requestId：

```js
async function onSubmitReply({ parent, content, requestId, done }) {
  if (!isLoggedIn.value) {
    showMessage('请先登录')
    return
  }
  try {
    await replyComment(parent.id, content, requestId)
    done?.()
    showMessage('回复已发布')
    await Promise.all([loadMeIfNeeded(), reload()])
  } catch (e) {
    showMessage(e?.message || '回复失败')
  }
}
```

- [ ] **Step 5: 前端构建验证**

Run: `cd E:\java\OpenBlog\vue && npm run build`
Expected: 构建成功，`dist/` 生成

- [ ] **Step 6: Commit**

```bash
git add vue/src/utils/uuid.js vue/src/api/comment.js vue/src/components
git commit -m "feat(comment): frontend requestId for comment idempotency"
```

---

### Task 12: 全量验证与收尾

**Files:**
- Modify: `README.md`（模块表补一行）

- [ ] **Step 1: 全量后端构建 + 测试**

Run: `cd E:\java\OpenBlog && mvn -pl OpenBlog-business -am package`
Expected: BUILD SUCCESS，framework-idempotent 与 business 的测试全部通过

- [ ] **Step 2: 前端构建（已含在 Task 11，复查）**

Run: `cd E:\java\OpenBlog\vue && npm run build`
Expected: 成功

- [ ] **Step 3: 更新 README 模块表**

`README.md` 的模块说明表，在 `framework-audit` 行后加：

```
| ├ `framework-idempotent` | 幂等框架：`@RepeatExecuteLimit` 注解 + AOP，本地锁 + Redisson 分布式锁 + Redis 标识双重检测，防重复点击/重复执行 |
```

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: document framework-idempotent module"
```

---

## 自检记录（Self-Review）

**Spec coverage**（对照设计文档）：
- 注解 `@RepeatExecuteLimit` → Task 2 ✅
- 策略枚举 CACHE_REJECT / RETURN_SAME_RESULT → Task 2 ✅
- 切面流程（快路径/本地锁/分布式锁/双重检测/成功写标识）→ Task 8 ✅
- 防重命中处理（RETURN_SAME_RESULT 有结果返回、无结果降级拒绝；CACHE_REJECT 抛异常）→ Task 8 `handleHit` ✅
- KeyBuilder / KeyResolver / LocalLockCache / FlagStore / LockSupport → Task 3-7 ✅
- 自动装配 + 环境前缀取 profile → Task 9 ✅
- 评论接入（requestId + 注解）→ Task 10 ✅
- 前端 crypto.randomUUID → Task 11 ✅
- 测试策略（切面单测全覆盖 + 装配测试）→ Task 8/10 ✅

**Placeholder scan**：无 TBD/TODO；所有代码步骤含完整实现。

**Type consistency**：
- `RepeatExecuteFlagStore` 接口签名（`isSuccess/setFlag/getResult/setResult`）在 Task 6 定义、Task 8 的内存实现与切面调用一致 ✅
- `RepeatExecuteKeyBuilder.lockKey/flagKey/resultKey(String, List<String>)` 在 Task 3/8 一致 ✅
- `BizException(int, String)` 构造器在 Task 8 使用 `4299` 常量 ✅
- `RepeatExecuteLockSupport.tryLock/unlock(String)` 在 Task 7/8 一致 ✅；Task 7 三态契约 `LockResult{ACQUIRED, CONTENDED, DEGRADED}` 与 Task 8 切面消费一致（CONTENDED→防重命中，ACQUIRED/DEGRADED→继续执行）✅

---

## 执行交接

计划已保存至 `docs/superpowers/plans/2026-08-29-idempotent-framework.md`。两种执行方式：

1. **Subagent-Driven（推荐）** —— 每个 Task 派发一个全新子代理，任务间我做 review，迭代快
2. **Inline Execution** —— 在当前会话用 executing-plans 批量执行，带检查点

选哪种？（另注：计划基于 Redisson 3.52.0，源自 [Redisson 官方兼容性说明](https://redisson.pro/blog/redisson-spring-boot-4-compatibility.html) 与 [Spring Boot Starter 文档](https://redisson.org/docs/integration-with-spring/)。）
