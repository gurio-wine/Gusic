package com.gurio.gusic.presentation.viewmodel

import android.content.Intent
import android.os.SystemClock
import com.gurio.gusic.data.update.AppUpdateChecker
import com.gurio.gusic.data.update.AppUpdateDownloader
import com.gurio.gusic.data.update.AppUpdateInfo
import com.gurio.gusic.data.update.AppUpdateInstaller
import com.gurio.gusic.data.update.UpdateCheckResult
import com.gurio.gusic.data.update.UpdateDownloadException
import com.gurio.gusic.data.update.UpdateDownloadFailure
import com.gurio.gusic.data.update.UpdateDownloadState
import com.gurio.gusic.di.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 一次检查更新的结局；发现新版本时由 [AppUpdateStateHolder.updateInfo] 承载，这里回到 Idle。 */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data object Failed : UpdateCheckState
}

/**
 * 应用内更新的共享状态：首页的更新弹窗和设置里的更新页读写同一份数据。
 * 公开方法只在主线程调用。
 */
@Singleton
class AppUpdateStateHolder @Inject constructor(
    private val updateChecker: AppUpdateChecker,
    private val updateDownloader: AppUpdateDownloader,
    private val updateInstaller: AppUpdateInstaller,
    @AppScope private val scope: CoroutineScope,
) {
    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState?>(null)
    val downloadState: StateFlow<UpdateDownloadState?> = _downloadState.asStateFlow()

    private var checkJob: Job? = null
    private var lastCheckElapsedMillis = 0L
    private var downloadJob: Job? = null

    /**
     * 检查更新。离上次检查不足 [minIntervalMillis] 时跳过（省 GitHub 接口限额）；
     * [force] 为 true 时无视间隔（设置页手动检查）。
     */
    fun checkForUpdates(
        currentVersion: String,
        force: Boolean = false,
        minIntervalMillis: Long = MIN_CHECK_INTERVAL_MILLIS,
    ) {
        if (checkJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        val throttled = lastCheckElapsedMillis != 0L &&
            now - lastCheckElapsedMillis < minIntervalMillis
        if (!force && throttled) return
        checkJob = scope.launch {
            _checkState.value = UpdateCheckState.Checking
            val result = try {
                updateChecker.checkForUpdate(currentVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "AppUpdateStateHolder: update check failed")
                UpdateCheckResult.Failed
            }
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateInfo.value = result.info
                    _checkState.value = UpdateCheckState.Idle
                }
                UpdateCheckResult.UpToDate -> {
                    if (_downloadState.value == null) _updateInfo.value = null
                    _checkState.value = UpdateCheckState.UpToDate
                }
                UpdateCheckResult.Failed -> _checkState.value = UpdateCheckState.Failed
            }
            lastCheckElapsedMillis = SystemClock.elapsedRealtime()
        }
    }

    fun startDownload(info: AppUpdateInfo) {
        if (downloadJob?.isActive == true) return
        _updateInfo.value = info
        _downloadState.value = UpdateDownloadState.Downloading(0L, info.apkSizeBytes)
        val previousJob = downloadJob
        downloadJob = scope.launch {
            // 上一次被取消的下载要先清理完（删半成品文件），再开始写同一个路径
            previousJob?.join()
            val outcome = try {
                val file = updateDownloader.download(info) { downloadedBytes, totalBytes ->
                    _downloadState.value = UpdateDownloadState.Downloading(downloadedBytes, totalBytes)
                }
                UpdateDownloadState.Ready(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateDownloadException) {
                UpdateDownloadState.Failed(e.reason)
            } catch (e: Exception) {
                Timber.w(e, "AppUpdateStateHolder: update download failed")
                UpdateDownloadState.Failed(UpdateDownloadFailure.Network)
            }
            _downloadState.value = outcome
        }
    }

    /** 取消下载并清掉下载状态，[updateInfo] 保留，角标还在。 */
    fun cancelDownload() {
        downloadJob?.cancel()
        _downloadState.value = null
    }

    /** 关掉已就绪/失败的弹窗：保留已下载的文件，下次点“下载更新”会直接进入安装。 */
    fun closeDownloadResult() {
        _downloadState.value = null
    }

    /** 用户选择“以后再说”：清掉新版本提示（进行中的下载一并取消）。 */
    fun dismissUpdate() {
        downloadJob?.cancel()
        _downloadState.value = null
        _updateInfo.value = null
    }

    fun canRequestPackageInstalls(): Boolean = updateInstaller.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent = updateInstaller.unknownSourcesIntent()

    /** 返回 false 表示没能拉起系统安装器。 */
    fun installUpdate(file: File): Boolean = updateInstaller.launchInstall(file)

    companion object {
        /** 首页自动检查的最小间隔。 */
        const val MIN_CHECK_INTERVAL_MILLIS = 15L * 60L * 1000L
        /** 进入“应用更新”页自动检查的最小间隔；页面里的手动检查按钮不受限。 */
        const val PAGE_ENTRY_MIN_INTERVAL_MILLIS = 60L * 1000L
    }
}
