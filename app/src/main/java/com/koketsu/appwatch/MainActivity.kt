package com.koketsu.appwatch

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.koketsu.appwatch.core.Health
import com.koketsu.appwatch.core.Targets
import com.koketsu.appwatch.data.MonitorRepository
import com.koketsu.appwatch.data.MonitorStore
import com.koketsu.appwatch.notify.Notifier
import com.koketsu.appwatch.ui.MonitorScreen
import com.koketsu.appwatch.ui.UiState
import com.koketsu.appwatch.ui.theme.AppWatchTheme
import com.koketsu.appwatch.work.CheckScheduler
import com.koketsu.appwatch.widget.MonitorWidgetProvider
import com.koketsu.appwatch.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主界面。
 *
 * 核心约定：**每次进入前台（onStart）立即执行一次检查**，不等后台任务。
 * 检查在 IO 线程执行，结果回主线程渲染，同时更新桌面小组件与通知。
 */
class MainActivity : ComponentActivity() {

    private val uiState = mutableStateOf(UiState())
    private var checkInFlight = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshMeta()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppWatchTheme {
                MonitorScreen(
                    state = uiState.value,
                    onCheckNow = { checkNow() },
                    onSelectTarget = { pkg -> switchTarget(pkg) },
                    onReschedule = {
                        CheckScheduler.reschedule(this)
                        refreshMeta()
                    },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onOpenNotificationSettings = { openNotificationSettings() },
                    onOpenBatterySettings = { openBatterySettings() },
                    onPinWidget = { pinWidget() },
                    onAddCustomTarget = { pkg -> addCustomTarget(pkg) },
                    onRemoveCustomTarget = { pkg -> removeCustomTarget(pkg) },
                    onOpenGithub = { openGithub() }
                )
            }
        }
        refreshMeta()
    }

    override fun onStart() {
        super.onStart()
        // 每次打开（含从后台切回前台）都立即检查，不依赖后台任务
        checkNow()
    }

    private fun checkNow() {
        if (checkInFlight) return
        checkInFlight = true
        uiState.value = uiState.value.copy(checking = true)

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                MonitorRepository.performCheck(this@MainActivity)
            }
            val store = MonitorStore(this@MainActivity)
            uiState.value = uiState.value.copy(
                checking = false,
                selectedPackage = store.selectedPackage,
                candidates = Targets.allCandidates(store.customPackages),
                status = outcome.result.status,
                label = store.lastKnownLabel,
                version = store.lastKnownVersion,
                lastCheckAt = store.lastCheckAt,
                nextPlannedAt = store.nextPlannedAt,
                lastWorkerRunAt = store.lastWorkerRunAt,
                health = Health.evaluate(store.lastWorkerRunAt, store.nextPlannedAt),
                error = store.lastError,
                justNotified = outcome.notified
            )
            checkInFlight = false
            refreshMeta(keepResult = true)
        }
    }

    /**
     * 刷新与"检查结果"无关的环境信息：通知权限、电池优化、排程时间。
     * @param keepResult true 时保留刚写入的检查结果，避免被存储里的旧值覆盖
     */
    private fun refreshMeta(keepResult: Boolean = false) {
        val store = MonitorStore(this)
        val base = uiState.value
        uiState.value = base.copy(
            selectedPackage = store.selectedPackage,
            candidates = Targets.allCandidates(store.customPackages),
            status = if (keepResult) base.status else (store.lastCheckStatus ?: store.lastValidStatus),
            label = store.lastKnownLabel,
            version = store.lastKnownVersion,
            lastCheckAt = store.lastCheckAt,
            nextPlannedAt = store.nextPlannedAt,
            lastWorkerRunAt = store.lastWorkerRunAt,
            health = Health.evaluate(store.lastWorkerRunAt, store.nextPlannedAt),
            error = store.lastError,
            notificationAllowed = Notifier.canPost(this),
            batteryUnrestricted = isIgnoringBatteryOptimizations()
        )
    }

    /**
     * 切换监控目标。
     *
     * 必须先清空历史状态（[MonitorStore.switchTarget] 内部完成），
     * 否则"目标 A 已安装 → 目标 B 未安装"会被误判成状态跳变、误发通知。
     * 清空后立刻重新检查一次，用当前事实重建基线。
     */
    private fun switchTarget(packageName: String) {
        val store = MonitorStore(this)
        if (store.selectedPackage == packageName) return

        store.switchTarget(packageName)
        uiState.value = UiState(selectedPackage = packageName, checking = true)
        WidgetRenderer.refreshAll(this)
        checkNow()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // 低版本没有运行时通知权限，直接跳系统通知设置
            openNotificationSettings()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            openNotificationSettings()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        }
        startActivitySafely(intent)
    }

    private fun openBatterySettings() {
        // 直接打开系统的电池优化列表，避免申请 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 这种敏感权限
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        startActivitySafely(intent)
    }

    /**
     * 请求桌面临时"钉"一个小部件。
     * 系统会弹一个确认框，用户点「添加」即可，比长按桌面找小组件快得多。
     */
    private fun pinWidget() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "系统版本过低，请长按桌面手动添加", Toast.LENGTH_LONG).show()
            return
        }
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager == null || !manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "当前桌面不支持直接添加，请长按桌面手动添加", Toast.LENGTH_LONG).show()
            return
        }
        val provider = ComponentName(this, MonitorWidgetProvider::class.java)
        manager.requestPinAppWidget(provider, null, null)
    }

    /** 添加一个自定义监控目标，并立即切过去检查 */
    private fun addCustomTarget(packageName: String) {
        val store = MonitorStore(this)
        val added = store.addCustomPackage(packageName)
        uiState.value = uiState.value.copy(candidates = Targets.allCandidates(store.customPackages))
        if (added) switchTarget(packageName.trim())
    }

    /** 移除自定义目标；如果移的正是当前目标，退回默认目标 */
    private fun removeCustomTarget(packageName: String) {
        val store = MonitorStore(this)
        store.removeCustomPackage(packageName)
        uiState.value = uiState.value.copy(candidates = Targets.allCandidates(store.customPackages))
        if (store.selectedPackage == packageName) {
            switchTarget(Targets.defaultPackage)
        }
    }

    /** 打开项目主页（交给浏览器，本应用自身不需要网络权限） */
    private fun openGithub() {
        startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(Targets.GITHUB_URL)))
    }

    private fun startActivitySafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // 个别 ROM 缺少该设置页，兜底打开应用详情
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null))
                )
            }
        }
    }
}
