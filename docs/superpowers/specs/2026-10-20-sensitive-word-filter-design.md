# OpenBlog 敏感词过滤系统设计（AC + 字典树双引擎版）

> 日期：2026-10-20
> 参考来源：`docs/sensitive-word-filter-design.md`（通用方案：版本化快照 + 本地不可变匹配器 + 原子替换）
> 目标：把 OpenBlog 的敏感词能力从「启动时固定词库 + 改配置重启」升级为「MySQL 词库 + 版本化快照 + Redis 分发 + 管理后台」，并同时实现 **字典树（Trie）** 与 **AC 自动机** 两套匹配引擎，保留现有 houbb 作为生产基线，便于后续用统一基准测试比较性能差距。
> 范围：`OpenBlog-business`（运行时 + 管理接口 + 基准测试）+ `vue/`（控制台页面）。`OpenBlog-message`、`OpenBlog-gateway` 不改，**不涉及三服务同步部署**。

---

## 0. 与参考文档的对应关系

参考文档是一套可直接移植的通用方案。本项目在保留其**核心原则**（读写分离、快照不可变、发布原子化、失败保留旧版本、版本只增不减）的前提下，按 OpenBlog 的实际技术栈做如下适配：

| 参考文档 | 本项目落地 | 原因 |
|---|---|---|
| 自研 AC 自动机 | **三种引擎并存**：`HoubbWordMatcher`（生产基线）/ `TrieWordMatcher`（字典树）/ `AcWordMatcher`（AC 自动机），由配置切换 | 用户要求两套自研引擎都实现，用统一基准测试比较；houbb 保留为行为与性能基线 |
| 独立 `sensitive-word/` 模块 | 放在 `OpenBlog-business` 的 `com.yqz.openblog.sensitiveword` 包 | 只有 business 有用户内容入口；message/gateway 不依赖，避免新增跨服务部署约束 |
| `/api/admin/sensitive-words` | `/api/v1/admin/sensitive-words` | 现有管理接口统一 `/api/v1/admin/**` |
| `{code,message,data,traceId}` | 复用 `ApiResponse` | 已有统一响应 |
| `page` 从 1 开始、`{list,total}` | **`page` 从 0 开始、`PageResult{items,page,size,total}`** | 现有前后端约定 |
| Redis Cluster hash tag | **单机 Redis，不使用 hash tag**；Key 走 `RedisKeys` 规范 | 当前部署为单机，无 Cluster |
| pub/sub 低延迟通知 | **5s 轮询**；pub/sub 列为二期 | 项目当前没有 Redis pub/sub 基础设施 |
| 本地磁盘兜底 | **MySQL 最新已发布版本兜底** | business 已强依赖 MySQL，比容器内磁盘更可靠；Redis 挂了也能启动 |
| Lua 原子发布 | **先写快照、后切指针**的两步写 | 顺序已保证「指针永远指向已存在的完整快照」；单机 Redis 无需 Lua |
| Prometheus 指标 | **结构化日志 + `/status` 状态接口** | 项目未引入 Micrometer/Actuator，不为本功能单独引入监控栈 |
| 管理服务独立部署 | **business 内置管理接口** | 博客规模，独立服务是过度设计 |
| JMH 基准 | **JUnit 5 基准测试类**（系统属性开关，不进入 CI） | 不为一次性对比引入 JMH；接口固定后可随时升级为 JMH |

---

## 1. 现状与问题

### 1.1 现有实现（已核实）

- `OpenBlog-business/src/main/java/com/yqz/openblog/forum/filter/SensitiveWordFilter.java`
  - 基于 `com.github.houbb:sensitive-word:0.29.5`（DFA）。
  - 构造时用 `WordDenys.defaults()`（内置 64,418 行词库）+ `openblog.sensitive-word.allow-words` 构建 `SensitiveWordBs`。
  - 对外只有 `contains / findAll / replace`。
- `OpenBlog-business/src/main/java/com/yqz/openblog/config/SensitiveWordProperties.java`
  - 仅有一个 `allow-words` 配置，当前值为 `["长期"]`。
- 调用点只有两处：
  - `CommentService.checkSensitive()`（评论 / 回复）
  - `ForumService.checkSensitive()`（论坛话题标题 + 正文、论坛评论）
  - 命中统一抛 `BizException(4003, "内容包含敏感词，请修改后重试")`，不向用户回显命中词。
- `application.yaml` 中 `openblog.sensitive-word.allow-words` 改动**必须重启**服务。
- 测试：`SensitiveWordFilterTest` 锁住了「内置词库把『长期』裸词收录导致正常文章被误拦」这条回归。

### 1.2 问题

| 问题 | 影响 |
|---|---|
| 词库是 jar 内置的，只能加白名单，不能增删改 | 发现漏拦/误伤只能改配置 + 发版重启 |
| 白名单变更需要重启 | 运营响应慢，误伤会持续到下一次发布 |
| 没有版本、没有审计、没有回滚 | 谁在什么时候改了词库不可追溯，改错无法快速恢复 |
| 没有管理界面、没有在线测试 | 运营无法自助校验「这句话会不会被拦」 |
| 词库与业务代码耦合 | 词库治理需要开发介入 |
| 匹配算法不可替换 | 无法用同一批数据比较 DFA / Trie / AC 的真实差异 |

> 现有实现已经在启动时建一次 DFA，请求路径没有 Redis 读取或建树。本次升级优先解决**可运营性**，同时把匹配引擎抽象成接口，提供 Trie / AC 两套实现和统一基准测试，为后续性能选型提供数据。

---

## 2. 目标与非目标

### 2.1 目标

1. **词库可运营**：管理后台完成增删改、批量导入、导出、启用/停用、分类、在线测试。
2. **变更可发布**：编辑工作集 → 预览待发布差异 → 发布新版本 → 全实例秒级到十秒级生效。
3. **可追溯可回滚**：版本历史、变更日志、回滚生成新版本（版本号只增不减）。
4. **运行时不退化**：
   - 请求路径零网络调用、零锁；
   - 快照不可变，`AtomicReference` 原子替换；
   - 刷新失败、Redis 不可用、快照损坏都保留旧版本，**绝不因刷新失败停止过滤**；
   - 启动时 Redis 不可用可从 MySQL 最新已发布版本恢复。
5. **行为兼容**：现有评论 / 论坛的 `4003` 拦截提示与命中日志格式保持不变；原 `allow-words` 语义迁移为 `status=0`。
6. **双引擎可比较**：
   - `TrieWordMatcher` 与 `AcWordMatcher` 使用同一份词表、同一套归一化、同一个 `WordMatcher` 接口；
   - 提供一致性对照测试（与 houbb、朴素 oracle 比对）；
   - 提供可重复的基准测试，输出构建耗时、contains/filter 延迟、吞吐、节点数等数据；
   - 提供配置切换与可选影子模式，支持灰度验证。

### 2.2 非目标

- 不做语义识别、拼音 / 谐音 / 拆字等变体识别（houbb 现有的宽度 / 大小写 / 中文样式归一化保留）。
- 不做按 category 分片、双数组 Trie、千万级词库优化。
- 不做 Redis pub/sub、本地磁盘缓存、独立管理服务。
- 不做网关层请求体过滤（网关目前不读 body，且会重复实现业务逻辑）。
- 不引入 RBAC 细粒度权限点（`rbac_*` 表目前只是预留脚手架，没有 Java 代码读取）。
- 不做 AI 内容审核。
- 一期不覆盖文章 / 项目 / 公司 / 站点配置等**管理员或作者受信内容**（见 §6.8，列入待确认）。
- 基准测试只做单机、单 JVM 的粗略对比，不承诺微基准级别的严谨性；需要时再升级 JMH。

---

## 3. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│ 控制台（Vue 3，/console/sensitive-words）                     │
│  词库 CRUD · 批量导入 · 待发布差异 · 发布 · 版本 · 回滚 · 在线测试 │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP /api/v1/admin/sensitive-words
┌──────────────────────────▼──────────────────────────────────┐
│ OpenBlog-business                                            │
│                                                              │
│  管理侧                                                       │
│    SensitiveWordAdminController                              │
│      ├─ 词条 CRUD / 导入 / 导出                               │
│      ├─ 待发布 diff（对比最近已发布快照）                       │
│      └─ 发布 / 回滚 / 手工同步                                 │
│    SensitiveWordPublishService                               │
│      ├─ 生成快照（排序 → \n 拼接 → gzip → SHA-256）            │
│      ├─ 写 sensitive_word_version                            │
│      └─ 写 Redis：先 snapshot，后 current                     │
│                                                              │
│  运行侧                                                       │
│    SensitiveWordManager                                      │
│      ├─ AtomicReference<SensitiveWordSnapshot>  ← 读路径无锁   │
│      ├─ 启动：Redis → 失败则 MySQL 最新版本                    │
│      ├─ 5s 轮询 current 版本号                                │
│      ├─ 单线程后台构建 + 原子替换 + 节点心跳                    │
│      └─ 可选 shadow 引擎对比（采样 + 日志 + /status 统计）      │
│    WordMatcherFactory                                        │
│      ├─ HoubbWordMatcher   （生产基线，默认）                  │
│      ├─ TrieWordMatcher    （字典树）                         │
│      └─ AcWordMatcher      （AC 自动机）                      │
│                                                              │
│  接入侧                                                       │
│    SensitiveWordGuard.rejectIfHit(...)                       │
│      └─ CommentService / ForumService / FeedbackService ...  │
└───────────────┬──────────────────────────────┬───────────────┘
                │ 写                            │ 读版本号/快照
┌───────────────▼──────────────┐   ┌───────────▼──────────────┐
│ MySQL（权威源）                │   │ Redis（分发层，可丢）       │
│ sensitive_word               │   │ openblog:content:          │
│ sensitive_word_version       │   │   sensitive-word:current   │
│ sensitive_word_change_log    │   │   ...:snapshot:{version}   │
└──────────────────────────────┘   │   ...:node:{instanceId}    │
                                   │   ...:publish-lock         │
                                   └────────────────────────────┘
```

### 3.1 核心原则（与参考文档一致）

1. **读写分离**：业务请求只读 JVM 内存；Redis/MySQL 只出现在启动、版本变更、管理操作。
2. **快照不可变**：`SensitiveWordSnapshot` 构建完不再修改，替换引用即可，读路径无锁。
3. **发布顺序固定**：先写 `snapshot:{v}`，再改 `current`；`current` 永远指向完整快照。
4. **失败保留旧版本**：刷新失败、快照缺失、校验和不匹配、Redis 故障，一律不替换当前快照。
5. **版本只增不减**：回滚 = 用旧内容发布一个**新版本号**。
6. **Redis 可丢**：Redis 只是分发层；丢了可以从 MySQL 最新 `PUBLISHED` 版本重建。

### 3.2 三种引擎的定位

| 引擎 | 定位 | 默认 |
|---|---|---|
| `HoubbWordMatcher` | 生产基线：直接封装现有 houbb DFA，行为与当前线上一致 | ✅ 默认 |
| `TrieWordMatcher` | 对照组：朴素字典树，逐起点回溯，实现简单、构建快、内存省 | 通过配置启用 |
| `AcWordMatcher` | 对照组：AC 自动机，fail 指针 + output 链，单遍扫描 | 通过配置启用 |

三者在同一个 JVM 内共享同一份 `List<String>` 词表与同一套归一化，只在匹配算法上不同。生产默认仍是 houbb，直到满足 §9.4 的切换门槛。

---

## 4. 数据模型

### 4.1 MySQL

新增脚本 `sql/sensitive-word.sql`，遵循现有 `CREATE TABLE IF NOT EXISTS`、显式 `COMMENT`、`utf8mb4` 约定。

#### 4.1.1 词条工作集

```sql
CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`       VARCHAR(128)    NOT NULL COMMENT '词条，trim 后存储，禁止换行/控制字符',
  `category`   VARCHAR(64)     NOT NULL DEFAULT 'default' COMMENT '分类，供运营筛选',
  `status`     TINYINT         NOT NULL DEFAULT 1 COMMENT '1=启用（参与过滤） 0=停用/豁免（原 allow-words 语义）',
  `remark`     VARCHAR(255)    DEFAULT NULL,
  `created_by` VARCHAR(64)     NOT NULL COMMENT '操作人用户名，解析失败时存 userId',
  `updated_by` VARCHAR(64)     NOT NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_word` (`word`),
  KEY `idx_sensitive_word_category_status` (`category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='敏感词工作集（草稿 + 当前内容）';
```

说明：

- **物理删除**，删除动作写入变更日志；重复添加同一个词直接重新插入即可。
- `status=0` 承担原 `allow-words` 的语义：词条保留、可追溯，但不参与过滤。这样「长期」这类误伤词不必删除，也不会建立独立白名单表。
- `utf8mb4_0900_ai_ci` 让唯一键天然大小写不敏感；运行时的宽度/中文样式归一化由匹配引擎负责。两者不完全等价（例如 `ＡＢ` 与 `AB` 在库里是两条，运行时都会命中），但不会造成漏拦，只会多几条重复词条，后台提示以 DB 唯一键为准。
- 结论：**保持 `utf8mb4_0900_ai_ci` 不改**。匹配本身已忽略大小写，`abc` / `ABC` 视为同一词条是期望行为；重音差异（`café` / `cafe`）也会被合并，属于可接受的少量边界。改为 `_as_cs` 会让大小写不同的词条各建一条，但运行时仍会同时命中，反而增加维护成本。更新词条时用 `countByWordExcludingId` 避免把自身算作重复。
- 停用一条词**不需要重建**：发布时只把 `status=1` 的词放进快照。

#### 4.1.2 版本表

```sql
CREATE TABLE IF NOT EXISTS `sensitive_word_version` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增 ID 即版本号',
  `kind`           VARCHAR(16)     NOT NULL DEFAULT 'PUBLISH' COMMENT 'PUBLISH / ROLLBACK',
  `status`         VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PUBLISHED / FAILED',
  `source_version` BIGINT UNSIGNED DEFAULT NULL COMMENT '回滚来源版本',
  `word_count`     INT             NOT NULL,
  `checksum`       CHAR(64)        NOT NULL COMMENT '快照明文 SHA-256',
  `snapshot_blob`  LONGBLOB        NOT NULL COMMENT 'gzip 压缩的换行分隔词表，用于分发、回滚与灾备',
  `change_summary` VARCHAR(512)    DEFAULT NULL COMMENT '{"added":3,"removed":1,"updated":0}',
  `remark`         VARCHAR(255)    DEFAULT NULL COMMENT '发布备注',
  `created_by`     VARCHAR(64)     NOT NULL,
  `published_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sensitive_word_version_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='不可变版本快照（版本号只增不减）';
```

与参考文档的差异：

- 增加 `kind`，区分普通发布与回滚（前端「类型」列直接使用）。
- 增加 `status` 的 `PENDING / FAILED`：DB 事务先落版本，Redis 分发可能失败；只有 Redis 写成功才置 `PUBLISHED`。这样「DB 已提交但 Redis 没写成功」不会伪装成已生效。
- `published_at` 在插入时即记录（DB 事务时间），Redis 成功后再更新 `status`。真正对外生效时间以 `current` 切换为准，状态接口返回 `localVersion` / `publishedVersion`。

#### 4.1.3 变更日志

```sql
CREATE TABLE IF NOT EXISTS `sensitive_word_change_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`        VARCHAR(128)    NOT NULL COMMENT '批量/回滚动作用 * 作为占位',
  `action`      VARCHAR(16)     NOT NULL COMMENT 'ADD / UPDATE / DELETE / IMPORT / ROLLBACK',
  `before_json` JSON            DEFAULT NULL,
  `after_json`  JSON            DEFAULT NULL,
  `version_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '发布后回填',
  `operator`    VARCHAR(64)     NOT NULL,
  `operated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sensitive_word_log_word_time` (`word`, `operated_at`),
  KEY `idx_sensitive_word_log_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='词库操作审计，只增不改';
```

写入策略：

- 手工新增 / 编辑 / 删除：一词一条。
- 批量导入：对**实际发生变化的词**批量写入 `IMPORT`（1000 条一批），不记录未变化的行。
- 回滚：写一条 `ROLLBACK` 汇总（`word='*'`，`after_json` 记目标版本与词数），不逐词写入，避免一次回滚产生数万条日志。
- 发布后回填 `version_id`；回滚日志的 `version_id` 指向新生成的版本。

### 4.2 数据量估算

当前内置词库 64,418 行：

| 项 | 值 |
|---|---|
| 明文（换行分隔 UTF-8） | ≈ 1.24 MB |
| gzip 后（DB `snapshot_blob`） | ≈ 378 KB |
| Base64(gzip)（Redis value） | ≈ 504 KB |
| `sensitive_word` 行数 | ≈ 64,418 |

这个规模完全在单机 MySQL / Redis / 1GB 堆内，不需要分片。Trie / AC 的节点数约为「词库总字符数 − 公共前缀重叠」，具体值由基准测试输出。

---

## 5. Redis 设计

### 5.1 Key

统一走 `framework-redis` 的 `RedisKeys`，领域前缀用 `content`：

| Key | 类型 | 内容 | TTL | 写者 | 读者 |
|---|---|---|---|---|---|
| `openblog:content:sensitive-word:current` | String | 当前版本号，如 `43` | 3650 天（近似永久） | 发布 / 同步 | 所有 business 实例 |
| `openblog:content:sensitive-word:snapshot:{v}` | String | `checksum + "\n" + Base64(gzip(词表))` | 30 天 | 发布 / 同步 | 所有 business 实例 |
| `openblog:content:sensitive-word:node:{instanceId}` | String | 节点当前版本号 | 30s（10s 心跳续期） | business 实例 | 管理状态接口 |
| `openblog:content:sensitive-word:publish-lock` | String | 发布锁 token | 30s | 发布服务 | 发布服务 |

`RedisKeys` 新增：

```java
private static final String CONTENT_SENSITIVE_WORD = "openblog:content:sensitive-word";

public static final String CONTENT_SENSITIVE_WORD_CURRENT =
        CONTENT_SENSITIVE_WORD + ":current";
public static final String CONTENT_SENSITIVE_WORD_PUBLISH_LOCK =
        CONTENT_SENSITIVE_WORD + ":publish-lock";

public static String sensitiveWordSnapshot(long version) {
    return CONTENT_SENSITIVE_WORD + ":snapshot:" + version;
}

public static String sensitiveWordNode(String instanceId) {
    return CONTENT_SENSITIVE_WORD + ":node:" + instanceId;
}
```

说明：

- **单机 Redis，不需要 hash tag。** 参考文档的 `{sens:dict}` 是为了 Cluster 下让 `current` 与 `snapshot` 同 slot；本项目没有 Cluster。
- 快照 value 用 **Base64** 而不是裸二进制：`RedisOps` 基于 `StringRedisTemplate`，Base64 可以让快照直接走现成的 `RedisOps.get/set`，代价是 +33% 体积（约 504 KB），可接受。
- 快照 key 带 30 天 TTL：历史版本过期不影响运行中的节点（内存里已有快照）；回滚时从 MySQL `snapshot_blob` 读取，不依赖 Redis 历史 key。
- `current` 用超长 TTL 代替「永不过期」，避免为 `RedisOps` 增加无 TTL 的 `set` 重载。

### 5.2 发布为什么不需要 Lua

参考文档用 Lua 保证「写快照 + 切指针」原子。本项目单机 Redis，采用固定顺序：

1. `SET snapshot:{v} <value> EX 30d`
2. `SET current v`

任何时刻只要 `current` 可见，它指向的快照必然已经写完。两次 SET 之间崩溃只会留下一个孤儿快照，不会产生空窗。并发发布由发布锁排除。

如果二期引入 pub/sub，需要把「写快照 + 切指针 + PUBLISH」放进 Lua，届时再补。

---

## 6. 后端设计

### 6.1 包结构

```
OpenBlog-business/src/main/java/com/yqz/openblog/
├── config/SensitiveWordProperties.java              （改造：扩展配置）
└── sensitiveword/
    ├── controller/SensitiveWordAdminController.java
    ├── dto/
    │   ├── SensitiveWordUpsertRequest.java
    │   ├── SensitiveWordItemResponse.java
    │   ├── SensitiveWordPublishRequest.java
    │   ├── SensitiveWordRollbackRequest.java
    │   ├── SensitiveWordImportPreviewResponse.java
    │   ├── SensitiveWordImportRequest.java
    │   ├── SensitiveWordPendingResponse.java
    │   ├── SensitiveWordVersionResponse.java
    │   ├── SensitiveWordStatusResponse.java
    │   └── SensitiveWordCheckRequest.java / CheckResponse.java
    ├── entity/
    │   ├── SensitiveWord.java
    │   ├── SensitiveWordVersion.java
    │   └── SensitiveWordChangeLog.java
    ├── repo/
    │   ├── SensitiveWordMapper.java
    │   ├── SensitiveWordVersionMapper.java
    │   └── SensitiveWordChangeLogMapper.java
    ├── runtime/
    │   ├── WordMatcher.java                        （统一接口）
    │   ├── FilterResult.java / HitRange.java       （统一结果）
    │   ├── CharNormalizer.java                     （三引擎共用归一化）
    │   ├── HoubbWordMatcher.java                   （生产基线）
    │   ├── TrieWordMatcher.java                    （字典树）
    │   ├── AcWordMatcher.java                      （AC 自动机）
    │   ├── WordMatcherFactory.java                 （按配置创建）
    │   ├── SensitiveWordSnapshot.java              （不可变快照）
    │   ├── SnapshotCodec.java                      （编码 / gzip / SHA-256）
    │   ├── SensitiveWordManager.java               （加载 / 轮询 / 原子替换 / 心跳 / 影子）
    │   └── SensitiveWordGuard.java                 （业务接入：rejectIfHit）
    └── service/
        ├── SensitiveWordAdminService.java          （CRUD / 导入 / 导出 / diff / 测试）
        ├── SensitiveWordPublishService.java        （发布 / 回滚 / 同步）
        └── SensitiveWordSeedRunner.java            （一次性从内置词库播种，见 §10.2）
```

测试：

```
OpenBlog-business/src/test/java/com/yqz/openblog/sensitiveword/
├── runtime/
│   ├── SnapshotCodecTest.java
│   ├── HoubbWordMatcherTest.java
│   ├── TrieWordMatcherTest.java
│   ├── AcWordMatcherTest.java
│   ├── EngineParityTest.java                       （四者一致性对照）
│   ├── SensitiveWordManagerTest.java
│   └── SensitiveWordGuardTest.java
└── benchmark/
    ├── SensitiveWordBenchmarkTest.java             （-Dopenblog.bench=true 才运行）
    └── BenchmarkCorpus.java                        （词表 / 文本语料生成）
```

**不新建 framework 模块**：只有 business 需要运行时；message 不处理用户正文，gateway 不读 body。放进 framework 会让 message/gateway 也背上词库依赖，并引入不必要的部署约束。

### 6.2 运行时数据结构

#### 6.2.1 统一接口与结果

```java
public interface WordMatcher {

    /** 全量过滤：是否命中、替换后的文本、命中片段、命中区间。 */
    FilterResult filter(String text);

    /** 只看是否命中；实现应走提前返回的快路径。 */
    boolean contains(String text);

    /** 用等长 * 替换命中区间。 */
    default String replace(String text) {
        return filter(text).filteredText();
    }

    /** 引擎名：houbb / trie / ac，用于日志、状态与基准结果。 */
    String engine();
}

/** [start, endExclusive) */
public record HitRange(int start, int endExclusive) {
    public int length() {
        return endExclusive - start;
    }
}

public record FilterResult(
        boolean hit,
        String filteredText,
        List<String> hitWords,
        List<HitRange> ranges) {

    public static FilterResult empty(String original) {
        return new FilterResult(false, original == null ? "" : original, List.of(), List.of());
    }
}
```

- `hitWords` 是**原文中的命中片段**（不是词典里的规范词），与参考文档一致；日志与在线测试直接用。
- `ranges` 供一致性对照测试使用，不对外暴露。
- 所有引擎必须满足同一套语义，见 §6.2.6。

```java
public record SensitiveWordSnapshot(
        long version,
        int wordCount,
        String checksum,
        String engine,
        WordMatcher matcher) {

    public static SensitiveWordSnapshot empty() {
        return new SensitiveWordSnapshot(0, 0, "", "none", EmptyWordMatcher.INSTANCE);
    }
}
```

#### 6.2.2 CharNormalizer：三引擎共用归一化

houbb 的归一化是**逐字符映射**（`IWordFormat#format(char, IWordContext)`），宽度、大小写、数字样式、中文样式、英文样式都是 `char → char`，不会改变长度。直接复用它的 format 链，可以让 Trie / AC 与 houbb 的输入完全一致：

```java
final class CharNormalizer {

    private static final IWordContext CONTEXT = SensitiveWordContext.newInstance();

    private final IWordFormat format;

    /** 与现有 houbb 配置一致：大小写、全半角、数字样式、中文样式、英文样式。 */
    static CharNormalizer houbbCompatible() {
        return new CharNormalizer(WordFormats.chains(
                WordFormats.ignoreCase(),
                WordFormats.ignoreWidth(),
                WordFormats.ignoreNumStyle(),
                WordFormats.ignoreChineseStyle(),
                WordFormats.ignoreEnglishStyle()));
    }

    /** 基准测试用：不做任何归一化，隔离算法本身。 */
    static CharNormalizer none() {
        return new CharNormalizer(WordFormats.none());
    }

    char normalize(char c) {
        return format.format(c, CONTEXT);
    }
}
```

关键约束：

- **词表与文本必须使用同一个 `CharNormalizer`**：建 Trie / AC 时归一化每个词的字符，匹配时归一化每个输入字符。
- 当前所有规则都是 1:1 字符映射，因此命中区间可以直接用原文下标，不需要 `normalizedIndex → originalIndex` 映射。实现时加一条单测断言「归一化后长度不变」；如果未来引入删除字符的规则，再补索引映射。
- 归一化成本对 Trie / AC 是相同的，三引擎对比是公平的；基准测试额外提供 `normalization=none` 一轮，用来看纯算法差距。

#### 6.2.3 HoubbWordMatcher（生产基线）

```java
final class HoubbWordMatcher implements WordMatcher {

    private final SensitiveWordBs delegate;

    static HoubbWordMatcher build(List<String> words) {
        return new HoubbWordMatcher(SensitiveWordBs.newInstance()
                .wordDeny(() -> words)                 // 只使用当前版本的词表
                .wordAllow(WordAllows.defaults())      // 保留内置 allow，行为与现状对齐
                .ignoreCase(true)
                .ignoreWidth(true)
                .ignoreNumStyle(true)
                .ignoreChineseStyle(true)
                .ignoreEnglishStyle(true)
                .ignoreRepeat(false)
                .init());
    }

    @Override public String engine() { return "houbb"; }

    @Override public boolean contains(String text) {
        return text != null && !text.isEmpty() && delegate.contains(text);
    }

    @Override public FilterResult filter(String text) {
        if (text == null || text.isEmpty()) return FilterResult.empty(text);
        List<IWordResult> raw = delegate.findAll(text, WordResultHandlers.raw());
        List<HitRange> ranges = raw.stream()
                .map(r -> new HitRange(r.startIndex(), r.endIndex()))
                .toList();
        List<String> words = raw.stream()
                .map(IWordResult::word)
                .distinct()
                .toList();
        return new FilterResult(!ranges.isEmpty(), delegate.replace(text), words, ranges);
    }
}
```

注意：

- `IWordResult#endIndex()` 的开闭区间语义已用单测确认：`endIndex` 为**开区间**，可直接映射 `HitRange(start, endIndex)`。
- `status=0` 的词在发布快照时已被排除，因此不需要再把 `allow-words` 传给 `wordAllow`。内置 allow 表（`生日快乐`、`幸运` 等）保留，避免迁移后出现新的误伤。
- 内置 allow 表只挂在 houbb 引擎上，因此 `WordMatcherFactory` 在构建 Trie / AC 时会先用同一个 `CharNormalizer` 归一化后剔除这些词（`withoutHoubbAllowWords`），否则切换引擎会让这少量词的判定发生变化。
- houbb 默认结果条件是 `englishWordMatch`（纯英文命中词左右不能紧邻英文字母），且默认 `wordFailFast=true` 会在每个起点遇到最短词就停。为保证与 Trie / AC 的区间语义一致，构造时显式设置 `wordFailFast(false)`，取每个起点的最长命中；英文词边界条件由另两个引擎在 `WordMatcher.acceptsMatch` 中复刻，而不是关掉。

#### 6.2.4 TrieWordMatcher（字典树）

结构：普通 Trie，节点保存 `next`、`terminal`、`length`。匹配时**对每个起点从根走到底**，收集全部候选命中；最坏 O(n × L)，L 为最长公共前缀 / 最长词长。候选命中最后统一交给 `WordMatcher.buildResult` 做「左到右、同起点取最长、区间不重叠」的收敛（见 6.2.6），保证 `filteredText` 与 houbb 逐字符一致。

```java
final class TrieWordMatcher implements WordMatcher {

    private static final class Node {
        final Map<Character, Node> next = new HashMap<>(4);
        boolean terminal;
        int length;             // 仅 terminal 节点有效
    }

    private final Node root;
    private final CharNormalizer normalizer;
    private final int nodeCount;

    static TrieWordMatcher build(List<String> words, CharNormalizer normalizer) {
        Node root = new Node();
        int count = 1;
        for (String word : words) {
            if (word == null || word.isEmpty()) continue;
            Node cur = root;
            for (int i = 0; i < word.length(); i++) {
                char c = normalizer.normalize(word.charAt(i));
                Node child = cur.next.get(c);
                if (child == null) {
                    child = new Node();
                    cur.next.put(c, child);
                    count++;
                }
                cur = child;
            }
            cur.terminal = true;
            cur.length = word.length();
        }
        return new TrieWordMatcher(root, normalizer, count);
    }

    @Override public String engine() { return "trie"; }

    @Override
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) return false;
        char[] src = text.toCharArray();
        for (int i = 0; i < src.length; i++) {
            Node node = root;
            for (int j = i; j < src.length; j++) {
                node = node.next.get(normalizer.normalize(src[j]));
                if (node == null) break;
                if (node.terminal) return true;      // 提前返回
            }
        }
        return false;
    }

    @Override
    public FilterResult filter(String text) {
        if (text == null || text.isEmpty()) return FilterResult.empty(text);
        char[] src = text.toCharArray();
        char[] out = src.clone();
        boolean[] marked = new boolean[src.length];
        LinkedHashSet<String> words = new LinkedHashSet<>();
        List<HitRange> ranges = new ArrayList<>();

        for (int i = 0; i < src.length; i++) {
            Node node = root;
            for (int j = i; j < src.length; j++) {
                node = node.next.get(normalizer.normalize(src[j]));
                if (node == null) break;
                if (node.terminal) {
                    int end = j + 1;
                    Arrays.fill(marked, i, end, true);
                    ranges.add(new HitRange(i, end));
                    words.add(new String(src, i, end - i));
                }
            }
        }
        for (int i = 0; i < marked.length; i++) {
            if (marked[i]) out[i] = '*';
        }
        return new FilterResult(!ranges.isEmpty(), new String(out),
                List.copyOf(words), List.copyOf(ranges));
    }

    int nodeCount() { return nodeCount; }            // 供基准测试 / 内存分析
}
```

特点：

- 构建只做插入，**最快**；节点只有 `next` + `terminal` + `length`，**内存最省**。
- `contains` 命中早退，短文本/高命中场景可能非常快。
- `filter` 必须遍历每个起点，长文本 + 无命中 + 长公共前缀时是 O(n × L) 的最坏情况，这正是参考文档指出的原实现瓶颈。
- 不做 fail 指针，因此不可能出现「漏掉从文本中间开始的后缀词」——每个起点都从根重新走。

#### 6.2.5 AcWordMatcher（AC 自动机）

结构：在 Trie 基础上增加 `fail`（失配指针）与 `output`（fail 链上最近的 terminal 节点，加速命中枚举）。构建时 BFS 计算 fail；匹配时单遍扫描，O(n + 命中数)。

```java
final class AcWordMatcher implements WordMatcher {

    private static final class Node {
        final Map<Character, Node> next = new HashMap<>(4);
        Node fail;
        Node output;
        boolean terminal;
        int length;
    }

    private final Node root;
    private final CharNormalizer normalizer;
    private final int nodeCount;

    static AcWordMatcher build(List<String> words, CharNormalizer normalizer) {
        Node root = new Node();
        int count = 1;
        for (String word : words) {
            if (word == null || word.isEmpty()) continue;
            Node cur = root;
            for (int i = 0; i < word.length(); i++) {
                char c = normalizer.normalize(word.charAt(i));
                Node child = cur.next.get(c);
                if (child == null) {
                    child = new Node();
                    cur.next.put(c, child);
                    count++;
                }
                cur = child;
            }
            cur.terminal = true;
            cur.length = word.length();
        }

        Deque<Node> queue = new ArrayDeque<>();
        for (Node child : root.next.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            for (Map.Entry<Character, Node> e : cur.next.entrySet()) {
                char c = e.getKey();
                Node child = e.getValue();
                Node f = cur.fail;
                while (f != null && !f.next.containsKey(c)) f = f.fail;
                child.fail = (f == null) ? root : f.next.get(c);
                if (child.fail == child) child.fail = root;    // 防御
                child.output = child.fail.terminal ? child.fail : child.fail.output;
                queue.add(child);
            }
        }
        return new AcWordMatcher(root, normalizer, count);
    }

    @Override public String engine() { return "ac"; }

    @Override
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) return false;
        Node node = root;
        for (int i = 0; i < text.length(); i++) {
            char c = normalizer.normalize(text.charAt(i));
            while (node != root && !node.next.containsKey(c)) node = node.fail;
            Node next = node.next.get(c);
            node = (next == null) ? root : next;
            if (node.terminal || node.output != null) return true;   // 提前返回
        }
        return false;
    }

    @Override
    public FilterResult filter(String text) {
        if (text == null || text.isEmpty()) return FilterResult.empty(text);
        char[] src = text.toCharArray();
        char[] out = src.clone();
        boolean[] marked = new boolean[src.length];
        LinkedHashSet<String> words = new LinkedHashSet<>();
        List<HitRange> ranges = new ArrayList<>();

        Node node = root;
        for (int i = 0; i < src.length; i++) {
            char c = normalizer.normalize(src[i]);
            while (node != root && !node.next.containsKey(c)) node = node.fail;
            Node next = node.next.get(c);
            node = (next == null) ? root : next;

            if (node.terminal) {
                int start = i - node.length + 1;
                Arrays.fill(marked, start, i + 1, true);
                ranges.add(new HitRange(start, i + 1));
                words.add(new String(src, start, node.length));
            }
            for (Node o = node.output; o != null; o = o.output) {
                int start = i - o.length + 1;
                Arrays.fill(marked, start, i + 1, true);
                ranges.add(new HitRange(start, i + 1));
                words.add(new String(src, start, o.length));
            }
        }
        for (int i = 0; i < marked.length; i++) {
            if (marked[i]) out[i] = '*';
        }
        return new FilterResult(!ranges.isEmpty(), new String(out),
                List.copyOf(words), List.copyOf(ranges));
    }

    int nodeCount() { return nodeCount; }
}
```

特点：

- 构建比 Trie 多一次 BFS，节点多两个引用（`fail` / `output`），**构建慢一点、内存高一点**。
- 匹配是单遍扫描，`contains` 最坏 O(n)，`filter` 候选收集 O(n + 命中数)、区间收敛排序 O(h log h)；长文本、高命中、大量共享后缀时优势明显。
- `output` 链只记录 terminal 节点，避免每次命中都完整走 fail 链。
- 防御性判断 `child.fail == child` 防止根转移自指导致死循环。

#### 6.2.6 匹配语义（三引擎必须一致）

| 规则 | 定义 |
|---|---|
| 命中判定 | 文本中任意子串等于词表中的某个词（归一化后比较） |
| 英文词边界 | 复刻 houbb `englishWordMatch`：纯英文/空格的命中词，左右不能紧邻英文字母（`class` 内的 `ass` 不算命中） |
| 重叠 | 收集全部候选后，从左到右贪心选择：每个起点取最长命中，已选区间内/交叉的后续命中不再记录（与 houbb `replace` 一致，保证 `filteredText` 逐字符相同） |
| 替换 | 选中区间替换为**等长** `*` |
| `hitWords` / `ranges` | 只记录最终选中的区间；被最长命中完全覆盖的短前缀/后缀词不重复记录 |
| 空文本 / `null` | `hit=false`，`filteredText` 原样（`null` 统一按空串） |
| 文本本身含 `*` | 仍按原文扫描，不受替换影响（先扫描、后统一写 `*`） |
| 归一化 | 词表与文本使用同一个 `CharNormalizer`；命中区间用原文下标 |
| 大小写 / 全半角等 | 由 `CharNormalizer` 决定；三引擎一致 |

为什么不做「重叠词全部记录」：houbb 是生产基线，它按非重叠区间替换；若 Trie / AC 记录更多区间，`hitWords` 会漂移，虽然掩码文本可能相同，但无法通过严格的一致性对照。三引擎的目标是**可无损切换**，因此以 houbb 的选择规则为准。`hitWords` 和 `ranges` 允许顺序不同，一致性测试时转成 `Set` 比较；`filteredText` 必须逐字符相等。

#### 6.2.7 引擎选择、切换与影子对比

```java
public enum MatcherEngine { HOUBB, TRIE, AC }

@Component
public class WordMatcherFactory {

    private final SensitiveWordProperties properties;

    public WordMatcher create(List<String> words) {
        return switch (properties.getEngine()) {
            case HOUBB -> HoubbWordMatcher.build(words);
            case TRIE  -> TrieWordMatcher.build(words, normalizer());
            case AC    -> AcWordMatcher.build(words, normalizer());
        };
    }

    private CharNormalizer normalizer() {
        return properties.getNormalization() == Normalization.NONE
                ? CharNormalizer.none()
                : CharNormalizer.houbbCompatible();
    }
}
```

配置切换：

```yaml
openblog:
  sensitive-word:
    engine: houbb              # houbb | trie | ac
    normalization: houbb-compatible   # houbb-compatible | none（none 仅供基准测试）
```

- `engine` 是**实例级**配置：可以只给一台实例设置 `ENGINE=ac` 做金丝雀，观察日志与 `/status`。
- 引擎只在快照构建时选择一次；运行中切换配置需要重启，符合当前配置项风格。
- `/status` 与 `/check` 返回当前引擎名，避免排查时「不知道用的哪套」。

**影子模式（可选，灰度期使用）**：

```yaml
    shadow-engine: none        # none | trie | ac；与 engine 相同时自动关闭
    shadow-sample-rate: 0.1    # 采样率，0 关闭
    shadow-log-mismatch: true  # 只在采样命中且结果不同时打 WARN
```

- `SensitiveWordManager` 在采样的请求上同时执行主引擎与影子引擎，**主引擎结果照常返回**，影子结果只用于对比。
- 记录：采样数、不一致数、两引擎平均耗时；`/status` 暴露 `shadow` 统计。
- 不一致日志只记录文本长度、命中数量、traceId，**不记录原文**。
- 影子模式会使 CPU 翻倍，只建议在 1–2 天的验证窗口开启。

#### 6.2.8 快照编码

```java
public final class SnapshotCodec {

    /** 排序 → UTF-8 → 换行拼接（无尾换行）。排序保证同一词集 checksum 稳定。 */
    public static byte[] encode(List<String> words) { ... }

    public static List<String> decode(byte[] plain) { ... }

    public static String sha256Hex(byte[] bytes) { ... }

    public static byte[] gzip(byte[] plain) { ... }

    public static byte[] gunzip(byte[] gz) { ... }
}
```

- 明文：UTF-8，`\n` 分隔，**末尾无换行**，词条已按字典序排序。
- `checksum = SHA-256(明文)`。
- MySQL `snapshot_blob = gzip(明文)`。
- Redis value = `checksum + "\n" + Base64(gzip(明文))`。
- 加载时先校验 checksum，不匹配直接拒绝替换。

### 6.3 SensitiveWordManager

```java
@Component
public class SensitiveWordManager {

    private final AtomicReference<SensitiveWordSnapshot> current =
            new AtomicReference<>(SensitiveWordSnapshot.empty());
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);
    private final ScheduledExecutorService poller = ...;   // 单线程
    private final ExecutorService builder = ...;           // 单线程
    private final WordMatcherFactory matcherFactory;

    /** 启动：Redis 优先，失败回退 MySQL 最新 PUBLISHED 版本 */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        reloadOnStartup();
        poller.scheduleWithFixedDelay(this::poll, 0, 5, TimeUnit.SECONDS);
        poller.scheduleWithFixedDelay(this::heartbeat, 0, 10, TimeUnit.SECONDS);
    }

    public boolean contains(String text) {
        if (!properties.isEnabled() || text == null || text.isBlank()) return false;
        SensitiveWordSnapshot s = current.get();
        if (s.version() == 0) {
            return properties.getFailMode() == FailMode.REJECT_ALL;
        }
        return s.matcher().contains(text);      // 影子模式下按采样对比
    }

    private void poll() {
        long local = current.get().version();
        redisOps.get(RedisKeys.CONTENT_SENSITIVE_WORD_CURRENT)
                .map(Long::parseLong)
                .filter(v -> v > local)
                .ifPresent(this::submitReload);
    }

    private synchronized void reload(long expectedVersion) {
        if (expectedVersion <= current.get().version()) return;   // 版本只前进
        String value = redisOps.get(RedisKeys.sensitiveWordSnapshot(expectedVersion)).orElse(null);
        if (value == null) { log.warn("sensitive snapshot missing, version={}", expectedVersion); return; }
        try {
            byte[] plain = SnapshotCodec.parseRedisValue(expectedVersion, value);
            List<String> words = SnapshotCodec.decode(plain);
            WordMatcher matcher = matcherFactory.create(words);
            SensitiveWordSnapshot next = new SensitiveWordSnapshot(
                    expectedVersion, words.size(), SnapshotCodec.sha256Hex(plain),
                    matcher.engine(), matcher);
            current.set(next);                                    // 原子替换
            log.info("sensitive dict reloaded: version={}, words={}, engine={}, cost={}ms", ...);
        } catch (Exception e) {
            log.error("sensitive dict reload failed, keep version {}", current.get().version(), e);
        }
    }
}
```

关键点：

- `reload` 串行化（`synchronized` + 单线程 builder），避免并发重建。
- 版本只前进：`expectedVersion <= local` 直接返回，避免乱序/重复消息导致回退。
- 构建在后台线程，不阻塞轮询与业务线程。
- 快速连续发布：轮询天然合并（只拉最新版本）。
- 构建期间旧快照继续服务；新快照构建完成才 `set`。
- **区分「合法空词表」和「加载失败」**：
  - `version=0` 且没有历史快照 → `EMPTY` / `UNAVAILABLE`，由 `fail-mode` 决定；
  - 有一份合法快照且词数为 0（理论上发布流程会拦截）→ 按空词表处理。
  - 状态接口暴露 `state`，日志中明确 `EMPTY` vs `UNAVAILABLE`。

#### 6.3.1 启动加载与 MySQL 兜底

```
1. 读 Redis current
2. 命中 → 读 snapshot → 校验 checksum → 构建 → READY
3. Redis 未命中 / 读取失败 / 快照缺失 / 校验失败
   → 查 MySQL 最新 status=PUBLISHED 的版本（ORDER BY id DESC LIMIT 1）
   → 从 snapshot_blob 解压、校验、构建
   → 尽力把 snapshot + current 写回 Redis（失败只告警）
4. MySQL 也没有可用版本
   → 按 fail-mode 决策：
      STALE      沿用内存空快照，放行，状态 UNAVAILABLE，ERROR 日志（默认）
      REJECT_ALL 拒绝所有待过滤文本，状态 UNAVAILABLE，ERROR 日志
      ALLOW_ALL  明确放行，仅测试环境使用
```

这样可以覆盖：首次部署尚未发布、Redis 被清空、Redis 宕机、发布时 Redis 写失败后重启。

#### 6.3.2 失败策略

| 场景 | 行为 |
|---|---|
| 轮询读 Redis 失败 | 保留当前快照，WARN 日志 |
| 快照缺失 / 解压失败 / checksum 不匹配 | 不替换，保留旧快照，ERROR 日志 |
| 新版本构建抛异常 | 不替换，保留旧快照，ERROR 日志 |
| Redis 不可用 | 运行中的实例继续用内存快照；新实例从 MySQL 兜底 |
| 空词表发布 | 发布接口默认拒绝；确需发布需显式 `allowEmpty=true` |
| 节点落后 | `/status` 展示落后节点；超过 3 个轮询周期（15s）打 WARN |

#### 6.3.3 节点心跳

- `instanceId = spring.application.name + "-" + hostname + "-" + server.port`，可用 `openblog.sensitive-word.node.id` 覆盖。
- 每 10s 写 `node:{id} = version`，TTL 30s。
- `/status` 用 `StringRedisTemplate.keys("openblog:content:sensitive-word:node:*")` 聚合。实例数是个位数，`KEYS` 可接受；不用 `RedisOps`（避免为管理场景给框架加 `KEYS` 能力）。

### 6.4 发布流程

```
1. 获取发布锁：setIfAbsent(publish-lock, token, 30s)；后台按 TTL/3 续租（token 匹配才 pexpire）
   失败 → BizException(4092, "已有发布任务进行中，请稍后重试")
2. 校验：
   - 当前工作集 status=1 的词数 > 0；为空且 allowEmpty != true → BizException(4000, "拒绝发布空词表")
   - 单条校验（正常 CRUD 已保证，发布时再兜底）
3. DB 事务：
   a. 查 status=1 的全部词条，按 word 字典序排序
   b. 生成明文 / gzip / checksum
   c. 与最近一次 PUBLISHED 快照 diff，得到 {added, removed, updated}
   d. INSERT sensitive_word_version (kind=PUBLISH, status=PENDING, ...)，拿到自增版本号 v
   e. 回填本次未发布变更日志的 version_id
   f. commit
4. Redis：
   a. 重新计算 blob 的 checksum，与 version.checksum 不一致则拒绝分发（防 DB blob 损坏）
   b. SET snapshot:{v} = checksum + "\n" + Base64(gzip) EX 30d
   c. current = v（Lua 仅在不存在或新值更大时写入，保证指针单调）
   d. 两行日志：version / wordCount / checksum / costMs
5. 更新 version.status = PUBLISHED
6. finally 释放发布锁（Lua compare-and-delete，只删自己的 token）
7. 返回 {version, wordCount, changeSummary, expectedEffectiveSeconds: 10}
```

失败处理：

- **DB 事务失败**：什么都不变，锁释放，直接报错。
- **Redis 写失败**：版本已是 `PENDING`，更新为 `FAILED`，返回 `5001`；旧 `current` 不变，运行中的节点继续用旧快照。管理员可在版本页点「同步」重试。
- **进程在 DB commit 后崩溃**：版本停留在 `PENDING`，`current` 未变；重启后管理端可见，手工同步或重新发布即可。

### 6.5 回滚与同步

**回滚**（不把版本号改回旧值）：

```
读取目标版本 vX 的 snapshot_blob
→ DB 事务：
   a. DELETE FROM sensitive_word
   b. 将 vX 的词表分批 INSERT 回工作集（1000/批）
   c. 写一条 ROLLBACK 变更日志（word='*'）
   d. 生成新版本 vN+1（kind=ROLLBACK, source_version=vX）
→ 走正常 Redis 分发流程
```

版本号始终单调递增，Redis 指针、节点本地版本、审计日志都不会出现「版本倒退」。

**同步**（`POST /versions/{version}/sync`）：

- 读取指定版本的 `snapshot_blob`，重新写 Redis snapshot + current。
- 用于：发布时 Redis 失败、Redis 被清空、节点长期落后。
- 与发布共用发布锁；拒绝同步比当前 `current` 更旧的版本（防全局指针回退）；失败不修改版本状态，避免把正在服务的 PUBLISHED 版本置为 FAILED。
- 只允许 ADMIN，写审计日志。

**启动对账**：启动时取 Redis `current` 与 DB 最新 `PUBLISHED` 的较大者；Redis 落后时改从 DB 加载并回写，回写 `current` 只接受更大的版本。

### 6.6 业务接入

统一入口：

```java
@Component
public class SensitiveWordGuard {

    private final SensitiveWordManager manager;

    /** 任一字段命中即抛 4003；沿用现有提示，不回显命中词。 */
    public void rejectIfHit(String... texts) {
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            if (manager.contains(text)) {
                throw new BizException(4003, "内容包含敏感词，请修改后重试");
            }
        }
    }
}
```

一期接入点（保持现有行为 + 补齐同类 UGC）：

| 入口 | 字段 | 现状 | 一期动作 |
|---|---|---|---|
| `CommentService.createTopLevel` / `reply` | `content` | 已接入 | 改为 `sensitiveWordGuard.rejectIfHit(content)` |
| `ForumService.createTopic` | `title`、`content` | 已接入 | 同上 |
| `ForumService.createComment` | `content` | 已接入 | 同上 |
| `FeedbackService.create` | `content` | 未接入 | 新增，放在账号/限流校验之后、`save` 之前 |
| `AuthService.updateMe` | `bio` | 未接入 | 新增；`username` 已有正则约束，不重复过滤 |

受信内容（二期或按配置开启）：

| 入口 | 字段 | 建议 |
|---|---|---|
| `ArticleService.createDraft` / `updateArticle` / Markdown 导入 | `title`、`summary`、`contentMarkdown` | 技术文章可能正常讨论敏感词，默认**不拦**；如产品要求，单独配置 `filter-trusted-content=true` 并使用 `REPLACE` 策略 |
| `ProjectService` / `SmallCompanyService` / `ChangelogService` | 标题、简介、正文 | 同上，默认不拦 |
| `SiteConfigService.updateConfigs` | `about_text` 等 | 默认不拦；`about_opensource` 是 JSON，需要逐字段处理，一期不接 |

设计要点：

- **过滤是纯内存、幂等操作**，可以安全重试；放在任何 DB 写之前，命中即抛，不会产生半提交。
- 不要在各 Service 里复制提示语；统一走 `SensitiveWordGuard`。
- 命中日志由 `SensitiveWordManager` 输出：`word + ±12 字上下文`，与现有格式一致；**不打印全文**。`contains` 直接使用 `WordMatcher.firstHit` 返回的首个区间生成日志，不再为了取词二次执行 `filter`。
- 当前 `CommentService` / `ForumService` 的私有 `checkSensitive` 方法删除，改为注入 `SensitiveWordGuard`。

### 6.7 配置

扩展现有 `SensitiveWordProperties`（`@ConfigurationProperties(prefix = "openblog.sensitive-word")`），不新增第二个前缀。

```yaml
openblog:
  sensitive-word:
    enabled: true                    # 总开关；false 时 contains 直接返回 false（应急放行）
    strategy: REJECT                 # REJECT | REPLACE（当前业务只用 REJECT）
    fail-mode: STALE                 # STALE | REJECT_ALL | ALLOW_ALL
    filter-trusted-content: false    # 是否过滤文章/项目/公司等受信内容（一期默认 false）
    seed-from-classpath: false       # 仅首次部署置 true，见 §10.2

    # 匹配引擎
    engine: houbb                    # houbb | trie | ac
    normalization: houbb-compatible  # houbb-compatible | none（none 仅供基准测试）
    shadow-engine: none              # none | trie | ac
    shadow-sample-rate: 0.1          # 0 关闭
    shadow-log-mismatch: true

    refresh:
      poll-interval: 5s
    snapshot:
      ttl-days: 30
    node:
      id: ""                         # 为空时自动生成
      heartbeat-interval: 10s
      ttl: 30s
    publish:
      lock-ttl: 30s
    import:
      max-file-size: 5MB
      max-rows: 50000
    validation:
      max-word-length: 128
```

迁移期保留 `allow-words`：

```yaml
    allow-words:
      - "长期"     # 仅被 SensitiveWordSeedRunner 读取，播种后置为 status=0
```

播种完成后可删除该配置项与字段。改动 `allow-words` 不再影响运行时。

---

## 7. 接口契约

### 7.1 通用约定

- 前缀：`/api/v1/admin/sensitive-words`
- 鉴权：`@PreAuthorize("hasRole('ADMIN')")`，**所有接口（含只读）一期都只允许 ADMIN**。控制台路由对 AUTHOR 可见，但直接访问接口会得到 `4030`。
- 响应：`ApiResponse<T>`，成功 `code=0`。
- 分页：`page` 从 **0** 开始，`size` 默认 20，服务层 clamp 到 `[1, 200]`；返回 `PageResult{items,page,size,total}`。
- 导入：`multipart/form-data`。
- 时间：`Instant`（ISO-8601）。

### 7.2 错误码

沿用项目 4 位数字风格（HTTP 状态由 `GlobalExceptionHandler` 按 `code / 10` 推导）：

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 4000 | 400 | 通用业务拒绝（如未确认发布空词表） |
| 4001 | 400 | 参数校验失败（Bean Validation） |
| 4002 | 400 | 导入文件格式错误 / 编码错误 / 超限 |
| 4003 | 400 | 内容包含敏感词（现有语义） |
| 4030 | 403 | 无权限（Spring Security） |
| 4041 | 404 | 词条 / 版本不存在 |
| 4091 | 409 | 词条已存在、状态冲突 |
| 4092 | 409 | 并发发布 |
| 4290 | 429 | 频率超限（如需） |
| 5001 | 500 | 服务内部错误 / Redis 分发失败 |

### 7.3 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 分页查询词条（`keyword`、`category`、`status`、`page`、`size`） |
| GET | `/categories` | 分类去重列表 |
| POST | `/` | 新增词条 |
| PUT | `/{id}` | 编辑词条 |
| DELETE | `/{id}` | 删除词条 |
| DELETE | `/batch` | 批量删除（body `{ids:[...]}`，最多 500 条） |
| POST | `/import/preview` | 导入预校验（multipart） |
| POST | `/import` | 导入（multipart + `mode` + `confirmReplace`） |
| GET | `/export` | 导出 CSV |
| GET | `/pending` | 待发布差异 |
| POST | `/publish` | 发布新版本 |
| GET | `/versions` | 版本历史（分页） |
| GET | `/versions/{version}/diff` | 版本与当前工作集差异 |
| POST | `/versions/{version}/rollback` | 回滚（生成新版本） |
| POST | `/versions/{version}/sync` | 重新分发指定版本到 Redis |
| GET | `/status` | 当前版本、引擎、待发布、节点状态、影子统计 |
| POST | `/check` | 在线测试 |

### 7.4 关键接口示例

#### 新增词条

```json
POST /api/v1/admin/sensitive-words
{
  "word": "敏感词A",
  "category": "ad",
  "status": 1,
  "remark": "广告类"
}
```

```json
{ "code": 0, "message": "success", "data": { "id": 1001 }, "traceId": "..." }
```

重复：

```json
{ "code": 4091, "message": "词条已存在", "data": null, "traceId": "..." }
```

#### 待发布差异

```json
GET /api/v1/admin/sensitive-words/pending
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "added": 3,
    "removed": 1,
    "updated": 0,
    "items": [
      { "action": "ADD", "word": "敏感词C" },
      { "action": "ADD", "word": "敏感词D" },
      { "action": "ADD", "word": "敏感词E" },
      { "action": "DELETE", "word": "敏感词B" }
    ]
  }
}
```

- `added`：当前 `status=1` 集合中有、最近已发布快照中没有。
- `removed`：快照中有、当前 `status=1` 集合中没有（含被停用 / 删除）。
- `updated`：两边都有但 `updated_at > 最近版本 published_at`（仅元数据变化，不影响快照）。
- `items` 分页返回（默认最多 200 条），计数是全量。

#### 发布

```json
POST /api/v1/admin/sensitive-words/publish
{ "remark": "十月广告专项", "allowEmpty": false }
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "version": 43,
    "wordCount": 64390,
    "changeSummary": { "added": 3, "removed": 1, "updated": 0 },
    "expectedEffectiveSeconds": 10
  }
}
```

#### 状态

```json
GET /api/v1/admin/sensitive-words/status
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "state": "READY",
    "engine": "houbb",
    "publishedVersion": 43,
    "localVersion": 43,
    "wordCount": 64390,
    "lastPublishedAt": "2026-10-20T10:00:00Z",
    "pending": { "added": 0, "removed": 0, "updated": 0, "items": [] },
    "nodes": {
      "total": 1,
      "upToDate": 1,
      "stale": []
    },
    "shadow": {
      "engine": "ac",
      "samples": 12345,
      "mismatches": 0,
      "primaryAvgUs": 42,
      "shadowAvgUs": 35
    }
  }
}
```

`state` 取值：`READY` / `EMPTY` / `UNAVAILABLE`。`localVersion` 是当前处理该请求的实例版本；`shadow` 未开启时为 `null` 或不返回。

#### 在线测试

```json
POST /api/v1/admin/sensitive-words/check
{ "text": "这是一段包含敏感词A的文本" }
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "hit": true,
    "filteredText": "这是一段包含****的文本",
    "hitWords": ["敏感词A"],
    "version": 43,
    "engine": "houbb",
    "costMs": 1
  }
}
```

- 服务端执行，**不下发全量词表**。
- `text` 长度上限 20,000 字，超出返回 `4001`。
- 返回当前实例实际使用的引擎，便于对比排查。

#### 导入预校验

```
POST /api/v1/admin/sensitive-words/import/preview   (multipart/form-data)
file=@words.txt
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 1000,
    "new": 980,
    "exist": 15,
    "invalid": 3,
    "ignored": 2,
    "rows": [
      { "line": 12, "word": "敏感词C", "status": "NEW" },
      { "line": 13, "word": "敏感词A", "status": "EXIST" },
      { "line": 15, "word": "xxxx", "status": "INVALID", "reason": "超过 128 字符" }
    ]
  }
}
```

#### 导入

```
POST /api/v1/admin/sensitive-words/import   (multipart/form-data)
file=@words.txt
mode=APPEND            # APPEND | REPLACE
confirmReplace=false   # REPLACE 时必填 true
```

```json
{
  "code": 0,
  "message": "success",
  "data": { "inserted": 980, "skipped": 15, "invalid": 3, "needPublish": true }
}
```

- 预校验与正式导入**各上传一次文件**，不做临时 fileToken（减少一层存储与清理逻辑）。
- 后端必须重新校验，前端预览只是体验优化。

#### 回滚

```json
POST /api/v1/admin/sensitive-words/versions/40/rollback
{ "remark": "误删回滚" }
```

```json
{
  "code": 0,
  "message": "success",
  "data": { "newVersion": 44, "rolledBackTo": 40, "wordCount": 64300 }
}
```

---

## 8. 前端设计

### 8.1 技术约束

- Vue 3.5 + `<script setup>`，**无 UI 组件库、无 Pinia、无 TypeScript、无 Tailwind**。
- HTTP 走 `vue/src/api/http.js` 的 `request()`：自动解包 `data`，`code !== 0` 时抛错。
- 表格、分页、弹层全部手写，沿用现有控制台类名（`.console-page`、`.console-card`、`.btn`、`.input`、`.field`）与 CSS 变量（含暗色主题）。
- 分页从 0 开始，使用 `PageResult.items`。
- 破坏性操作沿用 `window.confirm()`。

### 8.2 文件与路由

新增：

```
vue/src/api/sensitiveWord.js
vue/src/views/ConsoleSensitiveWordsView.vue
```

修改：

```
vue/src/router/index.js
  import ConsoleSensitiveWordsView from '../views/ConsoleSensitiveWordsView.vue'
  { path: 'sensitive-words', name: 'consoleSensitiveWords', component: ConsoleSensitiveWordsView }

vue/src/layouts/ConsoleLayout.vue
  「系统」分组新增侧边栏入口，仅 ADMIN 可见：
  <router-link v-if="isAdmin" to="/console/sensitive-words" class="console-nav-item"
               active-class="active">敏感词管理</router-link>
```

`ConsoleLayout.vue` 已通过 `fetchMe()` 拿到 `me.role`，新增 `isAdmin` 计算属性即可。

### 8.3 页面信息架构

```
敏感词管理
├── 顶部状态条
│   ├── 当前生效版本 / 词条数 / 发布时间 / 引擎 / 节点状态
│   └── [在线测试] [版本历史] [发布]
├── 未发布变更条（有变更时常驻）
│   ├── 新增 N / 删除 N / 修改 N
│   └── [查看差异]
├── 筛选栏
│   ├── 关键词搜索（300ms 防抖）
│   ├── 分类下拉 / 状态下拉
│   └── [新增] [批量导入] [导出]
├── 词条表格
│   ├── 复选框 / 词条 / 分类 / 状态 / 更新人 / 更新时间 / 操作
│   └── 分页
├── 新增/编辑弹层
├── 批量导入弹层（上传 → 预校验结果 → 模式选择 → 导入）
├── 版本历史弹层/区块（列表、差异、回滚、同步）
└── 在线测试弹层
```

### 8.4 交互细节

- **列表**：服务端分页；`loading / error / empty` 三态齐全；删除二次确认文案明确提示「删除后需点击发布才在线上生效」。
- **新增/编辑**：前端校验 `trim` 非空、长度 ≤ 128、不含换行/控制字符；重复由后端 `4091` 在表单内提示。
- **批量删除**：先展示将删除的数量；最多 500 条/次。
- **导入**：
  - 选择文件后调用 `/import/preview`，展示 `NEW / EXIST / INVALID / IGNORED` 逐行状态；
  - 选择 `APPEND` / `REPLACE`；`REPLACE` 必须二次确认并展示将删除的行数；
  - 导入完成后提示「已导入 N 条，还需发布才生效」。
- **发布**：
  - 无变更时禁用；
  - 弹层展示词条总数、diff 摘要、备注输入、预计生效时间；
  - 发布后每 2s 轮询 `/status`，最多 30s：`生效中 (n/m 节点)` → `已生效`；超时展示落后节点与「同步」按钮。
- **回滚**：确认文案明确「将创建新版本 vN+1，内容与 vX 一致」。
- **在线测试**：多行文本框 → 展示过滤后文本、命中词、版本、引擎、耗时；命中高亮用文本拼接或转义后的 `v-html`，**禁止直接渲染未转义内容**。
- **权限**：非 ADMIN 隐藏新增/编辑/删除/导入/发布/回滚/同步按钮；后端仍强制校验。
- **全量词表不下发前端**：列表是分页的，在线测试走服务端。

### 8.5 API 模块

```js
// vue/src/api/sensitiveWord.js
export function fetchSensitiveWords({ keyword, category, status, page = 0, size = 20 }) { ... }
export function fetchSensitiveWordCategories() { ... }
export function createSensitiveWord(payload) { ... }
export function updateSensitiveWord(id, payload) { ... }
export function deleteSensitiveWord(id) { ... }
export function batchDeleteSensitiveWords(ids) { ... }
export function previewSensitiveWordImport(file) { ... }   // FormData，不设 Content-Type
export function importSensitiveWords(file, mode, confirmReplace) { ... }
export function fetchPendingChanges() { ... }
export function publishSensitiveWords(payload) { ... }
export function fetchSensitiveWordVersions(page, size) { ... }
export function rollbackSensitiveWordVersion(version, remark) { ... }
export function syncSensitiveWordVersion(version) { ... }
export function fetchSensitiveWordStatus() { ... }
export function checkSensitiveText(text) { ... }
```

导出 CSV 复用 `vue/src/api/http.js` 已有的 `downloadWithAuth(path)`（参考 `admin.js` 的文章导出），不要把 token 拼进查询参数。

---

## 9. 测试与性能基准

### 9.1 单元测试

| 测试类 | 覆盖 |
|---|---|
| `SnapshotCodecTest` | 编解码往返、字典序、无尾换行、中文/emoji、checksum 稳定性、gzip 往返 |
| `HoubbWordMatcherTest` | 小词表命中、前缀词、重叠词、空文本、含 `*` 文本、词长 = 文本长；**保留**「长期」误伤回归（在词表层面验证 status=0 后不命中） |
| `TrieWordMatcherTest` / `AcWordMatcherTest` | 与 houbb 相同的语义用例；命中区间、重叠收敛、最长/后缀词、英文词边界、早退 |
| `AcWordMatcherTest` | 同上；额外覆盖 fail 链、output 链、根转移、单字符词、自指防御 |
| `EngineParityTest` | 四种实现（houbb / trie / ac / 朴素 oracle）在同一词表与随机语料上的 `hit` / `filteredText` / `ranges` 一致性 |
| `SensitiveWordManagerTest` | Mock `RedisOps`：版本只前进、快照缺失保留旧版本、checksum 不匹配拒绝、Redis 故障回退 MySQL、并发 filter + reload 无异常、引擎切换 |
| `SensitiveWordAdminServiceTest` | 词条校验（trim、长度、控制字符）、重复 `4091`、分页、diff 计数、导入模式 |
| `SensitiveWordPublishServiceTest` | 空词表拒绝 / `allowEmpty` 放行、版本插入、Redis 失败置 `FAILED`、发布锁冲突 `4092` |
| `SensitiveWordGuardTest` | 任一字段命中即 `4003`、空字段跳过、提示语不回显命中词 |

现有 `SensitiveWordFilterTest` 迁移为 `HoubbWordMatcherTest`；「长期」相关断言改为「词表不含长期时不命中；词表含长期时命中」，不再依赖 jar 内置词库的偶然内容（若继续依赖，需在注释中说明升级词库后可能失效）。

### 9.2 一致性对照（实现正确性的核心）

`EngineParityTest` 用**朴素 oracle** 作为最终裁判：

```java
/** 对每个词、每个出现位置标记区间；复杂度 O(词数 × 文本长)，只用于测试。 */
static FilterResult naive(List<String> words, String text, CharNormalizer normalizer) {
    char[] src = text.toCharArray();
    char[] out = src.clone();
    boolean[] marked = new boolean[src.length];
    List<HitRange> ranges = new ArrayList<>();
    for (String word : words) {
        char[] w = word.chars().mapToObj(c -> normalizer.normalize((char) c))
                .collect(...).toArray(char[]::new);
        for (int i = 0; i + w.length <= src.length; i++) {
            boolean match = true;
            for (int j = 0; j < w.length; j++) {
                if (normalizer.normalize(src[i + j]) != w[j]) { match = false; break; }
            }
            if (match) {
                Arrays.fill(marked, i, i + w.length, true);
                ranges.add(new HitRange(i, i + w.length));
            }
        }
    }
    ...
}
```

对照维度：

| 维度 | 取值 |
|---|---|
| 词表 | 1k / 10k / 64k（真实内置词库） |
| 文本 | 空、单字符、无命中、单命中、重叠命中、长文本（20k）、含 emoji / 全角 / 大小写变体 |
| 随机语料 | 固定种子，生成 10k 组「词表子集 + 嵌入词条的文本」 |
| 比较项 | `hit`、`filteredText` 逐字符相等；`ranges` / `hitWords` 转 `Set` 后相等 |
| 前提 | 使用同一个 `CharNormalizer`；houbb 的 `endIndex` 开闭语义先由单测确认 |

任何不一致都必须先修到零，才能进入性能对比。

### 9.3 并发测试

- 多线程持续 `manager.contains()`，同时后台线程反复 `reload()`。
- 断言：任何一次调用都看到完整快照；无 NPE、无部分结果、无死锁。
- `AtomicReference` 替换，读路径无锁。

### 9.4 性能基准（本次设计的重点）

#### 9.4.1 目标

回答三个问题：

1. **构建**：Trie / AC / houbb 在 1k / 10k / 64k 词下各花多少时间、占多少内存？
2. **contains**（线上热路径）：不同文本长度、命中率下谁更快？
3. **filter**（在线测试 / 替换）：重叠词、长文本、高命中下差距多大？

#### 9.4.2 基准测试类

`SensitiveWordBenchmarkTest` 用 JUnit 5 + 系统属性开关，**默认不运行、不进入 CI**：

```java
@EnabledIfSystemProperty(named = "openblog.bench", matches = "true")
class SensitiveWordBenchmarkTest {

    @Test
    void compareEngines() {
        List<String> all = BenchmarkCorpus.loadHoubbDictionary();   // 64,418 行
        int[] wordCounts = {1_000, 10_000, all.size()};
        List<BenchmarkCorpus.Case> cases = BenchmarkCorpus.cases(); // 短/中/长 × 命中/不命中

        List<Row> rows = new ArrayList<>();
        for (int n : wordCounts) {
            List<String> words = BenchmarkCorpus.sample(all, n, 42L);
            for (Engine engine : List.of(HOUBB, TRIE, AC)) {
                for (Normalization norm : List.of(HOUBB_COMPATIBLE, NONE)) {
                    WordMatcher matcher = build(engine, words, norm);
                    rows.add(measureBuild(engine, n, norm, () -> build(engine, words, norm)));
                    for (Case c : cases) {
                        rows.add(measure(matcher, c, /* iterations */ 10_000));
                    }
                }
            }
        }
        System.out.println(MarkdownTable.render(rows));
    }
}
```

运行命令：

```bash
mvn -pl OpenBlog-business -am test \
  -Dtest=SensitiveWordBenchmarkTest \
  -Dopenblog.bench=true \
  -DfailIfNoTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false
```

可选系统属性（按需扩展）：

```
-Dopenblog.bench.engines=houbb,trie,ac
-Dopenblog.bench.words=1000,10000,full
-Dopenblog.bench.cases=short,medium,long
-Dopenblog.bench.normalization=houbb-compatible,none
```

#### 9.4.3 测量方法

- **预热**：每个 case 先跑 5,000 次，丢弃结果，等待 JIT 稳定。
- **测量**：每轮 10,000 次，跑 3–5 轮；记录每次耗时到 `long[]`，排序后取 p50 / p99 / mean；吞吐 = 总次数 / 总耗时。
- **防死代码消除**：把 `filteredText().length()` 与 `hit ? 1 : 0` 累加到 `static volatile long sink`，最后打印。
- **构建**：每个引擎/词表规模构建 5 次，报告 min / mean / max；构建与查询分开统计。
- **内存**：
  - 记录节点数（Trie / AC 暴露 `nodeCount()`；houbb 记录词数）；
  - 用 `Runtime.totalMemory() - freeMemory()` 在 `System.gc()` 后做粗略估算；
  - 如需精确值，再引入 test-scope `org.openjdk.jol:jol-core`，对 root 做 `GraphLayout.parseInstance(root).totalSize()`（`root` 需包级可见）。
- **公平性**：
  - 同一份 `List<String>`、同一个 `CharNormalizer`、同一份文本；
  - 引擎顺序轮换，避免某个引擎总是享受 JIT 峰值；
  - 生产对比以 `normalization=houbb-compatible` 为准；`none` 仅用于隔离算法差距；
  - 严格结论建议每个引擎单独 JVM 跑一次（避免 JIT / GC 相互影响），JUnit 版本给的是快速结论。

#### 9.4.4 基准矩阵

| 维度 | 取值 |
|---|---|
| engine | `houbb` / `trie` / `ac` |
| 词表规模 | 1,000 / 10,000 / 64,418（真实内置词库） |
| normalization | `houbb-compatible` / `none` |
| 文本 | 50 字无命中 / 50 字 1 命中 / 500 字约 1% 命中 / 20,000 字无命中 / 20,000 字约 1% 命中 |
| API | `contains` / `filter` |
| 指标 | build ms、p50、p99、mean、ops/s、nodeCount、粗略堆占用 |

#### 9.4.5 结果记录

实测后把表格回填到这里（当前为**预期假设**，不是实测结果）：

| 假设 | 说明 |
|---|---|
| Trie 构建最快、节点最少 | 只做插入，无 fail / output 引用 |
| AC 构建略慢、内存略高 | 多一次 BFS，节点多两个引用 |
| 短文本 / 无命中：Trie 与 AC 接近 | 公共前缀短，Trie 回退次数少；AC 的 fail 维护也有成本 |
| 长文本 / 高命中：AC 明显占优 | Trie 最坏 O(n × L)，AC O(n + 命中数) |
| `contains` 差距小于 `filter` | `contains` 两边都能提前返回；`filter` 必须做全量扫描与结果构造 |
| houbb 的 `filter` 可能偏慢 | 需要构造 `IWordResult` 列表；`contains` 是优化过的快路径 |

### 9.5 集成 / 手工验证

项目没有 Testcontainers，集成验证以「Mock Redis + 真实 MySQL（本地）」或手工冒烟为主：

1. 执行 `sql/sensitive-word.sql`。
2. 开启 `seed-from-classpath=true` 启动 business，确认 v1 生成、Redis 有 `current` / `snapshot`。
3. 新增一个词 → 发布 → 10s 内 `/status` 的 `localVersion` 追上 `publishedVersion`。
4. 评论 / 论坛提交命中词 → `4003`；停用该词 → 发布 → 不再命中。
5. 停掉 Redis，重启 business → 能从 MySQL 兜底加载；业务过滤不中断。
6. 故意写入损坏快照 → 节点保留旧版本并打 ERROR。
7. 回滚 → 新版本号递增，内容等于目标版本。
8. 切换 `engine=ac` 重启 → 行为与 houbb 一致；`/check` 返回 `engine=ac`。

### 9.6 构建验证

```bash
mvn -pl OpenBlog-business -am test \
  -Dtest='SensitiveWord*Test' \
  -DfailIfNoTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false

mvn -pl OpenBlog-business -am package

cd vue && npm run build
```

### 9.7 引擎切换门槛

`engine` 从 `houbb` 切到 `trie` / `ac` 前，必须同时满足：

1. `EngineParityTest` 在 64k 真实词库 + 随机语料上零不一致；
2. 基准测试在目标负载（评论 50–2,000 字、论坛 ≤ 20,000 字）上显示明确收益，或至少不退化；
3. 影子模式跑 1–2 天，`mismatches=0`；
4. 先给单台实例切 `engine`，观察 `/status` 与日志，再全量切换；
5. 保留 `engine=houbb` 作为一键回退配置。

---

## 10. 上线、运维与安全

### 10.1 部署影响

- **只改 business + 前端**。
- `OpenBlog-message`、`OpenBlog-gateway` 不改：Dubbo 接口、JWT 密钥、网关路由都不受影响。
- **不需要三服务同步部署**；`/api/**` 的通配路由已经覆盖 `/api/v1/admin/sensitive-words`，网关无需改路由。
- gateway 的限流规则默认不覆盖 admin 路径；ADMIN 已登录且操作低频，一期不新增规则。

### 10.2 首次上线步骤

1. 执行 `sql/sensitive-word.sql` 建表。
2. 部署 business，设置 `openblog.sensitive-word.seed-from-classpath=true`（或环境变量映射）。
3. `SensitiveWordSeedRunner`（`ApplicationRunner`）在 `ApplicationReadyEvent` 之前执行：
   - 条件：开关为 true **且** `sensitive_word` 为空；
   - 读取 classpath 的 `sensitive_word_dict.txt`（houbb data jar，64,418 行），1000 条/批 `INSERT IGNORE`；
   - 将 `openblog.sensitive-word.allow-words`（当前为「长期」）更新为 `status=0`；
   - 生成并发布 v1（快照 + 版本记录 + 尽力写 Redis）；
   - 整个过程获取发布锁，幂等；多实例同时启动时只有一个执行。
4. business 启动完成后，`SensitiveWordManager` 从 Redis（或 MySQL 兜底）加载 v1，评论/论坛行为与升级前一致。
5. 部署前端，ADMIN 进入「敏感词管理」验证列表、在线测试、发布。
6. 将 `seed-from-classpath` 改回 false（默认值），可删除 `allow-words` 配置。
7. 引擎保持 `houbb`；Trie / AC 仅在测试环境通过配置启用，按 §9.7 验证后再灰度。

> 先播种再发布，而不是「部署后等运营手工建词库」：否则从部署到首次发布之间过滤是空的，属于安全回退窗口。

### 10.3 回滚预案

| 场景 | 操作 |
|---|---|
| 词库内容事故 | 后台回滚到目标版本（生成新版本），或 `sync` 重新分发 |
| 过滤误伤严重 | 将该词 `status=0` 并发布；紧急情况下把 `enabled` 置 false 重启（应急放行） |
| 新引擎行为异常 | 把 `engine` 切回 `houbb` 重启；无需回滚代码 |
| 新代码异常 | 回滚 business jar；旧代码不读取新表，不影响启动 |
| Redis 故障 | 业务继续用内存快照；新实例从 MySQL 兜底；恢复后自动轮询到最新 |
| 发布后节点不追 | `POST /versions/{v}/sync`，或等待轮询；超过 15s 告警 |

### 10.4 安全清单

- [ ] 所有管理接口 `hasRole('ADMIN')`，前端按钮级隐藏 + 后端强制校验。
- [ ] 词表不全量下发前端；在线测试服务端执行。
- [ ] 命中日志只记录命中词与局部上下文，不记录全文。
- [ ] 影子模式日志只记录长度 / 命中数 / traceId，不记录原文。
- [ ] 导入文件限制 5 MB / 50,000 行；UTF-8、去 BOM、拒绝二进制；后端重新校验。
- [ ] 导出 CSV 防公式注入：`= + - @` 开头的单元格前置 `'`。
- [ ] 前端渲染命中文本时转义，防 XSS。
- [ ] 变更日志只增不改；发布 / 回滚 / 导入记录操作人。
- [ ] 发布、回滚、REPLACE 导入二次确认；空词表发布需 `allowEmpty=true` 并记录审计。

### 10.5 可观测性

项目未引入 Micrometer，一期用日志 + `/status`：

| 事件 | 日志 |
|---|---|
| 启动加载成功 | `version / wordCount / checksum / engine / source=redis|db / costMs` |
| 启动加载失败 | `source / error / failMode / 当前状态` |
| 轮询 / 刷新失败 | `version / error / keep version` |
| 发布 | `version / wordCount / changeSummary / operator / costMs` |
| 同步 / 回滚 | `version / sourceVersion / operator` |
| 命中 | `words / ±12 字上下文`（沿用现有格式，WARN） |
| 影子不一致 | `traceId / textLength / hitCount / primaryEngine / shadowEngine`（不记录原文） |

`/status` 需要包含：`state`、`engine`、`publishedVersion`、`localVersion`、`wordCount`、`pending`、`nodes`、`shadow`。

---

## 11. 实施清单

### 11.1 后端

- [ ] `sql/sensitive-word.sql`：三张表
- [ ] `RedisKeys` 新增敏感词 Key 构建方法
- [ ] `SensitiveWordProperties` 扩展配置（engine / normalization / shadow / refresh / node / publish / import）；`allow-words` 标记为迁移期字段
- [ ] 实体 + Mapper（MyBatis-Plus）：`SensitiveWord`、`SensitiveWordVersion`、`SensitiveWordChangeLog`
- [ ] `SnapshotCodec`：编码、解码、gzip、SHA-256、Redis value 解析
- [ ] `WordMatcher` / `FilterResult` / `HitRange`
- [ ] `CharNormalizer`（houbb-compatible / none）
- [ ] `HoubbWordMatcher`
- [ ] `TrieWordMatcher`
- [ ] `AcWordMatcher`
- [ ] `WordMatcherFactory`
- [ ] `SensitiveWordSnapshot`
- [ ] `SensitiveWordManager`：启动加载、MySQL 兜底、轮询、后台构建、原子替换、心跳、命中日志、可选影子对比
- [ ] `SensitiveWordGuard`
- [ ] `SensitiveWordAdminService`：CRUD、分页、分类、diff、导入预校验、导入、导出、在线测试
- [ ] `SensitiveWordPublishService`：发布、回滚、同步、发布锁、空词表保护
- [ ] `SensitiveWordSeedRunner`：一次性播种 v1
- [ ] `SensitiveWordAdminController`：§7.3 全部接口 + `@PreAuthorize("hasRole('ADMIN')")`
- [ ] `@AuditLog` 按项目约定打在 **Service 方法**上（ADD / UPDATE / DELETE / IMPORT / PUBLISH / ROLLBACK / SYNC），与变更日志互补
- [ ] 改造 `CommentService`、`ForumService`：删除 `checkSensitive`，注入 `SensitiveWordGuard`
- [ ] `FeedbackService.create`、`AuthService.updateMe`（bio）接入
- [ ] 删除 `forum/filter/SensitiveWordFilter.java`，迁移测试
- [ ] `application.yaml` 配置块
- [ ] 单元 / 并发 / 一致性测试
- [ ] 基准测试类 + 语料工具
- [ ] 跑基准并把结果回填 §9.4.5

### 11.2 前端

- [ ] `vue/src/api/sensitiveWord.js`
- [ ] `vue/src/views/ConsoleSensitiveWordsView.vue`
- [ ] `vue/src/router/index.js` 注册路由
- [ ] `vue/src/layouts/ConsoleLayout.vue` 新增 ADMIN 可见入口
- [ ] 列表 / 筛选 / 分页 / 三态
- [ ] 新增 / 编辑弹层 + 校验 + 重复提示
- [ ] 删除 / 批量删除二次确认
- [ ] 导入（上传、预校验、模式、结果）
- [ ] 待发布条 + 差异查看
- [ ] 发布弹层 + 状态轮询
- [ ] 版本历史 + 回滚 + 同步
- [ ] 在线测试（过滤结果、命中词、版本、引擎、耗时）
- [ ] ADMIN 按钮级隐藏
- [ ] 暗色主题适配

### 11.3 运维

- [ ] 首次播种与 v1 发布
- [ ] `/status` 状态页验证
- [ ] 日志关键字与告警（构建失败、节点落后 > 15s、空词表、Redis 读失败、影子不一致）
- [ ] 词库定期导出备份（版本表本身即快照）
- [ ] 容量与构建耗时实测并回填本文档
- [ ] 引擎切换演练（houbb → trie/ac → houbb）

---

## 12. 设计权衡

### 12.1 为什么三种引擎并存

- **houbb 是生产基线**：已经在线，行为、归一化、边界都经过验证；直接替换成自研引擎属于无收益冒险。
- **Trie / AC 是候选与对照组**：两套实现共用词表、归一化、接口和结果类型，算法差异可以被单独测量；没有它们，就无法回答「要不要换、换了快多少」。
- **接口隔离**：`WordMatcher` 让引擎选择只发生在 `WordMatcherFactory`，Manager、快照、发布、管理端都不关心具体实现。
- **切换成本低**：`engine` 是配置项，异常时一键切回 houbb；`normalization=none` 只用于基准，不用于生产。

### 12.2 为什么 Trie 没有 fail 指针也能命中「从中间开始的词」

- Trie 的匹配是**逐起点从根重走**：文本位置 i 作为起点，沿树走到不能再走；然后再以 i+1 为起点。因此不会漏掉任何起点的匹配。
- 代价是最坏 O(n × L)：当文本很长、词表公共前缀也很长、且几乎不命中时，重复走前缀的开销明显。
- AC 用 fail 指针把「重走」变成状态转移，均摊 O(n)；代价是构建多一次 BFS、节点多两个引用。这正是基准测试要量化的差异。

### 12.3 为什么增删词要全量重建

- houbb 的 DFA 在 `init()` 时构建；Trie / AC 的 fail 指针是全局性质，增量维护的影响范围不可控。
- 重建是低频离线操作（发布时一次），用写路径成本换取读路径简单与正确，划算。
- 新快照构建期间旧快照继续服务；构建完成才 `AtomicReference.set`，读路径无锁。

### 12.4 为什么用版本号 + 快照，而不是 Redis `SET` / `SMEMBERS`

- 单词表读取成本随词库线性增长；版本号是 O(1)。
- 指针切换天然原子，不存在 `DEL` + `SADD` 的空窗。
- 版本即快照，可回滚、可追溯。
- 快照是纯文本，任何引擎、任何语言都能构建自己的匹配器。

### 12.5 为什么 Redis 可丢

- MySQL 的 `sensitive_word_version` 是权威源，`snapshot_blob` 是可解压的完整快照。
- 启动时 Redis 不可用 → 从 MySQL 最新 `PUBLISHED` 版本恢复；Redis 恢复后轮询会自然切到最新。
- 这比参考文档的「本地磁盘兜底」更符合 OpenBlog 的部署形态：容器内磁盘没有持久卷，MySQL 反而更可靠。

### 12.6 为什么不做 pub/sub

- 5s 轮询的最坏生效时间是 5s + 构建时间，满足博客场景；参考文档对外 SLA 示例也是 10s。
- 项目当前没有任何 Redis pub/sub 代码；为一个低频管理操作引入监听容器、断线重连、消息丢失兜底不划算。
- 保留升级路径：轮询始终是兜底，pub/sub 可在二期作为「低延迟触发器」叠加，不改变正确性。

### 12.7 为什么一期不拦文章等受信内容

- 作者 / 管理员是受信主体；技术博客可能正常讨论敏感词、安全、爬虫等话题，用通用 6.4 万词库硬拦会造成大量误伤。
- 参考文档也把「命中后策略」设计为按业务线可配置；本项目的落地方式是「UGC 默认拦，受信内容默认放行，需要时用 `filter-trusted-content` 开启」。
- 如果产品明确要求全站拦截，把 `filter-trusted-content` 置 true，并在文章入口使用 `REPLACE` 或人工审核策略（需要额外实现，见 §13 待确认）。

---

## 13. 待确认

以下四项建议在开工前确认；文档默认值已按推荐项写死，可直接按现状实现：

1. **生产引擎选择**：默认 `houbb`，Trie / AC 先作为可配置候选。是否接受「先实现、后基准、再切换」的节奏？如果希望直接以 AC 上线，需要先完成 §9.7 的对照与影子验证。
2. **归一化范围**：自定义引擎默认复用 houbb 的 `IWordFormat` 链（大小写 / 宽度 / 数字样式 / 中文样式 / 英文样式），保证与现状一致。如果希望基准更纯粹，可只用 `normalization=none` 跑算法对比，但该配置**不用于生产**。
3. **受信内容是否过滤**：默认只拦评论 / 论坛 / 反馈 / bio，不拦文章、项目、公司、更新日志。若产品要求全站拦截，需补充文章入口与「技术文章误伤」的处理策略（例如按分类使用不同词表，或改用审核队列）。
4. **命中策略**：默认 `REJECT`（`4003`）。文章类内容如果开启过滤，建议 `REPLACE`（静默替换）还是 `REJECT`（作者改稿）？两者的用户提示与前端交互不同。
