package com.ai.assistance.operit.ui.features.music

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.music.MusicFavorites
import com.ai.assistance.operit.core.music.MusicPlaybackManager
import com.ai.assistance.operit.core.music.MusicQueueItem
import com.ai.assistance.operit.core.music.online.MusicSourceEngine
import com.ai.assistance.operit.core.music.online.MusicSourceStore
import com.ai.assistance.operit.core.music.online.OnlineSong
import com.ai.assistance.operit.core.music.online.OnlineSource
import com.ai.assistance.operit.core.music.online.PlatformLyric
import com.ai.assistance.operit.core.music.online.PlatformPlaylist
import com.ai.assistance.operit.core.music.online.PlatformSearch
import com.ai.assistance.operit.core.music.online.SourceDialect
import com.ai.assistance.operit.core.music.online.toOnlineSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 在线音乐页。
 *
 * 分成两件事：
 *  1. 搜歌 —— **默认走应用侧内置搜索**（网易云/QQ/酷我/酷狗四个平台直连公开接口），
 *     一个插件都不用导入就能搜。洛雪音源协议里压根没有 search 接口，它只能
 *     「给一首歌的信息，还你一个播放地址」，所以分工是「应用搜歌 → 脚本换直链」。
 *     导入过 MusicFree 插件的话，插件也会作为一个额外通道出现在通道条里。
 *  2. 管源 —— 导入 .js、启停、测试、删除。导入完立刻探一次，能跑就标出它的能力，
 *     跑不了当场把错误原文显示出来，不让人瞎猜。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMusicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { MusicPlaybackManager.getInstance(context) }
    val playback by manager.state.collectAsState()

    var sources by remember { mutableStateOf(MusicSourceStore.list(context)) }
    var showSources by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<OnlineSong>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf(MusicSourceStore.quality(context)) }
    var showQuality by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showPlaylistImport by remember { mutableStateOf(false) }
    var playlistLink by remember { mutableStateOf("") }
    var importedPlaylistTitle by remember { mutableStateOf<String?>(null) }
    /**
     * 当前选中的搜索通道，见 [SearchChannel]。
     *
     * 这里必须显式写 `mutableStateOf<SearchChannel>(...)`：不写的话 Kotlin 会按初始值把
     * 整个 state 推成 `SearchChannel.All` 这个具体类型，后面赋 `Builtin` / `Plugin` 就
     * 报 "Assignment type mismatch"。
     */
    var channel by remember { mutableStateOf<SearchChannel>(SearchChannel.All) }

    val lyrics by manager.lyrics.collectAsState()
    // 收藏状态是全应用共用的，这里初始化一次，全屏页上的小心心才能显示对
    MusicFavorites.init(context)
    val favorites by MusicFavorites.keys.collectAsState()

    // 能搜索的插件源（只有 MusicFree 方言有 search 接口）
    val searchablePlugins = remember(sources) {
        sources.filter { it.enabled && it.dialect == SourceDialect.MUSICFREE }
    }
    // 至少有一个启用的音源，才可能把歌换成播放地址。搜索本身不依赖它。
    val hasPlayableSource = remember(sources) { sources.any { it.enabled } }

    // 通道列表随插件增减变化：内置四平台永远在，插件按导入情况追加
    val channels = remember(searchablePlugins) {
        buildList<SearchChannel> {
            add(SearchChannel.All)
            PlatformSearch.PLATFORMS.forEach { add(SearchChannel.Builtin(it)) }
            searchablePlugins.forEach { add(SearchChannel.Plugin(it.id, it.name)) }
        }
    }

    fun refreshSources() {
        sources = MusicSourceStore.list(context)
    }

    // 在线曲目的播放地址是「播放器真要读数据时」才去解析的，解析失败不会回到这里，
    // 只会出现在播放状态里。把那条错误捞出来显示，不然用户只看到「点了没反应」。
    LaunchedEffect(playback.errorMessage) {
        val error = playback.errorMessage
        if (!error.isNullOrBlank() && results.isNotEmpty()) {
            message = "播放失败：$error"
        }
    }

    // 点播到真正出声之间有一段「换地址」的时间（应用直连一般一两秒，
    // 退回音源时可能十几秒）。这段时间什么都不显示，用户看到的就是「点了没反应」——
    // 所以显式告诉他在忙什么；一旦开始播了/报错了就把这行撤掉。
    LaunchedEffect(playback.state) {
        if (playback.state == "preparing" && results.isNotEmpty()) {
            message = "正在换播放地址…"
        } else if (message == "正在换播放地址…") {
            message = null
        }
    }

    fun doSearch() {
        val text = query.trim()
        if (text.isEmpty()) return
        searching = true
        message = null
        importedPlaylistTitle = null
        val active = channel
        scope.launch {
            val outcome: Result<List<OnlineSong>> =
                withContext(Dispatchers.IO) {
                    runCatching {
                        when (active) {
                            is SearchChannel.All ->
                                PlatformSearch.search(text).map { it.toOnlineSong() }
                            is SearchChannel.Builtin ->
                                PlatformSearch.search(text, listOf(active.platform))
                                    .map { it.toOnlineSong() }
                            is SearchChannel.Plugin -> {
                                val source = sources.firstOrNull { it.id == active.sourceId }
                                    ?: error("这个音源已经不在了，重新选一个")
                                MusicSourceEngine.getInstance(context)
                                    .search(source, text, 1)
                                    .getOrThrow()
                            }
                        }
                    }
                }
            searching = false
            outcome
                .onSuccess { list ->
                    results = list
                    if (list.isEmpty()) {
                        message = "没搜到结果，换个关键词或者换个通道试试"
                    } else if (!hasPlayableSource) {
                        // 搜到了但换不了地址，先把原因说清楚，别让人以为点了没反应
                        message = "搜到 ${list.size} 首。但要播放得先导入一个音源（右上角齿轮）——搜索不用插件，换播放地址必须用。"
                    }
                }
                .onFailure { error ->
                    results = emptyList()
                    message = "搜索失败：${error.message}"
                }
        }
    }

    /** 导入外部歌单：拿到曲目后直接铺进结果列表，点哪首就播哪首 */
    fun importPlaylist() {
        val link = playlistLink.trim()
        if (link.isEmpty()) return
        searching = true
        message = null
        scope.launch {
            val outcome =
                withContext(Dispatchers.IO) { PlatformPlaylist.load(link) }
            searching = false
            outcome
                .onSuccess { info ->
                    results = info.songs.map { it.toOnlineSong() }
                    importedPlaylistTitle = info.title
                    channel = SearchChannel.All
                    if (info.songs.isEmpty()) message = "这个歌单里没有歌"
                }
                .onFailure { error ->
                    results = emptyList()
                    importedPlaylistTitle = null
                    message = "导入失败：${error.message}"
                }
        }
    }

    fun play(index: Int) {
        if (results.isEmpty()) return
        // 应用侧直连已经能自己取网易/酷我的播放地址，所以「没有音源」不再一刀切拦住。
        // 只有「这个平台拿不到直链、又没有音源兜底」时才真的没戏。
        val target = results.getOrNull(index) ?: return
        if (!hasPlayableSource && target.platform.lowercase() !in setOf("wy", "kw", "tx")) {
            message = "这个平台要靠音源换播放地址，点右上角齿轮导入一个 .js 音源。"
            return
        }
        val queue = results.map { it.toQueueItem() }
        scope.launch { manager.playTrackList(queue, index) }
    }

    Column(Modifier.fillMaxSize()) {
        // ---------------- 顶栏 ----------------
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "在线音乐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            // 导入外部歌单。放在音质前面：它出现频率不高但入口要显眼，
            // 藏进音源管理面板里没人找得到
            TextButton(onClick = { showPlaylistImport = true }) {
                Text("歌单", fontSize = 13.sp)
            }
            TextButton(onClick = { showQuality = true }) {
                Text(quality, fontSize = 12.sp)
                DropdownMenu(
                    expanded = showQuality,
                    onDismissRequest = { showQuality = false }
                ) {
                    listOf("128k", "320k", "flac", "flac24bit").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                quality = option
                                MusicSourceStore.setQuality(context, option)
                                showQuality = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { showSources = true }) {
                Icon(Icons.Default.Settings, contentDescription = "音源管理")
            }
        }

        // ---------------- 搜索框 ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜歌名、歌手") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { doSearch() }, enabled = !searching) {
                Text(if (searching) "搜索中" else "搜索")
            }
        }

        // ---------------- 通道选择 ----------------
        // 内置四平台永远可用（不用导入任何东西），插件通道只在真导入过 MusicFree 时才出现
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(channels) { item ->
                ChannelChip(
                    label = item.label,
                    selected = item.key == channel.key,
                    onClick = {
                        channel = item
                        message = null
                    }
                )
            }
        }

        // ---------------- 歌单标题条 ----------------
        importedPlaylistTitle?.let { title ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌单「$title」· ${results.size} 首",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { importedPlaylistTitle = null }) { Text("清除", fontSize = 12.sp) }
            }
        }

        // ---------------- 提示 / 进度 ----------------
        if (searching) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "正在搜索「$query」…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // ---------------- 结果 ----------------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                results.isEmpty() && !searching ->
                    CenterHint(
                        title = "搜歌试试",
                        subtitle =
                            if (hasPlayableSource) {
                                "内置网易云/QQ/酷我/酷狗直搜，不用导入任何插件。"
                            } else {
                                "搜索可以直接用；但要真的播出声音，还得导入一个音源（右上角齿轮）。"
                            },
                        actionLabel = if (hasPlayableSource) null else "去导入音源",
                        onAction = { showSources = true }
                    )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        itemsIndexed(results) { index, song ->
                            val playingHere =
                                playback.queueIndex == index &&
                                    playback.title == song.title
                            // 在线歌的收藏 key 是「平台:歌曲ID」，和播放器里那颗心用的是同一套
                            val favKey =
                                MusicFavorites.onlineKey(
                                    song.platform,
                                    PlatformLyric.songIdFor(song.platform, song.rawJson)
                                        .ifBlank { song.songId }
                                )
                            OnlineSongRow(
                                song = song,
                                playing = playingHere,
                                onPlay = { play(index) },
                                isFavorite = favorites.contains(favKey),
                                onToggleFavorite = { MusicFavorites.toggleOnline(song) }
                            )
                        }
                    }
            }
        }

        // 在线点播同样要能看见「在放什么」，所以底部也挂一条迷你条
        if (playback.hasTrack) {
            MiniPlayerBar(
                title = playback.title ?: "未命名",
                artist = playback.artist,
                artworkUri = playback.artworkUri,
                isPlaying = playback.isPlaying,
                progress = playback.progress,
                onOpen = { showPlayer = true },
                onTogglePlay = { manager.togglePlayPause() },
                onNext = { manager.next() }
            )
        }
    }

    // ---------------- 导入外部歌单 ----------------
    if (showPlaylistImport) {
        AlertDialog(
            onDismissRequest = { showPlaylistImport = false },
            title = { Text("导入歌单") },
            text = {
                Column {
                    Text(
                        text = "把网易云 / QQ音乐歌单的完整链接粘进来就行，也可以直接写歌单号（纯数字按网易处理）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = playlistLink,
                        onValueChange = { playlistLink = it },
                        singleLine = true,
                        placeholder = { Text("https://music.163.com/#/playlist?id=…", fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (searching) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "正在导入…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlaylistImport = false
                        importPlaylist()
                    },
                    enabled = !searching
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistImport = false }) { Text("取消") }
            }
        )
    }

    // ---------------- 音源管理 ----------------
    if (showSources) {
        ModalBottomSheet(
            onDismissRequest = { showSources = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            SourceManagerSheet(
                sources = sources,
                onChanged = { refreshSources() },
                onClose = { showSources = false }
            )
        }
    }

    // 全屏播放页 / 播放队列：在线点播也要能用，所以在这一页里同样挂一份
    if (showPlayer && playback.hasTrack) {
        MusicFullPlayer(
            manager = manager,
            state = playback,
            lyrics = lyrics,
            isFavorite = MusicFavorites.isFavorite(playback.favoriteKey),
            onToggleFavorite = {
                val online = manager.currentOnlineSong()
                if (online != null) {
                    MusicFavorites.toggleOnline(online)
                } else {
                    playback.favoriteKey?.let { MusicFavorites.toggle(it) }
                }
            },
            onOpenQueue = { showQueue = true },
            onDismiss = { showPlayer = false }
        )
    }
    if (showQueue) {
        QueueSheet(
            manager = manager,
            currentIndex = playback.queueIndex,
            onDismiss = { showQueue = false }
        )
    }
}

/**
 * 搜索通道。
 *
 * 之所以要分「内置」和「插件」两种：洛雪（LX Music）自定义音源协议里**没有 search**
 * 这个动作，它只能「给歌的信息、还播放地址」。所以「音源脚本不能搜歌」不是脚本残缺，
 * 是上游协议就这么定的 —— 分工必须是「应用侧搜歌 → 拿 (平台, 歌曲ID) → 脚本换直链」。
 *
 * 因此内置四平台搜索**不依赖任何插件**，导入插件只是为了能把歌换成可播放的地址。
 */
private sealed class SearchChannel {
    abstract val key: String
    abstract val label: String

    /** 四个平台一起搜 */
    data object All : SearchChannel() {
        override val key: String = "all"
        override val label: String = "全部"
    }

    /** 只搜某一个平台，platform 取 wy/tx/kw/kg */
    data class Builtin(val platform: String) : SearchChannel() {
        override val key: String = "p:$platform"
        override val label: String = PlatformSearch.label(platform)
    }

    /** 交给已导入的 MusicFree 插件搜（插件自带平台后端时才有意义） */
    data class Plugin(val sourceId: String, val sourceName: String) : SearchChannel() {
        override val key: String = "s:$sourceId"
        override val label: String = sourceName.ifBlank { "插件" }
    }
}

/** 通道芯片。M3 的 FilterChip 在低版本上样式偏大，这里自己画一个更贴紧凑布局 */
@Composable
private fun ChannelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val fg =
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(9.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 在线歌曲 → 播放队列条目。地址先空着，等 ExoPlayer 真要读了再解析 */
internal fun OnlineSong.toQueueItem(): MusicQueueItem =
    MusicQueueItem(
        source = "",
        sourceType = "online",
        title = title,
        artist = artist.takeIf { it.isNotBlank() },
        artworkUri = artworkUrl,
        onlineSourceId = sourceId,
        onlineSongJson = rawJson,
        onlinePlatform = platform
    )

/**
 * 一条在线歌曲。`internal` 是因为**收藏列表**也用它 —— 收藏列表里现在混着本地歌和在线歌，
 * 两种行不该长得不一样。
 */
@Composable
internal fun OnlineSongRow(
    song: OnlineSong,
    playing: Boolean,
    onPlay: () -> Unit,
    /** 传了就显示收藏心心；收藏列表里传 true、点一下就是取消收藏 */
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                    .background(
                        if (playing) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint =
                    if (playing) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (playing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle =
                listOfNotNull(
                    song.artist.takeIf { it.isNotBlank() && it != "<unknown>" },
                    song.album?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (song.durationMs > 0L) {
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
        }
        // 「全部」通道会把四个平台的结果混在一起，标一下来源才知道该挑哪个
        val platformLabel = PlatformSearch.label(song.platform)
        if (platformLabel.isNotBlank()) {
            Box(
                modifier =
                    Modifier.clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = platformLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onToggleFavorite != null) {
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector =
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint =
                        if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CenterHint(
    title: String,
    subtitle: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ====================================================================== 音源管理

@Composable
private fun SourceManagerSheet(
    sources: List<OnlineSource>,
    onChanged: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<OnlineSource?>(null) }

    // 系统文件选择器：音源脚本没有统一的 MIME，只能放开 */*
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            busy = "正在导入…"
            scope.launch {
                val outcome = withContext(Dispatchers.IO) { MusicSourceStore.import(context, uri) }
                outcome
                    .onSuccess { record ->
                        onChanged()
                        // 导进来立刻探一次：能跑就说明装好了，跑不了当场把原因摊开
                        busy = "正在测试「${record.name}」…"
                        val probe =
                            withContext(Dispatchers.IO) {
                                MusicSourceEngine.getInstance(context).probe(record)
                            }
                        busy = null
                        probe
                            .onSuccess { info ->
                                val abilities =
                                    buildList {
                                            if (info.actions.contains("search")) add("搜索")
                                            if (info.actions.contains("musicUrl")) add("播放")
                                            if (info.actions.contains("lyric")) add("歌词")
                                        }
                                        .joinToString("/")
                                        .ifBlank { "仅解析" }
                                Toast.makeText(
                                        context,
                                        "导入成功：${info.name}（$abilities）",
                                        Toast.LENGTH_LONG
                                    )
                                    .show()
                            }
                            .onFailure { error ->
                                Toast.makeText(
                                        context,
                                        "脚本入住了，但加载失败：${error.message}",
                                        Toast.LENGTH_LONG
                                    )
                                    .show()
                            }
                    }
                    .onFailure { error ->
                        busy = null
                        Toast.makeText(
                                context,
                                "导入失败：${error.message}",
                                Toast.LENGTH_LONG
                            )
                            .show()
                    }
            }
        }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "音源管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("导入 .js") }
        }
        Text(
            text = "搜歌不用插件。这里的音源只负责把歌换成可播放的地址：MusicFree 插件、洛雪音源都行。" +
                "脚本存在应用私有目录，不会上传。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        busy?.let { text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (sources.isEmpty()) {
            Text(
                text = "还没有音源。点「导入 .js」选一个脚本文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(if (sources.size > 4) 320.dp else 220.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(sources) { source ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = source.name.ifBlank { "未命名音源" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append(source.dialect.label)
                                if (source.platform.isNotBlank()) append(" · ${source.platform}")
                                append(
                                    when {
                                        source.canSearch -> " · 可搜索"
                                        else -> " · 仅解析"
                                    }
                                )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = { checked ->
                            MusicSourceStore.setEnabled(context, source.id, checked)
                            onChanged()
                        }
                    )
                    IconButton(onClick = { pendingDelete = source }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除音源？") },
            text = { Text("「${target.name.ifBlank { "未命名音源" }}」会被永久删除，需要重新导入。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        MusicSourceStore.delete(context, target.id)
                        MusicSourceEngine.getInstance(context).release()
                        pendingDelete = null
                        onChanged()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}
