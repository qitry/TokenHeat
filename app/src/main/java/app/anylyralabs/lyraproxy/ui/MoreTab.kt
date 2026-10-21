// Portions derived from mochi-compose example (Apache-2.0).
// Copyright 2026, mochi-compose contributors
// SPDX-License-Identifier: Apache-2.0

package app.anylyralabs.lyraproxy.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiStepSlider
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.MochiThemeRevealState
import ui.feice.mochi.effect.overscroll.MochiScrollDefaults
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.navigation.MochiBackButton
import app.anylyralabs.lyraproxy.ui.chat.ChatScreen
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.MochiScrollEdgeDefaults
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.navigation.MochiTitleBarDefaults
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.picker.MochiSeedPalette
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.ColorSchemeMode
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiGraphics
import ui.feice.mochi.theme.MochiGraphicsLevel
import ui.feice.mochi.theme.MochiHdr
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.PaletteStyle
import ui.feice.mochi.theme.emphasized

/** 有揭开转场就揭开，没有就直接改。 */
private fun MochiThemeRevealState?.redress(change: () -> Unit) {
    if (this == null) change() else reveal(change = change)
}

/** 换明暗：深浅真的变了才从手指处揭开；没变就直接改，颜色自己插值过去。 */
private fun MochiThemeRevealState?.rebright(fromDark: Boolean, toDark: Boolean, change: () -> Unit) {
    if (fromDark != toDark) redress(change) else change()
}

/** 换整套风格（Mochi ↔ Nothing）：原位的内容模糊转场，颜色照常插值，不从手指扩散。 */
private fun MochiThemeRevealState?.restyle(change: () -> Unit) {
    if (this == null) change() else crossfade(change)
}

/**
 * 「更多」标签：应用元信息、UI 设置、关于与软件设置。
 *
 * 二级页面用 MochiNavHost 侧推，返回手势 / 返回按钮都交给库的导航器处理；
 * 根页与子页各自负责状态栏、底部标签栏的留白。
 */
@Composable
internal fun MoreTab(
    state: HubState,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onClearCalls: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportFromText: () -> Unit,
) {
    val nav = rememberMochiNavigator("more")
    MochiNavHost(navigator = nav, modifier = Modifier.fillMaxSize()) { route ->
        when (route) {
            "ui" -> UiSettingsPage()
            "chat" -> ChatScreen(
                port = state.port,
                secret = state.secret,
                models = (if (state.provider == GatewayProvider.QODER) state.qoderModels else state.models)
                    .map { it.id },
                onBack = { nav.pop() },
                modifier = Modifier.fillMaxSize(),
            )
            "about" -> AboutPage(onOpenLicenses = { nav.push("licenses") })
            "licenses" -> LicensesPage()
            "settings" -> SettingsPage(
                state = state,
                onRequestNotifications = onRequestNotifications,
                onRequestBatteryExemption = onRequestBatteryExemption,
                onClearCalls = onClearCalls,
                onExportBackup = onExportBackup,
                onImportBackup = onImportBackup,
                onImportFromText = onImportFromText,
            )
            else -> MoreHome(
                style = LocalLyraAppearance.current?.style,
                onOpenUi = { nav.push("ui") },
                onOpenAbout = { nav.push("about") },
                onOpenSettings = { nav.push("settings") },
                onOpenChat = { nav.push("chat") },
            )
        }
    }
}

/** 根页：应用是谁，以及三个入口。 */
@Composable
private fun MoreHome(
    style: LyraStyle?,
    onOpenUi: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChat: () -> Unit,
) {
    MoreListPage(title = "更多", reserveTabBar = true) {
        item(key = "identity") { AppIdentity() }

        item(key = "personal") {
            MochiListSection(header = "个性化") {
                MochiListRow(
                    title = "UI 设置",
                    subtitle = "外观、配色、圆角曲线与图形档位",
                    value = style?.let { if (it == LyraStyle.Nothing) "Nothing" else "Mochi" },
                    onClick = onOpenUi,
                    chevron = true,
                )
            }
        }

        item(key = "chat") {
            MochiListSection(header = "工具") {
                MochiListRow(
                    title = "模型聊天",
                    subtitle = "选一个模型直接聊，支持思维链与网络检索",
                    onClick = onOpenChat,
                    chevron = true,
                )
            }
        }

        item(key = "app") {
            MochiListSection(header = "应用") {
                MochiListRow(
                    title = "关于",
                    subtitle = "版本、开源组件与许可证",
                    onClick = onOpenAbout,
                    chevron = true,
                )
                MochiListRow(
                    title = "设置",
                    subtitle = "网关端口、通知、保活与数据",
                    onClick = onOpenSettings,
                    chevron = true,
                )
            }
        }
    }
}

/** 品牌字标 + 名字 + 版本；不用图片资源，深色模式下也不会消失。 */
@Composable
private fun AppIdentity() {
    val version = rememberAppVersion()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(MochiTheme.colorScheme.onSurface, MochiRoundedRectangle(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "L",
                style = MochiTheme.textStyles.displaySmall,
                color = MochiTheme.colorScheme.surface,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "LyraProxy",
                style = MochiTheme.textStyles.headlineLarge,
                color = MochiTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "版本 $version",
                style = MochiTheme.textStyles.footnote,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "本机运行的 OpenAI 兼容网关",
                style = MochiTheme.textStyles.footnote,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 二级页的通用骨架：浮起的顶栏 + 分组列表。
 * @param back 二级页显示返回按钮；根页不显示
 * @param reserveTabBar 只有根页需要给浮起的标签栏留高度；二级页只留导航栏安全区
 */
@Composable
private fun MoreListPage(
    title: String,
    back: Boolean = false,
    reserveTabBar: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val progress = MochiScrollEdgeDefaults.progress(listState)

    Box(Modifier.fillMaxSize().background(MochiDefaults.groupedBackground())) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = top + MochiTitleBarDefaults.Height + 12.dp,
                bottom = bottom + if (reserveTabBar) LyraBottomBarClearance else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
            content = content,
        )
        MoreTitleBar(title = title, back = back, backgroundProgress = progress)
    }
}

/** 根页不显示返回；二级页把标题栏左侧交给库的返回按钮。 */
@Composable
private fun MoreTitleBar(
    title: String,
    back: Boolean,
    backgroundProgress: () -> Float,
) {
    if (back) {
        MochiTitleBar(
            title,
            navigationIcon = { MochiBackButton() },
            backgroundProgress = backgroundProgress,
        )
    } else {
        MochiTitleBar(title, backgroundProgress = backgroundProgress)
    }
}

/**
 * UI 设置：外观、配色、主题色、色板、表面层级、圆角曲线、图形档位与实验性 HDR。
 * 结构照搬 demo 的主题页，顶栏浮在滚动内容上方。
 */
@Composable
private fun UiSettingsPage() {
    val appearance = LocalLyraAppearance.current ?: return
    val reveal = appearance.reveal
    val systemDark = isSystemInDarkTheme()
    val nowDark = MochiTheme.colorScheme.isDark
    val scroll = rememberScrollState()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(MochiDefaults.groupedBackground())) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll, flingBehavior = MochiScrollDefaults.flingBehavior())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = top + MochiTitleBarDefaults.Height + 12.dp,
                    bottom = bottom,
                ),
            verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing),
        ) {
            LyraSettingsSection("外观") {
                toggles(
                    "主题",
                    listOf("Mochi" to LyraStyle.Mochi, "Nothing" to LyraStyle.Nothing),
                    appearance.style,
                ) {
                    reveal.restyle { appearance.style = it }
                }
                MochiListRow(
                    "衬线体标题",
                    subtitle = "大标题和导航标题用系统衬线体（宋体），正文不变",
                    trailing = { MochiSwitch(appearance.serif, { on -> reveal.restyle { appearance.serif = on } }) },
                )
            }

            LyraSettingsSection("配色") {
                if (appearance.style == LyraStyle.Nothing) {
                    toggles(
                        "模式",
                        listOf(
                            "跟随系统" to ColorSchemeMode.System,
                            "浅" to ColorSchemeMode.Light,
                            "深" to ColorSchemeMode.Dark,
                        ),
                        appearance.mode,
                    ) {
                        reveal.rebright(nowDark, it.isDark(systemDark)) { appearance.mode = it }
                    }
                } else {
                    toggles(
                        "模式",
                        listOf(
                            "系统取色" to ColorSchemeMode.MonetSystem,
                            "跟随系统" to ColorSchemeMode.System,
                            "浅" to ColorSchemeMode.Light,
                            "深" to ColorSchemeMode.Dark,
                        ),
                        appearance.controller.colorSchemeMode,
                    ) {
                        reveal.rebright(nowDark, it.isDark(systemDark)) { appearance.controller.colorSchemeMode = it }
                    }
                    toggles(
                        "取色算法",
                        listOf(
                            "Tonal" to PaletteStyle.TonalSpot,
                            "Expressive" to PaletteStyle.Expressive,
                            "Vibrant" to PaletteStyle.Vibrant,
                        ),
                        appearance.controller.paletteStyle,
                    ) {
                        appearance.controller.paletteStyle = it
                    }
                }
            }

            if (appearance.style == LyraStyle.Mochi) {
                LyraSection(
                    "主题色",
                    footer = "按 MD3 规则从种子色生成整套配色，色块上是生成的主色、辅助色和第三色。选了色块后，「系统取色」换成同样明暗的静态配色。",
                ) {
                    val mode = appearance.controller.colorSchemeMode
                    val monet = mode == ColorSchemeMode.MonetSystem ||
                        mode == ColorSchemeMode.MonetLight ||
                        mode == ColorSchemeMode.MonetDark
                    MochiSeedPalette(
                        selected = if (monet) null else appearance.controller.keyColor,
                        onSelect = { seed ->
                            appearance.controller.keyColor = seed
                            appearance.controller.colorSchemeMode = when (appearance.controller.colorSchemeMode) {
                                ColorSchemeMode.MonetSystem -> ColorSchemeMode.System
                                ColorSchemeMode.MonetLight -> ColorSchemeMode.Light
                                ColorSchemeMode.MonetDark -> ColorSchemeMode.Dark
                                else -> appearance.controller.colorSchemeMode
                            }
                        },
                        style = appearance.controller.paletteStyle,
                    )
                }
            }

            ThemeColorPalette("主题色板")

            ThemeSurfaceLevels("表面层级")

            CornerCurveSection("圆角曲线")

            GraphicsSettings("图形")

            LyraSettingsSection(
                "实验性",
                footer = if (MochiHdr.supported) {
                    "按压高光、边缘流光用超过 SDR 白的亮度画（全局生效）。屏幕会提高背光，更耗电。"
                } else {
                    "HDR 高光需要 Android 14 以上和支持 HDR 的屏幕，这台设备上不可用。"
                },
            ) {
                MochiListRow("HDR 高光", trailing = {
                    MochiSwitch(
                        appearance.hdr && MochiHdr.supported,
                        { appearance.hdr = it },
                        enabled = MochiHdr.supported,
                    )
                })
            }
        }

        MochiTitleBar(
            "UI 设置",
            navigationIcon = { MochiBackButton() },
            backgroundProgress = MochiScrollEdgeDefaults.progress(scroll),
        )
    }
}

/** 关于：版本、开源组件、许可证入口与仓库地址文本。 */
@Composable
private fun AboutPage(onOpenLicenses: () -> Unit) {
    val version = rememberAppVersion()
    MoreListPage(title = "关于", back = true) {
        item(key = "identity") { AppIdentity() }

        item(key = "version") {
            MochiListSection(header = "版本") {
                MochiListRow(title = "版本号", value = version)
                MochiListRow(title = "软件包", subtitle = "app.anylyralabs.lyraproxy")
            }
        }

        item(key = "oss") {
            MochiListSection(
                header = "开源组件",
                footer = "完整许可文本与资源出处见「许可证」。",
            ) {
                MochiListRow(
                    title = "mochi-compose",
                    subtitle = "Apache License 2.0 · 界面组件、动效与导航",
                )
                MochiListRow(
                    title = "Nothing OS 资源",
                    subtitle = "点阵字体与图标 · 仅本地集成，权利归原所有者",
                )
                MochiListRow(
                    title = "HarmonyOS Sans",
                    subtitle = "正文与标题字体",
                )
                MochiListRow(
                    title = "Material Design Icons",
                    subtitle = "Apache License 2.0 · 界面图标",
                )
                MochiListRow(
                    title = "Inter",
                    subtitle = "SIL Open Font License 1.1",
                )
                MochiListRow(
                    title = "Pixelarticons",
                    subtitle = "MIT License",
                )
            }
        }

        item(key = "links") {
            MochiListSection(header = "链接") {
                MochiListRow(
                    title = "许可证",
                    subtitle = "第三方组件、字体与图标许可",
                    onClick = onOpenLicenses,
                    chevron = true,
                )
                MochiListRow(
                    title = "GitHub",
                    trailing = { MonoValue("https://github.com/qitry/TokenHeat") },
                )
            }
        }
    }
}

/** 许可证清单：只列随包资源与组件，不在这里内嵌长文本。 */
@Composable
private fun LicensesPage() {
    MoreListPage(title = "许可证", back = true) {
        item(key = "app") {
            MochiListSection(header = "本应用") {
                MochiListRow(
                    title = "LyraProxy",
                    subtitle = "MIT License · Copyright (c) 2026 WB Hub contributors",
                )
            }
        }

        item(key = "components") {
            MochiListSection(header = "第三方组件") {
                MochiListRow(title = "mochi-compose", subtitle = "Apache License 2.0")
                MochiListRow(title = "AndroidX / Jetpack Compose", subtitle = "Apache License 2.0")
                MochiListRow(title = "Capsule", subtitle = "Apache License 2.0 · 连续圆角")
            }
        }

        item(key = "assets") {
            MochiListSection(
                header = "随包资源",
                footer = "资源清单与出处见 assets/nothing/ASSET_NOTICES.md。",
            ) {
                MochiListRow(title = "Inter", subtitle = "assets/licenses/Inter-OFL.txt")
                MochiListRow(title = "Material Design Icons", subtitle = "assets/licenses/MaterialDesignIcons-LICENSE.txt")
                MochiListRow(title = "NDOT FiraCode Nerd", subtitle = "assets/licenses/NDOT-FiraCode-Nerd-OFL.txt · MIT")
                MochiListRow(title = "Pixelarticons", subtitle = "assets/licenses/Pixelarticons-MIT.txt")
            }
        }
    }
}

/**
 * 软件设置。
 *
 * 能接的回调直接接上；「默认端口」「启动时自动开启服务」目前没有独立的持久化
 * 读写回调，先按只读占位显示，等启动流程支持后再换成可编辑控件。
 */
@Composable
private fun SettingsPage(
    state: HubState,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onClearCalls: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onImportFromText: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    MoreListPage(title = "设置", back = true) {
        item(key = "gateway") {
            MochiListSection(
                header = "网关",
                footer = "默认端口与启动时自动开启服务尚未接入持久化设置，当前为只读占位。",
            ) {
                // 占位：启动流程还没有暴露独立的「默认端口」持久化读写，先显示当前运行端口。
                MochiListRow(
                    title = "网关默认端口",
                    value = state.port.toString(),
                )
                // 占位：MainActivity 目前固定自动启动，等启动设置接入后再换成可交互开关。
                MochiListRow(
                    title = "启动时自动开启服务",
                    subtitle = "当前版本启动时固定开启",
                    trailing = {
                        MochiSwitch(checked = true, onCheckedChange = null, enabled = false)
                    },
                )
            }
        }

        item(key = "keepalive") {
            MochiListSection(
                header = "通知与保活",
                footer = "关闭通知需要前往系统设置；电池优化入口会打开系统的本应用页面。",
            ) {
                MochiListRow(
                    title = "通知",
                    subtitle = if (state.notificationsAllowed) {
                        "已允许；关闭请前往系统设置"
                    } else {
                        "未允许，系统可能回收后台服务"
                    },
                    trailing = {
                        MochiSwitch(
                            checked = state.notificationsAllowed,
                            onCheckedChange = { enabled ->
                                if (enabled && !state.notificationsAllowed) onRequestNotifications()
                            },
                        )
                    },
                )
                MochiListRow(
                    title = "电池优化",
                    subtitle = if (state.batteryExempt) {
                        "已忽略电池优化，后台限制较少"
                    } else {
                        "未忽略，系统可能限制或清理进程"
                    },
                    value = if (state.batteryExempt) "已忽略" else "未忽略",
                    onClick = onRequestBatteryExemption,
                    chevron = true,
                )
            }
        }

        item(key = "data") {
            MochiListSection(
                header = "数据",
                footer = "备份包包含账号凭证，请只保存在可信的位置。",
            ) {
                MochiListRow(
                    title = "清除调用记录",
                    subtitle = "删除本机保存的全部调用明细",
                    onClick = { confirmClear = true },
                    destructive = true,
                    trailing = { TextAction(text = "清除", onClick = { confirmClear = true }, danger = true) },
                )
                MochiListRow(title = "导出备份", onClick = onExportBackup, chevron = true)
                MochiListRow(title = "导入备份文件", onClick = onImportBackup, chevron = true)
                MochiListRow(title = "粘贴备份导入", onClick = onImportFromText, chevron = true)
            }
        }
    }

    if (confirmClear) {
        MochiDialog(
            visible = true,
            onDismissRequest = { confirmClear = false },
            title = "清除调用记录",
            message = "本机保存的全部调用明细会被删除，账号与密钥不受影响。",
            actions = persistentListOf(
                MochiDialogAction(label = "取消", role = MochiDialogActionRole.Cancel),
                MochiDialogAction(
                    label = "清除",
                    role = MochiDialogActionRole.Destructive,
                    onClick = {
                        confirmClear = false
                        onClearCalls()
                    },
                ),
            ),
        )
    }
}

@Suppress("DEPRECATION")
private fun versionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifBlank { "未知" }

@Composable
private fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember(context) { versionName(context) }
}

/** 圆角曲线：半径从直角一路拉到胶囊，看连续圆角怎么平滑过渡；可以叠上普通圆角的轮廓对比。 */
@Composable
private fun CornerCurveSection(title: String) {
    val cs = MochiTheme.colorScheme
    var radius by remember { mutableFloatStateOf(20f) }
    var compare by remember { mutableStateOf(false) }
    val plain = remember(radius) { RoundedCornerShape(radius.dp) }
    LyraSection(
        title,
        footer = "Kyant 的 G2 连续曲率圆角：直边到圆弧的弯曲是渐变的；拉到 36dp（高度的一半）就是胶囊。描边是普通圆角，对比看角上多出来的那一点。",
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(cs.primaryContainer, MochiRoundedRectangle(radius.dp))
                .then(if (compare) Modifier.border(1.5.dp, cs.primary, plain) else Modifier),
        )
    }
    LyraSliderSection("半径", "${radius.roundToInt()} dp", radius, { radius = it }, valueRange = 0f..36f)
    LyraSettingsSection(null) {
        MochiListRow("叠加普通圆角", trailing = { MochiSwitch(compare, { compare = it }) })
    }
}

/** 图形设置：档位字是主色，切档时模糊替换；下面是分档滑条、说明和各项效果的状态。 */
@Composable
private fun GraphicsSettings(title: String?) {
    val level = MochiGraphics.level
    val levels = MochiGraphicsLevel.entries
    val ts = MochiTheme.textStyles
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MochiListDefaults.SectionSpacing)) {
        Column {
            LyraSectionHeader(title)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MochiBlurText(level.label, style = ts.titleLarge.emphasized(), color = MochiTheme.colorScheme.primary)
                Text("画面效果", style = ts.titleLarge.emphasized())
            }
            MochiStepSlider(
                value = level.ordinal,
                onValueChange = { MochiGraphics.level = levels[it] },
                count = levels.size,
            )
            MochiBlurText(
                level.tagline,
                style = ts.footnote,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = MochiListDefaults.TextRowPadding)
                    .padding(top = 10.dp),
            )
        }
        GraphicsFeatures(level)
    }
}

/** 只画小标题，避免为了一个标题套一层空卡片。 */
@Composable
private fun LyraSectionHeader(title: String?) {
    if (title != null) {
        MochiListSectionHeader(title)
    }
}

@Composable
private fun GraphicsFeatures(level: MochiGraphicsLevel) {
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val shaderOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val rows = listOf(
        "模糊" to when {
            !blurOk -> "系统不支持"
            level == MochiGraphicsLevel.Extra -> "加强"
            else -> "开"
        },
        "着色器" to when {
            !shaderOk -> "系统不支持"
            level >= MochiGraphicsLevel.High -> "开"
            else -> "关"
        },
        "换明暗揭开" to if (level == MochiGraphicsLevel.Mid) "硬边" else "羽化",
        "刷新率" to if (level == MochiGraphicsLevel.Extra) "最高" else "跟随系统",
    )
    MochiListSection {
        rows.forEach { (title, value) ->
            MochiListRow(
                title,
                trailing = {
                    val off = value == "关" || value == "硬边" || value == "系统不支持"
                    MochiBlurText(
                        value,
                        color = if (off) MochiTheme.colorScheme.onSurfaceVariant else MochiTheme.colorScheme.primary,
                        contentAlignment = Alignment.CenterEnd,
                    )
                },
            )
        }
    }
}

private val MochiGraphicsLevel.label: String
    get() = when (this) {
        MochiGraphicsLevel.Mid -> "中"
        MochiGraphicsLevel.High -> "高"
        MochiGraphicsLevel.Extra -> "超高"
    }

private val MochiGraphicsLevel.tagline: String
    get() = when (this) {
        MochiGraphicsLevel.Mid -> "省电：着色器关闭，玻璃和融球变成纯色，换明暗的揭开是硬边。"
        MochiGraphicsLevel.High -> "完整效果：玻璃、融球、边缘流光、羽化揭开。推荐。"
        MochiGraphicsLevel.Extra -> "在「高」的基础上模糊再加强一半，并请求屏幕最高刷新率。更耗电。"
    }

/** 主题色板：实心主题色共用卡片轮廓，只用窄缝分隔。 */
@Composable
private fun ThemeColorPalette(title: String) {
    val cs = MochiTheme.colorScheme
    val accents = listOf(
        PaletteSwatch("主色", "primary", cs.primary, cs.onPrimary),
        PaletteSwatch("主色容器", "primaryContainer", cs.primaryContainer, cs.onPrimaryContainer),
        PaletteSwatch("辅助色", "secondary", cs.secondary, cs.onSecondary),
        PaletteSwatch("辅助色容器", "secondaryContainer", cs.secondaryContainer, cs.onSecondaryContainer),
        PaletteSwatch("第三色", "tertiary", cs.tertiary, cs.onTertiary),
        PaletteSwatch("第三色容器", "tertiaryContainer", cs.tertiaryContainer, cs.onTertiaryContainer),
    )
    LyraSection(title, padding = 0.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 300.dp * LocalDensity.current.fontScale) 2 else 1
            PaletteGrid(accents, columns)
        }
    }
}

/** 表面层级：五级容器色按顺序排开，检查卡片与页面底色是否分得开。 */
@Composable
private fun ThemeSurfaceLevels(title: String) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    LyraSection(title, padding = 0.dp) {
        Column(Modifier.fillMaxWidth().clip(MochiTheme.shapes.extraLarge)) {
            listOf(
                cs.surfaceContainerLowest to "Lowest",
                cs.surfaceContainerLow to "Low",
                cs.surfaceContainer to "Container",
                cs.surfaceContainerHigh to "High",
                cs.surfaceContainerHighest to "Highest",
            ).forEachIndexed { index, (color, name) ->
                Row(
                    Modifier.fillMaxWidth().background(color).padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("${index + 1}", color = cs.onSurfaceVariant, style = ts.footnote, modifier = Modifier.alignByBaseline())
                    Text(name, color = cs.onSurface, style = ts.body, modifier = Modifier.weight(1f).alignByBaseline())
                }
            }
        }
    }
}

private data class PaletteSwatch(val name: String, val token: String, val color: Color, val content: Color)

@Composable
private fun PaletteGrid(swatches: List<PaletteSwatch>, columns: Int) {
    val ts = MochiTheme.textStyles
    val gutter = 2.dp
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MochiTheme.shapes.extraLarge)
            .background(MochiDefaults.groupedContainer())
            .drawBehind {
                val gap = gutter.roundToPx()
                val rows = (swatches.size + columns - 1) / columns
                val width = size.width.roundToInt()
                val available = (width - gap * (columns - 1)).coerceAtLeast(0)
                val rowHeight = (size.height.roundToInt() - gap * (rows - 1)) / rows
                swatches.forEachIndexed { i, swatch ->
                    val column = i % columns
                    val left = available * column / columns + gap * column
                    val right = available * (column + 1) / columns + gap * column
                    val x = if (layoutDirection == LayoutDirection.Rtl) width - right else left
                    drawRect(
                        swatch.color,
                        topLeft = Offset(x.toFloat(), ((i / columns) * (rowHeight + gap)).toFloat()),
                        size = Size((right - left).toFloat(), rowHeight.toFloat()),
                    )
                }
            },
        content = {
            swatches.forEach { swatch ->
                Column(
                    Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(swatch.name, color = swatch.content, style = ts.labelLarge.emphasized().copy(textAlign = TextAlign.Center))
                    Text(
                        swatch.token,
                        color = swatch.content,
                        style = ts.footnote.copy(textAlign = TextAlign.Center),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val gap = gutter.roundToPx()
        val available = (constraints.maxWidth - gap * (columns - 1)).coerceAtLeast(0)
        val placeables = measurables.mapIndexed { i, measurable ->
            val column = i % columns
            val width = available * (column + 1) / columns - available * column / columns
            measurable.measure(Constraints.fixedWidth(width))
        }
        // The same pixel gutters drive cell painting and label placement, without an outer rim.
        val rowHeight = maxOf(112.dp.roundToPx(), placeables.maxOf { it.height })
        val rows = (placeables.size + columns - 1) / columns
        layout(constraints.maxWidth, constraints.constrainHeight(rows * rowHeight + gap * (rows - 1))) {
            placeables.forEachIndexed { i, p ->
                val column = i % columns
                p.placeRelative(
                    available * column / columns + gap * column,
                    (i / columns) * (rowHeight + gap) + (rowHeight - p.height) / 2,
                )
            }
        }
    }
}
