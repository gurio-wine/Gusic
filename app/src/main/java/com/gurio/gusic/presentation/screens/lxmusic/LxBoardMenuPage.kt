package com.gurio.gusic.presentation.screens.lxmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gurio.gusic.R
import com.gurio.gusic.data.lxmusic.LxBoard
import com.gurio.gusic.data.lxmusic.LxSong
import com.gurio.gusic.data.lxmusic.LxSources
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 排行榜菜单内容块：榜单卡按每行两张平铺进浏览态混合列表
 * （发现页已无“推荐/排行榜”分类页签，搜索框下直接展示，网易推荐内容在上、榜单卡在下）。
 */
internal fun LazyListScope.boardMenuItems(
    browseState: LxMusicViewModel.BrowseState,
    source: String,
    onSelectBoard: (LxBoard) -> Unit,
) {
    browseState.boards.chunked(2).forEachIndexed { rowIndex, rowBoards ->
        item(key = "board_row_$rowIndex") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowBoards.forEach { board ->
                    LxBoardCard(
                        board = board,
                        source = source,
                        preview = browseState.boardPreviews[board.id],
                        isLoading = browseState.loadingBoardPreviews,
                        onSelectBoard = onSelectBoard,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowBoards.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 榜单卡片：头部为品牌色榜名，下方展示前三首歌曲预览。 */
@Composable
private fun LxBoardCard(
    board: LxBoard,
    source: String,
    preview: List<LxSong>?,
    isLoading: Boolean,
    onSelectBoard: (LxBoard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brandColor = LxSources.brandColor(source)
    val onBrandColor = LxSources.onBrandColor(source)
    Card(
        onClick = { onSelectBoard(board) },
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 20.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 20.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 20.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 20.dp,
            smoothnessAsPercentBR = 60,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(brandColor, brandColor.copy(alpha = 0.72f))
                        )
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = board.name,
                    color = onBrandColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val songs = preview.orEmpty().take(3)
                when {
                    songs.isNotEmpty() -> {
                        songs.forEachIndexed { index, song ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${index + 1}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(14.dp),
                                )
                                Text(
                                    text = "${song.name} - ${song.singer}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    preview == null && isLoading -> {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.lx_board_preview_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}
