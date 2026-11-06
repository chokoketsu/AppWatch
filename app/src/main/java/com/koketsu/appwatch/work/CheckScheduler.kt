package com.koketsu.appwatch.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.koketsu.appwatch.data.MonitorStore
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 后台任务的排程中心。
 *
 * 使用 WorkManager 的周期任务，周期 24 小时，首次延迟到"下一个中午 12:00"。
 *
 * 关于精度：Worker 的实际执行时刻受 Doze、电池优化、系统调度影响，
 * 不能保证恰好 12:00 整执行。这里只保证"每天一次、大致落在中午附近"，
 * 并在界面与小组件中如实显示"上次实际检查时间"与"下次计划时间"。
 *
 * 重启恢复：WorkManager 自己把任务持久化到数据库，并注册了开机接收器，
 * 重启后会自动恢复；[com.koketsu.appwatch.receiver.BootReceiver] 只是再补一次
 * 幂等的 ensureScheduled()，并刷新小组件。
 */
object CheckScheduler {

    const val UNIQUE_WORK_NAME = "appwatch_daily_noon_check"

    /** 幂等：已存在则更新配置，不会叠加出第二个任务 */
    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val nextRun = nextNoon(now)
        val initialDelay = (nextRun - now).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))

        val request = PeriodicWorkRequestBuilder<MonitorWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        MonitorStore(appContext).nextPlannedAt = nextRun
    }

    /** 重新排程：把下一次运行重新对齐到最近的中午 */
    fun reschedule(context: Context) {
        ensureScheduled(context)
    }

    /**
     * 计算 [from] 之后最近的一个本地时间 12:00。
     * 如果今天 12:00 还没到，就取今天；否则取明天。
     */
    fun nextNoon(from: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= from) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private const val TAG = "appwatch"
}
