package com.rentz.zjkb.data.repo

import android.content.Context
import com.rentz.zjkb.data.GbuException
import com.rentz.zjkb.data.local.CredentialStore
import com.rentz.zjkb.data.local.SettingsStore
import com.rentz.zjkb.data.local.room.AppDatabase
import com.rentz.zjkb.data.local.room.CourseEntity
import com.rentz.zjkb.data.local.room.MeetingEntity
import com.rentz.zjkb.data.remote.xq.XqApi
import com.rentz.zjkb.data.remote.xq.XqException
import com.rentz.zjkb.data.remote.xq.XqModels
import com.rentz.zjkb.data.remote.xq.XqSessionCodec
import com.rentz.zjkb.domain.model.Course
import com.rentz.zjkb.domain.model.Meeting
import com.rentz.zjkb.domain.model.TermData
import com.rentz.zjkb.domain.time.TimeGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalTime

/**
 * 课表仓库 —— 数据源是喜鹊儿协议（XqApi）。
 *
 * 与 GBU 版的关键差异：
 *  - 课表是**结构化 JSON**（week1..week7 + sjhjinfo），不再解析 HTML；
 *  - 节次时间由服务端 sjhjinfo 直接下发（TimeGrid.update）；
 *  - 周次语义：服务端 zc + maxzc；假期时 zc > maxzc（显示假期，不回退）；
 *  - 会话为 XqSessionCodec.Session（四层：学校/用户/认证/动态配置）。
 */
class CourseRepository(
    private val client: XqApi,
    private val db: AppDatabase,
    private val creds: CredentialStore,
    private val settings: SettingsStore,
    private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun observeTermData(xnxq: String): Flow<TermData> =
        combine(
            db.courseDao().observeByXnxq(xnxq),
            db.meetingDao().observeByXnxq(xnxq),
        ) { courses, meetings ->
            TermData(
                courses = courses.map { it.toDomain() },
                meetings = meetings.map { it.toDomain() },
            )
        }

    suspend fun courseByRwh(rwh: String): Course? = db.courseDao().byRwh(rwh)?.toDomain()

    suspend fun meetingsByXnxq(xnxq: String): List<Meeting> =
        db.meetingDao().byXnxq(xnxq).map { it.toDomain() }

    suspend fun storedXnxqList(): List<String> = db.courseDao().allXnxq()

    /** 同步：无会话则先登录；拉取中过期则重登一次并重试。 */
    suspend fun sync(xnxq: String): SyncResult = withContext(Dispatchers.IO) {
        if (!client.currentSession.isAuthenticated) loginWithStoredCredentials()
        val result = fetchAndStore(xnxq)
        runCatching { calibrateSemesterStart(xnxq) }
        result
    }

    /**
     * 校准第 1 周周一：喜鹊儿课表响应自带 qssj（学期起始日期），
     * 已经在 fetchAndStore 里落盘；这里只读回。
     */
    suspend fun calibrateSemesterStart(xnxq: String, force: Boolean = false): LocalDate? {
        if (!force && settings.isSemesterStartCustomized(xnxq)) return null
        return settings.serverSemesterStartMonday(xnxq)
    }

    /** 登录并把会话/资料落盘。 */
    suspend fun loginWithStoredCredentials() {
        val u = creds.username ?: throw GbuException.BadCredentials("未保存登录凭据")
        val p = creds.password ?: throw GbuException.BadCredentials("未保存登录凭据")
        login(u, p)
    }

    suspend fun login(username: String, password: String) = withContext(Dispatchers.IO) {
        try {
            val result = client.login(username, password)
            // 登录即装配内部会话 —— 否则后续请求空会话 → HTTP 500。
            client.adopt(result.session)
            persistSession(result.session)
            persistProfile(result.profile)
        } catch (e: XqException.Auth) {
            throw GbuException.BadCredentials(e.message ?: "账号或密码不正确")
        } catch (e: XqException.SessionExpired) {
            throw GbuException.BadCredentials(e.message ?: "登录状态已失效")
        } catch (e: XqException) {
            throw GbuException.ApiError(stage = GbuException.ApiError.Stage.Network, summary = e.message ?: "登录失败")
        }
    }

    /** 会话持久化（不含密码明文；token 走系统加密存储由 CredentialStore 分担）。 */
    private fun persistSession(session: XqSessionCodec.Session) {
        context.getSharedPreferences("zjkb_session", Context.MODE_PRIVATE)
            .edit().putString("session", session.toJson().toString()).apply()
    }

    fun restoreSession(): XqSessionCodec.Session {
        val text = context.getSharedPreferences("zjkb_session", Context.MODE_PRIVATE)
            .getString("session", null)
        val session = XqSessionCodec.Session.fromJson(text)
        client.adopt(session)
        return session
    }

    private fun persistProfile(profile: XqModels.UserInfo) {
        context.getSharedPreferences("zjkb_profile", Context.MODE_PRIVATE)
            .edit().putString("profile", profile.toJson()).apply()
    }

    fun restoreProfile(): XqModels.UserInfo = XqModels.UserInfo.fromJsonText(
        context.getSharedPreferences("zjkb_profile", Context.MODE_PRIVATE).getString("profile", null),
    )

    /** 学期列表（服务端只给当前 2 个学期）。 */
    suspend fun semesters(): List<XqModels.Semester> = withContext(Dispatchers.IO) {
        ensureSession()
        runWithRelogin { client.semesters() }
    }

    private suspend fun fetchAndStore(xnxq: String): SyncResult {
        // 课表响应给整周结构（week1..week7 = 周一..周日）。
        val schedule = runWithRelogin { client.schedule(xnxq, null) }
        TimeGrid.update(schedule.slots.map { TimeGrid.Slot(it.jc, it.startText, it.endText) })
        schedule.semesterStart?.let { settings.setServerSemesterStartMonday(xnxq, it) }

        val now = System.currentTimeMillis()
        val meetingEntities = ArrayList<MeetingEntity>()
        val courseEntities = ArrayList<CourseEntity>()
        // rwh = 课程唯一标识（课程代码+教学班+学期+周几+节次），**不含周次**。
        // 同一门课的不同周次段（如 1-5,7-17 与 1-16）共享同一 rwh ——
        // 课块颜色/详情页/按课聚合全部一致；周次差异由 Meeting 行自己携带。
        // 此前 rwh 混入周次，同门课两个周次段被当成两门课，颜色各不相同（真机报告）。
        val rwhSeen = HashSet<String>()
        for (weekday in 1..7) {
            for (c in schedule.coursesOn(weekday)) {
                val courseKey = c.kcdm.ifEmpty { c.skbjmc.ifEmpty { c.kcmc } }
                val rwh = "${xnxq}-c${courseKey}-d${weekday}-p${c.qsjc}"
                if (rwh !in rwhSeen) {
                    rwhSeen.add(rwh)
                    courseEntities += c.toEntity(rwh, xnxq, now)
                }
                meetingEntities += c.toMeetingEntity(rwh, xnxq, weekday, schedule)
            }
        }

        db.withTransaction {
            db.courseDao().deleteByXnxq(xnxq)
            db.meetingDao().deleteByXnxq(xnxq)
            db.courseDao().insertAll(courseEntities)
            db.meetingDao().insertAll(meetingEntities)
        }

        settings.lastSyncAt = now
        return SyncResult(courseEntities.size, meetingEntities.size, emptyList())
    }

    private suspend fun ensureSession() {
        if (!client.currentSession.isAuthenticated) loginWithStoredCredentials()
    }

    private suspend fun <T> runWithRelogin(block: () -> T): T {
        return try {
            block()
        } catch (e: XqException.SessionExpired) {
            // 过期重登一次重试（挂起等待，绝不阻塞主线程）
            loginWithStoredCredentials()
            block()
        }
    }

    suspend fun logout() {
        settings.scheduledAlarmKeys = emptySet()
        creds.clear()
        context.getSharedPreferences("zjkb_session", Context.MODE_PRIVATE).edit().clear().apply()
    }

    data class SyncResult(val courseCount: Int, val meetingCount: Int, val unparsed: List<String>)

    // ---- 实体映射 ----

    private fun CourseEntity.toDomain() = Course(
        rwh = rwh, xnxq = xnxq, name = name, nameEn = nameEn, code = code, seq = seq,
        className = className, credits = credits, hours = hours, nature = nature, category = category,
        college = college, enrollTime = enrollTime, capacity = capacity, enrolled = enrolled,
        rawKcxx = rawKcxx,
        unparsed = runCatching { json.decodeFromString<List<String>>(unparsed) }.getOrDefault(emptyList()),
    )

    private fun MeetingEntity.toDomain() = Meeting(
        rwh = rwh,
        role = role,
        teachers = teachers.split(' ').filter { it.isNotBlank() },
        weeks = weeks.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
        weekday = weekday,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
        room = room,
        rawText = rawText,
    )

    private fun XqModels.Course.toEntity(rwh: String, xnxq: String, syncedAt: Long): CourseEntity =
        CourseEntity(
            rwh = rwh, xnxq = xnxq, name = kcmc, nameEn = null, code = kcdm, seq = skbjmc,
            className = skbjmc, credits = 0.0, hours = (periodCount * 45).toDouble(),
            nature = kcxz, category = kcxz, college = null, enrollTime = null,
            capacity = null, enrolled = null,
            rawKcxx = "${qsjc}-${jsjc}节 $zcs @$jsdd",
            unparsed = "[]",
            syncedAt = syncedAt,
        )

    private fun XqModels.Course.toMeetingEntity(
        rwh: String,
        xnxq: String,
        weekday: Int,
        schedule: XqModels.Schedule,
    ): MeetingEntity {
        // 节次时间：服务端 slots 优先；本校 sjhjinfo 实测为空 → 用 TimeGrid 华珠作息表
        val startSlot = schedule.slotOf(qsjc) ?: TimeGrid.period(qsjc)?.let {
            XqModels.PeriodSlot(qsjc, it.start.toSecondOfDay() / 60, it.end.toSecondOfDay() / 60, "${it.start}-${it.end}")
        }
        val endSlot = schedule.slotOf(jsjc) ?: TimeGrid.period(jsjc)?.let {
            XqModels.PeriodSlot(jsjc, it.start.toSecondOfDay() / 60, it.end.toSecondOfDay() / 60, "${it.start}-${it.end}")
        }
        val start = startSlot?.startText ?: "08:30"
        val end = endSlot?.endText ?: "09:10"
        val weeks = parseWeeks(zcs).ifEmpty { setOf(schedule.clampedZc) }
        return MeetingEntity(
            rwh = rwh, xnxq = xnxq, role = "主任务",
            teachers = jsxm,
            weeks = weeks.sorted().joinToString(","),
            weekday = weekday, startPeriod = qsjc, endPeriod = jsjc,
            startTime = start, endTime = end,
            room = jsdd, rawText = "$kcmc $zcs",
        )
    }

    /**
     * 解析周次文案。实测形态（珠江学院）：
     * `1-16周` / `1-4,6-17周` / `1,3,5周` / `第1-16周`
     * —— 逗号分段，每段可以是 `a-b` 区间或单值。之前把「区间+枚举混合」
     * 解析成 {1,5,7,17}，导致第 2/3/4 周课程凭空消失，勿回退此实现。
     */
    internal fun parseWeeks(zcs: String): Set<Int> = parseWeeksOf(zcs)
}

/**
 * 解析周次文案。实测形态（珠江学院）：
 * `1-16周` / `1-4,6-17周` / `1,3,5周` / `第1-16周`
 * —— 逗号分段，每段可以是 `a-b` 区间或单值。之前把「区间+枚举混合」
 * 解析成 {1,5,7,17}，导致第 2/3/4 周课程凭空消失，勿回退此实现。
 */
internal fun parseWeeksOf(zcs: String): Set<Int> {
    if (zcs.isBlank()) return emptySet()
    val out = sortedSetOf<Int>()
    for (segment in zcs.replace("第", "").replace("周", "").split(',')) {
        val seg = segment.trim()
        if (seg.isEmpty()) continue
        val range = Regex("""^(\d+)\s*-\s*(\d+)$""").find(seg)
        if (range != null) {
            val a = range.groupValues[1].toIntOrNull() ?: continue
            val b = range.groupValues[2].toIntOrNull() ?: continue
            if (a in 1..30 && b in 1..30 && a <= b) out.addAll(a..b)
            continue
        }
        val single = seg.toIntOrNull()
        if (single != null && single in 1..30) out.add(single)
    }
    return out
}
