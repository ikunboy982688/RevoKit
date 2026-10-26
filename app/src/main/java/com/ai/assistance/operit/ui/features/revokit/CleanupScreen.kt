package com.ai.assistance.operit.ui.features.revokit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.revokit.DiskCleaner
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 工具箱 - 一键清理。
 *
 * 干的事跟对话框里让 AI 调 revokit_ws:ws_scan / ws_purge 一样，只是不用经过 AI：
 *  1. 点「开始扫描」→ 列出所有逆向垃圾（unpack_out / pack360 / app_dex / 自己建的临时目录…）
 *  2. 勾选 → 点「清理」→ 二次确认 → 真删
 *
 * 默认只勾「可删」那一档；「先取出APK再删」的目录里有成品包，必须用户自己先拷出去，
 * 所以那档默认不勾、也不许一键全选。
 *
 * 页面刻意不显示「已释放多少 GB」这种漂亮数字以外的花活：这里是删文件的页面，
 * 信息越少越不容易点错。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(onGoBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var scanning by remember { mutableStateOf(false) }
    var purging by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<DiskCleaner.Item>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allowOutside by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var scannedOnce by remember { mutableStateOf(false) }

    fun doScan() {
        if (scanning) return
        scanning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DiskCleaner.scan() }.getOrElse { emptyList() }
            }
            items = result
            // 默认勾上「可删」，「先取出APK再删」和「保留」都不勾。
            selected = result.filter { it.advice == DiskCleaner.ADVICE_DELETE }
                .map { it.path }
                .toSet()
            scanning = false
            scannedOnce = true
        }
    }

    fun doPurge() {
        if (purging) return
        val targets = items.filter { selected.contains(it.path) }.map { it.path }
        if (targets.isEmpty()) return
        purging = true
        confirmOpen = false
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { DiskCleaner.purge(targets, allowOutside) }.getOrNull()
            }
            purging = false
            val msg = when {
                outcome == null -> "清理失败：读不到文件系统"
                outcome.failed.isNotEmpty() ->
                    "删了 ${outcome.deleted.size} 项，释放 ${fmtSize(outcome.releasedBytes)}；" +
                        "另有 ${outcome.failed.size} 项没删掉（权限不足或被占用）"
                outcome.deleted.isEmpty() && outcome.refused.isNotEmpty() ->
                    "一项都没删：${outcome.refused.first().reason}"
                else -> "清理完成：${outcome.deleted.size} 项，释放 ${fmtSize(outcome.releasedBytes)}"
            }
            snackbarHostState.showSnackbar(msg)
            scannedOnce = false
            items = emptyList()
            selected = emptySet()
            doScan()
        }
    }

    LaunchedEffect(Unit) { if (!scannedOnce && items.isEmpty() && !scanning) doScan() }

    val deletable = items.filter { it.advice == DiskCleaner.ADVICE_DELETE }
    val takeFirst = items.filter { it.advice == DiskCleaner.ADVICE_TAKE_FIRST }
    val keep = items.filter { it.advice == DiskCleaner.ADVICE_KEEP }
    val selectedItems = items.filter { selected.contains(it.path) }
    val selectedBytes = selectedItems.sumOf { it.bytes }

    CustomScaffold(
        topBar = {
            TopAppBar(
                title = { Text("一键清理") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { doScan() }, enabled = !scanning && !purging) {
                        Icon(Icons.Default.Refresh, contentDescription = "重新扫描")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (scanning || purging) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = if (purging) "正在删除，别退出…" else "正在扫描，文件多的目录要等几秒…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            SummaryCard(
                itemCount = items.size,
                deletableCount = deletable.size,
                deletableBytes = deletable.sumOf { it.bytes },
                takeFirstCount = takeFirst.size,
                takeFirstBytes = takeFirst.sumOf { it.bytes },
                keepCount = keep.size,
                keepBytes = keep.sumOf { it.bytes }
            )

            AllowOutsideRow(
                checked = allowOutside,
                onChange = { allowOutside = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        selected = items.filter { it.deletable }.map { it.path }.toSet()
                    },
                    enabled = items.isNotEmpty() && !purging
                ) { Text("勾上所有能删的") }
                TextButton(
                    onClick = { selected = emptySet() },
                    enabled = selected.isNotEmpty() && !purging
                ) { Text("全不选") }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${selectedItems.size} 项 / ${fmtSize(selectedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            if (items.isEmpty() && !scanning) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (scannedOnce) "没扫到逆向垃圾，挺干净的" else "点右上角刷新开始扫描",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items, key = { it.path }) { item ->
                        CleanupRow(
                            item = item,
                            checked = selected.contains(item.path),
                            enabled = !purging,
                            onToggle = {
                                selected = if (selected.contains(item.path)) {
                                    selected - item.path
                                } else {
                                    selected + item.path
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }

            if (items.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "清理勾选的 ${selectedItems.size} 项",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "预计释放 ${fmtSize(selectedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { confirmOpen = true },
                            enabled = selectedItems.isNotEmpty() && !purging && !scanning
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("清理")
                        }
                    }
                }
            }
        }
    }

    if (confirmOpen) {
        val targets = items.filter { selected.contains(it.path) }
        val containsTakeFirst = targets.any { it.advice == DiskCleaner.ADVICE_TAKE_FIRST }
        val containsOutside = targets.any { it.outsideWhitelist }
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("确认删除 ${targets.size} 项？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("预计释放 ${fmtSize(targets.sumOf { it.bytes })}。删掉就找不回来了。")
                    if (containsTakeFirst) {
                        Text(
                            text = "⚠ 勾选里包含「先取出APK再删」的目录，那里面有成品安装包。" +
                                "删完想再要就得重新打包。",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (containsOutside) {
                        Text(
                            text = "⚠ 勾选里包含白名单外的路径。工具会按内容再核一遍，但对不上就照样删。",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = "相册、文档、Android、MT2、回收站、对话工作区不在清理范围，就算误勾了也会被拒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { doPurge() }) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("再想想") } }
        )
    }
}

@Composable
private fun SummaryCard(
    itemCount: Int,
    deletableCount: Int,
    deletableBytes: Long,
    takeFirstCount: Int,
    takeFirstBytes: Long,
    keepCount: Int,
    keepBytes: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (itemCount == 0) "还没扫描" else "共 $itemCount 项",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (itemCount > 0) {
                StatRow("可直接删", "$deletableCount 项", fmtSize(deletableBytes))
                StatRow("要先取出APK再删", "$takeFirstCount 项", fmtSize(takeFirstBytes))
                StatRow("建议保留", "$keepCount 项", fmtSize(keepBytes))
            } else {
                Text(
                    text = "只认逆向产物：解包现场、脱壳 dex、壳的 so、成品包。认不出的东西一律不碰。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, count: String, size: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            text = count,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = size,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AllowOutsideRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("允许删除白名单外的路径", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "AI 自己建的临时目录（/sdcard/adx 这种）不在白名单里。打开后按内容判断：" +
                    "有成品 APK 的不删、只有一个 dex 的不删。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CleanupRow(
    item: DiskCleaner.Item,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val tone = when (item.advice) {
        DiskCleaner.ADVICE_DELETE -> MaterialTheme.colorScheme.primary
        DiskCleaner.ADVICE_TAKE_FIRST -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(checked = checked, onCheckedChange = { if (enabled) onToggle() }, enabled = enabled)
        Column(modifier = Modifier.weight(1f).padding(top = 10.dp, bottom = 10.dp)) {
            Text(
                text = item.path.substringAfter(DiskCleaner.storageRoot, item.path),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.kind,
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = " · ${fmtSize(item.bytes)} · ${item.fileCount} 个文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = item.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.advice == DiskCleaner.ADVICE_TAKE_FIRST && item.reason.isNotEmpty()) {
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (item.outsideWhitelist) {
                Text(
                    text = "白名单外",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** 人类看得懂的大小：不到 1MB 报 KB，不到 1GB 报 MB，再大报 GB。 */
internal fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
