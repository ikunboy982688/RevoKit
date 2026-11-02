package com.ai.assistance.operit.ui.features.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 从专辑封面里取一个「主色」，给全屏播放页当渐变背景用。
 *
 * 为什么自己写而不引 `androidx.palette`：
 *  - 这里只要一个颜色，Palette 会顺带算 vibrante/muted/light/dark 一整套，多半用不上；
 *  - 自己实现约 60 行，包体积 0 增长，取色逻辑也能按我们的场景调（要的是「适合当背景」的颜色，
 *    不是「最鲜艳」的颜色）。
 *
 * 算法很朴素但够用：把封面缩到 32×32 → 按每通道 4 位量化成 4096 个桶 → 每个桶按
 * 「像素数 × 饱和度 × 亮度合适度」加权 → 取权重最高的桶求平均色。
 * 亮度合适度刻意惩罚接近纯黑/纯白的颜色，否则歌词文字压上去会读不清。
 */
object ArtworkPalette {

    /** 缩略尺寸。32×32 足够看出主色调，又不会拖慢滚动 */
    private const val SAMPLE = 32

    /**
     * 读封面并算主色。封面为空、读不到、解析失败一律返回 null，
     * 调用方退化成主题色，不弹任何错误 —— 取色失败不该影响听歌。
     */
    suspend fun dominantColor(context: Context, data: String?): Color? {
        if (data.isNullOrBlank()) return null
        val bitmap =
            runCatching {
                    val request =
                        ImageRequest.Builder(context)
                            .data(data)
                            .size(96)
                            .allowHardware(false)
                            .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        (result.drawable as? BitmapDrawable)?.bitmap
                    } else {
                        null
                    }
                }
                .getOrNull() ?: return null
        return runCatching { extract(bitmap) }.getOrNull()
    }

    private fun extract(bitmap: Bitmap): Color? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val small =
            if (w == SAMPLE && h == SAMPLE) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, SAMPLE, SAMPLE, true)
            }
        val pixels = IntArray(SAMPLE * SAMPLE)
        small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        if (small !== bitmap) small.recycle()

        // 每个桶累计：像素数、色彩总和、加权分
        val count = IntArray(4096)
        val sumR = LongArray(4096)
        val sumG = LongArray(4096)
        val sumB = LongArray(4096)
        val weight = DoubleArray(4096)

        for (pixel in pixels) {
            val a = (pixel ushr 24) and 0xFF
            if (a < 128) continue // 半透明像素不参与
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val saturation = (maxC - minC).toDouble() / 255.0
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            // 0.5 上下最受欢迎；纯黑纯白几乎为 0
            val luminosityScore = (1.0 - abs(luminance - 0.5) / 0.5).coerceAtLeast(0.0)

            val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            count[bucket]++
            sumR[bucket] += r.toLong()
            sumG[bucket] += g.toLong()
            sumB[bucket] += b.toLong()
            weight[bucket] += 0.25 + saturation * 0.75 + luminosityScore * 0.5
        }

        var best = -1
        var bestWeight = 0.0
        for (i in 0 until 4096) {
            if (count[i] == 0) continue
            if (weight[i] > bestWeight) {
                bestWeight = weight[i]
                best = i
            }
        }
        if (best < 0) return null

        val n = count[best].toDouble()
        val r = (sumR[best] / n).toInt().coerceIn(0, 255)
        val g = (sumG[best] / n).toInt().coerceIn(0, 255)
        val b = (sumB[best] / n).toInt().coerceIn(0, 255)
        return Color(0xFF000000.toInt() or (r shl 16) or (g shl 8) or b)
    }

    /** 只是给调试/日志用的十六进制串，格式 `#RRGGBB` */
    fun hex(color: Color): String {
        val argb = color.toArgb()
        return "#%06X".format(argb and 0xFFFFFF)
    }
}

/**
 * Compose 侧入口：封面变了就重算一次，算的过程中先给 null。
 * 结果只跟 [artworkUri] 绑定，切歌才重算，进度刷新不会触发。
 */
@Composable
fun rememberArtworkColor(artworkUri: String?): Color? {
    val context = LocalContext.current
    val color by
        produceState<Color?>(initialValue = null, artworkUri) {
            value = ArtworkPalette.dominantColor(context, artworkUri)
        }
    return color
}
