package com.ai.assistance.operit.core.music

import android.content.Context
import com.ai.assistance.operit.core.music.online.OnlineSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏仓库：侧栏音乐页和悬浮窗共用同一份数据。
 *
 * **key 规则**：
 * - 本地曲目：MediaStore id 的字符串，如 `"1024"`（和历史存的数据兼容）。
 *   收藏列表对本地歌是**从曲库里过筛**出来的，所以不需要另外存歌名。
 * - 在线曲目：`"online:平台:歌曲id"`，如 `"online:wy:186016"`。
 *   在线歌**不在曲库里**，光记住 key 是列不出歌名的，必须把整条歌的信息存下来 ——
 *   这就是下面 `entries` 这份 JSON 的由来。
 *
 * 用 StateFlow 暴露，两个界面同时开关收藏会立刻同步，不用互相通知。
 */
object MusicFavorites {

    private const val PREFS = "revokit_music"
    private const val KEY = "favorites"
    /** 在线收藏的详情表。和 [KEY] 分开存，是为了不破坏老版本只存 key 集合的数据结构 */
    private const val KEY_ONLINE = "favorites_online"
    private const val ONLINE_PREFIX = "online:"

    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    /** 在线收藏的详情（key → 歌曲），列表页靠它把歌名列出来 */
    private val _entries = MutableStateFlow<Map<String, OnlineSong>>(emptyMap())
    val entries: StateFlow<Map<String, OnlineSong>> = _entries.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _keys.value = store.getStringSet(KEY, emptySet()).orEmpty().toSet()
        _entries.value = readEntries(store)
    }

    fun isFavorite(key: String?): Boolean = key != null && _keys.value.contains(key)

    /**
     * 收藏 / 取消收藏。只认 key，[entries] 里没有详情的就当本地曲目处理。
     *
     * 注意在线曲目请用 [toggle] 也能生效（key 已在集合里），但**写入详情**必须走
     * [toggleOnline]，否则收藏列表里会出现一条没有名字的歌。
     */
    fun toggle(key: String): Boolean {
        val current = _keys.value
        val added = !current.contains(key)
        val next = if (added) current + key else current - key
        _keys.value = next
        prefs?.edit()?.putStringSet(KEY, next)?.apply()
        if (!added) {
            // 顺手把详情也清掉，避免取消收藏后详情表越攒越大
            if (_entries.value.containsKey(key)) {
                writeEntries(_entries.value - key)
            }
        }
        return added
    }

    /** 在线曲目的收藏 key */
    fun onlineKey(platform: String, songId: String): String =
        "$ONLINE_PREFIX${platform.trim().lowercase()}:${songId.trim()}"

    fun isOnlineKey(key: String?): Boolean = key?.startsWith(ONLINE_PREFIX) == true

    /** 收藏 / 取消收藏一首在线歌，同时维护 key 集合和详情表 */
    fun toggleOnline(song: OnlineSong): Boolean {
        val key = onlineKey(song.platform, song.songId)
        val current = _keys.value
        val added = !current.contains(key)
        val next = if (added) current + key else current - key
        _keys.value = next
        val nextEntries =
            if (added) {
                _entries.value + (key to song)
            } else {
                _entries.value - key
            }
        _entries.value = nextEntries
        prefs?.edit()?.putStringSet(KEY, next)?.apply()
        writeEntries(nextEntries)
        return added
    }

    /** 收藏列表按收藏顺序展示（JSONObject 不保序，所以顺序单独记在数组里） */
    fun onlineSongs(): List<OnlineSong> {
        val map = _entries.value
        return order().mapNotNull { map[it] }
    }

    private var keyOrder: List<String> = emptyList()

    private fun order(): List<String> {
        if (keyOrder.isEmpty()) keyOrder = _entries.value.keys.toList()
        // 兜底：万一顺序表丢了（老数据升级），直接用详情表的顺序
        return keyOrder.filter { _entries.value.containsKey(it) } +
            _entries.value.keys.filterNot { keyOrder.contains(it) }
    }

    // ------------------------------------------------------------------ 持久化

    private fun readEntries(store: android.content.SharedPreferences): Map<String, OnlineSong> {
        val raw = store.getString(KEY_ONLINE, null) ?: return emptyMap()
        val out = linkedMapOf<String, OnlineSong>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val key = o.optString("key")
                if (key.isBlank()) continue
                out[key] =
                    OnlineSong(
                        sourceId = o.optString("sourceId"),
                        songId = o.optString("songId"),
                        platform = o.optString("platform"),
                        title = o.optString("title"),
                        artist = o.optString("artist"),
                        album = o.optString("album").takeIf { it.isNotBlank() },
                        durationMs = o.optLong("durationMs", 0L),
                        artworkUrl = o.optString("artworkUrl").takeIf { it.isNotBlank() },
                        rawJson = o.optString("rawJson", "{}")
                    )
            }
        }
        keyOrder = out.keys.toList()
        return out
    }

    private fun writeEntries(map: Map<String, OnlineSong>) {
        keyOrder = map.keys.toList()
        val arr = JSONArray()
        map.forEach { (key, song) ->
            arr.put(
                JSONObject().apply {
                    put("key", key)
                    put("sourceId", song.sourceId)
                    put("songId", song.songId)
                    put("platform", song.platform)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album.orEmpty())
                    put("durationMs", song.durationMs)
                    put("artworkUrl", song.artworkUrl.orEmpty())
                    put("rawJson", song.rawJson)
                }
            )
        }
        prefs?.edit()?.putString(KEY_ONLINE, arr.toString())?.apply()
    }
}
