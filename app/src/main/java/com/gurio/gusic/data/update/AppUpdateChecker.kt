package com.gurio.gusic.data.update

import android.os.Build
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateInfo(
    val latestVersion: String,
    val tag: String,
    val apkUrl: String?,
    val apkSizeBytes: Long?,
    val releaseNotes: String?,
    val releasePageUrl: String,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    /** 资产字节数；接口没给或给了 0 时为 null，避免被当成“已知大小”。 */
    val sizeBytes: Long?,
)

/** 解析 release JSON 的 `assets`；单个资产缺字段就跳过，不拖垮整次检查。 */
internal fun parseReleaseAssets(root: JsonObject): List<ReleaseAsset> {
    val array = root.get("assets")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        runCatching {
            val obj = element.asJsonObject
            val name = obj.get("name")?.takeUnless { it.isJsonNull }?.asString ?: return@runCatching null
            val url = obj.get("browser_download_url")?.takeUnless { it.isJsonNull }?.asString
                ?: return@runCatching null
            val size = obj.get("size")?.takeUnless { it.isJsonNull }?.asLong?.takeIf { it > 0 }
            ReleaseAsset(name, url, size)
        }.getOrNull()
    }
}

internal const val RELEASE_DOWNLOAD_BASE_URL = "https://github.com/gurio-wine/Gusic/releases/download"

private val ABI_ASSET_SEGMENTS = listOf("v8a", "v7a", "x86_64", "x86", "armeabi", "arm64-v8a", "armeabi-v7a")

internal fun abiAssetSegment(abi: String): String = when (abi) {
    "arm64-v8a" -> "v8a"
    "armeabi-v7a" -> "v7a"
    else -> abi
}

internal fun selectReleaseAsset(abis: Array<out String>, assets: List<ReleaseAsset>): ReleaseAsset? {
    for (abi in abis) {
        if (abi.isBlank()) continue
        val prefix = "gusic-${abiAssetSegment(abi)}-"
        assets.firstOrNull { it.name.startsWith(prefix) && it.name.endsWith(".apk") }
            ?.let { return it }
    }
    return assets.firstOrNull { asset ->
        asset.name.startsWith("gusic-") && asset.name.endsWith(".apk") &&
            ABI_ASSET_SEGMENTS.none { asset.name.startsWith("gusic-$it-") }
    }
}

internal fun buildConventionalApkUrl(abis: Array<out String>, tag: String, version: String): String? {
    val segment = abis.firstOrNull { it.isNotBlank() }?.let { abiAssetSegment(it) } ?: return null
    return "$RELEASE_DOWNLOAD_BASE_URL/$tag/gusic-$segment-v$version.apk"
}

internal fun parseVersion(value: String): List<Long> =
    value.trim().removePrefix("v").removePrefix("V")
        .substringBefore('-').substringBefore('+')
        .split('.')
        .map { it.filter(Char::isDigit).toLongOrNull() ?: 0L }

internal fun isNewer(latest: List<Long>, current: List<Long>): Boolean {
    val size = maxOf(latest.size, current.size)
    for (index in 0 until size) {
        val l = latest.getOrElse(index) { 0L }
        val c = current.getOrElse(index) { 0L }
        if (l != c) return l > c
    }
    return false
}

@Singleton
class AppUpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val noRedirectClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val info = fromReleaseApi(RELEASE_API_URL)
            ?: fromReleaseApi(MIRROR_GHPROXY_API_URL)
            ?: fromRedirect(RELEASE_DIRECT_URL)
            ?: fromRedirect(MIRROR_GHFAST_URL)
            ?: fromRedirect(MIRROR_GHPROXY_URL)
            ?: return@withContext UpdateCheckResult.Failed
        if (isNewer(parseVersion(info.latestVersion), parseVersion(currentVersion))) {
            UpdateCheckResult.UpdateAvailable(info)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private fun fromReleaseApi(url: String): AppUpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JsonParser.parseString(response.body.string()).asJsonObject
                val tag = root.get("tag_name")?.takeUnless { it.isJsonNull }?.asString ?: return@use null
                if (tag.isBlank()) return@use null
                val htmlUrl = root.get("html_url")?.takeUnless { it.isJsonNull }?.asString ?: ""
                val notes = root.get("body")?.takeUnless { it.isJsonNull }?.asString ?: ""
                val assets = parseReleaseAssets(root)
                val asset = selectReleaseAsset(Build.SUPPORTED_ABIS, assets)
                AppUpdateInfo(
                    parseVersion(tag).joinToString("."),
                    tag,
                    asset?.downloadUrl,
                    asset?.sizeBytes,
                    notes.takeIf { it.isNotBlank() },
                    htmlUrl.ifBlank { RELEASE_PAGE_URL },
                )
            }
        } catch (e: Exception) {
            Timber.d(e, "AppUpdateChecker: release api failed for %s", url)
            null
        }
    }

    private fun fromRedirect(url: String): AppUpdateInfo? {
        return try {
            val request = Request.Builder().url(url).head().build()
            noRedirectClient.newCall(request).execute().use { response ->
                val tag = response.header("Location")
                    ?.substringAfter("/tag/", "")
                    ?.takeIf { it.isNotBlank() } ?: return@use null
                val version = parseVersion(tag).joinToString(".")
                AppUpdateInfo(
                    version,
                    tag,
                    buildConventionalApkUrl(Build.SUPPORTED_ABIS, tag, version),
                    null,
                    null,
                    RELEASE_PAGE_URL,
                )
            }
        } catch (e: Exception) {
            Timber.d(e, "AppUpdateChecker: redirect endpoint failed for %s", url)
            null
        }
    }

    private companion object {
        const val RELEASE_API_URL = "https://api.github.com/repos/gurio-wine/Gusic/releases/latest"
        const val MIRROR_GHPROXY_API_URL = "https://gh-proxy.com/https://api.github.com/repos/gurio-wine/Gusic/releases/latest"
        const val RELEASE_DIRECT_URL = "https://github.com/gurio-wine/Gusic/releases/latest"
        const val MIRROR_GHFAST_URL = "https://ghfast.top/https://github.com/gurio-wine/Gusic/releases/latest"
        const val MIRROR_GHPROXY_URL = "https://gh-proxy.com/https://github.com/gurio-wine/Gusic/releases/latest"
        const val RELEASE_PAGE_URL = "https://github.com/gurio-wine/Gusic/releases/latest"
    }
}
