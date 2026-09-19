package com.rentz.zjkb.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rentz.zjkb.R
import com.rentz.zjkb.reminder.ReminderScheduler
import com.rentz.zjkb.ui.theme.CampusTheme as MiuixTheme

/** 提醒相关三项权限的当前状态。 */
data class PermissionStates(
    val notifications: Boolean,
    val exactAlarm: Boolean,
    /** true = 已加入电池优化白名单（后台可稳定运行）。 */
    val batteryWhitelisted: Boolean,
)

private val REMINDER_MINUTES = listOf(5, 10, 15, 20, 30)

/** 读取三项权限状态；非 Composable，便于在回调中主动刷新。 */
fun readPermissionStates(context: Context, scheduler: ReminderScheduler): PermissionStates = PermissionStates(
    notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED,
    exactAlarm = scheduler.canScheduleExact(),
    batteryWhitelisted = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName),
)

/**
 * 权限状态的可观察快照：ON_RESUME 时重新读取，从系统设置返回后立即刷新。
 */
@Composable
fun rememberPermissionStates(scheduler: ReminderScheduler): MutableState<PermissionStates> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(readPermissionStates(context, scheduler)) }
    DisposableEffect(lifecycleOwner, context, scheduler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = readPermissionStates(context, scheduler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

/** 三行权限清单：状态和授权操作，向导与设置页共用。 */
@Composable
fun PermissionChecklist(scheduler: ReminderScheduler, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val states = rememberPermissionStates(scheduler)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        states.value = readPermissionStates(context, scheduler)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionRow(
            title = stringResource(R.string.perm_notification),
            granted = states.value.notifications,
            onAction = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
        PermissionRow(
            title = stringResource(R.string.perm_exact_alarm),
            granted = states.value.exactAlarm,
            onAction = { scheduler.requestExactPermission() },
        )
        PermissionRow(
            title = stringResource(R.string.perm_battery),
            granted = states.value.batteryWhitelisted,
            optional = true,
            onAction = { requestIgnoreBatteryOptimizations(context) },
        )
    }
}

/** 上课提醒开关和提前分钟数。 */
@Composable
fun ReminderControls(
    enabled: Boolean,
    minutes: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_enable_reminders), Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onEnabledChange, modifier = Modifier.semantics { contentDescription = "上课提醒开关" })
        }
        Column {
            Text(
                stringResource(R.string.settings_reminder_minutes_label),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_MINUTES.forEach { min ->
                    val selected = minutes == min
                    Button(
                        onClick = { onMinutesChange(min) },
                        modifier = Modifier.weight(1f),
                        cornerRadius = 5.dp,
                        insideMargin = PaddingValues(horizontal = 2.dp, vertical = 10.dp),
                        colors = if (selected) {
                            ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.primary,
                                contentColor = MiuixTheme.colorScheme.onPrimary,
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.secondaryVariant,
                                contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                            )
                        },
                    ) {
                        Text("$min 分", style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.subtitle)
            Text(
                stringResource(
                    when {
                        granted -> R.string.perm_status_granted
                        optional -> R.string.perm_status_optional
                        else -> R.string.perm_status_missing
                    }
                ),
                style = MiuixTheme.textStyles.footnote1,
                color = if (granted) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                },
            )
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onAction,
            ) {
                Text(stringResource(R.string.perm_grant))
            }
        }
    }
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData("package:${context.packageName}".toUri())
    val ok = runCatching { context.startActivity(direct) }.isSuccess
    if (!ok) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
