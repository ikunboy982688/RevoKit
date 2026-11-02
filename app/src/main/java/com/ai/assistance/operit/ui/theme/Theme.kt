package com.ai.assistance.operit.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.net.Uri
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_DARK
import com.ai.assistance.operit.data.preferences.UserPreferencesManager.Companion.ON_COLOR_MODE_LIGHT
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

private val DarkColorScheme =
        darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
        lightColorScheme(
                primary = Purple40,
                secondary = PurpleGrey40,
                tertiary = Pink40,

                /* Other default colors to override
                background = Color(0xFFFFFBFE),
                surface = Color(0xFFFFFBFE),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onTertiary = Color.White,
                onBackground = Color(0xFF1C1B1F),
                onSurface = Color(0xFF1C1B1F),
                */
                )


@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID),
    )
    val themeSnapshot = rememberActiveThemePreferenceSnapshot()

    fun disableBackgroundForTarget(target: ActivePrompt) {
        coroutineScope.launch {
            activePromptManager.mutateActiveThemeForPrompt(target) { values ->
                values.withBoolean("use_background_image", false)
            }
        }
    }

    val useSystemTheme = themeSnapshot.useSystemTheme
    val themeMode = themeSnapshot.themeMode
    val useCustomColors = themeSnapshot.useCustomColors
    val customPrimaryColor = themeSnapshot.customPrimaryColor
    val customSecondaryColor = themeSnapshot.customSecondaryColor
    val onColorMode = themeSnapshot.onColorMode
    val useBackgroundImage = themeSnapshot.useBackgroundImage
    val backgroundImageUri = themeSnapshot.backgroundImageUri
    val backgroundImageOpacity = themeSnapshot.backgroundImageOpacity
    val backgroundMediaType = themeSnapshot.backgroundMediaType
    val videoBackgroundMuted = themeSnapshot.videoBackgroundMuted
    val videoBackgroundLoop = themeSnapshot.videoBackgroundLoop
    val useCustomStatusBarColor = themeSnapshot.useCustomStatusBarColor
    val customStatusBarColorValue = themeSnapshot.customStatusBarColor
    val statusBarTransparent = themeSnapshot.statusBarTransparent
    val statusBarHidden = themeSnapshot.statusBarHidden
    val useBackgroundBlur = themeSnapshot.useBackgroundBlur
    val backgroundBlurRadius = themeSnapshot.backgroundBlurRadius
    val useNoiseTexture = themeSnapshot.useNoiseTexture
    LaunchedEffect(useNoiseTexture) {
        AppLogger.d("RevoKitNoise", "theme 收到 use_noise_texture=$useNoiseTexture")
    }
    val useCustomFont = themeSnapshot.useCustomFont
    val fontType = themeSnapshot.fontType
    val systemFontName = themeSnapshot.systemFontName
    val customFontPath = themeSnapshot.customFontPath
    val fontScale = themeSnapshot.fontScale

    // 创建自定义 Typography
    val customTypography = remember(useCustomFont, fontType, systemFontName, customFontPath, fontScale) {
        createCustomTypography(
            context = context,
            useCustomFont = useCustomFont,
            fontType = fontType,
            systemFontName = systemFontName,
            customFontPath = customFontPath,
            fontScale = fontScale
        )
    }

    // 确定是否使用暗色主题
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
            if (useSystemTheme) {
                systemDarkTheme
            } else {
                themeMode == UserPreferencesManager.THEME_MODE_DARK
            }

    // Dynamic color is available on Android 12+
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 基础主题色调
    var colorScheme =
            when {
                dynamicColor -> {
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }

    // 应用自定义颜色和文本颜色
    if (useCustomColors) {
        customPrimaryColor?.let { primaryArgb ->
            val primary = Color(primaryArgb)
            val secondary = customSecondaryColor?.let { Color(it) } ?: colorScheme.secondary

            colorScheme = if (darkTheme) {
                generateDarkColorScheme(primary, secondary, onColorMode)
                    } else {
                generateLightColorScheme(primary, secondary, onColorMode)
                    }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = window.decorView.let { decorView ->
                androidx.core.view.WindowCompat.getInsetsController(window, decorView)
            }
            
            // 始终保持沉浸式模式，让Compose处理状态栏背景
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // 隐藏或显示状态栏
            if (statusBarHidden) {
                // 隐藏状态栏
                insetsController?.hide(WindowInsetsCompat.Type.statusBars())
                insetsController?.systemBarsBehavior = 
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // 显示状态栏
                insetsController?.show(WindowInsetsCompat.Type.statusBars())
                
                // 状态栏颜色和图标颜色控制
                val statusBarColor = when {
                    statusBarTransparent -> Color.Transparent.toArgb()
                    useBackgroundImage && backgroundImageUri != null -> Color.Transparent.toArgb()  // 有背景时透明
                    useCustomStatusBarColor && customStatusBarColorValue != null -> customStatusBarColorValue!!.toInt()
                    else -> colorScheme.primary.toArgb()
                }
                window.statusBarColor = statusBarColor

                // 根据状态栏背景色动态设置状态栏图标颜色
                // isAppearanceLightStatusBars = true 表示图标为深色（适用于浅色背景）
                // isAppearanceLightStatusBars = false 表示图标为浅色（适用于深色背景）
                insetsController?.isAppearanceLightStatusBars = !isColorLight(Color(statusBarColor))
            }
            
            // 设置导航栏颜色（底部小白条所在的区域）
            // 在有背景图片时，让导航栏透明
            if (useBackgroundImage && backgroundImageUri != null) {
                // 关键：禁用导航栏对比度强制模式（Android 10+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                // 设置为完全透明
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                // 根据主题设置导航栏图标颜色
                insetsController?.isAppearanceLightNavigationBars = !darkTheme
            } else {
                // 没有背景时使用软件背景色作为导航栏背景色
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = true
                }
                window.navigationBarColor = colorScheme.background.toArgb()
                // 根据导航栏背景色动态设置导航栏图标颜色
                // isAppearanceLightNavigationBars = true 表示图标为深色（适用于浅色背景）
                // isAppearanceLightNavigationBars = false 表示图标为浅色（适用于深色背景）
                insetsController?.isAppearanceLightNavigationBars = !isColorLight(colorScheme.background)
            }
        }
    }

    // 视频播放器状态
    val exoPlayer =
            remember(
                    useBackgroundImage,
                    backgroundImageUri,
                    backgroundMediaType,
                    videoBackgroundLoop,
                    videoBackgroundMuted
            ) {
                if (useBackgroundImage &&
                                backgroundImageUri != null &&
                                backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_VIDEO
                ) {
                    ExoPlayer.Builder(context)
                            // Add memory optimizations
                            .setLoadControl(
                                    DefaultLoadControl.Builder()
                                            .setBufferDurationsMs(
                                                    5000,  // 最小缓冲时间，减少到5秒
                                                    10000, // 最大缓冲时间，减少到10秒
                                                    500,   // 回放所需的最小缓冲
                                                    1000   // 重新缓冲后回放所需的最小缓冲
                                            )
                                            .setTargetBufferBytes(5 * 1024 * 1024) // 将缓冲限制为5MB
                                            .setPrioritizeTimeOverSizeThresholds(true)
                                            .build()
                            )
                            .build()
                            .apply {
                                // 设置循环播放
                                repeatMode =
                                        if (videoBackgroundLoop) Player.REPEAT_MODE_ALL
                                        else Player.REPEAT_MODE_OFF
                                // 设置静音
                                volume = if (videoBackgroundMuted) 0f else 1f
                                playWhenReady = true

                                // 加载视频
                                try {
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(backgroundImageUri))
                                        .build()
                                    setMediaItem(mediaItem)
                                    prepare()
                                } catch (e: Exception) {
                                    AppLogger.e(
                                            "OperitTheme",
                                            "Error loading video background: ${e.message}",
                                            e
                                    )
                                    disableBackgroundForTarget(activePrompt)
                                }
                            }
                } else {
                    null
                }
            }

    // 释放ExoPlayer资源
    DisposableEffect(key1 = Unit) { 
        onDispose { 
            try {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                exoPlayer?.release() 
            } catch (e: Exception) {
                AppLogger.e("OperitTheme", "ExoPlayer释放错误", e)
            }
        } 
    }

    // 监听应用生命周期，控制视频播放
    if (exoPlayer != null) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        exoPlayer.pause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        exoPlayer.play()
                    }
                    else -> {}
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // 应用主题和自定义背景
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val liquidGlassBackdrop = rememberLayerBackdrop()
        val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null

        CompositionLocalProvider(
            LocalThemePreferenceSnapshot provides themeSnapshot,
            LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
            LocalWaterGlassState provides waterGlassState,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().layerBackdrop(liquidGlassBackdrop)
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(if (darkTheme) Color.Black else Color.White)
                            .then(
                                if (waterGlassState != null) {
                                    Modifier.liquefiable(waterGlassState)
                                } else {
                                    Modifier
                                },
                            )
                )

                if (useBackgroundImage && backgroundImageUri != null) {
                    val uri = Uri.parse(backgroundImageUri)

                    if (backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
                        val painter =
                            rememberAsyncImagePainter(
                                model = uri,
                                error =
                                    rememberAsyncImagePainter(
                                        if (darkTheme) Color.Black else Color.White
                                    ),
                            )

                        LaunchedEffect(painter) {
                            if (painter.state is AsyncImagePainter.State.Error) {
                                AppLogger.e(
                                    "OperitTheme",
                                    "Error loading background image from URI: $backgroundImageUri",
                                )

                                if (uri.scheme == "file") {
                                    val file = uri.path?.let { File(it) }
                                    if (file == null || !file.exists()) {
                                        AppLogger.e(
                                            "OperitTheme",
                                            "Internal file doesn't exist: ${file?.absolutePath}",
                                        )
                                    } else {
                                        AppLogger.e(
                                            "OperitTheme",
                                            "File exists but couldn't be loaded: ${file.absolutePath}, size: ${file.length()}",
                                        )
                                    }
                                }

                                disableBackgroundForTarget(activePrompt)
                            }
                        }

                        Image(
                            painter = painter,
                            contentDescription = "Background Image",
                            modifier =
                                Modifier.fillMaxSize()
                                    .alpha(backgroundImageOpacity)
                                    .then(
                                        if (useBackgroundBlur) {
                                            Modifier.blur(radius = backgroundBlurRadius.dp)
                                        } else {
                                            Modifier
                                        },
                                    ).then(
                                        if (waterGlassState != null) {
                                            Modifier.liquefiable(waterGlassState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        exoPlayer?.let { player ->
                            val videoBackgroundColor =
                                if (darkTheme) {
                                    android.graphics.Color.BLACK
                                } else {
                                    android.graphics.Color.WHITE
                                }
                            AndroidView(
                                factory = { ctx ->
                                    (LayoutInflater.from(ctx).inflate(
                                        R.layout.view_background_texture_player,
                                        null,
                                        false,
                                    ) as StyledPlayerView).apply {
                                        this.player = player
                                        useController = false
                                        layoutParams =
                                            ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        setBackgroundColor(videoBackgroundColor)
                                        setShutterBackgroundColor(videoBackgroundColor)
                                        setKeepContentOnPlayerReset(true)
                                        foreground =
                                            android.graphics.drawable.ColorDrawable(
                                                android.graphics.Color.argb(
                                                    ((1f - backgroundImageOpacity) * 255).toInt(),
                                                    if (darkTheme) 0 else 255,
                                                    if (darkTheme) 0 else 255,
                                                    if (darkTheme) 0 else 255,
                                                )
                                            )
                                    }
                                },
                                update = { view ->
                                    if (view.player != player) {
                                        view.player = player
                                    }

                                    view.setBackgroundColor(videoBackgroundColor)
                                    view.setShutterBackgroundColor(videoBackgroundColor)
                                    view.setKeepContentOnPlayerReset(true)
                                    view.foreground =
                                        android.graphics.drawable.ColorDrawable(
                                            android.graphics.Color.argb(
                                                ((1f - backgroundImageOpacity) * 255).toInt(),
                                                if (darkTheme) 0 else 255,
                                                if (darkTheme) 0 else 255,
                                                if (darkTheme) 0 else 255,
                                            )
                                        )
                                },
                                modifier =
                                    Modifier.fillMaxSize().then(
                                        if (waterGlassState != null) {
                                            Modifier.liquefiable(waterGlassState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            if (useBackgroundImage && backgroundImageUri != null) {
                // 半透明卡片：开了这个开关就把「承载列表/分组的底色」放透明，
                // 让背景图透出来。弹窗那一层（surfaceContainer / High / Highest）保持不透明，
                // 否则对话框里文字压着花纹会难读。
                val cardAlpha =
                    if (themeSnapshot.translucentSurfaces) CARD_SURFACE_ALPHA else 1f
                MaterialTheme(
                    colorScheme =
                        colorScheme.copy(
                            surface = colorScheme.surface.copy(alpha = cardAlpha),
                            surfaceVariant = colorScheme.surfaceVariant.copy(alpha = cardAlpha),
                            background = colorScheme.background.copy(alpha = 1f),
                            surfaceContainer = colorScheme.surfaceContainer.copy(alpha = 1f),
                            surfaceContainerHigh =
                                colorScheme.surfaceContainerHigh.copy(alpha = 1f),
                            surfaceContainerHighest =
                                colorScheme.surfaceContainerHighest.copy(alpha = 1f),
                            surfaceContainerLow =
                                colorScheme.surfaceContainerLow.copy(alpha = cardAlpha),
                            surfaceContainerLowest =
                                colorScheme.surfaceContainerLowest.copy(alpha = cardAlpha),
                        ),
                    typography = customTypography,
                    content = content,
                )
            } else {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = customTypography,
                    content = content,
                )
            }

            // RevoKit：全局噪点/纸张质感。叠在所有内容之上、透明度很低，
            // 只在纯色背景上有感知；图片/视频背景时也可以关掉它。
            if (useNoiseTexture) {
                NoiseTextureOverlay(darkTheme = darkTheme)
            }
        }
    }
}

/**
 * RevoKit 噪点/纸张质感：一张 64×64 的可平铺噪声图，用重复 shader 一次画满全屏，
 * 只花一次绘制调用（不是逐块贴图），暗色下更明显一点。
 */
@Composable
private fun NoiseTextureOverlay(darkTheme: Boolean) {
    val context = LocalContext.current
    val alpha = if (darkTheme) 0.09f else 0.06f
    // 用原生 BitmapShader 平铺：一次 drawRect 画满整屏，不逐块贴图。
    // （之前用 Compose 的 ImageShader + background(alpha) 实测画不出东西。）
    val paint =
        remember(alpha) {
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.noise_tile)
            android.graphics.Paint().apply {
                shader =
                    BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
                this.alpha = (255 * alpha).toInt()
                isAntiAlias = false
                isFilterBitmap = false
            }
        }
    Box(
        modifier =
            Modifier.fillMaxSize()
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            paint,
                        )
                    }
                }
    )
}

/**
 * RevoKit：给「不在 Activity 主题树里」的地方算一份配色（音乐悬浮窗、通知、桌面组件）。
 *
 * 这些界面拿不到 `MaterialTheme`，又必须跟主界面一个色，所以把同一套算法暴露出来，
 * 让它们自己包一层 `MaterialTheme(colorScheme = ...)`，颜色就跟着主题走了。
 */
fun revokitColorScheme(
        context: Context,
        snapshot: ThemePreferenceSnapshot,
        dark: Boolean,
): ColorScheme {
    val primaryArgb = snapshot.customPrimaryColor
    if (snapshot.useCustomColors && primaryArgb != null) {
        val primary = Color(primaryArgb)
        val secondary = snapshot.customSecondaryColor?.let { Color(it) } ?: primary
        return if (dark) {
            generateDarkColorScheme(primary, secondary, snapshot.onColorMode)
        } else {
            generateLightColorScheme(primary, secondary, snapshot.onColorMode)
        }
    }
    // 没设自定义配色时，主界面用的是系统动态取色（Android 12+），这里必须对齐，
    // 否则悬浮窗会跟主界面差一个色系
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColorScheme else LightColorScheme
    }
}

/** RevoKit：判断当前该用深色还是浅色，规则与 `OperitTheme` 内部完全一致。 */
fun revokitIsDarkTheme(snapshot: ThemePreferenceSnapshot, systemDark: Boolean): Boolean =
        if (snapshot.useSystemTheme) {
            systemDark
        } else {
            snapshot.themeMode == UserPreferencesManager.THEME_MODE_DARK
        }

/** RevoKit：一整套由种子色派生的中性色。
 *
 * 背景：M3 的 `lightColorScheme()` / `darkColorScheme()` 里只有 primary 那一组是「活」的，
 * `surface`、`surfaceContainer*`、`outline` 全是库内置的静态浅紫白。只要用户套了自定义配色，
 * 这些 token 就跟主题彻底脱节——「关于页 / 音乐播放器 / 决策卡」那些卡片永远是大片白。
 *
 * 做法照 M3 的 tonal palette 思路：取种子色的**色相**，把饱和度压到近乎中性，
 * 再按 M3 的 tone 台阶（0=黑 100=白）取亮度。这样任何主色都能长出一套同色系的中性色，
 * 且深浅两档的对比关系固定（正文对背景始终约 12~13 tone 的差距，远高于 4.5:1）。
 */
private class TonalPalette(
        val surface: Color,
        val surfaceDim: Color,
        val surfaceBright: Color,
        val containerLowest: Color,
        val containerLow: Color,
        val container: Color,
        val containerHigh: Color,
        val containerHighest: Color,
        val onSurface: Color,
        val onSurfaceVariant: Color,
        val outline: Color,
        val outlineVariant: Color,
        val inverseSurface: Color,
        val inverseOnSurface: Color,
)

private fun tonalPalette(seed: Color, dark: Boolean): TonalPalette {
    val hue = hueOf(seed)
    // tone: 0~100 的亮度百分比；chroma: HSL 饱和度，0.05 左右已经是「能看出一点色、但不抢戏」
    fun tone(t: Float, chroma: Float): Color = hslToColor(hue, chroma, t / 100f)

    return if (!dark) {
        TonalPalette(
                surface = tone(98.5f, 0.05f),
                surfaceDim = tone(86f, 0.05f),
                surfaceBright = tone(99f, 0.04f),
                containerLowest = tone(100f, 0.03f),
                containerLow = tone(96f, 0.05f),
                container = tone(94.5f, 0.05f),
                containerHigh = tone(92f, 0.06f),
                containerHighest = tone(90f, 0.06f),
                onSurface = tone(12f, 0.08f),
                onSurfaceVariant = tone(32f, 0.10f),
                outline = tone(52f, 0.08f),
                outlineVariant = tone(82f, 0.07f),
                inverseSurface = tone(20f, 0.06f),
                inverseOnSurface = tone(95f, 0.05f),
        )
    } else {
        TonalPalette(
                surface = tone(8f, 0.06f),
                surfaceDim = tone(6f, 0.06f),
                surfaceBright = tone(24f, 0.06f),
                containerLowest = tone(5f, 0.06f),
                containerLow = tone(11f, 0.06f),
                container = tone(13f, 0.06f),
                containerHigh = tone(18f, 0.06f),
                containerHighest = tone(22f, 0.06f),
                onSurface = tone(92f, 0.06f),
                onSurfaceVariant = tone(80f, 0.08f),
                outline = tone(62f, 0.08f),
                outlineVariant = tone(32f, 0.07f),
                inverseSurface = tone(90f, 0.05f),
                inverseOnSurface = tone(15f, 0.06f),
        )
    }
}

/** 取色的色相（0~360），中性色的色相在这里无所谓，用它只是为了「沾一点主色的味道」。 */
private fun hueOf(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta == 0f) return 0f
    val raw =
            when (max) {
                r -> ((g - b) / delta) % 6f
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            }
    return (raw * 60f + 360f) % 360f
}

/** HSL → RGB。亮度按 0~1 给，和 M3 的 tone/100 基本对得上，够用。 */
private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val hp = hue / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val rgb =
            when {
                hp < 1f -> Triple(c, x, 0f)
                hp < 2f -> Triple(x, c, 0f)
                hp < 3f -> Triple(0f, c, x)
                hp < 4f -> Triple(0f, x, c)
                hp < 5f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
    val m = lightness - c / 2f
    return Color(
            (rgb.first + m).coerceIn(0f, 1f),
            (rgb.second + m).coerceIn(0f, 1f),
            (rgb.third + m).coerceIn(0f, 1f)
    )
}

// ============================ RevoKit：对比度兜底 ============================
// 主题包给的主色是「作者想要的那支颜色」，实测下来普遍偏浅（浅樱粉 #F6D2E6、
// 草绿 #7E926A、灰玫 #9A7E82）。我们全项目有近 400 处把它当文字/图标色用，
// 浅色压在白底（tonalPalette 的 tone 98.5）上对比度只有 1.33~3.57，直接糊成一片。
// 这里统一做一次「只调明度、不动色相」的修正，保证文字类颜色至少能读。

/** WCAG 相对亮度（0~1）。注意不是简单的 0.299r+0.587g+0.114b。 */
private fun relativeLuminance(color: Color): Float {
    fun channel(c: Float): Float =
            if (c <= 0.04045f) c / 12.92f
            else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * channel(color.red) +
            0.7152f * channel(color.green) +
            0.0722f * channel(color.blue)
}

/** WCAG 对比度，1.0 ~ 21.0。正文要求 ≥ 4.5，18sp 以上大字可放宽到 3.0。 */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** 拆成 HSL。色相沿用 `hueOf`，这里补上饱和度和明度。 */
private fun hslOf(color: Color): Triple<Float, Float, Float> {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val lightness = (max + min) / 2f
    val delta = max - min
    val sat =
            if (delta == 0f) 0f
            else delta / (1f - kotlin.math.abs(2f * lightness - 1f)).coerceAtLeast(1e-4f)
    return Triple(hueOf(color), sat.coerceIn(0f, 1f), lightness)
}

/**
 * 把 [color] 的明度朝「远离背景」的方向挪，直到与 [background] 的对比度 ≥ [minRatio]。
 *
 * 只动明度、不动色相和饱和度 —— 这样浅粉加深之后还是粉，不会被洗成灰。
 * 背景亮就压暗，背景暗就提亮，浅色/深色主题共用同一套逻辑。
 */
private fun ensureContrast(color: Color, background: Color, minRatio: Float = 4.5f): Color {
    if (contrastRatio(color, background) >= minRatio) return color
    val (hue, sat, lightness) = hslOf(color)
    // 白底上要压暗、黑底上要提亮
    val step = if (relativeLuminance(background) >= 0.5f) -0.01f else 0.01f
    var l = lightness
    while (l > 0f && l < 1f) {
        l += step
        val candidate = hslToColor(hue, sat, l)
        if (contrastRatio(candidate, background) >= minRatio) return candidate
    }
    // 明度走到头还不够（极端色），退回黑/白里对比度更高的那个
    return if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }
}

/** 十六进制打印，调试配色用。 */
private fun hexOf(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)

/** 为亮色主题生成基于主色的完整颜色方案 */
private fun generateLightColorScheme(
    primaryColor: Color,
    secondaryColor: Color,
    onColorMode: String
): ColorScheme {
    // RevoKit：整套中性色也由主色派生，否则 surface/surfaceContainer 会一直停留在
    // M3 默认的静态浅紫白，「关于页 / 播放器 / 决策卡」那些卡片就永远是大片白
    val p = tonalPalette(primaryColor, dark = false)

    // 主题包里的主色常常偏浅（实测有 1.33:1 的），先修到能在表面色上读出来再用
    val safePrimary = ensureContrast(primaryColor, p.surface)
    val safeSecondary = ensureContrast(secondaryColor, p.surface)

    val onPrimary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(safePrimary)
    }
    val onSecondary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(safeSecondary)
    }

    val primaryContainer = lightenColor(safePrimary, 0.7f)
    val onPrimaryContainer = getContrastingTextColor(primaryContainer)
    val secondaryContainer = lightenColor(safeSecondary, 0.7f)
    val onSecondaryContainer = getContrastingTextColor(secondaryContainer)

    AppLogger.d(
            "RevoKitColor",
            "浅色方案：主题包主色=${hexOf(primaryColor)} → 实际 ${hexOf(safePrimary)}" +
                    "，对表面色对比度 ${"%.2f".format(contrastRatio(safePrimary, p.surface))}:1" +
                    "（表面 ${hexOf(p.surface)}）",
    )

    // Return a complete color scheme, ensuring onSurface and onSurfaceVariant are consistent
    return LightColorScheme.copy(
            primary = safePrimary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = safeSecondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
        // Ensure other colors are consistent with a light theme
        onSurface = p.onSurface,
        onSurfaceVariant = p.onSurfaceVariant,
        onBackground = p.onSurface,
        surface = p.surface,
        background = p.surface,
        surfaceVariant = p.containerHighest,
        surfaceDim = p.surfaceDim,
        surfaceBright = p.surfaceBright,
        surfaceContainerLowest = p.containerLowest,
        surfaceContainerLow = p.containerLow,
        surfaceContainer = p.container,
        surfaceContainerHigh = p.containerHigh,
        surfaceContainerHighest = p.containerHighest,
        outline = p.outline,
        outlineVariant = p.outlineVariant,
        inverseSurface = p.inverseSurface,
        inverseOnSurface = p.inverseOnSurface
    )
}

/** 为暗色主题生成基于主色的完整颜色方案 */
private fun generateDarkColorScheme(
        primaryColor: Color,
    secondaryColor: Color,
    onColorMode: String
): ColorScheme {
    val p = tonalPalette(primaryColor, dark = true)

    // 深色底上的主色必须够亮；偏暗的主题色（比如纯黑包）在这里会被自动提亮到能读
    val adjustedPrimaryColor = ensureContrast(lightenColor(primaryColor, 0.2f), p.surface)
    val adjustedSecondaryColor = ensureContrast(lightenColor(secondaryColor, 0.2f), p.surface)

    val onPrimary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(adjustedPrimaryColor)
    }
    val onSecondary = when (onColorMode) {
        ON_COLOR_MODE_LIGHT -> Color.White
        ON_COLOR_MODE_DARK -> Color.Black
        else -> getContrastingTextColor(adjustedSecondaryColor)
    }

    val primaryContainer = darkenColor(primaryColor, 0.3f)
    // 原来是硬写白色，压在中调容器上会糊；改成按实际对比度取黑或白
    val onPrimaryContainer = getContrastingTextColor(primaryContainer)
    val secondaryContainer = darkenColor(secondaryColor, 0.3f)
    val onSecondaryContainer = getContrastingTextColor(secondaryContainer)

    AppLogger.d(
            "RevoKitColor",
            "深色方案：主题包主色=${hexOf(primaryColor)} → 实际 ${hexOf(adjustedPrimaryColor)}" +
                    "，对表面色对比度 ${"%.2f".format(contrastRatio(adjustedPrimaryColor, p.surface))}:1" +
                    "（表面 ${hexOf(p.surface)}）",
    )

    // Return a complete color scheme, ensuring onSurface and onSurfaceVariant are consistent
    return DarkColorScheme.copy(
            primary = adjustedPrimaryColor,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = adjustedSecondaryColor,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
        // Ensure other colors are consistent with a dark theme
        onSurface = p.onSurface,
        onSurfaceVariant = p.onSurfaceVariant,
        onBackground = p.onSurface,
        surface = p.surface,
        background = p.surface,
        surfaceVariant = p.containerHighest,
        surfaceDim = p.surfaceDim,
        surfaceBright = p.surfaceBright,
        surfaceContainerLowest = p.containerLowest,
        surfaceContainerLow = p.containerLow,
        surfaceContainer = p.container,
        surfaceContainerHigh = p.containerHigh,
        surfaceContainerHighest = p.containerHighest,
        outline = p.outline,
        outlineVariant = p.outlineVariant,
        inverseSurface = p.inverseSurface,
        inverseOnSurface = p.inverseOnSurface
    )
}

/** Add a new helper function to determine appropriate text color based on background color */
private fun getContrastingTextColor(
        backgroundColor: Color,
        forceDark: Boolean = false,
        forceLight: Boolean = false
): Color {
    // If forced, return the specified color
    if (forceDark) return Color.Black
    if (forceLight) return Color.White

    // RevoKit：原来用「加权亮度 > 0.5 就黑字」的阈值法，中间调会判错方向。
    // 现在直接算 WCAG 对比度，黑白里挑读得更清楚的那个。
    return if (contrastRatio(Color.Black, backgroundColor) >=
                    contrastRatio(Color.White, backgroundColor)) {
        Color.Black
    } else {
        Color.White
    }
}

/** 使颜色变亮 */
private fun lightenColor(color: Color, factor: Float): Color {
    val r = color.red + (1f - color.red) * factor
    val g = color.green + (1f - color.green) * factor
    val b = color.blue + (1f - color.blue) * factor
    return Color(r, g, b, color.alpha)
}

/** 使颜色变暗 */
private fun darkenColor(color: Color, factor: Float): Color {
    val r = color.red * (1f - factor)
    val g = color.green * (1f - factor)
    val b = color.blue * (1f - factor)
    return Color(r, g, b, color.alpha)
}

/** 混合两种颜色 */
private fun blendColors(color1: Color, color2: Color, ratio: Float): Color {
    val r = color1.red * (1 - ratio) + color2.red * ratio
    val g = color1.green * (1 - ratio) + color2.green * ratio
    val b = color1.blue * (1 - ratio) + color2.blue * ratio
    return Color(r, g, b)
}

/** 判断颜色是否较浅 */
private fun isColorLight(color: Color): Boolean {
    // 计算颜色亮度 (0.0-1.0)
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5
}

/** 判断颜色是否较深 */
private fun isColorDark(color: Color): Boolean {
    return !isColorLight(color)
}

/**
 * 半透明卡片的透明度。
 *
 * 0.72 是实测折中：再低背景花纹会明显干扰文字，再高就看不出"透"。
 * 只在主题包/用户显式开启 translucent_surfaces 时生效。
 */
private const val CARD_SURFACE_ALPHA = 0.72f
