package com.koketsu.appwatch

import android.app.Application
import com.koketsu.appwatch.notify.Notifier
import com.koketsu.appwatch.work.CheckScheduler

/**
 * 应用入口。
 *
 * 只做两件事，都很轻：
 *  1. 创建通知渠道（Android 8+ 必需，重复创建是幂等的）
 *  2. 确保后台每日检查任务已被排入 WorkManager（重复排程是幂等的）
 *
 * 不启动任何前台服务，不弹窗，不打开界面。
 */
class AppWatchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        CheckScheduler.ensureScheduled(this)
    }
}
