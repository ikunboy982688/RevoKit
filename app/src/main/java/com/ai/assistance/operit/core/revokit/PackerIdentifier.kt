package com.ai.assistance.operit.core.revokit

import android.content.Context
import android.content.pm.PackageManager
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.math.min

/**
 * 加固识别引擎（纯静态，不执行目标 APK）
 *
 * 判定方式全部基于 zip 结构 + dex 字符串，刻意不做任何「猜测性」判断：
 * 每条证据都能在 UI 里展开看到具体文件名/字符串，方便人工复核。
 */
object PackerIdentifier {

    private const val TAG = "PackerIdentifier"

    /** 单个 dex 最多读这么多字节找特征串，避免大包扫描过慢 */
    private const val DEX_SCAN_LIMIT = 8L * 1024 * 1024L

    /** UI 里最多展示这么多 assets，防止几千条撑爆列表 */
    private const val ASSET_KEEP_LIMIT = 4000

    /** 全部路径的收集上限，防止超大包把内存吃满 */
    private const val ALL_PATHS_LIMIT = 20000

    private val DEX_NAME_REGEX = Regex("^classes\\d*\\.dex$")

    fun identify(context: Context, apk: File): IdentifyReport {
        val start = System.currentTimeMillis()
        val profile = buildProfile(context, apk)
        val matches = refineQihooGeneration(profile, matchPackers(apk, profile))
        val stack = detectStacks(profile)
        val notes = structuralNotes(profile)
        val elapsed = System.currentTimeMillis() - start
        AppLogger.d(TAG, "identify ${apk.name} -> ${matches.firstOrNull()?.rule?.displayName ?: "未识别"} in ${elapsed}ms")
        return IdentifyReport(
                profile = profile,
                matches = matches,
                stack = stack,
                structuralNotes = notes,
                elapsedMs = elapsed
        )
    }

    /* ------------------------------------------------------------------ */
    /* 360 代次：老版 / 新版 / 企业版，以及 0701 付费时代                  */
    /* ------------------------------------------------------------------ */

    /** 新版 V8+ 才有的 ART 层壳本体（老版只有 libjiagu.so + jiagu.dat） */
    private val QIHOO_NEW_SO = listOf("libjiagu_art.so")
    private val QIHOO_NEW_ASSETS = listOf("jiagu.art", "jiagu_res.zip")

    /** 企业/VIP 版才有的东西。后两项来自公开教程，本机样本未实测，只作辅助线索 */
    private val QIHOO_ENT_SO = listOf("libvmp_360.so", "libjiagu_enterprise.so")
    private val QIHOO_ENT_ASSETS = listOf("vmp_config.bin", "jiagu_enterprise.dat")

    /**
     * 判 360 代次，返回 `代次 to 依据`。
     *
     * 判据优先级（高 → 低）：
     * 1. 企业/VIP 硬特征
     * 2. ART 层壳本体（`libjiagu_art.so` / `jiagu.art` / `jiagu_res.zip`）—— V8 才有，老版没有
     * 3. 桩类 `com.frezrik.jiagu` / `com.qihoo.util` —— V5 老壳
     * 4. 桩类 `com.stub.` / `com.byazt.` —— **跨代**，2019 年就在用。必须叠 dex 时间：
     *    早于 [QIHOO_V8_ERA_MS] → 老版；否则新版
     * 5. 只有 libjiagu*.so → 老版
     *
     * 第 4 条是 2026-10-24 真机测出来的教训：RedteaGO（2020-07-17，桩类 com.stub.StubApp）
     * 以前被判「新版」，于是同一份报告里工具说新版、AI 按年代说老版。桩类单独不足以定代次。
     */
    private fun detectQihooGeneration(profile: ApkProfile): Pair<QihooGeneration, String> {
        val soNames = profile.soFiles.map { it.name.substringAfterLast('/') }
        val assetPaths = profile.assetFiles.map { it.name } + profile.allPaths
        val entry = profile.applicationClass.orEmpty()

        fun anySo(p: String) = soNames.any { globMatch(p, it) }
        fun anyAsset(suffix: String) = assetPaths.any { it.endsWith(suffix) }

        // 企业/VIP：libjiagu_vip* / libjgcxi* 是老样本里确认过的企业版标志
        val hasEntSo =
                soNames.any { it.startsWith("libjiagu_vip") || it.startsWith("libjgcxi") } ||
                        QIHOO_ENT_SO.any { anySo(it) }
        val hasEntAsset = QIHOO_ENT_ASSETS.any { anyAsset(it) }
        if (hasEntSo || hasEntAsset) {
            return QihooGeneration.ENTERPRISE to "企业/VIP 版专属 so 或资源"
        }

        val hasNewEra = QIHOO_NEW_SO.any { anySo(it) } || QIHOO_NEW_ASSETS.any { anyAsset(it) }
        if (hasNewEra) {
            return QihooGeneration.NEW to
                    "ART 层壳本体（libjiagu_art.so / jiagu.art / jiagu_res.zip），老版没有"
        }

        val stubIsOld = entry.startsWith("com.frezrik.jiagu") || entry.startsWith("com.qihoo.util")
        if (stubIsOld) return QihooGeneration.OLD to "V5 老壳桩类 $entry"

        val stubIsNew = entry.startsWith("com.stub.") || entry.startsWith("com.byazt.")
        if (stubIsNew) {
            val w = profile.zipTime
            return when {
                w.usable && w.minMs < QIHOO_V8_ERA_MS ->
                        QihooGeneration.OLD to
                                "桩类 $entry 跨代不可靠，叠 dex 时间 ${fmtDay(w.minMs)}" +
                                "（早于 2024-01-01 的 V8 分界）→ 老版"
                w.usable ->
                        QihooGeneration.NEW to
                                "桩类 $entry + dex 时间 ${fmtDay(w.minMs)}（晚于 2024-01-01）→ 新版"
                else ->
                        QihooGeneration.NEW to
                                "桩类 $entry（dex 时间被抹，无法用时间校正，按现行引擎判新版）"
            }
        }

        return if (soNames.any { it.startsWith("libjiagu") })
                QihooGeneration.OLD to "只有 libjiagu*.so，无可识别的桩类"
        else QihooGeneration.UNKNOWN to "没有企业特征 / ART 特征 / 可识别桩类"
    }

    private fun fmtDay(ms: Long): String {
        return runCatching {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date(ms))
                }
                .getOrDefault("?")
    }

    /** 把时间窗翻译成一句给人看的话。判不出来就老实说不出来，不硬猜。 */
    private fun describeZipTime(w: ZipTimeWindow): String {
        if (!w.usable) {
            return if (w.sampleCount == 0) "APK 里没有 dex 时间可读"
            else "zip 时间戳被抹（${w.sampleCount} 个 dex 全是打包器固定值），无法判年代 —— 只看特征定代次"
        }
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val min = fmt.format(java.util.Date(w.minMs))
        val max = fmt.format(java.util.Date(w.maxMs))
        val base =
                when {
                    w.allAfterPaidCut -> "dex 修改时间 $min 起，全部在 2026-07-01 之后 → 付费时代"
                    w.allBeforePaidCut -> "dex 修改时间 $max 止，全部在 2026-07-01 之前 → 免费时代"
                    else -> "dex 修改时间跨 2026-07-01 分界（旧 $min / 新 $max）→ 时间不可作代次依据"
                }
        // 样本不完整必须说出来：只拿 1 个 dex 的时间判整包年代，跟蒙没区别
        return if (w.totalDex > w.sampleCount)
                "$base（时间样本 ${w.sampleCount}/${w.totalDex}，其余 dex 时间不可用）"
        else base
    }

    /**
     * 把命中的 360 规则的展示名换成带代次的版本：
     * `360加固（新版）` / `360加固（新版0701）` / `360加固（企业版0701）`。
     *
     * 只有「时间窗可用且全部越过分界线」才加 0701 —— 猜错比不写更糟。
     */
    private fun refineQihooGeneration(
            profile: ApkProfile,
            matches: List<PackerMatch>
    ): List<PackerMatch> {
        if (matches.none { it.rule.id.startsWith("packer.qihoo") }) return matches

        val (gen, genWhyRaw) = detectQihooGeneration(profile)
        val paidEra = profile.zipTime.allAfterPaidCut
        val timeNote = describeZipTime(profile.zipTime)
        // 依据里必须自带两件事：① 时间样本覆盖了多少 dex ② 有没有叠 0701。
        // 少一样就会出现「代次=新版0701 但依据写→新版」或者「基于 1/3 个 dex 的结论讲成定论」。
        val genWhy =
                buildString {
                    append(genWhyRaw)
                    val w = profile.zipTime
                    if (w.usable && w.totalDex > w.sampleCount) {
                        append("（时间样本 ${w.sampleCount}/${w.totalDex}，其余 dex 时间不可用）")
                    }
                    if (paidEra && gen != QihooGeneration.UNKNOWN) append("；再叠 0701 付费时代")
                }

        return matches.map { m ->
            if (!m.rule.id.startsWith("packer.qihoo")) return@map m

            val name =
                    when {
                        gen == QihooGeneration.UNKNOWN -> "360加固（代次未定）"
                        paidEra -> "360加固（${gen.label}0701）"
                        else -> "360加固（${gen.label}）"
                    }
            // 代次判定过程写进证据列表，用户在徽章详情里能直接看到为什么这么判
            val extra =
                    buildList {
                        // 代次是怎么判出来的，单独一条 —— 避免用户/AI 自己编一套解释
                        add(Evidence(EvidenceKind.APP_CLASS, "代次依据：$genWhy", 0))
                        add(Evidence(EvidenceKind.ZIP_TIME, timeNote, 0))
                        if (paidEra) {
                            add(
                                    Evidence(
                                            EvidenceKind.ZIP_TIME,
                                            "2026-07-01 起 360 加固保停止免费加固：付费时代产物预期带更重的 " +
                                                    "VMP / 反调试，清残留按企业版规格做" +
                                                    "（等价于 MT 管理器里的「360付费版」）",
                                            0
                                    )
                            )
                        }
                    }
            m.copy(rule = m.rule.copy(displayName = name), evidences = m.evidences + extra)
        }
    }

    /* ------------------------------------------------------------------ */
    /* 画像                                                                */
    /* ------------------------------------------------------------------ */

    private fun buildProfile(context: Context, apk: File): ApkProfile {
        val dexFiles = mutableListOf<ZipMember>()
        val soFiles = mutableListOf<ZipMember>()
        val assetFiles = mutableListOf<ZipMember>()
        val allPaths = mutableListOf<String>()
        val dexTimes = mutableListOf<Long>()
        var total = 0
        var totalDex = 0

        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                total++
                if (e.isDirectory) continue
                val name = e.name
                // zip 条目自带的修改时间。可能为 -1（没记时间），也可能被打包器写成 1980 固定值，
                // 所以只收集不判断，交给 buildZipTimeWindow 决定能不能用。
                val t = runCatching { e.time }.getOrDefault(-1L)
                if (allPaths.size < ALL_PATHS_LIMIT) allPaths += name
                // 「总 dex」按 .dex 后缀计，与 dexFiles 同口径（含 assets/ 与子目录里的）。
                // 只数根目录 classes*.dex 会出现「dex 数 3 而时间样本 1/1」的自相矛盾。
                if (name.lowercase().endsWith(".dex")) totalDex++
                when {
                    DEX_NAME_REGEX.matches(name) -> {
                        dexFiles += ZipMember(name, e.size, t)
                        if (t > 0) dexTimes += t
                    }
                    name.startsWith("lib/") && name.endsWith(".so") -> soFiles += ZipMember(name, e.size, t)
                    name.startsWith("assets/") && assetFiles.size < ASSET_KEEP_LIMIT ->
                            assetFiles += ZipMember(name, e.size, t)
                }
            }
        }

        dexFiles.sortBy { it.name }
        soFiles.sortBy { it.name }
        assetFiles.sortBy { it.name }

        val meta = readManifestMeta(context, apk)
        val hashes = hashOf(apk)

        return ApkProfile(
                path = apk.absolutePath,
                fileName = apk.name,
                fileSize = apk.length(),
                md5 = hashes.first,
                sha256 = hashes.second,
                packageName = meta.packageName,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                appLabel = meta.label,
                applicationClass = meta.applicationClass,
                minSdk = meta.minSdk,
                targetSdk = meta.targetSdk,
                signatureSchemes = readSignatureSchemes(apk),
                dexFiles = dexFiles,
                soFiles = soFiles,
                assetFiles = assetFiles,
                totalEntries = total,
                allPaths = allPaths,
                zipTime = buildZipTimeWindow(dexTimes, totalDex)
        )
    }

    /**
     * 只用 dex 的时间算窗口 —— dex 是加固流水线必然重写的产物，它的时间最能代表加固时刻。
     * 把全部条目一起算会被原包里没动过的老资源（图标、三方 so）带偏。
     *
     * 一旦所有 dex 的时间都早于 2015 年，说明打包器把时间抹成了固定值（1980/2008 很常见），
     * 这时 usable=false，上层必须放弃用时间判年代，不能硬猜。
     */
    private fun buildZipTimeWindow(times: List<Long>, totalDex: Int = 0): ZipTimeWindow {
        val sane = times.filter { it >= ZIP_TIME_SANE_MIN_MS }
        if (sane.isEmpty()) {
            return ZipTimeWindow(sampleCount = times.size, totalDex = totalDex, usable = false)
        }
        val lo = sane.minOrNull()
                ?: return ZipTimeWindow(sampleCount = times.size, totalDex = totalDex, usable = false)
        val hi = sane.maxOrNull() ?: lo
        return ZipTimeWindow(
                minMs = lo,
                maxMs = hi,
                sampleCount = sane.size,
                totalDex = totalDex,
                usable = true
        )
    }

    private data class ManifestMeta(
            val packageName: String?,
            val versionName: String?,
            val versionCode: Long,
            val label: String?,
            val applicationClass: String?,
            val minSdk: Int,
            val targetSdk: Int
    )

    /**
     * 用系统 PackageManager 解析 APK 的 AndroidManifest（只读 manifest，不安装）。
     * 比自己写 AXML 解析器可靠得多，也不会因为 aapt2 的 UTF-8 字符串池踩坑。
     */
    private fun readManifestMeta(context: Context, apk: File): ManifestMeta {
        var pkg: String? = null
        var verName: String? = null
        var verCode = 0L
        var label: String? = null
        var appClass: String? = null
        var minSdk = 0
        var targetSdk = 0
        try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
            if (info != null) {
                pkg = info.packageName
                verName = info.versionName
                verCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode
                else info.versionCode.toLong()
                val ai = info.applicationInfo
                if (ai != null) {
                    // loadLabel 需要 sourceDir 指向 APK 本体，否则拿不到资源
                    ai.sourceDir = apk.absolutePath
                    ai.publicSourceDir = apk.absolutePath
                    appClass = ai.className
                    label =
                            try {
                                ai.loadLabel(pm)?.toString()
                            } catch (t: Throwable) {
                                null
                            }
                    minSdk = if (android.os.Build.VERSION.SDK_INT >= 24) ai.minSdkVersion else 0
                    targetSdk = if (android.os.Build.VERSION.SDK_INT >= 24) ai.targetSdkVersion else 0
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "readManifestMeta failed: ${t.message}")
        }
        return ManifestMeta(pkg, verName, verCode, label, appClass, minSdk, targetSdk)
    }

    private fun hashOf(file: File): Pair<String, String> {
        var md5 = ""
        var sha = ""
        try {
            val md = MessageDigest.getInstance("MD5")
            val sd = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                    sd.update(buf, 0, n)
                }
            }
            md5 = md.digest().joinToString("") { "%02x".format(it) }
            sha = sd.digest().joinToString("") { "%02x".format(it) }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "hashOf failed: ${t.message}")
        }
        return md5 to sha
    }

    /**
     * 读 APK Signing Block 判断 v2/v3/v3.1，再按 META-INF 判断 v1。
     * v2 及以上签名块就压在 Central Directory 之前，所以先找 EOCD 再回退。
     */
    private fun readSignatureSchemes(apk: File): List<String> {
        val result = mutableListOf<String>()
        try {
            RandomAccessFile(apk, "r").use { raf ->
                val len = raf.length()
                val tailLen = min(len, 0xFFFFL + 22L).toInt()
                val tail = ByteArray(tailLen)
                raf.seek(len - tailLen)
                raf.readFully(tail)

                // 从后往前找 EOCD 魔数 0x06054b50
                var eocd = -1
                for (i in tailLen - 22 downTo 0) {
                    if (
                            tail[i] == 0x50.toByte() &&
                                    tail[i + 1] == 0x4b.toByte() &&
                                    tail[i + 2] == 0x05.toByte() &&
                                    tail[i + 3] == 0x06.toByte()
                    ) {
                        eocd = i
                        break
                    }
                }
                if (eocd < 0) return result

                val cdOffset =
                        (tail[eocd + 16].toLong() and 0xFF) or
                                ((tail[eocd + 17].toLong() and 0xFF) shl 8) or
                                ((tail[eocd + 18].toLong() and 0xFF) shl 16) or
                                ((tail[eocd + 19].toLong() and 0xFF) shl 24)

                if (cdOffset < 32 || cdOffset > len) return result

                // APK Signing Block 尾部 16 字节是 size(8) + magic(16)
                val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
                val footer = ByteArray(24)
                raf.seek(cdOffset - 24)
                raf.readFully(footer)
                var isMagic = true
                for (i in magic.indices) {
                    if (footer[8 + i] != magic[i]) {
                        isMagic = false
                        break
                    }
                }
                if (!isMagic) {
                    result += "v1"
                    return result
                }

                // size(8) 之后是 pairs，格式：uint64 len + uint32 id + value，从块头往后扫
                val blockSize =
                        (footer[0].toLong() and 0xFF) or
                                ((footer[1].toLong() and 0xFF) shl 8) or
                                ((footer[2].toLong() and 0xFF) shl 16) or
                                ((footer[3].toLong() and 0xFF) shl 24)
                val blockStart = cdOffset - blockSize - 8
                if (blockStart >= 0 && blockSize > 0 && blockSize < len) {
                    val block = ByteArray(blockSize.toInt())
                    raf.seek(blockStart + 8)
                    raf.readFully(block)
                    var p = 0
                    var v2 = false
                    var v3 = false
                    var v31 = false
                    while (p + 12 <= block.size) {
                        val pairLen =
                                (block[p].toLong() and 0xFF) or
                                        ((block[p + 1].toLong() and 0xFF) shl 8) or
                                        ((block[p + 2].toLong() and 0xFF) shl 16) or
                                        ((block[p + 3].toLong() and 0xFF) shl 24)
                        if (pairLen < 4 || p + 8 + pairLen > block.size) break
                        val id =
                                (block[p + 4].toLong() and 0xFF) or
                                        ((block[p + 5].toLong() and 0xFF) shl 8) or
                                        ((block[p + 6].toLong() and 0xFF) shl 16) or
                                        ((block[p + 7].toLong() and 0xFF) shl 24)
                        when (id) {
                                0x7109871aL -> v2 = true
                                0xf05368c0L -> v3 = true
                                0x1b93ad61L -> v31 = true
                        }
                        p += 8 + pairLen.toInt()
                    }
                    if (v2) result += "v2"
                    if (v3) result += "v3"
                    if (v31) result += "v3.1"
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "readSignatureSchemes failed: ${t.message}")
        }

        // v1 判断：META-INF 下有没有 .RSA/.DSA/.EC
        try {
            ZipFile(apk).use { zip ->
                val e = zip.entries()
                var v1 = false
                while (e.hasMoreElements()) {
                    val n = e.nextElement().name
                    if (n.startsWith("META-INF/") &&
                                    (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))
                    ) {
                        v1 = true
                        break
                    }
                }
                if (v1) result.add(0, "v1")
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "readSignatureSchemes v1 failed: ${t.message}")
        }
        return result.distinct()
    }

    /* ------------------------------------------------------------------ */
    /* 匹配                                                                */
    /* ------------------------------------------------------------------ */

    private fun matchPackers(apk: File, profile: ApkProfile): List<PackerMatch> {
        // so 名：lib/ 下的 + 藏在 assets / 任意目录里的
        // （爱加密把 libexec.so 藏 assets/ijm_lib、珊瑚灵御藏 assets/libreincp.so、
        //   启明星辰藏在 assets/venCache/ —— 只看 lib/ 会漏掉一半）
        val soNames =
                (profile.soFiles.map { it.name.substringAfterLast('/') } +
                                profile.allPaths
                                        .filter { it.endsWith(".so") }
                                        .map { it.substringAfterLast('/') })
                        .distinct()

        // 资源名：assets 相对路径 + src/ 下 + zip 根目录文件
        // 随风的 src/<md5>、顶象的 dsnapk0000.vd、阿里聚安全的 aliprotect.dat 都在根目录
        val assetNames =
                profile.allPaths
                        .mapNotNull { p ->
                            when {
                                p.startsWith("assets/") -> p.removePrefix("assets/")
                                p.startsWith("src/") -> p
                                !p.contains('/') -> p
                                else -> null
                            }
                        }
                        .distinct()
        val appClass = profile.applicationClass
        val soSet = soNames.toHashSet()
        val assetSet = assetNames.toHashSet()
        val dexProbe = DexProbe(apk, profile.dexFiles)
        val manifestProbe = ManifestProbe(apk)

        val scored = mutableListOf<PackerMatch>()
        val generic = mutableListOf<PackerMatch>()

        for (rule in PackerSignatures.ALL) {
            val isGeneric = rule.id == "packer.generic"
            val evidences = mutableListOf<Evidence>()

            // 1) Application 类 —— 最硬的一条，先判
            if (appClass != null) {
                val hit = rule.entryPatterns.firstOrNull { appClassMatches(it, appClass) }
                if (hit != null) {
                    evidences +=
                            Evidence(
                                    EvidenceKind.APP_CLASS,
                                    "application 指向 $appClass",
                                    EvidenceKind.APP_CLASS.weight
                            )
                }
            }

            // 2) native 库名（可能同时命中多个文件，多出来的每个 +5，封顶 +10）
            val soHits =
                    rule.soPatterns
                            .flatMap { p ->
                                if (!p.contains('*')) {
                                    if (p in soSet) listOf(p) else emptyList()
                                } else {
                                    soNames.filter { globMatch(p, it) }
                                }
                            }
                            .distinct()
            if (soHits.isNotEmpty()) {
                evidences +=
                        Evidence(
                                EvidenceKind.SO_LIB,
                                describeHits("so", soHits),
                                EvidenceKind.SO_LIB.weight + bonusOf(soHits.size)
                        )
            }

            // 3) assets / 根目录 特征文件
            val assetHits =
                    rule.assetPatterns
                            .flatMap { p ->
                                if (!p.contains('*')) {
                                    if (p in assetSet) listOf(p) else emptyList()
                                } else {
                                    assetNames.filter {
                                        globMatch(p, it) || globMatch(p, it.substringAfterLast('/'))
                                    }
                                }
                            }
                            .distinct()
            if (assetHits.isNotEmpty()) {
                evidences +=
                        Evidence(
                                EvidenceKind.ASSET,
                                describeHits("文件", assetHits),
                                EvidenceKind.ASSET.weight + bonusOf(assetHits.size)
                        )
            }

            // 4) Manifest 原文里的类名字符串（APP_CLASS 没命中时才作为补充）
            if (evidences.none { it.kind == EvidenceKind.APP_CLASS }) {
                for (s in rule.entryPatterns) {
                    if (manifestProbe.contains(s)) {
                        evidences +=
                                Evidence(
                                        EvidenceKind.MANIFEST_STRING,
                                        "Manifest 出现 \"$s\"",
                                        EvidenceKind.MANIFEST_STRING.weight
                                )
                        break
                    }
                }
            }

            // 5) dex 字符串只作辅证，且只有前面已经命中时才去扫（省时间）
            if (evidences.isNotEmpty()) {
                for (s in rule.dexStrings) {
                    if (dexProbe.containsAscii(s)) {
                        evidences +=
                                Evidence(
                                        EvidenceKind.DEX_STRING,
                                        "dex 内出现 \"$s\"",
                                        EvidenceKind.DEX_STRING.weight
                                )
                        break
                    }
                }
            }

            if (evidences.isEmpty()) continue

            var score = evidences.sumOf { it.score }.coerceAtMost(100)
            // 无实物样本的规则只有文件名作依据，压分
            if (!rule.hasSample) score = (score * 0.8f).toInt()

            val match = PackerMatch(rule = rule, score = score, evidences = evidences)
            if (isGeneric) generic += match else scored += match
        }

        // 通用壳规则只在没认出具体厂商时才有意义，否则纯噪音
        val bestNamed = scored.maxOfOrNull { it.score } ?: 0
        if (bestNamed < 50) scored += generic

        return scored
                .filter { it.score >= 20 }
                .sortedWith(
                        compareByDescending<PackerMatch> { it.score }
                                .thenByDescending { it.rule.priority }
                                .thenBy { it.rule.id }
                )
                .take(6)
    }

    /** 命中多个文件时给的额外分：每个 +5，最多 +10，避免总分被堆到没意义 */
    private fun bonusOf(count: Int): Int = ((count - 1) * 5).coerceAtMost(10)

    private fun describeHits(label: String, hits: List<String>): String =
            if (hits.size <= 3) "$label 命中 " + hits.joinToString("、")
            else "$label 命中 " + hits.take(3).joinToString("、") + " 等 ${hits.size} 个"

    /**
     * Application 类匹配。
     * 支持三种：完全相等、以 ".pattern" 结尾（pattern 是不带包名的短名）、glob 通配。
     * 刻意不做「包含」匹配 —— 否则 MyWrapperProxyApplication 这类应用自己的代理类
     * 会被误判成 StubWrapperProxyApplication。
     */
    private fun appClassMatches(pattern: String, appClass: String): Boolean {
        if (pattern.contains('*')) return globMatch(pattern, appClass)
        if (appClass == pattern) return true
        if (appClass.endsWith(".$pattern")) return true
        // 大小写不敏感兜底（某些壳会改大小写）
        return appClass.equals(pattern, ignoreCase = true)
    }

    private fun detectStacks(profile: ApkProfile): List<StackMatch> {
        val soNames = profile.soFiles.map { it.name.substringAfterLast('/') }
        val assetNames = profile.assetFiles.map { it.name.removePrefix("assets/") }
        val out = mutableListOf<StackMatch>()
        for (rule in PackerSignatures.STACKS) {
            val hits = mutableListOf<String>()
            for (p in rule.soPatterns) {
                val hit = soNames.firstOrNull { globMatch(p, it) }
                if (hit != null) hits += hit
            }
            for (p in rule.assetPatterns) {
                val hit =
                        assetNames.firstOrNull {
                            globMatch(p, it) ||
                                    globMatch(p, it.substringAfterLast('/')) ||
                                    (p.startsWith("*") && it.endsWith(p.removePrefix("*")))
                        }
                if (hit != null) hits += "assets/$hit"
            }
            if (hits.isNotEmpty()) {
                out += StackMatch(rule.name, "${rule.detailBase} · 命中 ${hits.take(3).joinToString("、")}")
            }
        }
        return out
    }

    private fun structuralNotes(profile: ApkProfile): List<String> {
        val notes = mutableListOf<String>()
        if (profile.dexFiles.isEmpty()) {
            notes += "APK 内没有 classes.dex —— 极大概率是整体加固（壳把 dex 全部藏进 so/assets）"
        } else if (profile.dexCount == 1 && profile.dexBytes < 120 * 1024) {
            notes +=
                    "只有 1 个 classes.dex 且仅 ${profile.dexBytes / 1024} KB —— 正常业务 App 很少这么小，典型壳特征"
        } else {
            notes += "共 ${profile.dexCount} 个 dex，合计 ${profile.dexBytes / 1024} KB"
        }

        val abis = profile.soFiles.mapNotNull { it.name.split('/').getOrNull(1) }.distinct().sorted()
        if (abis.isNotEmpty()) {
            notes += "支持的 ABI：${abis.joinToString("、")}"
        }

        if (profile.applicationClass != null) {
            notes += "Application 类：${profile.applicationClass}"
        }
        if (profile.signatureSchemes.isEmpty()) {
            notes += "未检测到任何签名方案 —— 可能是被工具重打包过，或签名块被破坏"
        }
        if (profile.soFiles.isEmpty()) {
            notes += "lib 目录为空 —— 无 native 代码（纯 Java/Kotlin 项目，脱壳难度低）"
        }
        return notes
    }

    /* ------------------------------------------------------------------ */
    /* 工具                                                                */
    /* ------------------------------------------------------------------ */

    /** 支持 * 通配的简单 glob（大小写敏感，Android so 名都是小写） */
    fun globMatch(pattern: String, name: String): Boolean {
        if (pattern == name) return true
        if (!pattern.contains('*')) return false
        val parts = pattern.split('*')
        var idx = 0
        for ((i, part) in parts.withIndex()) {
            if (part.isEmpty()) continue
            val found = name.indexOf(part, idx)
            if (found < 0) return false
            if (i == 0 && !pattern.startsWith("*") && found != 0) return false
            idx = found + part.length
        }
        if (!pattern.endsWith("*") && !name.endsWith(parts.last())) return false
        return true
    }

    /**
     * dex 特征串探针。
     *
     * 性能取舍：不扫全部 dex。壳的桩 dex 一定很小（样本库里 30~120 KB），
     * 所以只读「classes.dex + 体积最小的 3 个」，总共不超过 4 个 × 8MB。
     * 大包（40+ dex）如果全扫会把扫描时间从 200ms 拉到十几秒。
     */
    private class DexProbe(private val apk: File, dexFiles: List<ZipMember>) {
        private val candidates: List<ZipMember> =
                run {
                    val first = dexFiles.firstOrNull { it.name == "classes.dex" }
                    (listOfNotNull(first) + dexFiles.sortedBy { it.size })
                            .distinctBy { it.name }
                            .take(4)
                }

        private val buffers: List<ByteArray> by lazy { candidates.map { readHead(apk, it.name) } }

        fun containsAscii(needle: String): Boolean {
            if (needle.isEmpty()) return false
            val nb = needle.toByteArray(Charsets.UTF_8)
            for (buf in buffers) {
                if (indexOf(buf, nb) >= 0) return true
            }
            return false
        }

        private fun readHead(apk: File, entry: String): ByteArray {
            return try {
                ZipFile(apk).use { zip ->
                    val e = zip.getEntry(entry) ?: return ByteArray(0)
                    zip.getInputStream(e).use { input ->
                        val limit = min(e.size, DEX_SCAN_LIMIT).toInt()
                        val buf = ByteArray(limit)
                        var read = 0
                        while (read < limit) {
                            val n = input.read(buf, read, limit - read)
                            if (n <= 0) break
                            read += n
                        }
                        if (read == limit) buf else buf.copyOf(read)
                    }
                }
            } catch (t: Throwable) {
                ByteArray(0)
            }
        }
    }

    /**
     * Manifest 探针：AndroidManifest.xml 是二进制 AXML，不打算完整解析，
     * 只在原始字节里找特征串，ASCII 与 UTF-16LE 两种编码都试一遍。
     */
    private class ManifestProbe(apk: File) {
        private val bytes: ByteArray = load(apk)

        private fun load(apk: File): ByteArray {
            return try {
                ZipFile(apk).use { zip ->
                    val e = zip.getEntry("AndroidManifest.xml")
                    if (e == null || e.size > 8L * 1024 * 1024) {
                        ByteArray(0)
                    } else {
                        zip.getInputStream(e).use { it.readBytes() }
                    }
                }
            } catch (t: Throwable) {
                ByteArray(0)
            }
        }

        fun contains(s: String): Boolean {
            if (bytes.isEmpty() || s.isEmpty()) return false
            if (indexOf(bytes, s.toByteArray(Charsets.UTF_8)) >= 0) return true
            val utf16 = ByteArray(s.length * 2)
            for (i in s.indices) {
                utf16[i * 2] = s[i].code.toByte()
                utf16[i * 2 + 1] = (s[i].code shr 8).toByte()
            }
            return indexOf(bytes, utf16) >= 0
        }
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || hay.size < needle.size) return -1
        val first = needle[0]
        val max = hay.size - needle.size
        var i = 0
        while (i <= max) {
            if (hay[i] == first) {
                var j = 1
                while (j < needle.size && hay[i + j] == needle[j]) j++
                if (j == needle.size) return i
            }
            i++
        }
        return -1
    }
}
