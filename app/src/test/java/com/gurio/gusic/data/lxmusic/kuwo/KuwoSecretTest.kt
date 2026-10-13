package com.gurio.gusic.data.lxmusic.kuwo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Secret 头算法对拍：向量来自官网 bundle 原版 JS 的实跑输出
 * （walkman-tv / MusicBot-Go 整数移植与 JS 逐字节一致）。
 */
class KuwoSecretTest {

    @Test
    fun `secret matches official js vector`() {
        val secret = KuwoAccountApi.buildKuwoSecret("R2hKxX477spCzEpkjz5zXXDWD2p5FRC7", nonce = 12345678L)
        assertEquals(
            "7162e3e9697f5c1ce956d0d231ffc14662b5c4ad6fa2d8504db20bacc0d426bb00bc614e",
            secret,
        )
    }

    @Test
    fun `secret payload independent of nonce`() {
        val value = "AbCd1234567890AbCd1234567890Xy"
        val a = KuwoAccountApi.buildKuwoSecret(value, nonce = 99999999L)
        val b = KuwoAccountApi.buildKuwoSecret(value, nonce = 12345678L)
        // 前 64 位为 XOR 流 payload，与 nonce 无关；仅末 8 位 hex 随 nonce 变化
        assertEquals(a.dropLast(8), b.dropLast(8))
        assertEquals(
            "6232c8c620155b1feb1397a9728af04f4babc0e504cea9313eb842a9deff00bc614e",
            b,
        )
    }

    @Test
    fun `xor stream roundtrips back to cookie value`() {
        val value = "R2hKxX477spCzEpkjz5zXXDWD2p5FRC7"
        val secret = KuwoAccountApi.buildKuwoSecret(value, nonce = 12345678L)
        val payload = secret.dropLast(8)
        // 用同一 LCG 流再 XOR 一遍应还原出原值（服务端校验逻辑）
        var state = 59910100L
        val decoded = StringBuilder()
        for (i in value.indices) {
            state = (9253L * state + 23L) % 2147483647L
            val key = (state * 255L / 2147483647L).toInt()
            val byte = payload.substring(i * 2, i * 2 + 2).toInt(16)
            decoded.append(((byte xor key) and 0xFF).toChar())
        }
        assertEquals(value, decoded.toString())
    }

    @Test
    fun `rebuild cookie keeps single secret entry matching secret input`() {
        // WebView 多 URL 采集会产生同名多条不同值（host-only 轮换 + 登录写入 domain 条目）
        val messy = "Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324=hostOnlyValue; " +
            "userid=123456; Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324=loginValue; " +
            "kw_token=abc"
        val value = "loginValue"
        val rebuilt = KuwoAccountApi.rebuildCookieWithSecret(messy, value)
        // 重写后同名条目只剩一条，且值与 Secret 输入一致（网关取首条恒等于 Secret 输入）
        val entries = rebuilt.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val secretEntries = entries.filter {
            it.substringBefore('=').trim() == "Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324"
        }
        assertEquals(1, secretEntries.size)
        assertEquals("Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324=$value", secretEntries.single())
        // 其余条目原样保留
        assertEquals(true, entries.contains("userid=123456"))
        assertEquals(true, entries.contains("kw_token=abc"))
    }

    @Test
    fun `rebuild cookie appends secret entry when absent`() {
        val cookie = "userid=123456; kw_token=abc"
        val value = "R2hKxX477spCzEpkjz5zXXDWD2p5FRC7"
        val rebuilt = KuwoAccountApi.rebuildCookieWithSecret(cookie, value)
        assertEquals(true, rebuilt.endsWith("Hm_Iuvt_cdb524f42f23cer9b268564v7y735ewrq2324=$value"))
        assertEquals(true, rebuilt.contains("userid=123456"))
    }
}
