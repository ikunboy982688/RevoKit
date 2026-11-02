package com.ai.assistance.operit.core.music

import com.ai.assistance.operit.util.AppLogger
import java.io.File

/** 一行歌词：起始时间 + 文本 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * 一首歌的歌词。
 *
 * [source] 只是给人看的标签（"本地 lrc" / 插件名），不参与任何逻辑判断。
 * 行按时间升序，查当前行用二分，悬浮歌词条每 500ms 查一次也不会卡。
 */
class LyricsDoc(val lines: List<LyricLine>, val source: String = "") {

    val isEmpty: Boolean get() = lines.isEmpty()

    /** 当前位置应该高亮第几行；还没到第一句时返回 -1 */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    fun lineAt(positionMs: Long): LyricLine? = lines.getOrNull(indexAt(positionMs))

    /** 下一行，用来在歌词条里做"下一句"预览 */
    fun nextLineAt(positionMs: Long): LyricLine? = lines.getOrNull(indexAt(positionMs) + 1)

    companion object {
        val EMPTY = LyricsDoc(emptyList(), "")
    }
}

/**
 * LRC 解析 + 本地 .lrc 查找。
 *
 * 支持：
 *  - 一行多个时间戳 `[00:12.00][01:30.00]同一句`
 *  - 两种小数点写法 `[mm:ss.xx]` 和 `[mm:ss:xx]`
 *  - 忽略 `[ti:]` `[ar:]` `[al:]` `[by:]` `[offset:]` 这类元信息（正则不匹配，天然被跳过）
 */
object LrcParser {

    private const val TAG = "LrcParser"

    /** 只认「数字:数字」开头的方括号，元信息标签（ar/ti/al/by）不会命中 */
    private val TIME_TAG = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(raw: String, source: String = "lrc"): LyricsDoc {
        val collected = ArrayList<LyricLine>()
        for (line in raw.lineSequence()) {
            val tags = TIME_TAG.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (tag in tags) {
                val minutes = tag.groupValues[1].toLongOrNull() ?: continue
                val seconds = tag.groupValues[2].toLongOrNull() ?: continue
                val fractionRaw = tag.groupValues[3]
                val fraction =
                    when (fractionRaw.length) {
                        0 -> 0L
                        1 -> fractionRaw.toLong() * 100L
                        2 -> fractionRaw.toLong() * 10L
                        else -> fractionRaw.take(3).toLong()
                    }
                collected.add(LyricLine(minutes * 60_000L + seconds * 1_000L + fraction, text))
            }
        }
        collected.sortBy { it.timeMs }
        return LyricsDoc(collected, source)
    }

    /**
     * 在音频文件同目录下找同名 .lrc。
     * 找不到返回 null（调用方显示「暂无歌词」，不要报错弹窗）。
     */
    fun loadForAudioFile(audioPath: String?): LyricsDoc? {
        val path = audioPath?.takeIf { it.isNotBlank() } ?: return null
        return try {
            val audio = File(path)
            if (!audio.exists()) return null
            val base = audio.nameWithoutExtension
            val dir = audio.parentFile ?: return null
            val candidates =
                listOf(
                    File(dir, "$base.lrc"),
                    File(dir, "$base.LRC"),
                    File(dir, "$base.Lrc")
                )
            val lrc = candidates.firstOrNull { it.exists() && it.length() > 0L } ?: return null
            val doc = parse(lrc.readText(Charsets.UTF_8), source = "本地歌词")
            if (doc.isEmpty) null else doc
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取本地歌词失败: $path", e)
            null
        }
    }
}
