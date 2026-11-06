package com.koketsu.appwatch.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.koketsu.appwatch.core.Targets

/**
 * 监控状态的本地存储（SharedPreferences，纯本地，不联网、不上传）。
 *
 * 关键字段 [lastValidStatus] 只保存"有效结论"（INSTALLED / NOT_INSTALLED）。
 * 异常（UNKNOWN）不会写入该字段，它是通知去重与状态跳变判断的唯一依据。
 */
class MonitorStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前选中的监控目标，未选过则用配置里的默认值 */
    var selectedPackage: String
        get() = prefs.getString(KEY_SELECTED, null) ?: Targets.defaultPackage
        set(value) = prefs.edit { putString(KEY_SELECTED, value) }

    /**
     * 用户自行添加的监控目标（包名列表，纯本地保存）。
     *
     * 内置清单是编译期固定的；这里的条目由用户在界面「自行添加」里输入，
     * 会排在清单最末尾，选择的判定逻辑与内置条目完全一样。
     */
    var customPackages: List<String>
        get() = prefs.getString(KEY_CUSTOM, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) = prefs.edit { putString(KEY_CUSTOM, value.distinct().joinToString(",")) }

    /** 追加一个自定义目标（已存在或与内置重复则忽略） */
    fun addCustomPackage(packageName: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        val builtIn = Targets.builtInCandidates.map { it.packageName }
        if (pkg in builtIn || pkg in customPackages) return false
        customPackages = customPackages + pkg
        return true
    }

    /** 移除一个自定义目标 */
    fun removeCustomPackage(packageName: String) {
        customPackages = customPackages - packageName
    }

    /** 上一次**有效**检查结论；null 表示从未有过有效检查 */
    var lastValidStatus: MonitorStatus?
        get() = MonitorStatus.fromStorage(prefs.getString(KEY_LAST_VALID, null))
        set(value) = prefs.edit { putString(KEY_LAST_VALID, value?.name) }

    /** 上一次检查的原始结论（可能为 UNKNOWN），用于界面展示 */
    var lastCheckStatus: MonitorStatus?
        get() = MonitorStatus.fromStorage(prefs.getString(KEY_LAST_CHECK, null))
        set(value) = prefs.edit { putString(KEY_LAST_CHECK, value?.name) }

    /** 上次检查时间戳（毫秒），0 表示从未检查 */
    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CHECK_AT, value) }

    /** 下次计划检查时间戳（毫秒），0 表示未排程 */
    var nextPlannedAt: Long
        get() = prefs.getLong(KEY_NEXT_PLANNED, 0L)
        set(value) = prefs.edit { putLong(KEY_NEXT_PLANNED, value) }

    /**
     * 最后一次**后台 Worker 真正执行完**的时间戳。
     *
     * 这是判断"后台监控是否还活着"的唯一依据：
     * 前台手动检查不会更新它，只有定时任务跑起来才会写。
     */
    var lastWorkerRunAt: Long
        get() = prefs.getLong(KEY_LAST_WORKER_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_WORKER_AT, value) }

    /** 最近一次拿到的真实应用名 */
    var lastKnownLabel: String?
        get() = prefs.getString(KEY_LABEL, null)
        set(value) = prefs.edit { putString(KEY_LABEL, value) }

    /** 最近一次拿到的版本号 */
    var lastKnownVersion: String?
        get() = prefs.getString(KEY_VERSION, null)
        set(value) = prefs.edit { putString(KEY_VERSION, value) }

    /** 最近一次异常信息（成功后清空） */
    var lastError: String?
        get() = prefs.getString(KEY_ERROR, null)
        set(value) = prefs.edit { putString(KEY_ERROR, value) }

    /** 界面 / 小组件展示用名称 */
    fun displayName(): String = Targets.displayName(selectedPackage, lastKnownLabel)

    /**
     * 切换监控目标。
     *
     * **必须清空历史状态**：否则从"目标 A 已安装"切到"目标 B 未安装"会被
     * 误判成一次状态跳变。清空后 lastValidStatus 为 null，
     * 下一次检查只建立基线、不会触发通知。
     */
    fun switchTarget(newPackage: String) {
        prefs.edit {
            putString(KEY_SELECTED, newPackage)
            remove(KEY_LAST_VALID)
            remove(KEY_LAST_CHECK)
            remove(KEY_LABEL)
            remove(KEY_VERSION)
            remove(KEY_ERROR)
            putLong(KEY_LAST_CHECK_AT, 0L)
        }
    }

    companion object {
        private const val PREFS_NAME = "appwatch_state"
        private const val KEY_SELECTED = "selected_package"
        private const val KEY_CUSTOM = "custom_packages"
        private const val KEY_LAST_VALID = "last_valid_status"
        private const val KEY_LAST_CHECK = "last_check_status"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_NEXT_PLANNED = "next_planned_at"
        private const val KEY_LAST_WORKER_AT = "last_worker_run_at"
        private const val KEY_LABEL = "last_label"
        private const val KEY_VERSION = "last_version"
        private const val KEY_ERROR = "last_error"
    }
}
