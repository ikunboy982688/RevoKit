package com.ai.assistance.operit.core.revokit

import android.os.Environment
import java.io.File

/**
 * 逆向垃圾清理引擎（工具箱「一键清理」页在用）。
 *
 * 为什么界面这边用 Kotlin 又写了一遍，而不是去调 revokit_ws:ws_scan / ws_purge：
 * 那两个是给 AI 用的 JS 工具，底层要起 proot Ubuntu 里的 python3，点一下要等十几秒，
 * 而且包被关掉就直接不可用。界面要的是「点一下、马上出清单」，只能直接走文件系统。
 *
 * 代价是规则会有两份（这里 + revokit_ws.js），**改判据必须两边一起改**。
 * 为了少踩坑，两边的表名、阈值、判定顺序都刻意保持一致，看着像就直接对照着改。
 *
 * 安全底线（跟 JS 那边同一套）：
 *  1. 只有落在白名单里的东西才「默认放行」；白名单外必须用户勾了「允许白名单外」才按内容特征放行。
 *  2. 相册 / 文档 / Android / MT2 / 各种回收站 / 对话工作区，开了放行也一律拒。
 *  3. 有成品 APK 的目录不整目录删（要先拷包），只有一个 dex 的目录也不动（可能是用户的样本）。
 */
object DiskCleaner {

    // ---------------------------------------------------------------
    // 数据结构
    // ---------------------------------------------------------------

    /**
     * 清单里的一项。
     *
     * [advice] 三档：可删 / 先取出APK再删 / 保留。
     * [outsideWhitelist] 为 true 表示删它需要用户额外勾「允许白名单外」。
     */
    data class Item(
        val path: String,
        val kind: String,
        val desc: String,
        val advice: String,
        val reason: String,
        val note: String,
        val bytes: Long,
        val fileCount: Int,
        val outsideWhitelist: Boolean
    ) {
        val mb: Double get() = bytes / 1048576.0
        val deletable: Boolean get() = advice != ADVICE_KEEP
    }

    data class Refusal(val path: String, val reason: String)

    data class Outcome(
        val deleted: List<Item>,
        val refused: List<Refusal>,
        val failed: List<Refusal>,
        val releasedBytes: Long
    ) {
        val releasedMb: Double get() = releasedBytes / 1048576.0
    }

    const val ADVICE_DELETE = "可删"
    const val ADVICE_TAKE_FIRST = "先取出APK再删"
    const val ADVICE_KEEP = "保留"

    private class Sniff(
        val dexCount: Int,
        val apkNames: List<String>,
        val hasSmali: Boolean,
        val hasApktoolMark: Boolean,
        val imageCount: Int,
        val soCount: Int
    )

    private class Classified(
        val kind: String,
        val desc: String,
        val advice: String,
        val reason: String
    )

    // ---------------------------------------------------------------
    // 路径与规则表
    // ---------------------------------------------------------------

    /** 存储卡根，真机上就是 /storage/emulated/0（/sdcard 指向它）。 */
    val storageRoot: String
        get() = normalize(Environment.getExternalStorageDirectory().absolutePath)

    private val apktoolMarks = listOf("apktool.yml", "AndroidManifest.xml")

    /** 逆向过程会往里扔东西的地方。不在里面的默认不报，也不默认放行删除。 */
    private fun whitelistRoots(): List<String> {
        val sd = storageRoot
        return listOf(
            "$sd/Download/Operit/unpack_out",
            "$sd/Download/Operit/pack360",
            "$sd/Download/Operit/tuoke_out",
            "$sd/Download/Operit/dexclean",
            "$sd/Download/Operit/56al",
            "$sd/Download/Operit/rk",
            "$sd/Download/Operit/prev_dumps",
            "$sd/Download/Operit/quarantine",
            "$sd/Download/Operit/alldex",
            "$sd/Download/Operit/tmp",
            "$sd/Download/tmpchk",
            "$sd/Download/tmpverify",
            "$sd/Download/tmp",
            "$sd/app_dex",
            "$sd/jiagu_so",
            "$sd/revokit"
        )
    }

    /** 整棵树都不许碰：用户的照片、文档、应用数据、MT2 原始包、各种回收站、对话工作区。 */
    private fun forbiddenTrees(): List<Pair<String, String>> {
        val sd = storageRoot
        return listOf(
            "$sd/DCIM" to "这是相册（相机照片），不删",
            "$sd/Pictures" to "这是图片目录，不删",
            "$sd/Movies" to "这是视频目录，不删",
            "$sd/Music" to "这是音乐目录，不删",
            "$sd/Documents" to "这是文档目录，不删",
            "$sd/Android" to "这是应用数据目录，删了应用会出问题",
            "$sd/MT2" to "这是 MT 管理器的目录，不删",
            "$sd/.FileManagerRecycler" to "这是文件管理器的回收站（删了就没法恢复），不删",
            "$sd/.trash" to "这是回收站，不删",
            "$sd/.Trash" to "这是回收站，不删",
            "$sd/.recycle" to "这是回收站，不删",
            "$sd/Download/Operit/workspace" to "这是对话工作区，不删",
            "$sd/Download/Operit/skills" to "这是技能目录，不删",
            "$sd/Download/Operit/bridge" to "这是桥接目录，不删",
            "$sd/Download/Operit/cleanOnExit" to "这是退出清理目录，不删"
        )
    }

    /** 只比完全相等：这些路径本身不许删（但底下可能有白名单，所以不能整树拦）。 */
    private fun forbiddenExact(): List<String> {
        val sd = storageRoot
        return listOf(
            "/", sd, "/storage", "/storage/emulated", "/storage/emulated/0",
            "/data", "/data/local", "/data/local/tmp", "/system",
            "$sd/Download", "$sd/Download/Operit"
        )
    }

    /** 这几个目录本身不作为一项报出来，只报它的子目录（Download 里是用户自己下的包）。 */
    private fun containerOnly(): List<String> = listOf("$storageRoot/Download", storageRoot)

    private fun normalize(p: String): String {
        var s = p.replace('\\', '/')
        while (s.contains("//")) s = s.replace("//", "/")
        return s.trimEnd('/')
    }

    private fun isUnder(p: String, parent: String): Boolean {
        val pp = normalize(p)
        val pn = normalize(parent)
        return pp == pn || pp.startsWith("$pn/")
    }

    fun isWhitelisted(p: String): Boolean = whitelistRoots().any { isUnder(p, it) }

    // ---------------------------------------------------------------
    // 体积 / 内容特征
    // ---------------------------------------------------------------

    /** 递归量体积。单个文件也支持 —— 成品包经常直接躺在目录里，不是子目录。 */
    private fun sizeOf(file: File): Pair<Long, Int> {
        if (file.isFile) {
            return file.length() to 1
        }
        var total = 0L
        var count = 0
        val stack = ArrayDeque<File>()
        stack.addLast(file)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (c in children) {
                if (c.isDirectory) {
                    stack.addLast(c)
                } else {
                    total += c.length()
                    count++
                }
                if (count > 400_000) return total to count
            }
        }
        return total to count
    }

    /** 只看这一层：dex 数、apk 名、有没有 smali / apktool 标记、图片数、so 数。 */
    private fun sniff(dir: File): Sniff? {
        val children = dir.listFiles() ?: return null
        var dex = 0
        var imgs = 0
        var so = 0
        var smali = false
        var mark = false
        val apks = mutableListOf<String>()
        for (c in children) {
            if (c.isFile) {
                val low = c.name.lowercase()
                when {
                    low.endsWith(".dex") -> dex++
                    low.endsWith(".apk") -> apks.add(c.name)
                    low.endsWith(".png") || low.endsWith(".jpg") ||
                        low.endsWith(".jpeg") || low.endsWith(".webp") -> imgs++
                    low.endsWith(".so") -> so++
                }
                if (apktoolMarks.contains(c.name)) mark = true
            } else if (c.isDirectory) {
                if (c.name.startsWith("smali")) smali = true
                // res 目录本身就说明这是解包现场
                if (c.name == "res") mark = true
            }
        }
        return Sniff(dex, apks, smali, mark, imgs, so)
    }

    /**
     * 判断这是什么。
     *
     * 顺序要紧：先看本层，本层没特征再看下一层。真实的任务目录是
     * `<包名>/decode/` 这种结构，apktool.yml 在 decode 里，只看 `<包名>/` 会是空的。
     */
    private fun classify(path: String): Classified {
        val dir = File(path)
        val own = sniff(dir) ?: return Classified("读不到", "目录读不了（权限或已删）", ADVICE_KEEP, "")

        var dexCount = own.dexCount
        var soCount = own.soCount
        var hasSmali = own.hasSmali
        var hasMark = own.hasApktoolMark
        val apks = own.apkNames.toMutableList()
        val images = own.imageCount

        val subs = (dir.listFiles() ?: emptyArray()).filter { it.isDirectory }
        for (sub in subs) {
            val s = sniff(sub) ?: continue
            dexCount += s.dexCount
            apks.addAll(s.apkNames)
            hasSmali = hasSmali || s.hasSmali
            hasMark = hasMark || s.hasApktoolMark
            soCount += s.soCount
            // 再深一层：lib/arm64-v8a/libjiagu.so 这种结构 so 在第三层，
            // 不下潜的话 jiagu_so/lib 会被判成「认不出」，而它恰恰最该删。
            if (sub.name == "lib" || sub.name.equals("jniLibs", true)) {
                (sub.listFiles() ?: emptyArray()).filter { it.isDirectory }.forEach { g ->
                    soCount += sniff(g)?.soCount ?: 0
                }
            }
        }

        val signedKeys = listOf("_signed", "_repack", "_fixed", "_v2", "_v3", "_output", "成品", "patched")
        val signed = apks.filter { a -> signedKeys.any { a.lowercase().contains(it) } }
        val pick = if (signed.isNotEmpty()) signed else apks

        if (apks.isNotEmpty() && (hasSmali || hasMark || dexCount >= 2)) {
            val more = if (apks.size <= 3) "" else "等 ${apks.size} 个"
            val names = apks.take(3).joinToString(", ")
            return Classified(
                "任务目录",
                "含成品 APK（$names$more）+ 中间产物（${dexCount} 个 dex" +
                    (if (hasSmali || hasMark) "、解包目录" else "") + "）",
                ADVICE_TAKE_FIRST,
                "这里面有能用的安装包，别整目录删。先把 ${pick.first()} 拷出去，剩下的再清。"
            )
        }
        if (apks.isNotEmpty()) {
            return Classified(
                "成品APK",
                "含 ${apks.size} 个 APK：${pick.take(3).joinToString(", ")}",
                ADVICE_KEEP,
                "这是重打包出来的安装包。删之前先确认已经拷到别处。"
            )
        }
        if (hasMark && hasSmali) {
            val extra = if (images > 0) "，res 里 $images 张图" else ""
            return Classified(
                "解包目录",
                "apktool 解包现场（含 smali 与 res$extra）",
                ADVICE_DELETE,
                "纯中间产物。res 里那些图标就是相册被污染的来源。"
            )
        }
        if (hasSmali) return Classified("解包残留", "有 smali 目录但不是完整解包", ADVICE_DELETE, "中间产物。")
        if (dexCount >= 3) {
            return Classified("脱壳dex", "$dexCount 个 dex", ADVICE_DELETE, "脱壳 dump 产物。成品包出来的话这批可以不要。")
        }
        if (dexCount > 0) return Classified("零散dex", "$dexCount 个 dex", ADVICE_DELETE, "中间产物。")
        if (hasMark) return Classified("半成品解包", "有 res/Manifest 但不完整", ADVICE_DELETE, "中间产物。")
        if (soCount > 0) {
            return Classified("so目录", "$soCount 个 so", ADVICE_DELETE, "解包/壳的 so 文件。壳的 libjiagu 就在这类目录里。")
        }
        if (images > 0) return Classified("图片目录", "$images 张图片", ADVICE_DELETE, "解包出来的资源图，相册污染源。")
        return Classified("其他", "没有明显逆向特征", ADVICE_KEEP, "认不出是什么，不动它。")
    }

    /**
     * 用户的样本库 / 资料库：子目录多、总量小、每个子目录都很小。
     *
     * 真机上 `/sdcard/加固特征.txt/` 下面按厂商分了 40 个子目录，若照着内容特征扫，
     * 每个子目录都会被单独报成「零散dex 可删」。三条同时满足才判定为样本库。
     */
    private fun isSampleLibrary(dir: File): Boolean {
        val subs = (dir.listFiles() ?: return false).filter { it.isDirectory }
        if (subs.size < 6) return false
        val (total, files) = sizeOf(dir)
        if (total >= 300L * 1048576) return false
        if (total / subs.size >= 40L * 1048576) return false
        return files <= subs.size * 80
    }

    // ---------------------------------------------------------------
    // 扫描
    // ---------------------------------------------------------------

    private fun addItem(out: MutableList<Item>, dir: File, note: String = "", force: Boolean = false) {
        val path = normalize(dir.absolutePath)
        if (path.isEmpty() || !dir.isDirectory) return
        if (!force) {
            if (forbiddenTrees().any { isUnder(path, it.first) }) return
            if (containerOnly().any { normalize(it) == path }) return
        }
        val c = classify(path)
        val (bytes, files) = sizeOf(dir)
        out.add(
            Item(
                path = path,
                kind = c.kind,
                desc = c.desc,
                advice = c.advice,
                reason = c.reason,
                note = note,
                bytes = bytes,
                fileCount = files,
                outsideWhitelist = !isWhitelisted(path)
            )
        )
    }

    /** 根目录下直接躺着的单个大文件也要报：成品包不一定带 .apk 后缀，光看名字会整个漏掉。 */
    private fun addFileItem(out: MutableList<Item>, file: File) {
        if (!file.isFile) return
        val path = normalize(file.absolutePath)
        if (forbiddenTrees().any { isUnder(path, it.first) }) return
        val low = file.name.lowercase()
        val (kind, desc, advice, reason) = when {
            low.endsWith(".apk") -> listOf(
                "成品APK", "单个 APK 文件", ADVICE_KEEP, "这是安装包。删之前先确认已经拷到别处。"
            )
            low.endsWith(".dex") -> listOf("零散dex", "散落在外的 dex", ADVICE_DELETE, "脱壳倒出来的中间产物。")
            low.endsWith(".so") -> listOf("so文件", "散落在外的 so", ADVICE_DELETE, "壳的 so 或中间产物。")
            else -> {
                // 没有扩展名的大文件：看开头两个字节，PK 就是 zip/apk。小于 1MB 的不折腾。
                if (file.length() < 1048576) return
                val magic = runCatching {
                    file.inputStream().use { ins ->
                        val buf = ByteArray(2)
                        ins.read(buf)
                        String(buf, Charsets.ISO_8859_1)
                    }
                }.getOrNull() ?: return
                if (magic != "PK") return
                listOf(
                    "压缩包/APK", "没有扩展名的大文件（开头是 PK，像 zip 或 apk）",
                    ADVICE_KEEP, "可能是重打包出来的成品。删之前先确认已拷到别处。"
                )
            }
        }
        out.add(
            Item(
                path = path,
                kind = kind,
                desc = desc,
                advice = advice,
                reason = reason,
                note = "根目录下的大文件",
                bytes = file.length(),
                fileCount = 1,
                outsideWhitelist = !isWhitelisted(path)
            )
        )
    }

    /**
     * 扫一遍，返回清单（不删任何东西）。
     *
     * 三遍走：
     *  ① 白名单根目录，每个子目录算一次任务；只有一个 lib/res 的根目录就报它自己。
     *  ①b 根目录下直接躺着的大文件。
     *  ② Download 与存储卡根下潜两层，按内容特征再捞一遍（AI 随手建的那些名字没规律）。
     */
    fun scan(): List<Item> {
        val out = mutableListOf<Item>()

        for (rootPath in whitelistRoots()) {
            val root = File(rootPath)
            if (!root.isDirectory) continue
            if (forbiddenTrees().any { isUnder(rootPath, it.first) }) continue
            val subs = (root.listFiles() ?: emptyArray()).filter { it.isDirectory }
            if (subs.isEmpty()) {
                addItem(out, root)
                continue
            }
            val onlyContainer = subs.size <= 2 && subs.all {
                it.name.lowercase() in listOf("lib", "assets", "res", "original", "jnilibs")
            }
            if (onlyContainer) addItem(out, root) else subs.forEach { addItem(out, it) }
        }

        for (rootPath in whitelistRoots()) {
            val root = File(rootPath)
            if (!root.isDirectory) continue
            if (forbiddenTrees().any { isUnder(rootPath, it.first) }) continue
            (root.listFiles() ?: emptyArray()).forEach { addFileItem(out, it) }
        }

        val huntRoots = listOf(File("$storageRoot/Download"), File(storageRoot))
        for (hr in huntRoots) {
            if (!hr.isDirectory) continue
            val level1 = (hr.listFiles() ?: emptyArray()).filter { it.isDirectory }
            for (p1 in level1) {
                val p1Path = normalize(p1.absolutePath)
                if (forbiddenTrees().any { isUnder(p1Path, it.first) }) continue
                if (containerOnly().any { normalize(it) == p1Path }) continue
                if (isSampleLibrary(p1)) continue
                val s = sniff(p1) ?: continue
                if (s.dexCount > 0 || s.apkNames.isNotEmpty() || s.hasSmali || s.hasApktoolMark ||
                    s.soCount > 0 || s.imageCount > 0
                ) {
                    addItem(out, p1)
                }
                // 再下一层：<子目录>/decode/ 这种结构
                val level2 = (p1.listFiles() ?: emptyArray()).filter { it.isDirectory }
                for (p2 in level2) {
                    val p2Path = normalize(p2.absolutePath)
                    if (forbiddenTrees().any { isUnder(p2Path, it.first) }) continue
                    if (isUnder(p1Path, "$storageRoot/Download/Operit")) continue
                    val s2 = sniff(p2) ?: continue
                    if (s2.dexCount > 0 || s2.apkNames.isNotEmpty() || s2.hasSmali ||
                        s2.hasApktoolMark || s2.soCount > 0
                    ) {
                        addItem(out, p2)
                    }
                }
            }
        }

        // 同一路径只留一条（两遍扫描可能撞上），按体积从大到小排，先看大的。
        return out.distinctBy { it.path }.sortedByDescending { it.bytes }
    }

    // ---------------------------------------------------------------
    // 删除
    // ---------------------------------------------------------------

    /** 返回拒绝原因；空串表示放行。 */
    fun refuseReason(rawPath: String, allowOutside: Boolean): String {
        val p = normalize(rawPath)
        if (p.isEmpty()) return "空路径"

        if (forbiddenExact().any { normalize(it) == p }) return "这是受保护目录，不删"

        val root = storageRoot
        // 根目录判据看结构，不看字符长度：/sdcard/adx 只有 11 个字符，
        // 但它是 108MB 的脱壳产物，路径短不等于它是根目录。
        if (p == root || root.startsWith("$p/")) return "这是存储卡根目录（或它的上级），不删"

        for ((tree, why) in forbiddenTrees()) {
            if (isUnder(p, tree)) return why
        }

        if (isWhitelisted(p)) return ""

        if (!allowOutside) {
            return "不在逆向产物白名单内。确实要删它，把上面的「允许白名单外」打开再来一次"
        }
        if (!looksLikeArtifact(p)) {
            return "白名单外，且内容不像逆向产物（有成品APK、或只有一个dex），不删"
        }
        return ""
    }

    /**
     * 只认「纯中间产物」：解包现场 / 脱壳 dex / 零散 dex / so 目录 / 图片目录。
     * 有成品 APK 的一律不放行；只有一个 dex 的也不放行（可能是用户放的单个样本）。
     */
    private fun looksLikeArtifact(path: String): Boolean {
        val f = File(path)
        if (f.isFile) {
            val low = f.name.lowercase()
            return low.endsWith(".dex") || low.endsWith(".so")
        }
        val s = sniff(f) ?: return false
        if (s.apkNames.isNotEmpty()) return false
        if (s.hasSmali || s.hasApktoolMark) return true
        if (s.dexCount >= 2) return true
        if (s.soCount > 0) return true
        if (s.imageCount > 0 && s.dexCount == 0) return true
        return false
    }

    /**
     * 真删。调用方必须先把 [scan] 的结果给用户看过、并且用户点了确认。
     *
     * [allowOutside] 对应界面上的「允许删除白名单外的路径」，默认必须是 false。
     */
    fun purge(paths: List<String>, allowOutside: Boolean = false): Outcome {
        val deleted = mutableListOf<Item>()
        val refused = mutableListOf<Refusal>()
        val failed = mutableListOf<Refusal>()
        var released = 0L

        for (raw in paths.distinct()) {
            val why = refuseReason(raw, allowOutside)
            if (why.isNotEmpty()) {
                refused.add(Refusal(raw, why))
                continue
            }
            val f = File(raw)
            if (!f.exists()) {
                refused.add(Refusal(raw, "不存在（可能已经删过）"))
                continue
            }
            val path = normalize(f.absolutePath)
            val c = classify(path)
            val (bytes, files) = sizeOf(f)
            val item = Item(
                path = path,
                kind = c.kind,
                desc = c.desc,
                advice = c.advice,
                reason = c.reason,
                note = "",
                bytes = bytes,
                fileCount = files,
                outsideWhitelist = !isWhitelisted(path)
            )
            val ok = runCatching { f.deleteRecursively() }.getOrDefault(false)
            if (ok && !f.exists()) {
                deleted.add(item)
                released += bytes
            } else {
                failed.add(Refusal(path, "删不掉（权限不足，或文件被别的程序占用）"))
            }
        }
        return Outcome(deleted, refused, failed, released)
    }

    /** 给解包目录补 .nomedia，挡住媒体扫描器把图标收进相册。返回真正写了的目录。 */
    fun writeNomedia(dirs: List<String>, dryRun: Boolean): List<String> {
        val written = mutableListOf<String>()
        val targets = if (dirs.isEmpty()) whitelistRoots() else dirs
        for (d in targets) {
            val dir = File(d)
            if (!dir.isDirectory) continue
            val mark = File(dir, ".nomedia")
            if (mark.exists()) continue
            if (!dryRun) {
                runCatching { mark.createNewFile() }.getOrDefault(false)
            }
            written.add(normalize(dir.absolutePath))
        }
        return written
    }
}
