/* METADATA
{
  "name": "56al_tuoke",
  "display_name": {
    "zh": "脱壳修复工具包 v17（云端56.al）",
    "en": "Unpack & Dex Repair Toolkit"
  },
  "description": "基于 56.al 在线脱壳的一体化脱壳+修复工具包：上传 APK 云端自动脱壳，下载后用 dexlib2 引擎（与 MT 管理器同源）规范化修复 dex（重算 checksum/SHA-1、字符串去重、数据段重排，等价 MT 的 dex 修复）。首次使用：tuoke_login_url 获取授权链接→浏览器登录→tuoke_set_cookie 粘贴 Cookie（或 tuoke_browser_login 自动读取），登录态持久化自动复用。tuoke_all 一键完成上传→脱壳→下载→自动修复全流程。",
  "category": "ReverseEngineering",
  "tools": [
    {
      "name": "tuoke_login_url",
      "description": "获取 56.al 第三方 OAuth 授权登录链接（github/qq/gitee/microsoft/wx）。浏览器打开完成授权后，从浏览器复制 Cookie 并调用 tuoke_set_cookie 完成包内登录。",
      "parameters": [
        {
          "name": "provider",
          "description": "OAuth 提供方: github / qq / gitee / microsoft / wx，默认 github",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_browser_login",
      "description": "从 Operit 内置浏览器自动读取 56.al 登录 Cookie 并保存（用户只需先在浏览器里打开 56.al 完成登录）。自动验证登录态。零门槛，无需手动复制 Cookie。",
      "parameters": [
        {
          "name": "open_login_page",
          "description": "true=自动打开 56.al 登录页（默认 true）",
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_set_cookie",
      "description": "设置并持久化 56.al 登录 Cookie（保存到本地，之后所有上传请求自动携带）。接受完整 Cookie 字符串（如 PHPSESSID=abc123...），立即调用 pre_upload 验证登录态是否有效。",
      "parameters": [
        {
          "name": "cookie",
          "description": "登录后的 Cookie 字符串，例如 PHPSESSID=xxxx（可含多个键值对，用分号分隔）",
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "tuoke_get_cookie",
      "description": "查看当前已保存的 56.al Cookie 摘要（脱敏显示），并验证登录态是否有效。",
      "parameters": []
    },
    {
      "name": "tuoke_clear_cookie",
      "description": "清除本地保存的 56.al 登录 Cookie。",
      "parameters": []
    },
    {
      "name": "tuoke_upload",
      "description": "上传 APK 到 56.al 云脱壳。v16 起与大包走同一内核（分片在 Linux 侧落盘、curl 从磁盘直传，不进 JS 内存），不再有 8MB 限制，因此不再区分小包大包。返回任务 hash。需要先登录。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "待脱壳 APK 的 Android 路径",
          "type": "string",
          "required": true
        },
        {
          "name": "csrf_token",
          "description": "56.al 上传页的 csrf_token（可选，默认自动获取）",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_upload_big",
      "description": "上传大 APK 到 56.al 云脱壳。v17 内核：分片大小默认跟随服务端 pre_upload 下发的 chunksize（与官网 uploadnew.js 一致，根治分片对不上导致的卡死），dd 在 Linux 侧切分片落盘 → curl -F file=@分片文件 直传（不经过 JS 内存、不进命令行参数，根治旧版 11MB 参数超限卡死）；支持第三方通道（服务端返回 third 时整文件直传 result.url）；带假死检测（30 秒低于 1KB/s 自动放弃重试）、断点续传、进度落盘、csrf 失效自救。chunk_mb 仅在显式传入时强制覆盖；单片超时可用 timeout_sec 调大。上传上限 3000MB。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "待脱壳 APK 的 Android 路径",
          "type": "string",
          "required": true
        },
        {
          "name": "csrf_token",
          "description": "56.al 上传页的 csrf_token（可选，默认自动获取）",
          "type": "string",
          "required": false
        },
        {
          "name": "chunk_mb",
          "description": "强制分片大小（MB）。默认不传=跟随服务端下发的 chunksize（推荐）。只有在极端弱网、单片老是超时时才显式传 2 或 1 强制覆盖（覆盖后若与服务端尺寸不一致可能拼装失败，慎用）",
          "type": "string",
          "required": false
        },
        {
          "name": "timeout_sec",
          "description": "单片 curl 超时秒数，默认 120。网络慢导致单片超时就调大到 300",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_upload_resume",
      "description": "★ 上传卡住/断网后的断点续传：拿同一个 APK 再跑一次即可，会自动问服务端哪些分片已经收到并跳过，不用从头来、也不用记 hash。反复断就把 chunk_mb 调小再续传。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "和第一次上传时完全相同的 APK 路径（文件内容也必须一致，否则 md5 变了就是另一个任务）",
          "type": "string",
          "required": true
        },
        {
          "name": "chunk_mb",
          "description": "强制分片大小（MB），默认不传=跟随服务端。仅弱网时显式传 2 或 1",
          "type": "string",
          "required": false
        },
        {
          "name": "timeout_sec",
          "description": "单片超时秒数，默认 120",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_upload_progress",
      "description": "查看某个上传任务上次传到百分之几（读设备上的进度文件）。上传中途卡住时用它确认到底传了几片，再决定要不要续传。",
      "parameters": [
        {
          "name": "md5",
          "description": "上传任务的 hash（32 位 MD5，就是上传返回的 hash）",
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "tuoke_status",
      "description": "查询 56.al 脱壳任务状态（无需登录）。返回任务日志与状态(success/error/processing)。",
      "parameters": [
        {
          "name": "hash",
          "description": "任务 hash（32位MD5）",
          "type": "string",
          "required": true
        },
        {
          "name": "since_id",
          "description": "上次已读日志ID，用于增量查询，默认0",
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_download",
      "description": "下载 56.al 脱壳结果 7z 并自动解压 + 修复 dex 头（无需登录）。默认输出到 APK 同目录下新建的「<软件名>脱壳文件」文件夹（需传 apk_path 才能自动定位；否则输出到 /sdcard/Download/Operit/tuoke_out/{hash}）。大文件 Linux 侧流式处理，返回文件路径列表（不返回内容）。",
      "parameters": [
        {
          "name": "hash",
          "description": "任务 hash",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "自定义输出目录（默认：apk_path 同目录/<软件名>脱壳文件；无 apk_path 时默认 /sdcard/Download/Operit/tuoke_out/{hash}）",
          "type": "string",
          "required": false
        },
        {
          "name": "apk_path",
          "description": "原 APK 路径（用于自动定位输出目录，建议传）",
          "type": "string",
          "required": false
        },
        {
          "name": "skip_unpack",
          "description": "true=只下载 7z 不解压",
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_all",
      "description": "一键云脱壳：上传 → 轮询等待 → 下载+解压+修复 dex 头。产物默认放到 APK 同目录下新建的「<软件名>脱壳文件」文件夹（可传 output_dir 覆盖）。输出文件路径报告。需要先登录。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "待脱壳 APK 的 Android 路径",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "自定义输出目录（默认 APK 同目录/<软件名>脱壳文件）",
          "type": "string",
          "required": false
        },
        {
          "name": "max_wait_seconds",
          "description": "最大等待秒数（默认1800）",
          "type": "number",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_fix_dex",
      "description": "修复 dex 文件的 dex 头（魔数/文件大小/头大小）。Linux 侧流式处理，只读头部。",
      "parameters": [
        {
          "name": "dex_path",
          "description": "dex 文件路径",
          "type": "string",
          "required": true
        },
        {
          "name": "output_path",
          "description": "可选，输出路径（默认覆盖原文件）",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "tuoke_check_auth",
      "description": "检查 56.al 登录态是否有效：读取已保存 Cookie 并调用 pre_upload 验证（403=未登录/过期，200+code=有效）。返回诊断详情与修复指引。",
      "parameters": []
    },
    {
      "name": "tuoke_dex_clean",
      "description": "用 dexlib2（与 MT 管理器同源引擎）规范化重写 dex：重算 checksum/SHA-1 签名、字符串去重、数据段紧凑重排。等价于 MT 管理器的 dex 修复功能。输入文件或目录，输出到指定目录（默认 <输入目录>/修复dex/）。",
      "parameters": [
        {
          "name": "dex_path",
          "description": "待修复的 dex 文件路径或包含 dex 的目录",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "可选，输出目录（默认：dex_path 为目录时输出到 <目录>/修复dex/；为文件时输出到同目录/修复dex/）",
          "type": "string",
          "required": false
        }
      ]
    }
  ],
  "enabledByDefault": false
}
*/
// ============================================================
// 脱壳修复工具包 v11.1（56.al 在线脱壳 + dexlib2 修复）
// 1. 上传加固 APK 到 56.al 云端脱壳，下载 7z 并解压出 dex
// 2. dexlib2 规范化修复（等价 MT 管理器 dex 修复）
// 3. 全流程 Linux 侧流式处理，根治 OOM
// 4. 内置登录引导 + Cookie 持久化，分享即用
// 5. v11.1: 孤儿任务重建 + complete 前重新抓 csrf（根治新文件卡死）（文件已入库但任务未建时自动补传分片+complete）
// 6. v16 上传内核重写（根治"上传老是卡住"）：
//    旧链路 python3读8MB -> base64(≈11MB) 回传JS -> printf %s '<11MB>' -> base64 -d -> curl -F
//    致命点：Linux 单参数上限 MAX_ARG_STRLEN=128KB，11MB 参数必然卡死/失败。
//    v16 改为 dd 侧切分片落盘 + curl -F file=@分片文件 直传，JS 只经手几十字节 JSON。
//    新增：假死检测(speed-time/speed-limit)、断点续传、进度落盘、csrf 失效自救、
//          tuoke_upload_resume（断点续传）、tuoke_upload_progress（查进度）
// 7. v17（对齐官网 assets/js/uploadnew.js 逐行核对后的修正）：
//    (a) ★最重要：分片大小由【服务端 pre_upload 返回的 chunksize/chunks】决定，
//        官方 JS 是 var chunkSize = result.chunksize; —— 客户端无权自定义。
//        旧版自作主张用 4MB 切片，若服务端按别的尺寸拼装，分片号与写入偏移对不上，
//        服务端永远等不齐 chunks 片，表现就是"上传卡住/传不完"。现已改为以服务端为准。
//    (b) 补上第三方通道分支：pre_upload 返回 third=true 时，整文件一次 POST 到 result.url
//        （表单字段来自 result.post），再调 complete_upload。旧版没这分支，命中即卡死。
//    (c) 单片失败重试次数对齐官方（3 次），重试前先问服务端 received 避免重复传。
//    (d) 上传体积上限对齐官方页面变量 upload_max_filesize = 3000（MB），超限提前报错。
//    (e) chunk_mb 从"默认 4"改为"默认跟随服务端"；只有显式传值才强制覆盖。
// ============================================================
var tuoke56 = (function () {
    'use strict';

    var BASE = 'https://56.al';
    var PASSWORD = 'dump';
    var CHUNK_SIZE = 8388608;
    var COOKIE_FILE = '/sdcard/Download/Operit/tuoke_out/.56al_cookie.b64';

    function dirname(p) {
        if (!p) return '/sdcard/Download/Operit/tuoke_out';
        var i = p.lastIndexOf('/');
        return i > 0 ? p.substring(0, i) : '/';
    }

    // 根据 APK 路径派生默认输出目录：<APK同目录>/<软件名去扩展名>脱壳文件
    // 例如 /sdcard/Download/xxx.apk -> /sdcard/Download/xxx脱壳文件
    function deriveOutDir(apkPath) {
        if (!apkPath) return null;
        var dir = dirname(apkPath);
        var name = apkPath.split('/').pop() || '';
        var base = name.replace(/\.[^.]*$/, '') || 'tuoke';
        return dir + '/' + base + '脱壳文件';
    }

    function wrap(fn) {
        return function (params) {
            return Promise.resolve()
                .then(function () { return fn(params || {}); })
                .then(function (result) { return { success: true, data: result }; })
                .catch(function (err) {
                    return { success: false, message: (err && err.message) ? err.message : String(err) };
                });
        };
    }

    function exec(cmd, timeoutMs) {
        return Tools.System.terminal.hiddenExec(cmd, { timeoutMs: timeoutMs || 300000 })
            .then(function (r) {
                if (!r || r.exitCode !== 0) {
                    var out = (r && r.output) ? r.output : '';
                    var e = new Error('命令执行失败 (exit=' + (r ? r.exitCode : '?') + '): ' + cmd + '\n' + out.substring(0, 800));
                    e.exitCode = r ? r.exitCode : -1;
                    e.output = out;
                    throw e;
                }
                return r;
            });
    }

    function toLinuxPath(p) { return p; }

    function shellQuote(s) {
        return "'" + String(s).replace(/'/g, "'\\''") + "'";
    }

    function respText(resp) {
        return resp.text || resp.content || resp.body || '';
    }

    // ========== Cookie 持久化管理 ==========
    // 内置默认 Cookie（已内嵌进包源码，开箱即用，无需每次重新登录）
    var EMBEDDED_COOKIE = '';
    function ensureDir() {
        return exec('mkdir -p /sdcard/Download/Operit/tuoke_out', 10000);
    }

    function saveCookie(cookie) {
        var raw = String(cookie).trim();
        return ensureDir()
            .then(function () {
                // 用 Linux base64 编码写入（JS 环境可能无 btoa）
                return exec('printf %s ' + shellQuote(raw) + ' | base64 -w0 > ' + shellQuote(COOKIE_FILE) + ' && chmod 600 ' + shellQuote(COOKIE_FILE), 10000);
            });
    }

    function loadCookie() {
        return exec('cat ' + shellQuote(COOKIE_FILE) + ' 2>/dev/null', 10000)
            .then(function (r) {
                var b64 = r.output.trim();
                if (!b64) return EMBEDDED_COOKIE; // 文件不存在/为空时用内嵌 Cookie
                // 用 Linux base64 -d 解码
                return exec('printf %s ' + shellQuote(b64) + ' | base64 -d 2>/dev/null', 10000)
                    .then(function (rr) { return rr.output.trim() || EMBEDDED_COOKIE; })
                    .catch(function () { return EMBEDDED_COOKIE; });
            })
            .catch(function () { return EMBEDDED_COOKIE; });
    }

    function clearCookie() {
        return exec('rm -f ' + shellQuote(COOKIE_FILE), 10000)
            .then(function () { return { cleared: true }; });
    }

    function extractPhpSessId(cookie) {
        if (!cookie) return null;
        var m = cookie.match(/PHPSESSID=([^;\s]+)/i);
        return m ? m[1] : null;
    }

    // ========== 带 Cookie 的请求封装 ==========
    // 注意：Tools.Net.http 会把 Content-Type 强制改为 application/json，
    // 导致 56.al 的 PHP 端 $_POST 解析不到 form 字段（返回 no type / 403）。
    // 因此所有 form POST 统一走 Linux 侧 curl，可靠且不占 JS 内存。

    // curl 执行 form POST，可选带 cookie（先解码真实 cookie，用 -H 头发送）
    function curlFormPost(url, formData, withCookie) {
        var dataArg = shellQuote(formData);
        function doPost(cookieHeader) {
            var cmd = 'curl -s -m 20' + (cookieHeader || '') +
                " -H 'Content-Type: application/x-www-form-urlencoded'" +
                " -H 'User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36'" +
                " -H 'X-Requested-With: XMLHttpRequest'" +
                " -H 'Referer: " + BASE + "/upload.php'" +
                ' --data ' + dataArg + ' ' + shellQuote(url);
            return exec(cmd, 30000).then(function (r) {
                var out = r.output.trim();
                var parsed = null;
                try { parsed = JSON.parse(out); } catch (e) {}
                return { raw: out, json: parsed };
            });
        }
        if (!withCookie) return doPost('');
        return loadCookie().then(function (cookie) {
            if (!cookie) {
                var err = new Error('未检测到 56.al 登录 Cookie。请先调用 tuoke_login_url 获取授权链接并完成登录，再调用 tuoke_set_cookie 粘贴 Cookie。');
                err.needLogin = true;
                throw err;
            }
            return doPost(' -H ' + shellQuote('Cookie: ' + cookie));
        });
    }

    // curl GET 带 cookie（先解码真实 cookie，用 -H 头发送）
    function curlGetWithCookie(url) {
        return loadCookie().then(function (cookie) {
            if (!cookie) {
                var err = new Error('未检测到 56.al 登录 Cookie。请先调用 tuoke_login_url 获取授权链接并完成登录，再调用 tuoke_set_cookie 粘贴 Cookie。');
                err.needLogin = true;
                throw err;
            }
            var cmd = 'curl -s -m 20 -H ' + shellQuote('Cookie: ' + cookie) +
                " -H 'User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36'" +
                " -H 'Referer: " + BASE + "/upload.php'" +
                ' ' + shellQuote(url);
            return exec(cmd, 30000).then(function (r) {
                return r.output;
            });
        });
    }

    // 带 Cookie 的 GET（Tools.Net 无 form 需求时可用；这里统一用 curl 保证 cookie 生效）
    function authedGet(url) {
        return loadCookie().then(function (cookie) {
            if (!cookie) {
                var err = new Error('未检测到 56.al 登录 Cookie。请先调用 tuoke_login_url 获取授权链接并完成登录，再调用 tuoke_set_cookie 粘贴 Cookie。');
                err.needLogin = true;
                throw err;
            }
            return curlGetWithCookie(url).then(function (html) {
                return { text: html, content: html, body: html, statusCode: 200 };
            });
        });
    }

    // 带 Cookie 的 form POST
    function authedFormPost(url, formData) {
        return loadCookie().then(function (cookie) {
            if (!cookie) {
                var err = new Error('未检测到 56.al 登录 Cookie。请先调用 tuoke_login_url 获取授权链接并完成登录，再调用 tuoke_set_cookie 粘贴 Cookie。');
                err.needLogin = true;
                throw err;
            }
            return curlFormPost(url, formData, true).then(function (res) {
                if (!res.json) throw new Error('接口返回非 JSON: ' + res.raw.substring(0, 200));
                return res.json;
            });
        });
    }

    // 无 cookie 的 form POST（登录引导用）
    function plainFormPost(url, formData) {
        return curlFormPost(url, formData, false).then(function (res) {
            if (!res.json) throw new Error('接口返回非 JSON: ' + res.raw.substring(0, 200));
            return res.json;
        });
    }

    function fetchCsrf() {
        return authedGet(BASE + '/upload.php').then(function (resp) {
            var html = respText(resp);
            var m = html.match(/id="csrf_token"\s+value="([a-f0-9]{32})"/i) ||
                    html.match(/name="csrf_token"\s+value="([a-f0-9]{32})"/i) ||
                    html.match(/value="([a-f0-9]{32})"[^>]*id="csrf_token"/i);
            if (!m) throw new Error('无法从上传页解析 csrf_token（Cookie 可能已失效或页面结构变化）');
            return m[1];
        });
    }

    function preUpload(csrf, name, md5, size, extra) {
        extra = extra || {};
        var body = 'csrf_token=' + encodeURIComponent(csrf) +
            '&name=' + encodeURIComponent(name) +
            '&hash=' + md5 +
            '&size=' + size +
            // show=1 与官方 uploadnew.js 一致（this.input.show 默认 true → '1'）。
            // 关键修正（v15.1）：官方秒传命中逻辑对 show=1 才会正常走 uploadSuccess 建任务/跳转；
            // 用 show=0 会导致已入库文件 pre_upload 返回 code:1「已存在」后不再创建脱壳任务，
            // complete 也报 CSRF TOKEN ERROR，形成孤儿记录（文件在库、任务缺失、官方无法重建）。
            // v15.1 统一改用 show=1，与官方行为严格对齐。
            '&show=1&ispwd=' + (extra.ispwd ? '1' : '0') + '&pwd=' + encodeURIComponent(extra.pwd || '');
        return authedFormPost(BASE + '/ajax.php?act=pre_upload', body).then(function (data) {
            if (data.code === 1) return { instant: true, hash: data.hash };
            if (data.code === 403) throw new Error('登录态无效（pre_upload 返回 403）。请重新执行 tuoke_login_url + tuoke_set_cookie 登录。');
            if (data.code !== 0) throw new Error('pre_upload 失败: ' + JSON.stringify(data));
            return {
                instant: false, hash: data.hash,
                chunksize: data.chunksize, chunks: data.chunks,
                received: data.received || [],
                // v17：官方 uploadnew.js 的 result.third 分支 —— 服务端把文件转交给第三方存储。
                // 命中时不再走分片通道，而是整文件一次 POST 到 result.url，再 complete_upload。
                third: !!data.third, url: data.url || '', post: data.post || null
            };
        });
    }
    // v9.1: 孤儿任务重建（实测 2026-09-30 黄果短剧案例）。
    // 现象：分片全部上传完成后，若 complete_upload 因 csrf 一次性/网络中断失败，
    // 文件已入库（pre_upload code:1 exists:1）但任务表无任务（api_task exists:false）。
    // 此时官方网页 JS 对 code:1 直接跳转 file.php，不会再建任务；
    // ispwd=1&pwd=dump 兜底（v8）在当前服务端也已失效（实测同样返回 code:1 不建任务）。
    // 实测有效方案：补传一个分片（upload_part，哪怕服务端已有该分片）+ complete_upload（新 csrf），
    // 可触发服务端重新创建脱壳任务（黄果短剧案例即由此救回）。
    function checkTaskExists(hash) {
        // 用 curl 带 cookie 查询任务（api_task 无需登录，但保持 cookie 一致性）
        return loadCookie().then(function (cookie) {
            var cmd = 'curl -s -m 20 -H ' + shellQuote('Cookie: ' + cookie) +
                ' ' + shellQuote(BASE + '/api_task.php?hash=' + encodeURIComponent(hash) + '&since_id=0');
            return exec(cmd, 30000).then(function (r) {
                var data = null;
                try { data = JSON.parse(r.output.trim()); } catch (e) {}
                if (data && data.exists) return { taskExists: true, status: data.status || '' };
                return { taskExists: false, status: (data && data.status) || 'not_found' };
            }, function () { return { taskExists: false, status: 'query_error' }; });
        });
    }
    // ========== v15.3 核心修复 ==========
    // 【根因】官方 uploadnew.js 分片上传完成后，通过 uploadSuccess(hash) 跳转到 file.php?hash=<hash>，
    //        而【访问 file.php 这个页面本身会触发服务端创建/激活脱壳任务】。
    //        实测（2026-10-02 心养_1.0.0.apk）：
    //           - 上传完 5 个分片后，api_task 仍 not_found（任务未建）
    //           - 访问一次 file.php?hash= 后，api_task 立即变为 processing（任务开始接收、排队、执行）
    //        工具此前只调 checkTaskExists（api_task）而从未访问 file.php，导致分片传完但任务未触发，
    //        被误判为"孤儿"（文件在库、任务 not_found），实际只是漏了 file.php 这一步触发动作。
    // 【修复】新增 triggerTask(hash)：分片上传完成后（及秒传命中但任务 not_found 时），
    //        访问一次 file.php?hash= 触发服务端建任务，再重新查任务确认。
    function triggerTask(hash) {
        return authedGet(BASE + '/file.php?hash=' + encodeURIComponent(hash)).then(function () {
            // file.php 是页面访问（非 JSON），触发建任务后稍作等待再复查
            return new Promise(function (resolve) {
                setTimeout(function () { resolve(checkTaskExists(hash)); }, 1500);
            });
        }).catch(function () {
            // 访问 file.php 失败不致命，仍查一次任务兜底
            return checkTaskExists(hash);
        });
    }
    // 综合判定：先查任务，not_found 时尝试触发（访问 file.php）后再查一次。
    // 返回 { taskExists, status, triggered }，triggered 表示是否执行过 file.php 触发。
    function ensureTaskExists(hash) {
        return checkTaskExists(hash).then(function (chk) {
            if (chk.taskExists) return { taskExists: true, status: chk.status, triggered: false };
            return triggerTask(hash).then(function (chk2) {
                return { taskExists: chk2.taskExists, status: chk2.status, triggered: true };
            });
        });
    }
    // 上传一个真实分片（从本地文件读取指定分片，base64 编码后复用 uploadPart 通道）
    // 用于孤儿任务重建：文件已在库，服务端已有全部或部分分片，重传 chunk 1 也能通过校验。
    function uploadChunkFromFile(linuxPath, md5, chunkIndex, chunkSize) {
        var offset = (chunkIndex - 1) * chunkSize;
        var cmd = 'dd if=' + shellQuote(linuxPath) + ' bs=' + chunkSize + ' skip=' + (chunkIndex - 1) + ' count=1 2>/dev/null | base64 -w0';
        return exec(cmd, 60000).then(function (r) {
            var b64 = r.output.trim();
            if (!b64) throw new Error('读取分片 ' + chunkIndex + ' 失败');
            return fetchCsrf().then(function (csrf) {
                return uploadPart(csrf, md5, chunkIndex, b64);
            });
        });
    }
    function rebuildOrphanTask(name, md5, size, linuxPath, chunkSize) {
        // 先尝试直接补传 chunk 1 + complete（无需本地分片文件时也可用）
        return fetchCsrf().then(function (csrf) {
            // 分片 1 通常已入库；用极小内容不可行（服务端校验大小），必须传真实分片。
            // 优先从本地 APK 文件读取 chunk 1；若失败则用 uploadPart 的 b64 通道传文件头 8KB（仅探测）。
            return uploadChunkFromFile(linuxPath, md5, 1, chunkSize)
                .then(function () {
                    return fetchCsrf().then(function (csrf2) {
                        return completeUploadSafe(csrf2, md5);
                    }).then(function (cc) {
                        return checkTaskExists(md5).then(function (chk) {
                            return {
                                rebuilt: chk.taskExists,
                                taskExists: chk.taskExists,
                                status: chk.status,
                                uploadPart: 'ok',
                                completeWarning: cc.warning || null
                            };
                        });
                    });
                })
                .catch(function (err) {
                    // 补传失败也查一次任务（可能已建成）
                    return checkTaskExists(md5).then(function (chk) {
                        return {
                            rebuilt: chk.taskExists,
                            taskExists: chk.taskExists,
                            status: chk.status,
                            uploadPartError: String(err && err.message || err)
                        };
                    });
                });
        });
    }

    // upload_part 是 multipart 文件上传（$_FILES 接收，字段名 file）。
    // 实测 56.al 协议（uploadnew.js v1532）：
    //   1. file/hash/chunk/csrf_token 全部放 form 字段（不能用 URL query，否则 hash error；
    //      也不能 --data-binary 裸字节，否则"请选择文件"）。
    //   2. 关键：官方在一次完整上传会话中，csrf_token 在页面加载时抓取一次后【全程复用】，
    //      pre_upload → 各分片 upload_part → complete_upload 全部携带同一个 csrf。
    //      —— v14 的错误正是"每个分片前重抓 csrf + complete 前再重抓"，导致上传会话被搅乱，
    //         complete 时服务端找不到匹配 pre_upload 的会话 → 必然「参数校验失败」→ 任务未建 → poll 空转超时。
    // v15：本函数严格使用【调用方传入的会话 csrf】，绝不内部重抓。
    function uploadPart(csrf, md5, chunkIndex, chunkB64) {
        // 将 base64 解码为二进制文件，再 curl -F 以 multipart 上传
        var tmpFile = '/sdcard/Download/Operit/tuoke_out/_chunk_' + md5 + '_' + chunkIndex + '.bin';
        var b64File = tmpFile + '.b64';
        return exec('printf %s ' + shellQuote(chunkB64) + ' > ' + shellQuote(b64File) +
            ' && base64 -d ' + shellQuote(b64File) + ' > ' + shellQuote(tmpFile) +
            ' && rm -f ' + shellQuote(b64File), 30000)
            .then(function () {
                return loadCookie().then(function (cookie) {
                    if (!cookie) {
                        var err = new Error('未检测到 56.al 登录 Cookie。请先登录。');
                        err.needLogin = true;
                        throw err;
                    }
                    function doUpload(csrfToken) {
                        var cmd = 'curl -s -m 60 -H ' + shellQuote('Cookie: ' + cookie) +
                            " -H 'User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36'" +
                            " -H 'Referer: " + BASE + "/upload.php'" +
                            ' -F ' + shellQuote('file=@' + tmpFile + ';type=application/octet-stream') +
                            ' -F ' + shellQuote('hash=' + md5) +
                            ' -F ' + shellQuote('chunk=' + chunkIndex) +
                            ' -F ' + shellQuote('csrf_token=' + csrfToken) + ' ' +
                            shellQuote(BASE + '/ajax.php?act=upload_part');
                        return exec(cmd, 70000).then(function (r) {
                            var out = (r.output || '').trim();
                            var parsed = null;
                            try { parsed = JSON.parse(out); } catch (e) {}
                            if (parsed && parsed.code === 1) return { done: true };
                            if (parsed && parsed.code === 0) return { done: false };
                            if (parsed && parsed.code === 403) {
                                var err2 = new Error('登录态无效（upload_part 403）。请重新登录。');
                                err2.needLogin = true;
                                throw err2;
                            }
                            var msg = (parsed && parsed.msg) ? parsed.msg : out;
                            // v15：如实抛出错误，不做"参数校验失败→重抓csrf重试"的假设。
                            // （"参数校验失败"在孤儿状态/会话错乱下会无限重试，属误导；统一交给上层判定。）
                            if (parsed && parsed.msg) throw new Error('分片 ' + chunkIndex + ' 上传失败: ' + parsed.msg + '（' + out.substring(0, 120) + '）');
                            throw new Error('分片 ' + chunkIndex + ' 上传失败: ' + out.substring(0, 200));
                        });
                    }
                    return doUpload(csrf);
                });
            });
    }


    // ==================================================================
    // v16 上传内核重写：分片在 Linux 侧落盘，curl 直接从磁盘读，
    //                  全程不进 JS 内存、不进命令行参数
    //
    // 【旧实现为什么总卡住】v15 及以前的链路是：
    //     python3 读 8MB → base64 字符串(≈11MB) → 回传 JS
    //     → printf %s '<11MB字符串>' > 文件 → base64 -d → curl -F 上传
    //   三个致命点：
    //   ① Linux 单个命令行参数上限 MAX_ARG_STRLEN = 128KB，
    //      11MB 的参数必然触发 E2BIG / 被 shell 卡死 —— 这就是"卡住"的主因
    //   ② 11MB 字符串在 JS 侧进两次，又慢又吃内存，且过程中零进度反馈
    //   ③ csrf 全程只抓一次且不刷新，大包传几十分钟 session 过期后分片全废
    //
    // 【v16 做法】
    //   dd 直接在 Linux 侧把分片切到临时文件 → curl -F file=@<分片文件>
    //   （curl 自己从磁盘流式读，JS 只经手几十字节的 JSON 结果）
    //   另外加了：假死检测、断点续传、进度落盘、csrf 失效自救
    // ==================================================================
    var PROGRESS_DIR = '/sdcard/Download/Operit/tuoke_out';

    function progressFile(md5) {
        return PROGRESS_DIR + '/.upload_progress_' + md5 + '.json';
    }

    function writeProgress(md5, obj) {
        var s = JSON.stringify(obj);
        var cmd = 'mkdir -p ' + shellQuote(PROGRESS_DIR) +
            ' && printf %s ' + shellQuote(s) + ' > ' + shellQuote(progressFile(md5)) + ' 2>/dev/null; true';
        return exec(cmd, 15000).then(function () { return obj; }, function () { return obj; });
    }

    function readProgress(md5) {
        return exec('cat ' + shellQuote(progressFile(md5)) + ' 2>/dev/null || echo {}', 15000)
            .then(function (r) {
                try { return JSON.parse(String(r.output || '').trim() || '{}'); }
                catch (e) { return {}; }
            }, function () { return {}; });
    }

    // 上传前连通性预检：别传了半天才发现连不上/没登录
    function checkReach() {
        return exec('curl -s -o /dev/null -w "%{http_code} %{time_total}" -m 15 ' +
                    shellQuote(BASE + '/upload.php'), 30000)
            .then(function (r) {
                var o = String(r.output || '').trim();
                var code = parseInt(o.split(/\s+/)[0], 10) || 0;
                var cost = o.split(/\s+/)[1] || '';
                if (code === 0) {
                    throw new Error('连不上 56.al（curl 无响应）。检查网络/代理后重试。');
                }
                return { httpCode: code, seconds: cost };
            }, function () {
                throw new Error('连不上 56.al（curl 执行失败）。检查网络/代理后重试。');
            });
    }

    // 在 Linux 侧切出第 i 片（0-based），落盘并返回路径与字节数
    function sliceChunk(linuxPath, tmpDir, i, chunkSize, size) {
        var out = tmpDir + '/part_' + (i + 1) + '.bin';
        var expect = Math.min(chunkSize, size - i * chunkSize);
        var cmd = 'dd if=' + shellQuote(linuxPath) + ' of=' + shellQuote(out) +
                  ' bs=' + chunkSize + ' skip=' + i + ' count=1 2>/dev/null; stat -c %s ' +
                  shellQuote(out) + ' 2>/dev/null';
        return exec(cmd, 180000).then(function (r) {
            var got = parseInt(String(r.output || '').trim().split('\n').pop(), 10) || 0;
            if (got !== expect) {
                throw new Error('分片 ' + (i + 1) + ' 切分异常：期望 ' + expect +
                                ' 字节，实际 ' + got + '。多半是存储空间不够了');
            }
            return { path: out, bytes: got };
        });
    }

    // curl 直传一个分片文件（curl 从磁盘读，不经过 JS）
    //   --speed-time 30 --speed-limit 1024：30 秒内速率低于 1KB/s 判定假死并放弃，
    //   交给上层重试 —— 专门治"进度条不动又不报错"的卡死
    function uploadPartFile(csrf, cookie, md5, chunkIndex, partPath, timeoutSec) {
        var ts = timeoutSec || 120;
        var cmd = 'curl -s -m ' + ts +
            ' --retry 2 --retry-delay 2 --retry-all-errors' +
            ' --speed-time 30 --speed-limit 1024' +
            ' -H ' + shellQuote('Cookie: ' + cookie) +
            " -H 'User-Agent: Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36'" +
            " -H 'X-Requested-With: XMLHttpRequest'" +
            " -H 'Referer: " + BASE + "/upload.php'" +
            ' -F ' + shellQuote('file=@' + partPath + ';type=application/octet-stream') +
            ' -F ' + shellQuote('hash=' + md5) +
            ' -F ' + shellQuote('chunk=' + chunkIndex) +
            ' -F ' + shellQuote('csrf_token=' + csrf) + ' ' +
            shellQuote(BASE + '/ajax.php?act=upload_part');
        return exec(cmd, (ts + 60) * 1000).then(function (r) {
            var out = String(r.output || '').trim();
            var p = null;
            try { p = JSON.parse(out); } catch (e) {}
            if (p && p.code === 1) return { ok: true, done: true, raw: out, msg: p.msg || '' };
            if (p && p.code === 0) return { ok: true, done: false, raw: out, msg: p.msg || '' };
            if (p && p.code === 403) return { ok: false, needLogin: true, raw: out, msg: p.msg || '登录态无效(403)' };
            return { ok: false, raw: out, msg: (p && p.msg) ? p.msg : (out.substring(0, 160) || '空响应（超时或网络中断）') };
        }, function (err) {
            return { ok: false, raw: String((err && err.output) || ''),
                     msg: String((err && err.message) || err).substring(0, 200) };
        });
    }

    // v17 新增：第三方通道上传（对齐官方 uploadnew.js 的 uploadThird）。
    // 官方在 pre_upload 返回 third=true 时，把整个文件一次 POST 到 result.url，
    // 表单字段来自 result.post，最后必须再调 complete_upload 通知服务端。
    // 旧版完全没实现这个分支：一旦服务端返回 third，脚本还在傻傻地切分片往 ajax.php 传，
    // 服务端根本不认，表现就是"传到一半卡住 / 永远传不完"。
    function uploadThirdFile(cookie, url, post, linuxPath, timeoutSec) {
        var ts = Math.max(600, (parseInt(timeoutSec, 10) || 120) * 5);
        var parts = [];
        Object.keys(post || {}).forEach(function (k) {
            var v = post[k];
            if (v === null || v === undefined) return;
            parts.push(' -F ' + shellQuote(String(k) + '=' + String(v)));
        });
        parts.push(' -F ' + shellQuote('file=@' + linuxPath + ';type=application/octet-stream'));
        var cmd = 'curl -s -m ' + ts +
            ' --retry 2 --retry-delay 2 --retry-all-errors' +
            ' --speed-time 30 --speed-limit 1024' +
            (cookie ? (' -H ' + shellQuote('Cookie: ' + cookie)) : '') +
            " -H 'User-Agent: Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36'" +
            parts.join('') + ' ' + shellQuote(url);
        return exec(cmd, (ts + 60) * 1000).then(function (r) {
            return { ok: true, raw: String(r.output || '').substring(0, 300) };
        }, function (err) {
            return { ok: false, msg: String((err && err.message) || err).substring(0, 200) };
        });
    }

    function cleanupParts(tmpDir) {
        return exec('rm -rf ' + shellQuote(tmpDir) + ' 2>/dev/null; true', 30000)
            .then(function () { return true; }, function () { return false; });
    }

    // 分片主循环：断点续传 + 失败重试 + csrf 失效自救 + 进度落盘
    function uploadAllChunks(o) {
        var total = Math.ceil(o.size / o.chunkSize);
        var chunkSize = o.chunkSize;          // v17：可被服务端下发的 chunksize 覆盖
        var serverChunkSize = 0;              // 服务端返回的单片字节数
        var serverChunks = 0;                 // 服务端返回的总片数
        var chunkSource = 'local';            // server | local
        var thirdInfo = null;                 // v17：第三方通道
        var thirdUsed = false;
        var received = {};        // 服务端已收到的分片（1-based）
        var log = [];
        var csrf = o.csrf;
        var serverDone = false;   // 服务端是否在最后一片返回 code:1（已自动完成）
        var instantHit = false;   // 秒传命中
        var csrfRefreshed = false;

        function askReceived() {
            return preUpload(csrf, o.name, o.md5, o.size).then(function (pre) {
                if (pre.instant) return true;
                // ★ v17 关键修正：官方 uploadnew.js 用 var chunkSize = result.chunksize;
                //   分片大小是【服务端说了算】，客户端不能自己定。
                //   旧版脚本自己用 4MB 切片，若服务端按 2MB（或其他值）拼装，
                //   分片号与写入偏移就对不上，服务端永远等不齐 chunks 片 → 直接表现为"上传卡住"。
                //   现在以服务端为准；只有服务端没给，才退回本地 chunk_mb。
                if (pre.chunksize && parseInt(pre.chunksize, 10) > 0) serverChunkSize = parseInt(pre.chunksize, 10);
                if (pre.chunks && parseInt(pre.chunks, 10) > 0) serverChunks = parseInt(pre.chunks, 10);
                if (pre.third && pre.url) thirdInfo = { url: pre.url, post: pre.post || {} };
                (pre.received || []).forEach(function (i) { received[i] = true; });
                return false;
            });
        }

        function uploadWithRetry(i) {
            var idx = i + 1;
            var tries = 0;
            function attempt() {
                tries++;
                return sliceChunk(o.linuxPath, o.tmpDir, i, chunkSize, o.size)
                    .then(function (sl) {
                        return uploadPartFile(csrf, o.cookie, o.md5, idx, sl.path, o.timeoutSec);
                    })
                    .then(function (res) {
                        if (res.ok) return res;
                        // 403 / csrf 失效：刷新一次 csrf 再从头试这一片
                        if (res.needLogin && !csrfRefreshed) {
                            csrfRefreshed = true;
                            return fetchCsrf().then(function (nc) {
                                csrf = nc;
                                return attempt();
                            }, function () { return res; });
                        }
                        if (tries >= 3) return res;
                        // 重试前先问服务端：也许这片其实已经收到了
                        return askReceived().then(function (hit) {
                            if (hit || received[idx]) {
                                return { ok: true, done: !!hit, skipped: true, msg: '重试时发现服务端已收到' };
                            }
                            return new Promise(function (resolve) {
                                setTimeout(function () { resolve(attempt()); }, 2000 * tries);
                            });
                        }, function () {
                            return new Promise(function (resolve) {
                                setTimeout(function () { resolve(attempt()); }, 2000 * tries);
                            });
                        });
                    });
            }
            return attempt();
        }

        function step(i) {
            if (i >= total || serverDone || instantHit) return Promise.resolve();
            var idx = i + 1;
            if (received[idx]) {
                log.push({ chunk: idx, state: 'skip', note: '服务端已收到，跳过' });
                return step(i + 1);
            }
            return uploadWithRetry(i).then(function (res) {
                log.push({ chunk: idx, state: res.ok ? (res.done ? 'done' : 'ok') : 'fail',
                           note: res.msg || '' });
                if (res.ok) {
                    received[idx] = true;
                    if (res.done) serverDone = true;
                }
                return writeProgress(o.md5, {
                    md5: o.md5, name: o.name, size: o.size, chunkSize: chunkSize,
                    totalChunks: total, doneChunks: Object.keys(received).length,
                    serverDone: serverDone, instantHit: instantHit,
                    updatedAt: Date.now()
                }).then(function () { return step(i + 1); });
            });
        }

        return ensureDir()
            .then(function () { return exec('mkdir -p ' + shellQuote(o.tmpDir), 15000); })
            .then(function () {
                return askReceived().then(function (hit) {
                    instantHit = hit;
                    if (hit) return;
                    // 以服务端分片参数为准（除非用户显式传了 chunk_mb 覆盖）
                    if (!o.forceChunk && serverChunkSize > 0) {
                        chunkSize = serverChunkSize;
                        chunkSource = 'server';
                    }
                    if (!o.forceChunk && serverChunks > 0) {
                        total = serverChunks;
                    } else {
                        total = Math.ceil(o.size / chunkSize);
                    }
                });
            })
            .then(function () {
                // v17：命中第三方通道则整文件一次 POST，不走分片
                if (thirdInfo && thirdInfo.url) {
                    thirdUsed = true;
                    return uploadThirdFile(o.cookie, thirdInfo.url, thirdInfo.post, o.linuxPath, o.timeoutSec)
                        .then(function (r) {
                            if (!r.ok) throw new Error('第三方通道上传失败：' + r.msg);
                            return completeUpload(csrf, o.md5).then(function () { serverDone = true; });
                        });
                }
                return step(0);
            })
            .then(function () {
                var okCount = log.filter(function (x) {
                    return x.state === 'ok' || x.state === 'done' || x.state === 'skip';
                }).length;
                var fails = log.filter(function (x) { return x.state === 'fail'; });
                return {
                    instant: instantHit,
                    serverDone: serverDone,
                    totalChunks: total,
                    okChunks: okCount,
                    failedChunks: fails,
                    csrfRefreshed: csrfRefreshed,
                    third: thirdUsed,
                    thirdUrl: thirdInfo ? thirdInfo.url : '',
                    chunkSize: chunkSize,
                    chunkSource: (thirdUsed ? 'third' : chunkSource),
                    serverChunkSize: serverChunkSize,
                    serverChunks: serverChunks,
                    log: log
                };
            });
    }

    // 统一上传入口（小包大包走同一内核）
    function doUploadFile(params) {
        var apkPath = params.apk_path;
        if (!apkPath) throw new Error('缺少 apk_path');
        var linuxPath = toLinuxPath(apkPath);
        var name = apkPath.split('/').pop();
        // v17：官方 upload.php 页面变量 var upload_max_filesize = '3000'（单位 MB）。
        var MAX_MB = 3000;
        // v17：默认不再自己定分片大小，跟随服务端 pre_upload 下发的 chunksize（官方行为）。
        // 只有显式传了 chunk_mb 才强制覆盖（网络极差时的兜底手段）。
        var chunkMB = params.chunk_mb ? parseFloat(params.chunk_mb) : 0;
        var forceChunk = chunkMB > 0;
        var chunkSize = forceChunk ? Math.max(1048576, Math.floor(chunkMB * 1048576)) : 4194304;
        var timeoutSec = parseInt(params.timeout_sec, 10) || 120; // 单片 curl 超时（原 60s 太短）

        return checkReach().then(function (reach) {
            return exec('stat -c %s ' + shellQuote(linuxPath), 15000).then(function (st) {
                var size = parseInt(st.output.trim(), 10);
                if (!size) throw new Error('无法获取文件大小（文件不存在或为空）: ' + apkPath);
                if (size > MAX_MB * 1048576) {
                    throw new Error('文件 ' + (size / 1048576).toFixed(1) + 'MB，超过 56.al 上传上限 '
                        + MAX_MB + 'MB（官方 upload.php 页面变量 upload_max_filesize）。请换小一点的包。');
                }
                return linuxMd5(linuxPath).then(function (md5) {
                    var tmpDir = '/sdcard/Download/Operit/tuoke_out/_parts_' + md5;
                    var csrfPromise = params.csrf_token ? Promise.resolve(params.csrf_token) : fetchCsrf();
                    return csrfPromise.then(function (csrf0) {
                        return loadCookie().then(function (cookie) {
                            if (!cookie) {
                                var e = new Error('未检测到登录 Cookie，请先 tuoke_login_url + tuoke_set_cookie');
                                e.needLogin = true;
                                throw e;
                            }
                            return uploadAllChunks({
                                md5: md5, name: name, linuxPath: linuxPath, size: size,
                                chunkSize: chunkSize, tmpDir: tmpDir, cookie: cookie,
                                csrf: csrf0, timeoutSec: timeoutSec, forceChunk: forceChunk
                            }).then(function (r) {
                                if (r.failedChunks && r.failedChunks.length) {
                                    return cleanupParts(tmpDir).then(function () {
                                        return {
                                            hash: md5, name: name, size: size, md5: md5,
                                            completed: false, interrupted: true,
                                            reason: 'chunk_failed',
                                            reach: reach,
                                            progress: { total: r.totalChunks, ok: r.okChunks,
                                                        failed: r.failedChunks },
                                            hint: '有 ' + r.failedChunks.length + ' 个分片没传上去（网络中断/超时）。'
                                                + '不用从头来，直接跑 tuoke_upload_resume(apk_path="' + apkPath + '") 从断点续传；'
                                                + '要是反复断，把 chunk_mb 调小（比如 2）再试。'
                                        };
                                    });
                                }
                                return ensureTaskExists(md5).then(function (chk) {
                                    return cleanupParts(tmpDir).then(function () {
                                        return {
                                            hash: md5, name: name, size: size, md5: md5,
                                            instant: r.instant, completed: true,
                                            autoCompleteByServer: r.serverDone,
                                            thirdChannel: r.third,
                                            chunkMB: (r.chunkSize / 1048576),
                                            chunkSource: r.chunkSource,
                                            serverChunks: r.serverChunks,
                                            totalChunks: r.totalChunks,
                                            okChunks: r.okChunks,
                                            csrfRefreshed: r.csrfRefreshed,
                                            reach: reach,
                                            taskExists: chk.taskExists,
                                            status: chk.status,
                                            triggered: chk.triggered,
                                            hint: chk.taskExists
                                                ? ('上传完成，任务状态 ' + chk.status + '。接着跑 tuoke_status(hash="' + md5 + '") 查进度')
                                                : '分片传完了但任务没建起来。先跑一次 tuoke_upload_resume 再确认，或用 tuoke_status 查一下。'
                                        };
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    function b64ToArrayBuffer(b64) {
        var bin = atob(b64);
        var len = bin.length;
        var bytes = new Uint8Array(len);
        for (var i = 0; i < len; i++) bytes[i] = bin.charCodeAt(i);
        return bytes.buffer;
    }

    function md5OfBase64(b64) {
        var buf = b64ToArrayBuffer(b64);
        var words = new Uint32Array(buf.byteLength / 4);
        var dv = new DataView(buf);
        for (var i = 0; i < words.length; i++) words[i] = dv.getUint32(i * 4, false);
        return CryptoJS.MD5({ words: Array.prototype.slice.call(words), sigBytes: buf.byteLength }).toString();
    }

    function linuxMd5(linuxPath) {
        return exec('md5sum ' + shellQuote(linuxPath) + ' | awk \'{print $1}\'', 120000)
            .then(function (r) {
                var raw = String(r.output || '').trim();
                // 保险：万一 awk 不可用，md5sum 会输出 "<md5>  <路径>"，用正则抽出 32 位十六进制
                var m = raw.match(/[0-9a-fA-F]{32}/);
                var v = m ? m[0] : raw.split(/\s+/)[0];
                if (!/^[0-9a-fA-F]{32}$/.test(v)) {
                    throw new Error('计算 MD5 失败（md5 就是任务 hash，错了续传会对不上）：' + raw.substring(0, 100));
                }
                return v;
            });
    }

    function completeUpload(csrf, md5) {
        var body = 'hash=' + md5 + '&csrf_token=' + encodeURIComponent(csrf);
        return authedFormPost(BASE + '/ajax.php?act=complete_upload', body).then(function (data) {
            if (data.code === 403) throw new Error('登录态无效（complete_upload 返回 403）。请重新登录。');
            if (data.code !== 0 && data.code !== 1) throw new Error('complete_upload 失败: ' + JSON.stringify(data));
            return data;
        });
    }
    // complete_upload 的容错包装：即使返回错误（CSRF/参数校验），任务也可能已在服务端创建，
    // 因此只记录警告不中断流程，后续通过 tuoke_status 查询实际任务状态。
    function completeUploadSafe(csrf, md5) {
        return completeUpload(csrf, md5).catch(function (err) {
            return { code: -1, warning: String(err && err.message || err) };
        });
    }
    // ========== 工具0: tuoke_browser_login（内置浏览器自动登录） ==========
    function tuoke_browser_login(params) {
        var openLogin = !params || params.open_login_page !== false;
        // 1. 打开 56.al 登录页（让用户在浏览器里完成登录）
        var navPromise = openLogin
            ? Tools.Net.browserNavigate(BASE + '/login.php')
            : Promise.resolve('skip');
        return navPromise.then(function () {
            // 2. 等用户登录（最多等 120 秒），轮询读取 cookie
            var deadline = Date.now() + 120000;
            function poll() {
                return Tools.Net.browserEvaluate({ function: '() => document.cookie' })
                    .then(function (ev) {
                        var text = String(ev || '');
                        // browserEvaluate 返回的内容里包含 Result 行
                        var m = text.match(/### Result\n([\s\S]*?)(?:\n\n###|\n$|$)/);
                        var cookieStr = m ? m[1].trim() : text.trim();
                        if (!cookieStr || cookieStr.indexOf('PHPSESSID') < 0) {
                            if (Date.now() > deadline) {
                                throw new Error('等待超时：未在浏览器中检测到 56.al 登录 Cookie。请先在打开的浏览器页面完成登录后重试，或使用 tuoke_set_cookie 手动粘贴。');
                            }
                            return new Promise(function (resolve) {
                                setTimeout(function () { resolve(poll()); }, 5000);
                            });
                        }
                        return cookieStr;
                    });
            }
            return poll();
        }).then(function (cookieStr) {
            // 3. 提取 PHPSESSID 和完整 cookie，保存
            var m = cookieStr.match(/PHPSESSID=[^;\s]+/i);
            if (!m) throw new Error('浏览器 Cookie 中未找到 PHPSESSID：' + cookieStr.substring(0, 120));
            return saveCookie(cookieStr).then(function () {
                // 4. 验证登录态
                var sess = extractPhpSessId(cookieStr);
                return authedGet(BASE + '/upload.php').then(function (resp) {
                    var html = respText(resp);
                    var cm = html.match(/id="csrf_token"\s+value="([a-f0-9]{32})"/i) ||
                             html.match(/name="csrf_token"\s+value="([a-f0-9]{32})"/i);
                    if (!cm) {
                        return { saved: true, valid: false, phpsessid: sess ? sess.substring(0, 6) + '...' : null, hint: 'Cookie 已从浏览器保存，但上传页无 csrf_token，登录态可能无效。' };
                    }
                    var body = 'csrf_token=' + cm[1] + '&name=verify.apk&hash=' + '0'.repeat(32) + '&size=1&show=1&ispwd=0&pwd=';
                    return authedFormPost(BASE + '/ajax.php?act=pre_upload', body).then(function (parsed) {
                        if (parsed && parsed.code === 403) {
                            return { saved: true, valid: false, phpsessid: sess ? sess.substring(0, 6) + '...' : null, preUpload: parsed, hint: '浏览器 Cookie 已保存，但登录态无效（403）。请确认浏览器中已成功登录 56.al 后重试。' };
                        }
                        return { saved: true, valid: true, phpsessid: sess ? sess.substring(0, 6) + '...' : null, preUpload: parsed, hint: '登录成功！Cookie 已从浏览器自动保存，可直接使用上传功能。' };
                    });
                });
            });
        });
    }

    // ========== 工具0: tuoke_login_url ==========
    function tuoke_login_url(params) {
        var provider = params.provider || 'github';
        var valid = { github: 1, qq: 1, gitee: 1, microsoft: 1, wx: 1 };
        if (!valid[provider]) throw new Error('不支持的登录方式: ' + provider + '（可选: github/qq/gitee/microsoft/wx）');
        return plainFormPost(BASE + '/login.php?act=connect', 'type=' + encodeURIComponent(provider) + '&app=0').then(function (data) {
            if (data.code !== 0 || !data.url) throw new Error('获取授权链接失败: ' + JSON.stringify(data));
            return {
                provider: provider,
                auth_url: data.url,
                steps: [
                    '1. 用浏览器打开上面的授权链接',
                    '2. 完成第三方账号登录/授权（页面会跳转到 56.al 并自动登录）',
                    '3. 登录成功后，从浏览器复制 Cookie（F12 → Network → 任意 56.al 请求 → Request Headers → Cookie）',
                    '4. 调用 tuoke_set_cookie 粘贴 Cookie 完成包内登录（只需一次，之后自动复用）'
                ],
                tip: 'Cookie 形如 PHPSESSID=xxxxxx；请妥善保管，不要分享给他人。'
            };
        });
    }
    // ========== 工具0b: tuoke_set_cookie ==========
    function tuoke_set_cookie(params) {
        var cookie = params.cookie;
        if (!cookie || !/PHPSESSID/i.test(cookie)) {
            throw new Error('Cookie 无效：未找到 PHPSESSID。请粘贴浏览器里的完整 Cookie 字符串（形如 PHPSESSID=xxxx; 其他=yyy）。');
        }
        return saveCookie(cookie).then(function () {
            return loadCookie().then(function (saved) {
                return authedGet(BASE + '/upload.php').then(function (resp) {
                    var html = respText(resp);
                    var m = html.match(/id="csrf_token"\s+value="([a-f0-9]{32})"/i) ||
                            html.match(/name="csrf_token"\s+value="([a-f0-9]{32})"/i);
                    if (!m) throw new Error('Cookie 已保存，但无法从上传页解析 csrf_token，请确认 Cookie 有效。');
                    var csrf = m[1];
                    var body = 'csrf_token=' + csrf + '&name=verify.apk&hash=' + '0'.repeat(32) + '&size=1&show=1&ispwd=0&pwd=';
                    return authedFormPost(BASE + '/ajax.php?act=pre_upload', body).then(function (parsed) {
                        var sess = extractPhpSessId(cookie);
                        if (parsed && parsed.code === 403) {
                            return { saved: true, valid: false, phpsessid: sess ? sess.substring(0, 6) + '...' : null, preUpload: parsed, hint: '登录态无效（403）。Cookie 可能已过期，请重新 tuoke_login_url 登录。' };
                        }
                        return { saved: true, valid: true, phpsessid: sess ? sess.substring(0, 6) + '...' : null, preUpload: parsed, hint: '登录成功，Cookie 已保存并持久化。' };
                    });
                });
            });
        });
    }

    // ========== 工具0c: tuoke_get_cookie ==========
    function tuoke_get_cookie() {
        return loadCookie().then(function (cookie) {
            var embedded = (cookie === EMBEDDED_COOKIE);
            if (!cookie) {
                return { saved: false, hint: '尚未保存 Cookie。请先 tuoke_login_url 获取授权链接并登录，再 tuoke_set_cookie 粘贴。' };
            }
            var sess = extractPhpSessId(cookie);
            var summary = {
                saved: true,
                embedded: embedded,
                phpsessid: sess ? sess.substring(0, 6) + '...' : null,
                keys: cookie.split(';').map(function (s) { return s.trim().split('=')[0]; }).filter(Boolean),
                cookieFile: COOKIE_FILE,
                hint: embedded ? '使用包内嵌 Cookie（开箱即用）。' : '使用已保存的 Cookie 文件。'
            };
            return authedGet(BASE + '/upload.php').then(function (resp) {
                var html = respText(resp);
                var m = html.match(/id="csrf_token"\s+value="([a-f0-9]{32})"/i) ||
                        html.match(/name="csrf_token"\s+value="([a-f0-9]{32})"/i);
                if (!m) {
                    summary.valid = false;
                    summary.hint = 'Cookie 已存在，但无法解析上传页 csrf_token，可能已失效。';
                    return summary;
                }
                var csrf = m[1];
                var body = 'csrf_token=' + csrf + '&name=verify.apk&hash=' + '0'.repeat(32) + '&size=1&show=1&ispwd=0&pwd=';
                return authedFormPost(BASE + '/ajax.php?act=pre_upload', body).then(function (parsed) {
                    summary.preUpload = parsed;
                    if (parsed && parsed.code === 403) {
                        summary.valid = false;
                        summary.hint = '登录态无效（403）。请重新 tuoke_login_url + tuoke_set_cookie。';
                    } else {
                        summary.valid = true;
                        summary.hint = '登录态有效，可直接使用上传功能。';
                    }
                    return summary;
                });
            });
        });
    }

    // ========== 工具0d: tuoke_clear_cookie ==========
    function tuoke_clear_cookie() {
        return clearCookie().then(function () {
            return { cleared: true, hint: '已清除 56.al 登录 Cookie。' };
        });
    }
    // ========== 工具1: tuoke_upload（小包兼容入口，内部与大包同一内核） ==========
    function tuoke_upload(params) {
        return doUploadFile(params);
    }

    // ========== 工具1b: tuoke_upload_big（大文件） ==========
    function tuoke_upload_big(params) {
        return doUploadFile(params);
    }

    // ========== 工具1c: tuoke_upload_resume（上传卡住后从断点续传） ==========
    // 原理：56.al 的 pre_upload 会返回服务端已收到的分片列表，
    //       所以拿同一个 APK 再跑一次就会自动跳过已传部分。
    //       不用从头来，也不用记 hash。
    function tuoke_upload_resume(params) {
        var apkPath = params.apk_path;
        if (!apkPath) throw new Error('缺少 apk_path（必须和第一次上传时是同一个文件，路径也要一样）');
        return doUploadFile(params).then(function (r) {
            r.resumed = true;
            if (r.completed) {
                r.hint = '续传完成。接着跑 tuoke_status(hash="' + r.hash + '") 查脱壳进度。';
            }
            return r;
        });
    }

    // ========== 工具1d: tuoke_upload_progress（查看上次上传进度） ==========
    function tuoke_upload_progress(params) {
        var md5 = params.md5 || params.hash;
        if (!md5) throw new Error('缺少 md5（就是上传任务的 hash）');
        return readProgress(md5).then(function (p) {
            if (!p || !p.totalChunks) {
                return { found: false, md5: md5, hint: '没找到这个任务的上传进度记录。' };
            }
            var pct = Math.round((p.doneChunks || 0) / p.totalChunks * 100);
            return {
                found: true,
                md5: md5,
                name: p.name,
                size: p.size,
                totalChunks: p.totalChunks,
                doneChunks: p.doneChunks,
                percent: pct,
                serverDone: !!p.serverDone,
                instantHit: !!p.instantHit,
                lastUpdate: p.updatedAt ? new Date(p.updatedAt).toLocaleString() : null,
                hint: '上次传到 ' + pct + '%（' + p.doneChunks + '/' + p.totalChunks + ' 片）。'
                    + '要续传就跑 tuoke_upload_resume(apk_path="<同一个 APK 的完整路径>")。'
            };
        });
    }

    // ========== 工具2: tuoke_status ==========
    function tuoke_status(params) {
        var hash = params.hash;
        var sinceId = params.since_id || 0;
        if (!hash) throw new Error('缺少 hash');
        return Tools.Net.httpGet(BASE + '/api_task.php?hash=' + encodeURIComponent(hash) + '&since_id=' + sinceId)
            .then(function (resp) {
                var data = JSON.parse(respText(resp));
                if (!data.exists) throw new Error(data.msg || '任务不存在');
                return {
                    hash: hash,
                    status: data.status,
                    exists: true,
                    logs: (data.logs || []).map(function (l) {
                        return { id: l.id, time: l.time, content: l.content };
                    }),
                    filetime: data.filetime || 0
                };
            });
    }

    // ========== 工具3: tuoke_download（Linux 流式下载 + 7z 解压 + dex 修复） ==========
    function tuoke_download(params) {
        var hash = params.hash;
        var skipUnpack = params.skip_unpack === true;
        if (!hash) throw new Error('缺少 hash');
        var outDir = params.output_dir || (params.apk_path ? deriveOutDir(params.apk_path) : '/sdcard/Download/Operit/tuoke_out/' + hash);
        var linuxTmp = '/sdcard/Download/Operit/tuoke_out/_tmp_' + hash;
        var sevenPath = linuxTmp + '/' + hash + '.7z';

        return Tools.Net.httpGet(BASE + '/api_task.php?hash=' + encodeURIComponent(hash) + '&since_id=0')
            .then(function (resp) {
                var data = JSON.parse(respText(resp));
                if (!data.exists) throw new Error('任务不存在');
                if (data.status !== 'success') throw new Error('任务未完成，当前状态: ' + data.status);
            })
            .then(function () {
                return exec('mkdir -p ' + shellQuote(linuxTmp), 15000);
            })
            .then(function () {
                var dlUrl = BASE + '/api_download.php?url=' + encodeURIComponent(hash);
                // --http1.1：下载接口 302 到 OneDrive，HTTP/2 跟随重定向会失败（curl exit 92）
                // --retry-all-errors + --retry + -C -：网络瞬断（Recv failure/exit56）自动重试并断点续传
                return exec('curl -L --http1.1 --fail --retry 8 --retry-delay 3 --retry-all-errors -C - --max-time 300 -o ' + shellQuote(sevenPath) + ' ' + shellQuote(dlUrl), 660000);
            })
            .then(function () {
                // 用 stat 精确获取文件大小（不用 ls 正则，避免误解析）
                return exec('stat -c %s ' + shellQuote(sevenPath), 15000);
            })
            .then(function (statr) {
                var dlSize = parseInt(statr.output.trim(), 10) || 0;
                var note = '已下载 7z 到: ' + sevenPath + ' (大小 ' + dlSize + 'B)';
                if (skipUnpack) {
                    return { hash: hash, savedPath: sevenPath, size: dlSize, note: note + '（skip_unpack=true，未解压）' };
                }
                return unpack7z(linuxTmp, sevenPath).then(function (unpacked) {
                    return fixDexInDir(linuxTmp).then(function (dexFixed) {
                        // 产物落地：递归复制解压出的 dex / txt / 7z 到 outDir（默认目标 APK 同目录；dex 可能在子目录 dex_out/ 内）
                        var outDirSh = shellQuote(outDir);
                        return exec('mkdir -p ' + outDirSh, 15000).then(function () {
                            var copyCmd = 'cp -f ' + shellQuote(linuxTmp + '/' + hash + '.7z') + ' ' + outDirSh +
                                '; find ' + shellQuote(linuxTmp) + ' -name "*.dex" -exec cp -f {} ' + outDirSh + ' \\;' +
                                '; find ' + shellQuote(linuxTmp) + ' -maxdepth 2 -name "*.txt" -exec cp -f {} ' + outDirSh + ' \\;' +
                                '; true';
                            return exec(copyCmd, 30000).then(function () {
                                return exec('ls ' + outDirSh + ' | grep -E "(\\.dex|\\.txt|\\.7z)$" | grep -F "' + hash + '" || ls ' + outDirSh + ' | grep -E "(\\.dex|\\.txt|\\.7z)$"', 15000);
                            }).then(function (lsr) {
                                var files = lsr.output.split('\n').filter(function (s) { return s.trim(); });
                                // v9: 自动跑 dexlib2 规范化（等价 MT 管理器 dex 修复），输出到 outDir/修复dex/
                                var cleanOut = outDir + '/修复dex';
                                return exec('mkdir -p ' + shellQuote(cleanOut) + ' && find ' + shellQuote(linuxTmp) + ' -name "*.dex" -type f | sort', 30000)
                                    .then(function (fr) {
                                        var dexFiles = fr.output.split('\n').map(function (s) { return s.trim(); }).filter(function (s) { return s; });
                                        var pairs = dexFiles.map(function (f) {
                                            return [f, cleanOut + '/' + f.split('/').pop()];
                                        });
                                        return runDexClean(pairs).then(function (cleanRes) {
                                            return {
                                                hash: hash,
                                                savedPath: sevenPath,
                                                size: dlSize,
                                                unpackDir: linuxTmp,
                                                outputDir: outDir,
                                                unpacked: unpacked,
                                                dexFixed: dexFixed,
                                                outputFiles: files,
                                                dexClean: { outputDir: cleanOut, cleaned: cleanRes.length, files: cleanRes },
                                                note: '脱壳完成。产物已复制到: ' + outDir + '（dex/txt/7z，dex 头已修复）；dexlib2 规范化修复版（等价 MT dex 修复）在 ' + cleanOut + '。'
                                            };
                                        });
                                    });
                            });
                        });
                    });
                });
            });
    }

    // 7z 解压（密码 dump），返回文件列表；失败时输出真实错误
    // 先探测真实格式（56.al 的"7z"实际可能是 zip/rar），再按格式解压
    function unpack7z(dir, sevenPath) {
        // 56.al 产物实际结构（实测）：
        //   外层 = 真 7z（LZMA2 + 7zAES，密码 dump），内含 3 个 txt + 内层同名 .7z
        //   内层 = zip 变体（7-Zip 0.4，unzip 不认，7za 可识别），内含 classes.dex
        // 注意：/usr/bin/7z 的 l 命令在 proot 环境会 hang，必须用 7za 且加 timeout；
        //       内层归档与外层同名，必须先解压到 extract/ 子目录避免覆盖。
        var extractDir = dir + '/extract';
        return exec('mkdir -p ' + shellQuote(extractDir), 15000)
            .then(function () {
                // 解压外层（7za 自动识别 7z/zip 变体）
                return exec('cd ' + shellQuote(dir) + ' && timeout 240 /usr/bin/7za x -p' + PASSWORD + ' -y -oextract ' + shellQuote(sevenPath) + ' >/dev/null 2>&1; ec=$?; echo "7z_exit=$ec"; [ $ec -eq 0 ]', 300000);
            })
            .then(function () {
                // 递归解压内层归档（最多 3 层），7za 自动识别格式
                return exec('cd ' + shellQuote(dir) + ' && for layer in 1 2 3; do found=$(find extract -type f \\( -name "*.7z" -o -name "*.zip" \\) 2>/dev/null); [ -z "$found" ] && break; for f in $found; do d=$(dirname "$f"); timeout 240 /usr/bin/7za x -p' + PASSWORD + ' -y -o"$d" "$f" >/dev/null 2>&1; done; done; echo done', 600000);
            })
            .then(function (r) {
                return exec('find ' + shellQuote(dir) + ' -type f | sed \'s|^' + dir + '/||\'', 30000);
            })
            .then(function (r) {
                return r.output.split('\n').filter(function (s) { return s.trim(); });
            })
            .catch(function (e) {
                throw new Error('7z 解压失败: ' + ((e && e.output || e && e.message) || '').substring(0, 300));
            });
    }

    // 递归查找 dex 并修复头（python 流式）
    function fixDexInDir(dir) {
        var py = String.raw`
import sys, os, struct
def fix(path):
    sz = os.path.getsize(path)
    if sz < 112:
        return (path, 'too_small')
    with open(path, 'r+b') as f:
        head = f.read(112)
        if head[0:8] != b'dex\n035\0':
            return (path, 'not_dex_magic')
        if sz >= 0xFFFFFFFF:
            return (path, 'large_file')
        f.seek(0)
        f.write(b'dex\n035\0')
        f.seek(32)
        f.write(struct.pack('<I', sz))
        f.seek(36)
        f.write(struct.pack('<I', 112))
    return (path, 'fixed')
results = []
for root, _, files in os.walk(sys.argv[1]):
    for fn in files:
        if fn.endswith('.dex') or fn.endswith('.DEX'):
            p = os.path.join(root, fn)
            try:
                results.append(fix(p))
            except Exception as e:
                results.append((p, 'error:' + str(e)))
for p, st in results:
    print(p + '|' + st)
`;
        return exec('python3 -c ' + shellQuote(py) + ' ' + shellQuote(dir), 120000)
            .then(function (r) {
                return r.output.split('\n').filter(function (s) { return s.trim(); }).map(function (line) {
                    var i = line.lastIndexOf('|');
                    return { file: line.substring(0, i), status: line.substring(i + 1) };
                });
            });
    }
    // ========== 工具4: tuoke_all ==========
    function tuoke_all(params) {
        var apkPath = params.apk_path;
        var maxWait = params.max_wait_seconds || 1800;
        if (!apkPath) throw new Error('缺少 apk_path');
        var start = Date.now();
        return exec('stat -c %s ' + shellQuote(toLinuxPath(apkPath)), 15000)
            .then(function (st) {
                var size = parseInt(st.output.trim(), 10);
                var uploadFn = size > CHUNK_SIZE ? tuoke_upload_big : tuoke_upload;
                return uploadFn({ apk_path: apkPath }).then(function (uploadRes) {
                    var hash = uploadRes.hash;
                    var outDir = params.output_dir || deriveOutDir(apkPath) || dirname(apkPath);
                    // v15: 秒传命中(instant)且任务存在 -> 直接取历史结果。
                    // 孤儿（instant 且 taskExists=false）：官方协议 code:1 直接成功不再建任务，
                    // 本渠道无法重建（实测补传分片/新 csrf complete 均被服务端拒绝），如实中断并给换渠道指引。
                    if (uploadRes.instant && uploadRes.taskExists === false && uploadRes.orphan) {
                        return Promise.resolve({
                            hash: hash,
                            upload: uploadRes,
                            interrupted: true,
                            reason: "orphan_task",
                            retryable: false,
                            hint: uploadRes.hint || '文件已入库但脱壳任务不存在（孤儿记录）。官方 56.al 协议下无法通过上传渠道重建任务，请从云盘取回历史产物，或换用其他脱壳渠道/本地脱壳。',
                            summary: "云脱壳中断: 文件已入库但任务不存在（孤儿），本渠道无法重建"
                        });
                    }
                    function poll() {
                        return tuoke_status({ hash: hash }).then(function (s) {
                            if (s.status === 'success') return s;
                            if (s.status === 'error') throw new Error('任务异常: ' + JSON.stringify(s.logs.slice(-3)));
                            if (Date.now() - start > maxWait * 1000) throw new Error('等待超时(' + maxWait + 's)');
                            return new Promise(function (resolve) {
                                setTimeout(function () { resolve(poll()); }, 60000);
                            });
                        }, function (err) {
                            // 任务刚创建时查询可能瞬时不存在，容错重试；但连续不存在要留意孤儿状态
                            if (Date.now() - start > maxWait * 1000) {
                                var e2 = new Error('等待超时(' + maxWait + 's)，任务未建立。可能原因：complete 未成功创建任务（孤儿状态）。可手动查 ' + BASE + '/api_task.php?hash=' + hash + ' 确认。');
                                e2.orphanPossible = true;
                                throw e2;
                            }
                            return new Promise(function (resolve) {
                                setTimeout(function () { resolve(poll()); }, 60000);
                            });
                        });
                    }
                    return poll().then(function (finalSt) {
                        return tuoke_download({ hash: hash, output_dir: outDir, apk_path: apkPath }).then(function (dlRes) {
                            return {
                                hash: hash,
                                upload: uploadRes,
                                finalStatus: finalSt.status,
                                logs: finalSt.logs,
                                download: dlRes,
                                summary: '云脱壳完成: ' + apkPath + ' -> ' + dlRes.unpackDir + ' (解压密码: ' + PASSWORD + ')'
                            };
                        });
                    });
                });
            });
    }

    // ========== 工具5: tuoke_fix_dex ==========
    function tuoke_fix_dex(params) {
        var dexPath = params.dex_path;
        if (!dexPath) throw new Error('缺少 dex_path');
        var linuxPath = toLinuxPath(dexPath);
        var outPath = params.output_path ? toLinuxPath(params.output_path) : linuxPath;
        var py = String.raw`
import sys, os, struct
src = sys.argv[1]
dst = sys.argv[2]
sz = os.path.getsize(src)
if sz < 112:
    print('too_small'); sys.exit(1)
with open(src, 'rb') as f:
    head = f.read(112)
if head[0:8] != b'dex\n035\0':
    print('not_dex_magic'); sys.exit(1)
if src == dst:
    with open(dst, 'r+b') as f:
        f.seek(32); f.write(struct.pack('<I', sz))
        f.seek(36); f.write(struct.pack('<I', 112))
else:
    with open(src, 'rb') as fi, open(dst, 'wb') as fo:
        fo.write(fi.read())
    with open(dst, 'r+b') as f:
        f.seek(32); f.write(struct.pack('<I', sz))
        f.seek(36); f.write(struct.pack('<I', 112))
print('fixed:' + dst)
`;
        return exec('python3 -c ' + shellQuote(py) + ' ' + shellQuote(linuxPath) + ' ' + shellQuote(outPath), 120000)
            .then(function (r) {
                return { dexPath: outPath, result: r.output.trim() };
            });
    }

    // ========== 工具5b: tuoke_dex_clean（dexlib2 规范化重写 = MT 管理器 dex 修复） ==========
    var DEXCLEAN_DIR = '/sdcard/Download/Operit/dexclean';
    var DEXCLEAN_CP = 'dexlib2-2.5.2.jar:util-2.5.2.jar:guava-27.1-android.jar:jsr305-3.0.2.jar:.';
    // 对一组 dex 文件跑 DexClean（java 批量），返回 {in,out,ok,info} 列表
    function runDexClean(inOutPairs) {
        if (!inOutPairs.length) return Promise.resolve([]);
        var args = '30';
        inOutPairs.forEach(function (p) { args += ' ' + shellQuote(p[0]) + ' ' + shellQuote(p[1]); });
        return exec('cd ' + shellQuote(DEXCLEAN_DIR) + ' && java -Xmx1g -cp ' + shellQuote(DEXCLEAN_CP) + ' DexClean ' + args, 1200000)
            .then(function (r) {
                var lines = (r.output || '').split('\n').filter(function (s) { return s.trim(); });
                var results = [];
                lines.forEach(function (l) {
                    if (l.indexOf('OK classes=') !== 0) return;
                    var m = l.match(/classes=(\d+) in=(.+) out=(.+)/);
                    if (m) results.push({ classes: parseInt(m[1], 10), in: m[2], out: m[3], ok: true });
                });
                return results;
            }, function (err) {
                throw new Error('dexlib2 修复失败: ' + ((err && err.output || err && err.message) || '').substring(0, 300));
            });
    }
    function tuoke_dex_clean(params) {
        var dexPath = params.dex_path;
        if (!dexPath) throw new Error('缺少 dex_path');
        var linuxIn = toLinuxPath(dexPath);
        var outDir = params.output_dir ? toLinuxPath(params.output_dir) : null;
        return exec('if [ -d ' + shellQuote(linuxIn) + ' ]; then echo dir; else echo file; fi', 15000)
            .then(function (r) {
                var isDir = r.output.trim() === 'dir';
                var files = [];
                var base = linuxIn;
                if (isDir) {
                    if (!outDir) outDir = linuxIn.replace(/\/$/, '') + '/修复dex';
                } else {
                    var idx = linuxIn.lastIndexOf('/');
                    base = idx >= 0 ? linuxIn.substring(0, idx) : '.';
                    if (!outDir) outDir = base + '/修复dex';
                }
                return exec('mkdir -p ' + shellQuote(outDir) + ' && find ' + shellQuote(linuxIn) + ' -name "*.dex" -type f | sort', 30000)
                    .then(function (fr) {
                        fr.output.split('\n').forEach(function (s) {
                            var t = s.trim();
                            if (t) files.push(t);
                        });
                        if (!files.length) throw new Error('未找到 dex 文件: ' + dexPath);
                        var pairs = files.map(function (f) {
                            var name = f.split('/').pop();
                            return [f, outDir + '/' + name];
                        });
                        return runDexClean(pairs).then(function (results) {
                            return {
                                input: dexPath,
                                outputDir: outDir,
                                total: results.length,
                                cleaned: results.filter(function (x) { return x.ok; }).length,
                                files: results.map(function (x) { return { in: x.in, out: x.out, classes: x.classes }; }),
                                note: 'dexlib2 规范化完成（等价 MT dex 修复：签名/去重/紧凑重排）。输出目录: ' + outDir
                            };
                        });
                    });
            });
    }
    // ========== 工具6: tuoke_check_auth ==========
    function tuoke_check_auth() {
        return loadCookie().then(function (cookie) {
            if (!cookie) {
                return {
                    saved: false,
                    valid: false,
                    hint: '尚未保存 Cookie。请先 tuoke_login_url 获取授权链接并登录，再 tuoke_set_cookie 粘贴。',
                    loginGuide: 'tuoke_login_url(provider) → 浏览器授权 → 复制 Cookie → tuoke_set_cookie(cookie)'
                };
            }
            var sess = extractPhpSessId(cookie);
            var diag = {
                saved: true,
                phpsessid: sess ? sess.substring(0, 6) + '...' : null,
                cookieFile: COOKIE_FILE
            };
            return authedGet(BASE + '/upload.php').then(function (resp) {
                diag.pageStatus = resp.statusCode;
                var html = respText(resp);
                var m = html.match(/id="csrf_token"\s+value="([a-f0-9]{32})"/i) ||
                        html.match(/name="csrf_token"\s+value="([a-f0-9]{32})"/i);
                diag.pageHasCsrf = !!m;
                var csrf = m ? m[1] : '0';
                var body = 'csrf_token=' + csrf + '&name=verify.apk&hash=' + '0'.repeat(32) + '&size=1&show=1&ispwd=0&pwd=';
                return authedFormPost(BASE + '/ajax.php?act=pre_upload', body).then(function (parsed) {
                    diag.preUploadParsed = parsed;
                    if (parsed && parsed.code === 403) {
                        diag.valid = false;
                        diag.hint = '登录态无效（pre_upload 403）。Cookie 已过期/失效，请重新登录：tuoke_login_url → 浏览器授权 → tuoke_set_cookie。';
                    } else {
                        diag.valid = true;
                        diag.hint = '登录态有效，可直接使用上传功能。';
                    }
                    return diag;
                });
            });
        });
    }

    return {
        tuoke_browser_login: wrap(tuoke_browser_login),
        tuoke_login_url: wrap(tuoke_login_url),
        tuoke_set_cookie: wrap(tuoke_set_cookie),
        tuoke_get_cookie: wrap(tuoke_get_cookie),
        tuoke_clear_cookie: wrap(tuoke_clear_cookie),
        tuoke_upload: wrap(tuoke_upload),
        tuoke_upload_big: wrap(tuoke_upload_big),
        tuoke_upload_resume: wrap(tuoke_upload_resume),
        tuoke_upload_progress: wrap(tuoke_upload_progress),
        tuoke_status: wrap(tuoke_status),
        tuoke_download: wrap(tuoke_download),
        tuoke_all: wrap(tuoke_all),
        tuoke_fix_dex: wrap(tuoke_fix_dex),
        tuoke_check_auth: wrap(tuoke_check_auth),
        tuoke_dex_clean: wrap(tuoke_dex_clean)
    };
})();

exports.tuoke_browser_login = tuoke56.tuoke_browser_login;
exports.tuoke_login_url = tuoke56.tuoke_login_url;
exports.tuoke_set_cookie = tuoke56.tuoke_set_cookie;
exports.tuoke_get_cookie = tuoke56.tuoke_get_cookie;
exports.tuoke_clear_cookie = tuoke56.tuoke_clear_cookie;
exports.tuoke_upload = tuoke56.tuoke_upload;
exports.tuoke_upload_big = tuoke56.tuoke_upload_big;
exports.tuoke_upload_resume = tuoke56.tuoke_upload_resume;
exports.tuoke_upload_progress = tuoke56.tuoke_upload_progress;
exports.tuoke_status = tuoke56.tuoke_status;
exports.tuoke_download = tuoke56.tuoke_download;
exports.tuoke_all = tuoke56.tuoke_all;
exports.tuoke_fix_dex = tuoke56.tuoke_fix_dex;
exports.tuoke_check_auth = tuoke56.tuoke_check_auth;
exports.tuoke_dex_clean = tuoke56.tuoke_dex_clean;