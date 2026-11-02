package com.ai.assistance.operit.ui.theme

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter

/**
 * 页面级背景图。
 *
 * 全局背景（Theme 里的 background_image_uri）是铺满整个应用的；有些页面希望用**另一张**图（比如设置页、侧边栏），
 * 就把本组件放在该页面最外层 Box 的第一个子节点，它会盖在全局背景之上。
 *
 * uri 为空或解析失败时什么都不画，保留全局背景 —— 所以没配图的页面行为和以前完全一致。
 */
@Composable
fun PageBackgroundImage(uri: String?, modifier: Modifier = Modifier) {
    if (uri.isNullOrBlank()) return
    val parsed = remember(uri) { runCatching { Uri.parse(uri) }.getOrNull() } ?: return
    val painter = rememberAsyncImagePainter(model = parsed)
    Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
    )
}

/** 雾面底的不透明度：背景图留 34% 透出来，够当氛围，又不至于把字糊掉。 */
internal const val CONTENT_SCRIM_ALPHA = 0.66f

/** 聊天页的雾面区间：明显更薄，背景图还要当家看。 */
internal val CHAT_SCRIM_RANGE = 0.22f..0.86f

/** 设置页的雾面区间：文字密集，保底就要压到接近纯底色。 */
internal val SETTINGS_SCRIM_RANGE = 0.55f..0.92f

/**
 * 内容雾面底。
 *
 * 为什么需要它：主题背景图是**花纹**，同一行小字可能一半压在浅色贴纸上、一半压在深色图案上，
 * 靠给文字调颜色是救不回来的（总有一半会糊）。所以文字密集的页面在背景图上再垫一层
 * 主题底色，让背景退到「氛围」的位置，文字永远落在可预测的底色上。
 *
 * 用 `surface` 而不是固定白色：底色会跟着主题走，深色主题下自动变成深色底。
 * [alpha] 由 [scrimAlphaFor] 按背景图明暗算出：图越暗垫得越厚。
 */
@Composable
fun ContentScrim(alpha: Float = CONTENT_SCRIM_ALPHA, modifier: Modifier = Modifier) {
    Box(
            modifier =
                    modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
    )
}
