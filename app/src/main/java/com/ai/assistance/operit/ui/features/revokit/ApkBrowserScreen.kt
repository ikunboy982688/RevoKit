package com.ai.assistance.operit.ui.features.revokit

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.revokit.ApkBrowser
import com.ai.assistance.operit.core.revokit.ApkEntry
import com.ai.assistance.operit.core.revokit.ApkEntryGroup
import com.ai.assistance.operit.core.revokit.IdentifyReport
import com.ai.assistance.operit.core.revokit.PackerIdentifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 该文件自带的 copyToCache 名字跟识别页不同，避免冲突 */
private fun copyToCacheDir(context: Context, uri: Uri): File {
    val dir = File(context.cacheDir, "revokit_pkg").apply { mkdirs() }
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "picked.apk"
    val safe = name.replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
    val out = File(dir, safe)
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
    } ?: throw IllegalStateException("无法打开所选文件")
    return out
}

/**
 * 加固徽章
 *
 * 选中 APK 就地出结论：不问 AI、不联网、不执行目标包。
 * 原理是「查壳不等于脱壳」——读 ZIP 中央目录拿到条目名，再跟内置 49 家特征库打分匹配。
 * 壳的 loader（入口类 + 原生库）必须是明文，否则没人能解密它，所以这部分永远可读。
 *
 * 注意：注释里不要出现 lib 目录通配写法，Kotlin 块注释可以嵌套，会提前闭合（已踩 6 次）。
 */
@Composable
private fun PackerBadge(report: IdentifyReport?, identifying: Boolean, fileName: String?) {
    if (fileName == null) return
    val primary = report?.primary
    val scheme =
            report?.profile?.signatureSchemes?.takeIf { it.isNotEmpty() }?.joinToString(" + ")
                    ?: if (report != null) "无签名信息" else null

    Surface(
            color =
                    when {
                        identifying || report == null -> MaterialTheme.colorScheme.surfaceVariant
                        primary != null -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                        text =
                                when {
                                    identifying -> "正在识别加固…"
                                    report == null -> "加固识别不可用"
                                    primary != null -> "加固：${primary.rule.displayName}"
                                    else -> "未识别到已知加固"
                                },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                )
                Spacer(Modifier.weight(1f))
                if (report != null && !identifying) {
                    Text(
                            "${report.elapsedMs} ms",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (report != null && !identifying) {
                if (primary != null) {
                    Text(
                            "${primary.rule.vendor} · ${primary.rule.category} · " +
                                    "${primary.confidence} ${primary.confidencePercent}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val hint = primary.rule.dumpHints.firstOrNull()
                    if (!hint.isNullOrBlank()) {
                        Text("建议：$hint", fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    if (report.matches.size > 1) {
                        val others =
                                report.matches.drop(1).take(3).joinToString("、") {
                                    it.rule.displayName
                                }
                        Text(
                                "另命中 $others —— 可能是套壳或内置安全 SDK",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                            "自研壳、小众壳，或改掉了默认类名/so 名的，规则库对不上就显示" +
                                    "「未识别」—— 这不等于没加固。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                    )
                    Text(
                            "dex ${report.profile.dexCount} 个 / " +
                                    "${readableSize(report.profile.dexBytes)} · " +
                                    "so ${report.profile.soFiles.size} 个 · " +
                                    "签名 ${scheme ?: "未知"}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (report.profile.dexCount > 0 && report.profile.dexBytes < 900_000L) {
                    Text(
                            "dex 总量偏小，业务代码很可能不在 dex 里（被抽取或放到别处）",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}


/**
 * APK 拆包浏览：列出 dex / so / assets，并能把需要的东西直接抽到磁盘。
 * 打开即顺手跑一次原生加固识别，结果展示在顶部徽章里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkBrowserScreen(onGoBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var loading by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var apkFile by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<ApkEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var activeGroup by remember { mutableStateOf<ApkEntryGroup?>(null) }

    // 原生加固识别：跟 MT 管理器一样，选中 APK 就地秒出结论，不经过 AI、不联网、不执行目标包
    var identifyReport by remember { mutableStateOf<IdentifyReport?>(null) }
    var identifying by remember { mutableStateOf(false) }

    val picker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    loading = true
                    identifyReport = null
                    try {
                        val picked: File = withContext(Dispatchers.IO) { copyToCacheDir(context, uri) }
                        val list = withContext(Dispatchers.IO) { ApkBrowser.list(picked) }
                        apkFile = picked
                        entries = list
                        query = ""
                        activeGroup = null
                    } catch (t: Throwable) {
                        snackbarHostState.showSnackbar("读取失败：${t.message ?: "未知错误"}")
                    } finally {
                        loading = false
                    }
                    // 列表先出来，识别紧接着跑（纯本地读 zip 目录 + dex 字符串，通常几十毫秒）
                    val target: File = apkFile ?: return@launch
                    identifying = true
                    try {
                        identifyReport =
                                withContext(Dispatchers.IO) {
                                    PackerIdentifier.identify(context, target)
                                }
                    } catch (t: Throwable) {
                        identifyReport = null
                    } finally {
                        identifying = false
                    }
                }
            }

    val visible =
            remember(entries, query, activeGroup) {
                entries.filter { e ->
                    (activeGroup == null || e.group == activeGroup) &&
                            (query.isBlank() || e.name.contains(query, ignoreCase = true))
                }
            }

    val groupsInUse = remember(entries) { entries.groupBy { it.group }.filterValues { it.isNotEmpty() } }

    CustomScaffold(
            topBar = {
                TopAppBar(
                        title = { Text("APK 拆包") },
                        navigationIcon = {
                            IconButton(onClick = onGoBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            IconButton(
                                    onClick = {
                                        picker.launch(
                                                arrayOf(
                                                        "application/vnd.android.package-archive",
                                                        "application/octet-stream",
                                                        "*/*"
                                                )
                                        )
                                    },
                                    enabled = !loading
                            ) { Icon(Icons.Default.FolderOpen, contentDescription = "选择 APK") }
                        }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (apkFile == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Text("拆开看里面有什么", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                                "列出 APK 里的全部文件，按 dex / so / assets 分组，" +
                                        "可以把 dex 和 so 一键抽到磁盘再拿去分析。",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 19.sp
                        )
                        Button(
                                onClick = {
                                    picker.launch(
                                            arrayOf(
                                                    "application/vnd.android.package-archive",
                                                    "application/octet-stream",
                                                    "*/*"
                                            )
                                    )
                                }
                        ) { Text("选择 APK") }
                    }
                }
            } else {
                PackerBadge(
                        report = identifyReport,
                        identifying = identifying,
                        fileName = apkFile?.name
                )

                // 搜索框
                OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        placeholder = { Text("搜索文件名，例如 classes / libjiagu", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "清空")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )

                // 分组筛选
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .horizontalScrollCompat()
                                        .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                            selected = activeGroup == null,
                            onClick = { activeGroup = null },
                            label = { Text("全部 ${entries.size}", fontSize = 12.sp) }
                    )
                    groupsInUse.forEach { (g, list) ->
                        FilterChip(
                                selected = activeGroup == g,
                                onClick = { activeGroup = if (activeGroup == g) null else g },
                                label = { Text("${g.label} ${list.size}", fontSize = 12.sp) }
                        )
                    }
                }

                // 导出条
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                            onClick = {
                                val file = apkFile ?: return@Button
                                scope.launch {
                                    exporting = true
                                    try {
                                        val n =
                                                withContext(Dispatchers.IO) {
                                                    val dir = ApkBrowser.exportDir(context, file)
                                                    ApkBrowser.extract(
                                                            file,
                                                            visible.map { it.name },
                                                            dir
                                                    )
                                                }
                                        val dir = ApkBrowser.exportDir(context, file)
                                        snackbarHostState.showSnackbar(
                                                "已导出 $n 个文件到\n${dir.absolutePath}"
                                        )
                                    } catch (t: Throwable) {
                                        snackbarHostState.showSnackbar("导出失败：${t.message}")
                                    } finally {
                                        exporting = false
                                    }
                                }
                            },
                            enabled = !exporting && visible.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (exporting) "导出中…" else "导出当前列表（${visible.size}）", fontSize = 13.sp)
                    }
                }

                Text(
                        "共 ${entries.size} 条 · 当前显示 ${visible.size} 条",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visible, key = { it.name }) { e -> EntryRow(e) }
                }
            }
        }
    }
}

/** 单个 zip 条目：图标 + 名字 + 组别/大小，命中加固特征的行高亮 */
@Composable
private fun EntryRow(e: ApkEntry) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
                imageVector =
                        when (e.group) {
                            ApkEntryGroup.DEX -> Icons.Default.DataObject
                            ApkEntryGroup.NATIVE -> Icons.Default.Memory
                            ApkEntryGroup.ASSETS -> Icons.Default.Inventory2
                            ApkEntryGroup.CONFIG -> Icons.Default.Tune
                            ApkEntryGroup.META -> Icons.Default.VerifiedUser
                            else -> Icons.Default.InsertDriveFile
                        },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint =
                        if (e.interesting) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    e.name,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (e.interesting) FontWeight.Medium else FontWeight.Normal
            )
            Text(
                    "${e.group.label} · ${readableSize(e.size)}" +
                            if (e.compressedSize > 0 && e.compressedSize < e.size)
                                    " · 压缩后 ${readableSize(e.compressedSize)}"
                            else "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun readableSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format("%.1f KB", kb)
        kb < 1024 * 1024 -> String.format("%.1f MB", kb / 1024)
        else -> String.format("%.2f GB", kb / 1024 / 1024)
    }
}


/** Compose 的 horizontalScroll 需要 state，包一层省得每处传 */
@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
        this.horizontalScroll(rememberScrollState())
