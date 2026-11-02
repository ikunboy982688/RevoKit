package com.ai.assistance.operit.ui.features.settings.screens.theme

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.model.ThemePack
import com.ai.assistance.operit.data.model.ThemePackPresets
import com.ai.assistance.operit.data.theme.QqThemePackImporter
import com.ai.assistance.operit.data.theme.ThemePackArchive
import com.ai.assistance.operit.data.theme.ThemePackLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主题包区：一排可一键套用的预设 + 已导入的主题包 + 导出/导入。
 *
 * 套用走的是编辑器会话（[ThemeEditorSession.update]），所以会进入「未保存」状态，
 * 由主题页原有的保存机制统一落盘，不改动任何存储路径。
 *
 * RevoKit 改动：
 * 1. 内置纯色预设只剩「跟随壁纸」，其余卡片位留给用户导入的主题包（[ThemePackLibrary]）；
 * 2. 原来的「纸张质感」开关已移除，位置换成「一键导出当前主题配置」；
 * 3. 导入/套用后会同时写进「默认角色」等所有作用域，做到导入一次全软件生效。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ThemePackSection(shared: ThemeSettingsShared) {
    val context = shared.context
    val session = shared.editorSession
    val values by session.values.collectAsState()

    val currentPrimary = values.int("custom_primary_color")
    val useCustom = values.boolean("use_custom_colors") ?: false

    val toast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // ---- 已导入的主题包库 ----
    var library by remember { mutableStateOf(emptyList<ThemePackLibrary.SavedPack>()) }
    var libraryVersion by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<ThemePackLibrary.SavedPack?>(null) }
    LaunchedEffect(libraryVersion) {
        library = withContext(Dispatchers.IO) { ThemePackLibrary.list(context) }
    }

    /** 套用一份主题：先改当前草稿，再同步到其它所有作用域，最后统一落盘。 */
    val applyPack: (ThemePack, String) -> Unit = { pack, label ->
        val merged = pack.applyTo(session.currentValues)
        session.update { merged }
        shared.scope.launch {
            withContext(Dispatchers.IO) { shared.applyGlobally(merged) }
            shared.saveInvoker.invoke()
        }
        toast("已套用：$label")
    }

    val exportLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                val snapshot = session.currentValues
                shared.scope.launch {
                    val ok =
                            withContext(Dispatchers.IO) {
                                runCatching {
                                            val pack =
                                                    ThemePack.fromValues(
                                                            id = "custom",
                                                            name = "我的主题",
                                                            values = snapshot
                                                    )
                                            val text = pack.toJson()
                                            context.contentResolver
                                                    .openOutputStream(uri)
                                                    ?.use { out ->
                                                        out.write(text.toByteArray(Charsets.UTF_8))
                                                        out.flush()
                                                    } ?: error("无法写入")
                                        }
                                        .isSuccess
                            }
                    toast(if (ok) "主题已导出" else "导出失败：文件不可写")
                }
            }

    // 一键导出：把当前配置 + 用到的图片打成一个 zip，能直接再导入回来
    val exportZipLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip")
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                val snapshot = session.currentValues
                shared.scope.launch {
                    val ok =
                            withContext(Dispatchers.IO) {
                                runCatching {
                                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                                ThemePackArchive.export(
                                                        context = context,
                                                        values = snapshot,
                                                        name = "我的主题",
                                                        output = out,
                                                )
                                            } ?: false
                                        }
                                        .getOrDefault(false)
                            }
                    toast(if (ok) "已导出主题包（含图片）" else "导出失败：文件不可写")
                }
            }

    val importLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                shared.scope.launch {
                    val parsed =
                            withContext(Dispatchers.IO) {
                                runCatching {
                                            val text =
                                                    context.contentResolver
                                                            .openInputStream(uri)
                                                            ?.bufferedReader(Charsets.UTF_8)
                                                            ?.use { it.readText() }
                                                            ?: error("读不到内容")
                                            ThemePack.fromJson(text)
                                        }
                                        .getOrNull()
                            }
                    if (parsed == null) {
                        toast("导入失败：不是有效的主题文件")
                    } else {
                        applyPack(parsed, parsed.name)
                    }
                }
            }

    // 从主题包 zip 导入：包里有 theme.json 就按配置来，没有则按原生素材文件名兜底
    val qqImportLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                shared.scope.launch {
                    val result =
                            withContext(Dispatchers.IO) {
                                runCatching { QqThemePackImporter.import(context, uri) }
                                        .getOrNull()
                            }
                    if (result == null) {
                        toast("没认出这个包：需要有 theme.json，或包含聊天背景/气泡图")
                        return@launch
                    }
                    // 先存一份到主题包库，再套用——库里的图是副本，放弃草稿也不会被清掉
                    val saved =
                            withContext(Dispatchers.IO) {
                                ThemePackLibrary.save(context, result.pack, result.matched)
                            }
                    if (saved != null) libraryVersion += 1
                    applyPack(saved?.pack ?: result.pack, result.pack.name.ifBlank { "导入的主题" })
                    val names = result.matched.map(::labelOfStem).distinct().joinToString("、")
                    if (names.isNotBlank()) toast("已导入：$names")
                }
            }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                    text = "主题包",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                    onClick = { exportLauncher.launch("我的主题.json") },
                    contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp
                            )
            ) {
                Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("导出配置", fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 4.dp
                            )
            ) {
                Icon(
                        Icons.Default.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("导入配置", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 内置只剩「跟随壁纸」，其余位置都是用户导入的包
            items(ThemePackPresets.all, key = { it.id }) { pack ->
                ThemePackCard(
                        name = pack.name,
                        badge =
                                if (pack.followWallpaper) "壁纸"
                                else if (pack.dark) "暗"
                                else "亮",
                        primary = pack.primaryColor,
                        secondary = pack.secondaryColor,
                        coverUri = null,
                        followWallpaper = pack.followWallpaper,
                        selected =
                                if (pack.followWallpaper) !useCustom
                                else useCustom && currentPrimary == pack.primaryColor,
                        onClick = { applyPack(pack, pack.name) },
                )
            }
            items(library, key = { "lib_${it.id}" }) { saved ->
                ThemePackCard(
                        name = saved.pack.name,
                        badge = "已导入",
                        primary = saved.pack.primaryColor,
                        secondary = saved.pack.secondaryColor,
                        coverUri = saved.coverUri,
                        followWallpaper = false,
                        selected = useCustom && currentPrimary == saved.pack.primaryColor,
                        onClick = { applyPack(saved.pack, saved.pack.name) },
                        onLongClick = { pendingDelete = saved },
                )
            }
            // 末尾留一个空位，点它直接进导入
            item(key = "empty_slot") {
                ThemePackEmptySlot(
                        onClick = {
                            qqImportLauncher.launch(
                                    arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream",
                                            "*/*"
                                    )
                            )
                        }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
                text = "点一下立即生效；长按已导入的主题包可以删除。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
                onClick = {
                    qqImportLauncher.launch(
                            arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                    "*/*"
                            )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 6.dp
                        )
        ) {
            Icon(
                    Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("导入主题包（zip）", fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
                text = "包里有 theme.json 就按配置走（名字、配色、图片都在清单里）；没有则按素材文件名自动认图和配色。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 玻璃质感总开关：一次控制侧栏水玻璃 + 按钮液态玻璃 + 背景模糊
        val glassOn = values.boolean("navigation_drawer_water_glass") == true
        Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = "玻璃质感",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = "侧栏水玻璃 + 按钮磨砂玻璃 + 背景柔化",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                    checked = glassOn,
                    onCheckedChange = { on ->
                        session.update { base ->
                            base.withBoolean("navigation_drawer_water_glass", on)
                                    .withBoolean("navigation_drawer_button_liquid_glass", on)
                                    .withBoolean("use_background_blur", on)
                        }
                        shared.saveInvoker.invoke()
                    }
            )
        }

        // 原来这里是「纸张质感」开关，已移除；位置改成「一键导出当前主题配置」
        OutlinedButton(
                onClick = { exportZipLauncher.launch("我的主题.zip") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp,
                                vertical = 6.dp
                        )
        ) {
            Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("一键导出当前主题配置", fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(
                text = "导出成 zip（theme.json + 图片），可以直接再导入回来，也能发给别人用。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
    }

    pendingDelete?.let { target ->
        AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除主题包") },
                text = {
                    Text(
                            "删除「${target.pack.name}」？如果它正在使用中，背景图会失效，" +
                                    "随后重新导入一次即可。"
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                val id = target.id
                                pendingDelete = null
                                shared.scope.launch {
                                    withContext(Dispatchers.IO) { ThemePackLibrary.delete(context, id) }
                                    libraryVersion += 1
                                    toast("已删除")
                                }
                            }
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                },
        )
    }
}

/** 导入成功后给用户看的中文名，避免甩一堆内部资源名。 */
private fun labelOfStem(stem: String): String =
        when (stem) {
            "skin_chat_background" -> "聊天背景"
            "skin_background_theme_version2" -> "主背景"
            "skin_background" -> "侧边栏背景"
            "qq_setting_me_bg" -> "设置背景"
            "skin_aio_friend_bubble_nor" -> "好友气泡"
            "skin_aio_user_bubble_nor" -> "我的气泡"
            else -> stem
        }

/** 末尾那个空位：点一下就是导入。 */
@Composable
private fun ThemePackEmptySlot(onClick: () -> Unit) {
    Column(
            modifier =
                    Modifier.width(92.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onClick)
                            .padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(10.dp)
                                ),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
                text = "导入",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemePackCard(
        name: String,
        badge: String,
        primary: Int,
        secondary: Int,
        coverUri: String?,
        followWallpaper: Boolean,
        selected: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null,
) {
    val borderColor =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Column(
            modifier =
                    Modifier.width(92.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                    onClick = onClick,
                                    onLongClick = onLongClick ?: onClick,
                            )
                            .padding(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(10.dp)
                                )
                                .background(
                                        when {
                                            coverUri != null ->
                                                    Brush.linearGradient(
                                                            listOf(
                                                                    Color(primary),
                                                                    Color(secondary)
                                                            )
                                                    )
                                            followWallpaper ->
                                                    // 「跟随壁纸」不是单一配色，用一条多色带表示「系统取色」
                                                    Brush.linearGradient(
                                                            listOf(
                                                                    Color(0xFF5B7C99),
                                                                    Color(0xFF8A6E7E),
                                                                    Color(0xFFB08244)
                                                            )
                                                    )
                                            else ->
                                                    Brush.linearGradient(
                                                            listOf(
                                                                    Color(primary),
                                                                    Color(secondary)
                                                            )
                                                    )
                                        }
                                )
        ) {
            // 有背景图就用它当缩略图，最直观
            if (coverUri != null) {
                Image(
                        painter = rememberAsyncImagePainter(model = coverUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                )
            }
            Text(
                    text = badge,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}
