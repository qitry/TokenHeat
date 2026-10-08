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

- `ui/` (`HubApp.kt`, `HubState.kt`, `DashboardScreen.kt`, `ChatScreen.kt`, `MarkdownView.kt`, `Screens.kt`, `ModelList.kt`, `BrandLogos.kt`, `Components.kt`, `Theme.kt`, `LucideIcons.kt`) — pure Compose UI with Shadcn-like Zinc low-saturation palette and non-linear icon styling; 全面采用统一的 Lucide Icons 矢量图标规范（`LucideIcons.kt` 与 43 个 `res/drawable/ic_lucide_*.xml`，彻底剥离 Material Icons）；全 APP 图标引入指数 $n=5$ 超椭圆（`IconSquircleShape`），卡片、容器与圆角引入指数 $n=6$ 超椭圆连续曲率（`SquircleCornerShape`、`SquirclePillShape`）；全局采用 iOS / HarmonyOS 灵动风格 56dp 悬浮式药丸底栏（`FloatingNavigationBar`，即时高亮响应并严格避让系统底部小白条）；首页顶栏根据本地时间动态展示亲切问候语（早/中/下午/晚/凌晨）；`Scaffold` 取消内部默认 insets，彻底根治键盘弹起时输入框双重 padding 悬空过高的问题；Tab 0 为首页数据大屏（`DashboardScreen.kt`，含实时 Uptime 计数、Token 吞吐、支持 52 周全年/半年/3个月跨度无缝切换的 GitHub 风格活跃墙、正确的调用频次颜色递进阶梯映射、Top 模型分布）；Tab 1 为账号设置（`CredentialScreen`，供应商选择器优化为 12dp 统一 Shadcn 悬浮菜单，登录授权状态由居中弹窗升级为贴合小白条的 `ModalBottomSheet` 大上拉抽屉，当前凭证卡片展示专属供应商图标，仅在已登录账号时展示每日签到与余额卡片，彻底移除冗余备份卡片）；Tab 2 为内置模型聊天（`ChatScreen.kt`，深度融合 Kelivo 纯净美学与 Codex、iOS/HarmonyOS 沉浸交互：点击聊天默认进入会话列表选择主界面 `ConversationsListScreen`，支持搜索过滤、置顶优先排序、极简化单行标题与时间戳排布；点击进入具体会话聊天详情界面，全局底栏与顶栏自动隐藏实现彻底沉浸；系统返回键精准拦截回退至会话列表；顶部严格由三部分组成：返回箭头、会话标题（点击重命名，带置顶标）、汉堡导航下拉菜单；AI 回复无气泡无头像，通栏渲染纯净 Markdown 与多语言代码高亮，支持 `>` 引用内嵌套代码块、加粗行内代码（`**`code`**`）、分割线 `---` 与圆角行内代码标签；连续工具调用聚拢为“操作组”卡片（`OperationGroupCard`，首行显示轮次概览，次行显示实时工具状态，支持折叠查看详细入参和返回结果），多轮过渡阶段不显示底部操作条，仅在最终回答展示重新生成与复制栏；思考链卡片支持流式期间用户自由折叠/展开；流式生成时若用户上滑翻阅历史记录，基于 `!listState.canScrollForward` 精准停靠，不强制跳底；输入框采用 Zinc 纯色实体表面并撤销高斯模糊与暗角阴影，采用单层 `navigationBarsPadding().imePadding()`，发送按钮缩减至精致 26dp，联网切换为纯图标，思考强度与模型切换重构为基于按钮向上弹出的轻量小上拉菜单（`DropdownMenu`），思考滑块支持物理阻尼动效，模型选择按钮移除硬边框并新增各模型动态最大上下文窗口（2M/1M/200k/128k/64k）的环形进度条 `ContextUsageIndicator`）；Tab 3 为服务与模型（`BridgeScreen`）；Tab 4 为调用记录（`CallsScreen`，增强状态码健康度分布条、Top 5 模型消耗榜、状态筛选器）。模型图标与供应商图标严格分治解耦，所有图标均配备官方 Light 与 Dark 亮暗两份资产，自适应切换。
- `data/ChatConversationStore.kt` — 会话历史持久化存储：基于本地内部存储持久化所有对话与消息记录，包含置顶（`isPinned`）与归档（`isArchived`）状态，首条提问自动拟定会话标题，流式回复全流程实时状态同步与本地落盘，支持单条消息实时删除与同步落盘。
- `mcp/McpClient.kt` — Model Context Protocol 引擎：内置 Kelivo 风格基础内置 Tools 闭环（无需 API Key 的 `web_fetch` 网页清洗提取、`web_search` 智能免配置检索回退、`get_current_time` 系统时间与 `calculator` 安全表达式求值，支持入参 Markdown 代码块剥离与多别名容错解析）；支持 Exa AI 神经网络搜索及 Streamable HTTP、Server-Sent Events (SSE) 及 RFC 6455 原生 WebSocket 三种传输协议的任意远程 MCP 服务器工具枚举与远程 JSON-RPC 执行。
- `MainActivity.kt` — wiring: login polling, `loadModels` / `loadBalance` / `doCheckin` / `checkinAllAccounts`, bridge start/stop, call-log reload, 多会话创建/切换/重命名/删除及本地持久化, 聊天消息单条删除及会话同步, 聊天流式请求与会话状态管理（引入 `ChatToolCallAccumulator` 增量拼接 SSE 分片 `tool_calls.arguments` 解决工具传值丢失问题，完善多轮工具循环调用链）, 附件选择器及多模态 Base64 编码, 思考强度与 MCP 设置持久化, 账号备份导出/导入文件选择器及 FileProvider 共享。
- `res/xml/network_security_config.xml` & `AndroidManifest.xml` — 放行 `127.0.0.1` 和 `localhost` 的明文 HTTP 传输（`cleartextTrafficPermitted="true"`），解决 Android 9+ 对 loopback 明文流量的限制（`Cleartext HTTP traffic to 127.0.0.1 not permitted`）。
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
