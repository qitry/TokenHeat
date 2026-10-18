package com.wbhub.app.bridge

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * 进程级的前台状态追踪。
 *
 * 保活悬浮窗只在应用处于后台时才有意义，应用自己的界面在前台时它必须让位，
 * 否则会盖住用户正在看的页面。用 Activity 计数统一判断，避免每个界面各自挂钩子。
 *
 * 移植自 termux-app 的 keepalive/AppForegroundTracker。
 */
class AppForegroundTracker private constructor() : Application.ActivityLifecycleCallbacks {

    private var resumedActivities = 0
    private var listener: ((Boolean) -> Unit)? = null

    fun setListener(listener: ((Boolean) -> Unit)?) {
        synchronized(this) {
            this.listener = listener
            // 立即回报当前状态，避免「注册时已在后台」却以为在前台。
            listener?.invoke(resumedActivities > 0)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        val becameForeground: Boolean
        synchronized(this) {
            resumedActivities++
            becameForeground = resumedActivities == 1
        }
        if (becameForeground) listener?.invoke(true)
    }

    override fun onActivityPaused(activity: Activity) {
        val becameBackground: Boolean
        synchronized(this) {
            if (resumedActivities > 0) resumedActivities--
            becameBackground = resumedActivities == 0
        }
        if (becameBackground) listener?.invoke(false)
    }

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        @Volatile
        private var instance: AppForegroundTracker? = null

        /** 在 Application 上注册一次；重复调用无副作用。 */
        @Synchronized
        fun init(application: Application): AppForegroundTracker {
            instance?.let { return it }
            val tracker = AppForegroundTracker()
            application.registerActivityLifecycleCallbacks(tracker)
            instance = tracker
            return tracker
        }

        fun get(): AppForegroundTracker? = instance
    }
}
