package com.tokenheat.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.ui.graphics.vector.ImageVector
import com.tokenheat.bridge.CallRecord
import com.tokenheat.data.CheckinItem
import com.tokenheat.proto.Balance
import com.tokenheat.proto.Credential
import com.tokenheat.proto.Provider
import com.tokenheat.proto.SavedAccount
import com.tokenheat.proto.HubModel
import com.tokenheat.proto.Wire

enum class HubTab(val label: String, val icon: ImageVector) {
    Dashboard("概览", Icons.Outlined.Dashboard),
    Credential("账号", Icons.Outlined.Key),
    Chat("聊天", Icons.Outlined.ChatBubbleOutline),
    Bridge("服务", Icons.Outlined.Hub),
    Calls("记录", Icons.Outlined.History),
}

enum class ChatRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelId: String? = null,
    val isError: Boolean = false,
    val isStreaming: Boolean = false,
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
)

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
