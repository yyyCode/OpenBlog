# 敏感词过滤系统设计文档（前后端完整方案）

> 目标：给出一套可直接移植到其他项目的敏感词过滤方案，覆盖数据模型、后端运行时、配置发布、管理后台前端、接口契约、测试与运维。
>
> 示例技术栈为 Java 17 + Spring Boot + MySQL + Redis + React/TypeScript，其他语言/框架可按相同结构实现。
>
> 设计来源：对 austin 原实现（每条消息 `SMEMBERS` 拉全量词表 + 现场 build Trie）的性能优化，改为「版本化快照 + 本地不可变 AC 自动机 + 原子替换」。

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [总体架构](#2-总体架构)
3. [数据模型](#3-数据模型)
4. [后端设计](#4-后端设计)
5. [前端设计](#5-前端设计)
6. [接口契约](#6-接口契约)
7. [测试方案](#7-测试方案)
8. [上线、运维与安全](#8-上线运维与安全)
9. [实施清单](#9-实施清单)
10. [设计权衡速查](#10-设计权衡速查)

---

## 1. 背景与目标

### 1.1 问题

原实现的两个性能瓶颈：

| 问题 | 代价 |
|---|---|
| 每条消息执行一次 `SMEMBERS SENS_WORDS_DICT` | 网络传输 + 反序列化 **O(词库大小)**，词库越大越慢 |
| 每条消息执行一次 `buildTrie()` | CPU **O(词库总字符数)**，且产生大量短命对象，增加 GC 压力 |
| 匹配采用「每个位置从根回溯」 | 最坏 **O(n × L)**，n 为文本长度，L 为最长词长 |

此外原实现存在一个正确性窗口：发布时 `DEL` + `SADD` 非原子，中间读到的空集合会直接跳过过滤。

### 1.2 目标

- 过滤阶段**零网络调用**、零锁，单次匹配 O(n + 命中数)。
- 词表变更后**秒级到分钟级**在全部实例生效（SLA 可配置）。
- 多实例最终一致，**不允许因为刷新失败而停止过滤**。
- 词表可运营：增删改、批量导入、版本历史、回滚、在线测试、审计。
- 内存与构建耗时可估算、可监控。

### 1.3 非目标

- 不做语义识别（反讽、上下文）。
- 不做变体识别（拼音、谐音、拆字、插符号）——列为后续扩展，见 §10。
- 不做多语言分词，AC 是字符级匹配。

---

## 2. 总体架构

### 2.1 分层

```
┌──────────────────────────────────────────────────────────────┐
│ 管理后台 / 运营                                                 │
│  词库 CRUD · 批量导入 · 版本发布 · 回滚 · 在线测试                 │
└──────────────────────────┬───────────────────────────────────┘
                           │ HTTP
┌──────────────────────────▼───────────────────────────────────┐
│ 管理服务                                                        │
│  权限校验 · 参数校验 · 变更日志 · 生成快照 · 版本号                 │
└───────────────┬──────────────────────────────┬────────────────┘
                │ 写                            │ 读（仅管理服务）
┌───────────────▼──────────────┐   ┌───────────▼────────────────┐
│ MySQL（权威源）                │   │ Redis（分发层）              │
│ sensitive_word               │   │ {sens:dict}:current         │
│ sensitive_word_version       │   │ {sens:dict}:snapshot:{ver}  │
│ sensitive_word_change_log    │   │ {sens:dict}:channel (pub/sub)│
└──────────────────────────────┘   │ {sens:dict}:node:{id}        │
                                   └───────────┬────────────────┘
                                               │ 版本轮询 / 订阅
┌──────────────────────────────────────────────▼───────────────┐
│ 业务实例（消息服务 / API 网关 …）                                │
│  SensitiveWordManager                                        │
│    ├─ AtomicReference<Snapshot>   ← 读路径无锁                 │
│    ├─ 轮询线程 + 订阅回调          ← 发现新版本                  │
│    └─ 单线程构建器                 ← 后台重建 + 原子替换           │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 核心原则

1. **读写分离**：消息路径只读内存；网络访问只发生在启动和版本变更时。
2. **快照不可变**：构建完成后不再修改，替换引用即可；读路径无需加锁。
3. **发布原子化**：先写快照，再切指针；指针永远指向完整快照。
4. **失败保留旧版本**：刷新失败、快照损坏、Redis 不可用，都继续使用旧自动机。
5. **版本只增不减**：回滚 = 用旧内容发布一个**新版本号**，避免缓存与日志歧义。

### 2.3 与原实现对比

| 维度 | 原实现 | 新设计 |
|---|---|---|
| 词表读取 | 每条消息 `SMEMBERS` | 节点每 5s 读一个版本号，版本变化才拉快照 |
| 数据结构 | 每条消息 build Trie | 启动/变更时 build 一次 AC 自动机 |
| 匹配 | 每个起点回溯，O(n·L) | 单遍扫描，O(n + 命中数) |
| 更新 | 定时 `DEL`+`SADD` | 版本化快照 + 原子指针 |
| 更新窗口 | 读到空集合 → 跳过过滤 | 指针原子切换，无空窗 |
| 失败行为 | 空词表 = 不过滤 | 保留旧版本 + 告警 |
| 可运维性 | 文件 + 无界面 | 后台 CRUD / 导入 / 版本 / 回滚 / 测试 |

---

## 3. 数据模型

### 3.1 MySQL

#### 3.1.1 词条表

```sql
CREATE TABLE `sensitive_word` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`        VARCHAR(128)    NOT NULL COMMENT '词条，已 trim，禁止换行/控制字符',
  `category`    VARCHAR(64)     NOT NULL DEFAULT 'default' COMMENT '分类，供运营筛选',
  `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  `remark`      VARCHAR(255)    DEFAULT NULL,
  `created_by`  VARCHAR(64)     NOT NULL,
  `updated_by`  VARCHAR(64)     NOT NULL,
  `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_category_status` (`category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='敏感词工作集（草稿 + 当前内容）';
```

说明：

- 采用**物理删除**，删除动作写入 `sensitive_word_change_log`；重复添加同一个词直接重新插入即可，避免软删除与唯一索引的冲突。
- `word` 的规范化规则（是否大小写不敏感、是否全半角归一）必须**与运行时归一化保持一致**，否则会出现「后台判定重复、运行时匹配不到」。
- 若产品需要按分类使用不同策略（替换/拦截），快照需按 category 分组，见 §4.5.6。

#### 3.1.2 版本表

```sql
CREATE TABLE `sensitive_word_version` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增 ID 即版本号',
  `word_count`     INT             NOT NULL,
  `checksum`       CHAR(64)        NOT NULL COMMENT '快照明文 SHA-256',
  `snapshot_blob`  LONGBLOB        NOT NULL COMMENT 'gzip 压缩的换行分隔词表，用于回滚与灾备',
  `change_summary` JSON            DEFAULT NULL COMMENT '{"added":3,"removed":1,"updated":0}',
  `status`         VARCHAR(16)     NOT NULL COMMENT 'PUBLISHED / ROLLED_BACK',
  `remark`         VARCHAR(255)    DEFAULT NULL COMMENT '发布备注',
  `created_by`     VARCHAR(64)     NOT NULL,
  `published_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变版本快照';
```

版本号使用自增 ID，天然单调递增。**永不删除**版本记录（可按保留策略归档）。

#### 3.1.3 变更日志表

```sql
CREATE TABLE `sensitive_word_change_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word`        VARCHAR(128)    NOT NULL,
  `action`      VARCHAR(16)     NOT NULL COMMENT 'ADD / UPDATE / DELETE / IMPORT / ROLLBACK',
  `before_json` JSON            DEFAULT NULL,
  `after_json`  JSON            DEFAULT NULL,
  `version_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '发布后回填',
  `operator`    VARCHAR(64)     NOT NULL,
  `operated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_word_time` (`word`, `operated_at`),
  KEY `idx_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计，只增不改';
```

### 3.2 Redis Key

| Key | 类型 | 内容 | TTL | 写者 | 读者 |
|---|---|---|---|---|---|
| `{sens:dict}:current` | String | 当前版本号 | 无 | 发布服务 | 所有业务节点 |
| `{sens:dict}:snapshot:{ver}` | String | gzip 词表（换行分隔 UTF-8） | 30 天 | 发布服务 | 业务节点 |
| `{sens:dict}:channel` | Pub/Sub | 变更通知 `{version}` | — | 发布服务 | 业务节点 |
| `{sens:dict}:node:{instanceId}` | String | 节点当前版本号 | 30s（心跳续期） | 业务节点 | 管理服务 |
| `{sens:dict}:publish:lock` | String | 发布互斥锁 | 30s | 发布服务 | 发布服务 |

**必须使用 hash tag `{sens:dict}`**，保证 Redis Cluster 下 `current` 与 `snapshot` 落在同一 slot，Lua 脚本才能原子执行。

> 不要再用 `SET` + `SMEMBERS` 存词表：读取成本随词库线性增长，且无法原子切换。

### 3.3 快照格式

```
敏感词A\n
敏感词B\n
敏感词C
```

- UTF-8，`\n` 分隔，末尾可无换行。
- gzip 压缩后存 Redis / DB。
- `checksum = SHA-256(解压后的字节)`，节点加载后校验。
- 词条中不允许出现 `\n` 和控制字符（入库校验）。
- 10 万词、平均 8 字符：明文约 1.6–2.4 MB，gzip 后通常 300–800 KB。

---

## 4. 后端设计

### 4.1 模块划分

```
sensitive-word/
├── api/            # Controller、DTO、参数校验
├── service/        # 词条 CRUD、导入、发布、回滚
├── repository/     # Mapper / JPA
├── snapshot/       # 快照生成、压缩、校验和、Redis 写入
├── runtime/        # 自动机、Manager、版本轮询、节点心跳
└── integration/    # 业务管线接入（Filter 接口、策略）
```

### 4.2 发布模型：草稿 + 显式发布

**不要让每次增删改都立即重建自动机。** 运营编辑的是「工作集」，只有点击「发布」才生成版本：

```
编辑词条（草稿）→ 待发布变更 N 项 → 点击发布 → 生成版本 vN+1 → 全节点生效
```

好处：

- 批量编辑只重建一次，避免反复构建与广播。
- 发布前可预览 diff、填写备注。
- 出错可回滚到任意历史版本。

若产品要求「保存即生效」，可实现自动发布（保存后 debounce 2s 触发），但版本语义要保留。

### 4.3 发布流程

```
1. 获取发布锁（Redis SET NX PX 30000），防止并发发布
2. DB 事务：
   a. 查询 status=1 的全部词条
   b. 生成快照（排序 → 拼接 → gzip）+ checksum
   c. 插入 sensitive_word_version（拿到自增版本号 v）
   d. 回填本次变更日志的 version_id
   e. commit
3. Redis（Lua 原子执行）：
   a. SET {sens:dict}:snapshot:{v} <blob> EX 2592000
   b. SET {sens:dict}:current v
   c. PUBLISH {sens:dict}:channel v
4. 释放发布锁
5. 返回版本号、词条数、预计生效时间
```

顺序约束：**先写快照，后切指针**。任何时刻 `current` 指向的版本都必须已存在。

Lua 脚本：

```lua
-- KEYS[1] = {sens:dict}:current
-- KEYS[2] = {sens:dict}:snapshot:{version}
-- ARGV[1] = version
-- ARGV[2] = blob
-- ARGV[3] = channel
redis.call('SET', KEYS[2], ARGV[2], 'EX', 2592000)
redis.call('SET', KEYS[1], ARGV[1])
redis.call('PUBLISH', ARGV[3], ARGV[1])
return 1
```

发布前置校验：

- 词条数 > 0（空词表需显式开关 + 二次确认，防止误操作导致过滤失效）。
- 单个词长度 ≤ 128，无换行/控制字符。
- 快照 checksum 与 DB 记录一致。
- 发布人具备 `sensitive:word:publish` 权限。

### 4.4 回滚

回滚**不是**把版本号改回旧值，而是：

```
读取目标版本 vX 的 snapshot_blob
→ DB 事务：用该快照覆盖 sensitive_word 工作集
→ 生成新版本 vN+1（内容 = vX 内容）
→ 正常发布流程
```

这样版本号始终单调递增，Redis 指针、节点本地版本、审计日志都不会出现「版本倒退」的歧义。

### 4.5 运行时：AC 自动机

#### 4.5.1 结构

```java
final class Node {
    Map<Character, Node> next = new HashMap<>(4);
    Node fail;        // 失配指针：最长真后缀对应的节点
    Node output;      // fail 链上最近的终结节点（加速命中枚举）
    boolean terminal; // 是否是某个词的结尾
    int length;       // 该词长度，仅在 terminal 节点有效
}
```

`Snapshot` 是不可变对象：

```java
public record Snapshot(long version, SensitiveWordAutomaton automaton,
                       int wordCount, String checksum) {
    public static Snapshot empty() { return new Snapshot(0, SensitiveWordAutomaton.empty(), 0, ""); }
}
```

运行时持有：

```java
private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.empty());
```

#### 4.5.2 构建（插入 + BFS 建 fail 指针）

```java
public static SensitiveWordAutomaton build(Collection<String> words) {
    Node root = new Node();
    for (String word : words) {
        if (word == null || word.isEmpty()) continue;
        Node cur = root;
        for (int i = 0; i < word.length(); i++) {
            cur = cur.next.computeIfAbsent(word.charAt(i), k -> new Node());
        }
        cur.terminal = true;
        cur.length = word.length();
    }

    Queue<Node> queue = new ArrayDeque<>();
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
            if (child.fail == child) child.fail = root; // 防御
            child.output = child.fail.terminal ? child.fail : child.fail.output;
            queue.add(child);
        }
    }
    return new SensitiveWordAutomaton(root);
}
```

#### 4.5.3 匹配与替换

语义：标记**所有命中区间**，替换为等长 `*`；同一结束位置有多个词时取最长（更短的都是它的后缀，区间已被覆盖）。

```java
public FilterResult filter(String text) {
    if (text == null || text.isEmpty()) return FilterResult.empty();
    char[] src = text.toCharArray();
    char[] out = src.clone();
    Set<String> hits = new LinkedHashSet<>();

    Node node = root;
    for (int i = 0; i < src.length; i++) {
        char c = src[i];
        while (node != root && !node.next.containsKey(c)) {
            node = node.fail;
        }
        Node next = node.next.get(c);
        node = (next == null) ? root : next;

        if (node.terminal) {
            int start = i - node.length + 1;
            Arrays.fill(out, start, i + 1, '*');
            hits.add(new String(src, start, node.length));
        } else {
            for (Node o = node.output; o != null; o = o.output) {
                int start = i - o.length + 1;
                Arrays.fill(out, start, i + 1, '*');
                hits.add(new String(src, start, o.length));
            }
        }
    }
    return new FilterResult(!hits.isEmpty(), new String(out), List.copyOf(hits));
}
```

> `out` 初始为 `src` 的副本，扫描始终读原始字符，避免「替换产生的 `*` 影响后续匹配」。

`contains` 可提前返回，不必构造结果字符串，适合只需要判定的场景。

#### 4.5.4 复杂度

| 操作 | 复杂度 |
|---|---|
| 构建 | O(总字符数) |
| 匹配 | O(n + 命中数)（fail 链遍历均摊 O(n)） |
| 空间 | O(节点数 × 单节点开销) |

节点数 ≈ 词库总字符数 − 公共前缀重叠。中文词库公共前缀少，按上限估算即可。

#### 4.5.5 内存优化

`HashMap<Character, Node>` 每节点开销大（对象头 + 表 + Entry）。规模上来后按顺序升级：

1. **简单版**：`HashMap`（≤ 5 万词可接受），实现快、易调试。
2. **紧凑版**：全局 `Map<Long, Integer>`，key = `(nodeId << 16) | char`，value = childId；或每节点 `int[]` 边表 + 兄弟链。
3. **生产版**：双数组 Trie（Double-Array Trie）承载转移，fail 指针单独数组。

选型前先用 heap dump / JOL 测真实占用，不要凭感觉。

内存粗估公式：

```
堆占用 ≈ 节点数 × (对象头 + 转移表开销 + fail/output 引用)
节点数 ≈ Σ 词长 − 公共前缀节省
```

在文档里记录实测值：`10k 词 ≈ ? MB，100k 词 ≈ ? MB，构建耗时 ≈ ? ms`。

#### 4.5.6 分片（仅在有实测必要时）

触发条件：构建耗时 > 可接受阈值，或内存超预算。

方案：

- 按 `category` 分片：不同分类独立自动机，可按需组合过滤。
- 按首字符分片：`Map<Character, Automaton>`；扫描到位置 i 时只用 `text[i]` 对应分片，仍然 O(n)。
- 分片后快照也分片存储，只重建变化的片。

> 不要一上来就分片。先测：10 万词构建通常在几十到几百毫秒，配置变更又是低频操作，多数项目不需要分片。

### 4.6 运行时加载与刷新

```java
@Component
public class SensitiveWordManager {
    private final AtomicReference<Snapshot> current = new AtomicReference<>(Snapshot.empty());
    private final ScheduledExecutorService poller  = Executors.newSingleThreadScheduledExecutor(...);
    private final ExecutorService          builder = Executors.newSingleThreadExecutor(...);
    private volatile long lastPollAt;

    @PostConstruct
    public void start() {
        reloadBlocking();                                  // 启动同步加载（见 §4.6.4）
        poller.scheduleWithFixedDelay(this::poll, 5, 5, TimeUnit.SECONDS);
        subscribeChangeChannel();                          // 可选：低延迟触发
    }

    private void poll() {
        try {
            String remote = redis.opsForValue().get(CURRENT_KEY);
            if (remote == null) return;
            long version = Long.parseLong(remote);
            if (version > current.get().version()) {
                builder.submit(() -> reload(version));
            }
        } catch (Exception e) {
            // 保留旧版本，打点 + 告警
            log.warn("sensitive dict poll failed, keep version {}", current.get().version(), e);
        }
    }

    private synchronized void reload(long expectedVersion) {
        Snapshot old = current.get();
        if (expectedVersion <= old.version()) return;      // 已被其他线程更新

        try {
            byte[] blob = redis.opsForValue().get(SNAPSHOT_KEY_PREFIX + expectedVersion);
            if (blob == null) { log.warn("snapshot missing, version={}", expectedVersion); return; }

            byte[] raw = GzipUtils.decompress(blob);
            String checksum = DigestUtils.sha256Hex(raw);
            List<String> words = SnapshotCodec.decode(raw);   // 换行分隔 → List

            SensitiveWordAutomaton automaton = SensitiveWordAutomaton.build(words);
            Snapshot snapshot = new Snapshot(expectedVersion, automaton, words.size(), checksum);
            current.set(snapshot);                            // 原子替换，读路径无锁
            reportNodeVersion(expectedVersion);               // 心跳
            log.info("sensitive dict reloaded: version={}, words={}", expectedVersion, words.size());
        } catch (Exception e) {
            log.error("sensitive dict reload failed, keep version {}", old.version(), e);
            // 打点：rebuild_failures_total++
        }
    }

    public FilterResult filter(String text) { return current.get().automaton().filter(text); }
}
```

关键点：

- **`reload` 串行化**（`synchronized` 或单线程构建器），避免并发重建。
- **版本只前进**：`expectedVersion <= local` 直接返回，防止乱序消息导致回退。
- **构建在后台线程**，不阻塞轮询线程与业务线程。
- **失败保留旧快照**，并上报指标。
- 快速连续发布时，轮询天然合并；pub/sub 触发可加 300–500ms debounce。

#### 4.6.1 一致性 SLA

- 轮询间隔 5s：最坏约 5s + 构建时间。
- pub/sub 正常时：通常 < 1s。
- 明确对外 SLA，例如「发布后 10s 内全节点生效」。
- 节点在 `{sens:dict}:node:{id}` 心跳上报版本，管理端可展示「生效中 / 已生效 / 落后节点」。

#### 4.6.2 Fail-safe 策略

| 场景 | 行为 |
|---|---|
| Redis 不可用 | 继续用本地旧快照；告警 |
| 快照缺失 / 解压失败 / 校验失败 | 不替换；保留旧版本；告警 + 重试 |
| 节点启动时拿不到快照 | 读取本地磁盘缓存；仍无则按配置决策 |
| 发布后部分节点未更新 | 状态页展示；超过 3× 轮询间隔告警 |
| 快照为空词表 | 默认拒绝发布；确需发布时二次确认 + 强告警 |

启动时的 `fail-mode` 建议做成配置：

```
STALE       —— 用本地磁盘/旧快照（可用性优先，默认）
REJECT_ALL  —— 无词表时拒绝所有文本（合规优先）
ALLOW_ALL   —— 明确接受风险（仅测试环境）
```

**绝不要把「加载失败」等同于「空词表」**。内部状态要区分 `EMPTY`（合法空词表）与 `UNAVAILABLE`（加载失败）。

#### 4.6.3 本地磁盘兜底（可选）

每次成功加载后，把 `snapshot_blob` 写入本地文件（如 `/data/austin/sensitive-dict.snapshot`）。启动时 Redis 不可用也能恢复。写入用「临时文件 + 原子 rename」。

#### 4.6.4 启动预热

- 应用启动时**同步**加载一次，加载成功后才对外提供服务（Spring `ApplicationRunner` 或 readniness probe 检查 `current.version() > 0`）。
- 避免滚动发布时首批请求承担构建成本。
- 加载失败按 fail-mode 处理，并让 `/health` 暴露降级状态。

### 4.7 业务接入

定义统一接口，屏蔽渠道差异：

```java
public interface SensitiveWordFilter {
    FilterResult filter(String text);
    default boolean contains(String text) { return filter(text).hit(); }
}

public record FilterResult(boolean hit, String filteredText, List<String> hitWords) {
    public static FilterResult empty() { return new FilterResult(false, "", List.of()); }
}
```

管线接入（以 austin 的 `BusinessProcess` 为例）：

```java
@Override
public void process(ProcessContext<TaskInfo> context) {
    if (!properties.isEnabled()) return;

    String text = extractText(context.getProcessModel());   // 各渠道自行提取文本字段
    if (text == null || text.isEmpty()) return;

    FilterResult result = filter.filter(text);
    if (!result.hit()) return;

    switch (properties.getStrategy()) {
        case REPLACE -> replaceText(context.getProcessModel(), result.filteredText());
        case REJECT  -> context.setNeedBreak(true);          // 终止发送 / 转人工审核
        case REVIEW  -> markForReview(context, result);
    }
    metrics.recordHit(result.hitWords().size());
}
```

设计要点：

- **按渠道提取文本**，不要在一个 switch 里复制 10 份过滤逻辑；IM / 小程序等无文本渠道直接跳过。
- 过滤是**纯内存、幂等**操作，可以安全重试。
- 命中后的策略（替换 / 拦截 / 审核）做成配置，不同业务线可以不同。
- 返回 `version` 便于排查「为什么这个词没被过滤」——日志里带上版本号。

### 4.8 文本归一化（可选扩展）

如果产品要求「全角/半角、大小写、零宽字符、空格插字」也命中，需要先归一化：

```
原文： 敏 感 词
归一：敏感词
```

实现要点：

- 逐字符归一化，同时维护 `normalizedIndex → originalIndex` 映射。
- 匹配在归一化文本上做，替换回原文时用映射定位区间。
- 归一化规则必须**前后台一致**，否则运营看到「已存在」但线上不生效。
- 不要过度归一化（如激进去标点）导致误杀。

> 这是第二期功能。第一期只做精确匹配，先把版本化与 AC 自动机跑通。

### 4.9 性能与容量

#### 4.9.1 压测方法

- 固定词库（1k / 10k / 100k）与文本集（短消息 50 字、长文本 500 字、无命中/高命中）。
- 预热后测 `filter()`：p50 / p99 / 吞吐。
- 对比基线：原 `SMEMBERS + buildTrie + 回溯匹配`。
- 分别记录：**过滤阶段耗时** 与 **端到端时延**，不要混为一谈。
- 构建性能：单次 build 耗时、峰值内存。

#### 4.9.2 预期收益来源

1. 去掉每条消息的全量 Redis 读取（通常是大头）。
2. 去掉每消息建树（CPU + GC）。
3. AC 单遍扫描替代逐位置回溯（词库大、文本长时更明显）。

对外汇报时**拆分三项收益**，不要只给一个总数。

### 4.10 可观测性

#### 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `sensitive_filter_duration_seconds` | Histogram | 过滤耗时，按 hit/未 hit 分桶 |
| `sensitive_filter_hits_total` | Counter | 命中次数 |
| `sensitive_dict_version` | Gauge | 各节点当前版本 |
| `sensitive_dict_version_lag` | Gauge | 当前版本 − 节点版本 |
| `sensitive_dict_rebuild_duration_seconds` | Histogram | 构建耗时 |
| `sensitive_dict_rebuild_failures_total` | Counter | 构建失败次数 |
| `sensitive_dict_snapshot_empty` | Gauge | 是否加载到空词表（应为 0） |
| `sensitive_dict_stale_nodes` | Gauge | 落后节点数 |

#### 日志

- 加载成功：`version / wordCount / checksum / costMs`
- 加载失败：`version / error / 保留版本`
- 发布：`version / wordCount / diff / operator`
- **不要打印词表内容和被过滤原文**（敏感数据），只打印长度、hash、命中数量；必要时脱敏。

#### 告警

- 构建失败 > 0。
- 节点版本落后 > 3 个轮询周期。
- 发布后 60s 内仍有落后节点。
- 快照为空但上一版本非空（高危）。
- Redis 读失败率上升。

### 4.11 权限与安全

| 操作 | 权限点 |
|---|---|
| 查看词库 / 版本 / 在线测试 | `sensitive:word:view` |
| 新增 / 编辑 / 删除 / 导入 | `sensitive:word:edit` |
| 发布 | `sensitive:word:publish` |
| 回滚 | `sensitive:word:rollback` |

- 词表本身是敏感数据：**不下发前端全量词表**，在线测试走服务端接口。
- 导入文件校验：大小上限（如 5 MB）、行数上限（如 5 万）、UTF-8、去 BOM、拒绝二进制。
- 导出 CSV 防公式注入：以 `= + - @` 开头的单元格前置 `'`。
- 前端渲染词条时转义，防 XSS。
- 所有写操作记审计日志，日志只增不改。
- 发布接口做幂等与并发控制（发布锁 + 版本 CAS）。

---

## 5. 前端设计

> 以 React + TypeScript + Ant Design 为例；Vue + Element Plus 可一一对应。

### 5.1 信息架构

```
敏感词管理
├── 词库列表（默认页）
│   ├── 搜索 / 分类筛选 / 状态筛选
│   ├── 新增 / 编辑（弹窗）
│   ├── 批量删除
│   └── 导出
├── 批量导入（抽屉/弹窗）
├── 版本与发布（页签或独立页）
│   ├── 待发布变更
│   ├── 版本历史
│   └── 回滚
└── 在线测试（抽屉）
```

### 5.2 主页面布局

```
┌──────────────────────────────────────────────────────────────────────┐
│ 敏感词管理                          [在线测试] [版本历史] [发布 v43]   │
│ 当前生效 v42 · 词条 12,345 · 发布于 2026-10-20 10:00 · 全节点已生效     │
├──────────────────────────────────────────────────────────────────────┤
│ 有 3 项未发布变更：新增 2 / 删除 1                          [查看差异]  │
├──────────────────────────────────────────────────────────────────────┤
│ [搜索词条________] [分类 ▼] [状态 ▼]     [新增] [批量导入] [导出]       │
├──────────────────────────────────────────────────────────────────────┤
│ □ │ 词条     │ 分类   │ 状态 │ 更新人 │ 更新时间         │ 操作        │
│ □ │ 敏感词A  │ 广告   │ 启用 │ 张三   │ 2026-10-20 09:12 │ 编辑 删除   │
│ □ │ 敏感词B  │ 广告   │ 停用 │ 李四   │ 2026-10-19 18:03 │ 编辑 删除   │
├──────────────────────────────────────────────────────────────────────┤
│ 已选 2 项 [批量删除]                              共 12,345 条  < 1 2 > │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.3 词库列表

**表格列**

| 列 | 说明 |
|---|---|
| 复选框 | 批量操作 |
| 词条 | 超长截断 + Tooltip；不提供全量复制 |
| 分类 | 可筛选 |
| 状态 | 启用/停用 Tag |
| 来源 | 手动 / 导入（可选） |
| 更新人 | |
| 更新时间 | 相对时间 + 精确时间 Tooltip |
| 操作 | 编辑、删除 |

**交互细节**

- 搜索 300ms 防抖，服务端分页。
- 删除需二次确认：`确认删除「敏感词A」？删除后需点击「发布」才会在线上生效。`
- 批量删除展示数量确认。
- 新增/编辑弹窗字段：词条（必填）、分类（下拉+可新建）、状态、备注。
- 前端校验：trim 后非空、长度 ≤ 128、无换行/控制字符；重复由后端返回 `40901` 并在表单上提示。
- 无权限用户隐藏写操作按钮（后端仍强制校验）。
- 空状态、加载骨架、错误重试三态齐全。

**待发布提示**

- 顶部常驻「未发布变更」条，展示 `新增 / 删除 / 修改` 数量。
- 点击查看差异（抽屉）：新增列表、删除列表，分页。
- 发布按钮在无变更时禁用。

### 5.4 批量导入

**流程**

```
选择文件 → 前端解析预览 → 校验（逐行状态）→ 选择模式 → 提交 → 展示结果
```

**文件格式**

- `.txt`：每行一个词。
- `.csv`：`word,category,remark`，支持表头。
- UTF-8；自动去 BOM；忽略空行；行内 trim。

**预览表格**

| 行号 | 内容 | 状态 | 说明 |
|---|---|---|---|
| 12 | 敏感词C | 新增 | |
| 13 | 敏感词A | 已存在 | 将跳过 |
| 14 | （空） | 忽略 | 空行 |
| 15 | 超长内容… | 非法 | 超过 128 字符 |

**模式**

- 追加：保留现有词条。
- 覆盖：导入集合即为全量（危险操作，需二次确认 + 展示将删除的数量）。

**限制**

- 文件 ≤ 5 MB，行数 ≤ 50,000；超出直接拒绝并提示。
- 前端预览只是体验优化，**后端必须重新校验**（`dryRun` 接口 + 正式导入接口）。
- 导入完成后提示「已导入 N 条，还需发布才生效」。

### 5.5 版本与发布

**版本历史表格**

| 列 | 说明 |
|---|---|
| 版本号 | `v43` |
| 类型 | 发布 / 回滚 |
| 词条数 | |
| 变更摘要 | 新增 3 / 删除 1 / 修改 0 |
| 操作人 | |
| 发布时间 | |
| 状态 | 当前生效 / 历史 |
| 操作 | 查看快照、回滚 |

**发布弹窗**

```
即将发布 v43
词条总数：12,347（新增 3，删除 1）
备注：[________________]
预计 10 秒内全节点生效。
[取消] [确认发布]
```

**发布后状态**

- 调用 `GET /status`，轮询 2s，最多 30s：
  - `生效中… (3/4 节点)` → 展示进度条。
  - `已生效` → 停止轮询，刷新页面版本号。
  - 超时仍有落后节点 → 黄色警告 + 节点列表，提供「重试同步」。

**回滚确认**

```
回滚到 v40？
将创建新版本 v44，内容与 v40 一致，预计 10 秒内生效。
[取消] [确认回滚]
```

### 5.6 在线测试

- 抽屉/弹窗，输入多行文本。
- 调用服务端 `POST /check`，展示：
  - 过滤后文本（命中的词用 `<mark>` 高亮，注意 HTML 转义）。
  - 命中词列表、命中数量。
  - 使用的词表版本、耗时（ms）。
- 支持切换策略（替换 / 拦截）查看不同结果（可选）。
- **不在前端加载词表**。

### 5.7 前端状态管理建议

- 列表、筛选、分页放 URL query，便于分享与返回。
- 词条数据用服务端状态库（React Query / SWR）缓存，写操作后 invalidate。
- 发布状态用短轮询，不要放在全局 store。
- 所有破坏性操作以服务端结果为准，不做乐观更新。

### 5.8 TypeScript 类型

```ts
export interface ApiResp<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
}

export interface PageResp<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export interface SensitiveWord {
  id: number;
  word: string;
  category: string;
  status: 0 | 1;
  remark?: string;
  updatedBy: string;
  updatedAt: string;
}

export type ChangeAction = 'ADD' | 'UPDATE' | 'DELETE';

export interface VersionInfo {
  version: number;
  kind: 'PUBLISH' | 'ROLLBACK';
  wordCount: number;
  changeSummary: { added: number; removed: number; updated: number };
  operator: string;
  publishedAt: string;
  current: boolean;
}

export interface PendingChanges {
  added: number;
  removed: number;
  updated: number;
  items: Array<{ action: ChangeAction; word: string }>;
}

export interface DictStatus {
  publishedVersion: number;
  lastPublishedAt: string;
  pending: PendingChanges;
  nodes: {
    total: number;
    upToDate: number;
    stale: Array<{ instanceId: string; version: number; lastHeartbeat: string }>;
  };
}

export interface CheckResult {
  hit: boolean;
  filteredText: string;
  hitWords: string[];
  version: number;
  costMs: number;
}

export interface ImportPreviewRow {
  line: number;
  word: string;
  status: 'NEW' | 'EXIST' | 'INVALID' | 'IGNORED';
  reason?: string;
}
```

---

## 6. 接口契约

### 6.1 通用约定

- 前缀：`/api/admin/sensitive-words`
- 鉴权：登录态 + RBAC 权限点，见 §4.11。
- 请求/响应：`application/json; charset=utf-8`；导入使用 `multipart/form-data`。
- 响应体统一 `{ code, message, data, traceId }`；`code=0` 表示成功。
- 分页参数：`page`（从 1 开始）、`size`（默认 20，最大 200）。
- 时间：ISO-8601 带时区，如 `2026-10-20T10:00:00+08:00`。

### 6.2 错误码

| code | HTTP | 含义 |
|---|---|---|
| 0 | 200 | 成功 |
| 40001 | 400 | 参数校验失败 |
| 40101 | 401 | 未登录 |
| 40301 | 403 | 无权限 |
| 40401 | 404 | 资源不存在 |
| 40901 | 409 | 词条已存在 / 版本冲突 |
| 40902 | 409 | 发布冲突（有并发发布） |
| 42201 | 422 | 导入文件格式错误 |
| 42901 | 429 | 频率超限 |
| 50001 | 500 | 服务内部错误 |

### 6.3 接口清单

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/` | 分页查询词条 | view |
| POST | `/` | 新增词条 | edit |
| PUT | `/{id}` | 编辑词条 | edit |
| DELETE | `/{id}` | 删除词条 | edit |
| DELETE | `/batch` | 批量删除 | edit |
| POST | `/import/preview` | 导入预校验 | edit |
| POST | `/import` | 导入并写入 | edit |
| GET | `/export` | 导出 CSV | view |
| GET | `/pending` | 待发布变更 | view |
| POST | `/publish` | 发布新版本 | publish |
| GET | `/versions` | 版本历史 | view |
| GET | `/versions/{version}/diff` | 版本差异 | view |
| POST | `/versions/{version}/rollback` | 回滚 | rollback |
| GET | `/status` | 当前版本与节点状态 | view |
| POST | `/check` | 在线测试过滤 | view |

### 6.4 核心接口示例

#### 查询词条

```
GET /api/admin/sensitive-words?keyword=敏感&category=ad&status=1&page=1&size=20
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [
      { "id": 1001, "word": "敏感词A", "category": "ad", "status": 1,
        "remark": null, "updatedBy": "zhangsan", "updatedAt": "2026-10-20T09:12:00+08:00" }
    ],
    "total": 1, "page": 1, "size": 20
  },
  "traceId": "9f1c..."
}
```

#### 新增词条

```json
POST /api/admin/sensitive-words
{
  "word": "敏感词A",
  "category": "ad",
  "status": 1,
  "remark": "广告类"
}
```

```json
{ "code": 0, "message": "ok", "data": { "id": 1001 }, "traceId": "..." }
```

重复时：

```json
{ "code": 40901, "message": "词条已存在", "data": { "existingId": 1001 } }
```

#### 批量导入预校验

```
POST /api/admin/sensitive-words/import/preview   (multipart/form-data)
file=@words.txt
```

```json
{
  "code": 0,
  "message": "ok",
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

```json
POST /api/admin/sensitive-words/import
{
  "mode": "APPEND",
  "fileToken": "tmp-2f8a..."
}
```

```json
{ "code": 0, "message": "ok",
  "data": { "inserted": 980, "skipped": 15, "invalid": 3, "needPublish": true } }
```

> `fileToken` 为预校验阶段生成并落临时存储，避免二次上传；也可直接再传一次文件。

#### 待发布变更

```json
GET /api/admin/sensitive-words/pending
{
  "code": 0,
  "message": "ok",
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

#### 发布

```json
POST /api/admin/sensitive-words/publish
{ "remark": "十月广告专项" }
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "version": 43,
    "wordCount": 12347,
    "changeSummary": { "added": 3, "removed": 1, "updated": 0 },
    "expectedEffectiveSeconds": 10
  }
}
```

#### 状态

```json
GET /api/admin/sensitive-words/status
{
  "code": 0,
  "message": "ok",
  "data": {
    "publishedVersion": 43,
    "lastPublishedAt": "2026-10-20T10:00:00+08:00",
    "pending": { "added": 0, "removed": 0, "updated": 0, "items": [] },
    "nodes": {
      "total": 4,
      "upToDate": 4,
      "stale": []
    }
  }
}
```

#### 在线测试

```json
POST /api/admin/sensitive-words/check
{ "text": "这是一段包含敏感词A的文本" }
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "hit": true,
    "filteredText": "这是一段包含***的文本",
    "hitWords": ["敏感词A"],
    "version": 43,
    "costMs": 1
  }
}
```

#### 回滚

```json
POST /api/admin/sensitive-words/versions/40/rollback
{ "remark": "误删回滚" }
```

```json
{ "code": 0, "message": "ok",
  "data": { "newVersion": 44, "rolledBackTo": 40, "wordCount": 12300 } }
```

---

## 7. 测试方案

### 7.1 单元测试（自动机）

| 用例 | 期望 |
|---|---|
| 单字词 | 命中并替换 |
| 前缀词（`敏感`、`敏感词`） | 长词优先，全部替换为等长 `*` |
| 重叠词（`abc`、`bc`） | 都命中，区间正确 |
| 无命中 | 原样返回，`hit=false` |
| 空文本 / `null` | 返回空结果，不抛异常 |
| 文本含 `*` | 仍按原文本扫描 |
| 边界：词长 = 文本长 | 全部替换 |
| Unicode：中文、emoji | 不越界、不乱码 |

**对照测试**：用朴素实现（`String.indexOf` 逐个词扫描）作为 oracle，随机生成词库与文本，比对 `hit`、`hitWords`、`filteredText`。建议用 property-based testing（如 jqwik）。

### 7.2 并发测试

- 多线程持续 `filter()`，同时后台线程反复 `reload()`。
- 断言：任何一次调用都看到完整快照，无 NPE、无部分替换、无死锁。
- 用 `AtomicReference` 替换，读路径不加锁，理论上不应出现撕裂读。

### 7.3 集成测试

- 发布 → 节点在 SLA 内加载新版本（可用 Testcontainers + Redis）。
- pub/sub 消息丢失时，轮询仍能兜底。
- 发布时快照缺失 → 节点不切换，保留旧版本。
- 快照 checksum 不匹配 → 拒绝加载。
- 回滚 → 新版本号递增，内容等于目标版本。
- 空词表发布 → 默认被拒绝。

### 7.4 性能测试

- JMH 基准：1k / 10k / 100k 词，短/长文本，无命中/高命中。
- 指标：吞吐、p50、p99、分配速率（GC）。
- 构建基准：单次 build 耗时、堆占用（JOL / heap dump）。
- 对比基线：原 `SMEMBERS + buildTrie + 回溯匹配`。
- 输出报告写入 `doc/`，记录机器规格与 JDK 版本。

### 7.5 故障注入

- Redis 宕机 / 超时：过滤继续可用，版本不变。
- 快照损坏：不替换，告警。
- 发布过程中断（写完快照未切指针）：`current` 仍指向旧版本，无影响；重试发布即可。
- 节点重启：从 Redis / 本地磁盘恢复。

---

## 8. 上线、运维与安全

### 8.1 配置项

```yaml
sensitive-word:
  enabled: true
  strategy: REPLACE            # REPLACE | REJECT | REVIEW
  fail-mode: STALE             # STALE | REJECT_ALL | ALLOW_ALL
  refresh:
    poll-interval: 5s
    channel: "{sens:dict}:channel"
    debounce: 500ms
  snapshot:
    current-key: "{sens:dict}:current"
    key-prefix: "{sens:dict}:snapshot:"
    ttl-days: 30
  node:
    heartbeat-key: "{sens:dict}:node:"
    heartbeat-interval: 10s
    ttl: 30s
  local-cache:
    enabled: true
    path: /data/app/sensitive-dict.snapshot
  import:
    max-file-size: 5MB
    max-rows: 50000
  validation:
    max-word-length: 128
```

### 8.2 上线步骤

1. 建表、部署管理服务与后台。
2. 导入现有词表（文件 / 脚本），生成 v1 并发布。
3. 业务侧接入 `SensitiveWordFilter`，先以**影子模式**运行：旧逻辑照常生效，新逻辑只记录差异（不影响结果）。
4. 对比 1–2 天，确认命中结果一致后切换。
5. 观察过滤耗时、命中量、版本一致性指标。
6. 下线旧逻辑与 Redis `SET` 词表。

### 8.3 回滚

- 内容回滚：后台操作，发布旧快照为新版本。
- 代码回滚：保留旧过滤实现开关（`strategy=LEGACY`），紧急时切换。
- 词表事故：立即回滚版本 + 告警 + 复盘；空词表发布需双人确认。

### 8.4 安全清单

- [ ] 管理接口鉴权 + RBAC，最小权限。
- [ ] 词表不下发前端，在线测试服务端执行。
- [ ] 日志脱敏，不记录词表全文与被过滤原文。
- [ ] 导入文件大小/行数/编码校验，拒绝二进制。
- [ ] 导出 CSV 防公式注入。
- [ ] 前端渲染转义，防 XSS。
- [ ] 审计日志只增不改，保留期符合合规要求。
- [ ] 发布、回滚操作二次确认 + 操作人记录。

---

## 9. 实施清单

### 9.1 后端

- [ ] 建表：`sensitive_word`、`sensitive_word_version`、`sensitive_word_change_log`
- [ ] Redis Key 与 Lua 发布脚本（hash tag）
- [ ] 词条 CRUD + 唯一约束 + 校验
- [ ] 批量导入（预校验 + 落库 + 批量删除模式）
- [ ] 待发布变更计算（对比最近发布快照）
- [ ] 快照生成：排序、拼接、gzip、SHA-256
- [ ] 版本发布 / 回滚
- [ ] AC 自动机：build / filter / contains
- [ ] `SensitiveWordManager`：启动加载、轮询、订阅、后台重建、原子替换
- [ ] 节点心跳与状态聚合
- [ ] 本地磁盘兜底
- [ ] 业务管线接入 + 策略（替换/拦截/审核）
- [ ] 指标、日志、告警
- [ ] 单元/并发/集成/性能测试

### 9.2 前端

- [ ] 词库列表（搜索、筛选、分页、批量操作）
- [ ] 新增/编辑弹窗 + 表单校验 + 重复提示
- [ ] 删除二次确认
- [ ] 批量导入（上传、本地预览、服务端预校验、模式选择、结果页）
- [ ] 待发布变更条 + 差异抽屉
- [ ] 发布弹窗 + 发布后轮询状态
- [ ] 版本历史 + 差异查看 + 回滚
- [ ] 在线测试（高亮、命中词、版本、耗时）
- [ ] 权限控制（按钮级）
- [ ] 空/加载/错误三态
- [ ] TypeScript 类型与 API 层

### 9.3 运维

- [ ] 指标面板（过滤耗时、命中量、版本落后、构建耗时）
- [ ] 告警规则
- [ ] 词表备份策略（版本表即备份，另做定期导出）
- [ ] 容量评估（词库规模、内存、构建耗时）
- [ ] 灰度与回滚预案

---

## 10. 设计权衡速查

### 10.1 为什么是 AC 自动机

- 一次扫描完成所有词匹配，复杂度 O(n + 命中数)，与词库规模解耦。
- fail 指针天然支持「从文本中间开始命中」和重叠词。
- 对比普通 Trie：Trie 需要对每个起点重新走，最坏 O(n·L)。

### 10.2 为什么增删词要全量重建

- AC 的 fail 指针是**全局性质**：
  - 插入一个词，可能让已有节点的最长后缀发生变化，fail 指针需要重算；
  - 删除一个词，可能让其他节点的 fail 指针回退。
- 增量维护的影响范围不可控，且与无锁读路径冲突。
- 重建是低频离线操作（几十到几百毫秒），用写路径成本换取读路径简单与正确，划算。

### 10.3 为什么用版本号 + 快照，而不是 Redis SET

- 读取成本：版本号是 O(1) 小对象；`SMEMBERS` 是 O(词库大小)。
- 原子性：指针切换天然原子，不存在 `DEL`/`SADD` 中间的空窗。
- 可回滚：版本即快照，历史内容可追溯。
- 多语言：快照是纯文本，任何语言的节点都能构建自己的结构。

### 10.4 方案的边界

| 场景 | 是否需要调整 |
|---|---|
| 词库 10 万级、变更分钟/天级 | 当前方案足够 |
| 词库千万级 | 分片 + 紧凑结构（双数组 Trie） |
| 变更秒级、频繁 | 增量结构或分片重建；评估 debounce |
| 需要变体识别 | 前置归一化层，或引入 NLP/正则规则 |
| 文本超长（文章级） | AC 仍适用；注意单次内存分配，可流式分块 |
| 需要按业务线隔离词表 | 按 category/tenant 分片，独立快照与版本 |

### 10.5 面试表达建议

- 不要把「逐条拉 Redis」硬说成「为了强一致」。词表本来就是定时刷新，SLA 是分钟级。
- 正确框架：**低流量阶段的简单实现 → 有意识地延后优化 → 有度量后触发重构 → 原子替换 + fail-safe**。
- 汇报收益时拆分：去掉 Redis 读取、去掉建树、AC 匹配优化，分别贡献多少。
- 主动给出 80/20 的中间方案（本地缓存 1 秒）与本方案的差异，体现工程判断。
