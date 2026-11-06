package com.koketsu.appwatch.data

/**
 * 单次查询的原始结果。
 *
 * @param status      查询结论
 * @param label       真实应用名（仅 INSTALLED 时可能有值）
 * @param versionName 版本名（仅 INSTALLED 时可能有值）
 * @param detail      异常描述（仅 UNKNOWN 时有值，写入界面便于排查）
 */
data class CheckResult(
    val status: MonitorStatus,
    val label: String? = null,
    val versionName: String? = null,
    val detail: String? = null
)
