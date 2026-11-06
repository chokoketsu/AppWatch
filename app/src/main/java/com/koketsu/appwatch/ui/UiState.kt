package com.koketsu.appwatch.ui

import com.koketsu.appwatch.core.MonitorHealth
import com.koketsu.appwatch.core.Targets
import com.koketsu.appwatch.data.MonitorStatus

/**
 * 主界面状态快照。全部来自本地存储，不含任何网络数据。
 */
data class UiState(
    /** 是否正在执行一次检查 */
    val checking: Boolean = false,
    /** 当前选中的监控目标包名 */
    val selectedPackage: String = "",
    /** 可选目标清单（内置 + 用户自行添加） */
    val candidates: List<Targets.Candidate> = emptyList(),
    /** 当前展示的状态（原始结论优先，异常会如实显示） */
    val status: MonitorStatus? = null,
    /** 真实应用名，可能为 null（目标未安装时拿不到） */
    val label: String? = null,
    /** 目标版本号 */
    val version: String? = null,
    /** 上次检查时间戳 */
    val lastCheckAt: Long = 0L,
    /** 下次计划检查时间戳 */
    val nextPlannedAt: Long = 0L,
    /** 上次后台 Worker 心跳时间戳 */
    val lastWorkerRunAt: Long = 0L,
    /** 后台调度健康度 */
    val health: MonitorHealth = MonitorHealth.NEVER_RAN,
    /** 上次异常描述（有值时界面会提示） */
    val error: String? = null,
    /** 通知权限是否可用 */
    val notificationAllowed: Boolean = true,
    /** 是否已被排除电池优化（true 表示后台更稳定） */
    val batteryUnrestricted: Boolean = false,
    /** 本次检查是否刚发出过通知 */
    val justNotified: Boolean = false
)
