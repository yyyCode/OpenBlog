# OpenBlog-business Docker 化部署

将 business 服务从「宝塔 Java 项目手动上传 + 重启」改为 **Docker 容器 + 一键脚本**。

## 部署流程

```
本地（开发机）                              服务器（宝塔，如 10.21.76.221）
┌─────────────────────────┐   scp    ┌───────────────────────────────────────┐
│ ./deploy.sh             │ ───────► │ /www/wwwroot/java/openblog/            │
│  ├─ mvn package (fat jar)│  上传    │   OpenBlog-business-1.0.0-SNAPSHOT.jar │
│  ├─ scp jar/Dockerfile/ │          │   Dockerfile                           │
│  │   docker-compose.yml │          │   docker-compose.yml                   │
│  └─ ssh 触发部署          │          │ docker compose up -d --build          │
└─────────────────────────┘          └───────────────────────────────────────┘
```

外部依赖（MySQL / Redis / Nacos / RocketMQ / MinIO）都在局域网 `10.21.76.221`，
容器走默认 bridge 网络直接可达，**compose 里不需要编排任何依赖**。

## 一次部署（替代手动上传 + 宝塔重启）

```bash
cd deploy/business
./deploy.sh root@10.21.76.221
# 若你的服务器 SSH 用户/地址不同，换成自己的；默认 root@10.21.76.221
```

脚本做了三件事：

1. 本地 `mvn -pl OpenBlog-business -am package -DskipTests`（只编 business 及其依赖）
2. `scp` 上传 jar + Dockerfile + docker-compose.yml 到服务器
3. 服务器 `docker compose up -d --build --remove-orphans`（jar 变了 → 重建镜像 → 重建容器）

## 与宝塔原配置的对应关系

| 宝塔配置 | Docker 化 |
|----------|-----------|
| 启动命令 `java -jar -Xmx1024M -Xms256M jar` | `JAVA_OPTS="-Xmx1024M -Xms256M"`，且正确放在 `-jar` 前（宝塔原文顺序其实是错的，`-Xmx` 被当成了程序参数） |
| 端口 8082 | `ports: "8082:8082"`，宿主机 `127.0.0.1:8082` 行为不变 |
| 项目路径 `/www/wwwroot/java/openblog/` | 服务器同一目录放 jar + 部署文件，容器内 `/app/app.jar` |
| 宝塔进程守护 | `restart: always`（容器随 Docker 自启、崩溃自动拉起） |
| JDK 17 | `eclipse-temurin:17-jre` 基础镜像 |

## 首次迁移步骤

1. **服务器装 Docker**：宝塔面板 → 软件商店 → 安装「Docker 管理器」（或用 docker-ce）
2. **停掉宝塔的 Java 项目**（`openblog` 项目停止），否则与容器抢 8082 端口
3. **配置免密 SSH**：`ssh-copy-id root@服务器IP`（Windows 用户：Git Bash 里执行）
4. 执行 `./deploy.sh`，观察 `docker logs -f openblog-business` 确认启动正常
5. 验证：Nginx 代理（`https://www.wecode.xin`）指向 `127.0.0.1:8082` 无需改动

## 覆盖配置（不重新打 jar）

compose 里已留注释，把服务器上的 `application.yaml` 放到 `/www/wwwroot/java/openblog/` 并挂载到
容器 `/app/application.yaml` 即可覆盖 jar 内配置（Spring Boot 外部配置文件优先级更高）。例如
服务器 IP 变化、切 storage 类型、改 Redis 密码时，改这一个文件 + 重启容器即可，不用重新打包。

## message 服务（统一通知服务，8083）

`OpenBlog-message` 已 Docker 化 + 接入 CI：push master（或 workflow_dispatch）触发 `build-message` / `deploy-message`，
在自托管 Runner 上构建镜像并部署到 `/www/wwwroot/java/openblog-message/`（端口 8083 + Dubbo 20883）。
部署文件见 `deploy/message/`；需在 GitHub Actions Secrets 配置 `ALIYUN_AK` / `ALIYUN_SK` / `ALIYUN_FROM`
（部署时写入服务器 .env，容器经 compose 读取，见 `.github/workflows/ci.yml` 的 deploy-message）。

> ⚠️ **business 与 message 必须一起升级**。RPC 按接口全限定名匹配
> `com.yqz.openblog.message.api.NotificationRpcService`。只升级 business 时，message 在 Nacos
> 仍注册旧接口包名 → business 订阅不到 → "No provider" → 5002「邮件服务暂不可用」。
> 升级 message 后到 Nacos 服务列表确认 provider 为新包名（`openblog-message`）再联调。

## 进阶：CI 全自动（已实现）

`.github/workflows/ci.yml` 中，push 到 master（或手动 `workflow_dispatch`）触发
`deploy-backend` job，在服务器上的自托管 Runner（`openblog-backend`）直接执行：

1. `actions/checkout` 拿到本目录的 `Dockerfile` / `docker-compose.yml` / `.dockerignore`
2. 下载 `build-backend` 产出的 jar
3. 停掉旧 systemd 服务 `openblog`（释放 8082），备份旧 jar
4. 拷贝 jar + 部署文件到 `/www/wwwroot/java/openblog/`
5. `docker compose up -d --build --remove-orphans` 并打印容器状态

即 push 到 master 即自动 Docker 部署，本地不再需要跑 `./deploy.sh`。
前提：服务器已装 Docker，Runner 用户有 docker 权限。前端走另一条 `deploy-frontend` 流水线。
改动 `deploy/business/**` 也会触发本流水线（Dockerfile/compose 变更即生效）。

## 常见问题

- **验证码发不出，business 日志抛 5002「邮件服务暂不可用 / No provider」**：message 服务（8083）
  没在跑，或 Nacos 里注册的接口包名与 business 不一致。到 Nacos 服务列表核对 provider 是否为
  `openblog-message` / `com.yqz.openblog.message.api.NotificationRpcService`；改名后 business 与
  message 必须一起重新部署（见上文「message 服务」）。
- **8082 被占用**：宝塔 Java 项目没停，先停掉。
- **容器起不来 / 一直重启**：`docker logs openblog-business` 看日志；多为连不上
  Nacos/MySQL/Redis（确认服务器能访问 10.21.76.221 对应端口、防火墙/安全组放行）。
- **图片不显示**：storage 走 MinIO（10.21.76.221:9000），容器内无需挂上传目录；
  若曾切回 `type=local`，需挂载 `/www/wwwroot/java/uploads`。
- **DNS/主机名**：容器用宿主机同一份外网访问能力，Nacos/Dubbo 消费注册均按 IP，无额外配置。
