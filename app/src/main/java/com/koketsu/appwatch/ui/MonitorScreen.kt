package com.koketsu.appwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koketsu.appwatch.core.MonitorHealth
import com.koketsu.appwatch.core.Targets
import com.koketsu.appwatch.core.TimeFmt
import com.koketsu.appwatch.data.MonitorStatus

// 配色约定：检测到 = 红（警报），未检测到 = 蓝（安全），异常 = 橙
private val InstalledColor = Color(0xFFE53935)
private val NotInstalledColor = Color(0xFF1E88E5)
private val UnknownColor = Color(0xFFB26A00)
private val RunningColor = Color(0xFF2E7D32)

@Composable
fun MonitorScreen(
    state: UiState,
    onCheckNow: () -> Unit,
    onSelectTarget: (String) -> Unit,
    onReschedule: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onPinWidget: () -> Unit,
    onAddCustomTarget: (String) -> Unit,
    onRemoveCustomTarget: (String) -> Unit,
    onOpenGithub: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(text = "AppWatch", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "本地应用存在性监控 · 完全离线",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TargetCard(state, onSelectTarget, onAddCustomTarget, onRemoveCustomTarget)
            StatusCard(state, onCheckNow)
            BackgroundCard(state, onReschedule)
            PermissionCard(
                state,
                onRequestNotificationPermission,
                onOpenNotificationSettings,
                onOpenBatterySettings
            )
            WidgetHintCard(onPinWidget)

            Spacer(Modifier.height(8.dp))
            Text(
                text = "本应用不使用网络权限，不做全量应用扫描，不上传任何数据。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 作者署名 / 项目主页
            TextButton(
                onClick = onOpenGithub,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "github.com/${Targets.GITHUB_USER}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TargetCard(
    state: UiState,
    onSelectTarget: (String) -> Unit,
    onAddCustomTarget: (String) -> Unit,
    onRemoveCustomTarget: (String) -> Unit
) {
    val menuOpen = remember { mutableStateOf(false) }
    val dialogOpen = remember { mutableStateOf(false) }
    val current = state.selectedPackage.ifBlank { Targets.defaultPackage }
    val currentIsCustom = state.candidates.any { it.custom && it.packageName == current }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "监控目标",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))

                Box {
                    OutlinedButton(
                        onClick = { menuOpen.value = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("切换目标", fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = menuOpen.value,
                        onDismissRequest = { menuOpen.value = false },
                        modifier = Modifier.heightIn(max = 420.dp)
                    ) {
                        state.candidates.forEach { candidate ->
                            val selected = candidate.packageName == current
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = candidate.label,
                                            fontSize = 14.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold
                                            else FontWeight.Normal
                                        )
                                        Text(
                                            text = candidate.packageName,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (selected) {
                                        Text("✓", fontSize = 14.sp, color = RunningColor)
                                    }
                                },
                                onClick = {
                                    menuOpen.value = false
                                    if (!selected) onSelectTarget(candidate.packageName)
                                }
                            )
                        }

                        HorizontalDivider()

                        // 清单末尾：自行添加
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "＋ 自行添加…",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                menuOpen.value = false
                                dialogOpen.value = true
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = Targets.displayName(current, state.label),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = current,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.status == MonitorStatus.INSTALLED && !state.version.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "版本 ${state.version}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                currentIsCustom -> {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { onRemoveCustomTarget(current) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("移除此自定义目标", fontSize = 11.sp)
                    }
                }

                current == Targets.defaultPackage -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "默认目标",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (dialogOpen.value) {
        AddTargetDialog(
            onDismiss = { dialogOpen.value = false },
            onConfirm = { pkg ->
                dialogOpen.value = false
                onAddCustomTarget(pkg)
            }
        )
    }
}

@Composable
private fun AddTargetDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val input = remember { mutableStateOf("") }
    val text = input.value
    val valid = Targets.isValidPackageName(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自行添加监控目标", fontSize = 18.sp) },
        text = {
            Column {
                Text(
                    text = "输入要监控的应用包名，例如 com.example.app",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input.value = it },
                    singleLine = true,
                    isError = text.isNotBlank() && !valid,
                    label = { Text("包名") },
                    placeholder = { Text("com.example.app") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (text.isNotBlank() && !valid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "格式不对，至少两段、每段以字母开头",
                        fontSize = 11.sp,
                        color = InstalledColor
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "提示：无桌面图标的后台类系统应用受 Android 11+ 可见性限制，" +
                        "可能查不到；这种情况需要把包名写进安装包清单才有效。",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(text.trim()) }
            ) {
                Text("添加并监控")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun StatusCard(state: UiState, onCheckNow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(statusColor(state.status), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = statusLabel(state.status),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor(state.status)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            InfoRow("上次检查", TimeFmt.full(state.lastCheckAt, "尚未检查"))
            InfoRow("下次计划检查", TimeFmt.full(state.nextPlannedAt, "未排程"))

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(text = "最近一次查询异常：$it", fontSize = 11.sp, color = UnknownColor)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onCheckNow,
                enabled = !state.checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.checking) "检查中…" else "立即检查")
            }
        }
    }
}

@Composable
private fun BackgroundCard(state: UiState, onReschedule: () -> Unit) {
    val warn = state.health.isWarning
    val healthColor = if (warn) UnknownColor else RunningColor
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("后台监控", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(8.dp).background(healthColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(healthLabel(state.health), fontSize = 12.sp, color = healthColor)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (warn)
                    "后台调度看起来已经停了。可能是被系统省电策略掐掉，或任务被取消。" +
                        "点下面「重新排程」可以恢复，另外建议把本应用加入电池优化白名单。"
                else
                    "每天中午 12:00 前后自动检查一次，由系统 WorkManager 调度。" +
                        "实际执行时间可能因省电策略略有延迟，以上方「上次检查」时间为准。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.lastWorkerRunAt > 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "上次后台运行：${TimeFmt.full(state.lastWorkerRunAt)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = onReschedule,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("重新排程到最近的中午")
            }
        }
    }
}

private fun healthLabel(health: MonitorHealth): String = when (health) {
    MonitorHealth.OK -> "运行中"
    MonitorHealth.NEVER_RAN -> "尚未运行"
    MonitorHealth.STALE -> "已停摆"
    MonitorHealth.UNSCHEDULED -> "未排程"
}

@Composable
private fun PermissionCard(
    state: UiState,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("权限与后台设置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("通知权限", fontSize = 13.sp, modifier = Modifier.width(96.dp))
                Text(
                    text = if (state.notificationAllowed) "已开启" else "未开启",
                    fontSize = 13.sp,
                    color = if (state.notificationAllowed) RunningColor else NotInstalledColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        if (state.notificationAllowed) onOpenNotificationSettings()
                        else onRequestNotificationPermission()
                    }
                ) {
                    Text(if (state.notificationAllowed) "设置" else "去开启", fontSize = 12.sp)
                }
            }

            if (!state.notificationAllowed) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "未授权通知时，状态检测、桌面小组件仍然正常工作，只是状态变化时不会弹通知。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("电池优化", fontSize = 13.sp, modifier = Modifier.width(96.dp))
                Text(
                    text = if (state.batteryUnrestricted) "不受限制" else "可能被限制",
                    fontSize = 13.sp,
                    color = if (state.batteryUnrestricted) RunningColor else UnknownColor,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onOpenBatterySettings) {
                    Text("去设置", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "把本应用加入「不优化」名单，可减少后台检查被系统推迟的情况。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WidgetHintCard(onPinWidget: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("桌面小组件", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "不打开应用也能看到状态。小组件会显示目标是否已安装、" +
                    "上次检查时间，以及后台调度是否还活着。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onPinWidget) {
                Text("添加到桌面", fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "也可以长按桌面空白处 → 小组件 → AppWatch 手动拖出，尺寸随意拉。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun statusLabel(status: MonitorStatus?): String = when (status) {
    MonitorStatus.INSTALLED -> "已安装"
    MonitorStatus.NOT_INSTALLED -> "未安装"
    MonitorStatus.UNKNOWN -> "检查异常"
    null -> "尚未检查"
}

private fun statusColor(status: MonitorStatus?): Color = when (status) {
    MonitorStatus.INSTALLED -> InstalledColor
    MonitorStatus.NOT_INSTALLED -> NotInstalledColor
    MonitorStatus.UNKNOWN -> UnknownColor
    null -> UnknownColor
}
