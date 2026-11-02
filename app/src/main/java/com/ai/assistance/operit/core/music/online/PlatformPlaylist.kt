package com.ai.assistance.operit.core.music.online

import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 一个外部歌单 */
data class PlaylistInfo(
    val platform: String,
    val playlistId: String,
    val title: String,
    val songs: List<PlatformSong>
)

/**
 * 外部歌单导入。
 *
 * 和搜索、歌词同一条思路：**应用侧直连公开接口**，不需要音源脚本参与。
 * 拿到的曲目直接是 [PlatformSong]，后面换播放地址复用的是和搜索完全一样的链路。
 *
 * 实测可用的两个（2026-10-31）：
 * - 网易 `music.163.com/api/playlist/detail?id=<id>` → `result.tracks[]`
 * - QQ   `c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?...&disstid=<id>`
 *        → `cdlist[0].songlist[]`
 *
 * 酷我 / 酷狗的歌单接口要签名或 cookie，**不做** —— 直接给明确报错，不让人瞎等。
 */
object PlatformPlaylist {

    private const val TAG = "PlatformPlaylist"

    /** 从链接 / id / `平台:id` 里认出平台和歌单 id */
    fun parse(input: String): Pair<String, String>? {
        val raw = input.trim()
        if (raw.isEmpty()) return null

        // 显式写法：wy:123 / tx:7755476181
        val explicit = Regex("^(wy|tx|kw|kg)\\s*[:：]\\s*([A-Za-z0-9]+)$").find(raw)
        if (explicit != null) {
            return explicit.groupValues[1].lowercase() to explicit.groupValues[2]
        }

        val lower = raw.lowercase()
        val id = Regex("[?&#/]?(?:id|disstid)[=/](\\d{4,20})").find(lower)?.groupValues?.get(1)
        return when {
            lower.contains("163.com") || lower.contains("163cn") ->
                (id ?: firstLongNumber(raw))?.let { "wy" to it }
            lower.contains("y.qq.com") || lower.contains("qq.com") ->
                (id ?: firstLongNumber(raw))?.let { "tx" to it }
            lower.contains("kuwo.cn") -> (id ?: firstLongNumber(raw))?.let { "kw" to it }
            lower.contains("kugou.com") -> (id ?: firstLongNumber(raw))?.let { "kg" to it }
            // 光秃秃一串数字：按网易处理（最常见），QQ 歌单 id 也是数字所以并不绝对，
            // 真不对就用 `tx:xxx` 显式指定
            raw.all { it.isDigit() } && raw.length in 4..20 -> "wy" to raw
            else -> null
        }
    }

    private fun firstLongNumber(text: String): String? =
        Regex("\\d{4,20}").find(text)?.groupValues?.get(1)

    suspend fun load(input: String): Result<PlaylistInfo> =
        withContext(Dispatchers.IO) {
            val parsed =
                parse(input)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("认不出这是哪个平台的歌单，试试贴完整链接，或者写成 wy:歌单号 / tx:歌单号")
                    )
            val (platform, id) = parsed
            runCatching {
                    when (platform) {
                        "wy" -> loadWy(id)
                        "tx" -> loadTx(id)
                        else ->
                            throw UnsupportedOperationException(
                                "酷我 / 酷狗的歌单接口要签名或 cookie，暂时不支持导入"
                            )
                    }
                }
                .onFailure { AppLogger.w(TAG, "$platform 歌单 $id 导入失败: ${it.message}") }
        }

    // ------------------------------------------------------------------ 网易云

    private fun loadWy(id: String): PlaylistInfo {
        val text =
            PlatformSearch.httpText(
                "https://music.163.com/api/playlist/detail?id=$id",
                mapOf("Referer" to "https://music.163.com/")
            )
        val result =
            JSONObject(text).optJSONObject("result")
                ?: throw IllegalStateException("歌单不存在或没有公开（返回为空）")
        val tracks = result.optJSONArray("tracks") ?: throw IllegalStateException("这个歌单里没有歌")
        val out = ArrayList<PlatformSong>(tracks.length())
        for (i in 0 until tracks.length()) {
            val song = tracks.optJSONObject(i) ?: continue
            val songId = song.optString("id")
            if (songId.isBlank()) continue
            out.add(
                PlatformSong(
                    platform = "wy",
                    musicId = songId,
                    title = song.optString("name"),
                    artist =
                        song.optJSONArray("artists")?.let { arr ->
                            (0 until arr.length())
                                .mapNotNull { arr.optJSONObject(it)?.optString("name") }
                                .filter { it.isNotBlank() }
                                .joinToString("/")
                        }.orEmpty(),
                    album = song.optJSONObject("album")?.optString("name").orEmpty(),
                    durationMs = song.optLong("duration", 0L)
                )
            )
        }
        if (out.isEmpty()) throw IllegalStateException("这个歌单里没有可播放的歌")
        return PlaylistInfo("wy", id, result.optString("name").ifBlank { "网易云歌单" }, out)
    }

    // ------------------------------------------------------------------ QQ 音乐

    private fun loadTx(id: String): PlaylistInfo {
        val text =
            PlatformSearch.httpText(
                "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
                    "?type=1&json=1&utf8=1&onlysong=0&nosign=1&disstid=$id&format=json",
                mapOf("Referer" to "https://y.qq.com/")
            )
        val list =
            JSONObject(text).optJSONArray("cdlist")?.optJSONObject(0)
                ?: throw IllegalStateException("歌单不存在或没有公开（返回为空）")
        val songs = list.optJSONArray("songlist") ?: throw IllegalStateException("这个歌单里没有歌")
        val out = ArrayList<PlatformSong>(songs.length())
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val mid = song.optString("songmid").ifBlank { song.optString("media_mid") }
            if (mid.isBlank()) continue
            val albumMid = song.optString("albummid")
            out.add(
                PlatformSong(
                    platform = "tx",
                    musicId = mid,
                    title = song.optString("songname"),
                    artist =
                        song.optJSONArray("singer")?.let { arr ->
                            (0 until arr.length())
                                .mapNotNull { arr.optJSONObject(it)?.optString("name") }
                                .filter { it.isNotBlank() }
                                .joinToString("/")
                        }.orEmpty(),
                    album = song.optString("albumname"),
                    durationMs = song.optLong("interval", 0L) * 1000L,
                    artworkUrl =
                        albumMid.takeIf { it.isNotBlank() }?.let {
                            "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg"
                        }
                )
            )
        }
        if (out.isEmpty()) throw IllegalStateException("这个歌单里没有可播放的歌")
        return PlaylistInfo("tx", id, list.optString("dissname").ifBlank { "QQ 歌单" }, out)
    }
}
