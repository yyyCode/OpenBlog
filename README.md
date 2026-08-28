# OpenBlog

全栈博客系统：**Spring Boot 3** + **Vue 3**，支持文章发布、Markdown 渲染、评论互动、论坛、审计追踪。

---

## 模块说明

| 模块 | 说明 |
|------|------|
| `OpenBlog-common` | 公共模块：统一响应 `ApiResponse`、`PageResult`、`BizException`、异常处理自动装配 |
| `OpenBlog-business` | 主业务服务（端口 8082）：文章、评论、论坛、用户、认证、SEO、媒体管理 |
| `OpenBlog-message` | 独立消息服务（端口 8083）：邮件通道（阿里云 DirectMail）+ Dubbo RPC + Nacos 注册 |
| `OpenBlog-api` | 共享 API 聚合模块 |
| ├ `OpenBlog-message-api` | Message RPC 接口与 DTO |
| `OpenBlog-framework` | 框架封装（父聚合） |
| ├ `framework-redis` | Redis 封装：`RedisOps`、Key 管理、滑动窗口限流 |
| ├ `framework-elasticsearch` | Elasticsearch 封装：索引与搜索 |
| └ `framework-audit` | 可插拔审计框架：`@AuditLog` 注解 + AOP + SPI 扩展点 |
| `vue` | Vue 3 前端（Vite 8） |
| `sql` | 数据库脚本：建表、迁移、Mock 数据 |

---

## 技术栈

**后端**：Java 17 · Spring Boot 3.5 · MyBatis-Plus 3.5 · MySQL 8 · Redis · JWT · MinIO · Dubbo 3.3 · Nacos · flexmark · Thymeleaf (SEO)

**前端**：Vue 3 · Vue Router 4 · Vite 8 · marked · dompurify

---

## 快速开始

```bash
# 1. 准备 MySQL 8 + Redis，创建数据库 openblog
# 2. 启动后端
mvn spring-boot:run -pl OpenBlog-business

# 3. 启动前端
cd vue && npm install && npm run dev
```

后端默认 **http://localhost:8082**，前端 **http://localhost:5173**。

---

## 主要功能

- **文章**：草稿/发布/定时发布/隐藏，Markdown 编辑，全文搜索，16:9 封面
- **互动**：评论/回复、点赞、收藏、关注
- **论坛**：话题发布、评论、敏感词过滤（DFA）、管理员隐藏
- **媒体**：MinIO/本地存储，缩略图，拖拽上传
- **权限**：JWT 认证，ADMIN/AUTHOR/READER 三级角色
- **SEO**：Thymeleaf SSR，Open Graph，sitemap.xml，百度推送
- **审计**：`@AuditLog` 注解自动记录操作快照与耗时
- **站点配置**：后台动态编辑站点名称/描述/版权等
- **Live2D**：右下角看板娘交互角色

---

## 待完成

- [ ] Email 服务联调上线（Nacos + Dubbo + DirectMail 已就绪，待配置 AK/SK）
- [ ] 搜索引擎优化完善（百度/Google 收录验证）
- [ ] 邮箱验证码注册
- [ ] 审计记录管理后台页面
- [ ] 论坛管理后台前端页面
- [ ] 单元测试覆盖

---

## 部署

后端两个服务需要**一起构建、一起升级**（business 经 Dubbo 调 message，接口包名强耦合）：

```bash
# 1) business（Web 服务，8082）—— CI 已接入：push master 自动 Docker 部署
mvn -pl OpenBlog-business -am package -DskipTests
# 产物 OpenBlog-business/target/OpenBlog-business-1.0.0-SNAPSHOT.jar，Docker 化部署见 deploy/business/README.md

# 2) message（消息服务，8083，邮件通道）—— 暂未接入 CI，需手动部署
mvn -pl OpenBlog-message -am package -DskipTests
# 产物 OpenBlog-message/target/OpenBlog-message-1.0.0-SNAPSHOT.jar，上传服务器替换旧 jar 后重启

# 前端
cd vue && npm run build
# dist/ 交由 Nginx 托管，API 反代到 8082
```

> ⚠️ business 与 message 的 RPC 接口按全限定名匹配（`com.yqz.openblog.message.api.NotificationRpcService`），
> 升级后必须**同时重新部署**两者；只升一个会导致 Nacos 里 provider/consumer 对不上，
> business 抛 "No provider"（5002 邮件服务暂不可用）。

CI/CD：push `master` 自动构建并 Docker 部署 business（GitHub Actions + 自托管 Runner）；message 暂未接入 CI。

---

## 提交规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
feat(article): add scheduled publish
fix(forum): improve topic list loading
refactor(theme): move dark mode toggle
```

| type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 重构 |
| `style` | 样式调整 |
| `docs` | 文档 |
| `ci` | CI/CD |
| `build` | 构建/依赖 |
| `perf` | 性能优化 |

---

## 许可证

使用前请与项目维护者确认授权范围。
