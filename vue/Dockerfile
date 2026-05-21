# CI 会在 GitHub Runner 构建镜像；本地/服务器手动 build 时使用国内可访问的镜像源
FROM docker.m.daocloud.io/library/nginx:alpine
COPY dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
