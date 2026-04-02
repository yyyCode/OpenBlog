#!/bin/sh
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
NAME="openblog-web"

docker stop "$NAME" 2>/dev/null || true
docker rm "$NAME" 2>/dev/null || true
docker rmi "$NAME" 2>/dev/null || true

docker build -t "$NAME" "$DIR"

docker run -d \
  -p 80:80 \
  --name "$NAME" \
  --add-host=host.docker.internal:host-gateway \
  --restart on-failure:5 \
  -v "$DIR/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
  "$NAME"
