package com.ai.assistance.operit.services.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.music.LyricsDoc
import com.ai.assistance.operit.core.music.MusicFavorites
import com.ai.assistance.operit.core.music.MusicPlaybackManager
import com.ai.assistance.operit.core.music.MusicPlaybackState
import com.ai.assistance.operit.services.ServiceLifecycleOwner
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.ui.theme.rememberActiveThemePreferenceSnapshot
import com.ai.assistance.operit.ui.theme.revokitColorScheme
import com.ai.assistance.operit.ui.theme.revokitIsDarkTheme
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 音乐悬浮窗 + 前台保活服务。
 *
 * 两个窗口，互相独立：
 *  1. **主悬浮球**：平时是一颗圆形小球（图标就是应用图标），带一圈播放进度环；
 *     点一下在原地展开成播放器面板，面板里再点箭头收回小球。拖哪儿都能拖。
 *  2. **桌面歌词条**（可选，默认关）：独立的一条滚动歌词，跟着唱，可以拖到任意位置。
 *
 * 播放本身仍然由 [MusicPlaybackManager] 负责，本服务不持有第二个播放器。
 * 服务本身是 `mediaPlayback` 类型的前台服务，所以收起小球、退到桌面、划掉任务卡都继续放歌。
 */
class MusicFloatService : Service() {

    companion object {
        private const val TAG = "MusicFloatService"
        private const val NOTIFICATION_ID = 2101
        private const val CHANNEL_ID = "revokit_music_channel"

        private const val PREFS = "revokit_music"
        private const val KEY_LYRICS_BAR = "lyrics_bar_enabled"

        private const val BALL_SIZE_DP = 58
        private const val PANEL_WIDTH_DP = 302
        private const val LYRICS_MARGIN_DP = 14

        const val ACTION_TOGGLE_PLAY_PAUSE = "com.ggongfang.app.music.TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.ggongfang.app.music.NEXT"
        const val ACTION_PREVIOUS = "com.ggongfang.app.music.PREVIOUS"
        const val ACTION_STOP = "com.ggongfang.app.music.STOP"
        const val ACTION_SET_LYRICS_BAR = "com.ggongfang.app.music.SET_LYRICS_BAR"
        const val ACTION_SUPPRESS_LYRICS_BAR = "com.ggongfang.app.music.SUPPRESS_LYRICS_BAR"

        private const val EXTRA_LYRICS_BAR_ENABLED = "enabled"
        private const val EXTRA_SUPPRESS = "suppress"

        /** 悬浮窗是否被用户主动开启（跨进程重启后 UI 用来恢复开关状态） */
        @Volatile var isRunning: Boolean = false
            private set

        fun show(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, MusicFloatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, MusicFloatService::class.java))
        }

        fun isLyricsBarEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_LYRICS_BAR, false)

        /**
         * 音乐页上的「桌面歌词」开关走这里：先落盘，服务在跑就顺手通知它挂上/卸下歌词条。
         * 服务没跑时只落盘，下次开悬浮球会自动恢复。
         */
        fun setLyricsBarEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LYRICS_BAR, enabled)
                .apply()
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, MusicFloatService::class.java)
                        .setAction(ACTION_SET_LYRICS_BAR)
                        .putExtra(EXTRA_LYRICS_BAR_ENABLED, enabled)
                )
            }
        }

        /**
         * 临时把歌词条藏起来 / 放回去，**不写盘**。
         *
         * 场景：App 里打开全屏播放页时，歌词条是另一个悬浮窗、永远盖在 App 之上，
         * 会正好挡在全屏页中间的封面区域。这时候藏起来，退出全屏页再放回来。
         * 和 [setLyricsBarEnabled] 的区别是这里不动用户存的开关偏好。
         */
        fun suppressLyricsBar(context: Context, suppress: Boolean) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, MusicFloatService::class.java)
                        .setAction(ACTION_SUPPRESS_LYRICS_BAR)
                        .putExtra(EXTRA_SUPPRESS, suppress)
                )
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateCollectJob: Job? = null

    private lateinit var lifecycleOwner: ServiceLifecycleOwner

    private var composeView: ComposeView? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var lyricsView: ComposeView? = null
    private var lyricsParams: WindowManager.LayoutParams? = null

    /** 悬浮球当前是展开成面板还是收起成球 */
    private val expanded = mutableStateOf(false)

    /** 桌面歌词条开关 */
    private val lyricsBarOn = mutableStateOf(false)

    /** 全屏播放页打开期间临时藏起歌词条；只影响这一次，不写盘 */
    private var suppressed = false

    /** 小球上次的位置，重新挂载时沿用，避免用户拖好的球跳回默认位置 */
    private var lastOverlayX: Int? = null
    private var lastOverlayY: Int? = null

    /** 应用图标，给小球当脸用。懒加载，避免服务启动时白等 */
    private val appIconBitmap: ImageBitmap by lazy {
        packageManager.getApplicationIcon(packageName).toBitmap(192, 192).asImageBitmap()
    }

    private val manager: MusicPlaybackManager
        get() = MusicPlaybackManager.getInstance(applicationContext)

    private val windowManager: WindowManager
        get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        MusicFavorites.init(this)
        createChannel()
        startForegroundSafely(buildNotification())
        lifecycleOwner =
            ServiceLifecycleOwner().apply {
                handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
                handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
                handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
            }
        attachOverlay()
        // 上次开着桌面歌词的话，这次直接恢复
        if (isLyricsBarEnabled(this)) {
            lyricsBarOn.value = true
            attachLyricsBar()
        }
        observePlayback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> manager.togglePlayPause()
            ACTION_NEXT -> manager.next()
            ACTION_PREVIOUS -> manager.previous()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_LYRICS_BAR -> {
                val enabled = intent.getBooleanExtra(EXTRA_LYRICS_BAR_ENABLED, false)
                setLyricsBar(enabled)
            }
            // 「暂时藏起来」：App 内打开全屏播放页时用，不改用户存的开关偏好
            ACTION_SUPPRESS_LYRICS_BAR -> {
                val suppress = intent.getBooleanExtra(EXTRA_SUPPRESS, false)
                suppressed = suppress
                if (suppress) {
                    // 小球和歌词条都收起来，否则它们会盖在全屏播放页上面
                    detachOverlay()
                    detachLyricsBar()
                } else {
                    attachOverlay()
                    if (lyricsBarOn.value) attachLyricsBar()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        stateCollectJob?.cancel()
        detachLyricsBar()
        detachOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 通知

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.music_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableVibration(false)
            }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): android.app.Notification {
        val state = manager.state.value
        val title = state.title?.takeIf { it.isNotBlank() } ?: getString(R.string.music_title)
        val subtitle =
            state.artist?.takeIf { it.isNotBlank() } ?: getString(R.string.music_now_playing_none)

        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val playPauseIcon =
            if (state.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        val playPauseTitle = getString(if (state.isPlaying) R.string.music_pause else R.string.music_play)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentIntent)
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_previous,
                getString(R.string.music_prev),
                actionIntent(ACTION_PREVIOUS)
            )
            .addAction(playPauseIcon, playPauseTitle, actionIntent(ACTION_TOGGLE_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, getString(R.string.music_next), actionIntent(ACTION_NEXT))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.music_close),
                actionIntent(ACTION_STOP)
            )
            .build()
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicFloatService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "startForeground 失败", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (inner: Exception) {
                AppLogger.e(TAG, "startForeground 退化也失败", inner)
            }
        }
    }

    private fun refreshNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification()) }
    }

    private fun observePlayback() {
        stateCollectJob =
            serviceScope.launch {
                manager.state.collect { refreshNotification() }
            }
    }

    // ------------------------------------------------------------------ 主悬浮球

    private fun attachOverlay() {
        if (suppressed) return
        if (!Settings.canDrawOverlays(this)) {
            AppLogger.w(TAG, "没有悬浮窗权限，服务本身保持运行，但不显示小窗")
            return
        }
        if (composeView != null) return

        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent { FloatingContent() }
            }

        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val ballPx = (BALL_SIZE_DP * density).toInt()

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // 从全屏播放页退回来时会重新挂载，位置沿用上次的，别让用户刚拖好的球跳回默认位置
                x = lastOverlayX ?: (screenWidth - ballPx - (12 * density).toInt()).coerceAtLeast(0)
                y = lastOverlayY ?: (resources.displayMetrics.heightPixels * 0.3f).toInt()
            }

        try {
            windowManager.addView(view, params)
            composeView = view
            windowParams = params
            AppLogger.d(TAG, "音乐悬浮球已挂载")
        } catch (e: Exception) {
            AppLogger.e(TAG, "挂载悬浮球失败", e)
            composeView = null
            windowParams = null
        }
    }

    private fun detachOverlay() {
        val view = composeView ?: return
        // 记住位置，下次挂回来时还原
        windowParams?.let {
            lastOverlayX = it.x
            lastOverlayY = it.y
        }
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            AppLogger.e(TAG, "移除悬浮球失败", e)
        }
        composeView = null
        windowParams = null
    }

    /**
     * 展开 / 收起。
     *
     * 窗口宽高都用 WRAP_CONTENT，尺寸完全由 Compose 内容决定（球 58dp / 面板 302dp），
     * 这样切换时不会出现「先按新宽度量一遍旧内容」的闪一下。
     * 需要手动调的只有 x：面板比球宽得多，按新尺寸重新贴右边缘，否则会被顶出屏幕。
     */
    private fun setExpanded(value: Boolean) {
        if (expanded.value == value) return
        expanded.value = value
        applyMainWindowAnchor(value)
    }

    private fun applyMainWindowAnchor(panelMode: Boolean) {
        val params = windowParams ?: return
        val view = composeView ?: return
        val density = resources.displayMetrics.density
        val width = ((if (panelMode) PANEL_WIDTH_DP else BALL_SIZE_DP) * density).toInt()
        val screenWidth = resources.displayMetrics.widthPixels
        params.x = (screenWidth - width - (12 * density).toInt()).coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { AppLogger.e(TAG, "调整悬浮窗位置失败", it) }
    }

    private fun moveMainWindow(dx: Float, dy: Float) {
        val params = windowParams ?: return
        val view = composeView ?: return
        params.x = (params.x + dx.toInt()).coerceAtLeast(0)
        params.y = (params.y + dy.toInt()).coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    // ------------------------------------------------------------------ 桌面歌词条

    private fun setLyricsBar(enabled: Boolean) {
        if (lyricsBarOn.value == enabled) return
        lyricsBarOn.value = enabled
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_LYRICS_BAR, enabled).apply()
        if (enabled) attachLyricsBar() else detachLyricsBar()
    }

    private fun attachLyricsBar() {
        if (!Settings.canDrawOverlays(this)) return
        if (suppressed) return
        if (lyricsView != null) return

        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setContent { LyricsBarContent() }
            }

        val density = resources.displayMetrics.density
        val margin = (LYRICS_MARGIN_DP * density).toInt()
        val width = resources.displayMetrics.widthPixels - margin * 2

        val params =
            WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = margin
                y = (resources.displayMetrics.heightPixels * 0.72f).toInt()
            }

        try {
            windowManager.addView(view, params)
            lyricsView = view
            lyricsParams = params
        } catch (e: Exception) {
            AppLogger.e(TAG, "挂载桌面歌词失败", e)
            lyricsView = null
            lyricsParams = null
        }
    }

    private fun detachLyricsBar() {
        val view = lyricsView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            AppLogger.e(TAG, "移除桌面歌词失败", e)
        }
        lyricsView = null
        lyricsParams = null
    }

    private fun moveLyricsBar(dx: Float, dy: Float) {
        val params = lyricsParams ?: return
        val view = lyricsView ?: return
        params.x = (params.x + dx.toInt()).coerceAtLeast(0)
        params.y = (params.y + dy.toInt()).coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    // ------------------------------------------------------------------ UI

    @Composable
    private fun FloatingContent() {
        val state by manager.state.collectAsState()
        val context = LocalContext.current
        // 悬浮窗挂在 Service 的 WindowManager 上，不在 Activity 的 Compose 主题树里，
        // 自己算一份同源配色再包一层 MaterialTheme，下面所有颜色才能跟着主题走
        val themeSnapshot = rememberActiveThemePreferenceSnapshot()
        val dark = revokitIsDarkTheme(themeSnapshot, isSystemInDarkTheme())
        val colorScheme =
            remember(context, themeSnapshot, dark) {
                revokitColorScheme(context, themeSnapshot, dark)
            }
        MaterialTheme(colorScheme = colorScheme) {
            if (expanded.value) {
                PlayerPanel(state = state)
            } else {
                MusicBall(state = state)
            }
        }
    }

    /** 收起态：一颗圆形小球，脸是应用图标，外面一圈进度环 */
    @Composable
    private fun MusicBall(state: MusicPlaybackState) {
        // 小球浮在其他应用上面，底色不可控，环的底槽只能取中灰半透明才不会两头都看不见
        val ringTrack = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
        val ringColor =
            if (state.isPlaying) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        Box(
            modifier =
                Modifier.size(BALL_SIZE_DP.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { setExpanded(true) }) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            moveMainWindow(dragAmount.x, dragAmount.y)
                        }
                    },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = ringTrack,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                if (state.progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * state.progress,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = getString(R.string.music_ball_expand),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
        }
    }

    /** 展开态：完整播放器面板 */
    @Composable
    private fun PlayerPanel(state: MusicPlaybackState) {
        val favorites by MusicFavorites.keys.collectAsState()
        val favoriteKey = state.favoriteKey
        val isFavorite = favoriteKey != null && favorites.contains(favoriteKey)
        val colors = MaterialTheme.colorScheme
        val panelShape = RoundedCornerShape(22.dp)
        val dragModifier =
            remember {
                Modifier.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        moveMainWindow(dragAmount.x, dragAmount.y)
                    }
                }
            }

        Column(
            modifier =
                Modifier.width(PANEL_WIDTH_DP.dp)
                    .clip(panelShape)
                    // 面板压在别人界面上，必须不透明 + 一圈描边，否则分不清边界
                    .background(colors.surfaceContainerHighest)
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 标题行：拖拽只挂在「封面 + 文字」上，右侧收起键在拖拽区之外
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).then(dragModifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier =
                            Modifier.size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.onSurface.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val art = state.artworkUri
                        if (art != null) {
                            AsyncImage(
                                model = art,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.title?.takeIf { it.isNotBlank() }
                                ?: getString(R.string.music_now_playing_none),
                            color = colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "",
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                MiniButton(
                    contentDescription = getString(R.string.music_panel_collapse),
                    size = 30.dp,
                    onClick = { setExpanded(false) }
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = colors.primary,
                trackColor = colors.onSurface.copy(alpha = 0.14f)
            )

            Spacer(Modifier.height(5.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(state.positionMs),
                    color = colors.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = playModeLabel(state.playMode),
                    color = colors.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTime(state.durationMs),
                    color = colors.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniButton(
                    contentDescription = getString(R.string.music_prev),
                    size = 38.dp,
                    onClick = { manager.previous() }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    MiniButton(
                        contentDescription =
                            getString(if (state.isPlaying) R.string.music_pause else R.string.music_play),
                        size = 46.dp,
                        onClick = { manager.togglePlayPause() }
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                MiniButton(
                    contentDescription = getString(R.string.music_next),
                    size = 38.dp,
                    onClick = { manager.next() }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 底部四个动作：播放模式 / 收藏 / 桌面歌词 / 关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniButton(
                    contentDescription = playModeLabel(state.playMode),
                    size = 36.dp,
                    onClick = { manager.cyclePlayMode() }
                ) {
                    Icon(
                        imageVector = playModeIcon(state.playMode),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                MiniButton(
                    contentDescription =
                        getString(if (isFavorite) R.string.music_unfavorite else R.string.music_favorite),
                    size = 36.dp,
                    onClick = {
                        // 在线曲目必须走 toggleOnline（连歌名一起存），
                        // 只 toggle 一个 key 的话收藏列表里会多出一条没名字的歌
                        val online = manager.currentOnlineSong()
                        if (online != null) {
                            MusicFavorites.toggleOnline(online)
                        } else {
                            favoriteKey?.let { MusicFavorites.toggle(it) }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFFF7A8A) else colors.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
                MiniButton(
                    contentDescription = getString(R.string.music_lyrics_bar),
                    size = 36.dp,
                    background = if (lyricsBarOn.value) colors.primary.copy(alpha = 0.18f) else Color.Transparent,
                    onClick = { setLyricsBar(!lyricsBarOn.value) }
                ) {
                    Text(
                        text = "词",
                        color = if (lyricsBarOn.value) colors.primary else colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                MiniButton(
                    contentDescription = getString(R.string.music_close),
                    size = 36.dp,
                    onClick = { stopSelf() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }

    /** 桌面歌词条 */
    @Composable
    private fun LyricsBarContent() {
        val state by manager.state.collectAsState()
        val lyrics by manager.lyrics.collectAsState()
        val dragModifier =
            remember {
                Modifier.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        moveLyricsBar(dragAmount.x, dragAmount.y)
                    }
                }
            }

        val current = lyrics.lineAt(state.positionMs)
        val next = lyrics.nextLineAt(state.positionMs)
        val hasLyrics = current != null || !lyrics.isEmpty

        // 悬浮歌词是叠在别人界面上的，**不能有背景板**（原来的深色圆角条被吐槽太丑）。
        // 代价是文字可能压在任何底色上，所以必须自带描边阴影才看得清 —— 见下面的 shadow。
        val outline =
            Shadow(
                color = Color.Black.copy(alpha = 0.75f),
                offset = Offset(0f, 1.5f),
                blurRadius = 6f
            )

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .then(dragModifier)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 关闭按钮挪到歌词上方、做得尽量淡：它只在你想收起歌词时才需要看见
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                MiniButton(
                    contentDescription = getString(R.string.music_lyrics_bar_close),
                    size = 24.dp,
                    onClick = { setLyricsBar(false) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                text =
                    when {
                        hasLyrics && current != null -> current.text
                        state.hasTrack -> getString(R.string.music_lyrics_none)
                        else -> getString(R.string.music_now_playing_none)
                    },
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = outline)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text =
                    when {
                        hasLyrics -> next?.text ?: ""
                        state.hasTrack -> getString(R.string.music_lyrics_none_hint)
                        else -> ""
                    },
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = outline)
            )
        }
    }

    /** 悬浮窗里所有可点元素都走这个：detectTapGestures 比 clickable 在悬浮窗里可靠 */
    @Composable
    private fun MiniButton(
        contentDescription: String,
        size: Dp,
        background: Color = Color.Transparent,
        onClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier =
                Modifier.size(size)
                    .clip(CircleShape)
                    .background(background)
                    .pointerInput(contentDescription) {
                        detectTapGestures(onTap = { onClick() })
                    },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }

    private fun playModeIcon(mode: Int): ImageVector =
        when (mode) {
            2 -> Icons.Default.RepeatOne
            3 -> Icons.Default.Shuffle
            else -> Icons.Default.Repeat
        }

    private fun playModeLabel(mode: Int): String =
        when (mode) {
            1 -> getString(R.string.music_play_mode_repeat_all)
            2 -> getString(R.string.music_play_mode_repeat_one)
            3 -> getString(R.string.music_play_mode_shuffle)
            else -> getString(R.string.music_play_mode_order)
        }

    private fun formatTime(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
