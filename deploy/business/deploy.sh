#!/usr/bin/env bash
# OpenBlog-business 一键部署：本地打包 → 上传 jar + 部署文件 → 服务器构建镜像并重启容器
#
# 前置条件：
#   1. 服务器已安装 Docker（宝塔「Docker 管理器」或 docker-ce）
#   2. 本机已配置到服务器的免密 SSH（ssh-copy-id user@server）
#   3. 本机有 ssh / scp / mvn
#
# 用法：
#   ./deploy.sh [user@host] [jar 路径]         # 默认 host=root@10.21.76.221
#
# 说明：
#   - 本地 mvn package 只编 business（-am 连带 common/framework/api/message-api）
#   - 服务器用 docker compose up -d --build：jar 变了 → 重建镜像 → 重建容器，尽量省时
set -euo pipefail

SERVER="${1:-root@10.21.76.221}"
REMOTE_DIR="/www/wwwroot/java/openblog"
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="${2:-$REPO_ROOT/OpenBlog-business/target/OpenBlog-business-1.0.0-SNAPSHOT.jar}"

cd "$REPO_ROOT"

# 1. 本地打包（若已打包且无需重编，可把下面这行注释掉以跳过）
echo "==> [1/3] 本地打包 OpenBlog-business ..."
mvn -q -pl OpenBlog-business -am package -DskipTests

if [ ! -f "$JAR" ]; then
    echo "!! 未找到 $JAR，请先执行 mvn -pl OpenBlog-business -am package" >&2
    exit 1
fi

# 2. 上传 jar + 部署文件
echo "==> [2/3] 上传到 $SERVER:$REMOTE_DIR ..."
ssh "$SERVER" "mkdir -p '$REMOTE_DIR'"
scp -q "$JAR"            "$SERVER:$REMOTE_DIR/OpenBlog-business-1.0.0-SNAPSHOT.jar"
scp -q "$REPO_ROOT/deploy/business/Dockerfile"         "$SERVER:$REMOTE_DIR/Dockerfile"
scp -q "$REPO_ROOT/deploy/business/docker-compose.yml" "$SERVER:$REMOTE_DIR/docker-compose.yml"
scp -q "$REPO_ROOT/deploy/business/.dockerignore"      "$SERVER:$REMOTE_DIR/.dockerignore"

# 3. 服务器端：构建镜像并重启容器（兼容 compose v2「docker compose」与 v1「docker-compose」）
echo "==> [3/3] 服务器构建镜像 + 启动容器 ..."
ssh "$SERVER" "cd '$REMOTE_DIR' && bash -s" <<'REMOTE'
set -e
echo "-- 目录内容 --"
ls -la
echo "-- docker 版本 --"
docker --version
if docker compose version >/dev/null 2>&1; then
    DC="docker compose"
elif docker-compose version >/dev/null 2>&1; then
    DC="docker-compose"
else
    echo "!! 服务器上没有 docker compose / docker-compose，请先安装 Docker（宝塔 → Docker 管理器）" >&2
    exit 1
fi
echo "-- 使用: $DC --"
$DC up -d --build --remove-orphans
echo "-- 容器状态 --"
$DC ps
REMOTE

echo ""
echo "==> 部署完成。常用命令："
echo "    日志：  docker logs -f openblog-business"
echo "    状态：  docker ps | grep openblog-business"
echo "    停止：  docker compose -f '$REMOTE_DIR/docker-compose.yml' stop"
