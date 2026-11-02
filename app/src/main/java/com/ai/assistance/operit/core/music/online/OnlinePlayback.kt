package com.ai.assistance.operit.core.music.online

import android.content.Context
import com.ai.assistance.operit.core.music.MusicQueueItem
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 在线曲目的「播放地址 / 歌词」解析入口。
 *
 * 单独拎出来是因为它必须能在**非主线程**上同步调用 —— ExoPlayer 的
 * ResolvingDataSource 在它自己的加载线程里问要地址，那里没法挂起。
 */
object OnlinePlayback {

    private const val TAG = "OnlinePlayback"

    /**
     * @param trial true = 这只是个试听/残片，凑合放，别当最终结果记下来。
     *   缓存那一步靠它判断 —— 把残片缓存住会让用户整整半小时都只能听几十秒。
     */
    data class Resolved(
        val url: String,
        val sourceName: String,
        val sourceId: String,
        val trial: Boolean = false
    )

    /**
     * 解析一首在线歌的播放地址。
     *
     * 三段式，每一段都比上一段慢、但比上一段更可能拿到**完整版**：
     *
     * ① **应用直连**（纯 Kotlin，一两次请求，通常一两秒）
     *    - 拿到完整版 → 直接返回，秒开。
     *    - 拿到的是**试听片段**（网易对 VIP/无版权歌只给几十秒）→ 不急着认，往下走。
     *    - 拿不到（QQ / 酷狗官方不给）→ 往下走。
     *
     * ② **问音源脚本**（这才是能拿到完整版的主力）
     *    洛雪音源背后是第三方聚合后端，它们能解析出完整版 —— 用户在自己手机上装同一批
     *    插件能正常听整首，靠的就是这一步。以前把音源放在最后、还让它无限重试，
     *    结果就是「要么等一分多钟、要么只能听几十秒」。现在给音源脚本设了**总预算**
     *    （在 HTTP 桥那一层，超预算的请求直接失败），它要么几秒内给出地址，要么快速放弃。
     *
     * ③ **酷我同名兜底** —— 用「歌名+歌手」去酷我找一首完整版。最后的保底。
     */
    fun resolveUrl(
        context: Context,
        item: MusicQueueItem,
        quality: String? = null
    ): Resolved? {
        val songJson = item.onlineSongJson ?: return null
        val wantQuality = quality ?: MusicSourceStore.quality(context)
        val platform = item.onlinePlatform.orEmpty()

        // 命中缓存直接返回 —— 重听同一首、或者列表里前进后退，都不用再等一次
        cacheKey(item, wantQuality)?.let { key -> urlCache[key]?.takeIf { it.fresh }?.resolved?.let { return it } }

        val song =
            OnlineSong(
                sourceId = item.onlineSourceId.orEmpty(),
                songId = "",
                platform = platform,
                title = item.title.orEmpty(),
                artist = item.artist.orEmpty(),
                rawJson = songJson
            )

        // ---------- ① 应用直连 ----------
        val direct = directUrl(platform, songJson, wantQuality)

        if (direct != null && !direct.trial) {
            AppLogger.d(TAG, "应用直连拿到完整版（$platform）")
            return remember(item, wantQuality, Resolved(direct.url, "应用直连", ""))
        }
        if (direct == null) {
            AppLogger.w(TAG, "$platform 应用直连没拿到地址，转问音源")
        } else {
            AppLogger.w(TAG, "$platform 直连只有试听片段，转问音源换完整版")
        }

        // ---------- ② 问音源 ----------
        val fromSource = askSources(context, item, song, platform, wantQuality)
        if (fromSource != null) {
            AppLogger.d(TAG, "由 ${fromSource.sourceName} 换到完整版")
            return remember(item, wantQuality, fromSource)
        }

        // ---------- ③ 酷我同名兜底 / 实在不行就放试听 ----------
        if (direct != null) {
            if (direct.trial && !platform.equals("kw", ignoreCase = true)) {
                val full =
                    try {
                        runBlocking {
                            withTimeoutOrNull(DIRECT_BUDGET_MS) {
                                PlatformPlayUrl.findComplete(
                                    item.title.orEmpty(),
                                    item.artist,
                                    wantQuality
                                )
                            }
                        }
                    } catch (_: Throwable) {
                        null
                    }
                if (!full.isNullOrBlank()) {
                    AppLogger.d(TAG, "酷我同名歌补到完整版")
                    return remember(item, wantQuality, Resolved(full, "酷我完整版", ""))
                }
            }
            // 试听也别浪费：有声音总比「点了没反应」强
            return Resolved(direct.url, if (direct.trial) "试听版" else "应用直连", "", direct.trial)
        }
        return null
    }

    /** 应用侧直连取链。失败返回 null —— 让调用方继续问音源 */
    private fun directUrl(platform: String, songJson: String, quality: String): PlatformPlayUrl.Direct? {
        if (platform.isBlank()) return null
        val id = PlatformLyric.songIdFor(platform, songJson)
        if (id.isBlank()) return null
        return try {
            runBlocking {
                withTimeoutOrNull(DIRECT_BUDGET_MS) { PlatformPlayUrl.load(platform, id, quality) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 挨个问音源脚本。
     *
     * 顺序不是瞎排的：先问「上次成功过这个平台的源」，再问「当初搜出这首歌的源」，
     * 然后「平台对得上的源」，最后才乱枪打鸟。绝大多数情况第一个就命中。
     *
     * 时间控制靠两道闸：
     * - 外层 [SOURCE_BUDGET_MS] 决定这一整段最多耗多久；
     * - 内层把剩余预算写进引擎的 `actionBudgetMs`，音源脚本再发 HTTP 一律直接失败，
     *   于是脚本会快速放弃而不是陪它干等（Kotlin 的 withTimeoutOrNull 掐不住同步的
     *   JS 调用，只能靠这一层）。
     */
    private fun askSources(
        context: Context,
        item: MusicQueueItem,
        song: OnlineSong,
        platform: String,
        quality: String
    ): Resolved? {
        val enabled = MusicSourceStore.list(context).filter { it.enabled }
        if (enabled.isEmpty()) {
            AppLogger.w(TAG, "没有启用任何在线音源")
            return null
        }
        val remembered = MusicSourceStore.lastSource(context, item.onlinePlatform)
        val best = MusicSourceStore.bestSource(context, platform)
        val bad = MusicSourceStore.badSources(context, platform)
        val candidates =
            ranked(enabled, item.onlineSourceId, remembered, platform, best, bad)
                .take(MAX_SOURCE_TRIES)
        if (candidates.isEmpty()) {
            AppLogger.w(TAG, "没有支持 $platform 的启用音源")
            return null
        }

        val deadline = System.currentTimeMillis() + SOURCE_BUDGET_MS
        val engine = MusicSourceEngine.getInstance(context)
        // 全是残片时的保底：先记着，一个完整版都没有就凑合放它
        var bestTrial: Resolved? = null
        for (source in candidates) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) break
            // 单个源的 HTTP 预算：优先保证「上次成功那个」有足够时间（真机实测同一首歌
            // 换源能差一个数量级），其余候选本来就只是猜的，给短绳。
            val perSource =
                if (source.id == best || source.id == remembered) REMEMBERED_SOURCE_MS
                else PER_SOURCE_MS
            engine.actionBudgetMs = left.coerceAtMost(perSource)
            // 外层超时给一点余量：预算到点后脚本还要一小会儿才真的把 Promise reject 掉
            val url: String? =
                try {
                    runBlocking {
                        withTimeoutOrNull(left + 2_000L) {
                            engine.resolveUrl(source, song, quality)
                        }
                    }?.getOrNull()
                } catch (_: Throwable) {
                    null
                }
            if (url.isNullOrBlank()) {
                AppLogger.w(TAG, "${source.name} 没能换到地址")
                MusicSourceStore.markBadSource(context, platform, source.id)
                continue
            }
            // **量一下字节数再决定要不要**。音源之间质量差距极大：实测同一首歌，
            // 有的源给 10MB 完整版，有的只给 0.9MB 甚至 0.08MB 的残片，而脚本一律自称
            // 「成功」。所以这里 HEAD 一下，明显是试听长度的就换下一个源；
            // 探不到长度（返回 -1）就当它没问题，别误杀。
            val bytes =
                try {
                    runBlocking { PlatformPlayUrl.probeBytes(url) }
                } catch (_: Throwable) {
                    -1L
                }
            if (bytes in 1 until PlatformPlayUrl.TRIAL_MAX_BYTES) {
                AppLogger.w(TAG, "${source.name} 只给了 ${bytes / 1024}KB 的残片，换下一个")
                MusicSourceStore.markBadSource(context, platform, source.id)
                if (bestTrial == null) {
                    bestTrial = Resolved(url, source.name, source.id, trial = true)
                }
                continue
            }
            item.onlinePlatform?.let {
                MusicSourceStore.markLastSource(context, it, source.id)
                MusicSourceStore.markBestSource(context, it, source.id)
            }
            AppLogger.d(TAG, "${source.name} 换到完整版（${if (bytes > 0) bytes / 1024 else -1}KB）")
            return Resolved(url, source.name, source.id)
        }
        return bestTrial
    }

    // ------------------------------------------------------------------ 结果缓存

    private data class CacheEntry(val resolved: Resolved, val at: Long) {
        /** 缓存只留半小时：第三方给的直链大多带时效签名，放久了会 403 */
        val fresh: Boolean
            get() = System.currentTimeMillis() - at < CACHE_TTL_MS
    }

    private val urlCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    private fun cacheKey(item: MusicQueueItem, quality: String): String? {
        val platform = item.onlinePlatform.orEmpty()
        val json = item.onlineSongJson ?: return null
        val id = PlatformLyric.songIdFor(platform, json)
        // 拿不到平台内 ID 的（比如插件搜出来的歌）就用「平台+歌名+歌手」当键，
        // 反正缓存只是加速，撞不上顶多多解析一次
        return if (id.isNotBlank()) "$platform|$id|$quality"
        else "$platform|${item.title}|${item.artist}|$quality"
    }

    /** 只缓存完整版 —— 试听/残片缓存了会让用户一直听几十秒 */
    private fun remember(item: MusicQueueItem, quality: String, resolved: Resolved): Resolved {
        if (!resolved.trial) {
            cacheKey(item, quality)?.let { urlCache[it] = CacheEntry(resolved, System.currentTimeMillis()) }
        }
        // 顺手清掉过期项，别让这个 Map 无限涨
        if (urlCache.size > 64) {
            urlCache.entries.removeIf { !it.value.fresh }
        }
        return resolved
    }

    /**
     * 应用侧直连的预算。它只有一次请求（顶多两次：取链 + 跟一次重定向），
     * 正常一秒内就回来了，给 8 秒是给弱网留余量 —— 超过就直接认为这条路不通，转音源。
     */
    private const val DIRECT_BUDGET_MS = 8_000L

    /**
     * 「问音源」这一整段的墙上时钟预算。直连已经先跑掉了，这一段就是用户感知到的
     * 额外等待，卡在 12 秒 —— 再久就真的像「点了没反应」了。
     */
    private const val SOURCE_BUDGET_MS = 10_000L

    /** 一次最多问几个音源。宁可少问两个、也别让用户干等 */
    private const val MAX_SOURCE_TRIES = 4

    /**
     * 「上次成功过这个平台的源」的 HTTP 预算。它是命中率最高的那个，值得多给一点 ——
     * 真机实测同一个源换不同歌的快慢能差一个数量级。
     */
    private const val REMEMBERED_SOURCE_MS = 6_000L

    /** 其余候选的 HTTP 预算。它们本来就是猜的，短绳，别拖累整体 */
    private const val PER_SOURCE_MS = 3_500L

    /** 播放地址缓存的有效期。第三方直链大多带时效签名，放久了会 403，半小时足够 */
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    /** 歌词比播放地址次要得多，给的时间更短，失败也不影响出声 */
    private const val LYRIC_BUDGET_MS = 6_000L

    private const val LYRIC_PER_SOURCE_MS = 4_000L

    /**
     * 候选音源及其排序。
     *
     * 平台未知的源一律保留（MusicFree 插件常不填 platform，但它是单平台的），
     * 只有「源明确写了支持列表、且列表里没有这首歌的平台」才真的丢弃。
     *
     * 排序直接决定点播快不快 —— 开着七八个源时逐个试，前面几个一失败就是好几秒。
     * 顺序：给过完整版的 → 上次成功的 → 搜出这首歌的 → 声明支持该平台的 → 其余；
     * **进过黑名单的（给残片 / 失败过）一律垫底**。
     */
    private fun ranked(
        enabled: List<OnlineSource>,
        sourceId: String?,
        rememberedId: String?,
        platform: String?,
        bestId: String?,
        bad: Set<String>
    ): List<OnlineSource> {
        val want = platform?.trim()?.lowercase().orEmpty()
        return enabled
            .filter { source ->
                val declared = source.platform.trim()
                declared.isBlank() ||
                    want.isBlank() ||
                    source.platform.split(',', '|', ' ')
                        .any { it.trim().lowercase() == want }
            }
            .sortedBy { source ->
                // 黑名单必须先判：否则「上次成功过」的源就算给了残片也还是排第一，
                // 黑名单等于没写 —— 试过，就是这么踩的。
                when {
                    source.id == bestId -> 0
                    source.id in bad -> 5
                    source.id == rememberedId -> 1
                    !sourceId.isNullOrBlank() && source.id == sourceId -> 2
                    supportsPlatform(source, platform) -> 3
                    else -> 4
                }
            }
    }

    /** 取在线歌词，只要拿到一首就返回，拿不到返回 null（UI 显示「暂无歌词」） */
    fun loadLyric(context: Context, item: MusicQueueItem): String? {
        val songJson = item.onlineSongJson ?: return null
        val song =
            OnlineSong(
                sourceId = item.onlineSourceId.orEmpty(),
                songId = "",
                platform = item.onlinePlatform.orEmpty(),
                title = item.title.orEmpty(),
                artist = item.artist.orEmpty(),
                rawJson = songJson
            )
        val enabled = MusicSourceStore.list(context).filter { it.enabled }
        val remembered = MusicSourceStore.lastSource(context, item.onlinePlatform)
        val candidates =
            ranked(enabled, item.onlineSourceId, remembered, item.onlinePlatform, null, emptySet())
        if (candidates.isEmpty()) return null
        val deadline = System.currentTimeMillis() + LYRIC_BUDGET_MS
        val engine = MusicSourceEngine.getInstance(context)
        for (source in candidates) {
            val remaining =
                (deadline - System.currentTimeMillis())
                    .coerceAtLeast(2_000L)
                    .coerceAtMost(LYRIC_PER_SOURCE_MS)
            val text: String? =
                try {
                    runBlocking {
                        withTimeoutOrNull(remaining) { engine.lyric(source, song) }?.getOrNull()
                    }
                } catch (_: Throwable) {
                    null
                }
            if (!text.isNullOrBlank()) return text
            if (System.currentTimeMillis() >= deadline) break
        }
        return null
    }

    /** 洛雪源把支持的平台写成 `kw,tx,wy` 这种串，MusicFree 插件是单平台 */
    private fun supportsPlatform(source: OnlineSource, platform: String?): Boolean {
        if (platform.isNullOrBlank()) return false
        if (source.platform.isBlank()) return false
        return source.platform.split(',', '|', ' ')
            .map { it.trim().lowercase() }
            .any { it == platform.lowercase() }
    }
}
