# TokenHeat 项目约定

## 架构

- 单 Activity + Jetpack Compose：`MainActivity` → `ClaudeTheme` → `HubApp`。
- 底部五个标签：概览、账号、服务、记录、更多，由 `ui/HubApp.kt` 的 `HubTab` 与 `MochiTabBar` 驱动。
- 主题与外观设置集中在 `ui/ClaudeTheme.kt`：
  - `LyraAppearance` / `LocalLyraAppearance` / `rememberLyraAppearance()`；
  - 持久化到 SharedPreferences `lyra_appearance`；
  - 默认 Nothing 外观、跟随系统明暗、MochiGraphics High。
- `ui/MoreTab.kt` 是「更多」页：根页承载应用元信息与设置入口，UI 设置、关于、设置、许可证用 `MochiNavHost` 做二级页；`ui/LyraWidgets.kt` 是设置/分组控件。
- 官方 Nothing 外观的源码位于 `ui/feice/mochi/nothing/`，由 mochi-nothing 模块 vendor 而来（该模块未发布 AAR）。点阵/显示字体与图标资源在 `res/font`、`res/drawable`，许可证在 `assets/licenses` 与 `assets/nothing`。

## 约定

- UI 组件统一使用 `ui.feice.mochi.*`，不要引入 `androidx.compose.material3`。
- 列表使用 `MochiListSection` / `MochiListRow`，弹层使用 `MochiDialog` / `MochiSheet`。
- 浮起的 `MochiTabBar` 会盖住页面底部：页面内容需预留 `LyraBottomBarClearance`（并加上导航栏 inset）。
- 依赖 `mochi-compose:0.1.180`（JitPack）；该版本的源码与 `mochi-src` 参考目录一致。
- 构建需要 JDK 17。本机 Termux 环境没有 JDK，不能本地编译；改动 Mochi API 前请先在 `~/mochi-src` 或已发布源码中核对签名。
