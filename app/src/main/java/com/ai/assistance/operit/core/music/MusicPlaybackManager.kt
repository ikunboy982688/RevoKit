package com.ai.assistance.operit.core.music

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.core.music.online.OnlinePlayback
import com.ai.assistance.operit.core.music.online.OnlineSong
import com.ai.assistance.operit.core.music.online.PlatformLyric
import com.ai.assistance.operit.core.tools.MusicPlaybackResultData
import com.ai.assistance.operit.util.AppLogger
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSource
// 注意：本项目用的 ExoPlayer 是拆包版（exoplayer-common / -datasource / -extractor 分开），
// ResolvingDataSource 在 upstream 包，不在 source 包 —— 按 2.x 官方文档写 import 会解析不到。
import com.google.android.exoplayer2.upstream.ResolvingDataSource
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全应用唯一的音乐播放内核。
 *
 * 来源：原先被 `StandardMusicPlaybackTools` 以 private 形式私藏在同一个文件里，只有 AI 工具能用。
 * 侧栏音乐播放器要复用同一条音频通道（否则会出现两个 ExoPlayer 同时出声），所以把类抽到
 * `core/music` 包并对外公开，同时补上：
 *
 *  1. [state] —— StateFlow，UI / 悬浮窗订阅它渲染，不用轮询；
 *  2. [playTrackList] / [next] / [previous] / [togglePlayPause] —— 播放器页面与悬浮小窗需要的操作；
 *  3. 播放位置每隔 500ms 刷新一次，供进度条显示。
 *
 * 原 AI 工具用的 [play] / [playQueue] / [pause] / [resume] / [stop] / [seek] / [setVolume] / [status]
 * 行为与返回值保持原样，只是内部改成同时同步 [state]。
 */
class MusicPlaybackManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    private var player: ExoPlayer? = null
    private var queue: List<MusicQueueItem> = emptyList()
    private var currentVolume: Float = 1f
    private var currentLoop: Boolean = false
    private var lastError: String? = null

    private val _state = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = _state.asStateFlow()

    /** 当前曲目的歌词（本地 .lrc / 以后的在线歌词）。没有歌词时是空文档，不是 null */
    private val _lyrics = MutableStateFlow(LyricsDoc.EMPTY)
    val lyrics: StateFlow<LyricsDoc> = _lyrics.asStateFlow()

    private var lyricsJob: Job? = null

    /**
     * 在线曲目的占位登记表：`token -> 队列条目`。
     *
     * 在线曲目没法在入队时就把播放地址拿到（地址有时效、一次解析还可能失败），
     * 所以队列里先放一个假 URI `revokitonline://<token>/stream`，等 ExoPlayer 真的要
     * 读数据了，再由 [resolveOnlineSpec] 现场解析成真实地址。
     */
    private val onlineTokens = ConcurrentHashMap<String, MusicQueueItem>()
    private val onlineUrlCache = ConcurrentHashMap<String, Pair<String, Long>>()
    private val onlineTokenSeq = AtomicLong(0L)

    /** 当前播放队列（只读快照），UI 用来高亮「正在播放」那一行 */
    val currentQueue: List<MusicQueueItem>
        get() = queue

    /**
     * 当前正在播的在线歌曲（本地曲库返回 null）。
     *
     * 收藏在线歌必须用它 —— 收藏列表要把歌名列出来，光有一个 key 是不够的。
     */
    fun currentOnlineSong(): OnlineSong? {
        val index = _state.value.queueIndex
        val item = queue.getOrNull(index) ?: return null
        if (!item.isOnline) return null
        return OnlineSong(
            sourceId = item.onlineSourceId.orEmpty(),
            songId = PlatformLyric.songIdFor(item.onlinePlatform.orEmpty(), item.onlineSongJson)
                .ifBlank { "" },
            platform = item.onlinePlatform.orEmpty(),
            title = item.title.orEmpty(),
            artist = item.artist.orEmpty(),
            artworkUrl = item.artworkUri,
            rawJson = item.onlineSongJson.orEmpty()
        ).takeIf { it.platform.isNotBlank() && it.songId.isNotBlank() }
    }

    // ------------------------------------------------------------------ AI 工具原有能力

    suspend fun play(
        source: String,
        sourceType: String,
        title: String?,
        artist: String?,
        loop: Boolean?,
        volume: Float?,
        startPositionMs: Long?
    ): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            val itemUri = resolveUri(source, sourceType)
            val musicTitle = title?.takeIf { it.isNotBlank() }
            val musicArtist = artist?.takeIf { it.isNotBlank() }
            val musicLoop = loop ?: false
            val musicVolume = volume ?: 1f
            validateVolume(musicVolume)
            if (startPositionMs != null && startPositionMs < 0L) {
                throw IllegalArgumentException("start_position_ms must be zero or greater")
            }

            val mediaItem = buildMediaItem(itemUri, musicTitle, musicArtist)

            val activePlayer = ensurePlayer()
            activePlayer.stop()
            activePlayer.clearMediaItems()
            activePlayer.setMediaItem(mediaItem)
            activePlayer.repeatMode =
                if (musicLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            activePlayer.volume = musicVolume
            activePlayer.prepare()
            if (startPositionMs != null) {
                activePlayer.seekTo(startPositionMs)
            }
            activePlayer.play()

            val item =
                MusicQueueItem(
                    source = source,
                    sourceType = sourceType,
                    title = musicTitle,
                    artist = musicArtist
                )
            queue = listOf(item)
            currentLoop = musicLoop
            currentVolume = musicVolume
            lastError = null
            _state.value = buildState("playing")
            startTicker()
            buildResult("Playback started")
        }

    suspend fun playQueue(
        items: List<MusicQueueItem>,
        loop: Boolean?,
        volume: Float?,
        startIndex: Int?,
        startPositionMs: Long?
    ): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            if (items.isEmpty()) {
                throw IllegalArgumentException("items must contain at least one track")
            }
            val musicLoop = loop ?: false
            val musicVolume = volume ?: 1f
            val firstIndex = startIndex ?: 0
            validateVolume(musicVolume)
            if (firstIndex < 0 || firstIndex >= items.size) {
                throw IllegalArgumentException("start_index must be between 0 and ${items.lastIndex}")
            }
            if (startPositionMs != null && startPositionMs < 0L) {
                throw IllegalArgumentException("start_position_ms must be zero or greater")
            }

            startQueueInternal(items, firstIndex, musicLoop, musicVolume, startPositionMs)
            buildResult("Queue playback started")
        }

    suspend fun pause(): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            val activePlayer = requirePlayer()
            activePlayer.pause()
            _state.value = buildState("paused")
            buildResult("Playback paused")
        }

    suspend fun resume(): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            val activePlayer = requirePlayer()
            activePlayer.play()
            _state.value = buildState("playing")
            startTicker()
            buildResult("Playback resumed")
        }

    suspend fun stop(): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            val activePlayer = requirePlayer()
            activePlayer.stop()
            activePlayer.clearMediaItems()
            queue = emptyList()
            currentLoop = false
            _state.value = MusicPlaybackState(volume = currentVolume)
            lyricsJob?.cancel()
            _lyrics.value = LyricsDoc.EMPTY
            stopTicker()
            buildResult("Playback stopped")
        }

    suspend fun seek(positionMs: Long): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            if (positionMs < 0L) {
                throw IllegalArgumentException("position_ms must be zero or greater")
            }
            val activePlayer = requirePlayer()
            activePlayer.seekTo(positionMs)
            _state.value = buildState(_state.value.state)
            buildResult("Playback position updated")
        }

    suspend fun setVolume(volume: Float): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            validateVolume(volume)
            val activePlayer = requirePlayer()
            activePlayer.volume = volume
            currentVolume = volume
            _state.value = buildState(_state.value.state)
            buildResult("Playback volume updated")
        }

    suspend fun status(): MusicPlaybackResultData =
        withContext(Dispatchers.Main.immediate) {
            buildResult(lastError ?: "Playback status")
        }

    // ------------------------------------------------------------------ 播放器 UI 能力

    /** 本地曲库点某首歌：整库入队并从 [startIndex] 开始播 */
    suspend fun playTrackList(
        tracks: List<MusicQueueItem>,
        startIndex: Int = 0,
        loop: Boolean = false
    ) {
        if (tracks.isEmpty()) return
        withContext(Dispatchers.Main.immediate) {
            val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
            startQueueInternal(tracks, safeIndex, loop, currentVolume, null)
        }
    }

    fun next() {
        scope.launch {
            val activePlayer = player ?: return@launch
            if (activePlayer.hasNextMediaItem()) {
                activePlayer.seekToNextMediaItem()
            } else if (activePlayer.mediaItemCount > 0) {
                // 到队尾了：回到第一首
                activePlayer.seekTo(0, 0L)
            }
        }
    }

    /**
     * 「下一首播放」：插到当前曲目后面，不动正在放的那首。
     *
     * 必须同时改 ExoPlayer 的 mediaItem 列表和本地 [queue]，两边下标要一一对应 ——
     * 只改一边会让 UI 高亮的行和实际播的歌错位（这个坑在别处踩过）。
     */
    fun playNext(item: MusicQueueItem) {
        scope.launch {
            val activePlayer = player ?: return@launch
            val insertAt =
                (activePlayer.currentMediaItemIndex + 1)
                    .coerceIn(0, activePlayer.mediaItemCount)
                    .coerceAtMost(queue.size)
            runCatching {
                activePlayer.addMediaItem(insertAt, buildMediaItemFor(item))
            }
                .onFailure { return@launch }
            queue = queue.toMutableList().apply { add(insertAt.coerceAtMost(size), item) }
            _state.value = buildState(_state.value.state)
        }
    }

    fun previous() {        scope.launch {
            val activePlayer = player ?: return@launch
            // 播放超过 3 秒时，「上一首」先回到本首开头，符合常见播放器习惯
            if (activePlayer.currentPosition > 3000L) {
                activePlayer.seekTo(0L)
            } else if (activePlayer.hasPreviousMediaItem()) {
                activePlayer.seekToPreviousMediaItem()
            } else if (activePlayer.mediaItemCount > 0) {
                activePlayer.seekTo(activePlayer.mediaItemCount - 1, 0L)
            }
        }
    }

    fun togglePlayPause() {
        scope.launch {
            val activePlayer = player ?: return@launch
            if (activePlayer.isPlaying) {
                activePlayer.pause()
            } else {
                if (activePlayer.playbackState == Player.STATE_IDLE) {
                    activePlayer.prepare()
                }
                activePlayer.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            val activePlayer = player ?: return@launch
            activePlayer.seekTo(positionMs.coerceAtLeast(0L))
            _state.value = buildState(_state.value.state)
        }
    }

    fun setLoopMode(loop: Boolean) {
        currentLoop = loop
        scope.launch {
            player?.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            _state.update { it.copy(loop = loop) }
        }
    }

    /** 顺序 → 列表循环 → 单曲循环 → 随机，循环切换 */
    fun cyclePlayMode() {
        scope.launch {
            val activePlayer = player ?: return@launch
            val next = (_state.value.playMode + 1) % 4
            when (next) {
                0 -> {
                    activePlayer.repeatMode = Player.REPEAT_MODE_OFF
                    activePlayer.shuffleModeEnabled = false
                }
                1 -> {
                    activePlayer.repeatMode = Player.REPEAT_MODE_ALL
                    activePlayer.shuffleModeEnabled = false
                }
                2 -> {
                    activePlayer.repeatMode = Player.REPEAT_MODE_ONE
                    activePlayer.shuffleModeEnabled = false
                }
                else -> {
                    activePlayer.repeatMode = Player.REPEAT_MODE_ALL
                    activePlayer.shuffleModeEnabled = true
                }
            }
            currentLoop = activePlayer.repeatMode != Player.REPEAT_MODE_OFF
            _state.update {
                it.copy(
                    repeatMode = activePlayer.repeatMode,
                    shuffle = activePlayer.shuffleModeEnabled,
                    loop = currentLoop
                )
            }
        }
    }

    /** 队列里点某一首直接跳过去 */
    fun jumpToQueueIndex(index: Int) {
        scope.launch {
            val activePlayer = player ?: return@launch
            if (index < 0 || index >= activePlayer.mediaItemCount) return@launch
            activePlayer.seekTo(index, 0L)
            if (!activePlayer.isPlaying) activePlayer.play()
        }
    }

    fun release() {
        stopTicker()
        player?.release()
        player = null
        queue = emptyList()
        lyricsJob?.cancel()
        _lyrics.value = LyricsDoc.EMPTY
        _state.value = MusicPlaybackState()
    }

    // ------------------------------------------------------------------ 内部实现

    private fun startQueueInternal(
        items: List<MusicQueueItem>,
        startIndex: Int,
        loop: Boolean,
        volume: Float,
        startPositionMs: Long?
    ) {
        // 换队列就把上一批在线曲目登记清掉，否则 token 会一直累积
        onlineTokens.clear()
        onlineUrlCache.clear()

        val mediaItems = items.map { item -> buildMediaItemFor(item) }

        val activePlayer = ensurePlayer()
        activePlayer.stop()
        activePlayer.clearMediaItems()
        activePlayer.setMediaItems(mediaItems, startIndex, startPositionMs ?: C.TIME_UNSET)
        activePlayer.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        activePlayer.volume = volume
        activePlayer.prepare()
        activePlayer.play()

        queue = items
        currentLoop = loop
        currentVolume = volume
        lastError = null
        _state.value = buildState("playing")
        refreshLyrics(startIndex)
        startTicker()
    }

    /**
     * 切歌时换歌词。先在 IO 线程读文件，读完再塞回 flow。
     * 找不到 .lrc 就置空文档 —— 悬浮歌词条会自己显示「暂无歌词」，不弹任何错误。
     */
    private fun refreshLyrics(index: Int) {
        lyricsJob?.cancel()
        val item = queue.getOrNull(index)
        if (item == null) {
            _lyrics.value = LyricsDoc.EMPTY
            return
        }
        // 上一首的歌词立刻清掉，避免新歌前 200ms 还显示旧歌词
        _lyrics.value = LyricsDoc.EMPTY
        if (item.isOnline) {
            // 在线歌词可能来自两条路：
            //   1. **应用侧直连**（网易/QQ 的公开歌词接口，快且稳）—— 绝大多数在这儿就出结果了
            //   2. 音源脚本 —— 但本地这批洛雪源大多压根没实现 lyric（lx15 声明 actions 只有
            //      musicUrl）、lx3/lx4/lx13 连这个字都没有，所以只能当兜底
            lyricsJob =
                scope.launch {
                    val doc =
                        withContext(Dispatchers.IO) {
                            val platform = item.onlinePlatform.orEmpty()
                            val songId = PlatformLyric.songIdFor(platform, item.onlineSongJson)
                            val raw =
                                if (platform.isNotBlank() && songId.isNotBlank()) {
                                    PlatformLyric.load(platform, songId)
                                } else {
                                    null
                                } ?: OnlinePlayback.loadLyric(appContext, item)
                            if (raw.isNullOrBlank()) {
                                LyricsDoc.EMPTY
                            } else {
                                LrcParser.parse(raw, "online")
                            }
                        }
                    _lyrics.value = doc
                }
            return
        }
        lyricsJob =
            scope.launch {
                val doc =
                    withContext(Dispatchers.IO) {
                        LrcParser.loadForAudioFile(item.lyricsPath)
                    }
                _lyrics.value = doc ?: LyricsDoc.EMPTY
            }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch {
                while (isActive) {
                    val activePlayer = player
                    if (activePlayer == null) break
                    _state.value = buildState(_state.value.state)
                    delay(500)
                }
            }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun ensurePlayer(): ExoPlayer {
        val existing = player
        if (existing != null) return existing

        val created =
            ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        // 在线曲目：读数据那一刻才去解析真实地址；本地曲目原样透传，
                        // 走 DefaultDataSource 的老路径，行为与之前完全一致
                        ResolvingDataSource.Factory(
                            DefaultDataSource.Factory(appContext),
                            this@MusicPlaybackManager::resolveOnlineSpec
                        )
                    )
                )
                .build()
                .apply {
                val audioAttributes =
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()
                setAudioAttributes(audioAttributes, true)
                // 锁屏/后台播放时尽量不让 CPU 睡死，避免放歌中途断
                runCatching { setWakeMode(C.WAKE_MODE_LOCAL) }
                addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            val mapped =
                                when (state) {
                                    Player.STATE_BUFFERING -> "preparing"
                                    Player.STATE_READY ->
                                        if (this@apply.isPlaying) "playing" else "paused"
                                    Player.STATE_ENDED -> "ended"
                                    else -> _state.value.state
                                }
                            _state.value = buildState(mapped)
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            val mapped =
                                when {
                                    isPlaying -> "playing"
                                    _state.value.state == "stopped" -> "stopped"
                                    _state.value.state == "ended" -> "ended"
                                    else -> "paused"
                                }
                            _state.value = buildState(mapped)
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            _state.value = buildState(_state.value.state)
                            refreshLyrics(this@apply.currentMediaItemIndex)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            lastError = error.message
                            _state.value =
                                _state.value.copy(state = "error", errorMessage = error.message)
                        }
                    }
                )
            }
        player = created
        return created
    }

    private fun requirePlayer(): ExoPlayer {
        return player ?: throw IllegalStateException("No active music playback session")
    }

    private fun resolveUri(source: String, sourceType: String): Uri {
        return when (sourceType) {
            "path" -> {
                val file = File(source)
                if (!file.exists() || !file.isFile) {
                    throw IllegalArgumentException("Music file does not exist: $source")
                }
                Uri.fromFile(file)
            }
            "url" -> {
                val uri = Uri.parse(source)
                val scheme = uri.scheme
                if (scheme != "http" && scheme != "https") {
                    throw IllegalArgumentException("url source_type requires http or https source")
                }
                uri
            }
            "uri" -> {
                val uri = Uri.parse(source)
                if (uri.scheme.isNullOrBlank()) {
                    throw IllegalArgumentException("uri source_type requires a URI scheme")
                }
                uri
            }
            else -> throw IllegalArgumentException("source_type must be path, url, or uri")
        }
    }

    private fun buildMediaItem(
        uri: Uri,
        title: String?,
        artist: String?,
        artworkUri: String? = null
    ): MediaItem {
        val metadataBuilder =
            MediaMetadata.Builder().setTitle(title).setArtist(artist)
        artworkUri?.let { runCatching { Uri.parse(it) }.getOrNull() }?.let { artUri ->
            metadataBuilder.setArtworkUri(artUri)
        }
        return MediaItem.Builder().setUri(uri).setMediaMetadata(metadataBuilder.build()).build()
    }

    /** 队列条目 → MediaItem。在线曲目先登记 token，把地址留给 [resolveOnlineSpec] 现场解析 */
    private fun buildMediaItemFor(item: MusicQueueItem): MediaItem {
        if (!item.isOnline) {
            return buildMediaItem(
                uri = resolveUri(item.source, item.sourceType),
                title = item.title,
                artist = item.artist,
                artworkUri = item.artworkUri
            )
        }
        val token = "o" + onlineTokenSeq.incrementAndGet()
        onlineTokens[token] = item
        return buildMediaItem(
            uri = Uri.parse("revokitonline://$token/stream"),
            title = item.title,
            artist = item.artist,
            artworkUri = item.artworkUri
        )
    }

    /**
     * 数据源解析器。ExoPlayer 在**加载线程**上调用它，这里可以放心阻塞联网 ——
     * 但绝不能碰主线程的东西，所以只读 [onlineTokens] 这个并发表。
     *
     * 同一个 token 解析出来的地址会缓存一小会儿：ExoPlayer 一首歌往往会重开好几次
     * 数据源（缓冲、seek），每次重新解析既慢又白烧接口配额。
     */
    private fun resolveOnlineSpec(dataSpec: DataSpec): DataSpec {
        // 这个解析器是挂在**所有**曲目上的（本地文件也会经过它），所以第一件事必须是
        // 认协议：不是 revokitonline:// 就原样返回，一个字都不许动。
        // 漏了这一步的后果实测过：本地 file:// 的 host 是 null，取出的 token 是空串，
        // 直接抛「在线曲目已失效」—— 整个本地播放全崩（日志里是 Source error）。
        if (!"revokitonline".equals(dataSpec.uri.scheme, ignoreCase = true)) {
            return dataSpec
        }
        val token = dataSpec.uri.host.orEmpty()
        val item =
            onlineTokens[token] ?: throw IOException("在线曲目已失效，请重新点播一首")
        val cached = onlineUrlCache[token]
        if (cached != null && System.currentTimeMillis() - cached.second < ONLINE_URL_TTL_MS) {
            return dataSpec.withUri(Uri.parse(cached.first))
        }
        val resolved =
            OnlinePlayback.resolveUrl(appContext, item)
                ?: throw IOException("所有启用的在线音源都没能解析出播放地址")
        onlineUrlCache[token] = resolved.url to System.currentTimeMillis()
        return dataSpec.withUri(Uri.parse(resolved.url))
    }

    private fun validateVolume(volume: Float) {
        if (volume < 0f || volume > 1f) {
            throw IllegalArgumentException("volume must be between 0 and 1")
        }
    }

    private fun buildState(playbackState: String): MusicPlaybackState {
        val activePlayer = player
        val duration = activePlayer?.duration?.takeIf { it != C.TIME_UNSET && it >= 0L } ?: 0L
        val index = activePlayer?.currentMediaItemIndex ?: -1
        val current = queue.getOrNull(index)
        val repeat = activePlayer?.repeatMode ?: Player.REPEAT_MODE_OFF
        val shuffle = activePlayer?.shuffleModeEnabled == true
        val playMode =
            when {
                shuffle -> 3
                repeat == Player.REPEAT_MODE_ONE -> 2
                repeat == Player.REPEAT_MODE_ALL -> 1
                else -> 0
            }
        return MusicPlaybackState(
            state = playbackState,
            title = current?.title,
            artist = current?.artist,
            artworkUri = current?.artworkUri,
            durationMs = duration,
            positionMs = activePlayer?.currentPosition ?: 0L,
            queueIndex = index,
            queueSize = queue.size,
            isPlaying = activePlayer?.isPlaying == true,
            loop = currentLoop,
            repeatMode = repeat,
            shuffle = shuffle,
            playMode = playMode,
            volume = currentVolume,
            errorMessage = lastError,
            trackId = current?.trackId,
            // 在线曲目把平台 + 歌曲 ID 带出去，收藏状态才能算出来（否则在线歌永远点不亮小心心）
            onlinePlatform = current?.onlinePlatform?.takeIf { it.isNotBlank() },
            onlineSongId =
                current
                    ?.takeIf { it.isOnline }
                    ?.let { PlatformLyric.songIdFor(it.onlinePlatform.orEmpty(), it.onlineSongJson) }
                    ?.takeIf { it.isNotBlank() }
        )
    }

    private fun buildResult(message: String): MusicPlaybackResultData {
        val activePlayer = player
        val duration = activePlayer?.duration?.takeIf { it != C.TIME_UNSET && it >= 0L }
        val currentIndex = activePlayer?.currentMediaItemIndex ?: 0
        val currentMetadata = queue.getOrNull(currentIndex)
        val queueSize = queue.size.takeIf { it > 1 }
        return MusicPlaybackResultData(
            state = _state.value.state,
            source = currentMetadata?.source,
            sourceType = currentMetadata?.sourceType,
            title = currentMetadata?.title,
            artist = currentMetadata?.artist,
            durationMs = duration,
            positionMs = activePlayer?.currentPosition ?: 0L,
            bufferedPositionMs = activePlayer?.bufferedPosition ?: 0L,
            volume = currentVolume,
            loop = currentLoop,
            queueIndex = queueSize?.let { currentIndex },
            queueSize = queueSize,
            message = message
        )
    }

    companion object {
        private const val TAG = "MusicPlaybackManager"

        /** 解析出来的在线地址缓存多久。这类直链普遍有时效（十几分钟到几小时），保守取 5 分钟 */
        private const val ONLINE_URL_TTL_MS = 5 * 60 * 1000L

        @Volatile private var INSTANCE: MusicPlaybackManager? = null

        fun getInstance(context: Context): MusicPlaybackManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE ?: MusicPlaybackManager(context).also { INSTANCE = it }
                }
        }

        fun peekInstance(): MusicPlaybackManager? = INSTANCE
    }
}
