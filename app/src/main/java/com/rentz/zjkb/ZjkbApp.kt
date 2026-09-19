package com.rentz.zjkb

import android.app.Application
import androidx.room.Room
import com.rentz.zjkb.data.local.CredentialStore
import com.rentz.zjkb.data.export.IcsExportManager
import com.rentz.zjkb.data.local.SettingsStore
import com.rentz.zjkb.data.local.room.AppDatabase
import com.rentz.zjkb.data.remote.xq.XqApi
import com.rentz.zjkb.data.remote.xq.XqTransport
import com.rentz.zjkb.data.repo.CourseRepository
import com.rentz.zjkb.reminder.ReminderScheduler

class ZjkbApp : Application() {

    lateinit var client: XqApi
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var repo: CourseRepository
        private set
    lateinit var creds: CredentialStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var reminderScheduler: ReminderScheduler
        private set
    lateinit var icsExport: IcsExportManager
        private set
    lateinit var updater: com.rentz.zjkb.update.AppUpdater
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        // 数据源：喜鹊儿协议。学校写死珠江学院，服务地址登录后由服务端下发。
        client = XqApi(XqTransport())
        db = Room.databaseBuilder(this, AppDatabase::class.java, "zjkb.db")
            .build()
        creds = CredentialStore(this)
        repo = CourseRepository(client, db, creds, settings, this)
        // 会话恢复含磁盘 IO，移出主线程（Application.onCreate 在 UI 关键路径上）
        Thread { runCatching { repo.restoreSession() } }.start()
        reminderScheduler = ReminderScheduler(this, settings)
        icsExport = IcsExportManager(this, repo, settings)
        updater = com.rentz.zjkb.update.AppUpdater(this)
    }

    companion object {
        lateinit var instance: ZjkbApp
            private set
    }
}
