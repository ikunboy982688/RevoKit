package com.ai.assistance.operit.core.music.online

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 音源脚本的方言。两家协议完全不互通，靠内容嗅探判断 */
enum class SourceDialect(val label: String) {
    MUSICFREE("MusicFree 插件"),
    LX("洛雪音源"),
    UNKNOWN("未知格式")
}

/** 已导入的一个音源脚本 */
data class OnlineSource(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val dialect: SourceDialect,
    val fileName: String,
    val enabled: Boolean,
    /** MusicFree 插件的平台名（kw/tx/wy…），洛雪源无所谓 */
    val platform: String = "",
    /** 加载时探测到的能力，用来在 UI 上说清楚这个源能干什么 */
    val canSearch: Boolean = false,
    val canResolveUrl: Boolean = true,
    val canLyric: Boolean = false
)

/** 一条在线搜索结果 */
data class OnlineSong(
    val sourceId: String,
    val songId: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    /** 插件返回的原始条目 JSON，回传给 getMediaSource 时要原封不动带回去 */
    val rawJson: String = "{}"
) {
    /** 列表里用来去重的 key */
    val key: String get() = "$sourceId::$platform::$songId"
}

/**
 * 内置平台搜索结果 → 洛雪音源认的 `musicInfo`。
 *
 * 关键技巧（照抄实测可用的那份桥接包）：**把同一个 ID 塞进所有可能的 ID 字段**。
 * 不同音源脚本读的字段不一样 —— 网易读 `musicId`、QQ 读 `songmid`、酷狗读 `hash`、
 * 酷我读 `rid` —— 与其猜每个脚本读哪个，不如全填成同一个值，谁读都能读到。
 * 这也是「搜索用应用侧、换链接用脚本」这个分工能对上的原因。
 */
fun PlatformSong.toMusicInfoJson(): String =
    JSONObject()
        .apply {
            put("source", platform)
            put("songmid", musicId)
            put("songId", musicId)
            put("id", musicId)
            put("hash", musicId)
            put("strMediaMid", musicId)
            put("copyrightId", musicId)
            put("albumId", "")
            put("albumMid", musicId)
            put("albumName", album)
            put("name", title)
            put("singer", artist)
            put("interval", "00:00")
            put("img", artworkUrl ?: "")
        }
        .toString()

/** 内置平台搜索结果 → 统一的在线歌曲行。`sourceId` 为空表示「不是插件搜出来的」 */
fun PlatformSong.toOnlineSong(): OnlineSong =
    OnlineSong(
        sourceId = "",
        songId = musicId,
        platform = platform,
        title = title,
        artist = artist,
        album = album.takeIf { it.isNotBlank() },
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        rawJson = toMusicInfoJson()
    )

/**
 * 音源脚本仓库：脚本文件放 `filesDir/music_sources/`，元信息存 SharedPreferences。
 *
 * 之所以把脚本落成文件而不是塞进 SharedPreferences —— 单个插件动辄上百 KB，
 * 而且 QuickJS 每次加载都要读全文，文件读取更直接。
 */
object MusicSourceStore {

    private const val TAG = "MusicSourceStore"
    private const val PREFS = "revokit_music_sources"
    private const val KEY_LIST = "sources"
    private const val KEY_QUALITY = "quality"
    /** 上次成功解析某个平台的音源 id，key 是 `<平台>`；命中就能一次到位，不用逐个试 */
    private const val KEY_LAST_SOURCE = "last_source_"
    private const val DIR = "music_sources"

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun list(context: Context): List<OnlineSource> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIST, "[]") ?: "[]"
        val out = mutableListOf<OnlineSource>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    OnlineSource(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        version = o.optString("version"),
                        author = o.optString("author"),
                        dialect =
                            runCatching { SourceDialect.valueOf(o.optString("dialect")) }
                                .getOrDefault(SourceDialect.UNKNOWN),
                        fileName = o.optString("fileName"),
                        enabled = o.optBoolean("enabled", true),
                        platform = o.optString("platform"),
                        canSearch = o.optBoolean("canSearch", false),
                        canResolveUrl = o.optBoolean("canResolveUrl", true),
                        canLyric = o.optBoolean("canLyric", false)
                    )
                )
            }
        }.onFailure { AppLogger.w(TAG, "读取音源列表失败", it) }
        return out
    }

    private fun save(context: Context, sources: List<OnlineSource>) {
        val arr = JSONArray()
        sources.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("version", s.version)
                    put("author", s.author)
                    put("dialect", s.dialect.name)
                    put("fileName", s.fileName)
                    put("enabled", s.enabled)
                    put("platform", s.platform)
                    put("canSearch", s.canSearch)
                    put("canResolveUrl", s.canResolveUrl)
                    put("canLyric", s.canLyric)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, arr.toString())
            .apply()
    }

    fun readScript(context: Context, source: OnlineSource): String? {
        val f = File(dir(context), source.fileName)
        if (!f.exists()) return null
        return runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        save(context, list(context).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    /**
     * 音质偏好。两家协议的音质 key 是一致的（`128k` / `320k` / `flac` / `flac24bit`），
     * 所以一个值就能同时喂给 MusicFree 插件和洛雪音源。
     */
    fun quality(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUALITY, "320k") ?: "320k"

    fun setQuality(context: Context, quality: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUALITY, quality)
            .apply()
    }

    /**
     * 取「上次成功解析过这个平台的音源 id」。
     *
     * 这是点播速度的关键：开着七八个源的时候逐个试，前面几个一失败就是好几秒，
     * 用户会觉得「点了半天才响」。记住上次那个，第二次起基本一次命中。
     */
    fun lastSource(context: Context, platform: String?): String? {
        if (platform.isNullOrBlank()) return null
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SOURCE + platform.lowercase(), null)
            ?.takeIf { it.isNotBlank() }
    }

    fun markLastSource(context: Context, platform: String, sourceId: String) {
        if (platform.isBlank() || sourceId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SOURCE + platform.lowercase(), sourceId)
            .apply()
    }

    // ------------------------------------------------------------------ 好坏记忆
    //
    // 光记「上次用谁」不够。真机实测：同一首歌，fish_music 给 10MB 完整版，
    // 另外两个源只给 0.9MB / 0.08MB 的残片，而它们全都自称成功。如果只按「上次成功」
    // 排序，第一次点歌撞上残片源就记住了它，之后一直听残片。
    // 所以分开记：
    // - `best_source_<平台>`：最近一次真的给出**完整版**的源，排最前；
    // - `bad_sources_<平台>`：给过残片或者干脆失败的源，排最后。
    // 这样用几首歌之后，每个平台自然会收敛到那个真正好用的源上。

    private const val KEY_BEST_SOURCE = "best_source_"

    private const val KEY_BAD_SOURCES = "bad_sources_"

    /** 最近一次真的给出完整版的源 */
    fun bestSource(context: Context, platform: String?): String? {
        if (platform.isNullOrBlank()) return null
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BEST_SOURCE + platform.lowercase(), null)
            ?.takeIf { it.isNotBlank() }
    }

    fun markBestSource(context: Context, platform: String, sourceId: String) {
        if (platform.isBlank() || sourceId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BEST_SOURCE + platform.lowercase(), sourceId)
            .apply()
        // 它现在是好源了，从黑名单里摘掉
        clearBadSource(context, platform, sourceId)
    }

    /** 给过残片 / 失败的源。老记录会自然被挤掉，不会无限涨 */
    fun badSources(context: Context, platform: String?): Set<String> {
        if (platform.isNullOrBlank()) return emptySet()
        val raw =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_BAD_SOURCES + platform.lowercase(), null)
                .orEmpty()
        return raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    fun markBadSource(context: Context, platform: String, sourceId: String) {
        if (platform.isBlank() || sourceId.isBlank()) return
        val key = KEY_BAD_SOURCES + platform.lowercase()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = (badSources(context, platform) + sourceId).toMutableList()
        // 只留最近 6 个：后端是会修好的，别把人家永久拉黑
        while (next.size > 6) next.removeAt(0)
        prefs.edit().putString(key, next.joinToString(",")).apply()
    }

    fun clearBadSource(context: Context, platform: String, sourceId: String) {
        if (platform.isBlank() || sourceId.isBlank()) return
        val key = KEY_BAD_SOURCES + platform.lowercase()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = badSources(context, platform) - sourceId
        prefs.edit().putString(key, next.joinToString(",")).apply()
    }

    fun delete(context: Context, id: String) {
        val target = list(context).firstOrNull { it.id == id }
        if (target != null) {
            runCatching { File(dir(context), target.fileName).delete() }
        }
        save(context, list(context).filterNot { it.id == id })
    }

    /**
     * 导入一个 .js 音源。返回导入后的记录。
     *
     * 只做「嗅探方言 + 落盘 + 记账」，不做语法检查 —— 脚本能不能跑要等真正加载时才知道，
     * 硬要在导入时跑一遍反而会拖慢导入、还会因为网络问题误报。
     */
    suspend fun import(context: Context, uri: Uri): Result<OnlineSource> =
        withContext(Dispatchers.IO) {
            runCatching {
                val text =
                    context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("读不到文件内容")

                if (text.length < 40) error("文件太小，不像音源脚本")
                if (!looksLikeScript(text)) error("这个文件里没有可识别的音源代码")

                val dialect = sniffDialect(text)
                val name = sniffMeta(text, "name") ?: "未命名音源"
                val version = sniffMeta(text, "version") ?: ""
                val author = sniffMeta(text, "author") ?: ""
                // id 用文件名 + 长度，保证同一个脚本重复导入会覆盖而不是堆一堆
                val id = Integer.toHexString(text.hashCode())
                val fileName = "src_$id.js"

                File(dir(context), fileName).writeText(text, Charsets.UTF_8)

                val existing = list(context).firstOrNull { it.id == id }
                val record =
                    OnlineSource(
                        id = id,
                        name = name,
                        version = version,
                        author = author,
                        dialect = dialect,
                        fileName = fileName,
                        enabled = existing?.enabled ?: true,
                        canSearch = dialect == SourceDialect.MUSICFREE,
                        canResolveUrl = true,
                        canLyric = dialect == SourceDialect.MUSICFREE || dialect == SourceDialect.LX
                    )
                save(context, list(context).filterNot { it.id == id } + record)
                record
            }
        }

    private fun looksLikeScript(text: String): Boolean =
        text.contains("module.exports") ||
            text.contains("globalThis.lx") ||
            text.contains("EVENT_NAMES") ||
            text.contains("getMediaSource") ||
            text.contains("musicUrl")

    fun sniffDialect(text: String): SourceDialect =
        when {
            text.contains("globalThis.lx") || text.contains("EVENT_NAMES") -> SourceDialect.LX
            text.contains("getMediaSource") || text.contains("onSearch") ||
                (text.contains("module.exports") && text.contains("search")) ->
                SourceDialect.MUSICFREE
            text.contains("module.exports") -> SourceDialect.MUSICFREE
            else -> SourceDialect.UNKNOWN
        }

    /** 从脚本头部注释里抠 `@name` / `@version` / `@author`，抠不到就给空 */
    private fun sniffMeta(text: String, field: String): String? {
        val head = text.take(2000)
        val regex = Regex("@$field\\s+([^\\r\\n*]+)")
        return regex.find(head)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
