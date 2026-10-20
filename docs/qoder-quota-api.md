# Qoder 国际版额度接口调研

> 调研对象：`/data/data/com.termux/files/usr/tmp/qcli/package/bundle/qodercli.js`（Qoder CLI 1.1.74，33 MB 压缩混淆包）
> 现有实现：`app/src/main/java/app/anylyralabs/lyraproxy/qoder/QoderClient.kt`
> 结论时间：2026-10-21

## 0. 先给结论

1. **真实接口就是** `GET https://openapi.qoder.sh/api/v2/quota/usage`。
   - 属于 **openapi** 网关，不是 `api3.qoder.sh`（聊天/模型网关），也不是 `api.qoder.com`（Cloud Agents / 组织管理 API，不是 CLI 额度接口）。
   - **不需要 COSY 签名**，不需要 `X-Model-Key`、`Cosy-Key`、`Cosy-User`、`Cosy-Sigpath`。
   - 只需要普通 `Authorization: Bearer <token>` + `Accept: application/json`；建议再带一个 `User-Agent: qoder/1.1.74`。

2. **现有代码的路径、域名、签名方式没有写错。**
   `QoderClient.kt:286` 的 URL 和 `:288-291` 的 Bearer 头都是对的，所以不要再去改成 `/algo/...` 或加 COSY 签名。

3. **真正的问题在令牌生命周期，最可能是 PAT 账号的 `jt-` 过期后没有刷新。**
   - PAT（`pt-`）不是 Bearer，必须先 `POST /api/v1/jobToken/exchange` 换成 job token（`jt-`，约 24 小时）。
   - 设备码登录拿到的是 device token（`dt-`，约 30 天）。
   - 现有 `refresh()` 把刷新响应的字段读成 `token`，但设备刷新接口返回的是 `device_token`，所以永远拿不到新 token；而且 `refresh()` 在整个项目里**没有任何调用点**。
   - `fetchUsage()` 也没有 401 后刷新再重试的逻辑。
   - 结果：PAT 账号登录 24 小时后，聊天和额度都会开始 401；设备码账号约 30 天后 401。用户看到的“积分检测不可用”很可能就是这个。

4. **解析不是首要故障。** 官方响应就是 lowerCamelCase：`userQuota` / `addOnQuota` / `orgResourcePackage`。现有 `bucket()` 对这三个主桶的解析基本正确。只有边角字段需要补：`percentage`、`available`、`totalUsagePercentage`、`isQuotaExceeded`、`expiresAt` 的 9999 哨兵值。

5. **响应默认是普通 JSON，不是加密的。**
   官方 CLI 对额度响应调用了 `withResponseDecryption()` / `Ek()`，但那是 best-effort：`Wv()` 内部 `try { decrypt_server_response } catch { return 原文 }`。社区客户端只发普通 Bearer 也能正常解析。只有响应首字节不是 `{` 时才需要怀疑加密/压缩。

> 调研边界：本次没有拿到你的账号 token，因此没有在本机做“200 + 真实额度”的实测。接口路径、请求头和字段名来自 CLI bundle、官方 SDK 文档与公开实测仓库的交叉核对；第 5 节给出可直接执行的设备验证命令。

---

## 1. 接口清单

### 1.1 额度主接口（必须）

| 项 | 值 |
|---|---|
| 方法 | `GET` |
| 域名 | `https://openapi.qoder.sh` |
| 路径 | `/api/v2/quota/usage` |
| 认证 | `Authorization: Bearer <dt-... 或 jt-...>` |
| 签名 | 无 COSY 签名 |
| 其他头 | `Accept: application/json`；建议 `User-Agent: qoder/1.1.74` |
| 缓存 | 官方 CLI 内存缓存 15 秒（`Usl=15e3`） |
| 置信度 | **99%**（CLI 常量 + 官方 SDK 文档 + 多个社区客户端实测） |

CLI 中的证据（在 bundle 里搜索这些字面量即可复现）：

- `xEg="/api/v2/quota/usage"`，与 `DEg="/api/v2/user/plan"`、`yEg="/api/v3/user/status"`、`PEg="/sash/api/v2/me/usage"` 定义在一起。
- `getQuotaUsage()` 的流水线：`endpoint(cs)`，其中 `cs="openapi"`；`.withResponseDecryption().get(xEg).withBearerToken()`。
- 旧实现 `fetchQuotaUsage()` 里直接写：
  ```js
  let e=cd("openapi"), t="/api/v2/quota/usage";
  headers: { Accept: "application/json", Authorization: `Bearer ${A}` }
  ```
- 端点表 `Nle.prod.openapi = https://${kJ}`，而 `kJ = "openapi.qoder.sh"`（国际版）；中国版才是 `openapi.qoder.com.cn`。

### 1.2 组织账号的展示接口（候选，非必须）

| 项 | 值 |
|---|---|
| 方法 | `GET` |
| 路径 | `https://openapi.qoder.sh/sash/api/v2/me/usage` |
| 认证 | 同上，Bearer |
| 用途 | 官方 CLI 在账号有 `organization_id` 时优先用它；返回 `displayMode` + `qoderUsage` / `enterpriseUsage` |
| 置信度 | **95%**（CLI 常量与 normalizer 明确） |

说明：

- `isUsagePresentationEnabled()` 只有在 `organization_id` 非空时才为 true。
- `displayMode="qoder"` 时，`qoderUsage` 里就是 1.1 节同样的额度对象。
- `displayMode="enterprise"` 时，只有 `detailUrl` / `openMode` / `fallbackUrl`，**没有数字额度**，不能用来算剩余积分。
- TokenHeat 的额度面板要数字，继续用 `/api/v2/quota/usage` 更合适；`/sash` 只作为 org 账号排查备选。

### 1.3 相关但不要混淆的接口

| 接口 | 方法 | 域名 | 用途 | 是否额度权威 | 置信度 |
|---|---|---|---|---|---|
| `/api/v2/user/plan` | GET | openapi.qoder.sh | 套餐名、`user_type`、到期日 | 否 | 95% |
| `/api/v3/user/status` | GET | openapi.qoder.sh | `plan`、`userType`、`nextResetAt` 等 | 否（`quota:0` 是精简字段） | 95% |
| `/api/v1/jobToken/exchange` | POST | openapi.qoder.sh | `pt-` → `jt-` + `jrt-` | 否（换 token） | 99% |
| `/api/v1/jobToken/refresh` | POST | openapi.qoder.sh | `jrt-` → 新 `jt-` | 否 | 99% |
| `/api/v1/deviceToken/refresh` | POST | openapi.qoder.sh | `drt-` → 新 `dt-` | 否 | 99% |
| `/algo/api/v3/user/status?Encode=1` | POST | api3.qoder.sh | 网关侧账号状态 | 否，`quota` 常为 0 | 90% |
| `/algo/api/v2/model/list?Encode=1` | GET | api3.qoder.sh | 模型目录 | 否 | 99% |

**不要**把 `/algo/api/v3/user/status` 的 `quota: 0` 当成“没有额度”。社区项目已经明确踩过这个坑：真正的额度在 OpenAPI 的 `userQuota` 里。

---

## 2. 请求细节

### 2.1 推荐请求

```http
GET /api/v2/quota/usage HTTP/1.1
Host: openapi.qoder.sh
Accept: application/json
Authorization: Bearer dt-xxxxxxxx
User-Agent: qoder/1.1.74
```

官方 CLI 的新请求管线还会统一加：

```http
Cosy-Version: 1.1.74
Cosy-ClientType: 5
Cosy-MachineOS: aarch64_android
X-Request-Id: <uuid>
```

这些是客户端归属/追踪头，不是签名。社区客户端只带 `Accept`、`Authorization`、`User-Agent` 也能成功，所以**不是必须**。

### 2.2 明确不需要的头

- `X-Model-Key`：聊天/模型网关用。
- `Cosy-Key` / `Cosy-User` / `Cosy-Date` / `Cosy-Sigpath` / `Cosy-Bodyhash`：COSY 签名用，额度接口不需要。
- `x-gw-user-id`：区域端点接口用（`/algo/api/v5/service/region/endpoints`），额度接口不需要。
- `uid` 头：不需要。额度归属由 Bearer token 决定。

### 2.3 Token 类型矩阵

| Token | 前缀 | 典型有效期 | 能否直接查额度 | 说明 |
|---|---|---|---|---|
| Personal Access Token | `pt-` | 创建时自填，可很长 | **不能** | 必须先 `/api/v1/jobToken/exchange` |
| Job Token | `jt-` | 约 24 小时 | 能 | `Authorization: Bearer jt-...` |
| Job Refresh Token | `jrt-` | 约 48 小时 | 不能 | 用来换新 `jt-` |
| Device Token | `dt-` | 约 30 天 | 能 | 设备码登录产物 |
| Device Refresh Token | `drt-` | 约 1 年 | 不能 | 用来换新 `dt-` |

这张表是判断“为什么昨天能查、今天不能查”的关键：**PAT 账号的 `jt-` 只有 24 小时**。

---

## 3. 响应结构

### 3.1 顶层字段

| 字段 | 类型 | 含义 | 备注 |
|---|---|---|---|
| `userId` | string | 账号 ID | CLI normalizer 要求存在，否则整份响应判为无效 |
| `userType` | string | 套餐档位，如 `free`、`personal_standard`、`teams` | 同上 |
| `usageType` | string | 通常是 `"credits"` | 可选 |
| `totalUsagePercentage` | number | 所有可用额度的整体用量 | 文档写 0–100，但实际也见过 0–1；CLI 用 `if (v<=1) v*100` 归一 |
| `isQuotaExceeded` | bool | 是否所有可用额度耗尽 | 官方超额判定 |
| `isHighestTier` | bool | 是否最高档 | 可选 |
| `expiresAt` | number | 当前计费周期/套餐到期，Unix 毫秒 | 可能是 `253402214400000`（9999-12-31）哨兵 |
| `upgradeUrl` | string | 升级页 | 可选 |
| `isPlanQuotaProrated` | bool | 套餐额度是否按比例折算 | 可选 |
| `userQuota` | object | 套餐内置额度 | 见 3.2 |
| `addOnQuota` | object | 加购额度 | 可能不存在 |
| `orgResourcePackage` | object | 组织共享资源包 | 可能不存在；注意 `cap` 和 `available` |
| `dedicatedResourcePackages` | array | 按模型/维度拆分的专属资源包 | 可能不存在；`/sash` 展示层更常见 |

### 3.2 额度桶字段

`userQuota` / `addOnQuota`：

| 字段 | 类型 | 含义 |
|---|---|---|
| `total` | number | 总额度 |
| `used` | number | 已用 |
| `remaining` | number | 剩余（权威值，优先用它，不要自己用 total-used 覆盖） |
| `percentage` | number | 使用比例，通常 0–1 |
| `unit` | string | 通常是 `"credits"` |
| `detailUrl` | string | 仅 `addOnQuota` 可能有，指向用量明细页 |

`orgResourcePackage`：

| 字段 | 类型 | 含义 |
|---|---|---|
| `used` | number | 组织包已用 |
| `cap` | number | 组织包总额度（不是 `total`） |
| `remaining` | number | 剩余 |
| `percentage` | number | 使用比例，通常 0–1 |
| `available` | bool | **当前成员是否真的能消费这个组织包**；`false` 时即使 `cap>0` 也不要用 |
| `unit` | string | 通常是 `"credits"` |

`dedicatedResourcePackages[]`：

| 字段 | 类型 | 含义 |
|---|---|---|
| `id` | string | 包 ID |
| `name` | string | 名称 |
| `description` | string | 描述 |
| `total` | number | 总额度 |
| `used` | number | 已用 |
| `remaining` | number | 剩余 |
| `percentage` | number | 使用比例 |
| `unit` | string | 单位 |
| `expiresAt` | number | 到期，Unix 毫秒 |
| `available` | bool | 是否可用 |
| `status` | string | 状态文案 |
| `displayLabels` | array | `[{dimension, value}]`，例如模型族/模型 key |

### 3.3 真实响应示例

以下样例来自公开的 Qoder 客户端/服务端实测仓库（不是本机账号，字段名与官方 SDK 文档一致）。

**Teams 账号（有套餐额度 + 组织包但当前不可用）：**

```json
{
  "userId": "u1",
  "userType": "teams",
  "totalUsagePercentage": 0.98,
  "isQuotaExceeded": false,
  "expiresAt": 1790265600000,
  "userQuota": {
    "total": 3000,
    "used": 2939,
    "remaining": 61,
    "percentage": 0.98,
    "unit": "credits"
  },
  "orgResourcePackage": {
    "used": 0,
    "cap": 4000,
    "remaining": 0,
    "percentage": 0,
    "available": false,
    "unit": "credits"
  }
}
```

**免费个人账号（套餐池为 0，额度在加购包）：**

```json
{
  "userId": "uid1",
  "userType": "personal_standard",
  "expiresAt": 1790812800000,
  "userQuota": {
    "total": 0,
    "used": 8,
    "remaining": 0,
    "unit": "credits"
  },
  "addOnQuota": {
    "total": 300,
    "used": 36,
    "remaining": 264,
    "unit": "credits"
  }
}
```

**组织账号，`/sash/api/v2/me/usage` 的 qoder 模式：**

```json
{
  "displayMode": "qoder",
  "qoderUsage": {
    "userId": "u1",
    "userType": "teams",
    "totalUsagePercentage": 0.98,
    "isQuotaExceeded": false,
    "expiresAt": 1790265600000,
    "userQuota": { "total": 3000, "used": 2939, "remaining": 61, "percentage": 0.98, "unit": "credits" },
    "orgResourcePackage": { "used": 120, "cap": 4000, "remaining": 3880, "percentage": 0.03, "available": true, "unit": "credits" }
  }
}
```

**组织账号，企业展示模式（没有数字额度）：**

```json
{
  "displayMode": "enterprise",
  "enterpriseUsage": {
    "detailUrl": "https://qoder.com/...",
    "openMode": "externalBrowser",
    "fallbackUrl": "https://qoder.com/..."
  }
}
```

### 3.4 “剩余积分”怎么算

单桶直接读 `remaining`。要汇总时：

```
总剩余 = userQuota.remaining
       + addOnQuota.remaining
       + (orgResourcePackage.available ? orgResourcePackage.remaining : 0)
```

注意：

- `orgResourcePackage.available=false` 时不要计入总剩余，但仍可以单独展示，标注“不可用”。
- `userQuota.total=0` 且 `addOnQuota` 存在时，不要把“0/0 套餐”当成主额度；直接展示加购包。
- `total`/`cap` 为 0 的桶可以隐藏，避免“套餐 0/0 · 已用尽”的误导。
- `expiresAt` 大于 2100-01-01 的值视为“无期限/哨兵”，不要显示 9999-12-31。

---

## 4. 现有实现逐项对照

### 4.1 结论表

| 位置 | 现状 | 判断 | 影响 | 置信度 |
|---|---|---|---|---|
| `QoderClient.kt:286` | `GET ${OPENAPI_URL}/api/v2/quota/usage` | **正确** | 无 | 99% |
| `QoderClient.kt:288-291` | `Accept` + `Bearer accessToken` | **正确** | 无 | 99% |
| `QoderClient.kt:300-303` | 解析 `userQuota` / `addOnQuota` / `orgResourcePackage` | **基本正确**（官方就是 camelCase） | 主字段能出 | 95% |
| `QoderClient.kt:302` | `bucket(orgResourcePackage, "cap")` | **正确** | 无 | 99% |
| `QoderClient.kt:303` | `parseExpiry(root)` | 正确读 `expires_at` / `expiresAt` | 但没过滤 9999 哨兵 | 90% |
| `QoderClient.kt:154-177` | `refresh()` | **有 bug** | 设备 token 永远刷不了 | 99% |
| `QoderClient.kt:157` | 固定 `/api/v1/deviceToken/refresh` | **有 bug** | PAT 的 `jt-` 应该走 `jobToken/refresh` | 99% |
| `QoderClient.kt:168` | `json.optString("token")` | **有 bug** | 设备刷新返回 `device_token`，这里读到空串后直接返回旧账号 | 99% |
| 全局 | `refresh()` 无调用点 | **有 bug** | 到期后没有任何自动续期 | 99% |
| `QoderClient.kt:284-305` | `fetchUsage()` 无 401 刷新重试 | **有 bug** | token 过期即失败 | 99% |
| `QoderClient.kt:113-127` | PAT 直接当 Bearer 试 `/api/v1/userinfo` | **有 bug/隐患** | 若 userinfo 接受 `pt-`，会把 `pt-` 存成 accessToken，额度必 401；官方从不这么用 | 95% |
| `QoderClient.kt:130-144` | PAT 交换只认 `token` | 隐患 | 官方还兼容 `access_token` / `device_token` | 80% |
| `QoderClient.kt:285-292` | 没有 `User-Agent` | 可能被 WAF 拒 | Android 默认 `Dalvik/...`，官方始终发 `qoder/<version>` | 30% |
| `QoderClient.kt:307-315` | `bucket()` 不读 `percentage` / `available` | 显示不完整 | 组织包不可用时仍当可用；进度条可能不准 | 90% |
| `QoderClient.kt:200-206` | `enrich()` 只补 name/email，不补 id | 隐患 | PAT 账号 id 可能一直是 `"qoder"`，影响 COSY 的 uid | 90% |
| `QoderClient.kt:374-394` | `accountFrom()` uid 为空就写 `"qoder"` | 隐患 | 同上 | 90% |

另外，登录参数本身也和 CLI bundle 对得上，不要误改：

- `CLIENT_ID = "e883ade2-e6e3-4d6d-adf7-f92ceff5fdcb"`：bundle 里 `jZc` 解出来就是它（CN 版是 `e93fe488-...`）。
- `CLIENT_TYPE = "5"`：bundle 里 `UiA="5"`。
- `DEVICE_PAGE = "https://qoder.com/device/selectAccounts"`：bundle 的 `cd("base")` 就是 `https://qoder.com`。
- `OPENAPI_URL = "https://openapi.qoder.sh"`：bundle 的 `kJ`。

### 4.2 为什么“路径明明对，却检测不可用”

按可能性排序：

1. **PAT 账号的 `jt-` 过期（最可能）**
   - `loginWithPat()` 在 `:143` 从交换响应取 `token`，这通常是 `jt-`，有效期约 24 小时。
   - `QoderAccount.expiresAt` 也会被写成 24 小时后。
   - 但 `refresh()` 从未被调用；即使调用，`:157` 走的是设备刷新端点，`:168` 读的字段也不对。
   - 所以登录第二天开始，额度接口 401，UI 显示“额度查询失败：HTTP 401 ...”。

2. **把 `pt-` 当成了 Bearer**
   - `loginWithPat()` 先走 `:113-127` 的直接路径。
   - 官方文档和社区矩阵都明确：`pt-` 不是 Bearer，只能换 `jt-`。
   - 如果某天 `/api/v1/userinfo` 对 `pt-` 返回了 200，代码就会把 `pt-` 存进 `accessToken`，之后额度一定失败。
   - 这条路径应删除，统一走 exchange。

3. **设备码账号的 `dt-` 过期（约 30 天）**
   - 设备刷新接口返回 `device_token`，现有 `:168` 读 `token`，永远拿不到新 token。
   - 同样没有调用点。

4. **WAF/UA 或网络层**
   - 官方 CLI 所有 OpenAPI 请求都带 `User-Agent: qoder/<version>`。
   - 现有 `call()` 不带 UA，Android 默认 UA 可能被边缘节点拒绝。
   - 这不是首选原因，但加上成本极低，建议补。

5. **响应被加密（概率低）**
   - 如果 `fetchUsage` 抛“额度响应不是 JSON”，先看响应首字节。
   - 普通账号返回 `{`；只有特殊灰度/企业安全策略才可能加密。
   - 官方 CLI 的 `withResponseDecryption()` 是 best-effort，失败会回退原文；不要因为它存在就认为一定加密。

6. **字段结构变化（概率低）**
   - 官方字段就是 camelCase；CLI 同时兼容 snake_case 是为了老版本/CN 兼容。
   - 如果 HTTP 200 且 `hasAny=false`，再补 snake_case 和 `data` 包裹解析。

### 4.3 精确到行的修改建议

#### A. `refresh()`（`:154-177`）

问题有三处：

- `:157` 固定走 `/api/v1/deviceToken/refresh`，PAT 的 `jrt-` 应该走 `/api/v1/jobToken/refresh`。
- `:168` 只读 `token`，设备刷新返回 `device_token`。
- `:169-176` 重新 `accountFrom()` 时，如果刷新响应没有 `user_id`，id 会退化成 `"qoder"`，覆盖原账号 id。

替换为：

```kotlin
/**
 * 刷新访问令牌。
 *
 * Qoder 有两套刷新链路，不能混用：
 * - 设备码登录：POST /api/v1/deviceToken/refresh，响应字段是 device_token；
 * - PAT/job token：POST /api/v1/jobToken/refresh，响应字段是 token。
 *
 * 失败时返回原账号，让上层决定是否提示重新登录。
 */
fun refresh(account: QoderAccount): QoderAccount {
    if (account.refreshToken.isBlank()) return account

    val jobToken = account.source == "pat" || account.accessToken.startsWith("jt-")
    val path = if (jobToken) {
        "/api/v1/jobToken/refresh"
    } else {
        "/api/v1/deviceToken/refresh"
    }

    val payload = JSONObject().put("refresh_token", account.refreshToken).apply {
        if (!jobToken) {
            // 官方设备刷新会带 machine_id / machine_type；缺失不一定失败，但带上更稳。
            put("machine_id", account.machineId)
            put("machine_type", QoderConfig.CLIENT_TYPE)
        }
    }

    val response = call(
        "${QoderConfig.OPENAPI_URL}$path",
        "POST",
        mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "User-Agent" to QoderConfig.USER_AGENT,
        ),
        payload.toString(),
    )
    if (response.status !in 200..299) return account

    val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return account
    val token = json.optString("token")
        .ifEmpty { json.optString("device_token") }
        .ifEmpty { json.optString("access_token") }
    if (token.isEmpty()) return account

    val refreshed = accountFrom(
        json = json,
        token = token,
        machineId = account.machineId,
        fallbackName = account.name,
        fallbackEmail = account.email,
        source = account.source,
        fallbackId = account.id,
    )
    return refreshed.copy(
        id = refreshed.id.ifEmpty { account.id },
        name = refreshed.name.ifBlank { account.name },
        email = refreshed.email.ifBlank { account.email },
    )
}
```

配套把 `accountFrom()` 的签名加上 `fallbackId`（`:374`）：

```kotlin
private fun accountFrom(
    json: JSONObject,
    token: String,
    machineId: String,
    fallbackName: String,
    fallbackEmail: String,
    source: String,
    fallbackId: String = "qoder",
): QoderAccount {
    val expiresAt = parseExpiry(json)
    val uid = json.optString("user_id").ifEmpty { json.optString("uid") }
    return QoderAccount(
        id = uid.ifEmpty { fallbackId },
        name = json.optString("name").ifEmpty { fallbackName },
        email = json.optString("email").ifEmpty { fallbackEmail },
        accessToken = token,
        refreshToken = json.optString("refresh_token"),
        expiresAt = expiresAt,
        machineId = machineId,
        source = source,
    )
}
```

> 现有调用点都用位置参数，最后一个 `fallbackId` 有默认值，不用改其他调用。

#### B. `loginWithPat()`（`:109-147`）

删除“PAT 直接当 Bearer”的路径一，统一 exchange；并兼容 `token` / `access_token` / `device_token` 三种响应字段。

替换整个函数：

```kotlin
fun loginWithPat(pat: String, machineId: String): Result<QoderAccount> {
    val trimmed = pat.trim()
    if (trimmed.isEmpty()) return Result.failure(IllegalStateException("令牌为空"))

    // PAT 不是 Bearer，必须先换成 job token。官方 CLI 也从不把 pt- 直接用于业务 API。
    val exchange = call(
        "${QoderConfig.OPENAPI_URL}/api/v1/jobToken/exchange",
        "POST",
        mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "User-Agent" to QoderConfig.USER_AGENT,
        ),
        JSONObject()
            .put("personal_token", trimmed)
            .put("machine_id", machineId)
            .toString(),
    )
    if (exchange.status !in 200..299) {
        return Result.failure(
            IllegalStateException("令牌无效：HTTP ${exchange.status} ${exchange.body.take(160)}"),
        )
    }

    val json = runCatching { JSONObject(exchange.body) }.getOrNull()
        ?: return Result.failure(IllegalStateException("令牌交换响应不是 JSON"))
    val token = json.optString("token")
        .ifEmpty { json.optString("access_token") }
        .ifEmpty { json.optString("device_token") }
    if (token.isEmpty()) {
        return Result.failure(IllegalStateException("令牌交换未返回 token"))
    }

    val base = accountFrom(json, token, machineId, "", "", "pat")
    // exchange 响应通常没有 user_id；用 userinfo 补 uid/name/email。
    val info = fetchUserInfo(token)
    return Result.success(
        base.copy(
            id = info?.id?.ifBlank { base.id } ?: base.id,
            name = info?.name?.ifBlank { base.name } ?: base.name,
            email = info?.email?.ifBlank { base.email } ?: base.email,
        ),
    )
}
```

说明：

- 不再保留“直接 PAT”分支。它即使偶尔能过 `/userinfo`，也会让后续额度/聊天拿到错误的 Bearer。
- `machine_id` 是官方 exchange 会带的字段；如果你的服务端不接受额外字段，删掉 `.put("machine_id", machineId)` 即可（Go 社区实现只发 `personal_token` 也能成功）。

#### C. `enrich()`（`:200-206`）

让 userinfo 的 uid 覆盖 `"qoder"` 占位：

```kotlin
private fun enrich(account: QoderAccount): QoderAccount {
    val info = fetchUserInfo(account.accessToken) ?: return account
    return account.copy(
        id = info.id.ifBlank { account.id },
        name = info.name.ifBlank { account.name },
        email = info.email.ifBlank { account.email },
    )
}
```

#### D. `fetchUsage()`（`:284-305`）

加三件事：

1. 到期前 5 分钟主动刷新；
2. 401 时刷新并重试一次；
3. 带上 `User-Agent`，并把刷新后的账号通过回调交给上层持久化。

替换为：

```kotlin
/**
 * 读取额度。
 *
 * `/api/v2/quota/usage` 走 openapi 的普通 Bearer，不需要 COSY 签名。
 * PAT 账号的 jt- 只有约 24 小时，所以这里必须处理刷新，否则第二天必 401。
 *
 * @param onAccountUpdated 刷新成功后回调，调用方应把它写回 QoderAccountStore。
 */
fun fetchUsage(
    account: QoderAccount,
    onAccountUpdated: (QoderAccount) -> Unit = {},
): QoderUsage {
    var current = account
    var didRefresh = false

    // 到期前 5 分钟主动刷新；expiresAt=0（未知）时交给 401 分支。
    val refreshBefore = System.currentTimeMillis() + 5 * 60_000L
    if (current.expiresAt in 1L..refreshBefore) {
        val renewed = refresh(current)
        if (renewed.accessToken != current.accessToken) {
            current = renewed
            didRefresh = true
            onAccountUpdated(renewed)
        }
    }

    var response = requestUsage(current)
    if (response.status == 401 && !didRefresh && current.refreshToken.isNotBlank()) {
        val renewed = refresh(current)
        if (renewed.accessToken != current.accessToken) {
            current = renewed
            onAccountUpdated(renewed)
            response = requestUsage(current)
        }
    }

    if (response.status !in 200..299) {
        throw IllegalStateException(
            "额度查询失败：HTTP ${response.status} ${response.body.take(160)}",
        )
    }
    val root = runCatching { JSONObject(response.body) }.getOrElse {
        throw IllegalStateException(
            "额度响应不是 JSON（前 40 字节：${response.body.take(40)}）",
        )
    }
    return QoderUsage(
        userQuota = bucket(child(root, "userQuota", "user_quota"), "total"),
        addOnQuota = bucket(child(root, "addOnQuota", "add_on_quota"), "total"),
        orgQuota = bucket(
            child(root, "orgResourcePackage", "org_resource_package", "sharedQuota", "shared_quota"),
            "cap",
        ),
        expiresAt = parseExpiry(root).takeIf { it in 1L..MAX_EXPIRY_MS } ?: 0L,
    )
}

private fun requestUsage(account: QoderAccount): Response = call(
    "${QoderConfig.OPENAPI_URL}/api/v2/quota/usage",
    "GET",
    mapOf(
        "Accept" to "application/json",
        "Authorization" to "Bearer ${account.accessToken}",
        "User-Agent" to QoderConfig.USER_AGENT,
    ),
)

private fun child(root: JSONObject, vararg names: String): JSONObject? {
    for (name in names) {
        root.optJSONObject(name)?.let { return it }
    }
    return null
}
```

在 `QoderConfig` 里加：

```kotlin
/** 官方 API 的 User-Agent；版本号跟 CLI bundle 对齐，不影响现有 COSY 签名。 */
const val USER_AGENT = "qoder/1.1.74"
```

> `CLI_VERSION` 可以暂时保留 `1.1.62`，因为聊天 COSY 签名已经用它验证过；只把 UA 换成 1.1.74 即可。若后续确认聊天也接受 1.1.74，再把两者统一。

在 `QoderClient` 末尾加：

```kotlin
private companion object {
    /** 2100-01-01；超过它的 expiresAt 视为“无期限/9999 哨兵”。 */
    const val MAX_EXPIRY_MS = 4_102_444_800_000L
}
```

`fetchUsage` 的调用方（`MainActivity.kt:897-911`）建议改成：

```kotlin
private fun refreshQoderUsage() {
    val account = state.qoderAccount ?: return
    lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                qoderClient.fetchUsage(account) { refreshed ->
                    // 回调在 IO 线程；这里只做持久化，不要碰 Compose state。
                    qoderStore.save(refreshed)
                }
            }
        }
        result.onSuccess { usage ->
            state = state.copy(
                qoderAccount = qoderStore.active() ?: account,
                qoderUsage = usage,
                qoderUsageError = "",
            )
        }.onFailure {
            state = state.copy(
                qoderUsageError = it.message?.take(120) ?: "额度查询失败",
            )
        }
    }
}
```

#### E. 解析兼容性（建议，不是首要故障）

如果实测 HTTP 200 但 `hasAny=false`，再补下面这些。官方字段是 camelCase，当前主解析基本正确；这些改动主要是为了老账号/CN 兼容和显示准确性。

`QoderQuota`（`QoderModels.kt:74-88`）增加：

```kotlin
data class QoderQuota(
    val total: Double? = null,
    val used: Double? = null,
    val remaining: Double? = null,
    val unit: String = "",
    /** 0..1 的使用比例；上游可能给 0.98 或 98，统一归一到 0..1。 */
    val percentage: Double? = null,
    /** 组织包是否真的可消费；其他桶默认 true。 */
    val available: Boolean = true,
) {
    val fraction: Float?
        get() {
            val ratio = percentage ?: run {
                val totalValue = total ?: return null
                if (totalValue <= 0.0) return null
                val usedValue = used ?: (totalValue - (remaining ?: 0.0))
                usedValue / totalValue
            }
            return ratio.toFloat().coerceIn(0f, 1f)
        }
}
```

`bucket()`（`:307-315`）替换为：

```kotlin
private fun bucket(json: JSONObject?, totalKey: String): QoderQuota? {
    if (json == null) return null
    val total = numberOrNull(json, totalKey) ?: numberOrNull(json, "total")
    val used = numberOrNull(json, "used")
    val remaining = numberOrNull(json, "remaining")
    val unit = json.optString("unit").ifBlank { "credits" }
    val percentage = numberOrNull(json, "percentage")?.let { raw ->
        if (raw > 1.0) raw / 100.0 else raw
    }
    val available = if (json.has("available")) json.optBoolean("available", true) else true
    if (total == null && used == null && remaining == null) return null
    return QoderQuota(
        total = total,
        used = used,
        remaining = remaining,
        unit = unit,
        percentage = percentage,
        available = available,
    )
}
```

组织包在 UI 里：

```kotlin
usage.orgQuota?.let {
    QuotaLine(if (it.available) "组织资源包" else "组织资源包（不可用）", it)
}
```

汇总剩余：

```kotlin
fun QoderUsage.totalRemaining(): Double =
    (userQuota?.remaining ?: 0.0) +
        (addOnQuota?.remaining ?: 0.0) +
        (orgQuota?.takeIf { it.available }?.remaining ?: 0.0)
```

`QoderUsage` 还可以加：

```kotlin
val totalUsagePercentage: Double? = null,
val isQuotaExceeded: Boolean = false,
```

解析时：

```kotlin
totalUsagePercentage = (numberOrNull(root, "totalUsagePercentage")
    ?: numberOrNull(root, "total_usage_percentage"))?.let { if (it > 1.0) it / 100.0 else it },
isQuotaExceeded = root.optBoolean("isQuotaExceeded", false) ||
    root.optBoolean("is_quota_exceeded", false),
```

> 注意：官方 Python 文档写 `totalUsagePercentage` 是 0–100，但 CLI 的实际 normalizer 同时兼容 0–1；上面统一成 0–1，UI 再乘 100。

---

## 5. 设备上怎么验证

### 5.1 准备 token

优先级从高到低：

1. 从 App 的账号备份导出 `lyra-qoder.json`，取 `accessToken` / `refreshToken`；
2. 临时在 `fetchUsage()` 里打印 token 前缀（只打前 3 位，别打全量）和 `expiresAt`；
3. 如果是 Qoder CLI 登录的账号，`~/.qoder/.auth/user` 里有凭据（AES 加密，不建议手工解）。

下面的命令都用环境变量，不要把 token 写进 shell history：

```bash
export QODER_TOKEN='dt-...'   # 或 jt-...
export QODER_UA='qoder/1.1.74'
```

### 5.2 直接查额度

```bash
curl -sS -D /data/data/com.termux/files/usr/tmp/qoder-quota.headers \
  -o /data/data/com.termux/files/usr/tmp/qoder-quota.json \
  'https://openapi.qoder.sh/api/v2/quota/usage' \
  -H "Authorization: Bearer $QODER_TOKEN" \
  -H 'Accept: application/json' \
  -H "User-Agent: $QODER_UA"

head -20 /data/data/com.termux/files/usr/tmp/qoder-quota.headers
python3 -m json.tool /data/data/com.termux/files/usr/tmp/qoder-quota.json | head -60
```

预期：

- `HTTP/2 200`
- `content-type: application/json`
- body 首字符是 `{`
- JSON 里有 `userId`、`userQuota`，可选 `addOnQuota`、`orgResourcePackage`

### 5.3 按结果排查

| 现象 | 判断 | 下一步 |
|---|---|---|
| `401` + `x-auth-error: TOKEN_INVALID` | token 过期/类型不对 | 看 5.4 刷新 |
| `401` + `TOKEN_EXPIRE` | 同上 | 同上 |
| `403` | WAF/UA/区域问题 | 换 `User-Agent: qoder/1.1.74` 再试；确认走的是 `openapi.qoder.sh` |
| `200` 但首字符不是 `{` | 响应加密/压缩 | `file`/`xxd` 看格式；普通账号不应出现 |
| `200` + JSON 但无 `userQuota` | 字段结构变化/企业展示 | 先看是否有 `data` 包裹或 `displayMode` |
| `200` + `userQuota.total=0` | 免费账号，额度可能在 `addOnQuota` | 看加购包 |
| `200` + `orgResourcePackage.available=false` | 组织包当前不可用 | 单独展示，不计入总剩余 |

### 5.4 刷新 token

**设备码账号（`dt-` / `drt-`）：**

```bash
curl -sS -X POST 'https://openapi.qoder.sh/api/v1/deviceToken/refresh' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H "User-Agent: $QODER_UA" \
  -d "{\"refresh_token\":\"$QODER_REFRESH\",\"machine_id\":\"$QODER_MACHINE_ID\",\"machine_type\":\"5\"}" \
  | python3 -m json.tool
```

关键：响应字段是 **`device_token`**，不是 `token`。刷新后还会返回新的 `refresh_token`，必须持久化，否则旧的 `drt-` 可能被轮换失效。

**PAT 账号（`jt-` / `jrt-`）：**

```bash
curl -sS -X POST 'https://openapi.qoder.sh/api/v1/jobToken/refresh' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H "User-Agent: $QODER_UA" \
  -d "{\"refresh_token\":\"$QODER_REFRESH\"}" \
  | python3 -m json.tool
```

响应字段是 `token`（新 `jt-`）和新的 `refresh_token`（新 `jrt-`）。

**PAT 换 job token：**

```bash
curl -sS -X POST 'https://openapi.qoder.sh/api/v1/jobToken/exchange' \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json' \
  -H "User-Agent: $QODER_UA" \
  -d "{\"personal_token\":\"$QODER_PAT\",\"machine_id\":\"$QODER_MACHINE_ID\"}" \
  | python3 -m json.tool
```

响应里的 `token` 就是 `jt-`，拿它去查额度。

### 5.5 验证 UA 是否影响

同一 token、同一路径，只换 UA：

```bash
for ua in 'Dalvik/2.1.0 (Linux; U; Android 14)' 'qoder/1.1.74'; do
  echo "--- $ua"
  curl -sS -o /dev/null -w '%{http_code}\n' \
    'https://openapi.qoder.sh/api/v2/quota/usage' \
    -H "Authorization: Bearer $QODER_TOKEN" \
    -H 'Accept: application/json' \
    -H "User-Agent: $ua"
done
```

如果两个都 200，UA 不是问题；如果只有 `qoder/...` 是 200，就必须在 `requestUsage()` 里加 UA。

### 5.6 在 App 内做一次性诊断

如果不想把 token 导出来，临时在 `fetchUsage()` 加：

```kotlin
android.util.Log.d(
    "QoderQuota",
    "tokenPrefix=${account.accessToken.take(3)} " +
        "expiresAt=${account.expiresAt} now=${System.currentTimeMillis()} " +
        "status=${response.status} body=${response.body.take(300)}",
)
```

只打 token 前缀，不要打完整 token。`logcat` 里看：

- `tokenPrefix=jt-` + `expiresAt` 已过 → 刷新问题；
- `tokenPrefix=pt-` → PAT 直连 bug；
- `status=401` → 认证；
- `status=200 body={` 但 UI 空 → 解析；
- `status=200 body=` 非 `{` → 加密/压缩。

---

## 6. 仍需真机确认的点

1. **你的账号当前 token 前缀是 `dt-` 还是 `jt-`，以及 `expiresAt` 是否已经过去。**
   这决定了是“刷新 bug”还是“解析 bug”。从代码看，PAT 账号 24 小时后必现；设备码账号 30 天后才现。

2. **`/api/v2/quota/usage` 对 `dt-` 的接受度。**
   公开的 Global OAuth 实测脚本用 `dt-` 查过该接口并成功；但你的账号可能是特殊组织/企业策略，需要实测。若 `dt-` 401，而 `jt-` 200，说明该账号需要走 PAT exchange。

3. **`totalUsagePercentage` 的实际量纲（0–1 还是 0–100）。**
   官方 SDK 文档写 0–100，CLI normalizer 兼容 0–1；建议代码统一归一，不依赖单一文档。

4. **组织包 `available=false` 时 `remaining` 是否仍可能大于 0。**
   社区样例里 `available=false` 且 `remaining=0`；但逻辑上要以 `available` 为准，不能只看 `remaining`。

5. **`expiresAt` 的 9999 哨兵。**
   社区项目明确提到无期限套餐会填 `9999-12-31`。当前 `parseExpiry` 会把它显示成 9999 年，建议过滤。

6. **PAT 的长期无感刷新。**
   当前 `QoderAccount` 没有保存 `pt-`。`jrt-` 约 48 小时后，`jobToken/refresh` 会失败，只能让用户重新粘贴 PAT。
   如果要长期无感，需要：
   - 在 `QoderAccount` 增加 `personalToken` 字段；
   - 在 `QoderAccountStore` 持久化；
   - 刷新失败时用 `pt-` 重新 exchange；
   - **安全提醒**：PAT 是长期凭据，明文写 `lyra-qoder.json` 风险高于短期 token。至少用 Android Keystore/EncryptedSharedPreferences 加密，或明确告知用户“重新登录”而不存 PAT。

7. **响应加密。**
   普通账号返回明文 JSON。若你的账号命中灰度加密，`fetchUsage` 会抛“额度响应不是 JSON”。此时需要复刻 CLI 的 `decrypt_server_response`（WASM/AES-GCM），成本较高；先确认响应首字节再决定，不要盲目加解密代码。

8. **`/sash/api/v2/me/usage` 的 `enterprise` 模式。**
   企业账号可能只返回 `detailUrl`，没有数字额度；这时 UI 应显示“请到企业控制台查看”，而不是“没有额度”。

---

## 7. 参考来源

### 7.1 CLI bundle 内定位

在 `qodercli.js` 中搜索以下字面量/函数名：

| 内容 | 说明 |
|---|---|
| `xEg="/api/v2/quota/usage"` | 额度路径常量 |
| `DEg="/api/v2/user/plan"` | 套餐路径 |
| `yEg="/api/v3/user/status"` | 状态路径 |
| `PEg="/sash/api/v2/me/usage"` | 组织展示路径 |
| `fetchQuotaUsage()` | 旧版额度请求函数，明确只发 `Accept` + `Authorization` |
| `normalizeQuotaUsage()` | 顶层字段归一化，兼容 camelCase/snake_case |
| `r8i()` | `userQuota` 解析：`total` / `used` / `remaining` / `percentage` / `unit` |
| `Jsl()` | `orgResourcePackage` 解析：`used` / `cap` / `remaining` / `percentage` / `available` / `unit` |
| `Ysl()` | `dedicatedResourcePackages` 解析 |
| `Nle.prod.openapi` | 端点表：国际版 openapi = `https://openapi.qoder.sh` |
| `kJ="openapi.qoder.sh"` | 国际版域名常量 |
| `withResponseDecryption()` | 额度请求的 best-effort 解密标记；`Wv()` 失败会回退原文 |

### 7.2 官方文档

- Qoder CLI 成本与用量（字段表）：
  <https://docs.qoder.com/cli/sdk/cost-usage>
- Qoder CLI 用量面板：
  <https://docs.qoder.com/cli/usage>
- Qoder CLI 登录与认证：
  <https://docs.qoder.com/cli/authentication>
- TypeScript SDK `UsageInfo` 参考：
  <https://docs.qoder.com/zh/cli/sdk/references-typescript>
- Python SDK `UsageInfo` 参考：
  <https://docs.qoder.com/zh/cli/sdk/references-python>

### 7.3 社区实测/实现

- `EchoPing07/Qoder-2API-Go`：`auth/auth.go` 的 `FetchQuotaUsage`、`QuotaUsage`、`OrgResourcePackage`，以及 `auth/auth_test.go` 里的 Teams 响应样例。
- `simonsmh/pi-provider-qoder`：`src/usage.ts` 的全局/CN 额度解析，只发 `Authorization` + `Accept` + `User-Agent`。
- `Sliverkiss/cpa-plugin` 的 `KNOWLEDGE.md`：Token 能力矩阵，明确 `pt-` 不能直接作为 Bearer，`jt-` / `dt-` 可以查额度。
- 9router Qoder 逆向分析：device token 约 30 天、`/api/v2/quota/usage` 的实际请求头与字段。
- 公开 Global OAuth 实测脚本：用 `dt-` 直接查 `/api/v2/quota/usage` 并读取 `userQuota.total/remaining`。

---

## 8. 一句话行动清单

1. 先按 5.2 用现有 token 打一次接口，确认是 401 还是 200。
2. 如果是 401：先修 `refresh()` 的端点和字段，再在 `fetchUsage()` 里加“到期前刷新 + 401 重试 + 持久化”。
3. 如果是 200 但 UI 空：补 snake_case、`percentage`、`available`、`totalUsagePercentage`，并检查 `hasAny`。
4. 如果 body 不是 JSON：再考虑响应解密，不要一上来就复刻 WASM。
5. 路径、域名、COSY 签名都不要动；这三个目前是对的。
