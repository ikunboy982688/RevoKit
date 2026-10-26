package com.ai.assistance.operit.core.revokit

import android.content.Context
import com.ai.assistance.operit.core.subpack.ApkReverseEngineer
import com.ai.assistance.operit.core.subpack.KeyStoreHelper
import com.ai.assistance.operit.util.AppLogger
import com.android.apksig.ApkSigner
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import pxb.android.axml.Axml
import pxb.android.axml.AxmlReader
import pxb.android.axml.AxmlVisitor
import pxb.android.axml.AxmlWriter

/**
 * 把修好的 dex 塞回 APK，并产出可直接安装的签名包。
 *
 * 流程对齐实战验证过的做法：重写 zip（换 dex + 去掉旧签名 + 可选修入口）→ zipalign → apksig 签名。
 * 后两步直接复用应用里已有的 ApkReverseEngineer，不重复造轮子。
 *
 * 三个关键细节：
 * - dex 用 STORED（不压缩）写入，和 zip -0 等价。ART 可以直接 mmap，也避开压缩 dex 的坑
 * - resources.arsc 必须不压缩存储，否则 targetSdk 30+ 报 RESOURCES_ARSC_COMPRESSED
 * - 回填 dex 后 AndroidManifest 里的 application 还指着壳的 StubApp，必须改回真实入口，否则装上也起不来
 */
object ApkRepacker {

    private const val TAG = "ApkRepacker"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    private val DEX_RE = Regex("^classes\\d*\\.dex$")
    private val SIG_RE =
            Regex("^META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))$", RegexOption.IGNORE_CASE)

    data class Result(
            val ok: Boolean,
            val apk: File?,
            val replaced: List<String>,
            val removedSignatureFiles: Int,
            val addedDexBytes: Long,
            val manifestPatched: Boolean,
            val removedShell: List<String>,
            val log: List<String>
    )

    /**
     * @param dexFiles 要写进去的 dex，顺序即 classes.dex、classes2.dex …
     * @param entryClass 真实 Application 类名，非空则改写 AndroidManifest 的 application android:name
     * @param cleanShell 是否顺手删掉壳残留（壳 so、加密容器、运行时目录）
     */
    fun replaceDexes(
            context: Context,
            sourceApk: File,
            dexFiles: List<File>,
            outDir: File,
            entryClass: String? = null,
            cleanShell: Boolean = false
    ): Result {
        val log = ArrayList<String>()
        if (dexFiles.isEmpty()) {
            return Result(false, null, emptyList(), 0, 0, false, emptyList(), listOf("没有给 dex，无法重打包"))
        }
        outDir.mkdirs()
        val baseName = sourceApk.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val unsigned = File(outDir, "${baseName}_unsigned.apk")
        val aligned = File(outDir, "${baseName}_aligned.apk")
        val signed = File(outDir, "${baseName}_signed.apk")

        val replaced = ArrayList<String>()
        val removedShell = ArrayList<String>()
        var removedSig = 0
        var addedBytes = 0L
        var manifestPatched = false

        if (cleanShell) {
            try {
                ZipFile(sourceApk).use { zip ->
                    val members = ArrayList<Pair<String, Long>>()
                    val es = zip.entries()
                    while (es.hasMoreElements()) {
                        val e = es.nextElement()
                        if (!e.isDirectory) members.add(e.name to e.size)
                    }
                    val p = ShellCleaner.plan(members)
                    if (p.count == 0) {
                        log.add("没找到可清理的壳残留（可能本来就没壳，或者特征库没覆盖）")
                    } else {
                        log.add("壳残留清理：${p.count} 项、约 ${p.bytes / 1024}KB")
                    }
                }
            } catch (t: Throwable) {
                AppLogger.w(TAG, "预扫壳残留失败：${t.message}")
            }
        }

        try {
            if (unsigned.exists()) unsigned.delete()
            ZipFile(sourceApk).use { zip ->
                BufferedOutputStream(unsigned.outputStream(), 1 shl 16).use { bos ->
                    ZipOutputStream(bos).use { out ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            if (e.isDirectory) continue
                            val name = e.name
                            if (DEX_RE.matches(name)) {
                                replaced.add(name)
                                continue
                            }
                            if (SIG_RE.matches(name)) {
                                removedSig++
                                continue
                            }
                            if (cleanShell) {
                                val reason = ShellCleaner.classify(name)
                                if (reason != null) {
                                    removedShell.add("$name（$reason）")
                                    continue
                                }
                            }

                            var payload: ByteArray? = null
                            if (name == "AndroidManifest.xml" && !entryClass.isNullOrBlank()) {
                                val original = zip.getInputStream(e).use { it.readBytes() }
                                val patched = patchApplicationName(original, entryClass.trim())
                                if (patched != null) {
                                    payload = patched
                                    manifestPatched = true
                                    log.add("已把 application 入口改成 ${entryClass.trim()}")
                                } else {
                                    payload = original
                                    log.add("Manifest 里找不到 application 节点，入口没改")
                                }
                            }

                            val keepStored =
                                    payload != null ||
                                            e.method == ZipEntry.STORED ||
                                            name == "resources.arsc"

                            if (payload != null) {
                                val ne = ZipEntry(name)
                                ne.method = ZipEntry.DEFLATED
                                out.putNextEntry(ne)
                                out.write(payload)
                                out.closeEntry()
                                continue
                            }

                            val ne = ZipEntry(name)
                            if (keepStored && e.size >= 0) {
                                ne.method = ZipEntry.STORED
                                ne.size = e.size
                                ne.crc = e.crc
                            } else {
                                ne.method = ZipEntry.DEFLATED
                            }
                            out.putNextEntry(ne)
                            zip.getInputStream(e).use { input -> input.copyTo(out, 1 shl 16) }
                            out.closeEntry()
                        }

                        dexFiles.forEachIndexed { index, f ->
                            val dexName = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
                            val bytes = f.readBytes()
                            val ne = ZipEntry(dexName)
                            ne.method = ZipEntry.STORED
                            ne.size = bytes.size.toLong()
                            ne.crc = crc32(bytes)
                            out.putNextEntry(ne)
                            out.write(bytes)
                            out.closeEntry()
                            addedBytes += bytes.size
                            log.add("写入 $dexName  ${bytes.size / 1024}KB（STORED 不压缩）")
                        }
                    }
                }
            }
            log.add("已替换原 dex ${replaced.size} 个：${replaced.joinToString("、")}")
            log.add("已清除旧签名文件 $removedSig 个")
            if (removedShell.isNotEmpty()) {
                log.add("已清理壳残留 ${removedShell.size} 项：${removedShell.take(8).joinToString("、")}")
                if (removedShell.size > 8) log.add("…另有 ${removedShell.size - 8} 项")
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "重写 zip 失败", t)
            return Result(
                    false, null, replaced, removedSig, addedBytes, manifestPatched, removedShell,
                    log + "重写 zip 失败：${t.message}"
            )
        }

        val engineer = ApkReverseEngineer(context)

        val alignedOk =
                try {
                    engineer.zipalign(unsigned, aligned, 4)
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "zipalign 失败", t)
                    false
                }
        log.add(if (alignedOk) "zipalign 完成（4 字节对齐）" else "zipalign 失败，直接用未对齐包签名")

        val toSign = if (alignedOk && aligned.exists()) aligned else unsigned
        val keystore =
                try {
                    KeyStoreHelper.getOrCreateKeystore(context)
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "取 debug 密钥失败", t)
                    null
                }
        if (keystore == null || !keystore.exists()) {
            log.add("没拿到 debug 密钥，只能输出未签名包（用 MT 管理器签一下）")
            return Result(false, unsigned, replaced, removedSig, addedBytes, manifestPatched, removedShell, log)
        }
        log.add("使用 debug 密钥：${keystore.name}（${keystore.length()} 字节）")

        val signResult = signApk(keystore, toSign, signed)
        if (signResult.first) log.add("签名方案：v1 + v2 + v3 全开（缺 v1 会被做签名校验的 App 拦）")

        if (!signResult.first || !signed.exists()) {
            log.add("签名失败：${signResult.second ?: "未知原因"}")
            return Result(false, unsigned, replaced, removedSig, addedBytes, manifestPatched, removedShell, log)
        }
        log.add("签名完成：${signed.name}  ${signed.length() / 1024 / 1024}MB")

        try {
            unsigned.delete()
            aligned.delete()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "清理中间文件失败：${t.message}")
        }

        return Result(true, signed, replaced, removedSig, addedBytes, manifestPatched, removedShell, log)
    }

    /**
     * 从 dex 文件里推断真实 Application 候选。
     *
     * 这一步最容易出错：Manifest 里的桩类只是壳的代理，真正要写进去的是继承链的**叶子节点**。
     * 选了基类会跳过 attachBaseContext，全局 Context 为 null，装上也起不来。
     */
    fun inferEntries(dexFiles: List<File>): List<DexRepair.EntryCandidate> {
        val bufs = ArrayList<ByteArray>(dexFiles.size)
        for (f in dexFiles) {
            if (!f.isFile) continue
            if (f.length() > 64L * 1024 * 1024) continue
            bufs.add(f.readBytes())
        }
        return DexRepair.inferEntryClasses(bufs)
    }

    /**
     * 直接用 apksig 签名，显式打开 v1 + v2 + v3。
     *
     * ApkSigner 的默认策略是 minSdk >= 24 就不签 v1，但很多做签名校验的 App
     * 会去看 META-INF 里的 v1 文件，缺了直接判定被改包。所以这里全开。
     */
    private fun signApk(keystore: File, input: File, output: File): Pair<Boolean, String?> {
        return try {
            val ks =
                    KeyStoreHelper.getKeyStoreInstance("PKCS12")
                            ?: KeyStore.getInstance("PKCS12")
            FileInputStream(keystore).use { ks.load(it, "android".toCharArray()) }

            val alias =
                    when {
                        ks.containsAlias("androidkey") -> "androidkey"
                        ks.aliases().hasMoreElements() -> ks.aliases().nextElement()
                        else -> return false to "密钥库里没有任何别名"
                    }
            val key = ks.getKey(alias, "android".toCharArray()) as? PrivateKey
                    ?: return false to "别名 $alias 取不到私钥"
            val chain =
                    ks.getCertificateChain(alias)?.map {
                        it as X509Certificate
                    } ?: return false to "别名 $alias 没有证书链"
            if (chain.isEmpty()) return false to "证书链为空"

            val config = ApkSigner.SignerConfig.Builder(alias, key, chain).build()
            if (output.exists()) output.delete()
            ApkSigner.Builder(listOf(config))
                    .setInputApk(input)
                    .setOutputApk(output)
                    .setMinSdkVersion(26)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .setV4SigningEnabled(false)
                    .build()
                    .sign()
            true to null
        } catch (t: Throwable) {
            AppLogger.e(TAG, "apksig 签名失败", t)
            false to (t.message ?: t.javaClass.simpleName)
        }
    }

    /** 改 manifest 里 application 的 android:name。找不到 application 节点返回 null */    private fun patchApplicationName(manifestBytes: ByteArray, newName: String): ByteArray? {
        return try {
            val reader = AxmlReader(manifestBytes)
            val axml = Axml()
            reader.accept(axml)
            val manifest =
                    axml.firsts.firstOrNull { it.name == "manifest" }
                            ?: return null
            val app =
                    manifest.children.firstOrNull { it.name == "application" }
                            ?: return null

            val existing = app.attrs.firstOrNull { it.name == "name" }
            if (existing != null) {
                existing.value = newName
            } else {
                app.attrs.add(
                        Axml.Node.Attr().apply {
                            name = "name"
                            ns = ANDROID_NS
                            resourceId = -1
                            type = AxmlVisitor.TYPE_STRING
                            value = newName
                        }
                )
            }

            val writer = AxmlWriter()
            axml.accept(writer)
            writer.toByteArray()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "改 Manifest 入口失败：${t.message}")
            null
        }
    }

    private fun crc32(b: ByteArray): Long {
        val c = CRC32()
        c.update(b)
        return c.value
    }

    fun outDir(context: Context): File {
        val base =
                context.getExternalFilesDir("revokit_export") ?: File(context.filesDir, "revokit_export")
        return File(base, "repack")
    }
}
