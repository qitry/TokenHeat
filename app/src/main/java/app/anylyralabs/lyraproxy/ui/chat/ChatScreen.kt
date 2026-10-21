/*
 * 模型聊天页 —— 主程序接入说明
 *
 * 只依赖 ui.feice.mochi.* 与 androidx.compose.foundation / ui，不引入 material3。
 * 签名：ChatScreen(port: Int, secret: String, models: List<String>, onBack: () -> Unit,
 *                modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues())
 * 调用方式（参数顺序固定）：
 *
 *   ChatScreen(
 *       port = bridgePort,                  // 本机网关端口
 *       secret = bridgeSecret,              // 网关 Bearer secret，可为空串
 *       models = gatewayModels,             // 模型名列表；为空时退回 "default"
 *       onBack = { nav.pop() },             // 左上角返回
 *       modifier = Modifier,
 *       contentPadding = PaddingValues(),   // 额外加给消息列表的留白，不影响底部输入区
 *   )
 *
 * 注意：targetSdk >= 28 时访问 http://127.0.0.1 属于明文流量，需要在 manifest 里允许
 * （android:usesCleartextTraffic="true"，或用 network-security-config 只放行 127.0.0.1），
 * 否则 HttpURLConnection 会直接抛 "Cleartext HTTP traffic ... not permitted"。
 *
 * 会话只存在内存里（标题经 rememberSaveable 保留），Exa / Firecrawl Key 在「网络工具设置」面板里配置。
 * 主程序只需在路由里挂这一行，其余内部自洽。
 */

package app.anylyralabs.lyraproxy.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anylyralabs.lyraproxy.ui.LyraBottomBarClearance
import kotlinx.collections.immutable.toImmutableList
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiDropdownDefaults
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiIconDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiSidebarHeader
import ui.feice.mochi.navigation.MochiSidebarHeaderButton
import ui.feice.mochi.navigation.MochiSidebarItem
import ui.feice.mochi.navigation.MochiSidebarLayout
import ui.feice.mochi.navigation.MochiSidebarStyle
import ui.feice.mochi.navigation.MochiTitleBar
import ui.feice.mochi.navigation.rememberMochiSidebarState
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.LocalContentColor
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

private const val DEFAULT_MODEL = "default"

@Composable
fun ChatScreen(
    port: Int,
    secret: String,
    models: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles

    val store = rememberChatStore()
    val session = store.current
    val sidebar = rememberMochiSidebarState()
    val settings = rememberMochiSheetState()
    val engine = remember { ChatEngine() }

    DisposableEffect(Unit) {
        onDispose { engine.stop() }
    }

    var keys by remember { mutableStateOf(ChatKeys.load(context)) }
    var input by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var streamingSession by remember { mutableStateOf<ChatSession?>(null) }

    val modelOptions = remember(models) {
        models.map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { listOf(DEFAULT_MODEL) }
    }
    var selectedModel by rememberSaveable { mutableStateOf(modelOptions.first()) }
    LaunchedEffect(modelOptions) {
        if (selectedModel !in modelOptions) selectedModel = modelOptions.first()
    }

    fun stopGeneration() {
        engine.stop()
        streamingSession?.finishStreaming()
        streamingSession = null
    }

    fun deleteSession(target: ChatSession) {
        if (streamingSession === target) {
            engine.stop()
            target.finishStreaming()
            streamingSession = null
        }
        store.delete(target.id)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || streamingSession != null) return

        input = ""
        val userMessage = ChatMessage.user(text)
        session.append(userMessage)
        store.autoTitle(session, text)

        val assistant = ChatMessage.assistant()
        session.append(assistant)
        streamingSession = session

        engine.stream(
            messages = session.messages.toList(),
            model = selectedModel,
            port = port,
            secret = secret,
            toolsEnabled = keys.webToolsAvailable,
            exaKey = keys.exa,
            firecrawlKey = keys.firecrawl,
            onDelta = { content, reasoning ->
                session.update(assistant.id) { message ->
                    message.copy(
                        content = message.content + content,
                        reasoning = message.reasoning + reasoning,
                    )
                }
            },
            onSources = { sources ->
                session.update(assistant.id) { it.copy(sources = sources) }
            },
            onDone = { error ->
                session.update(assistant.id) { it.copy(streaming = false, error = error) }
                if (streamingSession === session) streamingSession = null
            },
        )
    }

    val modelItems = remember(modelOptions) { modelOptions.map { MochiItem(it) }.toImmutableList() }
    var modelsOpen by remember { mutableStateOf(false) }
    val barColors = MochiButtonDefaults.barColors()
    val listState = rememberLazyListState()
    // contentPadding 只加在消息列表上：底部输入区自己处理标签栏与安全区，不跟着被顶起来
    val layoutDirection = LocalLayoutDirection.current
    val messagePadding = remember(contentPadding, layoutDirection) {
        PaddingValues(
            start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
            end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
            top = 10.dp + contentPadding.calculateTopPadding(),
            bottom = 10.dp + contentPadding.calculateBottomPadding(),
        )
    }
    val background = MochiDefaults.groupedBackground()
    // 顶栏左右各有按钮：标题只取模型名最后一段并限长，避免长 vendor 前缀压到按钮上；完整名字在菜单里
    val titleModel = remember(selectedModel) {
        val name = selectedModel.substringAfterLast('/').ifBlank { selectedModel }
        if (name.length > 16) name.take(15) + "…" else name
    }

    // 消息数和最后一截内容变化时贴着底部（下方有一个 1dp 锚点 item，滚到它就等于滚到底）
    val last = session.messages.lastOrNull()
    val tail = (last?.content?.length ?: 0) + (last?.reasoning?.length ?: 0) + (last?.sources?.size ?: 0)
    LaunchedEffect(session.id, session.messages.size, tail) {
        if (session.messages.isNotEmpty()) listState.scrollToItem(session.messages.size)
    }

    Box(modifier.fillMaxSize()) {
        MochiSidebarLayout(
            state = sidebar,
            modifier = Modifier.fillMaxSize(),
            style = MochiSidebarStyle.Push,
            // 本页通常是导航栈里的子页：整页右划要留给返回，侧边栏用左上角按钮打开
            swipeToOpen = false,
            sidebar = {
                MochiSidebarHeader(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = "搜索对话",
                ) {
                    MochiSidebarHeaderButton(
                        onClick = {
                            store.newSession()
                            search = ""
                            sidebar.close()
                        },
                    ) { PlusGlyph() }
                }

                val filtered = store.sessions.filter {
                    search.isBlank() || it.title.contains(search.trim(), ignoreCase = true)
                }
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        MochiSidebarItem(
                            title = item.title,
                            onClick = {
                                store.select(item.id)
                                sidebar.close()
                            },
                            selected = item.id == store.currentId,
                            trailing = {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .semantics { contentDescription = "删除对话" }
                                        .clickable { deleteSession(item) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CloseGlyph(if (item.id == store.currentId) cs.primary else cs.onSurfaceVariant)
                                }
                            },
                        )
                    }
                    if (filtered.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "没有匹配的对话",
                                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                style = ts.footnote,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }

                MochiSidebarItem(
                    title = "网络工具设置",
                    onClick = {
                        sidebar.close()
                        settings.show()
                    },
                    leading = { SparkleGlyph(cs.onSurfaceVariant, 18.dp) },
                )
                Text(
                    if (keys.webToolsAvailable) "网络工具已就绪" else "网络工具未配置",
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                    style = ts.footnote,
                    color = cs.onSurfaceVariant,
                )
            },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(background),
            ) {
                Column(Modifier.fillMaxSize()) {
                    MochiTitleBar(
                        title = titleModel,
                        navigationIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MochiBackButton(onClick = onBack)
                                MochiBarIconButton(
                                    onClick = { sidebar.open() },
                                    modifier = Modifier.semantics { contentDescription = "会话列表" },
                                ) { ListGlyph() }
                            }
                        },
                        actions = {
                            // MochiIconDropdown = 44dp 顶栏图标按钮 + MochiDropdown 菜单：
                            // 等同于「MochiBarIconButton 打开 MochiDropdown」，但只有一层按钮底色，不会叠两层
                            MochiIconDropdown(
                                expanded = modelsOpen,
                                onExpandedChange = { modelsOpen = it },
                                items = modelItems,
                                onItemClick = { index ->
                                    modelOptions.getOrNull(index)?.let { selectedModel = it }
                                    modelsOpen = false
                                },
                                selectedIndex = modelOptions.indexOf(selectedModel),
                                colors = MochiDropdownDefaults.iconDropdownColors(
                                    buttonColor = barColors.container,
                                    buttonContentColor = barColors.content,
                                ),
                                size = MochiButtonDefaults.BarItemSize,
                                modifier = Modifier.semantics { contentDescription = "选择模型" },
                            ) { SparkleGlyph(LocalContentColor.current) }
                        },
                    )

                    if (!keys.webToolsAvailable) {
                        Text(
                            "网络工具未配置，模型无法搜索或抓取网页",
                            Modifier
                                .fillMaxWidth()
                                .background(cs.surfaceContainerHigh)
                                .clickable { settings.show() }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            style = ts.footnote,
                            color = cs.onSurfaceVariant,
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = messagePadding,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (session.messages.isEmpty()) {
                            item(key = "welcome") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 88.dp, bottom = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("模型聊天", style = ts.titleLarge, color = cs.onSurface)
                                    Text(
                                        "会话只保存在本次运行中，不会写入数据库",
                                        Modifier.padding(top = 6.dp),
                                        style = ts.footnote,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        items(session.messages, key = { it.id }) { message ->
                            MessageBlock(message = message, background = background)
                        }

                        // 底部锚点：scrollToItem(messages.size) 就是滚到底
                        item(key = "bottom-anchor") { Spacer(Modifier.height(1.dp)) }
                    }

                    InputBar(
                        value = input,
                        onValueChange = { input = it },
                        streaming = streamingSession != null,
                        canSend = input.isNotBlank() && streamingSession == null,
                        onSend = { send() },
                        onStop = { stopGeneration() },
                    )
                }
            }
        }

        ChatSettingsSheet(
            state = settings,
            onDismiss = {},
            onKeysChanged = { keys = it },
        )
    }
}

// ───────────────────────── 消息 ─────────────────────────

@Composable
private fun MessageBlock(message: ChatMessage, background: Color) {
    if (message.role == ChatMessage.ROLE_USER) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(MochiTheme.colorScheme.primaryContainer, MochiRoundedRectangle(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    message.content,
                    style = MochiTheme.textStyles.body,
                    color = MochiTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        return
    }

    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles

    Column(Modifier.fillMaxWidth()) {
        if (message.reasoning.isNotBlank()) {
            ReasoningBlock(reasoning = message.reasoning, streaming = message.streaming, background = background)
        }

        if (message.content.isNotBlank()) {
            Text(
                message.content,
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (message.reasoning.isNotBlank()) 8.dp else 0.dp),
                style = ts.body,
                color = cs.onSurface,
            )
        } else if (message.streaming) {
            Text(
                if (message.reasoning.isNotBlank()) "正在整理回答…" else "正在思考…",
                Modifier.padding(top = if (message.reasoning.isNotBlank()) 8.dp else 0.dp),
                style = ts.footnote,
                color = cs.onSurfaceVariant,
            )
        }

        message.error?.let { error ->
            Text(
                error,
                Modifier
                    .padding(top = 8.dp)
                    .background(cs.errorContainer, MochiRoundedRectangle(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                style = ts.footnote,
                color = cs.onErrorContainer,
            )
        }

        if (message.sources.isNotEmpty()) {
            SourcesRow(message.sources, background)
        }
    }
}

/** 思维链折叠块：收起时 96dp + 底部渐变遮罩，展开显示全文。 */
@Composable
private fun ReasoningBlock(reasoning: String, streaming: Boolean, background: Color) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (streaming) "正在思考" else "思考过程",
                style = ts.footnote,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            ChevronGlyph(expanded = expanded, tint = cs.onSurfaceVariant)
        }

        if (expanded) {
            Text(
                reasoning,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                style = ts.footnote.copy(lineHeight = 20.sp),
                color = cs.onSurfaceVariant,
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clipToBounds()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to background,
                            ),
                        )
                    },
            ) {
                Text(
                    reasoning,
                    Modifier.padding(horizontal = 2.dp),
                    style = ts.footnote.copy(lineHeight = 20.sp),
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** 来源头像组：域名首字母圆形色块，横向重叠，最多 4 个 +N。 */
@Composable
private fun SourcesRow(sources: List<ChatSource>, background: Color) {
    val cs = MochiTheme.colorScheme
    val ts = MochiTheme.textStyles
    val palette = listOf(
        cs.primaryContainer to cs.onPrimaryContainer,
        cs.secondaryContainer to cs.onSecondaryContainer,
        cs.tertiaryContainer to cs.onTertiaryContainer,
        cs.surfaceContainerHighest to cs.onSurface,
        cs.errorContainer to cs.onErrorContainer,
    )
    val shown = sources.take(4)
    val extra = sources.size - shown.size

    Row(
        Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEachIndexed { index, source ->
            val pair = palette[(source.domain.hashCode() and 0x7fffffff) % palette.size]
            Box(
                Modifier
                    .offset(x = (-8 * index).dp)
                    .size(26.dp)
                    .background(background, CircleShape)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(pair.first, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        source.domain.firstOrNull()?.uppercaseChar()?.toString() ?: "·",
                        style = ts.footnote,
                        color = pair.second,
                    )
                }
            }
        }
        if (extra > 0) {
            Box(
                Modifier
                    .offset(x = (-8 * shown.size).dp)
                    .size(26.dp)
                    .background(background, CircleShape)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(cs.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+$extra", style = ts.footnote.copy(fontSize = 10.sp), color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

// ───────────────────────── 输入 ─────────────────────────

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    streaming: Boolean,
    canSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val density = LocalDensity.current
    // 键盘弹出时底部的标签栏被键盘盖住，输入区只跟 IME 走；没有键盘时才给标签栏让位
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    Row(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .then(
                if (imeVisible) {
                    Modifier
                } else {
                    Modifier
                        .navigationBarsPadding()
                        .padding(bottom = LyraBottomBarClearance)
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MochiTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = "发消息…",
            singleLine = false,
            maxLines = 5,
            clearButton = false,
        )
        // 发送键常驻（流式输出中置灰），停止键只在生成时出现，两个按钮不会互相顶掉
        MochiIconButton(
            onClick = onSend,
            enabled = canSend,
            style = MochiButtonStyle.Filled,
            modifier = Modifier.semantics { contentDescription = "发送" },
        ) { SendGlyph() }
        if (streaming) {
            MochiIconButton(
                onClick = onStop,
                style = MochiButtonStyle.Tonal,
                modifier = Modifier.semantics { contentDescription = "停止生成" },
            ) { StopGlyph() }
        }
    }
}

// ───────────────────────── 线性图标（Canvas 绘制，不依赖图标库） ─────────────────────────

@Composable
private fun ListGlyph(tint: Color = LocalContentColor.current) {
    Canvas(Modifier.size(19.dp)) {
        val w = 1.9.dp.toPx()
        val left = this.size.width * 0.22f
        val right = this.size.width * 0.78f
        listOf(0.3f, 0.5f, 0.7f).forEach { y ->
            drawLine(tint, Offset(left, this.size.height * y), Offset(right, this.size.height * y), w, StrokeCap.Round)
        }
    }
}

@Composable
private fun PlusGlyph(tint: Color = LocalContentColor.current) {
    Canvas(Modifier.size(18.dp)) {
        val w = 2.dp.toPx()
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val half = this.size.width * 0.34f
        drawLine(tint, Offset(cx - half, cy), Offset(cx + half, cy), w, StrokeCap.Round)
        drawLine(tint, Offset(cx, cy - half), Offset(cx, cy + half), w, StrokeCap.Round)
    }
}

@Composable
private fun CloseGlyph(tint: Color = LocalContentColor.current, size: Dp = 11.dp) {
    Canvas(Modifier.size(size)) {
        val w = 1.6.dp.toPx()
        val pad = this.size.width * 0.2f
        drawLine(
            tint,
            Offset(pad, pad),
            Offset(this.size.width - pad, this.size.height - pad),
            w,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(this.size.width - pad, pad),
            Offset(pad, this.size.height - pad),
            w,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun SparkleGlyph(tint: Color = LocalContentColor.current, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = this.size.minDimension * 0.46f
        val inner = r * 0.26f
        val path = Path().apply {
            moveTo(cx, cy - r)
            quadraticBezierTo(cx + inner, cy - inner, cx + r, cy)
            quadraticBezierTo(cx + inner, cy + inner, cx, cy + r)
            quadraticBezierTo(cx - inner, cy + inner, cx - r, cy)
            quadraticBezierTo(cx - inner, cy - inner, cx, cy - r)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
private fun ChevronGlyph(expanded: Boolean, tint: Color) {
    Canvas(
        Modifier
            .size(11.dp)
            .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
    ) {
        val w = 1.7.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.22f)
            lineTo(size.width * 0.68f, size.height * 0.5f)
            lineTo(size.width * 0.3f, size.height * 0.78f)
        }
        drawPath(path, tint, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun SendGlyph(tint: Color = LocalContentColor.current) {
    Canvas(Modifier.size(18.dp)) {
        val w = 2.dp.toPx()
        val cx = this.size.width / 2f
        drawLine(
            tint,
            Offset(cx, this.size.height * 0.82f),
            Offset(cx, this.size.height * 0.2f),
            w,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(this.size.width * 0.3f, this.size.height * 0.44f),
            Offset(cx, this.size.height * 0.2f),
            w,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(this.size.width * 0.7f, this.size.height * 0.44f),
            Offset(cx, this.size.height * 0.2f),
            w,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun StopGlyph(tint: Color = LocalContentColor.current) {
    Canvas(Modifier.size(16.dp)) {
        val side = this.size.minDimension * 0.72f
        drawRoundRect(
            color = tint,
            topLeft = Offset((this.size.width - side) / 2f, (this.size.height - side) / 2f),
            size = Size(side, side),
            cornerRadius = CornerRadius(side * 0.24f),
        )
    }
}
