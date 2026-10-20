# 敏感词引擎热切换（管理台）

## 目标

在敏感词管理台直接切换运行时匹配引擎（houbb / trie / ac），不重启、不改 YAML；
切换后所有 business 节点在轮询周期（默认 5s）内收敛到同一引擎。

## 方案

- **运行时快照保留词表**：`SensitiveWordSnapshot` 增加 `words`，切换引擎时直接重建
  `WordMatcher`，无需回读 Redis/MySQL。快照本身仍是不可变对象，原子替换引用。
- **覆盖值放 Redis**：`openblog:content:sensitive-word:engine-override`，值为
  `houbb|trie|ac`，无 TTL。选择 Redis 而不是新增 DB 表，是因为节点本来就在轮询 Redis，
  切换可以复用同一套传播机制，不引入新表和新迁移。
- **本地立即生效 + 其他节点轮询收敛**：
  - 管理端点先写 Redis，再调用 manager 就地重建并原子替换快照；
  - 每次轮询先读覆盖值，若与当前快照引擎不同则用当前词表重建；
  - 删除 Redis key 表示"跟随 `openblog.sensitive-word.engine` 配置默认值"。
- **一致性**：覆盖值更新与快照替换在同一把锁内完成，避免轮询用旧覆盖值把刚切换的
  引擎覆盖回去；版本刷新与引擎切换互斥，不会互相覆盖快照。
- **失败处理**：
  - Redis 写失败：端点返回 5001，不切换（避免只有单节点生效）；
  - 引擎构建失败：保留旧快照继续服务，覆盖值已在 Redis，轮询会重试；
  - Redis 读失败：保留上一次已知覆盖值，不回退默认引擎；
  - 非法覆盖值：按未设置处理并告警。
- **不切换 NONE**：主引擎停用仍走 `enabled` 开关，避免管理台误操作导致过滤全放行。

## API / 数据结构

- `PUT /api/v1/admin/sensitive-words/engine`，body `{"engine":"houbb|trie|ac|default"}`；
  `default`/空表示清除覆盖、跟随配置。
- 响应：`{engine, engineOverride, previousEngine, wordCount, buildMs, applied, propagateSeconds}`。
- `/status` 增加 `configuredEngine`、`engineOverride`，管理台据此回显下拉框。
- `@AuditLog(action = "SENSITIVE_WORD_ENGINE_SWITCH")` 记录操作人。

## 前端

状态卡片增加"运行引擎"下拉：跟随配置 / houbb / trie / ac + 热切换按钮；
切换成功后提示构建耗时与其他节点收敛时间，并刷新状态与在线检测结果。

## 验证

- `EngineSelectionTest`：覆盖值解析、默认值、非法值、NONE 拒绝。
- 现有三引擎一致性测试（`EngineParityTest`）保证热切换前后过滤结果一致。
- `mvn -pl OpenBlog-business -am package` + `cd vue && npm run build`。
