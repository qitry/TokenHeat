package app.anylyralabs.lyraproxy.bridge

import android.content.Context

/**
 * User preferences for the status panel.
 *
 * Kept in shared preferences rather than the credential file: these are
 * presentation choices that must survive a restart, and they are not secrets.
 */
object BridgeSettings {

    private const val PREFS = "wb-overlay"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_LOCKED = "locked"
    private const val KEY_ENABLED = "overlay_enabled"
    private const val KEY_MOVABLE = "overlay_movable"
    private const val KEY_PASSTHROUGH = "overlay_passthrough"
    private const val KEY_GATEWAY_PROVIDER = "gateway_provider"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"

    fun opacity(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_OPACITY, 0.94f)

    fun setOpacity(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_OPACITY, value.coerceIn(0.15f, 1f)).apply()
    }

    fun locked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCKED, false)

    fun setLocked(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCKED, value).apply()
    }

    /** 悬浮窗总开关；关闭后即便有权限也不显示。 */
    fun overlayEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setOverlayEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** 是否允许拖动；关闭即不能拖动但仍可点击。 */
    fun overlayMovable(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MOVABLE, true)

    fun setOverlayMovable(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MOVABLE, value).apply()
    }

    /**
     * 完全穿透：窗口不接收任何触摸，事件全部落到下层应用。
     *
     * 与「不可拖动」不同：不可拖动仍能点按折叠，穿透模式连点击都放弃，
     * 悬浮窗退化为纯展示。两者互斥，开启穿透时是否可拖动已无意义。
     */
    fun overlayPassthrough(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PASSTHROUGH, false)

    fun setOverlayPassthrough(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PASSTHROUGH, value).apply()
    }

    /** 网关当前转发的上游：workbuddy 或 qoder。 */
    fun gatewayProvider(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GATEWAY_PROVIDER, "workbuddy") ?: "workbuddy"

    fun setGatewayProvider(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_GATEWAY_PROVIDER, value).apply()
    }

    fun position(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_X, -1) to prefs.getInt(KEY_Y, -1)
    }

    fun setPosition(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    /** 别名，语义更贴近悬浮窗场景。 */
    fun overlayPosition(context: Context): Pair<Int, Int> = position(context)

    fun setOverlayPosition(context: Context, x: Int, y: Int) = setPosition(context, x, y)
}
