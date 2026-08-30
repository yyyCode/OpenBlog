# 小而美公司（Small Company）后端设计

> 目标：为首页榜单 / 推荐页 / 详情页提供真实数据，替换前端静态样例；管理端可增删改查。
> 对标：Project 模块（JPA 三层 + 公开/管理同 Controller），SmallCompany 结构高度相似，照抄模板。

## 现状与缺口

- 前端 `vue/src/data/smallCompanies.js` 是静态样例，`CompaniesRecommendView` / `CompanyDetailView` / `HomeView` 榜单都读它，无后端支撑。
- 后端无公司表、无接口。
- Project 模块已建立完整模式：JPA（`jakarta.persistence`）实体 + `JpaRepository` + Service + Controller，公开 GET 走 business `SecurityConfig` permitAll，管理接口方法级 `@PreAuthorize("hasRole('ADMIN')")`，统一返回 `ApiResponse`/`PageResult`/`BizException`（均在 OpenBlog-common）。

## 数据表 `small_companies`

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT AUTO_INCREMENT PK | 主键 |
| name | VARCHAR(120) NOT NULL | 公司名称 |
| type | VARCHAR(32) | 公司类型（效率工具/企业服务/云服务…） |
| scale_min | INT | 规模下限（如 `100`） |
| scale_max | INT | 规模上限（如 `499`，null 表示 `N+`） |
| color | VARCHAR(16) | 占位头像底色（可选） |
| logo_media_key | VARCHAR(64) | 头像 media key（可选，有则渲染真图，否则首字占位） |
| city | VARCHAR(64) | 所在城市 |
| founded | INT | 成立年份（可空） |
| address | VARCHAR(255) | 办公地址 |
| business | VARCHAR(255) | 主营业务 |
| description | TEXT | 简介（避免用 SQL 关键字 desc） |
| website | VARCHAR(128) | 官网域名（不带协议） |
| sort_order | INT NOT NULL DEFAULT 0 | 排序（越小越靠前） |
| status | VARCHAR(16) NOT NULL DEFAULT 'DRAFT' | DRAFT / PUBLISHED |
| published_at | DATETIME | 发布时间 |
| created_at / updated_at | DATETIME NOT NULL | 审计时间（Instant + @PrePersist/@PreUpdate） |

索引：`INDEX idx_small_companies_status_sort (status, sort_order, published_at)`

## 后端结构（OpenBlog-business，包 `com.yqz.openblog.smallcompany`）

新建（对齐 Project 包结构）：

- `entity/SmallCompany.java` + `entity/SmallCompanyStatus.java` — JPA 实体，`@Enumerated(STRING)` 存枚举
- `dto/SmallCompanyListItemResponse.java`（id/name/type/scaleMin/scaleMax/color/logoMediaKey/city/founded）
- `dto/SmallCompanyDetailResponse.java`（上 + address/business/description/website；管理端含 status）
- `dto/SmallCompanyUpsertRequest.java` — 校验注解 `@NotBlank @Size(max=…)`，含 status/sortOrder
- `repo/SmallCompanyRepository.java` — `JpaRepository` + `findByStatusOrderBySortOrderAscPublishedAtDesc`
- `service/SmallCompanyService.java` — 分页防御夹取、`@Transactional` 写操作、DTO 映射
- `controller/SmallCompanyController.java` — `@RequestMapping("/api/v1/small-companies")`，公开+管理同文件
- `sql/small_companies.sql` — `CREATE TABLE IF NOT EXISTS` + 中文 COMMENT + utf8mb4

## 接口

公开（匿名可访问）：

- `GET /api/v1/small-companies?page=&size=` — 仅 PUBLISHED，按 sort_order, published_at 排序，返回 `PageResult<ListItemResponse>`
- `GET /api/v1/small-companies/{id}` — 详情；草稿对非 ADMIN 返回 `BizException(4041)`（`currentUser.isAdmin()` 判断）

管理（`@PreAuthorize("hasRole('ADMIN')")`，与公开同文件）：

- `GET /api/v1/small-companies/admin` — 全状态列表（字面路径放在 `/{id}` 之前，避免被吞）
- `POST /api/v1/small-companies` — 新建（发布则写 publishedAt）
- `PUT /api/v1/small-companies/{id}` — 更新
- `DELETE /api/v1/small-companies/{id}` — 删除

## 接线（必改既有文件）

- `OpenBlog-business/.../security/SecurityConfig.java`：加 `.requestMatchers(HttpMethod.GET, "/api/v1/small-companies/**").permitAll()`（公开 GET；写操作仍走 `anyRequest().authenticated()` + 方法级注解）
- 网关 `application.yaml`：公开 GET 匿名透传，**无需**改 skip-paths

## 前端改造

- 新增 `vue/src/api/smallCompany.js`：`fetchSmallCompanies / fetchSmallCompanyDetail / fetchAdminSmallCompanies / createSmallCompany / updateSmallCompany / deleteSmallCompany` + `logoUrl(key)`（对齐 `api/project.js`）
- `CompaniesRecommendView` / `CompanyDetailView` / `HomeView` 榜单：静态数据改走 API，保留 loading/空态（不报错）
- `CompanyCard`：`logoMediaKey` 有值则渲染 `logoUrl` 图片，否则回退首字占位色块；规模由 `scaleMin/scaleMax` 格式化为 `100-499`（max 为 null 显示 `100+`）
- 管理端 `ConsoleCompaniesView` 增删改查页 + 路由/侧栏入口（对齐 `ConsoleProjectsView`）—— **本轮包含**

## 验收标准

1. `sql/small_companies.sql` 可执行建表；JPA `ddl-auto` 亦能自动建表（二者字段一致）
2. `mvn -pl OpenBlog-business -am package` 通过（含既有测试）
3. 匿名 `GET /api/v1/small-companies` 与 `/{id}` 返回 200；草稿对匿名 4041
4. 非 ADMIN 调管理接口返回 403；ADMIN 可 CRUD，发布/草稿切换正确
5. 前端推荐页 / 详情页 / 首页榜单读到真实数据；空库显示空态不报错
6. `cd vue && npm run build` 通过；`npm run dev` 联调通过

## 不做（边界）

- 不做公司详情富文本（description 为纯文本）
- 不做招聘岗位 / 团队亮点等扩展区块（表结构已预留扩展空间）
- 不做公司 logo 上传流程改造（复用现有 media 上传，仅存 media key）
