package com.ai.assistance.operit.core.music.online

import android.content.Context
import android.os.SystemClock
import com.ai.assistance.operit.core.tools.javascript.OperitQuickJsEngine
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 在线音源引擎：用项目自带的 QuickJS 跑第三方音源脚本。
 *
 * 为什么是 QuickJS 而不是 WebView：项目里本来就有 `quickjs` 模块（arm64-v8a），
 * JsEngine 那套已经在跑 ToolPkg，复用它不增加任何安装包体积；WebView 反而要额外
 * 处理跨域、Cookie、生命周期，还更吃内存。
 *
 * 一个实例只挂一个音源脚本 —— 脚本都是往全局写变量的（`module.exports`、
 * `globalThis.lx`），几个脚本挤一个全局作用域必然互相踩。所以换源就重建引擎，
 * 上一个直接关掉。
 */
class MusicSourceEngine private constructor(private val context: Context) {

    private val mutex = Mutex()

    private var engine: OperitQuickJsEngine? = null
    private var loadedId: String? = null
    private var loadedInfo: SourceRuntimeInfo? = null

    /**
     * 当前绑给引擎的 HTTP 桥。留着引用是为了能在跑动作前给它设总预算
     * （见 [MusicSourceHost.beginBudget]）—— 这是唯一能真正掐住「卡在同步 JS 调用里」
     * 的办法。
     */
    private var host: MusicSourceHost? = null

    /**
     * 单个动作（取链 / 取歌词）允许音源脚本花的总时长。
     * 到点后它再发 HTTP 一律直接失败，脚本会快速放弃。
     */
    @Volatile var actionBudgetMs: Long = DEFAULT_ACTION_BUDGET_MS

    companion object {
        private const val TAG = "MusicSourceEngine"
        private const val ADAPTER_ASSET = "packages/music_source.js"
        /** poll 循环的安全网。正常情况 HTTP 预算会让脚本早早就结束，轮不到它 */
        private const val TIMEOUT_MS = 15_000L
        private const val DEFAULT_ACTION_BUDGET_MS = 6_000L

        @Volatile private var INSTANCE: MusicSourceEngine? = null

        fun getInstance(context: Context): MusicSourceEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicSourceEngine(context.applicationContext).also { INSTANCE = it }
            }
    }

    /** 加载后探测到的运行时信息 */
    data class SourceRuntimeInfo(
        val name: String,
        val platform: String,
        val actions: List<String>,
        val qualities: List<String>,
        val platforms: List<String>
    )

    private var adapterCache: String? = null
    private val callCounter = AtomicLong(0)

    // ------------------------------------------------------------------ 对外

    /** 只探测不搜索：导入音源后点「测试」用它，能立刻知道这个脚本能不能跑 */
    suspend fun probe(source: OnlineSource): Result<SourceRuntimeInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    ensureLoaded(source)
                    loadedInfo ?: error("音源加载失败")
                }
            }
        }

    suspend fun search(source: OnlineSource, query: String, page: Int = 1): Result<List<OnlineSong>> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    ensureLoaded(source)
                    val payload = runAction("search", JSONArray().put(query).put(page).put("music"))
                    val value = payload.optJSONObject("value") ?: JSONObject()
                    val songs = value.optJSONArray("songs") ?: JSONArray()
                    val out = ArrayList<OnlineSong>(songs.length())
                    for (i in 0 until songs.length()) {
                        val item = songs.optJSONObject(i) ?: continue
                        out.add(
                            OnlineSong(
                                sourceId = source.id,
                                songId = item.optString("id"),
                                platform = item.optString("platform", source.platform),
                                title = item.optString("title", "未知歌曲"),
                                artist = item.optString("artist"),
                                album = item.optString("album").takeIf { it.isNotBlank() },
                                durationMs = item.optLong("durationMs", 0L),
                                artworkUrl =
                                    item.optString("artwork").takeIf { it.isNotBlank() },
                                rawJson =
                                    item.optJSONObject("raw")?.toString() ?: item.toString()
                            )
                        )
                    }
                    out
                }
            }
        }

    /** 取播放地址。失败不要吞异常，调用方要靠它决定回退到哪个音源 */
    suspend fun resolveUrl(
        source: OnlineSource,
        song: OnlineSong,
        quality: String = "320k"
    ): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    ensureLoaded(source)
                    val payload = runAction("resolve", JSONArray().put(songPayload(song)).put(quality))
                    val value = payload.optJSONObject("value") ?: JSONObject()
                    val url = value.optString("url")
                    if (url.isBlank()) error("音源没有返回播放地址")
                    url
                }
            }
        }

    suspend fun lyric(source: OnlineSource, song: OnlineSong): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                mutex.withLock {
                    ensureLoaded(source)
                    val payload = runAction("lyric", JSONArray().put(songPayload(song)))
                    val value = payload.optJSONObject("value") ?: JSONObject()
                    value.optString("lrc")
                }
            }
        }

    /** 换源 / 卸载脚本时清干净，避免旧脚本的全局变量残留 */
    fun release() {
        runCatching { engine?.close() }
        engine = null
        host = null
        loadedId = null
        loadedInfo = null
    }

    /**
     * 取地址 / 取歌词时发给适配层的歌曲载荷。
     *
     * 必须**显式带上 `platform`**（单个平台名，如 `tx`），原因是血泪教训：
     * 洛雪源上报的 `platform` 是它支持的**全部平台拼成的串**（`kw,wy,kg,tx,mg`），
     * 适配层若拿不到歌曲自己的平台就会掉到这个兜底值上，把整串塞进请求信封，
     * 脚本一律回「不支持的平台: kw,wy,kg,tx,mg」—— 表现就是「搜得到、点播没声音」。
     */
    private fun songPayload(song: OnlineSong): String {
        val info =
            runCatching { JSONObject(song.rawJson.ifBlank { "{}" }) }
                .getOrDefault(JSONObject())
        return JSONObject()
            .put("platform", song.platform)
            .put("raw", info)
            .toString()
    }

    // ------------------------------------------------------------------ 内部

    private fun ensureLoaded(source: OnlineSource) {
        if (engine != null && loadedId == source.id && loadedInfo != null) return

        release()

        val code = MusicSourceStore.readScript(context, source)
            ?: error("音源脚本文件不见了，请重新导入")

        val created = OperitQuickJsEngine()
        try {
            val hostInstance = MusicSourceHost()
            created.bindNativeInterface(hostInstance)
            host = hostInstance
            created.evaluate<Any?>(adapterScript(), "music_source_adapter.js")

            val args =
                JSONArray()
                    .put(if (source.dialect == SourceDialect.LX) "LX" else "MUSICFREE")
                    .put(code)
                    .toString()
            val raw = created.callFunction<String>("__ms_load", args)
            val obj = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
            if (!obj.optBoolean("ok", false)) {
                error(obj.optString("error").ifBlank { "音源脚本加载失败" })
            }

            loadedInfo =
                SourceRuntimeInfo(
                    name = obj.optString("name", source.name),
                    platform = obj.optString("platform"),
                    actions = obj.optJSONArray("actions")?.toStringList() ?: emptyList(),
                    qualities = obj.optJSONArray("qualities")?.toStringList() ?: emptyList(),
                    platforms = obj.optJSONArray("platforms")?.toStringList() ?: emptyList()
                )
            engine = created
            loadedId = source.id
        } catch (e: Exception) {
            runCatching { created.close() }
            throw e
        }
    }

    /**
     * 跑一个动作。
     *
     * 两段式的必要性：QuickJS 的 callFunction 是同步的，而插件接口返回 Promise。
     * `__ms_start` 只负责把 Promise 挂到槽位上并返回；宿主每次 callFunction 之后会自动
     * 跑一轮微任务，所以真正结果在随后的 `__ms_take` 里就能读到。因为 HTTP 是同步桥，
     * 绝大多数插件第一轮就结算完了；少数要多层 await 的，这里多问几轮即可。
     */
    private fun runAction(action: String, args: JSONArray): JSONObject {
        val active = engine ?: error("音源未加载")
        val callId = callCounter.incrementAndGet()
        // 关键：开闸必须在 __ms_start 之前。__ms_start 会把整个脚本连同它的同步 HTTP
        // 一起跑完才返回，等它回来再设预算就晚了。
        host?.beginBudget(actionBudgetMs)
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        return try {
            active.callFunction<String>(
                "__ms_start",
                JSONArray().put(action).put(args.toString()).toString()
            )
            pollUntilDone(active, deadline)
        } finally {
            host?.endBudget()
        }
    }

    /**
     * 轮询取动作结果。
     *
     * 两段式的必要性：QuickJS 的 callFunction 是同步的，而插件接口返回 Promise。
     * `__ms_start` 只负责把 Promise 挂到槽位上并返回；宿主每次 callFunction 之后会自动
     * 跑一轮微任务，所以真正结果在随后的 `__ms_take` 里就能读到。因为 HTTP 是同步桥，
     * 绝大多数插件第一轮就结算完了；少数要多层 await 的，这里多问几轮即可。
     *
     * [deadline] 只是安全网 —— 正常情况下 HTTP 预算（见 [MusicSourceHost.beginBudget]）
     * 会让脚本早早失败或返回，轮不到它。
     */
    private fun pollUntilDone(active: OperitQuickJsEngine, deadline: Long): JSONObject {
        while (true) {
            val raw = active.callFunction<String>("__ms_take", "[]")
            val obj = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
            if (obj.optBoolean("done", false)) {
                val errMessage = obj.optString("error")
                if (errMessage.isNotBlank()) error(errMessage)
                return obj
            }
            if (SystemClock.elapsedRealtime() > deadline) {
                error("音源响应超时")
            }
            // 微任务队列由下一次 callFunction 顺带抽干，这里只让出一点点 CPU
            Thread.sleep(8)
        }
    }

    private fun adapterScript(): String {
        adapterCache?.let { return it }
        val text = context.assets.open(ADAPTER_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        adapterCache = text
        return text
    }

    private fun JSONArray.toStringList(): List<String> =
        List(length()) { optString(it) }.filter { it.isNotBlank() }
}
