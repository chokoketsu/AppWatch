package com.koketsu.appwatch.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间显示工具（纯本地格式化，无时区/网络依赖） */
object TimeFmt {

    private const val PATTERN = "MM-dd HH:mm"
    private const val PATTERN_TIME_ONLY = "HH:mm"

    /** "MM-dd HH:mm"，0 或负数显示为占位符 */
    fun full(millis: Long, placeholder: String = "—"): String {
        if (millis <= 0L) return placeholder
        return SimpleDateFormat(PATTERN, Locale.getDefault()).format(Date(millis))
    }

    /** 只要时分，用于小组件里更紧凑的展示 */
    fun timeOnly(millis: Long, placeholder: String = "—"): String {
        if (millis <= 0L) return placeholder
        return SimpleDateFormat(PATTERN_TIME_ONLY, Locale.getDefault()).format(Date(millis))
    }
}
