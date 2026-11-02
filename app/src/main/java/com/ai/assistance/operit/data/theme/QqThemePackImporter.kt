package com.ai.assistance.operit.data.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import com.ai.assistance.operit.data.model.ThemePack
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导入美化素材 zip，翻译成一个可直接套用的主题包。
 *
 * 为什么能按文件名认图：这类美化包的做法是「自己做图替换源文件、文件名不变」，
 * 所以导出的素材文件名就是原生的资源名。认名字比认尺寸可靠——
 * 不同包的同名图尺寸可能完全不同（见过 1316x2340 和 720x1280 两种）。
 *
 * 只挑认识的几个文件，压缩包里其余内容一概不落盘，避免把几十 MB 垃圾拷进私有目录。
 */
object QqThemePackImporter {

    private const val TAG = "QqThemeImport"

    /** 配置里没写名字、文件名也抠不出来时的兜底名称。 */
    private const val DEFAULT_PACK_NAME = "自定义美化"

    /**
     * 从文件名里抠美化名，兼容几种常见的命名习惯：
     * `【温柔】美化版.zip`、`温柔美化版.zip`、`美化版【温柔】.zip`。
     * 这是「老包没有 theme.json」时的兜底，正常包一律以配置里的 name 为准。
     */
    private val NAME_AFTER_TAG = Regex("""美化版\s*[【\[（(]?\s*([^】\]）).\-—]+)""")
    private val NAME_IN_BRACKET = Regex("""^[【\[（(]([^】\]）)]+)[】\]）)]""")
    /** 提取站自动生成的名字（result_mvxnpzkt / result_mvxnpzkt-1），当不成名字。 */
    private val RANDOM_NAME = Regex("""(?i)^result[ _-][a-z0-9]+(-[0-9]+)?$""")
    /** 洗完只剩这些通用词，等于没有名字，退回兜底名。 */
    private val GENERIC_NAMES =
            setOf("主题", "美化", "美化版", "美化主题", "素材", "包", "自定义")

    /** 认识的资源：词干（去掉目录与扩展名）→ 用途。必须整词相等，避免前缀误匹配。 */
    private const val STEM_CHAT_BG = "skin_chat_background"
    private const val STEM_MAIN_BG_V2 = "skin_background_theme_version2"
    private const val STEM_MAIN_BG = "skin_background"
    private const val STEM_SETTING_BG = "qq_setting_me_bg"
    private const val STEM_FRIEND_BUBBLE = "skin_aio_friend_bubble_nor"
    private const val STEM_USER_BUBBLE = "skin_aio_user_bubble_nor"

    private val WANTED =
            setOf(
                    STEM_CHAT_BG,
                    STEM_MAIN_BG_V2,
                    STEM_MAIN_BG,
                    STEM_SETTING_BG,
                    STEM_FRIEND_BUBBLE,
                    STEM_USER_BUBBLE,
            )

    /**
     * 本批次真正落盘并写进配置的项。只解压用得到的——多解压的文件因为没被任何配置项
     * 引用，会被编辑器的「清理未引用暂存资源」直接删掉，白落一次盘。
     */
    private val EXTRACT_NOW =
            setOf(
                    STEM_CHAT_BG, // 全局背景 = 聊天页背景
                    STEM_MAIN_BG_V2, // 侧边栏背景（沿用「主界面」那张）
                    STEM_MAIN_BG, // 主界面背景的另一份命名，version2 缺失时兜底
                    STEM_SETTING_BG, // 设置页背景
                    STEM_FRIEND_BUBBLE,
                    STEM_USER_BUBBLE,
            )

    data class Result(
            val pack: ThemePack,
            /** 落盘的图片 URI，交给编辑器会话登记为暂存资源，丢弃时会被清掉。 */
            val stagedUris: List<String>,
            /** 认出来的资源，用于给用户提示。 */
            val matched: List<String>,
    )

    suspend fun import(context: Context, zipUri: Uri): Result? = withContext(Dispatchers.IO) {
        val tmpZip = File(context.cacheDir, "qq_theme_import_${System.currentTimeMillis()}.zip")
        try {
            context.contentResolver.openInputStream(zipUri)?.use { input ->
                tmpZip.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            if (tmpZip.length() <= 0) return@withContext null

            ZipFile(tmpZip).use { zip ->
                // 优先按包内 theme.json 走：谁做的包、图叫什么名都不影响，配置说了算。
                // 没有清单的老包才回退到下面这套「按原生资源名硬认图」的内置映射。
                val manifest =
                        ThemePackArchive.findManifestEntry(zip)?.let { entry ->
                            val raw =
                                    runCatching {
                                                zip.getInputStream(entry)
                                                        .use { it.readBytes().toString(Charsets.UTF_8) }
                                            }
                                            .getOrNull()
                            raw?.let(ThemePackArchive::parseManifest)
                        }
                if (manifest != null) {
                    AppLogger.d(TAG, "命中 theme.json，按清单导入：${manifest.images.keys}")
                    return@withContext importFromManifest(context, zip, manifest, zipUri)
                }

                val entries = zip.entries().toList().filterNot { it.isDirectory }
                val picked = LinkedHashMap<String, ZipEntry>()
                for (entry in entries) {
                    val stem = stemOf(entry.name)
                    if (stem in WANTED && stem !in picked) picked[stem] = entry
                }
                if (picked.isEmpty()) {
                    AppLogger.w(TAG, "zip 里没认出任何素材（共 ${entries.size} 个文件）")
                    return@withContext null
                }

                // 只抽出认识的文件，落到私有目录，文件名保持原样
                val dir = File(context.filesDir, "themepack_${System.currentTimeMillis()}")
                if (!dir.exists() && !dir.mkdirs()) return@withContext null
                val files = LinkedHashMap<String, File>()
                for ((stem, entry) in picked) {
                    if (stem !in EXTRACT_NOW) continue
                    val file = File(dir, safeName(entry.name))
                    runCatching {
                                zip.getInputStream(entry).use { input ->
                                    file.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                            .onFailure { AppLogger.e(TAG, "解压失败 ${entry.name}", it) }
                    if (file.exists() && file.length() > 0) files[stem] = file
                }

                val primary = resolvePrimary(zip, files[STEM_CHAT_BG])

                val pack =
                        buildPack(
                                context = context,
                                files = files,
                                primary = primary,
                                name = resolvePackName(context, zipUri, null),
                        )
                                ?: return@withContext null
                AppLogger.d(
                        TAG,
                        "导入完成 匹配=${files.keys} 主色=#%06X 副色=#%06X".format(
                                Locale.ROOT,
                                pack.primaryColor and 0xFFFFFF,
                                pack.secondaryColor and 0xFFFFFF,
                        )
                )
                Result(
                        pack = pack,
                        stagedUris = files.values.map { Uri.fromFile(it).toString() },
                        matched = files.keys.toList(),
                )
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "导入美化主题包失败", t)
            null
        } finally {
            runCatching { tmpZip.delete() }
        }
    }

    /**
     * 按包内 `theme.json` 导入。配置里写什么就是什么，不再猜。
     *
     * 气泡图如果在清单里没给几何参数，这里现场从 `.9.png` 的 npTc 里读，作者不必自己算那八个数。
     */
    private fun importFromManifest(
            context: Context,
            zip: ZipFile,
            manifest: ThemePackArchive.Manifest,
            zipUri: Uri,
    ): Result? {
        val dir = File(context.filesDir, "themepack_${System.currentTimeMillis()}")
        if (!dir.exists() && !dir.mkdirs()) return null

        val strings = LinkedHashMap(manifest.pack.strings)
        val floats = LinkedHashMap(manifest.pack.floats)
        val booleans = LinkedHashMap(manifest.pack.booleans)

        val extracted = LinkedHashMap<String, File>()
        manifest.images.forEach { (key, relPath) ->
            val entry = findEntry(zip, relPath)
            if (entry == null) {
                AppLogger.w(TAG, "清单里写的图片找不到：$relPath")
                return@forEach
            }
            val file = File(dir, safeName(entry.name))
            runCatching {
                        zip.getInputStream(entry).use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    .onFailure { AppLogger.e(TAG, "清单图片解压失败 $relPath", it) }
            if (file.exists() && file.length() > 0) {
                extracted[key] = file
                strings[key] = Uri.fromFile(file).toString()
            }
        }

        // 气泡图没写几何参数就现场从 .9.png 里读
        listOf("ai" to "bubble_ai_image_uri", "user" to "bubble_user_image_uri").forEach { (prefix, key) ->
            val file = extracted[key] ?: return@forEach
            if (floats.containsKey("bubble_${prefix}_image_repeat_start")) return@forEach
            val params = decodeBubble(file) ?: return@forEach
            floats["bubble_${prefix}_image_crop_left"] = params.cropLeft
            floats["bubble_${prefix}_image_crop_top"] = params.cropTop
            floats["bubble_${prefix}_image_crop_right"] = params.cropRight
            floats["bubble_${prefix}_image_crop_bottom"] = params.cropBottom
            floats["bubble_${prefix}_image_repeat_start"] = params.repeatXStart
            floats["bubble_${prefix}_image_repeat_end"] = params.repeatXEnd
            floats["bubble_${prefix}_image_repeat_y_start"] = params.repeatYStart
            floats["bubble_${prefix}_image_repeat_y_end"] = params.repeatYEnd
            floats["bubble_${prefix}_image_scale"] = 1f
            booleans["bubble_${prefix}_use_image"] = true
            booleans["bubble_${prefix}_bubble_liquid_glass"] = false
            booleans["bubble_${prefix}_bubble_water_glass"] = false
            strings.putIfAbsent("bubble_image_render_mode", "nine_patch")
        }

        // 名字以配置为准；配置没写 / 抠不出东西时退回文件名，再不行就叫「自定义美化」。
        // 无论哪条路径都会过一遍 sanitizeName，把 QQ 字样洗掉——这就是一个普通美化主题。
        val packName = resolvePackName(context, zipUri, manifest.pack.name)
        // 主色兜底：配置里的主色常常是从「配色总表」里抓的描边/阴影色（接近纯黑），
        // 直接拿去生成整套配色会做出一片墨黑界面。撑不起主题就改用背景图里的真实主色。
        val (primaryColor, secondaryColor) =
                resolveManifestColors(
                        primaryArgb = manifest.pack.primaryColor,
                        secondaryArgb = manifest.pack.secondaryColor,
                        bgFile = pickBackgroundFile(extracted),
                )
        val pack =
                manifest.pack.copy(
                        strings = strings,
                        floats = floats,
                        booleans = booleans,
                        name = packName,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                )
        AppLogger.d(
                TAG,
                "清单导入完成 名称=${pack.name} 图片=${extracted.keys} 主色=#%06X".format(
                        Locale.ROOT,
                        pack.primaryColor and 0xFFFFFF,
                )
        )
        return Result(
                pack = pack,
                stagedUris = extracted.values.map { Uri.fromFile(it).toString() },
                matched = extracted.keys.toList(),
        )
    }

    /**
     * 定主题名字：配置里的 name 优先 → 文件名 → [DEFAULT_PACK_NAME]。
     * 三道都会过 [sanitizeName]，保证落地的名字里不会出现 QQ 字样。
     */
    private fun resolvePackName(context: Context, zipUri: Uri, fromManifest: String?): String {
        // 清单解析时缺 name 会填成兜底名，那种情况不算「作者给了名字」，继续往下找
        sanitizeName(fromManifest)?.takeIf { it != DEFAULT_PACK_NAME }?.let { return it }
        nameFromFileName(context, zipUri)?.let { return it }
        return DEFAULT_PACK_NAME
    }

    /** 洗掉不能出现在主题名里的字样，并收拾多余的空格、括号和「美化版」这类套话。 */
    private fun sanitizeName(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        // 兜底名本身不要再洗，否则尾部的「美化」会被当成套话切掉
        if (text == DEFAULT_PACK_NAME) return null
        val cleaned =
                text.replace(Regex("""(?i)qq"""), " ")
                        .replace(Regex("""^美化版?"""), " ") // 「美化版【梦】」这种开头套话
                        .replace(Regex("""美化版?$"""), " ") // 「梦美化版」这种结尾套话
                        .replace(Regex("""[\s_]+"""), " ")
                        .trim()
                        .trim('【', '】', '[', ']', '（', '）', '(', ')', '.', ' ', '-')
                        .trim()
        if (cleaned.isEmpty() || cleaned in GENERIC_NAMES) return null
        return if (cleaned.length > 24) cleaned.take(24) else cleaned
    }

    /** 从压缩包文件名里抠名字；抠不出来（比如 result_mvxnpzkt.zip）返回 null。 */
    private fun nameFromFileName(context: Context, uri: Uri): String? {
        val fileName =
                runCatching {
                            context.contentResolver
                                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                                    ?.use { cursor ->
                                        if (cursor.moveToFirst() && cursor.columnCount > 0) {
                                            cursor.getString(0)
                                        } else {
                                            null
                                        }
                                    }
                        }
                        .getOrNull()
                        ?: uri.lastPathSegment
                        ?: return null
        // 重复下载会被系统改名成「xxx (1).zip」，先把这种尾巴去掉再判断
        val stem = fileName.substringBeforeLast('.').trim().replace(Regex("""\s*\(\d+\)$"""), "")
        if (stem.isEmpty() || RANDOM_NAME.matches(stem)) return null
        NAME_AFTER_TAG.find(stem)?.groupValues?.getOrNull(1)?.let { return sanitizeName(it) }
        NAME_IN_BRACKET.find(stem)?.groupValues?.getOrNull(1)?.let { return sanitizeName(it) }
        return sanitizeName(stem)
    }

    /** 主色能不能拿来当主题种子：太灰（没色相）、太白、太黑都不行。 */
    private fun isUsableSeed(argb: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        return hsv[1] >= 0.10f && hsv[2] in 0.12f..0.93f
    }

    /** 从已解压的图里挑一张「整屏背景」用来取色，优先非设置页/非侧边栏的那张。 */
    private fun pickBackgroundFile(extracted: Map<String, File>): File? {
        val primaryBg =
                extracted.entries.firstOrNull {
                    val key = it.key.lowercase(Locale.ROOT)
                    key.contains("background") &&
                            !key.contains("settings") &&
                            !key.contains("sidebar") &&
                            !key.contains("bubble")
                }
        return primaryBg?.value
                ?: extracted.entries.firstOrNull { it.key.contains("background") }?.value
                ?: extracted.values.firstOrNull()
    }

    /**
     * 主色兜底：配置给得能用就原样用（作者自己的配色，不该被我们改）；
     * 不能用（接近纯黑/纯白/纯灰）就改成背景图真实主色，副色跟着重算。
     */
    private fun resolveManifestColors(
            primaryArgb: Int,
            secondaryArgb: Int,
            bgFile: File?,
    ): Pair<Int, Int> {
        if (isUsableSeed(primaryArgb)) return primaryArgb to secondaryArgb
        val fromImage = bgFile?.let { sampleDominantColor(it) }?.let { strengthen(it) }
        if (fromImage != null) {
            AppLogger.w(
                    TAG,
                    "配置主色 #%06X 撑不起主题，改用背景图主色 #%06X".format(
                            Locale.ROOT,
                            primaryArgb and 0xFFFFFF,
                            fromImage and 0xFFFFFF,
                    )
            )
            return fromImage to deriveSecondary(fromImage)
        }
        // 图也取不出色（纯黑纯白图），至少把主色加工成看得见的深色，别交一片墨黑出去
        val patched = strengthen(primaryArgb)
        AppLogger.w(
                TAG,
                "配置主色 #%06X 不可用且背景图取不到色，改用加工色 #%06X".format(
                        Locale.ROOT,
                        primaryArgb and 0xFFFFFF,
                        patched and 0xFFFFFF,
                )
        )
        return patched to deriveSecondary(patched)
    }

    /** 清单里的路径可能带子目录、大小写也可能不同，三级兜底找一遍。 */
    private fun findEntry(zip: ZipFile, relPath: String): ZipEntry? {
        val target = relPath.replace('\\', '/').trimStart('/')
        if (target.isEmpty()) return null
        val entries = zip.entries().toList().filterNot { it.isDirectory }
        val normalized = entries.map { it to it.name.replace('\\', '/').trimStart('/') }
        return normalized.firstOrNull { it.second == target }?.first
                ?: normalized.firstOrNull { it.second.endsWith("/$target") }?.first
                ?: normalized
                        .firstOrNull {
                            it.second.substringAfterLast('/')
                                    .equals(target.substringAfterLast('/'), ignoreCase = true)
                        }
                        ?.first
    }

    private fun buildPack(
            context: Context,
            files: Map<String, File>,
            primary: Int,
            name: String,
    ): ThemePack? {
        val chatBg = files[STEM_CHAT_BG] ?: return null
        val friendBubble = files[STEM_FRIEND_BUBBLE]
        val userBubble = files[STEM_USER_BUBBLE]

        val strings = LinkedHashMap<String, String>()
        val floats = LinkedHashMap<String, Float>()
        val booleans = LinkedHashMap<String, Boolean>()

        // ---- 聊天页整体样式：气泡图能不能生效的前提 ----
        // chat_style 默认是 cursor（光标样式），不改这里的话下面所有气泡参数都不会被用上
        strings["chat_style"] = UserPreferencesManager.CHAT_STYLE_BUBBLE

        // 顶栏 / 状态栏 / 聊页头 / 输入框全透明，让背景图铺满整屏。
        // 美化包的效果就是「只在图片上浮着文字」，实色顶栏是最破坏氛围的一块。
        booleans["toolbar_transparent"] = true
        booleans["status_bar_transparent"] = true
        booleans["chat_header_transparent"] = true
        // 覆盖模式会额外盖一层，配合图片背景反而不透，保持默认关闭
        booleans["chat_header_overlay_mode"] = false
        booleans["chat_input_transparent"] = true
        // 输入栏：经典样式 + 悬浮 + 磨砂玻璃（对应「磨砂玻璃输入框」那个开关）
        strings["input_style"] = UserPreferencesManager.INPUT_STYLE_CLASSIC
        booleans["chat_input_floating"] = true
        booleans["chat_input_liquid_glass"] = true
        booleans["chat_input_water_glass"] = false
        // 侧边栏：水玻璃/按钮磨砂默认关——不开时画面更平但适配性最好，
        // 立体感强的那一档留给用户自己按需打开（主题包里不再强制开）
        booleans["navigation_drawer_water_glass"] = false
        booleans["navigation_drawer_button_liquid_glass"] = false
        // 配图片背景时噪点和模糊都是减分项
        booleans["use_noise_texture"] = false
        booleans["use_background_blur"] = false
        // 气泡外层的 clip(shape) 是圆角矩形，会把图片气泡的尖角和描边裁掉，必须关
        booleans["bubble_rounded_corners_enabled"] = false
        booleans["bubble_ai_rounded_corners_enabled"] = false
        // 一对一聊天不显示「谁在说」，气泡上方那行名字在美化主题里非常破坏画面
        booleans["show_role_name"] = false
        booleans["show_user_name"] = false
        // 图片背景上压着不透明白卡片太突兀，卡片改成半透明跟着主题走
        booleans["translucent_surfaces"] = true
        // 输入处理状态那行属于调试信息，美化主题里留着会打断画面
        booleans["show_input_processing_status"] = false

        // ---- 聊天背景 ----
        strings["background_image_uri"] = Uri.fromFile(chatBg).toString()
        strings["background_media_type"] = "image"
        // 默认透明度 0.3、模糊 10dp 会把背景图糊成一团，主题包自带完整图，要原样显示
        floats["background_image_opacity"] = 1f
        floats["background_blur_radius"] = 0f
        booleans["use_background_image"] = true

        // ---- 分页面背景 ----
        // 美化包每个页面用各自的背景图，不是一张图铺到底。这里把设置页、侧边栏各配一张，
        // 其余页面继续沿用上面的全局图。
        val settingBg = files[STEM_SETTING_BG]
        if (settingBg != null) {
            strings["settings_background_image_uri"] = Uri.fromFile(settingBg).toString()
        }
        val mainBg = files[STEM_MAIN_BG_V2] ?: files[STEM_MAIN_BG]
        if (mainBg != null) {
            strings["sidebar_background_image_uri"] = Uri.fromFile(mainBg).toString()
        }
        // 侧栏水玻璃由上面的「界面栏」开关统一控制（用户要水玻璃侧滑菜单），这里不再覆盖

        // ---- 气泡 ----
        // 好友/AI 气泡与我的气泡各一套，缺哪个就只配哪个
        applyBubble(
                prefix = "ai",
                file = friendBubble,
                strings = strings,
                floats = floats,
                booleans = booleans,
        )
        applyBubble(
                prefix = "user",
                file = userBubble,
                strings = strings,
                floats = floats,
                booleans = booleans,
        )

        val secondary = deriveSecondary(primary)

        return ThemePack(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                description = "导入的美化主题",
                dark = false,
                primaryColor = primary,
                secondaryColor = secondary,
                followWallpaper = false,
                strings = strings,
                booleans = booleans,
                ints = emptyMap(),
                floats = floats,
        )
    }

    private fun applyBubble(
            prefix: String,
            file: File?,
            strings: MutableMap<String, String>,
            floats: MutableMap<String, Float>,
            booleans: MutableMap<String, Boolean>,
    ) {
        if (file == null) return
        val params = decodeBubble(file)
        strings["bubble_${prefix}_image_uri"] = Uri.fromFile(file).toString()
        booleans["bubble_${prefix}_use_image"] = true
        // 玻璃质感跟图片气泡互斥，开图片就必须把玻璃关掉
        booleans["bubble_${prefix}_bubble_liquid_glass"] = false
        booleans["bubble_${prefix}_bubble_water_glass"] = false
        if (params != null) {
            floats["bubble_${prefix}_image_crop_left"] = params.cropLeft
            floats["bubble_${prefix}_image_crop_top"] = params.cropTop
            floats["bubble_${prefix}_image_crop_right"] = params.cropRight
            floats["bubble_${prefix}_image_crop_bottom"] = params.cropBottom
            floats["bubble_${prefix}_image_repeat_start"] = params.repeatXStart
            floats["bubble_${prefix}_image_repeat_end"] = params.repeatXEnd
            floats["bubble_${prefix}_image_repeat_y_start"] = params.repeatYStart
            floats["bubble_${prefix}_image_repeat_y_end"] = params.repeatYEnd
            floats["bubble_${prefix}_image_scale"] = 1f
        }
        // 只要配了气泡图就走九宫格，跟原生气泡的拉伸行为一致
        strings["bubble_image_render_mode"] = "nine_patch"
    }

    private fun decodeBubble(file: File): BubbleSliceParams? {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap =
                runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
                        ?: return null
        return runCatching {
                    // 优先用从 PNG 文件里抠出来的原始 chunk；系统的那份布局可能对不上
                    val fileChunk =
                            runCatching { QqBubbleSliceDecoder.findNinePatchChunk(file.readBytes()) }
                                    .getOrNull()
                    val chunk = fileChunk ?: bitmap.ninePatchChunk
                    val params = QqBubbleSliceDecoder.decode(bitmap, chunk)
                    AppLogger.d(
                            TAG,
                            "气泡 ${file.name} ${bitmap.width}x${bitmap.height} " +
                                    "文件chunk=${fileChunk?.size ?: 0}B 系统chunk=${bitmap.ninePatchChunk?.size ?: 0}B " +
                                    "hex=${QqBubbleSliceDecoder.hexPrefix(chunk)} -> $params"
                    )
                    params
                }
                .onFailure { AppLogger.e(TAG, "九宫格解析失败 ${file.name}", it) }
                .getOrNull()
                .also { bitmap.recycle() }
    }

    // ---------------- 取色 ----------------

    /**
     * 主色优先取包里现成的配色表，取不到就自己从背景图里量。
     * 但配色表给的是「图片主色调」，普遍很淡（#fafeea、#d6eefa 这种），
     * 直接当 Material 主色会导致按钮和选中态看不清，所以统一要过一道 strengthen。
     */
    private fun resolvePrimary(zip: ZipFile, chatBgFile: File?): Int {
        readColorFromTable(zip)?.let { tableColor ->
            // 配色总表里那行「主色」常常是描边/阴影色，纯黑纯白都见过，别直接用
            if (isUsableSeed(tableColor)) return strengthen(tableColor)
            AppLogger.w(
                    TAG,
                    "配色总表主色 #%06X 不可用，改用背景图取色".format(
                            Locale.ROOT,
                            tableColor and 0xFFFFFF,
                    )
            )
        }
        chatBgFile?.let { sampleDominantColor(it)?.let { c -> return strengthen(c) } }
        return -11629454 // 兜底：青竹 #4E8C72 的 ARGB 字面量
    }

    private fun readColorFromTable(zip: ZipFile): Int? {
        val txts =
                zip.entries()
                        .toList()
                        .filter {
                            !it.isDirectory &&
                                    it.name.endsWith(".txt", ignoreCase = true) &&
                                    ("配色" in it.name || "02_" in it.name)
                        }
        if (txts.isEmpty()) return null

        fun pick(vararg keys: String): Int? {
            for (entry in txts) {
                val stem = stemOf(entry.name)
                if (keys.any { it in stem }) {
                    val text =
                            runCatching { zip.getInputStream(entry).bufferedReader().readText() }
                                    .getOrNull() ?: continue
                    parseColorLine(text, "主色")?.let { return it }
                }
            }
            return null
        }

        // 先认英文词干（包2 用），再认中文名（包1 用）；都没有就退到主背景
        return pick(STEM_CHAT_BG, "聊天背景")
                ?: pick(STEM_MAIN_BG_V2, "主背景", "主题版主背景")
                ?: pick(STEM_SETTING_BG, "个人页背景", "设置背景")
    }

    /**
     * 取「主色」那一行。分隔符各家不统一：见过中文冒号，也见过制表符，
     * 所以分隔符写成可选的一类，别只认冒号——包1 就是制表符，只认冒号会整个取不到色。
     */
    private fun parseColorLine(text: String, label: String): Int? {
        val regex = Regex("""$label\s*[:：\t]*\s*#?([0-9a-fA-F]{6})""")
        val hex = regex.find(text)?.groupValues?.get(1) ?: return null
        return runCatching { Color.parseColor("#$hex") }.getOrNull()
    }

    /** 兜底取色：缩到小图求平均，避开接近全透明和接近纯白/纯黑的像素。 */
    private fun sampleDominantColor(file: File): Int? {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
                ?: return null
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return null
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            var r = 0L
            var g = 0L
            var b = 0L
            var n = 0
            for (p in pixels) {
                val a = (p ushr 24) and 0xFF
                if (a < 200) continue
                val rr = (p ushr 16) and 0xFF
                val gg = (p ushr 8) and 0xFF
                val bb = p and 0xFF
                val max = maxOf(rr, gg, bb)
                val min = minOf(rr, gg, bb)
                // 接近纯白/纯黑的像素没有色相信息，不参与平均
                if (max > 250 && min > 240) continue
                if (max < 12) continue
                r += rr
                g += gg
                b += bb
                n++
            }
            if (n == 0) null
            else Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        } catch (t: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * 把「图片主色调」加工成能用的 Material 主色：补饱和度、压暗明度。
     * 只做必要的一步，不追求漂亮——真要细调由用户在设置里改。
     */
    private fun strengthen(argb: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.34f).coerceAtMost(0.85f)
        hsv[2] = hsv[2].coerceAtMost(0.70f).coerceAtLeast(0.42f)
        return Color.HSVToColor(hsv)
    }

    /** 副色在主色基础上转一点色相、稍亮一点，保证跟主色是同一个氛围又不糊在一起。 */
    private fun deriveSecondary(primary: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(primary, hsv)
        hsv[0] = (hsv[0] + 22f) % 360f
        hsv[1] = (hsv[1] * 0.82f).coerceAtLeast(0.18f)
        hsv[2] = (hsv[2] * 1.12f).coerceAtMost(0.92f)
        return Color.HSVToColor(hsv)
    }

    // ---------------- 小工具 ----------------

    private fun stemOf(name: String): String {
        val base = name.substringAfterLast('/')
        var stem = base.substringBeforeLast('.')
        // 气泡是 .9.png 双扩展名，只去掉 .png 会剩下 "..._nor.9"，跟映射表对不上
        if (stem.endsWith(".9", ignoreCase = true)) stem = stem.dropLast(2)
        return stem.lowercase(Locale.ROOT)
    }

    private fun safeName(name: String): String =
            name.substringAfterLast('/').ifBlank { "asset_${System.nanoTime()}" }
}
