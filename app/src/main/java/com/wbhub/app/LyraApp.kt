package com.wbhub.app

import android.app.Application
import com.wbhub.app.bridge.AppForegroundTracker

/**
 * 应用入口。
 *
 * 唯一职责是尽早注册前台状态追踪：保活悬浮窗要据此判断
 * 「应用在前台就隐藏、退到后台才显示」，注册得越晚越容易出现闪烁。
 */
class LyraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppForegroundTracker.init(this)
    }
}
