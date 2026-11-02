/*
 * 逆向工坊 · 在线音源适配层
 * ---------------------------------------------------------------------------
 * 把两种第三方音源协议翻译成宿主能直接调用的形状：
 *
 *   1. MusicFree 插件：CommonJS（module.exports / require('axios') ...），能搜索、能取地址、能取歌词
 *   2. 洛雪音源：globalThis.lx（on / send / request），只能按歌曲信息取播放地址
 *
 * 宿主（Kotlin）只需要三步：
 *   evaluate(本文件)                     —— 装好翻译层
 *   callFunction("__ms_load", [方言, 脚本全文])  —— 载入一个音源
 *   callFunction("__ms_start", [动作, 参数JSON]) + 反复 callFunction("__ms_take")
 *
 * 之所以搞「start / take」两段式：QuickJS 的 callFunction 是同步返回的，
 * 而插件接口全是 async/Promise。宿主每次 callFunction 之后会自动跑一轮
 * pending jobs（微任务），所以真正的异步结果在 take 阶段就能读到。
 * 又因为宿主把 HTTP 做成了同步阻塞桥，绝大多数插件的 Promise 在第一轮就结算完了。
 */
(function () {
  'use strict'

  var root = typeof globalThis !== 'undefined' ? globalThis : this
  var HOST = root.NativeInterface || {}

  // ============================================================ 状态

  var state = {
    dialect: '',
    name: '',
    version: '',
    author: '',
    platform: '',
    plugin: null,
    lxHandler: null,
    lxSources: null,
    lxVersion: '',
    pending: null,
    lastError: ''
  }

  // ============================================================ 字节 / 编码工具
  // QuickJS 没有 Node 的 Buffer、也没有 atob/btoa，插件又普遍要用，这里自己实现。

  var HEX = '0123456789abcdef'

  function bytesToHex(bytes) {
    var out = ''
    for (var i = 0; i < bytes.length; i++) {
      out += HEX.charAt((bytes[i] >> 4) & 15) + HEX.charAt(bytes[i] & 15)
    }
    return out
  }

  function hexToBytes(hex) {
    var s = String(hex || '')
    var out = []
    for (var i = 0; i < s.length; i += 2) {
      var v = parseInt(s.substr(i, 2), 16)
      if (isNaN(v)) return []
      out.push(v)
    }
    return out
  }

  function utf8Encode(str) {
    var s = String(str == null ? '' : str)
    var out = []
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i)
      if (c < 0x80) {
        out.push(c)
      } else if (c < 0x800) {
        out.push(0xc0 | (c >> 6), 0x80 | (c & 63))
      } else if (c >= 0xd800 && c <= 0xdbff && i + 1 < s.length) {
        var c2 = s.charCodeAt(i + 1)
        if (c2 >= 0xdc00 && c2 <= 0xdfff) {
          var cp = 0x10000 + ((c - 0xd800) << 10) + (c2 - 0xdc00)
          i++
          out.push(0xf0 | (cp >> 18), 0x80 | ((cp >> 12) & 63), 0x80 | ((cp >> 6) & 63), 0x80 | (cp & 63))
        } else {
          out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63))
        }
      } else {
        out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63))
      }
    }
    return out
  }

  function utf8Decode(bytes) {
    var out = ''
    var i = 0
    while (i < bytes.length) {
      var b = bytes[i++]
      if (b < 0x80) {
        out += String.fromCharCode(b)
      } else if (b < 0xe0) {
        out += String.fromCharCode(((b & 31) << 6) | (bytes[i++] & 63))
      } else if (b < 0xf0) {
        out += String.fromCharCode(((b & 15) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63))
      } else {
        var cp = ((b & 7) << 18) | ((bytes[i++] & 63) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63)
        cp -= 0x10000
        out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 1023))
      }
    }
    return out
  }

  var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

  function bytesToBase64(bytes) {
    var out = ''
    for (var i = 0; i < bytes.length; i += 3) {
      var b0 = bytes[i]
      var b1 = i + 1 < bytes.length ? bytes[i + 1] : -1
      var b2 = i + 2 < bytes.length ? bytes[i + 2] : -1
      out += B64.charAt(b0 >> 2)
      out += B64.charAt(((b0 & 3) << 4) | (b1 < 0 ? 0 : b1 >> 4))
      out += b1 < 0 ? '=' : B64.charAt(((b1 & 15) << 2) | (b2 < 0 ? 0 : b2 >> 6))
      out += b2 < 0 ? '=' : B64.charAt(b2 & 63)
    }
    return out
  }

  function base64ToBytes(b64) {
    var s = String(b64 || '').replace(/[^A-Za-z0-9+/=]/g, '')
    var out = []
    for (var i = 0; i < s.length; i += 4) {
      var c0 = B64.indexOf(s.charAt(i))
      var c1 = B64.indexOf(s.charAt(i + 1))
      var c2 = s.charAt(i + 2) === '=' ? -1 : B64.indexOf(s.charAt(i + 2))
      var c3 = s.charAt(i + 3) === '=' ? -1 : B64.indexOf(s.charAt(i + 3))
      if (c0 < 0 || c1 < 0) break
      out.push((c0 << 2) | (c1 >> 4))
      if (c2 >= 0) out.push(((c1 & 15) << 4) | (c2 >> 2))
      if (c3 >= 0) out.push(((c2 & 3) << 6) | c3)
    }
    return out
  }

  function btoa(str) { return bytesToBase64(utf8Encode(str)) }
  function atob(str) { return utf8Decode(base64ToBytes(str)) }

  /** 把 Unicode 码点还原成 JS 字符串（HTML 实体解码要用） */
  function codePointToString(code) {
    if (code <= 0xffff) return String.fromCharCode(code)
    var offset = code - 0x10000
    return String.fromCharCode(0xd800 + (offset >> 10), 0xdc00 + (offset & 1023))
  }

  // ---------------------------------------------------------- 字节容器
  // 洛雪脚本拿到的 buffer 是「字节」，后面会 buf2hex / 转 base64，所以统一给 Uint8Array

  function toU8(list) {
    if (list instanceof Uint8Array) return list
    if (list && list.buffer && typeof list.byteLength === 'number') {
      return new Uint8Array(list.buffer, list.byteOffset || 0, list.byteLength)
    }
    if (Array.isArray(list)) return new Uint8Array(list)
    return new Uint8Array(0)
  }

  function toBytes(input, encoding) {
    if (input === null || input === undefined) return new Uint8Array(0)
    if (typeof input === 'string') {
      var enc = String(encoding || 'utf8').toLowerCase().replace('-', '')
      if (enc === 'hex') return toU8(hexToBytes(input))
      if (enc === 'base64') return toU8(base64ToBytes(input))
      if (enc === 'latin1' || enc === 'binary') {
        var raw = []
        for (var i = 0; i < input.length; i++) raw.push(input.charCodeAt(i) & 255)
        return toU8(raw)
      }
      return toU8(utf8Encode(input))
    }
    if (input instanceof Uint8Array) return input
    if (Array.isArray(input)) return toU8(input)
    return toU8(utf8Encode(String(input)))
  }

  function bytesToString(bytes, encoding) {
    var arr = toU8(bytes)
    var list = []
    for (var i = 0; i < arr.length; i++) list.push(arr[i])
    var enc = String(encoding || 'utf8').toLowerCase().replace('-', '')
    if (enc === 'base64') return bytesToBase64(list)
    if (enc === 'hex') return bytesToHex(list)
    if (enc === 'latin1' || enc === 'binary') {
      var out = ''
      for (var j = 0; j < list.length; j++) out += String.fromCharCode(list[j])
      return out
    }
    return utf8Decode(list)
  }

  /**
   * AES 走宿主实现（javax.crypto）：QuickJS 里手写 AES 又慢又容易错。
   * 入参统一转 hex 交给宿主，拿回 hex 再变回字节 —— 桥上不传二进制。
   */
  function aesCrypt(op, data, mode, key, iv) {
    var dataIsText = typeof data === 'string'
    var payload = {
      op: op,
      mode: String(mode === undefined || mode === null ? 'cbc' : mode),
      data: dataIsText ? String(data) : bytesToHex(toArray(data)),
      dataEnc: dataIsText ? 'utf8' : 'hex',
      key: bytesToHex(toArray(key)),
      keyEnc: 'hex',
      iv: iv === undefined || iv === null || iv === '' ? '' : bytesToHex(toArray(iv)),
      ivEnc: 'hex'
    }
    if (typeof HOST.aesCrypt !== 'function') {
      throw new Error('当前版本的宿主还没提供 AES 能力')
    }
    var res
    try {
      res = JSON.parse(String(HOST.aesCrypt(JSON.stringify(payload)) || '{}'))
    } catch (e) {
      throw new Error('AES 桥接异常: ' + messageOf(e))
    }
    if (!res || !res.ok) throw new Error('AES 失败: ' + ((res && res.error) || '未知错误'))
    return toU8(hexToBytes(res.hex))
  }

  function toArray(bytes) {
    var arr = toU8(bytes)
    var list = []
    for (var i = 0; i < arr.length; i++) list.push(arr[i])
    return list
  }

  // ============================================================ HTTP 桥

  function hostHttp(options) {
    var raw
    try {
      raw = String(HOST.httpRequest(JSON.stringify(options)) || '{}')
    } catch (e) {
      return { status: 0, headers: {}, body: '', error: 'HTTP 桥接异常: ' + messageOf(e) }
    }
    try {
      var parsed = JSON.parse(raw)
      if (!parsed || typeof parsed !== 'object') {
        return { status: 0, headers: {}, body: '', error: 'HTTP 桥接返回异常' }
      }
      return parsed
    } catch (e2) {
      return { status: 0, headers: {}, body: '', error: 'HTTP 响应无法解析' }
    }
  }

  function messageOf(e) {
    if (!e) return '未知错误'
    if (typeof e === 'string') return e
    if (e.message) return String(e.message)
    return String(e)
  }

  function appendQuery(url, params) {
    if (!params) return url
    var parts = []
    Object.keys(params).forEach(function (key) {
      var value = params[key]
      if (value === undefined || value === null) return
      if (Array.isArray(value)) {
        value.forEach(function (v) {
          parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(v)))
        })
      } else {
        parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(value)))
      }
    })
    if (!parts.length) return url
    return url + (url.indexOf('?') >= 0 ? '&' : '?') + parts.join('&')
  }

  function headerValue(headers, name) {
    if (!headers) return ''
    var target = String(name).toLowerCase()
    var keys = Object.keys(headers)
    for (var i = 0; i < keys.length; i++) {
      if (String(keys[i]).toLowerCase() === target) return String(headers[keys[i]])
    }
    return ''
  }

  function parseBodyText(body, headers) {
    var text = body === undefined || body === null ? '' : String(body)
    var contentType = headerValue(headers, 'content-type')
    if (/json/i.test(contentType)) {
      try { return JSON.parse(text) } catch (e) { return text }
    }
    var trimmed = text.trim()
    if (trimmed.charAt(0) === '{' || trimmed.charAt(0) === '[') {
      try { return JSON.parse(trimmed) } catch (e) { return text }
    }
    return text
  }

  function normalizeHeaders(raw) {
    var headers = {}
    if (!raw) return headers
    Object.keys(raw).forEach(function (key) {
      var value = raw[key]
      // axios 的 headers 容器里会混着 common/get/post 这类对象，跳过
      if (value === undefined || value === null || typeof value === 'object') return
      headers[key] = String(value)
    })
    return headers
  }

  // ============================================================ axios 兼容层

  function axiosRequest(config) {
    config = config || {}
    var method = String(config.method || 'GET').toUpperCase()
    var url = String(config.url || '')
    if (config.baseURL && url && !/^[a-z]+:\/\//i.test(url)) {
      url = String(config.baseURL).replace(/\/+$/, '') + '/' + url.replace(/^\/+/, '')
    }
    url = appendQuery(url, config.params)

    var headers = normalizeHeaders(config.headers)
    var data = config.data
    var bodyText = ''
    if (data !== undefined && data !== null) {
      if (typeof data === 'string') {
        bodyText = data
      } else {
        bodyText = JSON.stringify(data)
        if (!headerValue(headers, 'content-type')) headers['Content-Type'] = 'application/json'
      }
    }

    var startedAt = Date.now()
    var res = hostHttp({
      url: url,
      method: method,
      headers: headers,
      body: bodyText,
      timeout: config.timeout || 15000
    })

    var status = Number(res.status || 0) || 0
    var response = {
      data: parseBodyText(res.body, res.headers),
      status: status,
      statusText: res.error ? String(res.error) : (status === 200 ? 'OK' : ''),
      headers: res.headers || {},
      config: config,
      request: {},
      costMs: Date.now() - startedAt
    }

    var validate = config.validateStatus
    var accepted = typeof validate === 'function' ? !!validate(status) : (status >= 200 && status < 300)
    if (accepted) return Promise.resolve(response)

    var err = new Error(
      res.error ? String(res.error) : '请求失败（HTTP ' + status + '）'
    )
    err.response = response
    err.config = config
    err.isAxiosError = true
    return Promise.reject(err)
  }

  function axios(configOrUrl, maybeConfig) {
    if (typeof configOrUrl === 'string') {
      var merged = {}
      if (maybeConfig) Object.keys(maybeConfig).forEach(function (k) { merged[k] = maybeConfig[k] })
      merged.url = configOrUrl
      return axiosRequest(merged)
    }
    return axiosRequest(configOrUrl)
  }

  axios.request = axiosRequest

  ;['get', 'delete', 'head', 'options'].forEach(function (method) {
    axios[method] = function (url, config) {
      var merged = {}
      if (config) Object.keys(config).forEach(function (k) { merged[k] = config[k] })
      merged.url = url
      merged.method = method.toUpperCase()
      return axiosRequest(merged)
    }
  })

  ;['post', 'put', 'patch'].forEach(function (method) {
    axios[method] = function (url, data, config) {
      var merged = {}
      if (config) Object.keys(config).forEach(function (k) { merged[k] = config[k] })
      merged.url = url
      merged.data = data
      merged.method = method.toUpperCase()
      return axiosRequest(merged)
    }
  })

  axios.create = function (defaults) {
    var base = defaults || {}
    var instance = function (configOrUrl, maybeConfig) {
      var merged = {}
      Object.keys(base).forEach(function (k) { merged[k] = base[k] })
      if (typeof configOrUrl === 'string') {
        if (maybeConfig) Object.keys(maybeConfig).forEach(function (k) { merged[k] = maybeConfig[k] })
        merged.url = configOrUrl
      } else if (configOrUrl) {
        Object.keys(configOrUrl).forEach(function (k) { merged[k] = configOrUrl[k] })
      }
      return axiosRequest(merged)
    }
    instance.request = function (config) {
      var merged = {}
      Object.keys(base).forEach(function (k) { merged[k] = base[k] })
      if (config) Object.keys(config).forEach(function (k) { merged[k] = config[k] })
      return axiosRequest(merged)
    }
    ;['get', 'delete', 'head', 'options'].forEach(function (method) {
      instance[method] = function (url, config) {
        var merged = {}
        Object.keys(base).forEach(function (k) { merged[k] = base[k] })
        if (config) Object.keys(config).forEach(function (k) { merged[k] = config[k] })
        merged.url = url
        merged.method = method.toUpperCase()
        return axiosRequest(merged)
      }
    })
    ;['post', 'put', 'patch'].forEach(function (method) {
      instance[method] = function (url, data, config) {
        var merged = {}
        Object.keys(base).forEach(function (k) { merged[k] = base[k] })
        if (config) Object.keys(config).forEach(function (k) { merged[k] = config[k] })
        merged.url = url
        merged.data = data
        merged.method = method.toUpperCase()
        return axiosRequest(merged)
      }
    })
    instance.defaults = base
    instance.interceptors = {
      request: { use: function () {} },
      response: { use: function () {} }
    }
    return instance
  }

  axios.defaults = { headers: { common: {} } }
  axios.interceptors = {
    request: { use: function () {} },
    response: { use: function () {} }
  }
  axios.isAxiosError = function (e) { return !!(e && e.isAxiosError) }

  // ============================================================ crypto-js 兼容层

  function hostDigest(algorithm, args) {
    var out
    try {
      out = String(HOST.crypto(algorithm, 'digest', JSON.stringify(args)) || '')
    } catch (e) {
      throw new Error('哈希计算失败: ' + messageOf(e))
    }
    if (out.charAt(0) === '{') {
      var parsed = null
      try { parsed = JSON.parse(out) } catch (e2) { parsed = null }
      throw new Error((parsed && parsed.nativeError) || '不支持的哈希算法: ' + algorithm)
    }
    return out
  }

  var encUtf8 = {
    parse: function (str) { return makeWord(utf8Encode(str)) },
    stringify: function (word) { return word == null ? '' : utf8Decode(word.__bytes) }
  }
  var encHex = {
    parse: function (str) { return makeWord(hexToBytes(str)) },
    stringify: function (word) { return word == null ? '' : word.__hex }
  }
  var encLatin1 = encUtf8
  var encBase64 = {
    parse: function (str) { return makeWord(base64ToBytes(str)) },
    stringify: function (word) { return word == null ? '' : bytesToBase64(word.__bytes) }
  }

  function makeWord(bytes) {
    var word = {
      __bytes: bytes,
      sigBytes: bytes.length,
      __hex: bytesToHex(bytes)
    }
    word.toString = function (encoder) {
      if (!encoder || encoder === encHex) return word.__hex
      if (encoder === encBase64) return bytesToBase64(word.__bytes)
      if (encoder === encUtf8) return utf8Decode(word.__bytes)
      return word.__hex
    }
    word.concat = function (other) {
      var merged = word.__bytes.concat(other && other.__bytes ? other.__bytes : [])
      var next = makeWord(merged)
      word.__bytes = next.__bytes
      word.sigBytes = next.sigBytes
      word.__hex = next.__hex
      return word
    }
    word.clamp = function () { return word }
    return word
  }

  function fromWord(word) {
    if (word == null) return ''
    if (typeof word === 'string') return word
    if (word.__hex !== undefined) return utf8Decode(word.__bytes)
    return String(word)
  }

  function digestFactory(algorithm) {
    return function (input, key) {
      var text = fromWord(input)
      var args = key === undefined ? [text] : [text, fromWord(key)]
      var hex = hostDigest(algorithm, args)
      return makeWord(hexToBytes(hex))
    }
  }

  var CryptoJS = {
    MD5: digestFactory('md5'),
    SHA1: digestFactory('sha1'),
    SHA256: digestFactory('sha256'),
    HmacMD5: digestFactory('hmac-md5'),
    HmacSHA1: digestFactory('hmac-sha1'),
    enc: { Utf8: encUtf8, Hex: encHex, Base64: encBase64, Latin1: encLatin1 },
    lib: { WordArray: { create: function (init) { return makeWord((init && init.words) || []) } } },
    AES: {
      encrypt: function () { throw new Error('当前音源引擎不支持 AES 加密，这个插件用到了它') },
      decrypt: function () { throw new Error('当前音源引擎不支持 AES 解密，这个插件用到了它') }
    },
    pad: { Pkcs7: {} },
    mode: { ECB: {}, CBC: {} },
    algo: {}
  }

  // ============================================================ 其它小依赖

  var qs = {
    stringify: function (obj, options) {
      var opts = options || {}
      var parts = []
      var walk = function (prefix, value) {
        if (value === undefined || value === null) return
        if (Array.isArray(value)) {
          value.forEach(function (item) {
            if (opts.arrayFormat === 'brackets') walk(prefix + '[]', item)
            else walk(prefix, item)
          })
          return
        }
        if (typeof value === 'object') {
          Object.keys(value).forEach(function (key) {
            walk(prefix + '[' + key + ']', value[key])
          })
          return
        }
        parts.push(encodeURIComponent(prefix) + '=' + encodeURIComponent(String(value)))
      }
      Object.keys(obj || {}).forEach(function (key) { walk(key, obj[key]) })
      return parts.join('&')
    },
    parse: function (text) {
      var out = {}
      String(text || '').split('&').forEach(function (pair) {
        if (!pair) return
        var index = pair.indexOf('=')
        var key = index < 0 ? pair : pair.substring(0, index)
        var value = index < 0 ? '' : pair.substring(index + 1)
        try { out[decodeURIComponent(key)] = decodeURIComponent(value) } catch (e) { out[key] = value }
      })
      return out
    }
  }

  var HE_MAP = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' }
  var he = {
    decode: function (text) {
      return String(text == null ? '' : text).replace(/&(#x?[0-9a-fA-F]+|[a-zA-Z]+);/g, function (all, body) {
        if (body.charAt(0) === '#') {
          var code = body.charAt(1).toLowerCase() === 'x'
            ? parseInt(body.substring(2), 16)
            : parseInt(body.substring(1), 10)
          if (!isNaN(code) && code >= 0 && code <= 0x10ffff) return codePointToString(code)
          return all
        }
        var mapped = HE_MAP[body.toLowerCase()]
        return mapped === undefined ? all : mapped
      })
    },
    encode: function (text) {
      return String(text == null ? '' : text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
    }
  }

  // Node 的 Buffer 很常用，这里给个够用的子集
  function BufferLike(bytes) { this.__bytes = bytes }
  BufferLike.prototype.toString = function (encoding) {
    var enc = String(encoding || 'utf8').toLowerCase()
    if (enc === 'base64') return bytesToBase64(this.__bytes)
    if (enc === 'hex') return bytesToHex(this.__bytes)
    return utf8Decode(this.__bytes)
  }
  BufferLike.prototype.toJSON = function () { return { type: 'Buffer', data: this.__bytes } }
  var Buffer = {
    from: function (input, encoding) {
      if (input instanceof BufferLike) return new BufferLike(input.__bytes.slice())
      var enc = String(encoding || 'utf8').toLowerCase()
      if (Array.isArray(input)) return new BufferLike(input.map(function (v) { return v & 255 }))
      if (enc === 'base64') return new BufferLike(base64ToBytes(String(input)))
      if (enc === 'hex') return new BufferLike(hexToBytes(String(input)))
      return new BufferLike(utf8Encode(String(input)))
    },
    alloc: function (size) { return new BufferLike(new Array(size || 0)) },
    isBuffer: function (value) { return value instanceof BufferLike },
    concat: function (list) {
      var merged = []
      ;(list || []).forEach(function (item) { merged = merged.concat(item.__bytes || []) })
      return new BufferLike(merged)
    }
  }

  function nodeCrypto() {
    function Hasher(algorithm) {
      this.algorithm = algorithm
      this.parts = []
      this.encoding = 'utf8'
    }
    Hasher.prototype.update = function (value, encoding) {
      this.parts.push({ value: value, encoding: encoding || 'utf8' })
      return this
    }
    Hasher.prototype.digest = function (encoding) {
      var text = ''
      this.parts.forEach(function (part) {
        if (part.value instanceof BufferLike) text += part.value.toString('utf8')
        else text += String(part.value)
      })
      var hex = hostDigest(this.algorithm, [text])
      if (String(encoding).toLowerCase() === 'hex') return hex
      return new BufferLike(hexToBytes(hex))
    }
    return {
      createHash: function (algorithm) { return new Hasher(algorithm) },
      createHmac: function (algorithm, key) {
        var hasher = new Hasher('hmac-' + algorithm)
        hasher.key = key
        var original = hasher.digest
        hasher.digest = function (encoding) {
          var text = ''
          this.parts.forEach(function (part) { text += String(part.value) })
          var hex = hostDigest(this.algorithm, [text, String(key)])
          if (String(encoding).toLowerCase() === 'hex') return hex
          return new BufferLike(hexToBytes(hex))
        }
        return hasher
      },
      randomBytes: function (size) {
        var bytes = []
        for (var i = 0; i < (size || 0); i++) bytes.push(Math.floor(Math.random() * 256) & 255)
        return new BufferLike(bytes)
      }
    }
  }

  function makeRequire() {
    return function require(name) {
      var key = String(name || '').toLowerCase()
      if (key === 'axios' || key === 'axios/dist/axios' || key === 'axios/lib/axios') return axios
      if (key === 'crypto-js' || key === 'cryptojs') return CryptoJS
      if (key === 'qs' || key === 'query-string') return qs
      if (key === 'he') return he
      if (key === 'crypto') return nodeCrypto()
      if (key === 'buffer') return { Buffer: Buffer }
      if (key === 'url') return { URLSearchParams: function () {} }
      throw new Error(
        '音源需要 "' + name + '" 模块，当前引擎还没实现它。' +
        '常见原因：这个插件依赖 cheerio 做 HTML 解析或依赖 dayjs/big-integer 之类的大库。'
      )
    }
  }

  // ============================================================ 载入 MusicFree 插件

  function loadMusicFree(code) {
    var module = { exports: {} }
    var exports = module.exports
    var require = makeRequire()
    var sandboxConsole = makeConsole()

    // 这些包在插件的调用点写法是 `const axios = require('axios')`。
    // 如果作为 function 参数传进去，插件里的 const 就会和参数同名 —— 直接 SyntaxError
    // （"Identifier 'axios' has already been declared"）。所以统一挂到全局：
    // 函数作用域里的 const 会正常遮蔽全局，不冲突。
    root.Buffer = root.Buffer || Buffer
    root.btoa = root.btoa || btoa
    root.atob = root.atob || atob
    root.CryptoJS = root.CryptoJS || CryptoJS
    root.process =
      root.process ||
      Object.assign(
        { env: {}, platform: 'android', version: 'v18.0.0', browser: false },
        { nextTick: function (fn) { Promise.resolve().then(fn) } }
      )

    // 同洛雪那条：保留 "use strict"，只把 this 绑到 root（插件是给浏览器/Node 写的，
    // webpack 打包产物里到处是顶层 this）。不加 strict 的话插件的隐式赋值会污染宿主全局。
    var factory = new Function('module', 'exports', 'require', 'console', '"use strict";\n' + code)
    factory.call(root, module, exports, require, sandboxConsole)

    var plugin = module.exports
    // webpack/babel 打包过的插件常把真正的对象挂在 default 上
    if (plugin && typeof plugin === 'object' && plugin.default && typeof plugin.default === 'object' &&
        typeof plugin.search !== 'function' && typeof plugin.getMediaSource !== 'function') {
      plugin = plugin.default
    }
    if (!plugin || typeof plugin !== 'object') {
      throw new Error('插件没有导出任何内容（module.exports 为空）')
    }
    if (typeof plugin.search !== 'function' && typeof plugin.getMediaSource !== 'function') {
      throw new Error('插件的 module.exports 里既没有 search 也没有 getMediaSource')
    }
    state.plugin = plugin
    state.dialect = 'MUSICFREE'
    state.name = String(plugin.platform || plugin.name || '未命名插件')
    state.version = String(plugin.version || '')
    state.author = String(plugin.author || '')
    state.platform = String(plugin.platform || '')
  }

  // ============================================================ 载入洛雪音源

  function loadLx(code) {
    var listeners = {}
    var inited = null

    root.lx = {
      EVENT_NAMES: { request: 'request', inited: 'inited', updateAlert: 'updateAlert' },
      env: 'desktop',
      version: '2.0.0',
      request: function (url, options, callback) {
        var opts = options || {}
        var method = String(opts.method || 'GET').toUpperCase()
        var headers = opts.headers || {}
        var body = opts.body
        var bodyText = ''
        // 洛雪脚本里 post 常写成 {form:{...}}，等价于 urlencoded body
        if (opts.form) {
          var pairs = []
          Object.keys(opts.form).forEach(function (key) {
            pairs.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(opts.form[key])))
          })
          bodyText = pairs.join('&')
          if (!headerValue(headers, 'content-type')) {
            headers = Object.assign({}, headers, {
              'Content-Type': 'application/x-www-form-urlencoded'
            })
          }
        } else if (opts.formData) {
          var parts = []
          Object.keys(opts.formData).forEach(function (key) {
            parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(opts.formData[key])))
          })
          bodyText = parts.join('&')
          if (!headerValue(headers, 'content-type')) {
            headers = Object.assign({}, headers, {
              'Content-Type': 'application/x-www-form-urlencoded'
            })
          }
        } else if (body !== undefined && body !== null && method !== 'GET' && method !== 'HEAD') {
          if (typeof body === 'string') {
            bodyText = body
          } else if (body instanceof Uint8Array || Array.isArray(body)) {
            bodyText = bytesToString(toU8(body), 'utf8')
          } else {
            bodyText = JSON.stringify(body)
          }
        }
        var res = hostHttp({
          url: String(url),
          method: method,
          headers: headers,
          body: bodyText,
          timeout: 15000
        })
        var response = {
          statusCode: Number(res.status || 0) || 0,
          status: Number(res.status || 0) || 0,
          headers: res.headers || {},
          body: res.body === undefined ? '' : res.body
        }
        var error = res.error ? new Error(String(res.error)) : null
        callback(error, response, response.body)
      },
      on: function (eventName, handler) {
        if (eventName) listeners[String(eventName)] = handler
      },
      send: function (eventName, data) {
        if (String(eventName) === 'inited') inited = data
      },
      // 洛雪脚本普遍会调 lx.utils.buffer / lx.utils.crypto（我们这批里 lx1/lx3/lx4/lx14 都用），
      // 不提供的话它们会直接抛 "Cannot read properties of undefined"
      utils: {
        buffer: {
          from: function (input, encoding) { return toBytes(input, encoding) },
          bufToString: function (buf, encoding) { return bytesToString(buf, encoding) },
          concat: function (list) {
            var merged = []
            ;(list || []).forEach(function (item) { merged = merged.concat(toArray(item)) })
            return toU8(merged)
          }
        },
        crypto: {
          md5: function (input) { return hostDigest('md5', [String(input)]) },
          aesEncrypt: function (data, mode, key, iv) { return aesCrypt('encrypt', data, mode, key, iv) },
          aesDecrypt: function (data, mode, key, iv) { return aesCrypt('decrypt', data, mode, key, iv) },
          randomBytes: function (size) {
            var length = Number(size) || 16
            var bytes = new Uint8Array(length)
            for (var i = 0; i < length; i++) bytes[i] = Math.floor(Math.random() * 256) & 255
            return bytes
          }
        }
      }
    }


    // 顶层 `this` 必须是全局对象：社区音源是给浏览器 / Node 写的，顶层 this 默认是
    // window / globalThis，脚本里 `this.console` / `this.version` 这类写法很常见。
    // 但直接用 `factory(...)` 调用，严格模式下 this 是 undefined，就会抛
    // 「Cannot read properties of undefined」（六音、独家源就是这么挂的）。
    //
    // 所以这里**保留 "use strict"，只把 this 显式绑到 root**：
    //   - 试过去掉 strict，结果更糟 —— 非严格模式下脚本里的隐式赋值
    //     （`console = xxx` 这种没写 var 的）会直接污染宿主全局，
    //     六音就把 console 覆盖了，之后所有日志全部静默（2026-10-31 实测）。
    //   - strict 挡住这类污染，call(root) 又补上了 this，两头都要。
    var factory = new Function('console', '"use strict";\n' + code)
    factory.call(root, makeConsole())

    var handler = listeners.request
    if (typeof handler !== 'function') {
      throw new Error('洛雪音源没有注册 request 事件，脚本可能不完整')
    }
    state.lxHandler = handler
    state.lxSources = inited && inited.sources ? inited.sources : null
    state.dialect = 'LX'
    state.lxVersion = root.lx.version
    if (state.lxSources) {
      var platforms = Object.keys(state.lxSources)
      state.platform = platforms.join(',')
    }
  }

  function makeConsole() {
    return {
      log: function () { safeLog('info', arguments) },
      info: function () { safeLog('info', arguments) },
      warn: function () { safeLog('warn', arguments) },
      error: function () { safeLog('error', arguments) },
      debug: function () { safeLog('debug', arguments) }
    }
  }

  function safeLog(level, args) {
    try {
      var parts = []
      for (var i = 0; i < args.length; i++) {
        var value = args[i]
        if (typeof value === 'string') parts.push(value)
        else {
          try { parts.push(JSON.stringify(value)) } catch (e) { parts.push(String(value)) }
        }
      }
      HOST.log('[' + level + '] ' + parts.join(' '))
    } catch (e) {
      // 日志失败绝不能拖垮音源
    }
  }

  // ============================================================ 归一化

  function firstNonEmpty() {
    for (var i = 0; i < arguments.length; i++) {
      var value = arguments[i]
      if (value !== undefined && value !== null && String(value).trim() !== '') return String(value).trim()
    }
    return ''
  }

  function normalizeArtists(item) {
    if (!item) return ''
    if (typeof item.artists === 'string') return item.artists
    if (Array.isArray(item.artists)) {
      var names = item.artists.map(function (a) {
        return a && typeof a === 'object' ? firstNonEmpty(a.name, a.title) : String(a || '')
      })
      return names.filter(function (n) { return n }).join('、')
    }
    if (Array.isArray(item.singer)) {
      return item.singer.map(function (s) { return firstNonEmpty(s && s.name, s) }).filter(function (n) { return n }).join('、')
    }
    return firstNonEmpty(
      item.artist,
      item.singer,
      item.singername,
      item.artistName,
      item.ar && item.ar[0] && item.ar[0].name,
      item.artists && item.artists[0] && item.artists[0].name
    )
  }

  function normalizeArtwork(item) {
    if (!item) return ''
    var art = firstNonEmpty(
      item.artwork,
      item.albumArt,
      item.picUrl,
      item.pic,
      item.cover,
      item.img,
      item.albumPic,
      item.al && item.al.picUrl,
      item.album && item.album.picUrl
    )
    if (!art && item.al && item.al.pic) art = firstNonEmpty(item.al.pic)
    if (art && art.indexOf('//') === 0) art = 'https:' + art
    return art
  }

  function normalizeSong(item, index) {
    if (!item || typeof item !== 'object') return null
    var id = firstNonEmpty(
      item.id, item.songId, item.songmid, item.mid, item.hash, item.rid,
      item.musicId, item.copyrightId, item.songid, item.audioId
    )
    if (!id) id = 'idx_' + index
    var duration = 0
    var durationRaw = item.duration !== undefined ? item.duration : (item.interval !== undefined ? item.interval : item.dt)
    if (durationRaw !== undefined && durationRaw !== null) {
      var numeric = Number(durationRaw)
      if (!isNaN(numeric) && numeric > 0) {
        // MusicFree 约定单位是秒，个别插件给毫秒
        duration = numeric > 10000 ? Math.round(numeric) : Math.round(numeric * 1000)
      }
    }
    var album = ''
    if (typeof item.album === 'string') album = item.album
    else if (item.album && item.album.name) album = String(item.album.name)
    if (!album) album = firstNonEmpty(item.al && item.al.name, item.albumName, item.albumname)

    return {
      id: id,
      title: firstNonEmpty(item.title, item.name, item.songname, item.songName, item.song_name, '未知歌曲'),
      artist: normalizeArtists(item),
      album: album,
      durationMs: duration,
      artwork: normalizeArtwork(item),
      platform: firstNonEmpty(item.platform, state.platform),
      raw: item
    }
  }

  function callMaybeAsync(target, thisArg, args) {
    var value = target.apply(thisArg, args)
    if (value && typeof value.then === 'function') return value
    return Promise.resolve(value)
  }

  // ============================================================ 对外动作

  function actionSearch(args) {
    var query = String(args[0] || '')
    var page = Number(args[1] || 1)
    var type = String(args[2] || 'music')
    if (state.dialect === 'LX') {
      throw new Error('洛雪音源不支持搜索，只能按歌曲信息解析播放地址')
    }
    if (!state.plugin || typeof state.plugin.search !== 'function') {
      throw new Error('这个插件没有实现搜索')
    }
    return callMaybeAsync(state.plugin.search, state.plugin, [query, page, type]).then(function (result) {
      var list = []
      if (Array.isArray(result)) list = result
      else if (result && Array.isArray(result.data)) list = result.data
      else if (result && Array.isArray(result.musicList)) list = result.musicList
      var songs = []
      for (var i = 0; i < list.length; i++) {
        var song = normalizeSong(list[i], i)
        if (song) songs.push(song)
      }
      return {
        songs: songs,
        isEnd: result && result.isEnd !== undefined ? !!result.isEnd : songs.length === 0
      }
    })
  }

  function actionResolve(args) {
    var songJson = args[0] || '{}'
    var quality = String(args[1] || '320k')
    var song
    try { song = typeof songJson === 'string' ? JSON.parse(songJson) : songJson } catch (e) { song = {} }
    var raw = song.raw || song
    var platform = pickPlatform(song, raw)

    if (state.dialect === 'LX') {
      var info = {
        type: quality,
        musicInfo: raw
      }
      return callMaybeAsync(state.lxHandler, null, [
        { action: 'musicUrl', source: platform, info: info }
      ]).then(function (url) { return { url: normalizeUrl(url) } })
    }

    if (!state.plugin || typeof state.plugin.getMediaSource !== 'function') {
      throw new Error('这个插件没有实现 getMediaSource')
    }
    return callMaybeAsync(state.plugin.getMediaSource, state.plugin, [raw, quality]).then(function (source) {
      if (!source) throw new Error('插件没有返回播放地址')
      if (typeof source === 'string') return { url: normalizeUrl(source) }
      return {
        url: normalizeUrl(source.url || source.src || ''),
        headers: source.headers || null
      }
    })
  }

  /**
   * 决定请求信封里的 `source`（单个平台名）。
   *
   * **这里必须返回一个「单个」平台**。洛雪音源上报的 `state.platform` 是它支持的
   * **全部平台拼成的串**（`kw,wy,kg,tx,mg`），脚本按单个名字 switch 分支 ——
   * 把整串塞过去，脚本会一律回「不支持的平台: kw,wy,kg,tx,mg」，表现为「搜得到、点不了」。
   *
   * 优先级：歌曲自带的平台 → musicInfo 里的 source/source → 和支持的平台取交集 → 第一个。
   */
  function pickPlatform(song, raw) {
    var info = raw && typeof raw === 'object' ? raw : null
    var chosen = firstNonEmpty(
      song && (song.platform || song.source),
      info && (info.source || info.platform)
    )
    if (chosen && chosen.indexOf(',') < 0) return chosen

    // 支持列表：优先取「歌曲自己声称的那个」，否则退而求其次取列表第一个，
    // 让音源至少能进到它关心的那条分支里（真正取不到地址时会报错回退到下一个源）。
    var list = String(state.platform || '').split(/[,\s|]+/).filter(Boolean)
    if (!list.length) return ''
    if (chosen) {
      var want = String(chosen).toLowerCase()
      for (var i = 0; i < list.length; i++) {
        if (list[i].toLowerCase() === want) return list[i]
      }
    }
    return list[0]
  }

  function normalizeUrl(value) {
    var url = value
    if (url && typeof url === 'object') url = firstNonEmpty(url.url, url.src, url.data)
    url = String(url == null ? '' : url)
    if (url.indexOf('//') === 0) url = 'https:' + url
    if (!/^https?:\/\//i.test(url)) {
      throw new Error('音源没有返回可播放的 http 地址（拿到的是：' + (url || '空') + '）')
    }
    return url
  }

  function actionLyric(args) {
    var songJson = args[0] || '{}'
    var song
    try { song = typeof songJson === 'string' ? JSON.parse(songJson) : songJson } catch (e) { song = {} }
    var raw = song.raw || song
    var platform = pickPlatform(song, raw)

    if (state.dialect === 'LX') {
      return callMaybeAsync(state.lxHandler, null, [
        { action: 'lyric', source: platform, info: { musicInfo: raw } }
      ]).then(function (lrc) {
        return { lrc: extractLrc(lrc) }
      })
    }

    if (!state.plugin || typeof state.plugin.getLyric !== 'function') {
      return Promise.resolve({ lrc: '' })
    }
    return callMaybeAsync(state.plugin.getLyric, state.plugin, [raw]).then(function (result) {
      if (!result) return { lrc: '' }
      if (typeof result === 'string') return { lrc: result }
      return { lrc: extractLrc(result.rawLrc || result.lyric || result.lrc || '') }
    })
  }

  function extractLrc(value) {
    if (!value) return ''
    if (typeof value === 'string') return value
    if (typeof value === 'object') {
      return String(value.lyric || value.rawLrc || value.lrc || value.text || '')
    }
    return ''
  }

  function dispatch(action, args) {
    if (action === 'search') return actionSearch(args)
    if (action === 'resolve') return actionResolve(args)
    if (action === 'lyric') return actionLyric(args)
    throw new Error('未知动作: ' + action)
  }

  // ============================================================ 宿主调用入口

  function jsonSafe(value) {
    try {
      return JSON.stringify(value)
    } catch (e) {
      return 'null'
    }
  }

  /** 重置到干净状态：清掉上一个音源留下的一切 */
  root.__ms_reset = function () {
    state.dialect = ''
    state.name = ''
    state.version = ''
    state.author = ''
    state.platform = ''
    state.plugin = null
    state.lxHandler = null
    state.lxSources = null
    state.pending = null
    state.lastError = ''
    return '1'
  }

  /**
   * 载入一个音源脚本。
   * @param dialect 'MUSICFREE' | 'LX'
   * @param code    脚本全文
   * @return JSON：{ok, name, version, author, platform, dialect, actions, sources}
   */
  root.__ms_load = function (dialect, code) {
    try {
      root.__ms_reset()
      var text = String(code || '')
      if (text.length < 40) throw new Error('脚本内容太短')
      if (String(dialect) === 'LX') loadLx(text)
      else loadMusicFree(text)

      var actions = []
      if (state.dialect === 'LX') {
        actions.push('musicUrl')
        if (state.lxSources) {
          Object.keys(state.lxSources).forEach(function (key) {
            var entry = state.lxSources[key]
            var list = entry && entry.actions ? entry.actions : []
            list.forEach(function (a) {
              if (actions.indexOf(String(a)) < 0) actions.push(String(a))
            })
          })
        }
      } else {
        if (typeof state.plugin.search === 'function') actions.push('search')
        if (typeof state.plugin.getMediaSource === 'function') actions.push('musicUrl')
        if (typeof state.plugin.getLyric === 'function') actions.push('lyric')
      }

      var qualities = []
      if (state.lxSources) {
        Object.keys(state.lxSources).forEach(function (key) {
          var entry = state.lxSources[key]
          var list = entry && entry.qualitys ? entry.qualitys : []
          list.forEach(function (q) {
            if (qualities.indexOf(String(q)) < 0) qualities.push(String(q))
          })
        })
      }

      return jsonSafe({
        ok: true,
        name: state.name || state.platform || '在线音源',
        version: state.version,
        author: state.author,
        platform: state.platform,
        dialect: state.dialect,
        actions: actions,
        qualities: qualities,
        platforms: state.lxSources ? Object.keys(state.lxSources) : (state.platform ? [state.platform] : [])
      })
    } catch (e) {
      state.lastError = messageOf(e)
      return jsonSafe({ ok: false, error: state.lastError })
    }
  }

  /**
   * 发起一个动作。结果通过 __ms_take 反复取。
   * @param action 'search' | 'resolve' | 'lyric'
   * @param argsJson 参数数组的 JSON
   */
  root.__ms_start = function (action, argsJson) {
    var args = []
    try { args = JSON.parse(String(argsJson || '[]')) } catch (e) { args = [] }
    if (!Array.isArray(args)) args = [args]
    // 槽位单独持有：__ms_take 取走后会把 state.pending 置空，
    // 晚到的 then 回调必须写进自己的槽位，不能再去读 state.pending
    var slot = { done: false, value: null, error: null }
    state.pending = slot

    var result
    try {
      result = dispatch(String(action), args)
    } catch (e) {
      slot.done = true
      slot.error = messageOf(e)
      return '1'
    }

    if (result && typeof result.then === 'function') {
      result.then(
        function (value) {
          slot.done = true
          slot.value = value
        },
        function (error) {
          slot.done = true
          slot.error = messageOf(error)
        }
      )
    } else {
      slot.done = true
      slot.value = result
    }
    return '1'
  }

  /** 取结果：{done:false} 表示还没好，宿主下次再问 */
  root.__ms_take = function () {
    var pending = state.pending
    if (!pending) return '{"done":true,"error":"没有进行中的任务"}'
    if (!pending.done) return '{"done":false}'
    if (pending.error) {
      state.pending = null
      return jsonSafe({ done: true, error: pending.error })
    }
    var payload = jsonSafe(pending.value)
    state.pending = null
    return '{"done":true,"value":' + (payload === 'null' ? 'null' : payload) + '}'
  }

  /** 健康检查用：宿主启动时打一下，确认翻译层装好了 */
  root.__ms_ping = function () {
    return jsonSafe({ ok: true, hasHttp: typeof HOST.httpRequest === 'function' })
  }
})()
