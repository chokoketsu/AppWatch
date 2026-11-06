package com.koketsu.appwatch.data

/**
 * 监控状态。
 *
 * [UNKNOWN] 与 [NOT_INSTALLED] 必须严格区分：
 * 查询过程抛出的非 NameNotFoundException 异常一律归为 UNKNOWN，
 * 绝不能被当成"未安装"，否则会产生虚假的状态跳变与误通知。
 */
enum class MonitorStatus {
    /** 目标应用存在 */
    INSTALLED,

    /** 确认不存在（PackageManager 明确返回 NameNotFoundException） */
    NOT_INSTALLED,

    /** 查询失败，结果不可信 */
    UNKNOWN;

    companion object {
        fun fromStorage(raw: String?): MonitorStatus? =
            raw?.let { name -> entries.firstOrNull { it.name == name } }
    }
}
