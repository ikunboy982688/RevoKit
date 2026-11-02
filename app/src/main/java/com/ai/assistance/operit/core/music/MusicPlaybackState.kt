package com.ai.assistance.operit.core.music

/** 播放器对外暴露的可观察状态，UI / 悬浮窗都订阅它 */
data class MusicPlaybackState(
    /** idle / playing / paused / preparing / ended / stopped / error */
    val state: String = "idle",
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val isPlaying: Boolean = false,
    val loop: Boolean = false,
    /** 0=顺序播放 1=单曲循环 2=列表循环 */
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    /** 0=顺序 1=列表循环 2=单曲循环 3=随机（UI 直接用这个判断） */
    val playMode: Int = 0,
    val volume: Float = 1f,
    val errorMessage: String? = null,
    /** 当前本地曲目的 MediaStore id；在线曲目 / 直接播 url 时为 null */
    val trackId: Long? = null,
    /** 当前在线曲目的平台（wy/tx/kw/kg）；本地曲目为 null */
    val onlinePlatform: String? = null,
    /** 当前在线曲目在该平台的歌曲 ID；本地曲目为 null */
    val onlineSongId: String? = null
) {
    val hasTrack: Boolean
        get() = queueIndex >= 0 || !title.isNullOrBlank()

    /**
     * 收藏 key，和 [MusicFavorites] 用的是同一套规则。
     *
     * 必须覆盖在线曲目 —— 之前只认 `trackId`，在线歌拿到的永远是 null，
     * 全屏页那颗心点了没反应、悬浮窗也是灰的。
     */
    val favoriteKey: String?
        get() =
            trackId?.toString()
                ?: onlinePlatform?.takeIf { it.isNotBlank() }?.let { platform ->
                    onlineSongId?.takeIf { it.isNotBlank() }?.let { songId ->
                        MusicFavorites.onlineKey(platform, songId)
                    }
                }

    /** 当前曲目是不是在线的（用来决定收藏时该写详情还是只记 key） */
    val isOnlineTrack: Boolean
        get() = trackId == null && !onlinePlatform.isNullOrBlank() && !onlineSongId.isNullOrBlank()

    val progress: Float
        get() =
            if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
}
