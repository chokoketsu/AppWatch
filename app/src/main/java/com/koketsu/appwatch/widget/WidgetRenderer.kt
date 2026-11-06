package com.koketsu.appwatch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.koketsu.appwatch.MainActivity
import com.koketsu.appwatch.R
import com.koketsu.appwatch.core.Health
import com.koketsu.appwatch.core.MonitorHealth
import com.koketsu.appwatch.core.TimeFmt
import com.koketsu.appwatch.data.MonitorStatus
import com.koketsu.appwatch.data.MonitorStore

/**
 * 小组件渲染。
 *
 * 两条规则：
 *  1. **只读本地存储**，不发起查询。刷新小组件绝不会影响通知去重。
 *  2. **按尺寸自适应**：桌面格子可从 2×2 一直拉到很大，随着高度变小
 *     逐级隐藏次要信息（包名 → 下次检查 → 上次检查），保证任何尺寸都不出现半截文字。
 *
 * 配色约定：
 *  检测到（已安装）= 红，未检测到（未安装）= 蓝，查询异常 = 橙。
 */
object WidgetRenderer {

    /** 布局档位，按桌面格子尺寸选择 */
    private const val TIER_FULL = 0     // 完整：名称 / 包名 / 状态 / 上次 / 下次
    private const val TIER_MEDIUM = 1   // 省掉包名与下次检查
    private const val TIER_TINY = 2     // 只留名称与状态

    fun refreshAll(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, MonitorWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)
        ids.forEach { id ->
            appWidgetManager.updateAppWidget(id, build(context, appWidgetManager.getAppWidgetOptions(id)))
        }
    }

    fun build(context: Context, options: Bundle? = null): RemoteViews {
        val store = MonitorStore(context)
        val views = RemoteViews(context.packageName, R.layout.widget_monitor)
        val tier = tierOf(options)

        // 名称与包名
        views.setTextViewText(R.id.widget_title, store.displayName())
        views.setTextViewText(R.id.widget_package, store.selectedPackage)

        // 状态：优先展示"上一次原始结果"，异常会如实反映
        val shown = store.lastCheckStatus ?: store.lastValidStatus
        val status = shown ?: MonitorStatus.UNKNOWN
        views.setTextViewText(R.id.widget_status, statusText(context, status))
        views.setTextColor(R.id.widget_status, statusColor(context, status))
        views.setImageViewResource(R.id.widget_dot, statusDot(status))

        // 时间
        val lastAt = store.lastCheckAt
        views.setTextViewText(
            R.id.widget_last_check,
            if (lastAt > 0L) context.getString(R.string.widget_last_check, TimeFmt.full(lastAt))
            else context.getString(R.string.widget_never_checked)
        )
        views.setTextViewText(
            R.id.widget_next_check,
            context.getString(R.string.widget_next_check, TimeFmt.timeOnly(store.nextPlannedAt))
        )

        // 后台健康度：靠 Worker 心跳判断调度是否还活着。
        // 即使 App 进程早被系统回收、Worker 被厂商策略掐掉，
        // 小组件每 30 分钟被桌面唤醒重绘一次，也会在这里如实变脸。
        val health = Health.evaluate(store.lastWorkerRunAt, store.nextPlannedAt)
        views.setTextViewText(
            R.id.widget_health,
            healthText(context, health, store.lastWorkerRunAt)
        )
        views.setTextColor(
            R.id.widget_health,
            ContextCompat.getColor(
                context,
                when {
                    health == MonitorHealth.OK -> R.color.health_ok
                    health == MonitorHealth.NEVER_RAN -> R.color.widget_text_secondary
                    else -> R.color.health_warn
                }
            )
        )

        // 按档位隐藏次要信息（健康度在任何尺寸下都保留）
        views.setViewVisibility(
            R.id.widget_package,
            if (tier == TIER_FULL) View.VISIBLE else View.GONE
        )
        views.setViewVisibility(
            R.id.widget_next_check,
            if (tier == TIER_FULL) View.VISIBLE else View.GONE
        )
        views.setViewVisibility(
            R.id.widget_last_check,
            if (tier == TIER_TINY) View.GONE else View.VISIBLE
        )

        // 点击打开 App
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }

    private fun tierOf(options: Bundle?): Int {
        if (options == null) return TIER_FULL
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        return when {
            height in 1 until 90 -> TIER_TINY
            height in 1 until 140 || width in 1 until 170 -> TIER_MEDIUM
            else -> TIER_FULL
        }
    }

    fun statusText(context: Context, status: MonitorStatus): String = when (status) {
        MonitorStatus.INSTALLED -> context.getString(R.string.status_installed)
        MonitorStatus.NOT_INSTALLED -> context.getString(R.string.status_not_installed)
        MonitorStatus.UNKNOWN -> context.getString(R.string.status_unknown)
    }

    /** 后台健康度文案 */
    fun healthText(context: Context, health: MonitorHealth, lastWorkerRunAt: Long): String =
        when (health) {
            MonitorHealth.OK -> context.getString(R.string.health_ok)
            MonitorHealth.NEVER_RAN -> context.getString(R.string.health_never_ran)
            MonitorHealth.STALE -> context.getString(
                R.string.health_stale,
                Health.hoursSince(lastWorkerRunAt)
            )
            MonitorHealth.UNSCHEDULED -> context.getString(R.string.health_unscheduled)
        }

    fun statusColor(context: Context, status: MonitorStatus): Int = ContextCompat.getColor(
        context,
        when (status) {
            MonitorStatus.INSTALLED -> R.color.status_installed
            MonitorStatus.NOT_INSTALLED -> R.color.status_not_installed
            MonitorStatus.UNKNOWN -> R.color.status_unknown
        }
    )

    private fun statusDot(status: MonitorStatus): Int = when (status) {
        MonitorStatus.INSTALLED -> R.drawable.widget_dot_installed
        MonitorStatus.NOT_INSTALLED -> R.drawable.widget_dot_not_installed
        MonitorStatus.UNKNOWN -> R.drawable.widget_dot_unknown
    }
}
