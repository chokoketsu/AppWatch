package com.koketsu.appwatch.data

import android.content.Context
import android.content.pm.PackageManager

/**
 * 目标应用存在性查询。
 *
 * 只查一个包，不遍历应用列表。
 *
 * 结论映射规则（这是本项目的核心正确性约束）：
 *  - 拿到 PackageInfo                  -> INSTALLED
 *  - 抛出 NameNotFoundException        -> NOT_INSTALLED（系统明确表示"没有这个包"）
 *  - 其他任何异常（SecurityException、
 *    RuntimeException、OOM 等）         -> UNKNOWN（结果不可信，绝不当作未安装）
 */
object PackageChecker {

    fun check(context: Context, targetPackage: String): CheckResult {
        val pm = context.packageManager
        return try {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(targetPackage, 0)
            val label = info.applicationInfo?.let { appInfo ->
                runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrNull()
            }
            CheckResult(
                status = MonitorStatus.INSTALLED,
                label = label,
                versionName = info.versionName
            )
        } catch (e: PackageManager.NameNotFoundException) {
            // 系统明确回答"查无此包"
            CheckResult(status = MonitorStatus.NOT_INSTALLED)
        } catch (t: Throwable) {
            // 查询过程本身出问题，结论不可用
            CheckResult(
                status = MonitorStatus.UNKNOWN,
                detail = buildString {
                    append(t.javaClass.simpleName)
                    t.message?.let { append(": ").append(it) }
                }
            )
        }
    }
}
