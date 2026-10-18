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
    /** 初始是否完全穿透：不接收任何触摸，纯展示。 */
    passthroughInitially: Boolean = false,
) {

    /** 面板不透明度；修改后立即重绘。 */
    var panelOpacity: Float = panelOpacity
        set(value) {
            field = value
            applyBackground()
        }

    /** 是否允许拖动。关闭后窗口仍可点击折叠，只是不再移动位置。 */
    var movable: Boolean = movable
        set(value) {
            field = value
            params?.let { applyTouchable(it) }
        }

    /**
     * 完全穿透：窗口不接收触摸，点击落到下层应用。
     * 优先级高于 [movable] 与 [locked]——一旦穿透，拖动与点按都不再响应。
     */
    var passthrough: Boolean = passthroughInitially
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

    /** 是否展开为三行；收起时只留首行，窗口随之收窄。 */
    private var expanded = true

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
            // 单行显示，收起时窗口宽度就由这一行决定，不会因换行撑高。
            maxLines = 1
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

        // 重建窗口时沿用当前折叠状态，避免每次重建都跳回展开形态。
        detail.visibility = if (expanded) View.VISIBLE else View.GONE
        log.visibility = if (expanded) View.VISIBLE else View.GONE

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
     * 按三种显示模式设置窗口的触摸行为：
     * 1. 完全穿透（[passthrough]）：加 NOT_TOUCHABLE，所有事件落到下层应用；
     * 2. 其余两种模式都接收触摸，以便点按折叠；[movable] 只决定能否拖动。
     */
    private fun applyTouchable(lp: WindowManager.LayoutParams) {
        if (passthrough) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
            root?.setOnTouchListener(null)
        } else {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            attachDrag()
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
        // 手指是否发生过明显位移。锁定位置时不允许拖动，但也不应把
        // 「滑动了一下」误判成点击，所以单独记录位移而不是复用 dragging。
        var moved = false

        view.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    // 小位移不算移动，避免手指轻微抖动就判定为滑动。
                    if (!moved && abs(dx) + abs(dy) < dp(8)) return@setOnTouchListener true
                    moved = true
                    // 锁定位置：吞掉触摸（否则窗口会变成可点穿下层应用的洞），
                    // 但不改变坐标，只保留点击折叠能力。
                    if (locked) return@setOnTouchListener true
                    dragging = true
                    lp.x = startX + dx.roundToInt()
                    lp.y = startY + dy.roundToInt()
                    runCatching { windowManager.updateViewLayout(view, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        BridgeSettings.setOverlayPosition(context, lp.x, lp.y)
                    } else if (event.action == MotionEvent.ACTION_UP && !moved) {
                        // 没有位移的抬手视为一次点击：在「仅第一行」与「完整三行」之间切换。
                        toggleExpanded()
                    }
                    dragging = false
                    moved = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 收起时隐藏第二、三行。
     *
     * 窗口宽度由内容决定（WRAP_CONTENT），所以隐藏长文本行后，
     * 窗口自然收缩到第一行的宽度，而不是保留展开时的宽度。
     */
    private fun toggleExpanded() {
        expanded = !expanded
        detailView?.visibility = if (expanded) View.VISIBLE else View.GONE
        logView?.visibility = if (expanded) View.VISIBLE else View.GONE
        refresh()
        // 内容尺寸变化后立即让窗口重新测量，避免收起后仍占原来的宽度。
        runCatching {
            params?.let { lp ->
                // 展开会变宽，若原本靠右则可能超出屏幕，这里把窗口拉回屏内。
                clampToScreen(lp)
                windowManager.updateViewLayout(root, lp)
            }
        }
    }

    /** 把窗口位置约束在屏幕可见范围内，宽度变化后调用。 */
    private fun clampToScreen(lp: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        val view = root ?: return
        val width = if (view.width > 0) view.width else view.measuredWidth
        val height = if (view.height > 0) view.height else view.measuredHeight
        if (width <= 0 || height <= 0) return
        val maxX = (metrics.widthPixels - width - dp(4)).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - height - dp(4)).coerceAtLeast(0)
        lp.x = lp.x.coerceIn(0, maxX)
        lp.y = lp.y.coerceIn(0, maxY)
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
