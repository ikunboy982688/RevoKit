package com.ai.assistance.operit.core.revokit

/**
 * RevoKit 加固特征库
 *
 * 数据来源：用户 2026-10 整理的「加固特征.zip」样本库（49 家壳，34 家有真实样本，
 * 每家带 assets/lib 实际文件名 + AndroidManifest 里被替换的 Application 类）。
 * 凡标注「样本特征」的都来自实际文件，不是网上抄的。
 *
 * 重要教训（来自样本库）：
 * - 光看 Application 类会误判：娜迦的桩类也叫 com.stub.StubApp，跟 360 完全一样；
 *   易固、Frezrik 也都用 libjiagu.so。所以必须 so / assets / appclass 一起看。
 * - 因此 Application 类命中给最高分，同分时靠 so 名区分。
 */
data class PackerRule(
        /** 唯一 id，用于持久化与 skill 映射（如 360 -> "packer.qihoo"） */
        val id: String,
        /** 展示名 */
        val displayName: String,
        /** 厂商 */
        val vendor: String,
        /** 壳的大类：整体加固 / dex 抽取 / vmp / 混淆 */
        val category: String,
        /** native 库文件名（glob 允许 * 通配） */
        val soPatterns: List<String> = emptyList(),
        /** assets 下的特征文件名（同样允许 *） */
        val assetPatterns: List<String> = emptyList(),
        /** dex 中出现的特征字符串（smali 路径或类名），只作辅证 */
        val dexStrings: List<String> = emptyList(),
        /** AndroidManifest 中 <application android:name> 被替换成的桩类（最准的一条） */
        val entryPatterns: List<String> = emptyList(),
        /** 命中后展示的处理建议 */
        val dumpHints: List<String> = emptyList(),
        /** 样本库里是否有实物样本；无样本的规则只靠文件名，置信度会被压低 */
        val hasSample: Boolean = true,
        /**
         * 同分时的优先级。企业版 / 高级版 / 魔改版共用了普通版的文件（企业版就是在普通版基础上做的），
         * 光靠特征分不出胜负，只能靠它在「更具体」这一侧压过普通版。
         */
        val priority: Int = 0
)

enum class EvidenceKind(val label: String, val weight: Int) {
    /** <application android:name> 精确命中桩类 —— 最硬的一条 */
    APP_CLASS("Application 类", 50),
    SO_LIB("native 库", 35),
    ASSET("assets 资源", 30),
    MANIFEST_STRING("Manifest 特征", 25),
    DEX_STRING("DEX 字符串", 15),
    /** 只作展示，不参与打分：告诉用户「为什么判这一代」 */
    ZIP_TIME("zip 时间", 0)
}

data class Evidence(val kind: EvidenceKind, val detail: String, val score: Int)

data class PackerMatch(
        val rule: PackerRule,
        val score: Int,
        val evidences: List<Evidence>
) {
    val confidence: String
        get() =
                when {
                    score >= 75 -> "确定"
                    score >= 50 -> "高度疑似"
                    score >= 25 -> "疑似"
                    else -> "弱线索"
                }

    val confidencePercent: Int get() = score.coerceIn(0, 100)
}

/** 技术栈识别结果（跟加固无关，但同样从 zip 结构就能看出来） */
data class StackMatch(val name: String, val detail: String)

/** zip 内的一个成员 */
data class ZipMember(
        val name: String,
        val size: Long,
        /** zip 条目时间（毫秒）。读不到时为 0 —— 不是 1970，别拿它参与年代判断 */
        val timeMs: Long = 0L
)

/**
 * 360 加固的代次。
 *
 * 2026-07-01 起「360加固保」停止免费加固，之后统一走付费引擎 —— 更重的 VMP、
 * 更强的反调试、更多残留文件，处置规格与免费时代不一样，所以代次必须单独标出来。
 */
enum class QihooGeneration(val label: String) {
    OLD("老版"),
    NEW("新版"),
    ENTERPRISE("企业版"),
    UNKNOWN("未知代次")
}

/** 2026-07-01 00:00:00 GMT+8 —— 360 加固保停止免费加固的分界点 */
const val QIHOO_PAID_CUT_MS: Long = 1782835200000L

/** 早于这个时间的一律当假时间（打包器把条目时间写成 1980/2008 的固定值） */
const val ZIP_TIME_SANE_MIN_MS: Long = 1420070400000L // 2015-01-01

/**
 * 360 引擎换代分界：2024-01-01 00:00:00 GMT+8。
 *
 * 桩类 `com.stub.StubApp` 从 2019 年就在用，光看桩类分不出「2020 年的老包」和「今年的新包」。
 * 真 ART 层特征（libjiagu_art.so / jiagu.art）只有 V8 以后才稳定出现，但很多样本拿不到这一层，
 * 所以补一条：桩类命中 `com.stub` 且 dex 时间早于这条线 → 按老版处理（那时 V8 引擎还没铺开）。
 *
 * 这是**经验阈值**，不是官方文档；判定时一律通过「代次依据」证据项暴露，不藏在代码里。
 */
const val QIHOO_V8_ERA_MS: Long = 1704038400000L // 2024-01-01 00:00:00 GMT+8

/**
 * zip 条目时间窗。
 *
 * 为什么需要 usable：很多打包器会为了「可重现构建」把所有条目时间统一写成
 * 1980-02-01 或 2008-01-01。这种情况下时间窗**不能**用来判断年代，
 * 只能老实说「时间戳被抹」—— 硬拿它判代次会得出完全错误的结论。
 */
data class ZipTimeWindow(
        val minMs: Long = 0L,
        val maxMs: Long = 0L,
        /** 时间可信的样本数（只算根目录 classes*.dex 里时间合理的） */
        val sampleCount: Int = 0,
        /** APK 里所有 .dex 条目数（含 assets/ 与子目录），用于看时间证据覆盖了多少 */
        val totalDex: Int = 0,
        val usable: Boolean = false
) {
    /** 全部样本都落在付费时代（2026-07-01 之后） */
    val allAfterPaidCut: Boolean get() = usable && minMs >= QIHOO_PAID_CUT_MS
    /** 全部样本都在付费时代之前 */
    val allBeforePaidCut: Boolean get() = usable && maxMs < QIHOO_PAID_CUT_MS
    /** 跨越分界点（同一包里既有旧文件又有新文件，常见于增量打包） */
    val spansPaidCut: Boolean get() = usable && !allAfterPaidCut && !allBeforePaidCut
}

/** APK 基本画像 */
data class ApkProfile(
        val path: String,
        val fileName: String,
        val fileSize: Long,
        val md5: String,
        val sha256: String,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long,
        val appLabel: String?,
        val applicationClass: String?,
        val minSdk: Int,
        val targetSdk: Int,
        val signatureSchemes: List<String>,
        val dexFiles: List<ZipMember>,
        val soFiles: List<ZipMember>,
        val assetFiles: List<ZipMember>,
        val totalEntries: Int,
        /**
         * 全部条目路径。壳的特征并不都在 assets 里：
         * 随风的 src/<md5>、顶象的 dsnapk0000.vd、阿里聚安全的 aliprotect.dat 都在 zip 根目录；
         * 珊瑚灵御 / 深思数盾 / 启明星辰 甚至把 so 藏在 assets 里。
         */
        val allPaths: List<String> = emptyList(),
        /**
         * zip 条目时间窗。用来判 360 的「0701 付费时代」，但**不能**当硬判据 ——
         * 打包器可能把时间写成 1980/2008 固定值，那时只有 usable=false 才诚实。
         */
        val zipTime: ZipTimeWindow = ZipTimeWindow()
) {
    val dexCount: Int get() = dexFiles.size
    val dexBytes: Long get() = dexFiles.sumOf { it.size }
}

/** 识别总报告 */
data class IdentifyReport(
        val profile: ApkProfile,
        val matches: List<PackerMatch>,
        val stack: List<StackMatch>,
        val structuralNotes: List<String>,
        val elapsedMs: Long
) {
    val primary: PackerMatch? get() = matches.firstOrNull()
}

object PackerSignatures {

    /* ==================================================================
     * 一、国内主流加固（有实物样本，特征来自实际文件）
     * ================================================================== */

    /** 360 加固。样本：assets/libjiagu*.so + assets/.jgapp + com.stub.StubApp */
    val QIHOO =
            PackerRule(
                    id = "packer.qihoo",
                    displayName = "360 加固",
                    vendor = "奇虎 360",
                    category = "整体加固 + dex 抽取",
                    soPatterns =
                            listOf(
                                    "libjiagu.so",
                                    "libjiagu_a64.so",
                                    "libjiagu_x64.so",
                                    "libjiagu_x86.so",
                                    "libjiagu_x86_64.so",
                                    "libjiagu_art.so",
                                    "libjiagu_ls.so",
                                    "libjiagu*.so",
                                    "libprotectClass.so"
                            ),
                    assetPatterns =
                            listOf(
                                    ".jgapp",
                                    // 新版 V8+ 会多出这三个：jiagu.dat 是旧版就有的配置，
                                    // jiagu.art / jiagu_res.zip 是 ART 层壳本体与资源包。
                                    // 老版只有 jiagu.dat，所以这三个是区分老/新版的硬证据。
                                    "jiagu.dat",
                                    "jiagu.art",
                                    "jiagu_res.zip",
                                    "libjiagu*.so",
                                    "libjgcxi*.so",
                                    "libjiagu*.dex",
                                    "libjiagu_vip*.so"
                            ),
                    dexStrings = listOf("com/qihoo/util/"),
                    // 注意：不要把 com.frezrik.jiagu.StubApp 加进来 —— 它有自己的独立规则
                    // （老版变体），两条同时命中会让徽章出现两个 360。
                    // 代次判定直接读 ApkProfile.applicationClass 即可。
                    entryPatterns =
                            listOf("com.stub.StubApp", "com.qihoo.util.StubApplication"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/libjiagu*.so（加密的壳本体）+ assets/.jgapp（配置）",
                                    "root 下以 spawn 方式启动，等 libjiagu 解密完再 dump（过早 dump 拿到的是壳代码）",
                                    "dump 出来的 dex 头常被抹（magic 不是 dex\\n035），需先修头再回填 CodeItem",
                                    "注意与「易固」「Frezrik」「娜迦」区分：它们也用 libjiagu.so 或 com.stub.StubApp",
                                    "代次：只看 libjiagu.so + jiagu.dat = 老版 V7-；多出 libjiagu_art.so / jiagu.art / " +
                                            "jiagu_res.zip / smali 里的 com/qihoo/util/art/ = 新版 V8+"
                            )
            )

    /** 360 企业加固。样本：assets/libjiagu_vip*.so + libjgcxi_a64.so + 混淆过的 StubApp */
    val QIHOO_ENTERPRISE =
            PackerRule(
                    id = "packer.qihoo.enterprise",
                    displayName = "360 企业加固",
                    vendor = "奇虎 360（企业版）",
                    category = "整体加固 + VMP",
                    soPatterns =
                            listOf(
                                    "libjiagu_vip*.so",
                                    "libjgcxi*.so",
                                    "libjgcxi_a64.so",
                                    // 下面两个来自公开教程（公众号清残留清单），本机样本库未实测。
                                    // 只作低权重线索：文件名真存在就多一条证据，不存在也不影响判定。
                                    "libjiagu_enterprise.so",
                                    "libvmp_360.so"
                            ),
                    assetPatterns =
                            listOf(
                                    ".jgapp",
                                    "jiagu.dat",
                                    "jiagu.art",
                                    "jiagu_res.zip",
                                    "libjiagu_vip*.so",
                                    "libjgcxi*.so",
                                    "libjiagu_mips.a",
                                    "libjiagu_vip_mips.a",
                                    // 同上，来自公开教程，未实测
                                    "jiagu_enterprise.dat",
                                    "vmp_config.bin"
                            ),
                    dexStrings = listOf("com/qihoo/util/"),
                    entryPatterns = emptyList(),
                    dumpHints =
                            listOf(
                                    "样本特征：assets 里同时出现 libjiagu_vip_*.so 与 libjgcxi_*.so，这是企业版与普通版的区别",
                                    "企业版带 VMP，关键方法体被抽成自定义字节码，dump 后也无法直接反编译",
                                    "企业版 App 类做了混淆（样本里是反序字符串 dohtemtupni.uruhsnix.moc.StubApp），不能靠类名硬匹配"
                            ),
            priority = 1
    )

    /** 腾讯御安全（原「乐固」）。样本：libshella-4.6.2.2.so + libshell-super+包名.so + assets/0OO00l111l1l */
    val TENCENT_YUASAFE =
            PackerRule(
                    id = "packer.tencent.yuasafe",
                    displayName = "腾讯御安全",
                    vendor = "腾讯",
                    category = "整体加固 + dex 抽取",
                    soPatterns =
                            listOf("libshella-*.so", "libshellx-*.so", "libtosprotection*.so"),
                    assetPatterns =
                            listOf(
                                    "0OO00l111l1l",
                                    "0OO00oo01l1l",
                                    "0OO00oo11l1l",
                                    "o0oooOO0ooOo.dat",
                                    "tosversion",
                                    "t86",
                                    "t86_64"
                            ),
                    dexStrings = listOf("com/tencent/StubShell/"),
                    entryPatterns =
                            listOf(
                                    "StubWrapperProxyApplication",
                                    "com.tencent.StubShell.TxAppEntry",
                                    "com.tencent.StubShell.TxApplication"
                            ),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libshella-<版本>.so（如 libshella-4.6.2.2.so）+ lib/libshell-super<包名>.so",
                                    "assets/0OO00l111l1l 与 o0oooOO0ooOo.dat 是加密的原始 dex/配置，可配合 so 离线解",
                                    "启动后先加载 libshell-super，dump 点在其 attachBaseContext 之后",
                                    "有 anti-frida 检测，hook 前先过检测或改端口"
                            )
            )

    /** 腾讯御安全企业版。样本：libshell-superv*.so + dexMethod_*.dat */
    val TENCENT_YUASAFE_ENT =
            PackerRule(
                    id = "packer.tencent.yuasafe.enterprise",
                    displayName = "腾讯御安全（企业版）",
                    vendor = "腾讯（企业版）",
                    category = "整体加固 + 方法抽取",
                    soPatterns = listOf("libshell-superv*.so", "libshell-supervbasic*.so"),
                    assetPatterns = listOf("dexMethod_*.dat"),
                    dexStrings = listOf("com/tencent/StubShell/"),
                    entryPatterns = listOf("StubWrapperProxyApplication"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libshell-superv.2019.so + assets/dexMethod_*.dat —— 企业版抽方法体，不是整壳加密",
                                    "dexMethod_*.dat 需配合 so 里的算法还原，单纯 dump 内存拿不到被抽的方法"
                            ),
            priority = 1
    )

    /** 梆梆加固。样本：lib/libSecShell.so + assets/classes0.jar + com.SecShell.SecShell.AW */
    val BANGCLE =
            PackerRule(
                    id = "packer.bangcle",
                    displayName = "梆梆加固",
                    vendor = "梆梆安全",
                    category = "整体加固 + dex 抽取",
                    soPatterns =
                            listOf(
                                    "libSecShell*.so",
                                    "libsecshell*.so",
                                    "libsecexe.so",
                                    "libsecmain.so"
                            ),
                    assetPatterns =
                            listOf("classes0.jar", "meta-data/*", "secexe", "secData*.jar", "bangcle*"),
                    dexStrings = listOf("com/SecShell/", "com/secneo/apkwrapper/"),
                    entryPatterns =
                            listOf(
                                    "com.SecShell.SecShell.AW",
                                    "com.secneo.apkwrapper.ApplicationWrapper",
                                    "com.secneo.apkwrapper.AW"
                            ),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libSecShell.so + assets/classes0.jar + assets/meta-data/{manifest.mf,rsa.pub,rsa.sig}",
                                    "dex 主体在 libSecShell 解密后落到内存，dump 点通常在 attachBaseContext 之后",
                                    "新版本做指令抽取，dump 后需跑 CodeItem 回填"
                            )
            )

    /** 梆梆企业版。样本：libDexHelper.so + libdexjni.so + com.secneo.apkwrapper.{AW,CP,AP} */
    val BANGCLE_ENT =
            PackerRule(
                    id = "packer.bangcle.enterprise",
                    displayName = "梆梆企业版",
                    vendor = "梆梆（企业版）",
                    category = "整体加固 + 抽取",
                    soPatterns = listOf("libDexHelper.so", "libdexjni.so", "libDexHelper-x86.so"),
                    assetPatterns = listOf("secData*.jar", "mobisec*"),
                    dexStrings = listOf("com/secneo/apkwrapper/"),
                    entryPatterns =
                            listOf(
                                    "com.secneo.apkwrapper.AW",
                                    "com.secneo.apkwrapper.CP",
                                    "com.secneo.apkwrapper.AP"
                            ),
                    dumpHints =
                            listOf(
                                    "样本特征：libDexHelper.so + libdexjni.so，App 类 com.secneo.apkwrapper.{AW,CP,AP}",
                                    "CP 是以 ContentProvider 注册的（android:initOrder=2147483647），比 Application 更早执行 —— 企业版最明显的特征",
                                    "只 hook attachBaseContext 会漏掉 CP 阶段的解密，注意 CP 才是真实入口"
                            ),
            priority = 1
    )

    /** 爱加密。样本：assets/ijm_lib/<abi>/libexec.so + assets/ijiami.ajm + s.h.e.l.l.S */
    val IJIAMI =
            PackerRule(
                    id = "packer.ijiami",
                    displayName = "爱加密",
                    vendor = "爱加密",
                    category = "整体加固 + dex 抽取",
                    soPatterns = listOf("libexecmain.so", "libexec.so", "libijiami*.so", "libapkprotect.so"),
                    assetPatterns =
                            listOf(
                                    "ijiami.ajm",
                                    "ijiami.dat",
                                    "ijiami_alldata",
                                    "af.bin",
                                    "signed.bin",
                                    "ijm_lib/*/libexec.so"
                            ),
                    dexStrings = listOf("s/h/e/l/l/"),
                    entryPatterns = listOf("s.h.e.l.l.S", "s.h.e.l.l.A"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/ijm_lib/<abi>/libexec.so —— so 藏在 assets 里运行时释放，lib 目录反而没有",
                                    "assets/ijiami.ajm + af.bin + signed.bin 是加密的 dex 与签名校验数据",
                                    "壳类包名 s.h.e.l.l.* 是爱加密的签名，命中即可确认",
                                    "新版本对 MethodBody 做抽取，必须回填 CodeItem 才能反编译"
                            )
            )

    /** 爱加密企业版。样本：assets/libijmDataEncryption*.so + IJMDal.Data */
    val IJIAMI_ENT =
            PackerRule(
                    id = "packer.ijiami.enterprise",
                    displayName = "爱加密（企业版）",
                    vendor = "爱加密（企业版）",
                    category = "整体加固 + 数据加密",
                    soPatterns = listOf("libijmDataEncryption*.so"),
                    assetPatterns = listOf("IJMDal.Data", "libijmDataEncryption*.so"),
                    dexStrings = listOf("s/h/e/l/l/"),
                    entryPatterns = listOf("s.h.e.l.l.S"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/libijmDataEncryption_<abi>.so 全 ABI 齐放 + IJMDal.Data",
                                    "企业版重点在数据加密（DAL），dex 层相对轻，dump 后多数方法可读"
                            ),
            priority = 1
    )

    /** 网易易盾普通版。样本：libnesec.so + libunisec.so + assets/_ntcfg_.data */
    val YIDUN_NORMAL =
            PackerRule(
                    id = "packer.netease.yidun.normal",
                    displayName = "网易易盾（普通版）",
                    vendor = "网易易盾",
                    category = "整体加固",
                    soPatterns = listOf("libnesec.so", "libnesec-x86.so", "libunisec.so"),
                    assetPatterns = listOf("_ntcfg_.data", "yidun*.dat", "nesec*.dat"),
                    dexStrings = listOf("com/netease/"),
                    entryPatterns = listOf("com.netease.android.protect.StubApp"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libnesec.so + lib/libunisec.so 同时存在，assets/_ntcfg_.data 是配置",
                                    "普通版以整壳加密为主，未做方法抽取，dump 后修头即可"
                            )
            )

    /** 网易易盾高级版。样本：libnesec.so + assets/nedata.db + com.netease.nis.wrapper.MyApplication */
    val YIDUN_ADVANCED =
            PackerRule(
                    id = "packer.netease.yidun.advanced",
                    displayName = "网易易盾（高级版）",
                    vendor = "网易易盾（高级版）",
                    category = "整体加固 + 抽取",
                    soPatterns = listOf("libnesec.so", "libnesec-x86.so"),
                    assetPatterns = listOf("nedata.db", "yidun*.dat"),
                    dexStrings = listOf("com/netease/nis/"),
                    entryPatterns = listOf("com.netease.nis.wrapper.MyApplication"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/nedata.db（高级版专有）+ 壳类 com.netease.nis.wrapper.MyApplication",
                                    "与普通版区别：没有 libunisec.so / _ntcfg_.data，改成 nedata.db",
                                    "高级版做了方法抽取，dump 后需回填 CodeItem"
                            ),
            priority = 1
    )

    /** 阿里加固。样本：lib/<pkg>_shell_alijtca_plus.so + assets/ali_sec.dat + com.ali.mobisecenhance.ld.StubApplication */
    val ALI_MOBISEC =
            PackerRule(
                    id = "packer.ali.mobisec",
                    displayName = "阿里加固",
                    vendor = "阿里 (mobisec)",
                    category = "整体加固",
                    soPatterns =
                            listOf(
                                    "lib*_shell_alijtca_plus.so",
                                    "libmobisec*.so",
                                    "libaliprotect*.so",
                                    "libfakejni.so"
                            ),
                    assetPatterns =
                            listOf("ali_sec.dat", "alibaba_version", "mobisec*"),
                    dexStrings = listOf("com/ali/mobisecenhance/"),
                    entryPatterns =
                            listOf(
                                    "com.ali.mobisecenhance.ld.StubApplication",
                                    "com.ali.mobisecenhance.StubApplication"
                            ),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/<包名>_shell_alijtca_plus.so —— so 名里带原包名，很好认",
                                    "assets/ali_sec.dat + alibaba_version 是加密的 dex 与版本标记",
                                    "注意 libsgmain.so / libsgsecuritybody.so 只是阿里安全 SDK，单独出现不代表加固"
                            )
            )

    /** 阿里聚安全（老）。样本：根目录 aliprotect.dat + assets/dingtalkttid */
    val ALI_JU =
            PackerRule(
                    id = "packer.ali.ju",
                    displayName = "阿里聚安全",
                    vendor = "阿里（聚安全，老版）",
                    category = "整体加固",
                    soPatterns = listOf("libmobisec.so", "libsgmain.so"),
                    assetPatterns = listOf("aliprotect.dat", "dingtalkttid"),
                    dexStrings = listOf("com/ali/mobisecenhance/"),
                    entryPatterns = emptyList(),
                    dumpHints =
                            listOf(
                                    "样本特征：根目录 aliprotect.dat（不在 assets 里）+ assets/dingtalkttid",
                                    "老版聚安全，多出现在钉钉生态应用"
                            ),
            priority = 1
    )

    /** 百度加固。样本：libbaiduprotect*.so + assets/baiduprotect{1..9}.jar */
    val BAIDU =
            PackerRule(
                    id = "packer.baidu",
                    displayName = "百度加固",
                    vendor = "百度",
                    category = "整体加固 + 多 dex 分片",
                    soPatterns = listOf("libbaiduprotect*.so"),
                    assetPatterns =
                            listOf(
                                    "baiduprotect*.jar",
                                    "baiduprotect*.dex",
                                    "baiduprotect*.i.dex",
                                    "baiduprotect-sec.dex",
                                    "baiduprotect.m",
                                    "baiduprotect.md"
                            ),
                    dexStrings = listOf("com/baidu/protect/"),
                    entryPatterns = listOf("com.baidu.protect.StubApplication"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/baiduprotect1..9.jar + baiduprotectN.i.dex —— 原始 dex 被切成多片加密",
                                    "分片意味着不能只 dump 一个 dex，要全部拼回按索引排序",
                                    "lib/libbaiduprotect_sdk-<40位哈希>.so 的 so 名带哈希，可作为强特征"
                            )
            )

    /** 百度加固企业版（无实物样本，只有文件名） */
    val BAIDU_ENT =
            PackerRule(
                    id = "packer.baidu.enterprise",
                    displayName = "百度加固（企业版）",
                    vendor = "百度（企业版）",
                    category = "整体加固",
                    soPatterns = listOf("libbaiduprotect*.so"),
                    assetPatterns = listOf("baiduprotect.m", "baiduprotectmac-*", "baiduprotect*.jar"),
                    dexStrings = emptyList(),
                    entryPatterns = emptyList(),
                    dumpHints = listOf("样本特征：assets/baiduprotect.m + baiduprotectmac-<base64>"),
                    hasSample = false,
            priority = 1
    )

    /** 顶象加固。样本：lib/libapk0000.so + lib/libstub000.so + dsnapk0000.vd + com.security.shell.V5App */
    val DINGXIANG =
            PackerRule(
                    id = "packer.dingxiang",
                    displayName = "顶象加固",
                    vendor = "顶象",
                    category = "整体加固 + VMP",
                    soPatterns = listOf("libapk0000.so", "libstub000.so", "libdxhm.so", "libdxva.so"),
                    assetPatterns = listOf("dsnapk*.vd", "dsnstub*.vd", "output-*.zip", "csnb*.data"),
                    dexStrings = listOf("com/security/shell/"),
                    entryPatterns = listOf("com.security.shell.V5App"),
                    dumpHints =
                            listOf(
                                    "样本特征：根目录 dsnapk0000.vd + dsnstub000.vd，lib/libapk0000.so + lib/libstub000.so",
                                    "壳类固定 com.security.shell.V5App，很好认",
                                    "带 VMP，关键方法体抽成自定义字节码，dump 也不能直接反编译"
                            )
            )

    /** 几维安全。样本：libKwProtectSDK.so + libkwsdataenc.so + assets/<name>.dex */
    val KIWI =
            PackerRule(
                    id = "packer.kiwi",
                    displayName = "几维安全",
                    vendor = "几维 (KiwiVM)",
                    category = "VMP",
                    soPatterns =
                            listOf("libKwProtectSDK.so", "libkwsdataenc.so", "libkwscmm.so", "libkwscr.so"),
                    assetPatterns = listOf("ec_dt.lic", "39285EFA.dex", "*kiwi*"),
                    dexStrings = listOf("com/kiwivm/"),
                    entryPatterns = listOf("com.kiwivm.security.StubApplication"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libKwProtectSDK.so + lib/libkwsdataenc.so，壳类 com.kiwivm.security.StubApplication",
                                    "几维主打 so 层 VMP，dex 层通常完整，重点在 .so 还原而不是 dump dex"
                            )
            )

    /** 娜迦加固。样本：lib/libxloader.so + assets/maindata/<uuid>.txt + com.stub.StubApp（跟 360 撞名！） */
    val NAGA =
            PackerRule(
                    id = "packer.naga",
                    displayName = "娜迦加固",
                    vendor = "娜迦",
                    category = "VMP + 整体加固",
                    soPatterns =
                            listOf(
                                    "libxloader.so",
                                    "libchaosvmp.so",
                                    "libddog.so",
                                    "libfdog.so",
                                    "libnagain.so"
                            ),
                    assetPatterns = listOf("maindata/*", "maindata/version", "*naga*"),
                    dexStrings = listOf("com/chaos/", "com/nagain/"),
                    entryPatterns = listOf("com.stub.StubApp"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libxloader.so + assets/maindata/<uuid>.txt —— 原始 dex 按 UUID 分片藏在 maindata",
                                    "警告：娜迦的桩类也是 com.stub.StubApp，跟 360 完全一样；靠 libxloader.so 才能区分",
                                    "chaosvmp 是真正的 VMP，关键方法体无法通过 dump 还原"
                            )
            )

    /** 支付宝加固。样本：libashield.so + libashieldAdapter.so + libsign.so + com.ashield.Stub */
    val ALIPAY =
            PackerRule(
                    id = "packer.alipay",
                    displayName = "支付宝加固",
                    vendor = "蚂蚁 (ashield)",
                    category = "整体加固",
                    soPatterns = listOf("libashield.so", "libashieldAdapter.so", "libsign.so"),
                    assetPatterns = emptyList(),
                    dexStrings = listOf("com/ashield/"),
                    entryPatterns = listOf("com.ashield.Stub"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libashield.so + lib/libashieldAdapter.so + lib/libsign.so 三件套，壳类 com.ashield.Stub",
                                    "库全在 lib 目录、assets 是空的 —— 这点跟爱加密正好相反"
                            )
            )

    /** 启明星辰。样本：assets/venCache/ 下 classes*.dex 明文 + libvenSec*.so */
    val VENUSTECH =
            PackerRule(
                    id = "packer.venustech",
                    displayName = "启明星辰",
                    vendor = "启明星辰",
                    category = "整体加固（弱）",
                    soPatterns = listOf("libvenSec*.so", "libsqlen_venus*.so", "libvenustech*.so"),
                    assetPatterns = listOf("venCache/*", "signCache/*", "venus*"),
                    dexStrings = listOf("com/venustech/"),
                    entryPatterns = emptyList(),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/venCache/classes*.dex —— 这些 dex 是明文直接放着的，可直接抽出来",
                                    "样本库里最容易脱的一家：把 assets/venCache/*.dex 复制出来改名 classesN.dex 即可",
                                    "assets/venCache/version + venus0/venusmd/venusrc/venusrd 是壳的配置文件"
                            )
            )

    /** OPPO 加固。样本：assets/classes1..6.png（dex 伪装成 png）+ libomas.so */
    val OPPO =
            PackerRule(
                    id = "packer.oppo",
                    displayName = "OPPO 加固",
                    vendor = "OPPO (omas)",
                    category = "整体加固（dex 伪装）",
                    soPatterns = listOf("libomas.so", "libomas*.so"),
                    assetPatterns = listOf("classes*.png", "omas*"),
                    dexStrings = listOf("com/omes/omas/"),
                    entryPatterns = listOf("com.omes.omas.ProxyApplication"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/classes1.png ~ classes6.png —— 原始 dex 被改名成 .png 藏起来",
                                    "先抽出来看文件头：如果是 dex\\n035 说明没加密，直接改后缀回 .dex 即可",
                                    "库只有 lib/libomas.so 一个，壳很轻"
                            )
            )

    /** ShadowSafety。样本：assets/libShadowSafetyProtect*.so + libshadowsafety.so + v.m.p */
    val SHADOW_SAFETY =
            PackerRule(
                    id = "packer.shadowsafety",
                    displayName = "ShadowSafety",
                    vendor = "ShadowSafety",
                    category = "整体加固 + VMP",
                    soPatterns = listOf("libshadowsafety.so", "libShadowSafetyProtect*.so"),
                    assetPatterns = listOf("libShadowSafetyProtect*.so", "libShadowSafetyProtect_enc.so"),
                    dexStrings = listOf("v/m/p"),
                    entryPatterns = listOf("v.m.p"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/<abi>/libshadowsafety.so + assets/libShadowSafetyProtect*_<abi>.so 全 ABI 齐放",
                                    "壳类名极短，只有 v.m.p —— 没有这条几乎无法靠类名识别",
                                    "带 VMP"
                            )
            )

    /** 深思数盾。样本：assets/l5f259fe1_*.so + v5f259fe1.l5f259fe1 */
    val SHENSI =
            PackerRule(
                    id = "packer.shensi",
                    displayName = "深思数盾",
                    vendor = "深思数盾",
                    category = "整体加固 + VMP",
                    soPatterns = listOf("l5f259fe1_*.so", "libshensi*.so"),
                    assetPatterns = listOf("l5f259fe1_*.so"),
                    dexStrings = listOf("l5f259fe1/"),
                    entryPatterns = listOf("v5f259fe1.l5f259fe1"),
                    dumpHints =
                            listOf(
                                    "样本特征：so 名带 8 位哈希前缀 l5f259fe1，壳类 v5f259fe1.l5f259fe1（v+哈希 . l+哈希）",
                                    "so 只放 assets，lib 目录是空的 —— 运行时才释放"
                            )
            )

    /** 易固。样本：libjgdtc.so + libjiagu.so + libvmp.so + hehua.StubApp（注意也用 libjiagu.so） */
    val YIGU =
            PackerRule(
                    id = "packer.yigu",
                    displayName = "易固加固",
                    vendor = "易固 (hehua)",
                    category = "整体加固 + VMP",
                    soPatterns = listOf("libjgdtc.so", "libvmp.so", "libjiagu.so"),
                    assetPatterns = emptyList(),
                    dexStrings = listOf("hehua/"),
                    entryPatterns = listOf("hehua.StubApp"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libjgdtc.so + lib/libvmp.so + lib/libjiagu.so 三个同时存在，壳类 hehua.StubApp",
                                    "警告：它也放了一个 libjiagu.so，光看 so 名会被误判成 360，必须看 hehua.StubApp"
                            )
            )

    /** 蛮犀加固。样本：libmxldd.so + com.mx.shell.MXApplication */
    val MANXI =
            PackerRule(
                    id = "packer.manxi",
                    displayName = "蛮犀加固",
                    vendor = "蛮犀",
                    category = "整体加固",
                    soPatterns = listOf("libmxldd.so", "libmx*.so"),
                    assetPatterns = emptyList(),
                    dexStrings = listOf("com/mx/shell/"),
                    entryPatterns = listOf("com.mx.shell.MXApplication", "com.mx.shell.MXAppComponentFactory"),
                    dumpHints = listOf("样本特征：单个 lib/libmxldd.so，壳类 com.mx.shell.MXApplication")
            )

    /** 落叶加固开源版。样本：assets/OoooooOooo + d_shell_data_001 + vwwwwwvwww/<abi>/lib<hex>.so */
    val LUOYE_OSS =
            PackerRule(
                    id = "packer.luoye.oss",
                    displayName = "落叶加固（开源版）",
                    vendor = "落叶 (luoyesiqiu)",
                    category = "整体加固（开源）",
                    soPatterns = listOf("lib66d6a9d9339bd4f5.so"),
                    assetPatterns =
                            listOf("OoooooOooo", "d_shell_data_001", "vwwwwwvwww/*", "vwwwwwvwww/*/*.so"),
                    dexStrings = listOf("com/luoyesiqiu/shell/"),
                    entryPatterns =
                            listOf(
                                    "com.luoyesiqiu.shell.ProxyApplication",
                                    "com.luoyesiqiu.shell.ProxyComponentFactory"
                            ),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/OoooooOooo + assets/d_shell_data_001 + assets/vwwwwwvwww/<abi>/lib<hex>.so",
                                    "开源项目 github.com/luoyesiqiu/dpt-shell，算法公开，可直接照着解",
                                    "因为开源，是最适合练手的样本"
                            )
            )

    /** 落叶加固魔改版。样本：assets/OoooooOooo + app_acf + vwwwwwvwww/<abi>/libdpt.so */
    val LUOYE_MOD =
            PackerRule(
                    id = "packer.luoye.mod",
                    displayName = "落叶加固（魔改版）",
                    vendor = "落叶（二次修改）",
                    category = "整体加固（开源改）",
                    soPatterns = listOf("libdpt.so"),
                    assetPatterns = listOf("OoooooOooo", "app_acf", "app_name", "vwwwwwvwww/*/*.so"),
                    dexStrings = listOf("com/luoyesiqiu/shell/"),
                    entryPatterns = emptyList(),
                    dumpHints =
                            listOf(
                                    "样本特征：在落叶开源版基础上多塞了 assets/app_acf + assets/app_name，壳类名被改成随机十六进制前缀",
                                    "结构与开源版一致，可套用 dpt-shell 的解法，注意 app_acf 可能是额外校验"
                            ),
            priority = 1
    )

    /** 随风加固。样本：classes.dex + src/<md5>（无 lib、无 assets） */
    val SUIFENG =
            PackerRule(
                    id = "packer.suifeng",
                    displayName = "随风加固",
                    vendor = "随风 (beingyi)",
                    category = "整体加固（无 so）",
                    soPatterns = emptyList(),
                    assetPatterns = listOf("src/*"),
                    dexStrings = listOf("cn/beingyi/sub/"),
                    entryPatterns = listOf("cn.beingyi.sub.apps.SubApp.SubApplication"),
                    dumpHints =
                            listOf(
                                    "样本特征：根目录 src/<32位md5> —— 没有 lib、没有 assets，加密数据直接放 zip 根目录，很特殊",
                                    "壳类固定 cn.beingyi.sub.apps.SubApp.SubApplication",
                                    "因为没有 native 库，解密逻辑全在 dex 里，是最容易静态还原的一家"
                            )
            )

    /** Nesun。样本：libzprotect.so + assets/origin.apk + com.nesun.stub.ZAP */
    val NESUN =
            PackerRule(
                    id = "packer.nesun",
                    displayName = "Nesun 加固",
                    vendor = "Nesun",
                    category = "整体加固（套 APK）",
                    soPatterns = listOf("libzprotect.so"),
                    assetPatterns = listOf("origin.apk"),
                    dexStrings = listOf("com/nesun/stub/"),
                    entryPatterns = listOf("com.nesun.stub.ZAP"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/origin.apk —— 直接把原始 APK 整个塞进 assets，最原始的一种加固",
                                    "脱壳极简单：把 assets/origin.apk 抽出来就是原包"
                            )
            )

    /** ARM 加固。样本：libarm_protect*.so + libArmEpicVm*.so + assets/被加固的dex.dex */
    val ARM_PROTECT =
            PackerRule(
                    id = "packer.arm",
                    displayName = "ARM 加固",
                    vendor = "ARM (Epic)",
                    category = "整体加固 + VMP",
                    soPatterns = listOf("libarm_protect*.so", "libArmEpicVm*.so", "libarm*.so"),
                    assetPatterns = listOf("被加固的dex.dex", "config.so"),
                    dexStrings = listOf("arm/StubApp"),
                    entryPatterns = listOf("arm.StubApp"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/被加固的dex.dex（中文文件名，极好认）+ lib/libarm_protect*.so + lib/libArmEpicVm*.so",
                                    "初级版只有一个 so；高级版多一个 libArmEpicVm 走 VMP"
                            )
            )

    /** Appdome。样本：libloader.so + assets/m7a,m8a + android.support.v4.soft.ApplicationMain */
    val APPDOME =
            PackerRule(
                    id = "packer.appdome",
                    displayName = "Appdome 加固",
                    vendor = "Appdome（海外）",
                    category = "整体加固 + RASP",
                    soPatterns = listOf("libloader.so", "libappdome*.so"),
                    assetPatterns = listOf("m7a", "m8a"),
                    dexStrings = listOf("android/support/v4/soft/"),
                    entryPatterns = listOf("android.support.v4.soft.ApplicationMain"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libloader.so + assets/m7a + assets/m8a",
                                    "壳类伪装成 android.support.v4.soft.ApplicationMain，看着像官方支持库，很容易漏判",
                                    "主打 RASP（运行时自我保护），anti-debug 比常规壳强"
                            )
            )

    /** CTools 加固。样本：libnmmp.so + libnmmvm.so + assets/ByCrash */
    val CTOOLS =
            PackerRule(
                    id = "packer.ctools",
                    displayName = "CTools 加固",
                    vendor = "CTools",
                    category = "整体加固 + VM",
                    soPatterns = listOf("libnmmp.so", "libnmmvm.so"),
                    assetPatterns = listOf("ByCrash"),
                    dexStrings = listOf("crash/stub/"),
                    entryPatterns = listOf("crash.stub.ProxyApplication"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/<abi>/libnmmp.so + libnmmvm.so，assets/ByCrash 是壳配置",
                                    "nmmvm 是自研 VM，方法体被转成自定义字节码"
                            )
            )

    /** DexProtect（样本库里标注「自行辨别，结果不准确」）。样本：assets/dp.*.so.dat */
    val DEXPROTECT =
            PackerRule(
                    id = "packer.dexprotect",
                    displayName = "DexProtect",
                    vendor = "DexProtect",
                    category = "整体加固（数据全 .dat）",
                    soPatterns = emptyList(),
                    assetPatterns =
                            listOf(
                                    "dp.arm-v7.so.dat",
                                    "dp.arm-v8.so.dat",
                                    "dp.x86*.so.dat",
                                    "classes.dex.dat",
                                    "resources.dat"
                            ),
                    dexStrings = emptyList(),
                    entryPatterns = emptyList(),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/dp.<abi>.so.dat + classes.dex.dat + resources.dat，全部加 .dat 后缀",
                                    "该样本在样本库里被标注为「自行辨别，结果不准确」，特征可能不是这家独有"
                            )
            )

    /** Epic v2。样本：assets/Epic_dexs/<name>.dex + assets/Epic_so/libEP_<abi>.so + Epic.ProtectApp */
    val EPIC =
            PackerRule(
                    id = "packer.epic",
                    displayName = "Epic 加固",
                    vendor = "Epic",
                    category = "整体加固（开源系）",
                    soPatterns = listOf("libEP_arm.so", "libEP_arm64.so", "libEP_x86*.so"),
                    assetPatterns = listOf("Epic_dexs/*", "Epic_so/*", "Epic_dexs/*.dex"),
                    dexStrings = listOf("Epic/"),
                    entryPatterns = listOf("Epic.ProtectApp"),
                    dumpHints =
                            listOf(
                                    "样本特征：assets/Epic_dexs/<name>.dex + assets/Epic_so/libEP_*.so —— 目录名带 Epic，非常好认",
                                    "原始 dex 在 assets/Epic_dexs 里，先抽出来看是否明文"
                            )
            )

    /** Google Play 加固（PairIP）。样本：libpairipcore.so + 30 个随机名 assets */
    val GOOGLE_PAIRIP =
            PackerRule(
                    id = "packer.google.pairip",
                    displayName = "Google Play 加固",
                    vendor = "Google (PairIP)",
                    category = "整体加固 + 完整性校验",
                    soPatterns = listOf("libpairipcore.so"),
                    assetPatterns = emptyList(),
                    dexStrings = listOf("com/pairip/"),
                    entryPatterns = listOf("com.pairip.application.Application"),
                    dumpHints =
                            listOf(
                                    "样本特征：lib/libpairipcore.so + assets 下 30 个 16 位随机名文件（如 4owCveHemXqr7zMk）",
                                    "随机名 assets 就是打散的 dex 分片，数量和名字每次打包都不同，不能硬编码",
                                    "带签名/完整性校验，改包后可能起不来"
                            )
            )

    /* ==================================================================
     * 二、只有文件名、没有实物样本的（置信度会被压低）
     * ================================================================== */

    val APKPROTECT =
            PackerRule(
                    id = "packer.apkprotect",
                    displayName = "APKProtect",
                    vendor = "APKProtect",
                    category = "整体加固",
                    soPatterns = listOf("libAPKProtect.so"),
                    dumpHints = listOf("仅凭 so 名识别，无实物样本，置信度有限"),
                    hasSample = false
            )

    val APPSEALIN =
            PackerRule(
                    id = "packer.appsealin",
                    displayName = "AppSealin",
                    vendor = "AppSealin",
                    category = "整体加固",
                    soPatterns = listOf("libcovault.so", "libcovault-appsec.so"),
                    dumpHints = listOf("仅凭 so 名识别，无实物样本"),
                    hasSample = false
            )

    val APPSHIELD =
            PackerRule(
                    id = "packer.appshield",
                    displayName = "AppShield",
                    vendor = "AppShield",
                    category = "整体加固",
                    soPatterns = listOf("libahope.so"),
                    dumpHints = listOf("仅凭 so 名识别，无实物样本"),
                    hasSample = false
            )

    val FREZRIK =
            PackerRule(
                    id = "packer.frezrik",
                    displayName = "Frezrik 加固",
                    vendor = "Frezrik",
                    category = "整体加固",
                    soPatterns = listOf("libjiagu.so"),
                    dexStrings = listOf("com/frezrik/jiagu/"),
                    entryPatterns = listOf("com.frezrik.jiagu.StubApp"),
                    dumpHints = listOf("样本特征：lib/libjiagu.so + com.frezrik.jiagu.StubApp —— 又是撞名 libjiagu 的一家"),
                    hasSample = false
            )

    val MASTER_MU =
            PackerRule(
                    id = "packer.mastermu",
                    displayName = "Master-Mu",
                    vendor = "Master-Mu",
                    category = "整体加固",
                    soPatterns = listOf("libMaster-Mu*.so"),
                    assetPatterns = listOf("Master-Mu"),
                    dumpHints = listOf("样本库里只有一个叫 Master-Mu 的空目录，无任何可判定特征"),
                    hasSample = false
            )

    val TIAMO =
            PackerRule(
                    id = "packer.tiamo",
                    displayName = "Tiamo 沐雪加固",
                    vendor = "Tiamo / 沐雪",
                    category = "整体加固",
                    soPatterns = listOf("libTiamo.so", "libTiamoMuxue.so", "libmuxue.so"),
                    assetPatterns = listOf("沐雪"),
                    dexStrings = listOf("com/muxue/"),
                    entryPatterns = listOf("com.muxue.xue"),
                    dumpHints = listOf("样本特征：lib/libTiamo.so + lib/libTiamoMuxue.so + lib/libmuxue.so + assets/沐雪"),
                    hasSample = false
            )

    val UUSAFE =
            PackerRule(
                    id = "packer.uusafe",
                    displayName = "UU 安全",
                    vendor = "UU 安全",
                    category = "整体加固",
                    soPatterns = listOf("libuusafe.so", "libuusafe.jar.so", "libuusafeempty.so"),
                    dumpHints = listOf("仅凭 so 名识别，无实物样本"),
                    hasSample = false
            )

    val CHINA_MOBILE =
            PackerRule(
                    id = "packer.chinamobile",
                    displayName = "中国移动加固",
                    vendor = "中国移动 (mogosec)",
                    category = "整体加固",
                    soPatterns = listOf("libmogosec*.so", "libcmvmp.so", "decrypt.so"),
                    assetPatterns =
                            listOf(
                                    "mogosec_classes",
                                    "mogosec_data*",
                                    "mogosec_marc*",
                                    "mogosecurity.so",
                                    "libmogosec_dex.so"
                            ),
                    dexStrings = listOf("com/mogosec/"),
                    dumpHints = listOf("样本特征：assets 下 mogosec_* 系列文件（classes/dexinfo/marc/data）"),
                    hasSample = false
            )

    val HAIYUNAN =
            PackerRule(
                    id = "packer.haiyunan",
                    displayName = "海云安加固",
                    vendor = "海云安",
                    category = "整体加固",
                    soPatterns = listOf("libsecidea.so"),
                    assetPatterns = listOf("secdata1.dat", "secdata2.dat"),
                    dumpHints = listOf("样本特征：lib/<abi>/libsecidea.so + assets/secdata1.dat、secdata2.dat"),
                    hasSample = false
            )

    val CORALLINE =
            PackerRule(
                    id = "packer.coralline",
                    displayName = "珊瑚灵御加固",
                    vendor = "珊瑚灵御",
                    category = "整体加固",
                    soPatterns = emptyList(),
                    assetPatterns = listOf("libreincp.so", "libreincp_x86.so"),
                    dumpHints = listOf("样本特征：so 藏在 assets 里（assets/libreincp*.so），lib 目录没有"),
                    hasSample = false
            )

    val RISING =
            PackerRule(
                    id = "packer.rising",
                    displayName = "瑞星加固",
                    vendor = "瑞星",
                    category = "整体加固",
                    soPatterns = listOf("librsprotect.so"),
                    dumpHints = listOf("仅凭 so 名 librsprotect.so 识别，无实物样本"),
                    hasSample = false
            )

    val NQSHIELD =
            PackerRule(
                    id = "packer.nqshield",
                    displayName = "网秦加固",
                    vendor = "网秦",
                    category = "整体加固",
                    soPatterns = listOf("libnqshield.so"),
                    dumpHints = listOf("仅凭 so 名 libnqshield.so 识别，无实物样本"),
                    hasSample = false
            )

    val PAYEGIS =
            PackerRule(
                    id = "packer.payegis",
                    displayName = "通付盾加固",
                    vendor = "通付盾 (PayEgis)",
                    category = "整体加固 + 虚拟化",
                    soPatterns =
                            listOf("libegis.so", "libegis_security.so", "libegis_sls.so", "libegis-x86.so"),
                    assetPatterns = listOf("libegis.a", "virtual", "mode", "PK"),
                    dumpHints = listOf("样本特征：lib/libegis*.so 全家桶 + assets/virtual（虚拟化引擎）"),
                    hasSample = false
            )

    /** 通用兜底：只在没识别出厂商时给一点提示，权重故意压低 */
    val GENERIC_STUB =
            PackerRule(
                    id = "packer.generic",
                    displayName = "未具名的壳特征",
                    vendor = "未知",
                    category = "疑似加固",
                    dexStrings =
                            listOf("Lcom/SEC/SEC/Helper;->stub()V", "com/SEC/SEC/Helper", "Lcom/secneo/"),
                    dumpHints =
                            listOf(
                                    "只命中了通用壳签名，没匹配到具体厂商。建议看下方 lib 列表人工确认",
                                    "注意：不要靠 ProxyApplication 这类通用类名判定厂商 —— 差不多每家壳都有叫这个名字的类"
                            ),
                    hasSample = false
            )

    val ALL: List<PackerRule> =
            listOf(
                    QIHOO,
                    QIHOO_ENTERPRISE,
                    TENCENT_YUASAFE,
                    TENCENT_YUASAFE_ENT,
                    BANGCLE,
                    BANGCLE_ENT,
                    IJIAMI,
                    IJIAMI_ENT,
                    YIDUN_NORMAL,
                    YIDUN_ADVANCED,
                    ALI_MOBISEC,
                    ALI_JU,
                    BAIDU,
                    BAIDU_ENT,
                    DINGXIANG,
                    KIWI,
                    NAGA,
                    ALIPAY,
                    VENUSTECH,
                    OPPO,
                    SHADOW_SAFETY,
                    SHENSI,
                    YIGU,
                    MANXI,
                    LUOYE_OSS,
                    LUOYE_MOD,
                    SUIFENG,
                    NESUN,
                    ARM_PROTECT,
                    APPDOME,
                    CTOOLS,
                    DEXPROTECT,
                    EPIC,
                    GOOGLE_PAIRIP,
                    APKPROTECT,
                    APPSEALIN,
                    APPSHIELD,
                    FREZRIK,
                    MASTER_MU,
                    TIAMO,
                    UUSAFE,
                    CHINA_MOBILE,
                    HAIYUNAN,
                    CORALLINE,
                    RISING,
                    NQSHIELD,
                    PAYEGIS,
                    GENERIC_STUB
            )

    /* ---------------- 技术栈特征（非加固） ---------------- */

    data class StackRule(
            val name: String,
            val soPatterns: List<String> = emptyList(),
            val assetPatterns: List<String> = emptyList(),
            val detailBase: String
    )

    val STACKS: List<StackRule> =
            listOf(
                    StackRule(
                            "Flutter",
                            soPatterns = listOf("libflutter.so", "libapp.so"),
                            assetPatterns = listOf("flutter_assets/*", "*flutter_assets*"),
                            detailBase = "Dart AOT 编译，业务逻辑在 libapp.so 里（blutter 可解析）"
                    ),
                    StackRule(
                            "React Native",
                            soPatterns = listOf("libreactnativejni.so", "libhermes.so", "libjsc.so"),
                            assetPatterns = listOf("index.android.bundle", "*react_native*"),
                            detailBase = "JS bundle 通常在 assets/index.android.bundle，未加密时可直接读"
                    ),
                    StackRule(
                            "Unity",
                            soPatterns = listOf("libunity.so", "libil2cpp.so", "libmonobdwgc-2.0.so"),
                            assetPatterns = listOf("bin/Data/*", "unity default resources"),
                            detailBase = "IL2CPP / Mono，逻辑在 global-metadata.dat 与 libil2cpp.so"
                    ),
                    StackRule(
                            "Cocos",
                            soPatterns = listOf("libcocos2djs.so", "libcocos2d.so", "libcocos.so"),
                            assetPatterns = listOf("src/project.json", "*cocos*"),
                            detailBase = "Cocos Creator / 2d-x，脚本在 assets 或 src 下"
                    ),
                    StackRule(
                            "Xposed / LSPosed 模块",
                            assetPatterns = listOf("xposed_init"),
                            detailBase = "assets/xposed_init 声明了入口类，本质是模块不是普通 App"
                    ),
                    StackRule(
                            "Cordova / WebView 壳",
                            soPatterns = listOf("libcordova.so"),
                            assetPatterns = listOf("www/index.html", "*cordova*"),
                            detailBase = "业务逻辑在 assets/www 的 H5 里"
                    ),
                    StackRule(
                            "Electron / Node（少见）",
                            soPatterns = listOf("libnode.so", "libelectron.so"),
                            detailBase = "桌面端方案移植，assets 里通常有 app.asar"
                    )
            )
}
