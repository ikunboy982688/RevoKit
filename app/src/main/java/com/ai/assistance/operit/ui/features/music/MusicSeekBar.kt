package com.ai.assistance.operit.ui.features.music

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 自制的音乐进度条。
 *
 * 为什么不用 Material3 的 `Slider`：全屏播放页是 `ModalBottomSheet`，系统 Slider 在里面
 * 拖不动（面板自身的纵向拖拽手势会把触摸吃掉一半，实测拖动直接没反应）。
 * 这里用 `detectDragGestures` 自己做，水平拖拽完全由我们接管，点一下也能直接跳转。
 *
 * 触控高度 30dp（手指好按），视觉轨道 4dp，滑块 14dp。
 */
@Composable
fun MusicSeekBar(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
    onSeek: (Long) -> Unit,
    /** 拖动过程中回调当前比例（0~1），松手时回 null；外面的时间文字靠它跟手 */
    onDraggingChange: (Float?) -> Unit = {}
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current

    val realProgress =
        if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownProgress = dragging ?: realProgress

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(30.dp)) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

        fun fractionAt(x: Float): Float = (x / widthPx).coerceIn(0f, 1f)

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragging = fractionAt(offset.x)
                                onDraggingChange(dragging)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragging = fractionAt(change.position.x)
                                onDraggingChange(dragging)
                            },
                            onDragEnd = {
                                val target = dragging
                                dragging = null
                                onDraggingChange(null)
                                if (target != null && durationMs > 0L) {
                                    onSeek((target * durationMs).toLong())
                                }
                            },
                            onDragCancel = {
                                dragging = null
                                onDraggingChange(null)
                            }
                        )
                    }
                    .pointerInput(durationMs) {
                        // 点一下直接跳过去，不用非得拖
                        detectTapGestures { offset ->
                            if (durationMs > 0L) {
                                onSeek((fractionAt(offset.x) * durationMs).toLong())
                            }
                        }
                    }
        ) {
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(inactiveColor)
            )
            Box(
                modifier =
                    Modifier.fillMaxWidth(shownProgress.coerceIn(0.0001f, 1f))
                        .height(4.dp)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(activeColor)
            )
            // 拖动时才把滑块放大，静态时小一点，视觉上更像"跟手"
            val thumbSize = if (dragging != null) 16.dp else 13.dp
            val halfThumbPx = with(density) { thumbSize.toPx() / 2f }
            Box(
                modifier =
                    Modifier.offset {
                            val center = shownProgress * widthPx
                            IntOffset((center - halfThumbPx).roundToInt(), 0)
                        }
                        .size(thumbSize)
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(activeColor)
            )
        }
    }
}
