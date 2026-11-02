package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import org.json.JSONObject

/**
 * 主题包：一套可整体套用的视觉方案。
 *
 * 设计取舍：
 * 1. 采用「增量覆盖」而不是「整份替换」——套用时以当前配置为底，只改配色/明暗/背景，
 *    不动字体相关项。这样标题要求（字体与主题解耦）成立：换主题不会把用户挑的字体冲掉。
 * 2. 颜色以 ARGB Int 存储，跟 ThemePreferenceValues 里的 int 参数一一对应，不需要额外转换层。
 * 3. 序列化成 JSON，导出的文件用户能用记事本打开改数字，也方便分享。
 */
data class ThemePack(
        val id: String,
        val name: String,
        val description: String = "",
        val dark: Boolean = false,
        val primaryColor: Int,
        val secondaryColor: Int,
        /**
         * 「跟随壁纸」= 使用系统的莫奈取色（Android 12+ 从壁纸提色）。
         * 这条不是一套固定配色，而是把控制权交回系统，所以不写自定义色。
         */
        val followWallpaper: Boolean = false,
        val strings: Map<String, String> = emptyMap(),
        val booleans: Map<String, Boolean> = emptyMap(),
        val ints: Map<String, Int> = emptyMap(),
        val floats: Map<String, Float> = emptyMap()
) {

    /**
     * 把本主题套到 [base] 上，返回新配置。字体相关键刻意不覆盖。
     */
    fun applyTo(base: ThemePreferenceValues): ThemePreferenceValues {
        var values = base
        if (followWallpaper) {
            // 交回系统：跟随系统明暗 + 不做自定义着色，Theme.kt 会走 dynamicXxxColorScheme
            values = values.withBoolean("use_system_theme", true)
            values = values.withBoolean("use_custom_colors", false)
            values = values.withBoolean("use_background_image", false)
        } else {
            values = values.withBoolean("use_system_theme", false)
            values =
                    values.withString(
                            "theme_mode",
                            if (dark) UserPreferencesManager.THEME_MODE_DARK
                            else UserPreferencesManager.THEME_MODE_LIGHT
                    )
            values = values.withBoolean("use_custom_colors", true)
            values = values.withInt("custom_primary_color", primaryColor)
            values = values.withInt("custom_secondary_color", secondaryColor)
            // 主题包自带的是一套配色氛围，先关掉用户自选背景图，避免图文不搭
            values = values.withBoolean("use_background_image", false)
        }
        strings.forEach { (key, value) -> values = values.withString(key, value) }
        booleans.forEach { (key, value) -> values = values.withBoolean(key, value) }
        ints.forEach { (key, value) -> values = values.withInt(key, value) }
        floats.forEach { (key, value) -> values = values.withFloat(key, value) }
        return values
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put("format", "revokit-theme")
        root.put("version", FORMAT_VERSION)
        root.put("id", id)
        root.put("name", name)
        root.put("description", description)
        root.put("dark", dark)
        root.put("followWallpaper", followWallpaper)
        root.put("primary", primaryColor)
        root.put("secondary", secondaryColor)
        root.put("primaryHex", hexOf(primaryColor))
        root.put("secondaryHex", hexOf(secondaryColor))
        root.put("strings", JSONObject(strings as Map<*, *>))
        root.put("booleans", JSONObject(booleans as Map<*, *>))
        root.put("ints", JSONObject(ints as Map<*, *>))
        root.put("floats", JSONObject(floats as Map<*, *>))
        return root.toString(2)
    }

    companion object {
        const val FORMAT_VERSION = 1

        fun hexOf(color: Int): String = String.format(java.util.Locale.ROOT, "#%06X", color and 0xFFFFFF)

        /** 由当前配置导出一份主题包（用于「导出」）。 */
        fun fromValues(id: String, name: String, values: ThemePreferenceValues): ThemePack {
            val dark =
                    values.string("theme_mode") == UserPreferencesManager.THEME_MODE_DARK
            return ThemePack(
                    id = id,
                    name = name,
                    description = "导出于本机",
                    dark = dark,
                    primaryColor = values.int("custom_primary_color") ?: DEFAULT_PRIMARY,
                    secondaryColor = values.int("custom_secondary_color") ?: DEFAULT_SECONDARY,
                    followWallpaper = values.boolean("use_custom_colors") != true,
                    strings = values.strings,
                    booleans = values.booleans,
                    ints = values.ints,
                    floats = values.floats
            )
        }

        /** 解析导入的主题文件，格式不对返回 null。 */
        fun fromJson(raw: String): ThemePack? {
            return runCatching {
                        val root = JSONObject(raw)
                        val name = root.optString("name").takeIf { it.isNotBlank() } ?: return null
                        ThemePack(
                                id = root.optString("id").ifBlank { "imported_" + name.hashCode() },
                                name = name,
                                description = root.optString("description"),
                                dark = root.optBoolean("dark", false),
                                followWallpaper = root.optBoolean("followWallpaper", false),
                                primaryColor = root.optInt("primary", DEFAULT_PRIMARY),
                                secondaryColor = root.optInt("secondary", DEFAULT_SECONDARY),
                                strings = readMap(root.optJSONObject("strings")) { it.toString() },
                                booleans =
                                        readMap(root.optJSONObject("booleans")) {
                                            (it as? Boolean) ?: (it.toString().toBoolean())
                                        },
                                ints =
                                        readMap(root.optJSONObject("ints")) {
                                            (it as? Number)?.toInt() ?: 0
                                        },
                                floats =
                                        readMap(root.optJSONObject("floats")) {
                                            (it as? Number)?.toFloat() ?: 0f
                                        }
                        )
                    }
                    .getOrNull()
        }

        private fun <T> readMap(source: JSONObject?, convert: (Any) -> T): Map<String, T> {
            if (source == null) return emptyMap()
            val out = LinkedHashMap<String, T>()
            source.keys().forEach { key ->
                out[key] = convert(source.get(key))
            }
            return out
        }

        // 青竹（#4E8C72 / #6FA88F）的 ARGB 字面量，必须是编译期常量
        private const val DEFAULT_PRIMARY = -11629454
        private const val DEFAULT_SECONDARY = -9459569
    }
}
