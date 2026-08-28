# 通知服务拆分设计（Notification Service Split）

> 更新日期：2026-08-29
> 关联文档：`docs/designs/service-extraction-analysis.md`（3.1 通知域：P0 拆分候选）、`docs/designs/api-gateway-security-ha.md`（阶段 0 高可用加固）
> 开发分支：`feat/notification-service-split`（本设计文档即在此分支提交，后续实现也在该分支，合入 master 走 PR）

---

## 1. 背景与目标

`OpenBlog-business`（8082）内的**通知编排层**（`com.yqz.openblog.notification.*`）目前是「已就绪但只被一处使用」的独立域：唯一调用方是注册验证码 `EmailCodeService`，且走**同步**路径；异步路径（outbox / MQ）已建好但尚无调用方。而真正的邮件投递早已独立在 `OpenBlog-message`（8083）中（Dubbo + 阿里云 DirectMail）。

本次目标：**把通知编排层整体迁入 message，使 message 从「邮件服务」升级为「统一通知服务」**，并借机完成 message 的 Docker 化与 CI 接入。

### 目标清单

1. **服务边界**：通知编排层（渠道/模板/outbox/MQ/门面）整体迁移到 `OpenBlog-message`；business 只保留一个 Dubbo RPC 客户端。
2. **异步路径随迁保留**：`submitAsync` / outbox / `OutboxRelay` / MQ 消费随迁进 message，作为未来异步通知能力（当前无调用方，不接线）。
3. **message 部署现代化**：Docker 化 + 接入 CI，与 business 流水线**互不影响**。
4. **分支开发**：全部改动在 `feat/notification-service-split` 分支上进行，合入走 PR（项目既有约定）。

### 非目标（Out of scope）

- 不新建第三个服务/模块（决策：升级现有 message）。
- 不接线异步通知（`submitAsync` 的调用方）；只搬迁保留机制。
- 不拆分 business 其他域（article/user/comment 等枢纽域，见 service-extraction-analysis 第 4 节）。
- 不重命名模块/Nacos 服务名（保持 `OpenBlog-message` / `openblog-message`，减少 churn）。

---

## 2. 现状（代码事实，已核实）

**通知编排层（business）依赖关系**：零依赖其他业务域。仅依赖 `common`（BizException）、`message-api`（EmailRpcService）、Dubbo、RocketMQ、MyBatis-Plus、Spring 定时。

**关键类**：
- `NotificationService`：门面。`submit(msg)` 同步分发；`submitAsync(msg)` 同事务写 outbox（PENDING）。
- `ChannelRegistry`：按 `NotificationChannelType` 路由；`fail-closed`（未配置渠道抛 4000）。
- `AbstractNotificationChannel`：模板方法 `send` = 模板渲染 → `doSend`。
- `EmailNotificationChannel.doSend`：`@DubboReference(retries=0, timeout=5000) EmailRpcService` → 直发，失败抛 `BizException(5002)`。
- `NotificationTemplateService`：内置「注册验证码」邮件 HTML 模板（`{{code}}` 占位符）。
- `outbox`：`NotificationOutbox`（`notification_outbox` 表）+ `OutboxRelay`（`@Scheduled` 扫 PENDING → 发 MQ → 置 PUBLISHED，至少一次，幂等兜底）。
- `mq`：`NotificationMqProducer` / `NotificationMqConsumer`（消费后复用同步门面投递，失败抛给 MQ 重投/DLQ）/ `NotificationTopics`。

**唯一调用方**：`user/service/EmailCodeService.sendCode()` → `notificationService.submit(msg)` 同步；失败 catch `BizException` 清 Redis 验证码与冷却后重抛。

**message 服务现状**：`EmailRpcServiceImpl`（Dubbo provider）、`EmailService`（幂等发信 + `email_records` 记录）、`AliyunDirectMailSender`、`EmailAdminController`（`/api/v1/message/*`，管理测试与记录查询）。**无** RocketMQ / Redis / 定时任务依赖。

**message-api**：`EmailRpcService`、`EmailSendRequest`、`EmailSendResult`。

**模块依赖**：business 的 RocketMQ 仅被 `notification/mq` 使用；message 无 RocketMQ 依赖，但已有 MyBatis-Plus + common + message-api。

**CI 现状**（`.github/workflows/ci.yml`）：`changes` 过滤 → `build-backend`（`mvn package -pl OpenBlog-business -am`）→ `deploy-backend`（自托管 Runner `openblog-backend` 上 Docker 部署 business）。当前 `backend` 过滤包含 `OpenBlog-message/**`，即 message 改动也会触发 business 构建部署。

---

## 3. 目标架构

```
business (8082)                        message (8083) = 统一通知服务
  EmailCodeService                       ├─ NotificationRpcServiceImpl (Dubbo provider，新增)
    └─ @DubboReference                     │    └─ NotificationService.submit(msg)      [迁入]
       NotificationRpcService.submit(msg)   │         └─ ChannelRegistry → EmailNotificationChannel [迁入]
         └─ NotificationMessage             │              └─ EmailService.send(req)    [in-process 复用，原样]
              (message-api 共享 DTO)        ├─ EmailRpcService → 删除
                                            ├─ outbox / OutboxRelay / MQ consumer       [迁入，无调用方待用]
                                            ├─ EmailAdminController + email_records     [原样保留]
                                            └─ 新增: RocketMQ + @EnableScheduling + notification 配置
```

要点：迁入 message 的 `EmailNotificationChannel` 由「Dubbo 调 EmailRpcService」改为**进程内直接调 `EmailService`**（幂等逻辑、`email_records` 记录原样复用）。`EmailRpcService` 唯一消费者消失 → 删除该 RPC。

---

## 4. 详细设计

### 4.1 RPC 契约（`OpenBlog-message-api`）

**新增**（business 侧消费，message 侧提供）：

```java
public interface NotificationRpcService {
    NotificationSendResult submit(NotificationMessage message);
}
```

```java
public class NotificationSendResult {
    private boolean success;
    private Integer errorCode;   // 失败时：5002 等
    private String errorMsg;
    // getter/setter / 构造
}
```

**迁入 message-api 的共享 DTO**：`NotificationMessage`、`NotificationChannelType`（business 构造消息、message 投递，双端共用，故放共享模块）。

**删除**：`EmailRpcService`（唯一消费者 `EmailNotificationChannel` 改 in-process）。`EmailSendRequest` / `EmailSendResult` **保留**（`EmailService` 方法签名与 `EmailAdminController` 仍使用）。

**错误语义**：RPC 返回结果对象而非异常传播（避免 Dubbo 异常序列化脆弱性）。business 侧 `!result.isSuccess() → throw new BizException(result.getErrorCode(), result.getErrorMsg())`，`EmailCodeService` 现有 catch+清 Redis 逻辑保持不变。

### 4.2 文件移动清单

**→ `OpenBlog-message-api`（共享契约）**
- `notification/NotificationMessage.java`
- `notification/NotificationChannelType.java`
- 新增 `message/api/NotificationRpcService.java`、`message/api/NotificationSendResult.java`
- 删除 `message/api/EmailRpcService.java`

**→ `OpenBlog-message` 模块（保持包名 `com.yqz.openblog.notification.*`，最小化 import churn）**
- `NotificationService`、`ChannelRegistry`、`NotificationChannel`、`AbstractNotificationChannel`、`EmailNotificationChannel`、`NotificationTemplateService`、`NotificationProperties`
- `outbox/NotificationOutbox`、`outbox/NotificationOutboxMapper`、`outbox/OutboxRelay`
- `mq/NotificationMqProducer`、`mq/NotificationMqConsumer`、`mq/NotificationTopics`
- 新增 `message/service/NotificationRpcServiceImpl`（`@DubboService`，实现 `NotificationRpcService`，调进程内 `NotificationService`）

**→ business 删除/改动**
- 删除整个 `notification/` 包（含 outbox、mq）
- `EmailCodeService`：`notificationService.submit(...)` → `@DubboReference NotificationRpcService.submit(...)`，失败映射 `BizException`
- `pom.xml`：移除 `rocketmq-spring-boot-starter`
- `application.yaml` / `application-local.example.yaml`：移除 `rocketmq.*` 与 `openblog.notification.*`

### 4.3 message 侧改动细节

- `pom.xml`：新增 `rocketmq-spring-boot-starter`（版本对齐 business 的 2.3.2）。
- 启动类：加 `@EnableScheduling`（`OutboxRelay` 的 `@Scheduled`）。
- `application.yaml`：新增 `rocketmq.name-server`（10.21.76.221:9876）与 `openblog.notification.*`（outbox 配置等，从 business 平移）。
- `EmailNotificationChannel`：`@DubboReference EmailRpcService` 字段替换为构造注入 `EmailService`（进程内直调），幂等键逻辑保持不变。
- Dubbo `scan.base-packages` 当前 `com.yqz.openblog.message.service` —— 新 provider 放该包即可，无需改 scan。

### 4.4 数据与表

- `notification_outbox` 表：逻辑归属 message（business 不再读写）。同一 `openblog` 库，**无建表/迁移**（表已存在，message 用 MyBatis mapper 读写）。
- `email_records`：不变（message 已持有）。
- 不引入独立 schema / 独立库（小项目共享单库，见 service-extraction-analysis 建议）。

### 4.5 部署与 CI（message 与 business 互不影响）

**新增 `deploy/message/`**：
- `Dockerfile`：与 business 镜像策略一致（`eclipse-temurin:17-jre`，jar 拷贝）。
- `docker-compose.yml`：`message` 服务，`ports: "8083:8083"`，`restart: always`，`JAVA_OPTS` 对齐。

**CI 重构（`.github/workflows/ci.yml`）——核心约束：business 与 message 两条流水线完全独立**：

```
changes:
  outputs:
    business:  OpenBlog-business/**、OpenBlog-framework*/**、OpenBlog-common/**、OpenBlog-api/**、deploy/business/**、pom.xml
    message:   OpenBlog-message/**、OpenBlog-common/**、OpenBlog-api/**、deploy/message/**、pom.xml
    frontend:  vue/**（原样）

build-business:  needs changes（if business）
deploy-business: needs build-business（自托管 Runner，Docker 部署 8082）

build-message:   needs changes（if message）  ← 独立，不 needs build-business
deploy-message:  needs build-message（自托管 Runner，Docker 部署 8083）
```

- **互不影响**：business 链与 message 链无跨链 `needs`。仅 message 改动只构建/部署 message，仅 business 改动只走 business 链。
- **共享契约（`OpenBlog-api/**`）变更时**：两条链都被触发、各自独立构建/部署。部署顺序无强制依赖；切换窗口内 Dubbo `check:false` + 现有 5002 降级兜底（项目既有行为）。
- `deploy-message` 需要并到现有 `deploy-backend` 的部署脚本风格（备份旧 jar、`docker compose up -d --build --remove-orphans`）。

### 4.6 开发与发布流程

- 全部改动在 `feat/notification-service-split` 分支，合入 `master` 走 PR（项目既有 PR 模板 + Conventional Commits）。
- 提交规范：`feat(notification): move orchestration to message service` 等。
- 合入 master 触发 CI 双链部署。

---

## 5. 测试与验收

- [ ] 本地启动 business + message（Nacos/MySQL/RocketMQ 可达），注册 → 获取验证码 → 真实收到邮件（`email_records` 有记录）。
- [ ] 验证码重发冷却、错误次数限制行为与拆分前一致。
- [ ] message 停掉时：business 注册流程返回 5002「邮件服务暂不可用」，且验证码/冷却 Redis 键被清理（现有 catch 逻辑生效）。
- [ ] outbox 表：message 启动后 `OutboxRelay` 正常调度（日志可见），无残留 consumer 重复消费（确认 business 旧 `NotificationMqConsumer` 已删除，消费组仅 message 一个消费者）。
- [ ] CI：仅改 message 时只跑 message 链；仅改 business 时只跑 business 链；改 message-api 时双链都跑。
- [ ] 构建：`mvn package -pl OpenBlog-message -am` 与 `mvn package -pl OpenBlog-business -am` 各自独立成功。

## 6. 风险与回滚

| 风险 | 缓解 |
|------|------|
| Dubbo 契约变更（EmailRpcService→NotificationRpcService）：只升一个服务 → No provider | business 与 message **同时部署**（项目既有约定，见 deploy README）；双链独立但都触发时各自就位 |
| RocketMQ 消费组迁移：business 旧 consumer 残留双消费 | 删除 business 的 `NotificationMqConsumer`；验证消费组仅 message 一个消费者 |
| 错误语义丢失（5002 / Redis 清理） | RPC 返回 result 对象显式传 errorCode/errorMsg；EmailCodeService catch 逻辑不变 |
| `notification_outbox` 表归属切换 | 表已存在、同库，无迁移；仅读写方从 business 变 message |
| message Docker 化后连不上 Nacos/RocketMQ | 复用 business 部署排障文档（防火墙/安全组放行 8083、9876） |

**回滚**：`git revert` 合并提交，将迁入代码撤回 business；回滚时同样要求 business/message 同时部署回旧契约。

## 7. 后续（非本次范围，供参考）

- 异步通知接线：当出现「非强反馈」通知需求（新评论提醒、订阅推送）时，在 message 内用 `submitAsync` 或 MQ 事件触发，business 无需感知。
- message 开放 HTTP 管理接口对外时，触发 `docs/designs/api-gateway-security-ha.md` 阶段 1（引入 Spring Cloud Gateway）。
