package com.ai.assistance.operit.core.music

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地曲库扫描：只做「读 MediaStore 音频库里标了 IS_MUSIC 的条目」，不扫描全盘、不写文件。
 *
 * 权限：Android 13+ 需要 READ_MEDIA_AUDIO，12 及以下 READ_EXTERNAL_STORAGE。
 * 权限没给时返回空列表，由 UI 负责引导用户授权，不在这里弹窗。
 */
object MusicLibrary {

    private const val TAG = "MusicLibrary"

    private val PROJECTION =
        arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            // 已废弃但仍在绝大多数机型上可读，只用来定位同目录的 .lrc；读不到就当 null
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
        )

    suspend fun loadTracks(context: Context): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<MusicTrack>()
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            try {
                context.contentResolver
                    .query(collection, PROJECTION, selection, null, sortOrder)
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        // DATA 列可能不存在（个别 ROM 移除），拿不到就 -1，后面当 null 处理
                        @Suppress("DEPRECATION")
                        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val duration = cursor.getLong(durationCol)
                            // 过滤掉时长异常（0 或极短）的条目，通常是提示音/损坏文件
                            if (duration < 5000L) continue
                            val uri = ContentUris.withAppendedId(collection, id).toString()
                            val path =
                                runCatching { cursor.getString(dataCol) }
                                    .getOrNull()
                                    ?.takeIf { it.isNotBlank() }
                            result.add(
                                MusicTrack(
                                    id = id,
                                    uri = uri,
                                    title =
                                        cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                                            ?: "未命名",
                                    artist = cursor.getString(artistCol),
                                    album = cursor.getString(albumCol),
                                    albumId = cursor.getLong(albumIdCol),
                                    durationMs = duration,
                                    path = path
                                )
                            )
                        }
                    }
            } catch (e: Exception) {
                AppLogger.e(TAG, "扫描本地曲库失败", e)
            }
            result
        }
}
