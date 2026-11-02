package com.ai.assistance.operit.core.music.online

import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 应用侧直连取播放地址。
 *
 * **为什么连播放地址也要应用自己做**：2026-10-31 晚上真机复现 —— 点一首在线歌，
 * 日志里只有一串 `音源 HTTP 请求失败: timeout`，等了 90 秒才等到第一条
 * `星海音乐源 解析失败: 超过 14000ms 没出结果`，最后所有音源全军覆没、用户听不到声音。
 * 原因是这批洛雪音源走的都是**第三方聚合后端**，这些后端现在大面积超时/失效，
 * 而脚本内部又是串行重试好几个后端，一首歌能拖一分多钟。
 *
 * 所以继「搜索」「歌词」之后，**取播放地址也改成应用直连公开接口**，音源脚本退居兜底。
 * 好处很直接：直连是单次请求、几秒内出结果，成功就直接播，不用再陪着音源干等。
 *
 * 实测（2026-10-31）：
 * - 网易 `api/song/enhance/player/url`：**免费歌曲正常返回 mp3 直链**（实测 HTTP 200、
 *   `Content-Type: audio/mpeg`）；付费/无版权的回 `code=-110`、url 为 null，拿不到就是拿不到。
 * - 酷我 `antiserver.kuwo.cn/anti.s`：**稳定可用**，返回一个 302，Location 里就是 mp3 直链。
 * - QQ `u.y.qq.com/cgi-bin/musicu.fcg`：接口能通，但实测 `purl` 恒为空（拿不到 vkey），
 *   所以写成「尽力而为」，拿不到就退回音源脚本。
 * - 酷狗：要 hash 换 fileHash 那一套 + 签名，不实现。
 */
object PlatformPlayUrl {

    private const val TAG = "PlatformPlayUrl"

    /** 取链结果。多带两个字段是为了识别「试听片段」，见 [findComplete] */
    data class Direct(
        val url: String,
        /** 服务端给的字节数，0 = 没给 */
        val byteSize: Long = 0L,
        /** true = 疑似试听片段（几十秒），不是完整歌 */
        val trial: Boolean = false
    )

    /**
     * 取播放地址，拿到返回直链；拿不到返回 null（调用方会退回音源脚本）。
     *
     * @param platform wy / tx / kw / kg
     * @param musicId 该平台的歌曲 ID（语义同 [PlatformSearch]）
     * @param quality `128k` / `320k` / `flac` / `flac24bit`
     */
    suspend fun load(platform: String, musicId: String, quality: String): Direct? =
        withContext(Dispatchers.IO) {
            if (musicId.isBlank()) return@withContext null
            runCatching {
                    when (platform.lowercase()) {
                        "wy" -> loadWy(musicId, quality)
                        "kw" -> loadKw(musicId, quality)?.let { Direct(it) }
                        "tx" -> loadTx(musicId, quality)?.let { Direct(it) }
                        else -> null
                    }
                }
                .onFailure { AppLogger.w(TAG, "$platform 直连取链失败: ${it.message}") }
                .getOrNull()
                ?.takeIf { it.url.startsWith("http", ignoreCase = true) }
        }

    /**
     * 用「歌名 + 歌手」去酷我找一首同名歌，拿它的完整直链。
     *
     * **为什么需要这个**：网易对 VIP / 无版权歌曲只给**几十秒的试听片段**（用户反馈
     * 「点开是挺流畅的，关键就只有几十秒试听」）。酷我没有这层限制，实测返回的是完整
     * mp3（3~4 MB）。所以拿网易试听时，转头去酷我搜同名歌补一个完整版 ——
     * 宁可换一家平台，也比听 60 秒强。
     */
    suspend fun findComplete(title: String, artist: String?, quality: String): String? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val keyword = if (artist.isNullOrBlank()) title else "$title $artist"
            val hits =
                runCatching { PlatformSearch.search(keyword, listOf("kw"), 8) }
                    .onFailure { AppLogger.w(TAG, "酷我补链搜索失败: ${it.message}") }
                    .getOrDefault(emptyList())
            // **宁可听试听，也不放错歌**。
            // 第一版这里只判「标题包含歌名」就直接用，结果真机上把 Keyshia Cole 的
            // 《Love》换成了 Paul Mauriat 的同名曲 —— 用户听到完全不是自己点的那首，
            // 比试听还糟糕。所以改成打分：标题完全对上 4 分、互相包含 2 分，
            // 歌手对得上再 +3 分；只有 ≥4 分（也就是标题必须真的对得上）才用。
            val want = title.trim().lowercase()
            val wantArtist = artist?.trim()?.lowercase().orEmpty()
            fun score(hit: PlatformSong): Int {
                val t = hit.title.trim().lowercase()
                var s =
                    if (t == want) 4
                    else if (t.contains(want) || want.contains(t)) 2
                    else 0
                val ha = hit.artist?.trim()?.lowercase().orEmpty()
                if (wantArtist.isNotBlank() && ha.isNotBlank() &&
                    (ha.contains(wantArtist) || wantArtist.contains(ha))
                ) {
                    s += 3
                }
                return s
            }
            val best =
                hits.map { it to score(it) }
                    .filter { it.second >= 4 }
                    .maxByOrNull { it.second }
                    ?.first
            if (best == null) {
                AppLogger.w(TAG, "酷我没找到《$title》的同名同歌手版本，放弃补链")
                return@withContext null
            }
            val direct = load("kw", best.musicId, quality)
            if (direct?.url.isNullOrBlank()) return@withContext null
            AppLogger.d(TAG, "《$title》用酷我《${best.title}》补上完整版")
            direct?.url
        }

    /**
     * 探一下一个直链到底有多少字节（HEAD 一下读 Content-Length）。
     *
     * **为什么需要这个**：音源脚本换回来的地址质量参差不齐 —— 实测同一首《晴天》，
     * fish_music 给的是 10.3MB 完整版，另外两个源给的却只有 0.9MB / 0.08MB（几十秒
     * 甚至几秒的残片）。脚本自己都说自己是「完整版」，光看返回值分不出来，
     * 只有真的量一下才知道。所以拿到地址先量，太小的直接判不合格、换下一个源。
     *
     * 拿不到 Content-Length（有的 CDN 不给 HEAD 回长度）返回 -1，调用方按「不知道」处理，
     * 也就是照用不误 —— 宁可放过，也别因为探不到就把好地址丢了。
     */
    suspend fun probeBytes(url: String, timeoutMs: Int = 2_500): Long =
        withContext(Dispatchers.IO) {
            runCatching {
                    val conn =
                        (URL(url).openConnection() as HttpURLConnection).apply {
                            requestMethod = "HEAD"
                            connectTimeout = timeoutMs
                            readTimeout = timeoutMs
                            instanceFollowRedirects = true
                            useCaches = false
                            setRequestProperty("User-Agent", UA)
                        }
                    conn.responseCode
                    val len =
                        conn.getHeaderField("Content-Length")?.trim()?.toLongOrNull() ?: -1L
                    conn.disconnect()
                    len
                }
                .onFailure { AppLogger.w(TAG, "探测字节数失败: ${it.message}") }
                .getOrDefault(-1L)
        }

    /**
     * 试听判定阈值。60 秒的片段在 320k 下约 2.4MB、128k 下约 1MB；
     * 完整歌怎么也有 3MB 起。取 2MB 作分界，保守一点别把短歌误杀。
     */
    const val TRIAL_MAX_BYTES = 2_000_000L

    // ------------------------------------------------------------------ 网易云
    // https://music.163.com/api/song/enhance/player/url?ids=[<id>]&br=320000
    // → { data: [ { url, code, fee, br } ] }
    // code 不是 200 就说明这首歌要 VIP 或没版权，url 会是 null，别硬接着用。

    private fun loadWy(musicId: String, quality: String): Direct? {
        val br =
            when {
                quality.startsWith("128") -> 128000
                quality.startsWith("flac") -> 999000
                else -> 320000
            }
        val text =
            get(
                "https://music.163.com/api/song/enhance/player/url?csrf_token=" +
                    "&ids=%5B$musicId%5D&br=$br",
                mapOf("Referer" to "https://music.163.com/")
            )
                .body
        val first = JSONObject(text).optJSONArray("data")?.optJSONObject(0) ?: return null
        val url = first.optString("url")
        if (url.isBlank()) {
            // -110 = 付费 / 无版权；404 = 已下架。把这个原因带出去，比干巴巴的「解析失败」有用
            throw IllegalStateException("网易返回 code=${first.optInt("code")}（要 VIP 或没版权）")
        }
        // 接口给的是 http 直链，统一升级成 https —— 实测同源可用，还能避开明文流量限制
        val safe =
            if (url.startsWith("http://", ignoreCase = true)) {
                "https://" + url.removePrefix("http://")
            } else {
                url
            }
        // 未登录时 VIP 歌给的是几十秒试听片段，体积会明显偏小。记下来让上层好决定
        // 要不要去别家补一个完整版。
        val size = first.optLong("size", 0L)
        val trial = size > 0L && size < TRIAL_MAX_BYTES
        if (trial) AppLogger.w(TAG, "网易这首只有 ${size / 1024}KB，疑似试听片段")
        return Direct(safe, size, trial)
    }

    // ------------------------------------------------------------------ 酷我
    // http://antiserver.kuwo.cn/anti.s?type=convert_url&rid=<rid>&format=mp3&response=res
    // → 302，Location 是直链。**必须关掉自动跟随重定向**，否则 HttpURLConnection 会把
    // 整首 mp3 当成响应体下载下来（十几 MB，白等半天）。

    private fun loadKw(musicId: String, quality: String): String? {
        val format = if (quality.startsWith("flac")) "flac" else "mp3"
        val resp =
            get(
                "http://antiserver.kuwo.cn/anti.s?type=convert_url&rid=$musicId" +
                    "&format=$format&response=res",
                mapOf("Referer" to "http://www.kuwo.cn/"),
                followRedirects = false
            )
        val location = resp.location
        if (!location.isNullOrBlank() && location.startsWith("http", ignoreCase = true)) {
            return location
        }
        // 兜底：有些环境不给 Location，只给一段 302 的 HTML，里面挂着真实地址
        val found = Regex("""https?://[^"'\s<>]+\.(?:mp3|flac|m4a|aac|wma)[^"'\s<>]*""")
            .find(resp.body)
            ?.value
        if (found != null) return found
        throw IllegalStateException("酷我没返回播放地址（rid=$musicId）")
    }

    // ------------------------------------------------------------------ QQ 音乐
    // POST https://u.y.qq.com/cgi-bin/musicu.fcg
    // → req_0.data.midurlinfo[0].purl + sip[0] 拼起来就是直链
    // 实测 purl 经常是空的（拿不到 vkey），所以这里失败是常态，不抛大错，安静返回 null。

    private fun loadTx(musicId: String, quality: String): String? {
        val guid = "9876543210"
        val payload =
            JSONObject()
                .put(
                    "req_0",
                    JSONObject()
                        .put("module", "vkey.GetVkeyServer")
                        .put("method", "CgiGetVkey")
                        .put(
                            "param",
                            JSONObject()
                                .put("guid", guid)
                                .put("songmid", org.json.JSONArray().put(musicId))
                                .put("songtype", org.json.JSONArray().put(0))
                                .put("uin", "0")
                                .put("loginflag", 1)
                                .put("platform", "20")
                        )
                )
                .put("comm", JSONObject().put("uin", 0).put("format", "json").put("ct", 24).put("cv", 0))
                .toString()
        val text =
            postJson(
                "https://u.y.qq.com/cgi-bin/musicu.fcg?_webcgikey=GetVkey",
                payload,
                mapOf("Referer" to "https://y.qq.com/")
            )
        val data = JSONObject(text).optJSONObject("req_0")?.optJSONObject("data") ?: return null
        val purl = data.optJSONArray("midurlinfo")?.optJSONObject(0)?.optString("purl")
        if (purl.isNullOrBlank()) return null
        val sip = data.optJSONArray("sip")?.optString(0).orEmpty()
        val prefix = if (sip.startsWith("http", ignoreCase = true)) sip else "https://$sip"
        return prefix + purl
    }

    // ------------------------------------------------------------------ HTTP 小工具
    // 单独写一份而不是复用 PlatformSearch.httpText，是因为取链有两个特殊要求：
    //   1. 要能**关掉自动跟随重定向**（酷我那个 302 不能跟）
    //   2. 要能 POST JSON（QQ 那个接口只收 POST）

    private class Resp(val body: String, val location: String?)

    private fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        followRedirects: Boolean = true
    ): Resp {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = followRedirects
                useCaches = false
                setRequestProperty("User-Agent", UA)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
        try {
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            val stream =
                runCatching { if (code in 200..399) conn.inputStream else conn.errorStream }
                    .getOrNull()
            val body = stream?.use { readAll(it, conn.contentEncoding) }.orEmpty()
            return Resp(body, location)
        } finally {
            conn.disconnect()
        }
    }

    private fun postJson(url: String, json: String, headers: Map<String, String>): String {
        val conn =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                useCaches = false
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
        try {
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream =
                runCatching { if (code in 200..299) conn.inputStream else conn.errorStream }
                    .getOrNull()
            return stream?.use { readAll(it, conn.contentEncoding) }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun readAll(input: InputStream, encoding: String?): String {
        val source =
            if (encoding?.contains("gzip", ignoreCase = true) == true) {
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
            // 取链接口正常响应只有几百字节，给个上限防异常响应把内存吃光
            if (total > 256 * 1024) break
            out.write(buffer, 0, read)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
}
