package com.rentz.zjkb.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.rentz.zjkb.R
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Collection adapter for Android 8–11; Android 12+ receives its rows directly. */
class AgendaRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return object : RemoteViewsFactory {
            private var rows = emptyList<RemoteViews>()
            override fun onCreate() = Unit
            override fun onDataSetChanged() {
                rows = try {
                    runBlocking { withTimeout(6_000) { TodayWidgetProvider.legacyRows(applicationContext, id) } }
                } catch (error: Exception) {
                    Log.w("CampusWidget", "Cannot load legacy widget rows", error)
                    listOf(RemoteViews(packageName, R.layout.widget_agenda_course).apply {
                        setTextViewText(R.id.course_name, "暂时无法读取课程")
                        setTextViewText(R.id.course_room, "点右上角刷新重试")
                    })
                }
            }
            override fun onDestroy() { rows = emptyList() }
            override fun getCount() = rows.size
            override fun getViewAt(position: Int): RemoteViews? = rows.getOrNull(position)
            override fun getLoadingView(): RemoteViews? = null
            override fun getViewTypeCount() = 1
            override fun getItemId(position: Int) = position.toLong()
            override fun hasStableIds() = false
        }
    }
}
