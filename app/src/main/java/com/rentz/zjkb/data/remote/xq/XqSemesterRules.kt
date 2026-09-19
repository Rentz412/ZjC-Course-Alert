package com.rentz.zjkb.data.remote.xq

/**
 * 喜鹊儿学期语义（用户定案，勿改）：dm = yyyy+q，q=0 第一学期（秋）、1 第二学期（春）。
 */
object XqSemesterRules {

    /** 按今天日期推导当前学期 dm（展示兜底；权威值以服务端学期列表为准）。 */
    fun fallbackDm(now: java.time.LocalDate = java.time.LocalDate.now()): String {
        val y = now.year
        val m = now.monthValue
        return when (m) {
            in 9..12 -> "${y}0"
            in 1..2 -> "${y - 1}1"
            else -> "${y - 1}0"
        }
    }

    /** 学期 dm → 展示名（「2024-2025学年 第二学期」）。 */
    fun nameOf(dm: String): String {
        val n = dm.toIntOrNull() ?: return dm
        if (dm.length < 4) return dm
        val year = n / 10
        val q = n % 10
        val xq = if (q == 1) "第二学期" else "第一学期"
        return "$year-${year + 1}学年 $xq"
    }

    /** 默认学期：dqxq=1 优先；没标取 dm 数字最大的。 */
    fun pickDefault(list: List<XqModels.Semester>): String {
        if (list.isEmpty()) return ""
        list.firstOrNull { it.isCurrent }?.let { return it.dm }
        return list.maxByOrNull { it.dm.toIntOrNull() ?: -1 }?.dm ?: list.first().dm
    }

    /** 服务端学期列表之外的更早候选学期码（往前推 count 个）。 */
    fun earlierSemesters(existing: List<String>, count: Int = 8): List<String> {
        val set = existing.toSet()
        var best = Int.MAX_VALUE
        for (s in existing) {
            val n = s.toIntOrNull() ?: -1
            if (n > 0 && n < best) best = n
        }
        val out = ArrayList<String>()
        var year = best / 10
        var q = best % 10
        while (out.size < count) {
            if (q == 0) { q = 1; year -= 1 } else { q = 0 }
            val dm = "%04d%d".format(year, q)
            if (dm !in set) out.add(dm)
            if (year < 2000) break
        }
        return out
    }
}
