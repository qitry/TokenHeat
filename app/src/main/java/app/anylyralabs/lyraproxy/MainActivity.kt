package app.anylyralabs.lyraproxy

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
import app.anylyralabs.lyraproxy.bridge.ApiKeyStore
import app.anylyralabs.lyraproxy.bridge.BridgeService
import app.anylyralabs.lyraproxy.bridge.CheckinStore
import app.anylyralabs.lyraproxy.bridge.BridgeSettings
import app.anylyralabs.lyraproxy.bridge.CallLogStore
import app.anylyralabs.lyraproxy.bridge.Notifications
import app.anylyralabs.lyraproxy.data.AccountBackup
import app.anylyralabs.lyraproxy.data.CheckinItem
import app.anylyralabs.lyraproxy.data.Login
import app.anylyralabs.lyraproxy.proto.CheckinOutcome
import app.anylyralabs.lyraproxy.proto.Credential
import app.anylyralabs.lyraproxy.proto.CredentialStore
import app.anylyralabs.lyraproxy.proto.UpstreamClient
import app.anylyralabs.lyraproxy.proto.Wire
import app.anylyralabs.lyraproxy.proto.toHubModel
import app.anylyralabs.lyraproxy.qoder.QoderAccount
import app.anylyralabs.lyraproxy.qoder.QoderAccountStore
import app.anylyralabs.lyraproxy.qoder.QoderClient
import app.anylyralabs.lyraproxy.qoder.QoderConfig
import app.anylyralabs.lyraproxy.qoder.QoderPoll
import app.anylyralabs.lyraproxy.qoder.asHubModel
import app.anylyralabs.lyraproxy.ui.CheckinDialog
import app.anylyralabs.lyraproxy.ui.HubApp
import app.anylyralabs.lyraproxy.ui.Loading
import app.anylyralabs.lyraproxy.ui.LogoutDialog
import app.anylyralabs.lyraproxy.ui.HubState
import app.anylyralabs.lyraproxy.ui.HubTab
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

class MainActivity : ComponentActivity() {

    private lateinit var store: CredentialStore
    private lateinit var callLog: CallLogStore
    private lateinit var apiKeyStore: ApiKeyStore
    private lateinit var checkinStore: CheckinStore
    private lateinit var qoderStore: QoderAccountStore

    private val qoderClient = QoderClient()
    private var qoderPollJob: Job? = null

    /** 上次读取日志时的文件指纹，用于跳过无变化的重读。 */
    private var lastCallStamp: String = ""
    private var service: BridgeService? = null
    private var bound by mutableStateOf(false)
    private var pollJob: Job? = null

    private var state by mutableStateOf(HubState())
    private var showHelp by mutableStateOf(false)
    private var askedForNotifications = false
    private val upstream = UpstreamClient()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CredentialStore(this)
        callLog = CallLogStore(this)
        apiKeyStore = ApiKeyStore(this)
        checkinStore = CheckinStore(this)
        qoderStore = QoderAccountStore(this)
        Notifications.ensureChannel(this)
        refreshCredential()
        // Keep the daemon's credential copy current, including the first run
        // after an account switch happened while it was not running.
        store.mirrorActiveForDaemon()
        loadBalance()
        loadModels()
        loadCalls()
        loadApiKeys()
        // Qoder 的账号与模型是独立缓存，启动时直接读本地，失败也不影响主流程。
        state = state.copy(
            qoderAccount = qoderStore.load(),
            qoderModels = qoderStore.loadModels().map { it.asHubModel() },
        )
        state = state.copy(
            notificationsAllowed = Notifications.hasPermission(this),
            overlayOpacity = BridgeSettings.opacity(this),
            overlayLocked = BridgeSettings.locked(this),
            overlayEnabled = BridgeSettings.overlayEnabled(this),
            overlayMovable = BridgeSettings.overlayMovable(this),
            overlayPassthrough = BridgeSettings.overlayPassthrough(this),
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
                // 轮询会用这个回调；内部按文件指纹跳过无变化的重读。
                onRefreshCalls = { refreshCallsIfChanged() },
                onOverlayOpacity = { value ->
                    BridgeSettings.setOpacity(this, value)
                    // Applied to the live panel, so the slider is visible as it moves.
                    BridgeService.overlayRef?.panelOpacity = value
                    state = state.copy(overlayOpacity = value)
                },
                onOverlayLocked = { locked ->
                    BridgeSettings.setLocked(this, locked)
                    BridgeService.overlayRef?.apply { this.locked = locked }
                    state = state.copy(overlayLocked = locked)
                },                onOverlayEnabled = { enabled ->
                    BridgeSettings.setOverlayEnabled(this, enabled)
                    state = state.copy(overlayEnabled = enabled)
                    // 服务已在运行就只同步面板；未运行才需要把服务拉起来。
                    val running = service
                    if (running != null) {
                        running.refreshOverlay()
                    } else if (enabled) {
                        startBridge(state.port, silent = true)
                    }
                },
                onOverlayMovable = { movable ->
                    BridgeSettings.setOverlayMovable(this, movable)
                    BridgeService.overlayRef?.apply { this.movable = movable }
                    state = state.copy(overlayMovable = movable)
                },
                onOverlayPassthrough = { passthrough ->
                    BridgeSettings.setOverlayPassthrough(this, passthrough)
                    BridgeService.overlayRef?.apply { this.passthrough = passthrough }
                    state = state.copy(overlayPassthrough = passthrough)
                },
                onCopyApiKey = { key -> copyToClipboard(key, "已复制 API Key") },
                onToggleApiKeyEnabled = { apiKey, enabled ->
                    apiKeyStore.update(apiKey.copy(enabled = enabled))
                    loadApiKeys()
                },
                onEditApiKey = { apiKey -> state = state.copy(editingApiKey = apiKey) },
                onCreateApiKey = { state = state.copy(creatingApiKey = true, editingApiKey = null) },
                onDeleteApiKey = { apiKey -> state = state.copy(deletingApiKey = apiKey) },
                onClearApiKeyUsage = { apiKey ->
                    apiKeyStore.clearUsage(apiKey.id)
                    loadApiKeys()
                    toast("已重置用量")
                },
                onSaveApiKey = { label, allowedModels, budget, enabled ->
                    val editing = state.editingApiKey
                    if (editing == null) {
                        val created = apiKeyStore.create(
                            label = label,
                            allowedModels = allowedModels,
                            budget = budget,
                        )
                        state = state.copy(
                            creatingApiKey = false,
                            justCreatedApiKey = created,
                        )
                    } else {
                        apiKeyStore.update(
                            editing.copy(
                                label = label.ifBlank { editing.label },
                                allowedModels = allowedModels.distinct(),
                                budget = budget,
                                enabled = enabled,
                            ),
                        )
                        state = state.copy(editingApiKey = null)
                    }
                    loadApiKeys()
                },
                onDismissApiKeyEditor = {
                    state = state.copy(creatingApiKey = false, editingApiKey = null)
                },
                onDismissApiKeyCreated = { state = state.copy(justCreatedApiKey = null) },
                onConfirmDeleteApiKey = { apiKey ->
                    apiKeyStore.delete(apiKey.id)
                    state = state.copy(deletingApiKey = null)
                    loadApiKeys()
                    toast("已删除密钥")
                },
                onDismissDeleteApiKey = { state = state.copy(deletingApiKey = null) },
                onExportBackup = { exportBackup() },
                onImportBackup = { pickBackupFile() },
                onImportFromText = { state = state.copy(showPasteImport = true) },
                onDismissPasteImport = { state = state.copy(showPasteImport = false) },
                onConfirmPasteImport = { text -> importFromText(text) },
                onQoderLogin = { startQoderLogin() },
                onQoderPatLogin = { state = state.copy(qoderShowPatDialog = true) },
                onQoderPatConfirm = { pat ->
                    state = state.copy(qoderShowPatDialog = false)
                    loginQoderWithPat(pat)
                },
                onQoderTest = { testQoder() },
                onQoderRefreshModels = { refreshQoderModels() },
                onQoderLogout = { logoutQoder() },
                onSwitchProvider = { provider -> state = state.copy(provider = provider) },
                onDismissQoderPat = { state = state.copy(qoderShowPatDialog = false) },
                onDismissQoderProbe = { state = state.copy(qoderProbe = null) },
                onDismissQoderLogin = { state = state.copy(qoderLoginUrl = "") },
                onCopyQoderUrl = { copyToClipboard(state.qoderLoginUrl, "已复制授权链接") },
                onSwitchRealm = { region -> switchRealm(region) },
                onSwitchAccount = { id -> switchAccount(id) },
                onDeleteAccount = { id -> deleteAccount(id) },
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
                onTabShown = { tab ->
                    // Reload on entry: the service writes records without the
                    // UI knowing, so a snapshot taken at startup goes stale.
                    when (tab) {
                        HubTab.Dashboard -> {
                            loadCalls()
                            loadBalance()
                        }
                        HubTab.Calls -> loadCalls()
                        else -> Unit
                    }
                },
            )
        }

        requestNotificationPermissionIfNeeded()
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
        if (bound) runCatching { unbindService(connection) }
        pollJob?.cancel()
        super.onDestroy()
    }

    /** Re-reads the account in use and every build's saved accounts. */
    private fun refreshCredential() {
        val region = store.activeRegion()
        val account = store.activeAccount()
        state = state.copy(
            realm = region,
            credential = account?.toCredential(),
            expiryText = account?.let { formatExpiry(it.toCredential()) } ?: "",
            accounts = Wire.Region.entries.associateWith { store.accounts(it) },
            activeAccountId = account?.id,
            checkedInToday = account?.let { checkinStore.isCheckedToday(it.id) } ?: false,
        )
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

    private fun loadModels() {
        val cred = state.credential ?: return
        lifecycleScope.launch {
            val models = withContext(Dispatchers.IO) {
                withLoading(Loading.MODELS) {
                    runCatching { upstream.fetchModels(cred).map { it.toHubModel() } }.getOrDefault(emptyList())
                }
            }
            state = state.copy(models = models)
        }
    }

    private fun startLogin(region: Wire.Region) {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                runCatching { Login.start(region.toLoginRealm()) }.getOrNull()
            }
            if (session == null) {
                toast("获取登录链接失败")
                return@launch
            }
            state = state.copy(status = "请在浏览器完成登录：${session.authUrl}")
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(session.authUrl)))
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
                        // The slot is the version the user signed in to: it is
                        // the only reliable statement of which account system
                        // this credential belongs to, and it is what the
                        // switcher displays.
                        val target = realm.toRegion()
                        store.save(target, result.credential)
                        store.setActiveRegion(target)
                        // Re-read from disk so the expiry goes through the same
                        // normalisation as a cold start: the login response can
                        // omit expiresAt, leaving the JWT claim as the only source.
                        refreshCredential()
                        loadModels()
                        loadBalance()
                        Notifications.showLoginSuccess(this@MainActivity, result.credential.nickname)
                        toast("凭证已保存")
                        return@launch
                    }
                    is Login.Poll.Failed -> {
                        state = state.copy(status = "登录失败：${result.message}")
                        return@launch
                    }
                    else -> delay(POLL_INTERVAL_MS)
                }
            }
            state = state.copy(status = "登录超时，请重试")
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
        if (region == Wire.Region.GLOBAL) {
            state = state.copy(checkinMessage = "国际版不支持签到，仅可查看积分余额")
            return
        }
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
                        if (outcome.ok) checkinStore.markChecked(account.id)
                        CheckinItem(label = account.label, ok = outcome.ok, message = outcome.message)
                    }
                }
            }
            val activeChecked = state.activeAccountId?.let { checkinStore.isCheckedToday(it) } ?: false
            state = state.copy(
                checkinItems = results,
                checkinMessage = "已为 ${results.count { it.ok }} / ${results.size} 个账号完成签到",
                checkedInToday = activeChecked,
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
        // 国际版上游没有签到接口，直接拦下，避免发出必然失败的请求。
        if (cred.region == Wire.Region.GLOBAL) {
            state = state.copy(checkinMessage = "国际版不支持签到，仅可查看积分余额")
            return
        }
        // 界面在已签到时隐藏了入口，这里再拦一次，防止外部再次触发。
        if (state.checkedInToday) {
            state = state.copy(checkinMessage = "今天已经签到过了")
            return
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                withLoading(Loading.CHECKIN) {
                    runCatching { upstream.checkin(cred) }
                        .getOrElse { CheckinOutcome(false, it.message ?: "签到失败") }
                }
            }
            // 只有成功才记「今天已签到」；失败写进去会让按钮永远显示已签到。
            if (result.ok) {
                state.activeAccountId?.let { checkinStore.markChecked(it) }
            }
            state = state.copy(
                checkinMessage = result.message,
                checkedInToday = result.ok || state.checkedInToday,
            )
            loadBalance()
        }
    }

    private fun loadBalance() {
        val cred = state.credential
        if (cred == null) {
            state = state.copy(status = "余额：未登录")
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
        manager.setPrimaryClip(ClipData.newPlainText("wb", text))
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
        lastCallStamp = callLog.stamp()
        state = state.copy(calls = callLog.load())
    }

    /**
     * 高频轮询用的轻量刷新：只有日志文件真的变了才重新解析。
     *
     * 调用记录是追加写入的 NDJSON，文件可能上万行；每两秒全量解析一次
     * 在低端机上足以造成可感知的卡顿。用文件长度 + 修改时间做判据，
     * 没有新记录时不读盘。
     */
    private fun refreshCallsIfChanged() {
        val stamp = callLog.stamp()
        if (stamp == lastCallStamp) return
        lastCallStamp = stamp
        loadCalls()
    }

    /** 重新读取自定义密钥列表。 */
    private fun loadApiKeys() {
        state = state.copy(apiKeys = apiKeyStore.load())
    }

    // ------------------------------------------------------------------ //
    // 账号备份：导出 / 文件导入 / 粘贴导入
    // ------------------------------------------------------------------ //

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        AccountBackup.importFromGzipStream(input, store)
                    } ?: AccountBackup.ImportResult(success = false, message = "无法读取所选文件")
                }.getOrElse {
                    AccountBackup.ImportResult(success = false, message = "读取失败：${it.message}")
                }
            }
            onImportFinished(result)
        }
    }

    private fun exportBackup() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { AccountBackup.exportToFileAndGetIntent(this@MainActivity, store) }
            }
            result.onSuccess { (file, intent) ->
                runCatching { startActivity(Intent.createChooser(intent, "分享账号备份")) }
                    .onFailure { toast("已保存到 ${file.absolutePath}") }
            }.onFailure { toast("导出失败：${it.message?.take(40)}") }
        }
    }

    private fun pickBackupFile() {
        runCatching {
            importFileLauncher.launch(arrayOf("application/gzip", "application/json", "text/plain", "*/*"))
        }.onFailure { toast("无法打开文件选择器") }
    }

    private fun importFromText(text: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                AccountBackup.importFromJsonText(text, store)
            }
            state = state.copy(showPasteImport = false)
            onImportFinished(result)
        }
    }

    /** 导入结束后统一刷新账号状态并回报结果。 */
    private fun onImportFinished(result: AccountBackup.ImportResult) {
        if (result.success) {
            refreshCredential()
            loadModels()
            loadBalance()
            val detail = result.breakdown.entries.joinToString("，") { "${it.key} ${it.value} 个" }
            toast(if (detail.isBlank()) result.message else "${result.message}（$detail）")
        } else {
            toast(result.message.ifBlank { "导入失败" })
        }
    }

    // ------------------------------------------------------------------ //
    // Qoder 国际版
    // ------------------------------------------------------------------ //

    /** 走设备码：打开浏览器，同时轮询授权结果。 */
    private fun startQoderLogin() {
        if (state.qoderBusy) return
        val flow = runCatching { qoderClient.startDeviceFlow(qoderStore.machineId()) }.getOrElse {
            state = state.copy(qoderStatus = "无法创建授权链接：${it.message?.take(60)}")
            return
        }
        state = state.copy(
            qoderBusy = true,
            qoderLoginUrl = flow.authUrl,
            qoderStatus = "等待浏览器授权…",
        )
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(flow.authUrl)))
        }.onFailure {
            state = state.copy(qoderStatus = "无法打开浏览器，请复制授权链接手动打开")
        }

        qoderPollJob?.cancel()
        qoderPollJob = loginScope.launch {
            val deadline = System.currentTimeMillis() + QoderConfig.LOGIN_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(QoderConfig.POLL_INTERVAL_MS)
                when (val result = withContext(Dispatchers.IO) { qoderClient.poll(flow) }) {
                    QoderPoll.Pending -> Unit
                    is QoderPoll.Done -> {
                        onQoderLoggedIn(result.account)
                        return@launch
                    }
                    is QoderPoll.Failed -> {
                        state = state.copy(
                            qoderBusy = false,
                            qoderLoginUrl = "",
                            qoderStatus = result.message,
                        )
                        return@launch
                    }
                }
            }
            state = state.copy(
                qoderBusy = false,
                qoderLoginUrl = "",
                qoderStatus = "授权超时，请重试",
            )
        }
    }

    private fun loginQoderWithPat(pat: String) {
        if (pat.isBlank()) return
        state = state.copy(qoderBusy = true, qoderStatus = "正在验证令牌…")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                qoderClient.loginWithPat(pat, qoderStore.machineId())
            }
            result.onSuccess { onQoderLoggedIn(it) }
                .onFailure {
                    state = state.copy(
                        qoderBusy = false,
                        qoderStatus = it.message?.take(120) ?: "登录失败",
                    )
                }
        }
    }

    private fun onQoderLoggedIn(account: QoderAccount) {
        qoderStore.save(account)
        state = state.copy(
            qoderAccount = account,
            qoderLoginUrl = "",
            qoderBusy = false,
            qoderStatus = "已登录 ${account.name.ifBlank { account.email }}",
        )
        refreshQoderModels()
    }

    private fun refreshQoderModels() {
        val account = state.qoderAccount
        if (account == null) {
            state = state.copy(qoderStatus = "请先登录 Qoder")
            return
        }
        state = state.copy(qoderBusy = true, qoderStatus = "正在读取模型…")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { qoderClient.fetchModels(account) }
            }
            result.onSuccess { models ->
                qoderStore.saveModels(models)
                state = state.copy(
                    qoderModels = models.map { it.asHubModel() },
                    qoderBusy = false,
                    qoderStatus = "模型目录已更新，共 ${models.size} 个",
                )
            }.onFailure {
                state = state.copy(
                    qoderBusy = false,
                    qoderStatus = it.message?.take(120) ?: "读取模型失败",
                )
            }
        }
    }

    /** 只探测、不改状态；结果原样展示，便于确认认证方案是否被接受。 */
    private fun testQoder() {
        val account = state.qoderAccount
        if (account == null) {
            state = state.copy(qoderStatus = "请先登录 Qoder")
            return
        }
        state = state.copy(qoderBusy = true, qoderStatus = "正在测试连接…")
        lifecycleScope.launch {
            val probe = withContext(Dispatchers.IO) { qoderClient.probe(account) }
            state = state.copy(
                qoderBusy = false,
                qoderProbe = probe,
                qoderStatus = probe.summary,
            )
        }
    }

    private fun logoutQoder() {
        qoderPollJob?.cancel()
        qoderStore.clear()
        state = state.copy(
            qoderAccount = null,
            qoderModels = emptyList(),
            qoderBusy = false,
            qoderLoginUrl = "",
            qoderStatus = "已退出 Qoder",
        )
        toast("已退出 Qoder")
    }

    /** Discards the whole call history. */
    private fun clearCalls() {
        callLog.clear()
        loadCalls()
        toast("调用记录已清空")
    }

    /** Signs the user out everywhere, discarding both stored credentials. */
    private fun logout() {
        Wire.Region.entries.forEach { store.clear(it) }
        // 账号都清掉了，签到记录也一并清除，避免下次登录复用旧状态。
        checkinStore.clearAll()
        refreshCredential()
        state = state.copy(models = emptyList(), balance = null, status = "已退出登录")
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

    companion object {
        /** Keeps a loading indicator visible long enough to be noticed. */
        private const val MIN_LOADING_MS = 600L
        private const val POLL_INTERVAL_MS = 3000L
        private const val LOGIN_TIMEOUT_MS = 9 * 60 * 1000L
    }
}
