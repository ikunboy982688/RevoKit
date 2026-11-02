package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.core.music.MusicPlaybackManager
import com.ai.assistance.operit.core.music.MusicQueueItem
import com.ai.assistance.operit.core.tools.MusicPlaybackResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import org.json.JSONArray

class StandardMusicPlaybackTools(private val context: Context) {

    private val manager: MusicPlaybackManager by lazy {
        MusicPlaybackManager.getInstance(context)
    }

    suspend fun play(tool: AITool): ToolResult {
        val source = tool.parameters.find { it.name == "source" }?.value?.trim()
        if (source.isNullOrBlank()) {
            return error(tool, "Must provide source parameter")
        }

        val sourceType = tool.parameters.find { it.name == "source_type" }?.value?.trim()
        if (sourceType.isNullOrBlank()) {
            return error(tool, "Must provide source_type parameter: path, url, or uri")
        }

        val title = tool.parameters.find { it.name == "title" }?.value?.trim()
        val artist = tool.parameters.find { it.name == "artist" }?.value?.trim()
        val loop = parseOptionalBoolean(tool, "loop")
        if (loop.error != null) {
            return error(tool, loop.error)
        }
        val volume = parseOptionalFloat(tool, "volume")
        if (volume.error != null) {
            return error(tool, volume.error)
        }
        val startPositionMs = parseOptionalLong(tool, "start_position_ms")
        if (startPositionMs.error != null) {
            return error(tool, startPositionMs.error)
        }

        return try {
            val result =
                manager.play(
                    source = source,
                    sourceType = sourceType,
                    title = title,
                    artist = artist,
                    loop = loop.value,
                    volume = volume.value,
                    startPositionMs = startPositionMs.value
                )
            ToolResult(toolName = tool.name, success = true, result = result)
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Music play failed", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Music play failed: ${e.message}"
            )
        }
    }

    suspend fun playQueue(tool: AITool): ToolResult {
        val items = parseQueueItems(tool)
        if (items.error != null) {
            return error(tool, items.error)
        }

        val loop = parseOptionalBoolean(tool, "loop")
        if (loop.error != null) {
            return error(tool, loop.error)
        }
        val volume = parseOptionalFloat(tool, "volume")
        if (volume.error != null) {
            return error(tool, volume.error)
        }
        val startIndex = parseOptionalInt(tool, "start_index")
        if (startIndex.error != null) {
            return error(tool, startIndex.error)
        }
        val startPositionMs = parseOptionalLong(tool, "start_position_ms")
        if (startPositionMs.error != null) {
            return error(tool, startPositionMs.error)
        }

        return try {
            val result =
                manager.playQueue(
                    items = items.value,
                    loop = loop.value,
                    volume = volume.value,
                    startIndex = startIndex.value,
                    startPositionMs = startPositionMs.value
                )
            ToolResult(toolName = tool.name, success = true, result = result)
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Music queue play failed", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Music queue play failed: ${e.message}"
            )
        }
    }

    suspend fun pause(tool: AITool): ToolResult = execute(tool) { manager.pause() }

    suspend fun resume(tool: AITool): ToolResult = execute(tool) { manager.resume() }

    suspend fun stop(tool: AITool): ToolResult = execute(tool) { manager.stop() }

    suspend fun status(tool: AITool): ToolResult = execute(tool) { manager.status() }

    suspend fun seek(tool: AITool): ToolResult {
        val position = parseRequiredLong(tool, "position_ms")
        if (position.error != null) {
            return error(tool, position.error)
        }
        return execute(tool) { manager.seek(position.value) }
    }

    suspend fun setVolume(tool: AITool): ToolResult {
        val volume = parseRequiredFloat(tool, "volume")
        if (volume.error != null) {
            return error(tool, volume.error)
        }
        return execute(tool) { manager.setVolume(volume.value) }
    }

    private suspend fun execute(
        tool: AITool,
        action: suspend () -> MusicPlaybackResultData
    ): ToolResult {
        return try {
            ToolResult(toolName = tool.name, success = true, result = action())
        } catch (e: IllegalStateException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Music operation failed", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Music operation failed: ${e.message}"
            )
        }
    }

    private fun error(tool: AITool, message: String): ToolResult {
        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = message
        )
    }

    private fun parseOptionalBoolean(tool: AITool, name: String): ParsedBoolean {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw == null) return ParsedBoolean(null, null)
        val value = raw.toBooleanStrictOrNull()
        return if (value == null) {
            ParsedBoolean(null, "$name must be true or false")
        } else {
            ParsedBoolean(value, null)
        }
    }

    private fun parseOptionalFloat(tool: AITool, name: String): ParsedFloat {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw == null) return ParsedFloat(null, null)
        val value = raw.toFloatOrNull()
        return if (value == null) {
            ParsedFloat(null, "$name must be a number")
        } else {
            ParsedFloat(value, null)
        }
    }

    private fun parseRequiredFloat(tool: AITool, name: String): ParsedRequiredFloat {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw.isNullOrBlank()) return ParsedRequiredFloat(0f, "Must provide $name parameter")
        val value = raw.toFloatOrNull()
        return if (value == null) {
            ParsedRequiredFloat(0f, "$name must be a number")
        } else {
            ParsedRequiredFloat(value, null)
        }
    }

    private fun parseOptionalLong(tool: AITool, name: String): ParsedLong {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw == null) return ParsedLong(null, null)
        val value = raw.toLongOrNull()
        return if (value == null) {
            ParsedLong(null, "$name must be an integer")
        } else {
            ParsedLong(value, null)
        }
    }

    private fun parseOptionalInt(tool: AITool, name: String): ParsedInt {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw == null) return ParsedInt(null, null)
        val value = raw.toIntOrNull()
        return if (value == null) {
            ParsedInt(null, "$name must be an integer")
        } else {
            ParsedInt(value, null)
        }
    }

    private fun parseQueueItems(tool: AITool): ParsedQueueItems {
        val raw = tool.parameters.find { it.name == "items" }?.value?.trim()
        if (raw.isNullOrBlank()) {
            return ParsedQueueItems(emptyList(), "Must provide items parameter")
        }

        return try {
            val array = JSONArray(raw)
            if (array.length() == 0) {
                return ParsedQueueItems(emptyList(), "items must contain at least one track")
            }

            val items = mutableListOf<MusicQueueItem>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: return ParsedQueueItems(emptyList(), "items[$index] must be an object")
                val source = item.optString("source").trim()
                if (source.isBlank()) {
                    return ParsedQueueItems(emptyList(), "items[$index].source is required")
                }
                val sourceType = item.optString("source_type").trim()
                if (sourceType.isBlank()) {
                    return ParsedQueueItems(
                        emptyList(),
                        "items[$index].source_type is required: path, url, or uri"
                    )
                }
                items.add(
                    MusicQueueItem(
                        source = source,
                        sourceType = sourceType,
                        title = item.optString("title").trim().takeIf { it.isNotBlank() },
                        artist = item.optString("artist").trim().takeIf { it.isNotBlank() }
                    )
                )
            }
            ParsedQueueItems(items, null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse music queue items", e)
            ParsedQueueItems(emptyList(), "items must be a JSON array")
        }
    }

    private fun parseRequiredLong(tool: AITool, name: String): ParsedRequiredLong {
        val raw = tool.parameters.find { it.name == name }?.value?.trim()
        if (raw.isNullOrBlank()) return ParsedRequiredLong(0L, "Must provide $name parameter")
        val value = raw.toLongOrNull()
        return if (value == null) {
            ParsedRequiredLong(0L, "$name must be an integer")
        } else {
            ParsedRequiredLong(value, null)
        }
    }

    private data class ParsedBoolean(val value: Boolean?, val error: String?)
    private data class ParsedFloat(val value: Float?, val error: String?)
    private data class ParsedRequiredFloat(val value: Float, val error: String?)
    private data class ParsedInt(val value: Int?, val error: String?)
    private data class ParsedLong(val value: Long?, val error: String?)
    private data class ParsedRequiredLong(val value: Long, val error: String?)
    private data class ParsedQueueItems(val value: List<MusicQueueItem>, val error: String?)

    companion object {
        private const val TAG = "MusicPlaybackTools"
    }
}
