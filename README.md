# OpenBlog

这是一个使用 Go (Gin + GORM) 编写的个人博客系统，采用前后端分离架构，包含前台展示和后台管理两个独立的前端应用。

## 功能特性

- **后端**: Go, Gin, GORM, Viper (配置管理), JWT 认证.
- **前台 (Web)**: Vue 3, Vite, Axios, Marked (Markdown 渲染).
- **后台 (Admin)**: Vue 3, Vite, 独立登录与文章发布管理.
- **数据库**: 支持 MySQL (推荐) 和 SQLite.

## 目录结构

```
OpenBlog/
├── cmd/            # 后端入口
├── config/         # 后端配置文件
├── internal/       # 后端业务逻辑
├── web/            # 前台展示项目 (Vue 3)
├── admin/          # 后台管理项目 (Vue 3)
└── README.md
```

## 快速开始

### 1. 后端服务

配置数据库 (`config/config.yaml`) 后运行：

```bash
# 根目录下
go mod tidy
go run cmd/server/main.go
```
服务运行在 `http://localhost:8081`。
**默认管理员账号**: `admin` / `admin123` (首次启动自动创建)

### 2. 前台展示 (Web)

```bash
cd web
npm install
npm run dev
```
访问 `http://localhost:5173` 浏览文章。

### 3. 后台管理 (Admin)

```bash
cd admin
npm install
npm run dev
```
访问 `http://localhost:5174` 登录并发布文章。

## API 文档

- `GET /api/posts`: 获取文章列表
- `GET /api/posts/:id`: 获取文章详情
- `POST /api/login`: 管理员登录 (返回 JWT Token)
- `POST /api/posts`: 发布文章 (需要 Bearer Token)
