package com.gurio.gusic.presentation.screens.lxmusic

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gurio.gusic.R
import com.gurio.gusic.data.lxmusic.LxScriptMeta
import com.gurio.gusic.presentation.components.SmartImage
import com.gurio.gusic.presentation.components.SmartImageListTargetSize
import com.gurio.gusic.presentation.components.rememberModalSheetState
import com.gurio.gusic.ui.theme.RoundedSans

/**
 * 音源脚本管理 sheet：从 LxMusicScreen 原地抽出，供发现页与“我的”页两处挂载。
 * 登录行的 WebView 会话清理由宿主通过 onShowXxxLogin 回调完成（与原行为一致）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LxScriptManagementSheet(
    onDismiss: () -> Unit,
    onShowNeteaseLogin: () -> Unit,
    onShowTencentLogin: () -> Unit,
    onShowKugouLogin: () -> Unit,
    onShowKuwoLogin: () -> Unit,
    lxMusicViewModel: LxMusicViewModel,
) {
    val scripts by lxMusicViewModel.scripts.collectAsStateWithLifecycle()
    val activeScript by lxMusicViewModel.activeScript.collectAsStateWithLifecycle()
    val runtimePaths by lxMusicViewModel.runtimePaths.collectAsStateWithLifecycle()
    val loadState by lxMusicViewModel.loadState.collectAsStateWithLifecycle()
    val preferredQuality by lxMusicViewModel.preferredQuality.collectAsStateWithLifecycle()
    val activeScriptName = remember(activeScript, scripts) {
        scripts.firstOrNull { it.assetPath == activeScript }?.name
    }

    val neteaseCookie by lxMusicViewModel.neteaseCookie.collectAsStateWithLifecycle()
    val neteaseVipType by lxMusicViewModel.neteaseVipType.collectAsStateWithLifecycle()
    val neteaseCookieValid by lxMusicViewModel.neteaseCookieValid.collectAsStateWithLifecycle()
    val neteaseNickname by lxMusicViewModel.neteaseNickname.collectAsStateWithLifecycle()
    val neteaseAvatar by lxMusicViewModel.neteaseAvatar.collectAsStateWithLifecycle()
    val tencentCookie by lxMusicViewModel.tencentCookie.collectAsStateWithLifecycle()
    val tencentCookieValid by lxMusicViewModel.tencentCookieValid.collectAsStateWithLifecycle()
    val tencentNickname by lxMusicViewModel.tencentNickname.collectAsStateWithLifecycle()
    val tencentAvatar by lxMusicViewModel.tencentAvatar.collectAsStateWithLifecycle()
    val kugouCookie by lxMusicViewModel.kugouCookie.collectAsStateWithLifecycle()
    val kugouCookieValid by lxMusicViewModel.kugouCookieValid.collectAsStateWithLifecycle()
    val kugouNickname by lxMusicViewModel.kugouNickname.collectAsStateWithLifecycle()
    val kugouAvatar by lxMusicViewModel.kugouAvatar.collectAsStateWithLifecycle()
    val kugouLoggedIn by lxMusicViewModel.kugouLoggedIn.collectAsStateWithLifecycle()
    val kuwoCookie by lxMusicViewModel.kuwoCookie.collectAsStateWithLifecycle()
    val kuwoCookieValid by lxMusicViewModel.kuwoCookieValid.collectAsStateWithLifecycle()
    val kuwoNickname by lxMusicViewModel.kuwoNickname.collectAsStateWithLifecycle()
    val kuwoAvatar by lxMusicViewModel.kuwoAvatar.collectAsStateWithLifecycle()

    var showUrlImport by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var urlImporting by remember { mutableStateOf(false) }
    var scriptToDelete by remember { mutableStateOf<LxScriptMeta?>(null) }
    var showQualityDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { lxMusicViewModel.importFromFile(it) { } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())) {
            var scriptIntroExpanded by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.lx_title_source_scripts),
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(
                    onClick = { scriptIntroExpanded = !scriptIntroExpanded },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (scriptIntroExpanded) {
                            Icons.Rounded.ExpandLess
                        } else {
                            Icons.Rounded.ExpandMore
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = scriptIntroExpanded) {
                Text(
                    text = stringResource(R.string.lx_msg_script_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            if (activeScriptName != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.lx_title_current_source),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                            Text(
                                text = activeScriptName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (loadState.loading) {
                Text(
                    text = stringResource(R.string.lx_msg_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            loadState.error?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (loadState.loadedSources.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.lx_msg_declared_sources, loadState.loadedSources.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { showUrlImport = true },
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.lx_btn_url_import), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
                FilledTonalButton(
                    onClick = { filePicker.launch(arrayOf("application/javascript", "text/plain", "*/*")) },
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.lx_btn_file_import), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(12.dp))

            val loginStatusText = when {
                neteaseCookie.isNullOrBlank() -> stringResource(R.string.lx_msg_not_logged_in)
                !neteaseCookieValid -> stringResource(R.string.lx_msg_login_expired_status)
                neteaseVipType == 13 ->
                    neteaseNickname?.let { stringResource(R.string.lx_status_nickname_svip, it) } ?: stringResource(R.string.lx_status_logged_in_svip)
                neteaseVipType == 11 ->
                    neteaseNickname?.let { stringResource(R.string.lx_status_nickname_vip, it) } ?: stringResource(R.string.lx_status_logged_in_vip)
                neteaseVipType == 0 ->
                    neteaseNickname?.let { stringResource(R.string.lx_status_nickname_logged_in, it) } ?: stringResource(R.string.lx_status_logged_in)
                else -> neteaseNickname ?: stringResource(R.string.lx_status_logged_in)
            }
            @Composable
            fun platformStatusText(
                cookie: String?, valid: Boolean, nickname: String?, appLoggedIn: Boolean = false,
            ): String = when {
                appLoggedIn || (!cookie.isNullOrBlank() && valid) ->
                    nickname?.let { stringResource(R.string.lx_status_nickname_logged_in, it) }
                        ?: stringResource(R.string.lx_status_logged_in)
                cookie.isNullOrBlank() -> stringResource(R.string.lx_msg_not_logged_in_platform)
                else -> stringResource(R.string.lx_msg_login_expired_status)
            }
            @Composable
            fun PlatformLoginRow(
                title: String,
                statusText: String,
                avatarUrl: String?,
                trailing: @Composable () -> Unit,
                onClick: () -> Unit,
            ) {
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            SmartImage(
                                model = avatarUrl,
                                contentDescription = null,
                                targetSize = SmartImageListTargetSize,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        trailing()
                    }
                }
            }
            PlatformLoginRow(
                title = platformName("tx"),
                statusText = platformStatusText(tencentCookie, tencentCookieValid, tencentNickname),
                avatarUrl = if (!tencentCookie.isNullOrBlank() && tencentCookieValid) tencentAvatar else null,
                trailing = {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                },
                onClick = onShowTencentLogin,
            )
            Spacer(Modifier.height(8.dp))
            PlatformLoginRow(
                title = platformName("wy"),
                statusText = loginStatusText,
                avatarUrl = if (!neteaseCookie.isNullOrBlank() && neteaseCookieValid) neteaseAvatar else null,
                trailing = {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                },
                onClick = onShowNeteaseLogin,
            )
            Spacer(Modifier.height(8.dp))
            PlatformLoginRow(
                title = platformName("kg"),
                statusText = platformStatusText(kugouCookie, kugouCookieValid, kugouNickname, appLoggedIn = kugouLoggedIn),
                avatarUrl = if (kugouLoggedIn) kugouAvatar else null,
                trailing = {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                },
                onClick = onShowKugouLogin,
            )
            Spacer(Modifier.height(8.dp))
            PlatformLoginRow(
                title = platformName("kw"),
                statusText = platformStatusText(kuwoCookie, kuwoCookieValid, kuwoNickname),
                avatarUrl = if (!kuwoCookie.isNullOrBlank() && kuwoCookieValid) kuwoAvatar else null,
                trailing = {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                },
                onClick = onShowKuwoLogin,
            )

            Spacer(Modifier.height(12.dp))

            val qualityLabel = when (preferredQuality) {
                "128k" -> stringResource(R.string.lxmusic_quality_128k)
                "320k" -> stringResource(R.string.lxmusic_quality_320k)
                "flac" -> stringResource(R.string.lxmusic_quality_flac)
                "flac24bit" -> stringResource(R.string.lxmusic_quality_flac24bit)
                "atmos" -> stringResource(R.string.lxmusic_quality_atmos)
                "master" -> stringResource(R.string.lxmusic_quality_master)
                else -> stringResource(R.string.lxmusic_quality_320k)
            }
            Surface(
                onClick = { showQualityDialog = true },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.lxmusic_quality_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = qualityLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            scripts.forEachIndexed { index, meta ->
                ScriptRow(
                    meta = meta,
                    isPreferred = activeScript == meta.assetPath,
                    isEnabled = meta.assetPath in runtimePaths,
                    shape = when {
                        scripts.size == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(
                            topStart = 24.dp, topEnd = 24.dp,
                            bottomStart = 4.dp, bottomEnd = 4.dp,
                        )
                        index == scripts.size - 1 -> RoundedCornerShape(
                            topStart = 4.dp, topEnd = 4.dp,
                            bottomStart = 24.dp, bottomEnd = 24.dp,
                        )
                        else -> RoundedCornerShape(4.dp)
                    },
                    onToggle = { checked ->
                        if (checked) lxMusicViewModel.selectScript(meta.assetPath)
                        else lxMusicViewModel.unloadScript(meta.assetPath)
                    },
                    onSetPreferred = { lxMusicViewModel.makePreferred(meta.assetPath) },
                    onDelete = {
                        scriptToDelete = meta
                    },
                )
                if (index < scripts.size - 1) Spacer(Modifier.height(2.dp))
            }

            Spacer(Modifier.height(12.dp))

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showUrlImport) {
        val urlFieldColor = MaterialTheme.colorScheme.secondaryContainer
            .compositeOver(MaterialTheme.colorScheme.surface)
        val onUrlFieldColor = MaterialTheme.colorScheme.onSecondaryContainer
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { if (!urlImporting) showUrlImport = false },
            title = { Text(stringResource(R.string.lx_title_import_from_url)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(urlFieldColor)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !urlImporting,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = onUrlFieldColor),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (urlInput.isNotBlank() && !urlImporting) {
                                urlImporting = true
                                lxMusicViewModel.importFromUrl(urlInput) {
                                    urlImporting = false
                                    showUrlImport = false
                                    urlInput = ""
                                }
                            }
                        }),
                    ) { innerTextField ->
                        if (urlInput.isEmpty()) {
                            Text("https://example.com/source.js",
                                style = MaterialTheme.typography.bodyLarge,
                                color = onUrlFieldColor.copy(alpha = 0.6f))
                        }
                        innerTextField()
                    }
                }
            },
            confirmButton = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalIconButton(
                        enabled = !urlImporting,
                        onClick = { showUrlImport = false },
                        shape = lxSheetDialogCancelShape,
                        modifier = Modifier
                            .shadow(2.dp, lxSheetDialogCancelShape)
                            .size(42.dp),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                    }
                    FilledTonalIconButton(
                        enabled = urlInput.isNotBlank() && !urlImporting,
                        onClick = {
                            urlImporting = true
                            lxMusicViewModel.importFromUrl(urlInput) {
                                urlImporting = false
                                showUrlImport = false
                                urlInput = ""
                            }
                        },
                        shape = lxSheetDialogConfirmShape,
                        modifier = Modifier
                            .then(
                                if (urlInput.isNotBlank()) Modifier.shadow(2.dp, lxSheetDialogConfirmShape)
                                else Modifier
                            )
                            .size(42.dp),
                    ) {
                        if (urlImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.lx_desc_import))
                        }
                    }
                }
            },
        )
    }

    if (scriptToDelete != null) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { scriptToDelete = null },
            title = { Text(stringResource(R.string.lx_title_delete_script)) },
            text = { Text(stringResource(R.string.lx_msg_delete_script_confirm, scriptToDelete?.name ?: "")) },
            confirmButton = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = { scriptToDelete = null },
                        shape = lxSheetDialogCancelShape,
                        modifier = Modifier
                            .shadow(2.dp, lxSheetDialogCancelShape)
                            .size(42.dp),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                    }
                    FilledTonalIconButton(
                        onClick = {
                            scriptToDelete?.let { lxMusicViewModel.deleteScript(it.assetPath) }
                            scriptToDelete = null
                        },
                        shape = lxSheetDialogConfirmShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .shadow(2.dp, lxSheetDialogConfirmShape)
                            .size(42.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.lx_desc_delete))
                    }
                }
            },
        )
    }

    if (showQualityDialog) {
        val options = listOf(
            "master" to (R.string.lxmusic_quality_master to R.string.lxmusic_quality_master_desc),
            "atmos" to (R.string.lxmusic_quality_atmos to R.string.lxmusic_quality_atmos_desc),
            "flac24bit" to (R.string.lxmusic_quality_flac24bit to R.string.lxmusic_quality_flac24bit_desc),
            "flac" to (R.string.lxmusic_quality_flac to R.string.lxmusic_quality_flac_desc),
            "320k" to (R.string.lxmusic_quality_320k to R.string.lxmusic_quality_320k_desc),
            "128k" to (R.string.lxmusic_quality_128k to R.string.lxmusic_quality_128k_desc),
        )
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.lxmusic_quality_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { (key, labelDesc) ->
                        val (labelRes, descRes) = labelDesc
                        val isSelected = preferredQuality == key
                        Surface(
                            onClick = {
                                lxMusicViewModel.setPreferredQuality(key)
                                showQualityDialog = false
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalIconButton(
                    onClick = { showQualityDialog = false },
                    shape = lxSheetDialogCancelShape,
                    modifier = Modifier
                        .shadow(2.dp, lxSheetDialogCancelShape)
                        .size(42.dp),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.lx_desc_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScriptRow(
    meta: LxScriptMeta,
    isPreferred: Boolean,
    isEnabled: Boolean,
    shape: RoundedCornerShape,
    onToggle: (Boolean) -> Unit,
    onSetPreferred: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val containerColor = if (isPreferred) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainer
    val titleColor = when {
        isPreferred -> MaterialTheme.colorScheme.onPrimaryContainer
        isEnabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = containerColor,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = { if (isEnabled && !isPreferred) onSetPreferred() },
                onLongClick = { if (!meta.isDefault) onDelete() },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = meta.name + if (isPreferred) stringResource(R.string.lx_msg_preferred_suffix) else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPreferred) FontWeight.Bold else FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = buildList {
                    if (meta.author.isNotBlank()) add(meta.author)
                    if (meta.version.isNotBlank()) add("v" + meta.version)
                }.joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isPreferred) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!meta.isDefault) {
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle(checked)
                    },
                    thumbContent = {
                        AnimatedContent(
                            targetState = isEnabled,
                            transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                            label = "script_switch_thumb_icon",
                        ) { isChecked ->
                            Icon(
                                imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedIconColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }
        }
    }
}

private val lxSheetDialogCancelShape = RoundedCornerShape(
    topStart = 26.dp, bottomStart = 26.dp,
    topEnd = 8.dp, bottomEnd = 8.dp,
)
private val lxSheetDialogConfirmShape = RoundedCornerShape(
    topStart = 8.dp, bottomStart = 8.dp,
    topEnd = 26.dp, bottomEnd = 26.dp,
)

@Composable
private fun platformName(source: String): String {
    val resourceId = when (source) {
        "wy" -> R.string.lx_source_netease
        "kw" -> R.string.lx_source_kuwo
        "tx" -> R.string.lx_source_qq
        "kg" -> R.string.lx_source_kugou
        else -> null
    }
    return resourceId?.let { stringResource(it) } ?: source
}

/**
 * 四平台登录弹窗宿主：从 LxMusicScreen 原地抽出，供发现页与“我的”页两处挂载。
 * 各弹窗的显示开关与关闭回调由宿主持有，登录成功后的关闭行为与原实现一致。
 */
@Composable
fun LxLoginDialogsHost(
    lxMusicViewModel: LxMusicViewModel,
    showNetease: Boolean,
    showTencent: Boolean,
    showKugou: Boolean,
    showKuwo: Boolean,
    onDismissNetease: () -> Unit,
    onDismissTencent: () -> Unit,
    onDismissKugou: () -> Unit,
    onDismissKuwo: () -> Unit,
) {
    if (showNetease) {
        NeteaseLoginDialog(
            onDismiss = onDismissNetease,
            onValidate = { cookie, callback ->
                lxMusicViewModel.validateAndLogin(cookie) { ok, error ->
                    callback(ok, error)
                    if (ok) onDismissNetease()
                }
            },
        )
    }
    if (showTencent) {
        TencentWebLoginDialog(
            onDismiss = onDismissTencent,
            onValidate = { cookie, callback ->
                lxMusicViewModel.validateAndLoginTencent(cookie) { ok, error ->
                    callback(ok, error)
                    if (ok) onDismissTencent()
                }
            },
        )
    }
    if (showKugou) {
        KugouSmsLoginDialog(
            onDismiss = onDismissKugou,
            onSendCode = { mobile, callback ->
                lxMusicViewModel.sendKugouSmsCode(mobile) { ok, error -> callback(ok, error) }
            },
            onLogin = { mobile, code, callback ->
                lxMusicViewModel.loginKugouBySms(mobile, code) { ok, error ->
                    callback(ok, error)
                    if (ok) onDismissKugou()
                }
            },
        )
    }
    if (showKuwo) {
        // 酷我只保留短信验证码登录：原生会话（pl3_getlist 读自建大歌单必需）只有这条通道能签发
        KuwoSmsLoginDialog(
            onDismiss = onDismissKuwo,
            onSendCode = { mobile, callback ->
                lxMusicViewModel.sendKuwoSmsCode(mobile) { ok, error -> callback(ok, error) }
            },
            onLogin = { mobile, code, callback ->
                lxMusicViewModel.loginKuwoBySms(mobile, code) { ok, error ->
                    callback(ok, error)
                    if (ok) onDismissKuwo()
                }
            },
        )
    }
}
