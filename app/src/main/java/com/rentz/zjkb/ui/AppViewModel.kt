package com.rentz.zjkb.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.rentz.zjkb.BuildConfig
import com.rentz.zjkb.ZjkbApp
import com.rentz.zjkb.R
import com.rentz.zjkb.data.GbuException
import com.rentz.zjkb.data.remote.xq.XqSemesterRules
import com.rentz.zjkb.data.repo.CourseRepository
import com.rentz.zjkb.domain.logic.ScheduleLogic
import com.rentz.zjkb.domain.model.TermData
import com.rentz.zjkb.domain.oobe.OobeFlow
import com.rentz.zjkb.domain.oobe.OobeStep
import com.rentz.zjkb.update.AppUpdateChecks
import com.rentz.zjkb.update.AppUpdater
import com.rentz.zjkb.update.GitHubAsset
import com.rentz.zjkb.update.GitHubRelease
import java.io.File

class AppViewModel : ViewModel() {

    private val app = ZjkbApp.instance
    private val repo: CourseRepository = app.repo

    val settings = app.settings

    /** 设置项的可观察快照：SettingsStore 为普通持久化对象，UI 经由 StateFlow 响应变更。 */
    private val _remindersEnabled = MutableStateFlow(app.settings.remindersEnabled)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled
    private val _reminderMinutes = MutableStateFlow(app.settings.reminderMinutes)
    val reminderMinutes: StateFlow<Int> = _reminderMinutes

    fun setRemindersEnabled(on: Boolean) {
        settings.remindersEnabled = on
        _remindersEnabled.value = on
        if (on) app.reminderScheduler.rescheduleAsync() else app.reminderScheduler.cancelAll()
    }

    fun setReminderMinutes(min: Int) {
        settings.reminderMinutes = min
        _reminderMinutes.value = min
        if (settings.remindersEnabled) app.reminderScheduler.rescheduleAsync()
    }

    /** 「今日休息」是否对今天生效；跨零点或从后台返回时由 [refreshResting] 重新判定。 */
    private val _resting = MutableStateFlow(settings.isRestingToday())
    val resting: StateFlow<Boolean> = _resting

    /** 切换「今日休息」：开启即取消当天全部提醒；关闭即重排。仅当日生效。 */
    fun setResting(on: Boolean) {
        settings.restDay = if (on) java.time.LocalDate.now().toEpochDay() else null
        _resting.value = on
        if (on) app.reminderScheduler.cancelAll() else app.reminderScheduler.rescheduleAsync()
        viewModelScope.launch { com.rentz.zjkb.widget.TodayWidgetProvider.refreshAll(app) }
    }

    fun refreshResting() {
        _resting.value = settings.isRestingToday()
    }

    private val _selectedXnxq = MutableStateFlow(app.settings.selectedXnxq)
    val selectedXnxq: StateFlow<String?> = _selectedXnxq
    val xnxq: String get() = _selectedXnxq.value ?: XqSemesterRules.fallbackDm()

    @OptIn(ExperimentalCoroutinesApi::class)
    val termData: StateFlow<TermData> = _selectedXnxq.flatMapLatest { selected ->
        repo.observeTermData(selected ?: XqSemesterRules.fallbackDm())
            .onStart { emit(TermData(emptyList(), emptyList())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TermData(emptyList(), emptyList()))

    private val _semesterStart = MutableStateFlow(settings.semesterStartMonday(xnxq))
    val semesterStart: StateFlow<java.time.LocalDate> = _semesterStart
    val semesterStartMonday get() = _semesterStart.value
    val semesterOptions = MutableStateFlow(listOf(xnxq) + XqSemesterRules.earlierSemesters(listOf(xnxq)))

    fun loadSemesters() {
        viewModelScope.launch {
            val codes = runCatchingNonCancellation { repo.semesters().map { it.dm } }.getOrNull()
                ?.takeIf { it.isNotEmpty() } ?: return@launch
            semesterOptions.value = (codes + xnxq + XqSemesterRules.earlierSemesters(codes)).distinct()
        }
    }

    data class UiState(
        val syncing: Boolean = false,
        val message: String? = null,
        /** 失败时的技术细节（可复制上报）；成功消息或非接口失败为 null。 */
        val errorDetail: String? = null,
        val messageOk: Boolean? = null,
        val snackbar: String? = null,
        /** 导出到日历的独立提示（不与同步/校准消息串台）。 */
        val exportMessage: String? = null,
        val exportOk: Boolean? = null,
    )

    val ui = MutableStateFlow(UiState())

    fun sync() {
        if (ui.value.syncing) return
        viewModelScope.launch {
            ui.value = ui.value.copy(syncing = true, message = null, errorDetail = null, messageOk = null)
            val result = runCatchingNonCancellation { repo.sync(xnxq) }
            val e = result.exceptionOrNull()
            _semesterStart.value = settings.semesterStartMonday(xnxq)
            ui.value = if (e == null) {
                com.rentz.zjkb.widget.TodayWidgetProvider.refreshAll(app)
                val msg = app.getString(R.string.msg_synced_courses, result.getOrThrow().courseCount)
                ui.value.copy(syncing = false, message = msg, snackbar = msg, messageOk = true)
            } else {
                ui.value.copy(
                    syncing = false,
                    message = friendlyError(e),
                    snackbar = friendlyError(e),
                    errorDetail = errorDetailOf(e),
                    messageOk = false,
                )
            }
            app.reminderScheduler.rescheduleAsync()
        }
    }

    /** 首次登录：先认证，成功才保存凭据并同步课表。 */
    fun login(u: String, p: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            ui.value = ui.value.copy(syncing = true, message = null, errorDetail = null, messageOk = null)
            val result = runCatchingNonCancellation {
                repo.login(u, p)
                app.creds.save(u, p)
                repo.sync(xnxq)
            }
            val e = result.exceptionOrNull()
            _semesterStart.value = settings.semesterStartMonday(xnxq)
            ui.value = ui.value.copy(
                syncing = false,
                message = e?.let { friendlyError(it) },
                errorDetail = errorDetailOf(e),
                messageOk = if (e == null) null else false,
            )
            if (e == null) {
                com.rentz.zjkb.widget.TodayWidgetProvider.refreshAll(app)
                app.reminderScheduler.rescheduleAsync()
                onSuccess()
            }
        }
    }

    /** runCatching，但协程取消原样抛出。 */
    private inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }

    /** 异常 → 用户可读文案；永不为空、永不出现 "?"。 */
    private fun friendlyError(e: Throwable): String = when (e) {
        is GbuException.BadCredentials -> app.getString(R.string.error_login_failed, e.message0)
        is GbuException.NeedVerification -> e.message0
        is GbuException.SessionExpired -> app.getString(R.string.error_session_expired)
        is GbuException.Network -> app.getString(R.string.error_network)
        is GbuException.ApiError -> e.summary
        is java.io.IOException -> app.getString(R.string.error_network)
        else -> app.getString(
            R.string.error_sync_failed,
            e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
        )
    }

    /** 失败详情（含版本与时间），供「复制错误详情」。 */
    private fun errorDetailOf(e: Throwable?): String? =
        (e as? GbuException.ApiError)?.let {
            "华珠课表 ${BuildConfig.VERSION_NAME}\n${it.detail}\n时间：${
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                    .format(java.util.Date())
            }"
        }

    /** 设置页「保存并登录」：先登录成功才覆盖已存凭据，再同步。 */
    fun saveCredentialsAndLogin(u: String, p: String) {
        viewModelScope.launch {
            ui.value = ui.value.copy(syncing = true, message = null, errorDetail = null, messageOk = null)
            val auth = runCatchingNonCancellation {
                repo.login(u, p)
                app.creds.save(u, p)
            }
            val authError = auth.exceptionOrNull()
            if (authError != null) {
                ui.value = ui.value.copy(
                    syncing = false,
                    message = friendlyError(authError),
                    errorDetail = errorDetailOf(authError),
                    messageOk = false,
                )
                return@launch
            }
            val sync = runCatchingNonCancellation { repo.sync(xnxq) }
            val syncError = sync.exceptionOrNull()
            _semesterStart.value = settings.semesterStartMonday(xnxq)
            ui.value = ui.value.copy(
                syncing = false,
                message = when {
                    syncError == null ->
                        app.getString(R.string.msg_credentials_saved, sync.getOrThrow().courseCount)
                    else ->
                        app.getString(R.string.msg_credentials_saved_sync_failed, friendlyError(syncError))
                },
                errorDetail = errorDetailOf(syncError),
                messageOk = syncError == null,
            )
            com.rentz.zjkb.widget.TodayWidgetProvider.refreshAll(app)
            app.reminderScheduler.rescheduleAsync()
        }
    }

    fun clearSnackbar() {
        ui.value = ui.value.copy(snackbar = null)
    }

    fun setSemesterStartMonday(date: java.time.LocalDate) {
        settings.setSemesterStartMonday(xnxq, date)
        _semesterStart.value = date
        app.reminderScheduler.rescheduleAsync()
        viewModelScope.launch { com.rentz.zjkb.widget.TodayWidgetProvider.refreshAll(app) }
    }

    fun selectSemester(dm: String) {
        if (ui.value.syncing || dm == xnxq) return
        settings.selectedXnxq = dm
        _selectedXnxq.value = dm
        _semesterStart.value = settings.semesterStartMonday(dm)
        viewModelScope.launch { com.rentz.zjkb.widget.TodayWidgetProvider.refreshAll(app) }
        sync()
    }

    fun calibrateSemesterStartFromServer() {
        viewModelScope.launch {
            ui.value = ui.value.copy(syncing = true, message = null, errorDetail = null, messageOk = null)
            val result = runCatchingNonCancellation { repo.calibrateSemesterStart(xnxq, force = true) }
            val date = result.getOrNull()
            val e = result.exceptionOrNull()
            ui.value = ui.value.copy(
                syncing = false,
                message = when {
                    date != null -> app.getString(R.string.msg_calibrate_success, date)
                    e != null -> friendlyError(e)
                    else -> app.getString(R.string.msg_calibrate_unavailable)
                },
                errorDetail = errorDetailOf(e),
                messageOk = date != null,
            )
            if (date != null) {
                _semesterStart.value = settings.semesterStartMonday(xnxq)
                app.reminderScheduler.rescheduleAsync()
                com.rentz.zjkb.widget.TodayWidgetProvider.refreshAll(app)
            }
        }
    }

    fun updateGridFromData() {
        // TimeGrid 在同步时已由 sjhjinfo 更新
    }

    // ---- 导出到日历 ----

    val suggestedIcsFileName: String get() = app.icsExport.suggestedFileName(xnxq)

    fun exportIcs(uri: Uri) {
        viewModelScope.launch {
            ui.value = ui.value.copy(exportMessage = null, exportOk = null)
            val result = runCatchingNonCancellation {
                val content = app.icsExport.build(xnxq) ?: throw NoCourses
                app.icsExport.writeToUri(uri, content)
            }
            val e = result.exceptionOrNull()
            ui.value = ui.value.copy(
                exportMessage = when {
                    e == null -> app.getString(R.string.msg_export_ok)
                    e === NoCourses -> app.getString(R.string.msg_export_empty)
                    else -> exportFailed(e)
                },
                exportOk = e == null,
            )
        }
    }

    fun shareIcs(onIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            ui.value = ui.value.copy(exportMessage = null, exportOk = null)
            val result = runCatchingNonCancellation {
                val content = app.icsExport.build(xnxq) ?: throw NoCourses
                app.icsExport.shareIntent(content, app.icsExport.suggestedFileName(xnxq))
            }
            val intent = result.getOrNull()
            if (intent != null) {
                onIntent(intent)
            } else {
                val e = result.exceptionOrNull()
                ui.value = ui.value.copy(
                    exportMessage = if (e === NoCourses) app.getString(R.string.msg_export_empty)
                    else exportFailed(e ?: IllegalStateException("unknown")),
                    exportOk = false,
                )
            }
        }
    }

    fun showExportMessage(message: String) {
        ui.value = ui.value.copy(exportMessage = message, exportOk = false)
    }

    private fun exportFailed(e: Throwable): String = app.getString(
        R.string.msg_export_failed,
        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName,
    )

    private object NoCourses : Exception()

    // ---- 登录状态与 OOBE ----

    /** 已保存的学号；无凭据时 null。 */
    val credentialUsername: String? get() = app.creds.username

    val hasCredentials: Boolean get() = app.creds.username != null

    /** 是否需要走 OOBE 向导（学校写死，只剩登录/权限/提醒步）。 */
    val needsOobe: Boolean
        get() = !settings.oobeDone && !hasCredentials

    /** 自动进入向导时的起始步。 */
    fun oobeStartStep(): OobeStep = OobeFlow.startStep(hasCredentials)

    fun finishOobe() {
        settings.oobeDone = true
    }

    // ---- 应用更新（GitHub Release，手动触发） ----

    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data class UpToDate(val version: String) : UpdateState
        data class Available(
            val release: GitHubRelease,
            val asset: GitHubAsset,
            val error: String? = null,
        ) : UpdateState
        data class Downloading(val percent: Int) : UpdateState
        data class Ready(val file: File, val tag: String, val message: String? = null) : UpdateState
        data class NeedInstallPermission(val file: File, val tag: String) : UpdateState
        data class Failed(val message: String) : UpdateState
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private var updateJob: kotlinx.coroutines.Job? = null

    fun checkForUpdate() {
        val s = _updateState.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        updateJob?.cancel()
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            val result = runCatchingNonCancellation { app.updater.checkLatest() }
            val release = result.getOrNull()
            val e = result.exceptionOrNull()
            _updateState.value = when {
                e != null -> UpdateState.Failed(app.getString(R.string.msg_update_check_failed, updatePlainError(e)))
                release == null -> UpdateState.Failed(app.getString(R.string.msg_update_no_release))
                !AppUpdateChecks.isNewerVersion(BuildConfig.VERSION_NAME, release.tagName) ->
                    UpdateState.UpToDate(release.tagName.removePrefix("v"))
                else -> AppUpdateChecks.pickApkAsset(release.assets)
                    ?.let { UpdateState.Available(release, it) }
                    ?: UpdateState.Failed(app.getString(R.string.msg_update_no_apk))
            }
        }
    }

    fun downloadUpdate() {
        val s = _updateState.value as? UpdateState.Available ?: return
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(if (s.asset.size > 0) 0 else -1)
            try {
                val file = app.updater.downloadApk(s.asset) { pct ->
                    _updateState.value = UpdateState.Downloading(pct)
                }
                _updateState.value = UpdateState.Ready(file, s.release.tagName)
                installUpdate()
            } catch (c: kotlinx.coroutines.CancellationException) {
                _updateState.value = s
                throw c
            } catch (e: Exception) {
                _updateState.value = s.copy(error = updateDownloadError(e))
            }
        }
    }

    fun installUpdate() {
        val s = _updateState.value
        val file = when (s) {
            is UpdateState.Ready -> s.file
            is UpdateState.NeedInstallPermission -> s.file
            else -> return
        }
        val tag = (s as? UpdateState.Ready)?.tag ?: (s as? UpdateState.NeedInstallPermission)?.tag ?: ""
        _updateState.value = when (app.updater.install(file)) {
            is AppUpdater.InstallResult.Launched -> UpdateState.Ready(file, tag)
            is AppUpdater.InstallResult.NeedPermission -> UpdateState.NeedInstallPermission(file, tag)
            is AppUpdater.InstallResult.NoInstaller -> UpdateState.Ready(
                file,
                tag,
                app.getString(R.string.settings_update_no_installer),
            )
        }
    }

    fun cancelDownload() {
        updateJob?.cancel()
    }

    fun dismissUpdate() {
        updateJob?.cancel()
        app.updater.clearDownloads()
        _updateState.value = UpdateState.Idle
    }

    fun installPermissionIntent(onIntent: (Intent) -> Unit) {
        onIntent(app.updater.installPermissionIntent())
    }

    private fun updatePlainError(e: Throwable): String = when (e) {
        is java.net.UnknownHostException,
        is java.net.SocketTimeoutException,
        is java.net.ConnectException -> app.getString(R.string.error_network)
        else -> e.message?.takeIf { it.isNotBlank() } ?: app.getString(R.string.error_network)
    }

    private fun updateDownloadError(e: Throwable): String = when (e) {
        is AppUpdater.ChecksumMismatchException -> app.getString(R.string.msg_update_checksum)
        else -> app.getString(R.string.msg_update_download_failed, updatePlainError(e))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel() as T
        }
    }
}
