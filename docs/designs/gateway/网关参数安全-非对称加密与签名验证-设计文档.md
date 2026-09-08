# 网关层参数安全设计：参数非对称加密 + 签名验证

> **设计目标（一句话）**：在 Gateway 网关层统一实现 **参数非对称加密（RSA）与签名验证（SHA256withRSA）**，对请求参数进行加解密处理与签名校验，防止**数据篡改**与**重放攻击**，保障接口传输安全。
>
> **文档定位**：本设计已在大麦高并发购票项目中落地（`damai-gateway-service`），本文档抽取其完整设计，供其他项目直接照抄实现。
> **实现技术栈**：Java 17 + Spring Cloud Gateway（WebFlux 响应式）+ Redis + Feign + Vue3（jsrsasign）。
> **配套文档**：本模块的整体设计（过滤器体系、限流防刷、灰度、链路追踪等）见《网关模块整体设计总结.md》。

---

## 目录

1. [设计目标与解决的问题](#1-设计目标与解决的问题)
2. [总体架构与核心概念](#2-总体架构与核心概念)
3. [报文协议设计](#3-报文协议设计)
4. [网关处理流程详解](#4-网关处理流程详解)
5. [密钥管理设计](#5-密钥管理设计)
6. [核心代码实现](#6-核心代码实现)
7. [前端（客户端）实现](#7-前端客户端实现)
8. [安全能力分析与增强建议](#8-安全能力分析与增强建议)
9. [在其他项目落地的实施清单](#9-在其他项目落地的实施清单)
10. [配置开关与灰度](#10-配置开关与灰度)
11. [性能与注意事项](#11-性能与注意事项)
12. [附录：相关错误码](#附录相关错误码)

---

## 1. 设计目标与解决的问题

### 1.1 业务痛点

互联网接口长期暴露在公网，面临四类核心威胁：

| 威胁 | 说明 | 危害 |
|------|------|------|
| 窃听 | 明文传输，攻击者抓包即可读取业务参数 | 隐私泄露（手机号、身份证、订单数据） |
| 篡改 | 攻击者修改请求参数（如金额、购票数量）后转发 | 业务越权、订单造假 |
| 伪造 | 攻击者冒充合法客户端构造请求 | 刷票、恶意下单、薅羊毛 |
| 重放 | 把抓到的合法请求原样重复发送 | 重复下单、重复支付、库存误扣 |

### 1.2 安全目标

| 安全目标 | 手段 | 在本设计中的落点 |
|----------|------|------------------|
| 机密性（防窃听） | 参数**非对称加密** | RSA 对 `businessBody` 加密，明文不落公网 |
| 完整性（防篡改） | **签名校验** | SHA256withRSA 对全量参数签名，网关验签 |
| 真实性（防伪造） | 签名 + 渠道密钥 | 私钥只有合法渠道持有，验签即验真 |
| 时效性（防重放） | Token 有效期 + 频控 + （可选 nonce） | 见 [8. 安全能力分析](#8-安全能力分析与增强建议) |
| 统一收口 | 网关层统一处理 | 业务服务零侵入，一处实现处处生效 |

### 1.3 关键设计决策

| 决策点 | 选型 | 理由 |
|--------|------|------|
| 处理位置 | Gateway 网关层（GlobalFilter） | 统一收口，业务服务无需感知加解密；卸下业务侧安全负担 |
| 非对称加密 | RSA 2048 | 公钥可公开发布、私钥不出网；对称密钥分发是难点，非对称天然解决 |
| 签名算法 | SHA256withRSA（RSA-PKCS1 签名） | 与加密共用同一对密钥体系；256 位摘要抗碰撞 |
| 密钥粒度 | 按**渠道 code** 隔离 | 每个渠道独立密钥，吊销、轮换、审计互不影响 |
| 加密对象 | 仅加密 `businessBody`（业务参数） | 业务参数是核心敏感数据；渠道标识等元数据保持明文以便路由 |
| 分段方式 | 117/256 字节分段（PKCS#1 v1.5） | 满足 RSA 2048 单次加解密上限（117 字节加密 / 256 字节解密） |
| 前端实现 | jsrsasign（KJUR） | 浏览器端生成标准 SHA256withRSA 签名 |

---

## 2. 总体架构与核心概念

### 2.1 位置：网关层统一收口

```
┌──────────┐         ┌───────────────────────────────────────────┐
│  客户端   │  HTTPS  │              Gateway 网关层                │
│ (App/Web)│ ──────▶ │  RequestValidationFilter (请求过滤器)       │
└──────────┘         │   ▪ 渠道识别 code                            │
                     │   ▪ RSA 解密 businessBody (v2)              │
                     │   ▪ SHA256withRSA 验签                      │
                     │   ▪ Token 校验 / 用户身份还原                │
                     │   ▪ API 防刷（频控）                         │
                     │   ▪ 向下游透传 code / userId                 │
                     ├───────────────────────────────────────────┤
                     │  ResponseValidationFilter (响应过滤器)       │
                     │   ▪ RSA 加密响应 data 字段 (v2)              │
                     └───────────────────┬───────────────────────┘
                                         │ 内网
                          ┌──────────────┴──────────────┐
                          ▼                             ▼
                   User / Program / Order            ... 业务微服务
```

- **请求方向**：客户端签名（可选加密）→ 网关验签 / 解密 → 透传明文业务参数到下游微服务。
- **响应方向**：下游微服务返回明文 → 网关加密 `data` → 客户端解密（可选）。

### 2.2 核心概念

**① 渠道（Channel，标识为 `code`）**

每个接入方（App、Web、第三方渠道）在平台注册为一个渠道，拥有唯一的 `code`（如 `0001`）。渠道是密钥隔离的粒度。

**② 每个渠道持有一套密钥**

| 密钥 | 字段名 | 用途 | 持有方 |
|------|--------|------|--------|
| 签名私钥 | `signSecretKey` | 客户端签名 | 客户端私有 |
| 签名公钥 | `signPublicKey` | 网关验签 | 网关公开 |
| 数据加密公钥 | `dataPublicKey` | 网关加密响应 | 网关公开 |
| 数据解密私钥 | `dataSecretKey` | 网关解密请求 | 网关私有 |
| Token 密钥 | `tokenSecret` | JWT Token 解析 | 网关私有 |

> 签名对：**私钥签名、公钥验签**，用于"防伪造 + 防篡改"。
> 数据对：**公钥加密、私钥解密**，用于"防窃听"。
> 两对密钥**分离**，签名密钥与加解密密钥互不混用（业界推荐，避免重放利用同一密钥绕过）。

**③ 报文结构**

```
请求 Body（application/json）：
{
    "code":        "0001",            // 渠道标识（明文，用于路由与取密钥）
    "businessBody": "{\"id\":\"1111\",\"sleepTime\":10}",  // 业务参数（v2 下为 RSA 密文）
    "sign":        "iX9s...=="         // 对 code+businessBody 按规则签名
}

关键请求头：
    token      : JWT 登录令牌
    encrypt    : v2 表示启用"加密+签名"（不传则仅验签）
    no_verify  : true 表示跳过验签（仅开发/联调用，生产可强制关闭）
```

**④ 标志位语义**

| 组合 | 含义 | 网关行为 |
|------|------|----------|
| 只传 `sign` | v1 模式：仅防篡改/伪造 | 验签，不加密不解密 |
| `encrypt: v2` | v2 模式：防窃听 + 防篡改 | 先 RSA 解密 `businessBody`，再验签 |
| `no_verify: true` | 开发模式 | 跳过验签（`allow-normal-access=false` 时直接拒绝 517） |

---

## 3. 报文协议设计

### 3.1 请求报文格式

**Body（JSON）：**

| 字段 | 必填 | 说明 |
|------|------|------|
| `code` | ✅ | 渠道编号，网关据此查找渠道密钥 |
| `businessBody` | ✅ | 业务参数 JSON 字符串。v2 模式下为 RSA 密文 |
| `sign` | ✅ | 签名，见 3.2 |

**Header：**

| 头 | 说明 |
|----|------|
| `Content-Type` | 必须是 `application/json`（否则走非 body 分支，不验签） |
| `token` | JWT 登录令牌 |
| `encrypt` | 填 `v2` 启用加密 |
| `no_verify` | 填 `true` 跳过验签（联调用） |

### 3.2 签名规则（sign）

签名算法：**SHA256withRSA**，Base64 输出。

**签名原文构造规则（服务端与客户端必须一致）：**

1. 取参与签名的参数集合：`code`、`businessBody`（v2 模式下取**加密前的明文**还是密文？——本设计取参与签名的字段为外层 map 的值，即 v2 先替换 `businessBody` 为解密结果后验签；客户端侧 v2 示例为"加密后签名"两种都可，见 6.2 注释。实际网关验签输入为解密后的 `bodyContent` 与渠道 `signPublicKey`）。
2. **剔除** `sign`、`files` 两个字段（自身与文件流不参与签名）。
3. 剩余参数**按键名 ASCII 升序排序**。
4. 拼接为 `key1=value1&key2=value2&...`（键值均不做 URL 编码）。
5. 用渠道**签名私钥**对拼接串做 SHA256withRSA 签名，Base64 编码得 `sign`。

```
示例：
  参与参数: {code:"0001", businessBody:'{"id":"1111","sleepTime":10}'}
  排序拼接: businessBody={"id":"1111","sleepTime":10}&code=0001
  签名串:   sign = SHA256withRSA_Base64( 拼接串, 签名私钥 )
```

> 为什么排序？——消除 JSON 键序对签名结果的影响，保证同一参数集在服务端/客户端计算出**完全一致**的待签串。这是跨端签名正确性的基石。

### 3.3 数据加密规则（businessBody）

- 算法：`RSA`（`Cipher.getInstance("RSA")`，即 PKCS#1 v1.5 Padding）。
- 密钥长度：**2048 bit**。
- 分段加密：单次最大 117 字节 → 每 117 字节一段依次加密；Base64 编码输出。
- 分段解密：密文 Base64 解码后每 256 字节一段依次解密；UTF-8 输出明文。
- 兼容处理：解密前将密文中的 `%2B` 还原为 `+`（防止 URL 传输时 `+` 被转义）。

### 3.4 响应报文加密

网关 `ResponseValidationFilter` 对响应 `data` 字段处理：

- 触发条件：请求头 `encrypt == v2` 且请求头 `no_verify != true` 且响应体非空。
- 处理：解析统一响应体 `ApiResponse{code, message, data}`，对 `data` 字段的 JSON 字符串用渠道**数据加密公钥 `dataPublicKey`** 做 RSA 加密后回填。
- 效果：业务微服务返回明文，客户端收到的是密文 `data`，需用数据解密私钥解密（响应加密为**可选项**，不开启则 data 保持明文）。

### 3.5 透传约定（网关 → 下游微服务）

验签/解密成功后，网关把安全处理结果写入**请求头**透传给下游：

| 头 | 内容 |
|----|------|
| `code` | 渠道编号 |
| `userId` | 由 Token 解析出的用户 ID |
| `traceId` | 全链路追踪 ID |

下游微服务只接收**解密后的明文 `businessBody`**，业务代码零改动。

---

## 4. 网关处理流程详解

### 4.1 请求过滤链（RequestValidationFilter）

实现 `GlobalFilter`，`order = -2`（最高优先级），仅对 `application/json` 请求读取并重写 body。

```
请求进入
  │
  ├─ 全局信号量限流（RateLimiter，可选开关 rateSwitch）
  │
  ├─ 生成 / 透传 traceId，写入 MDC 与请求头
  │
  ├─ Content-Type 是否为 application/json ?
  │     ├─ 否 → doExecute("")（无 body，不验签），透传 code/userId 头
  │     └─ 是 → 读取 body → doExecute(body)
  │
  ▼
doExecute(originalBody)
  │
  ├─ [1] 强制签名开关校验
  │     若 allow-normal-access=false 且 no_verify=true → 抛 517 只允许签名访问
  │
  ├─ [2] 是否进入安全校验？
  │     条件: checkParameter(body非空 且 no_verify!=true)
  │           且 非 skipCheckParameter 路径（如支付宝回调）
  │     ├─ 否 → 直接跳过安全校验
  │     └─ 是 →
  │           code = body.code
  │           token = header.token
  │           ① 渠道数据 = getChannelDataByCode(code)     // Redis缓存 → Feign回源
  │           ② if encrypt==v2:
  │                businessBody = RsaTool.decrypt(businessBody, 渠道dataSecretKey)
  │           ③ 验签 verifyRsaSign256(bodyContent, 渠道signPublicKey)
  │                失败 → 抛 10000 RSA_SIGN_ERROR
  │           ④ Token 校验（跳过白名单路径）
  │               token 为空 → 抛 ARGUMENT_EMPTY
  │               userId = tokenService.getUser(token, code, 渠道tokenSecret)
  │           ⑤ 特定路径(如节目详情)需要用户身份时，再补一次 getUser
  │
  ├─ [3] API 防刷 apiRestrict(userId, url, request)
  │      命中规则 → 抛 API_RULE_TRIGGER
  │
  └─ 组装返回:
        body  = 解密后的 businessBody（透传下游）
        header = code / userId / traceId / gray
```

**要点**：解密 → 验签 → 鉴权 → 防刷，**顺序固定**。先解密才能得到参与验签的明文参数；先验签才敢信任参数做后续鉴权；防刷放在最后，任何一环失败都不放行。

### 4.2 响应过滤链（ResponseValidationFilter）

参照 Spring Cloud Gateway 自带 `ModifyResponseBodyGatewayFilterFactory` 实现：

```
下游响应
  │
  ├─ 读取响应体字符串
  ├─ 条件判断（encrypt==v2 && no_verify!=true && body非空）
  │     ├─ 否 → 原样透传
  │     └─ 是 →
  │          ApiResponse resp = JSON.parse(body)
  │          data = RsaTool.encrypt(JSON(data), 渠道dataPublicKey)
  │          resp.setData(密文)
  │          重新序列化返回
  └─ 客户端
```

### 4.3 旁路：API 防刷（配合签名防重放）

`ApiRestrictService` 基于 **Redis + Lua 滑动窗口**，按 `IP[_userId]_url` 维度统计频次，命中规则（固定规则 / 深度分时规则）即拒绝。与签名组合后：

- 签名挡住"篡改/伪造"；
- 频控挡住"同一签名请求在窗口内的暴力重放"。

详见独立文档《高并发与防超卖设计-面试问答.md》中防刷章节。

### 4.4 Token 校验

- `TokenService.parseToken` 用渠道 `tokenSecret` 解析 JWT，取出 `userId`；
- 再从 Redis `USER_LOGIN:{code}:{userId}` 取用户会话；
- 用户不存在 → 抛 `LOGIN_USER_NOT_EXIST`。
- 白名单路径（下单、购票人管理等）跳过 Token 校验，见 [10. 配置](#10-配置开关与灰度)。

---

## 5. 密钥管理设计

### 5.1 密钥生成

RSA 2048 密钥对可用项目内 `RsaTool.getKey()` 生成：

```java
Map<String, String> key = RsaTool.getKey();   // {publicKey, privateKey}
```

### 5.2 存储与下发

渠道密钥随**渠道数据**存储，链路为：

```
管理后台录入渠道数据（含各密钥）  →  DB (channel 表)
                                          │
                                          ▼
网关 ChannelDataService：  Redis(KEY: damai:channel:data:{code})   ← 缓存
        ↑                    未命中回源
        └── Feign ──▶ base-data-service getByCode() ──▶ DB
```

- 网关内 `ChannelDataService` **先查 Redis 缓存，未命中走 Feign 回源 DB，再回填缓存**，避免网关每次请求都穿透到 base-data 服务（10 秒超时兜底，失败抛 `CHANNEL_DATA_NOT_EXIST`）。
- 密钥按渠道隔离，`code` 即取密钥的 key。

### 5.3 密钥使用边界（重要）

| 密钥 | 只允许出现在 | 严禁 |
|------|--------------|------|
| 签名私钥 / 数据解密私钥 | 网关 / 服务端 | 下发到客户端、入前端工程 |
| 签名公钥 / 数据加密公钥 | 客户端 / 公开 | —（公钥公开无风险） |
| Token 密钥 | 网关 | 泄露可伪造任意用户 Token |

> 本仓库前端工程中 `VITE_SIGN_SECRET_KEY` 存放的是**签名私钥**，仅用于本地开发演示；生产环境应通过**安全的密钥下发通道（如配置中心加密、HSM、构建时注入）**注入，严禁硬编码进前端包。

### 5.4 密钥轮换建议（增强）

- 渠道密钥支持**平滑轮换**：DB 中保留新旧两套密钥，`code` 后再加 `keyVersion` 版本号，网关按版本取对应密钥验签；
- 旧密钥在轮换窗口期（如 24h）内仍接受验证，新请求用新密钥签名；
- 吊销：停用渠道 `status=0` 后网关立即拒绝。

---

## 6. 核心代码实现

> 以下代码均取自项目真实实现，可整体搬移。

### 6.1 RsaTool —— RSA 加解密

```java
public class RsaTool {
    private final static Integer KEY_SIZE = 2048;
    private static final int MAX_ENCRYPT_BLOCK = 117;   // 单次最大加密明文块
    private static final int MAX_DECRYPT_BLOCK = 256;   // 单次最大解密密文块
    public static final String KEY_ALGORITHM = "RSA";

    /** 生成公私钥 */
    public static Map<String, String> getKey() {
        KeyPair keyPair = KeyUtil.generateKeyPair(KEY_ALGORITHM, KEY_SIZE);
        Map<String, String> pubPriKey = new HashMap<>(8);
        pubPriKey.put(PUBLIC_KEY, java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        pubPriKey.put(PRIVATE_KEY, java.util.Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        return pubPriKey;
    }

    /** RSA 分段加密（117 字节/段），Base64 输出 */
    public static String encrypt(String data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        int inputLen = data.getBytes().length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offset = 0, i = 0;
        byte[] cache;
        while (inputLen - offset > 0) {
            cache = inputLen - offset > MAX_ENCRYPT_BLOCK
                    ? cipher.doFinal(data.getBytes(), offset, MAX_ENCRYPT_BLOCK)
                    : cipher.doFinal(data.getBytes(), offset, inputLen - offset);
            out.write(cache, 0, cache.length);
            i++;
            offset = i * MAX_ENCRYPT_BLOCK;
        }
        out.close();
        return new String(Base64.encodeBase64(out.toByteArray()));
    }

    /** RSA 分段解密（256 字节/段），UTF-8 输出 */
    public static String decrypt(String data, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] dataBytes = Base64.decodeBase64(data.replaceAll("%2B", "+").getBytes()); // 兼容URL转义
        int inputLen = dataBytes.length, offset = 0, i = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] cache;
        while (inputLen - offset > 0) {
            cache = inputLen - offset > MAX_DECRYPT_BLOCK
                    ? cipher.doFinal(dataBytes, offset, MAX_DECRYPT_BLOCK)
                    : cipher.doFinal(dataBytes, offset, inputLen - offset);
            out.write(cache, 0, cache.length);
            i++;
            offset = i * MAX_DECRYPT_BLOCK;
        }
        out.close();
        return new String(out.toByteArray(), "UTF-8");
    }
    // getPrivateKey / getPublicKey：Base64 解码后经 PKCS8EncodedKeySpec / X509EncodedKeySpec 还原
}
```

### 6.2 RsaSignTool —— SHA256withRSA 签名 / 验签

```java
public class RsaSignTool {
    private final static String SIGN_TYPE = "RSA";

    /** 签名：参数按字典序排序拼接后，用私钥做 SHA256withRSA */
    public static String rsaSign256(Map<String, String> params, String privateKey) {
        return rsaSign256(buildParam(params), privateKey);
    }

    public static String rsaSign256(String content, String privateKey) {
        Signature si = Signature.getInstance(SignAlgorithm.SHA256withRSA.getValue());
        si.initSign(KeyFactory.getInstance(SIGN_TYPE).generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey))));
        si.update(content.getBytes("utf-8"));
        return Base64.getEncoder().encodeToString(si.sign());
    }

    /** 验签：剔除 sign/files 后重建待签串，用公钥验证 */
    public static boolean verifyRsaSign256(Map<String, String> params, String publicKey) {
        params.remove("sign");
        params.remove("files");
        return verifyRsaSign256(getSignCheckContent(params).getBytes("utf-8"),
                params.get("sign"), publicKey);
    }

    /** 待签串构造：键名升序，k=v 以 & 连接，不做 URL 编码 */
    private static String buildParam(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append("&");
            sb.append(keys.get(i)).append("=").append(params.get(keys.get(i)));
        }
        return sb.toString();
    }
}
```

> 项目中同时给出 `parameterTransferV1`（仅签名）与 `parameterTransferV2`（加密+签名）两种联调示例，见 `RsaSignTool.main()`。

### 6.3 RequestValidationFilter —— 网关请求校验（核心片段）

```java
@Component
@Slf4j
public class RequestValidationFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        MediaType contentType = request.getHeaders().getContentType();
        if (Objects.nonNull(contentType)
                && contentType.toString().toLowerCase().contains(MediaType.APPLICATION_JSON_VALUE.toLowerCase())) {
            return readBody(exchange, chain, headMap);      // JSON 请求：读取并重写 body
        } else {
            Map<String, String> map = doExecute("", exchange);
            map.remove(REQUEST_BODY);
            map.putAll(headMap);
            request.mutate().headers(h -> map.forEach(h::add));
            return chain.filter(exchange);
        }
    }

    private Map<String, String> doExecute(String originalBody, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String requestBody = originalBody;
        Map<String, String> bodyContent = new HashMap<>(32);
        if (StringUtil.isNotEmpty(originalBody)) {
            bodyContent = JSON.parseObject(originalBody, Map.class);
        }
        String code, token, userId = null;
        String url = request.getPath().value();
        String noVerify = request.getHeaders().getFirst(NO_VERIFY);
        boolean allowNormalAccess = gatewayProperty.isAllowNormalAccess();

        // [1] 强制签名开关
        if ((!allowNormalAccess) && (VERIFY_VALUE.equals(noVerify))) {
            throw new DaMaiFrameException(BaseCode.ONLY_SIGNATURE_ACCESS_IS_ALLOWED);
        }
        // [2] 进入安全校验
        if (checkParameter(originalBody, noVerify) && !skipCheckParameter(url)) {
            String encrypt = request.getHeaders().getFirst(ENCRYPT);
            code = bodyContent.get(CODE);
            token = request.getHeaders().getFirst(TOKEN);

            // ① 渠道数据（含密钥）
            GetChannelDataVo channelDataVo = channelDataService.getChannelDataByCode(code);

            // ② 解密业务参数（encrypt == v2 时）
            if (StringUtil.isNotEmpty(encrypt) && V2.equals(encrypt)) {
                String decrypt = RsaTool.decrypt(bodyContent.get(BUSINESS_BODY), channelDataVo.getDataSecretKey());
                bodyContent.put(BUSINESS_BODY, decrypt);
            }
            // ③ 验签
            boolean checkFlag = RsaSignTool.verifyRsaSign256(bodyContent, channelDataVo.getSignPublicKey());
            if (!checkFlag) {
                throw new DaMaiFrameException(BaseCode.RSA_SIGN_ERROR);
            }
            // ④ Token 校验 → 还原 userId
            boolean skipCheckTokenResult = skipCheckToken(url);
            if (!skipCheckTokenResult && StringUtil.isEmpty(token)) {
                throw new ArgumentException(BaseCode.ARGUMENT_EMPTY.getCode(),
                        List.of(new ArgumentError(token, "token参数为空")));
            }
            if (!skipCheckTokenResult) {
                UserVo userVo = tokenService.getUser(token, code, channelDataVo.getTokenSecret());
                userId = userVo.getId();
            }
            if (StringUtil.isEmpty(userId) && checkNeedUserId(url) && StringUtil.isNotEmpty(token)) {
                UserVo userVo = tokenService.getUser(token, code, channelDataVo.getTokenSecret());
                userId = userVo.getId();
            }
            requestBody = bodyContent.get(BUSINESS_BODY);   // 明文透传下游
        }
        // [3] API 防刷
        apiRestrictService.apiRestrict(userId, url, request);

        Map<String, String> map = new HashMap<>(4);
        map.put(REQUEST_BODY, requestBody);
        if (StringUtil.isNotEmpty(code))  map.put(CODE, code);
        if (StringUtil.isNotEmpty(userId)) map.put(USER_ID, userId);
        return map;
    }
    // getOrder() = -2
}
```

> **读 body 的技巧**：WebFlux 中 body 只能读一次。`readBody` 用 `ServerRequest.create(exchange,...).bodyToMono(String.class)` 读取原始 body，经安全处理后再用 `CachedBodyOutputMessage` 重写，并通过 `ServerHttpRequestDecorator` 覆盖 `getBody()` 与 `getHeaders()`，实现"读取-校验-重写-透传"。

### 6.4 ResponseValidationFilter —— 网关响应加密（核心片段）

```java
private String checkResponseBody(final ServerWebExchange serverWebExchange, final String responseBody) {
    String modifyResponseBody = responseBody;
    ServerHttpRequest request = serverWebExchange.getRequest();
    String noVerify = request.getHeaders().getFirst(NO_VERIFY);
    String encrypt = request.getHeaders().getFirst(ENCRYPT);
    if ((!VERIFY_VALUE.equals(noVerify)) && V2.equals(encrypt) && StringUtil.isNotEmpty(responseBody)) {
        ApiResponse apiResponse = JSON.parseObject(responseBody, ApiResponse.class);
        Object data = apiResponse.getData();
        if (data != null) {
            String code = request.getHeaders().getFirst(CODE);
            GetChannelDataVo channelDataVo = channelDataService.getChannelDataByCode(code);
            String rsaEncrypt = RsaTool.encrypt(JSON.toJSONString(data), channelDataVo.getDataPublicKey());
            apiResponse.setData(rsaEncrypt);
            modifyResponseBody = JSON.toJSONString(apiResponse);
        }
    }
    return modifyResponseBody;
}
```

### 6.5 ChannelDataService —— 渠道密钥获取（Redis 缓存 + Feign 回源）

```java
public GetChannelDataVo getChannelDataByCode(String code) {
    checkCode(code);                                   // code 为空抛参数异常
    GetChannelDataVo vo = getChannelDataByRedis(code); // Redis 缓存
    if (Objects.isNull(vo)) {
        vo = getChannelDataByClient(code);              // Feign 回源 base-data-service
        setChannelDataRedis(code, vo);                  // 回填缓存
    }
    return vo;
}
```

---

## 7. 前端（客户端）实现

以 Vue3 + axios + jsrsasign 为例（`vue3/src/utils/request.js`）。

### 7.1 请求拦截器：签名后发出

```js
request.interceptors.request.use(config => {
    const signFlag = import.meta.env.VITE_SIGN_FLAG        // 0:普通调用 1:签名调用
    if (config.data && signFlag == 1) {
        config.data = sign(config.data)
    }
    const token = getToken()
    if (token) config.headers = Object.assign(config.headers, { token })  // 携带 token
    return config
}, ...)
```

### 7.2 签名函数

```js
import { KJUR, hextob64 } from "jsrsasign"

export function sign(params) {
    const code = import.meta.env.VITE_CODE                  // 渠道编号
    const paramsStr = JSON.stringify(params)                // 业务参数 → businessBody
    const signParam = { businessBody: paramsStr, code }     // 参与签名参数

    const sig = new KJUR.crypto.Signature({ alg: "SHA256withRSA" })
    sig.init("-----BEGIN PRIVATE KEY-----" + import.meta.env.VITE_SIGN_SECRET_KEY + "-----END PRIVATE KEY-----")
    sig.updateString(buildParam(signParam))                 // 字典序拼接（与后端一致）
    const sign = hextob64(sig.sign())

    return { code, businessBody: paramsStr, sign }          // 请求体
}

// 与后端 RsaSignTool.buildParam 完全一致的排序拼接规则
const buildParam = (params) => {
    const keys = Object.keys(params).sort()
    return keys.map((k, i) => {
        const value = params[k]
        return `${k}=${value}` + (i < keys.length - 1 ? "&" : "")
    }).join("")
}
```

### 7.3 启用开关

| 环境变量 | 值 | 说明 |
|----------|-----|------|
| `VITE_SIGN_FLAG` | `1` | 开启请求签名（0 关闭，纯联调） |
| `VITE_CODE` | `0001` | 渠道编号 |
| `VITE_SIGN_SECRET_KEY` | `<签名私钥>` | 生产必须安全注入 |

> v2 加密（`encrypt: v2` 头 + `businessBody` RSA 加密 + 响应解密）在服务端已就绪；如需启用，客户端用 `dataPublicKey` 对 `businessBody` 加密，并对响应 `data` 用 `dataPrivateKey` 解密。

---

## 8. 安全能力分析与增强建议

### 8.1 已抵御的攻击

| 攻击 | 抵御机制 | 效果 |
|------|----------|------|
| 篡改请求参数 | 验签失败即拒（RSA_SIGN_ERROR 10000） | 任何参数改动都会破坏签名 |
| 伪造请求 | 无渠道私钥无法生成合法签名 | 无法构造合法请求 |
| 窃听业务参数（v2） | RSA 公钥加密，密文公网传输 | 明文不落地公网 |
| 暴力重放 | Token 过期 + API 滑动窗口频控 | 窗口内重复请求被拒 |
| 越权访问 | 网关还原 userId 透传下游 | 下游可做用户级鉴权 |

### 8.2 局限性（必须如实告知）

1. **签名本身不防重放**：同一请求的 `sign` 是确定性的，抓包后原样重发签名依旧有效。当前项目的重放防护来自 **Token 有效期 + 频控**，属于"时间窗内限速"，不是"一次性消费"级别。
2. **v2 加密后的 `businessBody` 由时间戳等内容构成时密文不同，但 code/sign 组合对同一明文固定**，可被用于同明文重放。
3. 若 `allow-normal-access=true` 且客户端可自由传 `no_verify=true`，则验签形同虚设（这是开发期开关，生产必须关闭）。

### 8.3 增强建议：一次性 nonce + 时间戳，做到强防重放

若业务要求**严格防重放**（如支付、下单），在签名体系上叠加：

```
请求参数新增：
    timestamp : 客户端当前毫秒时间戳
    nonce     : 一次性随机串（UUID / 雪花ID）

签名时：timestamp 与 nonce 一并参与排序拼接（见 3.2），
        使其无法被第三方修改。

网关验签通过后：
    ① |now - timestamp| > 5 分钟 → 拒绝（防时间窗漂移）
    ② Redis SET NX nonce EX 300（5分钟）：
        返回成功 → 该 nonce 首次使用，放行
        返回失败 → 该 nonce 已被消费 → 判定重放，拒绝
```

```java
// Redis 一次性 nonce 消费（伪代码）
boolean firstUse = redisCache.setIfAbsent("replay:nonce:" + nonce, "1", 300);
if (!firstUse) throw new DaMaiFrameException(BaseCode.REPLAY_ERROR);
```

要点：
- **nonce 一次性**：同一请求只被处理一次，重放被 Redis 原子拦截（SET NX 天然防并发）。
- **timestamp 防大窗口**：限制请求只在极短时间窗口内有效，杜绝"昨天的包今天重放"。
- **nonce 参与签名**：nonce/timestamp 被篡改会破坏签名，无法绕过。

---

## 9. 在其他项目落地的实施清单

> 目标技术栈差异不大即可平移：网关需支持 WebFlux（Spring Cloud Gateway）或可拦截请求的中间件（Nginx Lua / Kong / Go-Gateway 均可对等实现）。

### 9.1 准备密钥

1. 用 `RsaTool.getKey()`（或 openssl）为每个渠道生成**两对** RSA 2048 密钥：
   - 签名对：`signSecretKey`（客户端） + `signPublicKey`（网关）
   - 数据对：`dataPublicKey`（网关加密响应） + `dataSecretKey`（网关解密请求）
2. 生成渠道 `tokenSecret`（JWT HMAC 密钥）。
3. 录入渠道表（`code / signPublicKey / signSecretKey / dataPublicKey / dataSecretKey / tokenSecret / status`）。

### 9.2 后端落地步骤

| 步骤 | 内容 | 依赖 |
|------|------|------|
| 1 | 搬运 `RsaTool`、`RsaSignTool` 到 common 模块 | hutool / commons-codec |
| 2 | 定义 `GetChannelDataVo`（渠道密钥 VO） | — |
| 3 | 实现 `ChannelDataService`（Redis 缓存 + Feign 回源） | Redis、base-data 服务 |
| 4 | 实现 `RequestValidationFilter`（GlobalFilter, order=-2） | WebFlux |
| 5 | 实现 `ResponseValidationFilter`（GlobalFilter, order=-2） | WebFlux |
| 6 | 实现 `TokenService`（JWT 解析 + Redis 会话） | JWT、Redis |
| 7 | 配置开关（见第 10 节） | GatewayProperty |
| 8 | 联调：用 `RsaSignTool.main()` 的示例数据自测签名/验签/加密 | — |

### 9.3 前端落地步骤

| 步骤 | 内容 |
|------|------|
| 1 | 引入 jsrsasign |
| 2 | 实现 `sign(params)`（排序拼接 + SHA256withRSA） |
| 3 | axios 请求拦截器：签名 + 携带 token |
| 4 | 配置 `VITE_SIGN_FLAG / VITE_CODE / VITE_SIGN_SECRET_KEY` |
| 5 | 联调自测：签名 → 网关验签通过 |

### 9.4 联调检查清单

- [ ] 相同参数在前后端计算出**一致**的待签串（可各打印 `buildParam` 输出比对）。
- [ ] 篡改 `businessBody` 中任意字段 → 网关返回 `10000 rsa签名验证失败`。
- [ ] 伪造/缺失 `sign` → 网关拒绝。
- [ ] `code` 不存在 → `512 没有找到ChannelData`。
- [ ] 未带 `token` 访问需鉴权路径 → `ARGUMENT_EMPTY`。
- [ ] `encrypt: v2` 时，请求 `businessBody` 为密文、响应 `data` 为密文。
- [ ] 关闭 `allow-normal-access` 后，`no_verify: true` 请求被 `517` 拒绝。
- [ ] 频繁调用触发频控 → `API_RULE_TRIGGER`。

### 9.5 兼容与灰度策略

- **先签后密**：上线分两步——先开启验签（v1），稳定后再按渠道灰度开启加密（v2）。
- **开发期豁免**：`no_verify: true` 仅用于内网联调，生产环境必须 `allow-normal-access=false`。
- **白名单路径**：对无法签名的外部回调（如支付回调 `/alipay/notify`）配置 `skip.check.parameter.paths` 豁免。

---

## 10. 配置开关与灰度

网关配置项（`GatewayProperty`，yml 中以 `${...}` 默认值形式给出）：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `api.limit.paths` | 空 | 需要频控的路径（配 /api/limit/*） |
| `skip.check.token.paths` | 下单/购票人/订单等 | 跳过 Token 校验的白名单 |
| `skip.check.parmeter.paths` | `/**/alipay/notify` | 跳过参数验签的白名单 |
| `allow.normal.access` | `true` | `false` 时强制只允许签名访问（生产建议 false） |
| `userId.paths` | 节目详情等 | 需要补齐 userId 的路径 |

> 建议生产配置：`allow.normal.access: false`、按渠道灰度下发 `encrypt: v2`，并接入配置中心（Nacos）动态调整。

---

## 11. 性能与注意事项

1. **RSA 是 CPU 密集运算**：单次 2048 位签名/验签约毫秒级。网关已通过"**仅加密/解密业务参数** + **渠道数据 Redis 缓存**"控制开销。若 QPS 极高，可将 RSA 公钥验签改为 **SM2/SM3 国密**或引入硬件加速。
2. **RSA 有长度上限**：2048 位 PKCS#1 单次最多加密 117 字节，已做分段处理；超长业务参数会明显膨胀体积（密文约 1.5 倍），建议控制 `businessBody` 大小；大批量场景可用 **RSA 加密随机 AES 密钥 + AES 加密正文**的混合加密方案。
3. **密钥安全**：私钥严禁出服务端；前端签名私钥需安全注入，禁止明文打包进前端包。
4. **密钥轮换**：预留 `keyVersion`，支持平滑轮换与吊销。
5. **body 只能读一次**：WebFlux 中必须用 `ServerRequest.bodyToMono` + `CachedBodyOutputMessage` 重写，否则下游拿不到 body。
6. **filter 顺序**：`order=-2` 保证在路由转发前完成校验；注意与限流、灰度等全局过滤器的顺序配合。
7. **响应加密按需开启**：响应加密会影响客户端解析，默认可不开，按渠道灰度开启。

---

## 附录：相关错误码

| 错误码 | 含义 |
|--------|------|
| 512 | 没有找到 ChannelData |
| 517 | 只允许签名访问 |
| 10000 | rsa 签名验证失败 |
| 10001 | rsa 解密失败 |
| 10002 | rsa 加密失败 |
| 10050 | 渠道数据为空 |
| 60008 | 生成 rsa 签名失败 |
| 10055 / 516 | 登录态过期（配合前端重新登录跳转） |
