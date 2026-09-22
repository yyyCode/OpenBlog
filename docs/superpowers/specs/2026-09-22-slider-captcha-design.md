# 发码接口滑动验证码设计

## 背景与目标

`POST /api/v1/auth/email-code` 当前防护为：邮箱白名单 → 注册态校验 → Redis SETNX 冷却（**key 按邮箱**）→ 发信。
缺口在于冷却按邮箱计：攻击者换邮箱即可无限发码，网关的 5 次/分/FP_IP（`application.yaml:74-78`）配合
最多 20 个指纹轮换仍可达 100 封/分/IP，上代理池后无上限。

引入**每请求固定人力成本**的滑动验证码，使发码成本不随邮箱多样性摊薄。

仓库已有 `/auth/slider-challenge` + `/auth/slider-complete` 与 `SliderVerificationService`，但：
- 开关关闭（`slider.enabled: false`），**前端 `vue/` 全目录零调用**
- 语义仅为"拖到底"：无图片缺口、无轨迹校验，`complete` 只比对 challengeId + 同 IP 段
  → 脚本 `GET challenge → POST complete → 带 id 调用` 三步即绕过，防护强度约等于零

本设计**重写其内部实现、保留接口路径与 `verifyAndConsume` 契约**，并把它接到发码接口。

### 明确的能力上限

缺口位置由服务端随机生成并存 Redis，客户端必须**从图片像素中算出 x**——这是图像识别题。
**能挡住脚本与一般自动化，挡不住打码平台/图像识别**。这是自研方案的强度上限，已确认接受；
轨迹校验是启发式的，不是强保证。

## 安全模型

三者缺一即被绕过，必须同时具备：

| 校验 | 防的是 |
|---|---|
| `abs(x - 存库x) <= 容差` | 盲猜（容差 6px / 随机空间 176px，单次命中率约 3.4%） |
| 一次性 `getAndDelete` | 拿到正确答案后重放 |
| 轨迹：时长 / 点数 / 非匀速 | 算出 x 后"瞬移"过去（机器特征） |

## 后端设计

### 图片生成 `SliderImageGenerator`

独立类，纯 Java2D、**无 Spring 依赖**、可独立单测。程序化生成，**零素材依赖**（规避图片版权，
且每张图不同 → 天然抗重放）：随机渐变底 + 随机几何图形，随机挖缺口并抠出滑块小图，带描边。

- 背景 320×160；滑块块 48×48
- `targetX ∈ [96, 272]`（避免贴近起点），`targetY ∈ [16, 96]`
- 输出 PNG，前端以 data URI 直接渲染

### Redis 状态

`RedisOps` 仅支持 String 值，状态用 Jackson 序列化为 JSON。

- `sliderPending:{id}` → `{"x":..,"ipSeg":"..","ts":..}`，TTL `ttlSeconds`（默认 300s）
- `sliderOk:{id}` → `"1"`，TTL `ttlSeconds`

Key 复用现有 `RedisKeys.sliderPending/sliderOk`，不做改动。

### 接口

沿用现有路径（网关 `skip-paths` 白名单 `application.yaml:62-63` 已含，无需改）：

```
GET  /auth/slider-challenge
  → { enabled, challengeId, background, slider, sliderY }
     background/slider 为 data URI；enabled=false 时仅返回 { enabled:false }

POST /auth/slider-complete
  { challengeId, x, trail:[{x,t}, ...] }
  → 200 通过 / 4001 失败
```

`complete` 校验顺序：

1. `getAndDelete(sliderPending:{id})`，miss → 4001「验证已失效」（**一次性，防重放**）
2. `ipSeg` 一致 → 否则 4001「验证环境异常」（**保留现有 IP 绑定**，接受移动网络误伤）
3. `abs(x - stored.x) <= tolerancePx` → 否则 4001
4. 轨迹：`size >= minTrailPoints`、时长 ∈ [`minDurationMs`, `maxDurationMs`]、总路径 / 净位移
   `<= 3.0`、`minSpeedCv` 校验——取相邻采样点间平均速度绝对值（px/ms）序列，
   要求变异系数 `CV = 标准差 / 均值 >= minSpeedCv`，即拒绝完全匀速的"机器直线"
   > **实现期修正**：初稿此处写的是「x 单调非递减，允许 ≤2px 回退」。实现时发现该规则会误伤
   > 正常用户——人手拖过头再拉回来（overshoot-correct）是常见行为，回退 3px 即被拒。
   > 改为限制总路径与净位移之比兜住来回锯齿（人手约 1.0~1.5，锯齿远大于 3），
   > 速度也改取绝对值后算 CV，避免回退段的负速度把均值拉向 0 使 CV 失真。
5. 全部通过 → 写 `sliderOk:{id}`

### 接入发码接口

`EmailCodeService.sendCode` **第一行**调用 `sliderVerificationService.verifyAndConsume(challengeId)`
（复用现有方法，内部 `getAndDelete` 一次性消费）。

**顺序必须在冷却 SETNX 之前**：`EmailCodeService` 现有注释已记录该坑——冷却键是发信前占位，
若滑块校验放在其后，滑块失败会白占 60 秒冷却，用户需空等一轮。置于最前则滑块失败直接抛 4001，
**完全不触碰冷却键**。

### 开关

`slider.enabled=false` 时：`issue()` 返回 `enabled:false`、`verifyAndConsume()` 直接放行。
前端据此不渲染滑块 → 本地开发与线上回滚均无需改前端代码。

### 撤掉登录/注册接线

`login`/`register` 的滑块调用从未上线（前端零调用、开关关闭），本次移除：
`AuthService:91`、`AuthService:144` 两行；`LoginRequest`/`RegisterRequest` 的 `sliderChallengeId` 字段。
登录爆破仍由 IP/设备锁定（`LoginLockout`/`DeviceLockout`）+ 网关限流负责。
**`SliderVerificationService.verifyAndConsume` 方法保留**，改由发码接口调用。

## 前端设计

新增 `vue/src/components/SliderCaptcha.vue`（与既有 19 个组件同风格），在 `SiteAuthView.vue`
的两处发码入口复用：`sendCode()`（注册）、`sendChangePasswordCode()`（重置密码）。

交互：点击"获取验证码" → 若有 `enabled:false` 则直接发码；否则拉取 challenge 并展示滑块面板 →
用户拖动（记录 `{x,t}` 轨迹）→ 松手提交 `complete` → 成功后立刻以 `sliderChallengeId` 发码。

`vue/src/api/admin.js` 的 `sendEmailCode(email, purpose, sliderChallengeId)` 增加第三参。

## 配置

`openblog.auth-security.slider` 新增：

```yaml
openblog:
  auth-security:
    slider:
      enabled: true          # 生产开启
      ttl-seconds: 300
      tolerance-px: 6
      min-duration-ms: 200
      max-duration-ms: 30000
      min-trail-points: 5
      min-speed-cv: 0.05
```

## 测试

- `SliderImageGeneratorTest`：尺寸正确；缺口坐标落在边界内；同种子输出可复现
- `SliderVerificationServiceTest`：正确 x 通过；**错误 x / 轨迹点数不足 / 时长越界 / 匀速直线 /
  同一 challengeId 二次 complete / 同一 ok 标记二次消费** 全部拒绝；`enabled=false` 时全放行
- `EmailCodeServiceTest`：构造器增加 `SliderVerificationService` 参数需同步修改；
  新增「无有效滑块凭证 → 抛 4001 **且冷却键未被 set**」回归用例
- `cd vue && npm run build`

## 不在范围内

- 打码平台/图像识别防护（已确认接受上限）
- 图片素材库、旋转/点选等其它验证码形态
- 滑块接入登录/注册（本次撤除既有接线）
