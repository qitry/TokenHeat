package com.tokenheat.bridge

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import com.tokenheat.proto.CredentialStore
import com.tokenheat.proto.Provider
import com.tokenheat.proto.UpstreamClient
import com.tokenheat.proto.ZUpstreamClient
import com.tokenheat.proto.QUpstreamClient
import com.tokenheat.proto.AGUpstreamClient
import com.tokenheat.proto.ZenUpstreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hosts the loopback bridge in the foreground so the system keeps serving it.
 *
 * The foreground promotion is not optional: without it the process is a plain
 * background service and aggressive OEM policies freeze it within minutes,
 * which shows up as requests that never answer. Promotion failure is therefore
 * recorded in [foreground] rather than swallowed, so the UI can say the bridge
 * is running but not protected.
 */
class BridgeService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): BridgeService = this@BridgeService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val upstream = UpstreamClient()
    private val zupstream = ZUpstreamClient()
    private val zenUpstream = ZenUpstreamClient()
    private val qoderUpstream = QUpstreamClient()
    private val agUpstream = AGUpstreamClient()

    /**
     * One rotation pool per provider. Candidates follow the provider's slot
     * (WorkBuddy additionally follows the active build); marks are keyed by
     * account id, so slots never confuse each other.
     */
    private val pools: Map<Provider, AccountPool> = Provider.entries.associateWith { provider ->
        AccountPool(
            candidates = {
                when (provider) {
                    Provider.WORKBUDDY -> store.accounts(store.activeRegion())
                    else -> store.slotAccounts(provider)
                }
            },
            refresher = { account ->
                when (account.provider) {
                    Provider.WORKBUDDY -> store.resolveAccount(account) { c -> upstream.refreshToken(c) }
                    Provider.QODER_CN, Provider.QODER_GLOBAL ->
                        store.resolveAccount(account) { c -> qoderUpstream.refreshToken(c) }
                    Provider.ANTIGRAVITY ->
                        store.resolveAccount(account) { c -> agUpstream.refreshToken(c) }
                    else -> account.toCredential()
                }
            },
        )
    }

    /** Latest known model ids per provider; failures keep the last good set. */
    private var modelMap: Map<Provider, List<String>> = emptyMap()
    private lateinit var store: CredentialStore
    private var bridge: BridgeServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlay: BridgeOverlay? = null
    private lateinit var callLog: CallLogStore

    /** Loopback port the bridge serves. */
    var port: Int = DEFAULT_PORT
        private set

    /** Whether the process is protected by a foreground notification. */
    @Volatile
    var foreground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        store = CredentialStore(this)
        callLog = CallLogStore(this)
        // Same one-time repair as the activity: the service reads the files too.
        store.repairMisplacedApiKeys()
        Notifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        val secret = intent?.getStringExtra(EXTRA_SECRET) ?: DEFAULT_SECRET
        promoteToForeground()
        acquireWakeLock()
        BridgeStatus.reset(port)
        showOverlay()

        if (bridge == null) {
            bridge = BridgeServer(
                port = port,
                secret = secret,
                // Qoder, WorkBuddy and Antigravity refresh through their own
                // endpoints; ZCode and Zen keys are used as-is.
                credential = {
                    when (store.activeProvider()) {
                        Provider.ZCODE -> store.activeZcode()
                        Provider.ZEN -> store.activeZen()
                        Provider.QODER_CN, Provider.QODER_GLOBAL ->
                            store.activeSlotAccount(store.activeProvider())
                                ?.let { store.resolveAccount(it) { c -> qoderUpstream.refreshToken(c) } }
                        Provider.ANTIGRAVITY ->
                            store.activeSlotAccount(store.activeProvider())
                                ?.let { store.resolveAccount(it) { c -> agUpstream.refreshToken(c) } }
                        Provider.WORKBUDDY -> store.resolve { c -> upstream.refreshToken(c) }
                    }
                },
                models = {
                    val active = store.activeProvider()
                    modelMap.flatMap { (provider, ids) ->
                        ids.map { BridgeModel("${provider.routeKey}/$it", provider.routeKey) }
                    } + modelMap[active].orEmpty().map { BridgeModel(it, active.routeKey) }
                },
                pools = { pools },
                defaultProvider = { store.activeProvider() },
                onCall = { record -> callLog.append(record) },
            ).also { it.start() }
            scope.launch { refreshModelsLoop() }
        }
        return START_STICKY
    }

    /**
     * Requests the foreground state. From a background caller Android 12+
     * refuses, so the outcome is captured instead of thrown; the UI reports it.
     */
    private fun promoteToForeground() {
        foreground = runCatching {
            startForeground(Notifications.bridgeId(), Notifications.buildBridge(this, port))
            true
        }.onFailure {
            android.util.Log.e("TokenHeat", "startForeground refused; bridge is unprotected", it)
        }.getOrDefault(false)
    }

    /**
     * A partial wake lock keeps the CPU servicing the socket while the screen is
     * off. Held only while the bridge exists, so it is released in onDestroy.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = runCatching {
            (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TokenHeat:bridge")
                .apply { setReferenceCounted(false); acquire() }
        }.onFailure { android.util.Log.w("TokenHeat", "wake lock unavailable", it) }.getOrNull()
    }

    /**
     * Shows the status panel when the user granted overlay permission.
     *
     * The panel is what keeps the process at visible importance, so the system
     * does not freeze it while it serves requests. Without the permission the
     * bridge still runs; it is simply subject to the usual background policy.
     */
    private fun showOverlay() {
        if (overlay?.isShowing() == true) return
        if (!android.provider.Settings.canDrawOverlays(this)) {
            android.util.Log.i("TokenHeat", "overlay permission not granted; bridge runs without the panel")
            return
        }
        overlay = BridgeOverlay(
            context = this,
            onClose = { removeOverlay() },
            panelOpacity = BridgeSettings.opacity(this),
            locked = BridgeSettings.locked(this),
        ).also { it.show() }
        overlayRef = overlay
    }

    /** Hides the panel; the service itself keeps running. */
    fun removeOverlay() {
        overlay?.hide()
        overlay = null
        overlayRef = null
    }

    private suspend fun refreshModelsLoop() {
        while (true) {
            Provider.entries.forEach { provider ->
                val fetched = when (provider) {
                    Provider.ZCODE -> store.activeSlotAccount(provider)?.toCredential()?.let { cred ->
                        runCatching { zupstream.fetchModels(cred).map { it.id } }.getOrNull()
                    }
                    Provider.ZEN -> runCatching { zenUpstream.fetchModels().map { it.id } }.getOrNull()
                    Provider.WORKBUDDY -> store.active()?.let { cred ->
                        runCatching { upstream.fetchModels(cred).map { it.id } }.getOrNull()
                    }
                    Provider.QODER_CN, Provider.QODER_GLOBAL ->
                        store.activeSlotAccount(provider)?.toCredential()?.let { cred ->
                            runCatching { qoderUpstream.fetchModels(cred) }.getOrNull()
                        }
                    Provider.ANTIGRAVITY ->
                        store.activeSlotAccount(provider)?.toCredential()?.let { cred ->
                            runCatching { agUpstream.fetchModels(cred) }.getOrNull()
                        }
                }
                if (!fetched.isNullOrEmpty()) modelMap = modelMap + (provider to fetched)
            }
            delay(MODEL_REFRESH_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        removeOverlay()
        BridgeStatus.stopped()
        bridge?.stop()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        /**
         * The live panel, so the settings screen can retune it without waiting
         * for the service to be recreated. Cleared when the panel is closed.
         */
        @Volatile
        var overlayRef: BridgeOverlay? = null
            private set

        const val DEFAULT_PORT = 8765
        const val DEFAULT_SECRET = "tokenheat-local"
        const val EXTRA_PORT = "port"
        const val EXTRA_SECRET = "secret"
        private const val MODEL_REFRESH_MS = 5 * 60 * 1000L
    }
}
