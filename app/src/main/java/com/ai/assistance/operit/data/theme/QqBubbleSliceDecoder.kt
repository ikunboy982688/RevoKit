package com.ai.assistance.operit.data.theme

import android.graphics.Bitmap
import kotlin.math.max

/**
 * 气泡切图参数：六个比例 + 渲染建议，直接对应 BubbleImageStyleConfig。
 *
 * 全部归一化到 [0,1]，语义跟渲染器一致：
 * - crop*：从整图四边裁掉的比例（裁掉的是透明留白）
 * - repeatX/Y*：在**裁剪后**的图上，可拉伸区间的起止比例
 */
data class BubbleSliceParams(
        val cropLeft: Float,
        val cropTop: Float,
        val cropRight: Float,
        val cropBottom: Float,
        val repeatXStart: Float,
        val repeatXEnd: Float,
        val repeatYStart: Float,
        val repeatYEnd: Float,
)

/**
 * 把 QQ 的 .9.png 气泡翻译成我们气泡渲染器能吃的六个比例。
 *
 * 为什么不能直接读黑线：APK 里的 .9.png 已被 aapt 编译过，边缘黑线被抹掉了，
 * 九宫格信息以 npTc chunk 的形式留在 PNG 里。所以要读 chunk，不是做图像识别。
 *
 * npTc 实测布局（QQ 8.3.9，84 字节）：
 *   0..3   wasDeserialized / numXDivs / numYDivs / numColors
 *   4..11  xDivsOffset / yDivsOffset（实测全是 0，不可信）
 *   12..27 padding 四个 int
 *   28..31 保留（实测 0）
 *   32..47 xDivs + yDivs   ← 真正的数据在这
 *   48..   colors
 * 大端。注意别把 12 那段 padding 当成拉伸区——那是内容边距，不是可拉伸区。
 */
object QqBubbleSliceDecoder {

    private const val ALPHA_THRESHOLD = 8
    private const val DEFAULT_DIVS_OFFSET = 32

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * 直接从 PNG 文件字节里抠出 npTc chunk。
     *
     * 为什么不直接用 Bitmap.getNinePatchChunk()：它返回的可能是系统重新序列化过的版本，
     * 布局跟文件里的原始 chunk 对不上（实测按文件布局去读会读不到）。自己抠最可控。
     */
    fun findNinePatchChunk(png: ByteArray): ByteArray? {
        if (png.size < 8) return null
        for (i in PNG_SIGNATURE.indices) {
            if (png[i] != PNG_SIGNATURE[i]) return null
        }
        var pos = 8
        while (pos + 8 <= png.size) {
            val length =
                    ((png[pos].toInt() and 0xFF) shl 24) or
                            ((png[pos + 1].toInt() and 0xFF) shl 16) or
                            ((png[pos + 2].toInt() and 0xFF) shl 8) or
                            (png[pos + 3].toInt() and 0xFF)
            if (length < 0 || pos + 12 + length > png.size) return null
            val type = String(png, pos + 4, 4, Charsets.US_ASCII)
            if (type == "npTc") {
                return png.copyOfRange(pos + 8, pos + 8 + length)
            }
            pos += 12 + length
        }
        return null
    }

    /** 排查用：把 chunk 开头若干字节打成十六进制，方便跟桌面端解析结果对照。 */
    fun hexPrefix(chunk: ByteArray?, count: Int = 48): String {
        if (chunk == null) return "null"
        val sb = StringBuilder()
        for (i in 0 until minOf(count, chunk.size)) {
            sb.append(String.format(java.util.Locale.ROOT, "%02x ", chunk[i]))
        }
        return sb.toString().trim()
    }

    fun decode(bitmap: Bitmap, chunk: ByteArray?): BubbleSliceParams? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 1 || height <= 1) return null

        val bounds = opaqueBounds(bitmap) ?: return null
        val croppedWidth = (bounds.right - bounds.left + 1).coerceAtLeast(1)
        val croppedHeight = (bounds.bottom - bounds.top + 1).coerceAtLeast(1)

        // 裁掉四边透明留白。尖角属于不透明区，不会被裁掉。
        val cropLeft = bounds.left.toFloat() / width
        val cropTop = bounds.top.toFloat() / height
        val cropRight = (width - 1 - bounds.right).toFloat() / width
        val cropBottom = (height - 1 - bounds.bottom).toFloat() / height

        val divs = readDivs(chunk, width, height)
        return if (divs == null) {
            // 没有九宫格信息就退一套居中的稳妥值，总比不显示强
            BubbleSliceParams(
                    cropLeft = cropLeft.coerceIn(0f, 0.45f),
                    cropTop = cropTop.coerceIn(0f, 0.45f),
                    cropRight = cropRight.coerceIn(0f, 0.45f),
                    cropBottom = cropBottom.coerceIn(0f, 0.45f),
                    repeatXStart = 0.35f,
                    repeatXEnd = 0.65f,
                    repeatYStart = 0.35f,
                    repeatYEnd = 0.65f,
            )
        } else {
            val (xs, ys) = divs
            // 拉伸区坐标是相对原图的，换算到裁剪后的坐标系
            BubbleSliceParams(
                    cropLeft = cropLeft.coerceIn(0f, 0.45f),
                    cropTop = cropTop.coerceIn(0f, 0.45f),
                    cropRight = cropRight.coerceIn(0f, 0.45f),
                    cropBottom = cropBottom.coerceIn(0f, 0.45f),
                    repeatXStart = ratioOf(xs[0] - bounds.left, croppedWidth),
                    repeatXEnd = ratioOf(xs[1] - bounds.left, croppedWidth),
                    repeatYStart = ratioOf(ys[0] - bounds.top, croppedHeight),
                    repeatYEnd = ratioOf(ys[1] - bounds.top, croppedHeight),
            )
        }
    }

    /** 渲染器把 repeat 卡在 [0.05,0.95]，这里也先卡一遍，避免被静默吃掉。 */
    private fun ratioOf(value: Int, size: Int): Float {
        val raw = value.toFloat() / size.toFloat().coerceAtLeast(1f)
        return raw.coerceIn(0.05f, 0.95f)
    }

    private data class Bounds(
            val left: Int,
            val top: Int,
            val right: Int,
            val bottom: Int,
    )

    /** 找出不透明区域的包围盒。扫全图，不能跳过最外圈——气泡尖角就在最外圈上。 */
    private fun opaqueBounds(bitmap: Bitmap): Bounds? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        runCatching { bitmap.getPixels(pixels, 0, width, 0, 0, width, height) }
                .getOrElse { return null }

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                if ((pixels[rowOffset + x] ushr 24) > ALPHA_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return null
        return Bounds(left, top, right, bottom)
    }

    /**
     * 读 xDivs / yDivs。返回 (xDivs, yDivs)，各取前两个（起点、终点）。
     * 优先用实测偏移 32；不通过合理性校验则按候选偏移逐个试。
     */
    private fun readDivs(chunk: ByteArray?, width: Int, height: Int): Pair<IntArray, IntArray>? {
        if (chunk == null || chunk.size < 12) return null
        val numX = chunk[1].toInt() and 0xFF
        val numY = chunk[2].toInt() and 0xFF
        if (numX < 2 || numY < 2) return null

        val candidates = mutableListOf(DEFAULT_DIVS_OFFSET)
        val declaredX = readIntBE(chunk, 4)
        val declaredY = readIntBE(chunk, 8)
        if (declaredX > 0 && declaredX < chunk.size) candidates += declaredX
        if (declaredY > 0 && declaredY < chunk.size) candidates += declaredY
        candidates += listOf(12, 28)

        for (offset in candidates) {
            val xs = readInts(chunk, offset, numX, width) ?: continue
            val ys = readInts(chunk, offset + numX * 4, numY, height) ?: continue
            // 必须是一段有宽度、且落在图内的区间，否则多半读串了
            if (xs[0] < xs[1] && ys[0] < ys[1]) return xs to ys
        }
        return null
    }

    private fun readInts(chunk: ByteArray, offset: Int, count: Int, size: Int): IntArray? {
        val out = IntArray(max(2, count))
        for (i in out.indices) {
            val at = offset + i * 4
            if (at + 4 > chunk.size) return null
            val value = readIntBE(chunk, at)
            if (value <= 0 || value >= size) return null
            out[i] = value
        }
        return out
    }

    private fun readIntBE(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return 0
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }
}
