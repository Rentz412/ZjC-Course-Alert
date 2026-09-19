package com.rentz.zjkb.data.remote.xq

/**
 * 业务模型 —— 字段名严格对齐接口资料里的真实响应结构。
 *
 * 课表来自 `RUNTIME-SCHEMA-09`（POST `{runtime.jw.base}/wap/mycourseschedule.action`），
 * 学期来自 `RUNTIME-SCHEMA-03`，个人资料来自 baseInfoServlet。
 *
 * 两条贯穿全文件的防御原则：
 *   1. 字段随学校变化 —— 每个字段都可能缺失、可能是 String 也可能是数字，
 *      解析一律走容错帮手，缺了就给空值让 UI 自己兜底；
 *   2. 不猜结构 —— 外层字段全部有据可查；课程条目内部字段名有别名表。
 */
object XqModels {

    /* --- 解析帮手 ------------------------------------------------------------- */

    private fun s(v: Any?): String = v?.toString()?.trim() ?: ""

    private fun i(v: Any?, fallback: Int = 0): Int {
        if (v is Int) return v
        if (v is Long) return v.toInt()
        if (v is Number) return v.toInt()
        val text = s(v)
        if (text.isEmpty()) return fallback
        return text.toIntOrNull() ?: text.toDoubleOrNull()?.toInt() ?: fallback
    }

    private fun list(v: Any?): List<Any?> = v as? List<*> ?: emptyList()

    private fun map(v: Any?): Map<String, Any?> =
        (v as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

    /** 按多个候选键名取第一个非空值（键名大小写不敏感）。 */
    private fun pick(json: Map<String, Any?>, keys: List<String>): String {
        val lowered = HashMap<String, Any?>()
        for ((k, v) in json) lowered[k.lowercase()] = v
        for (key in keys) {
            val direct = s(json[key])
            if (direct.isNotEmpty()) return direct
        }
        for (key in keys) {
            val value = s(lowered[key.lowercase()])
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    /* --- 节次时间 ------------------------------------------------------------- */

    /** `sjhjinfo` 的一项：节次 → 真实时间。课表纵轴、行高、「现在」线全由它驱动。 */
    data class PeriodSlot(
        val jc: Int,
        val startMinute: Int,
        val endMinute: Int,
        val label: String,
    ) {
        val durationMinutes: Int get() = endMinute - startMinute
        val startText: String get() = formatMinute(startMinute)
        val endText: String get() = formatMinute(endMinute)

        companion object {
            /** `{dm, value}` → PeriodSlot。`value` 形如 `08:00-08:45`。
             * 时间解析不出来返回 null，调用方直接丢弃这一节（比画错位置好）。 */
            fun tryParse(json: Any?): PeriodSlot? {
                val m = map(json)
                val jc = i(m["dm"], fallback = -1)
                val value = s(m["value"])
                if (jc < 0) return null
                val parts = value.split("-")
                if (parts.size != 2) return null
                val start = parseHhmm(parts[0]) ?: return null
                val end = parseHhmm(parts[1]) ?: return null
                if (end <= start) return null
                return PeriodSlot(jc = jc, startMinute = start, endMinute = end, label = value)
            }
        }
    }

    /** `HH:mm` → 当天分钟数。格式不对返回 null。 */
    private fun parseHhmm(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** 分钟数 → `HH:mm`。 */
    fun formatMinute(minute: Int): String =
        "%02d:%02d".format(minute / 60, minute % 60)

    /* --- 课程条目 ------------------------------------------------------------- */

    /** 课表格子里的一门课。除 [kcmc] 外所有字段都可能是空的。 */
    data class Course(
        val kcmc: String,
        val jsxm: String = "",
        val jsdd: String = "",
        val qsjc: Int = 0,
        val jsjc: Int = 0,
        val zcs: String = "",
        val kcxz: String = "",
        val skbjmc: String = "",
        val kcdm: String = "",
    ) {
        /** 占几节。字段异常时至少占一节，避免高度为 0 的隐形块。 */
        val periodCount: Int get() = if (jsjc >= qsjc) jsjc - qsjc + 1 else 1
        val hasTeacher: Boolean get() = jsxm.isNotEmpty()
        val hasRoom: Boolean get() = jsdd.isNotEmpty()

        companion object {
            fun fromJson(json: Any?): Course {
                val m = map(json)
                var qsjc = i(m["qsjc"])
                var jsjc = i(m["jsjc"])
                // 真实教务子系统不给数字节次，给的是字符串形态（实测 20251 第 1 周）：
                //   jcxx = "1-2"（起止节次）  jcdm = "01,02"（节次代码）
                if (qsjc <= 0 && jsjc <= 0) {
                    val jcxx = s(m["jcxx"])
                    val jcdm = s(m["jcdm"])
                    val parsed = parsePeriods(if (jcxx.isNotEmpty()) jcxx else jcdm)
                    qsjc = parsed.first
                    jsjc = parsed.second
                }
                // 结束节次缺失或早于起始 —— 当成单节课，不要反向跨行。
                if (jsjc < qsjc) jsjc = qsjc
                return Course(
                    kcmc = s(m["kcmc"]),
                    jsxm = pick(m, listOf("jsxm", "rkjs")),
                    jsdd = pick(m, listOf("jsdd", "skdd")),
                    qsjc = qsjc,
                    jsjc = jsjc,
                    zcs = pick(m, listOf("zcs", "skzs")),
                    kcxz = pick(m, listOf("kcxz", "kclb")),
                    skbjmc = s(m["skbjmc"]),
                    kcdm = s(m["kcdm"]),
                )
            }
        }
    }

    /** 从 `"1-2"` / `"01,02"` / `"1-2,3-4"` 这类节次字符串里提取最小/最大节次。 */
    private fun parsePeriods(text: String): Pair<Int, Int> {
        val nums = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        if (nums.isEmpty()) return 0 to 0
        return nums.min() to nums.max()
    }

    /* --- 课表 ----------------------------------------------------------------- */

    /** 一周课表 —— `RUNTIME-SCHEMA-09` 的完整响应。 */
    data class Schedule(
        val xn: String,
        val xq: String,
        /** 服务端认定的当前周次。 */
        val zc: Int,
        val maxzc: Int,
        val maxjc: Int,
        /** 上午 / 下午 / 晚上各自的节次数。服务端直接给了时段分段。 */
        val jcsw: Int,
        val jcxw: Int,
        val jczw: Int,
        /** 学期起止日期 `yyyy-MM-dd`。 */
        val qssj: String,
        val jssj: String,
        /** 节次 → 真实时间。 */
        val slots: List<PeriodSlot>,
        /** `week1`..`week7` 摊平成 1-7 → 课程列表。 */
        val weeks: Map<Int, List<Course>>,
    ) {
        fun coursesOn(weekday: Int): List<Course> = weeks[weekday] ?: emptyList()

        val isBlank: Boolean get() = slots.isEmpty() && weeks.values.all { it.isEmpty() }

        fun slotOf(jc: Int): PeriodSlot? = slots.firstOrNull { it.jc == jc }

        /**
         * 学期第 1 周周一。解析不了时返回 null，日期列就不显示。
         *
         * 语义注意（实测珠江学院 2026-09-15）：qssj/jssj 是**本次响应所在周**的
         * 起止日期（周一~周日），不是学期起点！服务端同时给 zc（当前周次）。
         * 第 1 周周一 = qssj - (zc-1) 周。qssj 与 zc 缺其一时无法推算 → null。
         */
        val semesterStart: java.time.LocalDate?
            get() {
                val monday = runCatching { java.time.LocalDate.parse(qssj) }.getOrNull() ?: return null
                // qssj 不是周一时先取当周周一（防御：服务端口径变化）
                val weekMonday = monday.minusDays((monday.dayOfWeek.value - 1).toLong())
                val zcVal = zc
                if (zcVal < 1) return null
                return weekMonday.minusWeeks((zcVal - 1).toLong())
            }

        /** 第 [week] 周、周 [weekday] 对应的真实日期。 */
        fun dateOf(week: Int, weekday: Int): java.time.LocalDate? {
            val start = semesterStart ?: return null
            return start.plusDays(((week - 1) * 7 + (weekday - 1)).toLong())
        }

        /** 服务端周次钳到合理范围（新学年 zc=0、假期 zc>maxzc 两种脏数据都治）。 */
        val clampedZc: Int
            get() = when {
                zc < 1 -> 1
                maxzc > 0 && zc > maxzc -> maxzc
                else -> zc
            }

        /** 按真实日期算当前是第几周；算出来落在 [1, maxzc] 之外退回服务端周次的钳位值。 */
        fun currentWeekAt(now: java.time.LocalDate): Int {
            val start = semesterStart ?: return clampedZc
            val days = java.time.temporal.ChronoUnit.DAYS.between(start, now)
            if (days < 0) return clampedZc
            val week = (days / 7 + 1).toInt()
            if (week < 1 || (maxzc > 0 && week > maxzc)) return clampedZc
            return week
        }

        /** 假期判定：服务端回 zc > maxzc 且 weekN 全空（实测 24>18）。
         * 用户定案：假期就显示假期，保持当前学期，不回退到有课周。 */
        val isVacation: Boolean
            get() = zc > maxzc && maxzc > 0 && weeks.values.all { it.isEmpty() }

        companion object {
            val BLANK = Schedule(
                xn = "", xq = "", zc = 1, maxzc = 20, maxjc = 0,
                jcsw = 0, jcxw = 0, jczw = 0, qssj = "", jssj = "",
                slots = emptyList(), weeks = emptyMap(),
            )

            fun fromJson(json: Map<String, Any?>): Schedule {
                val rawSlots = ArrayList<PeriodSlot>()
                for (raw in list(json["sjhjinfo"])) {
                    PeriodSlot.tryParse(raw)?.let { rawSlots.add(it) }
                }
                val slots = rawSlots.sortedBy { it.jc }

                val weeks = HashMap<Int, List<Course>>()
                for (d in 1..7) {
                    val courses = list(json["week$d"])
                        .map(Course::fromJson)
                        .filter { it.kcmc.isNotEmpty() }
                        .sortedBy { it.qsjc }
                    weeks[d] = courses
                }

                return Schedule(
                    xn = s(json["xn"]),
                    xq = s(json["xq"]),
                    zc = i(json["zc"], fallback = 1),
                    maxzc = i(json["maxzc"], fallback = 20),
                    maxjc = i(json["maxjc"], fallback = slots.size),
                    jcsw = i(json["jcsw"]),
                    jcxw = i(json["jcxw"]),
                    jczw = i(json["jczw"]),
                    qssj = s(json["qssj"]),
                    jssj = s(json["jssj"]),
                    slots = slots,
                    weeks = weeks,
                )
            }
        }
    }

    /* --- 学期 / 用户 ---------------------------------------------------------- */

    /** `RUNTIME-SCHEMA-03` 的学期项。 */
    data class Semester(
        /** 学期代码，如 `2025-2026-1`。 */
        val dm: String,
        /** 显示名称。 */
        val mc: String,
        /** `1` 表示当前学期。 */
        val dqxq: String,
    ) {
        val isCurrent: Boolean get() = dqxq == "1"

        companion object {
            fun fromJson(json: Any?): Semester {
                val m = map(json)
                return Semester(dm = s(m["dm"]), mc = s(m["mc"]), dqxq = s(m["dqxq"]))
            }
        }
    }

    /** 学生基本信息 —— baseInfoServlet / 登录响应。 */
    data class UserInfo(
        val xm: String = "",
        val xh: String = "",
        val bjmc: String = "",
        val xxmc: String = "",
        val xb: String = "",
        val rxnj: String = "",
    ) {
        /** 头像里的两个字。姓名为空时给中性占位。按码位切分，不劈生僻字。 */
        val avatarText: String
            get() {
                if (xm.isEmpty()) return "同学"
                val chars = xm.codePoints().toArray()
                return if (chars.size <= 2) xm
                else String(chars, chars.size - 2, 2)
            }

        fun toJson(): String = org.json.JSONObject().apply {
            put("xm", xm); put("xh", xh); put("bjmc", bjmc)
            put("xxmc", xxmc); put("xb", xb); put("rxnj", rxnj)
        }.toString()

        companion object {
            fun fromJson(json: Map<String, Any?>): UserInfo = UserInfo(
                xm = pick(json, listOf("xm", "name", "mc", "xsxm")),
                xh = pick(json, listOf("xh", "xsxh", "xuehao", "studentNo", "userid", "userId")),
                bjmc = pick(json, listOf("bjmc", "ssbj", "classname", "className", "bj")),
                xxmc = pick(json, listOf("xxmc", "schoolName", "school_name")),
                xb = pick(json, listOf("xb", "sex", "gender")),
                rxnj = pick(json, listOf("rxnj", "nj", "grade", "njmc")),
            )

            fun fromJsonText(text: String?): UserInfo {
                if (text.isNullOrBlank()) return UserInfo()
                return try {
                    val v = XqWire.parseJson(text)
                    if (v is Map<*, *>) fromJson(v.entries.associate { it.key.toString() to it.value })
                    else UserInfo()
                } catch (_: Exception) {
                    UserInfo()
                }
            }
        }
    }
}
