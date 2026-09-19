package com.rentz.zjkb.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rentz.zjkb.ui.components.CampusBrand
import com.rentz.zjkb.ui.components.CampusCredentials
import com.rentz.zjkb.ui.components.CampusIntroArt
import com.rentz.zjkb.ui.components.CampusPrimaryButton
import com.rentz.zjkb.ui.components.ErrorMessage
import com.rentz.zjkb.ui.components.Scaffold
import com.rentz.zjkb.ui.components.Text
import com.rentz.zjkb.ui.theme.CampusTheme as MiuixTheme

@Composable
fun LoginScreen(vm: AppViewModel, onLoggedIn: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    fun submit() {
        if (username.isNotBlank() && password.isNotBlank() && !ui.syncing) {
            focus.clearFocus()
            vm.login(username.trim(), password) { password = ""; onLoggedIn() }
        }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
            CampusBrand()
            Text("你的课表，\n也是生活的留白。", style = MiuixTheme.textStyles.title1, modifier = Modifier.padding(top = 32.dp))
            Text("华南农业大学珠江学院", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(top = 12.dp))
            CampusIntroArt(Modifier.padding(vertical = 24.dp))
            CampusCredentials(username, { username = it }, password, { password = it }, ::submit, !ui.syncing)
            Spacer(Modifier.height(24.dp))
            CampusPrimaryButton(if (ui.syncing) "正在连接校园…" else "连接并导入课表", enabled = !ui.syncing && username.isNotBlank() && password.isNotBlank(), onClick = ::submit)
            ErrorMessage(ui.message, detail = ui.errorDetail, ok = ui.messageOk, modifier = Modifier.padding(top = 12.dp))
            Text("非官方客户端 · 凭据仅在本机加密保存", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}
