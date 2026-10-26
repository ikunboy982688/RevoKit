package com.ai.assistance.operit.core.revokit

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.zip.ZipFile

/** 条目分组：按逆向时的关心程度排，dex / so 最前 */
enum class ApkEntryGroup(val label: String, val order: Int) {
    DEX("dex", 0),
    NATIVE("native so", 1),
    ASSETS("assets", 2),
    CONFIG("清单/资源", 3),
    META("签名/META-INF", 4),
    OTHER("其他", 5)
}

data class ApkEntry(
        val name: String,
        val size: Long,
        val compressedSize: Long,
        val group: ApkEntryGroup
) {
    /** dex/so 才是真正要抽出来看的东西 */
    val interesting: Boolean
        get() = group == ApkEntryGroup.DEX || group == ApkEntryGroup.NATIVE
}

/** 解包浏览 + 抽取 */
object ApkBrowser {

    private const val TAG = "ApkBrowser"

    private val DEX_REGEX = Regex("^classes\\d*\\.dex$")

    fun list(apk: File): List<ApkEntry> {
        val out = mutableListOf<ApkEntry>()
        ZipFile(apk).use { zip ->
            val it = zip.entries()
            while (it.hasMoreElements()) {
                val e = it.nextElement()
                if (e.isDirectory) continue
                out +=
                        ApkEntry(
                                name = e.name,
                                size = e.size,
                                compressedSize = e.compressedSize,
                                group = groupOf(e.name)
                        )
            }
        }
        return out.sortedWith(compareBy<ApkEntry> { it.group.order }.thenBy { it.name })
    }

    private fun groupOf(name: String): ApkEntryGroup =
            when {
                DEX_REGEX.matches(name) -> ApkEntryGroup.DEX
                name.startsWith("lib/") && name.endsWith(".so") -> ApkEntryGroup.NATIVE
                name.startsWith("assets/") -> ApkEntryGroup.ASSETS
                name.startsWith("META-INF/") -> ApkEntryGroup.META
                name == "AndroidManifest.xml" ||
                        name == "resources.arsc" ||
                        name.startsWith("res/") -> ApkEntryGroup.CONFIG
                else -> ApkEntryGroup.OTHER
            }

    /** 导出目录：应用专属外部目录，不需要任何存储权限，文件管理器能直接看到 */
    fun exportDir(context: Context, apk: File): File {
        val base = context.getExternalFilesDir("revokit_export")
                ?: File(context.filesDir, "revokit_export")
        val dir = File(base, apk.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._\\-]"), "_"))
        dir.mkdirs()
        return dir
    }

    /**
     * 抽取指定条目。返回成功写出的文件数。
     * 文件名扁平化（把 / 换成 _），避免不同目录同名 so 互相覆盖。
     */
    fun extract(apk: File, names: Collection<String>, destDir: File): Int {
        var count = 0
        destDir.mkdirs()
        ZipFile(apk).use { zip ->
            for (name in names) {
                val entry = zip.getEntry(name) ?: continue
                if (entry.isDirectory) continue
                val flat = name.replace('/', '_').replace('\\', '_')
                val target = File(destDir, flat)
                try {
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                    }
                    count++
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "extract $name failed: ${t.message}")
                }
            }
        }
        return count
    }
}
