package com.rentz.zjkb.data.remote.xq

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * HTTP 传输层 —— 只负责「把字节送出去、把字节收回来」。
 *
 * 两条与官方 app 不同的安全决定，都是刻意的：
 *  1. 用系统证书链和主机名校验（官方装的是 trust-all，不复制）；
 *  2. 明文 HTTP 默认放行：课表这条业务路由实测走 http:801（官方 app 同款
 *     拓扑），学校服务地址登录后才下发，没法预先写白名单。默认开着，
 *     想收紧可在设置里关掉。
 */
class XqTransport(
    var allowCleartext: Boolean = true,
    var onLog: ((String) -> Unit)? = null,
    client: OkHttpClient? = null,
) {
    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    class RawResponse(val statusCode: Int, val body: String)

    private fun log(message: String) {
        onLog?.invoke(redact(message))
    }

    /** POST 表单（业务请求默认通道）。[retry]=false 用于登录 —— 失败可能是密码错，重发只会加速账号锁定。 */
    fun postForm(url: String, body: Map<String, String>, retry: Boolean = true): RawResponse =
        send(url, retry) {
            val form = FormBody.Builder(Charsets.UTF_8)
            for ((k, v) in body) form.add(k, v)
            Request.Builder()
                .url(url)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json, text/plain, */*")
                .post(form.build())
                .build()
        }

    /** GET（baseInfoServlet、版本检查用）。 */
    fun get(url: String, retry: Boolean = true): RawResponse =
        send(url, retry) {
            Request.Builder().url(url).header("Accept", "*/*").get().build()
        }

    private fun send(url: String, retry: Boolean, build: () -> Request): RawResponse {
        guardScheme(url)
        val attempts = if (retry) 3 else 1
        var last: XqException.Network? = null
        for (attempt in 1..attempts) {
            try {
                http.newCall(build()).execute().use { response ->
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    // 服务端 Content-Type 常写 text/html 但内容是 UTF-8 JSON，统一按 UTF-8 解。
                    val text = String(bytes, Charsets.UTF_8)
                    when {
                        response.code >= 500 ->
                            last = XqException.Network("服务器暂时不可用（HTTP ${response.code}）。")
                        response.code >= 400 ->
                            throw XqException.Network("请求被服务器拒绝（HTTP ${response.code}）。")
                        else -> {
                            val host = url.toHttpUrl().let { "${it.host}${it.encodedPath}" }
                            log("$host → ${response.code}（${bytes.size} 字节）")
                            return RawResponse(response.code, text)
                        }
                    }
                }
            } catch (e: XqException) {
                throw e
            } catch (e: SocketTimeoutException) {
                last = XqException.Network("连接超时，请检查网络后重试。")
            } catch (e: UnknownHostException) {
                last = XqException.Network("域名解析失败：${redact(e.message ?: "")}")
            } catch (e: SSLException) {
                last = XqException.Network("安全连接失败：${redact(e.message ?: "")}")
            } catch (e: Exception) {
                last = XqException.Network("网络请求失败：${redact(e.message)}")
            }
            if (attempt < attempts) {
                log("第 $attempt 次失败，重试中")
                Thread.sleep(400L * attempt)
            }
        }
        throw last ?: XqException.Network("网络请求失败。")
    }

    /** 协议闸门：明文 HTTP 需 [allowCleartext] 放行。 */
    private fun guardScheme(url: String) {
        val lowered = url.lowercase()
        when {
            lowered.startsWith("https://") -> return
            lowered.startsWith("http://") && allowCleartext -> {
                log("走明文 HTTP：$url")
                return
            }
            lowered.startsWith("http://") ->
                throw XqException.Network(
                    "这条线路是明文 HTTP，数据在传输途中可能被查看或篡改。需要在设置里明确允许后才会连接。",
                )
            else -> throw XqException.Network("不支持的协议：$url")
        }
    }
}

/* --- URL 拼接 -------------------------------------------------------------- */

/** 规范化 `serviceUrl`：去尾斜杠、补协议；解析不出合法地址返回 null。 */
fun normalizeServiceUrl(value: Any?): String? {
    var text = value?.toString()?.trim() ?: ""
    while (text.endsWith("/")) text = text.substring(0, text.length - 1)
    if (text.isEmpty()) return null
    val lowered = text.lowercase()
    if (!lowered.startsWith("http://") && !lowered.startsWith("https://")) {
        if (!text.contains(".") || text.startsWith("/")) return null
        text = "https://$text"
    }
    return try {
        val uri = text.toHttpUrl()
        if (uri.host.isEmpty()) null else text
    } catch (_: Exception) {
        null
    }
}

/** `serviceUrl` → wapController.jsp 完整地址。保留原始斜杠语义，不自作主张规范化。 */
fun wapControllerUrl(serviceUrl: String): String {
    val base = normalizeServiceUrl(serviceUrl)
        ?: throw XqException.Protocol("学校服务地址无效。")
    val lowered = base.lowercase()
    if (lowered.endsWith("/wap/wapcontroller.jsp")) return base
    if (lowered.endsWith("/wap")) return "$base/wapController.jsp"
    return "$base/wap/wapController.jsp"
}

/** 默认管理端地址 —— 学校发现用它，登录后换成学校自己的 serviceUrl。 */
const val MANAGER_BASE_URL = "https://api.xiqueer.com/manager"

/** 学校发现端点。 */
val SCHOOL_LOOKUP_URL: String get() = "$MANAGER_BASE_URL/wap/wapController.jsp"

/** 客户端标识。不冒充官方 app —— 老实报出这是兼容客户端。 */
val USER_AGENT: String get() = "xiqueer-compatible-client/${XqSigner.APP_VERSION}"
