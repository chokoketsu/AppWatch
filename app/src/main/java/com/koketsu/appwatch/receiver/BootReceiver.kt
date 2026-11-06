package com.koketsu.appwatch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.koketsu.appwatch.widget.WidgetRenderer
import com.koketsu.appwatch.work.CheckScheduler

/**
 * 开机 / 应用更新后恢复监控。
 *
 * 两件事都是幂等的：
 *  - ensureScheduled()：WorkManager 本身已持久化任务，这里只是补一次保险
 *    （例如用户清理数据、或系统在某些机型上丢了 WorkManager 数据库）
 *  - refreshAll()：让桌面小组件立刻显示最新保存状态
 *
 * 不会在此处执行检查，避免开机瞬间做多余 IO。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != ACTION_QUICKBOOT_POWERON &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val appContext = context.applicationContext
        CheckScheduler.ensureScheduled(appContext)
        WidgetRenderer.refreshAll(appContext)
    }

    private companion object {
        /** 部分厂商（含小米/HTC）的快速开机广播 */
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
