/* METADATA

{
  "name": "revokit_ws",
  "display_name": {
    "zh": "工作区与清理（逆向产物盘点/清理）",
    "en": "Workspace & Cleanup"
  },
  "description": "逆向过程中的产物盘点和清理。★只读盘点 + 显式确认才删，绝不自动动手。解决的问题：解包/脱壳/重打包的产物散落在十几个目录里（unpack_out、pack360、tmpchk、app_dex、jiagu_so、/data/local/tmp/alldex_*……），用户手机空间一直涨却不知道该清哪里；以及解包出来的 res/ 图标被媒体扫描收进相册。三个工具：ws_scan 盘点（列出每个目录多大、是什么、能不能删）、ws_purge 删除（必须显式传路径 + confirm=true，带硬性安全阀）、ws_nomedia 堵相册（给目录补 .nomedia）。",
  "category": "ReverseEngineering",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "ws_scan",
      "description": "★逆向产物盘点（只读，绝不删）。扫一遍所有已知的散落位置，逐个报「多大 / 是什么 / 建议留还是删」，最后给总占用与可释放合计。用户在问「手机空间怎么少了」「不知道去哪清」时先跑这个。也支持 extra_dirs 扫额外路径。**每条的「备注」里写明了删它要不要额外传 allow_outside_whitelist，照着传就不会白跑一趟。**",
      "parameters": [
        {
          "name": "extra_dirs",
          "description": "额外要扫的目录，逗号分隔。一般不用填。",
          "type": "string",
          "required": false
        },
        {
          "name": "min_mb",
          "description": "小于这个体积（MB）的条目不报，默认 0（全列）。填 1 可以把零碎条目折叠成一句「还有 N 个」。",
          "type": "number",
          "required": false
        },
        {
          "name": "deep",
          "description": "是否递归算每个子目录的大小，默认 true。false 只算顶层，快但粗。",
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "ws_purge",
      "description": "★删除逆向产物。必须显式传 paths，且 confirm 必须为 true —— 少一个都不会删。内置安全阀：拒绝删 /sdcard、/sdcard/Download、DCIM、Pictures、Android、系统目录。白名单内的路径直接放行；白名单外的（如 /sdcard/adx 这种 AI 自己建的目录）要额外传 allow_outside_whitelist=true，工具会按内容特征再判一遍。删之前先报「将删 N 项、共 X MB」，删完报实际释放。**AI 不许自己拼路径调用本工具，必须用 ws_scan 返回的路径。**",
      "parameters": [
        {
          "name": "paths",
          "description": "要删的路径列表。直接传数组 [\"/sdcard/adx\",\"/sdcard/dexbak\"] 最好；JSON 数组字符串、逗号或换行分隔的字符串也认。必须是 ws_scan 报出来的路径。",
          "type": "string",
          "required": true
        },
        {
          "name": "confirm",
          "description": "必须传字符串 true。缺省或非 true 一律不删，只回显将要删什么。",
          "type": "string",
          "required": true
        },
        {
          "name": "use_root",
          "description": "删 /data/local/tmp 下的东西需要真 root，传 true 才走 root 通路。默认 false。",
          "type": "boolean",
          "required": false
        },
        {
          "name": "allow_outside_whitelist",
          "description": "允许删除白名单之外的路径（按内容特征判断）。真机上逆向产物大量散落在 /sdcard/adx、/sdcard/dexbak、/sdcard/tmwork/dump 这类地方，它们不在固定白名单里。**只有用户明确说了要删这些路径，才传 true**；传 true 之后禁区（相册、回收站、MT2、对话工作区、Download 根目录）照样拒绝，含成品 APK 的目录也照样拒绝。",
          "type": "boolean",
          "required": false
        }
      ]
    },
    {
      "name": "ws_nomedia",
      "description": "堵相册污染：给逆向产物目录补 .nomedia 文件，让系统媒体扫描不再把解包出来的 res 图标收进相册。手机相册里已经出现一堆软件图标时跑这个。注意 .nomedia 只挡以后的扫描，已经入相册的旧条目要系统自己清。",
      "parameters": [
        {
          "name": "dirs",
          "description": "要补 .nomedia 的目录，逗号分隔。不传就补全部已知的逆向产物根目录。",
          "type": "string",
          "required": false
        },
        {
          "name": "dry_run",
          "description": "只报告要写哪些，不实际写。默认 false（直接写）。",
          "type": "boolean",
          "required": false
        }
      ]
    }
  ]
}

*/

// ============================================================
// 为什么单独开一个包，而不是塞进 local_unpack：
//   1. 这一层是「运维」不是「脱壳」，跟壳无关，塞进 local_unpack 会让那个文件更肿；
//   2. 清理有删除动作，权限范围比脱壳工具大得多，单独一个包方便用户在
//      「包管理」里一键关掉它（不想让 AI 碰自己的文件时）；
//   3. 下一轮要把所有工具的默认输出路径改成走统一工作区，届时的入口也在这里。
//
// 三条硬规矩（写在代码里，别改）：
//   ① ws_scan 只读，一个字节都不删 —— 用户是被「不知道去哪清」困住的，
//      不是被「删不干净」困住的，所以先给他看清楚。
//   ② ws_purge 必须同时满足「显式传了 paths」+「confirm === 'true'」才动手。
//      少一个都只在报告里回显「将要删什么」。AI 手滑、模型幻觉、参数漏传，
//      这三种情况在真机上都会发生，靠这一个闸门全挡住。
//   ③ 白名单之外一律拒绝。宁可少删，不能多删 —— 用户的原包、相册、别的 App
//      数据都在同一块盘上，删错一个就没法还原（Android 上 rm 没有回收站）。
// ============================================================

var ws = (function () {
    'use strict';

    // 脚本临时落地点。放在 Operit 根下，和 local_unpack 用不同文件名避免撞车。
    var TMP_ROOT = '/sdcard/Download/Operit';

    function q(s) { return "'" + String(s).replace(/'/g, "'\\''") + "'"; }

    // ---------- 执行通路 ----------
    // 跟 local_unpack 同一套判断：/sdcard 走 proot 容器（有 python3），
    // /data/local/tmp 与 su 走 Android 侧（execute_shell → Root/Shizuku）。
    var ENV_LINUX = 'linux';
    var ENV_ANDROID = 'android';

    function execRaw(cmd, timeoutMs, environment) {
        return Tools.System.terminal.hiddenExec(cmd, {
            timeoutMs: timeoutMs || 60000,
            environment: environment || ENV_LINUX
        }).then(function (r) { return r || { exitCode: -1, output: '' }; });
    }

    function normSysResult(r) {
        if (r === null || r === undefined) return { exitCode: -1, output: '' };
        if (typeof r === 'string') return { exitCode: 0, output: r };
        var ec = (typeof r.exitCode === 'number') ? r.exitCode
            : (typeof r.exit_code === 'number' ? r.exit_code : 0);
        var out = '';
        if (r.output !== undefined && r.output !== null) out = String(r.output);
        else if (r.stdout !== undefined && r.stdout !== null) out = String(r.stdout);
        return { exitCode: ec, output: out };
    }

    function parseSysError(err) {
        var msg = '';
        if (err && err.message) msg = String(err.message);
        else if (typeof err === 'string') msg = err;
        else { try { msg = JSON.stringify(err); } catch (_e) { msg = String(err); } }
        var m = /ADB command execution failed \(exit code:\s*(-?\d+)\):\s*([\s\S]*)$/.exec(msg);
        if (m) return { exitCode: parseInt(m[1], 10), output: m[2] || '', channelOk: true };
        return { exitCode: -1, output: msg, channelOk: false };
    }

    function execAndroid(cmd, timeoutMs) {
        return Tools.System.shell(cmd).then(function (r) {
            return normSysResult(r);
        }, function (e) {
            var p = parseSysError(e);
            return { exitCode: p.exitCode, output: p.output };
        });
    }

    function exec(cmd, timeoutMs, environment) {
        return execRaw(cmd, timeoutMs, environment).then(function (r) {
            if (r.exitCode !== 0) {
                var e = new Error('命令失败 (exit=' + r.exitCode + '): ' + cmd
                    + '\n' + String(r.output || '').substring(0, 800));
                e.exitCode = r.exitCode;
                e.output = r.output;
                throw e;
            }
            return r;
        });
    }

    // 把 python 脚本写到设备再执行。
    // **分隔符必须带引号**（<<'WSEOF'）：不带的 heredoc 会做反斜杠转义，
    // python 源码里的 '\\' 会被折成 '\'，直接 SyntaxError。
    // local_unpack 上真机踩过 4/4 全挂，这里一次都别再犯。
    function runPy(pyCode, args, timeoutMs) {
        var script = TMP_ROOT + '/.ws_tmp.py';
        return exec('mkdir -p ' + q(TMP_ROOT), 15000)
            .then(function () {
                var marker = 'WSEOF';
                var cmd = 'cat > ' + q(script) + ' <<\'' + marker + '\'\n' + pyCode + '\n' + marker + '\n';
                return exec(cmd, 30000);
            })
            .then(function () {
                return exec('python3 ' + q(script) + ' ' + (args || ''), timeoutMs || 600000);
            })
            .then(function (r) {
                var out = (r.output || '').trim();
                var idx = out.lastIndexOf('WSRESULT:');
                if (idx >= 0) {
                    var js = out.substring(idx + 9).trim();
                    try { return JSON.parse(js); } catch (e) { return { raw: out }; }
                }
                return { raw: out };
            });
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

    function toList(v) {
        if (!v) return [];
        if (Object.prototype.toString.call(v) === '[object Array]') {
            return v.map(function (x) { return String(x).trim(); })
                .filter(function (x) { return x.length > 0; });
        }
        var s = String(v).trim();
        // 模型经常把数组序列化成字符串塞进来（实测传过 ["\/sdcard\/adx","\/sdcard\/dexbak"]）。
        // 只按逗号切的话，"[" 和引号会跟着路径一起进去，导致每一条都被判「不在白名单」。
        // 所以先试 JSON.parse；解析不了再退回按逗号/换行切。
        if (s.charAt(0) === '[') {
            try {
                var arr = JSON.parse(s);
                if (Object.prototype.toString.call(arr) === '[object Array]') {
                    return arr.map(function (x) { return String(x).trim(); })
                        .filter(function (x) { return x.length > 0; });
                }
            } catch (e) { /* 不是合法 JSON，往下走普通切分 */ }
        }
        return s.split(/[,\n]+/)
            .map(function (x) { return x.trim().replace(/^["'\[]+|["'\]]+$/g, ''); })
            .filter(function (x) { return x.length > 0; });
    }

    function isTrue(v) {
        return v === true || v === 'true' || v === '1' || v === 1;
    }

    // ============================================================
    // ① ws_scan —— 只读盘点
    // ============================================================
    function ws_scan(params) {
        var extra = toList(params.extra_dirs);
        // 默认 min_mb=0（全列）。原来默认 1，结果 tmpchk / tmpverify / app_dex
        // 这些真正该清的零碎（都不到 1MB）全被挡在清单外，只剩一句
        // 「还有 N 个没列出来」—— 用户照着清单删不干净。
        var minMb = (params.min_mb === undefined || params.min_mb === null) ? 0 : Number(params.min_mb);
        var deep = (params.deep === undefined) ? true : isTrue(params.deep);
        var args = [q(extra.join(',')), q(String(minMb)), deep ? '1' : '0'].join(' ');

        return runPy(PY_SCAN, args, 600000).then(function (r) {
            if (r && r.raw !== undefined) {
                throw new Error('盘点脚本没返回结构化结果，原始输出：' + String(r.raw).substring(0, 400));
            }
            return r;
        });
    }

    // ============================================================
    // ② ws_purge —— 删除（双闸门）
    // ============================================================
    function ws_purge(params) {
        var paths = toList(params.paths);
        var confirmed = isTrue(params.confirm);
        var useRoot = isTrue(params.use_root);
        var allowOutside = isTrue(params.allow_outside_whitelist);
        if (!paths.length) throw new Error('缺少 paths（必须是 ws_scan 报出来的路径）');

        var args = [
            q(paths.join('\n')),
            confirmed ? '1' : '0',
            useRoot ? '1' : '0',
            allowOutside ? '1' : '0'
        ].join(' ');
        return runPy(PY_PURGE, args, 900000).then(function (r) {
            if (r && r.raw !== undefined) {
                throw new Error('删除脚本没返回结构化结果，原始输出：' + String(r.raw).substring(0, 400));
            }
            return r;
        });
    }

    // ============================================================
    // ③ ws_nomedia —— 堵相册
    // ============================================================
    function ws_nomedia(params) {
        var dirs = toList(params.dirs);
        var dry = isTrue(params.dry_run);
        var args = [q(dirs.join(',')), dry ? '1' : '0'].join(' ');
        return runPy(PY_NOMEDIA, args, 120000).then(function (r) {
            if (r && r.raw !== undefined) {
                throw new Error('nomedia 脚本没返回结构化结果，原始输出：' + String(r.raw).substring(0, 400));
            }
            return r;
        });
    }

    // ============================================================
    // PY_SCAN
    // ============================================================
    var PY_SCAN = String.raw`
import os, sys, json, re

extra_raw = sys.argv[1] if len(sys.argv) > 1 else ''
min_mb = float(sys.argv[2]) if len(sys.argv) > 2 else 1.0
deep = (sys.argv[3] if len(sys.argv) > 3 else '1') == '1'
extra = [x for x in extra_raw.split(',') if x.strip()]

# 根目录允许被 WS_SD 环境变量覆盖 —— 真机上永远是 /sdcard，
# 但必须能在这台电脑上用假目录把清理逻辑跑通，否则这段代码只能靠真机试错。
SD = os.environ.get('WS_SD', '/sdcard').rstrip('/')


def N(p):
    """路径统一成正斜杠。

    这个不是为了好看。第一次写的时候没做归一化，结果在 Windows 上测出来
    同一个目录被报了两次（/Download/tmpchk 和 \\Download\\tmpchk），
    因为 seen 集合按字符串去重，反斜杠和正斜杠被当成两条。
    真机是 Linux 不会有这问题，但既然要做去重和父子关系判断，
    就必须先统一形式，否则逻辑在两端行为不一致。
    """
    if not p:
        return p
    return p.replace('\\', '/').rstrip('/')


# ---- 白名单：只在这些位置里找。不在里面的，一律不报。 ----
# 这里写的是「逆向过程会往里扔东西的地方」，不是「磁盘上所有目录」。
WHITELIST_ROOTS = [
    SD + '/Download/Operit/unpack_out',
    SD + '/Download/Operit/pack360',
    SD + '/Download/Operit/tuoke_out',
    SD + '/Download/Operit/dexclean',
    SD + '/Download/Operit/56al',
    SD + '/Download/Operit/rk',
    SD + '/Download/Operit/prev_dumps',
    SD + '/Download/Operit/quarantine',
    SD + '/Download/Operit/alldex',
    SD + '/Download/Operit/tmp',
    SD + '/Download/tmpchk',
    SD + '/Download/tmpverify',
    SD + '/Download/tmp',
    SD + '/app_dex',
    SD + '/jiagu_so',
    SD + '/revokit',
]

# ---- 这些就算在白名单底下也绝不报：用户的东西 ----
NEVER = [
    SD + '/Download/Operit/workspace',   # 对话工作区，用户可能在里面放自己的文件
    SD + '/Download/Operit/skills',
    SD + '/Download/Operit/bridge',
    SD + '/Download/Operit/cleanOnExit',  # 启动时自己会清，不用管
    SD + '/MT2',                          # 用户的原始 APK
    # 文件管理器的回收站。2026-10-26 真机实测里它被当成逆向垃圾清掉了 1.4GB ——
    # 那里面是用户「删过但还能恢复」的文件，删了就没救了。不是我们的地盘，不碰也不报。
    SD + '/.FileManagerRecycler',
    SD + '/.trash',
    SD + '/.Trash',
    SD + '/.recycle',
]

# ---- 这几个目录本身永远不作为「一项」报出来，只报它们的子目录 ----
# 为什么：/sdcard/Download 是用户的下载目录，真机上一报就是 8.7GB「先取出APK再删」，
# 里面装着 34 个他自己下的 apk。整目录删是荒唐的，报出来只会把人吓一跳。
CONTAINER_ONLY = [
    SD + '/Download',
    SD,
]

# ---- 内容特征：靠「长得像不像解包目录」判断，而不是靠路径猜 ----
# 这条很重要：AI 在真机上随手建过 tmpchk / tmpverify / app_dex / jiagu_so / revokit/septnet，
# 这些名字本来没有规律。但不管建在哪，解包目录必然带 apktool.yml 或 smali*；
# 脱壳产物必然是一堆 classes*.dex / dump_*.dex。按内容认比按名字认可靠得多。
APKTOOL_MARKS = ('apktool.yml', 'AndroidManifest.xml')


def quick_stat(p, cap_files=3000):
    """快速量一下体积和文件数，文件太多就提前收手。

    dir_size 是无限递归的，用来判断「这是不是我自己的样本库」太亏；
    这里只要一个量级，超过 3000 个文件就认定「不是小样本库」直接返回。
    """
    total = 0
    n = 0
    for r_, ds, fs in os.walk(p):
        for f in fs:
            try:
                total += os.path.getsize(os.path.join(r_, f))
            except Exception:
                pass
            n += 1
            if n >= cap_files:
                return total, n
    return total, n


def is_sample_library(p):
    """判断这是不是用户自己整理的样本库 / 资料库。

    真机实测（2026-10-26）：用户有个 `/sdcard/加固特征.txt/` 目录，下面按厂商分了
    几十个子目录（360加固 / 爱加密 / 梆梆 / 娜迦 …），每个装一个样本 apk 加几个 dex。
    那是他自己攒的加固样本库，结果被我们的内容特征一个个认成「零散dex / 可删」，
    AI 差点把整个样本库删了 —— 这属于「认得出是 dex，但认不出这是用户的东西」。

    判据三条同时满足才算：
      ① 子目录 ≥ 6 个（一大堆平级目录）
      ② 总量 < 300MB
      ③ 平均每个子目录 < 40MB（每个都是小样本，不是一个完整工程）

    注意判据里没有看目录名。README / 加固特征.txt 这种名字是用户随手起的，
    靠名字认不可靠。
    """
    try:
        subs = [os.path.join(p, d) for d in os.listdir(p)
                if os.path.isdir(os.path.join(p, d))]
    except Exception:
        return False
    if len(subs) < 6:
        return False
    tot, nf = quick_stat(p)
    if tot >= 300 * 1048576:
        return False
    if tot / float(len(subs)) >= 40 * 1048576:
        return False
    return nf <= len(subs) * 80


def dir_size(p):
    # 单个文件也要能算 —— 成品 APK 常常直接躺在根目录里，不是子目录，
    # 第一版只认目录，那个 3MB 的成品包整个消失了，总量少算 3MB。
    if os.path.isfile(p):
        try:
            return (os.path.getsize(p), 1)
        except Exception:
            return (0, 0)
    total = 0
    n = 0
    for root, dirs, files in os.walk(p):
        for f in files:
            try:
                total += os.path.getsize(os.path.join(root, f))
                n += 1
            except Exception:
                pass
        if n > 400000:
            break
    return total, n


def sniff(p):
    """只看这一层：返回 (dex数, apk列表, 有没有smali, 有没有apktool标记, 图片数, so数)。"""
    dex_n = 0
    apks = []
    has_smali = False
    has_apktool = False
    imgs = 0
    so_n = 0
    try:
        for name in os.listdir(p):
            fp = os.path.join(p, name)
            if os.path.isfile(fp):
                low = name.lower()
                if low.endswith('.dex'):
                    dex_n += 1
                if low.endswith('.apk'):
                    apks.append(name)
                if name in APKTOOL_MARKS:
                    has_apktool = True
                if low.endswith(('.png', '.jpg', '.jpeg', '.webp')):
                    imgs += 1
                if low.endswith('.so'):
                    so_n += 1
            elif os.path.isdir(fp):
                if name.startswith('smali'):
                    has_smali = True
                # res 目录本身就说明这是解包现场
                if name == 'res':
                    has_apktool = True
    except Exception:
        return None
    return (dex_n, apks, has_smali, has_apktool, imgs, so_n)


def classify(p):
    """判断这是什么。返回 (类型, 说明, 建议, 理由)。

    关键：先看本层，本层没有特征就往下看一层。
    因为真实的任务目录是 <包名>/decode/ 这种结构 ——
    直接看 <包名>/ 是空的，apktool.yml 在 decode/ 里。
    第一版没做这层下潜，于是 cc.septnet.student 被报成「其他/先别动」，
    而这恰恰是用户最该删的那个目录。
    """
    s = sniff(p)
    if s is None:
        return ('读不到', '目录读不了（权限或已删）', '先别动', '')
    dex_n, apks, has_smali, has_apktool, imgs, so_n = s

    own = (dex_n > 0 or apks or has_smali or has_apktool)
    sub_dex = 0
    sub_apks = []
    sub_smali = False
    sub_apktool = False
    sub_so = 0
    try:
        subs = [os.path.join(p, d) for d in os.listdir(p)
                if os.path.isdir(os.path.join(p, d))]
    except Exception:
        subs = []
    for sp in subs:
        s2 = sniff(sp)
        if not s2:
            continue
        sub_dex += s2[0]
        sub_apks += s2[1]
        sub_smali = sub_smali or s2[2]
        sub_apktool = sub_apktool or s2[3]
        sub_so += s2[5]
        # 再深一层：lib/arm64-v8a/libjiagu.so 这种结构，so 在第三层。
        # 不下潜两层的话，jiagu_so/lib 会被判成「其他/先别动」——
        # 而它恰恰是壳的 so，最该删的那一类。
        if N(sp).split('/')[-1] in ('lib', 'jniLibs', 'jnilibs'):
            try:
                for g in os.listdir(sp):
                    gp = os.path.join(sp, g)
                    if os.path.isdir(gp):
                        s3 = sniff(gp)
                        if s3:
                            sub_so += s3[5]
            except Exception:
                pass
    dex_n += sub_dex
    apks = apks + sub_apks
    has_smali = has_smali or sub_smali
    has_apktool = has_apktool or sub_apktool
    so_n += sub_so

    signed = [a for a in apks
              if any(k in a.lower() for k in ('_signed', '_repack', '_fixed', '_v2',
                                              '_v3', '_output', '成品', 'patched'))]
    pick = signed or apks

    # 又有成品又有中间产物 → 这是「一个任务的全部东西」。
    # 这种最典型：用户想清但不敢清，怕把成品也删了。
    if apks and (has_smali or has_apktool or dex_n >= 2):
        more = '' if len(apks) <= 3 else '等 %d 个' % len(apks)
        return ('任务目录',
                '含成品 APK（%s%s）+ 中间产物（%d 个 dex%s）'
                % (', '.join(apks[:3]), more, dex_n,
                   '、解包目录' if (has_smali or has_apktool) else ''),
                '先取出APK再删',
                '这里面有能用的安装包，别整目录删。先把 %s 拷出去，剩下的再清。' % pick[0])
    if apks:
        return ('成品APK', '含 %d 个 APK：%s' % (len(apks), ', '.join(pick[:3])),
                '保留', '这是重打包出来的安装包。删之前先确认已经拷到别处。')
    if has_apktool and has_smali:
        extra_txt = '，res 里 %d 张图' % imgs if imgs else ''
        return ('解包目录', 'apktool 解包现场（含 smali 与 res%s）' % extra_txt,
                '可删', '纯中间产物。res 里那些图标就是相册被污染的来源。')
    if has_smali:
        return ('解包残留', '有 smali 目录但不是完整解包', '可删', '中间产物。')
    if dex_n >= 3:
        return ('脱壳dex', '%d 个 dex' % dex_n, '可删', '脱壳 dump 产物。成品包已出来的话这批可以不要。')
    if dex_n > 0:
        return ('零散dex', '%d 个 dex' % dex_n, '可删', '中间产物。')
    if has_apktool:
        return ('半成品解包', '有 res/Manifest 但不完整', '可删', '中间产物。')
    if so_n:
        return ('so目录', '%d 个 so' % so_n, '可删',
                '解包/壳的 so 文件。壳的 libjiagu*.so 就在这类目录里。')
    if imgs:
        return ('图片目录', '%d 张图片' % imgs, '可删', '解包出来的资源图，相册污染源。')
    return ('其他', '没有明显逆向特征', '先别动', '认不出是什么，不动它。')


def is_never(p):
    pp = N(p)
    for n in NEVER:
        nn = N(n)
        if pp == nn or pp.startswith(nn + '/'):
            return True
    return False


def is_container_only(p):
    """这几个目录本身永远不作为一项报出来，只报它的子目录。

    真机实测：/sdcard/Download 被报成「任务目录，8.7GB，先取出APK再删」——
    那是用户自己的下载目录，里面有 34 个他自己下的 apk。整目录删是荒唐的。
    """
    pp = N(p)
    for c in CONTAINER_ONLY:
        if pp == N(c):
            return True
    return False


def add_file_item(fp):
    """根目录下直接躺着的单个文件也要报。

    理由见 ①b 里那段：成品 APK 不一定在子目录里，
    有时候就直接躺在 pack360/ 下面，只扫子目录会把它漏掉。
    只处理 .apk / .dex / .so 三种，另外再看一眼没有扩展名的大文件。
    """
    pp = N(fp)
    if not pp or is_never(pp) or not os.path.isfile(pp):
        return
    low = pp.lower()
    if low.endswith('.apk'):
        typ, desc, advice, why = ('成品APK', '单个 APK 文件', '保留',
                                  '这是安装包。删之前先确认已经拷到别处。')
    elif low.endswith('.dex'):
        typ, desc, advice, why = ('零散dex', '散落在外的 dex', '可删',
                                  '脱壳倒出来的中间产物。')
    elif low.endswith('.so'):
        typ, desc, advice, why = ('so文件', '散落在外的 so', '可删',
                                  '壳的 so 或中间产物。')
    else:
        # 没有扩展名的大文件也得看一眼。重打包工具的输出不一定带 .apk 后缀
        # （见过直接叫「幕布_4.3.3」的），光看名字会整个漏掉。
        # 判据看开头两个字节：PK 就是 zip/apk。小于 1MB 的不折腾。
        try:
            if os.path.getsize(pp) < 1048576:
                return
            with open(pp, 'rb') as fh:
                magic = fh.read(2)
        except Exception:
            return
        if magic != b'PK':
            return
        typ, desc, advice, why = ('压缩包/APK', '没有扩展名的大文件（开头是 PK，像 zip 或 apk）',
                                  '保留', '可能是重打包出来的成品。删之前先确认已拷到别处。')
    raw_items.append({'路径': pp, '类型': typ, '说明': desc,
                      '建议': advice, '理由': why, '备注': '根目录下的大文件'})


raw_items = []


def in_whitelist(p):
    pp = N(p)
    for r in WHITELIST_ROOTS:
        rn = N(r)
        if pp == rn or pp.startswith(rn + '/'):
            return True
    return False


def add_item(p, note=''):
    pp = N(p)
    if not pp:
        return
    if is_never(pp):
        return
    if is_container_only(pp):
        return
    if not os.path.isdir(pp):
        return
    typ, desc, advice, why = classify(pp)
    # 白名单外的东西（/sdcard/adx、/sdcard/dexbak 这种 AI 随手建的）ws_purge 默认是拒的。
    # 之前的坑：扫描说「可删」，AI 照着调 ws_purge，被一句「不在白名单内」顶回来，
    # 只好改用 shell mv 绕路。所以扫描这边就先把「要多传一个开关」写清楚。
    if advice == '可删' and not in_whitelist(pp):
        tip = '不在白名单，删的时候要带 allow_outside_whitelist=true'
        note = (note + '；' + tip) if note else tip
    raw_items.append({'路径': pp, '类型': typ, '说明': desc,
                      '建议': advice, '理由': why, '备注': note})


# ---- ① 白名单根：先看根本身，再看它下面每个子目录（一子目录 = 一个任务）----
for root in WHITELIST_ROOTS:
    if not os.path.isdir(root):
        continue
    if is_never(root):
        continue
    try:
        subs = [os.path.join(root, d) for d in os.listdir(root)
                if os.path.isdir(os.path.join(root, d))]
    except Exception:
        subs = []
    if not subs:
        add_item(root)
        continue
    # 只有一个 lib/ 或 assets/ 的根目录，报根本身更有用 ——
    # 用户要删的是「jiagu_so 这个文件夹」，不是「jiagu_so/lib」。
    only_container = len(subs) <= 2 and all(
        os.path.basename(N(x)).lower() in ('lib', 'assets', 'res', 'original', 'jnilibs')
        for x in subs)
    if only_container:
        add_item(root)
    else:
        for s in subs:
            add_item(s)

# ---- ①b 根目录下直接躺着的大文件 ----
# 第一版只认子目录，于是 pack360 下那个直接躺着的 3MB 成品包整个消失了，
# 清单上说总量 0.2MB，实际有 3MB 躺在那儿。看不见就等于用户永远不知道要搬走。
for root in WHITELIST_ROOTS:
    if not os.path.isdir(root) or is_never(root):
        continue
    try:
        names = os.listdir(root)
    except Exception:
        continue
    for n in names:
        add_file_item(os.path.join(root, n))

# ---- ② 泛化识别：在 Download 下两层的范围里，按内容特征再捞一遍 ----
# 目的：AI 随手建的那些（tmpchk / tmpverify / app_dex / revokit/xxx）名字不规律，
# 但内容一定有解包或 dex 特征，扫内容就能捞出来。只下潜两层，避免扫整块盘卡死。
hunt_roots = [SD + '/Download', SD]
for hr in hunt_roots:
    hr = N(hr)
    if not os.path.isdir(hr):
        continue
    try:
        lvl1 = [os.path.join(hr, d) for d in os.listdir(hr)]
    except Exception:
        continue
    for p1 in lvl1:
        p1 = N(p1)
        if not os.path.isdir(p1) or is_never(p1) or is_container_only(p1):
            continue
        # 用户的样本库 / 资料库整个跳过 —— 连它的子目录也不报。
        # 真机实测里 /sdcard/加固特征.txt 下面按厂商分了 40 个子目录，
        # 不在这里拦的话每个子目录都会被单独报成「零散dex 可删」。
        if is_sample_library(p1):
            continue
        s1 = sniff(p1)
        if s1 and (s1[0] > 0 or s1[1] or s1[2] or s1[3]):
            add_item(p1)
        try:
            names = os.listdir(p1)
        except Exception:
            continue
        for d in names:
            p2 = N(os.path.join(p1, d))
            if not os.path.isdir(p2) or is_never(p2):
                continue
            s2 = sniff(p2)
            if s2 and (s2[0] > 0 or s2[1] or s2[2] or s2[3]):
                add_item(p2)

# ---- ③ 额外指定 ----
for e in extra:
    add_item(e, note='用户指定')

# ---- 父子去重：父目录已经报了，里面的子目录不再单列 ----
# 用户删父目录就等于全删，列两遍只会让清单变长、还容易删到一半报「不存在」。
raw_items.sort(key=lambda x: (len(x['路径']), x['路径']))
items = []
for it in raw_items:
    p = it['路径']
    if any(p == k['路径'] or p.startswith(k['路径'] + '/') for k in items):
        # 已经被某个父级收编了 —— 把大小并进父级，别丢信息
        continue
    items.append(it)

# ---- 算大小（去重之后再算，省一半 IO）----
for it in items:
    size, cnt = (dir_size(it['路径']) if deep else (0, 0))
    it['大小MB'] = round(size / 1048576.0, 1)
    it['文件数'] = cnt
    it['_bytes'] = size

# 小条目过滤。判据用原始字节，不能用四舍五入后的 MB ——
# 第一版用 MB 比，一个 800 字节的解包残留被舍成 0.0MB，直接筛掉了，
# 结果「tmpverify 明明存在却不在清单里」。成品无论多小都保留。
_before = len(items)
items = [x for x in items
         if x['_bytes'] >= min_mb * 1048576 or x['建议'] in ('保留', '先取出APK再删')]
_filtered_n = _before - len(items)
for x in items:
    x.pop('_bytes', None)

# ---- ④ /data/local/tmp（脱壳模块直接往这吐，单个几十 MB，最容易被忽略）----
tmp_items = []
d = '/data/local/tmp'
if os.path.isdir(d):
    try:
        for name in os.listdir(d):
            fp = N(os.path.join(d, name))
            if not os.path.isfile(fp):
                continue
            low = name.lower()
            if not (low.endswith('.dex') or low.endswith('.so')
                    or name.startswith('alldex') or 'dump' in low):
                continue
            try:
                sz = os.path.getsize(fp)
            except Exception:
                continue
            if sz / 1048576.0 < min_mb:
                continue
            tmp_items.append({'路径': fp, '大小MB': round(sz / 1048576.0, 1),
                              '文件数': 1,
                              '类型': '系统临时dex',
                              '说明': '脱壳模块倒出来的 dex/so',
                              '建议': '可删',
                              '理由': '在 /data/local/tmp 里，不删一直占空间。删它要 root。',
                              '需要root': True})
    except Exception:
        pass

items.sort(key=lambda x: -x['大小MB'])
tmp_items.sort(key=lambda x: -x['大小MB'])

total_mb = round(sum(x['大小MB'] for x in items) + sum(x['大小MB'] for x in tmp_items), 1)
# 三档分开算。第一版把「先取出APK再删」也算进「可删」，
# 结果一个 4.8MB 的任务目录被说成「4.8MB 可以删」——
# 里面装着两个成品安装包，这么讲会把人坑了。
pure_del = [x for x in items if x['建议'] == '可删']
take_first = [x for x in items if x['建议'] == '先取出APK再删']
keep_list = [x for x in items if x['建议'] in ('保留', '先别动')]
pure_mb = round(sum(x['大小MB'] for x in pure_del) + sum(x['大小MB'] for x in tmp_items), 1)
take_mb = round(sum(x['大小MB'] for x in take_first), 1)
keep_mb = round(sum(x['大小MB'] for x in keep_list), 1)
del_list = pure_del + take_first
del_mb = round(pure_mb + take_mb, 1)

res = {}
res['扫描范围'] = 'Download/Operit 全部 + Download 下两层按内容特征识别 + /data/local/tmp'
res['条目数'] = len(items) + len(tmp_items)
res['总占用MB'] = total_mb
res['可直接删MB'] = pure_mb
res['要先取出APK再删MB'] = take_mb
res['可删合计MB'] = del_mb
res['建议保留MB'] = keep_mb
if take_first:
    res['这些目录里有成品安装包（先拷出来再删）'] = [
        {'路径': x['路径'], '大小MB': x['大小MB'], '说明': x['说明']} for x in take_first[:10]]
if _filtered_n:
    res['小于阈值的条目数'] = _filtered_n
    res['小于阈值说明'] = ('还有 %d 个目录体积小于 %.1f MB 没列出来（怕清单太长）。'
                           '想看全部就把 min_mb 传 0。' % (_filtered_n, min_mb))

if items:
    res['条目'] = items[:40]
    if len(items) > 40:
        res['其余条目数'] = len(items) - 40
if tmp_items:
    res['系统临时dex'] = tmp_items[:15]

# 结论直接说人话。判据先看「有没有可删的条目」，再看 MB ——
# 测试样本里的 dex 只有几百字节，只看 MB 会把「有 6 个可删目录」说成「没有可删的」。
if not items and not tmp_items:
    res['结论'] = '没发现逆向产物占空间（可能已经清过，或还没跑过任务）'
elif not del_list and not tmp_items:
    res['结论'] = ('发现 %d 项、共 %.1f MB，但都是建议保留的（成品 APK 等）。'
                   '想清的话先把成品拷到别处。' % (len(items), total_mb))
elif del_mb < 1:
    res['结论'] = ('发现 %d 项，其中 %d 项是中间产物（体积都很小，合计不到 1 MB），'
                   '另有 %.1f MB 是需要保留的成品。'
                   % (len(items) + len(tmp_items), len(del_list) + len(tmp_items), keep_mb))
elif take_mb > 0:
    res['结论'] = ('共 %d 项、占 %.1f MB。其中 %.1f MB 可以直接删，'
                   '另有 %.1f MB 在「装了成品包的目录」里 —— 那些得先把 apk 拷出来再删。'
                   % (len(items) + len(tmp_items), total_mb, pure_mb, take_mb))
else:
    res['结论'] = ('共 %d 项、占 %.1f MB，其中 %.1f MB 是中间产物，可以直接删。'
                   '删之前把「建议保留」里那几项确认一下。'
                   % (len(items) + len(tmp_items), total_mb, del_mb))

res['怎么删'] = ('把要删的路径原样传给 ws_purge，并传 confirm=true。'
                 '一次别删太多，先删最大的那一两个看看效果。')
res['注意'] = ('ws_scan 只读，没删任何东西。'
               '「建议=先别动」的是认不出内容的，别删。')
res['根目录'] = SD

print('WSRESULT:' + json.dumps(res, ensure_ascii=False))
`;

    // ============================================================
    // PY_PURGE
    // ============================================================
    var PY_PURGE = String.raw`
import os, sys, json, shutil, subprocess

raw = sys.argv[1] if len(sys.argv) > 1 else ''
confirmed = (sys.argv[2] if len(sys.argv) > 2 else '0') == '1'
use_root = (sys.argv[3] if len(sys.argv) > 3 else '0') == '1'
allow_outside = (sys.argv[4] if len(sys.argv) > 4 else '0') == '1'


def unquote(x):
    """把模型塞进来的引号/方括号剥掉。

    实测过：模型会把参数写成 '["/sdcard/adx","/sdcard/dexbak"]' 这种字符串，
    JS 侧已经解过一层，这里再兜一层。剥不掉的话 '"[' 会被当成路径的一部分，
    最后删不掉还报一堆看不懂的错。
    """
    x = (x or '').strip()
    changed = True
    while changed and len(x) >= 2:
        changed = False
        for a, b in (('"', '"'), ("'", "'"), ('[', ']')):
            if x.startswith(a) and x.endswith(b):
                x = x[len(a):-len(b)].strip()
                changed = True
    return x


def parse_paths(raw):
    txt = (raw or '').strip()
    # 整个参数就是一个 JSON 数组的情况
    if txt[:1] == '[':
        try:
            arr = json.loads(txt)
            if isinstance(arr, list):
                out = [unquote(str(x)) for x in arr]
                return [x for x in out if x]
        except Exception:
            pass
    out = [unquote(x) for x in raw.split('\n')]
    return [x for x in out if x]


paths = parse_paths(raw)

# 根目录允许被 WS_SD 环境变量覆盖 —— 真机上永远是 /sdcard，
# 但必须能在这台电脑上用假目录把清理逻辑跑通，否则这段代码只能靠真机试错。
SD = os.environ.get('WS_SD', '/sdcard').rstrip('/')

# ---- 安全阀 1：这些路径本身就永远不许删（哪怕用户手抄错了传进来）----
# 只比「完全相等」，所以底下可以放白名单（比如 /data/local/tmp 本身保护，
# 但 /data/local/tmp/alldex 允许删）。
FORBIDDEN_EXACT = [
    '/', SD, '/storage', '/storage/emulated', '/storage/emulated/0',
    '/data', '/data/local', '/data/local/tmp',
    '/system',
    SD + '/Download', SD + '/Download/Operit',
]

# ---- 安全阀 2：这些目录连同它下面的所有东西都不许删 ----
# 原来这些只做「完全相等」判断，于是 SD/DCIM 保护了、SD/DCIM/Camera 却漏了 ——
# 2026-10-26 的自测里 /sdcard/DCIM/Camera 在放行模式下被判成「一堆图片，像逆向产物」
# 差点被删。相册、文档、Android 数据这类目录必须整棵树保护。
# 回收站同理：那里面是用户「删过但还能恢复」的文件，删了就没救。
FORBIDDEN_TREES = [
    (SD + '/DCIM', '这是相册（相机照片），不删'),
    (SD + '/Pictures', '这是图片目录，不删'),
    (SD + '/Movies', '这是视频目录，不删'),
    (SD + '/Music', '这是音乐目录，不删'),
    (SD + '/Documents', '这是文档目录，不删'),
    (SD + '/Android', '这是应用数据目录，删了应用会出问题'),
    (SD + '/MT2', '这是 MT 管理器的目录，不删'),
    ('/sdcard/MT2', '这是 MT 管理器的目录，不删'),
    (SD + '/.FileManagerRecycler', '这是文件管理器的回收站（删了就没法恢复），不删'),
    (SD + '/.trash', '这是回收站，不删'),
    (SD + '/.Trash', '这是回收站，不删'),
    (SD + '/.recycle', '这是回收站，不删'),
    (SD + '/Download/Operit/workspace', '这是对话工作区，不删'),
    (SD + '/Download/Operit/skills', '这是技能目录，不删'),
    (SD + '/Download/Operit/bridge', '这是桥接目录，不删'),
    (SD + '/Download/Operit/cleanOnExit', '这是退出清理目录，不删'),
]
# ---- 必须落在这些前缀之下才允许删 ----
ALLOW_PREFIX = [
    SD + '/Download/Operit/unpack_out/',
    SD + '/Download/Operit/pack360/',
    SD + '/Download/Operit/tuoke_out/',
    SD + '/Download/Operit/dexclean/',
    SD + '/Download/Operit/56al/',
    SD + '/Download/Operit/rk/',
    SD + '/Download/Operit/prev_dumps/',
    SD + '/Download/Operit/quarantine',
    SD + '/Download/Operit/alldex',
    SD + '/Download/tmpchk/',
    SD + '/Download/tmpverify/',
    SD + '/app_dex/',
    SD + '/jiagu_so/',
    SD + '/revokit/',
    '/data/local/tmp/alldex',
    '/data/local/tmp/dump',
]

# 判断「是不是逆向产物」用的标记文件（跟 ws_scan 同一套口味）
ARTIFACT_MARKS = ('apktool.yml', 'AndroidManifest.xml')


def norm(p):
    p = p.replace('\\', '/').rstrip('/')
    while '//' in p:
        p = p.replace('//', '/')
    return p


def looks_like_artifact(p):
    """内容特征判据 —— 只有白名单外的路径才用它。

    用途：真机上用户的逆向产物大量散落在 /sdcard/adx、/sdcard/dexbak、
    /sdcard/tmwork/dump 这种地方，全都不在我写死的白名单里。
    2026-10-26 实测：ws_scan 把它们报成「可删」，ws_purge 却一个都不同意删，
    AI 被逼着改用 shell mv 绕路 —— 两套判据打架，必须统一到这来。

    只认「纯中间产物」：解包现场 / 脱壳 dex / 零散 dex / so 目录 / 图片目录。
    **有成品 APK 的目录一律不放行**（那种得先把包拷出来）；
    **只有一个 dex 的目录也不放行**（可能是用户放的单个样本，宁可让他手动删）。
    """
    if os.path.isfile(p):
        low = p.lower()
        return low.endswith('.dex') or low.endswith('.so')

    dex_n = 0
    apks = 0
    has_smali = False
    has_mark = False
    so_n = 0
    imgs = 0
    try:
        for name in os.listdir(p):
            fp = os.path.join(p, name)
            if os.path.isfile(fp):
                low = name.lower()
                if low.endswith('.dex'):
                    dex_n += 1
                if low.endswith('.apk'):
                    apks += 1
                if name in ARTIFACT_MARKS:
                    has_mark = True
                if low.endswith('.so'):
                    so_n += 1
                if low.endswith(('.png', '.jpg', '.jpeg', '.webp')):
                    imgs += 1
            elif os.path.isdir(fp):
                if name.startswith('smali'):
                    has_smali = True
                if name == 'res':
                    has_mark = True
    except Exception:
        return False

    if apks:
        return False
    if has_smali or has_mark:
        return True
    if dex_n >= 2:
        return True
    if so_n:
        return True
    if imgs and not dex_n:
        return True
    return False


def refuse_reason(p):
    pp = norm(p)
    if not pp:
        return '空路径'

    # 系统目录先挡。/、/storage 这些既是系统目录、又是 SD 根的上级
    if pp in ('/', '/storage', '/storage/emulated', '/storage/emulated/0',
              '/data', '/data/local', '/data/local/tmp', '/system'):
        return '这是系统目录，不删'

    # 根目录判据：等于 SD 根，或者是 SD 根的上级。
    # 这里犯过一次错：原来写的是 len(pp) <= len(root) + 1，
    # 看着「跟根目录比长度」很机智，其实是一坨 —— 换个假根跑测试（根路径很长）
    # 就全部误判成根目录；真机上要是 SD 挂到更长的路径也一样炸。
    # 改成看结构：pp 是不是 SD 根的上级，跟字符串长度无关。
    root = norm(SD)
    if pp == root or root.startswith(pp + '/'):
        return '这是存储卡根目录（或它的上级），不删'

    if pp in [norm(x) for x in FORBIDDEN_EXACT]:
        return '这是受保护目录，不删'

    # 整棵树保护：相册 / 文档 / Android / MT2 / 各种回收站 / 对话工作区
    for t, why in FORBIDDEN_TREES:
        tn = norm(t)
        if pp == tn or pp.startswith(tn + '/'):
            return why

    ok = False
    for pre in ALLOW_PREFIX:
        if pp.startswith(norm(pre)):
            ok = True
            break
    if ok:
        return ''

    # 白名单之外：只有用户明确要求（allow_outside=true）才按内容特征放行
    if not allow_outside:
        return ('不在逆向产物白名单内（这个工具只清逆向垃圾）。'
                '确实要删它，就再调一次并传 allow_outside_whitelist=true')
    if not looks_like_artifact(pp):
        return '白名单外，且内容不像逆向产物（有成品APK、或只有一个dex），不删'
    return ''


def size_of(p):
    if os.path.isfile(p):
        try:
            return os.path.getsize(p)
        except Exception:
            return 0
    total = 0
    for root, dirs, files in os.walk(p):
        for f in files:
            try:
                total += os.path.getsize(os.path.join(root, f))
            except Exception:
                pass
    return total


res = {'模式': '已确认，执行删除' if confirmed else '只预演（没删任何东西）'}
if allow_outside:
    res['白名单外放行'] = '已开启（allow_outside_whitelist=true）：白名单外的路径按内容特征判断，禁区照旧拒绝'
plan = []
blocked = []
outside_used = []
for p in paths:
    r = refuse_reason(p)
    if r:
        blocked.append({'路径': p, '拒绝原因': r})
        continue
    if not os.path.exists(p):
        blocked.append({'路径': p, '拒绝原因': '不存在（可能已经删过）'})
        continue
    in_wl = False
    for pre in ALLOW_PREFIX:
        if norm(p).startswith(norm(pre)):
            in_wl = True
            break
    if not in_wl:
        outside_used.append(norm(p))
    plan.append({'路径': norm(p), '大小MB': round(size_of(p) / 1048576.0, 1)})

res['将删除'] = plan
res['将删除项数'] = len(plan)
res['将释放MB'] = round(sum(x['大小MB'] for x in plan), 1)
if outside_used:
    res['其中白名单外的'] = outside_used
if blocked:
    res['拒绝'] = blocked
    res['拒绝说明'] = ('这些路径没动。没开白名单外放行时，绝大多数是因为不在白名单里；'
                       '开了之后还拒的，是因为禁区、或者内容不像逆向产物（有成品APK/单个dex）。')

if not confirmed:
    res['结论'] = ('预演完成：确认要删就跑一次 ws_purge，把 confirm 传成 true。'
                   '上面「将释放MB」就是能腾出来的空间。')
    print('WSRESULT:' + json.dumps(res, ensure_ascii=False))
    sys.exit(0)

if not plan:
    res['结论'] = '没有可删的（全被安全阀拦住了，或者路径不对）'
    print('WSRESULT:' + json.dumps(res, ensure_ascii=False))
    sys.exit(0)

done = []
failed = []
for item in plan:
    p = item['路径']
    try:
        if os.path.isfile(p):
            os.remove(p)
        else:
            shutil.rmtree(p, ignore_errors=False)
        done.append(item)
    except Exception as e:
        # /data/local/tmp 里的文件属主是 root，普通权限删不掉 → 换 root 通路再试一次。
        # 这是唯一会用到 root 的删除场景，所以只在 use_root=true 时才试，不多绕。
        if use_root:
            try:
                rc = subprocess.call(['su', '-c', 'rm -rf ' + "'" + p + "'"])
                if rc == 0 and not os.path.exists(p):
                    done.append(item)
                    continue
            except Exception:
                pass
        failed.append({'路径': p, '错误': str(e)[:200]})

res['已删除'] = done
res['已删除项数'] = len(done)
res['实际释放MB'] = round(sum(x['大小MB'] for x in done), 1)
if failed:
    res['失败'] = failed
    res['失败说明'] = ('删不掉的多数在 /data/local/tmp（属主 root）。'
                       '要么把 use_root 传成 true 再来一次，要么用 root 终端手动删。')

if done and not failed:
    res['结论'] = '删完了：%d 项，释放 %.1f MB' % (len(done), res['实际释放MB'])
elif done:
    res['结论'] = ('部分成功：删了 %d 项释放 %.1f MB，另有 %d 项没删掉（见「失败」）'
                   % (len(done), res['实际释放MB'], len(failed)))
else:
    res['结论'] = '一项都没删掉，看「失败」里的原因'

print('WSRESULT:' + json.dumps(res, ensure_ascii=False))
`;

    // ============================================================
    // PY_NOMEDIA
    // ============================================================
    var PY_NOMEDIA = String.raw`
import os, sys, json

raw = sys.argv[1] if len(sys.argv) > 1 else ''
dry = (sys.argv[2] if len(sys.argv) > 2 else '0') == '1'
given = [x.strip() for x in raw.split(',') if x.strip()]

# 根目录允许被 WS_SD 环境变量覆盖 —— 真机上永远是 /sdcard，
# 但必须能在这台电脑上用假目录把清理逻辑跑通，否则这段代码只能靠真机试错。
SD = os.environ.get('WS_SD', '/sdcard').rstrip('/')

# 默认补这些位置。加 .nomedia 的意思是「这里面别扫」，
# 代价是这些目录里的图片不会出现在相册 —— 逆向产物本来也不需要进相册。
DEFAULT_DIRS = [
    SD + '/Download/Operit',
    SD + '/Download/Operit/unpack_out',
    SD + '/Download/Operit/pack360',
    SD + '/Download/Operit/tuoke_out',
    SD + '/Download/tmpchk',
    SD + '/Download/tmpverify',
    SD + '/app_dex',
    SD + '/jiagu_so',
    SD + '/revokit',
]

targets = given if given else DEFAULT_DIRS
res = {'模式': '只预演' if dry else '已写入'}
written = []
skipped = []
for d in targets:
    d = d.replace('\\', '/').rstrip('/')
    if not os.path.isdir(d):
        skipped.append({'目录': d, '原因': '不存在'})
        continue
    f = os.path.join(d, '.nomedia')
    if os.path.exists(f):
        skipped.append({'目录': d, '原因': '已有 .nomedia'})
        continue
    if not dry:
        try:
            with open(f, 'w') as fh:
                fh.write('')
            written.append(d)
        except Exception as e:
            skipped.append({'目录': d, '原因': '写不进去: ' + str(e)[:120]})
    else:
        written.append(d)

res['已写.nomedia'] = written
res['跳过'] = skipped[:20]
res['写入数'] = len(written)

# 顺手看看这些目录底下已经有多少张图 —— 让用户知道之前被扫进去多少。
# 注意：父目录和子目录会重叠（unpack_out 在 Operit 里面），
# 直接相加会把同一张图数两遍（31 张报成 61 张），所以用见过集合去重。
img_n = 0
img_dirs = []
seen_img = set()
for d in sorted(targets, key=len):
    if not os.path.isdir(d):
        continue
    c = 0
    for root, dirs, files in os.walk(d):
        for fn in files:
            if fn.lower().endswith(('.png', '.jpg', '.jpeg', '.webp', '.bmp', '.gif')):
                fp = os.path.normpath(os.path.join(root, fn)).replace('\\', '/')
                if fp in seen_img:
                    continue
                seen_img.add(fp)
                c += 1
        if c > 5000:
            break
    if c:
        img_n += c
        img_dirs.append({'目录': d, '图片数': c})
if img_dirs:
    res['目录里的图片数'] = img_dirs[:10]
    res['图片合计'] = len(seen_img)

if written:
    res['结论'] = ('已给 %d 个目录补上 .nomedia。从现在起新解包的图标不会再进相册。' % len(written))
else:
    res['结论'] = '没有需要补的（都已经有了，或者目录不存在）'
res['旧图标怎么办'] = ('已经进相册的旧图标，.nomedia 管不到 —— 它只能挡以后的扫描。'
                        '要清掉它们：先把这些目录删了（ws_purge），再让系统重扫一次媒体库；'
                        '大多数手机在「设置 → 存储 → 清理」或重启后会自己把失效条目剔掉。'
                        '实在不消失就在文件管理器里删掉后重启一次手机。')
print('WSRESULT:' + json.dumps(res, ensure_ascii=False))
`;

    return {
        ws_scan: wrap(ws_scan),
        ws_purge: wrap(ws_purge),
        ws_nomedia: wrap(ws_nomedia)
    };
})();

exports.ws_scan = ws.ws_scan;
exports.ws_purge = ws.ws_purge;
exports.ws_nomedia = ws.ws_nomedia;
