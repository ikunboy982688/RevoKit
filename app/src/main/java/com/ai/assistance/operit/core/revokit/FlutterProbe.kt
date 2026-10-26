package com.ai.assistance.operit.core.revokit

import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.zip.ZipFile

/**
 * Flutter APK 体检
 *
 * Flutter 的代码不在 dex 里，全在 libapp.so 的 Dart AOT 快照里，所以普通反编译工具看不到业务逻辑。
 * 这里只做结构判断：是不是 Flutter、release 还是 debug、引擎版本、libapp 在哪些 ABI、快照段在哪。
 * 真正的函数符号还原要靠 PC 端的 blutter，本机做不了（需要完整 Dart SDK 表）。
 */
object FlutterProbe {

    private const val TAG = "FlutterProbe"

    private const val MAX_SCAN_BYTES = 16L * 1024 * 1024

    private val VERSION_RE = Regex("(\\d+\\.\\d+\\.\\d+) \\((stable|beta|dev|main)\\)")
    private val DART_VM_RE = Regex("Dart VM version:\\s*(\\d+\\.\\d+\\.\\d+)")

    data class Result(
            val isFlutter: Boolean,
            val mode: String,
            val engineVersion: String?,
            val dartVersion: String?,
            val abis: List<String>,
            val libapp: List<String>,
            val evidence: List<String>,
            val notes: List<String>
    )

    fun probe(apk: File): Result {
        val evidence = ArrayList<String>()
        val notes = ArrayList<String>()
        val abis = LinkedHashSet<String>()
        val libapp = ArrayList<String>()
        var engine: String? = null
        var dart: String? = null
        var hasKernel = false
        var hasAotSnapshot = false
        var flutterSo: String? = null

        try {
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    val n = e.name
                    when {
                        n.startsWith("lib/") && n.endsWith("/libflutter.so") -> {
                            flutterSo = n
                            abis.add(n.split('/')[1])
                            evidence.add("引擎库 $n（${e.size / 1024}KB）")
                        }
                        n.startsWith("lib/") && n.endsWith("/libapp.so") -> {
                            libapp.add(n)
                            abis.add(n.split('/')[1])
                            hasAotSnapshot = true
                            evidence.add("业务代码 $n（${e.size / 1024}KB，Dart AOT 快照）")
                        }
                        n == "assets/flutter_assets/kernel_blob.bin" -> {
                            hasKernel = true
                            evidence.add("kernel_blob.bin：JIT 中间产物，说明是 debug 构建")
                        }
                        n.startsWith("assets/flutter_assets/") &&
                                (n.endsWith("isolate_snapshot_data") ||
                                        n.endsWith("vm_snapshot_data")) -> {
                            evidence.add("JIT 快照 ${n.substringAfterLast('/')}")
                        }
                        n == "assets/flutter_assets/AssetManifest.json" ||
                                n == "assets/flutter_assets/AssetManifest.bin" -> {
                            evidence.add("Flutter 资源清单 ${n.substringAfterLast('/')}")
                        }
                    }
                }

                if (flutterSo != null) {
                    val head = readHead(zip, flutterSo, MAX_SCAN_BYTES)
                    if (head != null) {
                        VERSION_RE.find(String(head, Charsets.ISO_8859_1))?.let {
                            engine = "${it.groupValues[1]} (${it.groupValues[2]})"
                        }
                        DART_VM_RE.find(String(head, Charsets.ISO_8859_1))?.let { dart = it.groupValues[1] }
                    }
                }
                if (engine == null && libapp.isNotEmpty()) {
                    val head = readHead(zip, libapp.first(), MAX_SCAN_BYTES)
                    if (head != null) {
                        VERSION_RE.find(String(head, Charsets.ISO_8859_1))?.let {
                            engine = "${it.groupValues[1]} (${it.groupValues[2]})"
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "probe failed: ${t.message}")
            notes.add("读取 APK 出错：${t.message ?: t.javaClass.simpleName}")
        }

        val isFlutter = flutterSo != null || hasKernel || hasAotSnapshot
        val mode =
                when {
                    hasAotSnapshot && !hasKernel -> "AOT（release / profile 构建）"
                    hasKernel -> "JIT（debug 构建，业务代码在 kernel_blob.bin）"
                    isFlutter -> "疑似 Flutter，但没找到代码载体"
                    else -> "不是 Flutter"
                }

        if (isFlutter) {
            notes.add("业务逻辑在 libapp.so 的 Dart 快照里，dex 反编译看不到；常规脱壳也脱不出它")
            notes.add("要看 Dart 层函数名，需要在 PC 上用 blutter 解析 libapp.so（需匹配的 Dart SDK 表）")
            if (hasAotSnapshot && abis.size > 1) notes.add("多个 ABI 各有一份 libapp.so，建议先挑 arm64-v8a 分析")
        }

        return Result(
                isFlutter = isFlutter,
                mode = mode,
                engineVersion = engine,
                dartVersion = dart,
                abis = abis.toList(),
                libapp = libapp,
                evidence = evidence,
                notes = notes
        )
    }

    /** 只读前 limit 字节，避免把 20MB 的 so 整个读进内存 */
    private fun readHead(zip: ZipFile, name: String, limit: Long): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        return try {
            zip.getInputStream(entry).use { input ->
                val cap = minOf(limit, if (entry.size > 0) entry.size else limit)
                val buf = java.io.ByteArrayOutputStream(cap.toInt())
                val chunk = ByteArray(1 shl 16)
                var total = 0L
                while (total < limit) {
                    val n = input.read(chunk)
                    if (n <= 0) break
                    val take = minOf(n.toLong(), limit - total).toInt()
                    buf.write(chunk, 0, take)
                    total += take
                    if (take < n) break
                }
                buf.toByteArray()
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "readHead $name failed: ${t.message}")
            null
        }
    }
}
