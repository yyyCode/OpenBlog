# 架构图片

存放项目架构图、时序图、部署拓扑、数据流等设计类图片。

生成架构图的文生图 prompt（含拼写修正与文件命名要求）见 [architecture-diagram-prompt.md](architecture-diagram-prompt.md)。

## 现有图片

| 文件 | 说明 |
| --- | --- |
| [overview-system.png](overview-system.png) | 整体系统架构：Nginx → gateway → business / message → 数据层 |

## 命名约定

`<主题>-<内容>.<扩展名>`，主题用小写短横线，例如：

- `overview-system.png` — 整体架构
- `deploy-topology.png` — 三服务部署拓扑
- `sequence-email-verify.png` — 邮箱验证码时序
- `idempotent-flow.drawio` / `.png` — 幂等流程图（可编辑源文件一并保留）

## 约定

- 优先提交**可编辑源文件**（`.drawio` / `.svg` / `.excalidraw`），PNG/JPG 作为导出图
- 图片中的服务名、端口需与代码保持一致（business 8082 / message 8083 / gateway 8090）
- 配图对应的文字设计说明放 `docs/designs/`，此处只放视觉产物
