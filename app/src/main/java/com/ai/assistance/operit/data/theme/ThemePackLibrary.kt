package com.ai.assistance.operit.data.theme

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.data.model.ThemePack
import com.ai.assistance.operit.util.AppLogger
import java.io.File

/**
 * 已导入主题包的本地库。
 *
 * 为什么需要它：原来的导入是「导完直接写进主题偏好」，图片留在临时目录里，
 * 事后想换回来就没入口了。这里把每个包完整存一份：
 *
 * ```
 * files/themepacks/<id>/
 *   pack.json      ← ThemePack 的 JSON，图片 URI 已指向本目录
 *   <图片文件>      ← 从临时目录复制过来的副本
 * ```
 *
 * 复制而不是引用原路径，是因为临时目录里的图会被编辑器的「清理未引用暂存资源」删掉。
 */
object ThemePackLibrary {

    private const val TAG = "ThemePackLibrary"
    private const val ROOT = "themepacks"
    private const val PACK_FILE = "pack.json"

    /** 名字抠不出来时的兜底，与导入器保持同一个词。 */
    private const val DEFAULT_NAME = "自定义美化"

    /** 洗完只剩这些词就认为没有信息量，改用兜底名。 */
    private val GENERIC_NAMES =
            setOf("主题", "美化", "美化版", "美化主题", "素材", "包", "自定义")

    data class SavedPack(
            val id: String,
            val pack: ThemePack,
            /** 卡片缩略图：优先用聊天背景，没有就退到任意一张图。 */
            val coverUri: String?,
            val dir: File,
    )

    private fun root(context: Context): File = File(context.filesDir, ROOT)

    fun list(context: Context): List<SavedPack> {
        val base = root(context)
        if (!base.isDirectory) return emptyList()
        return base.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { read(context, it) }
                ?.sortedByDescending { it.dir.lastModified() }
                ?: emptyList()
    }

    fun get(context: Context, id: String): SavedPack? {
        val dir = File(root(context), ThemePackArchive.safeId(id))
        return read(context, dir)
    }

    /**
     * 保存一个包。会把 [imageKeys] 指向的本地图片复制进库目录，并把 pack 里的 URI 改成副本路径。
     * 库里的文件不登记成「暂存资源」——否则用户一放弃编辑草稿，库里的图就没了。
     */
    fun save(context: Context, pack: ThemePack, imageKeys: Collection<String> = pack.strings.keys): SavedPack? {
        return runCatching {
                    val dir = File(root(context), ThemePackArchive.safeId(pack.id))
                    if (!dir.exists() && !dir.mkdirs()) return null

                    val remap = LinkedHashMap<String, String>()
                    imageKeys.forEach { key ->
                        val file = ThemePackArchive.resolveLocalImage(pack.strings[key] ?: "") ?: return@forEach
                        val dst = File(dir, file.name)
                        if (dst.absolutePath != file.absolutePath) {
                            file.copyTo(dst, overwrite = true)
                        }
                        remap[pack.strings[key]!!] = Uri.fromFile(dst).toString()
                    }

                    val remapped =
                            pack.copy(
                                    strings =
                                            pack.strings.mapValues { (_, value) ->
                                                remap[value] ?: value
                                            }
                            )
                    File(dir, PACK_FILE).writeText(remapped.toJson())
                    SavedPack(
                            id = dir.name,
                            pack = remapped,
                            coverUri = coverOf(remapped),
                            dir = dir,
                    )
                }
                .onFailure { AppLogger.e(TAG, "保存主题包失败", it) }
                .getOrNull()
    }

    fun delete(context: Context, id: String): Boolean {
        return runCatching { File(root(context), ThemePackArchive.safeId(id)).deleteRecursively() }
                .onFailure { AppLogger.e(TAG, "删除主题包失败", it) }
                .getOrDefault(false)
    }

    private fun read(context: Context, dir: File): SavedPack? {
        val file = File(dir, PACK_FILE)
        if (!file.isFile) return null
        val raw = runCatching { ThemePack.fromJson(file.readText(Charsets.UTF_8)) }.getOrNull()
                ?: return null
        // 老版本导入的包名里带「QQ 主题」这类字样，读的时候顺手洗掉并写回，
        // 免得主题列表里一直挂着不该出现的词
        val pack = raw.copy(name = sanitizeLegacyName(raw.name))
        if (pack.name != raw.name) {
            runCatching { file.writeText(pack.toJson()) }
                    .onFailure { AppLogger.w(TAG, "主题包改名写回失败", it) }
        }
        return SavedPack(id = dir.name, pack = pack, coverUri = coverOf(pack), dir = dir)
    }

    /** 把历史遗留的「QQ 主题 / QQ美化版XXX」洗成一个普通主题名。 */
    private fun sanitizeLegacyName(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return DEFAULT_NAME
        // 兜底名自己不能再被洗一遍，否则「美化」两个字会被当成套话切掉，越洗越短
        if (text == DEFAULT_NAME) return DEFAULT_NAME
        val cleaned =
                text.replace(Regex("""(?i)qq"""), " ")
                        .replace(Regex("""^美化版?"""), " ")
                        .replace(Regex("""美化版?$"""), " ")
                        .replace(Regex("""[\s_]+"""), " ")
                        .trim()
                        .trim('【', '】', '[', ']', '（', '）', '(', ')', '.', ' ', '-')
                        .trim()
        // 洗完只剩「主题 / 美化」这种通用词，说明原名本来就没有信息量，直接兜底
        if (cleaned.isEmpty() || cleaned in GENERIC_NAMES) return DEFAULT_NAME
        return cleaned
    }

    private fun coverOf(pack: ThemePack): String? {
        val preferred =
                listOf(
                                "background_image_uri",
                                "settings_background_image_uri",
                                "sidebar_background_image_uri",
                                "bubble_ai_image_uri",
                        )
                        .firstNotNullOfOrNull { key ->
                            pack.strings[key]?.takeIf { ThemePackArchive.resolveLocalImage(it) != null }
                        }
        return preferred
                ?: pack.strings.entries
                        .firstOrNull {
                            it.key.endsWith("_uri") &&
                                    ThemePackArchive.resolveLocalImage(it.value) != null
                        }
                        ?.value
    }
}
