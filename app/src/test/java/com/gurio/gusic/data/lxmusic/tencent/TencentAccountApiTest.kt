package com.gurio.gusic.data.lxmusic.tencent

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TencentAccountApiTest {

    @Test
    fun `parseCookieMap trims pairs and lets later duplicates win`() {
        val map = TencentAccountApi.parseCookieMap("uin=12345; qm_keyst=abc; uin=67890; empty=")
        assertEquals("67890", map["uin"])
        assertEquals("abc", map["qm_keyst"])
        assertEquals("", map["empty"])
    }

    @Test
    fun `parseCookieMap blank input returns empty map`() {
        assertTrue(TencentAccountApi.parseCookieMap(null).isEmpty())
        assertTrue(TencentAccountApi.parseCookieMap("   ").isEmpty())
    }

    @Test
    fun `normalizeUin strips non-digits and leading zeros`() {
        assertEquals("12345", TencentAccountApi.normalizeUin("o0012345"))
        assertEquals("12345", TencentAccountApi.normalizeUin("0012345"))
        assertEquals("", TencentAccountApi.normalizeUin(null))
        assertEquals("", TencentAccountApi.normalizeUin("0000"))
    }

    @Test
    fun `cookieUin prefers wxuin when login_type is 2`() {
        val wechat = mapOf("login_type" to "2", "wxuin" to "0001111", "uin" to "9999")
        assertEquals("1111", TencentAccountApi.cookieUin(wechat))
        val qq = mapOf("login_type" to "1", "wxuin" to "0001111", "uin" to "9999")
        assertEquals("9999", TencentAccountApi.cookieUin(qq))
    }

    @Test
    fun `musicKey follows credential strength order`() {
        assertEquals("k1", TencentAccountApi.musicKey(mapOf("skey" to "s", "qm_keyst" to "k1")))
        assertEquals("k2", TencentAccountApi.musicKey(mapOf("skey" to "s", "qqmusic_key" to "k2")))
        assertEquals("", TencentAccountApi.musicKey(mapOf("skey" to "")))
    }

    @Test
    fun `cookieNickname decodes url-encoded ptnick including leading-zero key`() {
        val cookies = mapOf("ptnick_012345" to "%E5%B0%8F%E6%98%8E")
        assertEquals("小明", TencentAccountApi.cookieNickname(cookies, "12345"))
        assertEquals("", TencentAccountApi.cookieNickname(emptyMap(), "12345"))
    }

    @Test
    fun `decodeCookieValue treats plus as space and falls back on bad input`() {
        assertEquals("a b", TencentAccountApi.decodeCookieValue("a+b"))
        assertEquals("%zz", TencentAccountApi.decodeCookieValue("%zz"))
    }

    @Test
    fun `avatarForUin builds qlogo url only for non-empty uin`() {
        assertEquals(
            "https://q1.qlogo.cn/g?b=qq&nk=12345&s=100",
            TencentAccountApi.avatarForUin("12345"),
        )
        assertEquals("", TencentAccountApi.avatarForUin(""))
    }

    @Test
    fun `authComm attaches authst with ct 19 when key present`() {
        val comm = TencentAccountApi.authComm("uin=0012345; qm_keyst=ticket")
        assertEquals("12345", comm.get("uin").asString)
        assertEquals(19, comm.get("ct").asInt)
        assertEquals("ticket", comm.get("authst").asString)
    }

    @Test
    fun `authComm uses anonymous ct 24 without key`() {
        val comm = TencentAccountApi.authComm("uin=0012345")
        assertEquals(24, comm.get("ct").asInt)
        assertNull(comm.get("authst"))
    }

    @Test
    fun `albumCover builds photo url for non-blank mid`() {
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000002Neh8l0xiQhW.jpg",
            TencentAccountApi.albumCover("002Neh8l0xiQhW"),
        )
        assertEquals("", TencentAccountApi.albumCover(" "))
    }

    @Test
    fun `mapTrack unwraps track_info and maps fields and extras`() {
        val raw = JsonParser.parseString(
            """
            {"track_info":{"mid":"0039MnYb0qxYhV","name":"晴天","id":97773,"interval":269,
              "singer":[{"name":"周杰伦","mid":"003Nz2So3XXYek"},{"mid":"002J4UUk29vg68"}],
              "album":{"mid":"000MkMni19ClKF","name":"叶惠美"},
              "file":{"media_mid":"003lPqNR1YScYT"}}}
            """.trimIndent(),
        )
        val song = TencentAccountApi.mapTrack(raw)
        assertNotNull(song)
        song!!
        assertEquals("tx", song.source)
        assertEquals("0039MnYb0qxYhV", song.songmid)
        assertEquals("晴天", song.name)
        assertEquals("周杰伦", song.singer)
        assertEquals("叶惠美", song.albumName)
        assertEquals(269L, song.interval)
        assertEquals("003lPqNR1YScYT", song.extraFields["strMediaMid"])
        assertEquals("000MkMni19ClKF", song.extraFields["albumMid"])
        assertEquals("97773", song.extraFields["songId"])
        assertEquals("003Nz2So3XXYek,002J4UUk29vg68", song.extraFields["artistIds"])
        assertEquals(
            "https://y.qq.com/music/photo_new/T002R300x300M000000MkMni19ClKF.jpg",
            song.pic,
        )
    }

    @Test
    fun `mapTrack accepts flat legacy shape with numeric fields`() {
        val raw = JsonParser.parseString(
            """
            {"songmid":"004Z8Ihr0JIaLS","songname":"七里香","songid":105602,
             "singername":"周杰伦","albummid":"002MAeob3zLXwZ","albumname":"七里香","interval":299}
            """.trimIndent(),
        )
        val song = TencentAccountApi.mapTrack(raw)
        assertNotNull(song)
        song!!
        assertEquals("004Z8Ihr0JIaLS", song.songmid)
        assertEquals("七里香", song.name)
        assertEquals("周杰伦", song.singer)
        assertEquals("105602", song.extraFields["songId"])
        assertEquals("002MAeob3zLXwZ", song.extraFields["albumMid"])
    }

    @Test
    fun `mapTrack returns null when songmid missing`() {
        assertNull(TencentAccountApi.mapTrack(JsonParser.parseString("""{"name":"x"}""")))
        assertNull(TencentAccountApi.mapTrack(JsonParser.parseString("[1,2]")))
    }
}
