package com.rentz.zjkb.ui

internal data class CourseSlotSize(val startPeriod: Int, val endPeriod: Int, val requiredHeight: Int)

/** 每门课满足实际内容高度后，所有日期共享同一组节次边界。 */
internal fun timetableRowHeights(periodCount: Int, minimumHeight: Int, courses: List<CourseSlotSize>): List<Int> {
    val heights = MutableList(periodCount) { minimumHeight.coerceAtLeast(1) }
    for (course in courses) {
        if (course.startPeriod !in 1..periodCount || course.endPeriod < course.startPeriod) continue
        val start = course.startPeriod - 1
        val end = course.endPeriod.coerceAtMost(periodCount)
        val missing = course.requiredHeight - (start until end).sumOf { heights[it] }
        if (missing > 0) {
            val count = end - start
            val extra = missing / count
            val remainder = missing % count
            for (index in start until end) heights[index] += extra + if (index - start < remainder) 1 else 0
        }
    }
    return heights
}
