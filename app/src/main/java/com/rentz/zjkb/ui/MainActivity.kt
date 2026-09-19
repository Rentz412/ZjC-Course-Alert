package com.rentz.zjkb.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rentz.zjkb.ZjkbApp
import com.rentz.zjkb.domain.oobe.OobeStep
import com.rentz.zjkb.sync.SyncWorker
import com.rentz.zjkb.ui.theme.GbuCaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncWorker.enqueue(this)
        val app = ZjkbApp.instance
        setContent {
            GbuCaTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
                // null = 不在向导中；非 null = 向导入口步（老用户已登录时为 null）
                var oobeStart by rememberSaveable {
                    mutableStateOf(if (vm.needsOobe) vm.oobeStartStep() else null)
                }
                var loggedIn by rememberSaveable { mutableStateOf(app.creds.username != null) }
                when (val start = oobeStart) {
                    null -> if (loggedIn) {
                        AppNavHost(
                            vm = vm,
                            onRerunOobe = { oobeStart = OobeStep.Login },
                            reminderScheduler = app.reminderScheduler,
                        )
                    } else {
                        LoginScreen(
                            vm = vm,
                            onLoggedIn = { loggedIn = true },
                        )
                    }
                    else -> SetupFlowScreen(
                        vm = vm,
                        reminderScheduler = app.reminderScheduler,
                        startStep = start,
                        onFinished = {
                            oobeStart = null
                            loggedIn = app.creds.username != null
                        },
                    )
                }
            }
        }
    }
}
