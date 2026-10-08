package com.tokenheat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.tokenheat.bridge.BridgeService
import com.tokenheat.bridge.BridgeSettings
import com.tokenheat.bridge.CallLogStore
import com.tokenheat.bridge.Notifications
import com.tokenheat.data.AccountBackup
import com.tokenheat.data.CheckinItem
import com.tokenheat.data.AGLogin
import com.tokenheat.data.Login
import com.tokenheat.data.QLogin
import com.tokenheat.data.ZLogin
import com.tokenheat.proto.CheckinOutcome
import com.tokenheat.proto.Credential
import com.tokenheat.proto.CredentialStore
import com.tokenheat.proto.HubModel
import com.tokenheat.proto.Provider
import com.tokenheat.proto.SavedAccount
import com.tokenheat.proto.UpstreamClient
import com.tokenheat.proto.Wire
import com.tokenheat.proto.ZUpstreamClient
import com.tokenheat.proto.QUpstreamClient
import com.tokenheat.proto.AGUpstreamClient
import com.tokenheat.proto.ZenUpstreamClient
import com.tokenheat.proto.toHubModel
import com.tokenheat.ui.CheckinDialog
import com.tokenheat.ui.HubApp
import com.tokenheat.ui.Loading
import com.tokenheat.ui.LoginFlowState
import com.tokenheat.mcp.McpManager
import com.tokenheat.mcp.McpProtocol
import com.tokenheat.mcp.McpServerConfig
import com.tokenheat.ui.AttachmentType
import com.tokenheat.ui.ChatAttachment
import com.tokenheat.ui.ChatMessage
import com.tokenheat.ui.ChatRole
import com.tokenheat.ui.ChatToolCall
import com.tokenheat.ui.HubState
import com.tokenheat.ui.HubTab
import com.tokenheat.ui.ThinkingEffort
import com.tokenheat.ui.realmName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.tokenheat.data.ChatConversationStore
import com.tokenheat.ui.ChatConversation

class MainActivity : ComponentActivity() {

    private lateinit var store: CredentialStore
    private lateinit var callLog: CallLogStore
    private lateinit var chatStore: ChatConversationStore
    private var service: BridgeService? = null
    private var bound by mutableStateOf(false)
    private var pollJob: Job? = null
    private var chatJob: Job? = null
    private var currentChatConn: java.net.HttpURLConnection? = null

    private var state by mutableStateOf(HubState())
    private var showHelp by mutableStateOf(false)
    private var askedForNotifications = false
    private val upstream = UpstreamClient()
    private val zupstream = ZUpstreamClient()
    private val zenUpstream = ZenUpstreamClient()
    private val qoderUpstream = QUpstreamClient()
    private val agUpstream = AGUpstreamClient()

    /**
     * Sign-in polling must survive the app leaving the foreground, because the
     * user completes it in the browser; a lifecycle-bound scope would be
     * suspended exactly when the poll needs to run.
     */
    private val loginScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? BridgeService.LocalBinder)?.service()
            bound = service != null
            state = state.copy(bridgeRunning = bound, port = service?.port ?: state.port)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            state = state.copy(bridgeRunning = false)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state = state.copy(
            notificationsAllowed = granted,
            status = if (granted) "" else "未授予通知权限，转发服务可能被系统回收",
        )
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            handleImportBackup(uri)
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) handlePickedImage(uri)
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) handlePickedFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CredentialStore(this)
        callLog = CallLogStore(this)
        chatStore = ChatConversationStore(this)
        // One-time repair: keys once mis-saved into a build file move home.
        store.repairMisplacedApiKeys()
        Notifications.ensureChannel(this)
        refreshCredential()
        // Keep the daemon's credential copy current, including the first run
        // after an account switch happened while it was not running.
        store.mirrorActiveForDaemon()
        loadBalance()
        loadModels()
        loadCalls()
        loadMcpSettings()
        loadChatConversations()
        state = state.copy(
            notificationsAllowed = Notifications.hasPermission(this),
            overlayOpacity = BridgeSettings.opacity(this),
            overlayLocked = BridgeSettings.locked(this),
        )
        // Starting the bridge from the foreground is what makes the foreground
        // promotion legal; a later start from a background caller is refused.
        startBridge(state.port, silent = true)

        setContent {
            HubApp(
                state = state,
                onStartBridge = { port -> startBridge(port) },
                onStopBridge = { stopBridge() },
                onLogin = { region -> startLogin(region) },
                onLogout = { state = state.copy(showLogoutConfirm = true) },
                onClearCalls = { clearCalls() },
                onRefreshCalls = { loadCalls() },
                onOverlayOpacity = { value ->
                    BridgeSettings.setOpacity(this, value)
                    // Applied to the live panel, so the slider is visible as it moves.
                    BridgeService.overlayRef?.panelOpacity = value
                    state = state.copy(overlayOpacity = value)
                },
                onOverlayLocked = { locked ->
                    BridgeSettings.setLocked(this, locked)
                    BridgeService.overlayRef?.locked = locked
                    state = state.copy(overlayLocked = locked)
                },
                onSwitchRealm = { region -> switchRealm(region) },
                onSwitchAccount = { id -> switchAccount(id) },
                onDeleteAccount = { id -> deleteAccount(id) },
                onSwitchProvider = { provider -> switchProvider(provider) },
                onLoginZcode = { startZLogin() },
                onSwitchZcodeAccount = { id -> switchZcodeAccount(id) },
                onDeleteZcodeAccount = { id -> deleteZcodeAccount(id) },
                onToggleAccount = { account -> toggleAccount(account) },
                onSwitchZenAccount = { id -> switchZenAccount(id) },
                onDeleteZenAccount = { id -> deleteZenAccount(id) },
                onShowZenKeyDialog = { state = state.copy(showZenKeyDialog = true) },
                onDismissZenKeyDialog = { state = state.copy(showZenKeyDialog = false) },
                onConfirmZenKey = { key, label -> addZenKey(key, label) },
                onLoginQoder = { region -> startQoderLogin(region) },
                onSwitchSlotAccount = { provider, id -> switchSlotAccount(provider, id) },
                onDeleteSlotAccount = { provider, id -> deleteSlotAccount(provider, id) },
                onLoginAG = { startAGLogin() },
                onRequestOverlay = { requestOverlayPermission() },
                onOpenCredentialDetails = { state = state.copy(showCredentialDrawer = true) },
                onDismissCredentialDetails = { state = state.copy(showCredentialDrawer = false) },
                onCopyField = { label, value -> copyToClipboard(value, "已复制 $label") },
                onCheckin = { doCheckin() },
                onCheckinAll = { checkinAllAccounts() },
                onRefreshBalance = { loadBalance() },
                onCopyEndpoint = { copyEndpoint() },
                onCopyModel = { id -> copyToClipboard(id, "已复制模型名") },
                onRequestNotifications = { requestNotificationPermissionIfNeeded() },
                onRefreshModels = { loadModels() },
                onRequestBatteryExemption = { requestBatteryExemption() },
                onDismissCheckin = { state = state.copy(checkinMessage = "") },
                showHelp = showHelp,
                onShowHelp = { showHelp = true },
                onDismissHelp = { showHelp = false },
                onConfirmLogout = {
                    state = state.copy(showLogoutConfirm = false)
                    logout()
                },
                onDismissLogout = {
                    state = state.copy(showLogoutConfirm = false)
                },
                onOpenAuthUrl = { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                },
                onCancelLogin = { cancelLogin() },
                onDismissLoginDialog = { state = state.copy(loginFlow = null) },
                onExportBackup = { handleExportBackup() },
                onImportBackup = { pickBackupFile() },
                onTabShown = { tab ->
                    // Reload on entry: the service writes records without the
                    // UI knowing, so a snapshot taken at startup goes stale.
                    when (tab) {
                        HubTab.Calls -> loadCalls()
                        HubTab.Credential -> loadBalance()
                        HubTab.Chat -> if (state.models.isEmpty()) loadModels()
                        else -> Unit
                    }
                },
                onSendMessage = { text, atts -> sendChatMessage(text, atts) },
                onStopStreaming = { stopChatMessage() },
                onClearMessages = { clearChatMessages() },
                onSelectChatModel = { modelId -> state = state.copy(selectedChatModelId = modelId) },
                onRetryChatMessage = { retryChatMessage() },
                onUpdateThinkingEffort = { effort -> state = state.copy(thinkingEffort = effort) },
                onUpdateExaApiKey = { key -> updateExaApiKey(key) },
                onToggleMcpEnabled = { en -> updateMcpEnabled(en) },
                onAddMcpServer = { srv -> addMcpServer(srv) },
                onDeleteMcpServer = { id -> deleteMcpServer(id) },
                onToggleMcpServer = { id, en -> toggleMcpServer(id, en) },
                onPickImage = { imagePickerLauncher.launch("image/*") },
                onPickFile = { filePickerLauncher.launch("*/*") },
                onRemoveAttachment = { id -> state = state.copy(pendingAttachments = state.pendingAttachments.filter { it.id != id }) },
                onSelectConversation = { convId -> switchConversation(convId) },
                onNewConversation = { createConversation() },
                onRenameConversation = { convId, newTitle -> renameConversation(convId, newTitle) },
                onDeleteConversation = { convId -> deleteConversation(convId) },
                onClearAllConversations = { clearAllConversations() },
            )
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun handleExportBackup() {
        runCatching {
            val (_, shareIntent) = AccountBackup.exportToFileAndGetIntent(this, store)
            startActivity(Intent.createChooser(shareIntent, "导出并备份账号"))
        }.onFailure { e ->
            toast("导出备份失败：${e.message}")
        }
    }

    private fun pickBackupFile() {
        importLauncher.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream", "*/*"))
    }

    private fun handleImportBackup(uri: android.net.Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        AccountBackup.importFromGzipStream(stream, store)
                    }
                }.getOrNull()
            }
            if (result != null && result.success) {
                refreshCredential()
                loadModels()
                loadBalance()
                toast(result.message)
            } else {
                toast(result?.message ?: "导入备份失败")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Tapping the login notification returns here; make sure the app shows
        // the credential it just acquired.
        if (intent.getBooleanExtra(Notifications.EXTRA_OPEN_AFTER_LOGIN, false)) {
            refreshCredential()
            showHelp = false
        }
    }

    override fun onResume() {
        super.onResume()
        // Sign-in finishes in the browser, so the credential on disk is newer
        // than whatever this instance loaded; re-read it on every return.
        refreshCredential()
        state = state.copy(
            batteryExempt = isIgnoringBatteryOptimizations(),
            overlayAllowed = canDrawOverlay(),
        )
    }

    /**
     * Asks for POST_NOTIFICATIONS once per process. Launching from onCreate would
     * race the first composition, and re-asking on every launch is pointless
     * once the user has answered, so a denial is recorded instead of retried.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (Notifications.hasPermission(this)) return
        if (askedForNotifications) {
            state = state.copy(status = "如需后台保活，请在系统设置中允许通知")
            return
        }
        askedForNotifications = true
        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onDestroy() {
        stopChatMessage()
        if (bound) runCatching { unbindService(connection) }
        pollJob?.cancel()
        super.onDestroy()
    }

    /** Re-reads the account in use and every provider's saved accounts. */
    private fun refreshCredential() {
        val provider = store.activeProvider()
        val wbAccount = store.activeAccount()
        val zcodeAccount = store.activeZcodeAccount()
        val zenAccount = store.activeZenAccount()
        val account = store.effectiveActive()
        state = state.copy(
            provider = provider,
            realm = store.activeRegion(),
            credential = account,
            expiryText = account?.let { formatExpiry(it) } ?: "",
            accounts = Wire.Region.entries.associateWith { store.accounts(it) },
            activeAccountId = wbAccount?.id,
            zcodeAccounts = store.zcodeAccounts(),
            zcodeActiveId = zcodeAccount?.id,
            zenAccounts = store.zenAccounts(),
            zenActiveId = zenAccount?.id,
            slotAccounts = Provider.entries
                .filter { it != Provider.WORKBUDDY }
                .associateWith { store.slotAccounts(it) },
            slotActiveId = Provider.entries.mapNotNull { p ->
                store.activeSlotAccount(p)?.id?.let { p to it }
            }.toMap(),
        )
    }

    /** Switches to another saved Zen account. */
    private fun switchZenAccount(accountId: String) {
        store.selectZenAccount(accountId)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已切换账号")
    }

    /** Removes one saved Zen account. */
    private fun deleteZenAccount(accountId: String) {
        store.deleteZen(accountId)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已删除账号")
    }

    /**
     * Saves a pasted Zen API key. The label is only a display nickname; the
     * account id still derives from the key digest, so pasting the same key
     * twice renews the label instead of duplicating.
     */
    private fun addZenKey(key: String, label: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            state = state.copy(showZenKeyDialog = false)
            return
        }
        store.saveZen(
            Credential(
                provider = Provider.ZEN,
                accessToken = "",
                apiKey = trimmed,
                nickname = label.trim().ifEmpty { "Zen" },
                domain = ZenUpstreamClient.CHAT_BASE,
                source = "manual-zen",
            ),
        )
        store.setActiveProvider(Provider.ZEN)
        state = state.copy(showZenKeyDialog = false)
        refreshCredential()
        loadModels()
        loadBalance()
        toast("Zen API Key 已保存")
    }

    /**
     * Switches which account system the bridge serves. Each provider keeps its
     * own stored accounts, so the switch is instant and needs no re-login.
     */
    private fun switchProvider(provider: Provider) {
        store.setActiveProvider(provider)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已切换到${provider.label}账号")
    }

    /** Switches to another saved ZCode account. */
    private fun switchZcodeAccount(accountId: String) {
        store.selectZcodeAccount(accountId)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已切换账号")
    }

    /** Removes one saved ZCode account. */
    private fun deleteZcodeAccount(accountId: String) {
        store.deleteZcode(accountId)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已删除账号")
    }

    private fun startQoderLogin(region: QLogin.QRegion) {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                runCatching { QLogin.start(region, store.qoderMachineId()) }.getOrNull()
            }
            if (session == null) {
                toast("获取登录链接失败")
                return@launch
            }
            val title = if (region == QLogin.QRegion.CN) "Qoder 国内版" else "Qoder 国际版"
            state = state.copy(
                status = "请在浏览器完成登录：${session.authUrl}",
                loginFlow = LoginFlowState(
                    inProgress = true,
                    provider = region.provider,
                    title = title,
                    authUrl = session.authUrl,
                    statusText = "已生成授权链接，等待浏览器确认…",
                ),
            )
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(session.authUrl))) }
            pollQoderForToken(session)
        }
    }

    /**
     * Polls the Qoder device flow, then reads the user info and saves the
     * account. Same countdown-heartbeat shape as the ZCode poll loop.
     */
    private fun pollQoderForToken(session: QLogin.Session) {
        pollJob?.cancel()
        pollJob = loginScope.launch {
            val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val result = withContext(Dispatchers.IO) { runCatching { QLogin.poll(session) }.getOrNull() }
                when (result) {
                    is QLogin.Poll.Done -> {
                        state = state.copy(
                            status = "登录成功，正在读取账号信息…",
                            loginFlow = state.loginFlow?.copy(statusText = "登录成功，正在读取账号信息…"),
                        )
                        val credential = withContext(Dispatchers.IO) {
                            runCatching {
                                val (uid, name) = QLogin.userinfo(session.region, result.auth.token)
                                QLogin.toCredential(session.region, result.auth, uid, name)
                            }.getOrNull()
                        }
                        if (credential == null) {
                            state = state.copy(
                                status = "读取账号信息失败，请重试",
                                loginFlow = state.loginFlow?.copy(inProgress = false, error = "读取账号信息失败"),
                            )
                            return@launch
                        }
                        store.saveSlot(session.region.provider, credential)
                        store.setActiveProvider(session.region.provider)
                        refreshCredential()
                        loadModels()
                        loadBalance()
                        Notifications.showLoginSuccess(this@MainActivity, credential.nickname)
                        state = state.copy(loginFlow = null)
                        toast("凭证已保存")
                        return@launch
                    }
                    is QLogin.Poll.Failed -> {
                        state = state.copy(
                            status = "登录失败：${result.message}",
                            loginFlow = state.loginFlow?.copy(inProgress = false, error = result.message),
                        )
                        return@launch
                    }
                    else -> {
                        val remain = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                        state = state.copy(
                            status = "请在浏览器完成登录并确认授权（等待中，还剩约${remain / 60}分${remain % 60}秒）：" +
                                session.authUrl,
                            loginFlow = state.loginFlow?.copy(
                                statusText = "等待浏览器授权确认中，剩余约 ${remain / 60}分${remain % 60}秒…",
                            ),
                        )
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
            state = state.copy(
                status = "登录超时，请重试",
                loginFlow = state.loginFlow?.copy(inProgress = false, error = "授权轮询超时，请重试"),
            )
        }
    }

    /**
     * Antigravity Google OAuth via a loopback callback server. The wait runs
     * in [loginScope] (not `lifecycleScope`) because the user finishes in the
     * browser while this activity is stopped.
     */
    private fun startAGLogin() {
        pollJob?.cancel()
        pollJob = loginScope.launch {
            val server = withContext(Dispatchers.IO) {
                runCatching { AGLogin.CallbackServer() }.getOrNull()
            }
            if (server == null) {
                state = state.copy(
                    status = "无法在本机监听回调端口，请重试",
                    loginFlow = state.loginFlow?.copy(inProgress = false, error = "无法在本机监听回调端口"),
                )
                return@launch
            }
            val request = AGLogin.authRequest()
            state = state.copy(
                status = "请在浏览器完成 Google 登录：${request.authUrl}",
                loginFlow = LoginFlowState(
                    inProgress = true,
                    provider = Provider.ANTIGRAVITY,
                    title = "Antigravity CLI",
                    authUrl = request.authUrl,
                    statusText = "正在监听本地回调端口，等待浏览器完成授权…",
                ),
            )
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(request.authUrl))) }
            val callback = withContext(Dispatchers.IO) { server.awaitCode(LOGIN_TIMEOUT_MS) }
            server.close()
            if (callback == null) {
                state = state.copy(
                    status = "登录超时，请重试",
                    loginFlow = state.loginFlow?.copy(inProgress = false, error = "等待授权回调超时，请重试"),
                )
                return@launch
            }
            state = state.copy(
                status = "登录成功，正在兑换 Token…",
                loginFlow = state.loginFlow?.copy(statusText = "登录成功，正在兑换 Token…"),
            )
            val exchangeResult = withContext(Dispatchers.IO) {
                runCatching {
                    val tokens = AGLogin.exchange(callback.first, request.verifier)
                    val projectId = AGLogin.fetchProjectId(tokens.accessToken)
                    AGLogin.toCredential(tokens, projectId)
                }
            }
            val credential = exchangeResult.getOrNull()
            if (credential == null) {
                val errorMsg = exchangeResult.exceptionOrNull()?.message?.take(80) ?: "兑换 Token 失败"
                state = state.copy(
                    status = "兑换 Token 失败：$errorMsg",
                    loginFlow = state.loginFlow?.copy(inProgress = false, error = errorMsg),
                )
                return@launch
            }
            store.saveSlot(Provider.ANTIGRAVITY, credential)
            store.setActiveProvider(Provider.ANTIGRAVITY)
            refreshCredential()
            loadModels()
            loadBalance()
            Notifications.showLoginSuccess(this@MainActivity, credential.nickname)
            state = state.copy(loginFlow = null)
            toast("凭证已保存")
        }
    }

    private fun cancelLogin() {
        pollJob?.cancel()
        pollJob = null
        state = state.copy(
            loginFlow = null,
            status = "已取消登录",
        )
    }

    /** Switches to another saved account of one generic provider slot. */
    private fun switchSlotAccount(provider: Provider, accountId: String) {
        store.selectSlot(provider, accountId)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已切换账号")
    }

    /** Removes one saved account of a generic provider slot. */
    private fun deleteSlotAccount(provider: Provider, accountId: String) {
        store.deleteSlot(provider, accountId)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已删除账号")
    }

    /**
     * Takes one account out of (or returns it to) rotation. The pool reads the
     * store on every request, so the switch applies without restarting the
     * bridge; manual selection, balance and check-in are unaffected.
     */
    private fun toggleAccount(account: SavedAccount) {
        store.setDisabled(account, !account.disabled)
        refreshCredential()
        state = state.copy(status = if (account.disabled) "已恢复轮训" else "已暂停轮训")
    }

    /** Switches to another saved account inside the current build. */
    private fun switchAccount(accountId: String) {
        val region = state.realm
        if (store.accounts(region).none { it.id == accountId }) return
        store.selectAccount(region, accountId)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已切换账号")
    }

    /** Removes one saved account. */
    private fun deleteAccount(accountId: String) {
        val region = state.realm
        store.delete(region, accountId)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已删除账号")
    }

    /**
     * Switches which build's credential the bridge serves. Each build keeps its
     * own stored credential, so the switch is instant and needs no re-login;
     * models and balance are reloaded because they belong to the account.
     */
    private fun switchRealm(region: Wire.Region) {
        store.setActiveRegion(region)
        refreshCredential()
        loadModels()
        loadBalance()
        state = state.copy(status = "已切换到${regionLabel(region)}账号")
    }

    private fun startBridge(port: Int, silent: Boolean = false) {
        requestNotificationPermissionIfNeeded()
        val intent = Intent(this, BridgeService::class.java).apply {
            putExtra(BridgeService.EXTRA_PORT, port)
            putExtra(BridgeService.EXTRA_SECRET, state.secret)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }.onFailure {
            state = state.copy(status = "服务启动失败：${it.message?.take(60)}")
            if (!silent) toast("服务启动失败")
            return
        }
        runCatching { bindService(intent, connection, Context.BIND_AUTO_CREATE) }
        state = state.copy(bridgeRunning = true, port = port, status = "")
        loadModels()
    }

    private fun stopBridge() {
        runCatching { unbindService(connection) }
        stopService(Intent(this, BridgeService::class.java))
        state = state.copy(bridgeRunning = false, status = "服务已停止")
    }

    /**
     * Runs [block] with [Loading] shown, keeping the indicator on screen for at
     * least [MIN_LOADING_MS]. Upstream answers in well under a second, and a
     * flash that short reads as "nothing happened".
     */
    private suspend fun <T> withLoading(kind: Loading, block: suspend () -> T): T {
        state = state.copy(loading = kind)
        val started = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_LOADING_MS) delay(MIN_LOADING_MS - elapsed)
            state = state.copy(loading = null)
        }
    }

    /**
     * Merged roster across every provider holding an account: prefixed ids
     * (`provider/id`) for mixed-pool routing, plus bare ids of the active
     * provider so existing client configs keep working.
     */
    private fun loadModels() {
        if (!store.hasAny()) return
        val active = state.provider
        lifecycleScope.launch {
            val merged = withContext(Dispatchers.IO) {
                withLoading(Loading.MODELS) {
                    val out = mutableListOf<HubModel>()
                    out += modelsFor(active, bare = true)
                    Provider.entries.filter { it != active }.forEach { out += modelsFor(it, bare = false) }
                    out
                }
            }
            state = state.copy(models = merged)
        }
    }

    private fun modelsFor(provider: Provider, bare: Boolean): List<HubModel> {
        val prefix = if (bare) "" else "${provider.routeKey}/"
        return when (provider) {
            Provider.WORKBUDDY -> {
                val cred = store.active() ?: return emptyList()
                runCatching {
                    upstream.fetchModels(cred).map {
                        it.toHubModel().copy(id = prefix + it.id, name = prefix + it.name)
                    }
                }.getOrDefault(emptyList())
            }
            Provider.ZCODE -> {
                val cred = store.activeZcode() ?: return emptyList()
                runCatching {
                    zupstream.fetchModels(cred).map {
                        it.toZcodeHubModel().copy(id = prefix + it, name = prefix + it)
                    }
                }.getOrDefault(emptyList())
            }
            Provider.ZEN -> runCatching {
                zenUpstream.fetchModels().map {
                    it.toZenHubModel().copy(id = prefix + it.id, name = prefix + it.id)
                }.sortedByDescending { it.badges.isNotEmpty() }
            }.getOrDefault(emptyList())
            Provider.QODER_CN, Provider.QODER_GLOBAL -> {
                val cred = store.activeSlotAccount(provider)?.toCredential() ?: return emptyList()
                runCatching {
                    qoderUpstream.fetchModels(cred).map {
                        HubModel(id = prefix + it, name = prefix + it, vendor = "Qoder")
                    }
                }.getOrDefault(emptyList())
            }
            Provider.ANTIGRAVITY -> {
                val cred = store.activeSlotAccount(provider)?.toCredential() ?: return emptyList()
                runCatching {
                    agUpstream.fetchModels(cred).map {
                        HubModel(id = prefix + it, name = prefix + it, vendor = "Antigravity")
                    }
                }.getOrDefault(emptyList())
            }
        }
    }

    /** ZCode ids arrive bare; only the vendor tag is derived for display. */
    private fun String.toZcodeHubModel(): HubModel = HubModel(
        id = this,
        name = this,
        vendor = if (startsWith("glm", ignoreCase = true)) "智谱" else "",
    )

    /** Free models sort first; the 免费 badge is rendered by the model list. */
    private fun ZenUpstreamClient.ZenModel.toZenHubModel(): HubModel = HubModel(
        id = id,
        name = id,
        multiplier = if (free) 0.0 else -1.0,
        rate = if (free) "免费 $0" else "",
        vendor = "Zen",
        badges = if (free) listOf("免费") else emptyList(),
    )

    private fun startLogin(region: Wire.Region) {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                runCatching { Login.start(region.toLoginRealm()) }.getOrNull()
            }
            if (session == null) {
                toast("获取登录链接失败")
                return@launch
            }
            val title = "WorkBuddy ${realmName(region)}"
            state = state.copy(
                status = "请在浏览器完成登录：${session.authUrl}",
                loginFlow = LoginFlowState(
                    inProgress = true,
                    provider = Provider.WORKBUDDY,
                    title = title,
                    authUrl = session.authUrl,
                    statusText = "已生成授权链接，等待浏览器确认…",
                ),
            )
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(session.authUrl))) }
            pollForToken(session.state, region.toLoginRealm())
        }
    }

    private fun pollForToken(stateId: String, realm: Login.Realm) {
        pollJob?.cancel()
        pollJob = loginScope.launch {
            val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val result = withContext(Dispatchers.IO) { runCatching { Login.poll(stateId, realm) }.getOrNull() }
                when (result) {
                    is Login.Poll.Done -> {
                        val target = realm.toRegion()
                        store.save(target, result.credential)
                        store.setActiveRegion(target)
                        refreshCredential()
                        loadModels()
                        loadBalance()
                        Notifications.showLoginSuccess(this@MainActivity, result.credential.nickname)
                        state = state.copy(loginFlow = null)
                        toast("凭证已保存")
                        return@launch
                    }
                    is Login.Poll.Failed -> {
                        state = state.copy(
                            status = "登录失败：${result.message}",
                            loginFlow = state.loginFlow?.copy(inProgress = false, error = result.message),
                        )
                        return@launch
                    }
                    else -> {
                        val remain = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                        state = state.copy(
                            loginFlow = state.loginFlow?.copy(
                                statusText = "等待浏览器授权确认中，剩余约 ${remain / 60}分${remain % 60}秒…",
                            ),
                        )
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
            state = state.copy(
                status = "登录超时，请重试",
                loginFlow = state.loginFlow?.copy(inProgress = false, error = "授权轮询超时，请重试"),
            )
        }
    }

    private fun startZLogin() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                runCatching { ZLogin.start() }.getOrNull()
            }
            if (session == null) {
                toast("获取登录链接失败")
                return@launch
            }
            state = state.copy(
                status = "请在浏览器完成登录：${session.authUrl}",
                loginFlow = LoginFlowState(
                    inProgress = true,
                    provider = Provider.ZCODE,
                    title = "ZCode",
                    authUrl = session.authUrl,
                    statusText = "已生成授权链接，等待浏览器确认…",
                ),
            )
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(session.authUrl))) }
            pollZcodeForToken(session)
        }
    }

    /**
     * Polls the ZCode OAuth flow, then exchanges the token for an API key.
     */
    private fun pollZcodeForToken(session: ZLogin.Session) {
        pollJob?.cancel()
        pollJob = loginScope.launch {
            val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val result = withContext(Dispatchers.IO) { runCatching { ZLogin.poll(session) }.getOrNull() }
                when (result) {
                    is ZLogin.Poll.Done -> {
                        state = state.copy(
                            status = "登录成功，正在兑换 API Key…",
                            loginFlow = state.loginFlow?.copy(statusText = "登录成功，正在兑换 API Key…"),
                        )
                        val credential = withContext(Dispatchers.IO) {
                            runCatching { ZLogin.exchange(result.oauthToken) }.getOrNull()
                        }
                        if (credential == null) {
                            state = state.copy(
                                status = "兑换 API Key 失败，请重试",
                                loginFlow = state.loginFlow?.copy(inProgress = false, error = "兑换 API Key 失败"),
                            )
                            return@launch
                        }
                        store.saveZcode(credential)
                        store.setActiveProvider(Provider.ZCODE)
                        refreshCredential()
                        loadModels()
                        loadBalance()
                        Notifications.showLoginSuccess(this@MainActivity, credential.nickname)
                        state = state.copy(loginFlow = null)
                        toast("凭证已保存")
                        return@launch
                    }
                    is ZLogin.Poll.Failed -> {
                        state = state.copy(
                            status = "登录失败：${result.message}",
                            loginFlow = state.loginFlow?.copy(inProgress = false, error = result.message),
                        )
                        return@launch
                    }
                    else -> {
                        val remain = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                        state = state.copy(
                            status = "请在浏览器完成登录并确认授权（等待中，还剩约${remain / 60}分${remain % 60}秒）：${session.authUrl}",
                            loginFlow = state.loginFlow?.copy(
                                statusText = "等待浏览器授权确认中，剩余约 ${remain / 60}分${remain % 60}秒…",
                            ),
                        )
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
            state = state.copy(
                status = "登录超时，请重试",
                loginFlow = state.loginFlow?.copy(inProgress = false, error = "授权轮询超时，请重试"),
            )
        }
    }

    /**
     * Checks in every account of the current build, one after another, and
     * reports each outcome separately.
     *
     * Accounts run sequentially on purpose: the upstream is rate-limited, and a
     * burst of concurrent check-ins would make some of them fail for reasons
     * that have nothing to do with the account itself.
     */
    private fun checkinAllAccounts() {
        val region = state.realm
        val targets = state.accounts[region].orEmpty()
        if (targets.isEmpty()) {
            state = state.copy(checkinMessage = "${regionLabel(region)}还没有账号")
            return
        }
        // With a single account the long press would do exactly what a tap does,
        // so say so rather than repeating the same work behind a dialog.
        if (targets.size == 1) {
            doCheckin()
            return
        }
        lifecycleScope.launch {
            val results: List<CheckinItem> = withContext(Dispatchers.IO) {
                withLoading(Loading.CHECKIN) {
                    targets.map { account ->
                        val outcome = runCatching { upstream.checkin(account.toCredential()) }
                            .getOrElse { CheckinOutcome(false, it.message ?: "签到失败") }
                        CheckinItem(label = account.label, ok = outcome.ok, message = outcome.message)
                    }
                }
            }
            state = state.copy(
                checkinItems = results,
                checkinMessage = "已为 ${results.size} 个账号完成签到",
            )
            loadBalance()
        }
    }

    private fun doCheckin() {
        val cred = state.credential
        if (cred == null) {
            state = state.copy(checkinMessage = "请先登录获取凭证")
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                withLoading(Loading.CHECKIN) {
                    runCatching { upstream.checkin(cred) }
                        .getOrElse { CheckinOutcome(false, it.message ?: "签到失败") }
                }
            }
            state = state.copy(checkinMessage = result.message)
            loadBalance()
        }
    }

    private fun loadBalance() {
        val cred = state.credential
        if (cred == null) {
            state = state.copy(status = "余额：未登录")
            return
        }
        if (cred.provider == Provider.ZCODE) {
            loadQuota(cred)
            return
        }
        if (cred.provider == Provider.ZEN) {
            // Zen exposes no quota endpoint; billing guidance is static text.
            state = state.copy(
                quotas = state.quotas + (Provider.ZEN to "按量计费 · 免费模型 $0 · 余额以 opencode.ai 控制台为准"),
                status = "",
            )
            return
        }
        if (cred.provider == Provider.QODER_CN || cred.provider == Provider.QODER_GLOBAL) {
            loadSlotQuota(cred, upstream = { c -> qoderUpstream.fetchQuota(c) })
            return
        }
        if (cred.provider == Provider.ANTIGRAVITY) {
            loadSlotQuota(cred, upstream = { c -> agUpstream.fetchQuota(c) })
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                withLoading(Loading.BALANCE) { runCatching { upstream.fetchCredits(cred) } }
            }
            val balance = result.getOrNull()
            state = state.copy(
                balance = balance,
                status = if (balance == null) {
                    "余额查询失败：${result.exceptionOrNull()?.message?.take(80)}"
                } else {
                    ""
                },
            )
        }
    }

    /** ZCode quota is a display string, not a structured balance. */
    private fun loadQuota(cred: Credential) {
        loadSlotQuota(cred, upstream = { c -> zupstream.fetchQuota(c) })
    }

    /** Fetches a display-string quota for any provider holding one. */
    private fun loadSlotQuota(cred: Credential, upstream: (Credential) -> String?) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                withLoading(Loading.BALANCE) { runCatching { upstream(cred) } }
            }
            val quota = result.getOrNull()
            state = state.copy(
                quotas = state.quotas + (cred.provider to quota.orEmpty()),
                status = if (quota.isNullOrBlank()) {
                    "额度查询失败：${result.exceptionOrNull()?.message?.take(80)}"
                } else {
                    ""
                },
            )
        }
    }
    /** Whether this package is exempt from battery optimisation. */
    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    /**
     * Opens the system dialog that exempts the app from battery optimisation.
     * Without it aggressive OEM policies freeze the foreground service anyway.
     */
    private fun requestBatteryExemption() {
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }.onFailure {
            // Some builds disable the direct request; the settings list always works.
            runCatching {
                startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun copyEndpoint() {
        copyToClipboard("http://127.0.0.1:${state.port}/v1", "已复制接入地址")
    }

    private fun copyToClipboard(text: String, message: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("tokenheat", text))
        toast(message)
    }

    /** Whether the status panel may be drawn over other apps. */
    private fun canDrawOverlay(): Boolean = android.provider.Settings.canDrawOverlays(this)

    /** Opens the system screen where the user grants overlay permission. */
    private fun requestOverlayPermission() {
        runCatching {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                ),
            )
        }.onFailure { toast("请在系统设置中允许悬浮窗") }
    }

    /** Reloads the recorded calls, newest last. */
    private fun loadCalls() {
        state = state.copy(calls = callLog.load())
    }

    /** Discards the whole call history. */
    private fun clearCalls() {
        callLog.clear()
        loadCalls()
        toast("调用记录已清空")
    }

    /** Signs the user out everywhere, discarding all stored credentials. */
    private fun logout() {
        Wire.Region.entries.forEach { store.clear(it) }
        store.clearZcode()
        store.clearZen()
        store.clearSlot(Provider.QODER_CN)
        store.clearSlot(Provider.QODER_GLOBAL)
        store.clearSlot(Provider.ANTIGRAVITY)
        refreshCredential()
        state = state.copy(
            models = emptyList(),
            balance = null,
            quotas = emptyMap(),
            showZenKeyDialog = false,
            status = "已退出登录",
        )
        toast("已退出登录")
    }

    private fun Wire.Region.toLoginRealm(): Login.Realm =
        if (this == Wire.Region.GLOBAL) Login.Realm.GLOBAL else Login.Realm.CN

    private fun regionLabel(region: Wire.Region): String =
        if (region == Wire.Region.GLOBAL) "国际版" else "国内版"

    private fun formatExpiry(cred: Credential): String {
        if (cred.expiresAt <= 0L) return ""
        // Credentials written by different tools carry either seconds or
        // milliseconds; anything far below the epoch-millisecond range is
        // seconds, so normalise before formatting.
        val millis = if (cred.expiresAt < 1_000_000_000_000L) cred.expiresAt * 1000 else cred.expiresAt
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun handlePickedImage(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val fileName = queryFileName(uri) ?: "image_${System.currentTimeMillis()}.png"
                    val mime = contentResolver.getType(uri) ?: "image/png"
                    val att = ChatAttachment(
                        type = AttachmentType.IMAGE,
                        name = fileName,
                        sizeBytes = bytes.size.toLong(),
                        mimeType = mime,
                        base64Data = "data:$mime;base64,$base64",
                    )
                    withContext(Dispatchers.Main) {
                        state = state.copy(pendingAttachments = state.pendingAttachments + att)
                        toast("已添加图片附件: $fileName")
                    }
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) { toast("读取图片失败: ${e.message}") }
            }
        }
    }

    private fun handlePickedFile(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { it.readText() }
                    val fileName = queryFileName(uri) ?: "file.txt"
                    val mime = contentResolver.getType(uri) ?: "text/plain"
                    val att = ChatAttachment(
                        type = AttachmentType.TEXT_FILE,
                        name = fileName,
                        sizeBytes = text.toByteArray().size.toLong(),
                        mimeType = mime,
                        textContent = text.take(300_000),
                    )
                    withContext(Dispatchers.Main) {
                        state = state.copy(pendingAttachments = state.pendingAttachments + att)
                        toast("已添加文本附件: $fileName")
                    }
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) { toast("读取文件失败: ${e.message}") }
            }
        }
    }

    private fun queryFileName(uri: android.net.Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun loadMcpSettings() {
        val prefs = getSharedPreferences("tokenheat_mcp", Context.MODE_PRIVATE)
        val en = prefs.getBoolean("mcp_enabled", false)
        val exaKey = prefs.getString("exa_api_key", "").orEmpty()
        val serversJson = prefs.getString("mcp_servers", "[]").orEmpty()
        val list = mutableListOf<McpServerConfig>()
        runCatching {
            val arr = org.json.JSONArray(serversJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    McpServerConfig(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        protocol = McpProtocol.valueOf(obj.optString("protocol", McpProtocol.STREAMABLE_HTTP.name)),
                        url = obj.optString("url"),
                        authHeader = obj.optString("authHeader"),
                        enabled = obj.optBoolean("enabled", true),
                    )
                )
            }
        }
        state = state.copy(mcpEnabled = en, exaApiKey = exaKey, mcpServers = list)
    }

    private fun updateExaApiKey(key: String) {
        getSharedPreferences("tokenheat_mcp", Context.MODE_PRIVATE).edit().putString("exa_api_key", key).apply()
        state = state.copy(exaApiKey = key)
        toast("已保存 Exa API Key")
    }

    private fun updateMcpEnabled(enabled: Boolean) {
        getSharedPreferences("tokenheat_mcp", Context.MODE_PRIVATE).edit().putBoolean("mcp_enabled", enabled).apply()
        state = state.copy(mcpEnabled = enabled)
    }

    private fun saveMcpServers(servers: List<McpServerConfig>) {
        val arr = org.json.JSONArray()
        for (s in servers) {
            arr.put(org.json.JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("protocol", s.protocol.name)
                put("url", s.url)
                put("authHeader", s.authHeader)
                put("enabled", s.enabled)
            })
        }
        getSharedPreferences("tokenheat_mcp", Context.MODE_PRIVATE).edit().putString("mcp_servers", arr.toString()).apply()
    }

    private fun addMcpServer(server: McpServerConfig) {
        val updated = state.mcpServers + server
        state = state.copy(mcpServers = updated)
        saveMcpServers(updated)
        toast("已添加 MCP 服务器: ${server.name}")
    }

    private fun deleteMcpServer(id: String) {
        val updated = state.mcpServers.filter { it.id != id }
        state = state.copy(mcpServers = updated)
        saveMcpServers(updated)
        toast("已删除 MCP 服务器")
    }

    private fun toggleMcpServer(id: String, enabled: Boolean) {
        val updated = state.mcpServers.map { if (it.id == id) it.copy(enabled = enabled) else it }
        state = state.copy(mcpServers = updated)
        saveMcpServers(updated)
    }

    private fun sendChatMessage(text: String, attachments: List<ChatAttachment> = emptyList()) {
        if (!store.hasAny()) {
            toast("请先在账号页添加或登录账号")
            return
        }

        var currentActiveId = state.activeConversationId
        var convs = state.conversations
        if (currentActiveId == null || convs.none { it.id == currentActiveId }) {
            val fresh = ChatConversation(title = "新对话", modelId = state.selectedChatModelId)
            convs = listOf(fresh) + convs
            currentActiveId = fresh.id
            state = state.copy(conversations = convs, activeConversationId = currentActiveId)
        }

        val currentConv = convs.first { it.id == currentActiveId }
        val targetModelId = state.selectedChatModelId
            ?: currentConv.modelId
            ?: state.models.firstOrNull { it.isFree }?.id
            ?: state.models.firstOrNull()?.id
            ?: "default"

        val userMsg = ChatMessage(
            role = ChatRole.USER,
            content = text,
            modelId = targetModelId,
            attachments = attachments,
        )
        val assistantMsgId = java.util.UUID.randomUUID().toString()
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            role = ChatRole.ASSISTANT,
            content = "",
            modelId = targetModelId,
            isStreaming = true,
        )

        val newMessages = state.chatMessages + userMsg + assistantMsg
        val autoTitle = if (currentConv.title == "新对话" && currentConv.messages.isEmpty() && text.isNotBlank()) {
            text.trim().lines().firstOrNull { it.isNotBlank() }?.take(22) ?: "新对话"
        } else currentConv.title

        val updatedConvs = convs.map { conv ->
            if (conv.id == currentActiveId) {
                conv.copy(
                    title = autoTitle,
                    modelId = targetModelId,
                    messages = newMessages,
                    updatedAt = System.currentTimeMillis(),
                )
            } else conv
        }

        state = state.copy(
            conversations = updatedConvs,
            chatMessages = newMessages,
            pendingAttachments = emptyList(),
            isChatStreaming = true,
            selectedChatModelId = targetModelId,
        )
        chatStore.save(updatedConvs)

        if (!state.bridgeRunning) {
            startBridge(state.port, silent = true)
        }

        dispatchChatStream(targetModelId, assistantMsgId)
    }

    private fun dispatchChatStream(targetModelId: String, assistantMsgId: String) {
        chatJob?.cancel()
        chatJob = lifecycleScope.launch(Dispatchers.IO) {
            if (!waitForBridgeReady(state.port, 3000)) {
                withContext(Dispatchers.Main) {
                    updateAssistantMessageError(assistantMsgId, "本地服务启动超时，请重试或前往服务页手动启动")
                }
                return@launch
            }

            try {
                val contextMessages = state.chatMessages
                    .filter { !it.isError && (it.content.isNotBlank() || it.attachments.isNotEmpty() || it.toolCalls.isNotEmpty()) && it.id != assistantMsgId }
                    .takeLast(25)

                val requestObj = org.json.JSONObject().apply {
                    put("model", targetModelId)
                    put("stream", true)

                    // 1. Thinking Intensity
                    if (state.thinkingEffort != ThinkingEffort.OFF) {
                        put("reasoning_effort", state.thinkingEffort.levelName)
                        put("thinking", org.json.JSONObject().apply {
                            put("type", "enabled")
                            put("budget_tokens", state.thinkingEffort.budgetTokens)
                        })
                    }

                    // 2. MCP & Exa AI Tools
                    if (state.mcpEnabled) {
                        val tools = McpManager.getAvailableTools(state.mcpServers, state.exaApiKey)
                        if (tools.length() > 0) {
                            put("tools", tools)
                            put("tool_choice", "auto")
                        }
                    }

                    // 3. Assemble messages with multimodal vision & attachments
                    val arr = org.json.JSONArray()
                    for (msg in contextMessages) {
                        val obj = org.json.JSONObject()
                        obj.put("role", when (msg.role) {
                            ChatRole.USER -> "user"
                            ChatRole.ASSISTANT -> "assistant"
                            ChatRole.SYSTEM -> "system"
                            ChatRole.TOOL -> "tool"
                        })

                        val imageAtt = msg.attachments.firstOrNull { it.type == AttachmentType.IMAGE }
                        val fileAtts = msg.attachments.filter { it.type == AttachmentType.TEXT_FILE }

                        val promptWithFiles = StringBuilder().apply {
                            if (fileAtts.isNotEmpty()) {
                                fileAtts.forEach { f ->
                                    append("\n[附加文件: ${f.name}]\n```\n${f.textContent}\n```\n")
                                }
                            }
                            append(msg.content)
                        }.toString()

                        if (imageAtt != null && msg.role == ChatRole.USER) {
                            // OpenAI multimodal array
                            val contentArr = org.json.JSONArray().apply {
                                put(org.json.JSONObject().apply {
                                    put("type", "text")
                                    put("text", promptWithFiles)
                                })
                                put(org.json.JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", org.json.JSONObject().apply {
                                        put("url", imageAtt.base64Data)
                                    })
                                })
                            }
                            obj.put("content", contentArr)
                        } else {
                            obj.put("content", promptWithFiles)
                        }
                        arr.put(obj)
                    }
                    put("messages", arr)
                }

                val url = java.net.URL("http://127.0.0.1:${state.port}/v1/chat/completions")
                val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer ${state.secret}")
                    setRequestProperty("Accept", "text/event-stream")
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    doOutput = true
                    doInput = true
                }
                currentChatConn = conn

                conn.outputStream.use { os ->
                    os.write(requestObj.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                    os.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    val errText = conn.errorStream?.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)?.use { it.readText() }
                        ?: conn.inputStream?.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)?.use { it.readText() }
                        ?: "HTTP $responseCode"
                    val displayMsg = runCatching {
                        val errObj = org.json.JSONObject(errText).optJSONObject("error")
                        errObj?.optString("message") ?: errText
                    }.getOrDefault(errText)

                    withContext(Dispatchers.Main) {
                        updateAssistantMessageError(assistantMsgId, displayMsg)
                    }
                    return@launch
                }

                val contentBuilder = java.lang.StringBuilder()
                val reasoningBuilder = java.lang.StringBuilder()
                val toolCallBuilder = mutableListOf<ChatToolCall>()
                var lastUiUpdate = System.currentTimeMillis()

                conn.inputStream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.startsWith("data:")) {
                            val data = trimmed.substring(5).trim()
                            if (data == "[DONE]") break

                            val deltaText = parseDeltaContent(data)
                            val deltaReasoning = parseDeltaReasoning(data)
                            val discoveredTools = parseDeltaToolCalls(data)

                            if (deltaText.isNotEmpty()) contentBuilder.append(deltaText)
                            if (deltaReasoning.isNotEmpty()) reasoningBuilder.append(deltaReasoning)
                            if (discoveredTools.isNotEmpty()) toolCallBuilder.addAll(discoveredTools)

                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 45) {
                                val currentStr = contentBuilder.toString()
                                val currentReasoning = reasoningBuilder.toString()
                                withContext(Dispatchers.Main) {
                                    updateAssistantMessageState(
                                        id = assistantMsgId,
                                        content = currentStr,
                                        reasoning = currentReasoning,
                                        tools = toolCallBuilder,
                                        isStreaming = true,
                                    )
                                }
                                lastUiUpdate = now
                            }
                        }
                    }
                }

                // Check if the model triggered MCP tool calls
                if (toolCallBuilder.isNotEmpty() && state.mcpEnabled) {
                    val executedTools = mutableListOf<ChatToolCall>()
                    val toolResultsSb = StringBuilder()

                    for (tool in toolCallBuilder) {
                        withContext(Dispatchers.Main) {
                            updateAssistantMessageState(
                                id = assistantMsgId,
                                content = contentBuilder.toString(),
                                reasoning = reasoningBuilder.toString(),
                                tools = toolCallBuilder.map { if (it.name == tool.name) it.copy(isExecuting = true) else it },
                                isStreaming = true,
                            )
                        }

                        val result = McpManager.executeTool(
                            toolName = tool.name,
                            argumentsJson = tool.arguments,
                            servers = state.mcpServers,
                            exaApiKey = state.exaApiKey,
                        )

                        executedTools.add(tool.copy(result = result, isExecuting = false))
                        toolResultsSb.append("\n[工具调用结果: ${tool.name}]\n$result\n")
                    }

                    // Update assistant with tool results and launch continuation round
                    withContext(Dispatchers.Main) {
                        updateAssistantMessageState(
                            id = assistantMsgId,
                            content = contentBuilder.toString(),
                            reasoning = reasoningBuilder.toString(),
                            tools = executedTools,
                            isStreaming = false,
                        )

                        // Add tool message and trigger answer synthesis
                        val nextAssistantMsgId = java.util.UUID.randomUUID().toString()
                        val toolResponseMsg = ChatMessage(
                            role = ChatRole.TOOL,
                            content = toolResultsSb.toString(),
                            modelId = targetModelId,
                        )
                        val nextAssistantMsg = ChatMessage(
                            id = nextAssistantMsgId,
                            role = ChatRole.ASSISTANT,
                            content = "",
                            modelId = targetModelId,
                            isStreaming = true,
                        )
                        state = state.copy(
                            chatMessages = state.chatMessages + toolResponseMsg + nextAssistantMsg,
                            isChatStreaming = true,
                        )
                        dispatchChatStream(targetModelId, nextAssistantMsgId)
                    }
                    return@launch
                }

                // Finalize content without tools
                val finalContent = contentBuilder.toString()
                val finalReasoning = reasoningBuilder.toString()
                withContext(Dispatchers.Main) {
                    updateAssistantMessageState(
                        id = assistantMsgId,
                        content = finalContent,
                        reasoning = finalReasoning,
                        tools = toolCallBuilder,
                        isStreaming = false,
                    )
                    syncActiveConversationMessages(state.chatMessages)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        finishAssistantMessageOnCancel(assistantMsgId)
                        syncActiveConversationMessages(state.chatMessages)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        updateAssistantMessageError(assistantMsgId, "连接异常: ${e.message ?: "未知错误"}")
                        syncActiveConversationMessages(state.chatMessages)
                    }
                }
            } finally {
                currentChatConn?.runCatching { disconnect() }
                currentChatConn = null
                withContext(Dispatchers.Main) {
                    state = state.copy(isChatStreaming = false)
                }
            }
        }
    }

    private fun parseDeltaContent(data: String): String {
        return runCatching {
            val json = org.json.JSONObject(data)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val delta = choice.optJSONObject("delta")
                if (delta != null) {
                    val content = delta.optString("content", "")
                    if (content.isNotEmpty()) return@runCatching content
                }
                choice.optString("text", "")
            } else ""
        }.getOrDefault("")
    }

    private fun parseDeltaReasoning(data: String): String {
        return runCatching {
            val json = org.json.JSONObject(data)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val delta = choice.optJSONObject("delta")
                if (delta != null) {
                    delta.optString("reasoning_content", "")
                } else ""
            } else ""
        }.getOrDefault("")
    }

    private fun parseDeltaToolCalls(data: String): List<ChatToolCall> {
        return runCatching {
            val json = org.json.JSONObject(data)
            val choices = json.optJSONArray("choices") ?: return@runCatching emptyList()
            if (choices.length() == 0) return@runCatching emptyList()
            val choice = choices.getJSONObject(0)
            val delta = choice.optJSONObject("delta") ?: return@runCatching emptyList()
            val tools = delta.optJSONArray("tool_calls") ?: return@runCatching emptyList()

            val list = mutableListOf<ChatToolCall>()
            for (i in 0 until tools.length()) {
                val t = tools.getJSONObject(i)
                val func = t.optJSONObject("function") ?: continue
                val name = func.optString("name")
                val args = func.optString("arguments")
                if (name.isNotBlank()) {
                    list.add(ChatToolCall(id = t.optString("id", UUID.randomUUID().toString()), name = name, arguments = args))
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    private suspend fun waitForBridgeReady(port: Int, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val ok = runCatching {
                java.net.Socket("127.0.0.1", port).use { true }
            }.getOrDefault(false)
            if (ok) return true
            delay(80)
        }
        return false
    }

    private fun stopChatMessage() {
        chatJob?.cancel()
        currentChatConn?.runCatching { disconnect() }
        currentChatConn = null
        state = state.copy(
            isChatStreaming = false,
            chatMessages = state.chatMessages.map { msg ->
                if (msg.isStreaming) msg.copy(isStreaming = false) else msg
            },
        )
    }

    private fun retryChatMessage() {
        val lastMsg = state.chatMessages.lastOrNull() ?: return
        val messagesWithoutLast = if (lastMsg.role == ChatRole.ASSISTANT || lastMsg.role == ChatRole.TOOL) {
            state.chatMessages.dropLast(1)
        } else {
            state.chatMessages
        }
        val lastUserMsg = messagesWithoutLast.lastOrNull { it.role == ChatRole.USER } ?: return
        state = state.copy(chatMessages = messagesWithoutLast.dropLast(1))
        sendChatMessage(lastUserMsg.content, lastUserMsg.attachments)
    }

    private fun loadChatConversations() {
        val loaded = chatStore.load()
        if (loaded.isEmpty()) {
            val initial = ChatConversation(title = "新对话")
            chatStore.save(listOf(initial))
            state = state.copy(
                conversations = listOf(initial),
                activeConversationId = initial.id,
                chatMessages = emptyList(),
            )
        } else {
            val active = loaded.maxByOrNull { it.updatedAt } ?: loaded.first()
            state = state.copy(
                conversations = loaded,
                activeConversationId = active.id,
                chatMessages = active.messages,
                selectedChatModelId = active.modelId ?: state.selectedChatModelId,
            )
        }
    }

    private fun createConversation() {
        stopChatMessage()
        val newConv = ChatConversation(
            title = "新对话",
            modelId = state.selectedChatModelId,
        )
        val updated = listOf(newConv) + state.conversations
        state = state.copy(
            conversations = updated,
            activeConversationId = newConv.id,
            chatMessages = emptyList(),
            pendingAttachments = emptyList(),
        )
        chatStore.save(updated)
    }

    private fun switchConversation(convId: String) {
        if (state.activeConversationId == convId) return
        stopChatMessage()
        val target = state.conversations.firstOrNull { it.id == convId } ?: return
        state = state.copy(
            activeConversationId = target.id,
            chatMessages = target.messages,
            selectedChatModelId = target.modelId ?: state.selectedChatModelId,
            pendingAttachments = emptyList(),
        )
    }

    private fun renameConversation(convId: String, newTitle: String) {
        val trimmed = newTitle.trim().ifBlank { "未命名会话" }
        val updated = state.conversations.map { conv ->
            if (conv.id == convId) conv.copy(title = trimmed) else conv
        }
        state = state.copy(conversations = updated)
        chatStore.save(updated)
    }

    private fun deleteConversation(convId: String) {
        val currentActiveId = state.activeConversationId
        val remaining = state.conversations.filter { it.id != convId }
        if (remaining.isEmpty()) {
            val fresh = ChatConversation(title = "新对话", modelId = state.selectedChatModelId)
            val list = listOf(fresh)
            state = state.copy(
                conversations = list,
                activeConversationId = fresh.id,
                chatMessages = emptyList(),
                pendingAttachments = emptyList(),
            )
            chatStore.save(list)
        } else {
            val newActive = if (currentActiveId == convId) remaining.first() else remaining.firstOrNull { it.id == currentActiveId } ?: remaining.first()
            state = state.copy(
                conversations = remaining,
                activeConversationId = newActive.id,
                chatMessages = newActive.messages,
                selectedChatModelId = newActive.modelId ?: state.selectedChatModelId,
            )
            chatStore.save(remaining)
        }
    }

    private fun clearAllConversations() {
        stopChatMessage()
        val fresh = ChatConversation(title = "新对话", modelId = state.selectedChatModelId)
        val list = listOf(fresh)
        state = state.copy(
            conversations = list,
            activeConversationId = fresh.id,
            chatMessages = emptyList(),
            pendingAttachments = emptyList(),
        )
        chatStore.save(list)
    }

    private fun syncActiveConversationMessages(messages: List<ChatMessage>, newTitle: String? = null) {
        val activeId = state.activeConversationId ?: return
        val updated = state.conversations.map { conv ->
            if (conv.id == activeId) {
                conv.copy(
                    title = newTitle ?: conv.title,
                    messages = messages,
                    updatedAt = System.currentTimeMillis(),
                )
            } else conv
        }
        state = state.copy(
            conversations = updated,
            chatMessages = messages,
        )
        chatStore.save(updated)
    }

    private fun clearChatMessages() {
        stopChatMessage()
        val activeId = state.activeConversationId
        val updated = state.conversations.map { conv ->
            if (conv.id == activeId) conv.copy(messages = emptyList(), updatedAt = System.currentTimeMillis()) else conv
        }
        state = state.copy(conversations = updated, chatMessages = emptyList(), pendingAttachments = emptyList())
        chatStore.save(updated)
    }

    private fun updateAssistantMessageState(
        id: String,
        content: String,
        reasoning: String,
        tools: List<ChatToolCall>,
        isStreaming: Boolean,
    ) {
        state = state.copy(
            chatMessages = state.chatMessages.map { msg ->
                if (msg.id == id) {
                    msg.copy(
                        content = content,
                        reasoningContent = reasoning,
                        toolCalls = tools,
                        isStreaming = isStreaming,
                    )
                } else msg
            },
            isChatStreaming = isStreaming,
        )
    }

    private fun updateAssistantMessageError(id: String, errorText: String) {
        state = state.copy(
            chatMessages = state.chatMessages.map { msg ->
                if (msg.id == id) msg.copy(content = errorText, isError = true, isStreaming = false) else msg
            },
            isChatStreaming = false,
        )
    }

    private fun finishAssistantMessageOnCancel(id: String) {
        state = state.copy(
            chatMessages = state.chatMessages.map { msg ->
                if (msg.id == id) msg.copy(isStreaming = false) else msg
            },
            isChatStreaming = false,
        )
    }

    companion object {
        /** Keeps a loading indicator visible long enough to be noticed. */
        private const val MIN_LOADING_MS = 600L
        private const val POLL_INTERVAL_MS = 3000L
        private const val LOGIN_TIMEOUT_MS = 9 * 60 * 1000L
    }
}
