package com.koketsu.appwatch.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * 桌面常驻小组件。
 *
 * 小组件本身不发起查询（避免被桌面刷新反复触发检查），
 * 只负责把本地已保存的状态渲染出来；
 * 状态更新由 [com.koketsu.appwatch.data.MonitorRepository] / 后台 Worker 主动推送。
 *
 * 支持自由缩放：每次尺寸变化都会按新的格子大小重新渲染。
 */
class MonitorWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(
                id,
                WidgetRenderer.build(context, appWidgetManager.getAppWidgetOptions(id))
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // 用户拉动边角改变格子大小，按新尺寸重排内容
        appWidgetManager.updateAppWidget(
            appWidgetId,
            WidgetRenderer.build(context, newOptions)
        )
    }
}
