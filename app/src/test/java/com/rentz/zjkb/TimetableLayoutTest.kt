package com.rentz.zjkb

import com.rentz.zjkb.ui.CourseSlotSize
import com.rentz.zjkb.ui.timetableRowHeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableLayoutTest {
    @Test fun shortCoursesPreserveCompactGrid() {
        assertEquals(List(12) { 42 }, timetableRowHeights(12, 42, listOf(CourseSlotSize(1, 2, 80))))
    }

    @Test fun longCourseExpandsOnlyItsCoveredPeriods() {
        val heights = timetableRowHeights(12, 42, listOf(CourseSlotSize(3, 4, 235)))
        assertEquals(235, heights[2] + heights[3])
        assertEquals(42, heights[0])
        assertEquals(42, heights[4])
    }

    @Test fun overlappingAndCrossBlockCoursesAllRetainEnoughSpace() {
        val courses = listOf(CourseSlotSize(1, 2, 230), CourseSlotSize(1, 4, 520), CourseSlotSize(2, 3, 410), CourseSlotSize(4, 4, 170))
        val heights = timetableRowHeights(12, 42, courses)
        courses.forEach { course ->
            assertTrue(heights.subList(course.startPeriod - 1, course.endPeriod).sum() >= course.requiredHeight)
        }
        assertTrue(heights.all { it >= 42 })
    }

    @Test fun invalidSlotsCannotBreakTheGrid() {
        val heights = timetableRowHeights(12, 42, listOf(CourseSlotSize(0, 2, 300), CourseSlotSize(4, 3, 300), CourseSlotSize(11, 15, 200)))
        assertEquals(12, heights.size)
        assertEquals(42, heights.first())
        assertEquals(200, heights.takeLast(2).sum())
    }
}
