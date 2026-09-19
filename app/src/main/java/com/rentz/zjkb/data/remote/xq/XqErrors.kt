package com.rentz.zjkb.data.remote.xq

/**
 * 网络层异常与日志脱敏。
 *
 * 分成五类是为了让 UI 能说人话：网络不通、协议看不懂、账号密码不对、
 * 会话过期要重登、需要二次验证 —— 这几件事在界面上的处置完全不同。
 */
sealed class XqException(message: String) : Exception(message) {
    /** 连不上、超时、HTTP 5xx。可以重试。 */
    class Network(message: String) : XqException(message)

    /** 连上了但读不懂：响应不是 JSON、结构对不上、AES 解不开。重试没用。 */
    class Protocol(message: String) : XqException(message)

    /** 账号或密码不对、学校不支持这种登录方式。要用户改输入。 */
    class Auth(message: String) : XqException(message)

    /** 会话过期 / 被强制下线。要重新登录，但输入可能是对的。 */
    class SessionExpired(message: String) : XqException(message)

    /**
     * 需要验证码、短信验证、设备绑定等二次验证。
     * 这条路不走：二次验证必须由账户持有人在官方客户端或学校门户完成，
     * 兼容客户端不实现、不代填、不绕过。
     */
    class NeedsVerification(message: String) : XqException(message)
}

/* --- 日志脱敏 -------------------------------------------------------------- */

/** 出现在日志里就算泄漏的键。大小写不敏感。 */
private val SENSITIVE_KEYS = setOf(
    "password", "passwd", "pwd", "pwdstr", "token", "jwt", "authorization",
    "authorization_kingo", "cookie", "set-cookie", "param", "param2",
    "xqersign", "encrptsecretkey", "loginid", "userid", "xh", "echo",
)

/** 键值对脱敏 —— 打印请求体时用这个，不要直接打 body。 */
fun redactMap(body: Map<String, String>): Map<String, String> =
    body.mapValues { (k, v) -> if (k.lowercase() in SENSITIVE_KEYS) "<已脱敏>" else v }

/** 文本脱敏：键名沾边就整个值换掉，不做「只遮一半」的花活。 */
fun redact(value: Any?, limit: Int = 180): String {
    var text = value?.toString() ?: ""
    text = text.replace(Regex("[\\r\\n]+"), " ")
    text = text.replace(
        Regex(
            "(${SENSITIVE_KEYS.joinToString("|")})\\s*[:=]\\s*(?:(?:bearer|basic|token)\\s+)?[^\\s,;&<>\"']+",
            RegexOption.IGNORE_CASE,
        ),
    ) { "${it.groupValues[1]}=<已脱敏>" }
    return if (text.length > limit) text.substring(0, limit) + "…" else text
}
