package com.rentz.zjkb.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rentz.zjkb.BuildConfig
import com.rentz.zjkb.R
import com.rentz.zjkb.ZjkbApp
import com.rentz.zjkb.reminder.LiveUpdateNotifier
import com.rentz.zjkb.reminder.ReminderScheduler
import com.rentz.zjkb.ui.components.ErrorMessage
import com.rentz.zjkb.ui.components.PermissionChecklist
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.rentz.zjkb.ui.components.*
import com.rentz.zjkb.ui.components.CampusIcons as MiuixIcons
import com.rentz.zjkb.ui.theme.CampusTheme as MiuixTheme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentz.zjkb.ui.components.CampusAction
import com.rentz.zjkb.ui.components.CampusPrimaryButton
import com.rentz.zjkb.ui.components.CampusSectionTitle
import com.rentz.zjkb.ui.components.ReminderControls
import com.rentz.zjkb.ui.components.rememberPermissionStates

@Composable
fun SettingsScreen(reminderScheduler: ReminderScheduler, vm: AppViewModel, onRerunOobe: () -> Unit) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val enabled by vm.remindersEnabled.collectAsStateWithLifecycle()
    val minutes by vm.reminderMinutes.collectAsStateWithLifecycle()
    val resting by vm.resting.collectAsStateWithLifecycle()
    val startMonday by vm.semesterStart.collectAsStateWithLifecycle()
    val permissions by rememberPermissionStates(reminderScheduler)
    var dialog by rememberSaveable { mutableStateOf("") }
    val colors = MiuixTheme.colorScheme
    val username = vm.credentialUsername.orEmpty()
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) vm.exportIcs(uri)
    }
    val uriHandler = LocalUriHandler.current
    val repoUrl = stringResource(R.string.settings_about_repo_url)
    val exportUnavailableMessage = stringResource(R.string.msg_export_no_app)
    val upstreamUrl = stringResource(R.string.settings_about_upstream_url)
    val userProfile by vm.userProfile.collectAsState()
    val displayName = userProfile.xm.ifBlank { "华珠同学" }
    val displaySchool = userProfile.xxmc.ifBlank { "华南农业大学珠江学院" }

    Scaffold(topBar = {
        TopAppBar(title = "我的", actions = {
            CampusAction(MiuixIcons.Info, "关于华珠课表") { dialog = "about" }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { dialog = "account" }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(MiuixIcons.Graduation, null, Modifier.size(28.dp), tint = colors.onPrimaryContainer)
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(displayName, style = MiuixTheme.textStyles.title3)
                    Text(displaySchool, style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 6.dp))
                    Text(if (username.isNotBlank()) "学号 $username" else "连接校园账户", style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 3.dp))
                }
                Icon(MiuixIcons.ChevronRight, "修改账户", Modifier.size(16.dp), tint = colors.onSurfaceSecondary)
            }
            Row(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.primaryContainer).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (ui.syncing) "正在同步课表" else "课表与校园日常", style = MiuixTheme.textStyles.footnote1, color = colors.onPrimaryContainer)
                    val synced = vm.settings.lastSyncAt
                    Text(if (synced > 0) "上次同步 " + java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(synced)) else "同步后可离线查看", style = MiuixTheme.textStyles.footnote2, color = colors.onPrimaryContainer, modifier = Modifier.padding(top = 3.dp))
                }
                CampusAction(MiuixIcons.Refresh, "同步课表", enabled = !ui.syncing) { vm.sync() }
            }
            if (ui.message != null) ErrorMessage(ui.message, detail = ui.errorDetail, ok = ui.messageOk, modifier = Modifier.padding(top = 12.dp))
            Text("上课提醒", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
            CampusSettingsRow(MiuixIcons.Alarm, "上课提醒", if (enabled) "把时间留给从容" else "已暂停全部课程提醒") {
                Switch(checked = enabled, onCheckedChange = vm::setRemindersEnabled, modifier = Modifier.semantics { contentDescription = "上课提醒开关" })
            }
            if (enabled) CampusSettingsRow(MiuixIcons.Timer, "提前提醒", value = "$minutes 分钟", onClick = { dialog = "reminder" })
            CampusSettingsRow(MiuixIcons.VolumeOff, "今日休息", "明天自动恢复，不影响课表") {
                Switch(checked = resting, onCheckedChange = vm::setResting, modifier = Modifier.semantics { contentDescription = "今日休息开关" })
            }
            Text("课表与数据", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            CampusSettingsRow(MiuixIcons.Months, "学期与周次", value = "第 ${currentWeek(startMonday)} 周", onClick = { dialog = "term" })
            CampusSettingsRow(MiuixIcons.Panels, "桌面小部件", "日程手账 · 日期切换与完整课程", onClick = { dialog = "widget" })
            CampusSettingsRow(MiuixIcons.Download, "导出到日历", value = "本学期", onClick = { createDoc.launch(vm.suggestedIcsFileName) })
            CampusSettingsRow(MiuixIcons.Share, "分享课表", onClick = {
                vm.shareIcs { intent ->
                    if (runCatching { context.startActivity(Intent.createChooser(intent, "分享课表日历")) }.isFailure) {
                        vm.showExportMessage(exportUnavailableMessage)
                    }
                }
            })
            if (ui.exportMessage != null) ErrorMessage(ui.exportMessage, ok = ui.exportOk, modifier = Modifier.padding(vertical = 8.dp))
            CampusSettingsRow(MiuixIcons.Shield, "提醒权限", value = if (permissions.notifications && permissions.exactAlarm) "已就绪" else "待设置", onClick = { dialog = "permissions" })
            Text("工具与关于", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            CampusSettingsRow(MiuixIcons.Refresh, "从教务系统校准周次", onClick = { if (!ui.syncing) vm.calibrateSemesterStartFromServer() })
            CampusSettingsRow(MiuixIcons.Alarm, "测试常驻提醒", onClick = { LiveUpdateNotifier.showTest(context) })
            CampusSettingsRow(MiuixIcons.Months, "重新运行设置向导", onClick = onRerunOobe)
            Card(Modifier.fillMaxWidth().padding(top = 12.dp), cornerRadius = 8.dp) { UpdatePreferenceSection(vm) }
            Text("ZjC · 让每一天，井然有序", style = MiuixTheme.textStyles.footnote2, color = colors.onSurfaceSecondary, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 28.dp))
        }
        if (dialog == "account") AccountEditDialog(
            currentUsername = username,
            hasSavedPassword = ZjkbApp.instance.creds.password != null,
            onDismiss = { dialog = "" },
            onSave = { user, password ->
                dialog = ""
                vm.saveCredentialsAndLogin(user, password.ifBlank { ZjkbApp.instance.creds.password.orEmpty() })
            },
        )
        if (dialog == "term") TermStartDateDialog(startMonday, onDismiss = { dialog = "" }, onConfirm = { vm.setSemesterStartMonday(it); dialog = "" })
        if (dialog == "widget") WidgetSetupDialog(onDismiss = { dialog = "" })
        OverlayDialog(show = dialog == "reminder", onDismissRequest = { dialog = "" }, title = "提前一点，从容出发") {
            ReminderControls(enabled, minutes, vm::setRemindersEnabled, vm::setReminderMinutes)
            CampusPrimaryButton("完成", Modifier.padding(top = 24.dp)) { dialog = "" }
        }
        OverlayDialog(show = dialog == "permissions", onDismissRequest = { dialog = "" }, title = "提醒权限") {
            PermissionChecklist(reminderScheduler)
            CampusPrimaryButton("完成", Modifier.padding(top = 24.dp)) { dialog = "" }
        }
        OverlayDialog(show = dialog == "about", onDismissRequest = { dialog = "" }, title = "关于华珠课表") {
            Text("为华珠同学的每一天，留一点从容。", style = MiuixTheme.textStyles.body1)
            Text("非官方客户端，与学校及金智教育无关联。", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 12.dp))
            Text("版本 ${BuildConfig.VERSION_NAME} · Apache-2.0", style = MiuixTheme.textStyles.footnote1, color = colors.onSurfaceSecondary, modifier = Modifier.padding(top = 8.dp))
            CampusPrimaryButton("开源主仓库", Modifier.padding(top = 24.dp)) { uriHandler.openUri(repoUrl) }
            Button(onClick = { uriHandler.openUri(upstreamUrl) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("上游开源项目") }
        }
    }
}

@Composable
private fun CampusSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    value: String = "",
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MiuixTheme.colorScheme.onSurfaceSecondary)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MiuixTheme.textStyles.body1)
            if (subtitle.isNotBlank()) Text(subtitle, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(top = 4.dp))
        }
        if (trailing != null) trailing() else {
            if (value.isNotBlank()) Text(value, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary)
            if (onClick != null) Icon(MiuixIcons.ChevronRight, null, Modifier.padding(start = 6.dp).size(14.dp), tint = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
    }
}

@Composable
private fun AccountEditDialog(
    currentUsername: String,
    hasSavedPassword: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf(currentUsername) }
    var password by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "教务账号与密码",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.login_student_id),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = if (hasSavedPassword && username.trim() == currentUsername) "留空则保持原密码" else stringResource(R.string.login_password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                err?.let {
                    Text(text = it, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.secondaryVariant,
                            contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                        ),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = {
                            val u = username.trim()
                            if (u.isBlank()) {
                                err = "学号不能为空"
                            } else if (password.isBlank() && (!hasSavedPassword || u != currentUsername)) {
                                err = "请输入密码"
                            } else {
                                onSave(u, password)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.settings_save_and_login))
                    }
                }
            }
        }
    )
}

@Composable
private fun TermStartDateDialog(
    current: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var text by remember { mutableStateOf(current.format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var isError by remember { mutableStateOf(false) }

    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "设置学期开学首周周一",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextField(
                    value = text,
                    onValueChange = {
                        text = it
                        isError = runCatching { LocalDate.parse(it).dayOfWeek == java.time.DayOfWeek.MONDAY }.getOrDefault(false).not()
                    },
                    label = "YYYY-MM-DD（如 2026-08-31）",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (isError) {
                    Text(
                        text = "请填写周一日期，例如 2026-08-31",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.secondaryVariant,
                            contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                        ),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = {
                            val d = runCatching { LocalDate.parse(text) }.getOrNull()
                            if (d != null && d.dayOfWeek == java.time.DayOfWeek.MONDAY) onConfirm(d) else isError = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.settings_save))
                    }
                }
            }
        }
    )
}

@Composable
private fun UpdatePreferenceSection(vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.updateState.collectAsState()

    when (val s = state) {
        is AppViewModel.UpdateState.Idle -> {
            ArrowPreference(
                title = stringResource(R.string.settings_update_check),
                summary = "当前已安装 v${BuildConfig.VERSION_NAME}，点击在线检测",
                onClick = { vm.checkForUpdate() },
            )
        }
        is AppViewModel.UpdateState.Checking -> {
            ArrowPreference(
                title = stringResource(R.string.settings_update_checking),
                summary = "正在连接 GitHub Releases 检查新包...",
                onClick = null,
            )
        }
        is AppViewModel.UpdateState.UpToDate -> {
            ArrowPreference(
                title = stringResource(R.string.settings_update_uptodate, s.version),
                summary = "已是最新发布版，点击可重新检查",
                onClick = { vm.checkForUpdate() },
            )
        }
        is AppViewModel.UpdateState.Available -> {
            var showNotes by remember { mutableStateOf(false) }
            ArrowPreference(
                title = stringResource(R.string.settings_update_available, s.release.tagName),
                summary = "发现新版发布，点击查阅更新日志与下载",
                onClick = { showNotes = true },
            )
            if (showNotes) {
                ReleaseNotesDialog(
                    tagName = s.release.tagName,
                    body = s.release.body,
                    onDismiss = { showNotes = false },
                    onDownload = {
                        showNotes = false
                        vm.downloadUpdate()
                    },
                )
            }
        }
        is AppViewModel.UpdateState.Downloading -> {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (s.percent >= 0) stringResource(R.string.settings_update_downloading, s.percent)
                    else stringResource(R.string.settings_update_downloading_unknown),
                    style = MiuixTheme.textStyles.body2,
                )
                LinearProgressIndicator(
                    progress = if (s.percent >= 0) s.percent / 100f else 0.5f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.cancelDownload() },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.secondaryVariant,
                        contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                    ),
                ) {
                    Text(stringResource(R.string.settings_update_cancel))
                }
            }
        }
        is AppViewModel.UpdateState.Ready -> {
            ArrowPreference(
                title = stringResource(R.string.settings_update_ready, s.tag),
                summary = "新安装包已下载完毕，点击立即安装更新",
                onClick = { vm.installUpdate() },
            )
        }
        is AppViewModel.UpdateState.NeedInstallPermission -> {
            ArrowPreference(
                title = stringResource(R.string.settings_update_need_permission),
                summary = "需要授予安装外部应用权限，点击授权",
                onClick = {
                    vm.installPermissionIntent { intent ->
                        runCatching { context.startActivity(intent) }
                    }
                },
            )
        }
        is AppViewModel.UpdateState.Failed -> {
            ArrowPreference(
                title = "检查更新失败",
                summary = s.message + "，点击重试",
                onClick = { vm.checkForUpdate() },
            )
        }
    }
}

@Composable
private fun ReleaseNotesDialog(
    tagName: String,
    body: String?,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_update_dialog_title, tagName),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = body?.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_update_no_notes),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.secondaryVariant,
                            contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                        ),
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(stringResource(R.string.settings_update_download))
                    }
                }
            }
        },
    )
}
