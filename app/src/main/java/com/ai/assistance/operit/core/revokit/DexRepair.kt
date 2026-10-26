package com.ai.assistance.operit.core.revokit

import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.security.MessageDigest
import java.util.zip.Adler32

/**
 * RevoKit DEX 修复引擎
 *
 * 覆盖脱壳后的三件套：
 * 1. 修头：file_size -> signature -> checksum，顺序不可换
 * 2. 清 tries：try_item 的 handler_off 错乱会让 jadx 崩，直接抹掉 try 段
 * 3. CodeItem 回填：从 dump 出来的内存 dex 里，按 method_idx 把真实方法体搬回来
 *
 * 所有操作都在 ByteArray 里原地进行，不依赖 dexlib2，不重排 section。
 */
object DexRepair {

    private const val TAG = "DexRepair"

    const val HEADER_SIZE = 0x70
    private const val ACC_NATIVE = 0x0100
    private const val ACC_PUBLIC = 0x0001
    private const val ACC_INTERFACE = 0x0200
    private const val ACC_ABSTRACT = 0x0400
    private const val MAX_INSNS = 4_000_000
    private const val MAX_REASONABLE_DEX = 128L * 1024 * 1024

    // ------------------------------------------------------------------ 头

    /** dex header 里我们关心的字段 */
    data class Header(
            val version: Int,
            val checksum: Long,
            val signatureHex: String,
            val fileSize: Long,
            val headerSize: Int,
            val stringIds: Int,
            val typeIds: Int,
            val protoIds: Int,
            val fieldIds: Int,
            val methodIds: Int,
            val classDefs: Int,
            val dataSize: Long,
            val mapOff: Int
    )

    /** 体检结果：能不能用、像不像被抽过指令 */
    data class Inspection(
            val header: Header?,
            val actualSize: Int,
            val magicOk: Boolean,
            val checksumOk: Boolean,
            val signatureOk: Boolean,
            val fileSizeOk: Boolean,
            val classCount: Int,
            val methodCount: Int,
            val nativeMethodCount: Int,
            val emptyCodeCount: Int,
            val tryMethodCount: Int,
            val parseError: String? = null
    ) {
        val healthy: Boolean
            get() = magicOk && checksumOk && signatureOk && fileSizeOk

        /** 方法体被抽走的典型特征：大量方法标了 native 或 code_off 为 0 */
        val looksExtracted: Boolean
            get() {
                if (methodCount == 0) return false
                val bad = nativeMethodCount + emptyCodeCount
                return bad * 10 >= methodCount
            }

        val nativeRatio: Float
            get() = if (methodCount == 0) 0f else nativeMethodCount.toFloat() / methodCount
    }

    fun header(buf: ByteArray): Header? {
        if (buf.size < HEADER_SIZE) return null
        if (buf[0] != 'd'.code.toByte() ||
                        buf[1] != 'e'.code.toByte() ||
                        buf[2] != 'x'.code.toByte() ||
                        buf[3] != '\n'.code.toByte()
        ) {
            return null
        }
        return Header(
                version = parseVersion(buf),
                checksum = u4l(buf, 0x08),
                signatureHex = hex(buf, 0x0C, 20),
                fileSize = u4l(buf, 0x20),
                headerSize = u4(buf, 0x24),
                stringIds = u4(buf, 0x38),
                typeIds = u4(buf, 0x40),
                protoIds = u4(buf, 0x48),
                fieldIds = u4(buf, 0x50),
                methodIds = u4(buf, 0x58),
                classDefs = u4(buf, 0x60),
                dataSize = u4l(buf, 0x68),
                mapOff = u4(buf, 0x34)
        )
    }

    private fun parseVersion(buf: ByteArray): Int {
        var v = 0
        var digits = 0
        for (i in 4..6) {
            val c = buf[i].toInt() and 0xFF
            if (c < '0'.code || c > '9'.code) break
            v = v * 10 + (c - '0'.code)
            digits++
        }
        return if (digits > 0) v else 0
    }

    fun inspect(buf: ByteArray): Inspection {
        val h = header(buf)
                ?: return Inspection(
                        header = null,
                        actualSize = buf.size,
                        magicOk = false,
                        checksumOk = false,
                        signatureOk = false,
                        fileSizeOk = false,
                        classCount = 0,
                        methodCount = 0,
                        nativeMethodCount = 0,
                        emptyCodeCount = 0,
                        tryMethodCount = 0,
                        parseError = "magic 不是 dex，可能已加密或不是 dex 文件"
                )

        val checksumOk = h.checksum == adler32(buf, 0x0C)
        val signatureOk = h.signatureHex.equals(sha1Hex(buf, 0x20), ignoreCase = true)
        val sizeOk = h.fileSize == buf.size.toLong()

        var classes = 0
        var methods = 0
        var nativeCount = 0
        var emptyCode = 0
        var tryCount = 0
        var err: String? = null
        try {
            val slots = collectMethods(buf)
            methods = slots.size
            classes = h.classDefs
            val offs = slots.map { it.codeOff }.filter { it > 0 }.distinct()
            for (s in slots) {
                if ((s.accessFlags and ACC_NATIVE) != 0) nativeCount++
                if (s.codeOff == 0) emptyCode++
            }
            for (off in offs) {
                if (off + 8 <= buf.size && u2(buf, off + 6) > 0) tryCount++
            }
        } catch (t: Throwable) {
            err = "class_data 解析中断：${t.message ?: t.javaClass.simpleName}"
        }

        return Inspection(
                header = h,
                actualSize = buf.size,
                magicOk = true,
                checksumOk = checksumOk,
                signatureOk = signatureOk,
                fileSizeOk = sizeOk,
                classCount = classes,
                methodCount = methods,
                nativeMethodCount = nativeCount,
                emptyCodeCount = emptyCode,
                tryMethodCount = tryCount,
                parseError = err
        )
    }

    // -------------------------------------------------------------- 方法遍历

    /** 一个方法的定位信息。flagsOff + flagsLen 用来原地改 access_flags */
    data class MethodSlot(
            val methodIdx: Int,
            val classIdx: Int,
            val accessFlags: Int,
            val codeOff: Int,
            val flagsOff: Int,
            val flagsLen: Int
    )

    fun collectMethods(buf: ByteArray): List<MethodSlot> {
        if (buf.size < HEADER_SIZE) return emptyList()
        val classDefsSize = u4(buf, 0x60)
        val classDefsOff = u4(buf, 0x64)
        if (classDefsSize <= 0 || classDefsOff <= 0) return emptyList()
        if (classDefsOff.toLong() + classDefsSize.toLong() * 32 > buf.size.toLong()) return emptyList()

        val out = ArrayList<MethodSlot>(1024)
        for (i in 0 until classDefsSize) {
            val base = classDefsOff + i * 32
            if (base + 32 > buf.size) break
            val classIdx = u4(buf, base)
            val classDataOff = u4(buf, base + 24)
            if (classDataOff <= 0 || classDataOff >= buf.size) continue

            val cur = Cursor(buf, classDataOff)
            val staticFields = cur.uleb()
            val instanceFields = cur.uleb()
            val directMethods = cur.uleb()
            val virtualMethods = cur.uleb()

            repeat(staticFields + instanceFields) {
                cur.uleb()
                cur.uleb()
            }

            collectMethodList(buf, cur, directMethods, classIdx, out)
            collectMethodList(buf, cur, virtualMethods, classIdx, out)
        }
        return out
    }

    private fun collectMethodList(
            buf: ByteArray,
            cur: Cursor,
            count: Int,
            classIdx: Int,
            out: MutableList<MethodSlot>
    ) {
        var methodIdx = 0
        repeat(count) {
            val diff = cur.uleb()
            methodIdx += diff
            val flagsOff = cur.pos
            val flagsLen = cur.peekUlebLength()
            val flags = cur.uleb()
            val codeOff = cur.uleb()
            out.add(MethodSlot(methodIdx, classIdx, flags, codeOff, flagsOff, flagsLen))
        }
    }

    // ------------------------------------------------------------------ 修头

    /**
     * 重算 header 三件套。顺序绝对不能换：
     * file_size -> signature(覆盖 0x20 之后) -> checksum(覆盖 0x0C 之后)
     */
    fun fixHeader(buf: ByteArray) {
        if (buf.size < HEADER_SIZE) return
        w4(buf, 0x20, buf.size)
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(buf, 0x20, buf.size - 0x20)
        System.arraycopy(sha1.digest(), 0, buf, 0x0C, 20)
        w4(buf, 0x08, adler32(buf, 0x0C).toInt())
    }

    // ---------------------------------------------------------------- 清 tries

    /** 把每个 code_item 的 try 段抹平。返回被改的 code_item 数 */
    fun clearTries(buf: ByteArray): Int {
        val offs =
                try {
                    collectMethods(buf).map { it.codeOff }.filter { it > 0 }.distinct().sorted()
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "clearTries collect failed: ${t.message}")
                    return 0
                }

        var changed = 0
        for (off in offs) {
            if (off + 0x10 > buf.size) continue
            if (u2(buf, off + 6) == 0) continue
            val span = codeItemSpan(buf, off)
            if (span <= 0) continue
            val insns = u4(buf, off + 0x0C)
            w2(buf, off + 6, 0)
            var end = off + 0x10 + insns * 2
            if (insns % 2 == 1) end += 2
            if (end > off + span) end = off + span
            for (i in end until off + span) buf[i] = 0
            changed++
        }
        return changed
    }

    /** 计算一个 code_item 的总长度（含 padding、try_item、handler）。非法返回 0 */
    fun codeItemSpan(buf: ByteArray, off: Int): Int {
        if (off < 0 || off + 0x10 > buf.size) return 0
        val tries = u2(buf, off + 6)
        val insns = u4(buf, off + 0x0C)
        if (insns < 0 || insns > MAX_INSNS) return 0
        var len = 0x10 + insns * 2
        if (tries > 0) {
            if (insns % 2 == 1) len += 2
            len += tries * 8
            if (off + len >= buf.size) return 0
            val cur = Cursor(buf, off + len)
            try {
                val size = cur.sleb()
                var pairs = size
                if (pairs <= 0) pairs = -pairs
                repeat(pairs) {
                    cur.uleb()
                    cur.uleb()
                }
                if (size <= 0) cur.uleb()
                len = cur.pos - off
            } catch (t: Throwable) {
                return 0
            }
        }
        return if (off + len <= buf.size) len else 0
    }

    // ------------------------------------------------------- CodeItem 回填

    data class BackfillReport(
            val copied: Int = 0,
            val skippedTooLong: Int = 0,
            val missingInDump: Int = 0,
            val nativeCleared: Int = 0,
            val nativeFlagFailed: Int = 0,
            val unalignedSlots: Int = 0,
            val dumpMethodCount: Int = 0,
            val targetMethodCount: Int = 0,
            val notes: List<String> = emptyList()
    )

    /**
     * 从 dump 出来的内存 dex 里，按 method_idx 把真实 code_item 搬回目标 dex。
     *
     * 安全策略：只做「等长或更短」的原地覆盖，放不下就跳过并记账，绝不动别的 item。
     * 覆盖后把 code_item 里的 debug_info_off 置 0 —— 那是 dump 里的绝对偏移，在新文件里非法。
     */
    fun backfill(target: ByteArray, dump: ByteArray): BackfillReport {
        val notes = ArrayList<String>()
        val tSlots = try {
            collectMethods(target)
        } catch (t: Throwable) {
            return BackfillReport(notes = listOf("目标 dex 解析失败：${t.message}"))
        }
        val dSlots = try {
            collectMethods(dump)
        } catch (t: Throwable) {
            return BackfillReport(notes = listOf("dump dex 解析失败：${t.message}"))
        }
        if (tSlots.isEmpty()) return BackfillReport(notes = listOf("目标 dex 里没有方法"))
        if (dSlots.isEmpty()) return BackfillReport(notes = listOf("dump dex 里没有方法"))

        val dMap = HashMap<Int, Int>(dSlots.size * 2)
        for (s in dSlots) {
            if (s.codeOff > 0) dMap[s.methodIdx] = s.codeOff
        }

        val targetOffs = tSlots.map { it.codeOff }.filter { it > 0 }.distinct().sorted()
        val capacity = HashMap<Int, Int>()
        for (i in targetOffs.indices) {
            val cur = targetOffs[i]
            val next = if (i + 1 < targetOffs.size) targetOffs[i + 1] else 0
            capacity[cur] = if (next > cur) next - cur else codeItemSpan(target, cur)
        }

        var copied = 0
        var tooLong = 0
        var missing = 0
        var unaligned = 0
        val done = HashSet<Int>()

        for (s in tSlots) {
            val dst = s.codeOff
            if (dst <= 0) continue
            if (!done.add(dst)) continue
            val src = dMap[s.methodIdx]
            if (src == null || src <= 0) {
                missing++
                continue
            }
            val srcSpan = codeItemSpan(dump, src)
            if (srcSpan <= 0) {
                missing++
                continue
            }
            val cap = capacity[dst] ?: 0
            if (cap <= 0 || srcSpan > cap) {
                tooLong++
                continue
            }
            if (dst and 3 != 0) unaligned++
            System.arraycopy(dump, src, target, dst, srcSpan)
            w4(target, dst + 8, 0)
            for (i in dst + srcSpan until dst + cap) target[i] = 0
            copied++
        }

        var cleared = 0
        var flagFailed = 0
        for (s in tSlots) {
            if ((s.accessFlags and ACC_NATIVE) == 0) continue
            if (s.codeOff <= 0) continue
            val newFlags = s.accessFlags and ACC_NATIVE.inv()
            if (rewriteUlebFixedLength(target, s.flagsOff, s.flagsLen, newFlags)) cleared++ else flagFailed++
        }

        if (unaligned > 0) notes.add("有 $unaligned 个 code_item 未四字节对齐，ART 校验可能报 bad code item")
        if (flagFailed > 0) notes.add("$flagFailed 个方法的 native 标记改不回去（uleb 变长），需要全量重建 dex")
        if (tooLong > 0) notes.add("$tooLong 个方法体比占位长，已跳过（体积变化必须走全量重建）")

        return BackfillReport(
                copied = copied,
                skippedTooLong = tooLong,
                missingInDump = missing,
                nativeCleared = cleared,
                nativeFlagFailed = flagFailed,
                unalignedSlots = unaligned,
                dumpMethodCount = dSlots.size,
                targetMethodCount = tSlots.size,
                notes = notes
        )
    }

    // ------------------------------------------------------- 类表与入口推断

    /** 一个类的结构信息，用来推断真实 Application */
    data class ClassInfo(
            val name: String,
            val descriptor: String,
            val superName: String?,
            val accessFlags: Int,
            val methodNames: Set<String>,
            val dexIndex: Int
    ) {
        val isAbstract: Boolean get() = (accessFlags and ACC_ABSTRACT) != 0
        val isInterface: Boolean get() = (accessFlags and ACC_INTERFACE) != 0
        val isPublic: Boolean get() = (accessFlags and ACC_PUBLIC) != 0
    }

    /** 真实 Application 候选。chain 从 framework 基类排到本类 */
    data class EntryCandidate(
            val className: String,
            val chain: List<String>,
            val score: Int,
            val reasons: List<String>,
            val dexIndex: Int
    )

    /** dex 里六张索引表的访问器。嵌套类拿不到外层 object 的成员，读 u4 要带类名 */
    private class Tables(val b: ByteArray) {
        val stringIdsSize = DexRepair.u4(b, 0x38)
        val stringIdsOff = DexRepair.u4(b, 0x3C)
        val typeIdsSize = DexRepair.u4(b, 0x40)
        val typeIdsOff = DexRepair.u4(b, 0x44)
        val methodIdsSize = DexRepair.u4(b, 0x58)
        val methodIdsOff = DexRepair.u4(b, 0x5C)
        val classDefsSize = DexRepair.u4(b, 0x60)
        val classDefsOff = DexRepair.u4(b, 0x64)

        /** string_id -> 字符串内容（MUTF8，类名全是 ASCII，按 UTF-8 读即可） */
        fun stringAt(idx: Int): String? {
            if (idx < 0 || idx >= stringIdsSize) return null
            val off = DexRepair.u4(b, stringIdsOff + idx * 4)
            if (off <= 0 || off >= b.size) return null
            return try {
                val cur = Cursor(b, off)
                val len = cur.uleb()
                if (len <= 0 || cur.pos + len > b.size) return null
                String(b, cur.pos, len, Charsets.UTF_8)
            } catch (t: Throwable) {
                null
            }
        }

        fun typeAt(typeIdx: Int): String? {
            if (typeIdx < 0 || typeIdx >= typeIdsSize) return null
            return stringAt(DexRepair.u4(b, typeIdsOff + typeIdx * 4))
        }

        /** method_id 条目：class_idx(u2) proto_idx(u2) name_idx(u4) */
        fun methodName(idx: Int): String? {
            if (idx < 0 || idx >= methodIdsSize) return null
            return stringAt(DexRepair.u4(b, methodIdsOff + idx * 8 + 4))
        }
    }

    /** 类型描述符 Lcom/x/Y; -> com.x.Y。数组、基本类型返回 null */
    private fun readableName(desc: String): String? {
        if (desc.isEmpty() || desc[0] != 'L' || !desc.endsWith(";")) return null
        return desc.substring(1, desc.length - 1).replace('/', '.')
    }

    private fun isFramework(name: String): Boolean =
            name.startsWith("android.") ||
                    name.startsWith("androidx.") ||
                    name.startsWith("java.") ||
                    name.startsWith("kotlin.") ||
                    name.startsWith("dalvik.") ||
                    name.startsWith("com.android.")

    /** 枚举一个 dex 里的所有类，带父类名和方法名 */
    fun listClasses(buf: ByteArray, dexIndex: Int = 0): List<ClassInfo> {
        if (buf.size < HEADER_SIZE) return emptyList()
        val t = Tables(buf)
        if (t.classDefsSize <= 0 || t.classDefsOff <= 0) return emptyList()
        if (t.classDefsOff.toLong() + t.classDefsSize.toLong() * 32 > buf.size.toLong()) return emptyList()

        val out = ArrayList<ClassInfo>(t.classDefsSize)
        for (i in 0 until t.classDefsSize) {
            val base = t.classDefsOff + i * 32
            if (base + 32 > buf.size) break
            val desc = t.typeAt(u4(buf, base)) ?: continue
            val name = readableName(desc) ?: continue
            val superIdx = u4(buf, base + 8)
            val superName =
                    if (superIdx == -1) null else t.typeAt(superIdx)?.let { readableName(it) }
            out.add(
                    ClassInfo(
                            name = name,
                            descriptor = desc,
                            superName = superName,
                            accessFlags = u4(buf, base + 4),
                            methodNames = methodNamesOf(buf, t, u4(buf, base + 24)),
                            dexIndex = dexIndex
                    )
            )
        }
        return out
    }

    /** 读 class_data，解析出本类定义的所有方法名（含构造与静态初始化） */
    private fun methodNamesOf(buf: ByteArray, t: Tables, classDataOff: Int): Set<String> {
        if (classDataOff <= 0 || classDataOff >= buf.size) return emptySet()
        val names = HashSet<String>()
        try {
            val cur = Cursor(buf, classDataOff)
            val staticFields = cur.uleb()
            val instanceFields = cur.uleb()
            val directMethods = cur.uleb()
            val virtualMethods = cur.uleb()
            repeat(staticFields + instanceFields) {
                cur.uleb()
                cur.uleb()
            }
            var idx = 0
            repeat(directMethods) {
                idx += cur.uleb()
                cur.uleb()
                cur.uleb()
                t.methodName(idx)?.let { names.add(it) }
            }
            idx = 0
            repeat(virtualMethods) {
                idx += cur.uleb()
                cur.uleb()
                cur.uleb()
                t.methodName(idx)?.let { names.add(it) }
            }
        } catch (t: Throwable) {
            // 截断就返回已经拿到的部分，不往上抛
        }
        return names
    }

    private val APP_ROOTS =
            setOf(
                    "android.app.Application",
                    "android.support.multidex.MultiDexApplication",
                    "androidx.multidex.MultiDexApplication"
            )

    /** 命中任意一条就基本可以断定是壳的桩类，不是业务入口 */
    private val SHELL_KEYWORDS =
            listOf(
                    "stub", "wrapper", "proxy", "shell", "jiagu", "secshell", "secneo",
                    "bangcle", "ijiami", "naga", "dexprotector", "appsealing", "qhs",
                    "legu", "tencent", "apkprotect", "dexhelper"
            )

    /**
     * 从脱出来的 dex 里推断真实 Application。
     *
     * 关键教训（用户实录里连续错两次的点）：Manifest 里写的桩类只是壳的代理，
     * 真正被注册的是继承链的**叶子节点**。选了基类会跳过 attachBaseContext，
     * 全局 Context 为 null，启动必崩。
     */
    fun inferEntryClasses(dexs: List<ByteArray>): List<EntryCandidate> {
        val all = ArrayList<ClassInfo>()
        dexs.forEachIndexed { i, b ->
            if (b.size > HEADER_SIZE) {
                try {
                    all.addAll(listClasses(b, i))
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "第 $i 个 dex 解析类表失败：${t.message}")
                }
            }
        }
        if (all.isEmpty()) return emptyList()

        val byName = HashMap<String, ClassInfo>(all.size * 2)
        for (c in all) byName.putIfAbsent(c.name, c)

        val childCount = HashMap<String, Int>()
        for (c in all) {
            val s = c.superName ?: continue
            childCount[s] = (childCount[s] ?: 0) + 1
        }

        val out = ArrayList<EntryCandidate>()
        for (c in all) {
            if (c.isInterface) continue

            val chain = ArrayList<String>()
            var cur: ClassInfo? = c
            var hops = 0
            var rooted = false
            var rootName: String? = null
            while (cur != null && hops < 16) {
                chain.add(0, cur.name)
                val s = cur.superName
                if (s == null || s in APP_ROOTS || isFramework(s)) {
                    if (s != null) chain.add(0, s)
                    rootName = s ?: "java.lang.Object"
                    rooted = true
                    break
                }
                cur = byName[s]
                hops++
            }
            if (!rooted) continue

            var score = 0
            val reasons = ArrayList<String>()
            val kids = childCount[c.name] ?: 0
            if (kids == 0) {
                score += 60
                reasons.add("继承链叶子，没有子类")
            } else {
                score -= 40
                reasons.add("有 $kids 个子类，多半是基类")
            }

            // 关键分水岭：链的根必须是 Application 本身。
            // 只到 java.lang.Object / Activity / Context 的那些（比如 Guava 集合的继承链）
            // 压根不可能被写进 manifest 的 application 节点，必须重罚，否则噪声会淹没真答案。
            if (rootName in APP_ROOTS) {
                score += 80
                reasons.add("继承链直通 android.app.Application")
            } else {
                score -= 60
                reasons.add("继承链只到 $rootName，不是 Application 子类")
            }

            val hasAttach = "attachBaseContext" in c.methodNames
            val hasCreate = "onCreate" in c.methodNames
            if (hasAttach) {
                score += 50
                reasons.add("重写了 attachBaseContext")
            }
            if (hasCreate) {
                score += 25
                reasons.add("重写了 onCreate")
            }
            if (!hasAttach && !hasCreate) {
                score -= 80
                reasons.add("既没 attachBaseContext 也没 onCreate，不像 Application")
            }
            if (c.name.endsWith("Application")) score += 15
            else if (c.name.endsWith("App")) score += 8

            score += 8 * (chain.size - 2).coerceAtLeast(0)
            if (c.isPublic) score += 5
            if (c.isAbstract) {
                score -= 60
                reasons.add("抽象类，实例化不了")
            }

            val lower = c.name.lowercase()
            val hit = SHELL_KEYWORDS.firstOrNull { lower.contains(it) }
            if (hit != null) {
                score -= 500
                reasons.add("类名含壳关键词「$hit」，几乎肯定是桩类")
            }

            out.add(EntryCandidate(c.name, chain, score, reasons, c.dexIndex))
        }
        return out.sortedByDescending { it.score }
    }

    // ------------------------------------------------------------ dex 体检

    /** 六张索引表越界检查。任何一张越界，这个 dex ART 就加载不了 */
    fun validateTables(buf: ByteArray): List<String> {
        if (buf.size < HEADER_SIZE) return listOf("不足 0x70 字节，连头都不完整")
        val size = buf.size.toLong()
        val bad = ArrayList<String>()
        fun check(offPos: Int, sizePos: Int, entry: Int, label: String) {
            val off = u4l(buf, offPos)
            val n = u4l(buf, sizePos)
            if (n == 0L && off == 0L) return
            if (off <= 0 || off >= size) {
                bad.add("$label 偏移越界（off=$off）")
                return
            }
            if (off + n * entry > size) bad.add("$label 越界（off=$off + ${n}×$entry > $size）")
        }
        check(0x3C, 0x38, 4, "string_ids")
        check(0x44, 0x40, 4, "type_ids")
        check(0x4C, 0x48, 12, "proto_ids")
        check(0x54, 0x50, 8, "field_ids")
        check(0x5C, 0x58, 8, "method_ids")
        check(0x64, 0x60, 32, "class_defs")
        return bad
    }

    data class Quality(val level: String, val detail: String)

    /**
     * 判断 dump 出来的是真实 dex 还是壳的容器 dex。
     *
     * 判据来自实战：真实 dex 通常有成千上万个 class_def，只有一两千个的多半是容器。
     */
    fun quality(buf: ByteArray): Quality {
        val ins = inspect(buf)
        val h = ins.header ?: return Quality("损坏", "magic 不是 dex")
        val tableBad = validateTables(buf)
        if (tableBad.isNotEmpty()) return Quality("损坏", tableBad.joinToString("；"))

        return when {
            h.classDefs >= 3000 ->
                    Quality("真实 dex", "class_def ${h.classDefs} 个，方法 ${ins.methodCount} 个")
            h.classDefs >= 800 ->
                    Quality(
                            "疑似真实",
                            "class_def ${h.classDefs} 个，偏少，可能是分包里的一个小 dex"
                    )
            else ->
                    Quality(
                            "疑似容器",
                            "class_def 只有 ${h.classDefs} 个，像壳的加载器而不是业务 dex"
                    )
        }
    }

    // ------------------------------------------------------------------ 组合

    data class Options(
            val fixHeader: Boolean = true,
            val clearTries: Boolean = false,
            val dumpFile: File? = null
    )

    data class Result(
            val bytes: ByteArray,
            val before: Inspection,
            val after: Inspection,
            val triesCleared: Int,
            val backfill: BackfillReport?,
            val log: List<String>
    )

    fun repair(target: ByteArray, opts: Options): Result {
        val log = ArrayList<String>()
        val before = inspect(target)
        val work = target.copyOf()

        if (before.header == null) {
            return Result(work, before, before, 0, null, listOf("不是合法 dex，无法修复"))
        }
        log.add(
                "原始：v${before.header.version} · ${before.actualSize} 字节 · 类 ${before.classCount} · 方法 ${before.methodCount}"
        )
        if (!before.checksumOk) log.add("checksum 不符（被 dump 工具截过或改过）")
        if (!before.signatureOk) log.add("signature 不符")
        if (!before.fileSizeOk) log.add("file_size 与实际大小不一致（${before.header.fileSize} vs ${before.actualSize}）")

        var backfillReport: BackfillReport? = null
        val dumpFile = opts.dumpFile
        if (dumpFile != null && dumpFile.isFile) {
            val dump = dumpFile.readBytes()
            log.add("加载 dump：${dumpFile.name}（${dump.size} 字节）")
            backfillReport = backfill(work, dump)
            log.add(
                    "回填：成功 ${backfillReport.copied} · 缺指令 ${backfillReport.missingInDump} · " +
                            "超长跳过 ${backfillReport.skippedTooLong} · 清 native ${backfillReport.nativeCleared}"
            )
            log.addAll(backfillReport.notes)
        }

        var tries = 0
        if (opts.clearTries) {
            tries = clearTries(work)
            log.add("清 tries：改了 $tries 个 code_item")
        }

        if (opts.fixHeader) {
            fixHeader(work)
            log.add("已重算 file_size / signature / checksum")
        }

        val after = inspect(work)
        if (after.healthy) log.add("复检通过：magic、checksum、signature、file_size 全部一致") else log.add(
                "复检未完全通过：${describe(after)}"
        )
        return Result(work, before, after, tries, backfillReport, log)
    }

    fun describe(i: Inspection): String {
        val bad = ArrayList<String>()
        if (!i.magicOk) bad.add("magic")
        if (!i.checksumOk) bad.add("checksum")
        if (!i.signatureOk) bad.add("signature")
        if (!i.fileSizeOk) bad.add("file_size")
        return if (bad.isEmpty()) "全部一致" else "不一致：" + bad.joinToString("、")
    }

    /** 快速判断一段字节是不是一个完整、可用的 dex（dump 扫块时用） */
    fun looksLikeCompleteDex(buf: ByteArray, off: Int): Int {
        if (off + HEADER_SIZE > buf.size) return 0
        if (buf[off] != 'd'.code.toByte() ||
                        buf[off + 1] != 'e'.code.toByte() ||
                        buf[off + 2] != 'x'.code.toByte() ||
                        buf[off + 3] != '\n'.code.toByte()
        ) {
            return 0
        }
        val size = u4l(buf, off + 0x20)
        if (size < HEADER_SIZE || size > MAX_REASONABLE_DEX) return 0
        if (off + size > buf.size) return 0
        return size.toInt()
    }

    // ------------------------------------------------------------- 基础工具

    private class Cursor(val b: ByteArray, var pos: Int) {
        fun u1(): Int {
            if (pos >= b.size) throw IndexOutOfBoundsException("uleb 越界 @$pos")
            return b[pos++].toInt() and 0xFF
        }

        fun uleb(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val x = u1()
                result = result or ((x and 0x7F) shl shift)
                if ((x and 0x80) == 0) break
                shift += 7
                if (shift > 28) break
            }
            return result
        }

        fun sleb(): Int {
            var result = 0
            var shift = 0
            var x: Int
            do {
                x = u1()
                result = result or ((x and 0x7F) shl shift)
                shift += 7
            } while ((x and 0x80) != 0 && shift < 35)
            if (shift < 32 && (x and 0x40) != 0) result = result or (-1 shl shift)
            return result
        }

        fun peekUlebLength(): Int {
            var i = pos
            var n = 0
            while (i < b.size) {
                val x = b[i].toInt() and 0xFF
                n++
                i++
                if ((x and 0x80) == 0) return n
                if (n > 5) return n
            }
            return n
        }
    }

    /**
     * 用「正好 oldLen 字节」的 uleb128 写入 newValue。
     *
     * 值变小时补成超长编码（高位补 0x80），字节数不变，标准解码器读出来仍是 newValue。
     * 这样 class_data 里后续 item 的偏移不会错位。
     */
    private fun rewriteUlebFixedLength(b: ByteArray, off: Int, oldLen: Int, newValue: Int): Boolean {
        if (off < 0 || oldLen <= 0 || off + oldLen > b.size) return false
        if (newValue < 0) return false
        if (oldLen < 5 && (newValue ushr (7 * oldLen)) != 0) return false
        var v = newValue
        for (i in 0 until oldLen) {
            val x = v and 0x7F
            v = v ushr 7
            b[off + i] = (if (i < oldLen - 1) (x or 0x80) else x).toByte()
        }
        return true
    }

    fun u2(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    fun u4(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or
                    ((b[off + 1].toInt() and 0xFF) shl 8) or
                    ((b[off + 2].toInt() and 0xFF) shl 16) or
                    ((b[off + 3].toInt() and 0xFF) shl 24)

    fun u4l(b: ByteArray, off: Int): Long = u4(b, off).toLong() and 0xFFFFFFFFL

    private fun w2(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun w4(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun adler32(b: ByteArray, from: Int): Long {
        val a = Adler32()
        a.update(b, from, b.size - from)
        return a.value
    }

    private fun sha1Hex(b: ByteArray, from: Int): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(b, from, b.size - from)
        return hex(md.digest(), 0, 20)
    }

    fun hex(b: ByteArray, off: Int, len: Int): String {
        val sb = StringBuilder(len * 2)
        for (i in off until off + len) {
            if (i >= b.size) break
            val v = b[i].toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }
}
