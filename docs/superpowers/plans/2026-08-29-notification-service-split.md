# 通知服务拆分实现计划（Notification Service Split）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `OpenBlog-business` 内的通知编排层整体迁入 `OpenBlog-message`，使 message 升级为「统一通知服务」，business 改走 Dubbo RPC，并将 message Docker 化 + 接入 CI（与 business 流水线互不影响）。

**Architecture:** business 侧 `EmailCodeService` 经新的 `NotificationRpcService.submit()` 同步调 message；message 内部 `NotificationRpcServiceImpl → NotificationService → ChannelRegistry → EmailNotificationChannel → EmailService`（进程内直调），outbox/Relay/MQ 随迁保留。共享契约（`NotificationMessage`/`NotificationChannelType`/RPC 接口）放 `OpenBlog-message-api`。CI 拆成 business 与 message 两条互不依赖的构建/部署链。

**Tech Stack:** Spring Boot 3.5 / Java 17 / MyBatis-Plus / Dubbo 3.3 + Nacos / RocketMQ / 阿里云 DirectMail / Docker + GitHub Actions（自托管 Runner）。

**设计文档:** `docs/superpowers/specs/2026-08-29-notification-service-split-design.md`
**开发分支:** `feat/notification-service-split`（当前已切换）

---

## 前置约定

- 所有命令在项目根目录（`E:/java/OpenBlog`）Git Bash 下执行。
- 每个 Task 结束都要让相关模块**可编译**（采用「先复制后删除」的 strangler 策略，保证任意时刻仓库处于可编译状态）。
- 提交信息遵循 Conventional Commits，末尾带 `Co-Authored-By: Claude <noreply@anthropic.com>`。
- 以下「业务包路径」统一指 `OpenBlog-business/src/main/java/com/yqz/openblog/notification/`。

### 文件地图（Task → 改动文件）

| Task | 文件 |
|------|------|
| 1 | `OpenBlog-api/OpenBlog-message-api/.../message/api/NotificationRpcService.java`（新建）、`NotificationSendResult.java`（新建）；`NotificationMessage.java`、`NotificationChannelType.java`（business→message-api 移动） |
| 2 | `OpenBlog-message/pom.xml`、`src/main/resources/application.yaml`、`OpenBlogMessageApplication.java`（`@EnableScheduling`） |
| 3 | message 新增 `com.yqz.openblog.notification.*`（复制适配）+ `message/service/NotificationRpcServiceImpl.java` + 应用主类补扫描/配置注册 + 模板渲染单测 |
| 4 | `EmailCodeService.java`（改 RPC）、删除 business `notification/` 包、删除 `EmailRpcService`/`EmailRpcServiceImpl`、business `pom.xml` 与两个 yaml 清理 |
| 5 | `deploy/message/Dockerfile`、`docker-compose.yml`、`.dockerignore` |
| 6 | `.github/workflows/ci.yml` |
| 7 | 端到端验证清单（无代码） |

---

### Task 1: message-api 新增共享通知契约

**Files:**
- Move: `OpenBlog-business/src/main/java/com/yqz/openblog/notification/NotificationMessage.java` → `OpenBlog-api/OpenBlog-message-api/src/main/java/com/yqz/openblog/notification/NotificationMessage.java`
- Move: `OpenBlog-business/src/main/java/com/yqz/openblog/notification/NotificationChannelType.java` → `OpenBlog-api/OpenBlog-message-api/src/main/java/com/yqz/openblog/notification/NotificationChannelType.java`
- Create: `OpenBlog-api/OpenBlog-message-api/src/main/java/com/yqz/openblog/message/api/NotificationRpcService.java`
- Create: `OpenBlog-api/OpenBlog-message-api/src/main/java/com/yqz/openblog/message/api/NotificationSendResult.java`

> 包名保持 `com.yqz.openblog.notification`（business 现有 import 不变，从 message-api 依赖解析同名类）。`EmailRpcService` **暂不删除**（business 仍引用，Task 4 再删）。

- [ ] **Step 1: 移动两个共享 DTO（包名不变）**

```bash
cd "E:/java/OpenBlog"
mkdir -p OpenBlog-api/OpenBlog-message-api/src/main/java/com/yqz/openblog/notification
git mv OpenBlog-business/src/main/java/com/yqz/openblog/notification/NotificationMessage.java \
       OpenBlog-api/OpenBlog-message-api/src/main/java/com/yqz/openblog/notification/NotificationMessage.java
git mv OpenBlog-business/src/main/java/com/yqz/openblog/notification/NotificationChannelType.java \
       OpenBlog-api/OpenBlog-message-api/src/main/java/com/yqz/openblog/notification/NotificationChannelType.java
```

- [ ] **Step 2: 新增 `NotificationRpcService.java`**（写入以下完整内容）

```java
package com.yqz.openblog.message.api;

import com.yqz.openblog.notification.NotificationMessage;

/**
 * 统一通知 RPC 接口（business 消费，message 提供）。
 * <p>
 * 与旧 EmailRpcService 的区别：入参是渠道无关的 {@link NotificationMessage}，
 * 由 message 内部按渠道路由投递；同步返回结果对象（显式错误码，不依赖 Dubbo 异常传播）。
 */
public interface NotificationRpcService {

    /** 注册验证码模板 code（共享常量：business 构造消息与 message 渲染共用同一来源）。 */
    String TEMPLATE_REGISTER_VERIFICATION_CODE = "register-verification-code";

    /** 同步提交一条通知并投递。失败时返回 result.success=false + 错误码/消息。 */
    NotificationSendResult submit(NotificationMessage message);
}
```

- [ ] **Step 3: 新增 `NotificationSendResult.java`**（写入以下完整内容）

```java
package com.yqz.openblog.message.api;

/**
 * 通知提交结果（跨服务显式错误语义）。
 * <p>
 * success=false 时携带 errorCode / errorMsg（如 5002「邮件服务暂不可用」），
 * 供 business 侧映射回 {@code BizException} 并做相应清理。
 */
public class NotificationSendResult {

    private boolean success;
    private Integer errorCode;
    private String errorMsg;

    public NotificationSendResult() {}

    public NotificationSendResult(boolean success, Integer errorCode, String errorMsg) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public static NotificationSendResult ok() {
        return new NotificationSendResult(true, null, null);
    }

    public static NotificationSendResult fail(int errorCode, String errorMsg) {
        return new NotificationSendResult(false, errorCode, errorMsg);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Integer getErrorCode() { return errorCode; }
    public void setErrorCode(Integer errorCode) { this.errorCode = errorCode; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}
```

- [ ] **Step 4: 编译验证 message-api 与 business 仍可编译**

Run:
```bash
cd "E:/java/OpenBlog"
mvn -q -pl OpenBlog-message-api -am compile
mvn -q -pl OpenBlog-business -am compile
```
Expected: `BUILD SUCCESS`（business 通过 message-api 依赖解析 `com.yqz.openblog.notification.NotificationMessage`）。

- [ ] **Step 5: Commit**

```bash
cd "E:/java/OpenBlog"
git add -A
git commit -m "$(cat <<'EOF'
feat(notification): add NotificationRpcService contract to message-api

- move NotificationMessage / NotificationChannelType to shared message-api
- add NotificationRpcService + NotificationSendResult (explicit error semantics)
- EmailRpcService kept until switch-over (Task 4)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: message 模块接入 RocketMQ 依赖、定时任务与配置

**Files:**
- Modify: `OpenBlog-message/pom.xml`
- Modify: `OpenBlog-message/src/main/resources/application.yaml`
- Modify: `OpenBlog-message/src/main/java/com/yqz/openblog/message/OpenBlogMessageApplication.java`

- [ ] **Step 1: message `pom.xml` 新增 RocketMQ 与 test 依赖**

在 `OpenBlog-message/pom.xml` 的 `<dependencies>` 末尾（`</dependencies>` 之前）加入：

```xml
        <!-- RocketMQ（通知异步投递）。版本与 business 原依赖对齐 -->
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-spring-boot-starter</artifactId>
            <version>2.3.2</version>
        </dependency>

        <!-- 单元测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: message `application.yaml` 加入 RocketMQ + 通知配置**

> ⚠️ 该文件已有顶层 `openblog:` 键（含 `message.aliyun`）。**切勿新增第二个顶层 `openblog:`**——YAML 重复键会被后者覆盖，导致阿里云配置丢失。分两处改：

① 文件末尾追加顶层 `rocketmq:` 块：

```yaml
# RocketMQ（通知异步投递）。name-server 指向部署 RocketMQ 的机器
rocketmq:
  name-server: 10.21.76.221:9876
  producer:
    group: openblog-notification-producer
```

② 在已有 `openblog:` 映射内追加 `notification:` 子块（锚定在 `message:` 块的 `from-alias: OpenBlog` 行之后，缩进 2 空格）：

```yaml
  # 统一通知抽象层（自 business 迁入）
  notification:
    email:
      enabled: true
      default-subject: OpenBlog
    outbox:
      enabled: true
      relay-interval-ms: 5000
      publish-window-ms: 1000
      max-retry: 8
```

> 改后 `openblog:` 下应同时含 `message` 与 `notification` 两个子键（与 business 原 `openblog.notification.*` 对齐）。

- [ ] **Step 3: message 启动类加 `@EnableScheduling`**

`OpenBlog-message/src/main/java/com/yqz/openblog/message/OpenBlogMessageApplication.java` 中：

在类注解区新增 `@EnableScheduling`（`OutboxRelay` 的 `@Scheduled` 需要）：

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDubbo
@EnableScheduling
@MapperScan("com.yqz.openblog.message.repo")
public class OpenBlogMessageApplication {
```

- [ ] **Step 4: 编译验证 message 模块**

Run:
```bash
cd "E:/java/OpenBlog"
mvn -q -pl OpenBlog-message -am compile
```
Expected: `BUILD SUCCESS`（新增依赖尚未被代码使用，仅校验依赖解析）。

- [ ] **Step 5: Commit**

```bash
cd "E:/java/OpenBlog"
git add -A
git commit -m "$(cat <<'EOF'
feat(notification): add rocketmq + scheduling to message service

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: 通知编排层迁入 message（复制 + 适配）

**Files:**
- Copy: business `notification/` 下 13 个类 → message 同包路径
- Create: `OpenBlog-message/src/main/java/com/yqz/openblog/message/service/NotificationRpcServiceImpl.java`
- Modify: `OpenBlog-message/.../OpenBlogMessageApplication.java`（补 `@ComponentScan`/`@MapperScan`/`@EnableConfigurationProperties`）
- Move: business 下**已存在的 5 个编排层测试** → message 同包（`src/test/java/com/yqz/openblog/notification/`）

> 本 Task 用**复制**（business 原文件暂留），保证 business 依旧可编译；Task 4 再删 business 副本并切换调用方。

- [ ] **Step 1: 复制编排层类到 message（同包名）**

```bash
cd "E:/java/OpenBlog"
SRC=OpenBlog-business/src/main/java/com/yqz/openblog/notification
DST=OpenBlog-message/src/main/java/com/yqz/openblog/notification
mkdir -p "$DST/outbox" "$DST/mq" "$DST/channel"
# 根级类（NotificationTemplateService 在 Step 3 适配常量，EmailNotificationChannel 在 Step 2 适配 in-process，其余直接 cp）
cp "$SRC/NotificationService.java" "$DST/"
cp "$SRC/NotificationChannel.java" "$DST/"
cp "$SRC/ChannelRegistry.java" "$DST/"
cp "$SRC/AbstractNotificationChannel.java" "$DST/"
cp "$SRC/NotificationProperties.java" "$DST/"
cp "$SRC/NotificationTemplateService.java" "$DST/"
# outbox
cp "$SRC/outbox/NotificationOutbox.java" "$DST/outbox/"
cp "$SRC/outbox/NotificationOutboxMapper.java" "$DST/outbox/"
cp "$SRC/outbox/OutboxRelay.java" "$DST/outbox/"
# mq
cp "$SRC/mq/NotificationMqProducer.java" "$DST/mq/"
cp "$SRC/mq/NotificationMqConsumer.java" "$DST/mq/"
cp "$SRC/mq/NotificationTopics.java" "$DST/mq/"
# channel（Step 2 覆盖为 in-process 适配版）
cp "$SRC/channel/EmailNotificationChannel.java" "$DST/channel/"
```

- [ ] **Step 2: 适配 `EmailNotificationChannel`（Dubbo → 进程内 EmailService）**

将 `OpenBlog-message/src/main/java/com/yqz/openblog/notification/channel/EmailNotificationChannel.java` **整体替换**为：

```java
package com.yqz.openblog.notification.channel;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.message.api.EmailSendRequest;
import com.yqz.openblog.message.api.EmailSendResult;
import com.yqz.openblog.message.service.EmailService;
import com.yqz.openblog.notification.AbstractNotificationChannel;
import com.yqz.openblog.notification.NotificationChannelType;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 邮件通知渠道（迁入 message 后的 in-process 版本）。
 * 继承模板方法基类，直接调 message 内 {@link EmailService} 直发，复用其幂等保障
 * （idempotencyKey + uk_idempotency_key 唯一索引）。新增渠道（SMS / 飞书）参照本类扩展。
 */
@Component
@ConditionalOnProperty(prefix = "openblog.notification.email", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmailNotificationChannel extends AbstractNotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final EmailService emailService;

    public EmailNotificationChannel(NotificationTemplateService templateService, EmailService emailService) {
        super(templateService);
        this.emailService = emailService;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    protected void doSend(NotificationMessage message, String content) {
        String idempotencyKey = (message.getMessageId() == null || message.getMessageId().isBlank())
                ? UUID.randomUUID().toString()
                : message.getMessageId();
        EmailSendRequest request = new EmailSendRequest(message.getRecipient(), message.getSubject(), content);
        request.setIdempotencyKey(idempotencyKey);

        EmailSendResult result;
        try {
            result = emailService.send(request);
        } catch (Exception e) {
            log.warn("邮件通知发送失败。recipient={}", message.getRecipient(), e);
            throw new BizException(5002, "邮件服务暂不可用，请稍后再试");
        }

        if (!"SENT".equals(result.getStatus())) {
            log.warn("邮件通知发送失败（status={}, errorMsg={}）。recipient={}",
                    result.getStatus(), result.getErrorMsg(), message.getRecipient());
            throw new BizException(5002, "邮件发送失败，请稍后再试");
        }
    }
}
```

- [ ] **Step 3: 适配 `NotificationTemplateService`（模板常量改指向共享契约）**

将 `OpenBlog-message/src/main/java/com/yqz/openblog/notification/NotificationTemplateService.java` 的**常量定义行**改为：

```java
public class NotificationTemplateService {

    /** 注册验证码模板 code（与共享契约同源，见 NotificationRpcService.TEMPLATE_REGISTER_VERIFICATION_CODE）。 */
    public static final String REGISTER_VERIFICATION_CODE = NotificationRpcService.TEMPLATE_REGISTER_VERIFICATION_CODE;
```

并新增 import：

```java
import com.yqz.openblog.message.api.NotificationRpcService;
```

（其余方法体、模板 Map 保持不变。）

- [ ] **Step 4: 新增 `NotificationRpcServiceImpl`**（写入以下完整内容）

```java
package com.yqz.openblog.message.service;

import com.yqz.openblog.common.BizException;
import com.yqz.openblog.message.api.NotificationRpcService;
import com.yqz.openblog.message.api.NotificationSendResult;
import com.yqz.openblog.notification.NotificationMessage;
import com.yqz.openblog.notification.NotificationService;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一通知 RPC provider。入参消息交进程内 {@link NotificationService} 路由投递；
 * 业务异常显式转成 {@link NotificationSendResult} 返回（不依赖 Dubbo 异常序列化）。
 */
@DubboService
public class NotificationRpcServiceImpl implements NotificationRpcService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRpcServiceImpl.class);

    private final NotificationService notificationService;

    public NotificationRpcServiceImpl(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public NotificationSendResult submit(NotificationMessage message) {
        try {
            notificationService.submit(message);
            return NotificationSendResult.ok();
        } catch (BizException e) {
            log.warn("通知投递失败 channel={} code={} msg={}",
                    message == null ? "null" : message.getChannel(), e.getCode(), e.getMessage());
            return NotificationSendResult.fail(e.getCode(), e.getMessage());
        }
    }
}
```

- [ ] **Step 5: message 启动类补扫描与配置注册**

`OpenBlog-message/.../OpenBlogMessageApplication.java` **整体替换**为：

```java
package com.yqz.openblog.message;

import com.yqz.openblog.notification.NotificationProperties;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * message 即「统一通知服务」。
 * 显式组件扫描覆盖 {@link com.yqz.openblog.notification} 包（迁入的编排层不在默认扫描范围
 * com.yqz.openblog.message 下）；MapperScan 额外包含 outbox mapper；@EnableScheduling 供 OutboxRelay。
 */
@SpringBootApplication
@EnableDubbo
@EnableScheduling
@MapperScan(basePackages = {"com.yqz.openblog.message.repo", "com.yqz.openblog.notification"}, annotationClass = Mapper.class)
@EnableConfigurationProperties({NotificationProperties.class})
@ComponentScan(
        basePackages = {"com.yqz.openblog.message", "com.yqz.openblog.notification"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
        })
public class OpenBlogMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenBlogMessageApplication.class, args);
    }
}
```

> 说明：`EmailRecordMapper` 已带 `@Mapper` 注解（已核实），`annotationClass = Mapper.class` 只会注册带该注解的接口，可同时覆盖 `message.repo` 与 `notification` 下的 outbox mapper，不会误扫 `NotificationChannel` 等普通接口。

- [ ] **Step 6: 迁移现有编排层测试（git mv，无需改代码）**

business 下已存在整套编排层单测（用 Mockito，无 Spring 上下文；`NotificationTemplateServiceTest` 已覆盖模板渲染 4 个用例，比新建更完整）。随编排层迁入 message，包名不变即可直接运行：

```bash
cd "E:/java/OpenBlog"
TD=OpenBlog-message/src/test/java/com/yqz/openblog/notification
mkdir -p "$TD/outbox"
git mv OpenBlog-business/src/test/java/com/yqz/openblog/notification/ChannelRegistryTest.java      "$TD/ChannelRegistryTest.java"
git mv OpenBlog-business/src/test/java/com/yqz/openblog/notification/NotificationServiceTest.java   "$TD/NotificationServiceTest.java"
git mv OpenBlog-business/src/test/java/com/yqz/openblog/notification/NotificationTemplateServiceTest.java "$TD/NotificationTemplateServiceTest.java"
git mv OpenBlog-business/src/test/java/com/yqz/openblog/notification/outbox/NotificationOutboxTest.java "$TD/outbox/NotificationOutboxTest.java"
git mv OpenBlog-business/src/test/java/com/yqz/openblog/notification/outbox/OutboxRelayTest.java    "$TD/outbox/OutboxRelayTest.java"
```

> 说明：这些测试依赖 `NotificationMessage`/`NotificationChannelType`（Task 1 已迁 message-api）、`NotificationService`/`ChannelRegistry`/`OutboxRelay`/`NotificationTemplateService`（本 Task 复制到 message）、`NotificationMqProducer`（本 Task 复制到 message），全部同包名解析，**无需改动任何代码**。

- [ ] **Step 7: 编译 + 单测验证 message 与 business 均通过**

Run:
```bash
cd "E:/java/OpenBlog"
mvn -q -pl OpenBlog-message -am test
mvn -q -pl OpenBlog-business -am compile
```
Expected:
- message 单测 `BUILD SUCCESS`（5 个测试类共 14 个用例通过：ChannelRegistry 3 + NotificationService 3 + Template 4 + Outbox 1 + Relay 3）
- business 编译 `BUILD SUCCESS`（本 Task 未删 business 副本，仍可编译）

> `EmailRecordMapper` 已带 `@Mapper` 注解（已核实），`annotationClass = Mapper.class` 的扫描可同时注册 message.repo 与 outbox 的 mapper。

- [ ] **Step 8: Commit**

```bash
cd "E:/java/OpenBlog"
git add -A
git commit -m "$(cat <<'EOF'
feat(notification): move notification orchestration into message service

- copy orchestration layer (service/channel/outbox/mq) from business to message
- EmailNotificationChannel now calls EmailService in-process
- add NotificationRpcServiceImpl (Dubbo provider)
- extend message app scan/config; move existing orchestration tests to message

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: business 切换 RPC、删除旧编排层（switch-over）

**Files:**
- Modify: `OpenBlog-business/src/main/java/com/yqz/openblog/user/service/EmailCodeService.java`
- Delete: `OpenBlog-business/src/main/java/com/yqz/openblog/notification/`（整个目录，business 副本）
- Delete: `OpenBlog-api/OpenBlog-message-api/.../message/api/EmailRpcService.java`
- Delete: `OpenBlog-message/.../message/service/EmailRpcServiceImpl.java`
- Modify: `OpenBlog-business/pom.xml`（移除 rocketmq）
- Modify: `OpenBlog-business/src/main/resources/application.yaml`、`application-local.example.yaml`（移除 rocketmq.* 与 openblog.notification.*）

- [ ] **Step 1: `EmailCodeService` 改调 RPC**

`OpenBlog-business/src/main/java/com/yqz/openblog/user/service/EmailCodeService.java`：

**import 区**：删除以下两行
```java
import com.yqz.openblog.notification.NotificationService;
import com.yqz.openblog.notification.NotificationTemplateService;
```
新增：
```java
import com.yqz.openblog.message.api.NotificationRpcService;
import com.yqz.openblog.message.api.NotificationSendResult;
import org.apache.dubbo.config.annotation.DubboReference;
```

**字段与构造器**：删除 `private final NotificationService notificationService;`、构造器参数与赋值；新增：

```java
    /** Dubbo 调统一通知服务。retries=0：同步链路 messageId 为空 → 渠道每次生成新幂等键，重试会重复发信。 */
    @DubboReference(retries = 0, timeout = 5000)
    private NotificationRpcService notificationRpcService;
```

**`sendCode` 方法**：把原 `notificationService.submit(...)` 调用块替换为：

```java
        try {
            NotificationSendResult result = notificationRpcService.submit(NotificationMessage.builder()
                    .channel(NotificationChannelType.EMAIL)
                    .recipient(email)
                    .subject(SUBJECT)
                    .templateCode(NotificationRpcService.TEMPLATE_REGISTER_VERIFICATION_CODE)
                    .params(Map.of("code", code))
                    .build());
            if (!result.isSuccess()) {
                throw new BizException(result.getErrorCode(), result.getErrorMsg());
            }
        } catch (BizException e) {
            // 发送失败（通知服务返回 5002 等）：清理验证码与冷却，允许立即重试。
            redisOps.delete(codeKey);
            redisOps.delete(cooldownKey);
            log.warn("发送注册验证码失败。email={}", email, e);
            throw e;
        } catch (Exception e) {
            // message 服务不可达 / Dubbo 传输异常：同样清理后按 5002 降级，避免脏状态。
            redisOps.delete(codeKey);
            redisOps.delete(cooldownKey);
            log.warn("Dubbo 调用通知服务失败。email={}", email, e);
            throw new BizException(5002, "邮件服务暂不可用，请稍后再试");
        }
```

- [ ] **Step 2: 删除 business 的 notification 包副本**

```bash
cd "E:/java/OpenBlog"
rm -rf OpenBlog-business/src/main/java/com/yqz/openblog/notification
```

- [ ] **Step 3: 删除旧的 EmailRpcService 契约与实现**

```bash
cd "E:/java/OpenBlog"
git rm OpenBlog-api/OpenBlog-message-api/src/main/java/com/yqz/openblog/message/api/EmailRpcService.java
git rm OpenBlog-message/src/main/java/com/yqz/openblog/message/service/EmailRpcServiceImpl.java
```

- [ ] **Step 4: business `pom.xml` 移除 RocketMQ 依赖**

删除 `OpenBlog-business/pom.xml` 中以下依赖块：

```xml
        <!-- RocketMQ (通知异步投递，P1)。配 rocketmq.name-server；RocketMQ 为阿里系，与 Dubbo/Nacos 同生态 -->
        <dependency>
            <groupId>org.apache.rocketmq</groupId>
            <artifactId>rocketmq-spring-boot-starter</artifactId>
            <version>2.3.2</version>
        </dependency>
```

- [ ] **Step 5: business 两个 yaml 移除 rocketmq.* 与 openblog.notification.***

`OpenBlog-business/src/main/resources/application.yaml`：删除以下两段（全文精确匹配）

① RocketMQ 块（当前位于 `audit:` 与 `openblog:` 之间）：
```yaml
# RocketMQ（通知异步投递，P1）。name-server 指向部署 RocketMQ 的机器
rocketmq:
  name-server: 10.21.76.221:9876
  producer:
    group: openblog-notification-producer
```

② `openblog:` 下的 `notification:` 块：
```yaml
  notification:
    # 统一通知抽象层。当前只 email 渠道生效；SMS / 飞书为未来扩展位
    email:
      enabled: true
      default-subject: OpenBlog
    # 本地消息表 + MQ 异步投递（P1）：submitAsync 写 outbox → Relay 发布 → Consumer 投递
    # topic / consumer-group 见 NotificationTopics 常量（单一来源）
    outbox:
      enabled: true
      relay-interval-ms: 5000
      publish-window-ms: 1000
      max-retry: 8
```

`OpenBlog-business/src/main/resources/application-local.example.yaml`：删除以下两段（全文精确匹配）

① RocketMQ 块：
```yaml
# RocketMQ（通知异步投递，P1）。本地开发可先不部署 RocketMQ，同步验证码链路不受影响
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: openblog-notification-producer
```

② `openblog:` 下的 `notification:` 块：
```yaml
  notification:
    # 统一通知抽象层。当前只 email 渠道生效；SMS / 飞书为未来扩展位
    email:
      enabled: true
      default-subject: OpenBlog
    outbox:
      enabled: true
      relay-interval-ms: 5000
      publish-window-ms: 1000
      max-retry: 8
```

- [ ] **Step 6: 全量编译验证（双端）**

Run:
```bash
cd "E:/java/OpenBlog"
mvn -q -pl OpenBlog-message-api -am compile
mvn -q -pl OpenBlog-message -am compile
mvn -q -pl OpenBlog-business -am compile
```
Expected: 全部 `BUILD SUCCESS`（business 现从 message-api 解析 `NotificationMessage`/`NotificationChannelType`/`NotificationRpcService`；`EmailRpcService` 已删除且无残留引用）。

- [ ] **Step 7: 全局检索确认无残留引用**

Run:
```bash
cd "E:/java/OpenBlog"
grep -rn "EmailRpcService" --include="*.java" . | grep -v target || echo "OK: 无 EmailRpcService 残留"
grep -rn "openblog.notification\|rocketmq" OpenBlog-business/src/main/resources || echo "OK: business yaml 无 notification/rocketmq 残留"
```
Expected: 两处均输出 `OK:`。

- [ ] **Step 8: Commit**

```bash
cd "E:/java/OpenBlog"
git add -A
git commit -m "$(cat <<'EOF'
refactor(notification): switch business to NotificationRpcService, drop old path

- EmailCodeService calls NotificationRpcService; failure mapped to BizException + redis cleanup
- delete business notification package (moved to message)
- delete EmailRpcService contract + impl
- remove rocketmq dep and notification config from business

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: message Docker 化部署文件

**Files:**
- Create: `deploy/message/Dockerfile`
- Create: `deploy/message/docker-compose.yml`
- Create: `deploy/message/.dockerignore`

- [ ] **Step 1: 创建 `deploy/message/Dockerfile`**（完整内容）

```dockerfile
# OpenBlog-message 容器镜像
# 构建策略与 business 一致：本地/CI Maven 打 fat jar → 上传 → 镜像内只运行（不编译）。

FROM eclipse-temurin:17-jre

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

ARG JAR_FILE=OpenBlog-message-1.0.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

ENV JAVA_OPTS="-Xmx512M -Xms128M"

EXPOSE 8083
EXPOSE 20883

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
```

- [ ] **Step 2: 创建 `deploy/message/docker-compose.yml`**（完整内容）

```yaml
# OpenBlog-message（统一通知服务）Docker 编排
#
# 外部依赖（MySQL/Nacos/RocketMQ）均在局域网 10.21.76.221，容器走默认 bridge 即可直达。
# 发布 8083（HTTP 管理接口）+ 20883（Dubbo）。
# 关键：DUBBO_IP_TO_REGISTRY 指向宿主机 IP —— 使 Nacos 注册宿主机地址，
#       business 容器（不同 bridge 网络）经 10.21.76.221:20883 才能访问本服务。
# 阿里云 AK/SK/FROM 经同目录 .env（docker compose 自动加载）注入。

services:
  message:
    image: openblog-message:latest
    build:
      context: .
    container_name: openblog-message
    restart: always
    ports:
      - "8083:8083"
      - "20883:20883"
    environment:
      TZ: Asia/Shanghai
      JAVA_OPTS: "-Xmx512M -Xms128M"
      DUBBO_IP_TO_REGISTRY: "10.21.76.221"
      ALIYUN_AK: ${ALIYUN_AK}
      ALIYUN_SK: ${ALIYUN_SK}
      ALIYUN_FROM: ${ALIYUN_FROM}
    networks:
      - openblog-message-net

networks:
  openblog-message-net:
    driver: bridge
```

- [ ] **Step 3: 创建 `deploy/message/.dockerignore`**（完整内容）

```
# 构建上下文只需 jar + Dockerfile。排除 backup / actions-runner 等大目录。
*
!OpenBlog-message-*.jar
!Dockerfile
```

- [ ] **Step 4: Commit**

```bash
cd "E:/java/OpenBlog"
git add deploy/message
git commit -m "$(cat <<'EOF'
build(message): dockerize message service (8083 + dubbo 20883)

- mirror business deploy layout; expose DUBBO_IP_TO_REGISTRY for cross-container dubbo

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: CI 重构为 business / message 双独立链

**Files:**
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: 整体替换 `ci.yml`**（完整内容如下）

`changes` 过滤拆出 `business` / `message` 两个输出；`build-business`/`deploy-business` 与 `build-message`/`deploy-message` 两条链**互不 `needs`**；`build-frontend`/`deploy-frontend` 原样保留。

```yaml
name: Build and deploy OpenBlog

on:
  workflow_dispatch:
  push:
    branches:
      - master
    paths-ignore:
      - '**/*.md'

jobs:
  # 检测变更范围。business 与 message 两条链互不影响；共享契约（OpenBlog-api/**）变更会同时触发双链。
  changes:
    runs-on: ubuntu-latest
    outputs:
      business: ${{ steps.filter.outputs.business }}
      message: ${{ steps.filter.outputs.message }}
      frontend: ${{ steps.filter.outputs.frontend }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: filter
        with:
          filters: |
            business:
              - 'OpenBlog-business/**'
              - 'OpenBlog-framework*/**'
              - 'OpenBlog-common/**'
              - 'OpenBlog-api/**'
              - 'deploy/business/**'
              - 'pom.xml'
            message:
              - 'OpenBlog-message/**'
              - 'OpenBlog-common/**'
              - 'OpenBlog-api/**'
              - 'deploy/message/**'
              - 'pom.xml'
            frontend:
              - 'vue/**'

  # ============ business 链（独立） ============
  build-business:
    needs: changes
    if: ${{ needs.changes.outputs.business == 'true' || github.event_name == 'workflow_dispatch' }}
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
      - name: Build business (Maven)
        run: mvn -B -ntp package -DskipTests -pl OpenBlog-business -am
      - name: Upload business JAR
        uses: actions/upload-artifact@v4
        with:
          name: business-jar
          path: OpenBlog-business/target/OpenBlog-business-*.jar
          retention-days: 1

  deploy-business:
    needs: build-business
    if: ${{ needs.build-business.result == 'success' || github.event_name == 'workflow_dispatch' }}
    runs-on: openblog-backend

    steps:
      - name: Checkout（获取 deploy/business 下的 Docker 部署文件）
        uses: actions/checkout@v4
      - name: Download business JAR
        uses: actions/download-artifact@v4
        with:
          name: business-jar
          path: business
      - name: Deploy via Docker（构建镜像 + 重启容器）
        run: |
          set -euo pipefail
          TARGET_DIR="/www/wwwroot/java/openblog"
          TARGET_JAR="$TARGET_DIR/OpenBlog-business-1.0.0-SNAPSHOT.jar"

          # 1. 停掉旧 systemd 服务（释放 8082，避免与容器抢端口）
          systemctl stop openblog 2>/dev/null || true
          systemctl disable openblog 2>/dev/null || true

          # 2. 备份旧 jar
          if [ -f "$TARGET_JAR" ]; then
            mkdir -p "$TARGET_DIR/backup"
            cp "$TARGET_JAR" "$TARGET_DIR/backup/OpenBlog-business-$(date +%Y%m%d-%H%M%S).jar" 2>/dev/null || true
          fi

          # 3. 拷贝新 jar + Docker 部署文件
          JAR_FILE=$(ls business/OpenBlog-business-*.jar | head -1)
          cp "$JAR_FILE" "$TARGET_JAR"
          cp deploy/business/Dockerfile deploy/business/docker-compose.yml deploy/business/.dockerignore "$TARGET_DIR/"

          # 4. Docker 构建并启动容器
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
          echo "==> Deployed OpenBlog-business via Docker"

  # ============ message 链（独立，与 business 互不影响） ============
  build-message:
    needs: changes
    if: ${{ needs.changes.outputs.message == 'true' || github.event_name == 'workflow_dispatch' }}
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
      - name: Build message (Maven)
        run: mvn -B -ntp package -DskipTests -pl OpenBlog-message -am
      - name: Upload message JAR
        uses: actions/upload-artifact@v4
        with:
          name: message-jar
          path: OpenBlog-message/target/OpenBlog-message-*.jar
          retention-days: 1

  deploy-message:
    needs: build-message
    if: ${{ needs.build-message.result == 'success' || github.event_name == 'workflow_dispatch' }}
    runs-on: openblog-backend

    steps:
      - name: Checkout（获取 deploy/message 下的 Docker 部署文件）
        uses: actions/checkout@v4
      - name: Download message JAR
        uses: actions/download-artifact@v4
        with:
          name: message-jar
          path: message
      - name: Deploy via Docker（构建镜像 + 重启容器 + 注入阿里云凭据）
        run: |
          set -euo pipefail
          TARGET_DIR="/www/wwwroot/java/openblog-message"
          TARGET_JAR="$TARGET_DIR/OpenBlog-message-1.0.0-SNAPSHOT.jar"

          # 1. 停掉旧 宝塔/手动部署的 message 进程（释放 8083/20883；若无则该步 no-op）
          systemctl stop openblog-message 2>/dev/null || true

          # 2. 备份旧 jar
          mkdir -p "$TARGET_DIR"
          if [ -f "$TARGET_JAR" ]; then
            mkdir -p "$TARGET_DIR/backup"
            cp "$TARGET_JAR" "$TARGET_DIR/backup/OpenBlog-message-$(date +%Y%m%d-%H%M%S).jar" 2>/dev/null || true
          fi

          # 3. 拷贝新 jar + Docker 部署文件
          JAR_FILE=$(ls message/OpenBlog-message-*.jar | head -1)
          cp "$JAR_FILE" "$TARGET_JAR"
          cp deploy/message/Dockerfile deploy/message/docker-compose.yml deploy/message/.dockerignore "$TARGET_DIR/"

          # 4. 阿里云凭据写入服务器 .env（compose 自动加载），供 message 容器读取
          printf 'ALIYUN_AK=%s\nALIYUN_SK=%s\nALIYUN_FROM=%s\n' \
            "${{ secrets.ALIYUN_AK }}" "${{ secrets.ALIYUN_SK }}" "${{ secrets.ALIYUN_FROM }}" \
            > "$TARGET_DIR/.env"

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
          echo "==> Deployed OpenBlog-message via Docker"

  # ============ 前端链（原样保留） ============
  build-frontend:
    needs: changes
    if: ${{ needs.changes.outputs.frontend == 'true' || github.event_name == 'workflow_dispatch' }}
    runs-on: ubuntu-latest
    env:
      FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true

    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: "22"
          cache: "npm"
          cache-dependency-path: vue/package-lock.json
      - name: Install frontend dependencies and build
        working-directory: vue
        run: |
          npm ci
          npm run build
      - name: Prepare release bundle for server
        run: |
          mkdir -p release
          cp -r vue/dist release/dist
          cp vue/Dockerfile vue/docker.sh vue/nginx.conf release/
      - name: Build Docker image on runner
        run: |
          docker build -t openblog-web release/
          docker save openblog-web | gzip > release/openblog-web.tar.gz
      - name: Upload release bundle
        uses: actions/upload-artifact@v4
        with:
          name: release-frontend
          path: release/

  deploy-frontend:
    needs: build-frontend
    if: ${{ needs.build-frontend.result == 'success' }}
    runs-on: ubuntu-latest

    steps:
      - name: Download release bundle
        uses: actions/download-artifact@v4
        with:
          name: release-frontend
          path: release/
      - name: Deploy files to server
        uses: easingthemes/ssh-deploy@v5.1.1
        with:
          SSH_PRIVATE_KEY: ${{ secrets.SERVER_SSH_KEY }}
          ARGS: "-rlgoDzvc --delete --exclude=dist/.user.ini"
          SOURCE: "release/dist release/Dockerfile release/docker.sh release/nginx.conf release/openblog-web.tar.gz"
          REMOTE_HOST: ${{ secrets.SERVER_REMOTE_HOST }}
          REMOTE_USER: ${{ secrets.SERVER_REMOTE_USER }}
          TARGET: ${{ secrets.SERVER_TARGET }}
          SCRIPT_BEFORE: ls -la
      - name: Run Docker commands on server
        uses: appleboy/ssh-action@v1.2.0
        with:
          host: ${{ secrets.SERVER_REMOTE_HOST }}
          username: ${{ secrets.SERVER_REMOTE_USER }}
          key: ${{ secrets.SERVER_SSH_KEY }}
          command_timeout: 30m
          script: |
            cd ${{ secrets.SERVER_TARGET }}
            sh docker.sh
```

- [ ] **Step 2: 校验 YAML 语法**

Run:
```bash
cd "E:/java/OpenBlog"
python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('YAML OK')"
```
Expected: `YAML OK`（若无 python/yaml，可跳过，靠 review 确认缩进）。

- [ ] **Step 3: Commit**

```bash
cd "E:/java/OpenBlog"
git add .github/workflows/ci.yml
git commit -m "$(cat <<'EOF'
ci: split business and message into independent build/deploy chains

- changes filter now emits business / message / frontend separately
- build/deploy-business and build/deploy-message have no cross-chain needs
- message deployed via Docker on 8083 + dubbo 20883, AK/SK via server .env

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: 端到端验证

**Files:** 无（验证清单）

> 本 Task 在本地/服务器双端完成，属于合并 PR 前的验收门槛。

- [ ] **Step 1: 本地启动双服务**

前提：Nacos/MySQL/Redis/RocketMQ 可达（`10.21.76.221`），`application-local.yaml` 配置正确。

```bash
cd "E:/java/OpenBlog"
mvn -q spring-boot:run -pl OpenBlog-message   # 8083
mvn -q spring-boot:run -pl OpenBlog-business  # 8082（另开终端）
```

Expected: 两个进程均成功启动；Nacos 服务列表可见 `openblog-message`（provider 含 `NotificationRpcService`）与 `openblog-business`；message 日志可见 `OutboxRelay` 定时调度（每 5s）。

- [ ] **Step 2: 验证注册 → 验证码 → 收信**

- 前端或直接 `POST /api/v1/auth/email-code?email=<未注册邮箱>`（business）。
- Expected:
  - 返回冷却秒数；`email_records` 新增一条 `SENT` 记录（status 为 SENT）。
  - 该邮箱收到 OpenBlog 注册验证码邮件（验证码为 6 位数字）。
- 失败路径：停掉 message，再请求 → 返回 **5002「邮件服务暂不可用，请稍后再试」**；重发请求不再被冷却拦截（Redis 验证码/冷却键已清理）。

- [ ] **Step 3: 验证 RocketMQ 单消费组**

```bash
# 服务端：检查 consumer group（topic=openblog_notification）
# 应仅 message 一个消费者；business 的旧 NotificationMqConsumer 已删除
```
Expected: 控制台 `openblog_notification` 只有 `notification-consumer` 一个消费者组。

- [ ] **Step 4: 验证 CI 独立性（合并后触发）**

- 仅改 `OpenBlog-message/**` → 只跑 `build-message`/`deploy-message`。
- 仅改 `OpenBlog-business/**` → 只跑 `build-business`/`deploy-business`。
- 改 `OpenBlog-api/**`（共享契约）→ 双链都跑，各自独立成功/失败。
Expected: `changes` job 输出正确，两条链互不阻塞。

- [ ] **Step 5: 服务器部署验证（合入 master 后）**

- `docker ps` 可见 `openblog-business` 与 `openblog-message` 两个容器。
- 停掉 message 容器 5 秒再拉起：`restart: always` 自动恢复。
- 服务器注册验证码链路正常（说明 business 容器经 `10.21.76.221:20883` 能达 message 容器）。

---

## Self-Review 对照

| Spec 章节 | 覆盖 Task | 备注 |
|-----------|-----------|------|
| 4.1 RPC 契约（新增 NotificationRpcService/SendResult，迁 DTO，删 EmailRpcService） | Task 1 + Task 4 | 删除动作放 switch-over |
| 4.2 文件移动清单（迁入 message / business 删除改动） | Task 3 + Task 4 | 复制后删除，保证可编译 |
| 4.3 message 改动（rocketmq 依赖、@EnableScheduling、配置、in-process 渠道、provider） | Task 2 + Task 3 | 含 MapperScan/ComponentScan 细节 |
| 4.4 数据与表（outbox 归 message，无迁移） | —（无代码） | 同库共享，无建表 |
| 4.5 部署与 CI（Docker 化 + 双独立链） | Task 5 + Task 6 | 含 DUBBO_IP_TO_REGISTRY 网络细节 |
| 4.6 分支开发 + PR | 本计划整体 | 已在 feat/notification-service-split |
| 5 测试与验收（单测 + 端到端） | Task 3 迁移现有单测 + Task 7 | business 已有整套编排层单测（14 例），随迁至 message 并跑通 |
