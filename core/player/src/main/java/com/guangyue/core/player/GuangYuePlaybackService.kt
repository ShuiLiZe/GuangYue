package com.guangyue.core.player

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Playback foreground service.
 *
 * Media3 [MediaLibraryService] gives us for free:
 *  - the media notification + foreground/keep-alive (playback survives the UI being killed)
 *  - a browsable [MediaLibrarySession] (`onGetLibraryRoot` / `onGetChildren` / `onGetItem`)
 *
 * Playback logic itself lives in [PlaybackController]; this class only wires the session.
 */
@AndroidEntryPoint
class GuangYuePlaybackService : MediaLibraryService() {

    @Inject lateinit var playback: PlaybackController

    private var mediaLibrarySession: MediaLibrarySession? = null

    private val callback = object : MediaLibrarySession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(LibraryTree.rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(LibraryTree.children(parentId), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = LibraryTree.item(mediaId)
                ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaLibrarySession = MediaLibrarySession.Builder(this, playback.player, callback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 只是把服务拉起来（让 MediaLibrarySession 存在），播放由 PlaybackController 驱动。
        // START_NOT_STICKY：进程被杀后不靠旧广播复活；真正在播时 media3 自己会转前台服务。
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = playback.player
        if (!player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_CHANNEL = "guangyue.playback"

        /**
         * 点播放时把本服务拉起来。
         *
         * MediaSessionService 只有被 start/bind 之后才有会话：不拉的话虽然在放歌，
         * 系统里却没有 media session，没有通知栏/锁屏控制，
         * dumpsys media_session 里也查不到 state=PLAYING，系统媒体键也按不动。
         */
        fun start(context: android.content.Context) {
            val intent = Intent(context, GuangYuePlaybackService::class.java)
            // 用 startService：media3 在会话连上后自己走 startForeground；
            // 用 startForegroundService 会触发 did not then call startForeground 的崩溃。
            runCatching { context.startService(intent) }
                .onFailure {
                    android.util.Log.w("GYHOME", "start playback service failed: " + it.message)
                }
        }
    }
}
