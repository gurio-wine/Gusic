package com.gurio.gusic.data.lxmusic.kuwo

import java.security.MessageDigest
import java.util.Base64

/**
 * 酷我原生协议封包/解包。
 *
 * 酷我客户端的 `?f=ar&q=<base64>` 通道使用魔改 DES（自定义初始/终结置换、带 -1 占位的扩展
 * 置换、非标准 S 盒与密钥循环掩码），与标准 DES 不兼容，javax.crypto 无法实现，必须按表移植。
 * 分组为 ECB + 零填充（恒补 1..8 个 0x00），外层标准 Base64：
 *  - 请求封包：q = base64(des(form, "kwks&@69"))
 *  - 响应解密：body 明文 = des(base64(body), sx)，sx 为随请求上行的毫秒时间戳前 8 位数字
 *
 * 表值与测试向量（KuwoNativeCryptoTest）一一对应，改动任何表都会被单测拒绝。
 */
internal object KuwoNativeCrypto {
    private val INITIAL = intArrayOf(
        57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3, 61, 53, 45, 37, 29, 21, 13, 5, 63,
        55, 47, 39, 31, 23, 15, 7, 56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2, 60, 52,
        44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6,
    )
    private val EXPANSION = intArrayOf(
        31, 0, 1, 2, 3, 4, -1, -1, 3, 4, 5, 6, 7, 8, -1, -1, 7, 8, 9, 10, 11, 12, -1, -1, 11, 12, 13,
        14, 15, 16, -1, -1, 15, 16, 17, 18, 19, 20, -1, -1, 19, 20, 21, 22, 23, 24, -1, -1, 23, 24, 25,
        26, 27, 28, -1, -1, 27, 28, 29, 30, 31, 30, -1, -1,
    )
    private val PERMUTATION = intArrayOf(
        15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9, 1, 7, 23, 13, 31, 26, 2, 8, 18, 12,
        29, 5, 21, 10, 3, 24,
    )
    private val FINAL = intArrayOf(
        39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30, 37, 5, 45, 13, 53, 21, 61, 29,
        36, 4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26,
        33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24,
    )
    private val KEY_FIRST = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59,
        51, 43, 35, 62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28,
        20, 12, 4, 27, 19, 11, 3,
    )
    private val KEY_SECOND = intArrayOf(
        13, 16, 10, 23, 0, 4, -1, -1, 2, 27, 14, 5, 20, 9, -1, -1, 22, 18, 11, 3, 25, 7, -1, -1, 15, 6,
        26, 19, 12, 1, -1, -1, 40, 51, 30, 36, 46, 54, -1, -1, 29, 39, 50, 44, 32, 47, -1, -1, 43, 48,
        38, 55, 33, 52, -1, -1, 45, 41, 49, 35, 28, 31, -1, -1,
    )
    private val ROTATIONS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)
    private val BOXES = arrayOf(
        intArrayOf(14, 4, 3, 15, 2, 13, 5, 3, 13, 14, 6, 9, 11, 2, 0, 5, 4, 1, 10, 12, 15, 6, 9, 10, 1, 8, 12, 7, 8, 11, 7, 0, 0, 15, 10, 5, 14, 4, 9, 10, 7, 8, 12, 3, 13, 1, 3, 6, 15, 12, 6, 11, 2, 9, 5, 0, 4, 2, 11, 14, 1, 7, 8, 13),
        intArrayOf(15, 0, 9, 5, 6, 10, 12, 9, 8, 7, 2, 12, 3, 13, 5, 2, 1, 14, 7, 8, 11, 4, 0, 3, 14, 11, 13, 6, 4, 1, 10, 15, 3, 13, 12, 11, 15, 3, 6, 0, 4, 10, 1, 7, 8, 4, 11, 14, 13, 8, 0, 6, 2, 15, 9, 5, 7, 1, 10, 12, 14, 2, 5, 9),
        intArrayOf(10, 13, 1, 11, 6, 8, 11, 5, 9, 4, 12, 2, 15, 3, 2, 14, 0, 6, 13, 1, 3, 15, 4, 10, 14, 9, 7, 12, 5, 0, 8, 7, 13, 1, 2, 4, 3, 6, 12, 11, 0, 13, 5, 14, 6, 8, 15, 2, 7, 10, 8, 15, 4, 9, 11, 5, 9, 0, 14, 3, 10, 7, 1, 12),
        intArrayOf(7, 10, 1, 15, 0, 12, 11, 5, 14, 9, 8, 3, 9, 7, 4, 8, 13, 6, 2, 1, 6, 11, 12, 2, 3, 0, 5, 14, 10, 13, 15, 4, 13, 3, 4, 9, 6, 10, 1, 12, 11, 0, 2, 5, 0, 13, 14, 2, 8, 15, 7, 4, 15, 1, 10, 7, 5, 6, 12, 11, 3, 8, 9, 14),
        intArrayOf(2, 4, 8, 15, 7, 10, 13, 6, 4, 1, 3, 12, 11, 7, 14, 0, 12, 2, 5, 9, 10, 13, 0, 3, 1, 11, 15, 5, 6, 8, 9, 14, 14, 11, 5, 6, 4, 1, 3, 10, 2, 12, 15, 0, 13, 2, 8, 5, 11, 8, 0, 15, 7, 14, 9, 4, 12, 7, 10, 9, 1, 13, 6, 3),
        intArrayOf(12, 9, 0, 7, 9, 2, 14, 1, 10, 15, 3, 4, 6, 12, 5, 11, 1, 14, 13, 0, 2, 8, 7, 13, 15, 5, 4, 10, 8, 3, 11, 6, 10, 4, 6, 11, 7, 9, 0, 6, 4, 2, 13, 1, 9, 15, 3, 8, 15, 3, 1, 14, 12, 5, 11, 0, 2, 12, 14, 7, 5, 10, 8, 13),
        intArrayOf(4, 1, 3, 10, 15, 12, 5, 0, 2, 11, 9, 6, 8, 7, 6, 9, 11, 4, 12, 15, 0, 3, 10, 5, 14, 13, 7, 8, 13, 14, 1, 2, 13, 6, 14, 9, 4, 1, 2, 14, 11, 13, 5, 0, 1, 10, 8, 3, 0, 11, 3, 5, 9, 4, 15, 2, 7, 8, 12, 15, 10, 7, 6, 12),
        intArrayOf(13, 7, 10, 0, 6, 9, 5, 15, 8, 4, 3, 10, 11, 14, 12, 5, 2, 11, 9, 6, 15, 12, 0, 3, 4, 1, 14, 13, 1, 2, 7, 8, 1, 2, 12, 15, 10, 4, 0, 3, 13, 14, 6, 9, 7, 8, 9, 6, 15, 1, 5, 12, 3, 10, 14, 5, 8, 7, 11, 0, 4, 13, 2, 11),
    )

    const val QUERY_KEY = "kwks&@69"
    const val CLIENT_VERSION = "12.2.2.0"
    const val CLIENT_SOURCE = "kwplayer_ar_12.2.2.0_newpcguanwangmobile.apk"
    const val FALLBACK_QIMEI36 = "f2ce3c2ef68ddfd1b2bea7ed00001f314716"

    private fun permute(value: Long, positions: IntArray): Long {
        var out = 0L
        for (bit in positions.indices) {
            out = out or (((value ushr positions[bit]) and 1L) shl bit)
        }
        return out
    }

    private fun expand(value: Long, positions: IntArray): Long {
        var out = 0L
        for (bit in positions.indices) {
            val pos = positions[bit]
            if (pos >= 0) {
                out = out or (((value ushr pos) and 1L) shl bit)
            }
        }
        return out
    }

    private fun littleEndianLong(bytes: ByteArray): Long {
        var v = 0L
        for (i in 7 downTo 0) {
            v = (v shl 8) or (bytes[i].toLong() and 0xFFL)
        }
        return v
    }

    private fun littleEndianBytes(value: Long): ByteArray =
        ByteArray(8) { i -> (value ushr (8 * i)).toByte() }

    private fun schedule(key: ByteArray): LongArray {
        var current = permute(littleEndianLong(key), KEY_FIRST)
        val result = LongArray(ROTATIONS.size)
        for (rnd in ROTATIONS.indices) {
            val shift = ROTATIONS[rnd]
            // 28 位半密钥循环左移；掩码即原实现里的 0x100001 / 0x300003
            val mask = if (shift == 1) 0x100001L else 0x300003L
            current = ((current and mask.inv()) ushr shift) or ((current and mask) shl (28 - shift))
            result[rnd] = expand(current, KEY_SECOND)
        }
        return result
    }

    private fun block(block8: ByteArray, sched: LongArray): ByteArray {
        val state = permute(littleEndianLong(block8), INITIAL)
        var left = state and 0xFFFFFFFFL
        var right = state ushr 32
        for (key in sched) {
            val expanded = expand(right, EXPANSION) xor key
            var substituted = 0L
            for (i in BOXES.indices) {
                val idx = ((expanded ushr (i * 8)) and 0x3FL).toInt()
                substituted = substituted or (BOXES[i][idx].toLong() shl (i * 4))
            }
            val next = left xor permute(substituted, PERMUTATION)
            left = right
            right = next
        }
        return littleEndianBytes(permute(right or (left shl 32), FINAL))
    }

    fun encrypt(plain: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 8) { "kuwo des key must be 8 bytes" }
        val pad = 8 - plain.size % 8
        val padded = ByteArray(plain.size + pad)
        plain.copyInto(padded)
        val sched = schedule(key)
        val out = ByteArray(padded.size)
        var offset = 0
        while (offset < padded.size) {
            block(padded.copyOfRange(offset, offset + 8), sched).copyInto(out, offset)
            offset += 8
        }
        return out
    }

    fun decrypt(cipher: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 8) { "kuwo des key must be 8 bytes" }
        require(cipher.isNotEmpty() && cipher.size % 8 == 0) { "kuwo des cipher must be non-empty blocks of 8" }
        val forward = schedule(key)
        // 解密按加密轮次的逆序使用子密钥
        val reversed = LongArray(forward.size) { i -> forward[forward.size - 1 - i] }
        val out = ByteArray(cipher.size)
        var offset = 0
        while (offset < cipher.size) {
            block(cipher.copyOfRange(offset, offset + 8), reversed).copyInto(out, offset)
            offset += 8
        }
        // 零填充恒补 1..8 个 0x00；尾零数超出该范围说明包损坏，原样返回交由上层报错
        var pad = 0
        for (i in out.indices.reversed()) {
            if (out[i].toInt() == 0) pad++ else break
        }
        return if (pad in 1..8) out.copyOf(out.size - pad) else out
    }

    /** 请求封包：q = base64(des(utf8(form), "kwks&@69")) */
    fun sealQuery(form: String): String =
        Base64.getEncoder().encodeToString(
            encrypt(form.toByteArray(Charsets.UTF_8), QUERY_KEY.toByteArray(Charsets.UTF_8)),
        )

    /** 响应解密 key：毫秒时间戳字符串前 8 位 ASCII 数字 */
    fun responseKey(nowMs: Long = System.currentTimeMillis()): String =
        nowMs.toString().take(8)

    /** 解密服务端密文响应；key 必须是 8 位 ASCII 数字，失败返回 null */
    fun openResponse(encoded: String, key8: String): String? {
        if (key8.length != 8 || !key8.all { it in '0'..'9' }) return null
        return try {
            val cipher = Base64.getDecoder().decode(encoded.trim())
            String(decrypt(cipher, key8.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun md5Upper(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }

    /** send_sms 的 secret：MD5U(MD5U("imbadboy@!153") + MD5U(phone + "4" + tm)) */
    fun smsSecret(phone: String, tm: String): String =
        md5Upper(md5Upper("imbadboy@!153") + md5Upper(phone + "4" + tm))

    /** 设备注册 token：base64(xor(plain, "yeelion ")) */
    fun deviceRegisterToken(plain: String): String {
        val key = "yeelion ".toByteArray(Charsets.UTF_8)
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val out = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor key[i % key.size].toInt()).toByte() }
        return Base64.getEncoder().encodeToString(out)
    }
}
