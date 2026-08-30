# 日志优化：文件持久化 + 错误日志缺口修复

> 日期：2026-08-31
> 目标：①三个可部署服务（business/message/gateway）日志持久化到本地文件，可滚动、可保留、可快速翻错误；②修复"错误页不打印日志"——业务 5xx 错误静默返回的问题。
> 范围：OpenBlog-common（共享 logback + 异常处理）、OpenBlog-business（traceId 关联）、deploy/*（卷挂载）。不涉及 Dubbo 签名 / JWT 密钥耦合。

---

## 1. 现状（事实）

- 三个服务**全部是 Spring Boot 默认控制台日志**：无 logback-spring.xml、yaml 无 `logging:`，日志只进 stdout（docker logs 临时、容器重启即失）。
- 错误处理已打日志：网关 `GatewayErrorHandler`、`GlobalExceptionHandler` 的 `onIO`/`onOther` 均 `log.error(ex)`。
- **缺口**：`GlobalExceptionHandler.onBiz` 捕获**所有** `BizException` 返回错误响应但**不打日志**——业务层抛 `BizException(5001, ...)`（服务端故障码）时，前端看到错误页，后端零日志。
- traceId：网关 `TraceIdFilter` 生成并向下游透传 `X-Trace-Id`，但 business（servlet）没有对应 MDC 过滤器，日志无法按 traceId 关联。

## 2. 方案

### 2.1 共享 logback（OpenBlog-common，classpath 自动生效于所有模块）

新增 `OpenBlog-common/src/main/resources/logback-spring.xml`：

| Appender | 目标 | 级别 | 滚动 |
|----------|------|------|------|
| `CONSOLE` | stdout（保留 docker logs 行为） | ALL | — |
| `FILE` | `logs/${spring.application.name}.log` | INFO+ | 每日 + 50MB，留 14 天 |
| `ERROR_FILE` | `logs/${spring.application.name}-error.log` | ERROR+ | 每日 + 50MB，留 30 天 |

- pattern 含 `%X{traceId:-}`（有 MDC 则带出，无则留空）。
- 压噪：`org.apache.dubbo` / `com.alibaba.nacos` / `org.apache.rocketmq` / `com.mysql` → WARN。
- 文件名用 `<springProperty source="spring.application.name">`，三个服务各自落 `openblog-business.log` 等。

### 2.2 错误日志缺口修复（OpenBlog-common）

`GlobalExceptionHandler.onBiz`：当 `code >= 5000`（服务端故障码，映射 HTTP 500）时 `log.error("biz server error: code={}", code, ex)`；4xxx 属预期业务拒绝，继续静默防刷屏。

### 2.3 traceId 关联（OpenBlog-business）

新增 `TraceIdFilter`（`@Component` + `@Order(HIGHEST_PRECEDENCE)`）：读 `X-Trace-Id`（网关已透传），无则生成；写入 MDC + 响应头；`finally` 清理 MDC。使 business 日志行与网关日志可跨服务按 traceId 关联。

### 2.4 部署持久化（deploy/*）

三个 `docker-compose.yml` 各加 `volumes: - ./logs:/app/logs`，容器日志文件落宿主机。`gitignore` 加 `logs/` 与 `**/logs/`。

## 3. 边界

- **404 / 无异常的错误页**：Spring 容器级 404 无异常对象，不产生堆栈日志，属预期（不刷屏）；真正的 5xx 全部留痕（onIO/onOther/onBiz/网关）。
- **message（Dubbo provider）**：HTTP 面次要，本期不做 traceId 过滤器（pattern 留空容忍）；如需可按 business 同样方式补。
- **网关（WebFlux）**：MDC 不适合 Reactor 线程，traceId 走 exchange 属性（现状），日志 pattern 容忍无 MDC。

## 4. 验证

- `mvn -pl OpenBlog-business -am package`（覆盖 common + business 变更）
- `mvn -pl OpenBlog-gateway -am package`、`mvn -pl OpenBlog-message -am package`（确认共享 logback 不破坏上下文加载）
- 手工冒烟：本地起任一服务，确认 `logs/` 下生成 `.log` 与 `-error.log`，且 `BizException(5xxx)` 有 ERROR 堆栈。
