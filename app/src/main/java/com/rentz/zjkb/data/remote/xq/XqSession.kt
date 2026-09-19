package com.rentz.zjkb.data.remote.xq

import org.json.JSONObject

/**
 * 会话状态 —— 按层分开存：学校、用户、认证、动态配置。
 *
 * 分层的意义：刷新登录时只有认证层会变，学校层和动态配置层是复用的。
 * 合并成一坨后，一次失败的重登会把还能用的只读会话一起清掉。
 * 序列化也按层来：某一层在磁盘上坏了，只有那一层退化成空值，其余三层照活。
 */
object XqSessionCodec {

    /* --- 学校层 --------------------------------------------------------------- */

    data class School(
        val xxdm: String,
        val xxmc: String,
        /** 该校的业务服务地址。登录和后续业务都打这里，不是管理端。 */
        val serviceUrl: String,
        val pinyin: String = "",
        /** 认证方式。不同值意味着不同登录链路，目前只实现最常见的一种（APP_JW）。 */
        val rzfs: String = "",
        val mode: String = "",
    ) {
        val isUsable: Boolean get() = xxdm.isNotEmpty() && serviceUrl.isNotEmpty()

        fun toJson(): JSONObject = JSONObject().apply {
            put("xxdm", xxdm); put("xxmc", xxmc); put("serviceUrl", serviceUrl)
            put("pinyin", pinyin); put("rzfs", rzfs); put("mode", mode)
        }

        companion object {
            fun fromRow(row: Map<String, Any?>): School = School(
                xxdm = XqWire.pick(row, listOf("xxdm", "schoolCode", "school_code", "code")),
                xxmc = XqWire.pick(row, listOf("xxmc", "schoolName", "school_name", "name")),
                serviceUrl = XqWire.pick(
                    row,
                    listOf(
                        "serviceUrl", "serviceurl", "service_url", "serverUrl",
                        "wapUrl", "agentUrl", "url", "fwqdz",
                    ),
                ),
                pinyin = XqWire.pick(row, listOf("pinyin", "py", "spell")),
                rzfs = XqWire.pick(row, listOf("rzfs")),
                mode = XqWire.pick(row, listOf("mode")),
            )

            fun fromJson(json: Any?): School? {
                if (json !is Map<*, *>) return null
                val school = fromRow(json.entries.associate { it.key.toString() to it.value })
                return if (school.isUsable) school else null
            }
        }
    }

    /* --- 用户层 --------------------------------------------------------------- */

    data class Account(
        val userId: String,
        val usertype: String = "",
        val uuid: String = "",
        val name: String = "",
    ) {
        val isEmpty: Boolean get() = userId.isEmpty()

        fun toJson(): JSONObject = JSONObject().apply {
            put("userid", userId); put("usertype", usertype)
            put("uuid", uuid); put("xm", name)
        }

        companion object {
            fun fromMap(data: Map<String, Any?>, fallbackId: String = ""): Account {
                val id = XqWire.pick(data, listOf("userid", "userId", "user_id", "uid", "xh"))
                return Account(
                    userId = id.ifEmpty { fallbackId },
                    usertype = XqWire.pick(data, listOf("usertype", "userType", "user_type")),
                    uuid = XqWire.pick(data, listOf("uuid")),
                    name = XqWire.pick(data, listOf("xm", "name", "mc")),
                )
            }

            fun fromJson(json: Any?): Account =
                if (json is Map<*, *>) fromMap(json.entries.associate { it.key.toString() to it.value })
                else Account("")
        }
    }

    /* --- 认证层 --------------------------------------------------------------- */

    /** 认证材料。这个类里的每一个字段进日志都算泄漏。 */
    data class Credentials(val token: String = "", val jwt: String = "") {
        val isEmpty: Boolean get() = token.isEmpty() && jwt.isEmpty()

        /** 请求头（JWT 通过 Authorization_kingo 发送，不带 Bearer 前缀）。 */
        val headers: Map<String, String>
            get() = if (jwt.isNotEmpty()) mapOf("Authorization_kingo" to jwt) else emptyMap()

        fun toJson(): JSONObject = JSONObject().apply {
            put("token", token); put("jwt", jwt)
        }

        override fun toString(): String = "Credentials(<已脱敏>)"

        companion object {
            /** 无效值过滤：服务端失败时会把 token 塞成字符串 "error"。 */
            private fun valid(value: String): String {
                val t = value.trim()
                val bad = setOf("error", "null", "none", "false", "undefined", "nan", "0")
                return if (t.lowercase() in bad) "" else t
            }

            fun fromMap(data: Map<String, Any?>): Credentials = Credentials(
                token = valid(XqWire.pick(data, listOf("token", "access_token"))),
                jwt = valid(XqWire.pick(data, listOf("jwt", "Authorization_kingo"))),
            )

            fun fromJson(json: Any?): Credentials =
                if (json is Map<*, *>) fromMap(json.entries.associate { it.key.toString() to it.value })
                else Credentials()
        }
    }

    /* --- 动态配置层 ----------------------------------------------------------- */

    /**
     * 一个子系统的动态路由配置。课表这类接口不走管理端，而是被重写到
     * 学校自建的子系统：`{base}/wap/{backend}{suffix}`，用的是学校自己的 param key。
     */
    data class VendorConfig(
        val vendor: String,
        val base: String,
        val suffix: String = ".action",
        val key: String = "",
    ) {
        val isUsable: Boolean get() = base.isNotEmpty()

        /** 拼出真实端点。 */
        fun endpoint(backend: String): String {
            val b = if (base.endsWith("/")) base.substring(0, base.length - 1) else base
            return "$b/wap/$backend$suffix"
        }

        val isCleartext: Boolean get() = base.lowercase().startsWith("http://")

        fun toJson(): JSONObject = JSONObject().apply {
            put("vendor", vendor); put("base", base); put("suffix", suffix); put("key", key)
        }

        companion object {
            fun fromJson(vendor: String, json: Any?): VendorConfig? {
                if (json !is Map<*, *>) return null
                val m = json.entries.associate { it.key.toString() to it.value }
                val base = RuntimeConfig.normalizeBase(XqWire.pick(m, listOf("base")))
                if (base.isEmpty()) return null
                val suffix = XqWire.pick(m, listOf("suffix"))
                return VendorConfig(
                    vendor = vendor,
                    base = base,
                    suffix = suffix.ifEmpty { ".action" },
                    key = XqWire.pick(m, listOf("key")),
                )
            }
        }
    }

    /** 登录后服务端下发的整套动态配置。 */
    data class RuntimeConfig(val vendors: Map<String, VendorConfig> = emptyMap()) {
        operator fun get(vendor: String): VendorConfig? = vendors[vendor]
        val isEmpty: Boolean get() = vendors.isEmpty()

        fun toJson(): JSONObject = JSONObject().apply {
            for ((k, v) in vendors) put(k, v.toJson())
        }

        companion object {
            /**
             * 从登录响应里挖动态配置。
             *
             * 主路径（官方实证）：登录响应的 `info` 数组，元素形如
             * `{"os":"jw","url":"http://...:801/...","encrpt":"<key>","type":".action"}`。
             * 兜底路径：旧版 `xqerConfig` 嵌套 Map。两条都挖，`info` 优先。
             * 挖不到是正常情况 —— 有的学校业务全走管理端。
             */
            fun fromLogin(response: Any?): RuntimeConfig {
                val vendors = LinkedHashMap<String, VendorConfig>()
                if (response !is Map<*, *>) return RuntimeConfig(vendors)

                val info = response["info"]
                if (info is List<*>) {
                    for (item in info) {
                        if (item !is Map<*, *>) continue
                        val row = item.entries.associate { it.key.toString() to it.value }
                        val os = XqWire.pick(row, listOf("os"))
                        val url = normalizeBase(XqWire.pick(row, listOf("url")))
                        if (os.isEmpty() || url.isEmpty()) continue
                        vendors[os] = VendorConfig(
                            vendor = os,
                            base = url,
                            suffix = normalizeSuffix(XqWire.pick(row, listOf("type"))),
                            key = XqWire.pick(row, listOf("encrpt")),
                        )
                    }
                }

                val config = XqWire.firstMap(
                    response,
                    listOf("xqerConfig", "xqerconfig", "runtime", "config"),
                )
                for (vendor in listOf("jw", "xz", "ot", "sx")) {
                    if (vendors.containsKey(vendor)) continue
                    val raw = XqWire.firstMap(config, listOf(vendor, vendor.uppercase()))
                    if (raw.isEmpty()) continue
                    val base = normalizeBase(
                        XqWire.pick(raw, listOf("base", "url", "address", "dz")),
                    )
                    if (base.isEmpty()) continue
                    vendors[vendor] = VendorConfig(
                        vendor = vendor,
                        base = base,
                        suffix = normalizeSuffix(XqWire.pick(raw, listOf("suffix", "ext", "type"))),
                        key = XqWire.pick(raw, listOf("key", "zdykey", "paramKey", "encrpt")),
                    )
                }
                return RuntimeConfig(vendors)
            }

            /** 后缀规范化：`action` → `.action`；`.action` 原样保留；空给默认。 */
            private fun normalizeSuffix(value: String): String {
                val text = value.trim()
                if (text.isEmpty()) return ".action"
                return if (text.startsWith(".")) text else ".$text"
            }

            /** 基址规范化：补协议、去尾斜杠。 */
            fun normalizeBase(value: String): String {
                var text = value.trim()
                if (text.isEmpty()) return ""
                while (text.endsWith("/")) text = text.substring(0, text.length - 1)
                if (!text.lowercase().startsWith("http")) {
                    if (!text.contains(".") && !text.contains(":")) return ""
                    text = "http://$text"
                }
                return try {
                    if (java.net.URI(text).host?.isNotEmpty() == true) text else ""
                } catch (_: Exception) {
                    ""
                }
            }

            fun fromJson(json: Any?): RuntimeConfig {
                if (json !is Map<*, *>) return RuntimeConfig()
                val vendors = LinkedHashMap<String, VendorConfig>()
                for ((k, v) in json) {
                    val config = VendorConfig.fromJson(k.toString(), v)
                    // 单个子系统解析不出来就跳过它，别让一条坏配置带走整份路由表。
                    if (config != null) vendors[k.toString()] = config
                }
                return RuntimeConfig(vendors)
            }
        }
    }

    /* --- 会话 ----------------------------------------------------------------- */

    /** 完整会话 —— 四层的组合。不可变：每次更新返回新实例。 */
    data class Session(
        val school: School? = null,
        val account: Account = Account(""),
        val credentials: Credentials = Credentials(),
        val runtime: RuntimeConfig = RuntimeConfig(),
    ) {
        /** 有身份、有凭据、有学校地址才算真的登录上了。 */
        val isAuthenticated: Boolean
            get() = !account.isEmpty && !credentials.isEmpty && school?.isUsable == true

        /** 业务请求都要带的公共参数。 */
        val commonParams: Map<String, String>
            get() = buildMap {
                if (account.userId.isNotEmpty()) {
                    put("userId", account.userId)
                    put("userid", account.userId)
                }
                if (account.usertype.isNotEmpty()) put("usertype", account.usertype)
                school?.let { if (it.xxdm.isNotEmpty()) put("xxdm", it.xxdm) }
            }

        /** 只换某一层，其余层原样带过来。 */
        fun copyWith(
            school: School? = null,
            account: Account? = null,
            credentials: Credentials? = null,
            runtime: RuntimeConfig? = null,
        ) = Session(
            school = school ?: this.school,
            account = account ?: this.account,
            credentials = credentials ?: this.credentials,
            runtime = runtime ?: this.runtime,
        )

        /** 退出登录：清掉用户层和认证层，保留学校层 —— 它本身不敏感。 */
        fun signedOut() = Session(school = school)

        fun toJson(): JSONObject = JSONObject().apply {
            school?.let { put("school", it.toJson()) }
            put("account", account.toJson())
            put("credentials", credentials.toJson())
            put("runtime", runtime.toJson())
        }

        companion object {
            /** 每层独立兜底：某层坏了退化成那层的空值，不影响其它三层。 */
            fun fromJson(text: String?): Session {
                if (text.isNullOrBlank()) return Session()
                return try {
                    val m = XqWire.parseJson(text) as? Map<*, *> ?: return Session()
                    Session(
                        school = School.fromJson(m["school"]),
                        account = Account.fromJson(m["account"]),
                        credentials = Credentials.fromJson(m["credentials"]),
                        runtime = RuntimeConfig.fromJson(m["runtime"]),
                    )
                } catch (_: Exception) {
                    Session()
                }
            }
        }
    }
}
