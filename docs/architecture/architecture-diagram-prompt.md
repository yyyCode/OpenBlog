# 系统架构图生成 Prompt

用于文生图模型（豆包 / Qwen-Image / 即梦 / DALL·E 3 / Seedream 等）生成 OpenBlog 整体架构图。当前仓库图：`overview-system.png`。

---

## 一、生成前须知

| 项 | 要求 |
| --- | --- |
| 比例 / 尺寸 | 16:9，建议 2560×1440 或 2848×1600 |
| 风格 | 扁平矢量、圆角卡片、细描边、柔和投影 |
| 文字 | 中文标签用国产模型（豆包 / 即梦 / Qwen-Image）；英文标签用 DALL·E 3 更稳 |
| 文字量 | 每个标签 2–6 个字，模型才有可能写对 |
| 产出数量 | 一次生成 2–4 张挑一张，别指望一次成功 |

---

## 二、中文 Prompt（主用）

```
生成一张现代科技风的软件系统架构图。横向 16:9，浅灰白背景带极淡的网格纹理，扁平矢量风格，圆角矩形卡片，细描边，柔和投影，以蓝色为主色，青绿、紫色、橙色点缀，字号清晰、留白充足、所有连线不交叉。

【第 1 层 顶部】居中一张白色长条卡片，内含笔记本电脑与手机图标，文字"浏览器 / Vue 3 单页应用"。

【第 2 层】向下箭头指向一张白色长条卡片，左侧粗体"NGINX"，右侧副文本"静态资源托管 · /api 反向代理"。

【第 3 层】向下箭头指向一张紫色卡片，标题"API 网关 :8090"，内部并排五个浅色胶囊标签："JWT 校验"、"设备指纹"、"限流"、"TraceId"、"CORS"。

【第 4 层 左】向下箭头指向一个青绿色大容器，标题"业务服务 :8082"，内部 3×3 共九个等宽胶囊，从左到右、从上到下依次且各自只出现一次："文章"、"评论"、"论坛"、"用户认证"、"媒体库"、"站内搜索"、"SEO"、"审计日志"、"幂等控制"。

【第 4 层 右】一个橙色卡片，标题"消息服务 :8083"，内部两个胶囊："DirectMail 邮件"、"通知抽象层"。

【连接关系】业务服务右边缘与消息服务左边缘之间画一条水平双向箭头，箭头上标注"Dubbo RPC"。网关与消息服务之间没有任何连线。

【第 5 层】白色圆角标签"Nacos 注册中心"，位于业务服务与消息服务下方居中，用两条虚线分别连到业务服务底部和消息服务底部。

【第 6 层 底部左】数据层：一排四个数据库圆柱图标，从左到右标注"MySQL 8"、"Redis"、"MinIO"、"Elasticsearch（可选）"。每个数据库都由业务服务单独引出一条箭头指向，四个数据库之间互不连线。

【第 6 层 底部右】一个虚线圆角框，标题"GitHub Actions → Docker Compose"，框内三个并排的小容器图标，标注"business"、"message"、"gateway"。

【拼写要求】以下英文必须原样拼写，不得改写：JWT、TraceId、CORS、NGINX、Dubbo、Nacos、MySQL、Redis、MinIO、Elasticsearch、GitHub Actions、Docker Compose。所有标签只出现一次，禁止重复。
```

---

## 三、英文 Prompt（备用）

```
A clean modern software architecture diagram, wide 16:9, flat vector style, light gray background with a very subtle grid, rounded rectangle cards, thin outlines, soft shadows, blue as the primary color with teal, purple and orange accents, crisp readable text, generous whitespace, no crossing lines.

Layer 1 (top): a centered white bar card containing a laptop and phone icon, labeled "BROWSER · Vue 3 SPA".

Layer 2: a downward arrow to a white bar card, bold "NGINX" on the left, subtitle "static hosting · /api reverse proxy" on the right.

Layer 3: a downward arrow to a purple card titled "API GATEWAY :8090" containing five light pill labels in one row: "JWT", "Device Fingerprint", "Rate Limit", "TraceId", "CORS".

Layer 4 (left): a downward arrow to a large teal container titled "BUSINESS SERVICE :8082", containing a 3x3 grid of nine equal pills, each appearing exactly once: "Article", "Comment", "Forum", "User Auth", "Media", "Search", "SEO", "Audit", "Idempotency".

Layer 4 (right): an orange card titled "MESSAGE SERVICE :8083" with two pills: "DirectMail Email", "Notification Layer".

Connections: a single horizontal bidirectional arrow connects the right edge of the business container to the left edge of the message card, labeled "DUBBO RPC". There is NO line between the gateway and the message service.

Layer 5: a white rounded chip labeled "NACOS Registry" centered below the business and message cards, with two dashed lines connecting it to the bottom of the business container and the bottom of the message card.

Layer 6 (bottom left): a data layer row of four database cylinder icons labeled "MySQL 8", "Redis", "MinIO", "Elasticsearch (optional)". Each database is connected by its own separate arrow from the business container directly to the icon; the four databases are NOT connected to each other.

Layer 6 (bottom right): a dashed rounded box titled "GitHub Actions → Docker Compose" containing three small container icons labeled "business", "message", "gateway".

Spelling rules: JWT, TraceId, CORS, NGINX, Dubbo, Nacos, MySQL, Redis, MinIO, Elasticsearch, GitHub Actions, Docker Compose must be spelled exactly. Every label appears only once, no duplicates.
```

---

## 四、负向提示词（避免上一版问题）

```
不要出现重复的模块标签；不要把 Dubbo 箭头画在网关和消息服务之间；不要让 Nginx 的箭头指向消息服务；不要让 Nacos 连接到 CI/CD 框；不要出现拼写错误 JVT / Elesticsearch；不要画数据库之间的流转箭头；不要文字重叠、乱码或英文拼错；不要水印、logo、签名、作者署名；不要写实照片、3D 渲染、过曝霓虹。
```

> 若模型支持，直接把这段作为 negative prompt 传入。

---

## 五、文件命名与存放要求

| 项 | 要求 |
| --- | --- |
| 存放目录 | `docs/architecture/` |
| 命名格式 | `<主题>-<内容>.<扩展名>`，主题用小写英文短横线 |
| 本图文件名 | **`overview-system.png`**（生成后重命名，覆盖旧图） |
| 草稿命名 | `overview-system-draft-01.png`、`-02`……**不入库**，挑定后删掉 |
| 格式 | PNG，≥2560×1440，压缩后单文件 ≤ 800KB |
| 源文件 | 若用 Figma / draw.io / Excalidraw，一并上传 `overview-system.svg` 或 `.drawio` |
| 禁止 | 中文文件名、空格、括号、日期后缀、`生成`/`final`/`副本` 等字样 |

其他图示例：

```
overview-system.png           整体架构
deploy-topology.png           三服务部署拓扑
sequence-email-verify.png     邮箱验证码时序
idempotent-flow.png           幂等流程图
```

---

## 六、入库与引用

生成并命名为 `overview-system.png` 后：

1. README 引用（主 `README.md` 的「🏗️ 架构」小节已有）：

   ```markdown
   ![OpenBlog 系统架构图](docs/architecture/overview-system.png)
   ```

2. 在 `docs/architecture/README.md` 的「现有图片」表格中登记一行。
3. 提交信息建议：`docs: 更新系统架构图`

---

## 七、出图验收清单

- [ ] `JWT`、`Elasticsearch`、`TraceId` 拼写正确
- [ ] Dubbo 双向箭头只在 business ↔ message 之间
- [ ] Nginx 的下行箭头只指向 gateway
- [ ] Nacos 用虚线连 business 与 message，未连到 CI/CD 框
- [ ] 九个业务模块各出现一次，无「论坛」「SEO」重复
- [ ] 四个数据库各自从 business 引出箭头，彼此无连线
- [ ] 无生成水印 / logo / 署名
- [ ] 文件名 `overview-system.png`，体积 ≤ 800KB
- [ ] README 中图片可正常渲染
