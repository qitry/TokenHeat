package app.anylyralabs.lyraproxy.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 保活悬浮窗。
 *
 * 设计取舍（对照 termux-app 的实现）：
 * - 保留两种交互模式：[BridgeSettings] 里的 movable 对应「可拖动」，关闭则完全穿透；
 * - 应用在前台时隐藏（[AppForegroundTracker]），只在后台显示，避免遮挡自己的界面；
 * - 内容仍是可读的状态面板，而不是 1px 隐形窗口——用户看得见的窗口才能让进程维持可见优先级。
 *
 * 与旧实现的差异：去掉了「点击收起」的隐式交互（与拖动冲突时容易误触），
 * 改为纯状态显示 + 拖动；透明度可在设置里调整。
 */
class BridgeOverlay(
    private val context: Context,
    panelOpacity: Float = 0.94f,
    movable: Boolean = true,
    /** 初始锁定状态；由服务从设置读入，避免在构造期访问属性带来歧义。 */
    lockedInitially: Boolean = false,
) {

    /** 面板不透明度；修改后立即重绘。 */
    var panelOpacity: Float = panelOpacity
        set(value) {
            field = value
            applyBackground()
        }

    /** 是否允许拖动。关闭时窗口完全穿透，不拦截任何触摸。 */
    var movable: Boolean = movable
        set(value) {
            field = value
            params?.let { applyTouchable(it) }
        }

    /** 锁定位置：仍可拖动模式，但拖动被忽略，避免误触挪位。 */
    var locked: Boolean = lockedInitially
        set(value) {
            field = value
        }

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    private var dotView: View? = null
    private var titleView: TextView? = null
    private var detailView: TextView? = null
    private var logView: TextView? = null

    private var appInForeground = false

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    private val foregroundListener: (Boolean) -> Unit = { foreground ->
        appInForeground = foreground
        if (foreground) hide() else show()
    }

    fun attach() {
        AppForegroundTracker.get()?.setListener(foregroundListener)
    }

    /** 解除前台监听，避免服务销毁后仍被追踪器持有。 */
    fun detach() {
        AppForegroundTracker.get()?.setListener(null)
        hide()
    }

    /** 显示窗口；应用在前台或权限缺失时什么都不做。 */
    fun show() {
        if (root != null) return
        if (appInForeground) return
        if (!KeepAliveUtils.canDrawOverlays(context)) return
        if (!BridgeSettings.overlayEnabled(context)) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(context)
        header.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)))
        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(7), 0, 0, 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val detail = TextView(context).apply {
            setTextColor(Color.parseColor("#D9D4CC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setPadding(0, dp(2), 0, 0)
        }
        val log = TextView(context).apply {
            setTextColor(Color.parseColor("#A8A199"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            maxLines = 2
        }

        container.addView(header)
        container.addView(detail)
        container.addView(log)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val (savedX, savedY) = BridgeSettings.overlayPosition(context)
            x = if (savedX >= 0) savedX else dp(10)
            y = if (savedY >= 0) savedY else dp(80)
        }

        root = container
        params = lp
        dotView = dot
        titleView = title
        detailView = detail
        logView = log

        applyBackground()
        applyTouchable(lp)
        runCatching { windowManager.addView(container, lp) }.onFailure {
            BridgeStatus.recordError("悬浮窗显示失败：${it.message?.take(40)}")
            root = null
            params = null
            return
        }
        refresh()
        handler.postDelayed(ticker, REFRESH_MS)
    }

    fun hide() {
        handler.removeCallbacks(ticker)
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        params = null
        dotView = null
        titleView = null
        detailView = null
        logView = null
    }

    fun isShowing(): Boolean = root != null

    /** 按当前前后台状态重新同步一次可见性。 */
    fun sync() {
        if (appInForeground) hide() else show()
    }

    // ------------------------------------------------------------------ //

    /**
     * 可拖动时接受触摸以便移动；穿透模式加上 NOT_TOUCHABLE，
     * 让所有事件落到下层应用，悬浮窗成为纯展示。
     */
    private fun applyTouchable(lp: WindowManager.LayoutParams) {
        if (movable) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            attachDrag()
        } else {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
            root?.setOnTouchListener(null)
        }
        runCatching { params?.let { windowManager.updateViewLayout(root, it) } }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag() {
        val view = root ?: return
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false

        view.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 锁定后仍吞掉触摸（否则窗口会变成一个可点穿下层应用的洞），
                    // 只是不再改变位置。
                    if (locked) return@setOnTouchListener true
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    // 小位移不触发拖动，避免手指轻微抖动就挪位。
                    if (!dragging && abs(dx) + abs(dy) < dp(8)) return@setOnTouchListener true
                    dragging = true
                    lp.x = startX + dx.roundToInt()
                    lp.y = startY + dy.roundToInt()
                    runCatching { windowManager.updateViewLayout(view, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) BridgeSettings.setOverlayPosition(context, lp.x, lp.y)
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun refresh() {
        val running = BridgeStatus.running
        val dot = dotView ?: return
        (dot.background as? GradientDrawable)?.setColor(
            if (running) Color.parseColor("#8FBF6A") else Color.parseColor("#C4756A"),
        )
        dot.invalidate()

        titleView?.text = if (running) "LyraProxy 运行中" else "LyraProxy 已停止"
        detailView?.text = if (running) {
            "端口 ${BridgeStatus.port} · 已服务 ${BridgeStatus.requestCount.get()} 次"
        } else {
            "本机网关未启动"
        }
        logView?.text = when {
            BridgeStatus.lastError.isNotEmpty() -> "⚠ ${BridgeStatus.lastError}"
            BridgeStatus.lastRequest.isNotEmpty() -> "↳ ${BridgeStatus.lastRequest}"
            else -> "等待请求…"
        }
    }

    private fun applyBackground() {
        val container = root ?: return
        val drawable = GradientDrawable().apply {
            cornerRadius = dpF(14f)
            setColor(withAlpha(Color.parseColor("#1E1B19"), panelOpacity))
            setStroke(dp(1), withAlpha(Color.parseColor("#4A423B"), panelOpacity))
        }
        container.background = drawable
        // 圆点需要独立绘制，重设背景时一并补回。
        dotView?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                if (BridgeStatus.running) Color.parseColor("#8FBF6A") else Color.parseColor("#C4756A"),
            )
        }
        container.invalidate()
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0.15f, 1f) * 255).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun dpF(value: Float): Float = value * context.resources.displayMetrics.density

    private companion object {
        const val REFRESH_MS = 1000L
    }
}
