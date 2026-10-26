/* METADATA
{
  "name": "revokit_net",
  "display_name": {
    "zh": "网络观测（连接监控 / pcap 抓包）",
    "en": "Network Observer"
  },
  "description": "逆向场景的网络观测工具：① 列出某 App 当前所有 TCP/UDP 连接（远端 IP:端口、状态、所属 uid），用来判断它连了哪些服务、有没有二次网络校验、接了哪家 SDK；② 在设备存在 tcpdump 时按其能力抓原始 pcap（可拖进 Wireshark），没有 tcpdump 时如实提示获取方式，不假装能抓。注意：这不是 MITM，抓不到 HTTPS 明文；要看明文需要目标 App 不校验证书并配合外部代理工具。",
  "category": "ReverseEngineering",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "rk_net_apps",
      "description": "列出设备上的应用及其 uid（含包名与运行中进程）。脱壳/抓包前先用它确认目标包名和 uid。只读，不需要 root。",
      "parameters": [
        {
          "name": "filter",
          "description": "可选关键字过滤包名，例如 com.example；留空则只返回第三方应用（排除系统包）",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "rk_net_conn",
      "description": "列出指定 App 当前的网络连接：协议、本地地址、远端地址、TCP 状态、uid。数据直接从 /proc/net/{tcp,tcp6,udp,udp6} 读，需要 root（普通 shell 只能看到自己的连接）。用途：定位 App 连了哪些域名/IP、有没有残留的验证请求、判断是否走私有协议。",
      "parameters": [
        {
          "name": "package_name",
          "description": "目标应用包名（与 uid 二选一）",
          "type": "string",
          "required": false
        },
        {
          "name": "uid",
          "description": "直接给 uid（包名解析不出来时用）",
          "type": "string",
          "required": false
        },
        {
          "name": "resolve",
          "description": "true=尝试反查远端 IP 的主机名（离线或 DNS 不可用时会变慢，默认 false）",
          "type": "string",
          "required": false
        },
        {
          "name": "all",
          "description": "true=不过滤 uid，列出全部连接（用于看整体流量），默认 false",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "rk_net_capture",
      "description": "抓原始 pcap 包：先检测设备上有没有可用的 tcpdump，有则在指定时长内抓包并输出 pcap 文件路径（可直接拖进 Wireshark）；没有则返回具体的获取方式（Magisk 模块 / Termux pkg install tcpdump 后拷到 /data/local/tmp）。只抓原始流量，不做解密，HTTPS 内容是密文。",
      "parameters": [
        {
          "name": "duration",
          "description": "抓包时长（秒），默认 20，上限 300。时长到自动停止，不会留后台进程",
          "type": "string",
          "required": false
        },
        {
          "name": "output",
          "description": "pcap 输出路径，默认 /sdcard/Download/Operit/capture_<时间戳>.pcap",
          "type": "string",
          "required": false
        },
        {
          "name": "filter",
          "description": "可选 pcap 过滤表达式，例如 tcp port 443；留空抓全部",
          "type": "string",
          "required": false
        },
        {
          "name": "action",
          "description": "check=只检测环境（默认）；start=真的开抓",
          "type": "string",
          "required": false
        }
      ]
    }
  ]
}
*/
// ============================================================
// 网络观测工具包
//
// 定位说明（重要，不要误解本包能力）：
//   - 本包做的是「网络观测」，不是 MITM。
//   - rk_net_conn 读内核 socket 表，能看到「连了谁、连得上没、什么状态」，看不到报文内容。
//   - rk_net_capture 依赖设备上真实存在 tcpdump 二进制；Android 原生不带，必须外部提供。
//     有就抓 pcap（密文也能分析 TLS 指纹、SNI、时序），没有就如实说没有。
//   - 想看 HTTPS 明文需要 MITM（VpnService 或系统代理 + 用户证书 + 绕过 SSL Pinning），
//     那是另一套工程，本包不承诺。
// ============================================================
var rknet = (function () {
    'use strict';

    var DEFAULT_OUT = '/sdcard/Download/Operit';

    function wrap(fn) {
        return function (params) {
            return Promise.resolve()
                .then(function () { return fn(params || {}); })
                .then(function (r) { return { success: true, data: r }; })
                .catch(function (e) {
                    return { success: false, message: (e && e.message) ? e.message : String(e) };
                });
        };
    }

    function execRaw(cmd, timeoutMs) {
        return Tools.System.terminal.hiddenExec(cmd, { timeoutMs: timeoutMs || 60000 })
            .then(function (r) { return r || { exitCode: -1, output: '' }; });
    }

    function exec(cmd, timeoutMs) {
        return execRaw(cmd, timeoutMs).then(function (r) {
            if (r.exitCode !== 0) {
                var e = new Error('命令失败 (exit=' + r.exitCode + '): ' + cmd + '\n' + (r.output || '').substring(0, 600));
                e.exitCode = r.exitCode;
                throw e;
            }
            return r;
        });
    }

    function q(s) { return "'" + String(s).replace(/'/g, "'\\''") + "'"; }

    function nowStamp() {
        var d = new Date();
        function p(n) { return (n < 10 ? '0' : '') + n; }
        return d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) + '_'
            + p(d.getHours()) + p(d.getMinutes()) + p(d.getSeconds());
    }

    // 把 python 脚本写到设备再执行
    function runPy(pyCode, args, timeoutMs) {
        var script = DEFAULT_OUT + '/.rknet.py';
        return exec('mkdir -p ' + q(DEFAULT_OUT), 15000)
            .then(function () {
                // 分隔符必须带引号，否则 heredoc 会吃掉 python 里的反斜杠（\\ → \），
                // 直接把脚本搞成 SyntaxError。同一个坑在 local_unpack 里已经踩过。
                var marker = 'RKNEEOF';
                return exec('cat > ' + q(script) + ' <<\'' + marker + '\'\n' + pyCode + '\n' + marker + '\n', 30000);
            })
            .then(function () { return exec('python3 ' + q(script) + ' ' + (args || ''), timeoutMs || 120000); })
            .then(function (r) {
                var out = (r.output || '').trim();
                var idx = out.lastIndexOf('RKRESULT:');
                if (idx >= 0) {
                    try { return JSON.parse(out.substring(idx + 9).trim()); } catch (e) { return { raw: out }; }
                }
                return { raw: out };
            });
    }

    // ---------- 应用与 uid ----------
    function rk_net_apps(params) {
        var filter = (params.filter || '').trim();
        // pm list packages -U 直接带 uid，一步到位，不用额外解析
        return execRaw('pm list packages -U 2>/dev/null', 30000).then(function (r) {
            var out = r.output || '';
            var rows = [];
            out.split('\n').forEach(function (ln) {
                ln = ln.trim();
                if (ln.indexOf('package:') !== 0) return;
                var m = ln.match(/^package:(\S+)\s+uid:(\d+)/);
                if (!m) return;
                rows.push({ 包名: m[1], uid: parseInt(m[2], 10) });
            });
            var result = rows;
            if (filter) {
                result = rows.filter(function (x) {
                    return x.包名.toLowerCase().indexOf(filter.toLowerCase()) >= 0;
                });
            } else {
                // 默认排除系统包（uid < 10000 基本都是系统应用），避免刷屏
                result = rows.filter(function (x) { return x.uid >= 10000; });
            }
            return {
                总数: rows.length,
                命中: result.length,
                应用: result.slice(0, 80),
                说明: filter ? '按关键字「' + filter + '」过滤' : '默认只列第三方应用（uid>=10000）；要全部请给 filter',
                提示: '下一步：拿包名或 uid 调 rk_net_conn 看它连了哪些地址'
            };
        });
    }

    // ---------- 连接表 ----------
    var PY_CONN = String.raw`
import sys, json, os, socket

want_uid = sys.argv[1] if len(sys.argv) > 1 else ''
# 注意：JS 侧传的是字符串 'true'/'false'，在 python 里 'false' 也是真值，
# 所以不能直接拿它做布尔判断，否则过滤会永远失效（已踩，靠测试用例抓出来的）
allflag  = (len(sys.argv) > 2 and sys.argv[2].strip().lower() == 'true')
resolve  = (len(sys.argv) > 3 and sys.argv[3].strip().lower() == 'true')

STATE = {
    '01': 'ESTABLISHED', '02': 'SYN_SENT', '03': 'SYN_RECV', '04': 'FIN_WAIT1',
    '05': 'FIN_WAIT2', '06': 'TIME_WAIT', '07': 'CLOSE', '08': 'CLOSE_WAIT',
    '09': 'LAST_ACK', '0A': 'LISTEN', '0B': 'CLOSING', '0C': 'NEW_SYN_RECV',
}

def hex_to_v4(h):
    # /proc/net/tcp 里的 IPv4 是小端 8 位十六进制，例如 0100007F = 127.0.0.1
    try:
        b = bytes.fromhex(h)
        return '.'.join(str(x) for x in b[::-1])
    except Exception:
        return h

def hex_to_v6(h):
    try:
        b = bytes.fromhex(h)
        # 内核按 4 个 32 位小端字存放
        words = [b[i:i+4][::-1] for i in range(0, 16, 4)]
        raw = b''.join(words)
        ip = socket.inet_ntop(socket.AF_INET6, raw)
        return ip
    except Exception:
        return h

def parse(addr, v6):
    if ':' not in addr:
        return addr
    a, p = addr.rsplit(':', 1)
    ip = hex_to_v6(a) if v6 else hex_to_v4(a)
    try:
        port = int(p, 16)
    except Exception:
        port = p
    return '%s:%s' % (ip, port)

rows = []
for fn, v6 in (('/proc/net/tcp', False), ('/proc/net/tcp6', True),
               ('/proc/net/udp', False), ('/proc/net/udp6', True)):
    try:
        lines = open(fn).read().splitlines()
    except Exception:
        continue
    for ln in lines[1:]:
        p = ln.split()
        if len(p) < 10:
            continue
        try:
            uid = int(p[7])
        except Exception:
            uid = -1
        if not allflag and want_uid and str(uid) != str(want_uid):
            continue
        proto = 'TCP' if 'tcp' in fn else 'UDP'
        rows.append({
            '协议': proto,
            '本地': parse(p[1], v6),
            '远端': parse(p[2], v6),
            '状态': STATE.get(p[3].upper(), p[3]),
            'uid': uid,
            '发送队列': p[4].split(':')[0] if ':' in p[4] else p[4],
            'inode': p[9],
        })

# 去重 + 按远端排序，方便一眼看出连了哪些服务
seen = set()
uniq = []
for r in rows:
    k = (r['协议'], r['本地'], r['远端'], r['状态'])
    if k in seen:
        continue
    seen.add(k)
    uniq.append(r)

if resolve:
    cache = {}
    for r in uniq:
        ip = r['远端'].rsplit(':', 1)[0]
        if ip in cache:
            r['主机名'] = cache[ip]; continue
        try:
            cache[ip] = socket.gethostbyaddr(ip)[0]
        except Exception:
            cache[ip] = ''
        r['主机名'] = cache[ip]

# 汇总远端 IP，回答「它连了谁」
hosts = {}
for r in uniq:
    ip = r['远端'].rsplit(':', 1)[0]
    if ip in ('0.0.0.0', '::', '127.0.0.1', '::1'):
        continue
    hosts[ip] = hosts.get(ip, 0) + 1

print('RKRESULT:' + json.dumps({
    '连接数': len(uniq),
    '涉及远端IP数': len(hosts),
    '远端IP': [{'IP': k, '连接数': v} for k, v in sorted(hosts.items(), key=lambda x: -x[1])][:30],
    '连接明细': uniq[:120],
    '提示': '' if uniq else '没有连接 —— App 可能没在运行，或者它还没发起网络请求。先启动目标 App 再跑一次',
}, ensure_ascii=False))
`;

    function rk_net_conn(params) {
        var pkg = (params.package_name || '').trim();
        var uid = (params.uid || '').trim();
        var doResolve = String(params.resolve || 'false');
        var listAll = String(params.all || 'false');

        function parseResult(out) {
            out = (out || '').trim();
            var i = out.lastIndexOf('RKRESULT:');
            if (i >= 0) {
                try { return JSON.parse(out.substring(i + 9).trim()); } catch (e) { /* fallthrough */ }
            }
            return { error: '解析失败', raw: out.substring(0, 600) };
        }

        // 1) 先把脚本写到设备（一次）
        var pre = exec('mkdir -p ' + q(DEFAULT_OUT), 15000).then(function () {
            var marker = 'RKNEEOF';
            return exec('cat > ' + q(DEFAULT_OUT + '/.rknet.py') + ' <<\'' + marker + '\'\n' + PY_CONN + '\n' + marker + '\n', 30000);
        });

        // 2) 包名 -> uid
        if (!uid && pkg) {
            pre = pre.then(function () {
                return execRaw('pm list packages -U 2>/dev/null | grep -F ' + q('package:' + pkg + ' '), 30000)
                    .then(function (r) {
                        var m = (r.output || '').match(/uid:(\d+)/);
                        if (!m) throw new Error('找不到包 ' + pkg + ' 的 uid（包名对吗？先调 rk_net_apps 确认）');
                        uid = m[1];
                    });
            });
        }
        if (!uid && listAll !== 'true') {
            throw new Error('需要 package_name 或 uid；要列全部连接请传 all=true');
        }

        // 3) 选执行通道：进程本身是 root 就直接跑；否则 su -c；都不行就直跑（只能看到自己）
        return pre
            .then(function () {
                return execRaw('id -u', 8000).then(function (r1) {
                    if ((r1.output || '').trim() === '0') return 'self';
                    return execRaw('su -c id 2>/dev/null', 8000).then(function (r2) {
                        return ((r2.output || '').indexOf('uid=0') >= 0) ? 'su' : 'none';
                    });
                });
            })
            .then(function (channel) {
                var script = DEFAULT_OUT + '/.rknet.py';
                var inner = 'python3 ' + q(script) + ' ' + q(uid || '') + ' ' + q(listAll) + ' ' + q(doResolve);
                var cmd = channel === 'su' ? ('su -c ' + q(inner)) : inner;
                return execRaw(cmd, 120000).then(function (r) {
                    var res = parseResult(r.output);
                    res['权限'] = channel === 'self' ? '进程本身是 root，读全局 socket 表'
                        : channel === 'su' ? '已通过 su 取得 root'
                        : '未取得 root —— 只能看到本进程的连接，别人的 socket 读不到';
                    res['uid'] = uid || '(全部)';
                    res['包名'] = pkg || '';
                    res['说明'] = '这里看到的是「连了谁」，不是报文内容。HTTPS 内容在 pcap 里也是密文；'
                        + '要明文需要 MITM（VpnService/代理 + 用户证书 + 绕过 SSL Pinning），本工具不做';
                    if (channel === 'none') {
                        res['补救'] = '给 Operit 授予 root（Magisk 超级用户里允许），或者用 rk_net_apps 拿 uid 后手工确认';
                    }
                    return res;
                });
            });
    }

    // ---------- tcpdump 抓包 ----------
    var TCPDUMP_PATHS = [
        '/system/bin/tcpdump', '/system/xbin/tcpdump', '/system/sbin/tcpdump',
        '/data/local/tmp/tcpdump', '/data/local/bin/tcpdump',
        '/data/data/com.termux/files/usr/bin/tcpdump'
    ];

    function findTcpdump() {
        // 一次性把所有候选路径探完，返回第一个存在的
        var cmd = 'for p in ' + TCPDUMP_PATHS.map(function (x) { return q(x); }).join(' ')
            + '; do if su -c "test -x $p" 2>/dev/null; then echo "FOUND:$p"; break; fi; done; '
            + 'if command -v tcpdump >/dev/null 2>&1; then echo "PATH:$(command -v tcpdump)"; fi; echo END';
        return execRaw(cmd, 20000).then(function (r) {
            var out = r.output || '';
            var m = out.match(/FOUND:(\S+)/) || out.match(/PATH:(\S+)/);
            return m ? m[1] : '';
        });
    }

    function rk_net_capture(params) {
        var action = (params.action || 'check').toLowerCase();
        var duration = Math.max(3, Math.min(300, parseInt(params.duration || '20', 10)));
        var out = params.output || (DEFAULT_OUT + '/capture_' + nowStamp() + '.pcap');
        var filter = (params.filter || '').trim();

        return exec('mkdir -p ' + q(DEFAULT_OUT), 15000)
            .then(function () { return findTcpdump(); })
            .then(function (bin) {
                if (!bin) {
                    return {
                        可用: false,
                        结论: '这台设备上没有 tcpdump 二进制 —— Android 系统本身不带，所以现在抓不了 pcap',
                        获取方式: [
                            '① Termux：装 Termux 后执行 pkg install tcpdump，然后 '
                                + 'su -c "cp /data/data/com.termux/files/usr/bin/tcpdump /data/local/tmp/ && chmod 755 /data/local/tmp/tcpdump"',
                            '② Magisk 模块：找「tcpdump for Android」类模块刷入（arm64 对应 aarch64 版本）',
                            '③ PC 端：电脑上已有的 tcpdump/Wireshark 配合 adb forward 抓也行，不必在本机抓'
                        ],
                        现在能用的替代方案: [
                            'rk_net_conn —— 看 App 连了哪些 IP:端口、连接状态（不需要 tcpdump，只要 root）',
                            'logfox_toolkit —— 抓网络相关的崩溃日志（比如 SSLHandshakeException 会带主机名）',
                            '要 HTTPS 明文：必须 MITM，需要目标 App 不校验证书；本包不做这件事，别指望 pcap 能直接看明文'
                        ],
                        补充: '放好 tcpdump 之后重跑本工具（action=start）就能抓'
                    };
                }
                if (action !== 'start') {
                    return {
                        可用: true,
                        tcpdump: bin,
                        结论: '设备上有 tcpdump，可以抓包。把 action 设为 start 就开始',
                        预计输出: out,
                        提示: '抓包时长上限 300 秒；到点自动停，不会留后台进程'
                    };
                }
                var cmd = 'su -c ' + q('timeout ' + duration + ' ' + bin + ' -i any -s 0 -p'
                    + (filter ? ' ' + filter : '') + ' -w ' + out);
                return execRaw(cmd, (duration + 20) * 1000).then(function (r) {
                    return exec('ls -l ' + q(out) + ' 2>/dev/null || echo NONE', 15000).then(function (ls) {
                        var sizeTxt = (ls.output || '').trim();
                        var ok = sizeTxt !== 'NONE' && sizeTxt.indexOf('NONE') !== 0;
                        if (!ok) {
                            return {
                                可用: true, 抓到了: false,
                                原因: '命令执行完了但没生成文件（exit=' + r.exitCode + '）：' + (r.output || '').substring(0, 400),
                                常见原因: '没给 root 授权 / SELinux 拦了 / 输出目录不可写'
                            };
                        }
                        return {
                            可用: true,
                            抓到了: true,
                            文件: out,
                            大小: sizeTxt,
                            时长秒: duration,
                            过滤: filter || '(全部)',
                            怎么用: '把 pcap 拷到电脑，用 Wireshark 打开。能看：DNS 查询、TLS SNI（目标域名）、连接时序、'
                                + '包大小分布、证书链。看不了：HTTPS 请求体/响应体（那是密文）',
                            进一步: '想知道某 App 连了哪些地址但不想抓包，用 rk_net_conn 更快'
                        };
                    });
                });
            });
    }

    return {
        rk_net_apps: wrap(rk_net_apps),
        rk_net_conn: wrap(rk_net_conn),
        rk_net_capture: wrap(rk_net_capture)
    };
})();

exports.rk_net_apps = rknet.rk_net_apps;
exports.rk_net_conn = rknet.rk_net_conn;
exports.rk_net_capture = rknet.rk_net_capture;
