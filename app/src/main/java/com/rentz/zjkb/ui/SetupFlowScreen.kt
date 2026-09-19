package com.rentz.zjkb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rentz.zjkb.R
import com.rentz.zjkb.domain.oobe.OobeFlow
import com.rentz.zjkb.domain.oobe.OobeStep
import com.rentz.zjkb.reminder.ReminderScheduler
import com.rentz.zjkb.ui.components.ErrorMessage
import com.rentz.zjkb.ui.components.PermissionChecklist
import com.rentz.zjkb.ui.components.ReminderControls
import com.rentz.zjkb.ui.components.rememberPermissionStates
import com.rentz.zjkb.ui.components.*
import com.rentz.zjkb.ui.theme.CampusTheme as MiuixTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.rentz.zjkb.ui.components.CampusBrand
import com.rentz.zjkb.ui.components.CampusCredentials
import com.rentz.zjkb.ui.components.CampusIntroArt

private const val OOBE_ANIM_MS = 300

@Composable
fun SetupFlowScreen(
    vm: AppViewModel,
    reminderScheduler: ReminderScheduler,
    startStep: OobeStep,
    onFinished: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(startStep) }
    var forward by remember { mutableStateOf(true) }
    var username by rememberSaveable { mutableStateOf(vm.credentialUsername.orEmpty()) }

    val backTo = OobeFlow.previous(step)
    BackHandler(enabled = backTo != null) {
        forward = false
        backTo?.let { step = it }
    }

    fun go(target: OobeStep, forwardDirection: Boolean) {
        forward = forwardDirection
        step = target
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Spacer(Modifier.height(24.dp))
            CampusBrand()
            Spacer(Modifier.height(24.dp))
            OobeFlow.progressOf(step)?.let { (index, total) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(total) { position ->
                        Box(Modifier.width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (position < index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("0$index / 0$total", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
                Spacer(Modifier.height(24.dp))
            }
            Text(titleOf(step), style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(subtitleOf(step), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary)
            if (step == OobeStep.Login) CampusIntroArt(Modifier.padding(top = 24.dp))
            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val enter = slideInHorizontally(tween(OOBE_ANIM_MS)) { if (forward) it else -it } +
                        fadeIn(tween(OOBE_ANIM_MS))
                    val exit = slideOutHorizontally(tween(OOBE_ANIM_MS)) { if (forward) -it else it } +
                        fadeOut(tween(OOBE_ANIM_MS))
                    enter togetherWith exit
                },
                label = "oobe",
            ) { current ->
                val back: (() -> Unit)? = OobeFlow.previous(current)?.let { previous ->
                    { go(previous, false) }
                }
                Column(Modifier.fillMaxWidth()) {
                    when (current) {
                        OobeStep.Login -> LoginStep(
                            vm = vm,
                            username = username,
                            onUsernameChange = { username = it },
                            onPrevious = back,
                            onNext = { go(OobeStep.Permissions, true) },
                        )
                        OobeStep.Permissions -> PermissionsStep(
                            reminderScheduler = reminderScheduler,
                            onPrevious = back,
                            onNext = { go(OobeStep.Reminders, true) },
                        )
                        OobeStep.Reminders -> RemindersStep(
                            vm = vm,
                            onPrevious = back,
                            onNext = { go(OobeStep.Done, true) },
                        )
                        OobeStep.Done -> DoneStep(
                            vm = vm,
                            reminderScheduler = reminderScheduler,
                            onPrevious = back,
                            onFinish = onFinished,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun titleOf(step: OobeStep): String = when (step) {
    OobeStep.Login -> "你的课表，\n也是生活的留白。"
    OobeStep.Permissions -> "把准时，\n交给一个小提醒。"
    OobeStep.Reminders -> "提前一点，\n从容出发。"
    OobeStep.Done -> "新的学期，\n从容开场。"
}

private fun subtitleOf(step: OobeStep): String = when (step) {
    OobeStep.Login -> "华南农业大学珠江学院"
    OobeStep.Permissions -> "允许课程提醒，在出发前轻轻提醒你。"
    OobeStep.Reminders -> "给自己留出走向教室的时间。"
    OobeStep.Done -> "课表与提醒，已经准备就绪。"
}

@Composable
private fun LoginStep(
    vm: AppViewModel,
    username: String,
    onUsernameChange: (String) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    var password by remember { mutableStateOf("") }
    var switching by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val alreadyLoggedIn = vm.hasCredentials && !switching

    fun submit() {
        if (username.isNotBlank() && password.isNotBlank() && !ui.syncing) {
            focus.clearFocus()
            vm.login(username.trim(), password) { password = ""; onNext() }
        }
    }

    if (alreadyLoggedIn) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.oobe_login_already, vm.credentialUsername.orEmpty()),
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { switching = true },
                ) {
                    Text(stringResource(R.string.oobe_login_switch))
                }
            }
        }
    } else {
        CampusCredentials(username, onUsernameChange, password, { password = it }, ::submit, !ui.syncing)
        Text("非官方客户端 · 凭据仅在本机加密保存", style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(top = 14.dp))
        if (ui.message != null) Spacer(Modifier.height(8.dp))
        ErrorMessage(ui.message, detail = ui.errorDetail, ok = ui.messageOk)
    }
    Spacer(Modifier.height(24.dp))
    OobeFooter(
        onPrevious = onPrevious,
        nextLabel = if (alreadyLoggedIn) "继续" else if (ui.syncing) "正在连接校园…" else "连接并导入课表",
        nextEnabled = alreadyLoggedIn || (username.isNotBlank() && password.isNotBlank()),
        busy = ui.syncing,
        onNext = { if (alreadyLoggedIn) onNext() else submit() },
    )
}

@Composable
private fun PermissionsStep(
    reminderScheduler: ReminderScheduler,
    onPrevious: (() -> Unit)?,
    onNext: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            PermissionChecklist(reminderScheduler, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.oobe_perm_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    OobeFooter(
        onPrevious = onPrevious,
        nextLabel = stringResource(R.string.setup_next),
        onNext = onNext,
    )
}

@Composable
private fun RemindersStep(
    vm: AppViewModel,
    onPrevious: (() -> Unit)?,
    onNext: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val enabled by vm.remindersEnabled.collectAsState()
            val minutes by vm.reminderMinutes.collectAsState()
            ReminderControls(
                enabled = enabled,
                minutes = minutes,
                onEnabledChange = { vm.setRemindersEnabled(it) },
                onMinutesChange = { vm.setReminderMinutes(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    OobeFooter(
        onPrevious = onPrevious,
        nextLabel = stringResource(R.string.setup_next),
        onNext = onNext,
    )
}

@Composable
private fun DoneStep(
    vm: AppViewModel,
    reminderScheduler: ReminderScheduler,
    onPrevious: (() -> Unit)?,
    onFinish: () -> Unit,
) {
    val permissions = rememberPermissionStates(reminderScheduler)
    val enabled by vm.remindersEnabled.collectAsState()
    val minutes by vm.reminderMinutes.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            SummaryRow(
                stringResource(R.string.oobe_summary_account),
                vm.credentialUsername ?: stringResource(R.string.oobe_summary_account_none),
            )
            SummaryRow(
                stringResource(R.string.oobe_summary_reminder),
                if (enabled) {
                    stringResource(R.string.oobe_summary_reminder_on, minutes)
                } else {
                    stringResource(R.string.oobe_summary_reminder_off)
                },
            )
            SummaryRow(
                stringResource(R.string.oobe_summary_notification),
                stringResource(
                    if (permissions.value.notifications) R.string.oobe_summary_allowed
                    else R.string.oobe_summary_denied
                ),
                warn = !permissions.value.notifications,
            )
            SummaryRow(
                stringResource(R.string.oobe_summary_exact_alarm),
                stringResource(
                    if (permissions.value.exactAlarm) R.string.oobe_summary_allowed
                    else R.string.oobe_summary_denied
                ),
                warn = !permissions.value.exactAlarm,
            )
            SummaryRow(
                stringResource(R.string.oobe_summary_battery),
                stringResource(
                    if (permissions.value.batteryWhitelisted) R.string.oobe_summary_joined
                    else R.string.oobe_summary_not_joined
                ),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    OobeFooter(
        onPrevious = onPrevious,
        nextLabel = stringResource(R.string.oobe_start),
        onNext = {
            vm.finishOobe()
            onFinish()
        },
    )
}

@Composable
private fun SummaryRow(label: String, value: String, warn: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
            color = if (warn) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun OobeFooter(
    onPrevious: (() -> Unit)?,
    nextLabel: String,
    onNext: () -> Unit,
    nextEnabled: Boolean = true,
    busy: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onPrevious != null) {
            Button(
                onClick = onPrevious,
                enabled = !busy,
                modifier = Modifier.weight(1f).height(52.dp),
                cornerRadius = 8.dp,
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.secondaryVariant,
                    contentColor = MiuixTheme.colorScheme.onSecondaryVariant,
                ),
            ) {
                Text(stringResource(R.string.oobe_previous))
            }
        }
        Button(
            onClick = onNext,
            enabled = nextEnabled && !busy,
            modifier = Modifier.weight(1f).height(52.dp),
            cornerRadius = 8.dp,
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.primary,
                contentColor = MiuixTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(nextLabel)
        }
    }
}
