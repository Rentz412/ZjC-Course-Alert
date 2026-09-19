package com.rentz.zjkb.ui.components

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.rentz.zjkb.ui.components.CampusIcons as MiuixIcons
import com.rentz.zjkb.ui.theme.CampusTheme as MiuixTheme
import com.rentz.zjkb.ui.theme.CampusDisplay

@Composable
fun rememberCampusNow(): State<LocalDateTime> {
    val owner = LocalLifecycleOwner.current
    return produceState(LocalDateTime.now(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                value = LocalDateTime.now()
                delay(1000)
            }
        }
    }
}

@Composable
fun CampusBrand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(MiuixTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MiuixIcons.CalendarFold, null, Modifier.size(21.dp), tint = MiuixTheme.colorScheme.onPrimaryContainer)
        }
        Text("ZjC", style = MiuixTheme.textStyles.title3)
        Text("华珠课表", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary)
    }
}

@Composable
fun CampusAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(icon, label, Modifier.size(22.dp), tint = MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
fun CampusPrimaryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        cornerRadius = 8.dp,
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.primary,
            contentColor = MiuixTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(label, style = MiuixTheme.textStyles.button)
    }
}

@Composable
fun CampusSectionTitle(title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MiuixTheme.textStyles.subtitle, modifier = Modifier.weight(1f))
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(subtitle, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
    }
}

@Composable
fun CampusTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.clip(RoundedCornerShape(4.dp)).background(MiuixTheme.colorScheme.primaryContainer)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
fun CampusEmpty(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(MiuixIcons.Months, null, Modifier.size(42.dp), tint = MiuixTheme.colorScheme.primary)
        Text(title, style = MiuixTheme.textStyles.title4)
        Text(message, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary)
    }
}

@Composable
fun CampusCredentials(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("学号", style = MiuixTheme.textStyles.footnote1)
        TextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "输入你的学号",
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
        )
        Text("教务密码", style = MiuixTheme.textStyles.footnote1, modifier = Modifier.padding(top = 4.dp))
        Box(Modifier.fillMaxWidth()) {
            TextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "与喜鹊儿账户密码一致",
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                trailingIcon = {
                    CampusAction(if (visible) MiuixIcons.Hide else MiuixIcons.Show, if (visible) "隐藏密码" else "显示密码") { visible = !visible }
                },
            )
        }
        if (password.any { it in '\uFF01'..'\uFF5E' || it == '\u3000' }) {
            Text("密码中含有全角字符，请检查输入法。", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error)
        }
    }
}

@Composable
fun CampusIntroArt(modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Box(modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(8.dp)).background(colors.primaryContainer)) {
        Column(
            Modifier.offset(20.dp, 22.dp).width(210.dp).rotate(-7f)
                .clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainer).padding(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("新学期", style = MiuixTheme.textStyles.title3.copy(fontFamily = CampusDisplay))
                Icon(MiuixIcons.Months, null, Modifier.size(18.dp), tint = colors.primary)
            }
            Spacer(Modifier.height(12.dp))
            repeat(2) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { column ->
                        Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(4.dp))
                            .background(if ((row + column) % 3 == 0) colors.primaryContainer else colors.surfaceVariant))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).offset((-16).dp, (-18).dp).width(150.dp).rotate(6f)
                .clip(RoundedCornerShape(8.dp)).background(colors.onSurface).padding(16.dp),
        ) {
            Text("下一节", style = MiuixTheme.textStyles.footnote2, color = colors.surface)
            Text("从容出发", style = MiuixTheme.textStyles.subtitle, color = colors.surface, modifier = Modifier.padding(top = 7.dp))
            Text("课表与提醒，一起就绪", style = MiuixTheme.textStyles.footnote2, color = colors.surface.copy(alpha = .75f), modifier = Modifier.padding(top = 6.dp))
        }
    }
}
