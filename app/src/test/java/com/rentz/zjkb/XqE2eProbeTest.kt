package com.rentz.zjkb

import com.rentz.zjkb.data.remote.xq.XqApi
import com.rentz.zjkb.data.remote.xq.XqTransport
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 真实端到端探针：登录 → 学期 → 课表（华南农业大学珠江学院测试账号）。
 *
 * 凭据从本地 secrets 文件读取（不进仓库、不进日志、不打印）；
 * 文件不存在时跳过 —— CI 上天然不会跑这个测试。
 * 输出全部脱敏：只打数量与结构形态，不打任何 PII / 凭据 / token。
 */
class XqE2eProbeTest {

    private fun credentials(): Triple<String, String, String>? {
        val f = File(System.getenv("LOCALAPPDATA"), "hermes/secrets/xiqueer-runtime.env")
        if (!f.exists()) return null
        val map = f.readLines()
            .filter { it.contains('=') && !it.trim().startsWith("#") }
            .associate {
                val idx = it.indexOf('=')
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }
        val user = map["XQER_USERNAME"] ?: return null
        val pass = map["XQER_PASSWORD"] ?: return null
        val school = map["XQER_SCHOOL_NAME"] ?: "华南农业大学珠江学院"
        return Triple(school, user, pass)
    }

    @Test
    fun login_semesters_schedule_end_to_end() {
        val (schoolName, user, pass) = credentials() ?: run {
            assumeTrue("secrets 文件不存在，跳过真实探针", false)
            return
        }
        println("探针学校：$schoolName（凭据已就绪，不打印）")
        assertTrue("学号非空", user.isNotBlank())
        assertTrue("密码非空", pass.isNotBlank())

        val api = XqApi(XqTransport(allowCleartext = true))

        // 1. 学校发现（零凭据自检）：按代码 12623 查
        val schools = api.findSchools(code = "12623")
        println("学校发现：${schools.size} 所命中，xxdm=${schools.firstOrNull()?.xxdm}，rzfs=${schools.firstOrNull()?.rzfs}")
        assertTrue("应命中珠江学院", schools.isNotEmpty())
        val school = schools.first()
        assertTrue("serviceUrl 非空", school.serviceUrl.isNotEmpty())

        // 2. 登录
        val login = api.login(user, pass)
        // 登录即装配内部会话
        api.adopt(login.session)
        println("登录：成功，姓名长度=${login.profile.xm.length}，学校=${login.session.school?.xxmc}")
        println("会话：authenticated=${login.session.isAuthenticated}，vendors=${login.session.runtime.vendors.keys}")
        assertTrue("会话应就绪", login.session.isAuthenticated)

        // 3. 学期列表
        val semesters = api.semesters()
        println("学期列表：${semesters.size} 个 → ${semesters.joinToString { "${it.dm}(${if (it.isCurrent) "当前" else "非当前"})" }}")
        assertTrue("学期列表非空", semesters.isNotEmpty())

        // 4. 课表（服务端当前学期）
        val schedule = api.schedule()
        println(
            "课表：xn=${schedule.xn} xq=${schedule.xq} zc=${schedule.zc}/maxzc=${schedule.maxzc} " +
                "slots=${schedule.slots.size} 假期=${schedule.isVacation}",
        )
        val total = (1..7).sumOf { schedule.coursesOn(it).size }
        println("本周课程条目：$total")
        for (d in 1..7) {
            val list = schedule.coursesOn(d)
            if (list.isNotEmpty()) {
                println("  周$d：${list.size} 门；示例字段形态：kcmc长度=${list.first().kcmc.length} qsjc=${list.first().qsjc} jsjc=${list.first().jsjc} zcs=${list.first().zcs}")
            }
        }
        // 本校实测 sjhjinfo 为空 —— 节次时间由 TimeGrid.SCHOOL_DEFAULT 华珠作息表提供。
        // slots 非空 = 其他学校给了服务端时间，两者取其一即可。
        println("服务端 slots：${schedule.slots.size} 个（0 = 走内置华珠作息表）")
        println("内置网格示例：${com.rentz.zjkb.domain.time.TimeGrid.period(1)?.let { "节1 ${it.start}-${it.end}" }}")
        // 周次语义验证：qssj 是响应所在周 → 第1周周一 = qssj - (zc-1) 周
        println("学期第1周周一：${schedule.semesterStart}（由 qssj=$schedule 起推算）")
        schedule.semesterStart?.let { start ->
            val today = java.time.LocalDate.now()
            val weekByDate = java.time.temporal.ChronoUnit.WEEKS.between(start, today) + 1
            println("按日期推算今天是第 $weekByDate 周；服务端 zc=${schedule.zc}")
            assertTrue(
                "按日期推算周次($weekByDate)应与服务端 zc(${schedule.zc})一致",
                weekByDate == schedule.zc.toLong(),
            )
        }
        assertTrue(
            "节次时间可用（服务端 slots 或内置华珠作息表至少一个）",
            schedule.slots.isNotEmpty() ||
                com.rentz.zjkb.domain.time.TimeGrid.period(1) != null,
        )
    }
}
