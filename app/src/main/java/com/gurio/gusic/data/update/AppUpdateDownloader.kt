package com.gurio.gusic.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateDownloadState {
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : UpdateDownloadState
    data class Ready(val file: File) : UpdateDownloadState
    data class Failed(val reason: UpdateDownloadFailure) : UpdateDownloadState
}

enum class UpdateDownloadFailure { Network, Verify, Storage }

class UpdateDownloadException(val reason: UpdateDownloadFailure, cause: Throwable? = null) : Exception(cause)

/** 缓存文件名带原始 tag：同一版本号的不同构建不会互相复用。 */
internal fun updateApkFileName(tag: String): String {
    val safeTag = tag.map { if (it.isLetterOrDigit() || it in "._-") it else '_' }.joinToString("")
    return "gusic-$safeTag.apk"
}

/**
 * 下载发布包到缓存目录并校验签名。
 * 失败抛 [UpdateDownloadException]；协程取消时抛 [CancellationException]，半成品文件会被删除。
 */
@Singleton
class AppUpdateDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    baseClient: OkHttpClient,
) {
    // 走镜像时链路可能很慢，读超时放宽，避免中途断流就整个重下
    private val client = baseClient.newBuilder()
        .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        info: AppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        prepareUpdateDir(info)
        val target = targetFile(info)
        if (isUsableCachedFile(target, info)) return@withContext target

        val part = File(target.parentFile, target.name + PART_SUFFIX)
        try {
            copyFromCandidates(info, part, onProgress)
            if (!verifySignatureMatches(part)) {
                throw UpdateDownloadException(UpdateDownloadFailure.Verify)
            }
            if (target.exists() && !target.delete()) {
                throw UpdateDownloadException(UpdateDownloadFailure.Storage)
            }
            if (!part.renameTo(target)) {
                throw UpdateDownloadException(UpdateDownloadFailure.Storage)
            }
            target
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    private fun prepareUpdateDir(info: AppUpdateInfo) {
        val dir = updateDir()
        if (!dir.exists() && !dir.mkdirs()) {
            throw UpdateDownloadException(UpdateDownloadFailure.Storage)
        }
        val target = targetFile(info)
        dir.listFiles()?.forEach { file ->
            if (file.absolutePath != target.absolutePath) file.deleteRecursively()
        }
    }

    private fun updateDir(): File = File(context.cacheDir, UPDATES_DIR)

    private fun targetFile(info: AppUpdateInfo): File =
        File(updateDir(), updateApkFileName(info.tag))

    private fun isUsableCachedFile(file: File, info: AppUpdateInfo): Boolean {
        if (!file.isFile) return false
        if (info.apkSizeBytes != null && file.length() != info.apkSizeBytes) return false
        return verifySignatureMatches(file)
    }

    private fun downloadCandidates(apkUrl: String?): List<String> {
        if (apkUrl.isNullOrBlank()) return emptyList()
        return linkedSetOf(
            apkUrl,
            "$MIRROR_GHFAST_PREFIX$apkUrl",
            "$MIRROR_GHPROXY_PREFIX$apkUrl",
        ).toList()
    }

    /** 依次尝试直连和各镜像，任一候选成功即返回；全部失败抛 [UpdateDownloadFailure.Network]。 */
    private suspend fun copyFromCandidates(
        info: AppUpdateInfo,
        part: File,
        onProgress: (Long, Long?) -> Unit,
    ) {
        val candidates = downloadCandidates(info.apkUrl)
        if (candidates.isEmpty()) throw UpdateDownloadException(UpdateDownloadFailure.Network)
        for (url in candidates) {
            part.delete()
            val copied = try {
                copyFromCandidate(url, part, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                Timber.d(e, "AppUpdateDownloader: candidate failed: %s", url)
                false
            }
            if (copied) return
            part.delete()
        }
        throw UpdateDownloadException(UpdateDownloadFailure.Network)
    }

    /** 返回 false 表示该候选没给出可用的安装包（非 2xx、HTML 页面或非 ZIP 内容）。 */
    private suspend fun copyFromCandidate(
        url: String,
        part: File,
        onProgress: (Long, Long?) -> Unit,
    ): Boolean {
        val call = client.newCall(Request.Builder().url(url).build())
        return coroutineScope {
            // OkHttp 的阻塞读不响应协程取消：挂一个随作用域一起被取消的看守，负责掐断连接
            val cancelWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    if (response.header("Content-Type")?.contains("text/html", ignoreCase = true) == true) {
                        return@use false
                    }
                    copyBodyToFile(response, part, onProgress)
                }
            } finally {
                cancelWatcher.cancel()
            }
        }
    }

    private suspend fun copyBodyToFile(
        response: okhttp3.Response,
        part: File,
        onProgress: (Long, Long?) -> Unit,
    ): Boolean {
        val body = response.body
        val total = body.contentLength().takeIf { it > 0 }
        var lastEmittedPercent = -1
        var lastEmittedBytes = -1L
        var firstChunk = true
        body.byteStream().use { input ->
            FileOutputStream(part).use { output ->
                val buffer = ByteArray(BUFFER_SIZE_BYTES)
                var downloaded = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (firstChunk) {
                        // 安装包是 ZIP：开头不是 PK 说明拿到的是错误页之类的垃圾，立刻判废
                        if (read < APK_MAGIC.size || !buffer.copyOfRange(0, APK_MAGIC.size).contentEquals(APK_MAGIC)) {
                            return false
                        }
                        firstChunk = false
                    }
                    try {
                        output.write(buffer, 0, read)
                    } catch (e: IOException) {
                        throw UpdateDownloadException(UpdateDownloadFailure.Storage, e)
                    }
                    downloaded += read
                    if (total == null) {
                        if (lastEmittedBytes == -1L || downloaded - lastEmittedBytes >= PROGRESS_STEP_BYTES) {
                            lastEmittedBytes = downloaded
                            onProgress(downloaded, null)
                        }
                    } else {
                        val percent = ((downloaded * 100) / total).toInt()
                        if (percent > lastEmittedPercent) {
                            lastEmittedPercent = percent
                            lastEmittedBytes = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }
                try {
                    output.flush()
                } catch (e: IOException) {
                    throw UpdateDownloadException(UpdateDownloadFailure.Storage, e)
                }
            }
        }
        return !firstChunk
    }

    private fun verifySignatureMatches(file: File): Boolean {
        return try {
            val packageManager = context.packageManager
            val archiveInfo = packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ) ?: return false
            val installedInfo = packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val archiveCerts = archiveInfo.signingInfo?.certificateSet() ?: return false
            val installedCerts = installedInfo.signingInfo?.certificateSet() ?: return false
            archiveInfo.packageName == context.packageName && archiveCerts == installedCerts
        } catch (e: Exception) {
            Timber.d(e, "AppUpdateDownloader: signature verification failed")
            false
        }
    }

    private fun SigningInfo.certificateSet(): Set<String>? =
        apkContentsSigners
            ?.map { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }
            ?.toSet()

    private companion object {
        const val UPDATES_DIR = "updates"
        const val PART_SUFFIX = ".part"
        const val BUFFER_SIZE_BYTES = 64 * 1024
        const val PROGRESS_STEP_BYTES = 512 * 1024
        const val DOWNLOAD_READ_TIMEOUT_SECONDS = 60L
        const val MIRROR_GHFAST_PREFIX = "https://ghfast.top/"
        const val MIRROR_GHPROXY_PREFIX = "https://gh-proxy.com/"
        val APK_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)
    }
}
