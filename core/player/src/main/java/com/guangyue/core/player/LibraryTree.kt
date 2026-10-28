package com.guangyue.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.google.common.collect.ImmutableList

/**
 * Minimal browsable tree exposed through [MediaLibrarySession]. M1 keeps it static (the
 * library/playlist content is served by :core:data inside the app); M2 can feed real rows in
 * here so external controllers (Android Auto, Wear, system media resumption) can browse.
 */
@UnstableApi
object LibraryTree {

    const val ROOT_ID = "root"
    const val ID_PLAYLISTS = "playlists"
    const val ID_LIBRARY = "library"
    const val ID_LOCAL = "local"

    private const val MIME_ROOT = "vnd.android.cursor.dir/guangyue.root"
    private const val MIME_GROUP = "vnd.android.cursor.dir/guangyue.group"
    private const val MIME_PLAYLIST = "vnd.android.cursor.dir/guangyue.playlist"

    fun rootItem(): MediaItem = group(ROOT_ID, "广乐", "音乐库")

    fun children(parentId: String): ImmutableList<MediaItem> = when (parentId) {
        ROOT_ID, "" -> ImmutableList.of(
            group(ID_PLAYLISTS, "歌单", "我的歌单"),
            group(ID_LIBRARY, "收藏", "喜欢的音乐"),
            group(ID_LOCAL, "本地", "本地文件"),
        )
        else -> ImmutableList.of()
    }

    fun item(mediaId: String): MediaItem? = when (mediaId) {
        ID_PLAYLISTS -> group(ID_PLAYLISTS, "歌单", "我的歌单")
        ID_LIBRARY -> group(ID_LIBRARY, "收藏", "喜欢的音乐")
        ID_LOCAL -> group(ID_LOCAL, "本地", "本地文件")
        else -> null
    }

    private fun group(id: String, title: String, subtitle: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()
}
