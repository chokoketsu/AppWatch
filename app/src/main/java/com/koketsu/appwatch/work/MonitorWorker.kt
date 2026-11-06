package com.koketsu.appwatch.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.koketsu.appwatch.data.MonitorRepository
import com.koketsu.appwatch.data.MonitorStore

/**
 * 后台每日检查（不打开界面、不打扰用户）。
 *
 * 执行完固定返回 success：查询异常已在 [MonitorRepository] 内部被归为 UNKNOWN，
 * 不需要靠 Worker 重试去"修复"，无限重试只会无谓耗电。
 */
class MonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        return try {
            MonitorRepository.performCheck(applicationContext)

            val now = System.currentTimeMillis()
            val store = MonitorStore(applicationContext)
            // 心跳：只有后台任务真正跑完才写。小组件靠它判断调度是否还活着
            store.lastWorkerRunAt = now
            // 本次已执行，把"下次计划时间"推到下一个中午并落盘
            store.nextPlannedAt = CheckScheduler.nextNoon(now)

            Result.success()
        } catch (t: Throwable) {
            // 极端情况下的意外异常，交给 WorkManager 线性退避重试一次也好过静默失败
            Result.retry()
        }
    }
}
