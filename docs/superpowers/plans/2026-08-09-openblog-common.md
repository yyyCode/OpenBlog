# OpenBlog-common 抽取 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 抽取独立 `OpenBlog-common` 模块,让 business / email 共用统一响应格式与异常处理,email HTTP 接口切换为 `ApiResponse` 包装。

**Architecture:** 纯 jar 库模块,包名沿用 `com.yqz.openblog.common`(business 的 50+ 处 import 零改动)。异常处理器通过 Spring Boot 自动装配唯一注册;business 启动类用正则过滤把 common 包排除出组件扫描,避免同名 bean 冲突。Dubbo RPC 契约 `EmailSendResult` 保持不变。

**Tech Stack:** Maven 多模块 · Spring Boot 3.5 自动装配(`@AutoConfiguration` + `AutoConfiguration.imports`) · `@ConditionalOnClass` 条件装配 · MyBatis-Plus。

**设计文档:** `docs/superpowers/specs/2026-08-09-openblog-common-design.md`

---

## 文件清单

**创建(OpenBlog-common 模块):**
- `OpenBlog-common/pom.xml`
- `OpenBlog-common/src/main/java/com/yqz/openblog/common/ApiResponse.java`(自 business 原样搬入)
- `OpenBlog-common/src/main/java/com/yqz/openblog/common/PageResult.java`(原样搬入)
- `OpenBlog-common/src/main/java/com/yqz/openblog/common/BizException.java`(原样搬入)
- `OpenBlog-common/src/main/java/com/yqz/openblog/common/TraceId.java`(原样搬入)
- `OpenBlog-common/src/main/java/com/yqz/openblog/common/TreeUtils.java`(原样搬入)
- `OpenBlog-common/src/main/java/com/yqz/openblog/common/GlobalExceptionHandler.java`(移入,移除 security 方法)
- `OpenBlog-common/src/main/java/com/yqz/openblog/common/SecurityExceptionHandler.java`(新增)
- `OpenBlog-common/src/main/java/com/yqz/openblog/common/config/CommonAutoConfiguration.java`(新增)
- `OpenBlog-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(新增)

**修改:**
- `pom.xml`(根,注册模块)
- `OpenBlog-business/pom.xml`(加 common 依赖)
- `OpenBlog-email/pom.xml`(加 common 依赖)
- `OpenBlog-business/src/main/java/com/yqz/openblog/OpenBlogApplication.java`(加 `@ComponentScan` 排除)
- `OpenBlog-email/src/main/java/com/yqz/openblog/email/controller/EmailAdminController.java`(切 `ApiResponse` / `PageResult`)
- `README.md`(模块表)

**删除(business 内的旧文件):**
- `OpenBlog-business/src/main/java/com/yqz/openblog/common/{ApiResponse,PageResult,BizException,TraceId,TreeUtils,GlobalExceptionHandler}.java`

---

### Task 1: 创建 OpenBlog-common 模块骨架

**Files:**
- Create: `OpenBlog-common/pom.xml`
- Modify: `pom.xml`(根)

- [ ] **Step 1: 创建 `OpenBlog-common/pom.xml`**

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

    <artifactId>OpenBlog-common</artifactId>
    <name>OpenBlog-common</name>
    <description>OpenBlog common module — unified REST response, exceptions, and utilities</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-core</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

版本全部继承父 pom 的 Spring Boot BOM(`spring-boot-dependencies`),不硬编码。

- [ ] **Step 2: 根 `pom.xml` 注册模块**

在 `<modules>` 中新增(建议放首位,common 是基础):

```xml
<modules>
    <module>OpenBlog-common</module>
    <module>OpenBlog-framework-redis</module>
    <module>OpenBlog-framework-elasticsearch</module>
    <module>OpenBlog-api</module>
    <module>OpenBlog-email</module>
    <module>OpenBlog-business</module>
    <module>OpenBlog-framework-audit</module>
</modules>
```

- [ ] **Step 3: 验证模块被识别**

Run: `mvn -q -pl OpenBlog-common -am clean package -DskipTests`
Expected: BUILD SUCCESS(空 jar 模块可正常构建)

- [ ] **Step 4: Commit**

```bash
git add pom.xml OpenBlog-common/pom.xml
git commit -m "build(common): create OpenBlog-common module skeleton"
```

---

### Task 2: 搬入 5 个 POJO 类(原样迁移)

**Files:**
- Create: `OpenBlog-common/src/main/java/com/yqz/openblog/common/ApiResponse.java`
- Create: `OpenBlog-common/src/main/java/com/yqz/openblog/common/PageResult.java`
- Create: `OpenBlog-common/src/main/java/com/yqz/openblog/common/BizException.java`
- Create: `OpenBlog-common/src/main/java/com/yqz/openblog/common/TraceId.java`
- Create: `OpenBlog-common/src/main/java/com/yqz/openblog/common/TreeUtils.java`

- [ ] **Step 1: 创建 `ApiResponse.java`**(内容与 business 原文件逐字一致)

```java
package com.yqz.openblog.common;

/**
 * 统一返回结构（MVP 先用简单 code/message/data）。
 */
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        r.traceId = TraceId.get();
        return r;
    }

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        r.data = null;
        r.traceId = TraceId.get();
        return r;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
```

- [ ] **Step 2: 创建 `PageResult.java`**

```java
package com.yqz.openblog.common;

import java.util.List;

public class PageResult<T> {
    private List<T> items;
    private int page;
    private int size;
    private long total;

    public PageResult() {
    }

    public PageResult(List<T> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
```

- [ ] **Step 3: 创建 `BizException.java`**

```java
package com.yqz.openblog.common;

/**
 * 业务异常（统一由 GlobalExceptionHandler 捕获并转成 ApiResponse）。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
```

- [ ] **Step 4: 创建 `TraceId.java`**

```java
package com.yqz.openblog.common;

import java.util.UUID;

/**
 * 简单 traceId 生成器（MVP）。
 * 后续可接 MDC + 日志框架对接。
 */
public final class TraceId {

    private TraceId() {
    }

    public static String get() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
```

- [ ] **Step 5: 创建 `TreeUtils.java`**

```java
package com.yqz.openblog.common;

import java.util.*;
import java.util.function.Function;

/**
 * 树形结构通用工具方法。CategoryService 和 MediaFolderService 共用。
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * 按 ID 建立索引 Map。
     */
    public static <T> Map<Long, T> indexById(List<T> list, Function<T, Long> idGetter) {
        Map<Long, T> map = new HashMap<>();
        for (T item : list) {
            map.put(idGetter.apply(item), item);
        }
        return map;
    }

    /**
     * 从指定节点向上追溯，构建路径名列表（从根到当前节点）。
     */
    public static <T> List<String> buildPathNames(Long nodeId,
                                                   Map<Long, T> byId,
                                                   Function<T, Long> parentIdGetter,
                                                   Function<T, String> nameGetter) {
        List<String> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long current = nodeId;
        while (current != null && visited.add(current)) {
            T node = byId.get(current);
            if (node == null) {
                break;
            }
            path.add(0, nameGetter.apply(node));
            current = parentIdGetter.apply(node);
        }
        return path;
    }

    /**
     * 递归收集所有子孙节点 ID（含自身）。
     */
    public static void collectDescendants(Long id, Map<Long, List<Long>> childrenMap, Set<Long> out) {
        if (id == null || !out.add(id)) {
            return;
        }
        for (Long childId : childrenMap.getOrDefault(id, List.of())) {
            collectDescendants(childId, childrenMap, out);
        }
    }

    /**
     * 构建 parentId → children 映射。
     */
    public static Map<Long, List<Long>> buildChildrenMap(List<Long> ids, Function<Long, Long> parentIdGetter) {
        Map<Long, List<Long>> map = new HashMap<>();
        for (Long id : ids) {
            Long parentId = parentIdGetter.apply(id);
            if (parentId != null) {
                map.computeIfAbsent(parentId, k -> new ArrayList<>()).add(id);
            }
        }
        return map;
    }

    /**
     * 检查 nodeId 是否为 ancestorId 的后代。
     */
    public static <T> boolean isDescendant(Long ancestorId, Long nodeId, Map<Long, T> byId, Function<T, Long> parentIdGetter) {
        Long current = nodeId;
        while (current != null) {
            if (current.equals(ancestorId)) {
                return true;
            }
            T node = byId.get(current);
            current = node == null ? null : parentIdGetter.apply(node);
        }
        return false;
    }
}
```

- [ ] **Step 6: 验证 common 编译**

Run: `mvn -q -pl OpenBlog-common -am clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add OpenBlog-common/src/main/java/com/yqz/openblog/common/ApiResponse.java OpenBlog-common/src/main/java/com/yqz/openblog/common/PageResult.java OpenBlog-common/src/main/java/com/yqz/openblog/common/BizException.java OpenBlog-common/src/main/java/com/yqz/openblog/common/TraceId.java OpenBlog-common/src/main/java/com/yqz/openblog/common/TreeUtils.java
git commit -m "feat(common): move common POJOs and TreeUtils into OpenBlog-common"
```

---

### Task 3: 异常处理与自动装配

**Files:**
- Create: `OpenBlog-common/src/main/java/com/yqz/openblog/common/GlobalExceptionHandler.java`
- Create: `OpenBlog-common/src/main/java/com/yqz/openblog/common/SecurityExceptionHandler.java`
- Create: `OpenBlog-common/src/main/java/com/yqz/openblog/common/config/CommonAutoConfiguration.java`
- Create: `OpenBlog-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建 `GlobalExceptionHandler.java`**(自 business 移入,**移除** security 两个方法)

```java
package com.yqz.openblog.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理（OpenBlog-common）。通过 CommonAutoConfiguration 自动装配注册。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> onBiz(BizException ex) {
        int code = ex.getCode();
        // MVP：约定 code 的前 3 位近似映射为 HTTP status（如 4041 -> 404）。
        int httpStatus = Math.max(400, Math.min(500, code / 10));
        return ResponseEntity.status(httpStatus).body(ApiResponse.fail(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> onValidation(MethodArgumentNotValidException ex) {
        var fe = ex.getBindingResult().getFieldError();
        String msg =
                fe != null && fe.getDefaultMessage() != null && !fe.getDefaultMessage().isBlank()
                        ? fe.getDefaultMessage()
                        : "参数校验失败";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(4001, msg));
    }

    /**
     * IO 异常（MinIO 读写、文件读写等）直接返回原始错误信息，便于排查。
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Object>> onIO(IOException ex) {
        log.error("IO exception", ex);
        String msg = ex.getMessage() != null ? ex.getMessage() : "IO异常";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(5001, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> onOther(Exception ex) {
        log.error("unhandled server error", ex);
        String msg = ex.getMessage() != null ? ex.getMessage() : "服务器异常";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(5001, msg));
    }
}
```

- [ ] **Step 2: 创建 `SecurityExceptionHandler.java`**(新增,条件装配,email 无 security 依赖时自动跳过)

```java
package com.yqz.openblog.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 安全相关异常处理，仅在 classpath 存在 spring-security 时由 CommonAutoConfiguration 装配。
 * （email 等无 security 依赖的服务自动跳过。）
 */
@RestControllerAdvice
public class SecurityExceptionHandler {

    /**
     * 方法级鉴权（@PreAuthorize）失败会抛出 AuthorizationDeniedException；
     * 以前会落到兜底 Exception -> 5001，导致前端误判为“服务器异常”。
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Object>> onAccessDenied(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail(4030, "无权限"));
    }
}
```

- [ ] **Step 3: 创建 `CommonAutoConfiguration.java`**(自动装配唯一入口)

```java
package com.yqz.openblog.common.config;

import com.yqz.openblog.common.GlobalExceptionHandler;
import com.yqz.openblog.common.SecurityExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * OpenBlog-common 自动装配：注册统一异常处理器。
 * 注意：调用方组件扫描应排除 com.yqz.openblog.common.*，避免与自动装配重复注册。
 */
@AutoConfiguration
@ConditionalOnWebApplication
public class CommonAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }
}
```

`@ConditionalOnClass` 使用字符串形式的类名,基于 ASM 检查 classpath,**不存在 spring-security 时不会加载 `SecurityExceptionHandler` 类**,因此 email 可安全消费 common。

- [ ] **Step 4: 创建 `AutoConfiguration.imports`**

文件 `OpenBlog-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,内容一行:

```
com.yqz.openblog.common.config.CommonAutoConfiguration
```

- [ ] **Step 5: 验证 common 编译**

Run: `mvn -q -pl OpenBlog-common -am clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add OpenBlog-common/
git commit -m "feat(common): add global exception handlers via auto-configuration"
```

---

### Task 4: business 接线——依赖、删旧文件、排除扫描

**Files:**
- Modify: `OpenBlog-business/pom.xml`
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/OpenBlogApplication.java`
- Delete: `OpenBlog-business/src/main/java/com/yqz/openblog/common/ApiResponse.java`
- Delete: `OpenBlog-business/src/main/java/com/yqz/openblog/common/PageResult.java`
- Delete: `OpenBlog-business/src/main/java/com/yqz/openblog/common/BizException.java`
- Delete: `OpenBlog-business/src/main/java/com/yqz/openblog/common/TraceId.java`
- Delete: `OpenBlog-business/src/main/java/com/yqz/openblog/common/TreeUtils.java`
- Delete: `OpenBlog-business/src/main/java/com/yqz/openblog/common/GlobalExceptionHandler.java`

- [ ] **Step 1: `OpenBlog-business/pom.xml` 加 common 依赖**

在 `<dependencies>` 中(Email RPC 接口依赖之后)新增:

```xml
        <dependency>
            <groupId>com.yqz</groupId>
            <artifactId>OpenBlog-common</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: `OpenBlogApplication.java` 加 `@ComponentScan` 排除**

完整替换为:

```java
package com.yqz.openblog;

import com.yqz.openblog.config.AuthSecurityProperties;
import com.yqz.openblog.config.CorsProperties;
import com.yqz.openblog.config.SiteProperties;
import com.yqz.openblog.seo.SeoProperties;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDubbo
@EnableScheduling
@EnableConfigurationProperties({
        SiteProperties.class,
        CorsProperties.class,
        AuthSecurityProperties.class,
        SeoProperties.class
})
@ComponentScan(
        basePackages = "com.yqz.openblog",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.yqz\\.openblog\\.common\\..*"))
public class OpenBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenBlogApplication.class, args);
    }

}
```

说明:显式 `@ComponentScan` 会覆盖 `@SpringBootApplication` 的默认扫描(`basePackages` 保持一致,仅多了排除)。`com.yqz.openblog.common.*` 里的异常处理器改由自动装配注册,POJO 类不是 bean 不受影响。

- [ ] **Step 3: 删除 business 内的 6 个旧文件**

```bash
git rm OpenBlog-business/src/main/java/com/yqz/openblog/common/ApiResponse.java \
       OpenBlog-business/src/main/java/com/yqz/openblog/common/PageResult.java \
       OpenBlog-business/src/main/java/com/yqz/openblog/common/BizException.java \
       OpenBlog-business/src/main/java/com/yqz/openblog/common/TraceId.java \
       OpenBlog-business/src/main/java/com/yqz/openblog/common/TreeUtils.java \
       OpenBlog-business/src/main/java/com/yqz/openblog/common/GlobalExceptionHandler.java
```

(删除后 `com/yqz/openblog/common/` 目录如变空则一并移除,git 不追踪空目录无需担心。)

- [ ] **Step 4: 验证 business 编译(含依赖模块)**

Run: `mvn -q -pl OpenBlog-business -am clean package -DskipTests`
Expected: BUILD SUCCESS。若出现"找不到符号 `com.yqz.openblog.common.X`",说明业务代码引用了未搬走的类——检查是否漏删/漏建。

- [ ] **Step 5: 核对无残留引用**

Run: `grep -rn "com.yqz.openblog.common" OpenBlog-business/src --include="*.java" | grep -v "/common/" | grep import`
Expected: 输出为 business 代码里的 `import com.yqz.openblog.common.*`(这些现在解析到 common 模块,正确),**不应**有指向 business 本地 common 包的异常。

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(business): depend on OpenBlog-common, exclude common package from scan"
```

---

### Task 5: email 接线与接口迁移

**Files:**
- Modify: `OpenBlog-email/pom.xml`
- Modify: `OpenBlog-email/src/main/java/com/yqz/openblog/email/controller/EmailAdminController.java`

- [ ] **Step 1: `OpenBlog-email/pom.xml` 加 common 依赖**

在 `<dependencies>` 中(RPC interface 依赖之后)新增:

```xml
        <dependency>
            <groupId>com.yqz</groupId>
            <artifactId>OpenBlog-common</artifactId>
            <version>${project.version}</version>
        </dependency>
```

- [ ] **Step 2: 重写 `EmailAdminController.java`**

完整替换为:

```java
package com.yqz.openblog.email.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.email.api.EmailSendRequest;
import com.yqz.openblog.email.api.EmailSendResult;
import com.yqz.openblog.email.dto.EmailRecordResponse;
import com.yqz.openblog.email.service.EmailService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/email")
@CrossOrigin(origins = "*")
public class EmailAdminController {

    private final EmailService emailService;

    public EmailAdminController(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * 快速测试发送邮件：POST /api/v1/email/test?recipient=xxx&subject=xxx&body=xxx
     */
    @PostMapping("/test")
    public ApiResponse<EmailSendResult> testSend(
            @RequestParam String recipient,
            @RequestParam String subject,
            @RequestParam String body) {
        EmailSendRequest req = new EmailSendRequest(recipient, subject, body);
        return ApiResponse.ok(emailService.send(req));
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<EmailRecordResponse>> listRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        IPage<EmailRecordResponse> p = emailService.listRecords(page, size, status);
        PageResult<EmailRecordResponse> pr = new PageResult<>(p.getRecords(), page, size, p.getTotal());
        return ApiResponse.ok(pr);
    }
}
```

说明:`EmailService`、`EmailRpcService`、`EmailSendResult`(Dubbo RPC 契约)均**不动**。

- [ ] **Step 3: 验证 email 编译(含依赖模块)**

Run: `mvn -q -pl OpenBlog-email -am clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(email): wrap HTTP endpoints with ApiResponse/PageResult"
```

---

### Task 6: README 更新与全量构建

**Files:**
- Modify: `README.md`

- [ ] **Step 1: README 模块表加一行**

在模块表 `OpenBlog-business` 行前新增:

```markdown
| `OpenBlog-common` | 公共模块：统一响应 `ApiResponse`、`PageResult`、`BizException`、异常处理自动装配 |
```

- [ ] **Step 2: 全量构建验证**

Run: `mvn -q clean package -DskipTests`
Expected: BUILD SUCCESS(所有模块依次构建,含 common → business / email 依赖解析)

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: add OpenBlog-common to module table"
```

---

## 验收核对(对照设计文档)

| 设计要点 | 落地任务 |
|---|---|
| 新建 `OpenBlog-common` jar 模块 | Task 1 |
| 搬入 ApiResponse/PageResult/BizException/TraceId/TreeUtils | Task 2 |
| GlobalExceptionHandler 移除 security 方法 + 新增 SecurityExceptionHandler | Task 3 |
| 自动装配唯一注册 + business 排除 common 扫描 | Task 3 + Task 4 |
| business 加依赖、删旧文件、import 零改动 | Task 4 |
| email 加依赖、HTTP 接口切 ApiResponse/PageResult | Task 5 |
| RPC 契约 EmailSendResult 不变 | Task 5(未改 service/rpc) |
| 验证:三个模块 clean package | Task 4/5/6 |
