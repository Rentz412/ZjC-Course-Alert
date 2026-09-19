package com.rentz.zjkb.data.remote.xq

import com.rentz.zjkb.data.remote.xq.XqSessionCodec.Account
import com.rentz.zjkb.data.remote.xq.XqSessionCodec.Credentials
import com.rentz.zjkb.data.remote.xq.XqSessionCodec.RuntimeConfig
import com.rentz.zjkb.data.remote.xq.XqSessionCodec.School
import com.rentz.zjkb.data.remote.xq.XqSessionCodec.Session
import com.rentz.zjkb.data.remote.xq.XqSessionCodec.VendorConfig

/**
 * 业务接口客户端。
 *
 * 两条通道，选哪条由**登录后服务端下发的动态配置**决定：
 *
 *  1. 管理端通道 `{serviceUrl}/wap/wapController.jsp`：完整九键信封，
 *     POST form。学校发现、登录、大部分业务走这条。
 *  2. 子系统通道 `{runtime.jw.base}/wap/{backend}{suffix}`：只发
 *     `param` 和 `param2`，用学校自己的 key。课表实测走这条。
 *
 * 通道二没有配置就不猜：拿不到配置就老实走通道一。
 */
class XqApi(
    private val transport: XqTransport = XqTransport(),
    private var session: Session = Session(),
) {
    companion object {
        /** 设备标识。不冒充真机型号 —— 老实报出这是兼容客户端。 */
        const val DEVICE_NAME = "ZjkbCompatible"
        /** 本校写死：华南农业大学珠江学院。 */
        const val SCHOOL_XXDM = "12623"
        const val SCHOOL_XXMC = "华南农业大学珠江学院"
    }

    var currentSession: Session
        get() = session
        private set(value) { session = value }

    /** 换掉会话（登录成功、退出登录时）。 */
    fun adopt(value: Session) { session = value }

    /* --- 通道一：管理端信封 ------------------------------------------------- */

    /**
     * 管理端签名信封：业务参数 → 签名九键 + 外层字段。
     *
     * 登录后的请求（token 非空）走官方 `C0747b.m3942u(z10=true)` 形态：
     * 签名原文追加 `&xqerxm={转义姓名}&uuid={uuid}&md5=`。缺了这段
     * 管理端会回「口令失败」。登录本身（token 还是 00000）不追加。
     */
    private fun managerEnvelope(
        payload: Map<String, String>,
        preserveWapRoute: Boolean = true,
    ): Map<String, String> {
        val hasToken = session.credentials.token.isNotEmpty()
        val body = (
            if (hasToken) {
                XqSigner.signedBodyWithProfile(
                    payload,
                    xm = session.account.name,
                    uuid = session.account.uuid,
                    preserveWapRoute = preserveWapRoute,
                )
            } else {
                XqSigner.signedBody(payload, preserveWapRoute = preserveWapRoute)
            }
        ).toMutableMap()
        body["appsjxh"] = DEVICE_NAME
        if (hasToken) body["token"] = session.credentials.token
        return body
    }

    /** 走 wapController 的业务请求。[what] 是给用户看的动作描述。 */
    private fun callManager(
        payload: Map<String, String>,
        what: String,
        serviceUrl: String? = null,
        retry: Boolean = true,
    ): Any? {
        val base = serviceUrl ?: session.school?.serviceUrl ?: MANAGER_BASE_URL
        val url = wapControllerUrl(base)
        // token 和 appsjxh 是信封外层字段，不进签名原文（零凭据探针实证）。
        val body = managerEnvelope(payload)
        val headers = buildMap {
            put("User-Agent", USER_AGENT)
            putAll(session.credentials.headers)
        }
        val response = transport.postForm(url, body, retry = retry)
        return XqWire.decodeResponse(response.body, what)
    }

    /** 管理端 GET servlet（baseInfoServlet 这类）。签名九键整体进 query。 */
    private fun callManagerGet(
        path: String,
        payload: Map<String, String>,
        what: String,
    ): Any? {
        val base = normalizeServiceUrl(session.school?.serviceUrl ?: MANAGER_BASE_URL)
            ?: throw XqException.Protocol("学校服务地址无效。")
        val query = managerEnvelope(payload, preserveWapRoute = false)
        val sb = StringBuilder(base)
        val p = if (path.startsWith("/")) path else "/$path"
        sb.append(p)
        sb.append('?')
        sb.append(query.entries.joinToString("&") {
            "${urlEncode(it.key)}=${urlEncode(it.value)}"
        })
        val response = transport.get(sb.toString())
        return XqWire.decodeResponse(response.body, what)
    }

    private fun urlEncode(v: String): String =
        java.net.URLEncoder.encode(v, "UTF-8")

    /* --- 通道二：子系统直连 ------------------------------------------------- */

    /**
     * 走学校子系统的业务请求。只发 param/param2，用该子系统的 key。
     *
     * 加密原文的拼法与官方 `C0747b.m3946y`（明文源码实证）一致：
     * **剔除 `action`/`step`/`isZLPar` 三个路由字段**，路由信息只进
     * URL 路径。多拼了 action/step 会被子系统静默拒掉（0 字节响应）。
     */
    private fun callVendor(
        vendor: VendorConfig,
        backend: String,
        payload: Map<String, String>,
        what: String,
    ): Any? {
        val raw = payload.entries
            .filter { it.key.trim() !in setOf("action", "step", "isZLPar") }
            .joinToString("&") { "${it.key.trim()}=${it.value}" }
        val key = vendor.key.ifEmpty { XqSigner.ZDY_KEY }
        val response = transport.postForm(
            vendor.endpoint(backend),
            mapOf(
                "param" to XqSigner.ndkEncryptZdy(raw, key),
                "param2" to XqSigner.ndkParam2(raw),
            ),
        )
        return XqWire.decodeResponse(response.body, what)
    }

    /** 有子系统配置就走通道二，没有就走通道一。 */
    private fun call(
        payload: Map<String, String>,
        what: String,
        vendor: String,
        backend: String,
    ): Any? {
        val config = session.runtime[vendor]
        return if (config != null && config.isUsable) {
            callVendor(config, backend, payload, what)
        } else {
            callManager(payload, what)
        }
    }

    /* --- 学校发现 ----------------------------------------------------------- */

    /**
     * 按名称或代码查学校。这一步不需要任何凭据，也是零凭据自检的入口。
     * 本 App 学校写死，仅作兜底与诊断用。
     */
    fun findSchools(name: String = "", code: String = ""): List<School> {
        if (name.isBlank() && code.isBlank()) return emptyList()
        val payload = buildMap {
            put("action", if (code.isNotBlank()) "getAgentByXxdm" else "getAgent")
            put("appver", XqSigner.APP_VERSION)
            if (name.isNotBlank()) put("xxmc", name.trim())
            if (code.isNotBlank()) put("xxdm", code.trim())
        }
        val parsed = callManager(payload, "查找学校", serviceUrl = MANAGER_BASE_URL)
        val schools = XqWire.rowsOf(parsed).map(School::fromRow).filter { it.isUsable }
        if (code.isNotBlank()) {
            val exact = schools.filter { it.xxdm == code.trim() }
            if (exact.isNotEmpty()) return exact
        }
        if (name.isNotBlank()) {
            val target = name.trim().lowercase()
            val matched = schools.filter { s ->
                s.xxmc.lowercase().contains(target) || s.pinyin.lowercase().contains(target) || s.xxdm == target
            }.sortedWith(
                compareBy<School> { if (it.xxmc.lowercase() == target) 0 else 1 }
                    .thenBy { it.xxmc.length },
            )
            if (matched.isNotEmpty()) return matched
        }
        return schools
    }

    /* --- 登录 --------------------------------------------------------------- */

    class LoginResult(val session: Session, val profile: XqModels.UserInfo)

    /**
     * 登录。只发一种载荷组合：`getLoginInfoNew` + 明文密码 + `pwdsfzm=1`
     * （实测证据：明文被服务器接受，AES 密文反被拒）。
     * 载荷字段顺序由 [XqSigner.LOGIN_FIELD_ORDER] 固定。
     */
    fun login(loginId: String, password: String): LoginResult {
        if (loginId.isBlank() || password.isEmpty()) {
            throw XqException.Auth("请输入学号和密码。")
        }
        val school = session.school?.takeIf { it.isUsable }
            ?: School(xxdm = SCHOOL_XXDM, xxmc = SCHOOL_XXMC, serviceUrl = SCHOOL_LOOKUP_URL)

        val payload = linkedMapOf(
            "pwdsfzm" to "1",
            "loginId" to loginId.trim(),
            "sswl" to "",
            "os" to "android",
            "xtbb" to "13",
            "appver" to XqSigner.APP_VERSION,
            "isky" to "1",
            "zddl" to "1",
            "xxdm" to school.xxdm,
            "checktoken" to "true",
            "sjxh" to DEVICE_NAME,
            "action" to "getLoginInfoNew",
            "sjbz" to "",
            // 明文进签名。敏感，只进内存；任何日志路径都不能打。
            "pwd" to password,
            "loginmode" to "0",
        )

        // 登录不重试：失败可能是密码错，重发只会加速账号锁定。
        val parsed = callManager(payload, "登录", serviceUrl = school.serviceUrl, retry = false)
        return sessionFromLogin(parsed, school, loginId.trim())
    }

    /** 登录响应 → 会话。四层分别从响应里挖，挖不到的层保持空。 */
    private fun sessionFromLogin(response: Any?, school: School, loginId: String): LoginResult {
        // 会话材料可能在顶层，也可能裹在 data/result/person 里。
        val inner = XqWire.firstMap(
            response,
            listOf(
                "data", "result", "resultSet", "person", "user", "student",
                "personMessage", "PersonMessage", "LOGINSTR",
            ),
        )
        val top = (response as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
        val merged = LinkedHashMap<String, Any?>().apply {
            top?.let { putAll(it) }
            putAll(inner)
        }

        val credentials = Credentials.fromMap(merged)
        if (credentials.isEmpty) {
            // 业务信封判定认为「成功」但里面没有凭据 —— 多半是学校用了
            // 别的认证模式，如实说，不要假装登录成功。
            throw XqException.Auth(
                "服务器接受了请求但没有返回登录凭据，这所学校可能使用了本客户端尚未支持的认证方式。",
            )
        }

        val resolvedUrl = normalizeServiceUrl(
            XqWire.pick(merged, listOf("serviceUrl", "serviceurl")),
        ) ?: school.serviceUrl
        val resolvedSchool = school.copy(serviceUrl = resolvedUrl)

        val profile = XqModels.UserInfo.fromJson(merged)
        return LoginResult(
            session = Session(
                school = resolvedSchool,
                account = Account.fromMap(merged, fallbackId = loginId),
                credentials = credentials,
                runtime = RuntimeConfig.fromLogin(response),
            ),
            profile = XqModels.UserInfo(
                xm = profile.xm,
                xh = profile.xh.ifEmpty { loginId },
                bjmc = profile.bjmc,
                xxmc = profile.xxmc.ifEmpty { school.xxmc },
                xb = profile.xb,
                rxnj = profile.rxnj,
            ),
        )
    }

    /* --- 业务读取 ----------------------------------------------------------- */

    /** 学期列表（RUNTIME-SCHEMA-03）。 */
    fun semesters(): List<XqModels.Semester> {
        val parsed = call(
            mapOf(
                "userId" to session.account.userId,
                "usertype" to session.account.usertype,
                "action" to "getKb",
                "step" to "xnxq",
            ),
            what = "读取学期列表",
            vendor = "jw",
            backend = "getxnxq_kb",
        )
        val rows = if (parsed is Map<*, *> && parsed.containsKey("xnxq")) {
            XqWire.rowsOf(parsed["xnxq"])
        } else {
            XqWire.rowsOf(parsed)
        }
        return rows.map(XqModels.Semester::fromJson).filter { it.dm.isNotEmpty() }
    }

    /** 个人资料：`GET /wap/baseInfoServlet?step=list`（管理端，带签名信封）。 */
    fun profile(): XqModels.UserInfo {
        val parsed = callManagerGet(
            "/wap/baseInfoServlet",
            mapOf(
                "userId" to session.account.userId,
                "usertype" to session.account.usertype,
                "step" to "list",
            ),
            what = "读取个人资料",
        )
        val map = (parsed as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
            ?: emptyMap()
        val p = XqModels.UserInfo.fromJson(map)
        return XqModels.UserInfo(
            xm = p.xm,
            xh = session.account.userId,
            bjmc = p.bjmc,
            xxmc = p.xxmc,
            xb = p.xb,
            rxnj = p.rxnj,
        )
    }

    /**
     * 课表（RUNTIME-SCHEMA-09 / RUNTIME-012）。
     *
     * 载荷对齐官方 `WeekCourseFragment`：usertype/action=getKb/step=kbdetail_bz/
     * bjdm/jsdm/xnxq/week。分支语义照官方：
     *   * jw 子系统就绪 → `userid`（小写，去学校前缀 `xxx_`）；
     *   * 否则 → `userId`（完整）+ `channel=jrkb`。
     */
    fun schedule(xnxq: String = "", week: Int? = null): XqModels.Schedule {
        val payload = linkedMapOf(
            "usertype" to session.account.usertype,
            "action" to "getKb",
            "step" to "kbdetail_bz",
            "bjdm" to "",
            "jsdm" to "",
            "xnxq" to xnxq,
            "week" to (week?.toString() ?: ""),
        )
        val jw = session.runtime["jw"]
        if (jw != null && jw.isUsable) {
            val uid = session.account.userId
            payload["userid"] = if (uid.contains("_")) uid.substring(uid.indexOf('_') + 1) else uid
        } else {
            payload["userId"] = session.account.userId
            payload["channel"] = "jrkb"
        }
        val parsed = call(payload, "读取课表", vendor = "jw", backend = "mycourseschedule")

        if (parsed !is Map<*, *>) {
            throw XqException.Protocol("读取课表失败：返回的数据结构不是课表。")
        }
        val json = parsed.entries.associate { it.key.toString() to it.value }
        // sjhjinfo 是课表的骨架 —— 没有它就画不出时间轴，与其画错不如说清楚。
        if (!json.containsKey("sjhjinfo") && !json.containsKey("week1")) {
            throw XqException.Protocol(
                "读取课表失败：返回的数据里没有课程表结构，这所学校的课表接口可能不是标准形态。",
            )
        }
        return XqModels.Schedule.fromJson(json)
    }
}
