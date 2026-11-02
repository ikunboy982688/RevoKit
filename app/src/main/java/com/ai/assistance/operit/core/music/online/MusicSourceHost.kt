package com.ai.assistance.operit.core.music.online

import com.ai.assistance.operit.util.AppLogger
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * 暴露给音源脚本的本地能力（JS 里通过 `NativeInterface.httpRequest(...)` 调用）。
 *
 * 关键设计：**这些方法全是同步阻塞的**。
 * QuickJS 的 `callFunction` 是在运行线程上同步执行的，而 JS 那边的 `axios.get()` 又需要
 * 立刻拿到数据才能继续往下跑。做成异步回调要绕一大圈（回调队列 + 忙等），做成同步最省事，
 * 代价是联网期间 JS 线程被占住 —— 搜个歌几百毫秒到几秒，可以接受。
 */
class MusicSourceHost {

    @Volatile var lastError: String? = null

    // ------------------------------------------------------------------ 总预算闸门
    //
    // 为什么必须有这道闸：音源脚本内部是**串行试好几个第三方后端**的（试一个 8 秒、
    // 试四个就是半分多钟）。而 Kotlin 的 withTimeoutOrNull 掐不住同步的 JS 调用 ——
    // 计时到了它照样卡在 native 调用里不回来，外层只能干等，用户那边就是「点了没反应」。
    //
    // 所以在**脚本发 HTTP 的这一层**设总预算：超预算的请求直接返回失败。脚本看到的
    // 是「后端挂了」，它的串行重试会在几十毫秒内全部走完、整条 Promise 快速 reject，
    // 于是我们能在一个可预期的短时间内拿到失败结果，换下一个候选。
    // 这比试图强行中断 QuickJS 安全得多（中断一个正卡在 native 调用里的 runtime 会炸）。

    /** 预算到期的绝对时刻（epoch ms）。0 = 不限制 */
    @Volatile private var deadlineEpochMs: Long = 0L

    /** 一次动作最多允许发多少个请求，防止脚本无限重试 */
    @Volatile private var maxCalls: Int = Int.MAX_VALUE

    private val callCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** 开一次动作的总预算。[MusicSourceEngine] 在跑动作前调用 */
    fun beginBudget(budgetMs: Long, limit: Int = 10) {
        deadlineEpochMs = if (budgetMs <= 0L) 0L else System.currentTimeMillis() + budgetMs
        maxCalls = limit
        callCount.set(0)
    }

    fun endBudget() {
        deadlineEpochMs = 0L
        maxCalls = Int.MAX_VALUE
        callCount.set(0)
    }

    /**
     * 执行一次 HTTP 请求。
     *
     * 入参是 JSON：`{url, method, headers:{}, body, timeout, responseType}`
     * 返回也是 JSON：`{status, headers:{}, body}`
     * 网络层异常不抛出，统一塞进 `{status:0, body:"", error:"..."}`，让 JS 侧自己判断，
     * 否则 QuickJS 会直接抛 Java 异常、整个脚本挂掉。
     */
    fun httpRequest(optionsJson: String?): String {
        // 总预算已耗尽 / 请求次数超上限 → 立刻失败，不再真的发起联网
        if (deadlineEpochMs > 0L && System.currentTimeMillis() > deadlineEpochMs) {
            return errorResult("音源取链超时（已超出本次总预算）")
        }
        if (callCount.incrementAndGet() > maxCalls) {
            return errorResult("音源请求次数超出上限（$maxCalls 次）")
        }
        // 单次超时也要按剩余预算收紧：别让最后一个请求自己独占 8 秒，
        // 把整条链路拖过预算线
        val slack =
            if (deadlineEpochMs > 0L) (deadlineEpochMs - System.currentTimeMillis()) else 8_000L

        val result = JSONObject()
        var conn: HttpURLConnection? = null
        try {
            val options = JSONObject(optionsJson ?: "{}")
            val urlStr = options.optString("url")
            if (urlStr.isBlank()) {
                return errorResult("url 为空")
            }
            val method = options.optString("method", "GET").uppercase()
            // 上限从 60 秒压到 8 秒：脚本内部是串行试好几个后端的，一次 15 秒、
            // 试四个就是一分钟，而 Kotlin 的 withTimeoutOrNull 掐不住同步的 JS 调用，
            // 只能等它自己走完 —— 用户那边就是「点了没反应」。现在音源已经是兜底位，
            // 让它快速失败、早点让位给下一个候选，比陪它耗着强。
            val timeout = options.optInt("timeout", 8000).coerceIn(3000, 8000).coerceAtMost(
                slack.toInt().coerceAtLeast(1000)
            )

            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeout
                readTimeout = timeout
                instanceFollowRedirects = true
                useCaches = false
                // 有些接口不带 UA 直接 403
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept-Encoding", "gzip")
            }

            options.optJSONObject("headers")?.let { headers ->
                headers.keys().forEach { key ->
                    val value = headers.optString(key)
                    if (key.isNotBlank() && value.isNotBlank()) {
                        conn.setRequestProperty(key, value)
                    }
                }
            }

            val body = options.optString("body").takeIf { it.isNotBlank() && it != "null" }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val status = conn.responseCode
            val stream =
                runCatching {
                        if (status in 200..299) conn.inputStream else conn.errorStream
                    }
                    .getOrNull()

            val rawBytes =
                stream?.use { input ->
                    val source =
                        if (conn.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                            runCatching { GZIPInputStream(input) }.getOrDefault(input)
                        } else {
                            input
                        }
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        total += read
                        // 兜个上限，避免某个接口甩过来一个几百兆的东西把内存吃光
                        if (total > 8 * 1024 * 1024) break
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                } ?: ByteArray(0)

            val charset = parseCharset(conn.contentType) ?: "UTF-8"
            val text = runCatching { String(rawBytes, charset(charset)) }
                .getOrElse { String(rawBytes, Charsets.UTF_8) }

            val headers = JSONObject()
            conn.headerFields?.forEach { (key, values) ->
                if (!key.isNullOrBlank() && values.isNotEmpty()) {
                    headers.put(key, values.first())
                }
            }

            result.put("status", status)
            result.put("headers", headers)
            result.put("body", text)
            return result.toString()
        } catch (e: Exception) {
            lastError = e.message
            AppLogger.w(TAG, "音源 HTTP 请求失败: ${e.message}")
            return errorResult(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 脚本里的 console.log 落到这里，方便排查插件问题 */
    fun log(message: String?): String {
        if (!message.isNullOrBlank()) {
            AppLogger.d(TAG, "[音源] $message")
        }
        return ""
    }

    /**
     * CryptoJS.js 那套桥接脚本要求宿主提供 `crypto(algorithm, operation, argsJson)`。
     *
     * 这里只实现音乐插件真正会用到的几种：md5 / sha1 / sha256 / hmac-sha1 / hmac-md5。
     * AES 的加密模式各家插件写法差别太大（ECB/CBC/自定义 IV），没做 —— 需要 AES 的插件
     * 会在结果里看到明确的错误，而不是默默返回空。
     */
    fun crypto(algorithm: String?, operation: String?, argsJson: String?): String {
        return try {
            val args = parseStringArgs(argsJson)
            val input = args.getOrNull(0).orEmpty()
            when (algorithm?.lowercase()) {
                "md5" -> md5(input)
                "sha1" -> hexDigest("SHA-1", input)
                "sha256" -> hexDigest("SHA-256", input)
                "hmac-sha1" -> hmac("HmacSHA1", args.getOrElse(1) { "" }, input)
                "hmac-md5" -> hmac("HmacMD5", args.getOrElse(1) { "" }, input)
                else -> """{"nativeError":"不支持的算法: $algorithm"}"""
            }
        } catch (e: Exception) {
            """{"nativeError":"${(e.message ?: "crypto failed").replace("\"", "'")}"}"""
        }
    }

    // ------------------------------------------------------------------ AES

    /**
     * 洛雪脚本的 `lx.utils.crypto.aesEncrypt / aesDecrypt` 落到这里。
     *
     * 入参 JSON：`{data, dataEnc, key, keyEnc, iv, ivEnc, mode, op}`
     * - `dataEnc` / `keyEnc` / `ivEnc` 取值 `utf8`（默认）/ `hex` / `base64`
     * - `mode` 认 `ecb` / `cbc`，也认 `aes-128-ecb` 这种全名（只取最后一段）
     * - `op` 是 `encrypt` / `decrypt`
     *
     * 返回 JSON：`{ok:true, hex:"..."}` —— **统一给 hex**，JS 侧自己转成字节数组，
     * 这样不用在桥上处理二进制。
     */
    fun aesCrypt(argsJson: String?): String {
        return try {
            val args = JSONObject(argsJson ?: "{}")
            val modeRaw = args.optString("mode", "cbc").lowercase()
            val mode = if (modeRaw.contains("cbc")) "CBC" else "ECB"
            val encrypt = args.optString("op", "encrypt").lowercase() != "decrypt"

            val data = decodeBytes(args.optString("data"), args.optString("dataEnc", "utf8"))
            val key = normalizeKey(decodeBytes(args.optString("key"), args.optString("keyEnc", "utf8")))
            val iv =
                if (mode == "CBC") {
                    normalizeIv(decodeBytes(args.optString("iv"), args.optString("ivEnc", "utf8")))
                } else {
                    null
                }

            val cipher = Cipher.getInstance("AES/$mode/PKCS5Padding")
            val keySpec = SecretKeySpec(key, "AES")
            if (iv != null) {
                cipher.init(
                    if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE,
                    keySpec,
                    javax.crypto.spec.IvParameterSpec(iv)
                )
            } else {
                cipher.init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, keySpec)
            }
            val out = cipher.doFinal(data)
            JSONObject().put("ok", true).put("hex", out.joinToString("") { "%02x".format(it) }).toString()
        } catch (e: Exception) {
            JSONObject()
                .put("ok", false)
                .put("error", (e.message ?: e.javaClass.simpleName).replace("\"", "'"))
                .toString()
        }
    }

    private fun decodeBytes(value: String, encoding: String): ByteArray =
        when (encoding.lowercase()) {
            "hex" -> value.chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
            "base64" -> android.util.Base64.decode(value, android.util.Base64.DEFAULT)
            else -> value.toByteArray(Charsets.UTF_8)
        }

    /** AES 只接受 16/24/32 字节密钥；长度不对就补零或截断，别直接抛异常把脚本搞死 */
    private fun normalizeKey(raw: ByteArray): ByteArray {
        if (raw.size == 16 || raw.size == 24 || raw.size == 32) return raw
        val size = when {
            raw.size < 16 -> 16
            raw.size < 24 -> 24
            else -> 32
        }
        return raw.copyOf(size)
    }

    private fun normalizeIv(raw: ByteArray): ByteArray = raw.copyOf(16)

    // ------------------------------------------------------------------ 内部

    private fun errorResult(message: String): String =
        JSONObject()
            .put("status", 0)
            .put("headers", JSONObject())
            .put("body", "")
            .put("error", message)
            .toString()

    private fun parseCharset(contentType: String?): String? {
        val ct = contentType ?: return null
        val idx = ct.lowercase().indexOf("charset=")
        if (idx < 0) return null
        return ct.substring(idx + 8).substringBefore(';').trim().trim('"').takeIf { it.isNotBlank() }
    }

    private fun charset(name: String): java.nio.charset.Charset =
        runCatching { java.nio.charset.Charset.forName(name) }.getOrDefault(Charsets.UTF_8)

    private fun parseStringArgs(argsJson: String?): List<String> {
        if (argsJson.isNullOrBlank()) return emptyList()
        val arr = org.json.JSONArray(argsJson)
        return List(arr.length()) { i ->
            when (val v = arr.opt(i)) {
                null, JSONObject.NULL -> ""
                is String -> v
                else -> v.toString()
            }
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hexDigest(algorithm: String, input: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmac(algorithm: String, key: String, input: String): String {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm))
        return mac.doFinal(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "MusicSourceHost"
    }
}
