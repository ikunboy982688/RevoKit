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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.revokit.ApkProfile
import com.ai.assistance.operit.core.revokit.Evidence
import com.ai.assistance.operit.core.revokit.IdentifyReport
import com.ai.assistance.operit.core.revokit.PackerIdentifier
import com.ai.assistance.operit.core.revokit.PackerMatch
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RevoKit 加固识别页
 *
 * 选一个 APK -> 静态扫描 -> 给出「是不是壳 / 什么壳 / 怎么脱」三段结论。
 * 全程不执行目标 APK，纯读 zip 结构 + dex 字符串。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackerIdentifyScreen(onGoBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scanning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<IdentifyReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }

    val picker =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    scanning = true
                    error = null
                    report = null
                    try {
                        val file = withContext(Dispatchers.IO) { copyToCache(context, uri) }
                        pickedName = file.name
                        val result =
                                withContext(Dispatchers.IO) { PackerIdentifier.identify(context, file) }
                        report = result
                    } catch (t: Throwable) {
                        error = "扫描失败：${t.message ?: t.javaClass.simpleName}"
                    } finally {
                        scanning = false
                    }
                }
            }

    CustomScaffold(
            topBar = {
                TopAppBar(
                        title = { Text("加固识别") },
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
                                    enabled = !scanning
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "选择 APK")
                            }
                        }
                )
            }
    ) { padding ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (scanning) {
                ScanningCard(pickedName)
            }
            error?.let { msg ->
                ResultNoteCard(
                        title = "出错了",
                        body = msg,
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (!scanning && report == null && error == null) {
                IntroCard(
                        onPick = {
                            picker.launch(
                                    arrayOf(
                                            "application/vnd.android.package-archive",
                                            "application/octet-stream",
                                            "*/*"
                                    )
                            )
                        }
                )
            }
            report?.let { r -> ReportContent(r) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------------------------------------------ */
/* 状态卡片                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun IntroCard(onPick: () -> Unit) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("静态加固识别", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                    "选一个 APK，直接解压看 lib 目录的 so 名、assets 特征文件、dex 里的壳类名，" +
                            "判断它有没有被加固、是哪家的壳，并给出对应的脱壳切入点。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
            )
            Text(
                    "全程不上传、不联网、不执行目标 APK，只读文件本身。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
            )
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("选择 APK 开始识别")
            }
        }
    }
}

@Composable
private fun ScanningCard(name: String?) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
            Column {
                Text("正在分析…", fontWeight = FontWeight.SemiBold)
                Text(
                        name ?: "读取文件",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* 结果主体                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun ReportContent(r: IdentifyReport) {
    VerdictCard(r)
    BasicInfoCard(r.profile)
    if (r.stack.isNotEmpty()) StackCard(r)
    r.primary?.let { p ->
        if (p.rule.dumpHints.isNotEmpty()) DumpHintsCard(p)
        EvidenceCard(p)
    }
    val others = r.matches.drop(1)
    if (others.isNotEmpty()) OtherMatchesCard(others)
    StructuralCard(r.structuralNotes)
}

@Composable
private fun VerdictCard(r: IdentifyReport) {
    val p = r.primary
    val container =
            when {
                p == null -> MaterialTheme.colorScheme.surfaceVariant
                p.score >= 70 -> MaterialTheme.colorScheme.primaryContainer
                p.score >= 45 -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
    val onContainer =
            when {
                p == null -> MaterialTheme.colorScheme.onSurfaceVariant
                p.score >= 70 -> MaterialTheme.colorScheme.onPrimaryContainer
                p.score >= 45 -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        imageVector =
                                if (p != null) Icons.Default.Security
                                else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = onContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                        if (p != null) "已识别加固" else "未识别到已知加固特征",
                        fontSize = 12.sp,
                        color = onContainer
                )
            }
            Text(
                    p?.rule?.displayName ?: "疑似未加固（或无特征壳）",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = onContainer
            )
            if (p != null) {
                Text(
                        "${p.rule.vendor} · ${p.rule.category}",
                        fontSize = 13.sp,
                        color = onContainer
                )
                LinearProgressIndicator(
                        progress = { p.confidencePercent / 100f },
                        modifier =
                                Modifier.fillMaxWidth()
                                        .height(6.dp)
                                        .background(
                                                onContainer.copy(alpha = 0.15f),
                                                RoundedCornerShape(3.dp)
                                        ),
                        color = onContainer,
                        trackColor = Color.Transparent
                )
                Text(
                        "置信度 ${p.confidence} · ${p.score}/100 · 耗时 ${r.elapsedMs} ms",
                        fontSize = 11.sp,
                        color = onContainer.copy(alpha = 0.75f)
                )
                if (!p.rule.hasSample) {
                    Text(
                            "该厂商暂无实物样本，只按文件名识别，建议人工复核",
                            fontSize = 11.sp,
                            color = onContainer.copy(alpha = 0.75f),
                            lineHeight = 16.sp
                    )
                }
            } else {
                Text(
                        "没有命中特征库里的任何壳。这可能是：① 真的没加固；" +
                                "② 用的是小众/自研壳；③ 壳做了特征隐藏。看下面的 so 列表人工确认。",
                        fontSize = 12.sp,
                        color = onContainer,
                        lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun BasicInfoCard(p: ApkProfile) {
    SectionCard("文件信息") {
        InfoRow("文件名", p.fileName)
        InfoRow("包名", p.packageName ?: "无法解析")
        InfoRow("应用名", p.appLabel ?: "—")
        InfoRow("版本", "${p.versionName ?: "?"} (${p.versionCode})")
        InfoRow("大小", formatSize(p.fileSize))
        InfoRow("minSdk / targetSdk", "${p.minSdk} / ${p.targetSdk}")
        InfoRow("签名方案", if (p.signatureSchemes.isEmpty()) "无（异常）" else p.signatureSchemes.joinToString(" + "))
        InfoRow("zip 条目数", p.totalEntries.toString())
        InfoRow(
                "dex / so / assets",
                "${p.dexCount} / ${p.soFiles.size} / ${p.assetFiles.size}"
        )
        InfoRow("dex 总大小", formatSize(p.dexBytes))
        MonoRow("MD5", p.md5)
        MonoRow("SHA-256", p.sha256)
        if (p.applicationClass != null) MonoRow("Application", p.applicationClass)
    }
}

@Composable
private fun StackCard(r: IdentifyReport) {
    SectionCard("技术栈") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            r.stack.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Text(
                            s.detail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DumpHintsCard(p: PackerMatch) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
    ) {
        Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.width(8.dp))
                Text(
                        "脱壳切入点（${p.rule.displayName}）",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            p.rule.dumpHints.forEachIndexed { i, hint ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                            "${i + 1}.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                            hint,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceCard(p: PackerMatch) {
    var expanded by remember(p) { mutableStateOf(false) }
    SectionCard("命中证据（${p.evidences.size}）") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            p.evidences.forEach { e: Evidence -> EvidenceRow(e) }
            if (p.evidences.size > 2) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起说明" else "这些证据是什么意思？", fontSize = 12.sp)
                }
                if (expanded) {
                    Text(
                            "· native 库：lib/<abi>/ 下的 so 文件名是壳最稳定的指纹，" +
                                    "壳必须靠自己的 so 完成解密，这个文件删不掉。\n" +
                                    "· assets 资源：壳把加密后的原始 dex/so 藏在这里，离线解需要它。\n" +
                                    "· DEX 字符串：壳的 Application/入口类会作为字符串留在 dex 里，" +
                                    "但高级壳会改名或加密，所以只能作为辅证。\n" +
                                    "· Manifest 特征：application android:name 指向壳的类，最直接但最容易被改。",
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceRow(e: Evidence) {
    Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
                modifier =
                        Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                    e.kind.label,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
            )
        }
        Text(
                e.detail,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
                fontFamily = FontFamily.Monospace
        )
        Text("+${e.score}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OtherMatchesCard(others: List<PackerMatch>) {
    SectionCard("其他可能（分数较低）") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            others.forEach { m ->
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.rule.displayName, fontSize = 13.sp)
                    Text(
                            "${m.confidence} · ${m.score}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                        m.evidences.joinToString("；") { it.detail },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                )
                if (m != others.last()) Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun StructuralCard(notes: List<String>) {
    SectionCard("结构提示") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("·", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(
                            n,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* 小组件                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(110.dp)
        )
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f), lineHeight = 18.sp)
    }
}

@Composable
private fun MonoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
                value,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 15.sp
        )
    }
}

@Composable
private fun ResultNoteCard(title: String, body: String, container: Color, content: Color) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = content)
            Text(body, fontSize = 12.sp, color = content, lineHeight = 18.sp)
        }
    }
}

/* ------------------------------------------------------------------ */
/* 文件处理                                                            */
/* ------------------------------------------------------------------ */

private fun copyToCache(context: Context, uri: Uri): File {
    val dir = File(context.cacheDir, "revokit_scan").apply { mkdirs() }
    val name = queryDisplayName(context, uri) ?: "picked_${System.currentTimeMillis()}.apk"
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

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format("%.1f KB", kb)
        kb < 1024 * 1024 -> String.format("%.1f MB", kb / 1024)
        else -> String.format("%.2f GB", kb / 1024 / 1024)
    }
}
