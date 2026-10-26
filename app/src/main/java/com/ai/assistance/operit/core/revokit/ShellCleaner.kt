package com.ai.assistance.operit.core.revokit

/**
 * 壳残留清理
 *
 * 脱完壳的 APK 里还留着壳自己的 so、加密容器、配置文件。留着有两个坏处：
 * - 体积虚胖（乐固那个 assets/0OO00l111l1l 有 11.9MB，纯废物）
 * - 部分壳启动后会自检这些文件，缺了一般不崩，但留着会走「被篡改」分支
 *
 * 匹配策略（保守优先，宁可漏删不可错删）：
 * 1. 先把 PackerSignatures 里 49 家壳的 so 名全拿过来按文件名匹配 —— so 名这种东西不会撞业务
 * 2. 再补一份人工审过的「确定是壳」的 assets / 根目录清单
 * 3. 凡是可能装着业务 dex 的容器（启明星辰放在 assets/venCache 目录下的 dex、
 *    omas 改名成 png 的 classes 文件、落叶的 assets/vwwwwwvwww）一律不删，
 *    那是脱壳失败时唯一的备份
 *
 * 只有明确命中才删，匹配不上就原样保留。
 */
object ShellCleaner {

    /** 乐固 / 御安全在根目录塞的这几个目录，全是壳的运行时数据 */
    private val RESIDUE_DIRS =
            listOf("tosversion/", "tarm/", "t86/", "t86_64/", "tosversion", "tarm", "t86", "t86_64")

    /**
     * 人工审过的「确定是壳」清单。
     * 格式统一为 zip 内路径的片段，命中 basename 或 assets 相对路径即算命中。
     */
    private val RESIDUE_FILES =
            listOf(
                    // 乐固 / 腾讯御安全
                    "0OO00l111l1l",
                    "o0oooOO0ooOo.dat",
                    // 360
                    ".jgapp",
                    // 爱加密
                    "ijiami.ajm",
                    "IJMDal.Data",
                    "ijm_lib/",
                    // 梆梆
                    "secData0.jar",
                    "classes0.jar",
                    "meta-data/manifest.mf",
                    "meta-data/rsa.pub",
                    "meta-data/rsa.sig",
                    // 网易易盾
                    "_ntcfg_.data",
                    "nedata.db",
                    // 阿里聚安全
                    "aliprotect.dat",
                    // 顶象
                    "dsnapk0000.vd",
                    "dsnstub000.vd",
                    // 百度
                    "baiduprotect.m",
                    // 娜迦的分片容器目录
                    "maindata/",
                    // 乐固企业版抽方法体的索引
                    "dexMethod_0.dat",
                    "dexMethod_1.dat"
            )

    /** 通配符版的残留文件（走 glob 匹配） */
    private val RESIDUE_GLOBS =
            listOf(
                    "libshell*.so",
                    "libjiagu*.so",
                    "libjgcxi*.so",
                    "libtosprotection*.so",
                    "libSecShell*.so",
                    "libsecshell*.so",
                    "libDexHelper*.so",
                    "libdexjni*.so",
                    "libijiami*.so",
                    "libijm*.so",
                    "libnaga*.so",
                    "libnagapt*.so",
                    "libbaiduprotect*.so",
                    "libtup*.so",
                    "libnesec*.so",
                    "libunisec*.so",
                    "libmobisec*.so",
                    "libapk000*.so",
                    "libstub000*.so",
                    "libkws*.so",
                    "libKwProtectSDK*.so",
                    "libxloader*.so",
                    "libddog*.so",
                    "libvmp*.so",
                    "dexMethod_*.dat",
                    "dsnapk*.vd",
                    "dsnstub*.vd",
                    "yidun*.dat",
                    "nesec*.dat",
                    "baiduprotectmac-*",
                    "l5f259fe1_*.so",
                    "libShadowSafetyProtect*.so",
                    "libijmDataEncryption*.so"
            )

    /**
     * 判断一个 zip 成员该不该删。
     *
     * @return 删除原因；null 表示保留
     */
    fun classify(name: String): String? {
        val lower = name.lowercase()
        val base = name.substringAfterLast('/')
        val baseLower = base.lowercase()

        // 壳的运行时目录
        for (d in RESIDUE_DIRS) {
            if (lower.startsWith(d) || lower.contains("/$d")) return "壳目录 $d"
        }

        // 人工清单：既按完整路径比，也按文件名比（360 的 libjiagu.so 有时在 assets 里）
        for (f in RESIDUE_FILES) {
            val fl = f.lowercase()
            if (lower == fl || lower.endsWith("/$fl") || baseLower == fl.substringAfterLast('/')) {
                return "壳残留文件 $f"
            }
            if (fl.endsWith("/") && lower.contains(fl)) return "壳目录 $f"
        }

        // 特征库的 so 名 + 人工 glob
        val globs = RESIDUE_GLOBS + PackerSignatures.ALL.flatMap { it.soPatterns }
        for (g in globs) {
            if (PackerIdentifier.globMatch(g, base) ||
                            PackerIdentifier.globMatch(g, baseLower) ||
                            PackerIdentifier.globMatch(g.lowercase(), baseLower)
            ) {
                return "壳 so $g"
            }
        }

        return null
    }

    /**
     * 生成清理计划。
     *
     * @param members zip 内所有成员（路径 + 大小）
     */
    fun plan(members: List<Pair<String, Long>>): Plan {
        val remove = ArrayList<Pair<String, String>>()
        var bytes = 0L
        for ((name, size) in members) {
            val reason = classify(name) ?: continue
            remove.add(name to reason)
            bytes += size
        }
        return Plan(remove, bytes)
    }

    data class Plan(
            /** 待删成员：路径 + 原因 */
            val remove: List<Pair<String, String>>,
            val bytes: Long
    ) {
        val count: Int get() = remove.size
    }
}
