package com.gurio.gusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.gurio.gusic.data.update.AppUpdateInfo
import com.gurio.gusic.data.update.UpdateDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject

/**
 * [AppUpdateStateHolder] 的薄封装：状态都住在单例里，
 * 所以首页和设置里的更新页各拿一份本 ViewModel 时看到的仍是同一份数据。
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val stateHolder: AppUpdateStateHolder,
) : ViewModel() {

    val updateInfo: StateFlow<AppUpdateInfo?> = stateHolder.updateInfo
    val checkState: StateFlow<UpdateCheckState> = stateHolder.checkState
    val downloadState: StateFlow<UpdateDownloadState?> = stateHolder.downloadState

    fun checkForUpdates(
        currentVersion: String,
        force: Boolean = false,
        minIntervalMillis: Long = AppUpdateStateHolder.MIN_CHECK_INTERVAL_MILLIS,
    ) = stateHolder.checkForUpdates(currentVersion, force, minIntervalMillis)

    fun startDownload(info: AppUpdateInfo) = stateHolder.startDownload(info)

    fun cancelDownload() = stateHolder.cancelDownload()

    fun closeDownloadResult() = stateHolder.closeDownloadResult()

    fun dismissUpdate() = stateHolder.dismissUpdate()

    fun canRequestPackageInstalls(): Boolean = stateHolder.canRequestPackageInstalls()

    fun unknownSourcesIntent() = stateHolder.unknownSourcesIntent()

    fun installUpdate(file: File): Boolean = stateHolder.installUpdate(file)
}
