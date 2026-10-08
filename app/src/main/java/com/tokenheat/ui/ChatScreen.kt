package com.tokenheat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokenheat.mcp.McpProtocol
import com.tokenheat.mcp.McpServerConfig
import com.tokenheat.proto.HubModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    state: HubState,
    onSendMessage: (String, List<ChatAttachment>) -> Unit,
    onStopStreaming: () -> Unit,
    onClearMessages: () -> Unit,
    onSelectModel: (String) -> Unit,
    onStartBridge: (Int) -> Unit,
    onRefreshModels: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onRetryLastMessage: () -> Unit,
    onUpdateThinkingEffort: (ThinkingEffort) -> Unit,
    onUpdateExaApiKey: (String) -> Unit,
    onToggleMcpEnabled: (Boolean) -> Unit,
    onAddMcpServer: (McpServerConfig) -> Unit,
    onDeleteMcpServer: (String) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSelectConversation: (String) -> Unit = {},
    onNewConversation: () -> Unit = {},
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    onDeleteConversation: (String) -> Unit = {},
    onClearAllConversations: () -> Unit = {},
    isDarkTheme: Boolean = true,
    onToggleDarkTheme: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var showModelDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showThinkingDialog by remember { mutableStateOf(false) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var showNoVisionDialog by remember { mutableStateOf(false) }

    // Conversation dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetId by remember { mutableStateOf("") }
    var renameInitialTitle by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf("") }

    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    val activeConversation = state.activeConversation
    val activeConversationTitle = activeConversation?.title ?: "新对话"

    val activeModelId = state.selectedChatModelId
        ?: activeConversation?.modelId
        ?: state.models.firstOrNull { it.isFree }?.id
        ?: state.models.firstOrNull()?.id
        ?: ""

    val activeModel = state.models.firstOrNull { it.id == activeModelId }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when messages or content change
    val messagesCount = state.chatMessages.size
    val lastLength = state.chatMessages.lastOrNull()?.content?.length ?: 0
    val lastReasoningLength = state.chatMessages.lastOrNull()?.reasoningContent?.length ?: 0
    LaunchedEffect(messagesCount, lastLength, lastReasoningLength) {
        if (messagesCount > 0) {
            listState.animateScrollToItem(messagesCount - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            ) {
                ConversationDrawerContent(
                    conversations = state.conversations,
                    activeConversationId = state.activeConversationId,
                    onSelect = { convId ->
                        onSelectConversation(convId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNew = {
                        onNewConversation()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onRename = { convId, title ->
                        renameTargetId = convId
                        renameInitialTitle = title
                        showRenameDialog = true
                    },
                    onDelete = { convId ->
                        deleteTargetId = convId
                        showDeleteConfirmDialog = true
                    },
                    onClearAll = {
                        showClearAllConfirmDialog = true
                    },
                    onCloseDrawer = {
                        coroutineScope.launch { drawerState.close() }
                    },
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 1. Top Kelivo/Claude Toolbar: Drawer Menu, Title, Model Selector & Quick Actions
            ChatTopToolbar(
                conversationTitle = activeConversationTitle,
                activeModelId = activeModelId,
                activeModel = activeModel,
                thinkingEffort = state.thinkingEffort,
                mcpEnabled = state.mcpEnabled,
                modelsCount = state.models.size,
                hasMessages = state.chatMessages.isNotEmpty(),
                isDarkTheme = isDarkTheme,
                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                onEditTitle = {
                    renameTargetId = activeConversation?.id ?: ""
                    renameInitialTitle = activeConversationTitle
                    showRenameDialog = true
                },
                onOpenModelDialog = { showModelDialog = true },
                onNewConversation = onNewConversation,
                onOpenThinkingDialog = { showThinkingDialog = true },
                onOpenMcpDialog = { showMcpDialog = true },
                onRefreshModels = onRefreshModels,
                onOpenClearDialog = { showClearDialog = true },
                onToggleDarkTheme = onToggleDarkTheme,
            )

            // 2. Bridge Service Offline Warning Banner
            AnimatedVisibility(visible = !state.bridgeRunning) {
                BridgeOfflineBanner(
                    port = state.port,
                    onStartBridge = { onStartBridge(state.port) },
                )
            }

        // 3. Messages List or Empty State (Claude style: direct text, no speech bubble/avatar for AI)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (state.chatMessages.isEmpty()) {
                ChatEmptyState(
                    activeModelId = activeModelId,
                    activeModel = activeModel,
                    thinkingEffort = state.thinkingEffort,
                    mcpEnabled = state.mcpEnabled,
                    onSuggestionClick = { suggestion ->
                        onSendMessage(suggestion, emptyList())
                    },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(
                        items = state.chatMessages,
                        key = { it.id },
                    ) { message ->
                        val isLastAssistant = message.role == ChatRole.ASSISTANT &&
                            message.id == state.chatMessages.lastOrNull { it.role == ChatRole.ASSISTANT }?.id

                        if (message.role == ChatRole.USER) {
                            UserMessageView(
                                message = message,
                                onCopy = { onCopyText("消息内容", it) },
                            )
                        } else if (message.role == ChatRole.ASSISTANT) {
                            AssistantMessageView(
                                message = message,
                                defaultModelId = activeModelId,
                                isLastAssistant = isLastAssistant,
                                isChatStreaming = state.isChatStreaming,
                                onCopy = { onCopyText("回答内容", it) },
                                onCopyCode = { onCopyText("代码内容", it) },
                                onRetry = onRetryLastMessage,
                            )
                        }
                    }
                }
            }
        }

        // 4. Floating compound input bar matching design blueprint (IMG_20261008_141556.jpg)
        ChatInputBar(
            inputText = inputText,
            isStreaming = state.isChatStreaming,
            supportsImages = activeModel?.supportsImages == true,
            supportsReasoning = activeModel?.supportsReasoning == true,
            activeModelId = activeModelId,
            activeModel = activeModel,
            thinkingEffort = state.thinkingEffort,
            mcpEnabled = state.mcpEnabled,
            pendingAttachments = state.pendingAttachments,
            onInputTextChange = { inputText = it },
            onSend = {
                val text = inputText.trim()
                if (text.isNotEmpty() || state.pendingAttachments.isNotEmpty()) {
                    inputText = ""
                    onSendMessage(text, state.pendingAttachments)
                }
            },
            onStop = onStopStreaming,
            onPickImage = {
                if (activeModel?.supportsImages == true) {
                    onPickImage()
                } else {
                    showNoVisionDialog = true
                }
            },
            onPickFile = onPickFile,
            onRemoveAttachment = onRemoveAttachment,
            onToggleMcpEnabled = onToggleMcpEnabled,
            onUpdateThinkingEffort = onUpdateThinkingEffort,
            onOpenModelDialog = { showModelDialog = true },
            onOpenThinkingDialog = { showThinkingDialog = true },
        )
    }
}

    // Rename Conversation Dialog
    if (showRenameDialog) {
        RenameConversationDialog(
            initialTitle = renameInitialTitle,
            onConfirm = { newTitle ->
                if (renameTargetId.isNotBlank()) {
                    onRenameConversation(renameTargetId, newTitle)
                }
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    // Delete Single Conversation Confirm Dialog
    if (showDeleteConfirmDialog) {
        DeleteConversationConfirmDialog(
            onConfirm = {
                if (deleteTargetId.isNotBlank()) {
                    onDeleteConversation(deleteTargetId)
                }
                showDeleteConfirmDialog = false
            },
            onDismiss = { showDeleteConfirmDialog = false },
        )
    }

    // Clear All Conversations Confirm Dialog
    if (showClearAllConfirmDialog) {
        ClearAllConversationsConfirmDialog(
            onConfirm = {
                onClearAllConversations()
                showClearAllConfirmDialog = false
            },
            onDismiss = { showClearAllConfirmDialog = false },
        )
    }

    // Model Selector Dialog
    if (showModelDialog) {
        ModelSelectorDialog(
            models = state.models,
            selectedModelId = activeModelId,
            onSelect = { selectedId ->
                onSelectModel(selectedId)
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false },
            onRefreshModels = onRefreshModels,
        )
    }

    // Thinking Effort Intensity Dialog
    if (showThinkingDialog) {
        ThinkingEffortDialog(
            currentEffort = state.thinkingEffort,
            modelSupportsReasoning = activeModel?.supportsReasoning == true,
            onSelect = { effort ->
                onUpdateThinkingEffort(effort)
                showThinkingDialog = false
            },
            onDismiss = { showThinkingDialog = false },
        )
    }

    // MCP & Exa AI Search Dialog
    if (showMcpDialog) {
        McpSettingsDialog(
            enabled = state.mcpEnabled,
            exaApiKey = state.exaApiKey,
            servers = state.mcpServers,
            onToggleEnabled = onToggleMcpEnabled,
            onSaveExaApiKey = onUpdateExaApiKey,
            onAddServer = onAddMcpServer,
            onDeleteServer = onDeleteMcpServer,
            onToggleServer = onToggleMcpServer,
            onDismiss = { showMcpDialog = false },
        )
    }

    // No Vision Model Dialog
    if (showNoVisionDialog) {
        AlertDialog(
            onDismissRequest = { showNoVisionDialog = false },
            title = {
                Text(
                    text = "当前模型不支持视觉",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = "所选模型 [${activeModel?.name ?: activeModelId}] 未声明多模态视觉能力。如需发送图片，请在顶部切换至支持视觉的模型（例如 GPT-4o、Claude 3.5 Sonnet、Gemini 2.5 等）。\n\n您依然可以附带代码文件或文本文件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = { showNoVisionDialog = false }) {
                    Text("我知道了")
                }
            },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    // Clear Conversation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "清空对话记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = "确定要清空当前的全部对话历史吗？此操作无法撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearMessages()
                        showClearDialog = false
                    },
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

/**
 * Top Toolbar matching Codex / Claude: model badge, reasoning badge, and MCP indicator.
 */
@Composable
private fun ChatTopToolbar(
    conversationTitle: String,
    activeModelId: String,
    activeModel: HubModel?,
    thinkingEffort: ThinkingEffort,
    mcpEnabled: Boolean,
    modelsCount: Int,
    hasMessages: Boolean,
    isDarkTheme: Boolean,
    onOpenDrawer: () -> Unit,
    onEditTitle: () -> Unit,
    onOpenModelDialog: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenThinkingDialog: () -> Unit,
    onOpenMcpDialog: () -> Unit,
    onRefreshModels: () -> Unit,
    onOpenClearDialog: () -> Unit,
    onToggleDarkTheme: () -> Unit,
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Hamburger Menu + Conversation Title (clickable)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "历史会话",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.width(4.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onEditTitle() }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversationTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "重命名",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            // Right Action Elements: Compact Model Capsule + New Chat + More Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Model Capsule Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenModelDialog() },
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModelBrandBadge(modelId = activeModelId, size = 18.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = (activeModel?.name ?: activeModelId).ifBlank { "选择模型" }.substringAfterLast('/'),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 95.dp),
                        )
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "切换模型",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // New Chat Icon Button
                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "新建对话",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // More Menu Icon Button
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "更多设置",
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("思考强度: ${thinkingEffort.levelName}")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (thinkingEffort != ThinkingEffort.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onOpenThinkingDialog()
                            },
                        )

                        DropdownMenuItem(
                            text = {
                                Text(if (mcpEnabled) "MCP/Exa搜索: 已启用" else "MCP/Exa搜索: 未启用")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Handyman,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (mcpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onOpenMcpDialog()
                            },
                        )

                        DropdownMenuItem(
                            text = {
                                Text(if (isDarkTheme) "切换为浅色模式" else "切换为深色模式")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onToggleDarkTheme()
                            },
                        )

                        if (modelsCount == 0) {
                            DropdownMenuItem(
                                text = { Text("刷新模型列表") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onRefreshModels()
                                },
                            )
                        }

                        if (hasMessages) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text("清空当前对话", color = MaterialTheme.colorScheme.error)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onOpenClearDialog()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kelivo App style navigation drawer for managing conversation history.
 */
@Composable
private fun ConversationDrawerContent(
    conversations: List<ChatConversation>,
    activeConversationId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val sortedConversations = remember(conversations) {
        conversations.sortedByDescending { it.updatedAt }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Drawer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "对话列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                PillBadge(
                    text = "共 ${conversations.size} 个",
                    variant = BadgeVariant.Neutral,
                )
            }
            IconButton(
                onClick = onCloseDrawer,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "关闭",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // "+ New Chat" Kelivo style prominent button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onNew() },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "新建对话",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // Conversations List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = sortedConversations,
                key = { it.id },
            ) { conv ->
                val isActive = conv.id == activeConversationId
                ConversationDrawerItem(
                    conversation = conv,
                    isActive = isActive,
                    onClick = { onSelect(conv.id) },
                    onRename = { onRename(conv.id, conv.title) },
                    onDelete = { onDelete(conv.id) },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        // Drawer Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                onClick = onClearAll,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "清空全部历史",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Single conversation card in the navigation drawer. */
@Composable
private fun ConversationDrawerItem(
    conversation: ChatConversation,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val timeStr = remember(conversation.updatedAt) { timeFormat.format(Date(conversation.updatedAt)) }

    val lastMessagePreview = remember(conversation.messages) {
        val last = conversation.messages.lastOrNull { it.content.isNotBlank() }
        last?.content?.trim()?.replace("\n", " ")?.take(36) ?: "空会话"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
        ),
        color = if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lastMessagePreview,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "· $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "操作",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Dialog for renaming a conversation. */
@Composable
private fun RenameConversationDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "重命名对话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("对话名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** Dialog for confirming deletion of a single conversation. */
@Composable
private fun DeleteConversationConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "删除此对话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = "确定要删除该对话记录吗？所有消息将被移除，此操作无法撤销。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** Dialog for clearing all conversation history. */
@Composable
private fun ClearAllConversationsConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "清空全部历史对话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = "确定要清空全部对话历史吗？所有现有会话将被删除并为您新建一个空白对话。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清空全部", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** User message bubble with optional image and file attachments. */
@Composable
private fun UserMessageView(
    message: ChatMessage,
    onCopy: (String) -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 330.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                // Attachments in user message
                if (message.attachments.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        message.attachments.forEach { att ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (att.type == AttachmentType.IMAGE) Icons.Outlined.Image else Icons.Outlined.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = att.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${att.sizeBytes / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (message.content.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { onCopy(message.content) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/**
 * Claude App style AI response: NO speech bubble card and NO avatar.
 * Renders pure markdown with headers, code blocks, collapsible reasoning, and MCP tools.
 */
@Composable
private fun AssistantMessageView(
    message: ChatMessage,
    defaultModelId: String,
    isLastAssistant: Boolean,
    isChatStreaming: Boolean,
    onCopy: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        // Minimalist top information label (Model ID, time)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (message.modelId ?: defaultModelId).ifBlank { "Assistant" },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }

        // 1. Collapsible Reasoning / Thinking Chain Card (Codex/Claude style)
        if (message.reasoningContent.isNotBlank() || (message.isStreaming && message.content.isEmpty())) {
            ThinkingProcessCard(
                reasoningContent = message.reasoningContent,
                isStreaming = message.isStreaming && message.content.isEmpty(),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // 2. MCP Tool Execution Cards
        if (message.toolCalls.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.toolCalls.forEach { tool ->
                    ToolCallCard(
                        toolName = tool.name,
                        arguments = tool.arguments,
                        result = tool.result,
                        isExecuting = tool.isExecuting,
                    )
                }
            }
        }

        // 3. Main Response Body (Markdown rendering)
        if (message.isError) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    SelectionContainer {
                        Text(
                            text = message.content.ifBlank { "请求上游模型失败，请检查账号状态或网络连接。" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        } else if (message.content.isNotBlank() || message.isStreaming) {
            MarkdownView(
                content = message.content,
                isStreaming = message.isStreaming && message.content.isNotBlank(),
                onCopyCode = onCopyCode,
            )
        }

        // 4. Subtle Bottom Toolbar
        if (!message.isStreaming && (message.content.isNotBlank() || message.isError)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.content.isNotBlank()) {
                    IconButton(
                        onClick = { onCopy(message.content) },
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "复制正文",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isLastAssistant && !isChatStreaming) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "重新生成",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Attachments preview strip shown inside or above the input bar. */
@Composable
private fun AttachmentsPreviewStrip(
    attachments: List<ChatAttachment>,
    onRemove: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(attachments, key = { it.id }) { att ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (att.type == AttachmentType.IMAGE) Icons.Outlined.Image else Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = att.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "移除",
                        modifier = Modifier
                            .size(13.dp)
                            .clickable { onRemove(att.id) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 12-step discrete pill slider matching the visual design of Screenshot:
 * - 36dp pill container with fully rounded ends.
 * - Dark grey pill background track (Color(0xFF2C2C2E)).
 * - Light silver grey active track filling from start to thumb center (Color(0xFFA0A0A5)).
 * - 12 discrete tick dots (white when active, translucent white when inactive).
 * - Solid pure white thumb ball at the active step.
 * - Supports drag and tap gestures to snap immediately to discrete step (0..11).
 */
@Composable
fun SteppedPillSlider(
    currentEffort: ThinkingEffort,
    onEffortChanged: (ThinkingEffort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSteps = ThinkingEffort.entries.size // 12
    val activeIndex = currentEffort.ordinal.coerceIn(0, totalSteps - 1)

    val trackBgColor = Color(0xFF2C2C2E)
    val activeTrackColor = Color(0xFFA0A0A5)
    val activeDotColor = Color.White
    val inactiveDotColor = Color.White.copy(alpha = 0.35f)
    val thumbColor = Color.White
    val thumbShadowColor = Color.Black.copy(alpha = 0.3f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(totalSteps) {
                detectTapGestures { offset ->
                    val pillHeight = size.height.toFloat()
                    val pillWidth = size.width.toFloat()
                    val radius = pillHeight / 2f
                    val trackStart = radius
                    val trackEnd = pillWidth - radius
                    val trackLength = (trackEnd - trackStart).coerceAtLeast(1f)
                    val fraction = ((offset.x - trackStart) / trackLength).coerceIn(0f, 1f)
                    val newIndex = (fraction * (totalSteps - 1)).roundToInt().coerceIn(0, totalSteps - 1)
                    onEffortChanged(ThinkingEffort.entries[newIndex])
                }
            }
            .pointerInput(totalSteps) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val pillHeight = size.height.toFloat()
                    val pillWidth = size.width.toFloat()
                    val radius = pillHeight / 2f
                    val trackStart = radius
                    val trackEnd = pillWidth - radius
                    val trackLength = (trackEnd - trackStart).coerceAtLeast(1f)
                    val fraction = ((change.position.x - trackStart) / trackLength).coerceIn(0f, 1f)
                    val newIndex = (fraction * (totalSteps - 1)).roundToInt().coerceIn(0, totalSteps - 1)
                    onEffortChanged(ThinkingEffort.entries[newIndex])
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pillHeight = size.height
            val pillWidth = size.width
            val pillRadius = pillHeight / 2f

            // 1. Draw outer dark capsule background
            drawRoundRect(
                color = trackBgColor,
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(pillRadius, pillRadius),
            )

            val trackStart = pillRadius
            val trackEnd = pillWidth - pillRadius
            val trackLength = (trackEnd - trackStart).coerceAtLeast(1f)
            val stepDistance = trackLength / (totalSteps - 1)

            val thumbX = trackStart + activeIndex * stepDistance
            val centerY = pillHeight / 2f
            val thumbRadius = pillRadius - 2.5.dp.toPx()

            // 2. Draw active track from left capsule end to thumb center
            if (activeIndex > 0) {
                val activeWidth = (thumbX + thumbRadius * 0.5f).coerceAtMost(pillWidth)
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth, pillHeight),
                    cornerRadius = CornerRadius(pillRadius, pillRadius),
                )
            }

            // 3. Draw 12 tick dots
            val dotRadius = 2.4.dp.toPx()
            for (i in 0 until totalSteps) {
                val dotX = trackStart + i * stepDistance
                val isCoveredByThumb = kotlin.math.abs(dotX - thumbX) < thumbRadius * 0.8f
                if (!isCoveredByThumb) {
                    val dotColor = if (i <= activeIndex) activeDotColor else inactiveDotColor
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(dotX, centerY),
                    )
                }
            }

            // 4. Draw Thumb (pure white circle with shadow)
            drawCircle(
                color = thumbShadowColor,
                radius = thumbRadius + 1.5.dp.toPx(),
                center = Offset(thumbX, centerY + 1.dp.toPx()),
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = Offset(thumbX, centerY),
            )
        }
    }
}

/** In-card thinking effort slider panel expandable via the "思考强度" button. */
@Composable
private fun ThinkingSliderPanel(
    currentEffort: ThinkingEffort,
    modelSupportsReasoning: Boolean,
    onEffortChanged: (ThinkingEffort) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (currentEffort != ThinkingEffort.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "思考强度: ${currentEffort.label}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (currentEffort == ThinkingEffort.OFF) "(关闭)" else "(${currentEffort.budgetTokens} tokens)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "收起",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        SteppedPillSlider(
            currentEffort = currentEffort,
            onEffortChanged = onEffortChanged,
        )
    }
}

/**
 * Bottom chat input toolbar strictly matching design blueprint (IMG_20261008_141556.jpg):
 * - Compound card with RoundedCornerShape(22.dp).
 * - Upper area: Borderless multi-line text input with placeholder "描述你的任务...".
 * - Lower toolbar Row:
 *   - Left: [+] (Pick Image/File), [联网] (MCP/Exa quick toggle), [思考强度] (Expand 12-step slider)
 *   - Right: [Model] (Active model capsule with brand logo & picker), [Send/Stop] (Action circle button)
 */
@Composable
private fun ChatInputBar(
    inputText: String,
    isStreaming: Boolean,
    supportsImages: Boolean,
    supportsReasoning: Boolean,
    activeModelId: String,
    activeModel: HubModel?,
    thinkingEffort: ThinkingEffort,
    mcpEnabled: Boolean,
    pendingAttachments: List<ChatAttachment>,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onToggleMcpEnabled: (Boolean) -> Unit,
    onUpdateThinkingEffort: (ThinkingEffort) -> Unit,
    onOpenModelDialog: () -> Unit,
    onOpenThinkingDialog: () -> Unit,
) {
    var showThinkingSlider by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .imePadding(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Pending Attachments Preview
            if (pendingAttachments.isNotEmpty()) {
                AttachmentsPreviewStrip(
                    attachments = pendingAttachments,
                    onRemove = onRemoveAttachment,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // 2. Expandable 12-step discrete thinking effort slider panel
            AnimatedVisibility(visible = showThinkingSlider) {
                Column {
                    ThinkingSliderPanel(
                        currentEffort = thinkingEffort,
                        modelSupportsReasoning = supportsReasoning,
                        onEffortChanged = onUpdateThinkingEffort,
                        onClose = { showThinkingSlider = false },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            // 3. Multi-line borderless text input with strictly defined placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, top = 12.dp, bottom = 6.dp),
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp, max = 150.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "描述你的任务...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        innerTextField()
                    },
                )
            }

            // 4. Bottom action Row matching blueprint layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left action group: [+], [联网], [思考强度]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // [+] Attachment Button
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "添加附件",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("添加图片" + if (!supportsImages) " (当前模型不支持视觉)" else "") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (supportsImages) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    )
                                },
                                onClick = {
                                    showAttachMenu = false
                                    onPickImage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("添加文本/代码文件") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    showAttachMenu = false
                                    onPickFile()
                                },
                            )
                        }
                    }

                    // [联网] Toggle Pill Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onToggleMcpEnabled(!mcpEnabled) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (mcpEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (mcpEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Handyman,
                                contentDescription = "联网",
                                modifier = Modifier.size(14.dp),
                                tint = if (mcpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "联网",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (mcpEnabled) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (mcpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // [思考强度] Toggle Slider Pill Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showThinkingSlider = !showThinkingSlider },
                        shape = RoundedCornerShape(16.dp),
                        color = if (thinkingEffort != ThinkingEffort.OFF || showThinkingSlider) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (thinkingEffort != ThinkingEffort.OFF || showThinkingSlider) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Psychology,
                                contentDescription = "思考强度",
                                modifier = Modifier.size(14.dp),
                                tint = if (thinkingEffort != ThinkingEffort.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (thinkingEffort != ThinkingEffort.OFF) "思考: ${thinkingEffort.label}" else "思考",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (thinkingEffort != ThinkingEffort.OFF) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (thinkingEffort != ThinkingEffort.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Spacer pushing right controls to the right
                Spacer(Modifier.weight(1f))

                // Right action group: [Model Pill], [Send/Stop Button]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // [Model Capsule] Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenModelDialog() },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ModelBrandBadge(modelId = activeModelId, size = 15.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = (activeModel?.name ?: activeModelId).ifBlank { "模型" }.substringAfterLast('/'),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 80.dp),
                            )
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = "选择模型",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // [Send/Stop Button]
                    if (isStreaming) {
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)), CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Stop,
                                contentDescription = "停止生成",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        val canSend = inputText.trim().isNotEmpty() || pendingAttachments.isNotEmpty()
                        IconButton(
                            onClick = onSend,
                            enabled = canSend,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(
                                    BorderStroke(1.dp, if (canSend) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
                                    CircleShape,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Send,
                                contentDescription = "发送",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Thinking Intensity selector dialog with 12-step slider and quick options. */
@Composable
private fun ThinkingEffortDialog(
    currentEffort: ThinkingEffort,
    modelSupportsReasoning: Boolean,
    onSelect: (ThinkingEffort) -> Unit,
    onDismiss: () -> Unit,
) {
    var localEffort by remember(currentEffort) { mutableStateOf(currentEffort) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "思考强度设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (modelSupportsReasoning) "当前模型支持深度思考推理，拖动药丸滑块可精细调节思考预算 (0 ~ 65,536 tokens)。"
                    else "当前模型未显式声明思考能力，设置思考强度将向下游传递推理预算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )

                Spacer(Modifier.height(14.dp))

                // Current effort pill display
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "当前档位: ${localEffort.label} (第 ${localEffort.step} 档)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (localEffort.budgetTokens > 0) "${localEffort.budgetTokens} tokens" else "关闭思考",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 12-Step discrete pill slider
                SteppedPillSlider(
                    currentEffort = localEffort,
                    onEffortChanged = {
                        localEffort = it
                        onSelect(it)
                    },
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "12 级档位列表:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(6.dp))

                ThinkingEffort.entries.forEach { effort ->
                    val isSelected = effort == localEffort
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                localEffort = effort
                                onSelect(effort)
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    localEffort = effort
                                    onSelect(effort)
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = effort.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (effort.budgetTokens > 0) "${effort.budgetTokens} tokens" else "关闭",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** MCP tool calling and Exa AI search configuration dialog. */
@Composable
private fun McpSettingsDialog(
    enabled: Boolean,
    exaApiKey: String,
    servers: List<McpServerConfig>,
    onToggleEnabled: (Boolean) -> Unit,
    onSaveExaApiKey: (String) -> Unit,
    onAddServer: (McpServerConfig) -> Unit,
    onDeleteServer: (String) -> Unit,
    onToggleServer: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKeyText by remember { mutableStateOf(exaApiKey) }
    var showAddServerDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Handyman,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "MCP 与 Exa 搜索",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                Text(
                    text = "支持内置 Exa AI 全网实时检索，并支持接入符合三种传输规范（Streamable HTTP、SSE、WebSocket）的任意远程 MCP 服务器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                // Exa API Key Input
                Text(
                    text = "Exa API Key (用于智能联网搜索):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入 Exa API Key...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { onSaveExaApiKey(apiKeyText.trim()) },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("保存", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Remote MCP Servers Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "远程 MCP 服务器 (${servers.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = { showAddServerDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加服务", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Servers list
                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无远程 MCP 服务，可点击上方按钮添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(servers, key = { it.id }) { srv ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = srv.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "${srv.protocol.label} • ${srv.url}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = srv.enabled,
                                            onCheckedChange = { onToggleServer(srv.id, it) },
                                        )
                                        IconButton(onClick = { onDeleteServer(srv.id) }, modifier = Modifier.size(28.dp)) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "删除",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )

    if (showAddServerDialog) {
        AddMcpServerDialog(
            onConfirm = { server ->
                onAddServer(server)
                showAddServerDialog = false
            },
            onDismiss = { showAddServerDialog = false },
        )
    }
}

/** Dialog for adding a new remote MCP server supporting 3 protocols. */
@Composable
private fun AddMcpServerDialog(
    onConfirm: (McpServerConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(McpProtocol.STREAMABLE_HTTP) }
    var url by remember { mutableStateOf("") }
    var authHeader by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加远程 MCP 服务器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("服务名称 (如 GitHub MCP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                Text("传输协议:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    McpProtocol.entries.forEach { p ->
                        FilterChip(
                            selected = protocol == p,
                            onClick = { protocol = p },
                            label = { Text(p.name, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务 URL (http/https/ws/wss)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = authHeader,
                    onValueChange = { authHeader = it },
                    label = { Text("鉴权 Header (可选，如 Authorization: Bearer ...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onConfirm(
                            McpServerConfig(
                                name = name.trim(),
                                protocol = protocol,
                                url = url.trim(),
                                authHeader = authHeader.trim(),
                            ),
                        )
                    }
                },
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/** Offline bridge banner. */
@Composable
private fun BridgeOfflineBanner(
    port: Int,
    onStartBridge: () -> Unit,
) {
    Surface(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "本地服务未启动，发送对话将自动建立连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onStartBridge,
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text = "启动 :$port",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Empty state shown when there are no messages yet. */
@Composable
private fun ChatEmptyState(
    activeModelId: String,
    activeModel: HubModel?,
    thinkingEffort: ThinkingEffort,
    mcpEnabled: Boolean,
    onSuggestionClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ModelBrandBadge(modelId = activeModelId, size = 52.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text = "与 ${activeModel?.name ?: activeModelId.ifBlank { "AI 模型" }} 对话",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Claude APP 风格沉浸式排版 • 思考强度: ${thinkingEffort.label} • MCP工具: ${if (mcpEnabled) "已启用" else "已关闭"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(24.dp))

        val suggestions = listOf(
            "写一段 Kotlin 协程并发处理示例，要求带有优雅的取消逻辑",
            "用通俗易懂的语言详细分析 Transformer 注意力机制的演进",
            "分析主流大语言模型的优缺点，并给出选型建议",
            "写一首关于山川与思考的现代哲理诗",
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { prompt ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSuggestionClick(prompt) },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Dialog enabling real-time search, category filtering, and switching of AI models. */
@Composable
private fun ModelSelectorDialog(
    models: List<HubModel>,
    selectedModelId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onRefreshModels: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filterCategory by remember { mutableStateOf("全部") }

    val categories = remember(models) {
        val list = mutableListOf("全部")
        if (models.any { it.isFree }) list.add("免费")
        if (models.any { it.supportsImages }) list.add("视觉")
        if (models.any { it.supportsReasoning }) list.add("思考")
        val brands = listOf("Claude", "GPT", "Gemini", "DeepSeek", "GLM", "Qwen")
        brands.forEach { brand ->
            if (models.any { it.id.contains(brand, ignoreCase = true) || it.name.contains(brand, ignoreCase = true) }) {
                list.add(brand)
            }
        }
        list
    }

    val filtered = remember(models, query, filterCategory) {
        models.filter { model ->
            val matchQuery = query.isBlank() ||
                model.name.contains(query, ignoreCase = true) ||
                model.id.contains(query, ignoreCase = true) ||
                model.vendor.contains(query, ignoreCase = true)

            val matchCategory = when (filterCategory) {
                "全部" -> true
                "免费" -> model.isFree
                "视觉" -> model.supportsImages
                "思考" -> model.supportsReasoning
                else -> model.id.contains(filterCategory, ignoreCase = true) ||
                    model.name.contains(filterCategory, ignoreCase = true)
            }
            matchQuery && matchCategory
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择对话模型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onRefreshModels, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "刷新模型列表",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "搜索模型名称或 ID...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "清除搜索",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { cat ->
                        val selected = filterCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { filterCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(6.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (models.isEmpty()) "暂无可用模型，请在账号页登录或刷新" else "未找到匹配的模型",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filtered, key = { it.id }) { model ->
                            val isSelected = model.id == selectedModelId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelect(model.id) },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ModelBrandBadge(model = model, size = 32.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = model.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (model.isFree) {
                                                Spacer(Modifier.width(6.dp))
                                                PillBadge(text = "免费", variant = BadgeVariant.Success)
                                            }
                                            if (model.supportsImages) {
                                                Spacer(Modifier.width(4.dp))
                                                PillBadge(text = "视觉", variant = BadgeVariant.Neutral)
                                            }
                                            if (model.supportsReasoning) {
                                                Spacer(Modifier.width(4.dp))
                                                PillBadge(text = "思考", variant = BadgeVariant.Neutral)
                                            }
                                        }
                                        Text(
                                            text = model.id,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (isSelected) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = "已选择",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}
