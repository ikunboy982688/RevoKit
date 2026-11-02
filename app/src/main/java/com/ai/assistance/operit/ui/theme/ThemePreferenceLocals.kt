package com.ai.assistance.operit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.ThemePreferenceSnapshot
import com.ai.assistance.operit.data.preferences.ThemePreferenceValues
import com.ai.assistance.operit.data.preferences.ThemeStartupCache

val LocalThemePreferenceSnapshot =
    compositionLocalOf<ThemePreferenceSnapshot> {
        error("LocalThemePreferenceSnapshot is not provided.")
    }

@Composable
fun rememberActiveThemePreferenceSnapshot(): ThemePreferenceSnapshot {
    val context = LocalContext.current
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    // RevoKit：冷启动时 DataStore 的第一帧是异步的，用上次落盘的缓存兜住第一帧，
    // 否则清理后台重开会先闪一下默认主题（外面那层粉紫白）再跳回来。
    val startupSnapshot =
        remember(context) {
            ThemeStartupCache.load(context)
                ?: ThemePreferenceSnapshot(
                    source = "character_card",
                    sourceId = CharacterCardManager.DEFAULT_CHARACTER_CARD_ID,
                    values = ThemePreferenceValues.defaultVisual(),
                )
        }
    val themeSnapshot by activePromptManager.activeThemePreferenceSnapshotFlow.collectAsState(
        initial = startupSnapshot,
    )
    return themeSnapshot
}
