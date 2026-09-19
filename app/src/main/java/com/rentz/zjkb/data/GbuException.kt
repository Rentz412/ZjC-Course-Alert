package com.rentz.zjkb.data

/**
 * 异常体系（喜鹊儿协议语义）。
 *
 * UI 能说人话的关键：网络不通、协议看不懂、账号密码不对、会话过期要重登、
 * 需要二次验证 —— 这几件事在界面上的处置完全不同。
 */
sealed class GbuException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : GbuException("网络错误: ${cause.message}", cause)
    class BadCredentials(val message0: String) : GbuException(message0)

    /** 需要验证码 / 短信验证 / 设备绑定等二次验证：由账户持有人在官方渠道完成。 */
    class NeedVerification(val message0: String) : GbuException(message0)
    class SessionExpired : GbuException("会话已过期")

    /**
     * 接口 / 协议层失败。
     *
     * [summary] 面向用户：说明失败环节与最可能原因，直接展示。
     * [detail] 面向排查：环节与原始错误摘要，永不含请求表单与凭据。
     */
    class ApiError(
        val stage: Stage,
        val summary: String,
        val url: String? = null,
        val httpStatus: Int? = null,
        val snippet: String? = null,
    ) : GbuException(summary) {

        /** 失败环节；[label] 直接进用户可见的诊断文本。 */
        enum class Stage(val label: String) {
            XqLogin("喜鹊儿登录"),
            CourseApi("课表接口"),
            Semester("学期接口"),
            Parse("数据解析"),
            Network("网络"),
        }

        /** 技术细节（多行）；纯函数生成，可单测。 */
        val detail: String
            get() = buildString {
                appendLine("环节：${stage.label}")
                url?.let { appendLine("地址：$it") }
                httpStatus?.let { appendLine("HTTP：$it") }
                snippet?.let { appendLine("响应片段：$it") }
            }
    }
}
