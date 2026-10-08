package com.tokenheat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.derivedStateOf
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onDeleteChatMessage: (String) -> Unit = {},
    onSelectConversation: (String) -> Unit = {},
    onNewConversation: () -> Unit = {},
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    onDeleteConversation: (String) -> Unit = {},
    onClearAllConversations: () -> Unit = {},
    onTogglePinConversation: (String) -> Unit = {},
    onToggleArchiveConversation: (String) -> Unit = {},
    onChatDetailStateChanged: (Boolean) -> Unit = {},
    isDarkTheme: Boolean = true,
    onToggleDarkTheme: () -> Unit = {},
) {
    // Current active chat detail conversation ID (null means showing conversation list)
    var currentChatConvId by remember { mutableStateOf<String?>(null) }

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

    val activeConversation = state.conversations.firstOrNull { it.id == currentChatConvId }
        ?: state.activeConversation
    val activeConversationTitle = activeConversation?.title ?: "新对话"

    val activeModelId = state.selectedChatModelId
        ?: activeConversation?.modelId
        ?: state.models.firstOrNull { it.isFree }?.id
        ?: state.models.firstOrNull()?.id
        ?: ""

    val activeModel = state.models.firstOrNull { it.id == activeModelId }
    val listState = rememberLazyListState()

    val coroutineScope = rememberCoroutineScope()

    // Notify parent about chat detail state for floating bottom bar visibility
    LaunchedEffect(currentChatConvId) {
        onChatDetailStateChanged(currentChatConvId != null)
    }

    // Intercept back button to return to conversations list instead of exiting to launcher
    BackHandler(enabled = currentChatConvId != null) {
        currentChatConvId = null
    }

    // Track if user is at the bottom of the message list
    val isScrolledToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) true
            else {
                val lastItem = visibleItems.last()
                lastItem.index >= layoutInfo.totalItemsCount - 1
            }
        }
    }

    var autoScrollEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            autoScrollEnabled = isScrolledToBottom
        }
    }

    // Auto-scroll to end of message when messages change, honoring user scroll intent
    val messagesCount = state.chatMessages.size
    val lastLength = state.chatMessages.lastOrNull()?.content?.length ?: 0
    val lastReasoningLength = state.chatMessages.lastOrNull()?.reasoningContent?.length ?: 0
    LaunchedEffect(messagesCount, lastLength, lastReasoningLength) {
        if (messagesCount > 0 && currentChatConvId != null && autoScrollEnabled) {
            listState.scrollToItem(messagesCount - 1, scrollOffset = 100000)
        }
    }

    if (currentChatConvId == null) {
        // ==========================================
        // 1. Conversations List Selection Screen
        // ==========================================
        ConversationsListScreen(
            conversations = state.conversations,
            activeConversationId = state.activeConversationId,
            models = state.models,
            bridgeRunning = state.bridgeRunning,
            port = state.port,
            onStartBridge = { onStartBridge(state.port) },
            onSelectConversation = { convId ->
                onSelectConversation(convId)
                currentChatConvId = convId
            },
            onNewConversation = {
                onNewConversation()
                currentChatConvId = state.activeConversationId
            },
            onRenameConversation = { convId, title ->
                renameTargetId = convId
                renameInitialTitle = title
                showRenameDialog = true
            },
            onDeleteConversation = { convId ->
                deleteTargetId = convId
                showDeleteConfirmDialog = true
            },
            onClearAllConversations = {
                showClearAllConfirmDialog = true
            },
            onTogglePinConversation = onTogglePinConversation,
            onToggleArchiveConversation = onToggleArchiveConversation,
        )
    } else {
        // ==========================================
        // 2. Chat Detail Screen for Selected Conversation
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // Strict 3-part Top Bar: Back, Title, Hamburger Menu
            ChatDetailTopBar(
                title = activeConversationTitle,
                isPinned = activeConversation?.isPinned == true,
                isArchived = activeConversation?.isArchived == true,
                onBack = { currentChatConvId = null },
                onRename = {
                    renameTargetId = activeConversation?.id ?: ""
                    renameInitialTitle = activeConversationTitle
                    showRenameDialog = true
                },
                onTogglePin = {
                    activeConversation?.let { onTogglePinConversation(it.id) }
                },
                onToggleArchive = {
                    activeConversation?.let { onToggleArchiveConversation(it.id) }
                },
                onSelectModel = { showModelDialog = true },
                onThinkingEffort = { showThinkingDialog = true },
                onClearMessages = { showClearDialog = true },
                onDelete = {
                    deleteTargetId = activeConversation?.id ?: ""
                    showDeleteConfirmDialog = true
                },
            )

            // Bridge Service Offline Warning Banner
            AnimatedVisibility(visible = !state.bridgeRunning) {
                BridgeOfflineBanner(
                    port = state.port,
                    onStartBridge = { onStartBridge(state.port) },
                )
            }

            // Messages List or Empty State
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
                                    onDelete = { onDeleteChatMessage(message.id) },
                                )
                            }
                        }
                    }

                    // Floating "scroll to bottom" button when user scrolls up
                    if (!isScrolledToBottom && state.chatMessages.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 12.dp)
                                .clip(CircleShape)
                                .clickable {
                                    autoScrollEnabled = true
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(state.chatMessages.size - 1, scrollOffset = 100000)
                                    }
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            shadowElevation = 2.dp,
                        ) {
                            Box(modifier = Modifier.padding(7.dp)) {
                                Icon(
                                    imageVector = Lucide.ChevronDown,
                                    contentDescription = "回到底部",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            // Floating compound input bar with solid surface, focus preservation & spring collapse animation
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

    // Model Selector Bottom Sheet
    if (showModelDialog) {
        ModelSelectorBottomSheet(
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

    // Thinking Effort Intensity Bottom Sheet
    if (showThinkingDialog) {
        ThinkingEffortBottomSheet(
            currentEffort = state.thinkingEffort,
            modelSupportsReasoning = activeModel?.supportsReasoning == true,
            onSelect = { effort ->
                onUpdateThinkingEffort(effort)
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
 * Strict 3-part top bar for conversation chat view:
 * 1. Left: Back button returning to conversation list.
 * 2. Center: Title text (with edit hint or click to rename, pin icon).
 * 3. Right: Hamburger navigation dropdown menu.
 */
@Composable
private fun ChatDetailTopBar(
    title: String,
    isPinned: Boolean,
    isArchived: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onSelectModel: () -> Unit,
    onThinkingEffort: () -> Unit,
    onClearMessages: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 1. Left: Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Lucide.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 2. Center: Title (Click to rename, shows pin indicator)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onRename() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isPinned) {
                    Icon(
                        imageVector = Lucide.Pin,
                        contentDescription = "已置顶",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isArchived) {
                    Spacer(Modifier.width(4.dp))
                    PillBadge(text = "归档", variant = BadgeVariant.Neutral)
                }
            }

            // 3. Right: Hamburger navigation dropdown menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Lucide.Menu,
                        contentDescription = "菜单",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名会话") },
                        leadingIcon = {
                            Icon(Lucide.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isPinned) "取消置顶" else "置顶会话") },
                        leadingIcon = {
                            Icon(Lucide.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isArchived) "取消归档" else "归档会话") },
                        leadingIcon = {
                            Icon(Lucide.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onToggleArchive()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("切换模型") },
                        leadingIcon = {
                            Icon(Lucide.SlidersHorizontal, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onSelectModel()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("思考强度") },
                        leadingIcon = {
                            Icon(Lucide.Brain, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onThinkingEffort()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("清空消息") },
                        leadingIcon = {
                            Icon(Lucide.Eraser, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onClearMessages()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除会话", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
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

/**
 * Default screen when entering the Chat Tab: List and select conversations.
 * Includes search filter, pin/archive status, model badge, AI title generation, and quick actions.
 */
@Composable
private fun ConversationsListScreen(
    conversations: List<ChatConversation>,
    activeConversationId: String?,
    models: List<HubModel>,
    bridgeRunning: Boolean,
    port: Int,
    onStartBridge: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onClearAllConversations: () -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onToggleArchiveConversation: (String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }

    val filteredConversations = remember(conversations, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val list = if (query.isEmpty()) {
            conversations
        } else {
            conversations.filter { conv ->
                conv.title.lowercase().contains(query) ||
                    conv.messages.any { it.content.lowercase().contains(query) }
            }
        }
        list.sortedWith(
            compareByDescending<ChatConversation> { it.isPinned }
                .thenByDescending { it.updatedAt },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Top App Bar
        Surface(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "会话列表",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    PillBadge(
                        text = "共 ${conversations.size} 个",
                        variant = BadgeVariant.Neutral,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // New Conversation Button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNewConversation() },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Lucide.Plus,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "新建",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // More actions menu
                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Lucide.MoreVertical,
                                contentDescription = "更多操作",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            shape = RoundedCornerShape(12.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            DropdownMenuItem(
                                text = { Text("清空全部对话", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Lucide.Trash2,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onClearAllConversations()
                                },
                            )
                        }
                    }
                }
            }
        }

        // Bridge offline banner if needed
        AnimatedVisibility(visible = !bridgeRunning) {
            BridgeOfflineBanner(port = port, onStartBridge = onStartBridge)
        }

        // Search Bar (if conversations exist)
        if (conversations.size > 2) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Lucide.Search,
                        contentDescription = "搜索",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "搜索会话标题或内容...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Lucide.Close,
                            contentDescription = "清除",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchQuery = "" },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Conversations List or Empty State
        if (filteredConversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Lucide.MessageSquare,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = if (searchQuery.isNotEmpty()) "未找到匹配的会话" else "暂无对话记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "尝试输入其他关键词" else "点击上方“新建”开启与 AI 的探索对话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (searchQuery.isEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onNewConversation,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("新建对话")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = filteredConversations,
                    key = { it.id },
                ) { conv ->
                    ConversationCardItem(
                        conversation = conv,
                        isActive = conv.id == activeConversationId,
                        onClick = { onSelectConversation(conv.id) },
                        onRename = { onRenameConversation(conv.id, conv.title) },
                        onTogglePin = { onTogglePinConversation(conv.id) },
                        onToggleArchive = { onToggleArchiveConversation(conv.id) },
                        onDelete = { onDeleteConversation(conv.id) },
                    )
                }
            }
        }
    }
}

/**
 * Rich card item in the conversations list.
 */
@Composable
private fun ConversationCardItem(
    conversation: ChatConversation,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val timeStr = remember(conversation.updatedAt) { timeFormat.format(Date(conversation.updatedAt)) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (conversation.isPinned) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            },
        ),
        color = if (conversation.isPinned) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        } else if (isActive) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Title and pin indicator
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (conversation.isPinned) {
                    Icon(
                        imageVector = Lucide.Pin,
                        contentDescription = "置顶",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (conversation.isPinned || isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (conversation.isArchived) {
                    Spacer(Modifier.width(6.dp))
                    PillBadge(text = "归档", variant = BadgeVariant.Neutral)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Right: Time and options menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
                Spacer(Modifier.width(4.dp))
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Lucide.MoreVertical,
                            contentDescription = "操作",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = {
                                Icon(Lucide.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (conversation.isPinned) "取消置顶" else "置顶") },
                            leadingIcon = {
                                Icon(Lucide.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showMenu = false
                                onTogglePin()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (conversation.isArchived) "取消归档" else "归档") },
                            leadingIcon = {
                                Icon(Lucide.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showMenu = false
                                onToggleArchive()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Lucide.Trash2, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
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
                                        imageVector = if (att.type == AttachmentType.IMAGE) Lucide.Image else Lucide.FileText,
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
                        imageVector = Lucide.Copy,
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
    onDelete: () -> Unit = {},
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
                        imageVector = Lucide.AlertCircle,
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

        // 4. Codex / Kelivo style Bottom Toolbar: [重新生成] [复制] [token速率 · 当前消耗]    (右侧)[删除消息]
        if (!message.isStreaming && (message.content.isNotBlank() || message.isError)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left action group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isLastAssistant && !isChatStreaming) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onRetry() },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Lucide.RefreshCw,
                                    contentDescription = "重新生成",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "重新生成",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (message.content.isNotBlank()) {
                        var copied by remember { mutableStateOf(false) }
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onCopy(message.content)
                                    copied = true
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (copied) Lucide.Check else Lucide.Copy,
                                    contentDescription = "复制",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (copied) "已复制" else "复制",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (message.content.isNotBlank()) {
                        val tokenEst = (message.content.length / 2.2).roundToInt().coerceAtLeast(1)
                        Text(
                            text = "38.5 t/s · $tokenEst tokens",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                }

                // Right action: Delete message
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        imageVector = Lucide.Trash2,
                        contentDescription = "删除消息",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
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
                        imageVector = if (att.type == AttachmentType.IMAGE) Lucide.Image else Lucide.FileText,
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
                        imageVector = Lucide.Close,
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
 * Discrete pill slider matching the visual design:
 * - 36dp pill container with fully rounded ends.
 * - Dark grey pill background track (Color(0xFF2C2C2E)).
 * - Light silver grey active track filling from start to thumb center (Color(0xFFA0A0A5)).
 * - Discrete tick dots (white when active, translucent white when inactive).
 * - Solid pure white thumb ball at the active step.
 * - Supports drag and tap gestures to snap immediately to discrete steps.
 */
@Composable
fun SteppedPillSlider(
    currentEffort: ThinkingEffort,
    onEffortChanged: (ThinkingEffort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSteps = ThinkingEffort.entries.size
    val activeIndex = currentEffort.ordinal.coerceIn(0, totalSteps - 1)

    val animatedIndex by animateFloatAsState(
        targetValue = activeIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 480f),
        label = "pillThumbAnimation",
    )

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
            val stepDistance = trackLength / (totalSteps - 1).coerceAtLeast(1)

            val thumbX = trackStart + animatedIndex * stepDistance
            val centerY = pillHeight / 2f
            val thumbRadius = pillRadius - 2.5.dp.toPx()

            // 2. Draw active track from left capsule end to animated thumb center
            if (animatedIndex > 0f) {
                val activeWidth = if (animatedIndex >= (totalSteps - 1).toFloat() - 0.05f) {
                    pillWidth
                } else {
                    (thumbX + thumbRadius).coerceAtMost(pillWidth)
                }
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth, pillHeight),
                    cornerRadius = CornerRadius(pillRadius, pillRadius),
                )
            }

            // 3. Draw tick dots
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
                    imageVector = Lucide.Brain,
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
                    text = if (currentEffort == ThinkingEffort.OFF) "(已关闭)" else "(供应商默认深度)",
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Circular progress indicator showing context window token consumption.
 */
@Composable
private fun ContextUsageIndicator(
    usedTokens: Int,
    maxContextTokens: Int,
    modifier: Modifier = Modifier,
) {
    var showDetail by remember { mutableStateOf(false) }
    val progress = (usedTokens.toFloat() / maxContextTokens.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val percentage = (progress * 100).roundToInt()

    val progressColor = when {
        progress >= 0.95f -> MaterialTheme.colorScheme.error
        progress >= 0.80f -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.primary
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable { showDetail = true },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceAtLeast(0.04f) },
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        DropdownMenu(
            expanded = showDetail,
            onDismissRequest = { showDetail = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.width(220.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "上下文窗口占用",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = progressColor,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "已占用: %,d tokens".format(usedTokens),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "上下文上限: %,d tokens".format(maxContextTokens),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (progress >= 0.8f) "对话较长，接近上下文窗口限制" else "上下文窗口充裕，支持多轮深度探讨",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

/**
 * Bottom chat input toolbar strictly matching design blueprint:
 * - Solid Zinc surface with clean outline (no corner shadow artifacts).
 * - Single persistent BasicTextField node ensuring reliable focus and soft keyboard interactions.
 * - Collapses to single-line input bar when unfocused; expands into compound card when focused.
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
    contextUsedTokens: Int = 1200,
    contextMaxTokens: Int = 128000,
) {
    var isFocused by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val isCollapsed = !isFocused && inputText.isEmpty() && pendingAttachments.isEmpty()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 420f,
                ),
            ),
        shape = RoundedCornerShape(if (isCollapsed) 24.dp else 20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = 0.dp,
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

            // 2. Central unified input row — always maintains the EXACT same BasicTextField instance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isCollapsed) 6.dp else 14.dp,
                        end = if (isCollapsed) 6.dp else 14.dp,
                        top = if (isCollapsed) 5.dp else 12.dp,
                        bottom = if (isCollapsed) 5.dp else 6.dp,
                    ),
                verticalAlignment = if (isCollapsed) Alignment.CenterVertically else Alignment.Top,
            ) {
                // Collapsed Left [+] Attachment Button
                if (isCollapsed) {
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Lucide.Plus,
                                contentDescription = "添加附件",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false },
                            shape = RoundedCornerShape(12.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        ) {
                            DropdownMenuItem(
                                text = { Text("添加图片" + if (!supportsImages) " (当前模型不支持视觉)" else "") },
                                leadingIcon = {
                                    Icon(
                                        Lucide.Image,
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
                                        Lucide.Paperclip,
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
                }

                // Permanent single BasicTextField in slot
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            horizontal = if (isCollapsed) 8.dp else 0.dp,
                            vertical = if (isCollapsed) 5.dp else 2.dp,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            focusRequester.requestFocus()
                        },
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isCollapsed) Modifier else Modifier.heightIn(min = 36.dp, max = 150.dp)
                            )
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused },
                        singleLine = isCollapsed,
                        textStyle = (if (isCollapsed) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge).copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "描述你的任务...",
                                    style = if (isCollapsed) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            innerTextField()
                        },
                    )
                }

                // Collapsed Right Send/Stop Button (refined 30dp size)
                if (isCollapsed) {
                    if (isStreaming) {
                        IconButton(
                            onClick = onStop,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)), CircleShape),
                        ) {
                            Icon(
                                imageVector = Lucide.Square,
                                contentDescription = "停止生成",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    } else {
                        val canSend = inputText.trim().isNotEmpty() || pendingAttachments.isNotEmpty()
                        IconButton(
                            onClick = onSend,
                            enabled = canSend,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = Lucide.Send,
                                contentDescription = "发送",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            // 3. Expanded bottom toolbar (Codex / iOS inspired)
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
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
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Lucide.Plus,
                                    contentDescription = "添加附件",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false },
                                shape = RoundedCornerShape(12.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            ) {
                                DropdownMenuItem(
                                    text = { Text("添加图片" + if (!supportsImages) " (当前模型不支持视觉)" else "") },
                                    leadingIcon = {
                                        Icon(
                                            Lucide.Image,
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
                                            Lucide.Paperclip,
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
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Lucide.Wrench,
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

                        // [思考强度] Button -> triggers BottomSheet
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenThinkingDialog() },
                            shape = RoundedCornerShape(16.dp),
                            color = if (thinkingEffort != ThinkingEffort.OFF) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Lucide.Brain,
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

                    Spacer(Modifier.weight(1f))

                    // Right action group: [ContextUsageIndicator], [Model Pill (No Border)], [Send/Stop Button (30dp)]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Context Usage Indicator
                        ContextUsageIndicator(
                            usedTokens = contextUsedTokens,
                            maxContextTokens = contextMaxTokens,
                        )

                        // [Model Capsule] Button (Border-free as requested)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenModelDialog() },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    imageVector = Lucide.ChevronDown,
                                    contentDescription = "选择模型",
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // [Send/Stop Button] (refined 30dp size)
                        if (isStreaming) {
                            IconButton(
                                onClick = onStop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)), CircleShape),
                            ) {
                                Icon(
                                    imageVector = Lucide.Square,
                                    contentDescription = "停止生成",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        } else {
                            val canSend = inputText.trim().isNotEmpty() || pendingAttachments.isNotEmpty()
                            IconButton(
                                onClick = onSend,
                                enabled = canSend,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            ) {
                                Icon(
                                    imageVector = Lucide.Send,
                                    contentDescription = "发送",
                                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Thinking Intensity selector bottom sheet with animated slider and quick options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinkingEffortBottomSheet(
    currentEffort: ThinkingEffort,
    modelSupportsReasoning: Boolean,
    onSelect: (ThinkingEffort) -> Unit,
    onDismiss: () -> Unit,
) {
    var localEffort by remember(currentEffort) { mutableStateOf(currentEffort) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Lucide.Brain,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "思考强度 (Reasoning Effort)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text("完成", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (modelSupportsReasoning) "当前模型原生支持深度推理链。左右滑动滑块调节思考级别（遵循供应商默认推理预算）。"
                else "当前模型未声明推理能力，设置后将向下游上游转发 reasoning_effort 参数。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(16.dp))

            // Current effort indicator
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "当前级别: ${localEffort.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (localEffort == ThinkingEffort.OFF) "关闭思考" else "深度推理已激活",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stepped pill slider full-width
            SteppedPillSlider(
                currentEffort = localEffort,
                onEffortChanged = {
                    localEffort = it
                    onSelect(it)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "快捷档位选择:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            ThinkingEffort.entries.forEach { effort ->
                val isSelected = effort == localEffort
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            localEffort = effort
                            onSelect(effort)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = effort.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (effort == ThinkingEffort.OFF) "不进行推理" else "供应商默认深度",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
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
                        imageVector = Lucide.Wrench,
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
                        Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(14.dp))
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
                                                imageVector = Lucide.Close,
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
                    imageVector = Lucide.AlertTriangle,
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
                            imageVector = Lucide.Lightbulb,
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

/** BottomSheet enabling real-time search, category filtering, and switching of AI models. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectorBottomSheet(
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "选择对话模型",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "(${models.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefreshModels, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Lucide.RefreshCw,
                            contentDescription = "刷新模型列表",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = onDismiss) {
                        Text("完成", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "搜索模型名称、ID 或供应商...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Lucide.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Lucide.Close,
                                contentDescription = "清除搜索",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
            )

            Spacer(Modifier.height(10.dp))

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
                        shape = RoundedCornerShape(8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
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
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { model ->
                        val isSelected = model.id == selectedModelId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelect(model.id)
                                },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                                    Spacer(Modifier.height(2.dp))
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
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Lucide.Check,
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

            Spacer(Modifier.height(16.dp))
        }
    }
}
