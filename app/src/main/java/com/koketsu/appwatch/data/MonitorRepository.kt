package com.koketsu.appwatch.data

import android.content.Context
import com.koketsu.appwatch.notify.Notifier
import com.koketsu.appwatch.widget.WidgetRenderer

/**
 * 一次完整检查的编排者。
 *
 * 顺序很重要：
 *  1. **先落盘**（包括把 lastValidStatus 推到新值）
 *  2. 再根据"落盘前的旧值 -> 新值"决定是否通知
 *  3. 最后刷新桌面小组件
 *
 * 先落盘的意义：WorkManager 重试、界面重复调用、小组件刷新都可能触发多次检查，
 * 而 lastValidStatus 一旦变为 INSTALLED，下一次检查看到的就是
 * INSTALLED -> INSTALLED，天然不会重复通知。宁可极小概率漏一条通知，也绝不重复轰炸。
 *
 * 释放：整个读-改-写过程加进程内锁，避免前台检查与后台 Worker 并发时互相覆盖。
 */
object MonitorRepository {

    /**
     * @param previousValid 落盘前的有效状态（null = 从未有效检查过）
     * @param notified      本次是否真的发出了通知
     */
    data class Outcome(
        val result: CheckResult,
        val previousValid: MonitorStatus?,
        val notified: Boolean,
        val checkedAt: Long
    )

    private val lock = Any()

    fun performCheck(context: Context): Outcome {
        val appContext = context.applicationContext
        val store = MonitorStore(appContext)

        // 当前选中的目标（默认 com.mobiletools.systemhelper，可在界面切换）
        val targetPackage = store.selectedPackage

        // 查询本身可能较慢，放在锁外，减少临界区
        val result = PackageChecker.check(appContext, targetPackage)
        val checkedAt = System.currentTimeMillis()

        synchronized(lock) {
            val previousValid = store.lastValidStatus

            // ---- 1. 落盘 ----
            store.lastCheckStatus = result.status
            store.lastCheckAt = checkedAt
            if (result.status != MonitorStatus.UNKNOWN) {
                store.lastValidStatus = result.status
                result.label?.let { store.lastKnownLabel = it }
                result.versionName?.let { store.lastKnownVersion = it }
                store.lastError = null
            } else {
                // 异常不覆盖有效状态，只记录原因
                store.lastError = result.detail
            }

            // ---- 2. 状态跳变判定：未安装 -> 已安装 才通知 ----
            val shouldNotify = previousValid == MonitorStatus.NOT_INSTALLED &&
                result.status == MonitorStatus.INSTALLED
            if (shouldNotify) {
                Notifier.notifyInstalled(appContext, store.displayName())
            }

            // ---- 3. 刷新桌面小组件 ----
            WidgetRenderer.refreshAll(appContext)

            return Outcome(
                result = result,
                previousValid = previousValid,
                notified = shouldNotify,
                checkedAt = checkedAt
            )
        }
    }
}
