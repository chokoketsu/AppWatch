package com.koketsu.appwatch.core

/**
 * 后台监控的"健康度"。
 *
 * 判定依据是**心跳**：后台 Worker 每次真正跑完都会写一次 lastWorkerRunAt。
 * 小组件每 30 分钟被桌面唤醒一次重新渲染，每次都会算一遍这个健康度——
 * 所以哪怕 App 进程早就被系统回收、Worker 被厂商策略掐掉，
 * 小组件也能在半小时内自己变脸提示「后台没在跑了」。
 *
 * 注意：Android 里"App 进程死了"是正常现象（后台进程随时会被回收），
 * 真正该关心的是"定时任务还会不会跑"。所以这里判定的是调度是否失活，
 * 而不是进程是否存活。
 */
enum class MonitorHealth {
    /** 一切正常 */
    OK,

    /** 任务排上了，但后台 Worker 一次都还没跑过 */
    NEVER_RAN,

    /** 上次后台运行距今超过阈值，基本可以认定被杀了 */
    STALE,

    /** WorkManager 里没有排程（数据被清、或任务被取消） */
    UNSCHEDULED;

    /**
     * 是否属于"需要注意"的异常。
     * NEVER_RAN 只是"还没到点跑过"，不算异常，避免刚装完就一片橙色吓人。
     */
    val isWarning: Boolean get() = this == STALE || this == UNSCHEDULED
}

object Health {

    /** 超过这么久没有后台心跳，就认为调度已失活 */
    const val STALE_THRESHOLD_MS: Long = 36L * 60 * 60 * 1000

    fun evaluate(
        lastWorkerRunAt: Long,
        nextPlannedAt: Long,
        now: Long = System.currentTimeMillis()
    ): MonitorHealth = when {
        nextPlannedAt <= 0L -> MonitorHealth.UNSCHEDULED
        lastWorkerRunAt <= 0L -> MonitorHealth.NEVER_RAN
        now - lastWorkerRunAt > STALE_THRESHOLD_MS -> MonitorHealth.STALE
        else -> MonitorHealth.OK
    }

    /** 距离上次后台心跳过了多少小时（用于文案） */
    fun hoursSince(lastWorkerRunAt: Long, now: Long = System.currentTimeMillis()): Long =
        if (lastWorkerRunAt <= 0L) 0L else (now - lastWorkerRunAt) / (60 * 60 * 1000)
}
