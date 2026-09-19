package com.rentz.zjkb

import com.rentz.zjkb.data.repo.parseWeeksOf
import com.rentz.zjkb.domain.time.TimeGrid
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

/**
 * 周次文案解析回归（用户真机报告：点「本周」后周一/周四课程消失）。
 * 病根：`1-5,7-17周` 被旧解析读成 {1,5,7,17} —— 第 2/3/4 周凭空蒸发。
 */
class ParseWeeksTest {

    @Test fun mixed_range_and_enum() {
        assertEquals(
            (1..5).toSet() + (7..17).toSet(),
            parseWeeksOf("1-5,7-17周"),
        )
    }

    @Test fun pure_range() {
        assertEquals((1..16).toSet(), parseWeeksOf("1-16周"))
    }

    @Test fun pure_enum() {
        assertEquals(setOf(1, 3, 5), parseWeeksOf("1,3,5周"))
    }

    @Test fun with_di_prefix() {
        assertEquals((1..16).toSet(), parseWeeksOf("第1-16周"))
    }

    @Test fun single_week() {
        assertEquals(setOf(3), parseWeeksOf("3周"))
    }

    @Test fun multi_range() {
        assertEquals((1..4).toSet() + (6..17).toSet(), parseWeeksOf("1-4,6-17周"))
    }

    @Test fun garbage_returns_empty() {
        assertEquals(emptySet<Int>(), parseWeeksOf(""))
        assertEquals(emptySet<Int>(), parseWeeksOf("待定"))
        assertEquals(emptySet<Int>(), parseWeeksOf(",,"))
    }

    @Test fun out_of_range_dropped() {
        assertEquals(setOf(1, 2), parseWeeksOf("1-2,99周"))
    }

    /* --- 华珠作息表（用户口述定案 2026-09-15） --- */

    @Test fun school_grid_morning() {
        TimeGrid.reset()
        assertEquals(LocalTime.of(8, 30), TimeGrid.period(1)!!.start)
        assertEquals(LocalTime.of(9, 10), TimeGrid.period(1)!!.end)
        assertEquals(LocalTime.of(9, 20), TimeGrid.period(2)!!.start)
        assertEquals(LocalTime.of(10, 0), TimeGrid.period(2)!!.end)
        assertEquals(LocalTime.of(10, 20), TimeGrid.period(3)!!.start)   // 大课间 20min
        assertEquals(LocalTime.of(11, 50), TimeGrid.period(4)!!.end)
        TimeGrid.reset()
    }

    @Test fun school_grid_afternoon() {
        TimeGrid.reset()
        assertEquals(LocalTime.of(14, 30), TimeGrid.period(5)!!.start)
        assertEquals(LocalTime.of(16, 20), TimeGrid.period(7)!!.start)
        assertEquals(LocalTime.of(17, 50), TimeGrid.period(8)!!.end)
        TimeGrid.reset()
    }

    @Test fun school_grid_evening_no_inner_break() {
        TimeGrid.reset()
        assertEquals(LocalTime.of(18, 40), TimeGrid.period(9)!!.start)
        // 晚上大课内无课间：节10 = 19:20-20:00（紧接节9 结束）
        assertEquals(LocalTime.of(19, 20), TimeGrid.period(10)!!.start)
        // 末节 21:30 下课（用户定案）
        assertEquals(LocalTime.of(21, 30), TimeGrid.period(12)!!.end)
        TimeGrid.reset()
    }
}
