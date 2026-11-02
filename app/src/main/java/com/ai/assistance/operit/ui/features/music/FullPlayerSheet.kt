package com.ai.assistance.operit.ui.features.music

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ai.assistance.operit.core.music.LyricsDoc
import com.ai.assistance.operit.core.music.MusicPlaybackManager
import com.ai.assistance.operit.core.music.MusicPlaybackState

/**
 * 全屏播放页（从底部升起的整屏面板）。
 *
 * 背景用封面主色做竖向渐变 —— 取色逻辑在 [ArtworkPalette]，取不到就退化成主题色，
 * 不会因为封面缺失变成一片黑。
 *
 * 中间区域是「封面」和「歌词」二选一，点封面切歌词、点歌词切回封面（和主流播放器一致），
 * 底部单独留了一个按钮也能切，避免用户不知道可以点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicFullPlayer(
    manager: MusicPlaybackManager,
    state: MusicPlaybackState,
    lyrics: LyricsDoc,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 背景由内容自己画渐变，容器必须透明，否则圆角处会露出灰色底
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = null
    ) {
        FullPlayerBody(
            manager = manager,
            state = state,
            lyrics = lyrics,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onOpenQueue = onOpenQueue,
            onCollapse = onDismiss
        )
    }
}

@Composable
private fun FullPlayerBody(
    manager: MusicPlaybackManager,
    state: MusicPlaybackState,
    lyrics: LyricsDoc,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onCollapse: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    // RevoKit：原来用 background（可能是半透明/被主题包改过），展开播放器要保证不透明且跟随主题深浅，
    // 所以固定取 surfaceContainerHighest —— 这一档 Theme.kt 里刻意保持不透明，
    // 浅色主题是浅灰、深色主题是深灰，容器上的小字和进度条都不会被背景图干扰。
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val dominant = rememberArtworkColor(state.artworkUri)
    val accent = dominant ?: MaterialTheme.colorScheme.primary
    // 封面色只做很轻的一层氛围。原来浅色 0.40 / 深色 0.62，深色封面上会把整块压成黑色，
    // 小字直接看不清，这里压到 0.16 / 0.26。
    val topColor = accent.copy(alpha = if (dark) 0.26f else 0.16f)

    var showLyrics by remember { mutableStateOf(false) }
    // 进度条拖动中的比例，用来让上面的时间文字跟着手指走
    var draggingFraction by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.96f)
                .background(background)
                .background(
                    Brush.verticalGradient(
                        0f to topColor,
                        0.45f to topColor.copy(alpha = 0f),
                        1f to Color.Transparent
                    )
                )
    ) {
        // ---------------- 顶部：收起 / 标题 / 队列 ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.queueSize > 1) {
                    Text(
                        text = "${state.queueIndex + 1} / ${state.queueSize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Default.PlaylistPlay, contentDescription = "播放队列")
            }
        }

        // ---------------- 中间：封面 / 歌词 ----------------
        AnimatedContent(
            targetState = showLyrics,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "cover-lyrics"
        ) { lyricsMode ->
            if (lyricsMode) {
                LyricsPage(
                    manager = manager,
                    lyrics = lyrics,
                    positionMs = state.positionMs,
                    onTapCover = { showLyrics = false }
                )
            } else {
                CoverPage(
                    artworkUri = state.artworkUri,
                    accent = accent,
                    onClick = { showLyrics = true }
                )
            }
        }

        // ---------------- 歌名 / 歌手 / 收藏 ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.title ?: "未在播放",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = displayArtist(state.artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector =
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint =
                        if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------------- 进度 ----------------
        // 用自制的 MusicSeekBar 而不是 M3 的 Slider：
        // Slider 放在 ModalBottomSheet 里拖不动（面板的纵向拖拽会吃掉手势），实测无效
        val shownPosition =
            draggingFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(shownPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MusicSeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            activeColor = accent,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            onSeek = { manager.seekTo(it) },
            onDraggingChange = { draggingFraction = it }
        )

        Spacer(Modifier.height(6.dp))

        // ---------------- 控制区 ----------------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { manager.cyclePlayMode() }) {
                val icon =
                    when (state.playMode) {
                        1 -> Icons.Default.Repeat
                        2 -> Icons.Default.RepeatOne
                        3 -> Icons.Default.Shuffle
                        else -> Icons.Default.Repeat
                    }
                Icon(
                    imageVector = icon,
                    contentDescription = playModeLabel(state.playMode),
                    tint =
                        if (state.playMode == 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                )
            }
            IconButton(onClick = { manager.previous() }, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(34.dp)
                )
            }
            Box(
                modifier =
                    Modifier.size(66.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { manager.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
            IconButton(onClick = { manager.next() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = { showLyrics = !showLyrics }) {
                Icon(
                    Icons.Default.FormatAlignCenter,
                    contentDescription = if (showLyrics) "看封面" else "看歌词",
                    tint =
                        if (showLyrics) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CoverPage(artworkUri: String?, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth(0.76f)
                    .aspectRatio(1f)
                    .shadow(18.dp, RoundedCornerShape(26.dp), clip = false)
                    .clip(RoundedCornerShape(26.dp))
                    .background(accent.copy(alpha = 0.25f))
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // 音符垫底：没有封面 / 封面加载失败时露出它，而不是一片空白
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "点封面看歌词",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun LyricsPage(
    manager: MusicPlaybackManager,
    lyrics: LyricsDoc,
    positionMs: Long,
    onTapCover: () -> Unit
) {
    if (lyrics.isEmpty) {
        Column(
            modifier = Modifier.fillMaxSize().clickable(onClick = onTapCover),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "把同名 .lrc 放进歌曲同一目录即可显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val currentIndex = lyrics.indexAt(positionMs)
    // 只在「换行」时滚一次，进度每 500ms 刷新不会触发，省电也不抖
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            runCatching { listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0)) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 90.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val selected = index == currentIndex
            Text(
                text = line.text,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (selected) 18.sp else 15.sp,
                        lineHeight = if (selected) 26.sp else 22.sp
                    ),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                    },
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { manager.seekTo(line.timeMs) }
                        .padding(horizontal = 20.dp, vertical = 9.dp)
            )
        }
    }
}
