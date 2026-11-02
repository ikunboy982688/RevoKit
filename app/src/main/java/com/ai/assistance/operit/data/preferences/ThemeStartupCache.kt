package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONObject

/**
 * RevoKit：主题「启动预热」缓存。
 *
 * 背景：主题值存在 DataStore 里，读取是异步的。冷启动时 `OperitTheme` 的第一帧拿不到值，
 * 只能退回 `ThemePreferenceValues.defaultVisual()`，于是会**闪一下默认主题**，等 DataStore 的
 * 第一个 emission 到了才切回用户自己的主题。
 *
 * 做法：每当主题快照从 DataStore 里流出来，就顺手把这份值**同步**写进 SharedPreferences
 * （几十 KB 的小 XML，`apply()` 不阻塞）。下次冷启动时 `rememberActiveThemePreferenceSnapshot()`
 * 直接拿缓存值当 `collectAsState` 的 initial —— 第一帧就是用户真实主题，闪屏消失。
 *
 * 注意：缓存是「上一次实际生效的主题」，不参与任何写入决策，纯展示用途。
 * 万一缓存损坏 / 版本不符，返回 null，退回原来的默认值逻辑，不影响功能。
 */
object ThemeStartupCache {

    private const val TAG = "RevoKitThemeCache"
    private const val PREFS_NAME = "revokit_theme_prewarm"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_VERSION = "version"
    private const val KEY_SOURCE = "source"
    private const val KEY_SOURCE_ID = "sourceId"
    private const val KEY_STRINGS = "strings"
    private const val KEY_BOOLEANS = "booleans"
    private const val KEY_INTS = "ints"
    private const val KEY_FLOATS = "floats"
    private const val VERSION = 1

    /** 由 `observeThemePreferenceSnapshot` 每次 emit 时调用，把值落盘备用。 */
    fun store(context: Context, snapshot: ThemePreferenceSnapshot) {
        runCatching {
                val json =
                        JSONObject().apply {
                            put(KEY_VERSION, VERSION)
                            put(KEY_SOURCE, snapshot.source)
                            put(KEY_SOURCE_ID, snapshot.sourceId)
                            put(KEY_STRINGS, toJson(snapshot.values.strings))
                            put(KEY_BOOLEANS, toJson(snapshot.values.booleans))
                            put(KEY_INTS, toJson(snapshot.values.ints))
                            put(KEY_FLOATS, toJson(snapshot.values.floats))
                        }
                prefs(context).edit().putString(KEY_PAYLOAD, json.toString()).apply()
            }
            .onFailure { AppLogger.w(TAG, "主题预热缓存写入失败：${it.message}") }
    }

    /** 冷启动第一帧调用，**同步**返回上次生效的主题快照；没有缓存时返回 null。 */
    fun load(context: Context): ThemePreferenceSnapshot? {
        return runCatching {
                val raw = prefs(context).getString(KEY_PAYLOAD, null) ?: return null
                val json = JSONObject(raw)
                if (json.optInt(KEY_VERSION, 0) != VERSION) return null
                val source = json.optString(KEY_SOURCE, "").takeIf { it.isNotBlank() } ?: return null
                val sourceId =
                        json.optString(KEY_SOURCE_ID, "").takeIf { it.isNotBlank() } ?: return null
                ThemePreferenceSnapshot(
                        source = source,
                        sourceId = sourceId,
                        values =
                                ThemePreferenceValues(
                                        strings =
                                                stringMap(json.optJSONObject(KEY_STRINGS)),
                                        booleans =
                                                booleanMap(json.optJSONObject(KEY_BOOLEANS)),
                                        ints = intMap(json.optJSONObject(KEY_INTS)),
                                        floats = floatMap(json.optJSONObject(KEY_FLOATS)),
                                ),
                )
            }
            .onFailure { AppLogger.w(TAG, "主题预热缓存读取失败：${it.message}") }
            .getOrNull()
    }

    private fun prefs(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun toJson(map: Map<String, *>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) -> json.put(key, value) }
        return json
    }

    private fun keysOf(json: JSONObject?): List<String> {
        if (json == null) return emptyList()
        val out = ArrayList<String>(json.length())
        json.keys().forEach { out.add(it) }
        return out
    }

    private fun stringMap(json: JSONObject?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        keysOf(json).forEach { key -> out[key] = json!!.optString(key) }
        return out
    }

    private fun booleanMap(json: JSONObject?): Map<String, Boolean> {
        val out = LinkedHashMap<String, Boolean>()
        keysOf(json).forEach { key -> out[key] = json!!.optBoolean(key) }
        return out
    }

    private fun intMap(json: JSONObject?): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        keysOf(json).forEach { key -> out[key] = json!!.optInt(key) }
        return out
    }

    private fun floatMap(json: JSONObject?): Map<String, Float> {
        val out = LinkedHashMap<String, Float>()
        keysOf(json).forEach { key -> out[key] = json!!.optDouble(key).toFloat() }
        return out
    }
}
