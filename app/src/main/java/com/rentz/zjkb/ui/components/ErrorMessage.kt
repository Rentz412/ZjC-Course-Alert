package com.rentz.zjkb.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rentz.zjkb.R
import com.rentz.zjkb.ui.theme.CampusTheme as MiuixTheme

/**
 * 统一的提示文本：默认错误色，[ok] == true 时用主色。
 */
@Composable
fun ErrorMessage(
    message: String?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    ok: Boolean? = null,
) {
    if (message == null) return
    val context = LocalContext.current
    Column(modifier) {
        Text(
            text = message,
            style = MiuixTheme.textStyles.footnote1,
            color = if (ok == true) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
        )
        if (detail != null) {
            TextButton(
                text = stringResource(R.string.common_copy_error_detail),
                onClick = { context.copyToClipboard(detail) },
            )
        }
    }
}

private fun Context.copyToClipboard(text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("zjkb-error-detail", text))
}
