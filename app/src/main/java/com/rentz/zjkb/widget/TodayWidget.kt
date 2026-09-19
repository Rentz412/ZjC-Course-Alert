package com.rentz.zjkb.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.rentz.zjkb.R
import com.rentz.zjkb.ZjkbApp
import com.rentz.zjkb.data.remote.xq.XqSemesterRules
import com.rentz.zjkb.domain.logic.ScheduleLogic
import com.rentz.zjkb.domain.model.Meeting
import com.rentz.zjkb.domain.reminder.RestDay
import com.rentz.zjkb.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** RemoteViews keeps the widget independent of an in-app Compose/Glance session. */
class TodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAsync { render(context, manager, ids) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateAsync { render(context, manager, intArrayOf(id)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action in setOf(PREVIOUS, NEXT, TODAY, REFRESH)) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            updateAsync {
                val manager = AppWidgetManager.getInstance(context)
                if (id !in manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))) return@updateAsync
                val prefs = preferences(context)
                val saved = prefs.getString(dateKey(id), null)
                val selected = when (action) {
                    PREVIOUS -> WidgetAgenda.move(saved, -1, LocalDate.now())
                    NEXT -> WidgetAgenda.move(saved, 1, LocalDate.now())
                    TODAY -> null
                    else -> saved
                }
                if (action != REFRESH) prefs.edit().putString(dateKey(id), selected).apply()
                render(context, manager, intArrayOf(id))
            }
        } else if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED || action == Intent.ACTION_DATE_CHANGED) {
            updateAsync {
                val manager = AppWidgetManager.getInstance(context)
                render(context, manager, manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java)))
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        preferences(context).edit().apply {
            appWidgetIds.forEach { remove(dateKey(it)) }
        }.apply()
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val prefs = preferences(context)
        val dates = oldWidgetIds.map { prefs.getString(dateKey(it), null) }
        prefs.edit().apply {
            oldWidgetIds.forEach { remove(dateKey(it)) }
            newWidgetIds.forEachIndexed { index, id -> putString(dateKey(id), dates.getOrNull(index)) }
        }.apply()
    }

    private fun updateAsync(block: suspend () -> Unit) {
        // Retain the broadcast until Room reads and the RemoteViews update are complete.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000) { updateMutex.withLock { block() } }
            } catch (error: Exception) {
                Log.w("CampusWidget", "Widget update did not complete", error)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private const val PREVIOUS = "com.rentz.zjkb.widget.PREVIOUS"
        private const val NEXT = "com.rentz.zjkb.widget.NEXT"
        private const val TODAY = "com.rentz.zjkb.widget.TODAY"
        private const val REFRESH = "com.rentz.zjkb.widget.REFRESH"
        private val updateMutex = Mutex()
        private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        private fun preferences(context: Context) = context.getSharedPreferences("widget_agenda", Context.MODE_PRIVATE)
        private fun dateKey(id: Int) = "date_$id"

        @Suppress("DEPRECATION")
        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id -> manager.updateAppWidget(id, build(context, id)) }
            if (Build.VERSION.SDK_INT < 31) manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_courses)
        }

        suspend fun build(context: Context, appWidgetId: Int): RemoteViews = withContext(Dispatchers.IO) {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 320))
            // Existing pins retain their old, sometimes single-row, size after an upgrade.
            if (height < 230) return@withContext RemoteViews(context.packageName, R.layout.widget_agenda_compact).apply {
                setOnClickPendingIntent(R.id.widget_root, mainIntent(context))
            }
            val now = LocalDateTime.now()
            val date = WidgetAgenda.selectedDate(preferences(context).getString(dateKey(appWidgetId), null), now.toLocalDate())
            val views = shell(context, appWidgetId, date)
            try {
                val app = (context.applicationContext as? ZjkbApp) ?: ZjkbApp.instance
                val term = app.settings.selectedXnxq ?: XqSemesterRules.fallbackDm()
                val week = ScheduleLogic.weekOf(date, app.settings.semesterStartMonday(term))
                val all = app.repo.meetingsByXnxq(term)
                val meetings = WidgetAgenda.day(date, app.settings.semesterStartMonday(term), all)
                val hasCache = all.isNotEmpty() || term in app.repo.storedXnxqList()
                val resting = date == now.toLocalDate() && RestDay.isResting(app.settings.restDay, date)
                val names = meetings.map { it.rwh }.distinct().associateWith { app.repo.courseByRwh(it)?.name ?: "未命名课程" }
                val weekLabel = week?.let { "第 $it 周" } ?: "学期外"
                val dateLabel = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                views.setTextViewText(R.id.widget_subtitle, "$dateLabel · ${ScheduleLogic.weekdayName(date.dayOfWeek.value)}\n$weekLabel · ${meetings.size} 节课${if (resting) " · 今日休息" else ""}")
                views.setTextViewText(R.id.widget_today_action, if (date == now.toLocalDate()) "今天" else "回到今天")
                views.setViewVisibility(R.id.widget_empty, if (meetings.isEmpty()) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_courses, if (meetings.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, when {
                    !hasCache -> "课表，准备就绪再出发。\n点上方标题，登录并同步本学期课表。"
                    week == null -> "暂不在教学周内。\n为生活，留一点空白。"
                    else -> "这一天，没有课程安排。\n把时间留给喜欢的事。"
                })
                val rows = meetings.map { meeting -> courseRow(context, meeting, names.getValue(meeting.rwh), date, now) }
                setCourses(context, views, appWidgetId, rows)
                views.setTextViewText(R.id.widget_footer, "${now.format(timeFormat)} 更新 · ${if (meetings.isEmpty()) "点标题打开课表" else "上下滑动查看完整日程"}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("CampusWidget", "Cannot read cached timetable", error)
                views.setViewVisibility(R.id.widget_courses, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(R.id.widget_empty, "暂时无法读取课表。\n点右上角刷新重试，或点标题打开应用。")
            }
            views
        }

        private fun shell(context: Context, id: Int, date: LocalDate): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_agenda).apply {
                setTextViewText(R.id.widget_date, date.dayOfMonth.toString().padStart(2, '0'))
                setContentDescription(R.id.widget_date, date.toString())
                setOnClickPendingIntent(R.id.widget_open, mainIntent(context))
                setOnClickPendingIntent(R.id.widget_previous, actionIntent(context, id, PREVIOUS))
                setOnClickPendingIntent(R.id.widget_next, actionIntent(context, id, NEXT))
                setOnClickPendingIntent(R.id.widget_today_action, actionIntent(context, id, TODAY))
                setOnClickPendingIntent(R.id.widget_refresh, actionIntent(context, id, REFRESH))
            }

        @Suppress("DEPRECATION")
        private fun setCourses(context: Context, views: RemoteViews, id: Int, rows: List<RemoteViews>) {
            if (Build.VERSION.SDK_INT >= 31) {
                val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
                rows.forEachIndexed { index, row -> items.addItem(index.toLong(), row) }
                views.setRemoteAdapter(R.id.widget_courses, items.build())
            } else {
                val intent = Intent(context, AgendaRemoteViewsService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("zjkb-widget://agenda/$id/courses"))
                views.setRemoteAdapter(R.id.widget_courses, intent)
            }
        }

        internal suspend fun legacyRows(context: Context, id: Int): List<RemoteViews> = withContext(Dispatchers.IO) {
            val app = (context.applicationContext as? ZjkbApp) ?: ZjkbApp.instance
            val now = LocalDateTime.now()
            val date = WidgetAgenda.selectedDate(preferences(context).getString(dateKey(id), null), now.toLocalDate())
            val term = app.settings.selectedXnxq ?: XqSemesterRules.fallbackDm()
            val meetings = WidgetAgenda.day(date, app.settings.semesterStartMonday(term), app.repo.meetingsByXnxq(term))
            val names = meetings.map { it.rwh }.distinct().associateWith { app.repo.courseByRwh(it)?.name ?: "未命名课程" }
            meetings.map { courseRow(context, it, names.getValue(it.rwh), date, now) }
        }

        private fun courseRow(context: Context, meeting: Meeting, name: String, date: LocalDate, now: LocalDateTime): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_agenda_course).apply {
                setTextViewText(R.id.course_time, "${meeting.startTime.format(timeFormat)} — ${meeting.endTime.format(timeFormat)}")
                setTextViewText(R.id.course_name, name)
                setTextViewText(R.id.course_room, meeting.room?.takeIf { it.isNotBlank() } ?: "教室待定")
                setTextViewText(R.id.course_teachers, meeting.teachers.joinToString("、"))
                setViewVisibility(R.id.course_teachers, if (meeting.teachers.isEmpty()) View.GONE else View.VISIBLE)
                setTextViewText(R.id.course_status, WidgetAgenda.status(meeting, date, now))
                val active = date == now.toLocalDate() && now.toLocalTime() >= meeting.startTime && now.toLocalTime() < meeting.endTime
                setInt(R.id.course_row, "setBackgroundResource", if (active) R.drawable.widget_course_bg else R.drawable.widget_control_bg)
            }

        private fun actionIntent(context: Context, id: Int, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, TodayWidgetProvider::class.java).setAction(action)
                .setData(Uri.parse("zjkb-widget://agenda/$id/${action.substringAfterLast('.')}"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun mainIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        suspend fun refreshAll(context: Context) = withContext(Dispatchers.IO) {
            try {
                updateMutex.withLock {
                    val manager = AppWidgetManager.getInstance(context)
                    render(context, manager, manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java)))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A launcher failure must not interrupt account login or course sync.
                Log.w("CampusWidget", "Cannot publish widget update", error)
            }
        }
    }
}
