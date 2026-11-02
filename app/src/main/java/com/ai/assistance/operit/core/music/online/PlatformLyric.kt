package com.ai.assistance.operit.core.music.online

import android.util.Base64
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 应用侧歌词。
 *
 * **为什么歌词也要应用来做**：和搜索完全同一个道理。查了本地这批洛雪音源，
 * `lx15` 在初始化时明确声明 `actions: ['musicUrl']`（只有取地址），`lx3` / `lx4` / `lx13`
 * 连 lyric 这个字都没有，`lx14` 的歌词只挂在「本地音乐」这个源上。真机上试出来就是
 * 「action not supported / 不支持」—— 不是我们协议桥写错了，是这些脚本压根没实现歌词。
 *
 * 所以照搬搜索的思路：**公开 HTTP 接口，应用直连，不需要加密**。
 * 拿不到再退回音源脚本，两条腿走路。
 */
object PlatformLyric {

    private const val TAG = "PlatformLyric"

    /**
     * 取歌词，返回 LRC 文本；拿不到返回 null。
     *
     * @param platform wy / tx / kw / kg
     * @param musicId 该平台的歌曲 ID（语义同 [PlatformSearch]，wy 数字 / tx songmid / kw rid / kg hash）
     * @param durationMs 时长，酷狗那个接口要带；其余平台用不上
     */
    suspend fun load(platform: String, musicId: String, durationMs: Long = 0L): String? =
        withContext(Dispatchers.IO) {
            if (musicId.isBlank()) return@withContext null
            runCatching {
                    when (platform.lowercase()) {
                        "wy" -> loadWy(musicId)
                        "tx" -> loadTx(musicId)
                        "kw" -> loadKw(musicId)
                        "kg" -> loadKg(musicId, durationMs)
                        else -> null
                    }
                }
                .onFailure { AppLogger.w(TAG, "$platform 歌词失败: ${it.message}") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() && it.contains('[') }
        }

    /**
     * 从歌曲原始 JSON 里取出该平台对应的歌曲 ID。
     *
     * 应用侧搜出来的歌，`toMusicInfoJson()` 会把同一个 ID 写进**所有**可能的字段
     * （见 `PlatformSong.toMusicInfoJson`），所以这里随便读哪个都行 —— 这正是当初那么设计
     * 的原因之一：换链、取词两边都不用再去猜字段。
     */
    fun songIdFor(platform: String, rawJson: String?): String {
        if (rawJson.isNullOrBlank()) return ""
        val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return ""
        val candidates =
            when (platform.lowercase()) {
                "kg" -> listOf("hash", "id", "songId")
                "tx" -> listOf("songmid", "strMediaMid", "songId", "id")
                "kw" -> listOf("rid", "id", "songId")
                else -> listOf("songId", "id", "songmid", "copyrightId", "strMediaMid")
            }
        candidates.forEach { key ->
            val value = json.optString(key)
            if (value.isNotBlank()) return value
        }
        return ""
    }


    // ------------------------------------------------------------------ 网易云
    // https://music.163.com/api/song/lyric?id=<id>&lv=-1  → { lrc: { lyric: "[00:00.000]..." } }

    private fun loadWy(musicId: String): String? {
        val text =
            PlatformSearch.httpText(
                "https://music.163.com/api/song/lyric?id=$musicId&lv=-1&tv=-1",
                mapOf("Referer" to "https://music.163.com/")
            )
        return JSONObject(text).optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ QQ 音乐
    // https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=<songmid>&g_tk=5381&format=json
    // → { retcode: 0, lyric: "<Base64 编码的 LRC>" }
    // QQ 这里返回的是 **Base64**，不解码会显示成一串乱码，必须 Base64.decode

    private fun loadTx(musicId: String): String? {
        val text =
            PlatformSearch.httpText(
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
                    "?songmid=$musicId&g_tk=5381&format=json&nobase64=0",
                mapOf("Referer" to "https://y.qq.com/")
            )
        val json = JSONObject(text)
        if (json.optInt("retcode", -1) != 0 && json.optInt("code", -1) != 0) return null
        val encoded = json.optString("lyric")
        if (encoded.isBlank()) return null
        // 有些歌的 base64 里混着换行，Android 的 Base64.decode 碰上会直接抛，先剔干净
        val clean = encoded.replace(Regex("\\s"), "")
        return runCatching {
                String(Base64.decode(clean, Base64.DEFAULT), Charsets.UTF_8)
            }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ 酷我
    /**
     * 酷我歌词接口要 `musicId` 正确且**服务端愿意给**，实测相当一部分直接回
     * `{"status":301,"msg":"音乐查询失败"}`（需要 cookie / 已下线）。这里尽力而为，
     * 拿不到就退回音源脚本。
     */
    private fun loadKw(musicId: String): String? {
        val text =
            PlatformSearch.httpText(
                "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$musicId&httpsStatus=1",
                mapOf("Referer" to "http://m.kuwo.cn/")
            )
        return JSONObject(text).optJSONObject("data")?.optJSONArray("lrclist")?.let { arr ->
            buildString {
                for (i in 0 until arr.length()) {
                    val line = arr.optJSONObject(i) ?: continue
                    val timeSec = line.optDouble("time", 0.0)
                    val mm = (timeSec / 60).toInt()
                    val ss = timeSec - mm * 60
                    append(String.format("[%02d:%05.2f]", mm, ss))
                    append(line.optString("lineLyric"))
                    append('\n')
                }
            }
        }?.takeIf { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ 酷狗
    /**
     * 酷狗返回的是自家的 **KRC**（不是 LRC），要解一层专有编码，这里不实现 ——
     * 直接跳过，让 [com.ai.assistance.operit.core.music.online.OnlinePlayback] 退回音源脚本。
     */
    private fun loadKg(musicId: String, durationMs: Long): String? = null
}
