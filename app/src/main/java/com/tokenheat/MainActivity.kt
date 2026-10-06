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
import com.tokenheat.ui.LogoutDialog
import com.tokenheat.ui.HubState
import com.tokenheat.ui.HubTab
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
    private var service: BridgeService? = null
    private var bound by mutableStateOf(false)
    private var pollJob: Job? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CredentialStore(this)
        callLog = CallLogStore(this)
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
                onTabShown = { tab ->
                    // Reload on entry: the service writes records without the
                    // UI knowing, so a snapshot taken at startup goes stale.
                    when (tab) {
                        HubTab.Calls -> loadCalls()
                        HubTab.Rewards -> loadBalance()
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
            credential = account?.toCredential(),
            expiryText = account?.let { formatExpiry(it.toCredential()) } ?: "",
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
            state = state.copy(status = "请在浏览器完成登录：${session.authUrl}")
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(session.authUrl)))
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
                        state = state.copy(status = "登录成功，正在读取账号信息…")
                        val credential = withContext(Dispatchers.IO) {
                            runCatching {
                                val (uid, name) = QLogin.userinfo(session.region, result.auth.token)
                                QLogin.toCredential(session.region, result.auth, uid, name)
                            }.getOrNull()
                        }
                        if (credential == null) {
                            state = state.copy(status = "读取账号信息失败，请重试")
                            return@launch
                        }
                        store.saveSlot(session.region.provider, credential)
                        store.setActiveProvider(session.region.provider)
                        refreshCredential()
                        loadModels()
                        loadBalance()
                        Notifications.showLoginSuccess(this@MainActivity, credential.nickname)
                        toast("凭证已保存")
                        return@launch
                    }
                    is QLogin.Poll.Failed -> {
                        state = state.copy(status = "登录失败：${result.message}")
                        return@launch
                    }
                    else -> {
                        val remain = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                        state = state.copy(
                            status = "请在浏览器完成登录并确认授权（等待中，还剩约${remain / 60}分${remain % 60}秒）：" +
                                session.authUrl,
                        )
                        delay(POLL_INTERVAL_MS)
                    }
                }
            }
            state = state.copy(status = "登录超时，请重试")
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
                state = state.copy(status = "无法在本机监听回调端口，请重试")
                return@launch
            }
            val request = AGLogin.authRequest()
            state = state.copy(status = "请在浏览器完成 Google 登录：${request.authUrl}")
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(request.authUrl)))
            val callback = withContext(Dispatchers.IO) { server.awaitCode(LOGIN_TIMEOUT_MS) }
            server.close()
            if (callback == null) {
                state = state.copy(status = "登录超时，请重试")
                return@launch
            }
            state = state.copy(status = "登录成功，正在兑换 Token…")
            val credential = withContext(Dispatchers.IO) {
                runCatching {
                    val tokens = AGLogin.exchange(callback.first, request.verifier)
                    val projectId = AGLogin.fetchProjectId(tokens.accessToken)
                    AGLogin.toCredential(tokens, projectId)
                }.getOrNull()
            }
            if (credential == null) {
                state = state.copy(status = "兑换 Token 失败，请重试")
                return@launch
            }
            store.saveSlot(Provider.ANTIGRAVITY, credential)
            store.setActiveProvider(Provider.ANTIGRAVITY)
            refreshCredential()
            loadModels()
            loadBalance()
            Notifications.showLoginSuccess(this@MainActivity, credential.nickname)
            toast("凭证已保存")
        }
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

    private fun startZLogin() {
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) {
                runCatching { ZLogin.start() }.getOrNull()
            }
            if (session == null) {
                toast("获取登录链接失败")
                return@launch
            }
            state = state.copy(status = "请在浏览器完成登录：${session.authUrl}")
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(session.authUrl)))
            pollZcodeForToken(session)
        }
    }

    /**
     * Polls the ZCode OAuth flow, then exchanges the token for an API key.
     * The exchange walks several gateway calls (business login, org/project,
     * key ensure, secret copy), so it runs off the main thread with its own
     * status line instead of reusing the poll loop's.
     */
    private fun pollZcodeForToken(session: ZLogin.Session) {
        pollJob?.cancel()
        pollJob = loginScope.launch {
            val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val result = withContext(Dispatchers.IO) { runCatching { ZLogin.poll(session) }.getOrNull() }
                when (result) {
                    is ZLogin.Poll.Done -> {
                        state = state.copy(status = "登录成功，正在兑换 API Key…")
                        val credential = withContext(Dispatchers.IO) {
                            runCatching { ZLogin.exchange(result.oauthToken) }.getOrNull()
                        }
                        if (credential == null) {
                            state = state.copy(status = "兑换 API Key 失败，请重试")
                            return@launch
                        }
                        store.saveZcode(credential)
                        store.setActiveProvider(Provider.ZCODE)
                        refreshCredential()
                        loadModels()
                        loadBalance()
                        Notifications.showLoginSuccess(this@MainActivity, credential.nickname)
                        toast("凭证已保存")
                        return@launch
                    }
                    is ZLogin.Poll.Failed -> {
                        state = state.copy(status = "登录失败：${result.message}")
                        return@launch
                    }
                    else -> {
                        // Visible heartbeat: the user must approve the grant in
                        // the browser (a bare login is not enough), so the
                        // remaining time shows the wait is alive, not stuck.
                        val remain = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                        state = state.copy(
                            status = "请在浏览器完成登录并确认授权（等待中，还剩约${remain / 60}分${remain % 60}秒）：" +
                                session.authUrl,
                        )
                        delay(POLL_INTERVAL_MS)
                    }
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

    companion object {
        /** Keeps a loading indicator visible long enough to be noticed. */
        private const val MIN_LOADING_MS = 600L
        private const val POLL_INTERVAL_MS = 3000L
        private const val LOGIN_TIMEOUT_MS = 9 * 60 * 1000L
    }
}
