package com.ai.assistance.operit.ui.features.music

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 音乐页几个公用的格式化函数。
 *
 * 抽成 internal 是因为全屏播放页、迷你播放条是两个文件，都要用同一套文案和时长格式，
 * 各写一份迟早会对不上。纯函数，不碰状态，随便调。
 */

internal fun playModeLabel(mode: Int): String =
    when (mode) {
        1 -> "列表循环"
        2 -> "单曲循环"
        3 -> "随机播放"
        else -> "顺序播放"
    }

internal fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

internal fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) ==
        PackageManager.PERMISSION_GRANTED

/** `3:07` 这种短格式；时长未知时给 `--:--`，不要显示 `0:00` 误导用户 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** 文件夹分组用的名字：取路径最后一段，路径天生为空时兜底成“未知文件夹” */
internal fun folderLabel(path: String?): String {
    val p = path?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return "未知文件夹"
    return p.substringAfterLast('/').ifBlank { "未知文件夹" }
}

/**
 * MediaStore 会把没写 tag 的曲目的艺术家填成字符串 `<unknown>`（不是 null），
 * 直接显示会露出这个尖括号。迷你条、全屏页、队列都走这里，保证文案统一。
 */
internal fun displayArtist(artist: String?): String {
    val a = artist?.trim()
    return if (a.isNullOrBlank() || a == "<unknown>" || a.equals("unknown", ignoreCase = true)) {
        "未知艺术家"
    } else {
        a
    }
}
