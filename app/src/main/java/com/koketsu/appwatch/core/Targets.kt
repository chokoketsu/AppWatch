package com.koketsu.appwatch.core

import com.koketsu.appwatch.BuildConfig

/**
 * 监控目标目录。
 *
 * 内置清单来自 gradle.properties 的 appwatch.candidates（编译期注入 BuildConfig），
 * 中文名在这里补上。内置清单必须与 AndroidManifest 的 <queries> 一致，
 * 因为 Android 11+ 只能查询到 manifest 声明过的包。
 *
 * 用户自行添加的目标由 [com.koketsu.appwatch.data.MonitorStore.customPackages] 保管，
 * 会在界面里排在清单末尾，选择后的行为与内置条目完全一致。
 */
object Targets {

    data class Candidate(
        val packageName: String,
        val label: String,
        /** 是否用户自行添加（界面据此显示"移除"入口） */
        val custom: Boolean = false
    )

    /** 作者署名（界面与文档统一用这一处） */
    const val GITHUB_USER = "chokoketsu"
    const val GITHUB_URL = "https://github.com/chokoketsu/AppWatch"

    // 注意：Kotlin object 的属性按声明顺序初始化，LABELS 必须写在 candidates 之前

    private val LABELS = mapOf(
        "com.mobiletools.systemhelper" to "SystemHelper（联通设备注册）",
        "com.miui.systemAdSolution" to "系统广告服务",
        "com.miui.analytics" to "小米分析统计",
        "com.miui.msa.global" to "MSA 广告标识",
        "com.xiaomi.mipicks" to "应用中心推荐",
        "com.miui.hybrid" to "快应用框架",
        "com.miui.video" to "小米视频",
        "com.miui.news" to "小米新闻资讯",
        "com.miui.browser" to "小米浏览器",
        "com.xiaomi.gamecenter" to "游戏中心",
        "com.xiaomi.gsgservice" to "游戏服务框架",
        "com.miui.miservice" to "服务与反馈",
        "com.xiaomi.market" to "小米应用商店",
        "com.iflytek.inputmethod.miui" to "讯飞输入法（小米定制）",
        "com.miui.cleanmaster" to "小米清理"
    )

    /** 默认监控目标（gradle.properties → appwatch.defaultPackage） */
    val defaultPackage: String = BuildConfig.DEFAULT_PACKAGE

    /** 内置可选目标清单 */
    val builtInCandidates: List<Candidate> = BuildConfig.CANDIDATES.map { pkg ->
        Candidate(pkg, LABELS[pkg] ?: pkg)
    }

    /** 内置 + 自定义 合并后的完整清单（自定义排在最后） */
    fun allCandidates(customPackages: List<String>): List<Candidate> =
        builtInCandidates + customPackages.map { pkg ->
            Candidate(pkg, "自定义 · $pkg", custom = true)
        }

    /**
     * 粗略校验包名格式（只做形状检查，不保证真的存在这个应用）。
     * 至少两段、每段以字母开头、允许数字与下划线。
     */
    private val PACKAGE_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun isValidPackageName(input: String): Boolean =
        input.isNotBlank() && PACKAGE_PATTERN.matches(input.trim())

    /** 包名 → 中文名，查不到就退回包名本身 */
    fun labelOf(packageName: String): String = LABELS[packageName] ?: packageName

    /** 展示名：优先用系统给的真实应用名，拿不到再用目录里的中文名 */
    fun displayName(packageName: String, realLabel: String?): String =
        if (realLabel.isNullOrBlank()) labelOf(packageName) else realLabel
}
