# 通知服务化设计计划书：本地消息表 + MQ 异步消费 + 延时重试

> 日期：2026-08-28
> 状态：设计稿（待评审）；**P1 已实施**（outbox + RocketMQ Relay/Consumer + 幂等闭环，见 `docs/ROADMAP.md`）
> 前置：统一通知抽象层已落地（`com.yqz.openblog.notification`，见 `docs/ROADMAP.md` 任务 1 完成情况）

---

## 0. 背景与目标

现状：验证码邮件走「同步 Dubbo 直发」，可靠性已闭环（retries=0 + 幂等键 + `email_records.idempotency_key` 唯一索引），且刚重构出统一通知抽象层（Channel 策略 + 模板方法 + Registry + 门面），当前只实现 EMAIL 通道。

痛点 / 目标：

| # | 现状 | 目标 |
|---|------|------|
| 1 | 同步阻塞：业务线程等阿里云发信返回，耗时 >1s | 可容忍延迟的通知异步化，业务不阻塞 |
| 2 | 无重试队列：发信失败只删键报错，不自动补偿 | 投递失败按退避自动重试，超限进死信 + 告警 |
| 3 | 只能邮件 | 多通道：邮件 / 短信 / 飞书（本期仍只设计接缝） |
| 4 | 发送入口内嵌在验证码业务里 | 通知成为独立的可靠投递链路 |

**核心诉求：消息不丢失、不重复、可靠送达，同时保留现有 Dubbo 直发方式与已实现的抽象层。**

---

## 1. 边界：什么场景该走异步（重要）

| 场景 | 通道 | 理由 |
|------|------|------|
| 注册/登录验证码 | **保持现状同步 Dubbo 直发** | 强反馈：用户盯着页面等验证码，异步会引入不确定延迟；且它已幂等可靠 |
| 站内信、周报、异常告警、欢迎邮件、营销 | **MQ 异步** | 可容忍秒级延迟，量大、失败可重试、不阻塞主流程 |

因此不是「全量迁异步」，而是**双路径**：同步 submit 服务强反馈场景；异步 submitAsync 服务其余通知。两条路径共用同一套 Channel 策略与幂等键，只是投递触发方式不同。

---

## 2. 总体架构

```
                业务服务 (business)                    通知投递服务 (OpenBlog-notification)【目标态】
 ┌───────────────────────────────┐   ┌──────────────────────────────────────────────────┐
 │ 业务动作 (注册/评论/告警)        │   │ MQ Consumer                                        │
 │   └─ NotificationService       │   │   └─ NotificationMessage(messageId, channel, ...)   │
 │        ├─ submit()      同步 ──┼───┼─►  Dubbo 直发 email 模块（强反馈，现状不动）          │
 │        └─ submitAsync() 异步 ──┼───┼─►  Channel 策略渲染+投递                            │
 │              │ 写 outbox(同事务) │   │       ├─ EmailChannel ──► Dubbo → email 模块       │
 │              ▼                  │   │       ├─ SmsChannel  ──► 短信服务商 API（扩展位）    │
 │          notification_outbox    │   │       └─ FeishuChannel ─► webhook（扩展位）         │
 │              │ Relay(定时) 发布  │   │              │ 成功→ack + 更新 outbox=SENT          │
 │              ▼                  │   │              │ 失败→发延时重试消息 / 死信             │
 │   ┌─────── MQ Producer ───────┐ │   │                                                  │
 └───┼──── RocketMQ ─────────────┼─┼───┼──────────────────────────────────────────────────┘
     └──── 通知 topic ────────────┘ │   └──── 延时 topic（失败退避）────┘
```

- **不丢失**：业务写 outbox 与业务动作同事务提交；Relay 保证 outbox → MQ 至少一次发布。
- **不重复**：`messageId`(UUID) 贯穿 outbox / MQ / 消费端；消费端按 messageId + 通道幂等键去重（email 已天然支持）。
- **可靠**：RocketMQ 持久化 + 消费成功后 ack；失败走延时队列退避重试，超限死信 + 告警。

---

## 3. 核心机制逐一设计

### 3.1 本地消息表（Transactional Outbox）

**为什么用 outbox 而不是「直接发 MQ」：** 直接发 MQ 时「业务提交」和「消息入队」是两个独立动作，业务提交后、入队前进程崩溃 → 消息永久丢失。outbox 把「要发的通知」作为一条 DB 记录，与业务动作在**同一个本地事务**里提交，谁也不会先死。

**表结构**（新增，business 库；与 email 模块的 `email_records` 职责不同 —— 那是发送结果记录，这是待投递任务）：

```sql
CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL COMMENT '全局幂等键（UUID），MQ 消息与消费去重共用',
    channel VARCHAR(16) NOT NULL COMMENT 'EMAIL / SMS / FEISHU',
    recipient VARCHAR(128) NOT NULL COMMENT '接收方（邮箱/手机号/webhook）',
    subject VARCHAR(256) COMMENT '主题',
    template_code VARCHAR(64) NOT NULL COMMENT '模板 code，经 NotificationTemplateService 渲染',
    params_json JSON COMMENT '模板参数',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING待发布 / PUBLISHED已发布 / SENT已送达 / FAILED失败重试中 / DEAD死信',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME COMMENT '下次重试时间',
    last_error VARCHAR(512) COMMENT '最近一次失败原因',
    sent_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_message_id (message_id),
    INDEX idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**写入流程**（调用方）——`submitAsync` 内部：

```java
@Transactional
public void submitAsync(NotificationMessage msg) {
    msg.setMessageId(genIdempotentKey(msg));          // 幂等键，复用现有模型
    outboxMapper.insert(OutboxRecord.from(msg));      // 与业务动作同一事务
    // 业务动作的其它 DB 写（如建用户）也在此事务内
}
```

**中继 Relay**（`@Scheduled`，每 1~5s）：扫 `status=PENDING` 且 `created_at` 早于 N 秒的记录（留提交窗口），发布到 RocketMQ 后置 `PUBLISHED`。Relay 是「至少一次」发布：发布成功置 PUBLISHED；崩溃重启后 PENDING 会再次发布，重复由消费端幂等兜住。

**状态机：**

```
PENDING ──Relay发布──► PUBLISHED ──消费投递成功──► SENT
   │                       │
   └──(崩溃重扫再次发布)─────┘
PUBLISHED ──消费失败──► FAILED ──(延时到期重发消息, retry_count++)──► 回到 PUBLISHED 语义
                            └─ retry_count >= MAX ──► DEAD（人工/告警）
```

### 3.2 MQ 选型：RocketMQ（推荐）

| 对比 | RocketMQ | RabbitMQ | Kafka |
|------|----------|----------|-------|
| 与现有生态 | **Dubbo / Nacos 同属阿里系，Spring Boot 集成成熟** | 通用，需装延迟插件 | 流式，延迟需自建 |
| 原生延时消息 | ✅ 内置 18 个延时等级 | ⚠️ 需 rabbitmq_delayed_message_exchange 插件 | ❌ 需自实现 |
| 事务消息 | ✅（本项目用 outbox 替代，非必需） | 部分 | ✅ |
| 部署 | 轻量（nameserver + broker） | 轻量 | 较重 |
| 幂等消费 | 消费端自己保证（本项目已有幂等键） | 同左 | 同左 |

**结论：RocketMQ**（与 Dubbo/Nacos 同生态、原生延时队列正是本设计的重试基石）。版本与 spring boot starter 依赖开工前确认（当前主流 4.9.x + `rocketmq-spring-boot-starter:2.3.x`）。

部署：局域网一台（如 10.21.76.221）Docker 起 `rocketmq-namesrv` + `rocketmq-broker`，与 MySQL/Redis/MinIO/Nacos 同级，不进应用编排。

### 3.3 消费与投递

Consumer（监听通知 topic）反序列化 `NotificationMessage` → 交给 `ChannelRegistry.resolve(channel).send(...)`（**复用已实现的抽象层，零改动**）→ 成功则 ack 并更新 outbox=SENT；失败不 ack，交给重试。

```java
@RocketMQMessageListener(topic = NOTIFICATION_TOPIC, consumerGroup = "notification-consumer")
public class NotificationMqConsumer implements RocketMQListener<NotificationMessage> {
    private final NotificationService notificationService;   // 复用：渲染 + 路由 + 投递
    private final OutboxMapper outboxMapper;

    @Override
    public void onMessage(NotificationMessage msg) {
        try {
            notificationService.submit(msg);                 // 与同步路径同一个门面！
            outboxMapper.markSent(msg.getMessageId());
        } catch (Exception e) {
            // 不抛（避免无限重试），由延时重试机制接管；或抛出让 RocketMQ 按 DLQ 策略处理
            log.warn("MQ 通知投递失败 messageId={}", msg.getMessageId(), e);
            throw e;
        }
    }
}
```

**「消费端幂等」为什么这里天然成立**：`messageId` 传递到 email 通道后作为 `EmailSendRequest.idempotencyKey`，email 模块靠 `uk_idempotency_key` 去重 —— 同一条消息无论被 MQ 重投多少次，只发一封。这正好把已经上线的幂等保障**无缝复用**到异步链路。

### 3.4 延时重试（RocketMQ 延时消息）

投递失败（短信/飞书通道失败、email 通道临时不可用）→ Consumer 抛异常 → 重试消息按退避等级再投递。

**RocketMQ 内置延时等级**（messageDelayLevel，默认）：`1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h`

| 重试次数 | 延时等级 | 说明 |
|---------|----------|------|
| 第 1 次 | 1s / 5s | 瞬时失败（网络抖动）快速重试 |
| 2~3 次 | 30s / 1m | 中间退避 |
| 4~6 次 | 5m / 10m / 20m | 服务短时不可用等待恢复 |
| ≥7 次 | 30m 起步 | 长退避 |
| > MAX（默认 8） | 死信 | 置 outbox=DEAD，告警（企业微信/飞书群消息）人工介入 |

实现要点：
- 每轮重试携带 `attempt`，Producer 按 attempt 选延时等级；outbox 的 `retry_count` / `next_retry_at` 同步更新。
- 重试消息与原始消息同 `messageId` → 幂等仍生效，**重试不会造成重复邮件**。
- 兜底：Relay 也可定期扫 `FAILED` 且 `next_retry_at <= now` 的记录主动补发（与 MQ 延时双保险），此时 MQ 仅作加速通道。

### 3.5 可靠性矩阵（三个「不」如何保证）

| 承诺 | 机制 | 落点 |
|------|------|------|
| 不丢失（业务提交后必投） | outbox 与业务同事务 + Relay 至少一次发布 + RocketMQ 持久化 | `submitAsync` @Transactional；Relay |
| 不丢失（消费后必送达） | 消费成功才 ack；失败进重试而非丢弃 | `NotificationMqConsumer` |
| 不重复 | messageId 全局唯一（outbox 唯一约束）+ email 幂等键 + 唯一索引（已有） | `uk_message_id`；`EmailRpcServiceImpl` 去重 |
| 可靠送达 | 延时退避重试 + 死信告警 | `3.4` |
| 最终一致 | 状态机驱动，outbox 落库，可对账 | `3.1` 状态机 |

---

## 4. 与已实现抽象层的衔接（关键：调用方零改动）

现有抽象层已经为异步预留了接缝，本设计几乎只做「加」：

| 现有组件 | 本设计中的角色 |
|----------|----------------|
| `NotificationService.submit()` | **同步路径不动**；新增 `submitAsync()`（写 outbox），两者可共用校验 |
| `NotificationChannel` / `AbstractNotificationChannel` | **原样复用**，Consumer 调用同一策略 |
| `NotificationTemplateService` | 原样复用（outbox 存 params，消费时渲染） |
| `NotificationMessage` | 增加 `messageId` 字段（幂等键，复用 email 已实现的去重） |
| `EmailNotificationChannel` | 原样复用；email 模块与 Dubbo 直发**保留**，异步链路只是把「触发」换成 MQ |
| `ChannelRegistry` | 原样复用（Consumer 按 channel 路由） |

**为什么 email 通道异步时仍然走 Dubbo**：email 已是独立服务且幂等闭环，通知消费者调它用 Dubbo 是「保留现有方式」的落点 —— MQ 管「可靠调度」，Dubbo 管「实际投递」，各司其职。短信/飞书通道同理（消费端调服务商 API）。

**新增类清单：**

```
business 模块：
├── notification/outbox/NotificationOutbox            # 实体 + Mapper（MyBatis-Plus）
├── notification/outbox/OutboxRelay                   # @Scheduled 扫 PENDING → 发布 MQ → PUBLISHED
├── notification/mq/NotificationMqProducer            # 发通知消息 / 发延时重试消息
├── notification/mq/NotificationMqConsumer            # 消费 → submit() → ack
├── notification/NotificationService#submitAsync      # 写 outbox（@Transactional）
└── NotificationMessage#messageId                     # 新增字段

（目标态）OpenBlog-notification 模块：
├── 平移：NotificationChannel / AbstractNotificationChannel / ChannelRegistry /
│          NotificationTemplateService / NotificationProperties / EmailNotificationChannel
├── SmsNotificationChannel / FeishuNotificationChannel   # 扩展位
└── mq/NotificationMqConsumer 等
```

---

## 5. 配置（business + 目标模块）

```yaml
rocketmq:
  name-server: 10.21.76.221:9876
  producer:
    group: openblog-notification-producer

openblog:
  notification:
    outbox:
      enabled: true
      relay-cron: "*/5 * * * * *"      # 每 5s 扫一次 PENDING
      publish-window-ms: 1000          # 跳过刚提交的记录，留事务提交窗口
      max-retry: 8                     # 超过进死信
      topic: openblog_notification
```

---

## 6. 验收标准

- [ ] `submitAsync` 后即使业务进程在 Relay 执行前崩溃，重启后通知仍被投递（不丢失）
- [ ] 同一条消息被 MQ 重投 N 次，email 模块只发一封（幂等；现有单测 + `email_records` 验证）
- [ ] email 通道临时不可用时自动退避重试，恢复后投递成功，outbox 状态 PENDING→PUBLISHED→SENT
- [ ] 超过 max-retry 进 DEAD 并触发告警
- [ ] 同步验证码链路行为完全不变（回归：发 1 封、60s 冷却、失败删键报 5002）

## 7. 风险与开放问题（开工前确认）

- **RocketMQ 版本 / starter 依赖**（4.9.x + 2.3.x，还是 5.x）需确认，影响延时等级与 API。
- **outbox 表放 business 库 vs 目标通知模块库**：设计采用「业务事务内写 outbox」保证原子性，故先放 business 库；服务化平移时整表迁走。
- 死信告警通道用哪个（邮件 / 飞书群机器人）——可复用本通知体系自身。
- 消费失败是「抛异常走 RocketMQ 重投」还是「吞掉由延时重试机制接管」二选一，避免双重重试放大。
- 通知量级预估：当前个人博客量很小，是否需要 MQ 按实际决定，避免过度设计 —— **建议先只落 outbox + 同步发送**（用 outbox 做可靠记录，投递仍同步），确认有量再引入 RocketMQ。见实施路线。

## 8. 实施里程碑（渐进，非大爆炸）

| 阶段 | 内容 | 产出 |
|------|------|------|
| **P0** | 先落 outbox 表 + `submitAsync`（写记录，投递仍走同步 submit） | 通知有可靠留痕，无 MQ 依赖，风险最小 |
| **P1** | 引入 RocketMQ：Relay 发布 + Consumer 消费 + 幂等闭环 | 验证码外的通知走异步；同步验证码不动 |
| **P2** | 延时重试 + 死信告警 | 可靠性闭环 |
| **P3** | 服务化：平移 Channel 到 `OpenBlog-notification`，新增短信 / 飞书通道 | 多通道统一入口 |

> P0 是「先要可靠性，再要异步」。若最终判定通知量小、无需异步，停在 P0 也有收益（留痕 + 未来可迁移）。

---

## 附：同步 Dubbo 直发 vs MQ 异步（决策备忘）

| 维度 | 同步 Dubbo（现状，验证码用） | MQ 异步（本设计，其余通知用） |
|------|------------------------------|------------------------------|
| 反馈时效 | 秒级强反馈 | 秒~分钟级，可容忍 |
| 阻塞 | 业务线程阻塞等外部发信 | 不阻塞 |
| 失败补偿 | 删键报错，人工重试 | 自动退避重试 + 死信告警 |
| 幂等 | ✅ 已闭环 | ✅ 复用同一 messageId / 幂等键 |
| 适用 | 验证码等强反馈 | 站内信 / 告警 / 欢迎 / 营销 |
