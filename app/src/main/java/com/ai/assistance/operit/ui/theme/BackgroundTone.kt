package com.ai.assistance.operit.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 背景图明暗采样。
 *
 * 为什么需要它：美化包的背景图是 100% 强度铺满的，深色照片上放深色小字就是白写。
 * 拿到这张图的平均亮度，就能把「雾面底」按需加减——图越暗垫得越厚，图本来就浅就少垫一点，
 * 把照片的观感尽量留住。结果按 uri 缓存在内存 + SharedPreferences，同一张图只采样一次。
 */
object BackgroundTone {
    private const val TAG = "BackgroundTone"
    private const val PREFS_NAME = "revokit_background_tone"

    /** 采样长边像素数。48 已经足够代表整体明暗，再大只是浪费解码时间。 */
    private const val SAMPLE_SIZE = 48

    private val memory = HashMap<String, Float>()

    /** 只读缓存，不触发采样。没算过返回 null。 */
    fun cached(context: Context, uri: String?): Float? {
        val key = uri?.takeIf { it.isNotBlank() } ?: return null
        memory[key]?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getFloat(key, -1f)
        if (stored < 0f) return null
        memory[key] = stored
        return stored
    }

    /** 取不到缓存就采样一次并写回。失败返回 null，调用方按默认强度处理。 */
    suspend fun resolve(context: Context, uri: String?): Float? {
        cached(context, uri)?.let { return it }
        val key = uri?.takeIf { it.isNotBlank() } ?: return null
        val value =
                withContext(Dispatchers.IO) { runCatching { sample(context, key) }.getOrNull() }
                        ?: return null
        memory[key] = value
        withContext(Dispatchers.IO) {
            context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putFloat(key, value)
                    .apply()
        }
        return value
    }

    /** 解码成极小图后取平均相对亮度（0 = 全黑，1 = 全白），口径与 WCAG 一致。 */
    private fun sample(context: Context, uri: String): Float? {
        val parsed = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var scale = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / scale > SAMPLE_SIZE) {
            scale *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap =
                openStream(context, parsed)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return null

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()

        var sum = 0.0
        var count = 0
        for (pixel in pixels) {
            // 全透明像素不参与统计，否则九宫格边缘的黑边会把结果拉偏
            if ((pixel ushr 24) < 8) continue
            sum += relativeLuminanceOf(pixel)
            count++
        }
        if (count == 0) return null
        return (sum / count).toFloat()
    }

    private fun openStream(context: Context, uri: Uri) =
            runCatching {
                        if (uri.scheme == null || uri.scheme == "file") {
                            File(uri.path ?: return@runCatching null).inputStream()
                        } else {
                            context.contentResolver.openInputStream(uri)
                        }
                    }
                    .getOrElse {
                        AppLogger.w(TAG, "背景图采样失败：$uri", it)
                        null
                    }

    private fun relativeLuminanceOf(pixel: Int): Double {
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        val r = channel((pixel shr 16) and 0xFF)
        val g = channel((pixel shr 8) and 0xFF)
        val b = channel(pixel and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}

/**
 * 给界面用的背景图亮度。没图、或者还在采样时返回 null，调用方按最薄的一层雾面处理。
 *
 * 冷启动第一帧不阻塞：先读缓存，缓存没有再异步算，算完自动重组。
 */
@Composable
fun rememberBackgroundLuminance(uri: String?): Float? {
    if (uri.isNullOrBlank()) return null
    val context = LocalContext.current
    var luminance by remember(uri) { mutableStateOf(BackgroundTone.cached(context, uri)) }
    LaunchedEffect(uri) {
        if (luminance == null) {
            luminance = BackgroundTone.resolve(context, uri)
        }
    }
    return luminance
}

/**
 * 雾面底强度：让文字落在一个可预测的底色上。
 *
 * 思路是把「图 + 雾面」混合后的亮度拉到一个目标区间（浅色主题拉到 0.74、深色主题压到 0.10），
 * 图离目标越远垫得越厚。上下限由调用方给——设置页文字密集要厚，聊天页要看清背景要薄。
 */
fun scrimAlphaFor(luminance: Float?, dark: Boolean, min: Float, max: Float): Float {
    if (luminance == null) return min.coerceAtMost(max)
    val target = if (dark) 0.10f else 0.74f
    val raw =
            if (dark) {
                // 往暗里压：混合后亮度 ≈ 图亮度 × (1 - alpha)
                if (luminance <= 0.02f) 0f else 1f - target / luminance
            } else {
                // 往亮里提：混合后亮度 ≈ 图亮度 × (1 - alpha) + alpha
                if (luminance >= 0.98f) 0f else (target - luminance) / (1f - luminance)
            }
    return raw.coerceIn(min, max)
}
