package com.ai.assistance.operit.core.music

/** 本地曲库中的一首歌（来自 MediaStore） */
data class MusicTrack(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    /** 真实磁盘路径，仅用于找同目录的 .lrc；拿不到时为 null */
    val path: String? = null
) {
    /** MediaStore 专辑封面地址，albumId 无效时返回 null（UI 会退化成音符占位图） */
    val artworkUri: String?
        get() = if (albumId > 0L) "content://media/external/audio/albumart/$albumId" else null

    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "未知艺术家"

    fun toQueueItem(): MusicQueueItem =
        MusicQueueItem(
            source = uri,
            sourceType = "uri",
            title = title,
            artist = artist,
            artworkUri = artworkUri,
            trackId = id,
            lyricsPath = path
        )
}
