package com.rentz.zjkb.domain.time

import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 作息时间网格 —— 服务端驱动。
 *
 * 数据来源：课表接口响应的 `sjhjinfo`（节次 → 真实时间，如 dm=1 → "08:00-08:45"）。
 * 课表纵轴、行高、「现在」线全部由它驱动，UI 不写死任何上课时间。
 * 服务端没给时退回内置通用网格（35 分钟一节，首节 8:00）。
 */
object TimeGrid {

    data class Period(
        val index: Int,
        val start: LocalTime,
        val end: LocalTime,
        val bigBlock: Int,
        val sxw: Int, // 1 上午 3 下午 5 晚上
    )

    data class BigBlock(
        val index: Int,
        val firstPeriod: Int,
        val lastPeriod: Int,
        val start: LocalTime,
        val end: LocalTime,
        val sxw: Int,
    ) {
        val label: String get() = "第$firstPeriod-${lastPeriod}节"
    }

    /**
     * 华珠作息（用户口述定案，2026-09-15）：
     * - 上午：节1 8:30 起；小课 40min；大课内课间 10min；大课之间 20min
     * - 下午：节5 14:30 起，同上午规则
     * - 晚上：节9 18:40 起；大课 80min 连上（无课内课间）；大课之间 10min；末节 21:30 下课
     */
    val SCHOOL_DEFAULT: List<Period> = listOf(
        Period(1, LocalTime.of(8, 30), LocalTime.of(9, 10), 1, 1),
        Period(2, LocalTime.of(9, 20), LocalTime.of(10, 0), 1, 1),
        Period(3, LocalTime.of(10, 20), LocalTime.of(11, 0), 2, 1),
        Period(4, LocalTime.of(11, 10), LocalTime.of(11, 50), 2, 1),
        Period(5, LocalTime.of(14, 30), LocalTime.of(15, 10), 3, 3),
        Period(6, LocalTime.of(15, 20), LocalTime.of(16, 0), 3, 3),
        Period(7, LocalTime.of(16, 20), LocalTime.of(17, 0), 4, 3),
        Period(8, LocalTime.of(17, 10), LocalTime.of(17, 50), 4, 3),
        Period(9, LocalTime.of(18, 40), LocalTime.of(19, 20), 5, 5),
        Period(10, LocalTime.of(19, 20), LocalTime.of(20, 0), 5, 5),
        Period(11, LocalTime.of(20, 10), LocalTime.of(20, 50), 6, 5),
        Period(12, LocalTime.of(20, 50), LocalTime.of(21, 30), 6, 5),
    )

    fun sxwOfPeriod(p: Int): Int = when {
        p <= 4 -> 1
        p <= 8 -> 3
        else -> 5
    }

    private fun bigBlockOfPeriod(p: Int): Int = (p + 1) / 2

    @Volatile
    var periods: List<Period> = SCHOOL_DEFAULT
        private set

    fun bigBlocks(): List<BigBlock> {
        val groups = periods.groupBy { it.bigBlock }
        return groups.keys.sorted().mapNotNull { b ->
            val list = groups[b].orEmpty().sortedBy { it.index }
            val first = list.firstOrNull() ?: return@mapNotNull null
            val last = list.lastOrNull() ?: return@mapNotNull null
            BigBlock(b, first.index, last.index, first.start, last.end, first.sxw)
        }
    }

    fun period(index: Int): Period? = periods.firstOrNull { it.index == index }

    /** 按星期取节次（服务端网格不分单双日，直接返回同一份）。weekday: 1=周一 … 7=周日。 */
    fun period(index: Int, @Suppress("UNUSED_PARAMETER") weekday: Int): Period? = period(index)

    /** 时刻 → (节次序号, 行内真实时间比例 0f..1f)。行跨度 = 本节开始→下一节开始（末节为→本节结束）。 */
    fun locate(time: LocalTime): Pair<Int, Float>? {
        val sorted = periods.sortedBy { it.index }
        val first = sorted.firstOrNull() ?: return null
        if (!time.isAfter(first.start)) return first.index to 0f
        for (i in sorted.indices) {
            val p = sorted[i]
            val spanEnd = sorted.getOrNull(i + 1)?.start ?: p.end
            if (time.isBefore(spanEnd)) {
                val span = ChronoUnit.SECONDS.between(p.start, spanEnd).coerceAtLeast(1)
                val frac = ChronoUnit.SECONDS.between(p.start, time).toFloat() / span
                return p.index to frac.coerceIn(0f, 1f)
            }
        }
        return sorted.last().index to 1f
    }

    /**
     * 用课表响应的节次时间覆盖内置网格。
     * slots 为空（假期周 / 接口未返回）不覆盖 —— 保留上一次同步的网格。
     */
    fun update(slots: List<Slot>) {
        if (slots.isEmpty()) return
        val parsed = slots.mapNotNull { it.toPeriod() }.sortedBy { it.index }
        if (parsed.isNotEmpty()) periods = parsed
    }

    fun reset() {
        periods = SCHOOL_DEFAULT
    }

    /** 服务端 sjhjinfo 条目：dm（节次）+ "HH:mm-HH:mm"。 */
    data class Slot(val dm: Int, val startText: String, val endText: String) {
        fun toPeriod(): Period? {
            val start = runCatching { LocalTime.parse(startText) }.getOrNull() ?: return null
            val end = runCatching { LocalTime.parse(endText) }.getOrNull() ?: return null
            if (dm < 1 || !end.isAfter(start)) return null
            return Period(dm, start, end, bigBlockOfPeriod(dm), sxwOfPeriod(dm))
        }
    }
}
