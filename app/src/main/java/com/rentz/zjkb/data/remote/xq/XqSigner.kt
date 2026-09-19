package com.rentz.zjkb.data.remote.xq

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 喜鹊儿协议签名器（Kotlin 移植版）。
 *
 * 算法来源：官方 App 逆向还原的公开复现实现（Dart/Python/Node 三版逐字节一致），
 * 本文件以 Flutter 兼容客户端 `signer.dart` 为蓝本移植，固定向量单测锁定行为。
 *
 * 信封字段（POST application/x-www-form-urlencoded）：
 *   param / param2 / timestamp / echo / encrptSecretKey / xqerSign
 *   / token / appinfo / appsjxh
 *
 * 注意：本文件内嵌的 RSA 密钥是官方 app 随 APK 分发的材料，只用于个人兼容
 * 客户端的请求签名，不是任何用户凭据。
 */
object XqSigner {

    /** 目标客户端版本号（与版本检查、getAgent 等接口共用）。 */
    const val APP_VERSION = "2.6.453"

    /** param 编码键；同时是 encrptSecretKey 加密的业务 key。 */
    const val ZDY_KEY = "yt6n78"

    /** AES/CBC 整包密钥与 IV（登录密码加密、响应整包解密共用）。 */
    const val AES_APP_KEY = "loginkeyapp93214"
    const val AES_APP_IV = "12fg45gpsdfz34ab"

    /** 服务器 RSA 公钥（SPKI，base64 url-safe）—— encrptSecretKey 用它加密业务 key。 */
    private const val SERVER_RSA_PUBLIC_B64 =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCas4d50ICb7CndbHiSZbxnSHKw" +
            "LFPSlEYDLP6JCAI21LumZ9aQslzTYEdbUoE2PfxfEROrYJ6tgZn8wHrCwXRT1RjS" +
            "84VeV3Cu8u78Kr1ZgpJj9USj-CF4jiL7RztjkleWQr4b0HGP54DSgDoqp7R9j0r" +
            "-IFlEyb-FarSwqk7eAwIDAQAB"

    /** app RSA 私钥（PKCS8，base64 url-safe）—— 仅用于 xqerSign 的客户端签名运算。
     * 与服务器公钥是【不同密钥对】，协议设计如此。 */
    private const val APP_RSA_PRIVATE_B64 =
        "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAIR5Q78yidl14R1O" +
            "u-EKjs0_tQg4-0APwKDDe2NJ04OMRSnYUoTJI3rv-8cZ548kvwioh6IcWp6yzTd9" +
            "QQK3lpJdpko2ouVnrInUIlZAJ4l9FCq36NAGb2Rzh6zoP5s5lUugPj_ZbRuPpoyR" +
            "ZLHy3SZHoIjosCvvjD7BkJm7snTHAgMBAAECgYBPKhhuHcl7BpKsbOyhoymLRlLs" +
            "wwCCW-eFKsyFnQylRCHgy8EkUP6-7MLNTJGwXQk8J1pGaiNNSxSP4G4FLajwmqA" +
            "p7-PUAt8aiSh578n-hSyBM9lMB77LC_-AFoTLNnbkqziu5QWyrwsOe6ekoSTf2W" +
            "ko1k_j2xicCrmUmcBZQQJBALpyoXq9oRdX8in5XsrZqHOPgRevQH937rUieB0Fv" +
            "EpKiV3ySbl-NxrNWd1BnHvNWhul7bgmd5rxqOc4H7nGuukCQQC15Dj1Hl8R82WX" +
            "rlHK4cz2TpOA7_WLPHflSNznA5RX_q2kSOojdYW_xdb20qEbsjZ-mFv4jbeANo" +
            "38t7TXZoQvAkEAtHkzH4kgvmTNrp2IiRfou3tD_PYRm5EuybyEwasEmHDPyNU3" +
            "Ucr_cf0mKEpTO28J8stJcMAjdCLJWI71_rCDyQJAcxOT8Yioj1vVX5SbDOe02_" +
            "Q0oDOwvsmf9UEW-VUrakynoTO8Zni5CO5rJTd3VGV40rkkHunSOdzKEiRL1qd2" +
            "YwJAAy5Nl_4J2UlLa-jR6pWoasf0WNIsLbMrvCwtoO28KcSJW09UyyENL8-I7" +
            "qSLhv0qV1A8lHlr_1U6308ER0wRlA"

    private const val NDK_BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"
    private const val ECHO_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"

    /** 登录载荷的固定字段顺序（服务端按此顺序拼串验签）。 */
    val LOGIN_FIELD_ORDER = listOf(
        "pwdsfzm", "loginId", "sswl", "os", "xtbb", "appver", "isky", "zddl",
        "xxdm", "checktoken", "sjxh", "action", "sjbz", "pwd", "loginmode",
    )

    private val secure = SecureRandom()

    /* --- 基础摘要与数字变换 ------------------------------------------------- */

    fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** 与参考实现 `("000"+str(v))[-3:]` 语义一致：超出 3 位只取末 3 位。 */
    private fun last3(value: Int): String {
        val s = "000$value"
        return s.substring(s.length - 3)
    }

    /** 与参考实现 `("000000"+s)[-6:]` 语义一致。 */
    private fun last6(value: String): String {
        val s = "000000$value"
        return s.substring(s.length - 6)
    }

    private fun base36Encode(number: BigInteger): String {
        if (number.signum() == 0) return "0"
        val digits = ArrayList<String>()
        var n = number
        val base = BigInteger.valueOf(36)
        while (n.signum() > 0) {
            digits.add(0, NDK_BASE36[(n % base).toInt()].toString())
            n /= base
        }
        return digits.joinToString("")
    }

    /* --- param / param2 ------------------------------------------------------ */

    /**
     * param：自定义字符变换（NDK zdy），键 [ZDY_KEY]。
     *
     * 注意字符语义：官方 Java 层按 **UTF-16 code unit**（char）取值，
     * Kotlin 的 [String.get] 与之完全一致，多字节字符截断行为相同。
     */
    fun ndkEncryptZdy(raw: String, key: String = ZDY_KEY): String {
        if (raw.isEmpty() || key.isEmpty()) return raw
        val keyLength = key.length
        val rawLength = raw.length
        val rows = (rawLength + keyLength - 1) / keyLength
        val offset = ((rawLength + 2) / 3) * 6 % keyLength
        val digits = StringBuilder()
        for (row in 0 until rows) {
            for (column in 1..keyLength) {
                val index = row * keyLength + column
                if (index > rawLength) break
                digits.append(last3(raw[index - 1].code + key[column - 1].code + offset))
                if (index == rawLength) break
            }
        }
        val numeric = digits.toString()
        val buffer = StringBuilder()
        var start = 0
        while (start < numeric.length) {
            val end = minOf(start + 9, numeric.length)
            buffer.append(last6(base36Encode(BigInteger(numeric.substring(start, end)))))
            start += 9
        }
        return buffer.toString()
    }

    /**
     * param2：两阶段 MD5，第一阶段删掉摘要第 {2,9,16,24} 位（0 起）字符。
     *
     * 依据官方 `C0747b.m3927f`（Java）：`split("")` 后数组前面补了一个空元素，
     * Java 跳过的是补位后的索引 {3,10,17,25}，等价于原始 32 位摘要去掉
     * {2,9,16,24} 位。管理端不校验 param2，教务子系统严格校验，错位即被拒。
     */
    fun ndkParam2(raw: String): String {
        if (raw.isEmpty()) return ""
        val digest = md5Hex(raw)
        val removed = setOf(2, 9, 16, 24)
        val filtered = buildString {
            for (i in digest.indices) if (i !in removed) append(digest[i])
        }
        return md5Hex(filtered.toString())
    }

    /** 把键值对拼成验签原文；登录载荷按 [LOGIN_FIELD_ORDER] 优先排序。 */
    fun formString(payload: Map<String, String>): String {
        val isLogin = payload.containsKey("loginId") && payload.containsKey("pwd")
        val keys = ArrayList<String>()
        if (isLogin) {
            keys.addAll(LOGIN_FIELD_ORDER.filter { payload.containsKey(it) })
        }
        keys.addAll(payload.keys.filter { it !in keys })
        return keys.joinToString("&") { "$it=${payload[it]}" }
    }

    /* --- RSA ----------------------------------------------------------------- */

    class RsaPublicKey(val modulus: BigInteger, val exponent: BigInteger) {
        val sizeBytes: Int get() = (modulus.bitLength() + 7) / 8
    }

    class RsaPrivateKey(
        val modulus: BigInteger,
        val publicExponent: BigInteger,
        val privateExponent: BigInteger,
    ) {
        val sizeBytes: Int get() = (modulus.bitLength() + 7) / 8
    }

    /** 最小 DER 读取器（只服务 RSA 密钥解析）。 */
    private class DerReader(bytes: ByteArray) {
        private val bytes = bytes
        private var pos = 0

        private fun readByte(): Int = bytes[pos++].toInt() and 0xFF

        private fun readLength(): Int {
            val first = readByte()
            if (first and 0x80 == 0) return first
            val count = first and 0x7f
            var value = 0
            repeat(count) { value = (value shl 8) or readByte() }
            return value
        }

        private fun child(expectedTag: Int): DerReader {
            val tag = readByte()
            check(tag == expectedTag) { "DER tag 0x${tag.toString(16)} != 期望 0x${expectedTag.toString(16)}" }
            val length = readLength()
            val body = bytes.copyOfRange(pos, pos + length)
            pos += length
            return DerReader(body)
        }

        private fun readInt(): BigInteger {
            val intReader = child(0x02)
            val body = intReader.bytes
            var start = 0
            // DER INTEGER：最高位为 1 时带前导 0x00（正数）；按无符号大端解析。
            if (body.size > 1 && body[0].toInt() == 0x00) start = 1
            return bytesToBigInt(body.copyOfRange(start, body.size))
        }

        fun readIntPublic(): BigInteger = readInt()
        fun childPublic(tag: Int): DerReader = child(tag)
        fun readBytePublic(): Int = readByte()
    }

    private fun bytesToBigInt(bytes: ByteArray): BigInteger = BigInteger(1, bytes)

    /** 解析 SubjectPublicKeyInfo（SPKI）RSA 公钥。 */
    fun parseSpkiPublicKey(der: ByteArray): RsaPublicKey {
        val root = DerReader(der).childPublic(0x30)
        root.childPublic(0x30) // algorithmIdentifier（跳过）
        val bitString = root.childPublic(0x03)
        bitString.readBytePublic() // 跳过 unused-bits 字节
        val inner = bitString.childPublic(0x30)
        val n = inner.readIntPublic()
        val e = inner.readIntPublic()
        return RsaPublicKey(n, e)
    }

    /** 解析 PKCS#8 RSA 私钥（取 n、e、d 即可）。 */
    fun parsePkcs8PrivateKey(der: ByteArray): RsaPrivateKey {
        val root = DerReader(der).childPublic(0x30)
        root.readIntPublic() // PKCS#8 层 version
        root.childPublic(0x30) // algorithmIdentifier
        val octets = root.childPublic(0x04)
        val inner = octets.childPublic(0x30)
        inner.readIntPublic() // RSAPrivateKey 层 version
        val n = inner.readIntPublic()
        val e = inner.readIntPublic()
        val d = inner.readIntPublic()
        return RsaPrivateKey(n, e, d)
    }

    /** base64 url-safe 形态 → 标准 base64 DER 缓冲。 */
    private fun keyBytesFromB64(text: String): ByteArray {
        val base = text.trim().replace('-', '+').replace('_', '/')
        val padded = base + "=".repeat((4 - base.length % 4) % 4)
        return Base64.getDecoder().decode(padded)
    }

    val serverRsaPublicKey: RsaPublicKey by lazy {
        parseSpkiPublicKey(keyBytesFromB64(SERVER_RSA_PUBLIC_B64))
    }

    val appRsaPrivateKey: RsaPrivateKey by lazy {
        parsePkcs8PrivateKey(keyBytesFromB64(APP_RSA_PRIVATE_B64))
    }

    /** 原始模幂：m^exp mod n，输出定长大端字节。 */
    private fun rsaRawPow(input: BigInteger, exponent: BigInteger, modulus: BigInteger, sizeBytes: Int): ByteArray {
        val out = input.modPow(exponent, modulus)
        val bytes = ByteArray(sizeBytes)
        var v = out
        for (i in sizeBytes - 1 downTo 0) {
            bytes[i] = (v and BigInteger.valueOf(0xFF)).toInt().toByte()
            v = v.shiftRight(8)
        }
        return bytes
    }

    /** PKCS1 v1.5 类型 2 加密（encrptSecretKey 用）：00 || 02 || PS || 00 || M。 */
    fun rsaPublicEncryptRaw(message: String, key: RsaPublicKey): ByteArray {
        val raw = message.toByteArray(Charsets.UTF_8)
        val size = key.sizeBytes
        val psLength = size - raw.size - 3
        require(psLength >= 8) { "消息过长" }
        val block = ByteArray(size)
        block[0] = 0x00
        block[1] = 0x02
        var pos = 2
        val psEnd = 2 + psLength
        while (pos < psEnd) {
            val byte = secure.nextInt(256)
            if (byte != 0) block[pos++] = byte.toByte()
        }
        block[pos] = 0x00
        System.arraycopy(raw, 0, block, pos + 1, raw.size)
        return rsaRawPow(bytesToBigInt(block), key.exponent, key.modulus, size)
    }

    /** xqerSign 客户端签名运算：00 || 01 || 0xFF… || 00 || M，用 d 做模幂。
     * 填充固定为 0xFF（与官方 APK 行为一致），结果可重复，便于测试向量锁定。 */
    fun rsaPrivateSignRaw(message: String, key: RsaPrivateKey): ByteArray {
        val raw = message.toByteArray(Charsets.UTF_8)
        val size = key.sizeBytes
        val chunkSize = size - 11
        val chunks = ArrayList<ByteArray>()
        var start = 0
        while (start < raw.size) {
            val end = minOf(start + chunkSize, raw.size)
            val chunk = raw.copyOfRange(start, end)
            val block = ByteArray(size)
            block[0] = 0x00
            block[1] = 0x01
            java.util.Arrays.fill(block, 2, size - chunk.size - 1, 0xff.toByte())
            block[size - chunk.size - 1] = 0x00
            System.arraycopy(chunk, 0, block, size - chunk.size, chunk.size)
            chunks.add(rsaRawPow(bytesToBigInt(block), key.privateExponent, key.modulus, size))
            start = end
        }
        val out = ByteArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, out, offset, chunk.size)
            offset += chunk.size
        }
        return out
    }

    /* --- AES/CBC ------------------------------------------------------------- */

    /** AES/CBC 整包加密 → base64。失败抛异常（登录密码加密是主动调用，不该静默）。 */
    fun aesCbcEncryptB64(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(AES_APP_KEY.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(AES_APP_IV.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    /** AES/CBC 整包解密。密文不合法（base64 坏、填充错、非 UTF-8）返回 null，
     * 由调用方决定是试别的解码路径还是报错。 */
    fun aesCbcDecryptB64(ciphertextB64: String): String? = try {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(AES_APP_KEY.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(AES_APP_IV.toByteArray(Charsets.UTF_8)),
        )
        String(cipher.doFinal(Base64.getDecoder().decode(ciphertextB64)), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    /* --- 信封组装 ------------------------------------------------------------- */

    private fun randomEcho(): String = buildString {
        repeat(16) { append(ECHO_ALPHABET[secure.nextInt(ECHO_ALPHABET.length)]) }
    }

    /** 生成签名信封：param / param2 / timestamp / echo / encrptSecretKey / xqerSign。 */
    fun signatureFields(paramString: String, timestamp: String? = null, echo: String? = null): Map<String, String> {
        val param = ndkEncryptZdy(paramString)
        val timestampText = timestamp
            ?: System.currentTimeMillis().toString().substring(0, 10)
        val nonce = echo ?: randomEcho()
        val encryptedKey = Base64.getEncoder().encodeToString(rsaPublicEncryptRaw(ZDY_KEY, serverRsaPublicKey))
        val signSource = "param=$param&param2=&timestamp=$timestampText&echo=$nonce"
        val signature = Base64.getEncoder().encodeToString(
            rsaPrivateSignRaw(md5Hex(signSource + encryptedKey), appRsaPrivateKey),
        )
        return linkedMapOf(
            "param" to param,
            "param2" to ndkParam2(paramString),
            "timestamp" to timestampText,
            "echo" to nonce,
            "encrptSecretKey" to encryptedKey,
            "xqerSign" to signature,
        )
    }

    /**
     * 官方 `C9787y.m29930a`（encodeURIComponent 变体）：ASCII 字母数字和
     * `- _ . ! ~ * ' ( )` 保留；其余 ASCII → `%XX`；非 ASCII → `%uXXXX`。
     * 只用在登录后签名原文的追加段（xqerxm）。
     */
    fun xqerEscape(value: String): String {
        val keep = "-_.!~*'()"
        val out = StringBuilder()
        for (ch in value) {
            val code = ch.code
            val isAlnum = (code in 0x30..0x39) || (code in 0x41..0x5A) || (code in 0x61..0x7A)
            if (isAlnum || keep.contains(ch)) {
                out.append(ch)
            } else if (code <= 127) {
                out.append('%').append(code.toString(16).uppercase().padStart(2, '0'))
            } else {
                out.append("%u").append(code.toString(16).uppercase().padStart(4, '0'))
            }
        }
        return out.toString()
    }

    /** 信封外层字段（token/appinfo/appsjxh 不进签名原文）。 */
    private fun fillEnvelopeOuter(body: MutableMap<String, String>, payload: Map<String, String>) {
        val ver = payload["appver"] ?: APP_VERSION
        body["appsjxh"] = payload["appsjxh"] ?: payload["sjxh"] ?: "Standalone"
        body["appinfo"] = payload["appinfo"] ?: "android$ver"
        body["token"] = payload["token"] ?: "00000"
    }

    private fun preserveWapRouteKeys(body: MutableMap<String, String>, payload: Map<String, String>) {
        for (key in listOf("action", "xxdm", "xxmc", "schoolCode", "schoolName", "appver")) {
            payload[key]?.let { body[key] = it }
        }
    }

    /** 签名原文追加 `xqerxm/uuid/md5` 并生成信封 —— 登录后的管理端请求形态。
     * 官方 `C0747b.m3942u(z10=true)`：业务字段串之后接
     * `&xqerxm={转义姓名}&uuid={uuid}&md5={缓存md5(通常空)}`。 */
    fun signedBodyWithProfile(
        payload: Map<String, String>,
        xm: String,
        uuid: String,
        preserveWapRoute: Boolean = false,
    ): Map<String, String> {
        val raw = formString(payload) +
            "&xqerxm=" + xqerEscape(xm) +
            "&uuid=" + uuid +
            "&md5="
        val body = signatureFields(raw).toMutableMap()
        fillEnvelopeOuter(body, payload)
        if (preserveWapRoute) preserveWapRouteKeys(body, payload)
        return body
    }

    /** 完整业务 body：纯文本参数 → 签名信封 + 外层字段。 */
    fun signedBody(
        payload: Map<String, String>,
        preserveWapRoute: Boolean = false,
    ): Map<String, String> {
        val raw = formString(payload)
        val body = signatureFields(raw).toMutableMap()
        fillEnvelopeOuter(body, payload)
        if (preserveWapRoute) preserveWapRouteKeys(body, payload)
        return body
    }
}
