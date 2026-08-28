# OpenBlog 服务拆分评估：哪些域适合独立成服务

> 更新日期：2026-08-29
> 目的：分析 `OpenBlog-business` 单体中各业务域的解耦现状，评估哪些域适合拆成独立服务、按什么顺序拆。
> 前置阅读：`docs/designs/api-gateway-security-ha.md`（网关选型与安全高可用路线）。

---

## 1. 判断标准：什么才算「适合拆」

不按「好不好看」拆，按四个硬指标：

| 指标 | 拆分的信号 |
|------|-----------|
| **依赖边界** | 该域「不依赖」或「极少依赖」其他域（叶子节点）；被依赖方多时拆分要付出跨服务聚合成本 |
| **生命周期独立** | 部署频率、伸缩需求与其他域明显不同（如媒体上传量大、搜索吃 CPU） |
| **资源特性不同** | IO 密集 / CPU 密集 / 异步重试 / 有独立基础设施（MinIO、ES、MQ、邮箱） |
| **故障隔离价值** | 该域挂了不该拖垮主站（如上传服务被刷爆、ES 不可用、邮件积压） |

## 2. 现状依赖图（已按包间 import 核实）

```
                 ┌──────────────► user ◄──────────────┐
                 │                  ▲                  │
   article ◄─────┤                  │                  ├────► comment
   category      │      interaction │ seo              │
   site ◄────────┤        search    │ site             │
                 │                  │                  │
            （枢纽域：被几乎所有域依赖）
   article ◄── category / search / seo / site / interaction
   user    ◄── forum / comment / article / interaction / search
   comment ◄── site / user

   （叶子域：几乎不依赖别人）
   feedback   → 无任何业务依赖        （纯叶子）
   project    → 无任何业务依赖        （纯叶子）
   changelog  → 无任何业务依赖        （纯叶子）
   notification → 无业务域依赖        （自包含：outbox + channel + template）
   media      → 仅依赖 security       （自包含：MinIO/local 存储）
   search     → 依赖 article/user    （重 IO，依赖 ES）
```

**结论：这是一个典型的「枢纽—叶子」结构。** `article` / `user` / `comment` 是几乎所有域依赖的枢纽；可拆的候选全在叶子侧。

---

## 3. 候选拆分逐一分析

### 3.1 通知 / 消息投递（notification）—— ⭐ 最自然，已半拆

**现状（代码已核实）**
- business 内 `notification/`：`NotificationService` 门面 + `ChannelRegistry` 路由 + `AbstractNotificationChannel` 模板 + `NotificationTemplateService` 渲染 + outbox（本地消息表 + `OutboxRelay` 轮询发布）+ `NotificationMqProducer/Consumer`。
- **真正的投递已独立**：`EmailNotificationChannel` 经 Dubbo 调 `EmailRpcService`（message 服务）直发，已带幂等（retries=0 + 幂等键 + `email_records` 唯一索引）。
- 该域在 business 内**不依赖任何业务域**；被 `user`（注册验证码等）依赖。

**拆分方式**：把 business 里的「通知编排」（outbox、channel、template、MQ）整体下沉进 message 服务，business 侧只暴露一个「提交通知」的 RPC/API。通知域成为完全独立的异步投递服务。

**收益**：邮件重试/积压/幂等彻底隔离；发信慢不再阻塞主链路（`EmailNotificationChannel` 现在同步抛 5002 的地方会变成异步告警）；SMS / 飞书扩展位在独立服务里迭代不触碰主站。
**成本**：低——outbox + MQ + Dubbo 全链路已就绪，只是「搬代码 + 改调用边界」，无新基础设施。
**判断**：**当前最值得拆的一个**，几乎是把「已拆好的邮件服务」补齐为「完整的通知服务」。

### 3.2 媒体 / 对象存储（media）—— 资源型服务，典型拆分对象

**现状（代码已核实）**
- `media/`：上传、缩略图（thumb-long-edge=320）、文件夹管理；存储抽象 `MediaStorage`（`MinioMediaStorage` / `LocalMediaStorage`），MinIO 已启用。
- 依赖只有 `security`（取 CurrentUser）。被 `user`（头像）、`seo`（MediaProperties）依赖；文章封面等存的是**图片 URL 字符串**（`storage.public-base-url` 下），非媒体表外键——这降低了拆分的引用耦合。

**拆分方式**：独立媒体服务负责上传 / 缩略 / 桶管理 / 鉴权下载，返回持久化 URL；业务服务只存「URL 字符串」，下载走媒体服务域名（或 CDN）。

**收益**：
- **故障隔离 + 攻击面收敛**：文件上传是安全重灾区（类型伪造、滥用存储、超大文件拖垮带宽），拆出去后即使上传服务被打爆，文章/评论主链路不受影响。
- **资源隔离**：上传 IO / 缩略 CPU / 对象存储流量单独伸缩，不影响业务实例。
- 前端图片量大（文章封面、头像、正文图），是唯一真正「流量型」的域。
**成本**：中。主要工作是把 `user`/`seo`/前端对媒体 URL 的引用迁到新域名/新服务，并处理存量图片。
**判断**：**第二个值得拆的**，一旦图片流量或存储用量上来就有明确收益。

### 3.3 全文搜索（search）—— 独立基础设施，但当前未启用

**现状（代码已核实）**
- `search/`（`ArticleSearchController` / `ArticleSearchService`）+ `framework-elasticsearch`；`application.yaml` 中 `openblog.search.enabled: false`（ES 未启用）。
- 强依赖 `article` / `user` 数据做索引与检索。

**拆分方式**：独立搜索服务订阅 MQ 的「文章/用户变更事件」异步建索引，对外只提供检索 API；ES 独立集群伸缩。
**收益**：索引/检索 CPU 密集，独立伸缩；ES 不可用时主链路不受影响（当前是搜不动，拆后是「搜索挂了，文章照常看」）。
**成本**：中高——需要把「写库 → 索引」改成「事件 → 异步索引」，并处理初始存量索引；且引入事件消费的最终一致性。
**判断**：**现在是「设计成独立服务」而非「拆」** ——功能本就没启用，将来启用时**直接按独立搜索服务设计**，避免先单体化再拆。
**关键提醒**：若走事件同步，正好复用已有 RocketMQ；不要为了省事把 ES 索引逻辑塞回 article。

### 3.4 纯叶子：feedback / project / changelog

三个域**零业务依赖、表独立**，拆起来最容易（复制粘贴级）：

| 域 | 现状 | 拆分评估 |
|----|------|----------|
| `feedback` | 反馈表单提交，`feedback_entries` 表 | 拆最简单，但价值最小（一个表单）。可作为「零依赖拆分练手」；或并入未来内容服务 |
| `project` | 项目推荐墙，`projects` 表 | 内容型，但首页/文章聚合会跨服务读 → 拆了反而增加聚合成本 |
| `changelog` | 更新日志 | 同上，纯内容展示 |

**判断**：不建议单独成服务（运维成本 > 收益），更适合**并入内容类服务**或保持原样。若一定要拆一个练手，选 `feedback`。

---

## 4. 不建议拆的域（枢纽）

`article`、`user`、`comment`、`interaction`、`forum`、`category`、`seo`、`site` 全部不建议拆：

- 它们**互相依赖、并被几乎所有域依赖**。拆出去会迫使每个服务做跨服务 RPC 聚合 + 共享数据同步——这是微服务拆分最典型的反模式（「分布式单体」）。
- `user` 尤需警惕：微服务理论里「用户/认证」常被独立，但当前 `user` 直接查询 `article`/`comment` 表做聚合（点赞数、收藏、头像等）。要拆必须先引入「用户中心 API」并改造全部调用方，成本最高、收益在现阶段为零。
- `seo`/`site`/`interaction` 本质是 article+comment+user 的**只读聚合视图**，必须跟着枢纽走，无法独立。

---

## 5. 拆分优先级建议

**结论：当前值得动手的只有「通知」一个，其余按需触发。**

| 优先级 | 候选 | 触发条件 | 拆分成本 | 收益 |
|--------|------|----------|----------|------|
| **P0（现在）** | 通知服务（3.1） | 邮件链路已半拆，补齐即可 | 低 | 投递彻底隔离、主链路解耦 |
| P1（按需） | 媒体服务（3.2） | 图片流量/存储用量上升、或上传成为攻击面 | 中 | 故障隔离、资源独立伸缩 |
| P2（启用时） | 搜索服务（3.3） | 启用 ES 全文搜索时**直接按独立服务设计** | 中高 | ES 独立伸缩、主链路不受影响 |
| 不拆 | 枢纽域（第 4 节） | 出现真实的独立伸缩/独立团队需求前 | 高 | — |

**与网关路线的配合**（见 `api-gateway-security-ha.md` 阶段 1）：
- 拆出通知服务后，它若开放 HTTP 管理接口，就出现**第二个对外服务** → 触发引入 Spring Cloud Gateway 的时机。
- 拆分顺序应「**先拆叶子、后上网关、再考虑拆枢纽**」——网关为多服务提供统一入口，但不会降低「拆枢纽」本身的耦合成本。

## 6. 一句话总结

> business 是「枢纽—叶子」结构：**唯一现在值得拆的是通知服务（邮件已半拆，补齐即完整）**；媒体、搜索是按需/启用时拆的次优候选；`article`/`user`/`comment` 等枢纽域在出现真实独立伸缩需求前**不要拆**，否则只会得到一个分布式单体。
