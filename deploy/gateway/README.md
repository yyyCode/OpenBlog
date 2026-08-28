# OpenBlog-gateway 部署

## 切换节奏（首次上线顺序）
1. 部署 gateway 容器（8080）—— CI deploy-gateway 或手动 `docker compose up -d --build`
2. 重建 vue 容器让 `/api/` 指向网关：`sh vue/docker.sh`（nginx.conf 已改 8080）
3. 验证：登录 / 读文章 / 写评论；未带 token 访问受保护接口 → business 401；伪造 token → 网关 401
4. 回退预案：nginx.conf `/api/` 改回 `host.docker.internal:8082` 重建 vue 容器，网关可保留不停

## 端口暴露安全
compose 将 8080 发布在宿主 0.0.0.0（供 vue 容器经 host.docker.internal 访问），**也直接暴露给外网**。
上线时必须在宝塔/宿主防火墙仅放行内网访问 8080（外部只暴露 80/443），否则攻击者绕过 Nginx 直连网关，
触发网关自身的 JWT 校验 / 限流（含伪造 X-Forwarded-For 绕限流）路径。

## 环境变量
- `OPENBLOG_JWT_SECRET`：与 business 同值（GitHub Actions Secrets + 服务器 `.env`），注入容器；空值会 fail-fast 拒绝启动。
