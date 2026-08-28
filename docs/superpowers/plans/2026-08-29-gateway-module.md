# OpenBlog 网关模块实现计划（Spring Cloud Gateway）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增独立 `OpenBlog-gateway` 模块（Spring Cloud Gateway / WebFlux，8080），承接 `/api/**` 的统一 JWT 粗校验、限流防刷、traceId 透传、统一错误兜底与 CORS 收敛；Nginx `/api/` 改指网关，business 保留完整授权。

**Architecture:** Nginx(边缘) `/api/**` → Gateway(8080) GlobalFilter 链（TraceIdFilter -3 → JwtCheckFilter -2 → RateLimitFilter -1）→ 静态路由转发 business:8082；`GatewayErrorHandler`（ErrorWebExceptionHandler）统一兜底。sitemap/robots/seo/爬虫路径保持 Nginx 直连 business。

**Tech Stack:** Spring Cloud Gateway 4.3.x（Spring Cloud 2025.0.x Northfields BOM）+ Spring Boot 3.5.12 + Java 17 + WebFlux + jjwt 0.12.5 + framework-redis（SlidingWindowLimiter）+ OpenBlog-common（仅 ApiResponse）。

**前置阅读：** `docs/superpowers/specs/2026-08-29-gateway-module-design.md`（已批准的 spec）。

---

## ⚠️ 相对 spec 的两处细化（实现者必读）

1. **JwtCheckFilter 语义（比 spec §4.4 更安全）**：spec 字面是「白名单外 token 缺失即 401」，但博客有大量匿名公共路由（读文章/评论），若白名单漏配会误伤公共访问。实现采用：**白名单外路径「无 token → 放行交由 business 判定；带 token 但无效/过期 → 边缘 401」**。公共路由无 token（前端 `http.js` 仅 `withAuth` 请求带 token），天然放行；带失效 token 的请求被边缘拦截。白名单从「必需精确枚举」降级为「兜底豁免」（仅 auth 入口路径）。
2. **RateLimitFilter 阻塞适配**：`SlidingWindowLimiter` 走阻塞 `StringRedisTemplate`，必须在 `Schedulers.boundedElastic()` 上执行，`chain.filter()` 回到事件循环，禁止阻塞 Netty 线程。

---

## 模块文件清单

```
OpenBlog-gateway/
├── pom.xml
└── src/main/java/com/yqz/openblog/gateway/
    ├── GatewayApplication.java
    ├── config/JwtProperties.java
    ├── config/GatewayProperties.java
    ├── filter/TraceIdFilter.java
    ├── filter/JwtVerifier.java
    ├── filter/JwtCheckFilter.java
    ├── filter/RateLimitFilter.java
    ├── handler/GatewayErrorHandler.java
    └── handler/GatewayResponses.java
└── src/main/resources/application.yaml
└── src/test/java/com/yqz/openblog/gateway/
    ├── filter/TraceIdFilterTest.java
    ├── filter/JwtCheckFilterTest.java
    ├── filter/RateLimitFilterTest.java
    ├── handler/GatewayErrorHandlerTest.java
    └── GatewayApplicationTest.java   (Task 6)
```

---

## Task 1: 根 pom 引入 Spring Cloud BOM + OpenBlog-gateway 模块骨架

**Files:**
- Modify: `pom.xml:17-38`（modules + dependencyManagement + properties）
- Create: `OpenBlog-gateway/pom.xml`
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/GatewayApplication.java`
- Create: `OpenBlog-gateway/src/main/resources/application.yaml`

- [ ] **Step 1: 修改根 pom，注册模块并引入 Spring Cloud 2025.0.x BOM**

`pom.xml`：

```xml
    <modules>
        <module>OpenBlog-common</module>
        <module>OpenBlog-framework</module>
        <module>OpenBlog-api</module>
        <module>OpenBlog-message</module>
        <module>OpenBlog-business</module>
        <module>OpenBlog-gateway</module>
    </modules>
    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.5.12</spring-boot.version>
        <spring-cloud.version>2025.0.3</spring-cloud.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

- [ ] **Step 2: 创建 `OpenBlog-gateway/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.yqz</groupId>
        <artifactId>OpenBlog</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>OpenBlog-gateway</artifactId>
    <name>OpenBlog-gateway</name>
    <description>OpenBlog API gateway (Spring Cloud Gateway, WebFlux)</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
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
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.5</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <mainClass>com.yqz.openblog.gateway.GatewayApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: 创建启动类 `GatewayApplication.java`**

```java
package com.yqz.openblog.gateway;

import com.yqz.openblog.common.config.CommonAutoConfiguration;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * OpenBlog API 网关。
 * 排除 CommonAutoConfiguration：其 GlobalExceptionHandler 是 servlet 风格 @RestControllerAdvice，
 * 与 WebFlux 的 ErrorWebExceptionHandler 冲突；仅复用 common 的 ApiResponse POJO。
 */
@SpringBootApplication(exclude = CommonAutoConfiguration.class)
@EnableConfigurationProperties({GatewayProperties.class, JwtProperties.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 4: 创建 `src/main/resources/application.yaml`（最小占位，Task 6 补全）**

```yaml
server:
  port: 8080

spring:
  application:
    name: openblog-gateway
```

- [ ] **Step 5: 编译验证**

Run: `mvn -q -pl OpenBlog-gateway -am compile`
Expected: BUILD SUCCESS（spring-cloud BOM 与 gateway starter 可解析）。

> 若 `2025.0.3` 与 Boot 3.5.12 版本不兼容报错，回退 `2025.0.0` 再试。

- [ ] **Step 6: Commit**

```bash
git add pom.xml OpenBlog-gateway
git commit -m "feat(gateway): scaffold OpenBlog-gateway module with Spring Cloud 2025.0.x"
```

---

## Task 2: TraceIdFilter（traceId 生成/透传）

**Files:**
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/filter/TraceIdFilter.java`
- Create: `OpenBlog-gateway/src/test/java/com/yqz/openblog/gateway/filter/TraceIdFilterTest.java`

- [ ] **Step 1: 写失败测试 `TraceIdFilterTest.java`**

```java
package com.yqz.openblog.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void generatesTraceIdWhenAbsent() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").build());
        ServerWebExchange[] forwarded = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            forwarded[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        Object attr = exchange.getAttribute(TraceIdFilter.TRACE_ID_ATTR);
        assertThat(attr).isNotNull().isNotEmpty();
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo(attr.toString());
        assertThat(forwarded[0].getRequest().getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER))
                .isEqualTo(attr.toString());
    }

    @Test
    void propagatesIncomingTraceId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/x").header("X-Trace-Id", "t-123").build());
        ServerWebExchange[] forwarded = new ServerWebExchange[1];
        GatewayFilterChain chain = ex -> {
            forwarded[0] = ex;
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getAttribute(TraceIdFilter.TRACE_ID_ATTR)).isEqualTo("t-123");
        assertThat(forwarded[0].getRequest().getHeaders().getFirst("X-Trace-Id")).isEqualTo("t-123");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=TraceIdFilterTest`
Expected: FAIL（TraceIdFilter 不存在，编译错误）。

- [ ] **Step 3: 实现 `TraceIdFilter.java`**

```java
package com.yqz.openblog.gateway.filter;

import com.yqz.openblog.common.TraceId;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器（order=-3，最先执行）：
 * 生成/透传 X-Trace-Id 到 exchange 属性、响应头与下游请求头。
 * WebFlux 下 traceId 载体是 exchange 属性（非 ThreadLocal/MDC），供错误处理器统一带出。
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_ATTR = "openblog.traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank()) ? incoming : TraceId.get();
        exchange.getAttributes().put(TRACE_ID_ATTR, traceId);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        ServerWebExchange mutated = exchange.mutate()
                .request(req -> req.header(TRACE_ID_HEADER, traceId))
                .build();
        return chain.filter(mutated);
    }

    public static String traceIdOf(ServerWebExchange exchange) {
        Object v = exchange.getAttribute(TRACE_ID_ATTR);
        return v != null ? v.toString() : "";
    }

    @Override
    public int getOrder() {
        return -3;
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=TraceIdFilterTest`
Expected: PASS（2 个用例）。

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-gateway
git commit -m "feat(gateway): add TraceIdFilter with exchange-attribute traceId propagation"
```

---

## Task 3: 统一错误响应（GatewayResponses + GatewayErrorHandler）

**Files:**
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/handler/GatewayResponses.java`
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/handler/GatewayErrorHandler.java`
- Create: `OpenBlog-gateway/src/test/java/com/yqz/openblog/gateway/handler/GatewayErrorHandlerTest.java`

- [ ] **Step 1: 写失败测试 `GatewayErrorHandlerTest.java`**

```java
package com.yqz.openblog.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.io.IOException;
import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorHandlerTest {

    private final GatewayErrorHandler handler = new GatewayErrorHandler(new ObjectMapper());

    private ServerWebExchange exchange(String traceId) {
        var req = MockServerHttpRequest.get("/api/v1/x");
        return MockServerWebExchange.from(
                traceId == null ? req.build() : req.header("X-Trace-Id", traceId).build());
    }

    @Test
    void downstreamConnectFailure_mapsTo502AndCarriesTraceId() {
        ServerWebExchange ex = exchange("t-9");
        handler.handle(ex, new ConnectException("connection refused")).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        String body = ex.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":5000").contains("\"message\":\"服务暂不可用\"")
                .contains("\"traceId\":\"t-9\"");
    }

    @Test
    void responseStatus401_mapsTo401() {
        ServerWebExchange ex = exchange(null);
        handler.handle(ex, new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no auth")).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("\"code\":4010");
    }

    @Test
    void genericException_mapsTo500NoStackLeak() {
        ServerWebExchange ex = exchange(null);
        handler.handle(ex, new IllegalStateException("boom: secret=abc")).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getResponse().getBodyAsString().block())
                .contains("\"code\":5000").doesNotContain("secret=");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=GatewayErrorHandlerTest`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 `GatewayResponses.java`**

```java
package com.yqz.openblog.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.gateway.filter.TraceIdFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一网关错误响应：ApiResponse 结构 + traceId，与 business 契约一致（前端 http.js 零改动）。
 */
public final class GatewayResponses {

    private GatewayResponses() {
    }

    public static Mono<Void> writeJson(ServerWebExchange exchange, ObjectMapper objectMapper,
                                       HttpStatus status, int code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiResponse<Object> resp = ApiResponse.fail(code, message);
        resp.setTraceId(TraceIdFilter.traceIdOf(exchange));
        String body;
        try {
            body = objectMapper.writeValueAsString(resp);
        } catch (JsonProcessingException e) {
            body = "{\"code\":5000,\"message\":\"系统繁忙\"}";
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
```

- [ ] **Step 4: 实现 `GatewayErrorHandler.java`**

```java
package com.yqz.openblog.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;

/**
 * 网关内部异常统一兜底（仅网关自身错误；下游业务正常/业务错误响应原样透传）。
 */
@Component
@Order(-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status;
        int code;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            HttpStatus s = HttpStatus.valueOf(rse.getStatusCode().value());
            if (s == HttpStatus.NOT_FOUND || s == HttpStatus.SERVICE_UNAVAILABLE) {
                status = HttpStatus.BAD_GATEWAY;
                code = 5000;
                message = "服务暂不可用";
            } else if (s == HttpStatus.UNAUTHORIZED) {
                status = HttpStatus.UNAUTHORIZED;
                code = 4010;
                message = "登录已失效";
            } else if (s == HttpStatus.TOO_MANY_REQUESTS) {
                status = HttpStatus.TOO_MANY_REQUESTS;
                code = 4290;
                message = "请求过于频繁，请稍后再试";
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                code = 5000;
                message = "系统繁忙";
            }
        } else if (ex instanceof IOException) {
            // 下游不可达（ConnectException 等）
            status = HttpStatus.BAD_GATEWAY;
            code = 5000;
            message = "服务暂不可用";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = 5000;
            message = "系统繁忙";
        }

        log.error("gateway error: method={} path={} ex={}", exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(), ex.toString());
        return GatewayResponses.writeJson(exchange, objectMapper, status, code, message);
    }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=GatewayErrorHandlerTest`
Expected: PASS（3 个用例）。

- [ ] **Step 6: Commit**

```bash
git add OpenBlog-gateway
git commit -m "feat(gateway): unified error handler returning ApiResponse with traceId"
```

---

## Task 4: JWT 校验（JwtVerifier + JwtCheckFilter + 白名单）

**Files:**
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/config/JwtProperties.java`
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/config/GatewayProperties.java`
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/filter/JwtVerifier.java`
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/filter/JwtCheckFilter.java`
- Create: `OpenBlog-gateway/src/test/java/com/yqz/openblog/gateway/filter/JwtCheckFilterTest.java`

- [ ] **Step 1: 先建两个属性类（编译依赖）**

`JwtProperties.java`：

```java
package com.yqz.openblog.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 与 business 同源的 JWT 密钥配置（openblog.jwt.secret，env OPENBLOG_JWT_SECRET 注入同一值）。 */
@ConfigurationProperties(prefix = "openblog.jwt")
public class JwtProperties {

    private String secret;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
```

`GatewayProperties.java`：

```java
package com.yqz.openblog.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 网关自定义配置：认证白名单 + 限流规则（openblog.gateway.*）。 */
@ConfigurationProperties(prefix = "openblog.gateway")
public class GatewayProperties {

    private Auth auth = new Auth();
    private RateLimit rateLimit = new RateLimit();

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class Auth {
        private List<String> skipPaths = new ArrayList<>();

        public List<String> getSkipPaths() {
            return skipPaths;
        }

        public void setSkipPaths(List<String> skipPaths) {
            this.skipPaths = skipPaths;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private List<Rule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }
    }

    public static class Rule {
        private String path;
        private long windowMs = 60_000;
        private int limit = 60;
        private Scope scope = Scope.IP;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public long getWindowMs() {
            return windowMs;
        }

        public void setWindowMs(long windowMs) {
            this.windowMs = windowMs;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public Scope getScope() {
            return scope;
        }

        public void setScope(Scope scope) {
            this.scope = scope;
        }
    }

    public enum Scope {
        IP, IP_UID
    }
}
```

- [ ] **Step 2: 写失败测试 `JwtCheckFilterTest.java`**

```java
package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtCheckFilterTest {

    private static final String SECRET = "test-secret-key-0123456789abcdef-0123456789";

    private JwtCheckFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret(SECRET);
        JwtVerifier verifier = new JwtVerifier(jwtProps);

        GatewayProperties props = new GatewayProperties();
        props.getAuth().setSkipPaths(List.of("/api/v1/auth/login"));
        filter = new JwtCheckFilter(props, verifier, new ObjectMapper());
    }

    private String signToken(long uid) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().claim("uid", uid)
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }

    private ServerWebExchange exchange(String path, String bearer) {
        var req = MockServerHttpRequest.get(path);
        if (bearer != null) {
            req.header("Authorization", "Bearer " + bearer);
        }
        return MockServerWebExchange.from(req.build());
    }

    @Test
    void whitelistedPath_passesEvenWithInvalidToken() {
        ServerWebExchange ex = exchange("/api/v1/auth/login", "invalid.token.here");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void validToken_passes() {
        ServerWebExchange ex = exchange("/api/v1/user/profile", signToken(42L));
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void noTokenOnProtectedPath_passesToBusiness() {
        // 无 token 放行交由 business 判定（匿名公共路由不被网关误伤）
        ServerWebExchange ex = exchange("/api/v1/articles/1", null);
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void invalidTokenOnProtectedPath_returns401() {
        ServerWebExchange ex = exchange("/api/v1/user/profile", "invalid.token.here");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("\"code\":4010");
    }

    @Test
    void expiredToken_returns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().claim("uid", 7L)
                .setExpiration(new java.util.Date(System.currentTimeMillis() - 60_000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        ServerWebExchange ex = exchange("/api/v1/user/profile", expired);
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=JwtCheckFilterTest`
Expected: FAIL（类不存在）。

- [ ] **Step 4: 实现 `JwtVerifier.java`**

```java
package com.yqz.openblog.gateway.filter;

import com.yqz.openblog.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 校验（与 business JwtService 同源：HS256 + openblog.jwt.secret）。
 * 只做「签名 + 过期」粗校验并取 uid；角色授权仍由 business 完成。
 */
@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(JwtProperties props) {
        String secret = props.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("openblog.jwt.secret 长度须至少 32 字符（与 business 同源）");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** @return 解析出的 userId；token 缺失/无效/过期返回 null */
    public Long parseUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Object uid = claims.get("uid");
            if (uid instanceof Integer i) {
                return i.longValue();
            }
            if (uid instanceof Long l) {
                return l;
            }
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
```

- [ ] **Step 5: 实现 `JwtCheckFilter.java`**

```java
package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.handler.GatewayResponses;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT 粗校验（order=-2）。
 * 语义：白名单外路径「无 token → 放行交由 business 判定（避免误伤匿名公共路由）；
 * 带 token 但无效/过期 → 边缘 401」。token 原样透传，business 保留完整授权。
 */
@Component
public class JwtCheckFilter implements GlobalFilter, Ordered {

    private final GatewayProperties props;
    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtCheckFilter(GatewayProperties props, JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.props = props;
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isSkipped(path)) {
            return chain.filter(exchange);
        }
        String token = bearerToken(exchange);
        if (token != null && jwtVerifier.parseUserId(token) == null) {
            return GatewayResponses.writeJson(exchange, objectMapper,
                    HttpStatus.UNAUTHORIZED, 4010, "登录已失效");
        }
        return chain.filter(exchange);
    }

    private boolean isSkipped(String path) {
        return props.getAuth().getSkipPaths().stream()
                .anyMatch(p -> pathMatcher.match(p, path));
    }

    private String bearerToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
```

- [ ] **Step 6: 运行确认通过**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=JwtCheckFilterTest`
Expected: PASS（5 个用例）。

- [ ] **Step 7: Commit**

```bash
git add OpenBlog-gateway
git commit -m "feat(gateway): JWT coarse validation with whitelist, pass-through for anonymous paths"
```

---

## Task 5: RateLimitFilter（framework-redis 滑动窗口）

**Files:**
- Create: `OpenBlog-gateway/src/main/java/com/yqz/openblog/gateway/filter/RateLimitFilter.java`
- Create: `OpenBlog-gateway/src/test/java/com/yqz/openblog/gateway/filter/RateLimitFilterTest.java`

- [ ] **Step 1: 写失败测试 `RateLimitFilterTest.java`**

```java
package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.config.JwtProperties;
import com.yqz.openblog.redis.limiter.SlidingWindowLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private GatewayProperties props;
    private SlidingWindowLimiter limiter;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        props = new GatewayProperties();
        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("test-secret-key-0123456789abcdef-0123456789");
        limiter = mock(SlidingWindowLimiter.class);
        filter = new RateLimitFilter(props, limiter, new JwtVerifier(jwtProps), new ObjectMapper());
    }

    private void addRule(String path, int limit, GatewayProperties.Scope scope) {
        GatewayProperties.Rule rule = new GatewayProperties.Rule();
        rule.setPath(path);
        rule.setWindowMs(60_000);
        rule.setLimit(limit);
        rule.setScope(scope);
        props.getRateLimit().getRules().add(rule);
    }

    private ServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path).header("X-Forwarded-For", "1.2.3.4").build());
    }

    @Test
    void allowedRequest_passes() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(true);
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void overLimit_returns429() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        when(limiter.tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString()))
                .thenReturn(false);
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("\"code\":4290");
    }

    @Test
    void disabled_passesWithoutCallingLimiter() {
        props.getRateLimit().setEnabled(false);
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        ServerWebExchange ex = exchange("/api/v1/auth/login");
        filter.filter(ex, c -> Mono.empty()).block();
        verify(limiter, never()).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }

    @Test
    void unmatchedPath_passesWithoutCallingLimiter() {
        addRule("/api/v1/auth/login", 10, GatewayProperties.Scope.IP);
        ServerWebExchange ex = exchange("/api/v1/articles/1");
        filter.filter(ex, c -> Mono.empty()).block();
        verify(limiter, never()).tryAcquire(anyString(), anyLong(), anyInt(), anyLong(), anyString());
        assertThat(ex.getResponse().getStatusCode()).isNull();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=RateLimitFilterTest`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 实现 `RateLimitFilter.java`**

```java
package com.yqz.openblog.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yqz.openblog.gateway.config.GatewayProperties;
import com.yqz.openblog.gateway.handler.GatewayResponses;
import com.yqz.openblog.redis.limiter.SlidingWindowLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * 限流防刷（order=-1）：复用 framework-redis SlidingWindowLimiter（Redis ZSET+Lua 原子滑动窗口）。
 * 阻塞 Redis 调用在 boundedElastic 执行，chain.filter 回到事件循环，不阻塞 Netty。
 * key = gateway:rl:{IP}[_{uid}]_{path}；scope=IP_UID 时从已校验 token 解析 uid（无 token 退化为纯 IP）。
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final GatewayProperties props;
    private final SlidingWindowLimiter limiter;
    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(GatewayProperties props, SlidingWindowLimiter limiter,
                           JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.props = props;
        this.limiter = limiter;
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!props.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        return props.getRateLimit().getRules().stream()
                .filter(r -> r.getPath() != null && PATH_MATCHER.match(r.getPath(), path))
                .findFirst()
                .map(rule -> applyRule(exchange, chain, rule))
                .orElseGet(() -> chain.filter(exchange));
    }

    private Mono<Void> applyRule(ServerWebExchange exchange, GatewayFilterChain chain,
                                 GatewayProperties.Rule rule) {
        return Mono.fromCallable(() -> {
            String ip = clientIp(exchange);
            String uid = null;
            if (rule.getScope() == GatewayProperties.Scope.IP_UID) {
                uid = resolveUid(exchange);
            }
            String key = "gateway:rl:" + ip + (uid != null ? "_" + uid : "") + "_" + rule.getPath();
            return limiter.tryAcquire(key, rule.getWindowMs(), rule.getLimit(),
                    System.currentTimeMillis(), UUID.randomUUID().toString());
        }).subscribeOn(Schedulers.boundedElastic()).flatMap(allowed -> {
            if (allowed) {
                return chain.filter(exchange);
            }
            log.warn("gateway rate limited: path={}", exchange.getRequest().getPath());
            return GatewayResponses.writeJson(exchange, objectMapper,
                    HttpStatus.TOO_MANY_REQUESTS, 4290, "请求过于频繁，请稍后再试");
        });
    }

    private String resolveUid(ServerWebExchange exchange) {
        String token = bearerToken(exchange);
        Long uid = token != null ? jwtVerifier.parseUserId(token) : null;
        return uid != null ? uid.toString() : null;
    }

    private String bearerToken(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private String clientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=RateLimitFilterTest`
Expected: PASS（4 个用例）。

- [ ] **Step 5: Commit**

```bash
git add OpenBlog-gateway
git commit -m "feat(gateway): rate limiting via framework-redis SlidingWindowLimiter"
```

---

## Task 6: application.yaml 完整配置 + 上下文加载测试

**Files:**
- Modify: `OpenBlog-gateway/src/main/resources/application.yaml`
- Create: `OpenBlog-gateway/src/test/java/com/yqz/openblog/gateway/GatewayApplicationTest.java`

- [ ] **Step 1: 写失败测试 `GatewayApplicationTest.java`**

```java
package com.yqz.openblog.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoadsAndRoutesConfigured() {
        int routes = routeLocator.getRoutes().collectList().block().size();
        assertThat(routes).isGreaterThan(0);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=GatewayApplicationTest`
Expected: FAIL（无 application.yaml 路由配置，断言 0 > 0 失败；或上下文缺配置启动失败）。

- [ ] **Step 3: 补全 `application.yaml`**

```yaml
server:
  port: 8080

spring:
  application:
    name: openblog-gateway
  data:
    redis:
      host: 10.21.76.221
      port: 6739
      password: 12345678aa
  cloud:
    gateway:
      routes:
        - id: business-api
          uri: http://host.docker.internal:8082
          predicates:
            - Path=/api/**
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origin-patterns:
              - "https://www.wecode.xin"
              - "http://localhost:5173"
              - "http://localhost"
            allowed-methods: "*"
            allowed-headers: "*"
            allow-credentials: false
            max-age: 3600

openblog:
  jwt:
    secret: ${OPENBLOG_JWT_SECRET:openblog-jwt-secret-key-please-change-me-0123456789abcdef}
  gateway:
    auth:
      skip-paths:
        - /api/v1/auth/login
        - /api/v1/auth/register
        - /api/v1/auth/email/code
        - /api/v1/auth/captcha
        - /api/v1/auth/refresh
    rate-limit:
      enabled: true
      rules:
        - path: /api/v1/auth/login
          window-ms: 60000
          limit: 10
          scope: IP
        - path: /api/v1/auth/email/code
          window-ms: 60000
          limit: 5
          scope: IP
        - path: /api/v1/auth/refresh
          window-ms: 60000
          limit: 20
          scope: IP
        - path: /api/v1/comments/**
          window-ms: 60000
          limit: 30
          scope: IP_UID
        - path: /api/v1/forum/**
          window-ms: 60000
          limit: 30
          scope: IP_UID
        - path: /api/v1/feedback/**
          window-ms: 60000
          limit: 20
          scope: IP
```

> 若 SCG 2025.0.x 解析 `spring.cloud.gateway.*` 前缀告警，可尝试迁移到 `spring.cloud.gateway.server.webflux.*`（Task 6 实现时以实际解析结果为准，二选一即可）。

- [ ] **Step 4: 运行确认通过**

Run: `mvn -pl :OpenBlog-gateway test -Dtest=GatewayApplicationTest`
Expected: PASS（上下文加载 + 1 条路由配置就位）。

- [ ] **Step 5: 全模块回归**

Run: `mvn -pl OpenBlog-gateway -am test`
Expected: PASS（TraceIdFilterTest + GatewayErrorHandlerTest + JwtCheckFilterTest + RateLimitFilterTest + GatewayApplicationTest 全绿）。

- [ ] **Step 6: Commit**

```bash
git add OpenBlog-gateway
git commit -m "feat(gateway): full config with routes, globalcors, redis, rate-limit rules"
```

---

## Task 7: business CORS 收敛（`*` → 真实域名）

**Files:**
- Modify: `OpenBlog-business/src/main/resources/application.yaml:92-94`

- [ ] **Step 1: 修改 CORS 白名单**

`OpenBlog-business/src/main/resources/application.yaml`：

```yaml
  cors:
    allowed-origin-patterns:
      - "https://www.wecode.xin"
      - "http://localhost:5173"
      - "http://localhost"
```

- [ ] **Step 2: 编译验证**

Run: `mvn -q -pl OpenBlog-business -am compile`
Expected: BUILD SUCCESS（纯配置改动，无测试变化）。

- [ ] **Step 3: Commit**

```bash
git add OpenBlog-business/src/main/resources/application.yaml
git commit -m "security(business): restrict CORS allowed-origin-patterns to real domains"
```

---

## Task 8: Nginx `/api/` 改指网关 8080

**Files:**
- Modify: `vue/nginx.conf:41-46`（仅 `/api/` 块）

- [ ] **Step 1: 修改 `/api/` 反代目标**

`vue/nginx.conf`：

```nginx
    location ^~ /api/ {
        proxy_pass http://host.docker.internal:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
```

> 其余 location（sitemap/robots/seo/爬虫）**保持直连 `host.docker.internal:8082` 不变**。

- [ ] **Step 2: 校验 Nginx 语法（本机无 nginx 可跳过，提交前确认目标行唯一）**

Run: `grep -n "host.docker.internal:8080" vue/nginx.conf`
Expected: 仅 `/api/` 块一处出现。

- [ ] **Step 3: Commit**

```bash
git add vue/nginx.conf
git commit -m "feat(gateway): route /api/** through gateway 8080 in nginx"
```

---

## Task 9: deploy/gateway Docker 化

**Files:**
- Create: `deploy/gateway/Dockerfile`
- Create: `deploy/gateway/docker-compose.yml`
- Create: `deploy/gateway/.dockerignore`

- [ ] **Step 1: 创建 `deploy/gateway/Dockerfile`**

```dockerfile
# OpenBlog-gateway 容器镜像
# 构建策略与 business/message 一致：本地/CI Maven 打 fat jar → 上传 → 镜像内只运行（不编译）。

FROM eclipse-temurin:17-jre

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

ARG JAR_FILE=OpenBlog-gateway-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

ENV JAVA_OPTS="-Xmx512M -Xms128M"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 2: 创建 `deploy/gateway/docker-compose.yml`**

```yaml
# OpenBlog-gateway Docker 编排
#
# 外部依赖（Redis 10.21.76.221:6739）走默认 bridge 网络直达。
# 发布 8080；/api/** 经网关转发到宿主机 business（host.docker.internal:8082，--add-host 由 CI/部署注入）。
# JWT secret 经 env OPENBLOG_JWT_SECRET 注入，须与 business 同值。

services:
  gateway:
    image: openblog-gateway:latest
    build:
      context: .
    container_name: openblog-gateway
    restart: always
    ports:
      - "8080:8080"
    extra_hosts:
      - "host.docker.internal:host-gateway"
    environment:
      TZ: Asia/Shanghai
      JAVA_OPTS: "-Xmx512M -Xms128M"
      OPENBLOG_JWT_SECRET: ${OPENBLOG_JWT_SECRET}
    networks:
      - openblog-gateway-net

networks:
  openblog-gateway-net:
    driver: bridge
```

- [ ] **Step 3: 创建 `deploy/gateway/.dockerignore`**

```
# 构建上下文只需 jar + Dockerfile。
*
!OpenBlog-gateway-*.jar
!Dockerfile
```

- [ ] **Step 4: Commit**

```bash
git add deploy/gateway
git commit -m "feat(gateway): dockerize gateway service (port 8080)"
```

---

## Task 10: CI 新增 gateway 独立链

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: `changes` 增加 `gateway` 输出**

`ci.yml` 的 `changes` job：

```yaml
    outputs:
      business: ${{ steps.filter.outputs.business }}
      message: ${{ steps.filter.outputs.message }}
      gateway: ${{ steps.filter.outputs.gateway }}
      frontend: ${{ steps.filter.outputs.frontend }}
```

filter 块末尾追加：

```yaml
            gateway:
              - 'OpenBlog-gateway/**'
              - 'OpenBlog-common/**'
              - 'OpenBlog-framework*/**'
              - 'deploy/gateway/**'
              - 'pom.xml'
            frontend:
              - 'vue/**'
```

- [ ] **Step 2: 在 message 链后追加 gateway 链（无跨链 needs）**

`ci.yml` 末尾（`build-frontend` 前）插入：

```yaml
  # ============ gateway 链（独立，与 business/message 互不影响） ============
  build-gateway:
    needs: changes
    if: ${{ needs.changes.outputs.gateway == 'true' || github.event_name == 'workflow_dispatch' }}
    runs-on: ubuntu-latest
    env:
      FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true

    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: "17"
          distribution: "temurin"
          cache: "maven"
      - name: Build gateway (Maven)
        run: mvn -B -ntp package -DskipTests -pl OpenBlog-gateway -am
      - name: Upload gateway JAR
        uses: actions/upload-artifact@v4
        with:
          name: gateway-jar
          path: OpenBlog-gateway/target/OpenBlog-gateway-*.jar
          retention-days: 1

  deploy-gateway:
    needs: build-gateway
    if: ${{ needs.build-gateway.result == 'success' || github.event_name == 'workflow_dispatch' }}
    runs-on: openblog-backend

    steps:
      - name: Checkout（获取 deploy/gateway 下的 Docker 部署文件）
        uses: actions/checkout@v4
      - name: Download gateway JAR
        uses: actions/download-artifact@v4
        with:
          name: gateway-jar
          path: gateway
      - name: Deploy via Docker（构建镜像 + 重启容器 + 注入 JWT secret）
        env:
          OPENBLOG_JWT_SECRET: ${{ secrets.OPENBLOG_JWT_SECRET }}
        run: |
          set -euo pipefail
          TARGET_DIR="/www/wwwroot/java/openblog-gateway"
          TARGET_JAR="$TARGET_DIR/OpenBlog-gateway-1.0.0-SNAPSHOT.jar"

          # 1. 停掉旧进程（若有，释放 8080）
          systemctl stop openblog-gateway 2>/dev/null || true

          # 2. 备份旧 jar
          mkdir -p "$TARGET_DIR"
          if [ -f "$TARGET_JAR" ]; then
            mkdir -p "$TARGET_DIR/backup"
            cp "$TARGET_JAR" "$TARGET_DIR/backup/OpenBlog-gateway-$(date +%Y%m%d-%H%M%S).jar" 2>/dev/null || true
          fi

          # 3. 拷贝新 jar + Docker 部署文件
          JAR_FILE=$(ls gateway/OpenBlog-gateway-*.jar | head -1)
          cp "$JAR_FILE" "$TARGET_JAR"
          cp deploy/gateway/Dockerfile deploy/gateway/docker-compose.yml deploy/gateway/.dockerignore "$TARGET_DIR/"

          # 4. JWT secret 写入服务器 .env（compose 自动加载）
          printf 'OPENBLOG_JWT_SECRET=%s\n' "$OPENBLOG_JWT_SECRET" > "$TARGET_DIR/.env"

          # 5. Docker 构建并启动容器
          cd "$TARGET_DIR"
          docker info >/dev/null 2>&1 || { echo "!! Docker 守护进程未运行，请先启动 Docker" >&2; exit 1; }
          if docker compose version >/dev/null 2>&1; then
            DC="docker compose"
          elif docker-compose version >/dev/null 2>&1; then
            DC="docker-compose"
          else
            echo "!! 服务器上没有 docker compose / docker-compose" >&2
            exit 1
          fi
          echo "==> 使用: $DC"
          $DC up -d --build --remove-orphans
          $DC ps
          echo "==> Deployed OpenBlog-gateway via Docker"
```

- [ ] **Step 3: 语法校验 YAML**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))" 2>/dev/null && echo "yaml ok" || echo "check yaml manually"`
Expected: `yaml ok`（或人工确认缩进）。

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci(gateway): add independent gateway build/deploy chain"
```

> ⚠️ `OPENBLOG_JWT_SECRET` 需在 GitHub Actions Secrets 配置（须与 business 的 `OPENBLOG_JWT_SECRET` 同值），否则 deploy-gateway 写入空值导致网关启动抛 IllegalStateException。空值防护已在 `JwtVerifier` 构造器兜底。

---

## Task 11: README 更新 + 上线清单（手动 E2E 验收）

**Files:**
- Modify: `README.md`、`deploy/business/README.md`（新增 gateway 段）

- [ ] **Step 1: README 模块表新增 gateway 行 + 部署说明**

`README.md` 模块表新增：

```markdown
| `OpenBlog-gateway` | API 网关（端口 8080）：统一 JWT 粗校验 / 限流 / traceId / 统一错误兜底 / CORS |
```

`README.md` 部署节新增：

```bash
# 3) gateway（API 网关，8080）—— CI 已接入：push master 自动 Docker 部署
mvn -pl OpenBlog-gateway -am package -DskipTests
# 产物 OpenBlog-gateway/target/OpenBlog-gateway-1.0.0-SNAPSHOT.jar，Docker 化部署见 deploy/gateway/
```

并在「待完成」勾掉网关相关项（如适用）。

- [ ] **Step 2: `deploy/business/README.md` 或新增 `deploy/gateway/README.md` 记录切换节奏**

新增 `deploy/gateway/README.md`，包含：

```markdown
# OpenBlog-gateway 部署

## 切换节奏（首次上线顺序）
1. 部署 gateway 容器（8080）—— CI deploy-gateway 或手动 `docker compose up -d --build`
2. 重建 vue 容器让 `/api/` 指向网关：`sh vue/docker.sh`（nginx.conf 已改 8080）
3. 验证：登录 / 读文章 / 写评论；未带 token 访问受保护接口 → business 401；伪造 token → 网关 401
4. 回退预案：nginx.conf `/api/` 改回 `host.docker.internal:8082` 重建 vue 容器，网关可保留不停

## 环境变量
- `OPENBLOG_JWT_SECRET`：与 business 同值（GitHub Secrets + 服务器 `.env`），注入容器
```

- [ ] **Step 3: Commit**

```bash
git add README.md deploy/gateway/README.md deploy/business/README.md
git commit -m "docs(gateway): document gateway deployment and switch-over checklist"
```

- [ ] **Step 4: 全量回归（本任务收尾，已实现代码全绿）**

Run:
```bash
mvn -pl OpenBlog-gateway -am test
mvn -pl OpenBlog-business -am test -DskipTests=false 2>/dev/null || mvn -pl OpenBlog-business -am test
```
Expected: gateway 5 个测试类全绿；business 现有测试不受影响。

---

## 验收（上线后，人工，对应 spec §6）

- [ ] 登录/注册/读文章/读评论 经网关正常（白名单 + 无 token 放行逻辑生效）。
- [ ] 伪造/过期 token 访问需鉴权路径 → 网关 401（`ApiResponse{4010}`），前端跳登录。
- [ ] 高频调用登录接口 → 429 限流文案（Redis 键 `gateway:rl:{IP}_/api/v1/auth/login`）。
- [ ] 响应头与错误响应体均带 `X-Trace-Id` / `traceId`。
- [ ] CORS：生产域名正常；非白名单 Origin 被拒。
- [ ] 网关容器 `restart: always`；`docker compose stop gateway` 后 Nginx 502（预期）→ 改回直连 8082 重建 vue 容器即恢复（回退演练）。
- [ ] 仅改 `OpenBlog-gateway/**` 的 PR 只触发 gateway 链（CI 独立验证）。
