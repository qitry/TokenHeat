package app.anylyralabs.lyraproxy.ui

import app.anylyralabs.lyraproxy.bridge.ApiKey
import app.anylyralabs.lyraproxy.bridge.CallRecord
import app.anylyralabs.lyraproxy.data.CheckinItem
import app.anylyralabs.lyraproxy.proto.Balance
import app.anylyralabs.lyraproxy.proto.Credential
import app.anylyralabs.lyraproxy.proto.SavedAccount
import app.anylyralabs.lyraproxy.proto.HubModel
import app.anylyralabs.lyraproxy.proto.Wire

enum class HubTab(val label: String) {
    Dashboard("概览"),
    Credential("账号"),
    Bridge("服务"),
    Calls("记录"),
}

/** Everything the screens render; owned by the activity and backed by the service. */
data class HubState(
    val credential: Credential? = null,
    val expiryText: String = "",
    val bridgeRunning: Boolean = false,
    val port: Int = 8765,
    val secret: String = "wb-local",
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
    val overlayEnabled: Boolean = false,
    val overlayMovable: Boolean = true,
    /** 完全穿透：窗口不接收触摸。 */
    val overlayPassthrough: Boolean = false,
    /** 是否显示「粘贴凭证导入」对话框。 */
    val showPasteImport: Boolean = false,
    /** 自定义 API Key 列表。 */
    val apiKeys: List<ApiKey> = emptyList(),
    /** 正在编辑的密钥；非空则显示编辑面板。 */
    val editingApiKey: ApiKey? = null,
    /** 是否处于新建密钥状态。 */
    val creatingApiKey: Boolean = false,
    /** 新建成功后展示一次完整密钥。 */
    val justCreatedApiKey: ApiKey? = null,
    /** 待确认删除的密钥。 */
    val deletingApiKey: ApiKey? = null,
    val realm: Wire.Region = Wire.Region.CN,
    val accounts: Map<Wire.Region, List<SavedAccount>> = emptyMap(),
    val activeAccountId: String? = null,
)

/** Which operation is in flight, so each control can show its own indicator. */
enum class Loading { CHECKIN, BALANCE, MODELS }

/** Whether any build holds at least one saved account. */
val HubState.hasAnyCredential: Boolean
    get() = accounts.values.any { it.isNotEmpty() }
