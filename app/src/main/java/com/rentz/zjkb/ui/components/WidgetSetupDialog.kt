package com.rentz.zjkb.ui.components

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rentz.zjkb.R
import com.rentz.zjkb.ui.theme.CampusTheme
import com.rentz.zjkb.widget.TodayWidgetProvider

@Composable
fun WidgetSetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var feedback by remember { mutableStateOf<String?>(null) }
    val supported = remember {
        runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)
    }
    OverlayDialog(show = true, onDismissRequest = onDismiss, title = "把日程，放在桌面上") {
        Text("日程手账 · 默认 4 × 4，可自由调整大小", style = CampusTheme.textStyles.footnote1,
            color = CampusTheme.colorScheme.onSurfaceSecondary)
        AndroidView(
            factory = { RemoteViews(it.packageName, R.layout.widget_agenda_preview).apply(it, null) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).height(310.dp),
        )
        Text("前后切换日期，点「今天」恢复自动跟随日期。课程区可以上下滚动；添加多个时，每个分别记住浏览日期。",
            style = CampusTheme.textStyles.body2)
        feedback?.let {
            Text(it, modifier = Modifier.padding(top = 12.dp), color = CampusTheme.colorScheme.primary,
                style = CampusTheme.textStyles.body2)
        }
        if (supported) {
            CampusPrimaryButton(if (feedback == null) "请求添加到桌面" else "重新请求添加", Modifier.padding(top = 20.dp)) {
                val result = runCatching {
                    val extras = Bundle().apply {
                        putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, RemoteViews(context.packageName, R.layout.widget_agenda_preview))
                    }
                    AppWidgetManager.getInstance(context).requestPinAppWidget(
                        ComponentName(context, TodayWidgetProvider::class.java), extras, null,
                    )
                }
                feedback = when {
                    result.isFailure -> "桌面未能处理添加请求，请按下方步骤手动添加。"
                    result.getOrDefault(false) -> "已向桌面发送请求，请在系统弹窗中确认。若未出现弹窗，或取消了添加，请使用下方的手动添加方式。"
                    else -> "当前桌面未接受添加请求，请按下方步骤手动添加。"
                }
            }
        }
        Text(
            (if (supported) "也可以手动添加" else "当前桌面不支持应用内直接添加") +
                "：\n1. 长按桌面空白处，进入「小部件」或「添加工具」。\n2. 找到「华珠课表 · 日程手账」，拖到空白位置。\n3. 长按小部件并拖动边框，调整到合适大小。",
            modifier = Modifier.padding(top = 16.dp), style = CampusTheme.textStyles.footnote1,
            color = CampusTheme.colorScheme.onSurfaceSecondary,
        )
        Button(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
                .onFailure { feedback = "无法切换桌面，请使用系统 Home 手势返回桌面后手动添加。" }
        }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("回到桌面") }
    }
}
