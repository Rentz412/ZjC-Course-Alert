package com.rentz.zjkb

import com.rentz.zjkb.data.remote.xq.XqSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * 签名器固定向量 —— 与 Flutter 客户端 signer_test.dart 同向量逐字节一致。
 * 向量由公开复现实现生成，已通过真实服务器验收。
 */
class XqSignerTest {

    private val asciiRaw = "action=getAgent&xxmc=test&appver=2.6.453"
    private val asciiParam =
        "3lx3wi3k3r9y30hl773qmjfj3ob2t62g7wp43t2b4b3qnb7v3x71bb3o9tae3ob5ur2nbit22rizqj00004h"
    private val asciiParam2 = "ffd88d26d8f5639ead5621368ae48cb8"
    private val cjkRaw = "action=getAgent&xxmc=示例大学&appver=2.6.453"
    private val cjkParam =
        "3lx3wi3k3r9y30hl773qmjfj3ob2t62g7wp43t2b4b2dzebj8l39wn3o9tae3ob5ur2nbit22rizqj00004h"
    private val cjkParam2 = "4302e830e80551a8b1eca7b24d44072c"
    private val loginRaw =
        "pwdsfzm=1&loginId=2024010317&sswl=&os=android&xtbb=13&appver=2.6.453&isky=1" +
            "&zddl=1&xxdm=00000&checktoken=true&sjxh=Xiqueer-Dart&action=getLoginInfoNew" +
            "&sjbz=&pwd=secret123&loginmode=0"
    private val loginParam =
        "3uv1ea3q1we23t1hsf2g7nfj3phu5o311ne52twn2g2oifk12spu672qaipf3wnc7u2tv4bz" +
            "3wm3gf3n2ote3u9azm2g7wp03miioz2mq71q3lxdxi3ru6fe30gg9g2pp9842vp06s3k3ucz" +
            "406p1r2g7y843nplmb2mpx2w3zm3kz2tvc002spruu2g7ghc3oaubd3qnixf3ob2rn3qnl9" +
            "13o9j953kpdtk30h9mn3ovcql3ob5ub2y2f3e3x71bb3gj9i13u9etf3iwocs39f9g53k3q" +
            "h53tnt2t340u8f2msvjs3fxyhp2mst8t3h3iob3oaubk3hq4o12twpdg3lw2333qot833no" +
            "4et003wk4"
    private val loginParam2 = "b759c4457afc2af30a487e917e21da05"
    private val privateSignData = "0123456789abcdef"
    private val privateSignVector =
        "eZkBcHqZsO+nXb6kIeohgWc9D7qGSywcEirjTz14um+M9GDIj+WUqyXRklyzyzq+DIbz" +
            "da4gHbQaAvYC6TXkHlDzyo+8N+Pnm9ybSradT847LStind4/qHUCKPqUR9FAyAV/FyrJ" +
            "tr02hWmGeDodzEqOQEtBrqbvxr3Ree+Zf6A="
    private val aesVectorCiphertext = "iusUcyG3zq9odT1TLEmUfutB35wAOwWY0gFRBxAFFUU="

    @Test fun param_ascii() {
        assertEquals(asciiParam, XqSigner.ndkEncryptZdy(asciiRaw))
    }

    @Test fun param_cjk_utf16_code_unit() {
        assertEquals(cjkParam, XqSigner.ndkEncryptZdy(cjkRaw))
    }

    @Test fun param_login_payload() {
        assertEquals(loginParam, XqSigner.ndkEncryptZdy(loginRaw))
    }

    @Test fun param_empty() {
        assertEquals("", XqSigner.ndkEncryptZdy(""))
    }

    @Test fun param_different_inputs_differ() {
        assertNotEquals(XqSigner.ndkEncryptZdy(asciiRaw), XqSigner.ndkEncryptZdy(cjkRaw))
    }

    @Test fun param2_ascii() {
        assertEquals(asciiParam2, XqSigner.ndkParam2(asciiRaw))
    }

    @Test fun param2_cjk() {
        assertEquals(cjkParam2, XqSigner.ndkParam2(cjkRaw))
    }

    @Test fun param2_login() {
        assertEquals(loginParam2, XqSigner.ndkParam2(loginRaw))
    }

    @Test fun param2_empty() {
        assertEquals("", XqSigner.ndkParam2(""))
    }

    @Test fun md5_baseline() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", XqSigner.md5Hex("abc"))
    }

    @Test fun formString_login_field_order() {
        val payload = linkedMapOf(
            "pwdsfzm" to "1",
            "loginId" to "2024010317",
            "sswl" to "",
            "os" to "android",
            "xtbb" to "13",
            "appver" to "2.6.453",
            "isky" to "1",
            "zddl" to "1",
            "xxdm" to "00000",
            "checktoken" to "true",
            "sjxh" to "Xiqueer-Dart",
            "action" to "getLoginInfoNew",
            "sjbz" to "",
            "pwd" to "secret123",
            "loginmode" to "0",
        )
        assertEquals(loginRaw, XqSigner.formString(payload))
    }

    @Test fun formString_non_login_insertion_order() {
        assertEquals("a=1&b=2", XqSigner.formString(linkedMapOf("a" to "1", "b" to "2")))
    }

    @Test fun rsa_server_public_key_parsed() {
        val pub = XqSigner.serverRsaPublicKey
        assertEquals(
            "9ab38779d0809bec29dd6c789265bc674872b02c53d29446032cfe89080236d4bba667d" +
                "690b25cd360475b5281363dfc5f1113ab609ead8199fcc07ac2c17453d518d2f3855e57" +
                "70aef2eefc2abd59829263f544a3f821788e22fb473b6392579642be1bd0718fe780d280" +
                "3a2aa7b47d8f4afe205944c9bf856ab4b0aa4ede03",
            pub.modulus.toString(16),
        )
        assertEquals(BigInteger("10001", 16), pub.exponent)
        assertEquals(128, pub.sizeBytes)
    }

    @Test fun rsa_app_private_key_parsed() {
        val priv = XqSigner.appRsaPrivateKey
        assertEquals(
            "847943bf3289d975e11d4ebbe10a8ecd3fb50838fb400fc0a0c37b6349d3838c4529d8" +
                "5284c9237aeffbc719e78f24bf08a887a21c5a9eb2cd377d4102b796925da64a36a2e56" +
                "7ac89d422564027897d142ab7e8d0066f647387ace83f9b39954ba03e3fd96d1b8fa68" +
                "c9164b1f2dd2647a088e8b02bef8c3ec19099bbb274c7",
            priv.modulus.toString(16),
        )
        assertEquals(BigInteger("10001", 16), priv.publicExponent)
        assertEquals(
            "4f2a186e1dc97b0692ac6ceca1a3298b4652ecc300825be7852acc859d0ca54421e0cb" +
                "c12450febeecc2cd4c91b05d093c275a466a234d4b148fe06e052da8f09aa029efe3d40" +
                "2df1a892879efc9fe852c8133d94c07becb0bff801684cb3676e4ab38aee505b2af0b0e" +
                "7ba7a4a124dfd96928d64fe3db189c0ab99499c05941",
            priv.privateExponent.toString(16),
        )
    }

    @Test fun xqerSign_fixed_vector() {
        val sign = java.util.Base64.getEncoder()
            .encodeToString(XqSigner.rsaPrivateSignRaw(privateSignData, XqSigner.appRsaPrivateKey))
        assertEquals(privateSignVector, sign)
    }

    @Test fun xqerSign_recoverable_by_public_key() {
        val priv = XqSigner.appRsaPrivateKey
        val sign = XqSigner.rsaPrivateSignRaw(privateSignData, priv)
        // 用 e 模幂回来验证 00 || 01 || FF… || 00 || M 结构。
        val body = BigInteger(1, sign).modPow(priv.publicExponent, priv.modulus)
        val bytes = ByteArray(priv.sizeBytes)
        var v = body
        for (i in priv.sizeBytes - 1 downTo 0) {
            bytes[i] = (v and BigInteger.valueOf(0xFF)).toInt().toByte()
            v = v.shiftRight(8)
        }
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x01, bytes[1].toInt() and 0xFF)
        var i = 2
        while (i < bytes.size && bytes[i].toInt() and 0xFF == 0xff) i++
        assertEquals(0x00, bytes[i].toInt() and 0xFF)
        assertEquals(privateSignData, String(bytes, i + 1, bytes.size - i - 1, Charsets.UTF_8))
    }

    @Test fun encrptSecretKey_roundtrip_with_paired_key() {
        val priv = XqSigner.appRsaPrivateKey
        val pub = XqSigner.RsaPublicKey(priv.modulus, priv.publicExponent)
        val ct = XqSigner.rsaPublicEncryptRaw(XqSigner.ZDY_KEY, pub)
        assertEquals(128, ct.size)
        val m = BigInteger(1, ct).modPow(priv.privateExponent, priv.modulus)
        val bytes = ByteArray(128)
        var v = m
        for (i in 127 downTo 0) {
            bytes[i] = (v and BigInteger.valueOf(0xFF)).toInt().toByte()
            v = v.shiftRight(8)
        }
        assertEquals(0x00, bytes[0].toInt() and 0xFF)
        assertEquals(0x02, bytes[1].toInt() and 0xFF)
        var i = 2
        while (i < bytes.size && bytes[i].toInt() and 0xFF != 0x00) i++
        assertEquals(XqSigner.ZDY_KEY, String(bytes, i + 1, bytes.size - i - 1, Charsets.UTF_8))
    }

    @Test fun aes_encrypt_vector_and_roundtrip() {
        val plain = "hello 喜鹊儿 2025"
        assertEquals(aesVectorCiphertext, XqSigner.aesCbcEncryptB64(plain))
        assertEquals(plain, XqSigner.aesCbcDecryptB64(aesVectorCiphertext))
    }

    @Test fun aes_invalid_ciphertext_returns_null() {
        assertNotNull(XqSigner.aesCbcDecryptB64(aesVectorCiphertext))
        org.junit.Assert.assertNull(XqSigner.aesCbcDecryptB64("!!!not-base64-???"))
    }

    @Test fun signatureFields_deterministic() {
        val sig = XqSigner.signatureFields(asciiRaw, timestamp = "1723000000", echo = "AABBCCDDEEFF0011")
        assertEquals("1723000000", sig["timestamp"])
        assertEquals("AABBCCDDEEFF0011", sig["echo"])
        assertEquals(asciiParam, sig["param"])
        assertEquals(asciiParam2, sig["param2"])
        assertEquals(128, java.util.Base64.getDecoder().decode(sig["encrptSecretKey"]!!).size)
        assertTrue(sig["xqerSign"]!!.isNotEmpty())
    }

    @Test fun signedBody_outer_fields() {
        val body = XqSigner.signedBody(
            linkedMapOf("action" to "getAgent", "xxmc" to "示例大学", "appver" to "2.6.453"),
            preserveWapRoute = true,
        )
        for (key in listOf(
            "param", "param2", "timestamp", "echo", "encrptSecretKey",
            "xqerSign", "token", "appinfo", "appsjxh",
        )) {
            assertTrue("缺少字段 $key", body.containsKey(key))
        }
        assertEquals("00000", body["token"])
        assertEquals("android2.6.453", body["appinfo"])
        assertEquals("getAgent", body["action"])
        assertEquals("示例大学", body["xxmc"])
    }

    @Test fun xqerEscape_ascii_and_cjk() {
        assertEquals("azAZ09-_.!~*'()", XqSigner.xqerEscape("azAZ09-_.!~*'()"))
        assertEquals("a%20b%3Dc", XqSigner.xqerEscape("a b=c"))
        assertEquals("%u5F20%u4E09", XqSigner.xqerEscape("张三"))
        assertEquals("", XqSigner.xqerEscape(""))
    }

    @Test fun signedBodyWithProfile_appends_profile_to_sign_source() {
        val payload = linkedMapOf(
            "userId" to "12623_202400000001",
            "usertype" to "STU",
            "step" to "list",
        )
        val body = XqSigner.signedBodyWithProfile(payload, xm = "张三", uuid = "12623_202400000001")
        val raw = XqSigner.formString(payload) +
            "&xqerxm=" + XqSigner.xqerEscape("张三") +
            "&uuid=12623_202400000001" +
            "&md5="
        val expected = XqSigner.signatureFields(raw)
        assertEquals(expected["param"], body["param"])
        assertEquals(expected["param2"], body["param2"])
        for (key in listOf("timestamp", "echo", "encrptSecretKey", "xqerSign", "token", "appinfo", "appsjxh")) {
            assertTrue("缺少字段 $key", body.containsKey(key))
        }
    }

    @Test fun signedBodyWithProfile_differs_from_plain() {
        val payload = linkedMapOf("userId" to "u", "usertype" to "STU", "step" to "list")
        val plain = XqSigner.signedBody(payload)
        val withProfile = XqSigner.signedBodyWithProfile(payload, xm = "张三", uuid = "x")
        assertNotEquals(plain["param"], withProfile["param"])
    }
}
