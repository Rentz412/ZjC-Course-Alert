package com.rentz.zjkb.widget

import com.rentz.zjkb.domain.logic.ScheduleLogic
import com.rentz.zjkb.domain.model.Meeting
import java.time.LocalDate
import java.time.LocalDateTime

internal object WidgetAgenda {
    fun selectedDate(saved: String?, today: LocalDate): LocalDate =
        saved?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today

    // A missing date follows today across midnight; a browsed date stays fixed.
    fun move(saved: String?, days: Long, today: LocalDate): String? {
        val date = runCatching { selectedDate(saved, today).plusDays(days) }.getOrDefault(today)
        return date.takeUnless { it == today }?.toString()
    }

    fun day(date: LocalDate, startMonday: LocalDate, meetings: List<Meeting>): List<Meeting> =
        ScheduleLogic.meetingsOn(date, ScheduleLogic.weekOf(date, startMonday), meetings)

    fun status(meeting: Meeting, date: LocalDate, now: LocalDateTime): String = when {
        date != now.toLocalDate() -> "${meeting.startPeriod}–${meeting.endPeriod} 节"
        now.toLocalTime() < meeting.startTime -> "${meeting.startPeriod}–${meeting.endPeriod} 节 · 待上课"
        now.toLocalTime() < meeting.endTime -> "${meeting.startPeriod}–${meeting.endPeriod} 节 · 进行中"
        else -> "${meeting.startPeriod}–${meeting.endPeriod} 节 · 已结束"
    }
}
