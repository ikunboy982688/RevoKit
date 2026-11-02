package com.ai.assistance.operit.core.music.online

import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 内置平台搜索拿到的一首歌 */
data class PlatformSong(
    /** wy / tx / kw / kg */
    val platform: String,
    /** 该平台的歌曲 ID（网易数字 id、QQ songmid、酷我 rid、酷狗 hash） */
    val musicId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String? = null
)

/**
 * 应用侧的四平台搜索。
 *
 * **为什么搜索必须由应用做**：洛雪（LX Music）自定义音源的协议里只有
 * `musicUrl` / `lyric` / `pic` 三个动作，**没有 search**。所以「音源脚本不能搜歌」
 * 不是脚本的缺陷，而是上游协议就这么定的 —— 分工是：
 *
 *   应用侧搜索（本文件）→ 得到 (平台, 歌曲ID) → 交给音源脚本换直链 → 播放器放直链
 *
 * 四个平台都是纯 HTTP + JSON，全程不需要 weapi/eapi 加密；加密只出现在「拿 ID 换直链」
 * 那一步，那是音源脚本的活。
 *
 * ID 语义按平台不同，混用会取不到地址：
 * - `wy` 数字 songid ｜ `tx` songmid 字符串 ｜ `kw` 纯数字 rid ｜ `kg` 32 位 hash
 */
object PlatformSearch {

    private const val TAG = "PlatformSearch"

    val PLATFORMS = listOf("wy", "tx", "kw", "kg")

    fun label(platform: String): String =
        when (platform.lowercase()) {
            "wy" -> "网易云"
            "tx" -> "QQ音乐"
            "kw" -> "酷我"
            "kg" -> "酷狗"
            "mg" -> "咪咕"
            else -> platform
        }

    fun isPlatform(code: String): Boolean = PLATFORMS.contains(code.lowercase())

    /**
     * 并发搜指定平台，单个平台失败不影响其他；结果按传入顺序拼起来。
     *
     * @param platforms 想搜哪几个平台，默认四个全搜
     */
    suspend fun search(
        keyword: String,
        platforms: List<String> = PLATFORMS,
        limit: Int = 20
    ): List<PlatformSong> = coroutineScope {
        val kw = keyword.trim()
        if (kw.isEmpty()) return@coroutineScope emptyList()
        val safeLimit = limit.coerceIn(1, 30)
        val targets = platforms.map { it.lowercase() }.filter { isPlatform(it) }.ifEmpty { PLATFORMS }
        targets.map { platform ->
                async(Dispatchers.IO) {
                    runCatching { searchOne(platform, kw, safeLimit) }
                        .onFailure { AppLogger.w(TAG, "$platform 搜索失败: ${it.message}") }
                        .getOrDefault(emptyList())
                }
            }
            .awaitAll()
            .flatten()
    }

    private suspend fun searchOne(platform: String, keyword: String, limit: Int): List<PlatformSong> =
        withContext(Dispatchers.IO) {
            when (platform) {
                "wy" -> searchWy(keyword, limit)
                "tx" -> searchTx(keyword, limit)
                "kw" -> searchKw(keyword, limit)
                "kg" -> searchKg(keyword, limit)
                else -> emptyList()
            }
        }

    // ------------------------------------------------------------------ 网易云

    private fun searchWy(keyword: String, limit: Int): List<PlatformSong> {
        val url =
            "https://music.163.com/api/search/get?s=${encode(keyword)}" +
                "&type=1&limit=$limit&offset=0"
        val text = httpText(url, mapOf("Referer" to "https://music.163.com/"))
        val songs = JSONObject(text).optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        val out = ArrayList<PlatformSong>(songs.length())
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val id = song.optString("id")
            if (id.isBlank()) continue
            val artists =
                song.optJSONArray("artists")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                        .filter { it.isNotBlank() }
                } ?: emptyList()
            out.add(
                PlatformSong(
                    platform = "wy",
                    musicId = id,
                    title = song.optString("name"),
                    artist = artists.joinToString("/"),
                    album = song.optJSONObject("album")?.optString("name").orEmpty(),
                    // 网易这个接口的 duration 是毫秒
                    durationMs = song.optLong("duration", 0L)
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------------ QQ 音乐

    private fun searchTx(keyword: String, limit: Int): List<PlatformSong> {
        val url =
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=${encode(keyword)}" +
                "&format=json&n=$limit&p=1&t=0"
        val text = httpText(url, mapOf("Referer" to "https://y.qq.com/"))
        val list =
            JSONObject(text).optJSONObject("data")?.optJSONObject("song")?.optJSONArray("list")
                ?: return emptyList()
        val out = ArrayList<PlatformSong>(list.length())
        for (i in 0 until list.length()) {
            val song = list.optJSONObject(i) ?: continue
            // QQ 用的不是数字 id，是 songmid；拿错了换不到直链
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
                            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") }
                                .filter { it.isNotBlank() }
                                .joinToString("/")
                        }.orEmpty(),
                    album = song.optString("albumname"),
                    // interval 单位是秒
                    durationMs = song.optLong("interval", 0L) * 1000L,
                    artworkUrl =
                        albumMid.takeIf { it.isNotBlank() }?.let {
                            "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg"
                        }
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------------ 酷我

    private fun searchKw(keyword: String, limit: Int): List<PlatformSong> {
        val url =
            "http://search.kuwo.cn/r.s?all=${encode(keyword)}" +
                "&ft=music&itemset=web_2013&client=kt&pn=0&rn=$limit&rformat=json&encoding=utf8"
        val text = httpText(url)
        val json = parseKwJson(text) ?: return emptyList()
        val list = json.optJSONArray("abslist") ?: return emptyList()
        val out = ArrayList<PlatformSong>(list.length())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            // MUSICRID 形如 MUSIC_51685512，要剥掉前缀只留数字
            val rid =
                (item.optString("MUSICRID").ifBlank { item.optString("DC_TARGETID") })
                    .replace(Regex("^MUSIC_", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("[^0-9A-Za-z]"), "")
            if (rid.isBlank()) continue
            val pic = item.optString("web_albumpic_short")
            out.add(
                PlatformSong(
                    platform = "kw",
                    musicId = rid,
                    title = kwText(item.optString("SONGNAME")),
                    artist = kwText(item.optString("ARTIST")),
                    album = kwText(item.optString("ALBUM")),
                    durationMs = item.optLong("DURATION", 0L) * 1000L,
                    artworkUrl =
                        pic.takeIf { it.isNotBlank() }?.let { "https://img2.kuwo.cn/star/albumcover/300/$it" }
                )
            )
        }
        return out
    }

    /** 酷我返回的是**单引号 JSON**，还可能带前导 `var result =`，得先修一遍 */
    private fun parseKwJson(text: String): JSONObject? {
        val trimmed = text.trim()
        val start = trimmed.indexOf('{')
        if (start < 0) return null
        val body = if (start > 0) trimmed.substring(start) else trimmed
        runCatching { return JSONObject(body) }
        // 单引号 → 双引号是暴力但有效的兜底（酷我偶尔整段用单引号）
        return runCatching { JSONObject(body.replace('\'', '"')) }.getOrNull()
    }

    /**
     * 酷我字段有多重转义（unicode 转义序列、`\/`），要循环解到收敛，最后还要清掉残留反斜杠，
     * 否则歌名是乱码甚至带一堆 `\`。
     *
     * 注意：这里**不能**在注释里写反斜杠加 u 加四位十六进制的字面量 —— kapt 生成 Java 桩
     * 时会把它当成 Java 的 unicode 转义来解析，直接报「非法的 Unicode 逃逸」，整包编不过。
     */
    private fun kwText(value: String?): String {
        var s =
            (value ?: "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
        val unicode = Regex("\\\\u([0-9a-fA-F]{4})")
        repeat(6) {
            val before = s
            s = unicode.replace(s) { m -> m.groupValues[1].toInt(16).toChar().toString() }
            s = s.replace("\\/", "/")
            if (s == before) return@repeat
        }
        return s.replace("\\", "").trim()
    }

    // ------------------------------------------------------------------ 酷狗

    private fun searchKg(keyword: String, limit: Int): List<PlatformSong> {
        val url =
            "http://mobilecdn.kugou.com/api/v3/search/song?keyword=${encode(keyword)}" +
                "&page=1&pagesize=$limit&showtype=1"
        val text = httpText(url, mapOf("Referer" to "http://m.kugou.com/"))
        val list = JSONObject(text).optJSONObject("data")?.optJSONArray("info") ?: return emptyList()
        val out = ArrayList<PlatformSong>(list.length())
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            // 酷狗用 32 位 hash，不是数字 id
            val hash = item.optString("hash")
            if (hash.isBlank()) continue
            out.add(
                PlatformSong(
                    platform = "kg",
                    musicId = hash,
                    title = item.optString("songname"),
                    artist = item.optString("singername"),
                    album = item.optString("album_name"),
                    durationMs = item.optLong("duration", 0L) * 1000L
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------------ HTTP

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    /**
     * 同步 GET 一段文本。
     *
     * `internal` 是因为 [PlatformLyric] 要走同一套 HTTP 参数（UA / gzip / 超时 / 上限），
     * 而且**这个调用只能发生在 IO 线程**（ExoPlayer 加载线程也是 IO 线程）。
     */
    internal fun httpText(url: String, headers: Map<String, String> = emptyMap()): String {
        var conn: HttpURLConnection? = null
        try {
            conn =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                    useCaches = false
                    // 不带 UA 会被某些接口直接 403
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept-Encoding", "gzip")
                    headers.forEach { (key, value) -> setRequestProperty(key, value) }
                }
            val code = conn.responseCode
            val stream =
                runCatching { if (code in 200..299) conn.inputStream else conn.errorStream }
                    .getOrNull() ?: error("HTTP $code")
            val bytes =
                stream.use { input ->
                    val source =
                        if (conn.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                            runCatching { GZIPInputStream(input) }.getOrDefault(input)
                        } else {
                            input
                        }
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        total += read
                        // 搜索接口不该超过这个量，防一手异常响应把内存吃光
                        if (total > 4 * 1024 * 1024) break
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            return String(bytes, Charsets.UTF_8)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
