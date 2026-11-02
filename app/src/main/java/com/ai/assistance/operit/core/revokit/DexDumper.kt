package com.ai.assistance.operit.core.revokit

import android.content.Context
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * RevoKit 内存脱壳引擎（root 路线）
 *
 * 走应用自带的 RootAuthorizer（libsu + KernelSU + exec 三种模式），不自己找 su 二进制。
 * 「在固定路径里找 su 文件」这种写法在新 Magisk / KernelSU 上必然失败：
 * systemless 的 su 不在 /system/xbin，而 /data/adb/magisk 这类目录 App 根本没权限 stat。
 *
 * dump 流程：
 * 1. RootAuthorizer 拿 root，没授权就主动弹 su 管理器的授权框
 * 2. 读 /proc/pid/maps，挑出可能是 dex 的内存区（匿名段、dalvik 段、ashmem、memfd）
 * 3. root 侧用 dd 把整段写到 /data/local/tmp，chmod 666 后由 App 直接读文件
 *    （不再从管道里读二进制，省掉 base64 开销和 IOException）
 * 4. 在字节流里扫 "dex\n" magic，从 magic + 0x20 读 file_size 整块切出来
 * 5. sha1 去重后落盘
 *
 * 这里只负责把内存里的东西原样搬出来，不做任何修改；修头 / 清 tries / 回填在 DexRepair 里做。
 */
object DexDumper {

    private const val TAG = "DexDumper"

    private const val PAGE = 4096
    private const val MAX_REGION_BYTES = 64L * 1024 * 1024
    private const val MIN_REGION_BYTES = 16 * 1024
    private const val MAX_TOTAL_BYTES = 384L * 1024 * 1024
    /** 单个 dex 的合理上限，超过就当是误判的 magic */
    private const val MAX_REASONABLE_DEX = 64L * 1024 * 1024
    private const val TMP_DIR = "/data/local/tmp/revokit_dump"
    private const val CHUNK = 1 shl 16

    data class RootState(val rooted: Boolean, val detail: String, val idOutput: String = "")

    data class AppProcess(val pid: Int, val name: String)

    data class Region(
            val start: Long,
            val end: Long,
            val perms: String,
            val name: String
    ) {
        val size: Long get() = end - start
        val readable: Boolean get() = perms.startsWith("r")
    }

    data class Dumped(
            val file: File,
            val size: Long,
            val version: Int,
            val sha1: String,
            val region: String,
            val checksumOk: Boolean,
            val signatureOk: Boolean,
        val methodCount: Int,
        /** 类数量。真实业务 dex 通常几千个，壳的加载器只有一两千 */
        val classCount: Int = 0,
        /** 来源：文件直接拷贝，还是内存段地址 */
        val origin: String = "",
        /** 类型：dex / cdex / oat / vdex / zip / 未知，或体检判定（真实 dex / 疑似容器） */
        val kind: String = "dex"
    )

    data class DumpResult(
            val dumped: List<Dumped>,
            val regionsScanned: Int,
            val bytesScanned: Long,
            val log: List<String>
    )

    // -------------------------------------------------------------- root 检测

    /** 拿 root。没授权会触发 su 管理器的授权弹窗，失败时给出可排查的原因 */
    suspend fun checkRoot(context: Context): RootState = withContext(Dispatchers.IO) {
        val granted =
                try {
                    RootAuthorizer.checkRootStatus(context)
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "checkRootStatus 异常", t)
                    false
                }

        if (granted) {
            val (ok, out) = RootAuthorizer.executeRootCommand("id", context)
            val idText = if (ok) out.trim() else ""
            return@withContext RootState(true, "已拿到 root 权限", idText)
        }

        val requested = requestPermissionOnce()
        if (requested) {
            val (ok, out) = RootAuthorizer.executeRootCommand("id", context)
            if (ok && out.contains("uid=0")) {
                return@withContext RootState(true, "root 授权成功", out.trim())
            }
        }

        RootState(
                rooted = false,
                detail =
                        "拿不到 root 权限，请依次确认：\n" +
                                "1. 设备确实已 root（Magisk / KernelSU / APatch 任意一个）\n" +
                                "2. su 管理器的超级用户列表里，本应用是「允许」\n" +
                                "3. KernelSU / APatch 的话，到设置里把 Root 执行模式改成「强制 exec」\n" +
                                "4. 确认完回本页点「重新检测」"
        )
    }

    private suspend fun requestPermissionOnce(): Boolean =
            suspendCancellableCoroutine { cont ->
                try {
                    RootAuthorizer.requestRootPermission { granted -> cont.resume(granted) }
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "请求 root 授权异常", t)
                    cont.resume(false)
                }
            }

    // -------------------------------------------------------------- 进程列表

    /**
     * 保证 RootAuthorizer 已经完成初始化。
     *
     * 这一步不能省。RootAuthorizer 是单例，_hasRootAccess 的初值是 false，只有
     * initialize() / checkRootStatus() 被调用过才会变成真实状态。首次进对话就让 AI
     * 调本工具时，前面没有任何界面碰过 RootAuthorizer，executeRootCommand 会直接走
     * `!_hasRootAccess.value` 分支返回 false —— 整条命令 1 毫秒就"完成"，ps 输出为空，
     * listProcesses 落到 fallback 的普通 ps（只看得到自己 uid 的进程），
     * 最终表现成「没找到进程」，很容易被误判成 SELinux 拦截。
     * 实测 360 加固（com.juyingfun.mqjz，PID 11175）首次调用就是这个现象。
     */
    private fun ensureRoot(context: Context) {
        if (RootAuthorizer.hasRootAccess.value) return
        try {
            RootAuthorizer.checkRootStatus(context)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "ensureRoot 检测失败", t)
        }
    }

    suspend fun listProcesses(context: Context): List<AppProcess> = withContext(Dispatchers.IO) {
        ensureRoot(context)
        val (ok, out) = RootAuthorizer.executeRootCommand("ps -A -o PID,NAME", context)
        val text =
                if (ok && out.isNotBlank()) out
                else {
                    val fallback = RootAuthorizer.executeRootCommand("ps -A", context)
                    if (fallback.first) fallback.second else ""
                }
        val list = ArrayList<AppProcess>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("PID")) continue
            val cols = t.split(Regex("\\s+"))
            if (cols.size < 2) continue
            val pid = cols[0].toIntOrNull() ?: continue
            if (pid <= 0) continue
            list.add(AppProcess(pid, cols.last()))
        }
        list.sortedBy { it.name }
    }

    // ------------------------------------------------------------------ maps

    suspend fun readMaps(context: Context, pid: Int): List<Region> = withContext(Dispatchers.IO) {
        ensureRoot(context)
        val (ok, out) = RootAuthorizer.executeRootCommand("cat /proc/$pid/maps", context)
        if (!ok || out.isBlank()) return@withContext emptyList<Region>()
        val regions = ArrayList<Region>()
        for (line in out.lineSequence()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val cols = t.split(Regex("\\s+"), limit = 6)
            if (cols.size < 2) continue
            val range = cols[0]
            val dash = range.indexOf('-')
            if (dash <= 0) continue
            val start = range.substring(0, dash).toLongOrNull(16) ?: continue
            val end = range.substring(dash + 1).toLongOrNull(16) ?: continue
            if (end <= start) continue
            regions.add(
                    Region(
                            start = start,
                            end = end,
                            perms = cols[1],
                            name = if (cols.size >= 6) cols[5] else ""
                    )
            )
        }
        regions
    }

    /**
     * 只留下值得读的段。
     *
     * 注意 /dev/ashmem 必须放行 —— 乐固、梆梆这类壳把解密后的 dex mmap 在匿名共享内存里，
     * 段名就是 /dev/ashmem/dalvik-xxx。早先按「/dev/ 开头一律扔掉」过滤，结果真实 dex 全被扔了。
     * 这里只排除 /dev/ 下的其它节点（kgsl、ashmem 之外的设备映射）。
     */
    fun candidateRegions(regions: List<Region>): List<Region> {
        val out = ArrayList<Region>()
        for (r in regions) {
            if (!r.readable) continue
            if (r.size <= 0 || r.size > MAX_REGION_BYTES) continue
            val n = r.name
            // 纯噪音：栈、TLS、vdso
            if (n.startsWith("[anon:stack") || n.startsWith("[anon:thread") || n.startsWith("[vdso")) continue
            // ART 自己的内部结构。实测（360 加固 com.juyingfun.mqjz）这些段加起来
            // 能吃掉整个 384MB 预算，里面一个 dex 都没有，把真正装着 dex 的段挤到了队尾。
            if (n.contains("mark stack") || n.contains("bitmap") ||
                            n.contains("live stack") || n.contains("allocation stack") ||
                            n.contains("zygote space") || n.contains("boot-")
            ) continue
            // 系统分区里的东西不是我们的目标（但 /data 下的 dex 缓存要留）
            if (n.startsWith("/system/") || n.startsWith("/vendor/") ||
                            n.startsWith("/apex/") || n.startsWith("/proc/") ||
                            n.startsWith("/sys/")
            ) continue
            // /dev/ 只放行 ashmem，其它设备节点（显存、ion、dmabuf）不可能有 dex
            if (n.startsWith("/dev/") && !n.startsWith("/dev/ashmem")) continue
            if (n.endsWith(".so") || n.endsWith(".ttf") || n.endsWith(".otf")) continue
            // 壳自己的 base.apk 里是壳 dex，扫了也是白扫
            if (n.endsWith(".apk") && n.contains("/app/")) continue
            if (r.size < MIN_REGION_BYTES) continue
            val keep =
                    n.isEmpty() ||
                            n.startsWith("[anon") ||
                            n.contains("dalvik") ||
                            n.contains("ashmem") ||
                            n.contains("memfd") ||
                            n.contains("jit-cache") ||
                            n.contains("InMemoryDex") ||
                            n.contains("dex") ||
                            n.endsWith(".dex") ||
                            n.endsWith(".vdex") ||
                            n.endsWith(".odex") ||
                            n.endsWith(".oat")
            if (!keep) continue
            out.add(r)
        }
        return out.sortedByDescending { score(it) }
    }

    private fun score(r: Region): Int {
        var s = 0
        val n = r.name
        // 空名的匿名段是壳解密 dex 的主战场，必须排最前。
        // 实测 360 加固（com.juyingfun.mqjz）的 3 个业务 dex 就躺在 19MB 的空名 rwxp 段里，
        // 而 dalvik 的 GC 栈 / bitmap / jit-cache 段加起来一个 dex 都没有。
        // 这里原来只给 +15 分，排序后落到 291 个候选的末尾，
        // 还没轮到 384MB 预算就耗尽了 —— 最终只拿到 1 个误判的 dex。
        if (n.isEmpty()) s += 90
        if (n.contains("InMemoryDex")) s += 55
        if (n.contains("dalvik")) s += 45
        if (n.contains("memfd")) s += 35
        if (n.contains("jit-cache")) s += 25
        if (n.endsWith(".dex")) s += 30
        if (n.endsWith(".vdex") || n.endsWith(".odex") || n.endsWith(".oat")) s += 20
        if (n.contains("ashmem")) s += 60
        if (n.startsWith("[anon")) s += 10
        if (r.perms.contains("w")) s += 5
        if (r.size > 1024 * 1024) s += 5
        return s
    }

    // ------------------------------------------------------------------ dump

    suspend fun dump(
            context: Context,
            pid: Int,
            pkg: String,
            outDir: File,
            onProgress: (String) -> Unit = {}
    ): DumpResult = withContext(Dispatchers.IO) {
        val log = ArrayList<String>()
        fun note(s: String) {
            log.add(s)
            AppLogger.i(TAG, s)
            onProgress(s)
        }

        outDir.mkdirs()
        ensureRoot(context)
        RootAuthorizer.executeRootCommand("mkdir -p $TMP_DIR", context)
        RootAuthorizer.executeRootCommand("chmod 777 $TMP_DIR", context)

        val dumped = ArrayList<Dumped>()
        val seen = HashSet<String>()

        note("第一步：直接拷贝目标私有的 dex 与容器文件")
        dumped.addAll(collectFiles(context, pkg, outDir, seen) { note(it) })

        note("第二步：扫进程内存")
        val maps = readMaps(context, pid)
        if (maps.isEmpty()) {
            note("读不到 /proc/$pid/maps。进程可能已被杀，或者被 SELinux 拦了")
            return@withContext DumpResult(dumped, 0, 0L, log)
        }
        val candidates = candidateRegions(maps)
        note("maps 共 ${maps.size} 段，候选 ${candidates.size} 段")

        var totalBytes = 0L
        var scanned = 0

        for (r in candidates) {
            if (totalBytes >= MAX_TOTAL_BYTES) {
                note("已扫够 ${MAX_TOTAL_BYTES / 1024 / 1024}MB，停止（避免占满存储）")
                break
            }
            val tmp = "$TMP_DIR/reg_" + java.lang.Long.toHexString(r.start) + ".bin"
            val skip = r.start / PAGE
            val count = r.size / PAGE
            if (count <= 0) continue

            // conv=noerror,sync 不能省。
            // 内存段中间经常夹着尚未分配的页，不带它时 dd 读到第一个空洞就 EIO 退出，
            // 整段被下面的 ok=false 判成「读不通」扔掉。
            // 实测 360 加固（com.juyingfun.mqjz）那个 19MB 匿名段里有 3 个完整 dex，
            // 不带 noerror 一个都拿不到，带上就完整读出来了。
            // 末尾用 ; 而不是 &&：dd 中途报错也要把已读到的部分 chmod 出来，
            // 真正「完全没读到」的情况由后面的 local.length() <= 0 兜住。
            val ddCmd =
                    "dd if=/proc/$pid/mem of=$tmp bs=$PAGE skip=$skip count=$count " +
                            "conv=noerror,sync 2>/dev/null; chmod 666 $tmp"
            val (ok, _) = RootAuthorizer.executeRootCommand(ddCmd, context)
            val local = File(tmp)
            if (!ok || !local.exists() || local.length() <= 0L) {
                // 读不通的段很正常（EIO），跳过即可
                RootAuthorizer.executeRootCommand("rm -f $tmp", context)
                continue
            }
            scanned++
            totalBytes += local.length()
            note("扫描段 ${hexAddr(r.start)}  ${local.length() / 1024}KB  " + r.name.ifEmpty { "[anon]" })
            try {
                extractDex(local, r, outDir, dumped, seen) { line -> note(line) }
            } catch (t: Throwable) {
                note("解析该段出错：${t.message}")
            }
            try {
                local.delete()
            } catch (t: Throwable) {
                AppLogger.w(TAG, "删临时文件失败：${t.message}")
            }
            RootAuthorizer.executeRootCommand("rm -f $tmp", context)
        }

        note("扫完 $scanned 段、共 ${totalBytes / 1024 / 1024}MB，拿到 ${dumped.size} 个 dex")
        DumpResult(dumped.sortedByDescending { it.size }, scanned, totalBytes, log)
    }

    /**
     * 第一步：把目标 App 私有目录和 dalvik-cache 里的 dex / 容器直接拷出来。
     *
     * 很多壳会把解出来的 dex 落成文件再加载（比如 files/proxydex 目录下的 dex、dalvik-cache 里的 vdex），
     * 这些直接用 cp 拿最稳，比扫内存靠谱得多。
     */
    private suspend fun collectFiles(
            context: Context,
            pkg: String,
            outDir: File,
            seen: MutableSet<String>,
            log: (String) -> Unit
    ): List<Dumped> {
        val out = ArrayList<Dumped>()
        val filesDir = File(outDir, "files").apply { mkdirs() }
        val tmpFiles = "$TMP_DIR/files"
        RootAuthorizer.executeRootCommand("mkdir -p $tmpFiles && chmod 777 $tmpFiles", context)

        val findCmd =
                "find /data/data/$pkg /data/user/0/$pkg /data/user_de/0/$pkg " +
                        "-type f -size -80M 2>/dev/null | grep -E '\\.dex$|\\.jar$|\\.apk$|\\.odex$|\\.vdex$|\\.zip$' ; " +
                        "find /data/dalvik-cache -type f 2>/dev/null | grep -i '$pkg'"
        val (ok, listing) = RootAuthorizer.executeRootCommand(findCmd, context)
        if (!ok || listing.isBlank()) {
            log("私有目录里没找到 dex / 容器文件")
            return out
        }

        val paths =
                listing.lineSequence()
                        .map { it.trim() }
                        .filter { it.startsWith("/") }
                        .distinct()
                        .take(60)
                        .toList()

        for (p in paths) {
            val flat = p.trimStart('/').replace('/', '_').replace(Regex("[^A-Za-z0-9._-]"), "_")
            val tmp = "$tmpFiles/$flat"
            val (cpOk, _) =
                    RootAuthorizer.executeRootCommand("cp '$p' $tmp 2>/dev/null && chmod 666 $tmp", context)
            val tmpFile = File(tmp)
            if (!cpOk || !tmpFile.exists() || tmpFile.length() <= 0L) continue

            val target = File(filesDir, flat)
            try {
                tmpFile.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
            } catch (t: Throwable) {
                AppLogger.e(TAG, "落地文件失败 $p", t)
                continue
            }
            RootAuthorizer.executeRootCommand("rm -f $tmp", context)

            val bytes = target.readBytes()
            val sha1 = sha1Hex(bytes)
            val kind = kindOf(bytes)
            log("拷到文件 ${target.name}  ${bytes.size / 1024}KB  类型 $kind")

            if (kind == "dex") {
                if (!seen.add(sha1)) continue
                val ins = DexRepair.inspect(bytes)
                val q = DexRepair.quality(bytes)
                out.add(
                        Dumped(
                                file = target,
                                size = bytes.size.toLong(),
                                version = ins.header?.version ?: 0,
                                sha1 = sha1,
                                region = "文件",
                                checksumOk = ins.checksumOk,
                                signatureOk = ins.signatureOk,
                                methodCount = ins.methodCount,
                                classCount = ins.classCount,
                                origin = p,
                                kind = q.level
                        )
                )
            } else if (kind == "zip") {
                // jar / apk 里可能塞着 classes*.dex
                try {
                    java.util.zip.ZipFile(target).use { zip ->
                        zip.entries().toList().filter { it.name.endsWith(".dex") && !it.isDirectory }.forEach { e ->
                            val dexBytes = zip.getInputStream(e).use { it.readBytes() }
                            val dsha = sha1Hex(dexBytes)
                            if (seen.add(dsha)) {
                                val ins = DexRepair.inspect(dexBytes)
                                val q = DexRepair.quality(dexBytes)
                                val f = File(filesDir, flat + "_" + e.name.replace('/', '_'))
                                f.writeBytes(dexBytes)
                                out.add(
                                        Dumped(
                                                file = f,
                                                size = dexBytes.size.toLong(),
                                                version = ins.header?.version ?: 0,
                                                sha1 = dsha,
                                                region = "zip 内",
                                                checksumOk = ins.checksumOk,
                                                signatureOk = ins.signatureOk,
                                                methodCount = ins.methodCount,
                                                classCount = ins.classCount,
                                                origin = "$p!/${e.name}",
                                                kind = q.level
                                        )
                                )
                                log(
                                        "从 $flat 里抽出 ${e.name}  ${dexBytes.size / 1024}KB  " +
                                                "类 ${ins.classCount}  判定「${q.level}」"
                                )
                            }
                        }
                    }
                } catch (t: Throwable) {
                    log("拆 $flat 失败：${t.message}")
                }
            }
        }
        return out
    }

    /** 判断文件类型，看前 4 个字节 */
    private fun kindOf(b: ByteArray): String {
        if (b.size < 4) return "未知"
        return when {
            b[0] == 'd'.code.toByte() && b[1] == 'e'.code.toByte() &&
                    b[2] == 'x'.code.toByte() && b[3] == '\n'.code.toByte() -> "dex"
            b[0] == 'c'.code.toByte() && b[1] == 'd'.code.toByte() &&
                    b[2] == 'e'.code.toByte() && b[3] == 'x'.code.toByte() -> "cdex"
            b[0] == 'o'.code.toByte() && b[1] == 'a'.code.toByte() &&
                    b[2] == 't'.code.toByte() && b[3] == '\n'.code.toByte() -> "oat"
            b[0] == 'v'.code.toByte() && b[1] == 'd'.code.toByte() &&
                    b[2] == 'e'.code.toByte() && b[3] == 'x'.code.toByte() -> "vdex"
            b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() -> "zip"
            else -> "未知"
        }
    }

    /** 在 dump 出来的区域文件里扫 dex magic，切出完整块 */
    private fun extractDex(
            src: File,
            region: Region,
            outDir: File,
            out: MutableList<Dumped>,
            seen: MutableSet<String>,
            log: (String) -> Unit
    ) {
        RandomAccessFile(src, "r").use { raf ->
            val len = raf.length()
            val buf = ByteArray(CHUNK)
            var pos = 0L
            var truncatedHeads = 0
            while (pos < len - 8) {
                raf.seek(pos)
                val n = raf.read(buf, 0, minOf(CHUNK.toLong(), len - pos).toInt())
                if (n <= 4) break

                var i = 0
                while (i < n - 4) {
                    if (buf[i] != 'd'.code.toByte() ||
                                    buf[i + 1] != 'e'.code.toByte() ||
                                    buf[i + 2] != 'x'.code.toByte() ||
                                    buf[i + 3] != '\n'.code.toByte()
                    ) {
                        i++
                        continue
                    }
                    val abs = pos + i
                    val head = readHeader(raf, abs)
                    if (head == null) {
                        i += 4
                        continue
                    }
                    // 注意：head 只有 0x70 字节，不能拿它去调 looksLikeCompleteDex
                    // —— 那个函数会校验「整块 dex 装不装得下」，传小 buffer 必然返回 0。
                    // 这里只从头部读 file_size，越界判断要拿整个区域的长度去比。
                    val size = fileSizeFromHeader(head)
                    if (size <= 0) {
                        i += 4
                        continue
                    }
                    if (abs + size > len) {
                        truncatedHeads++
                        i += 4
                        continue
                    }
                    val slice = ByteArray(size.toInt())
                    raf.seek(abs)
                    if (raf.read(slice) != size.toInt()) {
                        i += 4
                        continue
                    }
                    val sha1 = sha1Hex(slice)
                    if (seen.add(sha1)) {
                        val ins = DexRepair.inspect(slice)
                        val ver = ins.header?.version ?: 0
                        val q = DexRepair.quality(slice)
                        val file =
                                File(outDir, "dump_" + (out.size + 1) + "_v" + ver + "_" + sha1.substring(0, 8) + ".dex")
                        file.writeBytes(slice)
                        out.add(
                                Dumped(
                                        file = file,
                                        size = size,
                                        version = ver,
                                        sha1 = sha1,
                                        region = hexAddr(region.start),
                                        checksumOk = ins.checksumOk,
                                        signatureOk = ins.signatureOk,
                                        methodCount = ins.methodCount,
                                        origin = "内存 " + hexAddr(abs),
                                        kind = q.level,
                                        classCount = ins.classCount
                                )
                        )
                        log(
                                "命中 dex v$ver  ${size / 1024}KB  类 ${ins.classCount} 方法 ${ins.methodCount}  " +
                                        "判定「${q.level}」  校验" +
                                        if (ins.checksumOk && ins.signatureOk) "通过"
                                        else "不符（需要修头）"
                        )
                    }
                    i += size.toInt()
                }
                pos += n - 3
            }
            if (truncatedHeads > 0) {
                log("本段有 $truncatedHeads 个 dex 头，但整块超出段尾（相邻段没连上），已跳过")
            }
        }
    }

    /**
     * 只从 0x70 字节的头部里读 file_size 并做合理性判断。
     *
     * 不要拿这个小 buffer 去调 DexRepair.looksLikeCompleteDex——那个函数内部会做
     * 「off + size 是否超出 buffer」的整块校验，传 0x70 字节的头必然返回 0。
     */
    private fun fileSizeFromHeader(head: ByteArray): Long {
        val size = DexRepair.u4l(head, 0x20)
        if (size < DexRepair.HEADER_SIZE) return 0
        if (size > MAX_REASONABLE_DEX) return 0
        return size
    }

    /** 读 dex 头（0x70 字节），读不到完整返回 null */
    private fun readHeader(raf: RandomAccessFile, off: Long): ByteArray? {
        if (off < 0 || off + DexRepair.HEADER_SIZE > raf.length()) return null
        val b = ByteArray(DexRepair.HEADER_SIZE)
        raf.seek(off)
        if (raf.read(b) != b.size) return null
        return b
    }

    private fun hexAddr(v: Long): String = "0x" + java.lang.Long.toHexString(v)

    private fun sha1Hex(b: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        return DexRepair.hex(md.digest(b), 0, 20)
    }
}
