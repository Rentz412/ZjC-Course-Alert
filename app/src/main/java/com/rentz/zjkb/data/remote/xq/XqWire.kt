package com.rentz.zjkb.data.remote.xq

import org.json.JSONArray
import org.json.JSONObject

/**
 * 响应解码管线。
 *
 * 顺序（接口资料定案）：HTTP 2xx → 业务错误信封 → 必要时条件 AES 整包解密
 * → JSON 规范化 → 递归处理 *_desn。
 *
 * 服务端响应 Content-Type 写的是 text/html，实际内容可能是裸 JSON、
 * HTML 实体转义、URL 编码、整包 AES base64、或字段单独加密（*_desn）。
 * 每层都得试，试不出来要老实报错，不能返回半个对象让上层以为成功了。
 */
object XqWire {

    /* --- 文本反转义 ---------------------------------------------------------- */

    private val HTML_ENTITIES = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
        "&apos;" to "'", "&nbsp;" to " ", "&#39;" to "'", "&#x27;" to "'",
        "&#34;" to "\"", "&#x22;" to "\"",
    )

    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
    private val PERCENT_UNICODE = Regex("%u([0-9a-fA-F]{4})")

    fun unescapeHtml(input: String): String {
        var text = input
        for ((k, v) in HTML_ENTITIES) text = text.replace(k, v)
        return text.replace(NUMERIC_ENTITY) { m ->
            val radix = if (m.groupValues[1].isEmpty()) 10 else 16
            val code = m.groupValues[2].toIntOrNull(radix)
            if (code == null) m.value else code.toChar().toString()
        }
    }

    /** URL 解码。解不动就原样返回 —— 半个解码结果比不解码更糟。 */
    private fun tryPercentDecode(input: String): String {
        val text = input.replace(PERCENT_UNICODE) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
        if (!text.contains("%")) return text
        return try {
            java.net.URLDecoder.decode(text, "UTF-8")
        } catch (_: Exception) {
            text
        }
    }

    /* --- JSON 候选提取 ------------------------------------------------------- */

    /** 从一段可能带前后杂物的文本里挑出可解析的 JSON 片段。 */
    private fun jsonCandidates(text: String): List<String> {
        val trimmed = text.trim()
        val out = ArrayList<String>()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) out.add(trimmed)
        for (pair in listOf("{" to "}", "[" to "]")) {
            val start = trimmed.indexOf(pair.first)
            val end = trimmed.lastIndexOf(pair.second)
            if (start != -1 && end > start) {
                val slice = trimmed.substring(start, end + 1)
                if (slice !in out) out.add(slice)
            }
        }
        return out
    }

    private fun tryJson(text: String): Any? {
        for (candidate in jsonCandidates(text)) {
            try {
                return parseJson(candidate)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    /** JSON 文本 → Map/<List>/基本值（org.json 无“任意值”入口，这里做一个）。 */
    fun parseJson(text: String): Any? {
        val trimmed = text.trim()
        return if (trimmed.startsWith("{")) {
            JSONObject(trimmed).toMap()
        } else if (trimmed.startsWith("[")) {
            JSONArray(trimmed).toList()
        } else {
            throw IllegalArgumentException("not json")
        }
    }

    /** 任意响应文本 → JSON 值。试的顺序：原文 → HTML 反转义 → URL 解码 → AES 整包解密。
     * 全都不行返回原始字符串（不抛异常）—— 纯文本接口该不该报错由调用方决定。 */
    fun decodeLoose(value: Any?): Any? = decodeLoose(value, 0)

    private fun decodeLoose(value: Any?, depth: Int): Any? {
        if (value is Map<*, *> || value is List<*>) return value
        val raw = value?.toString()?.trim() ?: ""
        if (raw.isEmpty()) return ""

        for (variant in listOf(raw, unescapeHtml(raw), tryPercentDecode(raw))) {
            val parsed = tryJson(variant)
            if (parsed != null) return parsed
        }

        // 整包 AES：登录响应和部分业务响应会把整个 JSON 加密后 base64 返回。
        // 密文外可能还裹着 HTML 实体或 URL 编码，剥壳在 tryAes 里做。
        for (variant in listOf(raw, unescapeHtml(raw))) {
            val plain = tryAes(variant) ?: continue
            if (depth < 4) {
                val parsed = decodeLoose(plain, depth + 1)
                if (parsed is Map<*, *> || parsed is List<*>) return parsed
            }
            if (plain.isNotEmpty()) return plain
        }
        return raw
    }

    /**
     * AES/CBC 整包解密。不是密文（base64 不合法、填充不对、解出来不是 UTF-8）
     * 一律返回 null。
     *
     * 进解密前要先剥两层包装，顺序不能反：
     *  1. 空白（见过 MIME 风格每 76 字符折一行）；
     *  2. URL 编码（登录接口实测：整包 AES 密文再 URL 编码，`+`→`%2B`）。
     */
    private fun tryAes(text: String): String? {
        var candidate = text.replace(Regex("\\s"), "")
        if (candidate.contains("%")) {
            candidate = tryPercentDecode(candidate).replace(Regex("\\s"), "")
        }
        if (candidate.length < 24 || candidate.length % 4 != 0) return null
        if (!Regex("^[A-Za-z0-9+/]+={0,2}$").matches(candidate)) return null
        return XqSigner.aesCbcDecryptB64(candidate)
    }

    /* --- 递归 *_desn --------------------------------------------------------- */

    /**
     * 递归解密 `*_desn` 字段：`foo_desn` 是 `foo` 的密文形态，解开后写回 `foo`。
     * 解不开保留原样 —— 宁可让 UI 显示一串 base64，也不能悄悄吞掉字段。
     */
    @Suppress("UNCHECKED_CAST")
    fun decryptDesnFields(value: Any?, depth: Int = 0): Any? {
        if (depth > 8) return value
        if (value is List<*>) {
            return value.map { decryptDesnFields(it, depth + 1) }
        }
        if (value !is Map<*, *>) return value

        val out = LinkedHashMap<String, Any?>()
        for ((k, item) in value) {
            val name = k.toString()
            if (!name.lowercase().endsWith("_desn")) {
                out[name] = decryptDesnFields(item, depth + 1)
                continue
            }
            val plainKey = name.substring(0, name.length - 5)
            val decrypted = tryAes(item?.toString() ?: "")
            if (decrypted == null) {
                out[name] = item
                continue
            }
            val parsed = decodeLoose(decrypted)
            out[if (plainKey.isEmpty()) name else plainKey] = decryptDesnFields(parsed, depth + 1)
        }
        return out
    }

    /* --- 业务信封判定 -------------------------------------------------------- */

    private val STATUS_KEYS = listOf("errcode", "flag", "state", "success", "status", "code")
    private val OK_VALUES = setOf("0", "1", "true", "success", "ok", "200")

    /** 业务是否成功。HTTP 200 不等于业务成功 —— 这套服务端失败也回 200。 */
    fun isBusinessSuccess(value: Any?): Boolean {
        if (value !is Map<*, *>) return true
        for (key in STATUS_KEYS) {
            if (!value.containsKey(key)) continue
            val raw = value[key]
            if (raw is Boolean) return raw
            val normalized = raw?.toString()?.trim()?.lowercase() ?: ""
            if (normalized.isEmpty()) continue
            return when (key) {
                "errcode", "code" ->
                    normalized == "0" || normalized == "200" ||
                        normalized == "success" || normalized == "ok"
                "flag" ->
                    normalized == "0" || normalized == "true" ||
                        normalized == "success" || normalized == "ok"
                else -> normalized in OK_VALUES
            }
        }
        return true
    }

    /** 业务错误消息（给用户看的那一句）。 */
    fun businessMessage(value: Any?): String {
        if (value !is Map<*, *>) return ""
        for (key in listOf("msg", "message", "errmsg", "error", "result")) {
            val item = value[key] ?: continue
            if (item is Map<*, *> || item is List<*>) continue
            val text = item.toString().trim()
            if (text.isNotEmpty()) return unescapeHtml(text)
        }
        return ""
    }

    /* --- 失败标记 ------------------------------------------------------------ */

    private val AUTH_FAILURE_MARKERS = listOf(
        "账号或密码错误", "账号密码错误", "帐号或密码错误", "帐号密码错误", "密码错误",
        "密码不正确", "用户名或密码", "账号不存在", "帐号不存在", "账户不存在",
        "用户不存在", "登录失败", "认证失败", "验证失败", "invalid password",
        "incorrect password", "login failed", "not exist",
    )

    private val SESSION_EXPIRED_MARKERS = listOf(
        "重新登录", "重新登陆", "会话已过期", "会话失效", "登录已过期", "登录超时",
        "未登录", "强制退出", "被踢", "relogin", "session expired",
    )

    private val VERIFICATION_MARKERS = listOf(
        "验证码", "短信验证", "动态口令", "人机验证", "滑动验证", "设备绑定",
        "设备认证", "首次登录需", "captcha", "verification code",
    )

    private fun containsAny(text: String, markers: List<String>): Boolean {
        val lowered = text.lowercase()
        return markers.any { lowered.contains(it.lowercase()) }
    }

    /** 把整个响应拍平成一段文本用于关键词判定（失败信息可能藏在嵌套对象里）。 */
    fun flattenText(value: Any?, depth: Int = 0): String {
        if (depth > 5) return ""
        return when (value) {
            is Map<*, *> -> value.entries.joinToString(" ") { "${it.key}=${flattenText(it.value, depth + 1)}" }
            is List<*> -> value.joinToString(" ") { flattenText(it, depth + 1) }
            else -> unescapeHtml(value?.toString() ?: "")
        }
    }

    fun hasAuthFailureMarker(value: Any?) = containsAny(flattenText(value), AUTH_FAILURE_MARKERS)
    fun hasSessionExpiredMarker(value: Any?) = containsAny(flattenText(value), SESSION_EXPIRED_MARKERS)
    fun hasVerificationMarker(value: Any?) = containsAny(flattenText(value), VERIFICATION_MARKERS)

    /* --- 完整管线 ------------------------------------------------------------ */

    /** 原始响应文本 → 可用的业务数据。唯一的响应入口，所有接口都从这儿过。 */
    fun decodeResponse(body: String, what: String): Any? {
        val decoded = decodeLoose(body)

        if (decoded is String) {
            if (decoded.isBlank()) {
                throw XqException.Protocol("${what}失败：服务器返回了空响应")
            }
            throwIfBusinessFailure(decoded, what, decoded)
            throw XqException.Protocol(
                "${what}失败：服务器返回的不是可识别的数据。${shapeHint(decoded)}",
            )
        }

        val resolved = decryptDesnFields(decoded)
        throwIfBusinessFailure(resolved, what, null)
        return resolved
    }

    /** 「解不开」时给用户和排查者的那句补充。 */
    private fun shapeHint(text: String): String {
        val head = text.trimStart()
        val lowered = head.lowercase()
        if (lowered.startsWith("<!doctype") || lowered.startsWith("<html") || head.contains("<title")) {
            return "服务器回的是一个网页而不是数据 —— 多半是这所学校的接口地址" +
                "和这个客户端认识的不一样，或者这条请求被中间的网关/门户拦下了。" +
                "响应开头：「${redact(head, limit = 90)}」"
        }
        if (lowered.startsWith("<?xml") || lowered.startsWith("<")) {
            return "服务器回的是一段标记语言而不是 JSON。响应开头：「${redact(head, limit = 90)}」"
        }
        return "响应开头：「${redact(head, limit = 110)}」"
    }

    /**
     * 按优先级把业务失败翻译成异常。
     * 顺序：二次验证 > 会话过期 > 账号密码 > 通用失败。
     */
    private fun throwIfBusinessFailure(value: Any?, what: String, fallback: String?) {
        val message = businessMessage(value)
        val detail = if (message.isNotEmpty()) message else redact(fallback ?: "")

        if (hasVerificationMarker(value)) {
            throw XqException.NeedsVerification(
                "${what}需要二次验证（$detail）。请在学校官方客户端或门户完成验证，本客户端不代为处理。",
            )
        }
        if (hasSessionExpiredMarker(value)) {
            throw XqException.SessionExpired("登录状态已失效，需要重新登录。")
        }
        if (hasAuthFailureMarker(value)) {
            throw XqException.Auth(if (message.isNotEmpty()) message else "账号或密码不正确。")
        }
        if (!isBusinessSuccess(value)) {
            throw XqException.Protocol(
                if (message.isNotEmpty()) "${what}失败：$message" else "${what}失败：服务器拒绝了请求",
            )
        }
    }

    /* --- 取值帮手 ------------------------------------------------------------ */

    /** 从 Map 里按多个候选键取第一个非空 Map。 */
    fun firstMap(value: Any?, keys: List<String>): Map<String, Any?> {
        if (value !is Map<*, *>) return emptyMap()
        for (key in keys) {
            val item = value[key]
            if (item is Map<*, *>) {
                return item.entries.associate { it.key.toString() to it.value }
            }
            if (item is String && item.trim().isNotEmpty()) {
                val parsed = decodeLoose(item)
                if (parsed is Map<*, *>) {
                    return parsed.entries.associate { it.key.toString() to it.value }
                }
            }
        }
        return emptyMap()
    }

    /** 从响应里挖出行数组（数组可能在顶层，也可能裹在 resultSet/rows/data 里）。 */
    fun rowsOf(value: Any?, depth: Int = 0): List<Map<String, Any?>> {
        if (depth > 5) return emptyList()
        val parsed = decodeLoose(value)

        if (parsed is List<*>) {
            val rows = ArrayList<Map<String, Any?>>()
            for (item in parsed) {
                when (item) {
                    is Map<*, *> -> rows.add(item.entries.associate { it.key.toString() to it.value })
                    is String -> rows.addAll(rowsOf(item, depth + 1))
                }
            }
            return rows
        }
        if (parsed !is Map<*, *>) return emptyList()

        for (key in listOf(
            "resultSet", "RegisterData", "registerData", "rows", "list", "data",
            "result", "items", "schools", "schoolList", "agentList",
        )) {
            if (!parsed.containsKey(key)) continue
            val rows = rowsOf(parsed[key], depth + 1)
            if (rows.isNotEmpty()) return rows
        }
        return emptyList()
    }

    /** 从 Map 里按多个候选键取第一个非空字符串（键名大小写不敏感）。 */
    fun pick(row: Map<String, Any?>, keys: List<String>): String {
        val lowered = HashMap<String, Any?>()
        for ((k, v) in row) lowered[k.lowercase()] = v
        for (key in keys) {
            val value = row[key] ?: lowered[key.lowercase()]
            val text = value?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) return text
        }
        return ""
    }
}

/* --- org.json 容器转 Kotlin 容器 -------------------------------------------- */

private fun JSONObject.toMap(): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    for (key in keys()) out[key] = wrapValue(this.opt(key))
    return out
}

private fun JSONArray.toList(): List<Any?> {
    val out = ArrayList<Any?>()
    for (i in 0 until length()) out.add(wrapValue(this.opt(i)))
    return out
}

private fun wrapValue(v: Any?): Any? = when (v) {
    is JSONObject -> v.toMap()
    is JSONArray -> v.toList()
    JSONObject.NULL -> null
    else -> v
}
