package com.gurio.gusic.presentation.components

import android.content.Intent
import android.text.format.Formatter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gurio.gusic.BuildConfig
import com.gurio.gusic.R
import com.gurio.gusic.data.update.UpdateDownloadFailure
import com.gurio.gusic.data.update.UpdateDownloadState
import com.gurio.gusic.presentation.viewmodel.AppUpdateViewModel
import java.io.File

/**
 * 应用内更新弹窗：首页与设置里的更新页共用。
 * 状态都住在 [AppUpdateViewModel] 背后的单例里，弹窗只负责展示与交互。
 * 调用方用 `if (showDialog)` 控制挂载，关闭时通过 [onDismiss] 回调。
 */
@Composable
fun AppUpdateDialog(
    viewModel: AppUpdateViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    var installPermissionDenied by rememberSaveable { mutableStateOf(false) }

    fun openReleasePage(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // 缓存目录可能被系统清理：文件已经不在就退回未下载状态，让用户重新下载
    fun installDownloaded(file: File, releasePageUrl: String) {
        if (!file.isFile) {
            viewModel.cancelDownload()
        } else if (!viewModel.installUpdate(file)) {
            viewModel.dismissUpdate()
            onDismiss()
            openReleasePage(releasePageUrl)
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val readyFile = (viewModel.downloadState.value as? UpdateDownloadState.Ready)?.file
        if (viewModel.canRequestPackageInstalls()) {
            installPermissionDenied = false
            readyFile?.let { installDownloaded(it, viewModel.updateInfo.value?.releasePageUrl.orEmpty()) }
        } else {
            installPermissionDenied = true
        }
    }
    LaunchedEffect(Unit) { installPermissionDenied = false }

    // 更新信息被清掉时（例如检查结果变为已是最新）主动收起，别让调用方的开关停在 true
    val update = updateInfo ?: run {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val state = downloadState
    AlertDialog(
        onDismissRequest = {
            when (state) {
                is UpdateDownloadState.Downloading -> viewModel.cancelDownload()
                is UpdateDownloadState.Ready,
                is UpdateDownloadState.Failed -> viewModel.closeDownloadResult()
                null -> viewModel.dismissUpdate()
            }
            onDismiss()
        },
        title = {
            Text(stringResource(R.string.update_available_title, update.latestVersion))
        },
        text = {
            when (state) {
                null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        update.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 10,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        update.apkSizeBytes?.let { sizeBytes ->
                            Text(
                                text = stringResource(
                                    R.string.update_available_size,
                                    Formatter.formatShortFileSize(context, sizeBytes)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    val totalBytes = state.totalBytes?.takeIf { it > 0 }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.update_downloading_label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (totalBytes != null) {
                            LinearProgressIndicator(
                                progress = {
                                    (state.downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(
                                    R.string.update_download_progress,
                                    ((state.downloadedBytes * 100) / totalBytes).toInt(),
                                    Formatter.formatShortFileSize(context, state.downloadedBytes),
                                    Formatter.formatShortFileSize(context, totalBytes)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = stringResource(
                                    R.string.update_download_progress_unknown,
                                    Formatter.formatShortFileSize(context, state.downloadedBytes)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                is UpdateDownloadState.Ready -> {
                    Text(
                        text = stringResource(
                            if (installPermissionDenied) {
                                R.string.update_install_permission_needed
                            } else {
                                R.string.update_download_ready
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is UpdateDownloadState.Failed -> {
                    Text(
                        text = stringResource(
                            when (state.reason) {
                                UpdateDownloadFailure.Verify -> R.string.update_download_failed_verify
                                UpdateDownloadFailure.Network -> R.string.update_download_failed_network
                                UpdateDownloadFailure.Storage -> R.string.update_download_failed_storage
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                null -> {
                    val downloadInApp = BuildConfig.BUILD_TYPE == "release" && update.apkUrl != null
                    TextButton(
                        onClick = {
                            if (downloadInApp) {
                                viewModel.startDownload(update)
                            } else {
                                viewModel.dismissUpdate()
                                onDismiss()
                                openReleasePage(update.releasePageUrl)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.update_available_download))
                    }
                }

                is UpdateDownloadState.Downloading -> {
                    TextButton(onClick = { viewModel.cancelDownload() }) {
                        Text(stringResource(R.string.update_download_cancel))
                    }
                }

                is UpdateDownloadState.Ready -> {
                    TextButton(
                        onClick = {
                            if (!state.file.isFile) {
                                viewModel.cancelDownload()
                            } else if (viewModel.canRequestPackageInstalls()) {
                                installDownloaded(state.file, update.releasePageUrl)
                            } else {
                                unknownSourcesLauncher.launch(viewModel.unknownSourcesIntent())
                            }
                        }
                    ) {
                        Text(stringResource(R.string.update_install))
                    }
                }

                is UpdateDownloadState.Failed -> {
                    val verifyFailed = state.reason == UpdateDownloadFailure.Verify
                    TextButton(
                        onClick = {
                            if (verifyFailed) {
                                viewModel.dismissUpdate()
                                onDismiss()
                                openReleasePage(update.releasePageUrl)
                            } else {
                                viewModel.startDownload(update)
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                if (verifyFailed) R.string.update_download_open_release
                                else R.string.update_download_retry
                            )
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    when (state) {
                        is UpdateDownloadState.Downloading -> viewModel.cancelDownload()
                        is UpdateDownloadState.Ready,
                        is UpdateDownloadState.Failed -> viewModel.closeDownloadResult()
                        null -> viewModel.dismissUpdate()
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.update_available_later))
            }
        },
    )
}
