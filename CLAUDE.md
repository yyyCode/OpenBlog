# OpenBlog 开发规范

Spring Boot 3 多模块后端 + Vue 3 前端博客系统，部署于 wecode.xin。本文件规范项目开发过程，**优先省时省 token**：默认轻量流程，仅大型功能走轻量设计 + 一次性评审。

## 项目概览

| 项 | 值 |
|----|----|
| 后端 | Java 17、Spring Boot 3.5.12、Spring Cloud 2025.0.3 |
| 中间件 | MyBatis-Plus、Dubbo(3.4x)+Nacos、Redisson 3.52.0、Redis |
| 前端 | `vue/`（Vue 3、Vite 8），`npm run dev/build/preview` |
| 部署 | Docker，business + message + gateway 三服务独立，CI 在 `.github/workflows/ci.yml` |

模块结构（根 `pom.xml`）：

```
OpenBlog-common     通用：BizException、PageResult 等
OpenBlog-api        API 聚合（message-api 等）
OpenBlog-framework  framework-redis / -elasticsearch / -audit / -idempotent
OpenBlog-message    消息服务：邮件(DirectMail)+Dubbo RPC+Nacos，端口 8083 / dubbo 20883
OpenBlog-business   业务主服务，端口 8082
OpenBlog-gateway    API 网关，端口 8090（勿改回 8080：与 RocketMQ broker 冲突）
```

服务端口：business **8082**、message **8083**（dubbo 20883）、gateway **8090**、Redis **6739**。

## 常用命令

后端构建 / 测试：

```bash
# 全量（含依赖模块编译+测试）——最常用
mvn -pl OpenBlog-business -am package
# 只跑单模块测试
mvn -pl OpenBlog-framework/framework-idempotent -am test
# 过滤指定测试类（surefire 3.5.5 必须带两个 flag，否则无该测试的模块会报错）
mvn -pl OpenBlog-business -am test -Dtest=CommentIdempotencyWiringTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
```

前端：

```bash
cd vue && npm run build    # 构建验证（dist/ 生成即通过）
```

## 开发流程（按规模分级）

**小/中改动**（≤3 文件、单模块、无跨服务耦合）：

1. 直接实现，不派子代理、不逐任务评审
2. 改后跑对应模块测试 / 构建
3. 提交前自评：命名、注释密度、边界与失败路径
4. 常规提交信息（见下）

**大型功能**（跨模块 / 多文件 / 涉及 business↔message↔gateway 耦合）：

1. 先在 `docs/superpowers/specs/` 写**简短设计说明**（1–2 屏，非完整 spec，不含占位符）
2. 单代理或本会话内联实现，逐文件验证
3. **收尾一次性整体评审**（一个评审代理），不做每任务两阶段评审
4. 涉及服务耦合的：评审必须检查"同部署约束"（见部署与运维约束）

**任何改动强制**：

- 改后必跑对应测试/构建，全量通过才提交
- 提交前 `git status` 确认无意外文件
- **不触碰未跟踪的设计文档**（`docs/designs/gateway/`、`docs/幂等组件设计-防重复执行详解.md`），不将其加入提交

## 代码与提交约定

- 提交信息用**中文 conventional 格式**：`feat|fix|refactor|docs|harden|build(...): 一句话说明`，末尾加 `Co-Authored-By: Claude Code <noreply@anthropic.com>`
- 每个任务独立提交（小型）；大型功能按可验证的原子步骤提交
- 不改动与任务无关的文件；不顺手重构

## 部署与运维约束

- **三服务强耦合**：business↔message 通过 Dubbo 接口（改签名需两端同部署）；business↔gateway 共用 JWT 密钥（改密钥需两端同部署）。涉及任一耦合的变更，部署必须三服务同步
- **幂等框架**：`@RepeatExecuteLimit` 注解 + AOP（framework-idempotent），fail-open 契约（Redis/Redisson 故障降级放行、绝不 500）。已有"业务 + Redis"冒烟测试门禁：验证现有缓存/限流、双击去重、Redis 宕机降级
- 数据库迁移脚本放 `sql/`，部署顺序遵守迁移文档

## 已知坑位

- **surefire 3.5.5**：`-Dtest=某测试` 时若 reactor 里有模块无该测试会报 "No tests matching pattern"，必须加 `-Dsurefire.failIfNoSpecifiedTests=false`
- **Redisson starter**：其自动装配先于 Spring Boot Redis 装配，注册 `StringRedisTemplate`/`RedissonConnectionFactory`，现有 `RedisOps` 功能（文章缓存、Lua 滑动窗口限流）会改走 Redisson 连接工厂——代码无缺陷，但改前/上线前需冒烟确认
- **幂等业务差异**：评论 `createTopLevel` 用 RETURN_SAME_RESULT（双击返回同一评论）、`reply` 用 CACHE_REJECT（双击提示 4299）——属有意设计，勿"修正"统一

## 已有经验沉淀

- 踩坑记录见 `docs/dev-experiences.md`（Dubbo 重试致邮件重复 → 幂等三件套：调用方幂等键 + 接收方去重 + DB 唯一索引）
- 幂等框架完整设计见 `docs/superpowers/specs/2026-08-29-idempotent-framework-design.md`

## 高效协作准则

- 优先用专用工具（Grep/Read/Glob/Edit）而非 bash 搜索；不重复读取已读文件
- 小改动直接改，不启动 spec→plan→subagent 重型流程
- 需要用户执行的命令（如登录/部署）提示用 `! <command>` 在本会话内执行
