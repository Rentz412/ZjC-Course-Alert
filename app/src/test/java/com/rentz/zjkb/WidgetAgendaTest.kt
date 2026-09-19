package com.rentz.zjkb

import com.rentz.zjkb.domain.model.Meeting
import com.rentz.zjkb.widget.WidgetAgenda
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class WidgetAgendaTest {
    private val monday = LocalDate.of(2026, 8, 31)
    private val today = LocalDate.of(2026, 9, 14)
    private fun meeting(id: String = "course", weeks: Set<Int> = setOf(3), weekday: Int = 1,
                        start: String = "08:30", end: String = "10:00") = Meeting(
        id, "student", listOf("欧阳嘉宁", "司徒思远"), weeks, weekday, 1, 2,
        LocalTime.parse(start), LocalTime.parse(end), "19栋人文综合教学楼 [19-402]", "",
    )

    @Test fun todayModeFollowsMidnight() {
        assertEquals(today, WidgetAgenda.selectedDate(null, today))
        assertEquals(today.plusDays(1), WidgetAgenda.selectedDate(null, today.plusDays(1)))
    }

    @Test fun browsedDateSurvivesMidnight() {
        val selected = WidgetAgenda.move(null, 1, today)
        assertEquals(today.plusDays(1), WidgetAgenda.selectedDate(selected, today.plusDays(2)))
    }

    @Test fun returningToTodayRestoresFollowingMode() {
        val tomorrow = WidgetAgenda.move(null, 1, today)
        assertNull(WidgetAgenda.move(tomorrow, -1, today))
    }

    @Test fun navigationCrossesYearAndLeapDay() {
        assertEquals("2027-01-01", WidgetAgenda.move(null, 1, LocalDate.of(2026, 12, 31)))
        assertEquals("2024-02-29", WidgetAgenda.move(null, -1, LocalDate.of(2024, 3, 1)))
    }

    @Test fun damagedSavedDateFallsBackToToday() {
        assertEquals(today, WidgetAgenda.selectedDate("not-a-date", today))
        assertEquals(today, WidgetAgenda.selectedDate("2026-02-30", today))
        assertEquals(today.plusDays(1).toString(), WidgetAgenda.move("broken", 1, today))
    }

    @Test fun selectedDayRespectsWeekAndWeekday() {
        val all = listOf(meeting(), meeting("even", setOf(2)), meeting("tuesday", weekday = 2))
        assertEquals(listOf("course"), WidgetAgenda.day(today, monday, all).map { it.rwh })
        assertEquals(listOf("tuesday"), WidgetAgenda.day(today.plusDays(1), monday, all).map { it.rwh })
        assertTrue(WidgetAgenda.day(today.plusWeeks(1), monday, all).isEmpty())
    }

    @Test fun datesOutsideTermNeverFallBackToAnOlderCourseWeek() {
        val all = listOf(meeting(weeks = setOf(1, 3)))
        assertTrue(WidgetAgenda.day(monday.minusDays(7), monday, all).isEmpty())
        assertTrue(WidgetAgenda.day(monday.plusWeeks(30), monday, all).isEmpty())
    }

    @Test fun overlappingCoursesAndLongFieldsArePreserved() {
        val early = meeting("early")
        val overlap = meeting("overlap", start = "09:00", end = "10:30")
        val late = meeting("late", start = "14:30", end = "16:00")
        assertEquals(listOf(early, overlap, late), WidgetAgenda.day(today, monday, listOf(late, overlap, early)))
    }

    @Test fun classStatusUsesExactStartAndEndBoundaries() {
        val course = meeting()
        assertTrue(WidgetAgenda.status(course, today, today.atTime(8, 29)).endsWith("待上课"))
        assertTrue(WidgetAgenda.status(course, today, today.atTime(8, 30)).endsWith("进行中"))
        assertTrue(WidgetAgenda.status(course, today, today.atTime(10, 0)).endsWith("已结束"))
    }

    @Test fun browsingOtherDatesDoesNotUseTodaysClockStatus() {
        val now = LocalDateTime.of(today, LocalTime.of(9, 0))
        assertEquals("1–2 节", WidgetAgenda.status(meeting(), today.minusDays(1), now))
        assertEquals("1–2 节", WidgetAgenda.status(meeting(), today.plusDays(1), now))
    }
}
