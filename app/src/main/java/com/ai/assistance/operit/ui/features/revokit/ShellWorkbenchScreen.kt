package com.ai.assistance.operit.ui.features.revokit

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.core.revokit.ApkRepacker
import com.ai.assistance.operit.core.revokit.DexDumper
import com.ai.assistance.operit.core.revokit.DexRepair
import com.ai.assistance.operit.core.revokit.FlutterProbe
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 脱壳工作台
 *
 * 三个页签：DEX 修复 / 内存 dump / Flutter 体检。
 * 覆盖脱壳链路里除了「重打包过签」以外的全部环节。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellWorkbenchScreen(onGoBack: () -> Unit) {
    var tabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("一键脱修", "DEX 修复", "内存 dump", "Flutter", "重打包")

    CustomScaffold(
            topBar = {
                TopAppBar(
                        title = { Text("脱壳工作台") },
                        navigationIcon = {
                            IconButton(onClick = onGoBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                )
            }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                    selectedTabIndex = tabIndex,
                    edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                            selected = tabIndex == i,
                            onClick = { tabIndex = i },
                            text = { Text(t, fontSize = 13.sp) }
                    )
                }
            }
            when (tabIndex) {
                0 -> OneClickTab()
                1 -> RepairTab()
                2 -> DumpTab()
                3 -> FlutterTab()
                else -> RepackTab()
            }
        }
    }
}

/* ================================================================== */
/* 一键脱修                                                            */
/* ================================================================== */

private data class OneClickOutcome(
        val dexCount: Int,
        val entry: String?,
        val repack: ApkRepacker.Result,
        val log: List<String>,
        val dexRows: List<Pair<String, String>>,
        val workDir: File
)

/**
 * 一键脱修：修头 → 体检 → 推断入口 → 重打包签名，一把跑完。
 *
 * 四个页签是给需要逐步排查的人用的；这一页是给「我只想拿到能装的包」的人用的。
 * 每一步都复用已有实现，不另起一套逻辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneClickTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apk by remember { mutableStateOf<File?>(null) }
    var dexes by remember { mutableStateOf<List<File>>(emptyList()) }
    var optAutoEntry by remember { mutableStateOf(true) }
    var optCleanShell by remember { mutableStateOf(true) }
    var optClearTries by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var outcome by remember { mutableStateOf<OneClickOutcome?>(null) }

    val apkPicker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    try {
                        apk = withContext(Dispatchers.IO) { copyPicked(context, uri, "revokit_apk") }
                        outcome = null
                        error = null
                    } catch (t: Throwable) {
                        error = "读取 APK 失败：${t.message ?: t.javaClass.simpleName}"
                    }
                }
            }

    val dexPicker =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
            ) { uris: List<Uri> ->
                if (uris.isEmpty()) return@rememberLauncherForActivityResult
                scope.launch {
                    busy = true
                    error = null
                    try {
                        val picked =
                                withContext(Dispatchers.IO) {
                                    uris.mapNotNull { u ->
                                        runCatching { copyPicked(context, u, "revokit_dex_multi") }
                                                .getOrNull()
                                    }
                                }
                        dexes = (dexes + picked).distinctBy { it.absolutePath }
                        outcome = null
                    } catch (t: Throwable) {
                        error = "读取 dex 失败：${t.message ?: t.javaClass.simpleName}"
                    } finally {
                        busy = false
                    }
                }
            }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NoteCard(
                title = "这一页干什么",
                body =
                        "把「修头 → 体检 → 推断入口 → 重打包签名」四步串起来一次跑完，产出可直接安装的包。\n" +
                                "dex 从哪来：先在「内存 dump」页脱出来，或者用别的工具 dump 好再选进来。\n" +
                                "dex 顺序很重要：classes.dex 对应第一个选中项，往后依次 classes2、classes3……",
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
        )

        SectionCard(title = "第一步 · 原包") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                        onClick = { apkPicker.launch(arrayOf("*/*")) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (apk == null) "选择原 APK" else "换一个")
                }
                if (apk != null) {
                    OutlinedButton(onClick = { apk = null; outcome = null }, enabled = !busy) { Text("清空") }
                }
            }
            apk?.let {
                Text(
                        "已选：${it.name}  ${formatBytes(it.length())}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }

        SectionCard(title = "第二步 · 要装进去的 dex（${dexes.size}）") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                        onClick = { dexPicker.launch(arrayOf("*/*")) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加 dex（可多选）")
                }
                if (dexes.isNotEmpty()) {
                    OutlinedButton(onClick = { dexes = emptyList(); outcome = null }, enabled = !busy) {
                        Text("清空")
                    }
                }
            }
            dexes.forEachIndexed { i, f ->
                Text(
                        "${if (i == 0) "classes.dex" else "classes${i + 1}.dex"}  ←  ${f.name}  ${formatBytes(f.length())}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                )
            }
        }

        SectionCard(title = "第三步 · 选项并开跑") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = optAutoEntry, onCheckedChange = { optAutoEntry = it })
                Text("自动推断真实 Application 入口", fontSize = 12.sp)
            }
            Text(
                    "推断的是继承链的叶子节点。选基类会跳过 attachBaseContext，全局 Context 为 null，启动必崩。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = optCleanShell, onCheckedChange = { optCleanShell = it })
                Text("清理壳残留（壳 so / 加密容器 / 运行时目录）", fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = optClearTries, onCheckedChange = { optClearTries = it })
                Text("清空 try 段（jadx 反编译崩了才勾）", fontSize = 12.sp)
            }
            Button(
                    onClick = {
                        val src = apk ?: return@Button
                        scope.launch {
                            busy = true
                            error = null
                            outcome = null
                            try {
                                val o =
                                        withContext(Dispatchers.IO) {
                                            runOneClick(
                                                    context = context,
                                                    apk = src,
                                                    dexes = dexes,
                                                    autoEntry = optAutoEntry,
                                                    cleanShell = optCleanShell,
                                                    clearTries = optClearTries
                                            )
                                        }
                                outcome = o
                            } catch (t: Throwable) {
                                error = "一键脱修失败：${t.message ?: t.javaClass.simpleName}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && apk != null && dexes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("处理中，大包可能要一分多钟…")
                } else {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("开始一键脱修")
                }
            }
        }

        error?.let {
            NoteCard(
                    title = "出错了",
                    body = it,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        outcome?.let { o ->
            SectionCard(title = if (o.repack.ok) "完成 · 已产出可安装包" else "未完成") {
                Text(
                        if (o.repack.ok) "下面这个包可以直接装，签名是应用自带的 debug 密钥"
                        else "见日志，通常是签名失败或 dex 写入出错",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color =
                                if (o.repack.ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                )
                KeyValueTable(
                        listOf(
                                "处理 dex" to "${o.dexCount} 个",
                                "真实入口" to (o.entry ?: "未改动"),
                                "清理壳残留" to "${o.repack.removedShell.size} 项",
                                "替换原 dex" to "${o.repack.replaced.size} 个",
                                "工作目录" to o.workDir.absolutePath
                        )
                )
                Text(
                        "各 dex 体检",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                )
                KeyValueTable(o.dexRows)
                LogBlock(o.log)
                o.repack.apk?.let {
                    Text(
                            "产物：\n${it.absolutePath}\n${formatBytes(it.length())}",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 一键脱修的实际执行体。全在 IO 线程里跑，不碰 Compose 状态 */
private fun runOneClick(
        context: Context,
        apk: File,
        dexes: List<File>,
        autoEntry: Boolean,
        cleanShell: Boolean,
        clearTries: Boolean
): OneClickOutcome {
    val base =
            context.getExternalFilesDir("revokit_export") ?: File(context.filesDir, "revokit_export")
    val workDir = File(base, "oneclick_" + System.currentTimeMillis()).apply { mkdirs() }
    val fixedDir = File(workDir, "fixed").apply { mkdirs() }

    val log = ArrayList<String>()
    val rows = ArrayList<Pair<String, String>>()
    val fixed = ArrayList<File>(dexes.size)

    log.add("原包：${apk.name}（${formatBytes(apk.length())}）")
    log.add("工作目录：${workDir.absolutePath}")
    log.add("")

    dexes.forEachIndexed { i, f ->
        val outName = if (i == 0) "classes.dex" else "classes${i + 1}.dex"
        val raw = f.readBytes()
        val q = DexRepair.quality(raw)
        val repair = DexRepair.repair(raw, DexRepair.Options(fixHeader = true, clearTries = clearTries))
        val target = File(fixedDir, outName)
        target.writeBytes(repair.bytes)
        fixed.add(target)

        rows.add(outName to "${q.level} · ${formatBytes(target.length())}")
        log.add("[$outName] 来源 ${f.name}")
        log.addAll(repair.log.map { "    $it" })
        log.add("    体检：${q.detail}")
        if (!repair.after.healthy) {
            log.add("    警告：修头后仍不一致（${DexRepair.describe(repair.after)}），可能出现 VerifyError")
        }
        log.add("")
    }

    var entry: String? = null
    if (autoEntry) {
        val cands =
                try {
                    ApkRepacker.inferEntries(fixed)
                } catch (t: Throwable) {
                    log.add("推断入口异常：${t.message}")
                    emptyList()
                }
        val top = cands.firstOrNull()
        if (top == null) {
            log.add("自动推断入口：失败（这批 dex 里没有 Application 子类），入口保持原样")
        } else {
            entry = top.className
            log.add("自动推断入口：${top.className}（${top.score} 分）")
            log.add("    继承链：" + top.chain.joinToString(" -> "))
            log.add("    依据：" + top.reasons.joinToString("；"))
            cands.getOrNull(1)?.let {
                log.add("    次选：${it.className}（${it.score} 分）—— 如果启动崩了，换它试试")
            }
        }
        log.add("")
    }

    log.add("开始重打包…")
    val repack = ApkRepacker.replaceDexes(context, apk, fixed, workDir, entry, cleanShell)
    log.addAll(repack.log)

    return OneClickOutcome(
            dexCount = fixed.size,
            entry = entry,
            repack = repack,
            log = log,
            dexRows = rows,
            workDir = workDir
    )
}

/* ================================================================== */
/* DEX 修复                                                           */
/* ================================================================== */

@Composable
private fun RepairTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dexFile by remember { mutableStateOf<File?>(null) }
    var dumpFile by remember { mutableStateOf<File?>(null) }
    var inspection by remember { mutableStateOf<DexRepair.Inspection?>(null) }
    var result by remember { mutableStateOf<DexRepair.Result?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedPath by remember { mutableStateOf<String?>(null) }
    var optFixHeader by remember { mutableStateOf(true) }
    var optClearTries by remember { mutableStateOf(false) }

    val dexPicker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    busy = true
                    error = null
                    result = null
                    savedPath = null
                    inspection = null
                    try {
                        val f = withContext(Dispatchers.IO) { copyPicked(context, uri, "revokit_dex") }
                        dexFile = f
                        inspection = withContext(Dispatchers.IO) { DexRepair.inspect(f.readBytes()) }
                    } catch (t: Throwable) {
                        error = "读取失败：${t.message ?: t.javaClass.simpleName}"
                    } finally {
                        busy = false
                    }
                }
            }

    val dumpPicker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    try {
                        dumpFile = withContext(Dispatchers.IO) { copyPicked(context, uri, "revokit_dump") }
                    } catch (t: Throwable) {
                        error = "读取 dump 失败：${t.message}"
                    }
                }
            }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "第一步 · 选要修的 dex") {
            Text(
                    "把脱壳或 dump 出来的 dex 选进来。先做体检，再决定修哪几项。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                        onClick = { dexPicker.launch(arrayOf("*/*")) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (dexFile == null) "选择 dex" else "换一个")
                }
                if (dexFile != null) {
                    OutlinedButton(
                            onClick = {
                                dexFile = null
                                inspection = null
                                result = null
                                savedPath = null
                            },
                            enabled = !busy
                    ) { Text("清空") }
                }
            }
            dexFile?.let {
                Text(
                        "已选：${it.name}  ${formatBytes(it.length())}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                )
            }
        }

        inspection?.let { ins -> InspectionCard(ins) }

        SectionCard(title = "第二步 · 修复选项") {
            ToggleRow(
                    title = "重算 dex 头",
                    desc = "file_size → signature → checksum，顺序固定。修法必选，不修装不进系统",
                    checked = optFixHeader,
                    onCheckedChange = { optFixHeader = it }
            )
            ToggleRow(
                    title = "清空 try / catch 段",
                    desc = "回填后 handler 错位会让 jadx 崩。开了会丢异常逻辑，但能反编译",
                    checked = optClearTries,
                    onCheckedChange = { optClearTries = it }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                    "方法体回填（可选）：选一个从内存 dump 出来的同版本 dex，按 method_idx 把真实指令搬回来。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { dumpPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (dumpFile == null) "选择 dump dex" else "换 dump")
                }
                if (dumpFile != null) {
                    TextButton(onClick = { dumpFile = null }) { Text("不要回填") }
                }
            }
            dumpFile?.let {
                Text(
                        "dump：${it.name}  ${formatBytes(it.length())}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(6.dp))
            Button(
                    onClick = {
                        val f = dexFile ?: return@Button
                        scope.launch {
                            busy = true
                            error = null
                            savedPath = null
                            try {
                                val r =
                                        withContext(Dispatchers.IO) {
                                            DexRepair.repair(
                                                    f.readBytes(),
                                                    DexRepair.Options(
                                                            fixHeader = optFixHeader,
                                                            clearTries = optClearTries,
                                                            dumpFile = dumpFile
                                                    )
                                            )
                                        }
                                result = r
                            } catch (t: Throwable) {
                                error = "修复失败：${t.message ?: t.javaClass.simpleName}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && dexFile != null,
                    modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (busy) "处理中…" else "开始修复")
            }
        }

        error?.let {
            NoteCard(
                    title = "出错了",
                    body = it,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        result?.let { r ->
            SectionCard(title = "修复结果") {
                val a = r.after
                val healthy = a.healthy
                Text(
                        if (healthy) "dex 头已一致，可以拿去重打包" else "仍有问题，见下面的日志",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color =
                                if (healthy) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                )
                KeyValueTable(
                        listOf(
                                "版本" to "v${a.header?.version ?: 0}",
                                "大小" to formatBytes(r.bytes.size.toLong()),
                                "类 / 方法" to "${a.classCount} / ${a.methodCount}",
                                "校验" to DexRepair.describe(a),
                                "清 tries" to "${r.triesCleared} 个 code_item"
                        )
                )
                r.backfill?.let { b ->
                    KeyValueTable(
                            listOf(
                                    "回填成功" to "${b.copied}",
                                    "dump 缺指令" to "${b.missingInDump}",
                                    "超长跳过" to "${b.skippedTooLong}",
                                    "清 native 标记" to "${b.nativeCleared}"
                            )
                    )
                }
                LogBlock(r.log)
                Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val out =
                                            withContext(Dispatchers.IO) {
                                                val base =
                                                        context.getExternalFilesDir("revokit_export")
                                                                ?: File(context.filesDir, "revokit_export")
                                                val dir = File(base, "repair").apply { mkdirs() }
                                                val name =
                                                        (dexFile?.nameWithoutExtension ?: "repaired")
                                                                .replace(Regex("[^A-Za-z0-9._-]"), "_") +
                                                                "_fixed.dex"
                                                val f = File(dir, name)
                                                f.writeBytes(r.bytes)
                                                f.absolutePath
                                            }
                                    savedPath = out
                                } catch (t: Throwable) {
                                    error = "导出失败：${t.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出修复后的 dex")
                }
                savedPath?.let {
                    Text(
                            "已保存到\n$it",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InspectionCard(ins: DexRepair.Inspection) {
    SectionCard(title = "体检结果") {
        val h = ins.header
        if (h == null) {
            Text(
                    ins.parseError ?: "不是合法 dex",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
            )
            return@SectionCard
        }
        Text(
                when {
                    ins.healthy -> "格式完整，可以直接用"
                    ins.looksExtracted -> "方法体大面积缺失，典型被抽取过，需要回填或 dump"
                    else -> "头部信息不一致，建议勾上「重算 dex 头」"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color =
                        if (ins.healthy) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
        )
        KeyValueTable(
                listOf(
                        "magic / 版本" to "dex · v${h.version}",
                        "实际大小" to formatBytes(ins.actualSize.toLong()),
                        "头部记录大小" to formatBytes(h.fileSize),
                        "checksum" to if (ins.checksumOk) "一致" else "不符",
                        "signature" to if (ins.signatureOk) "一致" else "不符",
                        "类数量" to "${ins.classCount}",
                        "方法数量" to "${ins.methodCount}",
                        "native 方法" to "${ins.nativeMethodCount}（${(ins.nativeRatio * 100).toInt()}%）",
                        "无方法体" to "${ins.emptyCodeCount}",
                        "带 try 的方法体" to "${ins.tryMethodCount}"
                )
        )
        if (ins.looksExtracted) {
            Text(
                    "提示：native 与空方法体合计超过 10%，是一代/二代抽取壳的典型特征。" +
                            "这类 dex 直接反编译只能看到空壳，必须先把方法体补回来。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ================================================================== */
/* 内存 dump                                                          */
/* ================================================================== */

@Composable
private fun DumpTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var root by remember { mutableStateOf<DexDumper.RootState?>(null) }
    var processes by remember { mutableStateOf<List<DexDumper.AppProcess>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var dumping by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DexDumper.AppProcess?>(null) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var result by remember { mutableStateOf<DexDumper.DumpResult?>(null) }
    var outDir by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                val r = DexDumper.checkRoot(context)
                root = r
                if (r.rooted) {
                    processes = DexDumper.listProcesses(context)
                }
            } catch (t: Throwable) {
                error = "读取进程失败：${t.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "运行环境") {
            val r = root
            when {
                r == null -> Text("正在检查 root…", fontSize = 13.sp)
                r.rooted ->
                        Column {
                                Text(
                                        r.detail,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                )
                                if (r.idOutput.isNotBlank()) {
                                        Text(
                                                r.idOutput,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }
                else -> {
                    Text(
                            r.detail,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                    )
                    Text(
                            "内存 dump 必须 root。没有 root 只能走云端脱壳服务，本页用不了。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { reload() }, enabled = !loading && !dumping) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重新检测")
                }
            }
        }

        if (root?.rooted == true) {
            SectionCard(title = "选目标进程（${processes.size}）") {
                OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索包名 / 进程名") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth()
                )
                val filtered =
                        if (query.isBlank()) processes
                        else processes.filter { it.name.contains(query, ignoreCase = true) }
                if (filtered.isEmpty()) {
                    Text("没有匹配的进程", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    filtered.take(40).forEach { p ->
                        val isSel = selected?.pid == p.pid
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(
                                                        if (isSel) MaterialTheme.colorScheme.secondaryContainer
                                                        else MaterialTheme.colorScheme.surface,
                                                        RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    "${p.pid}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(56.dp)
                            )
                            Text(p.name, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { selected = p }) {
                                Text(if (isSel) "已选" else "选它")
                            }
                        }
                    }
                    if (filtered.size > 40) {
                        Text(
                                "只显示前 40 个，输入更精确的关键字再找",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionCard(title = "开始 dump") {
                Text(
                        "会扫目标的匿名段 / dalvik 段 / ashmem / memfd，从内存里捞 dex 原块。" +
                                "捞出来的块不一定头部完好，回来用「DEX 修复」修一下就成。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NoteCard(
                        title = "扫不到 dex 先看这三条",
                        body =
                                "1. 时机太早：先把目标 App 打开、停在主界面，再回来 dump。" +
                                        "壳是在 Application 里解密的，冷启动那一瞬间内存里还是壳代码。\n" +
                                        "2. 补码壳要多扫一轮：同一个 dex 连续两次 size + SHA-1 完全一致才算补完。\n" +
                                        "3. 扫出来只有 1~2 个、或者类数量只有一两千 —— 那是壳的加载器，不是业务 dex。",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                )
                selected?.let { p ->
                    Text(
                            "目标：${p.name}（pid ${p.pid}）",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                        onClick = {
                            val p = selected ?: return@Button
                            scope.launch {
                                dumping = true
                                error = null
                                logs = emptyList()
                                result = null
                                try {
                                    val base =
                                            context.getExternalFilesDir("revokit_export")
                                                    ?: File(context.filesDir, "revokit_export")
                                    val dir =
                                            File(
                                                    base,
                                                    "dump_" + p.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                                            ).apply { mkdirs() }
                                    outDir = dir.absolutePath
                                    val r =
                                            DexDumper.dump(context, p.pid, p.name, dir) { line ->
                                                logs = logs + line
                                            }
                                    result = r
                                } catch (t: Throwable) {
                                    error = "dump 失败：${t.message ?: t.javaClass.simpleName}"
                                } finally {
                                    dumping = false
                                }
                            }
                        },
                        enabled = !dumping && selected != null,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    if (dumping) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (dumping) "正在扫内存…" else "开始 dump")
                }
                if (logs.isNotEmpty()) LogBlock(logs)
            }

            result?.let { r ->
                SectionCard(title = "dump 结果 · ${r.dumped.size} 个 dex") {
                    Text(
                            "扫了 ${r.regionsScanned} 段、${formatBytes(r.bytesScanned)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (r.dumped.isEmpty()) {
                        Text(
                                "没捞到 dex。常见原因：目标还没加载业务 dex（先在目标里点几下再 dump）、" +
                                        "或者壳把 dex 加密后不在可读段里。",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.error
                        )
                    }
                    r.dumped.forEach { d ->
                        Column(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(10.dp)
                                                )
                                                .padding(10.dp)
                        ) {
                            Text(
                                    "${d.file.name}  ${formatBytes(d.size)}",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                            )
                            Text(
                                    "v${d.version} · ${d.region} · ${d.kind} · 方法 ${d.methodCount} · " +
                                            "校验和${if (d.checksumOk) "正常" else "不符"} · sha1 ${d.sha1.take(12)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (d.origin.isNotBlank()) {
                                Text(
                                        d.origin,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    outDir?.let {
                        Text(
                                "文件都在\n$it",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        error?.let {
            NoteCard(
                    title = "出错了",
                    body = it,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/* ================================================================== */
/* Flutter                                                            */
/* ================================================================== */

@Composable
private fun FlutterTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apk by remember { mutableStateOf<File?>(null) }
    var result by remember { mutableStateOf<FlutterProbe.Result?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    busy = true
                    error = null
                    result = null
                    try {
                        val f = withContext(Dispatchers.IO) { copyPicked(context, uri, "revokit_flutter") }
                        apk = f
                        result = withContext(Dispatchers.IO) { FlutterProbe.probe(f) }
                    } catch (t: Throwable) {
                        error = "分析失败：${t.message ?: t.javaClass.simpleName}"
                    } finally {
                        busy = false
                    }
                }
            }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "Flutter 结构体检") {
            Text(
                    "Flutter 的业务代码不在 dex 里，全在 libapp.so 的 Dart AOT 快照里，" +
                            "脱壳和 dex 反编译都碰不到它。这里先把结构、版本、ABI 摸清楚。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (busy) "分析中…" else "选择 APK")
            }
            apk?.let {
                Text(
                        "已选：${it.name}  ${formatBytes(it.length())}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }

        error?.let {
            NoteCard(
                    title = "出错了",
                    body = it,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        result?.let { r ->
            SectionCard(title = if (r.isFlutter) "是 Flutter 应用" else "不是 Flutter 应用") {
                KeyValueTable(
                        listOf(
                                "构建模式" to r.mode,
                                "引擎版本" to (r.engineVersion ?: "未探测到"),
                                "Dart 版本" to (r.dartVersion ?: "未探测到"),
                                "ABI" to if (r.abis.isEmpty()) "—" else r.abis.joinToString("、"),
                                "libapp 数量" to "${r.libapp.size}"
                        )
                )
                if (r.evidence.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("依据", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    r.evidence.forEach {
                        Text(
                                "· $it",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (r.notes.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("下一步", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    r.notes.forEach {
                        Text(
                                "· $it",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/* ================================================================== */
/* 重打包                                                              */
/* ================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepackTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apk by remember { mutableStateOf<File?>(null) }
    var dexes by remember { mutableStateOf<List<File>>(emptyList()) }
    var entryClass by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<DexRepair.EntryCandidate>>(emptyList()) }
    var optCleanShell by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ApkRepacker.Result?>(null) }

    val apkPicker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    error = null
                    result = null
                    try {
                        apk = withContext(Dispatchers.IO) { copyPicked(context, uri, "revokit_repack_apk") }
                    } catch (t: Throwable) {
                        error = "读取 APK 失败：${t.message}"
                    }
                }
            }

    val dexPicker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
                scope.launch {
                    error = null
                    result = null
                    try {
                        dexes =
                                withContext(Dispatchers.IO) {
                                    uris.map { copyPicked(context, it, "revokit_repack_dex") }
                                            .sortedBy { it.name }
                                }
                    } catch (t: Throwable) {
                        error = "读取 dex 失败：${t.message}"
                    }
                }
            }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "第一步 · 选原包") {
            Text(
                    "选要改的那个 APK。里面的 classes*.dex 会被全部替换掉，资源、so、清单都不动。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                    onClick = { apkPicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (apk == null) "选择 APK" else "换一个 APK")
            }
            apk?.let {
                Text(
                        "已选：${it.name}  ${formatBytes(it.length())}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }

        SectionCard(title = "第二步 · 选修好的 dex（${dexes.size}）") {
            Text(
                    "可以多选。按文件名排序后依次写成 classes.dex、classes2.dex……" +
                            "所以修过的 dex 数量和顺序要对得上，别少选。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                    onClick = { dexPicker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加 dex")
            }
            dexes.forEachIndexed { i, f ->
                Text(
                        "${if (i == 0) "classes.dex" else "classes${i + 1}.dex"}  ←  ${f.name}  ${formatBytes(f.length())}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                )
            }
            if (dexes.isNotEmpty()) {
                TextButton(onClick = { dexes = emptyList() }, enabled = !busy) { Text("清空 dex 列表") }
            }
        }

        SectionCard(title = "第三步 · 打包并签名") {
            Text(
                    "流程：重写 zip（换 dex、删旧签名）→ zipalign 四字节对齐 → apksig 签名。" +
                            "用的是应用自带的 debug 密钥，包名没改的话能直接覆盖安装。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                    value = entryClass,
                    onValueChange = { entryClass = it },
                    label = { Text("真实 Application 类名（可留空）") },
                    placeholder = { Text("例如 com.example.RealApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
            )
            Text(
                    "回填 dex 之后，Manifest 里的 application 还指着壳的 StubApp，不改的话装上也起不来。" +
                            "填在这里我直接写进 Manifest。拿不准就点下面的「自动推断入口」。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            try {
                                candidates =
                                        withContext(Dispatchers.IO) { ApkRepacker.inferEntries(dexes) }
                                val top = candidates.firstOrNull()
                                if (top != null) {
                                    entryClass = top.className
                                } else {
                                    error = "这批 dex 里没找到继承 android.app.Application 的类"
                                }
                            } catch (t: Throwable) {
                                error = "推断入口失败：${t.message ?: t.javaClass.simpleName}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && dexes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("自动推断入口（继承链叶子节点）")
            }
            if (candidates.isNotEmpty()) {
                Text(
                        "按可信度排序。要的是**继承链的叶子**——选了基类会跳过 attachBaseContext，" +
                                "全局 Context 为 null，启动必崩。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                candidates.take(6).forEach { c ->
                    val picked = c.className == entryClass
                    Card(
                            onClick = { entryClass = c.className },
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    if (picked) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                    c.className,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                            )
                            Text(
                                    "继承链：" + c.chain.joinToString(" → "),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                    "${c.score} 分 · " + c.reasons.joinToString("；"),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = optCleanShell, onCheckedChange = { optCleanShell = it })
                Text("顺手清理壳残留（壳 so / 加密容器 / 运行时目录）", fontSize = 12.sp)
            }
            Button(
                    onClick = {
                        val src = apk ?: return@Button
                        scope.launch {
                            busy = true
                            error = null
                            result = null
                            try {
                                val r =
                                        withContext(Dispatchers.IO) {
                                            ApkRepacker.replaceDexes(
                                                    context,
                                                    src,
                                                    dexes,
                                                    ApkRepacker.outDir(context),
                                                    entryClass,
                                                    optCleanShell
                                            )
                                        }
                                result = r
                            } catch (t: Throwable) {
                                error = "重打包失败：${t.message ?: t.javaClass.simpleName}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && apk != null && dexes.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (busy) "打包中…" else "开始重打包")
            }
        }

        error?.let {
            NoteCard(
                    title = "出错了",
                    body = it,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        result?.let { r ->
            SectionCard(title = if (r.ok) "打包成功" else "打包未完成") {
                Text(
                        if (r.ok) "已产出可直接安装的签名包" else "见下面日志，通常是没有 debug 密钥或签名失败",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                KeyValueTable(
                        listOf(
                                "替换 dex" to "${r.replaced.size} 个",
                                "清除旧签名" to "${r.removedSignatureFiles} 个",
                                "清理壳残留" to "${r.removedShell.size} 项",
                                "修入口" to if (r.manifestPatched) "已改写" else "未改",
                                "新 dex 总量" to formatBytes(r.addedDexBytes)
                        )
                )
                LogBlock(r.log)
                r.apk?.let {
                    Text(
                            "产物：\n${it.absolutePath}\n${formatBytes(it.length())}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/* ================================================================== */
/* 通用小部件                                                          */
/* ================================================================== */

@Composable
private fun SectionCard(title: String, body: @Composable ColumnScope.() -> Unit) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            body()
        }
    }
}

@Composable
private fun NoteCard(title: String, body: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = content)
            Text(body, fontSize = 12.sp, lineHeight = 18.sp, color = content)
        }
    }
}

@Composable
private fun ToggleRow(
        title: String,
        desc: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                    desc,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun KeyValueTable(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (k, v) ->
            Row {
                Text(
                        k,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(108.dp)
                )
                Text(v, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LogBlock(lines: List<String>) {
    if (lines.isEmpty()) return
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        lines.takeLast(40).forEach {
            Text(it, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/* ------------------------------------------------------------------ */

private fun copyPicked(context: Context, uri: Uri, subDir: String): File {
    val dir = File(context.cacheDir, subDir).apply { mkdirs() }
    val name = queryDisplayName(context, uri) ?: "picked_${System.currentTimeMillis()}"
    val safe = name.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
    val out = File(dir, safe)
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
    } ?: throw IllegalStateException("无法打开所选文件")
    return out
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (t: Throwable) {
        uri.lastPathSegment
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format("%.1f KB", kb)
        kb < 1024 * 1024 -> String.format("%.1f MB", kb / 1024)
        else -> String.format("%.2f MB", kb / 1024 / 1024)
    }
}
