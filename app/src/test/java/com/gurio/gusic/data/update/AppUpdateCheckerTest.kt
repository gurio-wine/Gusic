package com.gurio.gusic.data.update

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdateCheckerTest {

    private fun asset(name: String) = ReleaseAsset(name, "https://github.com/gurio-wine/Gusic/releases/download/v26.11.24/$name", 42L)

    @Test
    fun `arm64-v8a asset is preferred over other abi assets`() {
        val assets = listOf(asset("gusic-v7a-v26.11.24.apk"), asset("gusic-v8a-v26.11.24.apk"))
        val selected = selectReleaseAsset(arrayOf("arm64-v8a", "armeabi-v7a"), assets)
        assertEquals("gusic-v8a-v26.11.24.apk", selected?.name)
    }

    @Test
    fun `v7a asset is selected when only v7a is available`() {
        val assets = listOf(asset("gusic-v7a-v26.11.24.apk"))
        val selected = selectReleaseAsset(arrayOf("arm64-v8a", "armeabi-v7a"), assets)
        assertEquals("gusic-v7a-v26.11.24.apk", selected?.name)
    }

    @Test
    fun `generic asset is selected when no abi-specific asset matches`() {
        val assets = listOf(
            asset("gusic-v7a-v26.11.24.apk"),
            asset("gusic-v26.11.24.apk"),
        )
        val selected = selectReleaseAsset(arrayOf("arm64-v8a"), assets)
        assertEquals("gusic-v26.11.24.apk", selected?.name)
    }

    @Test
    fun `returns null when no asset matches the device abi and no generic asset exists`() {
        val assets = listOf(asset("gusic-v7a-v26.11.24.apk"), asset("notes.txt"))
        val selected = selectReleaseAsset(arrayOf("arm64-v8a"), assets)
        assertNull(selected)
    }

    @Test
    fun `other abis use the raw abi name as asset segment`() {
        val assets = listOf(asset("gusic-x86_64-v26.11.24.apk"))
        val selected = selectReleaseAsset(arrayOf("x86_64", "x86"), assets)
        assertEquals("gusic-x86_64-v26.11.24.apk", selected?.name)
    }

    @Test
    fun `conventional apk url follows the release asset naming for arm64`() {
        val url = buildConventionalApkUrl(arrayOf("arm64-v8a"), "v26.11.24", "26.11.24")
        assertEquals(
            "https://github.com/gurio-wine/Gusic/releases/download/v26.11.24/gusic-v8a-v26.11.24.apk",
            url,
        )
    }

    @Test
    fun `conventional apk url follows the release asset naming for v7a`() {
        val url = buildConventionalApkUrl(arrayOf("armeabi-v7a"), "v26.11.24", "26.11.24")
        assertEquals(
            "https://github.com/gurio-wine/Gusic/releases/download/v26.11.24/gusic-v7a-v26.11.24.apk",
            url,
        )
    }

    @Test
    fun `conventional apk url uses the raw abi name for other abis`() {
        val url = buildConventionalApkUrl(arrayOf("x86_64"), "v26.11.24", "26.11.24")
        assertEquals(
            "https://github.com/gurio-wine/Gusic/releases/download/v26.11.24/gusic-x86_64-v26.11.24.apk",
            url,
        )
    }

    @Test
    fun `conventional apk url is null when no abi is available`() {
        assertNull(buildConventionalApkUrl(emptyArray(), "v26.11.24", "26.11.24"))
    }

    @Test
    fun `version comparison treats 26-11-24 as newer than 26-10-13-014`() {
        assertTrue(isNewer(parseVersion("26.11.24"), parseVersion("26.10.13.014")))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(isNewer(parseVersion("26.11.24"), parseVersion("26.11.24")))
    }

    @Test
    fun `v prefix is stripped when parsing versions`() {
        assertEquals(parseVersion("26.12.1"), parseVersion("v26.12.1"))
        assertTrue(isNewer(parseVersion("v26.12.1"), parseVersion("26.12.0")))
    }

    @Test
    fun `prerelease suffix is ignored when parsing versions`() {
        assertEquals(parseVersion("26.11.24"), parseVersion("v26.11.24-beta"))
        assertFalse(isNewer(parseVersion("v26.11.24-beta"), parseVersion("26.11.24")))
    }

    @Test
    fun `asset size is null when the api omits it or reports zero`() {
        val root = JsonParser.parseString(
            """
            {"assets":[
              {"name":"gusic-v8a-v26.11.24.apk","browser_download_url":"https://example.com/a.apk"},
              {"name":"gusic-v7a-v26.11.24.apk","browser_download_url":"https://example.com/b.apk","size":0},
              {"name":"gusic-universal-v26.11.24.apk","browser_download_url":"https://example.com/c.apk","size":12345},
              {"name":"broken.apk"}
            ]}
            """.trimIndent()
        ).asJsonObject
        val assets = parseReleaseAssets(root)
        assertEquals(listOf("gusic-v8a-v26.11.24.apk", "gusic-v7a-v26.11.24.apk", "gusic-universal-v26.11.24.apk"), assets.map { it.name })
        assertEquals(listOf(null, null, 12345L), assets.map { it.sizeBytes })
    }

    @Test
    fun `assets missing or malformed yields an empty list`() {
        assertEquals(emptyList<ReleaseAsset>(), parseReleaseAssets(JsonParser.parseString("{}").asJsonObject))
        assertEquals(emptyList<ReleaseAsset>(), parseReleaseAssets(JsonParser.parseString("""{"assets":"x"}""").asJsonObject))
    }

    @Test
    fun `cached apk file name is derived from the raw tag`() {
        assertEquals("gusic-v26.11.24.apk", updateApkFileName("v26.11.24"))
        assertEquals("gusic-v26.11.24_rc_1.apk", updateApkFileName("v26.11.24 rc/1"))
    }
}
