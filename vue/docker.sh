#!/bin/sh
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
NAME="openblog-web"

# 映射到宿主机的端口（容器内仍为 80）。默认用偏门端口，避免与宝塔/本机 80、8080 冲突。
# 需要：云安全组与本机防火墙放行该端口。临时改用其他端口：HOST_PORT=xxxxx sh docker.sh
HOST_PORT="${HOST_PORT:-18088}"

docker stop "$NAME" 2>/dev/null || true
docker rm "$NAME" 2>/dev/null || true
docker rmi "$NAME" 2>/dev/null || true

docker build -t "$NAME" "$DIR"

docker run -d \
  -p "${HOST_PORT}:80" \
  --name "$NAME" \
  --add-host=host.docker.internal:host-gateway \
  --restart on-failure:5 \
  -v "$DIR/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
  "$NAME"
