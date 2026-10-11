package com.gurio.gusic.data.service

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.gurio.gusic.R

/**
 * 基类在有自定义 BACK 槽按钮（循环）时走 if/else 分支，不会补上原生“上一首”。
 * 这里在 BACK 之后手动插入一个带语义的上一首按钮，
 * 最终通知顺序为：循环、上一首、播放/暂停、下一首、收藏。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class CycleFirstNotificationProvider(private val appContext: Context) :
    DefaultMediaNotificationProvider(appContext) {

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val buttons = super.getMediaButtons(session, playerCommands, mediaButtonPreferences, showPauseButton)
        // 前提：MappingPlayer 对会话层常开 seek-to-previous（含 MEDIA_ITEM），
        // 故 playerCommands 必然含该命令；若此前提变更，这里会跳过插入以避免无效按钮
        if (!playerCommands.containsAny(
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
        ) {
            return buttons
        }
        val hasBackCustom = buttons.firstOrNull()?.sessionCommand != null
        val hasPrev = buttons.any { it.playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM }
        if (!hasBackCustom || hasPrev || buttons.size >= 5) return buttons
        val previousButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .setDisplayName(appContext.getString(R.string.previous_track))
            .build()
        return ImmutableList.builder<CommandButton>()
            .add(buttons[0])
            .add(previousButton)
            .addAll(buttons.subList(1, buttons.size))
            .build()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
class LocalOnlyMediaNotificationProvider(
    private val context: Context,
    private val delegate: DefaultMediaNotificationProvider =
        CycleFirstNotificationProvider(context),
) : MediaNotification.Provider {

    @Volatile
    var currentLyricLine: String? = null

    @Volatile
    var cachedActionFactory: MediaNotification.ActionFactory? = null

    @Volatile
    var cachedCallback: MediaNotification.Provider.Callback? = null

    @Volatile
    var cachedCustomLayout: ImmutableList<CommandButton> = ImmutableList.of()

    fun setSmallIcon(iconResId: Int) {
        delegate.setSmallIcon(iconResId)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        cachedActionFactory = actionFactory
        cachedCallback = callback
        cachedCustomLayout = customLayout
        val mediaNotification = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            callback
        )
        return MediaNotification(
            mediaNotification.notificationId,
            decorate(mediaNotification.notification)
        )
    }

    fun buildNotification(mediaSession: MediaSession): MediaNotification? {
        val factory = cachedActionFactory ?: return null
        val callback = cachedCallback ?: return null
        return runCatching {
            val mediaNotification = delegate.createNotification(
                mediaSession,
                cachedCustomLayout,
                factory,
                callback
            )
            MediaNotification(mediaNotification.notificationId, decorate(mediaNotification.notification))
        }.getOrNull()
    }

    private fun decorate(notification: Notification): Notification {
        return runCatching {
            val builder = Notification.Builder.recoverBuilder(context, notification)
                .setLocalOnly(true)
            val line = currentLyricLine?.takeIf { it.isNotBlank() }
            line?.let {
                val songTitle = notification.extras?.getString(Notification.EXTRA_TITLE)
                val songArtist = notification.extras?.getString(Notification.EXTRA_TEXT)
                builder.setContentTitle(it)
                if (!songTitle.isNullOrBlank()) {
                    builder.setContentText(songTitle)
                    if (!songArtist.isNullOrBlank()) {
                        builder.setSubText(songArtist)
                    }
                }
            }
            builder.build()
        }.getOrElse {
            notification
        }
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()
}
