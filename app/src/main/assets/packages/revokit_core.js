/* METADATA
{
  "name": "revokit_core",
  "display_name": { "zh": "GG工坊调度", "en": "GOOG Router" },
  "description": "脱壳修复总入口。一次调用完成：扫 APK → 判壳型与代次 → 告诉你该调哪个技能包、下一步什么参数。不认识任何壳的细节，也不代替技能包干活。",
  "category": "ReverseEngineering",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "rk_auto",
      "description": "★拿到 APK 就调这个，一次到位。扫 APK 判壳型/代次/是否只是壳容器，返回：命中什么壳、代次依据、该调哪个包的什么工具（含完整参数）、通用红线。不写文件。用户说「仅识别/别动包/只看一下」时必须传 mode=identify。",
      "parameters": [
        { "name": "apk_path", "description": "APK 完整路径", "type": "string", "required": true },
        { "name": "mode", "description": "identify = 只识别，不给方案/下一步；省略 = 完整路由", "type": "string", "required": false }
      ]
    },
    {
      "name": "rk_playbook",
      "description": "零参数返回分层架构与 AI 标准动作序列、通用红线、什么时候该放弃。不确定流程时读它。",
      "parameters": []
    }
  ]
}
*/
// ============================================================
// GG工坊调度层
//
// 【硬约束，别再踩】ToolPkg 脚本里**不能**用 toolCall 调别的包的工具 ——
// 实测返回空对象 {} 且不抛错（2026-10-24 test13 真机日志）。
// 所以本包必须自给自足：所有识别自己用 shell + python 做完，不跨包。
// 编排交给 AI：本包只负责「判什么壳、该调谁、参数是什么」。
//
// 分层：
//   L0 local_unpack  机械操作（解包/改 smali/修 dex 头/重打包/签名），壳无关
//   L1 revokit_core  本包。只做识别与路由
//   L2 pack_360      360 的知识与计划（detect/strategy/plan/knowledge）
// ============================================================
var rk = (function () {
    'use strict';

    var OUT = '/sdcard/Download/Operit/rk';

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
                var e = new Error('命令失败 (exit=' + r.exitCode + ')：' + (r.output || '').substring(0, 400));
                throw e;
            }
            return r;
        });
    }

    function q(s) { return "'" + String(s).replace(/'/g, "'\\''") + "'"; }

    // 一次扫完：壳特征 + 桩类 + dex 头。输出保持精简，避免撑爆上下文。
    var PY_SCAN = String.raw`
import sys, json, zipfile, os, re, struct, datetime

apk = sys.argv[1]
r = {"apk": apk}

try:
    z = zipfile.ZipFile(apk)
    names = z.namelist()
except Exception as e:
    print("RKRESULT:" + json.dumps({"error": "打不开 APK：" + str(e)}, ensure_ascii=False)); sys.exit(0)

# ---- zip 条目时间：判 360 的「0701 付费时代」----
# 2026-07-01 起 360 加固保停止免费加固，之后的产物走付费引擎（更重的 VMP / 反调试）。
# 硬坑：大量打包器（zipflinger、可重现构建流水线）会把条目时间统一写成 1980/2008 固定值，
# 那时可信=False，绝对不能拿时间判年代 —— 宁可说不知道，也不能硬猜。
ZIP_SANE_MIN = 1420070400000   # 2015-01-01，早于它一律当假时间
QIHOO_PAID_CUT = 1782835200000  # 2026-07-01 00:00:00 GMT+8

# 360 引擎换代分界：2024-01-01 00:00:00 GMT+8。
# 桩类 com.stub.StubApp 从 2019 年就在用，光看桩类分不出「2020 年的老包」和「今年的新包」。
# 真 ART 层特征（libjiagu_art.so / jiagu.art）只有 V8 以后才稳定出现，但很多样本拿不到这一层，
# 所以补一条：桩类命中 com.stub 且 dex 时间早于这条线 → 按老版处理（那时 V8 引擎还没铺开）。
# 这是**经验阈值**，不是官方文档；对外一律通过「代次依据」字段暴露，不藏在代码里。
QIHOO_V8_ERA = 1704038400000

def _zip_ts(dt):
    try:
        return int(datetime.datetime(*dt).timestamp() * 1000)
    except Exception:
        return 0

def _fmt_day(ms):
    try:
        return datetime.datetime.fromtimestamp(ms / 1000.0).strftime("%Y-%m-%d")
    except Exception:
        return "?"

_dex_names = [n for n in names if re.match(r"^classes\d*\.dex$", n)]
# 「全部 dex」按 .dex 后缀计，跟外面的 r["dex数"] 同口径。
# 以前 总dex 只数根目录的 classes*.dex，于是出现「dex.数量=3 而 总dex=1」这种自相矛盾，
# 更糟的是它把「只有 1 个 dex 有时间证据」伪装成了全量证据。
_dex_all = [n for n in names if n.lower().endswith(".dex")]
_ts = []
for _n in _dex_names:
    try:
        _t = _zip_ts(z.getinfo(_n).date_time)
        if _t > 0:
            _ts.append(_t)
    except Exception:
        pass
_sane = [t for t in _ts if t >= ZIP_SANE_MIN]
# 时间样本覆盖率，如 "1/3"。分子只算根目录 classes*.dex 里时间可信的。
_dex_cover = "%d/%d" % (len(_sane), len(_dex_all)) if _dex_all else "0/0"
_dex_cover_partial = bool(_dex_all) and len(_sane) < len(_dex_all)
if _sane:
    r["dex时间"] = {"可信": True, "最早": _fmt_day(min(_sane)), "最晚": _fmt_day(max(_sane)),
                    "dex数": len(_sane),
                    "总dex": len(_dex_all),
                    "时间样本": _dex_cover,
                    "全在0701后": min(_sane) >= QIHOO_PAID_CUT,
                    "全在0701前": max(_sane) < QIHOO_PAID_CUT}
    if _dex_cover_partial:
        r["dex时间"]["注意"] = ("%d 个 dex 里只有 %d 个时间可用（其余被抹或不在根目录），"
                                "代次只按这 %d 个判" % (len(_dex_all), len(_sane), len(_sane)))
else:
    r["dex时间"] = {"可信": False, "原始数": len(_ts), "总dex": len(_dex_all),
                    "时间样本": _dex_cover,
                    "说明": "时间戳被抹（打包器写成 1980/2008 固定值），不能用时间判年代，只看特征定代次"}

# 代次要用的时间证据（判不出来时 zip_ok=False，任何时间相关的推断都必须停手）
_zip_ok = bool(_sane)
_zip_min_ms = min(_sane) if _sane else 0

# ---- AXML 解析：只读到 <application>，拿包名与入口类 ----
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
                cnt, style_cnt, flags, str_start, _ = struct.unpack_from("<IIIII", data, p + 8)
                utf8 = (flags & 0x100) != 0
                offs = struct.unpack_from("<%dI" % cnt, data, p + 28)
                base = p + str_start
                for off in offs:
                    q = base + off
                    if utf8:
                        n = data[q]
                        q += 2 if n & 0x80 else 1
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
        # 第二趟：找 <manifest> 与 <application>
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
pkg = man.get("package") or ""
appcls = man.get("app_class") or ""
r["包名"] = pkg
r["应用类"] = appcls

# ---- 壳 so 特征表：只留区分度高的 ----
SO = [
    (["libjiagu.so","libjiagu_art.so","libjiagu_64.so","libjiagu_a64.so","libjiagu_x86.so"], "360", "360加固"),
    (["libjiagu_vip.so","libjiagu_vip_a64.so","libjgcxi.so","libjgcxi_a64.so"], "360", "360加固·企业版"),
    (["libjiagu_sdk.so"], "360", "360加固·SDK变体"),
    (["libDexHelper.so","libDexHelper-x86.so","libsecexe.so","libsecmain.so","libSecShell.so"], "bangcle", "梆梆加固"),
    (["libtosprotection.so","libtup.so","libtprt.so","libshellx.so","libshell-super.so"], "tencent", "腾讯乐固/御安全"),
    (["libshella-4.6.2.2.so"], "tencent", "腾讯乐固 StubShell 4.6.2.2"),
    (["libexec.so","libexecmain.so"], "ijiami", "爱加密"),
    (["libsgmain.so","libsgsecuritybody.so","libzuma.so","libmobisec.so"], "ali", "阿里聚安全"),
    (["libnesec.so","libepic.so"], "netease", "网易易盾"),
    (["libchaosvmp.so","libddog.so","libnfix.so"], "naga", "娜迦（VMP）"),
    (["libbaiduprotect.so"], "baidu", "百度加固"),
    (["libx3g.so"], "dingxiang", "顶象"),
    (["libsecshell.so","libegis.so","libtongfudun.so"], "tongfudun", "通付盾"),
    (["libkwscmm.so","libkwsgmain.so"], "kiwi", "几维安全"),
]

# ---- 包名里的特征（乐固把 so 名字改成 app 相关的很常见）----
NAME_HINT = [
    (r"libshell.*\.so$", "tencent", "腾讯乐固（so 名带 shell）"),
    (r"libjiagu", "360", "360加固（壳资源）"),
    (r"libjgdtc", "360", "360/易固（libjgdtc）"),
]

all_so = {}
all_so_paths = []
for n in names:
    if n.lower().endswith(".so"):
        all_so_paths.append(n)
        _b = os.path.basename(n)
        # 同名 so 可能同时躺在 lib/ 和 assets/ 里。以前直接赋值，后出现的把先出现的盖掉，
        # 于是「位置」永远显示后面那个 —— 正是 AI 照着编判据的源头。lib/ 下的优先。
        if _b not in all_so or (n.startswith("lib/") and not all_so[_b].startswith("lib/")):
            all_so[_b] = n

hits = []
seen = set()
for sigs, key, label in SO:
    for s in sigs:
        if s in all_so:
            if key not in seen:
                seen.add(key)
                hits.append({"壳": label, "家族": key, "证据": s,
                             "位置": "lib" if all_so[s].startswith("lib/") else "非lib(assets/根)"})
            break
for n in list(all_so.keys()):
    for pat, key, label in NAME_HINT:
        if re.search(pat, n) and key not in seen:
            seen.add(key)
            hits.append({"壳": label, "家族": key, "证据": n, "位置": "非lib(assets/根)" if not all_so[n].startswith("lib/") else "lib"})

# ---- assets 特征 ----
assets = [n for n in names if n.startswith("assets/")]
ASSET_HINT = [
    (".jgapp", "360", "360 授权文件 .jgapp"),
    ("libjiagu", "360", "360 壳资源在 assets"),
    ("ijiami", "ijiami", "爱加密指纹"),
    ("bangcle", "bangcle", "梆梆资源"),
    ("0OO00l111l1l", "tencent", "乐固特征文件 0OO00l111l1l"),
    ("o0oooOO0", "tencent", "乐固特征文件 o0oooOO0"),
    ("tosversion", "tencent", "乐固 tosversion 目录"),
]
for a in assets:
    low = a.lower()
    for key_w, key, label in ASSET_HINT:
        if key_w in low and key not in seen:
            seen.add(key)
            hits.append({"壳": label, "家族": key, "证据": a.replace("assets/", ""), "位置": "assets"})

# ---- 桩类：入口类最可靠，其次扫全 manifest 字符串 ----
STUBS = [
    (r"com\.frezrik\.jiagu", "360", "老版"),
    (r"com\.stub\.StubApp", "360", "新版"),
    (r"com\.qihoo\.", "360", "新版"),
    (r"com\.byazt\.", "360", "新版"),
    (r"com\.tencent\.StubShell", "tencent", ""),
    (r"com\.SecShell\.", "bangcle", ""),
    (r"com\.secneo\.", "bangcle", ""),
    (r"com\.saurik\.", "bangcle", ""),
    (r"com\.ijiami\.", "ijiami", ""),
    (r"com\.ali\.mobisec\.", "ali", ""),
    (r"com\.netease\.nirvana", "netease", ""),
]
stub = ""
stub_family = ""
stub_gen = ""
for pat, fam, gen in STUBS:
    if appcls and re.search(pat, appcls):
        stub, stub_family, stub_gen = appcls, fam, gen
        break
if not stub:
    try:
        txt = z.read("AndroidManifest.xml").decode("utf-16-le", errors="ignore")
        for pat, fam, gen in STUBS:
            m = re.search(pat + r"[\w\.]*", txt)
            if m:
                stub, stub_family, stub_gen = m.group(0), fam, gen
                break
    except Exception:
        pass
r["桩类"] = stub
# 以前这里还会吐一个「代次线索」= 桩类表里的粗标签。它跟真正的 360代次 会打架
# （com.stub.StubApp 在表里写死「新版」，可 2020 年的包其实是老版），
# 同一份返回值里两个代次说法 —— AI 必然选错那个。代次只认下面的 360代次 + 代次依据。

# 桩类单独成一条证据（so 可能被改名，桩类更可靠）
if stub and stub_family and stub_family not in seen:
    seen.add(stub_family)
    hits.append({"壳": "由桩类判定", "家族": stub_family, "证据": stub, "位置": "Manifest"})

# ---- dex 头：判容器还是真货 ----
dex = sorted([n for n in names if n.lower().endswith(".dex")])
total_cls = 0
real = 0
for n in dex:
    try:
        f = z.open(n); head = f.read(112); f.close()
        if len(head) < 112 or head[0:4] != b"dex\n":
            continue
        cd = struct.unpack_from("<I", head, 0x60)[0]
        total_cls += cd
        if cd >= 800:
            real += 1
    except Exception:
        pass

r["dex数"] = len(dex)
r["类总数"] = total_cls
if total_cls == 0:
    r["dex状态"] = "读不到/空"
elif total_cls < 800:
    r["dex状态"] = "疑似壳容器（类太少，业务代码不在这）"
elif total_cls < 5000:
    r["dex状态"] = "类数偏少"
else:
    r["dex状态"] = "正常"

r["命中"] = hits[:8]
r["命中数"] = len(hits)
# 统计用原始路径列表，不用 all_so：同名 so 在字典里只留一条，会把数量算少。
# 但多 ABI（arm64-v8a + armeabi-v7a）的同名 so 又是重复的，所以要同时给出去重后的数。
r["so总数"] = len(all_so_paths)
_lib_paths = [v for v in all_so_paths if v.startswith("lib/")]
_nonlib_paths = [v for v in all_so_paths if not v.startswith("lib/")]
_lib_uniq = len(set(os.path.basename(v) for v in _lib_paths))
_lib_note = ("lib 下 %d 个 so（去重 %d 个）" % (len(_lib_paths), _lib_uniq)) if _lib_uniq != len(_lib_paths) \
            else ("lib 下 %d 个 so" % len(_lib_paths))
r["evidence_lib"] = "%s，非 lib 目录 %d 个" % (_lib_note, len(_nonlib_paths))

if not hits:
    r["结论"] = "没命中任何已知壳特征，也没有加固桩类" + ("，" + r["dex状态"] if r.get("dex状态") else "")
    r["判定"] = "未加固" if r["类总数"] >= 800 else "存疑"
else:
    fam = hits[0]["家族"]
    r["主壳家族"] = fam
    r["结论"] = "命中 " + "、".join(sorted(set(h["壳"] for h in hits)))

# ---- 360 代次：老版 / 新版 / 企业版，再叠加 0701 付费时代 ----
# 判据优先级（高 → 低），返回 (代次, 依据)：
#   ① 企业/VIP 硬特征
#   ② ART 层壳本体 libjiagu_art.so / jiagu.art / jiagu_res.zip —— V8 才有，老版没有
#   ③ 桩类 com.frezrik / com.qihoo.util —— V5 老壳
#   ④ 桩类 com.stub / com.byazt —— **跨代**，2019 年就在用。必须叠 dex 时间：
#      早于 QIHOO_V8_ERA（2024-01-01）→ 老版；否则新版
#   ⑤ 只有 libjiagu*.so 没桩类 → 老版
# 第 ④ 条是关键：不叠时间的话，2020 年的老包会被判成新版，AI 也会照着这个错判往下走。
def _qihoo_gen(stub_cls, so_keys, all_names, zip_ok, zip_min_ms):
    if any(k in so_keys for k in ("libjiagu_vip.so", "libjiagu_vip_a64.so",
                                  "libjgcxi.so", "libjgcxi_a64.so",
                                  "libvmp_360.so", "libjiagu_enterprise.so")) \
       or any(n.endswith(("vmp_config.bin", "jiagu_enterprise.dat")) for n in all_names):
        return "企业版", "企业/VIP 版专属 so 或资源"
    if "libjiagu_art.so" in so_keys \
       or any(n.endswith(("jiagu.art", "jiagu_res.zip")) for n in all_names):
        return "新版", "ART 层壳本体（libjiagu_art.so / jiagu.art / jiagu_res.zip），老版没有"
    if stub_cls.startswith("com.frezrik") or stub_cls.startswith("com.qihoo.util"):
        return "老版", "V5 老壳桩类 " + stub_cls
    if stub_cls.startswith("com.stub") or stub_cls.startswith("com.byazt"):
        if zip_ok and zip_min_ms < QIHOO_V8_ERA:
            return "老版", ("桩类 %s 跨代不可靠，叠 dex 时间 %s（早于 2024-01-01 的 V8 分界）→ 老版"
                            % (stub_cls, _fmt_day(zip_min_ms)))
        if zip_ok:
            return "新版", ("桩类 %s + dex 时间 %s（晚于 2024-01-01）→ 新版"
                            % (stub_cls, _fmt_day(zip_min_ms)))
        return "新版", ("桩类 %s（dex 时间被抹，无法用时间校正，按现行引擎判新版）" % stub_cls)
    if any(k.startswith("libjiagu") for k in so_keys):
        return "老版", "只有 libjiagu*.so，无可识别的桩类"
    return "未知", "没有企业特征 / ART 特征 / 可识别桩类"

_is360 = (r.get("主壳家族") == "360") or any(h.get("家族") == "360" for h in hits)
if _is360:
    _g, _why = _qihoo_gen(stub or "", set(all_so.keys()), names, _zip_ok, _zip_min_ms)
    _paid = bool(r.get("dex时间", {}).get("可信")) and r["dex时间"].get("全在0701后")
    # 代次依据必须自带两件事：① 时间样本覆盖了多少 dex ② 有没有叠 0701。
    # 少一样，AI 就会把「基于 1/3 个 dex 的推断」当成全量结论，或者把「新版0701」讲成「新版」。
    if _dex_cover_partial and _zip_ok:
        _why += "（时间样本 %s，其余 dex 时间不可用）" % _dex_cover
    if _g != "未知" and _paid:
        _why += "；再叠 0701 付费时代"
    r["360代次"] = _g
    r["代次依据"] = _why
    r["付费时代"] = _paid
    # 显示名统一成「360加固（新版0701）」这种；时间不可信时不加 0701 —— 猜错比不写更糟
    if _g == "未知":
        r["360显示名"] = "360加固（代次未定）"
    else:
        r["360显示名"] = "360加固（%s%s）" % (_g, "0701" if _paid else "")
    r["结论"] = "命中 " + r["360显示名"]
    if _paid:
        r["付费时代说明"] = ("2026-07-01 起 360 停止免费加固，付费引擎产物 —— "
                             "等价于 MT 管理器里的「360付费版」")

print("RKRESULT:" + json.dumps(r, ensure_ascii=False))
`;

    function baseName(p) {
        var s = String(p || '').split('/').pop();
        return s.replace(/\.apk$/i, '') || 'target';
    }
    function decDirFor(apk) { return OUT + '/' + baseName(apk) + '_dec'; }

    // 家族(+代次) -> 该调谁。参数必须给**具体值**，不能留占位符，
    // 否则 AI 会把「<先解包拿到的目录>」这种字符串原样传进去。
    function routeFor(fam, gen, apk) {
        var dec = decDirFor(apk);

        if (fam === '360') {
            if (gen && gen.indexOf('老版') === 0) {
                return {
                    包: '56al_tuoke',
                    备注: '老版 360 云端优先：本地对老版几乎没有可靠修法，别耗时间硬修',
                    下一步: [{ 包: '56al_tuoke', 工具: 'tuoke_all', 参数: { apk_path: apk }, 说明: '云端脱壳（需先启用并登录该包）' }]
                };
            }
            return {
                包: 'pack_360',
                备注: '新版 360 是函数抽取壳：只启动 App 再 dump 拿到的是空壳，必须走主动调用（FunDex2/FART）',
                下一步: [
                    { 包: 'pack_360', 工具: 'p360_strategy', 参数: { generation: gen || '' }, 说明: '先拿策略、前置条件、红线' },
                    { 包: 'pack_360', 工具: 'p360_plan', 参数: { apk_path: apk, generation: gen || '' }, 说明: '拿有序计划，再按计划逐步调 local_unpack' }
                ]
            };
        }

        if (fam === 'tencent') {
            return {
                包: '(无)',
                备注: '乐固没有专属技能书。各版本 StubShell 差异大，别硬套流程；抽取率决定能不能修',
                下一步: [
                    { 包: 'local_unpack', 工具: 'lu_decode', 参数: { apk_path: apk, output_dir: dec }, 说明: '先解包（路径已算好，直接用）' },
                    { 包: 'local_unpack', 工具: 'lu_scan', 参数: { decoded_dir: dec }, 说明: '扫抽取率：>70% 别修，先解决脱壳' }
                ]
            };
        }

        // 其余壳：只有特征识别，没有实测流程，别硬编
        return {
            包: '(无)',
            备注: '这个壳没有实测过的脱壳流程，不要硬编步骤。如实告诉用户，给云端兜底',
            下一步: [
                { 包: 'local_unpack', 工具: 'lu_decode', 参数: { apk_path: apk, output_dir: dec }, 说明: '先解包看结构' },
                { 包: 'local_unpack', 工具: 'lu_scan', 参数: { decoded_dir: dec }, 说明: '看抽取率，判断有没有可修的空方法体' },
                { 包: '56al_tuoke', 工具: 'tuoke_all', 参数: { apk_path: apk }, 说明: '云端兜底（需先启用并登录）' }
            ]
        };
    }

    function rk_auto(params) {
        var apk = params.apk_path;
        if (!apk) throw new Error('缺少 apk_path');
        // mode="identify"：只识别。用户说「仅识别 / 别动包 / 只看一下」时必须传，
        // 这样返回值里不会出现「要现在就开始吗」「建议路线」「下一步」这些推人动手的字段。
        var identifyOnly = String(params.mode || '').toLowerCase() === 'identify';

        return exec('mkdir -p ' + q(OUT), 15000)
            .then(function () {
                var script = OUT + '/.rk_scan.py';
                var marker = 'RKPYEOF';
                // 标记加引号，防止 shell 展开脚本里的 $ / 反引号
                return exec('cat > ' + q(script) + ' <<\'' + marker + '\'\n' + PY_SCAN + '\n' + marker + '\n', 30000)
                    .then(function () { return exec('python3 ' + q(script) + ' ' + q(apk), 120000); });
            })
            .then(function (r) {
                var out = (r.output || '').trim();
                var i = out.lastIndexOf('RKRESULT:');
                if (i < 0) throw new Error('扫描没出结果（python3 装了吗？）：' + out.substring(0, 300));
                var scan = JSON.parse(out.substring(i + 9).trim());
                if (scan.error) throw new Error(scan.error);

                var hits = scan.命中 || [];
                var gen = '';
                if (hits.length) {
                    var fam = hits[0].家族;
                    if (fam === '360') {
                        // 代次已经由 python 侧按「ART 层特征 > 桩类」的优先级算好了，这里直接用。
                        // 千万别在 JS 里再算一套 —— 两套判据打架时最难查（test18 那次就吃过这亏）。
                        var g = scan['360代次'] || '未知';
                        var paid = !!scan['付费时代'];
                        gen = (g === '未知') ? '未知代次' : (g + (paid ? '0701' : ''));
                    }
                }

                var route = hits.length ? routeFor(hits[0].家族, gen, apk) : null;

                var result = {
                    APK: apk,
                    包名: scan.包名 || '(读不出，解包后看 AndroidManifest 的 package=)',
                    判定: scan.判定 || (hits.length ? '已加固' : '未加固'),
                    结论: scan.结论,
                    // 只给「壳 + 证据」。以前还塞了一个「位置」（lib / 非lib(assets/根)），
                    // 它不参与任何打分，却长得像判据 —— AI 直接拿它编出
                    // 「libjiagu.so 在 lib 下所以是新版」这种代码里根本不存在的规则。
                    // 判据只从 代次依据 这一个字段来。
                    命中: hits.map(function (h) { return h.壳 + '（' + h.证据 + '）'; }),
                    代次: gen,
                    应用类: scan.应用类 || '',
                    桩类: scan.桩类 || '',
                    dex: { 数量: scan.dex数, 类总数: scan.类总数, 状态: scan.dex状态 },
                    加固时间: scan['dex时间'],
                    so: scan.evidence_lib
                };
                if (scan['代次依据']) result.代次依据 = scan['代次依据'];
                if (scan['付费时代说明']) result.付费时代说明 = scan['付费时代说明'];

                // ── 只识别模式到此为止 ──
                if (identifyOnly) {
                    result.只识别 = true;
                    result.提示 = '用户只要识别结果：只讲结论 / 代次 / 代次依据 / 加固时间，'
                        + '不要讲脱壳方案，不要调 local_unpack:lu_* 或 pack_360:p360_*，也不要改包。';
                    return result;
                }

                // ★ 必须给「本地能做什么」。
                // 真机日志里 AI 只报「这是什么壳」就收工，用户拿不到任何本地方案 —— 这是设计漏洞。
                var decDir = decDirFor(apk);
                var fam0 = hits.length ? hits[0].家族 : '';
                // 包名读不出时留空串，不要塞 <包名> 这种占位符 —— AI 会原样当参数传下去（踩过）
                var pn = scan.包名 || '';
                var adv;
                if (!hits.length) {
                    adv = scan.判定 === '未加固'
                        ? {
                            能本地做吗: '不用脱壳 —— 原包没加固，本地没有要脱的东西',
                            想改包才这么做: [
                                'lu_decode({ apk_path, output_dir: ' + decDir + ' }) 解包',
                                '改 smali / 资源',
                                'lu_rebuild 重打包 + 对齐 + v1/v2/v3 签名'
                            ],
                            该问用户: '你是想脱壳，还是想改这个包（去广告/改入口/过校验）？'
                        }
                        : {
                            能本地做吗: '可能不行 —— 没命中已知壳，但类数异常少',
                            本地先试: [
                                'lu_decode 解包看结构',
                                'lu_scan 扫抽取率（>70% 说明是抽取壳，本地修不了）',
                                'lu_dump 试主动调用脱壳',
                                '仍不行 → 56al_tuoke:tuoke_all 云端兜底'
                            ]
                        };
                } else if (fam0 === '360' && gen && gen.indexOf('老版') === 0) {
                    adv = {
                        能本地做吗: '不建议本地 —— 老版 360 本地几乎没有可靠修法',
                        建议路线: [
                            '56al_tuoke:tuoke_all 云端脱壳（需登录）',
                            'local_unpack:lu_dump_check 体检产物，类总数≥3000 才是真货'
                        ]
                    };
                } else if (fam0 === '360') {
                    adv = {
                        能本地做吗: '取决于本机装了什么 —— 先跑 lu_env_check，它会算好唯一可行的那条',
                        建议路线: [
                            'local_unpack:lu_env_check 先跑（别跳）',
                            '→ 它返回的「脱壳能力判断.首选」就是下一步，照着调那一个工具',
                            'local_unpack:lu_dump_check 体检抽取率（<70% 才有修的价值）',
                            'pack_360:p360_plan 拿完整修复步骤，按步执行'
                        ],
                        四种情况: {
                            '装了 FunDex2/FART/frida_fart': pn
                                ? 'lu_dump({ package_name: ' + pn + ', rounds: 2, wait_seconds: 60 }) 主动调用'
                                : '包名没读出来 —— 先 lu_decode 解包，读解包目录 AndroidManifest.xml 第一行的 package="..."，再 lu_dump 主动调用',
                            '只装了 Layout Inspect': 'lu_dump_li 引导脱壳（同样能处理抽取壳）',
                            '一个本地模块都没有': '56al_tuoke:tuoke_all 云端脱壳 —— 零安装，需登录该包；'
                                + '拿到 dex 后从 p360_plan 的「产物体检」继续，不要停下来让用户装模块',
                            '内核支持 eBPF': '放 eBPFDexDumper-rs 到 /data/local/tmp 后走 lu_dump'
                        },
                        注意: '只启动 App 再 dump 拿到的是空壳，必须走主动调用或云端，二选一，别原地报「缺模块」就收工'
                    };
                } else {
                    adv = {
                        能本地做吗: '只能做到解包 + 评估，这个壳没有实测过的本地脱壳流程',
                        建议路线: [
                            'local_unpack:lu_decode 解包',
                            'local_unpack:lu_scan 扫抽取率',
                            '抽取率低 → 可试 lu_fill_oncreate 邪修（约 30%）',
                            '否则 56al_tuoke:tuoke_all 云端兜底'
                        ]
                    };
                }
                adv.要现在就开始吗 = '要 —— 除非用户明确说「只看方案」，否则直接执行上面第一条，不要只做诊断就收工';
                result.本地方案 = adv;

                if (!hits.length) {
                    result.建议 = scan.判定 === '未加固'
                        ? '原包直接能装，不需要脱壳。只有要改逻辑（去广告/过校验/改入口）时才解包改再重打包'
                        : '没命中特征但类数异常偏少 —— 可能是冷门壳或 dex 整体加密。先试云端脱壳兜底';
                    result.下一步 = scan.判定 === '未加固'
                        ? [{ 包: 'local_unpack', 工具: 'lu_decode', 参数: { apk_path: apk, output_dir: decDirFor(apk) }, 说明: '要改包才需要；不改就别解包' }]
                        : [{ 包: '56al_tuoke', 工具: 'tuoke_all', 参数: { apk_path: apk }, 说明: '云端兜底（需先启用该包）' }];
                    return result;
                }

                result.走这个包 = route.包;
                result.下一步 = route.下一步;
                result.备注 = route.备注;
                result.提醒 = '调用时工具名写成「包名:工具名」就会自动激活该包，不要额外调 use_package';
                return result;
            });
    }

    function rk_playbook() {
        return {
            分层: {
                'L0 local_unpack': '机械操作：解包/改 smali/修 dex 头/重打包/签名。壳无关，任何壳都用它',
                'L1 revokit_core': '本包。识别 + 路由，不碰文件',
                'L2 pack_360': '360 的知识与计划（detect/strategy/plan/knowledge）'
            },
            标准动作: [
                '① 拿到 APK → 调 rk_auto({apk_path})，一次拿到壳型/代次/该调谁/本地方案',
                '② 照 rk_auto 返回的「本地方案」把「本地能不能脱、怎么脱」讲给用户，不要只报壳型',
                '③ 除非用户明确说只看方案，直接执行「本地方案」第一条，别停在诊断',
                '④ 按「下一步」逐条调（工具名写 包名:工具名，会自动激活包）',
                '⑤ 有技能书的壳调 p360_plan 拿完整步骤，按步调 local_unpack:lu_* 执行',
                '⑥ 没技能书的壳如实说没有流程，给云端兜底 + lu_scan 评估，不要硬编',
                '⑦ 修完必须验证启动；崩了用 logfox_toolkit 抓日志，循环修'
            ],
            汇报规则: [
                '先一句话结论（什么壳 / 本地能不能脱），再最多 4 行要点，最后一句下一步',
                '不要复述工具返回的 JSON 字段名，不要把 命中/证据 原样贴出来',
                '本地方案已经算好放在 rk_auto 返回值里，照着说，不要自己重新发挥',
                '不要只做诊断。用户丢的是安装包，默认目标是脱壳修复，不是「帮你看看是什么壳」',
                '禁止把整段工具输出抄进回复，只摘关键行',
                '体检报告里的「受限」≠「缺失」。root 可用却读不到 /data/adb 是授权/命名空间问题，'
                    + '不要据此让用户去装 Magisk 或重新授权 root',
                '【根因级】终端有两套环境：默认的 proot Ubuntu（提示符 root@localhost）'
                    + '有 python3 但看不到 /system、没有 pm/am/monkey/su/data-adb；'
                    + 'Android 侧走 execute_shell 才有这些但没有 python3。'
                    + '体检里的「Android 执行环境」一项就是在确认通路对不对 —— 若它报「通路不可用」，'
                    + '先怀疑授权没给，不要怀疑设备没装',
                '体检报告带 通道不可用:true 时，其余能力一律是「未验证」，不是「缺失」，不要往下推',
                '壳名里的代次：`360加固（新版0701）` 指 2026-07-01 之后付费时代的新版 360。'
                    + '带 0701 = dex 修改时间全部越过分界线，预期残留更多、反调试更狠，'
                    + '清残留按企业版规格做（libjiagu_art.so / jiagu.art / jiagu_res.zip 都要删）；'
                    + '不带 0701 不代表是老包 —— 可能是打包器把时间戳抹了（看「加固时间」字段的 可信/时间窗）',
                '解包/重打包/签名一律先想 apk_reverse（内置 apktool + apksig 的 jar），'
                    + '不要因为「没有 apktool 命令 / 没有 Java / 没有 MT 管理器」就判定本地做不了'
            ],
            硬约束: [
                '不要在写文件之前跳步 —— 所有写入都是 apply=true / dry_run=false 才生效',
                '不要重复识别：rk_auto 已经扫过 so/assets/桩类/dex头，别再 list_files / read_file / grep 翻包，也别再调 加固特征库 或 lu_probe',
                '禁止调 apk_reverse:apk_reverse_inspect —— 单次返回 3.5 万字符，会在后面每一轮被重发，是拖慢整轮的头号原因',
                '工具名带包名前缀会自动激活包，不需要 use_package（实测白调 6 次）',
                '一个 APK 只跑一次 rk_auto。它的结论就是最终结论，不要交叉验证',
                '例外：rk_auto 说「未加固」但用户坚持有壳，或要看签名方案时，调原生 apk_packer_identify 复核（Kotlin 侧 49 家特征库，比本包的 python 扫描更全，毫秒级）',
                '禁止只报壳型就收工 —— 必须给「本地能不能脱、怎么脱」的方案',
                '禁止以「本机没装脱壳模块」作为最终结论。lu_env_check 已经算好「脱壳能力判断.首选」，'
                    + '照它执行；本地一条都不通就走 56al_tuoke:tuoke_all 云端，不要停在让用户装东西',
                'Android 侧动作（pm / am / monkey / su / 读 /data/adb）走 execute_shell，'
                    + '本包内已由 execAndroid 自动分流；python 扫描走 Ubuntu 侧。'
                    + '自己写命令时不要混，混了就是「root 明明有却被判没装」',
                // ↓ 2026-10-24 实测：一轮 33 分钟 / 263 万 token 的会话里，84 次调用有 64 次是
                //   「用 super_admin:shell / super_admin:terminal 手工重写 local_unpack 的既有流程」。
                //   这是目前最大的浪费源，必须写成硬约束顶住。
                '【硬约束】禁止用 super_admin:shell / super_admin:terminal / execute_shell / '
                    + 'execute_hidden_terminal_command 手工替代 local_unpack 的既有工具'
                    + '（lu_env_check / lu_probe / lu_decode / lu_dump / lu_dump_li / lu_fix / lu_rebuild）。'
                    + '先调工具，工具报错再考虑手工；确需工具没覆盖的临时命令，最多 3 条并说明理由',
                '【硬约束】同一个工具、同一类错误，连续失败 2 次就停手：换通路或把错误原样报给用户。'
                    + '实测有同一个 241 字符的错误被原样重试 5 次，每次白等 30 秒超时',
                '不要为了「验证一下」去调 super_admin。工具返回什么就当什么；要交叉验证就用 lu_env_check'
            ],
            通用红线: [
                '真实 Application 是继承链叶子节点，不是基类 —— 选基类跳过 attachBaseContext，Context 为 null，启动必崩',
                'Manifest 的 rawValue 和 typedValue.data 必须同时改',
                'extractNativeLibs=true 时 .so 保持 DEFLATED，无条件 STORED 会白胖 20MB+',
                'zipalign 在前，apksigner 在后；之后改动 ZIP 字节就要重签',
                'minSdk≥24 必须显式 v1+v2+v3',
                '抽取率 >70% 不要修 —— 修的是空壳，先解决脱壳'
            ],
            该放弃: [
                'VMP 壳（娜迦 libchaosvmp / 阿里聚安全）：字节码虚拟化，静态 dex 里没有原指令，任何工具都还原不了',
                '抽取率极高且无主动调用模块：只能改资源（CorePatch 过签后改 arsc/XML）'
            ]
        };
    }

    return { rk_auto: wrap(rk_auto), rk_playbook: wrap(rk_playbook) };
})();

exports.rk_auto = rk.rk_auto;
exports.rk_playbook = rk.rk_playbook;
