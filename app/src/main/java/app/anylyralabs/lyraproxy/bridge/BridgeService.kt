package app.anylyralabs.lyraproxy.bridge

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import app.anylyralabs.lyraproxy.proto.CredentialStore
import app.anylyralabs.lyraproxy.qoder.QoderAccountStore
import app.anylyralabs.lyraproxy.proto.UpstreamClient
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
    private var models: List<String> = emptyList()
    private val upstream = UpstreamClient()
    private lateinit var store: CredentialStore
    private var bridge: BridgeServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlay: BridgeOverlay? = null
    private lateinit var callLog: CallLogStore
    private lateinit var apiKeys: ApiKeyStore
    private lateinit var qoderStore: QoderAccountStore

    /** Loopback port the bridge serves. */
    var port: Int = DEFAULT_PORT
        private set

    /** 当前共享密钥；随启动 Intent 更新，供鉴权回调比较。 */
    @Volatile
    private var sharedSecret: String = DEFAULT_SECRET

    /** Whether the process is protected by a foreground notification. */
    @Volatile
    var foreground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        store = CredentialStore(this)
        callLog = CallLogStore(this)
        apiKeys = ApiKeyStore(this)
        qoderStore = QoderAccountStore(this)
        Notifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        val secret = intent?.getStringExtra(EXTRA_SECRET) ?: DEFAULT_SECRET
        sharedSecret = secret
        promoteToForeground()
        acquireWakeLock()
        BridgeStatus.reset(port)
        showOverlay()

        if (bridge == null) {
            bridge = BridgeServer(
                port = port,
                secret = secret,
                credential = { store.resolve { c -> upstream.refreshToken(c) } },
                models = { models },
                onCall = { record -> callLog.append(record) },
                authenticate = { presented -> authenticateKey(presented) },
                onKeyUsage = { keyId, credits -> apiKeys.recordUsage(keyId, credits) },
                useQoder = { BridgeSettings.gatewayProvider(this) == "qoder" },
                qoderAccount = { qoderStore.active() },
                qoderModels = { qoderStore.loadModels() },
            ).also { it.start() }
            scope.launch { refreshModelsLoop() }
        }
        return START_STICKY
    }

    /**
     * 解析请求携带的密钥。
     *
     * 共享密钥始终有效（应用内复制端点用的是它），自定义密钥则按其开关、
     * 模型白名单与预算判定。返回 null 即拒绝。
     */
    private fun authenticateKey(presented: String): BridgeServer.AuthContext? {
        if (presented.isEmpty()) return null
        if (presented == sharedSecret) return BridgeServer.AuthContext.DEFAULT
        val matched = apiKeys.load().firstOrNull { it.enabled && it.key == presented } ?: return null
        return BridgeServer.AuthContext(
            keyId = matched.id,
            allowedModels = matched.allowedModels,
            budgetRemaining = matched.budgetRemaining,
        )
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
            android.util.Log.e("LyraProxy", "startForeground refused; bridge is unprotected", it)
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
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LyraProxy:bridge")
                .apply { setReferenceCounted(false); acquire() }
        }.onFailure { android.util.Log.w("LyraProxy", "wake lock unavailable", it) }.getOrNull()
    }

    /**
     * Shows the status panel when the user enabled it and granted overlay permission.
     *
     * The panel is what keeps the process at visible importance, so the system
     * does not freeze it while it serves requests. Without the permission the
     * bridge still runs; it is simply subject to the usual background policy.
     */
    private fun showOverlay() {
        if (!BridgeSettings.overlayEnabled(this)) return
        if (overlay?.isShowing() == true) return
        if (!KeepAliveUtils.canDrawOverlays(this)) {
            android.util.Log.i("LyraProxy", "overlay permission not granted; running without the panel")
            return
        }
        if (overlay == null) {
            overlay = BridgeOverlay(
                context = this,
                panelOpacity = BridgeSettings.opacity(this),
                movable = BridgeSettings.overlayMovable(this),
                lockedInitially = BridgeSettings.locked(this),
                passthroughInitially = BridgeSettings.overlayPassthrough(this),
            ).also { it.attach() }
        }
        overlay?.show()
        overlayRef = overlay
    }

    /** Hides the panel; the service itself keeps running. */
    fun removeOverlay() {
        overlay?.detach()
        overlay = null
        overlayRef = null
    }

    /**
     * 设置页改变开关后调用，让面板立刻跟随，而不必等下一次服务启动。
     * 关闭时直接隐藏；开启时按前后台状态重新判断。
     */
    fun refreshOverlay() {
        if (!BridgeSettings.overlayEnabled(this)) {
            removeOverlay()
            return
        }
        showOverlay()
        overlay?.sync()
    }

    private suspend fun refreshModelsLoop() {
        while (true) {
            val cred = store.active()
            if (cred != null) {
                val fetched = runCatching { upstream.fetchModels(cred).map { it.id } }.getOrNull()
                if (!fetched.isNullOrEmpty()) models = fetched
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
        const val DEFAULT_SECRET = "wb-local"
        const val EXTRA_PORT = "port"
        const val EXTRA_SECRET = "secret"
        private const val MODEL_REFRESH_MS = 5 * 60 * 1000L
    }
}
