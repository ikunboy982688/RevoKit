package com.ai.assistance.operit.ui.features.revokit

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 逆向工作台（首页）
 *
 * 只读骨架：列本机 apk/dex、给常用工具快捷入口、记最近打开的文件。
 * 本轮不碰任何写盘、不自动跑脱壳，点条目只做「复制路径 + 记入最近」，
 * 目的是先把导航入口跑通、验证不崩，功能后续增量加。
 */
private const val SCAN_MAX_DEPTH = 2
private const val SCAN_MAX_ITEMS = 60
private const val RECENT_PREFS = "revokit_workbench_recent"
private const val RECENT_KEY = "items"
private const val RECENT_MAX = 5

private data class WorkspaceFile(
        val path: String,
        val name: String,
        val sizeBytes: Long,
        val lastModified: Long,
        val kind: String
)

private data class RecentFile(val path: String, val name: String, val openedAt: Long)

private fun scanWorkspaceFiles(): List<WorkspaceFile> {
    val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull() ?: return emptyList()
    val starts = listOf(File(root, "Download"), root)
    val collected = LinkedHashMap<String, WorkspaceFile>()
    starts.forEach { start ->
        if (start.isDirectory) {
            runCatching { walkForTargets(start, 0, collected) }
        }
    }
    return collected.values.sortedByDescending { it.lastModified }.take(SCAN_MAX_ITEMS)
}

private fun walkForTargets(dir: File, depth: Int, out: MutableMap<String, WorkspaceFile>) {
    if (depth > SCAN_MAX_DEPTH) return
    val children = dir.listFiles() ?: return
    children.forEach { child ->
        val name = child.name
        if (name.startsWith(".")) return@forEach
        if (child.isDirectory) {
            // 这几个目录体量大且几乎不会有目标文件，跳过以控制扫描耗时
            if (name == "Android" || name == "DCIM" || name == "Movies") return@forEach
            walkForTargets(child, depth + 1, out)
        } else {
            val lower = name.lowercase(Locale.ROOT)
            val kind =
                    when {
                        lower.endsWith(".apk") -> "apk"
                        lower.endsWith(".dex") -> "dex"
                        else -> return@forEach
                    }
            val length = child.length()
            if (length <= 0L) return@forEach
            out[child.absolutePath] =
                    WorkspaceFile(
                            path = child.absolutePath,
                            name = name,
                            sizeBytes = length,
                            lastModified = child.lastModified(),
                            kind = kind
                    )
        }
    }
}

private fun loadRecent(context: Context): List<RecentFile> {
    val raw =
            context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
                    .getString(RECENT_KEY, null)
                    ?: return emptyList()
    return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val path = item.optString("path").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    RecentFile(
                            path = path,
                            name = item.optString("name").ifEmpty { File(path).name },
                            openedAt = item.optLong("openedAt")
                    )
                }
            }
            .getOrDefault(emptyList())
}

private fun persistRecent(context: Context, list: List<RecentFile>) {
    val array = JSONArray()
    list.forEach { item ->
        array.put(
                JSONObject().apply {
                    put("path", item.path)
                    put("name", item.name)
                    put("openedAt", item.openedAt)
                }
        )
    }
    runCatching {
        context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(RECENT_KEY, array.toString())
                .apply()
    }
}

private fun pushRecent(context: Context, file: WorkspaceFile) {
    val current = loadRecent(context)
    val next =
            buildList {
                        add(RecentFile(path = file.path, name = file.name, openedAt = System.currentTimeMillis()))
                        addAll(current.filterNot { it.path == file.path })
                    }
                    .take(RECENT_MAX)
    persistRecent(context, next)
}

private fun dropRecent(context: Context, path: String) {
    persistRecent(context, loadRecent(context).filterNot { it.path == path })
}

private fun formatSize(bytes: Long): String =
        when {
            bytes >= 1024L * 1024L * 1024L ->
                    String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L ->
                    String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }

private fun formatTime(epochMillis: Long): String =
        if (epochMillis <= 0L) "—"
        else SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date(epochMillis))

private fun storageRootLabel(path: String): String {
    val root = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrDefault("")
    val stripped = if (root.isNotEmpty() && path.startsWith(root)) path.removePrefix(root) else path
    return stripped.trimStart('/').ifEmpty { path }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen(
        onOpenPackerIdentify: () -> Unit,
        onOpenApkBrowser: () -> Unit,
        onOpenShellWorkbench: () -> Unit,
        onOpenCleanup: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var scanning by remember { mutableStateOf(true) }
    var files by remember { mutableStateOf<List<WorkspaceFile>>(emptyList()) }
    var recent by remember { mutableStateOf<List<RecentFile>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        scanning = true
        recent = withContext(Dispatchers.IO) { loadRecent(context) }
        files = withContext(Dispatchers.IO) { scanWorkspaceFiles() }
        scanning = false
    }

    val apkCount = files.count { it.kind == "apk" }
    val dexCount = files.count { it.kind == "dex" }

    val copyPath: (String) -> Unit = { path ->
        clipboard.setText(AnnotatedString(path))
        scope.launch { snackbarHostState.showSnackbar("已复制路径") }
    }

    CustomScaffold(
            topBar = {
                TopAppBar(
                        title = { Text("逆向工作台") },
                        actions = {
                            IconButton(
                                    onClick = { refreshTick += 1 },
                                    enabled = !scanning
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "重新扫描")
                            }
                        }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 6.dp,
                        bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { OverviewCard(apkCount = apkCount, dexCount = dexCount, scanning = scanning) }

            item { SectionTitle("常用工具") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Security,
                            title = "加壳识别",
                            desc = "本地查壳，不联网",
                            onClick = onOpenPackerIdentify
                    )
                    ToolCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Inventory2,
                            title = "APK 拆包",
                            desc = "列 dex / so / assets",
                            onClick = onOpenApkBrowser
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Build,
                            title = "脱壳工作台",
                            desc = "修头、dump、回填",
                            onClick = onOpenShellWorkbench
                    )
                    ToolCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.DeleteSweep,
                            title = "清理",
                            desc = "扫残留解包目录",
                            onClick = onOpenCleanup
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("最近工程", Modifier.weight(1f))
                    if (recent.isNotEmpty()) {
                        TextButton(
                                onClick = {
                                    persistRecent(context, emptyList())
                                    recent = emptyList()
                                }
                        ) { Text("清空", fontSize = 12.sp) }
                    }
                }
            }
            if (recent.isEmpty()) {
                item { HintRow(Icons.Default.History, "还没有记录。点下面列表里的文件就会自动记一条。") }
            } else {
                items(recent, key = { "recent_" + it.path }) { entry ->
                    FileRow(
                            icon = Icons.Default.History,
                            title = entry.name,
                            subtitle = storageRootLabel(entry.path),
                            trailing = formatTime(entry.openedAt),
                            onClick = { copyPath(entry.path) },
                            onLongClick = {
                                dropRecent(context, entry.path)
                                recent = loadRecent(context)
                            }
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("本机安装包 / dex", Modifier.weight(1f))
                    if (scanning) {
                        Text("扫描中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!scanning && files.isEmpty()) {
                item {
                    HintRow(
                            Icons.Default.FolderOpen,
                            "没扫到 apk/dex。确认已授予「所有文件访问权限」，把包放进 Download 后再刷新。"
                    )
                }
            } else {
                items(files, key = { "file_" + it.path }) { file ->
                    FileRow(
                            icon = if (file.kind == "apk") Icons.Default.Inventory2 else Icons.Default.Build,
                            title = file.name,
                            subtitle = storageRootLabel(file.path),
                            trailing = formatSize(file.sizeBytes),
                            onClick = {
                                pushRecent(context, file)
                                recent = loadRecent(context)
                                copyPath(file.path)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(apkCount: Int, dexCount: Int, scanning: Boolean) {
    Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
    ) {
        Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                    "逆向工作台",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                    if (scanning) "正在扫描本机文件…" else "本机找到 $apkCount 个安装包 · $dexCount 个 dex",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
            text = text,
            modifier = modifier.padding(top = 6.dp, bottom = 2.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ToolCard(
        modifier: Modifier = Modifier,
        icon: ImageVector,
        title: String,
        desc: String,
        onClick: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
            )
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                    desc,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileRow(
        icon: ImageVector,
        title: String,
        subtitle: String,
        trailing: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier =
                    Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Text(
                        subtitle,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
            if (onLongClick != null) {
                IconButton(onClick = onLongClick, modifier = Modifier.size(30.dp)) {
                    Icon(
                            Icons.Default.Delete,
                            contentDescription = "移除记录",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                    trailing,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HintRow(icon: ImageVector, text: String) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    text,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
