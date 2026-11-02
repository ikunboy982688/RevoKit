package com.ai.assistance.operit.data.theme

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.data.model.ThemePack
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * 主题包归档格式：zip 里一个 `theme.json` + 若干图片。
 *
 * 有了它，主题包不再依赖「按 QQ 文件名硬认图」——谁做的包、图叫什么名都无所谓，
 * 只要 theme.json 里写清哪个键用哪张图。没有 theme.json 的老包仍走
 * [QqThemePackImporter] 里的内置映射，向后兼容。
 *
 * `theme.json` 结构（键名与主题偏好完全一致，不需要翻译层）：
 * ```
 * {
 *   "format": "revokit-theme", "version": 1,
 *   "name": "我的主题", "description": "...", "dark": false,
 *   "primary": -9001293, "secondary": -7296824,
 *   "strings": { "chat_style": "bubble" },
 *   "booleans": { "toolbar_transparent": true },
 *   "ints": {}, "floats": { "background_image_opacity": 1 },
 *   "images": { "background_image_uri": "images/bg.png" }
 * }
 * ```
 * 拆成 `strings/booleans/ints/floats` 而不是一个大 `settings` 块，是因为 JSON 没法区分
 * `1` 和 `1.0`；`font_scale` 这类浮点键写成整数会被读成 int，快照那边取不到值。
 */
object ThemePackArchive {

    const val MANIFEST_NAME = "theme.json"
    private const val TAG = "ThemePackArchive"
    private const val FORMAT = "revokit-theme"

    /** 解析出来的清单：主题本体 + 还没落盘的图片（偏好键 → zip 内路径）。 */
    data class Manifest(val pack: ThemePack, val images: Map<String, String>)

    /** 在 zip 里找 theme.json（允许放在一级子目录里）。 */
    fun findManifestEntry(zip: ZipFile): ZipEntry? {
        val entries = zip.entries().toList().filterNot { it.isDirectory }
        // 根目录优先，其次只接受一级子目录，避免误命中素材包里别的 json
        return entries.firstOrNull { it.name.equals(MANIFEST_NAME, ignoreCase = true) }
                ?: entries.firstOrNull { entry ->
                    val parts = entry.name.split('/')
                    parts.size == 2 && parts[1].equals(MANIFEST_NAME, ignoreCase = true)
                }
    }

    fun parseManifest(raw: String): Manifest? {
        return runCatching {
                    val root = JSONObject(raw)
                    // 名字缺失不算错：导入器还会从文件名里抠，最后兜底叫「自定义美化」。
                    // 这里要是直接判失败，整份 theme.json 就白写了。
                    val name =
                            root.optString("name").takeIf { it.isNotBlank() } ?: "自定义美化"
                    val strings = readMap(root.optJSONObject("strings")) { it.toString() }
                    val booleans =
                            readMap(root.optJSONObject("booleans")) {
                                (it as? Boolean) ?: it.toString().toBoolean()
                            }
                    val ints =
                            readMap(root.optJSONObject("ints")) {
                                (it as? Number)?.toInt() ?: 0
                            }
                    val floats =
                            readMap(root.optJSONObject("floats")) {
                                (it as? Number)?.toFloat() ?: 0f
                            }
                    val images =
                            readMap(root.optJSONObject("images")) { it.toString() }
                    val pack =
                            ThemePack(
                                    id = root.optString("id").ifBlank { "pack_" + name.hashCode() },
                                    name = name,
                                    description = root.optString("description"),
                                    dark = root.optBoolean("dark", false),
                                    followWallpaper = root.optBoolean("followWallpaper", false),
                                    primaryColor =
                                            root.optInt("primary", DEFAULT_PRIMARY),
                                    secondaryColor =
                                            root.optInt("secondary", DEFAULT_SECONDARY),
                                    strings = strings,
                                    booleans = booleans,
                                    ints = ints,
                                    floats = floats,
                            )
                    Manifest(pack = pack, images = images)
                }
                .onFailure { AppLogger.e(TAG, "theme.json 解析失败", it) }
                .getOrNull()
    }

    /**
     * 把一份主题配置导出成 zip（`theme.json` + `images/` 目录下的图片）。
     *
     * 只导出 `file://` 且真实存在的图片；`android_asset`、网络地址之类跳过，
     * 免得导出包里塞一堆打不开的路径。
     */
    fun export(
            context: Context,
            values: ThemePreferenceValues,
            name: String,
            output: OutputStream,
    ): Boolean {
        return runCatching {
                    val strings = LinkedHashMap<String, String>()
                    val images = LinkedHashMap<String, String>()
                    val usedNames = HashSet<String>()

                    values.strings.forEach { (key, value) ->
                        val file = resolveLocalImage(value)
                        if (key.endsWith("_uri") && file != null) {
                            var entryName = "images/" + file.name
                            var n = 1
                            while (!usedNames.add(entryName)) {
                                entryName = "images/${n}_${file.name}"
                                n++
                            }
                            images[key] = entryName
                        } else {
                            strings[key] = value
                        }
                    }

                    val pack =
                            ThemePack(
                                    id = "export_" + System.currentTimeMillis(),
                                    name = name,
                                    description = "导出于本机",
                                    dark = values.string("theme_mode") ==
                                            UserPreferencesManager.THEME_MODE_DARK,
                                    primaryColor =
                                            values.int("custom_primary_color") ?: DEFAULT_PRIMARY,
                                    secondaryColor =
                                            values.int("custom_secondary_color")
                                                    ?: DEFAULT_SECONDARY,
                                    followWallpaper = values.boolean("use_custom_colors") != true,
                                    strings = strings,
                                    booleans = values.booleans,
                                    ints = values.ints,
                                    floats = values.floats,
                            )

                    ZipOutputStream(output).use { zip ->
                        val manifest = JSONObject()
                        manifest.put("format", FORMAT)
                        manifest.put("version", ThemePack.FORMAT_VERSION)
                        manifest.put("id", pack.id)
                        manifest.put("name", pack.name)
                        manifest.put("description", pack.description)
                        manifest.put("dark", pack.dark)
                        manifest.put("followWallpaper", pack.followWallpaper)
                        manifest.put("primary", pack.primaryColor)
                        manifest.put("secondary", pack.secondaryColor)
                        manifest.put("primaryHex", ThemePack.hexOf(pack.primaryColor))
                        manifest.put("strings", JSONObject(pack.strings as Map<*, *>))
                        manifest.put("booleans", JSONObject(pack.booleans as Map<*, *>))
                        manifest.put("ints", JSONObject(pack.ints as Map<*, *>))
                        manifest.put("floats", JSONObject(pack.floats as Map<*, *>))
                        manifest.put("images", JSONObject(images as Map<*, *>))
                        zip.putNextEntry(ZipEntry(MANIFEST_NAME))
                        zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        images.forEach { (key, entryName) ->
                            val file = resolveLocalImage(values.string(key) ?: "") ?: return@forEach
                            zip.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                    true
                }
                .onFailure { AppLogger.e(TAG, "导出主题包失败", it) }
                .getOrDefault(false)
    }

    /** `file://…` 且文件存在才返回，其余一律 null。 */
    fun resolveLocalImage(uriString: String): File? {
        if (!uriString.startsWith("file://")) return null
        val path = runCatching { Uri.parse(uriString).path }.getOrNull() ?: return null
        val file = File(path)
        return file.takeIf { it.isFile && it.length() > 0 }
    }

    private fun <T> readMap(source: JSONObject?, convert: (Any) -> T): Map<String, T> {
        if (source == null) return emptyMap()
        val out = LinkedHashMap<String, T>()
        source.keys().forEach { key -> out[key] = convert(source.get(key)) }
        return out
    }

    // 与 ThemePack 保持一致的兜底色，避免清单里没写颜色时炸掉
    private const val DEFAULT_PRIMARY = -11629454
    private const val DEFAULT_SECONDARY = -9459569

    /** 生成一个能当目录名用的 id。 */
    fun safeId(raw: String): String {
        val cleaned = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9_-]"), "_")
        return cleaned.ifBlank { "pack_" + System.currentTimeMillis() }
    }
}
