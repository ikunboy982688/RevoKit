package com.ai.assistance.operit.ui.features.music

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.core.music.MusicFavorites
import com.ai.assistance.operit.core.music.MusicLibrary
import com.ai.assistance.operit.core.music.MusicPlaybackManager
import com.ai.assistance.operit.core.music.MusicTrack
import androidx.compose.foundation.lazy.itemsIndexed
import com.ai.assistance.operit.core.music.online.OnlineSong
import com.ai.assistance.operit.services.music.MusicFloatService
import kotlinx.coroutines.launch

/** 曲库的四种看法 + 收藏，共五个入口 */
private const val MODE_SONGS = 0
private const val MODE_ALBUMS = 1
private const val MODE_ARTISTS = 2
private const val MODE_FOLDERS = 3
private const val MODE_FAVORITES = 4

private val MODE_LABELS =
    listOf(
        MODE_SONGS to "歌曲",
        MODE_ALBUMS to "专辑",
        MODE_ARTISTS to "歌手",
        MODE_FOLDERS to "文件夹",
        MODE_FAVORITES to "收藏"
    )

/**
 * 侧栏「音乐」页。
 *
 * 这一版把「正在播放」大卡片去掉了，改成底部常驻迷你条 + 点开全屏播放页：
 *  - 列表能多露 3 行，首屏信息密度明显更高；
 *  - 播放控制、进度、歌词只在真正要用的时候才占满屏；
 *  - 悬浮球开关压成一行，不再占着首屏最贵的位置。
 *
 * 曲库按 歌曲/专辑/歌手/文件夹 四种口径看同一份 MediaStore 数据，收藏单独一档。
 * 全部本地计算，不联网、不上传。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { MusicPlaybackManager.getInstance(context) }
    val playback by manager.state.collectAsState()
    val lyrics by manager.lyrics.collectAsState()

    // 收藏统一走 MusicFavorites，悬浮窗和这个页面看的是同一份数据
    MusicFavorites.init(context)
    val favorites by MusicFavorites.keys.collectAsState()
    // 在线收藏的详情。在线歌不在本地曲库里，光有 key 列不出歌名，必须从详情表取
    val onlineFavorites by MusicFavorites.entries.collectAsState()
    val onlineFavoriteSongs = remember(onlineFavorites) { MusicFavorites.onlineSongs() }

    var tracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(hasAudioPermission(context)) }
    var floatEnabled by remember { mutableStateOf(MusicFloatService.isRunning) }
    var reloadFlag by remember { mutableStateOf(0) }

    var groupMode by remember { mutableStateOf(MODE_SONGS) }
    // 专辑/歌手/文件夹 点进去后的下钻 key；null = 还在分组列表
    var drillKey by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showQueue by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showOnline by remember { mutableStateOf(false) }

    // 点搜索图标后自动聚焦并弹键盘，少一次点击；收起时清掉焦点把键盘收走
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            runCatching { searchFocus.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
            if (granted) reloadFlag++
        }

    LaunchedEffect(reloadFlag, hasPermission) {
        if (!hasPermission) {
            loading = false
            tracks = emptyList()
            return@LaunchedEffect
        }
        loading = true
        tracks = MusicLibrary.loadTracks(context)
        loading = false
    }

    fun playTrack(track: MusicTrack, source: List<MusicTrack>) {
        val queue = source.map { it.toQueueItem() }
        scope.launch { manager.playTrackList(queue, source.indexOf(track)) }
        if (floatEnabled) MusicFloatService.show(context)
    }

    // ---------------- 派生数据 ----------------
    val favoriteTracks =
        remember(tracks, favorites) { tracks.filter { favorites.contains(it.id.toString()) } }
    val baseList = if (groupMode == MODE_FAVORITES) favoriteTracks else tracks
    val visible = remember(baseList, query) { filterTracks(baseList, query) }
    val buckets = remember(tracks, query, groupMode) { buildBucketsFor(tracks, groupMode, query) }
    val drilledSongs =
        remember(tracks, query, groupMode, drillKey) {
            val key = drillKey ?: return@remember emptyList()
            filterTracks(tracks.filter { belongsTo(it, groupMode, key) }, query)
        }

    // 在线音乐是一个完整子页：进去就整页接管，返回时再回到本地曲库。
    // 早返回比塞进 when 分支清爽 —— 在线页和本地曲库除了「播歌」之外没有共用状态。
    if (showOnline) {
        OnlineMusicScreen(onBack = { showOnline = false })
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---------------- 顶部一行：悬浮球开关 + 搜索 + 刷新 ----------------
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier =
                    Modifier.size(30.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = "悬浮球",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = floatEnabled,
                onCheckedChange = { checked ->
                    if (!checked) {
                        floatEnabled = false
                        MusicFloatService.hide(context)
                    } else if (Settings.canDrawOverlays(context)) {
                        floatEnabled = true
                        MusicFloatService.show(context)
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                }
            )
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "搜索",
                    tint =
                        if (searchOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { reloadFlag++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新曲库")
            }
            // 在线音乐入口。放在最右边单独一个文字按钮，前面图标区已经够挤了，
            // 再加图标会让人分不清哪个是搜索哪个是在线
            TextButton(
                onClick = { showOnline = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("在线", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (!Settings.canDrawOverlays(context) && floatEnabled.not()) {
            Text(
                text = "需要「显示在其他应用上层」权限才能显示悬浮球",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // ---------------- 搜索框（默认收起，不占地方） ----------------
        if (searchOpen) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜索歌曲、歌手、专辑") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .focusRequester(searchFocus)
            )
        }

        // ---------------- 五个分类 ----------------
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MODE_LABELS) { (mode, label) ->
                FilterChip(
                    selected = groupMode == mode,
                    onClick = {
                        groupMode = mode
                        drillKey = null
                    },
                    label = { Text(label) }
                )
            }
        }

        // ---------------- 列表区 ----------------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                !hasPermission -> EmptyHint(
                    title = "需要「音乐和音频」权限才能读取本地曲库",
                    actionText = "去授权",
                    onAction = { permissionLauncher.launch(audioPermission()) }
                )
                tracks.isEmpty() -> EmptyHint(
                    title = "没找到本地音乐",
                    hint = "把音频文件放进手机存储后点右上角刷新；也可以检查是否已授予「音乐和音频」权限",
                    actionText = "刷新",
                    onAction = { reloadFlag++ }
                )
                groupMode == MODE_FAVORITES &&
                    favoriteTracks.isEmpty() &&
                    onlineFavoriteSongs.isEmpty() -> EmptyHint(
                        title = "还没有收藏的歌",
                        hint = "本地歌点右边的小心心就能收藏；在线歌在「在线」页搜索结果里点小心心",
                        actionText = "去曲库",
                        onAction = { groupMode = MODE_SONGS }
                    )
                groupMode == MODE_FAVORITES -> {
                    FavoriteList(
                        localTracks = favoriteTracks,
                        onlineSongs = onlineFavoriteSongs,
                        favorites = favorites,
                        currentTrackId = playback.trackId,
                        currentTitle = playback.title,
                        queueIndex = playback.queueIndex,
                        isPlayingNow = playback.isPlaying,
                        onPlayLocal = { playTrack(it, favoriteTracks) },
                        onPlayOnline = { song, list ->
                            val queue = list.map { it.toQueueItem() }
                            scope.launch { manager.playTrackList(queue, list.indexOf(song)) }
                            if (floatEnabled) MusicFloatService.show(context)
                        },
                        onToggleLocal = { MusicFavorites.toggle(it.id.toString()) },
                        onToggleOnline = { MusicFavorites.toggleOnline(it) }
                    )
                }
                drillKey != null -> {
                    val key = drillKey!!
                    Column(Modifier.fillMaxSize()) {
                        DrillHeader(
                            title = drillTitle(key, groupMode),
                            subtitle = "${drilledSongs.size} 首",
                            onBack = { drillKey = null }
                        )
                        SongList(
                            songs = drilledSongs,
                            currentTrackId = playback.trackId,
                            isPlaying = playback.isPlaying,
                            favorites = favorites,
                            onPlay = { playTrack(it, drilledSongs) },
                            onPlayNext = { manager.playNext(it.toQueueItem()) },
                            onToggleFavorite = { MusicFavorites.toggle(it.id.toString()) },
                            onOpenAlbum = null,
                            onOpenArtist = null
                        )
                    }
                }
                groupMode == MODE_ALBUMS || groupMode == MODE_ARTISTS || groupMode == MODE_FOLDERS -> {
                    GroupList(
                        buckets = buckets,
                        mode = groupMode,
                        onOpen = { drillKey = it.key },
                        onShowSongs = { groupMode = MODE_SONGS }
                    )
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        if (visible.isNotEmpty()) {
                            Text(
                                text = "共 ${visible.size} 首",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 18.dp, bottom = 2.dp)
                            )
                        }
                        if (visible.isEmpty()) {
                            EmptyHint(
                                title = "没有匹配的歌曲",
                                hint = "换个关键词试试",
                                actionText = "清空搜索",
                                onAction = { query = "" }
                            )
                        } else {
                            SongList(
                                songs = visible,
                                currentTrackId = playback.trackId,
                                isPlaying = playback.isPlaying,
                                favorites = favorites,
                                onPlay = { playTrack(it, visible) },
                                onPlayNext = { manager.playNext(it.toQueueItem()) },
                                onToggleFavorite = { MusicFavorites.toggle(it.id.toString()) },
                                onOpenAlbum = { track ->
                                    track.album?.let {
                                        groupMode = MODE_ALBUMS
                                        drillKey = it
                                    }
                                },
                                onOpenArtist = { track ->
                                    groupMode = MODE_ARTISTS
                                    drillKey = track.displayArtist
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 底部迷你播放条 ----------------
        // 搜索时藏起来：这个 Activity 的 windowSoftInputMode 是 adjustPan（全局改不得），
        // 键盘弹出来不会把窗口顶上去，迷你条会被键盘整个盖住，露出半截反而更难看。
        if (playback.hasTrack && !searchOpen) {
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

    // 全屏播放页打开时把桌面歌词条临时藏起来：那条是独立悬浮窗，永远盖在 App 上面，
    // 正好会挡在全屏页中间的封面区域。退出全屏页再放回来，不动用户存的开关偏好。
    DisposableEffect(showPlayer) {
        MusicFloatService.suppressLyricsBar(context, showPlayer)
        onDispose {
            if (showPlayer) MusicFloatService.suppressLyricsBar(context, false)
        }
    }

    // ---------------- 全屏播放页 ----------------
    if (showPlayer && playback.hasTrack) {
        MusicFullPlayer(
            manager = manager,
            state = playback,
            lyrics = lyrics,
            isFavorite = MusicFavorites.isFavorite(playback.favoriteKey),
            onToggleFavorite = {
                // 在线曲目必须走 toggleOnline（会连歌名一起存），
                // 否则收藏列表里会出现一条没有名字的歌
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

    // ---------------- 播放队列 ----------------
    if (showQueue) {
        QueueSheet(
            manager = manager,
            currentIndex = playback.queueIndex,
            onDismiss = { showQueue = false }
        )
    }
}

// ====================================================================== 子组件

@Composable
private fun DrillHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 收藏列表：本地歌和在线歌混在一个列表里。
 *
 * 之所以要自己写而不是复用 [SongList] —— 收藏里有**在线歌**，而在线歌不是
 * [MusicTrack]（本地曲库的数据结构）。分成两段渲染：先本地、后在线，
 * 中间各带一个小标题，让人一眼分得清哪些是手机里的、哪些要联网解析。
 */
@Composable
private fun FavoriteList(
    localTracks: List<MusicTrack>,
    onlineSongs: List<OnlineSong>,
    favorites: Set<String>,
    currentTrackId: Long?,
    currentTitle: String?,
    queueIndex: Int,
    isPlayingNow: Boolean,
    onPlayLocal: (MusicTrack) -> Unit,
    onPlayOnline: (OnlineSong, List<OnlineSong>) -> Unit,
    onToggleLocal: (MusicTrack) -> Unit,
    onToggleOnline: (OnlineSong) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (localTracks.isNotEmpty()) {
            item { SectionHeader("本地歌曲 · ${localTracks.size}") }
            items(localTracks) { track ->
                TrackRow(
                    track = track,
                    isCurrent = currentTrackId == track.id,
                    isPlaying = isPlayingNow,
                    isFavorite = favorites.contains(track.id.toString()),
                    onClick = { onPlayLocal(track) },
                    onPlayNext = {},
                    onToggleFavorite = { onToggleLocal(track) },
                    onOpenAlbum = null,
                    onOpenArtist = null
                )
            }
        }
        if (onlineSongs.isNotEmpty()) {
            item { SectionHeader("在线收藏 · ${onlineSongs.size}") }
            itemsIndexed(onlineSongs) { index, song ->
                val playingHere = queueIndex == index && currentTitle == song.title
                OnlineSongRow(
                    song = song,
                    playing = playingHere,
                    onPlay = { onPlayOnline(song, onlineSongs) },
                    // 已经在收藏列表里，心心是亮的，点一下就是取消收藏
                    isFavorite = true,
                    onToggleFavorite = { onToggleOnline(song) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 2.dp)
    )
}

/** 歌曲列表。抽出来是因为「曲库/收藏/下钻到专辑」三处用的是同一个列表 */
@Composable
private fun SongList(
    songs: List<MusicTrack>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    favorites: Set<String>,
    onPlay: (MusicTrack) -> Unit,
    onPlayNext: (MusicTrack) -> Unit,
    onToggleFavorite: (MusicTrack) -> Unit,
    onOpenAlbum: ((MusicTrack) -> Unit)?,
    onOpenArtist: ((MusicTrack) -> Unit)?
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(songs, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying,
                isFavorite = favorites.contains(track.id.toString()),
                onClick = { onPlay(track) },
                onPlayNext = { onPlayNext(track) },
                onToggleFavorite = { onToggleFavorite(track) },
                onOpenAlbum = onOpenAlbum?.let { { it(track) } },
                onOpenArtist = onOpenArtist?.let { { it(track) } }
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun TrackRow(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAlbum: (() -> Unit)?,
    onOpenArtist: (() -> Unit)?
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            // 音符垫底，封面加载失败就露出它（多数本地曲目其实没有内嵌封面）
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
            if (track.artworkUri != null) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                        track.displayArtist,
                        track.album?.takeIf { it.isNotBlank() }
                    )
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        if (isCurrent && isPlaying) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = "正在播放",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                modifier = Modifier.size(17.dp),
                tint =
                    if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("下一首播放") },
                    onClick = {
                        menuOpen = false
                        onPlayNext()
                    }
                )
                if (onOpenAlbum != null) {
                    DropdownMenuItem(
                        text = { Text("查看专辑") },
                        onClick = {
                            menuOpen = false
                            onOpenAlbum()
                        }
                    )
                }
                if (onOpenArtist != null) {
                    DropdownMenuItem(
                        text = { Text("查看歌手") },
                        onClick = {
                            menuOpen = false
                            onOpenArtist()
                        }
                    )
                }
            }
        }
    }
}

/** 专辑 / 歌手 / 文件夹 的分组列表 */
@Composable
private fun GroupList(
    buckets: List<GroupBucket>,
    mode: Int,
    onOpen: (GroupBucket) -> Unit,
    onShowSongs: () -> Unit
) {
    if (buckets.isEmpty()) {
        EmptyHint(
            title = if (mode == MODE_FOLDERS) "没有可用的文件夹信息" else "这里是空的",
            hint = if (mode == MODE_FOLDERS) "部分机型读不到音频的真实路径，这个分类会空着" else null,
            actionText = "看全部歌曲",
            onAction = onShowSongs
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(buckets, key = { it.key }) { bucket ->
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { onOpen(bucket) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier =
                        Modifier.size(46.dp)
                            .clip(if (mode == MODE_ARTISTS) CircleShape else RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    // 占位图标先画，封面加载失败时露出来
                    Icon(
                        imageVector =
                            when (mode) {
                                MODE_ARTISTS -> Icons.Default.Person
                                MODE_FOLDERS -> Icons.Default.Folder
                                else -> Icons.Default.Album
                            },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                    if (bucket.artworkUri != null) {
                        AsyncImage(
                            model = bucket.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier.fillMaxSize()
                                    .clip(
                                        if (mode == MODE_ARTISTS) CircleShape
                                        else RoundedCornerShape(10.dp)
                                    )
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = bucket.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bucket.subtitle != null) {
                        Text(
                            text = bucket.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // 数量固定放右边：塞在副标题里会被长路径挤掉，末尾只剩一个省略号
                Text(
                    text = "${bucket.count} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    manager: MusicPlaybackManager,
    currentIndex: Int,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = "接下来播放",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            val queue = manager.currentQueue
            LazyColumn(Modifier.height(360.dp)) {
                items(queue.size) { index ->
                    val item = queue[index]
                    val isCurrent = index == currentIndex
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    manager.jumpToQueueIndex(index)
                                    onDismiss()
                                }
                                .background(
                                    if (isCurrent) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title ?: "未命名",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color =
                                    if (isCurrent) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            item.artist?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = displayArtist(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (isCurrent) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmptyHint(
    title: String,
    hint: String? = null,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAction) { Text(actionText) }
    }
}

// ====================================================================== 分组逻辑

private data class GroupBucket(
    val key: String,
    val title: String,
    val subtitle: String?,
    val artworkUri: String?,
    val count: Int
)

/** 专辑/歌手/文件夹共用一个分组器，key 用能唯一定位一组的字符串 */
private fun buildBucketsFor(
    tracks: List<MusicTrack>,
    mode: Int,
    query: String
): List<GroupBucket> {
    val filtered = filterTracks(tracks, query)
    val grouped: Map<String, List<MusicTrack>> =
        when (mode) {
            MODE_ALBUMS -> filtered.groupBy { it.album?.takeIf { a -> a.isNotBlank() } ?: "未知专辑" }
            MODE_ARTISTS -> filtered.groupBy { it.displayArtist }
            MODE_FOLDERS -> filtered.groupBy { dirOf(it) }
            else -> emptyMap()
        }
    return grouped
        .map { (key, list) ->
            val title =
                when (mode) {
                    MODE_ALBUMS -> key
                    MODE_ARTISTS -> key
                    else -> folderLabel(key)
                }
            val subtitle =
                when (mode) {
                    MODE_ALBUMS ->
                        list.firstNotNullOfOrNull { it.artist?.takeIf { a -> a.isNotBlank() } }
                            ?.let { displayArtist(it) }
                    MODE_ARTISTS ->
                        list.firstNotNullOfOrNull { it.album?.takeIf { a -> a.isNotBlank() } }
                    else -> key.takeIf { it.isNotBlank() }
                }
            GroupBucket(
                key = key,
                title = title,
                subtitle = subtitle,
                artworkUri = list.firstNotNullOfOrNull { it.artworkUri },
                count = list.size
            )
        }
        .sortedBy { it.title }
}

private fun dirOf(track: MusicTrack): String =
    track.path?.substringBeforeLast('/', "")?.takeIf { it.isNotBlank() } ?: ""

private fun belongsTo(track: MusicTrack, mode: Int, key: String): Boolean =
    when (mode) {
        MODE_ALBUMS -> (track.album?.takeIf { it.isNotBlank() } ?: "未知专辑") == key
        MODE_ARTISTS -> track.displayArtist == key
        MODE_FOLDERS -> dirOf(track) == key
        else -> true
    }

private fun drillTitle(key: String, mode: Int): String =
    when (mode) {
        MODE_FOLDERS -> folderLabel(key)
        else -> key
    }

private fun filterTracks(tracks: List<MusicTrack>, query: String): List<MusicTrack> {
    val q = query.trim()
    if (q.isEmpty()) return tracks
    return tracks.filter { track ->
        track.title.contains(q, ignoreCase = true) ||
            (track.artist?.contains(q, ignoreCase = true) == true) ||
            (track.album?.contains(q, ignoreCase = true) == true)
    }
}
