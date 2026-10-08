# AGENTS.md — TokenHeat

Single-module Android app (`:app`, `com.tokenheat`). Kotlin 2.0.21, AGP 8.3.2, Compose BOM `2025.05.00`, `compileSdk/targetSdk 35`, `minSdk 26`, JDK 21. No tests, no lint/detekt config. CI (`.github/workflows/build.yml`) runs `assembleDebug` on push/PR and uploads the APK.

## Build

Requires JDK 21 + Android SDK (platform 35, build-tools). Gradle via wrapper only.

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug   # APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- `local.properties` is git-ignored; never commit it.
- Release signing reads root `keystore.properties` (also git-ignored, plus `*.jks`/`*.keystore`). Missing file = unsigned release build still compiles, do not "fix" by hardcoding signing.
- No unit tests exist (`app/src/main` only). Verify with `assembleDebug` (or `assembleRelease` for signing path).

## Architecture

- `ui/` (`HubApp.kt`, `HubState.kt`, `DashboardScreen.kt`, `ChatScreen.kt`, `Screens.kt`, `ModelList.kt`, `BrandLogos.kt`, `Components.kt`, `Theme.kt`) — pure Compose UI with Shadcn-like Zinc low-saturation palette and non-linear icon styling; state in `HubState`. Tab 0 为首页数据大屏（`DashboardScreen.kt`，含实时 Uptime 计数、Token 吞吐、支持 52 周全年/半年/3个月跨度无缝切换的 GitHub 风格活跃墙、正确的调用频次颜色递进阶梯映射、Top 模型分布）；Tab 1 为账号设置（`CredentialScreen`，供应商选择器与当前凭证卡片展示专属供应商图标，原独立积分 Tab 移除，下沉为支持余额供应商的内嵌卡片，不支持余额的供应商不展示余额卡片，集成 `.json.gz` 账号备份包导出/导入）；Tab 2 为内置模型聊天（`ChatScreen.kt`，支持在现有模型池中快速搜索/分类选择、展示自适应品牌徽标、流式 SSE 逐字打字机回复、多轮对话历史维护、终止与重试、一键复制、清空会话；底层直连本地 `BridgeServer` 自动路由与轮询，对话请求实时计入数据大屏和调用记录）；Tab 3 为服务与模型（`BridgeScreen`）；Tab 4 为调用记录（`CallsScreen`，增强状态码健康度分布条、Top 5 模型消耗榜、状态筛选器）。模型图标与供应商图标严格分治解耦，所有图标均配备官方 Light 与 Dark 亮暗两份资产（`res/drawable/ic_model_*_{light,dark}.png` 与 `res/drawable/ic_provider_*_{light,dark}.png`），随应用/系统深浅色主题自适应切换，模型解析自动剥离供应商路由前缀，杜绝供应商冒充模型图标，未知模型回退 Bot 图标。
- `MainActivity.kt` — wiring: login polling, `loadModels` / `loadBalance` / `doCheckin` / `checkinAllAccounts`, bridge start/stop, call-log reload, 聊天流式请求与会话状态管理, 账号备份导出/导入文件选择器及 FileProvider 共享。
- `data/AccountBackup.kt` — 账号备份管理器：支持全平台账号配置一键序列化为 `.json.gz` 压缩包及反序列化安全合并导入。
- `data/AGLogin.kt` — Antigravity CLI Google OAuth: loopback callback server on `localhost:51121`。Google Token Endpoint 强制要求提供 `client_secret`（以 XOR 掩码动态还原避免 GitHub Push Protection 误报）完成 exchange 和 refresh。
- `proto/AGUpstreamClient.kt` — Antigravity CLI 适配器：兼容 Google 模型端点返回的 `models` 为 JSONObject 字典遍历与 JSONArray 数组，自动清除 `models/` 前缀，多端点回退。
- `proto/Wire.kt` — 免费模型判定逻辑支持 `rates`、`badges` 标签与 `-free` 后缀识别，修复 OpenCode Zen 免费模型分类归属。
- `ui/ModelList.kt` — 内部模型条目采用 `Column` 展开，避免嵌套在 `BridgeScreen` 的 `LazyColumn` 中引发 Compose 无限高度测量崩溃 (IllegalStateException)。

## Gotchas

- Upstream hosts are private (`copilot.tencent.com`, `www.codebuddy.cn`, `www.workbuddy.ai`, `zcode.z.ai`, `api.z.ai`), not public APIs — breakage after upstream change is expected; check `Wire`/`UpstreamClient`/`ZUpstreamClient`/`ZenUpstreamClient` first. Zen's `/v1/models` is public, but its free list rotates: refresh curated `CHAT_IDS`/`FREE_IDS` when models vanish or new `*-free` ids appear (`responses`/`messages` ids must stay excluded). Zen Free Tier 强依赖客户端指纹校验（2026年9月中旬上线风控：服务端强制要求 `User-Agent: opencode/<semver>`、`x-opencode-session: ses_<12hex><14base62>`、`x-opencode-client: cli` 等头），仅限免模型受此限制，Zen 平台按量付费/正式模型不受影响；TokenHeat 在 `ZenUpstreamClient` 中补齐了全套官方指纹头，并在 `Wire.displayError` 中对该限制做明确友好的中文引导。
- `Credential.expiresAt` seconds vs `Date` millis: `MainActivity.formatExpiry` normalizes `< 1e12` as seconds. Keep new time code in the same convention.
- Check-in of multiple accounts runs sequentially (upstream rate-limits); do not parallelize.
- `BridgeServer.KIND_STATUS` maps `ErrorKind` → HTTP status (401 keeps `(http 401)` note); preserve mapping when editing error paths.
- SSE usage parsing scans trailing 8 KB for the last `"usage"` block — usage object is nested, brace-depth matched, not first-`}`.
