package com.tokenheat.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.tokenheat.R
import com.tokenheat.bridge.CallRecord
import com.tokenheat.data.CheckinItem
import com.tokenheat.proto.Balance
import com.tokenheat.proto.Credential
import com.tokenheat.proto.Provider
import com.tokenheat.proto.SavedAccount
import com.tokenheat.proto.HubModel
import com.tokenheat.proto.Wire
import com.tokenheat.mcp.McpServerConfig

enum class HubTab(val label: String, val iconResId: Int) {
    Dashboard("概览", R.drawable.ic_lucide_layout_dashboard),
    Credential("账号", R.drawable.ic_lucide_key),
    Chat("聊天", R.drawable.ic_lucide_message_square),
    Bridge("服务", R.drawable.ic_lucide_network),
    Calls("记录", R.drawable.ic_lucide_history);

    val icon: ImageVector
        @Composable get() = ImageVector.vectorResource(iconResId)
}

enum class ChatRole { USER, ASSISTANT, SYSTEM, TOOL }

enum class ThinkingEffort(
    val label: String,
    val levelName: String,
    val budgetTokens: Int,
    val stepIndex: Int,
) {
    OFF("关闭", "none", 0, 0),
    LOW("低", "low", 0, 1),
    MEDIUM("中", "medium", 0, 2),
    HIGH("高", "high", 0, 3);

    val step: Int get() = stepIndex + 1

    companion object {
        fun fromStep(step: Int): ThinkingEffort {
            val clamped = step.coerceIn(0, entries.size - 1)
            return entries[clamped]
        }
    }
}

enum class AttachmentType { IMAGE, TEXT_FILE }

data class ChatAttachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AttachmentType,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val textContent: String = "",
    val base64Data: String = "",
)

data class ChatToolCall(
    val id: String,
    val name: String,
    val arguments: String,
    val result: String? = null,
    val isExecuting: Boolean = false,
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val reasoningContent: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val modelId: String? = null,
    val isError: Boolean = false,
    val isStreaming: Boolean = false,
    val attachments: List<ChatAttachment> = emptyList(),
    val toolCalls: List<ChatToolCall> = emptyList(),
)

data class ChatConversation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "新对话",
    val modelId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
)

/** Everything the screens render; owned by the activity and backed by the service. */
data class HubState(
    val credential: Credential? = null,
    val expiryText: String = "",
    val bridgeRunning: Boolean = false,
    val port: Int = 8765,
    val secret: String = "tokenheat-local",
    val status: String = "",
    val balance: Balance? = null,
    val checkinMessage: String = "",
    val checkinItems: List<CheckinItem> = emptyList(),
    val models: List<HubModel> = emptyList(),
    val notificationsAllowed: Boolean = true,
    val batteryExempt: Boolean = false,
    val loading: Loading? = null,
    val showCredentialDrawer: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val overlayAllowed: Boolean = false,
    val calls: List<CallRecord> = emptyList(),
    val overlayOpacity: Float = 0.94f,
    val overlayLocked: Boolean = false,
    val realm: Wire.Region = Wire.Region.CN,
    val accounts: Map<Wire.Region, List<SavedAccount>> = emptyMap(),
    val activeAccountId: String? = null,
    val provider: Provider = Provider.WORKBUDDY,
    val zcodeAccounts: List<SavedAccount> = emptyList(),
    val zcodeActiveId: String? = null,
    val zenAccounts: List<SavedAccount> = emptyList(),
    val zenActiveId: String? = null,
    /** Generic per-slot accounts for newer providers (Qoder, Antigravity). */
    val slotAccounts: Map<Provider, List<SavedAccount>> = emptyMap(),
    val slotActiveId: Map<Provider, String> = emptyMap(),
    /** Per-provider quota/billing text; WorkBuddy uses [balance] instead. */
    val quotas: Map<Provider, String> = emptyMap(),
    val showZenKeyDialog: Boolean = false,
    val loginFlow: LoginFlowState? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val selectedChatModelId: String? = null,
    val isChatStreaming: Boolean = false,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    val exaApiKey: String = "",
    val mcpServers: List<McpServerConfig> = emptyList(),
    val mcpEnabled: Boolean = false,
    val pendingAttachments: List<ChatAttachment> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val activeConversationId: String? = null,
)

val HubState.activeConversation: ChatConversation?
    get() = conversations.firstOrNull { it.id == activeConversationId } ?: conversations.firstOrNull()

/** Real-time OAuth/CLI login session state for dialog and status rendering. */
data class LoginFlowState(
    val inProgress: Boolean = false,
    val provider: Provider = Provider.WORKBUDDY,
    val title: String = "",
    val authUrl: String = "",
    val statusText: String = "",
    val error: String? = null,
)

/** Which operation is in flight, so each control can show its own indicator. */
enum class Loading { CHECKIN, BALANCE, MODELS }

/** Whether any provider holds at least one saved account. */
val HubState.hasAnyCredential: Boolean
    get() = accounts.values.any { it.isNotEmpty() } || zcodeAccounts.isNotEmpty() || zenAccounts.isNotEmpty()
