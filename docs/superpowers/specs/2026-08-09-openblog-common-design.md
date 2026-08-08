# OpenBlog-common 公共模块抽取设计

日期:2026-08-09
状态:已批准

## 背景与目标

当前 `OpenBlog-business` 的 `com.yqz.openblog.common` 包内有一批通用 REST/基础设施代码(`ApiResponse`、`PageResult`、`BizException`、`TraceId`、`TreeUtils`、`GlobalExceptionHandler`),但它们被锁死在 business 模块内,**独立服务 `OpenBlog-email` 无法复用**。这导致:

- email 的 HTTP 接口返回原始对象 / `Map.of(...)`,与 business 的 `ApiResponse` 响应格式不一致。
- email 没有任何统一异常处理,出错时返回 Spring 默认错误 JSON。

**目标**:抽取独立 `OpenBlog-common` 模块,让 business / email 共用统一响应格式与异常处理;email 的 HTTP 接口切换为 `ApiResponse` 包装。Dubbo RPC 契约(`EmailSendResult`)保持不变。

## 范围

**做**:
- 新建 `OpenBlog-common` jar 模块,搬入 6 个公共类 + 异常处理。
- business 移除已搬走的源文件,加 common 依赖(import 零改动,因包名不变)。
- email 加 common 依赖,HTTP 接口切 `ApiResponse` / `PageResult`。

**不做**(后续单独处理):
- `MybatisPlusMetaObjectHandler`(MyBatis-Plus 配置,概念上属于 framework 模块)。
- 重复版本号收敛到根 pom `dependencyManagement`(属于独立的"基础设施清理"事项)。
- 单元测试补充(README 待办)。

## 新模块结构

```
OpenBlog-common/
├── pom.xml                                     # jar,parent = OpenBlog
└── src/main/java/com/yqz/openblog/common/
    ├── ApiResponse.java                        # 原样搬入
    ├── PageResult.java                         # 原样搬入
    ├── BizException.java                       # 原样搬入
    ├── TraceId.java                            # 原样搬入
    ├── TreeUtils.java                          # 原样搬入
    ├── GlobalExceptionHandler.java             # 移入,移除 security 相关方法
    ├── SecurityExceptionHandler.java           # 新增,security 异常处理,条件装配
    └── config/CommonAutoConfiguration.java     # 自动装配入口
src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### pom 依赖

全部版本继承父 pom 的 Spring Boot BOM,不硬编码:

| 依赖 | scope | 说明 |
|---|---|---|
| `spring-boot-autoconfigure` | compile | `@ConditionalOnClass` + 自动装配机制 |
| `spring-web` | compile | `@RestControllerAdvice`、`ResponseEntity`、校验异常 |
| `spring-security-core` | **optional** | 仅编译 `SecurityExceptionHandler`;不传递给下游模块 |
| `slf4j-api` | compile | 异常处理日志 |

不引入 `spring-boot-starter-web`(避免把整个 web starter 拖进消费方),只声明所需的最小依赖。

## 异常处理拆分

原 `GlobalExceptionHandler` 混合了 web 异常和 security 异常,拆成两个类:

### GlobalExceptionHandler(仅 web 依赖)
处理:
- `BizException` → code 前三位映射 HTTP status
- `MethodArgumentNotValidException` → 4001
- `IOException` → 5001(带日志)
- `Exception` 兜底 → 5001(带日志)

### SecurityExceptionHandler(新增,条件装配)
- 处理 `AccessDeniedException` / `AuthorizationDeniedException` → 4030 "无权限"
- 类级注解 `@ConditionalOnClass(AccessDeniedException.class)`
- 依赖 `spring-security-core`(optional scope),business 有 security 则生效,email 无 security 自动跳过

## 自动装配方式

**采用 Spring Boot 自动装配作为唯一注册机制**,而非修改 email 启动类扫描范围:

- `CommonAutoConfiguration`(`@AutoConfiguration`)通过 `@Bean` 注册 `GlobalExceptionHandler`、`SecurityExceptionHandler`。
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 声明该配置类。
- 与现有 `OpenBlog-framework-audit` 模块的做法一致。
- 原因:email 启动类在 `com.yqz.openblog.email` 包,默认组件扫描不到 `com.yqz.openblog.common`;用 `scanBasePackages` 硬改扫描范围脆弱,自动装配是标准库化方案。

### 组件扫描冲突及处理(关键)

`@RestControllerAdvice` 是 `@Component` 的元注解。business 启动类 `OpenBlogApplication` 位于 `com.yqz.openblog` 包,**默认组件扫描会覆盖 `com.yqz.openblog.common.**`**,导致异常处理器被组件扫描 + 自动装配各注册一次 → 同名 bean 冲突(`ConflictingBeanDefinitionException`)。

**处理**:自动装配是唯一注册机制,business 启动类用正则过滤将 `com.yqz.openblog.common.*` 排除出组件扫描:

```java
@SpringBootApplication
@ComponentScan(
    basePackages = "com.yqz.openblog",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.yqz\\.openblog\\.common\\..*"))
public class OpenBlogApplication { ... }
```

注意:
- POJO 类(`ApiResponse` 等)不是 Spring bean,不受排除影响,包名不变、business 的 import 依旧零改动。
- email 不扫描 `com.yqz.openblog` 包,无需排除,自动装配直接生效。
- 两个 `@ConditionalOnWebApplication` 守卫保证该自动装配只在 web 应用中生效。

## 依赖接线

## 依赖接线

1. **根 `pom.xml`**:`<modules>` 增加 `<module>OpenBlog-common</module>`。
2. **`OpenBlog-business/pom.xml`**:增加 `OpenBlog-common` 依赖;删除已搬走的 6 个源文件(`ApiResponse`、`PageResult`、`BizException`、`TraceId`、`TreeUtils`、`GlobalExceptionHandler`);`OpenBlogApplication` 加 `@ComponentScan` 排除 `com.yqz.openblog.common.*`。
3. **`OpenBlog-email/pom.xml`**:增加 `OpenBlog-common` 依赖。

## email 接口契约变更

`EmailAdminController` 两个 HTTP 端点切换包装格式:

| 端点 | 现状 | 改为 |
|---|---|---|
| `POST /api/v1/email/test` | 返回 `EmailSendResult` | `ApiResponse<EmailSendResult>` |
| `GET /api/v1/email/records` | 返回 `Map.of("items", ...)` | `ApiResponse<PageResult<EmailRecordResponse>>` |

**不变**:
- Dubbo RPC 接口 `EmailRpcService.send()` 仍返回 `EmailSendResult`(RPC 契约不裹壳)。
- business 的 HTTP 响应结构(`code/message/data/traceId`)不变,前端零影响。

**顺带收益**:email 获得统一异常响应与 `traceId` 字段。

## 验证

```bash
mvn -pl OpenBlog-common -am clean install
mvn -pl OpenBlog-business -am clean package -DskipTests
mvn -pl OpenBlog-email -am clean package -DskipTests
```

- business 编译通过且 import 无遗漏(包名不变,预期无需改业务代码)。
- email 编译通过,新响应格式生效。
- 前端无需改动。
