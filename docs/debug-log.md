# Debug 记录

本文档用于记录项目开发与线上排查中的典型 Bug：现象、根因、修复方式与验证要点，便于后续同类问题快速定位。

---

## 2025-05-28 · 头像/媒体上传后预览不可见，访问 URL 返回 5001

### 现象

- 个人资料页从本地上传头像后，预览区图片不显示。
- 保存后访问返回的媒体 URL（如 `https://www.wecode.xin/api/v1/media/files/general/{uuid}.jpg`），响应为 JSON 而非图片：

```json
{
  "code": 5001,
  "message": "No static resource api/v1/media/files/general/df42c00b-b840-4e29-87f1-cc7f56ef4dc0.jpg.",
  "data": null,
  "traceId": "..."
}
```

### 根因

1. **存储 key 含路径分隔符**  
   上传时未指定 `folderId`，`MediaService` 生成的 key 形如 `general/{uuid}.jpg`（`general` 为默认分类前缀）。

2. **对外 URL 与 key 一致带 `/`**  
   访问地址为：`/api/v1/media/files/general/{uuid}.jpg`，即 `files/` 之后还有多段路径。

3. **Spring Boot 3 路径匹配不兼容**  
   控制器原先使用：

   ```java
   @GetMapping("/files/{key:.+}")
   ```

   在 **Spring Boot 3 默认的 `PathPatternParser`** 下，`{key:.+}` **不能跨路径段**匹配，往往只能匹配到 `general`，剩余 `/{uuid}.jpg` 无法命中该接口。

4. **落入静态资源兜底**  
   无 Controller 命中后，请求被当作静态资源处理，抛出 `NoResourceFoundException`（消息为 `No static resource ...`），再被 `GlobalExceptionHandler` 包装为 `code: 5001`。

### 解决方法

修改 `MediaController`：将文件访问接口改为通配路径，从 `HttpServletRequest` 的 URI 中解析完整 storageKey。

```java
private static final String FILES_PATH_MARKER = "/api/v1/media/files/";

@GetMapping(value = "/files/**", produces = MediaType.ALL_VALUE)
public void serveFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String uri = request.getRequestURI();
    int idx = uri.indexOf(FILES_PATH_MARKER);
    // 解析 path，支持原图与 .../thumb 缩略图
    // key 示例：general/uuid.jpg
    serveFile(key, thumb, response);
}
```

**说明：**

- 原图：`/api/v1/media/files/general/uuid.jpg` → key = `general/uuid.jpg`
- 缩略图：`/api/v1/media/files/general/uuid.jpg/thumb` → key = `general/uuid.jpg`，`thumb = true`
- 无需再配置 `spring.mvc.pathmatch.matching-strategy: ant_path_matcher` 作为权宜之计。

### 涉及文件

| 文件 | 变更 |
|------|------|
| `OpenBlog-business/.../media/controller/MediaController.java` | `/files/{key:.+}` → `/files/**` + URI 解析 |
| `OpenBlog-business/src/main/resources/application.yaml` | 移除（若曾添加）`ant_path_matcher` 配置 |

### 验证

1. 重新编译并部署/重启后端。
2. 上传头像后，预览 `<img :src="avatarUrl">` 应能正常显示。
3. 浏览器直接打开媒体 URL，应返回 **图片**（`Content-Type: image/jpeg` 等），而不是 JSON。
4. 附件库、文章封面等使用 `/api/v1/media/files/{category}/{uuid}.ext` 的链接一并恢复。

### 延伸注意

- 若修复路径后仍 5001 且报 MinIO 相关错误，需单独检查 `openblog.storage.minio` 连通性与桶内对象路径（`original/general/...`）。
- 开发环境 `public-base-url` 指向生产域名时，本地预览会请求生产环境，需保证该环境已部署含本次修复的后端。

---

<!-- 新记录请复制上方「## 日期 · 标题」区块，追加在文件末尾 -->
