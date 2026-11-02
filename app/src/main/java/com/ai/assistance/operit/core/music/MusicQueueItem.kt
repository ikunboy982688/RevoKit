package com.ai.assistance.operit.core.music

/**
 * 播放队列里的一个条目。
 *
 * 最初由 AI 音乐工具（standard_music_playback）使用，现在也供侧栏音乐播放器复用。
 * 新增字段一律给默认值，保证旧调用点（AI 工具解析参数的地方）不需要改。
 */
data class MusicQueueItem(
    val source: String,
    val sourceType: String,
    val title: String? = null,
    val artist: String? = null,
    /** 封面图 uri，本地曲库来自 MediaStore albumart，可能为空 */
    val artworkUri: String? = null,
    /** 本地曲库的 MediaStore id；在线曲目为空。收藏用它做 key */
    val trackId: Long? = null,
    /** 音频文件的真实磁盘路径（不是 content:// ），用来找同目录的 .lrc */
    val lyricsPath: String? = null,
    /**
     * 在线音源：发起播放时用的那个音源 id。为空表示这是本地/直链曲目。
     *
     * 在线曲目的 [source] 存的是一个假 URI（`revokitonline://<token>/stream`）——
     * 真正的播放地址要等 ExoPlayer 真正要读数据时才去解析（地址有时效、也可能一次解析不出来），
     * 见 `MusicPlaybackManager` 里的 ResolvingDataSource。
     */
    val onlineSourceId: String? = null,
    /** 在线曲目的原始条目 JSON，取地址/取歌词时要原样回传给插件 */
    val onlineSongJson: String? = null,
    /** 抓列表时记下的平台名（kw/tx/wy…），解析失败时用来挑一个对得上的音源兜底 */
    val onlinePlatform: String? = null
) {
    /**
     * 这是不是一首「要走音源脚本换播放地址」的歌。
     *
     * 判定只看 [onlineSongJson]：只有在线点播那条链路会给它赋值，本地曲库永远是 null，
     * 所以「非空 ⟺ 在线」是精确的。别改成看 [onlineSourceId] —— 内置四平台搜索
     * （应用侧直搜）搜出来的歌本来就没有音源 id，那样会被误判成本地文件、点了播不出声。
     */
    val isOnline: Boolean get() = !onlineSongJson.isNullOrBlank()
}
