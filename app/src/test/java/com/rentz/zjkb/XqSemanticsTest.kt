package com.rentz.zjkb

import com.rentz.zjkb.data.remote.xq.XqSemesterRules
import com.rentz.zjkb.data.remote.xq.XqModels
import com.rentz.zjkb.domain.time.TimeGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * 喜鹊儿数据语义测试：学期 dm 规则 + 服务端 sjhjinfo 驱动的作息网格。
 * 学期规则是用户定案（勿改）：dm = yyyy+q，q=0 第一学期（秋）、1 第二学期（春）。
 */
class XqSemanticsTest {

    /* --- 学期 dm 规则 --- */

    @Test fun fallback_dm_by_month() {
        assertEquals("20260", XqSemesterRules.fallbackDm(LocalDate.of(2026, 9, 1)))
        assertEquals("20260", XqSemesterRules.fallbackDm(LocalDate.of(2026, 12, 31)))
        assertEquals("20251", XqSemesterRules.fallbackDm(LocalDate.of(2026, 1, 15)))
        assertEquals("20251", XqSemesterRules.fallbackDm(LocalDate.of(2026, 2, 28)))
        assertEquals("20250", XqSemesterRules.fallbackDm(LocalDate.of(2026, 5, 20)))
    }

    @Test fun semester_display_name() {
        assertEquals("2024-2025学年 第一学期", XqSemesterRules.nameOf("20240"))
        assertEquals("2025-2026学年 第二学期", XqSemesterRules.nameOf("20251"))
        // 非法输入原样返回
        assertEquals("abc", XqSemesterRules.nameOf("abc"))
    }

    @Test fun pick_default_prefers_current() {
        val list = listOf(
            XqModels.Semester(dm = "20240", mc = "2024-2025-1", dqxq = "0"),
            XqModels.Semester(dm = "20251", mc = "2025-2026-2", dqxq = "1"),
            XqModels.Semester(dm = "20260", mc = "2026-2027-1", dqxq = "0"),
        )
        assertEquals("20251", XqSemesterRules.pickDefault(list))
    }

    @Test fun pick_default_falls_back_to_max_dm() {
        val list = listOf(
            XqModels.Semester(dm = "20240", mc = "2024-2025-1", dqxq = "0"),
            XqModels.Semester(dm = "20260", mc = "2026-2027-1", dqxq = "0"),
        )
        assertEquals("20260", XqSemesterRules.pickDefault(list))
    }

    @Test fun pick_default_empty() {
        assertEquals("", XqSemesterRules.pickDefault(emptyList()))
    }

    @Test fun earlier_semesters_walk_backwards() {
        // 服务端给了 20251/20260，往前推：20250, 20241, 20240, 20231, ...
        val out = XqSemesterRules.earlierSemesters(listOf("20251", "20260"), count = 4)
        assertEquals(listOf("20250", "20241", "20240", "20231"), out)
    }

    /* --- 服务端网格 --- */

    @Test fun server_slots_override_fallback() {
        TimeGrid.reset()
        TimeGrid.update(
            listOf(
                TimeGrid.Slot(1, "08:00", "08:45"),
                TimeGrid.Slot(2, "08:55", "09:40"),
                TimeGrid.Slot(3, "10:00", "10:45"),
            ),
        )
        assertEquals(LocalTime.of(8, 0), TimeGrid.period(1)!!.start)
        assertEquals(LocalTime.of(9, 40), TimeGrid.period(2)!!.end)
        assertEquals(3, TimeGrid.periods.size)
        TimeGrid.reset()
    }

    @Test fun empty_slots_keep_previous_grid() {
        TimeGrid.reset()
        TimeGrid.update(listOf(TimeGrid.Slot(1, "08:00", "08:45")))
        val before = TimeGrid.periods
        TimeGrid.update(emptyList()) // 假期周：不覆盖
        assertEquals(before, TimeGrid.periods)
        TimeGrid.reset()
    }

    @Test fun invalid_slots_are_dropped() {
        TimeGrid.reset()
        TimeGrid.update(
            listOf(
                TimeGrid.Slot(0, "08:00", "08:45"),   // dm < 1
                TimeGrid.Slot(1, "09:00", "08:45"),   // end <= start
                TimeGrid.Slot(2, "bad", "09:40"),     // 时间解析失败
                TimeGrid.Slot(3, "10:00", "10:45"),   // 唯一合法
            ),
        )
        assertEquals(1, TimeGrid.periods.size)
        assertEquals(3, TimeGrid.periods.first().index)
        TimeGrid.reset()
    }

    @Test fun locate_by_real_time() {
        TimeGrid.reset()
        TimeGrid.update(
            listOf(
                TimeGrid.Slot(1, "08:00", "08:45"),
                TimeGrid.Slot(2, "08:55", "09:40"),
            ),
        )
        val (idx1, _) = TimeGrid.locate(LocalTime.of(8, 10))!!
        assertEquals(1, idx1)
        val (idx2, _) = TimeGrid.locate(LocalTime.of(8, 50))!! // 08:45-08:55 课间仍属第1行跨度
        assertEquals(1, idx2)
        val (idx3, _) = TimeGrid.locate(LocalTime.of(9, 0))!! // 第2节开始后 → 第2行
        assertEquals(2, idx3)
        TimeGrid.reset()
    }

    @Test fun weekday_variant_returns_same_grid() {
        TimeGrid.reset()
        // 服务端网格不分单双日
        assertEquals(TimeGrid.period(3, 1), TimeGrid.period(3, 2))
        assertEquals(TimeGrid.period(3, 3), TimeGrid.period(3, 4))
        TimeGrid.reset()
    }

    /* --- Schedule 课表模型 --- */

    @Test fun schedule_json_parse_with_alias_fields() {
        val json = mapOf(
            "xn" to "2025-2026", "xq" to "2", "zc" to 5, "maxzc" to 18,
            "maxjc" to 12, "jcsw" to 4, "jcxw" to 4, "jczw" to 4,
            "qssj" to "2026-03-02", "jssj" to "2026-07-05",
            "sjhjinfo" to listOf(
                mapOf("dm" to 1, "value" to "08:00-08:45"),
                mapOf("dm" to 2, "value" to "08:55-09:40"),
            ),
            "week1" to listOf(
                // 真实教务子系统的字段形态：jcxx="1-2" 字符串节次 + rkjs/skdd/skzs 别名
                mapOf(
                    "kcmc" to "高等数学", "rkjs" to "张三", "skdd" to "A101",
                    "jcxx" to "1-2", "skzs" to "1-16周", "kcxz" to "必修",
                ),
            ),
            "week3" to listOf(
                mapOf("kcmc" to "大学英语", "jsxm" to "李四", "jsdd" to "B202", "qsjc" to 3, "jsjc" to 4, "zcs" to "1,3,5周"),
            ),
        )
        val schedule = XqModels.Schedule.fromJson(json)
        assertEquals(5, schedule.zc)
        assertEquals(18, schedule.maxzc)
        assertEquals(2, schedule.slots.size)
        assertEquals(1, schedule.coursesOn(1).size)
        val m = schedule.coursesOn(1).first()
        assertEquals("高等数学", m.kcmc)
        assertEquals("张三", m.jsxm)     // rkjs → jsxm
        assertEquals("A101", m.jsdd)     // skdd → jsdd
        assertEquals(1, m.qsjc)          // jcxx "1-2" → qsjc=1
        assertEquals(2, m.jsjc)
        assertEquals("1-16周", m.zcs)    // skzs → zcs
        assertEquals(1, schedule.coursesOn(3).size)
        assertNull(schedule.coursesOn(2).firstOrNull())
        // qssj 是响应所在周（第5周）的周一 → 第1周周一 = 2026-03-02 - 4周 = 2026-02-02
        assertEquals(LocalDate.of(2026, 2, 2), schedule.semesterStart)
        assertEquals(LocalDate.of(2026, 3, 2), schedule.dateOf(5, 1)) // 第5周周一 = qssj 本身
        assertEquals(LocalDate.of(2026, 3, 4), schedule.dateOf(5, 3))
    }

    @Test fun schedule_clamps_dirty_zc() {
        val base = mapOf(
            "zc" to 0, "maxzc" to 18,
            "sjhjinfo" to listOf(mapOf("dm" to 1, "value" to "08:00-08:45")),
        )
        assertEquals(1, XqModels.Schedule.fromJson(base).clampedZc) // zc=0 → 1
        val vacation = base + mapOf("zc" to 24)
        assertEquals(18, XqModels.Schedule.fromJson(vacation).clampedZc) // 假期 24>18 → 18
    }

    @Test fun schedule_vacation_detection() {
        val vacation = mapOf(
            "zc" to 24, "maxzc" to 18,
            "sjhjinfo" to listOf(mapOf("dm" to 1, "value" to "08:00-08:45")),
            // weekN 全空
        )
        val s = XqModels.Schedule.fromJson(vacation)
        // 假期就显示假期，保持当前学期，不回退到有课周（用户定案）
        assertEquals(true, s.isVacation)
        val term = vacation + mapOf("week1" to listOf(mapOf("kcmc" to "x")))
        assertEquals(false, XqModels.Schedule.fromJson(term).isVacation)
    }
}
