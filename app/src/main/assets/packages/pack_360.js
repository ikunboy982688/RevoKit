/* METADATA
{
  "name": "pack_360",
  "display_name": { "zh": "技能书 · 360 加固", "en": "Playbook · 360" },
  "description": "360 加固（数字/720/某60）专属技能书。只做 360。提供代次判定、处理策略、有序修复计划、实战知识。不含执行动作 —— 执行按计划调 local_unpack 的 lu_* 工具。",
  "category": "ReverseEngineering",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "p360_detect",
      "description": "确认是否 360 并判代次（老版 frezrik / 新版 com.stub / 企业版 libjgcxi）。查全包 so、assets、Manifest 桩类。只读。rk_auto 已判过 360 时可不重复调。",
      "parameters": [
        { "name": "apk_path", "description": "APK 完整路径", "type": "string", "required": true }
      ]
    },
    {
      "name": "p360_strategy",
      "description": "返回 360 的处理策略：难度、推荐路线（云端 or 本地主动调用）、前置条件、红线、放弃条件。纯知识。",
      "parameters": [
        { "name": "generation", "description": "代次，如「新版」「老版」；留空返回三档全部", "type": "string", "required": false }
      ]
    },
    {
      "name": "p360_plan",
      "description": "出 360 的有序修复计划（只读）：每步带工具名、包名、参数、失败兜底。按序调 local_unpack 的 lu_* 执行即可。",
      "parameters": [
        { "name": "apk_path", "description": "APK 完整路径", "type": "string", "required": true },
        { "name": "decoded_dir", "description": "已解包目录（可选）", "type": "string", "required": false },
        { "name": "generation", "description": "代次（可选，留空自动判）", "type": "string", "required": false },
        { "name": "real_app", "description": "真实 Application 类名（可选）", "type": "string", "required": false }
      ]
    },
    {
      "name": "p360_knowledge",
      "description": "360 实战知识：为什么单纯 dump 拿到空壳、四类必崩原因、入口三铁律、重打包铁律、何时放弃。纯文本。",
      "parameters": []
    }
  ]
}
*/
// ============================================================
// 360 技能书
//
// 【硬约束】不能用 toolCall 调别的包（实测返回 {} 且不报错）。
// 所以本包只出「知识与计划」，不代替 local_unpack 干活。
// 执行由 AI 按 plan 里的步骤逐个调 local_unpack:lu_* —— AI 调工具是可靠的。
// ============================================================
var p360 = (function () {
    'use strict';

    var OUT = '/sdcard/Download/Operit/pack360';

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
            if (r.exitCode !== 0) throw new Error('命令失败 (exit=' + r.exitCode + ')');
            return r;
        });
    }

    function q(s) { return "'" + String(s).replace(/'/g, "'\\''") + "'"; }

    var PY_DETECT = String.raw`
import sys, json, zipfile, os, re, struct, datetime

apk = sys.argv[1]
r = {"apk": apk, "是否360": False, "代次": "不是360", "置信度": "低", "证据": [],
     "包名": "", "应用类": "", "桩类": ""}

try:
    z = zipfile.ZipFile(apk)
    names = z.namelist()
except Exception as e:
    print("P360:" + json.dumps({"error": "打不开：" + str(e)}, ensure_ascii=False)); sys.exit(0)


def parse_axml(data):
    out = {"package": "", "app_class": ""}
    if len(data) < 8:
        return out
    try:
        if struct.unpack_from("<H", data, 0)[0] != 0x0003:
            return out
        p = 8
        strings = []
        while p + 8 <= len(data):
            ctype, hsize, csize = struct.unpack_from("<HHI", data, p)
            if csize <= 0 or p + csize > len(data):
                break
            if ctype == 0x0001:
                cnt, _sc, flags, str_start, _ = struct.unpack_from("<IIIII", data, p + 8)
                utf8 = (flags & 0x100) != 0
                offs = struct.unpack_from("<%dI" % cnt, data, p + 28)
                base = p + str_start
                for off in offs:
                    q = base + off
                    if utf8:
                        n = data[q]; q += 2 if n & 0x80 else 1
                        n = data[q]
                        if n & 0x80:
                            n = ((n & 0x7f) << 8) | data[q+1]; q += 2
                        else:
                            q += 1
                        strings.append(data[q:q+n].decode("utf-8", "ignore"))
                    else:
                        n = struct.unpack_from("<H", data, q)[0]; q += 2
                        if n & 0x8000:
                            n = ((n & 0x7fff) << 16) | struct.unpack_from("<H", data, q)[0]; q += 2
                        strings.append(data[q:q+n*2].decode("utf-16-le", "ignore"))
                break
            p += csize

        def s(i):
            return strings[i] if 0 <= i < len(strings) else ""

        p = 8
        while p + 8 <= len(data):
            ctype, hsize, csize = struct.unpack_from("<HHI", data, p)
            if csize <= 0 or p + csize > len(data):
                break
            if ctype == 0x0102:
                name = s(struct.unpack_from("<I", data, p + 20)[0])
                astart, asize, acount = struct.unpack_from("<HHH", data, p + 24)
                if name in ("manifest", "application"):
                    a = p + 16 + astart
                    for k in range(acount):
                        b = a + k * asize
                        if b + 20 > len(data):
                            break
                        _ns, aname, raw, _vs, _r0, _dt, dval = struct.unpack_from("<IIIHBBI", data, b)
                        key = s(aname).split(":")[-1]
                        val = s(raw) if raw != 0xFFFFFFFF else str(dval)
                        if name == "manifest" and key == "package":
                            out["package"] = val
                        if name == "application" and key == "name":
                            out["app_class"] = val
                    if name == "application":
                        break
            p += csize
    except Exception:
        pass
    return out


man = {"package": "", "app_class": ""}
try:
    man = parse_axml(z.read("AndroidManifest.xml"))
except Exception:
    pass
r["包名"] = man.get("package") or ""
appcls = man.get("app_class") or ""
r["应用类"] = appcls

so = {os.path.basename(n): n for n in names if n.lower().endswith(".so")}

SO_360 = {
    "libjiagu.so": "标准", "libjiagu_art.so": "标准", "libjiagu_a64.so": "64位",
    "libjiagu_64.so": "64位", "libjiagu_x86.so": "x86", "libjiagu_sdk.so": "SDK变体",
    "libjiagu_vip.so": "企业版", "libjgcxi.so": "企业版",
    "libjiagu_vip_a64.so": "企业版", "libjgcxi_a64.so": "企业版",
}
vip = False
for b, path in so.items():
    if b in SO_360:
        if SO_360[b] == "企业版":
            vip = True
        r["证据"].append({"文件": b, "路径": path, "档次": SO_360[b],
                         "位置": "lib/" if path.startswith("lib/") else "非lib"})

for n in names:
    low = n.lower()
    if low.startswith("assets/") and ("libjiagu" in low or "libjgcxi" in low or low.endswith(".jgapp")):
        r["证据"].append({"文件": os.path.basename(n), "路径": n, "档次": "资源", "位置": "assets"})

STUB_PAT = (r"com\.frezrik\.jiagu", r"com\.stub\.StubApp",
            r"com\.qihoo\.", r"com\.byazt\.")
stub = ""
for pat in STUB_PAT:
    if appcls and re.search(pat, appcls):
        stub = appcls
        break
if not stub:
    try:
        txt = z.read("AndroidManifest.xml").decode("utf-16-le", errors="ignore")
        for pat in STUB_PAT:
            m = re.search(pat + r"[\w\.]*", txt)
            if m:
                stub = m.group(0)
                break
    except Exception:
        pass
r["桩类"] = stub

has_so = len([e for e in r["证据"] if e["位置"] != "assets"]) > 0
has_res = len([e for e in r["证据"] if e["位置"] == "assets"]) > 0
if not (has_so or has_res or stub):
    print("P360:" + json.dumps(r, ensure_ascii=False)); sys.exit(0)

r["是否360"] = True
# 代次判据优先级（高 → 低）：
#   ① 企业/VIP 硬特征
#   ② ART 层壳本体 libjiagu_art.so / jiagu.art / jiagu_res.zip —— V8 才有
#   ③ 桩类 com.frezrik / com.qihoo.util —— V5 老壳
#   ④ 桩类 com.stub / com.byazt —— **跨代**（2019 年就在用），必须叠 dex 时间：
#      早于 QIHOO_V8_ERA（2024-01-01）→ 老版；否则新版
#   ⑤ 只有 libjiagu*.so → 老版
_sos = set(os.path.basename(n).lower() for n in names if n.lower().endswith(".so"))
_vip_so = {"libjiagu_vip.so", "libjiagu_vip_a64.so", "libjgcxi.so", "libjgcxi_a64.so",
           "libvmp_360.so", "libjiagu_enterprise.so"}
_ent_res = any(n.endswith(("vmp_config.bin", "jiagu_enterprise.dat")) for n in names)
_new_res = any(n.endswith(("jiagu.art", "jiagu_res.zip")) for n in names)

# 0701 与 V8 分界都要用 dex 时间，所以**先算时间、再判代次**（顺序反了就拿不到时间证据）
ZIP_SANE_MIN = 1420070400000
QIHOO_PAID_CUT = 1782835200000  # 2026-07-01 00:00:00 GMT+8
QIHOO_V8_ERA = 1704038400000    # 2024-01-01 00:00:00 GMT+8（经验阈值）
_ts = []
for _n in names:
    if re.match(r"^classes\d*\.dex$", _n):
        try:
            _t = int(datetime.datetime(*z.getinfo(_n).date_time).timestamp() * 1000)
            if _t > 0:
                _ts.append(_t)
        except Exception:
            pass
_sane = [t for t in _ts if t >= ZIP_SANE_MIN]
_paid = bool(_sane) and min(_sane) >= QIHOO_PAID_CUT

def _d(ms):
    try:
        return datetime.datetime.fromtimestamp(ms / 1000.0).strftime("%Y-%m-%d")
    except Exception:
        return "?"

gen = "未知代次"
gen_why = ""
if vip or (_sos & _vip_so) or _ent_res:
    gen, gen_why = "企业版", "企业/VIP 版专属 so 或资源"
elif "libjiagu_art.so" in _sos or _new_res:
    gen, gen_why = "新版", "ART 层壳本体（libjiagu_art.so / jiagu.art / jiagu_res.zip），老版没有"
elif stub.startswith(("com.frezrik", "com.qihoo.util")):
    gen, gen_why = "老版", "V5 老壳桩类 " + stub
elif stub.startswith(("com.stub", "com.byazt")):
    if _sane and min(_sane) < QIHOO_V8_ERA:
        gen, gen_why = "老版", ("桩类 %s 跨代不可靠，叠 dex 时间 %s（早于 2024-01-01 的 V8 分界）→ 老版"
                                % (stub, _d(min(_sane))))
    elif _sane:
        gen, gen_why = "新版", ("桩类 %s + dex 时间 %s（晚于 2024-01-01）→ 新版"
                                % (stub, _d(min(_sane))))
    else:
        gen, gen_why = "新版", "桩类 %s（dex 时间被抹，无法用时间校正，按现行引擎判新版）" % stub
elif any(s.startswith("libjiagu") for s in _sos):
    gen, gen_why = "老版", "只有 libjiagu*.so，无可识别的桩类"

# 0701：2026-07-01 起 360 加固保停止免费加固，之后走付费引擎。
# 时间戳若被打包器抹成固定值就判不出来，这时不加 0701 —— 猜错比不写更糟。
r["付费时代"] = _paid
if gen != "未知代次" and _paid:
    gen += "0701"
    # 依据里必须带上 0701，否则会出现「代次=新版0701 但依据写→新版」的自相矛盾
    if gen_why:
        gen_why += "；再叠 0701 付费时代"
r["代次"] = gen
r["代次依据"] = gen_why
r["显示名"] = ("360加固（%s）" % gen) if gen != "未知代次" else "360加固（代次未定）"
r["置信度"] = "高" if (stub and has_so) else ("中" if (stub or has_so) else "低")
r["难度"] = "极高" if "企业版" in gen else ("中（云端可解）" if gen.startswith("老版") else "高（真函数抽取）")
print("P360:" + json.dumps(r, ensure_ascii=False))
`;

    function p360_detect(params) {
        var apk = params.apk_path;
        if (!apk) throw new Error('缺少 apk_path');
        return exec('mkdir -p ' + q(OUT), 15000)
            .then(function () {
                var script = OUT + '/.det.py';
                // 分隔符必须带引号。不带引号的 heredoc 会做反斜杠转义处理，
                // PY_DETECT 里那些 `\.` `\w` 虽然目前侥幸没被吃掉，但同一个坑
                // 在 local_unpack 上真的炸过（接连 4 个包 SyntaxError: unterminated
                // string literal）—— 一旦哪天有人往这段里加个 `\\` 就必炸。
                // 统一写成 <<'P360EOF'，不留这个隐患。
                return exec('cat > ' + q(script) + ' <<\'P360EOF\'\n' + PY_DETECT + '\nP360EOF\n', 30000)
                    .then(function () { return exec('python3 ' + q(script) + ' ' + q(apk), 120000); });
            })
            .then(function (r) {
                var out = (r.output || '').trim();
                var i = out.lastIndexOf('P360:');
                if (i < 0) throw new Error('识别没出结果：' + out.substring(0, 200));
                return JSON.parse(out.substring(i + 5).trim());
            });
    }

    function p360_strategy(params) {
        var want = String(params.generation || '').trim();
        var ALL = {
            '老版': {
                难度: '中',
                路线: '云端优先：tuoke_all。云端能直接吐完整方法体，本地跑常拿壳容器',
                前置: ['能联网', '56al_tuoke 包已启用并登录'],
                红线: ['云端产物必须 lu_dump_check 体检，class_defs<800 是容器，别往下修'],
                放弃: '云端不出结果且无主动调用模块 → 转「只改资源」'
            },
            '新版': {
                难度: '高',
                路线: '本地主动调用：lu_dump，rounds≥2。必须 FunDex2 / FART / frida_fart',
                前置: ['root', '已装支持主动调用的模块', '模块作用域含目标 App'],
                红线: [
                    '主动加载不能跑主线程 —— 类初始化等 onCreate 返回，互等死锁，表现是启动白屏',
                    '改完作用域必须重启 zygote，只重启 lspd 不生效',
                    'dump 完先 lu_dump_check 体检，别拿容器当宝贝'
                ],
                放弃: '无主动调用模块 → 只能 lu_fill_oncreate 邪修（约 30%），剩下靠抓日志循环补'
            },
            '企业版': {
                难度: '极高',
                路线: '先云端，再本地主动调用。企业版常连主动调用都拿不全',
                前置: ['同新版', '心理准备：可能白干'],
                红线: ['常带反调试 + 二次校验', '别承诺一定脱得掉'],
                放弃: '抽取率极高且反调试顽固 → 只改资源，或如实说做不到'
            },
            '未知代次': {
                难度: '高',
                路线: '先解包看 Manifest 的 application android:name 定代次，拿不准就云端兜底',
                前置: ['apktool 解包'],
                红线: ['代次判错整条路线都错'],
                放弃: '仍判不出 → 云端试一次，失败放弃本地硬啃'
            }
        };
        var out;
        if (want) {
            out = ALL[want] || null;
            for (var k in ALL) {
                if (ALL.hasOwnProperty(k) && want.indexOf(k) >= 0) { out = ALL[k]; out.代次 = k; break; }
            }
            if (!out) out = { 说明: '未匹配代次，返回全部', 全部: ALL };
        } else {
            out = { 说明: '未指定代次', 全部: ALL };
        }
        out.通用红线 = [
            '真实 Application 必须是继承链叶子节点，不是基类',
            'Manifest 的 rawValue 与 typedValue.data 必须同时改',
            'extractNativeLibs=true 时 .so 保持 DEFLATED',
            'zipalign 在前，apksigner 在后，v1+v2+v3 全开'
        ];
        return out;
    }

    function baseName(p) {
        var s = String(p || '').split('/').pop();
        return s.replace(/\.apk$/i, '') || 'target';
    }

    function p360_plan(params) {
        var apk = params.apk_path;
        if (!apk) throw new Error('缺少 apk_path');
        var decoded = params.decoded_dir || (OUT + '/' + baseName(apk) + '_dec');
        var genIn = String(params.generation || '').trim();

        // 始终跑一次 detect：包名/应用类/桩类都靠它，指定代次时只覆盖代次字段
        return p360_detect({ apk_path: apk })
            .then(function (det) {
                if (det && det.是否360 === false) {
                    return { 归本包管: false, 结论: '这不是 360，调 revokit_core:rk_auto 让调度器重判' };
                }
                var gen = genIn || det.代次 || '未知代次';
                var isOld = gen.indexOf('老版') >= 0;
                var isNew = gen.indexOf('新版') >= 0 || gen.indexOf('企业版') >= 0;

                // 包名尽量给实值：p360_detect 已从 AXML 读出来了。
                // 读不出就留空（别塞提示文字，会被当参数传下去），另给「怎么取」的说明
                var pkg = String(params.package_name || det.包名 || '').trim();
                var pkgArg = pkg;
                var pkgHint = pkg ? '' : '包名读不出：先 lu_decode，再读解包目录 AndroidManifest.xml 第一行的 package="..."；或设备上 pm list packages -3 查';
                var dexDir = '/sdcard/Download/Operit/unpack_out/' + (pkg || baseName(apk)) + '/dex';
                var outDex = decoded + '_dex';
                var rebuilt = decoded + '/rebuilt.apk';

                var steps = [];

                // 序 0：环境闸门。不做这一步，后面的计划会在「没装 FunDex2」的机器上直接卡死，
                // 而 AI 只会回一句「去装模块」——用户要的是「现在能用什么」。
                steps.push({
                    序: 0, 动作: '环境闸门（先跑这个，别跳）', 包: 'local_unpack', 工具: 'lu_env_check',
                    参数: {},
                    说明: '看返回里的「脱壳能力判断.首选」—— 它已按本机装机情况算好唯一可行路线，'
                        + '下面的脱壳步骤只在首选是本地主动调用时才执行',
                    分叉: {
                        '首选含 lu_dump（FunDex2/FART 在位）': '按序 1 走主动调用',
                        '首选含 lu_dump_li（Layout Inspect 在位）': '改调 local_unpack:lu_dump_li，后面的体检/修复步骤不变',
                        '首选是 eBPF（内核支持）': '先把 eBPFDexDumper-rs 推进 /data/local/tmp，再用 lu_dump',
                        '首选是云端（一个本地脱壳模块都没有）': '直接调 56al_tuoke:tuoke_all，'
                            + '拿到 dex 后从「产物体检」那一步继续，不要停下来让用户装模块'
                    }
                });

                if (isNew) {
                    steps.push({
                        序: steps.length, 动作: '主动调用脱壳', 包: 'local_unpack', 工具: 'lu_dump',
                        参数: { package_name: pkgArg, rounds: '2', wait_seconds: '60', output_dir: dexDir },
                        前置: 'FunDex2/FART/frida_fart 在位且作用域含目标；改过作用域必须重启 zygote',
                        兜底: '没有主动调用模块 → 只能 lu_fill_oncreate 邪修（约 30%）'
                    });
                } else if (isOld) {
                    steps.push({
                        序: steps.length, 动作: '云端脱壳', 包: '56al_tuoke', 工具: 'tuoke_all',
                        参数: { apk_path: apk },
                        前置: '56al_tuoke 包已启用并登录',
                        兜底: '云端失败 → 本地 lu_dump，或转只改资源'
                    });
                    steps.push({
                        序: steps.length, 动作: '产物体检', 包: 'local_unpack', 工具: 'lu_dump_check',
                        参数: { dex_path: '/sdcard/Download/Operit/56al/' + (pkg || baseName(apk)), package_name: pkgArg },
                        判定: '类总数≥3000 才是真货；<800 是壳容器，别往下修'
                    });
                } else {
                    steps.push({
                        序: steps.length, 动作: '先定代次', 包: 'pack_360', 工具: 'p360_detect',
                        参数: { apk_path: apk },
                        说明: '看 Manifest 的 application 是 frezrik(老版) 还是 stub/qihoo/byazt(新版)',
                        兜底: '判不出 → 云端 tuoke_all 试一次'
                    });
                }

                if (isNew) {
                    steps.push({
                        序: steps.length, 动作: '产物体检', 包: 'local_unpack', 工具: 'lu_dump_check',
                        参数: { dex_path: dexDir, package_name: pkgArg },
                        判定: '抽取率>70% 说明没脱干净，回第 1 步加 rounds；类总数<800 是容器'
                    });
                }

                steps.push({
                    序: steps.length, 动作: '解包', 包: 'local_unpack', 工具: 'lu_decode',
                    参数: { apk_path: apk, output_dir: decoded, force: 'true' },
                    兜底: 'lu_decode 报没 apktool → 直接调 apk_reverse:apk_reverse_decode'
                        + '({ input_apk_path: apk, output_dir: ' + decoded + ', force: true })，'
                        + '解到同一目录后继续，不要改用 MT 手动操作'
                });
                steps.push({
                    序: steps.length, 动作: '扫抽取率', 包: 'local_unpack', 工具: 'lu_scan',
                    参数: { decoded_dir: decoded },
                    判定: '空方法体>70% → 回去重脱，别修'
                });
                steps.push({
                    序: steps.length, 动作: '邪修回填（预演）', 包: 'local_unpack', 工具: 'lu_fill_oncreate',
                    参数: { decoded_dir: decoded, dry_run: 'true' },
                    说明: '把 onCreate 的 smali 抄回被抽空的方法，约 30% 命中'
                });
                steps.push({
                    序: steps.length, 动作: '★启动页接回（预演）', 包: 'local_unpack', 工具: 'lu_splash_fix',
                    参数: { decoded_dir: decoded, dry_run: 'true' },
                    说明: '专治「邪修回填后装上去不崩、但界面死在启动页不动」。'
                        + 'lu_fill_oncreate 的前提是「父类那份 onCreate 够用」，'
                        + '但幕布那种父类只设横竖屏和状态栏、一次业务方法调用都没有的情况，'
                        + '继承它等于什么都不发生。本工具自动判定哪些类属于这种「父类只做半套」，'
                        + '列出同类里还活着的候选入口（360 只抽生命周期那个方法，'
                        + '它内部调的私有入口方法全都还在），并在 onCreate 里补一句调用把流程接回去。'
                        + '判定依据是「父类调的方法里有没有一个被子类重写」——'
                        + '有就能虚分派到子类（mqjz 的 initView 就是这样），没有就得接回',
                    判定: '自动选的入口不对 → 用 entry 参数指定正确方法名再跑一次'
                });
                steps.push({
                    序: steps.length, 动作: '清桩调用（预演）', 包: 'local_unpack', 工具: 'lu_stub_clean',
                    参数: { decoded_dir: decoded, dry_run: 'true' },
                    说明: '删 StubApp/DtcLoader/frezrik/qihoo 调用，连带删 move-result-*'
                });
                steps.push({
                    序: steps.length, 动作: '入口回填（预演）', 包: 'local_unpack', 工具: 'lu_manifest_fix',
                    参数: { decoded_dir: decoded, real_app: params.real_app || '', drop_proxy: 'true', dry_run: 'true' },
                    说明: '① android:name 换成真实 Application（继承链叶子节点）② drop_proxy 删 com.byazt.ff.Stub_*Activity，否则 ClassNotFound',
                    兜底: '真实 Application 类名从解包目录里找：grep -rl "extends Application" 或看壳 dex 的 attachBaseContext 调用点'
                });
                steps.push({
                    序: steps.length, 动作: 'dex 重排', 包: 'local_unpack', 工具: 'lu_repack_assemble',
                    参数: { output_dir: outDex, dump_dir: dexDir, apk_path: apk, dry_run: 'true' },
                    说明: 'dex 编号必须连续，跳号 ART 直接拒绝加载'
                });
                steps.push({
                    序: steps.length, 动作: '生成兼容桩（预演）', 包: 'local_unpack', 工具: 'lu_stub_scaffold',
                    参数: { stub_smali: '', output_dir: decoded, real_app: params.real_app || '', dry_run: 'true' },
                    说明: '业务代码里 55+ 处调 StubApp.getOrigApplicationContext，桩删干净会 NoClassDefFound。'
                        + 'stub_smali 必填：填壳桩类的 smali 路径，一般是 ' + decoded + '/smali*/com/stub/StubApp.smali',
                    可跳过: 'lu_stub_clean 报告没有残留引用时，这一步不需要',
                    兜底: '找不到桩 smali → 说明这个包没有 StubApp 兼容需求，跳过即可'
                });
                steps.push({
                    序: steps.length, 动作: '★dex 层启动页接回（预演）', 包: 'local_unpack', 工具: 'lu_code_patch',
                    参数: {
                        dex_dir: decoded,
                        prefix: (pkg && pkg !== '-') ? ('L' + pkg.replace(/\./g, '/') + '/') : '-',
                        dry_run: 'true'
                    },
                    说明: '这一步是幕布 com.mubu.app 真机补出来的，专门治「不崩了但永远卡在启动页」。'
                        + '★必须跑在 lu_v9_patch 之前：v9 会把被抽走的 onCreate 定义删掉，'
                        + '删完本工具就没有「哪个类被抽过」的证据了。'
                        + '根因：删定义后子类继承父类实现，但父类往往「只做了一半」——'
                        + '幕布 BaseSplashActivity.onCreate 只有 37 条指令，设横竖屏和状态栏颜色，一次虚分派都没有，'
                        + '真正的跳转全在被抽走的 MubuSplashActivity.onCreate 里。'
                        + '而 360 通常只抽生命周期那一个，同类里真正的业务入口还活着、只是没人调用。'
                        + '本工具找一个必定会被框架调用的存活方法（onResume 优先）做「搭载」，'
                        + '原地等长改写成 invoke-super 父类同名方法 + 调用业务入口 + return-void。'
                        + '等长 = 新指令不超过原 unit 数就整段覆盖、余下填 nop → 偏移全不变 → 不碰 map_list',
                    判定: '第一次跑一定别带 cls，只看「分析」列表：找启动/闪屏那个 Activity，'
                        + '确认它的「首选入口」确实是真正的业务入口（幕布实测自动选中 checkAndShowMubuTerm，80 分，'
                        + '与 PC 端手工选定的完全一致）。确认后带 cls、dry_run 改 false 再跑一次。'
                        + '★只改启动入口那一个类，别的 Activity 交给 v9 继承父类就够了',
                    兜底: '① 如果 lu_repack_assemble 把 dex 输出到别的目录而不是解包目录，dex_dir 要跟着改；'
                        + '② 自动选的入口不对 → 用 entry 参数指定正确方法名；'
                        + '③ 已经跑过 v9 了才发现卡启动页 → 直接带 cls + entry 手动跑，不再依赖自动判定'
                });
                steps.push({
                    序: steps.length, 动作: '★dex 层删抽取定义（预演）', 包: 'local_unpack', 工具: 'lu_v9_patch',
                    参数: {
                        dex_dir: decoded,
                        prefix: (pkg && pkg !== '-') ? ('L' + pkg.replace(/\./g, '/') + '/') : '-',
                        dry_run: 'true'
                    },
                    说明: '这一步在岛风真机上补出来的，缺了它前面全白干。'
                        + '手机端链路是「apktool 解包原包 + 把脱壳 dump 出来的 dex 直接塞进解包目录」，'
                        + '而 apktool 只反编译得动**壳 dex** —— 岛风那次整个解包目录里业务包的 smali 数是 0，'
                        + '全目录只有 1 个 com/stub/StubApp.smali。于是走 smali 路线的 lu_fill_oncreate 和 '
                        + 'lu_splash_fix 一个业务类都覆盖不到，出包后主界面能进、一点「立即登录」就崩：'
                        + 'UnsatisfiedLinkError: No implementation found for ...LoginActivity.onCreate。'
                        + '本工具直接在 dex 字节上动手，不依赖 smali。'
                        + '原理是删掉那条「ACC_NATIVE + code_off=0」的定义让子类继承父类真实实现，'
                        + '再用非最短 uleb128 把省下的字节填回 4 个 size 字段，长度分毫不差、偏移全不变、不碰 map_list。'
                        + 'PC 端妙钱记账/幕布/岛风三样本都是这么跑通的',
                    判定: '看「改动明细」里的类是不是主程序自己的类；「其它native无体」那批绝大多数是真 JNI'
                        + '（sqlcipher / 百度OCR / 有道翻译 / OcrEngine —— 岛风 138 个里 132 个是真的），千万别删。'
                        + '确认后 dry_run 改 false 再跑一次',
                    兜底: '如果 lu_repack_assemble 把 dex 输出到别的目录而不是解包目录，dex_dir 要跟着改成那个目录'
                });
                steps.push({
                    序: steps.length, 动作: '重打包+签名', 包: 'local_unpack', 工具: 'lu_rebuild',
                    参数: { decoded_dir: decoded, output_apk: rebuilt },
                    兜底: '签名失败（无 apksigner）→ apk_reverse:apk_reverse_build_and_sign'
                        + '({ decoded_dir: ' + decoded + ', output_apk_path: ' + rebuilt + ', sign_mode: "debug" })；'
                        + '装不上 → lu_zipfix 把 .so 改 STORED，再推到 /data/local/tmp 安装'
                });
                steps.push({
                    序: steps.length, 动作: '验证启动', 包: 'logfox_toolkit', 工具: 'apk_launch_test',
                    参数: { apk_path: rebuilt, package: pkgArg },
                    说明: '崩了抓 logcat(*:V)，把栈贴给 AI 定位缺失类，循环修',
                    禁令: '**绝对不要用 monkey 启动 App** —— monkey 一跑就会把系统「自动旋转」开关打开'
                        + '（实测：跑之前 accelerometer_rotation=0，跑完变 1），用户每次跑完任务都发现自动旋转被开了。'
                        + '一律用 `am start -n <包名>/<主Activity>`，或 `cmd package resolve-activity --brief <包名> | tail -1` 取到主 Activity 再 start。'
                        + '同理，任何 `settings put system/secure/global` 都不许碰跟本任务无关的用户偏好（自动旋转 / 亮度 / 音量 / 字体 / 壁纸）'
                });

                return {
                    归本包管: true, 代次: gen, 包名: pkg, 桩类: det.桩类 || '', 应用类: det.应用类 || '',
                    解包目录: decoded, 脱壳产物目录: dexDir, 步骤: steps,
                    包名提示: pkgHint,
                    提醒: '前两步（脱壳 + 体检）没做完，后面修的都是空壳。dry_run 步骤确认无误后改 false 再跑一遍',
                    两条硬纪律: [
                        '**禁用 monkey 启动 App**：monkey 会把系统「自动旋转」开关打开（实测 0→1），'
                        + '用户每次跑完任务都发现自动旋转被开了。一律用 am start。',
                        '**不许乱动系统设置**：除任务必需的项（如 adb 调试、未知来源安装）外，'
                        + '自动旋转 / 亮度 / 音量 / 字体 / 壁纸这类用户偏好一个都不许碰。'
                        + '真要临时改，改之前告知、用完恢复原值。'
                    ]
                };
            });
    }

    function p360_knowledge() {
        return {
            核心原理: [
                '360 是函数抽取壳：方法体在原始 dex 里被抽走，只有该方法即将执行时才由壳还原进内存',
                '所以「启动 App 再 dump」拿到的是空壳 —— 最常见的白干方式',
                '必须主动调用：FunDex2 / FART / frida_fart'
            ],
            代次差异: {
                老版: 'frezrik 桩。云端能吐完整方法体，本地跑常拿壳容器',
                新版: 'com.stub / com.qihoo / com.byazt 桩。真抽取，云端也拿不全，必须本地主动调用',
                企业版: 'libjgcxi / libjiagu_vip。常带反调试，连主动调用都可能拿不全'
            },
            四类必崩: [
                { 现象: '启动白屏', 原因: '主动加载跑主线程 → 类初始化等 onCreate 返回，互等死锁', 解法: '确认模块在后台线程做主动调用' },
                { 现象: 'ClassNotFoundException: com.byazt.ff.Stub_*_Activity', 原因: '壳代理 Activity 住在壳 dex 里，壳 dex 丢了就没了', 解法: 'lu_manifest_fix drop_proxy=true' },
                { 现象: 'NoClassDefFoundError: com.stub.StubApp', 原因: '业务代码 55+ 处调 StubApp.getOrigApplicationContext，桩删了会缺类', 解法: 'lu_stub_scaffold 生成签名一致的兼容桩' },
                { 现象: 'ART 拒绝加载', 原因: 'dex 编号跳号（原包 classes69~72，脱壳产物 classes.dex/2..8）', 解法: 'lu_repack_assemble 连续编号' }
            ],
            入口三铁律: [
                '① 真实 Application 是继承链叶子节点，不是基类',
                '② rawValue 和 typedValue.data 必须同时改',
                '③ 16 字符桩类名改 33 字符真实类名，字符串池槽位不够 → 必须 apktool 解码回编译，不能二进制替换'
            ],
            重打包铁律: [
                'apktool -s 只解资源、dex 原样拷贝，省十几分钟',
                'extractNativeLibs=true 时 .so 保持 DEFLATED',
                'zipalign 在前，apksigner 在后，之后改动 ZIP 字节要重签',
                'minSdk≥24 显式 v1+v2+v3',
                '装不上八成是 lib 下 .so 被压缩 → lu_zipfix 改 STORED'
            ],
            该放弃: [
                'VMP 壳（娜迦/阿里）→ 字节码虚拟化，救不回来',
                '抽取率>70% 且无主动调用模块 → 只改资源',
                '企业版 + 顽固反调试 → 如实说做不到'
            ]
        };
    }

    return {
        p360_detect: wrap(p360_detect),
        p360_strategy: wrap(p360_strategy),
        p360_plan: wrap(p360_plan),
        p360_knowledge: wrap(p360_knowledge)
    };
})();

exports.p360_detect = p360.p360_detect;
exports.p360_strategy = p360.p360_strategy;
exports.p360_plan = p360.p360_plan;
exports.p360_knowledge = p360.p360_knowledge;
