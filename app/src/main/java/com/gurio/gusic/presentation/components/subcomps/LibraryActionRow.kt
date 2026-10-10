package com.gurio.gusic.presentation.components.subcomps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gurio.gusic.R
import com.gurio.gusic.data.model.MusicFolder
import com.gurio.gusic.ui.theme.RoundedSans
import java.io.File
import androidx.compose.ui.res.stringResource

val defaultShape = RoundedCornerShape(26.dp)
val genHeight = 42.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryActionRow(
    onMainActionClick: () -> Unit,
    iconRotation: Float,
    showImportButton: Boolean = true,
    isPlaylistTab: Boolean,
    onImportM3uClick: () -> Unit = {},
    isFoldersTab: Boolean,
    modifier: Modifier = Modifier,
    currentFolder: MusicFolder?,
    folderRootPath: String,
    folderRootLabel: String,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    // 歌单/文件夹以外的标签左侧没有任何按钮，整行不渲染也不占高度
    if (!isPlaylistTab && !isFoldersTab) return

    val shouldShowImport = isPlaylistTab && showImportButton

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = isFoldersTab,
            label = "ActionRowContent",
            transitionSpec = {
                if (targetState) {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                } else {
                    slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                }
            },
            modifier = Modifier.weight(1f)
        ) { isFolders ->
            if (isFolders) {
                Breadcrumbs(
                    currentFolder = currentFolder,
                    rootPath = folderRootPath,
                    rootLabel = folderRootLabel,
                    onFolderClick = onFolderClick,
                    onNavigateBack = onNavigateBack
                )
            } else if (isPlaylistTab) {
                val newButtonEndCorner by animateDpAsState(
                    targetValue = if (shouldShowImport) 8.dp else 26.dp,
                    label = "NewButtonEndCorner"
                )

                val importButtonStartCorner by animateDpAsState(
                    targetValue = 8.dp,
                    label = "ImportButtonStartCorner"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val buttonContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                    val buttonContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    
                    FilledTonalButton(
                        onClick = onMainActionClick,
                        shape = RoundedCornerShape(
                            topStart = 26.dp, bottomStart = 26.dp,
                            topEnd =  newButtonEndCorner, bottomEnd = newButtonEndCorner
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.height(genHeight)
                    ) {
                        val icon = Icons.AutoMirrored.Rounded.PlaylistAdd
                        val text = stringResource(R.string.library_action_new)
                        val contentDesc = stringResource(R.string.cd_create_new_playlist)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = contentDesc,
                                modifier = Modifier.size(20.dp).rotate(iconRotation)
                            )
                            Text(
                                modifier = Modifier.animateContentSize(),
                                text = text,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = shouldShowImport,
                        enter = fadeIn() + expandHorizontally(
                            expandFrom = Alignment.Start,
                            clip = false,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                        exit = fadeOut() + shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            clip = false,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Row(modifier = Modifier.height(genHeight), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))

                            FilledTonalButton(
                                onClick = onImportM3uClick,
                                shape = RoundedCornerShape(
                                    topStart = importButtonStartCorner,
                                    bottomStart = importButtonStartCorner,
                                    topEnd = 26.dp,
                                    bottomEnd = 26.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp,
                                    pressedElevation = 6.dp
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 10.dp
                                ),
                                modifier = Modifier.height(genHeight)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.rounded_upload_file_24),
                                        contentDescription = stringResource(R.string.cd_import_m3u_playlist),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.import_file),
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
fun Breadcrumbs(
    currentFolder: MusicFolder?,
    rootPath: String,
    rootLabel: String,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val rowState = rememberLazyListState()
    val pathSegments = remember(currentFolder?.path, rootPath, rootLabel) {
        val path = currentFolder?.path ?: rootPath
        val normalizedRoot = rootPath.removeSuffix("/")
        val normalizedPath = path.removeSuffix("/")
        val relativePath = normalizedPath
            .removePrefix(normalizedRoot)
            .removePrefix("/")

        if (!normalizedPath.startsWith(normalizedRoot) || relativePath.isEmpty() || normalizedPath == normalizedRoot) {
            listOf(rootLabel to rootPath)
        } else {
            listOf(rootLabel to rootPath) + relativePath.split("/").scan("") { acc, segment ->
                "$acc/$segment"
            }.drop(1).map {
                val file = File(rootPath, it)
                file.name to file.path
            }
        }
    }

    val showStartFade by remember { derivedStateOf { rowState.canScrollBackward } }
    val showEndFade by remember { derivedStateOf { rowState.canScrollForward } }

    LaunchedEffect(pathSegments.size) {
        if (pathSegments.isNotEmpty()) {
            rowState.animateScrollToItem(pathSegments.lastIndex + 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(36.dp),
            enabled = currentFolder != null
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.auth_cd_back))
        }
        Spacer(Modifier.width(8.dp))

        LazyRow(
            state = rowState,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()

                    val gradientWidth = 24.dp.toPx()

                    if (showStartFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                endX = gradientWidth
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }

                    if (showEndFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = this.size.width - gradientWidth
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            item { Spacer(modifier = Modifier.width(12.dp)) }

            items(pathSegments.size, key = { pathSegments[it].second }) { index ->
                val (name, path) = pathSegments[index]
                val isLast = index == pathSegments.lastIndex
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = RoundedSans,
                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !isLast) { onFolderClick(path) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                if (!isLast) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.width(12.dp)) }
        }
    }
}
