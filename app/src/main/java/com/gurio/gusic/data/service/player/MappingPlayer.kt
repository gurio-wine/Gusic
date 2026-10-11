package com.gurio.gusic.data.service.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.gurio.gusic.utils.MediaItemBuilder

/**
 * Rewrites app-internal artwork URIs on outgoing media items to shareable content URIs, so
 * external session consumers (system media surfaces, notification listeners, etc.) can load
 * the artwork instead of receiving an unreadable custom scheme.
 */
@OptIn(UnstableApi::class)
class MappingPlayer(
    val innerPlayer: Player,
    private val context: Context
) : ForwardingPlayer(innerPlayer) {

    private fun mapMediaItem(mediaItem: MediaItem?): MediaItem? {
        if (mediaItem == null) return null
        val artworkUri = mediaItem.mediaMetadata.artworkUri ?: return mediaItem
        val exposedArtworkUri = MediaItemBuilder.externalControllerArtworkUri(
            context = context,
            rawArtworkUri = artworkUri.toString()
        ) ?: return mediaItem
        if (exposedArtworkUri == artworkUri) return mediaItem

        val mappedMetadata = mediaItem.mediaMetadata.buildUpon()
            .setArtworkUri(exposedArtworkUri)
            .build()
        return mediaItem.buildUpon()
            .setMediaMetadata(mappedMetadata)
            .build()
    }

    override fun getCurrentMediaItem(): MediaItem? {
        return mapMediaItem(super.getCurrentMediaItem())
    }

    /**
     * 会话层常开 seek-to-previous：ExoPlayer 在单曲循环等状态下会撤掉这两个命令，
     * 导致通知/系统媒体控制里的原生“上一首”按钮消失。实际调用 [seekToPrevious]
     * 时有兜底分支，不会越界。
     */
    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        if (command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ) {
            return true
        }
        return super.isCommandAvailable(command)
    }

    override fun seekToPrevious() {
        // 内层命令被裁剪时的兜底：按会话层顺序优先级逐档降级，
        // 全部不可用时退回当前曲开头；命令不可用的 seek 会被 ExoPlayer 静默忽略，
        // 因此判据必须用命令可用性（currentMediaItemIndex 在 shuffle 下不可靠）
        when {
            innerPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) ->
                innerPlayer.seekToPrevious()
            innerPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ->
                innerPlayer.seekToPreviousMediaItem()
            innerPlayer.currentMediaItemIndex > 0 &&
                innerPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) ->
                innerPlayer.seekTo(innerPlayer.currentMediaItemIndex - 1, C.TIME_UNSET)
            innerPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ->
                innerPlayer.seekTo(0)
            else -> Unit
        }
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        return mapMediaItem(super.getMediaItemAt(index))!!
    }

    override fun getMediaMetadata(): MediaMetadata {
        val metadata = super.getMediaMetadata()
        val artworkUri = metadata.artworkUri ?: return metadata
        val exposedArtworkUri = MediaItemBuilder.externalControllerArtworkUri(
            context = context,
            rawArtworkUri = artworkUri.toString()
        ) ?: return metadata
        if (exposedArtworkUri == artworkUri) return metadata

        return metadata.buildUpon()
            .setArtworkUri(exposedArtworkUri)
            .build()
    }

    override fun getCurrentTimeline(): Timeline {
        val timeline = super.getCurrentTimeline()
        if (timeline.isEmpty) return timeline
        return object : Timeline() {
            override fun getWindowCount(): Int = timeline.windowCount
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val w = timeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
                w.mediaItem = mapMediaItem(w.mediaItem) ?: MediaItem.EMPTY
                return w
            }
            override fun getPeriodCount(): Int = timeline.periodCount
            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
                return timeline.getPeriod(periodIndex, period, setIds)
            }
            override fun getIndexOfPeriod(periodUid: Any): Int = timeline.getIndexOfPeriod(periodUid)
            override fun getUidOfPeriod(periodIndex: Int): Any = timeline.getUidOfPeriod(periodIndex)
        }
    }
}
