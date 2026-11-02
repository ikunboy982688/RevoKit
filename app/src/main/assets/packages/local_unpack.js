/* METADATA

{
  "name": "local_unpack",
  "display_name": {
    "zh": "本地脱壳修复工具包（360/数字加固专项）",
    "en": "Local Unpack & Repair Toolkit"
  },
  "description": "本地脱壳修复工具包（壳无关的机械层）。解包、扫抽取率、回填 onCreate、清桩类、修 Manifest 入口、修 dex 头与校验和、重打包对齐签名。不判壳、不出流程——判壳用 revokit_core，壳知识用 pack_xxx。",
  "category": "ReverseEngineering",
  "enabledByDefault": true,
  "tools": [
    {
      "name": "lu_env_check",
      "description": "环境体检（第一步跑）：root/LSPosed、已装脱壳模块、python3/apktool、磁盘空间。",
      "parameters": []
    },
    {
      "name": "lu_probe",
      "description": "只读 APK 判壳型与代次（含 assets 下的壳 so）。★代次是流程分水岭。一般用 rk_auto 就够。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "APK 文件路径",
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "lu_dump",
      "description": "驱动本地脱壳模块拉起 App 收 dex。360 函数抽取壳必须用 FunDex2/FART/frida_fart，否则拿到空壳。",
      "parameters": [
        {
          "name": "package_name",
          "description": "目标应用包名",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "dex 收集目录，默认 /sdcard/Download/Operit/unpack_out/<包名>/dex",
          "type": "string",
          "required": false
        },
        {
          "name": "wait_seconds",
          "description": "拉起后等待秒数，默认 45",
          "type": "string",
          "required": false
        },
        {
          "name": "rounds",
          "description": "脱壳轮数，默认 2。抽取壳边跑边补，第一轮常常没补完；1 = 最快",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_dump_check",
      "description": "dex 体检：头校验、类数、抽取率、与 baseline 逐 dex 比对。dump 完先跑它。",
      "parameters": [
        {
          "name": "dex_path",
          "description": "要体检的 dex 文件或目录",
          "type": "string",
          "required": true
        },
        {
          "name": "apk_path",
          "description": "原 APK，给了就按原包类数算抽取率",
          "type": "string",
          "required": false
        },
        {
          "name": "package_name",
          "description": "目标包名",
          "type": "string",
          "required": false
        },
        {
          "name": "baseline_dir",
          "description": "上一轮的 dex 目录，逐 dex 比对 size+SHA1",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_decode",
      "description": "apktool 解包到目录。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "APK 文件路径",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "解包输出目录",
          "type": "string",
          "required": true
        },
        {
          "name": "force",
          "description": "是否覆盖已有目录 true/false",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_scan",
      "description": "扫 smali 空方法体算抽取率。抽取率 >70% 别修，先解决脱壳。",
      "parameters": [
        {
          "name": "decoded_dir",
          "description": "apktool 解包后的目录",
          "type": "string",
          "required": true
        },
        {
          "name": "max_details",
          "description": "最多返回几条明细",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_fill_oncreate",
      "description": "把 onCreate 里的调用回填进空方法体（邪修大法）。dry_run 默认 true。",
      "parameters": [
        {
          "name": "decoded_dir",
          "description": "apktool 解包目录",
          "type": "string",
          "required": true
        },
        {
          "name": "dry_run",
          "description": "true=只报告不修改（默认），false=实际写入",
          "type": "string",
          "required": false
        },
        {
          "name": "only_class",
          "description": "只处理某个类",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_splash_fix",
      "description": "★启动页接回补丁：专治「删掉/回填 onCreate 后界面卡在启动页不动」。适用于父类的 onCreate 只做半套（只设横竖屏、状态栏颜色，一次业务方法调用都没有，就是不跳转）的情况 —— 360 只抽走生命周期那个方法，它内部调的私有入口方法（checkAndShowXxx / continueXxxJump / toMainPage 这类）全都还在。本工具自动判定哪些类属于这种情况，列出候选入口，并在子类 onCreate 里补一句调用把流程接回去。dry_run 默认 true。",
      "parameters": [
        {
          "name": "decoded_dir",
          "description": "apktool 解包目录",
          "type": "string",
          "required": true
        },
        {
          "name": "dry_run",
          "description": "true=只报告不修改（默认），false=实际写入",
          "type": "string",
          "required": false
        },
        {
          "name": "entry",
          "description": "手动指定入口方法名（自动选的不对时用，例如 checkAndShowMubuTerm）",
          "type": "string",
          "required": false
        },
        {
          "name": "only_class",
          "description": "只处理某个类（类名片段或路径片段）",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_stub_clean",
      "description": "删掉 StubApp/DtcLoader 等壳桩类在入口里的调用。",
      "parameters": [
        {
          "name": "decoded_dir",
          "description": "apktool 解包目录",
          "type": "string",
          "required": true
        },
        {
          "name": "dry_run",
          "description": "true=只报告不修改（默认），false=实际写入",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_manifest_fix",
      "description": "把 Manifest 入口回填成真实 Application。★真实 Application 是继承链叶子节点。",
      "parameters": [
        {
          "name": "decoded_dir",
          "description": "apktool 解包目录",
          "type": "string",
          "required": true
        },
        {
          "name": "real_app",
          "description": "真实 Application 全类名。★必须是继承链叶子节点",
          "type": "string",
          "required": false
        },
        {
          "name": "drop_proxy",
          "description": "同时删掉壳的代理组件",
          "type": "string",
          "required": false
        },
        {
          "name": "keep_stub_as_app",
          "description": "保留桩类作为 Application（少见）",
          "type": "string",
          "required": false
        },
        {
          "name": "dry_run",
          "description": "true=只报告不修改（默认），false=实际写入",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_repack_assemble",
      "description": "把 dump 出的 dex 塞回解包目录并重排 classesN 编号。",
      "parameters": [
        {
          "name": "output_dir",
          "description": "组装结果的输出目录（之后整体覆盖进 apktool 解包…",
          "type": "string",
          "required": true
        },
        {
          "name": "dump_dir",
          "description": "dump 出的 dex 所在目录",
          "type": "string",
          "required": true
        },
        {
          "name": "apk_path",
          "description": "原 APK，用于确认基准",
          "type": "string",
          "required": false
        },
        {
          "name": "dry_run",
          "description": "true=只出编号方案不写文件（默认），false=实际…",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_stub_scaffold",
      "description": "生成兼容桩 smali（StubApp 兼容层），给还需要壳符号的场景用。",
      "parameters": [
        {
          "name": "stub_smali",
          "description": "现有桩类 smali 路径",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "桩 smali 的输出目录（会保持 com/stub/…",
          "type": "string",
          "required": true
        },
        {
          "name": "real_app",
          "description": "真实 Application 的 smali 类名（如…",
          "type": "string",
          "required": false
        },
        {
          "name": "dry_run",
          "description": "true=只报告不写入（默认），false=实际生成 s…",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_dex_fix",
      "description": "修 dex 头：file_size→SHA1→checksum 顺序不可换，顺带清 tries。",
      "parameters": [
        {
          "name": "dex_path",
          "description": "dex 文件路径，或包含 dex 的目录（目录时递归处理…",
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "lu_v9_patch",
      "description": "dex 层删掉 360 抽走的方法定义（native 且 code_off=0），让子类继承父类真实实现；用非最短 uleb128 填回差额，保证 class_data_item 等长、偏移全不变、不碰 map_list。不依赖 smali，专治手机端「把 dump 的 dex 直接塞进解包目录、业务类根本没反编译成 smali」导致 lu_fill_oncreate/lu_splash_fix 覆盖不到的情况。症状是主界面能进，一点某些页面就 UnsatisfiedLinkError: No implementation found for ...onCreate。",
      "parameters": [
        {
          "name": "dex_dir",
          "description": "dex 所在目录（递归取 *.dex），也可以填单个 .dex 文件路径。一般填 apktool 解包目录（里面 classes2.dex/classes3.dex 就是塞进去的业务 dex）。",
          "type": "string",
          "required": true
        },
        {
          "name": "prefix",
          "description": "主程序包名前缀，填 dex 描述符形式，如 Lxyz/daofeng/ 或 Lcom/juyingfun/mqjz/；多个用逗号分隔。留空则按「排除框架和三方库」兜底过滤。强烈建议显式填，避免误删三方库的真 JNI。",
          "type": "string",
          "required": false
        },
        {
          "name": "sigs",
          "description": "要删的方法签名（逗号分隔，shorty 形式）。默认只删 onCreate(Landroid/os/Bundle;)V 和 onRequestPermissionsResult(I[Ljava/lang/String;[I)V。要扩就传，例如 onResume()V,onStart()V,onDestroy()V。",
          "type": "string",
          "required": false
        },
        {
          "name": "dry_run",
          "description": "默认 true（只预演、不落盘，列出将删哪些）。确认无误后设 false 真正改写。",
          "type": "string",
          "required": false
        },
        {
          "name": "in_place",
          "description": "默认 true：原地覆盖 dex。设 false 且不填 output_dir 时写成同名 .v9 文件。",
          "type": "string",
          "required": false
        },
        {
          "name": "output_dir",
          "description": "指定输出目录时，改完的 dex 写到该目录（原 dex 不动）。填了它 in_place 无效。",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_code_patch",
      "description": "dex 层「启动页接回」：把被 360 抽走入口的 Activity 用等长方法体原地改写接回真实业务入口。症状是 lu_v9_patch 之后不崩了，但永远卡在启动页/闪屏进不去 —— 根因是父类实现只做了一半（幕布 BaseSplashActivity.onCreate 只有 37 条指令、设横竖屏和状态栏、一次虚分派都没有），真正的跳转全在被抽走的子类 onCreate 里，而同类里那个业务入口方法还活着、只是没人调。本工具找一个必定会被框架调用的存活方法（onResume 优先）做「搭载」，原地改写成 invoke-super 父类同名方法 + 调用业务入口 + return-void。等长（新指令不超过原 unit 数就整段覆盖、余下填 nop）→ 偏移不变 → 不碰 map_list。★必须在 lu_v9_patch 之前跑：v9 删掉 onCreate 定义后，本工具就失去「哪个类被抽过」的证据了。",
      "parameters": [
        {
          "name": "dex_dir",
          "description": "dex 所在目录（取 *.dex），也可以填单个 .dex 文件路径。先不填 cls 跑一次看分析结果，再带 cls 正式改。",
          "type": "string",
          "required": true
        },
        {
          "name": "prefix",
          "description": "主程序包名前缀（dex 描述符形式，如 Lcom/mubu/），多个逗号分隔。留空则扫全部类（慢，且会混进三方库）。",
          "type": "string",
          "required": false
        },
        {
          "name": "cls",
          "description": "要改的类描述符，如 Lcom/mubu/app/splash/MubuSplashActivity;。留空=只分析列出所有被抽过的类和入口候选打分，不写盘。",
          "type": "string",
          "required": false
        },
        {
          "name": "method",
          "description": "搭载方法（含签名），默认自动挑 onResume()V / onStart()V / onRestart()V / onPostResume()V 里第一个有真实方法体的。",
          "type": "string",
          "required": false
        },
        {
          "name": "entry",
          "description": "业务入口方法名（不含签名）。留空则用自动打分的第一名（判据：关键词 + 「同类里没有任何地方调用它」+30 + private+5）。",
          "type": "string",
          "required": false
        },
        {
          "name": "dry_run",
          "description": "默认 true（只分析打分、不写盘）。确认首选入口就是真正的业务入口后设 false 才真正改写。",
          "type": "string",
          "required": false
        },
        {
          "name": "output_dir",
          "description": "输出目录。填了它原 dex 不动、改完的写到该目录；不填则原地覆盖。",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_codeitem_fill",
      "description": "用 native 工具回填 CodeItem（需要 bin_dir 里已有工具）。",
      "parameters": [
        {
          "name": "dex_path",
          "description": "被抽空的 dex 文件路径",
          "type": "string",
          "required": true
        },
        {
          "name": "bin_dir",
          "description": "native 工具所在目录",
          "type": "string",
          "required": true
        },
        {
          "name": "output_path",
          "description": "回填后输出的 dex 路径",
          "type": "string",
          "required": true
        },
        {
          "name": "dry_run",
          "description": "true=只统计不写入（默认），false=实际写入",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_fart_import",
      "description": "把 FART 产物导入解包目录。",
      "parameters": [
        {
          "name": "artifact_dir",
          "description": "FART 产物目录",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "回填后 dex 的输出目录",
          "type": "string",
          "required": true
        },
        {
          "name": "dry_run",
          "description": "true=只统计不写入（默认），false=实际写入",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_rebuild",
      "description": "重打包 + zipalign + v1/v2/v3 签名。zipalign 必须在前。",
      "parameters": [
        {
          "name": "decoded_dir",
          "description": "解包目录",
          "type": "string",
          "required": true
        },
        {
          "name": "output_apk",
          "description": "输出 APK 路径",
          "type": "string",
          "required": true
        }
      ]
    },
    {
      "name": "lu_zipfix",
      "description": "修 ZIP 结构 / 重新对齐 / 重签。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "待修正的 APK（通常是 lu_rebuild 的产物）",
          "type": "string",
          "required": true
        },
        {
          "name": "output_apk",
          "description": "输出路径",
          "type": "string",
          "required": false
        },
        {
          "name": "zipalign",
          "description": "是否 4 字节对齐，默认 true",
          "type": "string",
          "required": false
        },
        {
          "name": "resign",
          "description": "是否重签，默认 true",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_dump_li",
      "description": "用 Layout Inspect 模块脱壳。",
      "parameters": [
        {
          "name": "package_name",
          "description": "目标应用包名",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "dex 收集目录，默认 /sdcard/Download…",
          "type": "string",
          "required": false
        },
        {
          "name": "wait_seconds",
          "description": "在悬浮窗点完 Dump 后等待收集产物的秒数，默认 60",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_all",
      "description": "一键流水线（默认预演 apply=false）。步骤不透明时不要用，按单步工具走。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "APK 文件路径。若设备没有 apktool，可先用 a…",
          "type": "string",
          "required": true
        },
        {
          "name": "decoded_dir",
          "description": "已解包目录（可选）。填了就跳过第 2 步解包，直接用这个…",
          "type": "string",
          "required": false
        },
        {
          "name": "work_dir",
          "description": "工作目录，默认 APK 同目录下 <软件名>脱修",
          "type": "string",
          "required": false
        },
        {
          "name": "real_app",
          "description": "真实 Application 全类名",
          "type": "string",
          "required": false
        },
        {
          "name": "apply",
          "description": "true 才真正写文件，默认 false 只预演",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_clear_shell",
      "description": "★一键清壳残留（360/乐固/爱加密/梆梆/娜迦/阿里）。内置各家现成正则和兜底规则，AI 不用自己拼正则、也不许自己拼。做五件事：①Manifest 入口换回真实 Application ②连标签一起删掉清单里残留的壳组件（provider/service 等，留着必闪退）③按厂商内置正则删掉冒充壳的 invoke 调用（含带 move-result 的两行写法，invoke-static 与 invoke-static/range 两种编码都覆盖）④把 native onCreate 换回 invoke-super + return-void（否则必闪退）⑤删壳 so / assets / smali 目录。删不掉或不敢删的壳调用会单独列在「没敢删的壳调用」里，必须如实转告用户。默认 dry_run 只报告。",
      "parameters": [
        {
          "name": "decoded_dir",
          "description": "apktool 解包目录",
          "type": "string",
          "required": true
        },
        {
          "name": "vendor",
          "description": "加固厂商：360（默认）/ tencent / ijiami / bangcle / naga / ali",
          "type": "string",
          "required": false
        },
        {
          "name": "real_app",
          "description": "真实 Application 全类名。不填会自动推断（唯一候选才用，多个候选会列出来让你选）",
          "type": "string",
          "required": false
        },
        {
          "name": "purge_so",
          "description": "删壳 so 与 assets 残留，默认 true",
          "type": "string",
          "required": false
        },
        {
          "name": "purge_smali_dir",
          "description": "整个删掉 smali/com/qihoo/ 这类壳包目录（激进，默认 false：业务代码引用到就会 ClassNotFound）",
          "type": "string",
          "required": false
        },
        {
          "name": "dry_run",
          "description": "true=只报告不修改（默认），false=实际写入",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_verify_clean",
      "description": "清完自检：搜 com.qihoo / com.stub / jiagu / vmp 四个关键词，全为 0 才算干净（企业版允许极少量业务自带字样，但 invoke 形态的壳调用必须为 0）。**文件名也查**（libjiagu.so / assets/jiagu.dat 这类光看内容是抓不到的，以前就因此假绿）。扫 APK 时还顺带做体积体检：报 dex 有没有被「不压缩」存进去（这是包体翻三倍的唯一原因）。解包目录和成品 APK 都能扫。",
      "parameters": [
        {
          "name": "decoded_dir",
          "description": "解包目录（与 apk_path 二选一）",
          "type": "string",
          "required": false
        },
        {
          "name": "apk_path",
          "description": "成品 APK（重打包之后用这个验）",
          "type": "string",
          "required": false
        },
        {
          "name": "keywords",
          "description": "自定义关键词，逗号分隔。默认 com.qihoo,com.stub,jiagu,vmp",
          "type": "string",
          "required": false
        }
      ]
    },
    {
      "name": "lu_qh_unpack",
      "description": "★免费版（老版）360 加固的「纯静态」脱壳：直接读 APK 里的 classes.dex，找 360 的 qh 容器（dex map_list 末尾那段），用内置密钥派生 + RC4 + LZMA/zstd 解出业务 dex。不用 root、不用跑 App、不用 dump、不连云端。顺带能判断「classes.dex 本身就是完整业务 dex」这种混合加固。**只覆盖 2019~2021 的免费版模型（0x52 整包 / 0xC6 记录 / qh 容器）**；2024 年以后的新版（有 libjiagu_art、函数抽取）不适用，那种还是得走 lu_dump 那条路。默认 dry_run 只探测。",
      "parameters": [
        {
          "name": "apk_path",
          "description": "APK 路径",
          "type": "string",
          "required": true
        },
        {
          "name": "output_dir",
          "description": "解出的 dex 放哪（dry_run=false 时用）",
          "type": "string",
          "required": false
        },
        {
          "name": "dry_run",
          "description": "true=只探测不写文件（默认），false=真解出并写盘",
          "type": "string",
          "required": false
        },
        {
          "name": "max_seconds",
          "description": "密钥枚举时间预算，默认 120 秒。没命中就加大再试（256 个种子逐个试是慢的）",
          "type": "string",
          "required": false
        }
      ]
    }
  ]
}
*/
// ============================================================
// 本地脱壳修复工具包 v1.0（全本地，专项 360 / 数字 / 720 加固）
//
// 设计原则：
//  1. 不依赖任何云端服务，全部在设备本地完成
//  2. 重活全部下沉到 python3 一次性执行（避免 JS 侧大量文件往返与 OOM）
//  3. 修复类工具只依赖「解包目录」，不管目录是谁解出来的
//     （本包 lu_decode / 官方 apk_reverse / MT 管理器 均可）
//  4. 所有写操作默认 dry_run，先报告再落地
//
// 关于 360 的核心认知（来自 MT 论坛 344 篇原帖）：
//   - 360 是函数抽取壳，onCreate 等方法体被抽走
//   - 方法体只有在「即将执行」那一刻才被壳还原进内存
//   - 所以单纯启动 App 再 dump → 拿到的是空壳
//   - 必须主动调用（FunDex2 / FART / frida_fart）才能拿到真实方法体
//   - 拿到后若仍不全 → 邪修大法（约 30%）/ AI 循环修复
//
// 2026-10-11 实战修正（血泪教训，别再踩）：
//   - 【老版 360】（桩类 com.frezrik.jiagu.StubApp）云端脱壳站能直接吐出完整方法体，
//     比本地 FunDex2/frida 稳得多，优先走云端；本地跑反而常拿到【壳容器】白干半天。
//   - 【新版 360】（桩类 com.stub.StubApp，真抽取）才需要主动调用。
//   - 所以：dump 完第一件事是 lu_dump_check 体检，别拿个容器当宝贝往下修。
//   - 重打包后装不上，八成是 lib/*.so 被压缩了 → lu_zipfix 改 STORED + 推 /data/local/tmp。
//
// v1.1 补充（依据开源工具 mHook xiamiya/mHook 的源码 + 一份实战重打包调试报告）：
//   - 【多轮对比】函数抽取壳是「边跑边补」的：第一轮 dump 的 dex 常常还没补完。
//     判据是「同一 dex 连续两轮 size+SHA-1 都不变」→ lu_dump 的 rounds 参数就是干这个的。
//   - 【致命坑】主动加载（遍历类逐个触发初始化）绝对不能跑在目标 App 的主线程：
//     类初始化要等 Application.onCreate 返回，而主线程此刻正卡在 hook 里等着 → 互等死锁，
//     表现就是 App 启动永远白屏。确认脱壳模块是在后台线程做主动调用的。
//   - 【特征库】360 还有两个藏得深的特征：libjgcxi.so / libjiagu_vip.so（企业版）
//     和 assets 下的 .appkey。另外 360/梆梆/腾讯 会把壳 so 塞在 assets/ 而不是 lib/，
//     所以 lu_probe 改成扫「全包所有 .so」，不再只扫 lib/。
//   - 【签名】必须显式 --v1-signing-enabled true --v2-signing-enabled true。
//     不写时 apksigner 按 SDK 版本自行推断，脱修场景经常只签了 v1 导致安装被拒。
//     且顺序铁律：zipalign 在前，apksigner 在后；之后任何改动 ZIP 字节的动作都要重签。
//
// v1.2 补充（依据手机端 AI 一次真实的重打包实战记录，2026-10-11 22:17）：
//   - 【重打包阶段原来是空的】v1.1 的 lu_rebuild 只管「apktool b + 签名」，但新版 360 的包
//     在回编译【之前】还有三件必做的事，缺一件就崩，这一版补齐：
//       ① 【dex 编号重排】原始 360 包的 dex 编号是乱的（classes.dex 是壳 + classes69~72.dex 是业务），
//          脱壳产物又是 classes.dex / classes2..8。跳号 ART 拒绝加载 → 新增 lu_repack_assemble。
//       ② 【壳代理 activity 必须从 manifest 删掉】新版 360 注册了 7 个 com.byazt.ff.Stub_*_Activity
//          （Standard/Portrait/Landscape/SingleTask + _T 半透明变体），这些类住在壳 dex 里。
//          壳 dex 丢弃后 → 启动即 ClassNotFoundException: com.byazt.ff.Stub_Standard_Activity。
//          → lu_manifest_fix 现在同时干这件事（原来只管改 android:name）。
//       ③ 【StubApp 桩不能原样留也不能直接删】业务代码里有 55+ 处调用 StubApp.getOrigApplicationContext、
//          40+ 处 interface11、以及 mark/interface12。壳的 StubApp.smali 有 1860 行且含 native 实现，
//          保留无用、删除则 NoClassDefFoundError → 必须「照签名写一份干净兼容桩」。
//          → 新增 lu_stub_scaffold 全自动生成（手机端 AI 手工写 359 行桩的活）。
//   - 【apktool -s 的正确用法】只解资源、dex 原样拷贝：
//       java -jar apktool.jar d -f -s <apk> -o <dir>
//     输出会打印 "Copying raw classes.dex file..."。这样改完 manifest 再回编译，
//     12 个巨型 dex 不用走 smali 往返（手机端全量 baksmali 跑十几分钟还超时，就是这个坑）。
//   - 【必须用 apktool 解，不能二进制替换】把 16 字符的 com.stub.StubApp 改成 33 字符的真实类名，
//     字符串池槽位不够，二进制替换做不到，只能走 apktool 解码 / 回编译。
//   - 【FunDex2 改作用域后必须重启 zygote】LSPosed/zygisk 的作用域注入决策在 zygote 启动时加载，
//     只重启 lspd 不生效 —— 要重启 zygote（system_server 的父进程）。
//   - 【Operit package_proxy 调不了别的包的工具】跨包调用会报
//     "Exactly one tool_name parameter is required"。要驱动 apktool 等 CLI，
//     直接走 super_admin:terminal，别绕 package_proxy。
//   - 【56.al 上传真机实测】58MB 包、8 片 × 8MB 分片，成功上传并轮询到完成。
//     下载回的 7z 解压密码统一是 dump（外层和内层都是）。
// ============================================================
var lu = (function () {
    'use strict';

    var DEFAULT_OUT = '/sdcard/Download/Operit/unpack_out';

    // ---------- 基础工具 ----------
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

    // ================== 两个执行环境，别混用 ==================
    // hiddenExec 默认跑在 proot Ubuntu 容器里（`login_ubuntu`），那个环境：
    //   ✔ 有 python3 / apt / /usr          ✘ 看不到 /system、/data/adb
    //   ✘ 没有 pm / am / monkey / su       ✔ 能看到 /sdcard、/data/local/tmp（被 proot 绑定进去）
    // 所以：python 扫描修复 → ENV_LINUX；任何 Android 侧探测或动作 → execAndroid。
    // 混用的后果就是「root 明明可用却报没装 Magisk」这类误判。
    var ENV_LINUX = 'linux';
    var ENV_ANDROID = 'android';

    // 宽松执行：不因非零退出码抛错，原样返回（用于探测类命令）
    function execRaw(cmd, timeoutMs, environment) {
        return Tools.System.terminal.hiddenExec(cmd, {
            timeoutMs: timeoutMs || 60000,
            environment: environment || ENV_LINUX
        }).then(function (r) { return r || { exitCode: -1, output: '' }; });
    }

    // ---------- Android 侧主通路：execute_shell（Tools.System.shell） ----------
    // 为什么不用 hiddenExec(environment:'android')：隐藏终端要它自己先打印 TERMINAL_READY
    // 才算就绪，Android 侧没有 login_ubuntu 那样的脚本替我们打 marker，于是每次都是
    // 30 秒超时 SHELL_START_FAILED（test18 真机 6 次全败）。而 execute_shell 走
    // AndroidShellExecutor → Root/Shizuku 执行器，同一轮 64 次成功。
    // 所以顺序反过来：execute_shell 为主，hiddenExec(android) 只作兜底。
    var ANDROID_VIA_SYS = 'sys';
    var ANDROID_VIA_HIDDEN = 'hidden';
    var ANDROID_DEAD = 'dead';
    var androidChannel = null; // 首次成功后记住，后面不再反复试错
    var androidHardFail = 0;   // 通路级失败连击计数

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

    // execute_shell 在退出码非 0 时会 reject，错误文本是
    //   "ADB command execution failed (exit code: N): <stderr>\n<stdout>"
    // 这种属于「通道正常、只是命令失败」，不该切兜底 —— 把输出从错误里捞回来即可。
    function parseSysError(err) {
        var msg = '';
        if (err && err.message) msg = String(err.message);
        else if (typeof err === 'string') msg = err;
        else { try { msg = JSON.stringify(err); } catch (_e) { msg = String(err); } }
        var m = /ADB command execution failed \(exit code:\s*(-?\d+)\):\s*([\s\S]*)$/.exec(msg);
        if (m) return { exitCode: parseInt(m[1], 10), output: m[2] || '', channelOk: true, raw: msg };
        return { exitCode: -1, output: msg, channelOk: false, raw: msg };
    }

    function execAndroidViaHidden(cmd, timeoutMs) {
        return execRaw(cmd, timeoutMs, ENV_ANDROID).then(
            function (r) { return r || { exitCode: -1, output: '' }; },
            function (e) { return { exitCode: -1, output: String((e && e.message) || e) }; }
        );
    }

    // Android 侧动作专用（pm/am/monkey/su/读 /data/adb 都得走这个）
    function execAndroid(cmd, timeoutMs) {
        var t = timeoutMs || 60000;
        if (androidChannel === ANDROID_DEAD) {
            return Promise.resolve({ exitCode: -1, output: '[android 通路已判定不可用，本条跳过]' });
        }
        if (androidChannel === ANDROID_VIA_HIDDEN) return execAndroidViaHidden(cmd, t);
        return Tools.System.shell(cmd).then(function (r) {
            androidChannel = ANDROID_VIA_SYS;
            androidHardFail = 0;
            return normSysResult(r);
        }, function (e) {
            var p = parseSysError(e);
            if (p.channelOk) {
                androidChannel = ANDROID_VIA_SYS;
                androidHardFail = 0;
                return { exitCode: p.exitCode, output: p.output };
            }
            return execAndroidViaHidden(cmd, t).then(function (r) {
                if (r && r.exitCode !== -1) {
                    androidChannel = ANDROID_VIA_HIDDEN;
                    androidHardFail = 0;
                    return r;
                }
                // 兜底通路代价很高：hiddenExec(android) 失败要等满 30 秒超时。
                // 所以连挂 2 次就直接熔断，别让后面每条命令再各罚站 30 秒（17 条 = 8.5 分钟）。
                androidHardFail++;
                if (androidHardFail >= 2) androidChannel = ANDROID_DEAD;
                return {
                    exitCode: -1,
                    output: '[android 双通路都失败] execute_shell: ' + p.output.substring(0, 200)
                        + ' || hiddenExec: ' + String((r && r.output) || '').substring(0, 200)
                };
            });
        });
    }

    // 只走 execute_shell、绝不兜底的版本：用于「必须真 root」的判定点，
    // 避免兜底通道把真实失败掩盖成半成功。
    function execAndroidStrict(cmd, timeoutMs) {
        return Tools.System.shell(cmd).then(function (r) {
            androidChannel = ANDROID_VIA_SYS;
            return normSysResult(r);
        }, function (e) {
            var p = parseSysError(e);
            if (p.channelOk) { androidChannel = ANDROID_VIA_SYS; return { exitCode: p.exitCode, output: p.output }; }
            return { exitCode: -1, output: p.output };
        });
    }

    // 严格执行：非零退出码抛错
    function exec(cmd, timeoutMs, environment) {
        return execRaw(cmd, timeoutMs, environment).then(function (r) {
            if (r.exitCode !== 0) {
                var out = r.output || '';
                var e = new Error('命令失败 (exit=' + r.exitCode + '): ' + cmd + '\n' + out.substring(0, 800));
                e.exitCode = r.exitCode; e.output = out;
                throw e;
            }
            return r;
        });
    }

    function q(s) { return "'" + String(s).replace(/'/g, "'\\''") + "'"; }

    function dirname(p) {
        if (!p) return DEFAULT_OUT;
        var i = p.lastIndexOf('/');
        return i > 0 ? p.substring(0, i) : '/';
    }

    function basenameNoExt(p) {
        if (!p) return 'app';
        var n = p.split('/').pop() || 'app';
        return n.replace(/\.[^.]*$/, '') || 'app';
    }

    // 把 python 脚本写到设备再执行（避免命令行长度限制与引号地狱）
    function runPy(pyCode, args, timeoutMs) {
        var script = DEFAULT_OUT + '/.lu_tmp.py';
        return exec('mkdir -p ' + q(DEFAULT_OUT), 15000)
            .then(function () {
                // 用 heredoc 写入，避免转义问题。
                // **分隔符必须带引号**（<<'LUPYEOF'）。不带的 heredoc 会做反斜杠转义处理，
                // python 里的 '\\' 会被吃成 '\' —— 实测真机上 lu_clear_shell 连着 4 个包全部
                // 挂在 `SyntaxError: unterminated string literal`（.replace('\\','/') 被吃成 '\''），
                // 于是「清壳」这一步从来没真正跑过，用户看到的就是「加固特征没删」。
                var marker = 'LUPYEOF';
                var cmd = 'cat > ' + q(script) + ' <<\'' + marker + '\'\n' + pyCode + '\n' + marker + '\n';
                return exec(cmd, 30000);
            })
            .then(function () {
                return exec('python3 ' + q(script) + ' ' + (args || ''), timeoutMs || 300000);
            })
            .then(function (r) {
                var out = (r.output || '').trim();
                // 约定：python 侧最后一行输出 JSON，前缀 LURESULT:
                var idx = out.lastIndexOf('LURESULT:');
                if (idx >= 0) {
                    var js = out.substring(idx + 9).trim();
                    try { return JSON.parse(js); } catch (e) { return { raw: out }; }
                }
                return { raw: out };
            });
    }

    // ---------- 环境体检 ----------
    function lu_env_check(params) {
        var checks = [];
        function add(name, ok, detail, fix) {
            checks.push({ 项: name, 状态: ok ? 'OK' : '缺失', 详情: detail, 补救: ok ? '' : (fix || '') });
        }
        // 三态版：OK / 受限（探测不到但不代表没有，比如 root 通了却读不到 /data/adb）/ 缺失
        function addS(name, state, detail, fix) {
            checks.push({ 项: name, 状态: state, 详情: detail, 补救: state === 'OK' ? '' : (fix || '') });
        }

        // 【执行环境】Android 侧命令必须走 ENV_ANDROID。proot Ubuntu 里没有 /system、/data/adb，
        // 也没有 pm / am / monkey / su —— 在那里探测，结论必然全是「没装」。
        // 唯一必须留在 ENV_LINUX 的是 python3（Android 系统 shell 没有 python）。
        var LSMOD = "if su -c 'ls /data/adb/modules' >/dev/null 2>&1; then su -c 'ls /data/adb/modules'; else echo NONE; fi";
        var cmds = [
            // ---- 先确认自己到底在哪个环境 ----
            ['selfenv', 'echo "uname=$(uname -s 2>/dev/null)"; '
                + 'echo "uid=$(id -u 2>/dev/null)"; '
                + 'echo "sh=$([ -x /system/bin/sh ] && echo YES || echo NO)"; '
                + 'echo "is_android=$([ -x /system/bin/app_process64 ] || [ -x /system/bin/app_process ] || [ -f /system/build.prop ] && echo YES || echo NO)"; '
                + 'echo "has_pm=$(command -v pm >/dev/null 2>&1 && echo YES || echo NO)"; '
                + 'echo "has_magisk_bin=$(command -v magisk >/dev/null 2>&1 && echo YES || echo NO)"; '
                + 'echo "adb_dir=$([ -d /data/adb ] && echo YES || echo NO)"; '
                + 'echo SELFEND', ENV_ANDROID],

            // ---- root / 方案 ----
            ['root', 'id -u 2>/dev/null; echo "---"; su -c id 2>/dev/null || echo NOSU', ENV_ANDROID],
            ['lsmod', LSMOD, ENV_ANDROID],
            ['magiskbin', "su -c 'command -v magisk' 2>/dev/null || su -c 'ls -d /data/adb/magisk' 2>/dev/null || echo NONE", ENV_ANDROID],
            ['lsposed', "if su -c 'ls /data/adb/modules' >/dev/null 2>&1; then su -c 'ls /data/adb/modules' | grep -i -E 'lsposed|zygisk' || echo NONE; else echo NONE; fi", ENV_ANDROID],
            ['lspapp', "su -c 'pm list packages' 2>/dev/null | grep -i -E 'lsposed|magisk|fundex|blackdex|layoutinspect' || echo NONE", ENV_ANDROID],
            // root 方案多路径探测。/data/adb 读不到不代表没装 root —— 命名空间/SELinux 也会导致读不到，
            // 而"root 明明可用却报没装 Magisk"是最误导人的一种误判。
            ['adbx', "su -c 'magisk -V' 2>/dev/null; su -c 'ksud -V' 2>/dev/null; "
                + "su -c 'ls -d /data/adb/magisk /data/adb/ksu /data/adb/ap /data/adb/modules /data/adb/lspd /data/adb/modules_update' 2>&1 | head -20; "
                + "ls -d /debug_ramdisk/.magisk /sbin/.magisk 2>/dev/null; echo ADBXEND", ENV_ANDROID],

            // ---- 工具链 ----
            // python3 一定在 Ubuntu 侧，Android 系统 shell 里没有
            ['python3', 'command -v python3 2>/dev/null || echo NONE', ENV_LINUX],
            ['apktool', 'command -v apktool 2>/dev/null || su -c "ls /data/data/com.termux/files/usr/bin/apktool" 2>/dev/null || echo NONE', ENV_ANDROID],
            ['signer', 'command -v apksigner 2>/dev/null || echo NONE', ENV_ANDROID],
            ['zipalign', 'command -v zipalign 2>/dev/null || echo NONE', ENV_ANDROID],
            ['sigapp', "su -c 'pm list packages' 2>/dev/null | grep -i -E 'bin.mt.plus|mt2|npmanager|apktool|termux' || echo NONE", ENV_ANDROID],
            // apktool/apksigner 都是 Java 程序。设备没有 apktool 命令不代表不能跑：
            // 只要能找到 java/dalvikvm + 现成的 apktool jar，就能在脚本里直接调用。
            ['java', "command -v java 2>/dev/null; command -v dalvikvm 2>/dev/null; ls /system/bin/dalvikvm /system/bin/app_process64 /system/bin/app_process32 /apex/com.android.art/bin/dalvikvm 2>/dev/null; echo JAVEND", ENV_ANDROID],
            // apk_reverse 的 jar 在 /data/data/<pkg>/files/toolpkg_cache 下
            ['jars', "timeout 15 su -c \"find /data/data /data/app /data/local -maxdepth 7 -type f -name '*.jar' 2>/dev/null | grep -iE 'apktool|apksigner|jadx|baksmali|helper|sign' | head -30\" 2>/dev/null; echo JAREND", ENV_ANDROID],

            // ---- 脱壳手段储备 ----
            // eBPF 能力：决定 eBPFDexDumper-rs（root 免刷 ROM、自带方法体回填）这条路能不能走
            ['ebpf', "ls /sys/kernel/btf/vmlinux 2>/dev/null; ls -d /sys/fs/bpf 2>/dev/null; su -c 'zcat /proc/config.gz' 2>/dev/null | grep -E 'CONFIG_(BPF|UPROBES|BPF_EVENTS|BPF_SYSCALL)=' ; ls /proc/config.gz 2>/dev/null; uname -r; echo EBPFEND", ENV_ANDROID],
            // 现成的第三方脱壳二进制（有人推到 /data/local/tmp 就能直接用）
            ['dumpbin', "ls -la /data/local/tmp 2>/dev/null | grep -iE 'dump|fart|frida|dex' ; su -c 'ls -la /data/local/tmp' 2>/dev/null | grep -iE 'dump|fart|frida|dex'; echo BINEND", ENV_ANDROID],
            ['dumpdirs', 'ls -d /sdcard/dexFile /sdcard/Download/*.dex /sdcard/Android/data/*/files/*.dex 2>/dev/null; echo END', ENV_ANDROID],
            ['space', 'df -h /sdcard 2>/dev/null | tail -1 || echo NONE', ENV_ANDROID]
        ];

        var raw = {};
        var chain = Promise.resolve();
        var androidRuns = 0;   // 走 Android 通路的命令数
        var chanFail = 0;      // 其中「通路级失败」的数量（不是命令失败）
        cmds.forEach(function (c) {
            chain = chain.then(function () {
                // android 走 execute_shell（Tools.System.shell），linux 走 proot 的 hiddenExec。
                // 不能统一走 hiddenExec：android 那条在真机上起不来（见 execAndroid 注释）。
                var run = (c[2] === ENV_ANDROID)
                    ? execAndroid(c[1], 20000)
                    : execRaw(c[1], 20000, ENV_LINUX);
                return run.then(function (r) {
                    var out = (r.output || '').trim();
                    if (c[2] === ENV_ANDROID) {
                        androidRuns++;
                        // 通路级失败时 output 是错误文本（以 '[' 开头），不是设备信息。
                        // 绝不能把它当探测结果：否则「[android 双通路都失败]…」这串字会让
                        // Magisk / 签名 / Java / LSPosed 逐条被判成 OK，得出完全相反的结论。
                        if (r.exitCode === -1 && out.charAt(0) === '[') {
                            chanFail++;
                            raw[c[0]] = '';
                            return;
                        }
                    }
                    raw[c[0]] = out;
                }, function (e) {
                    if (c[2] === ENV_ANDROID) { androidRuns++; chanFail++; }
                    raw[c[0]] = '';
                });
            });
        });

        function lines(k) {
            var o = raw[k] || '';
            if (o === 'NONE' || !o) return [];
            return o.split('\n').map(function (x) { return x.trim(); })
                .filter(function (x) { return x && x !== 'NONE' && x.charAt(0) !== '['; });
        }

        return chain.then(function () {
            // --- 通路级失败：直接出结论，不再往下猜 ---
            // 全部 Android 命令都没拿到设备信息时，继续往下判只会产出一份「看似完整、实则全是瞎猜」
            // 的报告（`lsmod` 拿到一串报错文本 → 判「共 N 个模块」→ Magisk OK）。宁可明确说"没验到"。
            var chanDead = androidRuns > 0 && chanFail >= androidRuns - 1 && !raw.selfenv;
            if (chanDead) {
                var retryTip = '1) Operit 设置 → 权限，确认把 root（Magisk）或 Shizuku 授予了本应用；\n'
                    + '2) 授权后回到这里重跑 lu_env_check；\n'
                    + '3) 不要因为这份报告就去重装 Magisk —— 通路失败和 Magisk 是否安装是两回事。';
                checks.push({
                    项: 'Android 执行环境', 状态: '缺失',
                    详情: 'Android 侧 ' + androidRuns + ' 条命令全部没有返回设备信息（execute_shell 通路不可用）',
                    补救: retryTip
                });
                var pyOut = (raw.python3 || '').trim();
                checks.push({
                    项: 'python3', 状态: (pyOut && pyOut !== 'NONE') ? 'OK' : '缺失',
                    详情: pyOut || '未探测到（Ubuntu 侧同样没起来）',
                    补救: (pyOut && pyOut !== 'NONE') ? '' : 'Ubuntu 侧也不可用，本包的 python 扫描/修复跑不了'
                });
                return {
                    体检项: checks,
                    缺失数: 1,
                    受限数: 0,
                    通道不可用: true,
                    结论: '【停在第一步】Android 执行通路不可用，本次所有 Android 侧能力「未验证」，'
                        + '不代表没装。先按「补救」列修授权，然后原样重跑 lu_env_check。'
                        + '在通路恢复前不要执行 lu_dump / lu_decode / 任何 adb 类动作 —— 它们会全部失败。',
                    解包能力判断: '未验证（先修 Android 执行通路）',
                    签名能力判断: '未验证（先修 Android 执行通路）',
                    脱壳能力判断: {
                        首选: '未验证（先修 Android 执行通路）',
                        备选: ['通路恢复后重跑 lu_env_check 再定路线']
                    },
                    提示: '本次没有对设备做任何假设。上面的「缺失数」只统计通路本身，不是能力缺失。'
                };
            }

            // --- 先报「命令到底跑在哪个环境」 ---
            // 这一段是本轮最大的教训：proot Ubuntu 里没有 /system、/data/adb、pm、am、monkey、su。
            // 如果在这层做探测，结论必然全是"没装"，而用户看到的 prompt 是 root@localhost。
            var selfL = (raw.selfenv || '').split('\n')
                .map(function (x) { return x.trim(); })
                .filter(function (x) { return x && x !== 'SELFEND'; });
            function selfVal(key) {
                var hit = selfL.filter(function (x) { return x.indexOf(key + '=') === 0; })[0];
                return hit ? hit.substring(key.length + 1) : '';
            }
            var isAndroidEnv = selfVal('is_android') === 'YES' || selfVal('has_pm') === 'YES';
            var selfUid = selfVal('uid') || '?';
            addS('Android 执行环境', isAndroidEnv ? 'OK' : '缺失',
                isAndroidEnv
                    ? 'execute_shell 通路正常（uid=' + selfUid + '，pm 可见，/data/adb '
                        + (selfVal('adb_dir') === 'YES' ? '存在' : '不存在') + '）'
                    : 'Android 侧命令拿不到有效输出（uname=' + (selfVal('uname') || '?')
                        + '，pm 不可见，uid=' + selfUid + '）',
                isAndroidEnv ? ''
                    : '本包的 Android 侧命令走 execute_shell（Tools.System.shell → Root/Shizuku 执行器）。'
                      + '拿不到输出通常是 Operit 的「首选权限级别」没授权：去设置里确认 root 或 Shizuku 已授予本应用。'
                      + '注意这不是 Magisk 没装 —— 两条通路无关');

            // --- root ---
            var rl = (raw.root || '').split('---');
            var uid = (rl[0] || '').trim();
            var suPart = (rl[1] || '').trim();
            var isRoot = uid === '0' || /uid=0/.test(suPart);
            add('root 权限', isRoot, 'uid=' + uid + ' | su: ' + (suPart === 'NOSU' ? '不可用' : '可用'),
                '刷入 Magisk 并在 Magisk 里给 Operit 授予 root 授权');

            // --- magisk / root 方案 ---
            // 关键：root 可用但 /data/adb 读不到 → 判「受限」而不是「缺失」。
            // 否则会出现「uid=0 却说没装 Magisk，让用户去授权 root」的自相矛盾结论。
            var mods = lines('lsmod');
            var mgBin = lines('magiskbin');
            var adbx = (raw.adbx || '').split('\n')
                .map(function (x) { return x.trim(); })
                .filter(function (x) { return x && x !== 'ADBXEND' && x !== 'NONE'; });
            var mgHit = adbx.filter(function (x) { return /^magisk|^[0-9]+\.[0-9]|^v?[0-9]+\.[0-9]+\.[0-9]+/.test(x) || /\/data\/adb\/(magisk|ksu|ap|modules|lspd)/.test(x); });
            var hasMg = mods.length > 0 || mgBin.length > 0 || mgHit.length > 0;
            var adbBlocked = isRoot && !hasMg;
            addS('Magisk', hasMg ? 'OK' : (adbBlocked ? '受限' : '缺失'),
                hasMg ? ('模块目录可读，共 ' + mods.length + ' 个模块' + (mgBin.length ? ' | magisk: ' + mgBin[0] : ''))
                    : adbBlocked
                        ? 'root 可用（uid=' + uid + '），但 /data/adb 读不到；探测输出: ' + (adbx.join(' | ').slice(0, 120) || '(空)')
                        : '/data/adb/modules 读不到且无 magisk 命令',
                hasMg ? '' : (adbBlocked
                    ? '先自查：su -c "magisk -V" 和 su -c "ls /data/adb"。root 已通却读不到 /data/adb，'
                      + '通常是 root shell 的挂载命名空间/SELinux 限制，不是没装 —— 换 Operit 的 root 终端重开一次再测'
                    : '确认已装 Magisk / KernelSU / APatch，并在管理器里给 Operit 授予 root'));
            if (mods.length) {
                checks.push({ 项: '已装模块', 状态: 'OK', 详情: mods.join(', '), 补救: '' });
            }

            // --- 脱壳模块（360 成败关键） ---
            var dumpers = mods.filter(function (m) { return /dump|dex|fundex|frida|blackdex/i.test(m); });
            addS('本地脱壳模块', dumpers.length > 0 ? 'OK' : (adbBlocked ? '受限' : '缺失'),
                dumpers.length ? dumpers.join(', ')
                    : adbBlocked ? '模块目录读不到，无法判断装没装（不代表没装）'
                    : '未发现脱壳类模块（当前 ' + mods.length + ' 个模块里没有）',
                dumpers.length ? ''
                    : '推荐装 FunDex2（LSPosed，支持类抽取，360 必需）或 frida_fart；'
                      + '不想装东西就走 56al_tuoke:tuoke_all 云端脱壳');
            // --- Layout Inspect（LSPosed 悬浮窗脱壳工具，支持愛加蜜等） ---
            var liApp = lines('lspapp').filter(function (x) { return /layoutinspect/i.test(x); });
            add('Layout Inspect', liApp.length > 0,
                liApp.length ? liApp.join(', ') + '（支持 Dump Dex + 主动加载类，愛加蜜等抽取壳可用）' : '未检测到',
                '装 com.flass.layoutinspect 并启用为 LSPosed 模块，作用域勾选目标 App，整机重启；之后可用 lu_dump_li 引导脱壳');

            // --- lsposed ---
            var lspMod = lines('lsposed');
            var lspApp = lines('lspapp');
            var hasLsp = lspMod.length > 0 || lspApp.some(function (x) { return /lsposed/i.test(x); });
            addS('LSPosed', hasLsp ? 'OK' : (adbBlocked ? '受限' : '缺失'),
                hasLsp ? ('模块: ' + (lspMod.join(', ') || '(由包列表命中)') + ' | 包: ' + (lspApp.join(', ') || '-'))
                    : adbBlocked ? '模块目录读不到，无法判断（FunDex2 依赖它）'
                    : '未检测到（不影响本包，但 FunDex2 需要它）',
                hasLsp ? '' : '装 LSPosed，再装 FunDex2 用于 360 类抽取脱壳');

            // --- 工具链 ---
            var py = lines('python3');
            add('python3', py.length > 0, py.length ? py[0] : '未找到',
                '本包的扫描/修复依赖 python3。若无可改用 Operit 官方 apk_reverse');

            // 先算出 jar / Java 情况，后面的 apktool、签名、Java 三项都要用它判断
            function marked(k, marker) {
                return (raw[k] || '').split('\n')
                    .map(function (x) { return x.trim(); })
                    .filter(function (x) { return x && x !== marker && x !== 'NONE'; });
            }
            var javaL = marked('java', 'JAVEND');
            var jarL = marked('jars', 'JAREND');
            var atJar = jarL.filter(function (x) { return /apktool/i.test(x); });
            var hasJava = javaL.length > 0;
            // 【关键判据】apk_reverse 工具包自带 apktool / jadx / apksig 的 android 版 jar，
            // 走的是 Operit 内部的 JS→Java 桥，**不依赖 shell 里的 java 命令**。
            // 只要缓存里能看到这几个 jar，解包 + 重打包 + 签名这三件事本地就是通的。
            var hasApkReverse = jarL.some(function (x) {
                return /apk-reverse-helper-runtime|apktool-runtime|jadx-runtime/i.test(x);
            });

            var at = lines('apktool');
            addS('apktool 命令', at.length > 0 ? 'OK' : (hasApkReverse ? 'OK' : '缺失'),
                at.length ? at[0]
                    : hasApkReverse ? '无 shell 命令，但 apk_reverse 工具包已就位（用 apk_reverse:apk_reverse_decode）'
                    : '未找到',
                '可用 Operit 官方 apk_reverse 的 apk_reverse_decode 代替，解出的目录本包照常能用');

            var sg = lines('signer');
            var sigApp = lines('sigapp');
            var hasSign = sg.length > 0 || sigApp.length > 0 || hasApkReverse;
            addS('签名能力', hasSign ? 'OK' : '缺失',
                sg.length ? sg[0]
                    : hasApkReverse ? 'apk_reverse:apk_reverse_sign / apk_reverse_build_and_sign（内置 apksig，v1+v2+v3 可开）'
                    : sigApp.length ? ('兜底: ' + sigApp.join(', '))
                    : '既无 apksigner 也无 MT/NP/Termux，也没有内置 apksig',
                'apksigner 缺失时，用 MT 管理器「APK 签名」或 apk_reverse:apk_reverse_sign 兜底');

            add('zipalign', lines('zipalign').length > 0, lines('zipalign')[0] || '未找到', '可选');

            // --- Java 运行时：只在「没有内置 jar」时才真正构成阻塞 ---
            addS('Java 运行时', hasJava ? 'OK' : (hasApkReverse ? 'OK' : '缺失'),
                hasJava ? javaL.join(', ')
                    : hasApkReverse ? '无 shell java —— 不需要：apk_reverse 用内置 jar + JS 桥直调，不走 shell'
                    : '没找到 java / dalvikvm / app_process',
                'apktool 与 apksigner 都是 Java 程序。没有 Java 运行时且无内置 jar 时，'
                    + '只能靠 MT 管理器或 apk_reverse 兜底');
            if (jarL.length) {
                checks.push({ 项: '发现的逆向 jar', 状态: 'OK', 详情: jarL.join(', '), 补救: '' });
            }

            // --- eBPF 能力：eBPFDexDumper-rs 路线是否可行 ---
            var ebpfL = marked('ebpf', 'EBPFEND');
            var hasBtf = ebpfL.some(function (x) { return /vmlinux/.test(x); });
            var cfgOk = ebpfL.some(function (x) { return /CONFIG_(BPF|UPROBES|BPF_EVENTS|BPF_SYSCALL)=y/.test(x); });
            var hasCfg = ebpfL.some(function (x) { return /config\.gz/.test(x); });
            add('eBPF 能力', hasBtf || cfgOk,
                ebpfL.join(' | ') || '未探测到',
                hasCfg && !cfgOk
                    ? '内核配置里没有开 CONFIG_BPF / UPROBES / BPF_EVENTS → eBPFDexDumper-rs 用不了，'
                      + '改用 FunDex2(LSPosed) 或 frida_fart'
                    : '需 /sys/kernel/btf/vmlinux 或内核开 CONFIG_BPF=y 且 CONFIG_UPROBES=y；'
                      + '满足就能用 eBPFDexDumper-rs（root 免刷 ROM，自带方法体回填）');

            // --- 现成第三方脱壳二进制 ---
            var binL = marked('dumpbin', 'BINEND');
            if (binL.length) {
                checks.push({ 项: '/data/local/tmp 里的脱壳二进制', 状态: 'OK', 详情: binL.join(' | '), 补救: '' });
            }

            add('存储空间', true, raw.space || '', '');

            // --- 解包能力综合判定 ---
            // 顺序很重要：内置 apk_reverse 永远排第一 —— 它是自带 jar 的，不受 shell 环境影响。
            var hasMt = sigApp.some(function (x) { return /bin\.mt\.plus|mt2|npmanager/i.test(x); });
            var decodeWay = hasApkReverse
                    ? 'apk_reverse:apk_reverse_decode（工具包已内置，不依赖 apktool 命令/系统 Java/MT 管理器）'
                : at.length > 0 ? 'apktool 命令直接可用'
                : (hasJava && atJar.length > 0) ? ('可 java/dalvikvm 调用现成 apktool jar：' + atJar[0])
                : hasMt ? '用 MT/NP 管理器解包，再把目录填进 lu_all 的 decoded_dir'
                : '暂时无解包手段 —— 先装 Termux(pkg install apktool) 或 MT 管理器，'
                  + '或用官方 apk_reverse 的 apk_reverse_decode';

            // --- 脱壳手段闸门：按「装了哪个就先走哪条」给出唯一推荐，不要只报缺失 ---
            // 排序原则：首选必须是**现在立刻能跑**的。eBPF 虽然内核支持，但二进制还不在设备上，
            // 要用户先放文件，所以它排云端之后。
            var dumpGate;
            if (dumpers.length > 0) {
                dumpGate = {
                    首选: 'local_unpack:lu_dump（本地主动调用，已装 ' + dumpers.join('、') + '）',
                    备选: ['local_unpack:lu_dump_li（若同时装了 Layout Inspect）', '56al_tuoke:tuoke_all 云端兜底']
                };
            } else if (liApp.length > 0) {
                dumpGate = {
                    首选: 'local_unpack:lu_dump_li（Layout Inspect 引导脱壳，已装）',
                    备选: ['56al_tuoke:tuoke_all 云端兜底']
                };
            } else {
                dumpGate = {
                    首选: '56al_tuoke:tuoke_all 云端脱壳（零安装、现在就能跑，需先登录该包）',
                    备选: [
                        (hasBtf || cfgOk)
                            ? '本地 eBPF：内核支持（' + (hasBtf ? '有 BTF' : 'CONFIG_BPF=y') + '），'
                              + '放 eBPFDexDumper-rs 到 /data/local/tmp 后走 lu_dump，自带方法体回填'
                            : '装 FunDex2（LSPosed）后 lu_dump 主动调用',
                        'local_unpack:lu_fill_oncreate 邪修（约 30%，不建议单独用）'
                    ]
                };
            }

            var missing = checks.filter(function (c) {
                return c.状态 === '缺失' && c.项 !== '存储空间' && c.项 !== 'zipalign';
            });
            var limited = checks.filter(function (c) { return c.状态 === '受限'; });
            var okAll = missing.length === 0 && limited.length === 0;
            return {
                体检项: checks,
                缺失数: missing.length,
                受限数: limited.length,
                结论: okAll ? '环境齐备，可以直接开工'
                    : (missing.length === 0 && limited.length > 0)
                        ? '有 ' + limited.length + ' 项「受限」：root 已通但读不到 /data/adb，'
                          + '这类项不能当成没装（详见补救列），其余能力可用'
                    : '有 ' + missing.length + ' 项缺失' + (limited.length ? '、' + limited.length + ' 项受限' : '')
                      + '，见「补救」列。python3 在就能跑本包的扫描与修复；'
                      + '解包/签名/重打包由内置 apk_reverse 提供，不受影响',
                解包能力判断: decodeWay,
                签名能力判断: hasApkReverse ? 'apk_reverse:apk_reverse_sign（内置 apksig）'
                    : (sg.length ? sg[0] : (sigApp.length ? 'MT/NP 管理器' : '缺失')),
                脱壳能力判断: dumpGate,
                提示: '针对 360：函数抽取壳的方法体只有在该方法即将执行时才被还原，单纯启动 App 再 dump 拿到的是空壳，'
                    + '必须走「主动调用」（FunDex2 / FART / frida_fart）或 Layout Inspect 引导；'
                    + '两样都没有时，直接走「脱壳能力判断」里的首选，不要停在那里让用户装东西。'
            };
        });
    }

    // ---------- 壳型与难度预判（直接读 APK，不需解码） ----------
    function lu_probe(params) {
        var apk = params.apk_path;
        if (!apk) throw new Error('缺少 apk_path');

        var py = String.raw`
import sys, os, zipfile, json, re, datetime
apk = sys.argv[1]
res = {"apk": apk, "size_mb": round(os.path.getsize(apk)/1048576, 2)}
try:
    z = zipfile.ZipFile(apk)
    names = z.namelist()
except Exception as e:
    print("LURESULT:" + json.dumps({"error": "无法读取 APK: " + str(e)}))
    sys.exit(0)

dex = [n for n in names if n.lower().endswith(".dex")]
libs = [n for n in names if n.startswith("lib/")]
assets = [n for n in names if n.startswith("assets/")]
res["dex_count"] = len(dex)
res["dex_sizes"] = [round(z.getinfo(n).file_size/1024.0, 1) for n in dex]
res["libs"] = sorted(set(os.path.basename(n) for n in libs))
res["assets"] = sorted(set(os.path.basename(n) for n in assets))[:40]

# ---- zip 条目时间：判 360 的「0701 付费时代」(2026-07-01 起停止免费加固) ----
# 硬坑：zipflinger / 可重现构建流水线会把条目时间统一写成 1980/2008 固定值，
# 那时 可信=False，绝不能拿时间判年代 —— 说「不知道」比猜错强。
ZIP_SANE_MIN = 1420070400000    # 2015-01-01
QIHOO_PAID_CUT = 1782835200000  # 2026-07-01 00:00:00 GMT+8
# 360 引擎换代分界 2024-01-01 00:00:00 GMT+8。桩类 com.stub.StubApp 从 2019 年就在用，
# 光看桩类分不出 2020 年的老包和今年的新包 —— 必须叠 dex 时间。经验阈值，通过「代次依据」字段暴露。
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

_cls = [n for n in names if re.match(r"^classes\d*\.dex$", n)]
_ts = []
for _n in _cls:
    try:
        _t = _zip_ts(z.getinfo(_n).date_time)
        if _t > 0:
            _ts.append(_t)
    except Exception:
        pass
_sane = [t for t in _ts if t >= ZIP_SANE_MIN]
# 「总dex」用全部 .dex 计，与 res["dex_count"] 同口径。
# 只数根目录 classes*.dex 会出现「dex_count=3 而 总dex=1」的自相矛盾，
# 还会把「只有 1 个 dex 有时间证据」伪装成全量证据。
_dex_cover = "%d/%d" % (len(_sane), len(dex)) if dex else "0/0"
_dex_cover_partial = bool(dex) and len(_sane) < len(dex)
if _sane:
    _paid = min(_sane) >= QIHOO_PAID_CUT
    _free = max(_sane) < QIHOO_PAID_CUT
    res["dex时间"] = {
        "可信": True,
        "最早": _fmt_day(min(_sane)),
        "最晚": _fmt_day(max(_sane)),
        "dex数": len(_sane),
        "总dex": len(dex),
        "时间样本": _dex_cover,
        "时间窗": ("付费时代（2026-07-01 之后）" if _paid
                   else ("免费时代（2026-07-01 之前）" if _free
                         else "跨 2026-07-01 分界，时间不可作代次依据")),
        "_全在0701后": _paid,
    }
    if _dex_cover_partial:
        res["dex时间"]["注意"] = ("%d 个 dex 里只有 %d 个时间可用（其余被抹或不在根目录），"
                                  "代次只按这 %d 个判" % (len(dex), len(_sane), len(_sane)))
else:
    res["dex时间"] = {
        "可信": False,
        "原始数": len(_ts),
        "总dex": len(dex),
        "时间样本": _dex_cover,
        "时间窗": "时间戳被抹（打包器写成固定值），不能用时间判年代 —— 只看特征定代次",
        "_全在0701后": False,
    }

manifest = None
for n in names:
    if n == "AndroidManifest.xml":
        manifest = z.read(n)
        break
res["has_manifest"] = manifest is not None

stub = None
if manifest:
    # 二进制 AXML 里类名以 UTF-16 存放，做宽松匹配
    txt = manifest.decode("utf-16-le", errors="ignore")
    m = re.search(r"com\.(stub|qihoo|frezrik|secneo)\.[\w\.]+", txt)
    if m:
        stub = m.group(0)
    m2 = re.search(r"com\.frezrik\.jiagu\.[\w\.]+", txt)
    if m2:
        stub = m2.group(0)
res["stub_class"] = stub

# 2026-10-11 特征库扩容，依据开源工具 mHook（xiamiya/mHook）的 PackerDetector.java：
#   ① 360 企业版的两个隐蔽特征是 libjgcxi.so / libjiagu_vip.so，旧表只有 libjiagu.so 会漏判
#   ② 360 会在 assets 下放 .appkey 授权文件，找不到 so 时它是唯一线索
#   ③ 360 / 梆梆 / 腾讯 都可能把壳 so 塞在 assets/ 或包根而不是 lib/，所以改成扫「全包所有 .so」
#   ④ com.frezrik.jiagu.* 是 360 老版壳的桩类，单独正名，不混进通用的 com.stub
SHELL_RULES = [
    (["libjiagu.so", "libjiagu_art.so", "libjiagu_x86.so", "libjiagu_64.so",
      "libjiagu_vip.so", "libjgcxi.so"], "360加固（数字/720/某60）"),
    (["libjiagu_sdk.so"], "360加固（SDK 变体）"),
    (["libDexHelper.so", "libDexHelper-x86.so"], "梆梆加固（Bangcle 新版）"),
    (["libsecexe.so", "libsecmain.so"], "梆梆加固（Bangcle 旧版）"),
    (["libSecShell.so", "libSecShell-x86.so"], "梆梆加固（SecShell）"),
    (["libexec.so", "libexecmain.so", "ijiami"], "爱加密"),
    (["ijiami.ajm", "ijiami3.ajm"], "爱加密（新版）"),
    (["libtosprotection.so", "libtup.so", "libtprt.so",
      "libshellx.so", "libshellx-super.2019.so"], "腾讯御安全/乐固"),
    (["libbaiduprotect.so"], "百度加固"),
    (["libx3g.so"], "顶象"),
    (["libsgmain.so", "libsgsecuritybody.so", "libzuma.so", "libmobisec.so"], "阿里聚安全"),
    (["libsecshell.so", "libegis.so", "libtongfudun.so"], "通付盾"),
    (["libepic.so", "libepic_arm.so", "libnesec.so"], "网易易盾"),
    (["libnfix.so", "libddog.so", "libchaosvmp.so"], "娜迦 Nagain（VMP，硬骨头）"),
    (["libkwscmm.so", "libkwsgmain.so"], "几维安全"),
    (["libnqshield.so"], "网秦"),
    (["libkiroro.so"], "Kiro"),
    (["libapssec.so"], "AppSealing"),
    (["libDexProtector.so"], "DexProtector（商业壳）"),
]
# 资源文件型特征：没有匹配上 so 时靠它认壳
# 用子串匹配而不是精确比对 —— assets 里的壳资源常带版本号或放在子目录里，
# 精确比对会漏（真实坑：360 的企业版样本里是 assets/xxx/libjgcxi.so 这种路径）
ASSET_RULES = [
    ([".appkey"], "360加固（assets 下 .appkey 授权文件）"),
    (["libjiagu"], "360加固（壳资源藏在 assets）"),
    (["ijiami.dat"], "爱加密（assets 下指纹文件）"),
    (["bangcleplugin", "bangcleclasses.jar"], "梆梆加固（assets 特征）"),
    (["baiduprotect"], "百度加固（assets 特征）"),
    (["appsealing"], "AppSealing（assets 特征）"),
    (["libshell.so", "libshellx.so"], "腾讯乐固（壳 so 藏在 assets）"),
]
matched = []
all_so = sorted(set(os.path.basename(n) for n in names if n.lower().endswith(".so")))
res["全包so"] = all_so[:40]
if len(all_so) > len(res["libs"]):
    res["提醒"] = ("有 %d 个 .so 不在 lib/ 下（共 %d 个）——壳很可能把 so 藏在了 assets/ 或包根，"
                   "只扫 lib/ 会漏判" % (len(all_so) - len(res["libs"]), len(all_so)))
for sigs, name in SHELL_RULES:
    hit = [s for s in sigs if s in all_so]
    if hit:
        where = "lib/" if all(s in set(res["libs"]) for s in hit) else "非lib目录(assets/包根)"
        matched.append({"壳": name, "命中": hit, "位置": where})
asset_paths = [n for n in names if n.startswith("assets/")]
asset_base = set(res["assets"])
for sigs, name in ASSET_RULES:
    hit = [s for s in sigs if (s in asset_base) or any(s in p for p in asset_paths)]
    if hit and not any(m["壳"] == name for m in matched):
        where = [p for p in asset_paths if hit[0] in p][:2]
        matched.append({"壳": name, "命中": hit, "位置": "assets：" + (where[0] if where else hit[0])})
res["shell_matched"] = matched

is360 = False
if stub and (stub.startswith("com.stub") or "qihoo" in stub or "frezrik" in stub):
    is360 = True
if any(m["壳"].startswith("360") for m in matched):
    is360 = True
res["is_360"] = is360

# 代次判定 —— 这是整条流程的分水岭，决定「走云端」还是「必须主动调用」
# 判据优先级（高 → 低）：
#   ① 企业/VIP 硬特征
#   ② ART 层壳本体 libjiagu_art.so / jiagu.art / jiagu_res.zip —— V8 才有，老版没有
#   ③ 桩类 com.frezrik / com.qihoo.util —— V5 老壳
#   ④ 桩类 com.stub / com.byazt —— **跨代**，2019 年就在用。必须叠 dex 时间：
#      早于 QIHOO_V8_ERA（2024-01-01）→ 老版；否则新版
#   ⑤ 只有 libjiagu*.so 没桩类 → 老版
# 第 ④ 条是 2026-10-24 真机测出来的：RedteaGO（2020-07-17，桩类 com.stub.StubApp）
# 以前被判「新版」，工具说新版、AI 按年代说老版，同一份报告里两个说法。
gen = "未知"
gen_why = ""
if is360:
    _stub = stub or ""
    _sos = set(os.path.basename(n) for n in names if n.lower().endswith(".so"))

    def _has_any_suffix(*sufs):
        return any(n.endswith(sufs) for n in names)

    if (_sos & {"libjiagu_vip.so", "libjiagu_vip_a64.so", "libjgcxi.so", "libjgcxi_a64.so",
                "libvmp_360.so", "libjiagu_enterprise.so"}) \
            or _has_any_suffix("vmp_config.bin", "jiagu_enterprise.dat"):
        gen = "企业版"
        gen_why = "企业/VIP 版专属 so 或资源"
    elif "libjiagu_art.so" in _sos or _has_any_suffix("jiagu.art", "jiagu_res.zip"):
        gen = "新版"
        gen_why = "ART 层壳本体（libjiagu_art.so / jiagu.art / jiagu_res.zip），老版没有"
    elif _stub.startswith("com.frezrik") or _stub.startswith("com.qihoo.util"):
        gen = "老版"
        gen_why = "V5 老壳桩类 " + _stub
    elif _stub.startswith("com.stub") or _stub.startswith("com.byazt"):
        if _sane and min(_sane) < QIHOO_V8_ERA:
            gen = "老版"
            gen_why = ("桩类 %s 跨代不可靠，叠 dex 时间 %s（早于 2024-01-01 的 V8 分界）→ 老版"
                       % (_stub, _fmt_day(min(_sane))))
        elif _sane:
            gen = "新版"
            gen_why = ("桩类 %s + dex 时间 %s（晚于 2024-01-01）→ 新版"
                       % (_stub, _fmt_day(min(_sane))))
        else:
            gen = "新版"
            gen_why = "桩类 %s（dex 时间被抹，无法用时间校正，按现行引擎判新版）" % _stub
    elif any(s.startswith("libjiagu") for s in _sos):
        gen = "老版"
        gen_why = "只有 libjiagu*.so，无可识别的桩类"

    # 0701：2026-07-01 起 360 加固保停止免费加固，之后的产物走付费引擎。
    # 只有时间窗可信且全部越过分界线才加这个后缀 —— 猜错比不写更糟。
    _paid = bool(res.get("dex时间", {}).get("_全在0701后"))
    # 代次依据必须自带两件事：① 时间样本覆盖了多少 dex ② 有没有叠 0701。
    # 少一样，AI 就会把「基于 1/3 个 dex 的推断」当全量结论，或者把「新版0701」讲成「新版」。
    if gen_why and _dex_cover_partial and _sane:
        gen_why += "（时间样本 %s，其余 dex 时间不可用）" % _dex_cover
    if gen != "未知" and _paid:
        gen += "0701"
        if gen_why:
            gen_why += "；再叠 0701 付费时代"
    res["付费时代"] = _paid
    res["360显示名"] = ("360加固（%s）" % gen) if gen != "未知" else "360加固（代次未定）"
    if gen_why:
        res["代次依据"] = gen_why
res["360代次"] = gen

# 难度分档
if is360:
    res["难度"] = "高"
    if gen.startswith("老版"):
        head = ("① 【%s】→ 优先走【云端脱壳】：云端脱壳站能直接吐出完整方法体，"
                "比本地 FunDex2/frida 稳得多，本地跑反而常拿到壳容器" % gen)
    elif gen.startswith("新版") or gen.startswith("企业版"):
        head = ("① 【%s】→ 是真抽取壳，云端多半也拿不全。"
                "走本地 lu_dump，且必须配支持主动调用的模块（FunDex2 / FART / frida_fart）" % gen)
    else:
        head = "① 若目的只是改界面/文案/去广告位：不要脱壳，CorePatch 过签后直接改 resources.arsc 与 XML，成功率接近 100%"
    res["路线"] = [
        head,
        "② 目的若只是改资源/文案/去广告位，就地 CorePatch 过签改 resources.arsc 与 XML 就行，成功率接近 100%",
        "③ 真要脱修：本地脱壳必须走「主动调用」（FunDex2 / FART / frida_fart），否则 dex 是空的；"
        "并且别忘防曲轴 —— 主动加载要跑在后台线程，跑主线程会卡死 Application.onCreate（启动白屏）",
        "④ 拿到 dex 后：lu_dump 用 rounds 多跑几轮直到「指纹稳定」→ lu_dump_check 体检 → "
        "lu_scan 看抽取率 → lu_fill_oncreate 邪修回填 → lu_stub_clean 清特征 → lu_manifest_fix 回填入口 → 重打包",
        "⑤ 仍有崩溃：抓日志交给 AI 循环补方法体（配 logfox_toolkit）"
    ]
elif matched:
    res["难度"] = "中"
    res["路线"] = ["非 360 壳，常规脱修流程即可：解包 → 回填 → 清特征 → 入口回填 → 重打包签名"]
else:
    res["难度"] = "低"
    res["路线"] = ["未发现明显加固特征，可直接修改"]
res["提示"] = "本预判只看 APK 静态特征。360 真正的难度取决于「方法体是否被抽走」，请用 lu_scan 扫解包目录确认抽取率。"
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, q(apk), 120000);
    }

    // ---------- 本地脱壳调度 ----------
    function lu_dump(params) {
        var pkg = params.package_name;
        if (!pkg) throw new Error('缺少 package_name');
        var outDir = params.output_dir || (DEFAULT_OUT + '/' + pkg + '/dex');
        var wait = params.wait_seconds || 45;

        // 已知脱壳模块的常见产物目录
        var CANDIDATES = [
            { name: 'zygisk-dump-dex', cmd: 'ls -R /data/local/tmp 2>/dev/null | grep -i -m5 dex || echo NONE' },
            { name: 'FunDex2(LSPosed 类抽取)', cmd: 'ls /data/data/' + pkg + '/ 2>/dev/null || echo NONE' },
            { name: 'DexInjector', cmd: 'ls /storage/emulated/0/dexInjector/ 2>/dev/null || echo NONE' },
            { name: 'GGdex/GG修改器', cmd: 'ls /sdcard/dexFile/' + pkg + '/ 2>/dev/null || echo NONE' },
            { name: 'BlackDex', cmd: 'ls /sdcard/Android/data/top.niunaijun.blackdex*/ 2>/dev/null || echo NONE' },
            { name: 'APK自动脱壳器', cmd: 'ls /data/Plugin/' + pkg + '/data/' + pkg + '/files/cookie_dump/ 2>/dev/null || echo NONE' }
        ];

        var found = [];
        var chain = exec('mkdir -p ' + q(outDir), 15000);
        CANDIDATES.forEach(function (c) {
            chain = chain.then(function () {
                return execAndroid(c.cmd, 20000).then(function (r) {
                    var out = (r.output || '').trim();
                    if (out && out !== 'NONE') found.push({ 模块: c.name, 产物: out.split('\n').slice(0, 6) });
                });
            });
        });

        // ---- 多轮 dump + 轮间指纹比对 ----
        // 思路来自开源工具 mHook（xiamiya/mHook）的 redumpFilled()：
        // 函数抽取壳是「边跑边补」的，第一轮 dump 到的 dex 很可能还没补完。
        // 判据很简单 —— 同一个 dex 连续两轮 size + SHA-1 都不再变化，才算补完。
        var rounds = Math.max(1, Math.min(5, parseInt(params.rounds || '2', 10)));

        function oneRound(idx) {
            var rdir = outDir + '/round' + idx;
            // 第 2 轮起先 force-stop 再拉起：逼 App 重新走一遍 Application.onCreate，
            // 让壳把另一批类的方法体填回来。不重启的话同一批类已经初始化过，不会再触发补码。
            var kick = (idx === 1)
                ? 'monkey -p ' + q(pkg) + ' -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; echo done'
                : 'am force-stop ' + q(pkg) + ' >/dev/null 2>&1; sleep 1; '
                  + 'monkey -p ' + q(pkg) + ' -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; echo done';
            return exec('mkdir -p ' + q(rdir), 15000)
                .then(function () { return execAndroid(kick, 40000); })
                .then(function () {
                    return new Promise(function (resolve) { setTimeout(resolve, Math.min(wait, 90) * 1000); });
                })
                .then(function () {
                    // 收集所有能找到的 dex
                    return execAndroid(
                        'for d in /data/local/tmp /data/data/' + pkg + ' /storage/emulated/0/dexInjector /sdcard/dexFile/' + pkg +
                        ' /data/Plugin/' + pkg + '/data/' + pkg + '/files/cookie_dump; do ' +
                        '  find $d -maxdepth 3 -name "*.dex" 2>/dev/null; done | head -200 || echo NONE', 60000
                    );
                })
                .then(function (r) {
                    var list = (r.output || '').split('\n').map(function (x) { return x.trim(); })
                                 .filter(function (x) { return x && x.indexOf('NONE') < 0; });
                    var copied = [];
                    var cp = Promise.resolve();
                    list.forEach(function (p, i) {
                        cp = cp.then(function () {
                            var dst = rdir + '/dump_' + i + '.dex';
                            return execAndroid('cp ' + q(p) + ' ' + q(dst) + ' 2>/dev/null && echo OK', 30000)
                                .then(function (rr) {
                                    if ((rr.output || '').indexOf('OK') >= 0) copied.push(dst);
                                });
                        });
                    });
                    return cp;
                });
        }

        function loopRound(i) {
            if (i > rounds) return Promise.resolve();
            return oneRound(i).then(function () { return loopRound(i + 1); });
        }

        return chain
            .then(function () { return loopRound(1); })
            .then(function () {
                var last = outDir + '/round' + rounds;
                var prev = rounds > 1 ? (outDir + '/round' + (rounds - 1)) : null;
                var diffP = prev
                    ? runPy(PY_DC, 'basecheck ' + q(last) + ' ' + q(prev), 300000)
                    : Promise.resolve(null);
                return diffP.then(function (bc) {
                    return execAndroid('find ' + q(last) + ' -name "*.dex" 2>/dev/null | head -200', 30000)
                        .then(function (rr) {
                            var copied = (rr.output || '').split('\n').map(function (x) { return x.trim(); })
                                           .filter(function (x) { return x; });
                            var out = {
                                包名: pkg,
                                输出目录: outDir,
                                本轮目录: last,
                                轮次: rounds,
                                检测到模块: found,
                                收集到dex: copied,
                                数量: copied.length,
                                // 关键提示
                                警告: '360（数字）加固是函数抽取壳：方法体只有在该方法即将执行时才被壳还原进内存。'
                                    + '单纯启动 App 再 dump，拿到的 dex 里方法体大概率是空的。',
                                坑提醒: '主动加载（遍历 DexFile 逐个触发类初始化）绝不能跑在目标 App 的主线程：'
                                    + '类初始化要等 Application.onCreate 返回，而主线程此刻正卡在 hook 里等结果，'
                                    + '两边互等 = 死锁，表现是 App 启动白屏、Change-Id 也不动。'
                                    + '确认你的脱壳模块是在后台线程做主动调用的。'
                            };
                            if (bc && bc.ok) {
                                out['轮间稳定性'] = bc;
                                out['下一步'] = (bc['仍变化数'] > 0)
                                    ? ('还有 ' + bc['仍变化数'] + ' 个 dex 在变化 —— 壳还在补方法体，'
                                       + '把 rounds 往上加到 4 或 5 再跑一次，别拿半成品去修')
                                    : ('两轮指纹完全一致 → 补码完成。跑 lu_dump_check 确认不是壳容器，'
                                       + '然后回填 / 解包 / 修 Manifest / 重打包');
                            } else if (bc) {
                                out['轮间稳定性'] = bc;
                                out['下一步'] = '轮间比对失败（看上面的错误），但产物已经落盘，按单轮流程往下走即可';
                            } else if (copied.length === 0) {
                                out['下一步'] = '没拿到 dex。请先装并启用支持主动调用的脱壳模块：'
                                    + 'FunDex2（LSPosed，推荐，明确支持类抽取）或 ZygiskFrida + frida_fart。';
                            } else {
                                out['下一步'] = '拿到 ' + copied.length + ' 个 dex。'
                                    + '立刻跑 lu_dump_check 体检（先看是不是壳容器）或 lu_dex_fix 修头。'
                                    + '建议下次带 rounds=2 做一次轮间比对，确认方法体真的补完了。';
                            }
                            return out;
                        });
                });
            });
    }

    // ---------- Layout Inspect 对接（LSPosed 悬浮窗脱壳） ----------
    // Layout Inspect (com.flass.layoutinspect) 是 LSPosed 模块，内置 Dump Dex 能力，
    // 明确支持愛加蜜/愛加蜜企业/dpt-shell 脱壳，并可在高级设置配【主动加载类】对抗抽取壳 nop。
    // 因需在悬浮窗里手动点 Dump，本工具负责：检测安装 -> 输出操作指引 -> 等待 -> 收集 dex 产物。
    function lu_dump_li(params) {
        var pkg = params.package_name;
        if (!pkg) throw new Error('缺少 package_name');
        var outDir = params.output_dir || '/sdcard/Download/Operit/unpack_out/' + pkg + '/dex_li';
        var wait = parseInt(params.wait_seconds || '60', 10);
        var hasLI = false;
        return execAndroid('pm list packages | grep -i com.flass.layoutinspect || echo NONE', 20000)
            .then(function (r) {
                var out = (r.output || '').trim();
                hasLI = (out && out !== 'NONE');
                var tip = [];
                if (hasLI) {
                    tip.push('✅ 检测到 Layout Inspect 已安装（' + out.split('\n')[0].trim() + '）。');
                } else {
                    tip.push('❌ 未检测到 com.flass.layoutinspect。请先安装并启用该 LSPosed 模块（作用域勾选目标 App），整机重启后重试。');
                }
                tip.push('');
                tip.push('【请你在 Layout Inspect 悬浮窗里操作】');
                tip.push('1. 打开目标 App ' + pkg + '，让壳先跑起来（Application.onCreate 触发壳初始化）。');
                tip.push('2. 打开 Layout Inspect 悬浮窗 -> 找到 Dump Dex 入口。');
                tip.push('3. 若目标是愛加蜜/愛加蜜企业/dpt-shell，务必在「高级设置 -> 主动加载类」里勾上，');
                tip.push('   并建议开启 DexFileSize（对抗抹 header）。');
                tip.push('4. 点 Dump。Dex 会 dump 到它的输出目录（通常在 /sdcard/Android/data/com.flass.layoutinspect/ 下）。');
                tip.push('5. 点完告诉我，本工具会自动收集产物到：' + outDir);
                return { 检测: hasLI ? '已安装' : '未安装', 操作指引: tip.join('\n') };
            })
            .then(function (guide) {
                if (!hasLI) return guide;
                return exec('mkdir -p ' + q(outDir), 15000)
                    .then(function () {
                        return new Promise(function (resolve) { setTimeout(resolve, Math.min(wait, 120) * 1000); });
                    })
                    .then(function () {
                        // 收集 Layout Inspect 的产物 dex（多个常见路径全扫一遍）
                        return execAndroid(
                            'for d in /sdcard/Android/data/com.flass.layoutinspect /data/data/com.flass.layoutinspect '
                            + '/sdcard/Download /sdcard/DexDump /sdcard/Android/data/' + pkg + ' /data/data/' + pkg + '; do '
                            + '  find $d -maxdepth 5 -name "*.dex" 2>/dev/null; done | head -200 || echo NONE', 60000
                        );
                    })
                    .then(function (r) {
                        var list = (r.output || '').split('\n').map(function (x) { return x.trim(); })
                                     .filter(function (x) { return x && x.indexOf('NONE') < 0; });
                        var copied = [];
                        var cp = Promise.resolve();
                        list.forEach(function (p, i) {
                            cp = cp.then(function () {
                                var dst = outDir + '/li_dump_' + i + '.dex';
                                return execAndroid('cp ' + q(p) + ' ' + q(dst) + ' 2>/dev/null && echo OK', 30000)
                                    .then(function (rr) {
                                        if ((rr.output || '').indexOf('OK') >= 0) copied.push(dst);
                                    });
                            });
                        });
                        return cp.then(function () {
                            guide.收集结果 = copied.length ? ('收集到 ' + copied.length + ' 个 dex 于 ' + outDir) : '暂未收集到 dex（可能还没点 Dump 或输出路径不同）';
                            guide['下一步'] = copied.length
                                ? '立刻跑 lu_dump_check(dex_path=' + outDir + ', apk_path=<原APK>) 体检，确认是真 dex 还是壳容器，别急着重打包。'
                                : '请回到悬浮窗点 Dump，点完后再跑一次本工具收集；或告诉我 Layout Inspect 的实际输出路径。';
                            return guide;
                        });
                    });
            });
    }
    // ---------- 解包 ----------
    function lu_decode(params) {
        var apk = params.apk_path, out = params.output_dir;
        if (!apk || !out) throw new Error('缺少 apk_path 或 output_dir');
        var force = params.force === 'true' || params.force === true;
        var cmd = 'apktool' + (force ? ' -f' : '') + ' d ' + q(apk) + ' -o ' + q(out);
        return execRaw('which apktool || command -v apktool || echo NONE', 15000).then(function (r) {
            if ((r.output || '').indexOf('NONE') >= 0) {
                return {
                    success: false,
                    原因: '设备上没找到 apktool',
                    替代方案: [
                        '方案1：用 Operit 官方 apk_reverse 工具包的 apk_reverse_decode 解包，解出的目录直接喂给本包的 lu_scan / lu_fill_oncreate / lu_stub_clean / lu_manifest_fix',
                        '方案2：用 MT 管理器解包，把目录路径传给本包',
                        '方案3：Termux 里 pkg install apktool'
                    ],
                    说明: '本包的修复类工具只依赖解包目录，不关心是谁解出来的'
                };
            }
            return exec(cmd, 600000).then(function (rr) {
                return { 解包目录: out, 输出: (rr.output || '').substring(0, 500) };
            });
        });
    }

    // ---------- 空方法体扫描 ----------
    function lu_scan(params) {
        var dir = params.decoded_dir;
        if (!dir) throw new Error('缺少 decoded_dir');
        var maxDetails = parseInt(params.max_details || '60', 10);

        var py = String.raw`
import sys, os, re, json
root = sys.argv[1]
maxd = int(sys.argv[2]) if len(sys.argv) > 2 else 60

METHOD_RE = re.compile(r'^\s*\.method\b(.*)$')
END_RE    = re.compile(r'^\s*\.end\s+method\s*$')
SUPER_RE  = re.compile(r'^\s*\.super\s+(L[^;]+;)\s*$')
DIRECTIVE_RE = re.compile(r'^\s*\.(locals|registers|prologue|line|param|annotation|end|catch|catchall|packed-switch|sparse-switch|array-data|restart|source|implements|class|super)')
LABEL_RE  = re.compile(r'^\s*:')
SHELL_PATS = ['Lcom/stub/StubApp', 'Lcom/qihoo/', 'Lcom/frezrik/', 'Lcom/secneo/', 'Lcom/tencent/Stub', 'Ldalvik/system/DexFile']
TRIVIAL_RE = re.compile(r'^\s*(return-void|return\b|return-object|return-wide|nop|const/4|const/16|const|const-wide|move-result|move-result-object|move-result-wide)\b')

total = 0
empty = []
shell_only = []
trivial = []
oncreate_empty = []

for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.smali'):
            continue
        p = os.path.join(dirpath, fn)
        try:
            with open(p, 'r', encoding='utf-8', errors='replace') as f:
                lines = f.read().splitlines()
        except Exception:
            continue
        i = 0
        n = len(lines)
        while i < n:
            m = METHOD_RE.match(lines[i])
            if not m:
                i += 1
                continue
            header = m.group(1).strip()
            j = i + 1
            while j < n and not END_RE.match(lines[j]):
                j += 1
            body = lines[i+1:j]
            total += 1
            insns = []
            for b in body:
                s = b.strip()
                if not s:
                    continue
                if DIRECTIVE_RE.match(b) or LABEL_RE.match(b):
                    continue
                insns.append(b)
            # 分类：完全空 / 仅壳调用 / 平凡
            # 关键：360 抽空的方法体通常是「StubApp 调用 + return-void」混合，
            #       不能简单要求「所有指令都含壳特征」，必须允许其余指令是平凡的
            shell_hits = [x for x in insns if any(pat in x for pat in SHELL_PATS)]
            other_ins  = [x for x in insns if not any(pat in x for pat in SHELL_PATS)]
            cat = None
            if len(insns) == 0:
                cat = '完全空'
            elif shell_hits and all(TRIVIAL_RE.match(x) for x in other_ins):
                cat = '仅壳调用'
            elif all(TRIVIAL_RE.match(x) for x in insns):
                cat = '平凡'
            if cat is None:
                i = j + 1
                continue
            if cat == '完全空':
                if len(empty) < maxd:
                    empty.append({'file': p, 'method': header})
            elif cat == '仅壳调用':
                if len(shell_only) < maxd:
                    shell_only.append({'file': p, 'method': header})
            else:
                if len(trivial) < maxd:
                    trivial.append({'file': p, 'method': header})
            if 'onCreate(' in header:
                if len(oncreate_empty) < maxd:
                    oncreate_empty.append({'file': p, 'method': header, '类型': cat})
            i = j + 1

susp = len(empty) + len(shell_only) + len(trivial)
rate = round(susp * 100.0 / total, 2) if total else 0.0

if rate >= 15:
    verdict = '高抽取率 —— 典型的函数抽取壳（360/数字加固特征）。必须做方法体回填，否则装上去必崩。'
elif rate >= 3:
    verdict = '中等抽取 —— 部分方法体缺失。可用邪修大法回填 onCreate，其余视崩溃情况补。'
else:
    verdict = '低抽取率 —— 方法体基本完整，常规流程（清特征 + 入口回填 + 重打包）即可。'

res = {
    '解包目录': root,
    '方法总数': total,
    '完全空方法体': len(empty),
    '仅含壳调用': len(shell_only),
    '平凡方法体': len(trivial),
    '可疑合计': susp,
    '抽取率百分比': rate,
    '判定': verdict,
    'onCreate被抽空': oncreate_empty,
    '空方法体样例': empty[:20],
    '仅壳调用样例': shell_only[:20],
    '下一步': '抽取率高 → lu_fill_oncreate（邪修大法）→ lu_stub_clean → lu_manifest_fix → 重打包；仍有崩溃 → 抓日志交给 AI 循环补方法体'
}
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, q(dir) + ' ' + maxDetails, 600000);
    }

    // ---------- 邪修大法：onCreate 回填 ----------
    function lu_fill_oncreate(params) {
        var dir = params.decoded_dir;
        if (!dir) throw new Error('缺少 decoded_dir');
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        var only = params.only_class || '';

        var py = String.raw`
import sys, os, re, json
root = sys.argv[1]
dry = sys.argv[2] == '1'
only = sys.argv[3] if len(sys.argv) > 3 else ''

METHOD_RE = re.compile(r'^\s*\.method\s+(.*)$')
END_RE    = re.compile(r'^\s*\.end\s+method\s*$')
SUPER_RE  = re.compile(r'^\s*\.super\s+(L[^;]+;)\s*$')
DIRECTIVE_RE = re.compile(r'^\s*\.(locals|registers|prologue|line|param|annotation|end|catch|catchall|packed-switch|sparse-switch|array-data)')
LABEL_RE  = re.compile(r'^\s*:')
SHELL_PATS = ['Lcom/stub/StubApp', 'Lcom/qihoo/', 'Lcom/frezrik/', 'Lcom/secneo/']
TRIVIAL_RE = re.compile(r'^\s*(return-void|return\b|return-object|return-wide|nop|const/4|const/16|const|move-result|move-result-object|move-result-wide)\b')

TARGETS = [
    ('onCreate(Landroid/os/Bundle;)V', 1),
    ('onCreate(Landroid/os/Bundle;Landroid/os/PersistableBundle;)V', 2),
]

def is_bad(insns):
    # 完全空
    if len(insns) == 0:
        return True
    shell_hits = [x for x in insns if any(pat in x for pat in SHELL_PATS)]
    other_ins  = [x for x in insns if not any(pat in x for pat in SHELL_PATS)]
    # 关键：360 抽空的 onCreate 典型形态是
    #   invoke-static {p0}, Lcom/stub/StubApp;->xxx(...)V
    #   return-void
    # 即「壳调用 + 平凡指令」混合。若要求所有指令都含壳特征就会漏判，
    # 所以判定为：有壳调用，且其余指令全是平凡的 → 方法体已被抽走
    if shell_hits and all(TRIVIAL_RE.match(x) for x in other_ins):
        return True
    # 无任何实质指令（只有 return / nop / const / move-result）
    if all(TRIVIAL_RE.match(x) for x in insns):
        return True
    return False

changed = []
scanned_files = 0
scanned_methods = 0

for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.smali'):
            continue
        p = os.path.join(dirpath, fn)
        if only and only not in p:
            continue
        try:
            with open(p, 'r', encoding='utf-8', errors='replace') as f:
                lines = f.read().splitlines()
        except Exception:
            continue
        scanned_files += 1

        supercls = None
        for l in lines:
            m = SUPER_RE.match(l)
            if m:
                supercls = m.group(1)
                break

        if not supercls:
            continue

        i = 0
        n = len(lines)
        modified = False
        while i < n:
            m = METHOD_RE.match(lines[i])
            if not m:
                i += 1
                continue
            header = m.group(1).strip()
            j = i + 1
            while j < n and not END_RE.match(lines[j]):
                j += 1
            scanned_methods += 1

            for sig, argc in TARGETS:
                if header.endswith(sig) and (' onCreate(' in header or header.startswith('onCreate(')):
                    body = lines[i+1:j]
                    insns = []
                    for b in body:
                        s = b.strip()
                        if not s or DIRECTIVE_RE.match(b) or LABEL_RE.match(b):
                            continue
                        insns.append(b)
                    if not is_bad(insns):
                        break
                    # 构造标准空实现
                    if argc == 1:
                        invoke = '    invoke-super {p0, p1}, ' + supercls + '->onCreate(Landroid/os/Bundle;)V'
                    else:
                        invoke = '    invoke-super {p0, p1, p2}, ' + supercls + '->onCreate(Landroid/os/Bundle;Landroid/os/PersistableBundle;)V'
                    newbody = ['    .locals 1', invoke, '    return-void']
                    lines[i+1:j] = newbody
                    changed.append({'file': p, 'method': header, 'super': supercls})
                    modified = True
                    # 长度变了，重新定位
                    n = len(lines)
                    j = i + 1 + len(newbody)
                    break
            i = j + 1

        if modified and not dry:
            try:
                with open(p, 'w', encoding='utf-8') as f:
                    f.write('\n'.join(lines) + '\n')
            except Exception as e:
                changed.append({'file': p, 'error': str(e)})

res = {
    '解包目录': root,
    '模式': ('只报告(dry_run)' if dry else '已写入'),
    '扫描文件数': scanned_files,
    '扫描方法数': scanned_methods,
    '回填数量': len(changed),
    '明细': changed[:80],
    '说明': '把被抽空的 onCreate 替换为「invoke-super + return-void」标准空实现，super 类取自该 smali 的 .super 声明，'
            '因此对 Activity / AppCompatActivity / 自定义父类都成立。论坛实测对 360 成功率约 30%。',
    '下一步': ('确认无误后把 dry_run 设为 false 实际写入' if dry else '接着跑 lu_stub_clean 清特征、lu_manifest_fix 回填入口')
}
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, q(dir) + ' ' + (dry ? '1' : '0') + (only ? ' ' + q(only) : ''), 600000);
    }

    // ---------- 启动页接回补丁：父类只做半套 ----------
    // 背景（2026-10-27 幕布 com.mubu.app 实测）：
    //   lu_fill_oncreate 把被抽空的 onCreate 换成「invoke-super + return-void」，
    //   前提是「父类那份 onCreate 够用」。但幕布不行 —— 父类 BaseSplashActivity.onCreate
    //   只有 37 条指令、只设横竖屏和状态栏颜色，一次业务方法调用都没有，它就是不跳转。
    //   真正的跳转在被抽走的那段里，但同类里的私有入口方法（checkAndShowMubuTerm /
    //   continueMubuJump / toMubuJump）**全都还在**，只是没人调。
    //   所以这里在 onCreate 里补一句调用那个还活着的入口方法，把流程接回去。
    //
    //   判定「半套」的启发式：祖先 onCreate 里既没有指向自身类族的方法调用，
    //   也没有任何非框架类的方法调用 —— 即没有任何虚分派钩子。
    function lu_splash_fix(params) {
        var dir = params.decoded_dir;
        if (!dir) throw new Error('缺少 decoded_dir');
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        var only = params.only_class || '';
        var entry = params.entry || '';

        var py = String.raw`
import sys, os, re, json
root = sys.argv[1]
dry = sys.argv[2] == '1'
only = sys.argv[3] if len(sys.argv) > 3 else ''
entry = sys.argv[4] if len(sys.argv) > 4 else ''

METHOD_RE = re.compile(r'^\s*\.method\s+(.*)$')
END_RE    = re.compile(r'^\s*\.end\s+method\s*$')
SUPER_RE  = re.compile(r'^\s*\.super\s+(L[^;]+;)\s*$')
CLASS_RE  = re.compile(r'^\s*\.class\s+(.*)$')
DIRECTIVE_RE = re.compile(r'^\s*\.(locals|registers|prologue|line|param|annotation|end|catch|catchall|packed-switch|sparse-switch|array-data)')
LABEL_RE  = re.compile(r'^\s*:')
SHELL_PATS = ['Lcom/stub/StubApp', 'Lcom/qihoo/', 'Lcom/frezrik/', 'Lcom/secneo/']
TRIVIAL_RE = re.compile(r'^\s*(return-void|return\b|return-object|return-wide|nop|const/4|const/16|const|move-result|move-result-object|move-result-wide)\b')
FRAMEWORK_RE = re.compile(r'->L(android/|androidx/|java/|javax/|kotlin/|kotlinx/|dalvik/|org/|com/google/)')
CALL_RE = re.compile(r'^\s*invoke-(virtual|direct|interface|super|static)(?:/range)?\s+\{[^}]*\},\s*(.+)$')

# 这些是标准生命周期/工具名，不能当「入口」
LIFECYCLE = set(['onCreate','onStart','onResume','onPause','onStop','onDestroy','onRestart',
                 'onActivityResult','onRequestPermissionsResult','onBackPressed','onNewIntent',
                 'onSaveInstanceState','onRestoreInstanceState','onConfigurationChanged',
                 'onWindowFocusChanged','onAttachedToWindow','onDetachedFromWindow','onDestroyView',
                 'onCreateView','setContentView','findViewById','finish','dispatchTouchEvent'])

# 入口方法名的加权词
KEYWORDS = ['jump','Jump','toMain','toLogin','toHome','toWelcome','checkAndShow','check',
            'continue','goMain','enter','welcome','Welcome','splash','Splash','next','launch',
            'Launch','start','init','toRN','show','term','Term']

def insns_of(lines, i, j):
    out = []
    for b in lines[i+1:j]:
        s = b.strip()
        if not s: continue
        if DIRECTIVE_RE.match(b): continue
        if LABEL_RE.match(b): continue
        out.append(b)
    return out

def split_method(header):
    tok = header.split()
    if not tok: return '', '', ''
    last = tok[-1]
    mods = ' '.join(tok[:-1])
    if '(' in last:
        k = last.index('(')
        return mods, last[:k], last[k:]
    return mods, last, ''

def methods_of(lines):
    out = []
    n = len(lines)
    i = 0
    while i < n:
        m = METHOD_RE.match(lines[i])
        if not m:
            i += 1
            continue
        j = i + 1
        while j < n and not END_RE.match(lines[j]):
            j += 1
        out.append((i, j, m.group(1).strip()))
        i = j + 1
    return out

def is_orphan(insns):
    """判断这个 onCreate 是不是「没有自己的业务代码」"""
    if len(insns) == 0:
        return True
    shell_hits = [x for x in insns if any(p in x for p in SHELL_PATS)]
    other = [x for x in insns if not any(p in x for p in SHELL_PATS)]
    if shell_hits and all(TRIVIAL_RE.match(x) for x in other):
        return True
    if all(TRIVIAL_RE.match(x) for x in insns):
        return True
    # lu_fill_oncreate 已经回填过的标准空实现：只剩一句 invoke-super onCreate
    real = [x for x in insns if not TRIVIAL_RE.match(x)]
    if len(real) == 1 and 'invoke-super' in real[0] and '->onCreate(' in real[0]:
        return True
    return False

# ---------- 建类索引 ----------
files = []
for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if fn.endswith('.smali'):
            files.append(os.path.join(dirpath, fn))

cidx = {}
for p in files:
    try:
        with open(p, 'r', encoding='utf-8', errors='replace') as f:
            L = f.read().splitlines()
    except Exception:
        continue
    desc = None
    for l in L:
        if l.strip().startswith('.method'):
            break
        m = CLASS_RE.match(l)
        if m:
            tok = m.group(1).split()
            if tok:
                desc = tok[-1]
                break
    if desc:
        cidx[desc] = p

_cache = {}
def load(desc):
    if desc in _cache:
        return _cache[desc]
    p = cidx.get(desc)
    if not p:
        _cache[desc] = None
        return None
    try:
        with open(p, 'r', encoding='utf-8', errors='replace') as f:
            L = f.read().splitlines()
    except Exception:
        _cache[desc] = None
        return None
    _cache[desc] = (p, L)
    return _cache[desc]

def super_of(L):
    for l in L:
        m = SUPER_RE.match(l)
        if m:
            return m.group(1)
    return None

# ---------- 主扫描 ----------
targets = []
for desc in sorted(cidx.keys()):
    p = cidx[desc]
    if only and only not in p:
        continue
    got = load(desc)
    if not got:
        continue
    fp, L = got
    sup = super_of(L)
    if not sup:
        continue

    for (i, j, header) in methods_of(L):
        mods, name, proto = split_method(header)
        if name != 'onCreate' or proto != '(Landroid/os/Bundle;)V':
            continue
        insns = insns_of(L, i, j)
        if not is_orphan(insns):
            continue

        # 沿父类链找第一个有 onCreate 的祖先
        chain = []
        anc = sup
        seen = set()
        anc_info = None
        while anc and anc not in seen and len(chain) < 8:
            seen.add(anc)
            chain.append(anc)
            g = load(anc)
            if not g:
                break
            aL = g[1]
            hit = None
            for (ai, aj, ah) in methods_of(aL):
                am, an, ap = split_method(ah)
                if an == 'onCreate' and ap == '(Landroid/os/Bundle;)V':
                    hit = (ai, aj, ah)
                    break
            if hit:
                anc_info = (anc, hit)
                break
            anc = super_of(aL)

        if not anc_info:
            targets.append({
                '类': desc, '父类链': chain[:4],
                '判定': '父类链上找不到 onCreate（本工具救不了，得靠云端或手补）',
                '候选入口': []
            })
            continue

        anc_desc, (ai, aj, ah) = anc_info
        aL = load(anc_desc)[1]
        ains = insns_of(aL, ai, aj)

        # 半套判定（关键，别用「父类有没有调业务方法」这么粗的判据）：
        # 幕布的 BaseSplashActivity.onCreate 确实调了 getDefaultStatusBarColor()，
        # 目标是它自己类 —— 光看「有没有业务调用」会误判成「够用」。
        # 真正要问的是：**父类调的那些方法里，有没有一个被子类重写了**。
        #   有  → 继承父类时会虚分派到子类自己的实现（mqjz 的 initView 就是这样），够了
        #   没有 → 继承父类等于什么都不发生，必须接回
        hook_calls = set()
        for x in ains:
            m = CALL_RE.match(x)
            if not m:
                continue
            tgt = m.group(2).strip()
            mm = re.match(r'(L[^;]+;)->([^(]+)(\(.*)$', tgt)
            if not mm:
                continue
            owner, mname, mproto = mm.group(1), mm.group(2), mm.group(3)
            if owner != anc_desc and owner not in chain:
                continue          # 不是调用自己类族里的方法，谈不上虚分派钩子
            hook_calls.add(mname + mproto)

        cdefs = set()
        for (ci, cj, ch) in methods_of(L):
            cm, cn, cp = split_method(ch)
            cdefs.add(cn + cp)
        hooks = sorted([h for h in hook_calls if h in cdefs])

        if hooks:
            # 父类调的方法里有子类重写的 → 虚分派能走到子类实现，继承它就够
            continue

        # 找候选入口：同类里无参、返回 void、有真实方法体、非生命周期
        # 关键加分项：**同类里没有任何地方调用它**。
        # 真正的入口只由那个被抽走的 onCreate 调，所以它在本类里是「没人调」的；
        # 而 continueMubuJump / toMubuJump 这种是被别的私有方法调的，属下一层。
        # 幕布实测：checkAndShowMubuTerm 没人调（+25），continueMubuJump 被 toMubuJump 调（0），
        # 靠这一项就能把正确答案顶到第一。
        called = set()
        for l in L:
            mm = re.search(r'->([A-Za-z0-9_$<>]+)(\([^)]*\)[A-Za-z\[;]+)', l)
            if mm:
                called.add(mm.group(1) + mm.group(2))

        cands = []
        for (ci, cj, ch) in methods_of(L):
            cm, cn, cp = split_method(ch)
            if cn in LIFECYCLE: continue
            if cn.startswith('access$') or cn.startswith('lambda$'): continue
            if 'static' in cm: continue
            if cp != '()V': continue
            cins = insns_of(L, ci, cj)
            if len(cins) < 3: continue
            score = min(len(cins), 80)
            for k in KEYWORDS:
                if k in cn:
                    score += 15
            uncalled = (cn + cp) not in called
            if uncalled:
                score += 25
            cands.append({'方法': cn, '修饰符': cm, '指令数': len(cins),
                          '未被同类调用': uncalled, '分': score})
        cands.sort(key=lambda x: -x['分'])

        targets.append({
            '类': desc,
            '父类': anc_desc,
            '父类onCreate指令数': len(ains),
            '父类调用了这些方法': sorted(hook_calls)[:10],
            '其中子类重写的': hooks,
            '判定': '父类只做半套 —— 它调的方法子类一个都没重写，继承它不会走到子类代码，必须接回入口',
            '候选入口': cands[:6]
        })

# ---------- 写入 ----------
written = []
for t in targets:
    if not t.get('候选入口'):
        continue
    desc = t['类']
    got = load(desc)
    fp, L = got
    sup = super_of(L)
    name = entry if entry else t['候选入口'][0]['方法']
    mods = ''
    for c in t['候选入口']:
        if c['方法'] == name:
            mods = c['修饰符']
    if entry and not mods:
        mods = 'private'
    kind = 'invoke-direct' if ('private' in mods or 'constructor' in mods) else 'invoke-virtual'
    for (i, j, header) in methods_of(L):
        m2, n2, p2 = split_method(header)
        if n2 == 'onCreate' and p2 == '(Landroid/os/Bundle;)V':
            newbody = [
                '    .locals 1',
                '    invoke-super {p0, p1}, ' + sup + '->onCreate(Landroid/os/Bundle;)V',
                '    ' + kind + ' {p0}, ' + desc + '->' + name + '()V',
                '    return-void'
            ]
            L[i+1:j] = newbody
            written.append({'类': desc, '接回的入口方法': name, '调用方式': kind})
            break
    if not dry:
        try:
            with open(fp, 'w', encoding='utf-8') as f:
                f.write('\n'.join(L) + '\n')
        except Exception as e:
            written.append({'类': desc, 'error': str(e)})

res = {
    '解包目录': root,
    '模式': ('只报告(dry_run)' if dry else '已写入'),
    '扫到的 dex 类文件数': len(cidx),
    '需要接回的类数': len(targets),
    '已写入': (0 if dry else len(written)),
    '预演将改动': (len(written) if dry else 0),
    '明细': targets[:40],
    '写入明细': written[:40],
    '原理': '父类 onCreate 里一次业务方法调用都没有（没有虚分派钩子），继承它等于什么都不做。'
            '本工具在子类 onCreate 里补一句调用「同类里还活着的入口方法」，把启动流程接回去。',
    '用法': '先 dry_run=true 看候选入口对不对；不对就用 entry 参数指定正确的方法名，再 dry_run=false 写入。',
    '下一步': ('确认候选入口无误后把 dry_run 设为 false' if dry else '接着 lu_stub_clean → lu_manifest_fix → lu_rebuild 重打包')
}
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, q(dir) + ' ' + (dry ? '1' : '0') + ' ' + q(only) + ' ' + q(entry), 600000);
    }

    // ---------- 壳特征清理 ----------
    function lu_stub_clean(params) {
        var dir = params.decoded_dir;
        if (!dir) throw new Error('缺少 decoded_dir');
        var dry = !(params.dry_run === 'false' || params.dry_run === false);

        var py = String.raw`
import sys, os, re, json
root = sys.argv[1]
dry = sys.argv[2] == '1'

STUB_PAT = re.compile(
    r'^\s*(?:invoke-(?:static|virtual|direct)(?:/range)?)\s+\{[^}]*\},\s*'
    r'L(?:com/stub/StubApp|com/qihoo/stub/DtcLoader|com/frezrik/jiagu/StubApp|com/secneo/apkwrapper/.*?);->(.*)$'
)
MOVE_RES = re.compile(r'^\s*move-result(?:-object|-wide)?\s+')

scanned = 0
removed = 0
files_changed = []
samples = []

for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.smali'):
            continue
        p = os.path.join(dirpath, fn)
        try:
            with open(p, 'r', encoding='utf-8', errors='replace') as f:
                lines = f.read().splitlines()
        except Exception:
            continue
        scanned += 1
        out = []
        i = 0
        n = len(lines)
        hit = 0
        while i < n:
            line = lines[i]
            if STUB_PAT.match(line):
                hit += 1
                if len(samples) < 20:
                    samples.append({'file': p, 'removed': line.strip()})
                removed += 1
                # 连带删除紧随其后的 move-result-*（否则留下悬空寄存器）
                i += 1
                while i < n and lines[i].strip() == '':
                    out.append(lines[i]); i += 1
                if i < n and MOVE_RES.match(lines[i]):
                    removed += 1
                    i += 1
                continue
            out.append(line)
            i += 1
        if hit and not dry:
            try:
                with open(p, 'w', encoding='utf-8') as f:
                    f.write('\n'.join(out) + '\n')
                files_changed.append(p)
            except Exception as e:
                files_changed.append(p + ' ERROR:' + str(e))
        elif hit:
            files_changed.append(p)

res = {
    '解包目录': root,
    '模式': ('只报告(dry_run)' if dry else '已写入'),
    '扫描文件数': scanned,
    '删除语句数': removed,
    '涉及文件': files_changed[:60],
    '样例': samples,
    '说明': '已清理 com.stub.StubApp / com.qihoo.stub.DtcLoader / com.frezrik.jiagu.StubApp / com.secneo 等桩调用，'
            '并连带删除紧随其后的 move-result-* 语句。DtcLoader 是 360 特有特征（Lcom/qihoo/stub/DtcLoader;->clinit()V），漏删会崩。',
    '注意': '特征删除有风险：误删正常代码会导致闪退。建议先 dry_run 看清单，确认后再写入。',
    '下一步': ('确认后设 dry_run=false' if dry else '接着跑 lu_manifest_fix 回填入口')
}
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, q(dir) + ' ' + (dry ? '1' : '0'), 600000);
    }

    // ---------- Manifest 入口回填 ----------
    function lu_manifest_fix(params) {
        var dir = params.decoded_dir;
        if (!dir) throw new Error('缺少 decoded_dir');
        var realApp = params.real_app || '';
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        // 是否顺带删壳代理 activity，默认开。壳 dex 被丢弃后这些类不存在，不删必 ClassNotFoundException
        var dropProxy = !(params.drop_proxy === 'false' || params.drop_proxy === false);
        // 可选：让桩类自己充当 Application（不把入口指向真实 App，而是指向注入的 StubApp 桩）
        var keepStubAsApp = (params.keep_stub_as_app === 'true' || params.keep_stub_as_app === true);

        var py = String.raw`
import sys, os, re, json
root = sys.argv[1]
real = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2] != '-' else ''
dry = sys.argv[3] == '1'
drop_proxy = sys.argv[4] == '1'
keep_stub = sys.argv[5] == '1'

mpath = os.path.join(root, 'AndroidManifest.xml')
if not os.path.exists(mpath):
    print("LURESULT:" + json.dumps({'error': '找不到 AndroidManifest.xml: ' + mpath}))
    sys.exit(0)

with open(mpath, 'r', encoding='utf-8', errors='replace') as f:
    s = f.read()

STUBS = ['com.stub.StubApp', 'com.frezrik.jiagu.StubApp', 'com.qihoo.stub.StubApp',
         'com.secneo.apkwrapper.ApplicationWrapper', 'com.stub.StubApplication']

m = re.search(r'<application\b[^>]*>', s, re.S)
if not m:
    print("LURESULT:" + json.dumps({'error': 'AndroidManifest.xml 里没找到 <application> 标签'}))
    sys.exit(0)

tag = m.group(0)
old_name = None
nm = re.search(r'android:name="([^"]*)"', tag)
if nm:
    old_name = nm.group(1)

is_stub = bool(old_name) and any(st in old_name for st in STUBS)

# ---------- 1) 找壳代理 activity ----------
# 新版 360 会在 manifest 里注册一批 com.byazt.ff.Stub_*_Activity（Standard/Portrait/Landscape/
# SingleTask + _T 半透明变体），这些类住在壳 dex 里。壳 dex 丢弃后必须从 manifest 删掉，
# 否则启动即 ClassNotFoundException: com.byazt.ff.Stub_Standard_Activity。
# ★ 模式必须「够特异」：只在【明确的壳命名空间】里匹配，绝不能写成
#   'com.frezrik.jiagu.*Activity' 这种宽泛式 —— 会把业务自己放在该前缀下的 Activity 一起删掉。
PROXY_PATTERNS = [
    r'com\.byazt\.ff\.Stub_[\w]*Activity[\w]*',      # 新版 360 主力
    r'com\.byazt\.[\w.]*\.Stub_[\w]*Activity[\w]*',  # 变体命名空间
    r'com\.qihoo\.stub\.[\w]*Stub[\w]*Activity[\w]*',
    r'com\.stub\.[\w]*Stub[\w]*Activity[\w]*',
    r'com\.stub\.[\w]*Loader[\w]*Activity[\w]*',
]
proxy_names = set()
for pat in PROXY_PATTERNS:
    for mm in re.finditer(pat, s):
        proxy_names.add(mm.group(0))
# 双保险：只保留确实以 Stub / DtcLoader 之类壳命名开头的（挡掉误匹配）
proxy_names = set(n for n in proxy_names if re.search(r'\.(Stub|DtcLoader)[\w]*Activity', n))

# 进一步：把这些类名出现在 <activity android:name="..."> 的整个 activity 块找出来
def find_activity_blocks(text, names):
    """返回 [(start, end, name)]，只挑 android:name 命中 names 的 activity 块"""
    hits = []
    for am in re.finditer(r'<activity\b', text):
        start = am.start()
        # 找到该 activity 的闭合位置：可能是自闭合 />，也可能是 </activity>
        rest = text[start:]
        self_close = re.match(r'<activity\b[^>]*/>', rest, re.S)
        if self_close:
            end = start + self_close.end()
            block = self_close.group(0)
        else:
            full = re.match(r'<activity\b[\s\S]*?</activity>', rest)
            if not full:
                continue
            end = start + full.end()
            block = full.group(0)
        nmm = re.search(r'android:name="([^"]*)"', block)
        if nmm and nmm.group(1) in names:
            hits.append((start, end, nmm.group(1), block))
    return hits

blocks = find_activity_blocks(s, proxy_names) if (drop_proxy and proxy_names) else []
removed_names = [b[2] for b in blocks]

# ---------- 2) 决定 application 入口 ----------
guessed = None
if not real:
    for md in re.finditer(r'<meta-data\b[^>]*>', s):
        t = md.group(0)
        v = re.search(r'android:value="([^"]*)"', t)
        if v:
            val = v.group(1)
            if re.match(r'^[a-zA-Z][\w\.]*(\.[A-Za-z][\w]*)+$', val) and not any(st in val for st in STUBS):
                guessed = val
                break

target = real or guessed
if keep_stub:
    target = old_name if is_stub else target

# ---------- 3) 执行修改 ----------
# 删壳代理 activity：在【同一个字符串 s】上倒序删除，保证偏移始终有效
news = s
for (st, en, nm2, blk) in sorted(blocks, key=lambda x: -x[0]):
    s_start = st
    while s_start > 0 and s[s_start - 1] in ' \t':
        s_start -= 1
    e_end = en
    if e_end < len(s) and s[e_end] == '\n':
        e_end += 1
    news = news[:s_start] + news[e_end:]

mf_action = '无需修改'
if is_stub:
    m2 = re.search(r'<application\b[^>]*>', news, re.S)
    tag2 = m2.group(0)
    if target:
        tag3 = re.sub(r'android:name="[^"]*"', 'android:name="' + target + '"', tag2, count=1)
        mf_action = '替换为 ' + target
    else:
        tag3 = re.sub(r'\s*android:name="[^"]*"', '', tag2, count=1)
        mf_action = '删除 android:name 属性（未指定且未推测到真实入口）'
    news = news[:m2.start()] + tag3 + news[m2.end():]

changed = (news != s)
if changed and not dry:
    with open(mpath, 'w', encoding='utf-8') as f:
        f.write(news)

res = {
    '原入口': old_name,
    '是否加固桩': is_stub,
    '真实入口': (target or '(未指定)') if is_stub else '(无需修改)',
    '推测来源': ('用户提供' if real else ('meta-data 推测' if guessed else '无')),
    '入口动作': mf_action,
    '壳代理activity_发现数': len(proxy_names),
    '壳代理activity_待删数': len(removed_names),
    '壳代理activity_待删清单': removed_names[:20],
    '模式': '只报告(dry_run)' if dry else '已写入',
    '文件是否改动': changed,
    '说明': '360（新版 com.stub）把 7 个 com.byazt.ff.Stub_*_Activity 注册进 manifest，'
            '这些类住在【壳 dex】里。脱修后壳 dex 被丢弃 → 这些类不存在 → 启动即 ClassNotFoundException。'
            '所以「删掉 manifest 里的壳代理 activity」和「回填 application 入口」是同一个必做步骤，'
            '漏一个就崩。'
}
if is_stub and not target:
    res['警告'] = ('入口是加固桩但没给真实 Application 类名，也没从 meta-data 推测到 —— '
                   '已删除 android:name 属性。如果启动崩，请从脱壳产物的 application.txt 找到真实类名，'
                   '用 real_app 参数重跑本工具。')
if len(removed_names) == 0 and drop_proxy:
    res['提示'] = ('manifest 里没发现壳代理 activity。若是老版 360（com.frezrik.jiagu.StubApp），'
                   '本来就没有这类代理类，属正常。')
res['下一步'] = ('确认上面的「壳代理activity_待删清单」都是壳的类（不是业务自己的 Activity），'
                 '无误后用 dry_run=false 落地。落地后重打包，壳 dex 可以放心丢。')
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, q(dir) + ' ' + q(realApp || '-') + ' ' + (dry ? '1' : '0')
                        + ' ' + (dropProxy ? '1' : '0') + ' ' + (keepStubAsApp ? '1' : '0'), 120000);
    }

    // ---------- dex 二进制修复（含 SHA-1 / Adler-32 重算） ----------
    function lu_dex_fix(params) {
        var path = params.dex_path;
        if (!path) throw new Error('缺少 dex_path');

        var py = String.raw`
import sys, os, struct, hashlib, zlib, json

CHUNK = 1 << 20

def digest_from(path, start, algo):
    h = hashlib.sha1() if algo == 'sha1' else None
    v = 1
    with open(path, 'rb') as f:
        f.seek(start)
        while True:
            b = f.read(CHUNK)
            if not b:
                break
            if h:
                h.update(b)
            else:
                v = zlib.adler32(b, v)
    return h.digest() if h else (v & 0xFFFFFFFF)

def fix(path):
    try:
        sz = os.path.getsize(path)
    except Exception as e:
        return (path, 'error:' + str(e))
    if sz < 112:
        return (path, 'too_small')
    if sz >= 0xFFFFFFFF:
        return (path, 'too_large')
    try:
        with open(path, 'r+b') as f:
            if f.read(8) != b'dex\n035\0':
                return (path, 'not_dex_magic')
            # 1) magic / file_size / header_size
            f.seek(0);  f.write(b'dex\n035\0')
            f.seek(32); f.write(struct.pack('<I', sz))
            f.seek(36); f.write(struct.pack('<I', 112))
            # 2) endian_tag @0x28
            f.seek(40); f.write(struct.pack('<I', 0x12345678))
            f.flush(); os.fsync(f.fileno())
            # 3) SHA-1 signature @0x0c，覆盖 [0x20, EOF)
            sig = digest_from(path, 32, 'sha1')
            f.seek(12); f.write(sig)
            f.flush(); os.fsync(f.fileno())
            # 4) Adler-32 checksum @0x08，覆盖 [0x0c, EOF)
            #    必须在 signature 写入之后算，因为 Adler 范围包含 signature
            ck = digest_from(path, 12, 'adler')
            f.seek(8); f.write(struct.pack('<I', ck))
        return (path, 'fixed')
    except Exception as e:
        return (path, 'error:' + str(e))

target = sys.argv[1]
results = []
if os.path.isdir(target):
    for root, _, files in os.walk(target):
        for fn in files:
            if fn.lower().endswith('.dex'):
                results.append(fix(os.path.join(root, fn)))
else:
    results.append(fix(target))

ok = [r for r in results if r[1] == 'fixed']
res = {
    '目标': target,
    '总数': len(results),
    '修复成功': len(ok),
    '明细': [{'file': r[0], 'status': r[1]} for r in results[:100]],
    '说明': '已重写 magic / file_size / header_size / endian_tag，并重算 SHA-1 signature（覆盖 0x20 到文件尾）'
            '与 Adler-32 checksum（覆盖 0x0c 到文件尾）。顺序固定为「先 SHA-1 后 Adler-32」，'
            '因为 Adler-32 的覆盖范围包含刚写入的 signature。这两个值不重算，ART 会直接拒绝加载 dex。',
}
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, q(path), 600000);
    }

    // ============================================================
    // dex 层 v9 修复（lu_v9_patch）—— 删掉 360 抽走的方法定义
    //
    // 存在的理由（岛风真机实测踩出来的）：
    //   手机端链路是「apktool 解包原包 + 把 dump 出来的业务 dex 直接塞进解包目录」。
    //   但 apktool 只反编译得动**壳 dex** —— 岛风那次解包目录里业务包的 smali 数 = 0，
    //   全目录只有 1 个 com/stub/StubApp.smali。于是走 smali 路线的 lu_fill_oncreate /
    //   lu_splash_fix 一个业务类都覆盖不到，出包后主界面能进、一点「立即登录」就崩：
    //     UnsatisfiedLinkError: No implementation found for
    //     void xyz.daofeng.ui.mine.activity.LoginActivity.onCreate(android.os.Bundle)
    //   本工具直接在 dex 字节上动手，**不依赖 smali**，专治这种「塞 dex 不修复」的缺口。
    //
    // 原理（PC 端妙钱记账 / 幕布 / 岛风 三样本实测跑通）：
    //   360 抽取 = 方法体抽走 + 声明改成 ACC_NATIVE(0x100) + code_off=0，运行时由
    //   libjiagu 回填。重打包后壳没了 → 永久 UnsatisfiedLinkError。
    //   把这条定义**删掉**，子类就继承父类的真实实现（父类 onCreate 有真实方法体，
    //   靠虚分派回调子类）。删定义会让 class_data_item 变短 → 用非最短 uleb128 把
    //   省下的字节摊回 4 个 size 字段，长度分毫不差 → 所有偏移不变 → 不碰 map_list。
    //
    // 千万别扩大范围：native 无体方法里绝大多数是**真 JNI**（sqlcipher / 百度 OCR /
    // 有道翻译 / OcrEngine —— 岛风 138 个里 132 个是真的），删了反而崩。
    // 所以默认只删两个生命周期签名，其余全部列出来供人工二次判断。
    // ============================================================
    function lu_v9_patch(params) {
        var dir = params.dex_dir || params.path;
        if (!dir) throw new Error('缺少 dex_dir（dex 所在目录，也可以填单个 .dex 文件）');
        var prefix = params.prefix || '-';
        var sigs = params.sigs || '-';
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        var inplace = !(params.in_place === 'false' || params.in_place === false);
        var outDir = params.output_dir || '-';

        var py = String.raw`
import sys, os, glob, zlib, shutil, struct, hashlib, json

ACC_NATIVE = 0x100
NO_IDX = 0xFFFFFFFF
# 默认只删这两个：生命周期方法，父类几乎必然有真实实现。
# 想扩就用 sigs 参数传，例如 onResume()V,onStart()V,onDestroy()V
DEFAULT_SIGS = 'onCreate(Landroid/os/Bundle;)V,onRequestPermissionsResult(I[Ljava/lang/String;[I)V'
# prefix 留空时的兜底过滤：这些是框架/三方库，删它们的定义纯属自找麻烦
FRAMEWORK = ('Landroid', 'Landroidx', 'Lcom/android', 'Lcom/google', 'Ljava',
             'Ljavax', 'Lkotlin', 'Lkotlinx', 'Lorg/', 'Lcom/facebook',
             'Lcom/tencent', 'Lcom/alibaba', 'Lcom/baidu', 'Lcom/squareup',
             'Lokhttp', 'Lretrofit2', 'Lio/reactivex', 'Lcom/airbnb',
             'Lcom/github', 'Lcom/qq', 'Lcom/sina', 'Lcom/umeng')


def u16(b, o):
    return struct.unpack_from('<H', b, o)[0]


def u32(b, o):
    return struct.unpack_from('<I', b, o)[0]


def uleb(b, o):
    r = s = 0
    while True:
        x = b[o]
        o += 1
        r |= (x & 0x7F) << s
        if x < 0x80:
            return r, o
        s += 7


def uleb_len(v):
    n = 1
    v >>= 7
    while v:
        n += 1
        v >>= 7
    return n


def put_uleb(v, minlen=1):
    """序列化成 uleb128，可强制最小长度（非最短编码）。
    非最短是合法的：前 n-1 个字节高位置 1，最后一个字节 < 0x80。
    例：1 写成 2 字节 = 81 00；写成 3 字节 = 81 80 00。
    """
    out = bytearray()
    while True:
        x = v & 0x7F
        v >>= 7
        if v:
            out.append(x | 0x80)
        else:
            out.append(x)
            break
    if len(out) < minlen:
        out[-1] |= 0x80
        for _ in range(minlen - len(out) - 1):
            out.append(0x80)
        out.append(0x00)
    return bytes(out)


class Dex:
    def __init__(self, path):
        self.path = path
        self.name = os.path.basename(path)
        self.b = bytearray(open(path, 'rb').read())
        b = self.b
        self.off_str = u32(b, 60)
        self.n_str = u32(b, 56)
        self.off_type = u32(b, 68)
        self.n_type = u32(b, 64)
        self.off_proto = u32(b, 76)
        self.off_meth = u32(b, 92)
        self.n_meth = u32(b, 88)
        self.off_cls = u32(b, 100)
        self.n_cls = u32(b, 96)
        self.data_off = u32(b, 108)
        self.types = [u32(b, self.off_type + 4 * i) for i in range(self.n_type)]
        self._sc = {}

    def string(self, i):
        v = self._sc.get(i)
        if v is not None:
            return v
        p = u32(self.b, self.off_str + 4 * i)
        while self.b[p] & 0x80:
            p += 1
        p += 1
        st = p
        while self.b[p] != 0:
            p += 1
        s = self.b[st:p].decode('utf-8', 'replace')
        self._sc[i] = s
        return s

    def tn(self, i):
        return self.string(self.types[i]) if i < self.n_type else '?'

    def proto_str(self, i):
        o = self.off_proto + 12 * i
        rt = u16(self.b, o + 4)
        po = u32(self.b, o + 8)
        out = '('
        if po:
            n = u32(self.b, po)
            p = po + 4
            for k in range(n):
                out += self.tn(u16(self.b, p + 2 * k))
        return out + ')' + self.tn(rt)

    def meth_sig(self, i):
        o = self.off_meth + 8 * i
        return (self.tn(u16(self.b, o)) + '->' +
                self.string(u32(self.b, o + 4)) +
                self.proto_str(u16(self.b, o + 2)))


def parse_class(d, i):
    """返回 (类名, 父类下标, 父类名, class_data_off)"""
    o = d.off_cls + 32 * i
    cn = d.tn(u32(d.b, o))
    sup = u32(d.b, o + 8)
    cdo = u32(d.b, o + 24)
    return cn, sup, (d.tn(sup) if sup != NO_IDX and sup < d.n_type else None), cdo


def read_all(d, cdo):
    """读出一个 class_data_item 的全部内容，返回 (fields, directs, virtuals, 原长度)"""
    b = d.b
    sf, p = uleb(b, cdo)
    inf, p = uleb(b, p)
    dm, p = uleb(b, p)
    vm, p = uleb(b, p)
    fields = []
    # encoded_field = idx_diff + access_flags（2 个 uleb）
    for _ in range(sf + inf):
        a, p = uleb(b, p)
        c, p = uleb(b, p)
        fields.append((a, c))

    def read_meths(cnt, p):
        ms = []
        idx = 0
        for _ in range(cnt):
            df, p = uleb(b, p)
            acc, p = uleb(b, p)
            co, p = uleb(b, p)
            idx += df
            sig = d.meth_sig(idx).split('->', 1)[1] if idx < d.n_meth else '?'
            ms.append({'abs': idx, 'acc': acc, 'code': co, 'sig': sig})
        return ms, p

    # 注意：direct 与 virtual 两个列表的 method_idx_diff 各自从 0 累加，
    # 不是跨列表连续累加。写成连续累加会让第二个列表的索引整体翻倍/越界，
    # 方法名于是全部错乱 —— 曾经误判成「dump 数据残缺」就是这个原因。
    directs, p = read_meths(dm, p)
    virtuals, p = read_meths(vm, p)
    return (sf, inf), fields, directs, virtuals, (p - cdo)


def patch_dex(path, prefixes, sigs, apply):
    d = Dex(path)
    b = d.b
    changed = []
    problems = []
    natives = []

    def in_scope(cn):
        if prefixes:
            return cn.startswith(prefixes)
        return not cn.startswith(FRAMEWORK)

    for ci in range(d.n_cls):
        cn, sup_idx, sup, cdo = parse_class(d, ci)
        if not cdo:
            continue
        sizes, fields, directs, virtuals, orig_len = read_all(d, cdo)

        for m in directs + virtuals:
            if (m['acc'] & ACC_NATIVE) and m['code'] == 0:
                natives.append((cn, m['sig'], sup))

        if not in_scope(cn):
            continue

        def keep(m):
            return not (m['sig'] in sigs and (m['acc'] & ACC_NATIVE) and m['code'] == 0)

        nd = [m for m in directs if keep(m)]
        nv = [m for m in virtuals if keep(m)]
        dropped = [m for m in directs + virtuals if not keep(m)]
        if not dropped:
            continue

        # 删定义的前提是「父类有真实实现可继承」。没有父类（java.lang.Object 除外）
        # 或父类下标是 NO_IDX 时，删了就是真没了 —— 宁可不删。
        if sup_idx == NO_IDX:
            problems.append({'类': cn, '问题': '无父类', '详情': '删掉定义后没有任何实现可继承，已跳过'})
            continue

        def rebuild(lst):
            out = bytearray()
            prev = 0
            for m in lst:
                out += put_uleb(m['abs'] - prev)
                out += put_uleb(m['acc'])
                out += put_uleb(m['code'])
                prev = m['abs']
            return out

        body = bytearray()
        for a, c in fields:
            body += put_uleb(a) + put_uleb(c)
        body += rebuild(nd) + rebuild(nv)

        vals = [sizes[0], sizes[1], len(nd), len(nv)]
        minimal = bytearray()
        for v in vals:
            minimal += put_uleb(v)
        need = orig_len - len(minimal) - len(body)
        if need < 0:
            problems.append({'类': cn, '问题': 'SHRINK_IMPOSSIBLE',
                             '详情': '原长 %d，最短也要 %d' % (orig_len, len(minimal) + len(body))})
            continue

        # 把需要多出来的字节摊到 4 个 size 字段上（每个 uleb 最多 5 字节）
        cap = [5 - uleb_len(v) for v in vals]
        extra = [0, 0, 0, 0]
        left = need
        for k in range(4):
            if left <= 0:
                break
            add = min(cap[k], left)
            extra[k] = add
            left -= add
        if left > 0:
            problems.append({'类': cn, '问题': 'PAD_INSUFFICIENT',
                             '详情': '还要 %d 字节但 4 个 size 字段只剩 %d 可摊' % (need, sum(cap))})
            continue

        head = bytearray()
        for k in range(4):
            head += put_uleb(vals[k], uleb_len(vals[k]) + extra[k])
        newbytes = head + body
        if len(newbytes) != orig_len:
            problems.append({'类': cn, '问题': 'LEN_MISMATCH',
                             '详情': '%d != %d' % (len(newbytes), orig_len)})
            continue

        if apply:
            b[cdo:cdo + orig_len] = newbytes
        changed.append({'类': cn, '父类': sup, '删掉的方法': [m['sig'] for m in dropped]})

    data = None
    if apply and changed:
        new_size = len(b)
        struct.pack_into('<I', b, 32, new_size)               # file_size
        struct.pack_into('<I', b, 104, new_size - d.data_off) # data_size
        b[12:32] = hashlib.sha1(bytes(b[32:])).digest()
        struct.pack_into('<I', b, 8, zlib.adler32(bytes(b[12:])) & 0xFFFFFFFF)
        data = bytes(b)
    return d, data, changed, problems, natives


target = sys.argv[1]
prefixArg = sys.argv[2] if len(sys.argv) > 2 else '-'
sigArg = sys.argv[3] if len(sys.argv) > 3 else '-'
dry = (sys.argv[4] if len(sys.argv) > 4 else '1') == '1'
inplace = (sys.argv[5] if len(sys.argv) > 5 else '1') == '1'
outDir = sys.argv[6] if len(sys.argv) > 6 and sys.argv[6] != '-' else ''

prefixes = tuple(x.strip() for x in prefixArg.split(',') if x.strip()) if prefixArg != '-' else ()
sigs = set(x.strip() for x in sigArg.split(',') if x.strip()) if sigArg != '-' else set(DEFAULT_SIGS.split(','))

if os.path.isdir(target):
    files = sorted(glob.glob(os.path.join(target, '*.dex')))
else:
    files = [target]
files = [f for f in files if os.path.isfile(f)]

if not files:
    print('LURESULT:' + json.dumps({'ok': False, '错误': '目录下没有找到任何 .dex：' + target}, ensure_ascii=False))
    sys.exit(0)

apply = not dry
if apply and outDir:
    if os.path.isdir(outDir):
        shutil.rmtree(outDir)
    os.makedirs(outDir)

per_file = []
all_changed = []
all_problems = []
all_natives = []
for f in files:
    d, data, changed, problems, natives = patch_dex(f, prefixes, sigs, apply)
    for c in changed:
        c['dex'] = d.name
        all_changed.append(c)
    for p in problems:
        p['dex'] = d.name
        all_problems.append(p)
    for cn, sig, sup in natives:
        all_natives.append({'dex': d.name, '类': cn, '签名': sig, '父类': sup})
    per_file.append({'dex': d.name, '改类数': len(changed)})
    # 输出到目录时**必须全量复制**，包括没改动的 dex。早先只在 changed 时才写，
    # 结果输出目录少了一半 dex（岛风那次 classes2.dex 直接不见），拿去组装就缺类。
    if apply and outDir:
        open(os.path.join(outDir, d.name), 'wb').write(data if changed else open(f, 'rb').read())
    elif apply and changed:
        open(f if inplace else (f + '.v9'), 'wb').write(data)

dropped_sigs = sorted(set(s for c in all_changed for s in c['删掉的方法']))
other_natives = [n for n in all_natives if n['签名'] not in set(dropped_sigs)]

res = {
    'ok': True,
    '目标': target,
    'dex数': len(files),
    '包前缀': list(prefixes) if prefixes else '(留空=排除框架/三方库)',
    '要删的签名': sorted(sigs),
    '模式': ('预演（没动盘，dry_run=false 才落盘）' if dry else ('已落盘' + ('（原地覆盖）' if inplace and not outDir else ('（输出到 ' + outDir + '）')))),
    'native无体方法总数': len(all_natives),
    '实际删掉的方法数': sum(len(c['删掉的方法']) for c in all_changed),
    '改类数': len(all_changed),
    '改动明细': all_changed[:100],
    '其它native无体（没动，人工二次确认）': other_natives[:200],
    '异常': all_problems[:50],
    '逐dex': per_file,
}
if dry:
    res['下一步'] = '核对「改动明细」里的类名是不是主程序自己的类。确认后把 dry_run 设为 false 再跑一次。'
    res['提醒'] = ('「其它native无体」里绝大多数是真 JNI（sqlcipher / 百度OCR / 有道翻译 / OcrEngine），'
                   '删掉反而崩。只有主程序自己类的生命周期方法才该删，'
                   '要扩范围就把签名用 sigs 参数传进来。')
else:
    res['下一步'] = '修完的 dex 已经就位，接下来直接 lu_rebuild 回编译出包。'
print('LURESULT:' + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, q(dir) + ' ' + q(prefix) + ' ' + q(sigs) + ' '
            + (dry ? '1' : '0') + ' ' + (inplace ? '1' : '0') + ' ' + q(outDir), 600000)
            .then(function (r) {
                if (!r || r.raw) return r;
                r['一句话'] = (dry ? '预演：' : '已修复：') + '扫到 native 无体方法 '
                    + (r['native无体方法总数'] || 0) + ' 个，删掉 '
                    + (r['实际删掉的方法数'] || 0) + ' 个（涉及 '
                    + (r['改类数'] || 0) + ' 个类）。';
                return r;
            });
    }

    // ============================================================
    // dex 层「启动页接回」补丁（lu_code_patch）—— 等长方法体原地改写
    //
    // 存在的理由（幕布 com.mubu.app 实测踩出来的）：
    //   lu_v9_patch 删掉被抽走的 onCreate 定义后，子类继承父类实现 —— 但父类
    //   往往「只做了一半」。幕布的 BaseSplashActivity.onCreate 只有 37 条指令，
    //   设了个横竖屏和状态栏颜色，**一次虚分派都没有**；真正的跳转全在被抽走的
    //   MubuSplashActivity.onCreate 里。于是：不崩了，但永远卡在启动页。
    //
    //   好消息是 360 通常只抽生命周期那一个，同类里真正的业务入口还活着、只是
    //   没人调用（幕布是 checkAndShowMubuTerm）。所以找一个必定会被框架调用的存活
    //   方法（onResume 优先）做「搭载」，原地改写成：
    //       invoke-super 父类->onResume(); 本类->入口(); return-void
    //
    // 为什么必须「等长」：code_item 的 insns 区定长。往 dex 尾部追加 code_item
    //   会破坏 map_list，ART 会整体拒绝加载整个 dex（历史已证，别再试）。原地覆盖
    //   + 余下填 nop → 长度不变 → 偏移不变 → 不碰 map_list。
    //
    // ★顺序要求：必须跑在 lu_v9_patch **之前**。v9 删掉 onCreate 定义后，
    //   本工具就没有「哪个类被抽过」的证据了（只能靠手动传 cls 兜底）。
    // ============================================================
    function lu_code_patch(params) {
        var dir = params.dex_dir || params.path;
        if (!dir) throw new Error('缺少 dex_dir（dex 所在目录，也可以填单个 .dex 文件）');
        var prefix = params.prefix || '';
        var cls = params.cls || '';
        var method = params.method || '';
        var entry = params.entry || '';
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        var outDir = params.output_dir || '';
        var py = String.raw`
# -*- coding: utf-8 -*-
"""dex 层「启动页接回」补丁：自动定位入口 + 等长方法体原地改写。

为什么需要（幕布 com.mubu.app 实测踩出来的）：
  360 抽走的生命周期方法（onCreate 等）被 patch_v9 / lu_v9_patch **删掉定义**后，
  子类会继承父类实现。但父类往往「只做了一半」——幕布的
  BaseSplashActivity.onCreate 只有 37 条指令、设了个横竖屏和状态栏颜色，
  **一次虚分派都没有**，真正的跳转逻辑全在被抽走的 MubuSplashActivity.onCreate 里。
  结果：不崩了，但永远卡在启动页。

  好消息是 360 通常只抽生命周期那一个，同一类里真正的业务入口（幕布是
  checkAndShowMubuTerm）**还活着，只是没人调用**。所以只要把另一个必然会被
  框架调用的生命周期方法（onResume）原地改写成
      invoke-super 父类->onResume(); 本类->入口(); return-void
  就能把流程接回去。

为什么必须「等长」：
  code_item 的 insns 区是定长的。往 dex 尾部追加 code_item 会破坏 map_list，
  ART 会整体拒绝加载整个 dex（历史已证，别再试）。原地覆盖 + 余下填 nop
  → 长度不变 → 所有偏移不变 → 不碰 map_list → 绝对安全。

用法：
  python patch_entry.py <dex目录或单个dex> [--prefix Lcom/xxx/] \
      [--cls Lcom/x/Y;] [--method onResume()V] [--entry someMethod] \
      [--dry] [--out 输出目录]

  默认只演练（分析 + 打分 + 列出入口候选），加 --apply 才真正写盘。
  最后一行输出 RKRESULT: + JSON。
"""
import os
import re
import sys
import glob
import zlib
import shutil
import struct
import hashlib
import json

ACC_STATIC = 0x8
ACC_PRIVATE = 0x2
ACC_NATIVE = 0x100

OP_INVOKE_VIRTUAL = 0x6e
OP_INVOKE_SUPER = 0x6f
OP_INVOKE_DIRECT = 0x70
OP_INVOKE_STATIC = 0x71
OP_INVOKE_INTERFACE = 0x72

# 360 抽取几乎只打这几种生命周期回调
LIFECYCLE = ('onCreate', 'onStart', 'onResume', 'onRestart',
             'onPostCreate', 'onPostResume', 'onRequestPermissionsResult',
             'onActivityResult', 'onNewIntent', 'onConfigurationChanged')

# 这些绝不可能是「业务入口」：构造函数 / 销毁与返回回调 / 框架重写点。
# 幕布第一版没排掉 <init>，它被「同类无人调用+30 + 关键词 init+10」顶到 40 分，
# 在多个 Activity 里排到了真实入口前面 —— 把构造函数塞进 onResume 必炸。
NOT_ENTRY = ('<init>', '<clinit>', 'onDestroy', 'onBackPressed', 'onStop',
             'onPause', 'onSaveInstanceState', 'onRestoreInstanceState',
             'onLowMemory', 'onTrimMemory', 'onAttachedToWindow',
             'onDetachedFromWindow', 'onUserLeaveHint', 'onWindowFocusChanged',
             'recreate', 'finish', 'finishActivity', 'finishAffinity',
             'overridePendingTransitionEnter', 'overridePendingTransitionExit',
             'onSupportNavigateUp', 'onNavigateUp', 'onOptionsItemSelected',
             'onKeyDown', 'onKeyUp', 'onTouchEvent', 'dispatchTouchEvent')

# 用来「搭载」新逻辑的方法：必须框架保证会调用，且自己有真实方法体
CARRIER = ('onResume()V', 'onStart()V', 'onRestart()V', 'onPostResume()V')

# 直接父类没在 method_ids 里时，按这个顺序找真实祖先（幕布命中 FragmentActivity）
ANCESTOR_FALLBACK = (
    'Landroidx/appcompat/app/AppCompatActivity;',
    'Landroidx/fragment/app/FragmentActivity;',
    'Landroidx/core/app/ComponentActivity;',
    'Landroid/app/Activity;',
)

ENTRY_KW = (('jump', 25), ('term', 25), ('continue', 20), ('launch', 15),
            ('check', 15), ('enter', 10), ('init', 10), ('main', 10),
            ('start', 8), ('load', 5), ('open', 5), ('show', 5),
            ('to', 5), ('go', 5))

# 指令长度表（16-bit unit）。只列真实会出现的，缺省按 1 处理并停止该段扫描。
INSN_LEN = {}


def _build_len():
    for op in range(0x00, 0x0e):
        INSN_LEN[op] = 1          # nop / move* / move-result* / move-exception
    for op in range(0x0e, 0x12):
        INSN_LEN[op] = 1          # return*
    INSN_LEN[0x12] = 1            # const/4
    INSN_LEN[0x13] = 2            # const/16
    INSN_LEN[0x14] = 3            # const
    INSN_LEN[0x15] = 2            # const/high16
    INSN_LEN[0x16] = 2
    INSN_LEN[0x17] = 3
    INSN_LEN[0x18] = 5
    INSN_LEN[0x19] = 2
    INSN_LEN[0x1a] = 2            # const-string
    INSN_LEN[0x1b] = 3            # const-string/jumbo
    INSN_LEN[0x1c] = 2            # const-class
    INSN_LEN[0x1d] = 1
    INSN_LEN[0x1e] = 1
    INSN_LEN[0x1f] = 2            # check-cast
    INSN_LEN[0x20] = 2            # instance-of
    INSN_LEN[0x21] = 1            # array-length
    INSN_LEN[0x22] = 2            # new-instance
    INSN_LEN[0x23] = 2            # new-array
    INSN_LEN[0x24] = 3            # filled-new-array
    INSN_LEN[0x25] = 3            # filled-new-array/range
    INSN_LEN[0x26] = 3            # fill-array-data
    INSN_LEN[0x27] = 1            # throw
    INSN_LEN[0x28] = 1            # goto
    INSN_LEN[0x29] = 2            # goto/16
    INSN_LEN[0x2a] = 3            # goto/32
    INSN_LEN[0x2b] = 3            # packed-switch
    INSN_LEN[0x2c] = 3            # sparse-switch
    for op in range(0x2d, 0x32):
        INSN_LEN[op] = 2          # cmpl/cmpl-float/cmpg...
    for op in range(0x32, 0x3e):
        INSN_LEN[op] = 2          # if-* / if-*z
    for op in range(0x44, 0x52):
        INSN_LEN[op] = 2          # aget* / aput*
    for op in range(0x52, 0x6e):
        INSN_LEN[op] = 2          # iget* / iput* / sget* / sput*
    for op in range(0x6e, 0x73):
        INSN_LEN[op] = 3          # invoke-* (35c)
    for op in range(0x74, 0x79):
        INSN_LEN[op] = 3          # invoke-*/range (3rc)
    for op in range(0x7c, 0x90):
        INSN_LEN[op] = 1          # unop
    for op in range(0x90, 0xb0):
        INSN_LEN[op] = 2          # binop
    for op in range(0xb0, 0xd0):
        INSN_LEN[op] = 1          # binop/2addr
    for op in range(0xd0, 0xe0):
        INSN_LEN[op] = 2          # binop/lit16
    for op in range(0xe0, 0x100):
        INSN_LEN[op] = 2          # binop/lit8


_build_len()


def u16(b, o):
    return struct.unpack_from('<H', b, o)[0]


def u32(b, o):
    return struct.unpack_from('<I', b, o)[0]


def uleb(b, o):
    r = s = 0
    while True:
        x = b[o]
        o += 1
        r |= (x & 0x7F) << s
        if x < 0x80:
            return r, o
        s += 7


class Dex:
    def __init__(self, path):
        self.path = path
        self.name = os.path.basename(path)
        self.b = bytearray(open(path, 'rb').read())
        b = self.b
        self.off_str = u32(b, 60)
        self.off_type = u32(b, 68)
        self.n_type = u32(b, 64)
        self.off_proto = u32(b, 76)
        self.off_meth = u32(b, 92)
        self.n_meth = u32(b, 88)
        self.off_cls = u32(b, 100)
        self.n_cls = u32(b, 96)
        self.data_off = u32(b, 108)
        self.types = [u32(b, self.off_type + 4 * i) for i in range(self.n_type)]
        self._sc = {}

    def string(self, i):
        v = self._sc.get(i)
        if v is not None:
            return v
        p = u32(self.b, self.off_str + 4 * i)
        while self.b[p] & 0x80:
            p += 1
        p += 1
        st = p
        while self.b[p] != 0:
            p += 1
        s = self.b[st:p].decode('utf-8', 'replace')
        self._sc[i] = s
        return s

    def tn(self, i):
        return self.string(self.types[i])

    def proto_str(self, i):
        o = self.off_proto + 12 * i
        rt = u16(self.b, o + 4)
        po = u32(self.b, o + 8)
        out = '('
        if po:
            n = u32(self.b, po)
            for k in range(n):
                out += self.tn(u16(self.b, po + 4 + 2 * k))
        return out + ')' + self.tn(rt)

    def meth_id(self, i):
        o = self.off_meth + 8 * i
        return (self.tn(u16(self.b, o)),
                self.string(u32(self.b, o + 4)),
                self.proto_str(u16(self.b, o + 2)))

    def meth_sig(self, i):
        c, n, p = self.meth_id(i)
        return c + '->' + n + p

    def find_method(self, sig):
        for i in range(self.n_meth):
            if self.meth_sig(i) == sig:
                return i
        return None

    def classes(self):
        """yield (class_desc, super_desc, class_data_off)"""
        for i in range(self.n_cls):
            o = self.off_cls + 32 * i
            yield self.tn(u32(self.b, o)), self.tn(u32(self.b, o + 8)), u32(self.b, o + 24)

    def methods(self, cdo):
        """返回 [(sig_without_class, acc, code_off)]"""
        if not cdo:
            return []
        b = self.b
        sf, p = uleb(b, cdo)
        inf, p = uleb(b, p)
        dm, p = uleb(b, p)
        vm, p = uleb(b, p)
        for _ in range(sf):
            _, p = uleb(b, p)
            _, p = uleb(b, p)
        for _ in range(inf):
            _, p = uleb(b, p)
            _, p = uleb(b, p)
        out = []
        for cnt in (dm, vm):          # 两个列表各自从 0 起算（历史 bug，别改）
            idx = 0
            for _ in range(cnt):
                df, p = uleb(b, p)
                acc, p = uleb(b, p)
                co, p = uleb(b, p)
                idx += df
                ms = self.meth_sig(idx)
                out.append((ms.split('->', 1)[1], acc, co))
        return out

    # ---- 指令扫描：找出某个方法体里 invoke 了哪些 method_idx ----
    def insns_of(self, co):
        if not co:
            return b'', 0
        n = u32(self.b, co + 12)
        return bytes(self.b[co + 16:co + 16 + n * 2]), n

    def invoked_methods(self, co):
        """粗略但够用：按长度表走一遍，收集所有 invoke-* 引用的 method_idx"""
        ins, n = self.insns_of(co)
        out = set()
        i = 0
        while i < n:
            op = ins[i * 2]
            ln = INSN_LEN.get(op)
            if not ln:
                break                      # 数据/未知指令，别再往下猜
            if 0x6e <= op <= 0x72:
                out.add(u16(ins, i * 2 + 2))
            elif 0x74 <= op <= 0x78:
                out.add(u16(ins, i * 2 + 2))
            i += ln
        return out


def insn35c(op, regs, midx):
    n = len(regs)
    assert 0 < n <= 5, '寄存器个数必须是 1..5'
    r = list(regs) + [0] * (5 - n)
    for v in r[:n]:
        assert 0 <= v < 16, '寄存器编号必须 <16'
    c, d, e, f, g = r[0], r[1], r[2], r[3], r[4]
    u0 = op | (g << 8) | (n << 12)
    u2 = c | (d << 4) | (e << 8) | (f << 12)
    return struct.pack('<HHH', u0, midx, u2)


def kw_score(name):
    low = name.lower()
    return sum(w for k, w in ENTRY_KW if k in low)


def is_synthetic(name):
    return name.startswith('access$') or name.startswith('lambda$') or name.startswith('$')


def analyze(d, prefix, want_cls):
    """返回 [{'cls','super','extracted',[sig],'carrier','ancestor','entries':[(name,score,reason)]}]"""
    res = []
    for cls, sup, cdo in d.classes():
        if want_cls and cls != want_cls:
            continue
        if not want_cls and prefix and not cls.startswith(prefix):
            continue
        ms = d.methods(cdo)
        extracted = [s for s, a, c in ms
                     if (a & ACC_NATIVE) and c == 0 and s.split('(')[0] in LIFECYCLE]
        if not extracted and not want_cls:
            continue
        # 搭载方法：有真实体、在 CARRIER 列表里
        carrier = None
        for cand in CARRIER:
            hit = [m for m in ms if m[0] == cand and m[2] and not (m[1] & ACC_NATIVE)]
            if hit:
                carrier = cand
                break
        if not carrier:
            continue
        name_only = carrier.split('(')[0]
        # invoke-super 的祖先：直接父类优先，没有就按框架基类兜底
        ancestor = None
        for cand in (sup,) + ANCESTOR_FALLBACK:
            if d.find_method(cand + '->' + carrier) is not None:
                ancestor = cand
                break
        # 入口候选：同类、有体、()V、非静态、非合成、非生命周期
        called = set()
        for s, a, c in ms:
            if not c or (a & ACC_NATIVE):
                continue
            midx = d.find_method(cls + '->' + s)
            if midx is None:
                continue
            for s2, a2, c2 in ms:
                if not c2 or (a2 & ACC_NATIVE) or s2 == s:
                    continue
                if midx in d.invoked_methods(c2):
                    called.add(s)
                    break
        entries = []
        for s, a, c in ms:
            if not c or (a & ACC_NATIVE):
                continue
            nm = s.split('(')[0]
            if not s.endswith('()V'):
                continue

            if a & ACC_STATIC or is_synthetic(nm) or nm in LIFECYCLE or nm == name_only:
                continue
            if nm in NOT_ENTRY:
                continue
            score = kw_score(nm)
            why = []
            if score:
                why.append('关键词+%d' % score)
            if s not in called:
                score += 30
                why.append('同类无人调用+30')
            if a & ACC_PRIVATE:
                score += 5
                why.append('private+5')
            entries.append((nm, score, '；'.join(why) or '无特征'))
        entries.sort(key=lambda x: (-x[1], x[0]))
        res.append({'cls': cls, 'super': sup, 'extracted': extracted,
                    'carrier': carrier, 'ancestor': ancestor,
                    'entries': entries[:6]})
    return res


def patch(d, cls, carrier, entry_name, ancestor):
    """等长改写 cls->carrier 的方法体。返回 dict（含 notes）或抛 ValueError"""
    sup_sig = None
    if ancestor:
        sup_sig = ancestor + '->' + carrier
    entry_sig = cls + '->' + entry_name + '()V'
    midx_super = d.find_method(sup_sig) if sup_sig else None
    midx_entry = d.find_method(entry_sig)
    if midx_entry is None:
        raise ValueError('dex 里找不到入口方法：%s' % entry_sig)

    hit = None
    entry_acc = None
    for c, sup, cdo in d.classes():
        if c != cls:
            continue
        ms = d.methods(cdo)
        for s, a, co in ms:
            if s == carrier:
                hit = (a, co)
            if s == entry_name + '()V':
                entry_acc = a
        break
    if hit is None:
        raise ValueError('找不到类或方法：%s->%s' % (cls, carrier))
    if entry_acc is None:
        raise ValueError('入口方法不在本类的 class_data 里：%s' % entry_name)
    acc, co = hit
    if acc & ACC_NATIVE or co == 0:
        raise ValueError('搭载方法是 native/无体的，改不了：%s' % carrier)

    b = d.b
    regs = u16(b, co)
    ins = u16(b, co + 2)
    outs = u16(b, co + 4)
    tries = u16(b, co + 6)
    n_units = u32(b, co + 12)
    base = co + 16
    this = regs - ins
    if this < 0 or this >= 16:
        raise ValueError('this 寄存器算不出来：regs=%d ins=%d' % (regs, ins))
    if outs < 1:
        raise ValueError('outs_size=%d，不足以发起调用' % outs)
    if tries:
        raise ValueError('该方法带 try/catch（tries=%d），等长改写不安全，跳过' % tries)

    code = bytearray()
    notes = []
    if midx_super is not None:
        code += insn35c(OP_INVOKE_SUPER, [this], midx_super)
        notes.append('invoke-super {v%d}, %s' % (this, sup_sig))
    else:
        notes.append('!! 父类 %s 的 method_id 不存在，已省略 invoke-super' % carrier.split('(')[0])
    # 调用种类看的是**入口方法**自己的访问标志，不是搭载方法的。
    # 幕布 checkAndShowMubuTerm 是 private，必须用 invoke-direct；
    # 用 invoke-virtual 调 private 会 NoSuchMethodError（private 不在虚表里）。
    private_entry = bool(entry_acc & ACC_PRIVATE)
    entry_op = OP_INVOKE_DIRECT if private_entry else OP_INVOKE_VIRTUAL
    code += insn35c(entry_op, [this], midx_entry)
    notes.append('%s {v%d}, %s' % ('invoke-direct' if private_entry else 'invoke-virtual',
                                   this, entry_sig))
    code += struct.pack('<H', 0x000e)
    notes.append('return-void')

    need = len(code) // 2
    if need > n_units:
        raise ValueError('空间不够：%s 只有 %d 单元，需要 %d' % (carrier, n_units, need))
    if max(this, 0) >= regs:
        raise ValueError('用到的寄存器 v%d 超出 registers_size=%d' % (this, regs))

    new = bytes(code) + b'\x00\x00' * (n_units - need)
    b[base:base + n_units * 2] = new
    struct.pack_into('<I', b, co + 8, 0)      # debug_info_off=0：旧行号表别指向已改写地址
    return {'code_off': co, 'units': n_units, 'need': need,
            'regs': regs, 'ins': ins, 'outs': outs, 'notes': notes}


def finalize(d):
    b = d.b
    n = len(b)
    struct.pack_into('<I', b, 32, n)                       # file_size
    struct.pack_into('<I', b, 104, n - d.data_off)         # data_size
    b[12:32] = hashlib.sha1(bytes(b[32:])).digest()        # signature
    struct.pack_into('<I', b, 8, zlib.adler32(bytes(b[12:])) & 0xFFFFFFFF)  # checksum
    return bytes(b)

def main():
    a = sys.argv[1:]
    src = a[0] if len(a) > 0 else ''
    prefix = a[1] if len(a) > 1 else ''
    want_cls = a[2] if len(a) > 2 else ''
    want_method = a[3] if len(a) > 3 else ''
    want_entry = a[4] if len(a) > 4 else ''
    dry = (a[5] if len(a) > 5 else '1') == '1'
    out = a[6] if len(a) > 6 else ''
    apply = not dry

    res = {'分析': [], '已改写': [], '问题': []}
    if not src:
        res['问题'].append('缺少 dex_dir')
        print('LURESULT:' + json.dumps(res, ensure_ascii=False))
        return 2
    if not os.path.exists(src):
        res['问题'].append('路径不存在：' + src)
        print('LURESULT:' + json.dumps(res, ensure_ascii=False))
        return 2

    files = sorted(glob.glob(os.path.join(src, '*.dex'))) if os.path.isdir(src) else [src]
    if not files:
        res['问题'].append('目录下没有 .dex：' + src)
        print('LURESULT:' + json.dumps(res, ensure_ascii=False))
        return 2
    if apply and out:
        if os.path.isdir(out):
            shutil.rmtree(out)
        os.makedirs(out)

    for f in files:
        d = Dex(f)
        infos = analyze(d, prefix, want_cls)
        for info in infos:
            if not info['carrier']:
                res['问题'].append('%s: %s 没有可用的搭载方法（onResume/onStart/onRestart/onPostResume 都没真实体）'
                                   % (d.name, info['cls']))
                continue
            if not info['entries']:
                res['问题'].append('%s: %s 找不到可用入口候选' % (d.name, info['cls']))
                continue
            res['分析'].append({
                'dex': d.name, '类': info['cls'], '父类': info['super'],
                '被抽走': info['extracted'] or ['(已被 v9 删过，无证据)'],
                '搭载方法': info['carrier'],
                'invoke-super目标': info['ancestor'] or '(dex 里找不到，会省略)',
                '首选入口': info['entries'][0][0],
                '首选入口分数': info['entries'][0][1],
                '入口候选': [x[0] + '(%d)' % x[1] for x in info['entries']],
            })
        if not apply or not want_cls:
            continue
        changed = False
        for info in infos:
            if info['cls'] != want_cls or not info['entries']:
                continue
            carrier = want_method or info['carrier']
            entry_name = want_entry or info['entries'][0][0]
            try:
                r = patch(d, want_cls, carrier, entry_name, info['ancestor'])
            except ValueError as e:
                res['问题'].append('%s: %s' % (d.name, e))
                continue
            changed = True
            res['已改写'].append({
                'dex': d.name, '类': want_cls, '搭载方法': carrier, '入口': entry_name,
                'code_off': r['code_off'], '原单元数': r['units'], '占用单元': r['need'],
                '寄存器': 'regs=%d ins=%d outs=%d' % (r['regs'], r['ins'], r['outs']),
                '新指令': r['notes'],
            })
        data = finalize(d) if changed else open(f, 'rb').read()
        dst = os.path.join(out, d.name) if out else f
        open(dst, 'wb').write(data)

    if apply and out:
        # 未命中的 dex 也必须复制过去（历史 bug：只写 changed 的，结果输出目录少一个 dex）
        for f in files:
            dst = os.path.join(out, os.path.basename(f))
            if not os.path.exists(dst):
                shutil.copy(f, dst)

    res['扫到的dex数'] = len(files)
    res['被抽过入口的类数'] = len(res['分析'])
    res['实际改写的dex数'] = len(res['已改写'])
    if dry:
        res['下一步'] = ('以上是演练。确认「首选入口」确实是真正的业务入口后，'
                         '带 cls（必要时带 entry）并把 dry_run 设为 false 再跑一次。')
        res['提醒'] = ('★必须在 lu_v9_patch 之前跑：v9 会把被抽走的 onCreate 定义删掉，'
                       '删完本工具就没有「哪个类被抽过」的证据了。'
                       '另外 invoke-super 目标如果显示「找不到」，说明父类没在 method_ids 里，'
                       '会自动省略 super 调用（多数情况仍可跑，但要实测确认）。')
    else:
        res['下一步'] = '改写已落盘，接下来跑 lu_v9_patch（dry_run=false）删其余抽取定义，再 lu_rebuild 出包。'
    print('LURESULT:' + json.dumps(res, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    sys.exit(main())

`;
        return runPy(py, [q(dir), q(prefix), q(cls), q(method), q(entry),
            dry ? '1' : '0', q(outDir)].join(' '), 600000)
            .then(function (r) {
                if (!r || r.raw) return r;
                r['一句话'] = (dry ? '预演：' : '已改写：') + '扫到 '
                    + (r['扫到的dex数'] || 0) + ' 个 dex，'
                    + (r['被抽过入口的类数'] || 0) + ' 个类的入口被抽走，改写 '
                    + (r['实际改写的dex数'] || 0) + ' 个 dex。';
                if (dry && r['分析'] && r['分析'].length) {
                    r['一句话'] += ' 首选入口：'
                        + r['分析'][0]['类'] + ' -> ' + r['分析'][0]['首选入口']
                        + '（' + r['分析'][0]['首选入口分数'] + ' 分）。';
                }
                return r;
            });
    }
    // ---------- 重打包 + 签名 ----------
    function lu_rebuild(params) {
        var dir = params.decoded_dir, outApk = params.output_apk;
        if (!dir || !outApk) throw new Error('缺少 decoded_dir 或 output_apk');

        return execRaw('which apktool || command -v apktool || echo NONE', 15000).then(function (r) {
            if ((r.output || '').indexOf('NONE') >= 0) {
                return {
                    success: false,
                    原因: '设备上没找到 apktool，无法回编译',
                    替代方案: [
                        '用 Operit 官方 apk_reverse 工具包的 apk_reverse_build + apk_reverse_sign',
                        '用 MT 管理器：解包目录 → 回编译 → 签名',
                        '修复类步骤（lu_scan / lu_fill_oncreate / lu_stub_clean / lu_manifest_fix）都已经落盘了，只差最后打包'
                    ]
                };
            }
            return exec('apktool b ' + q(dir) + ' -o ' + q(outApk), 900000).then(function () {
                return execRaw('which zipalign || command -v zipalign || echo NONE', 15000);
            }).then(function (zr) {
                var hasZ = (zr.output || '').indexOf('NONE') < 0;
                var chain = hasZ
                    ? execRaw('zipalign -f 4 ' + q(outApk) + ' ' + q(outApk + '.aligned') + ' && mv ' + q(outApk + '.aligned') + ' ' + q(outApk), 300000)
                    : Promise.resolve({ output: 'skip zipalign' });
                return chain.then(function () {
                    return execRaw('which apksigner || command -v apksigner || echo NONE', 15000);
                });
            }).then(function (sr) {
                var hasS = (sr.output || '').indexOf('NONE') < 0;
                if (!hasS) {
                    return {
                        输出APK: outApk,
                        签名: '未签名（设备无 apksigner）',
                        下一步: '用 MT 管理器或官方 apk_reverse 的 sign 完成签名。装不上可开 CorePatch（LSPosed）绕过签名校验。'
                    };
                }
                // 实战教训：必须显式声明 v1 + v2。不写时 apksigner 会按 minSdk/maxSdk 自行推断，
                // 脱修场景里经常只签了 v1，安装阶段直接吃「no APK Signing Scheme v2 signature」。
                var signCmd = 'apksigner sign --ks-debug --v1-signing-enabled true --v2-signing-enabled true '
                            + '--out ' + q(outApk + '.signed') + ' ' + q(outApk) + ' 2>&1 | tail -8';
                return execRaw(signCmd, 300000).then(function (rr) {
                    var so = (rr.output || '');
                    return execRaw('if [ -f ' + q(outApk + '.signed') + ' ]; then mv ' + q(outApk + '.signed') + ' '
                                   + q(outApk) + '; echo MOVED; else echo NOSIGNED; fi', 30000)
                        .then(function (mv) {
                            var moved = (mv.output || '').indexOf('MOVED') >= 0;
                            return execRaw('apksigner verify --print-certs ' + q(outApk) + ' 2>&1 | head -20', 60000)
                                .then(function (vf) {
                                    return {
                                        输出APK: outApk,
                                        签名: moved ? '已 debug 签名（v1 + v2 显式开启）'
                                                    : '签名失败，产物仍是未签名的包',
                                        签名输出: so.substring(0, 500),
                                        校验输出: (vf.output || '').substring(0, 500),
                                        zipalign: hasZ ? '已在签名之前完成 ✅（顺序正确）'
                                                       : '跳过（设备无 zipalign）',
                                        下一步: moved
                                            ? '装上去跑冒烟测试（logfox_toolkit 的 apk_launch_test）。闪退就抓日志看是哪里崩。'
                                            : 'apksigner 没产出签名包，看上面的签名输出排查。兜底：MT 管理器「APK 签名」。',
                                        注意: '顺序铁律：先 zipalign 再 apksigner。'
                                            + '之后凡是又动过 ZIP 字节的（比如 lu_zipfix 改 STORED / 重新对齐），'
                                            + '签名都会失效 —— lu_zipfix 已默认自动重签，别手动改包。'
                                    };
                                });
                        });
                });
            });
        });
    }

    // ---------- 一键流水线 ----------
    function lu_all(params) {
        var apk = params.apk_path;
        if (!apk) throw new Error('缺少 apk_path');
        var work = params.work_dir || (dirname(apk) + '/' + basenameNoExt(apk) + '脱修');
        var realApp = params.real_app || '';
        // 设备没 apktool 时，可以先用 apk_reverse_decode / MT 管理器解包，把目录填进来跳过解包
        var decoded = params.decoded_dir || (work + '/decoded');
        var skipDecode = !!params.decoded_dir;
        var outApk = work + '/rebuilt.apk';
        var steps = [];
        // 默认预演：只扫描报告不写文件。删特征有风险（误删正常代码会闪退），
        // 必须先看完清单再决定写入
        var apply = params.apply === 'true' || params.apply === true;

        function step(name, fn) {
            return Promise.resolve().then(fn).then(function (r) {
                steps.push({ 步骤: name, 状态: '完成', 结果: r });
                return r;
            }, function (e) {
                steps.push({ 步骤: name, 状态: '失败', 错误: (e && e.message) || String(e) });
                throw e;
            });
        }

        return exec('mkdir -p ' + q(work), 15000)
            .then(function () { return step('1.预判', function () { return lu_probe({ apk_path: apk }); }); })
            .then(function () {
                if (skipDecode) {
                    steps.push({ 步骤: '2.解包', 状态: '跳过', 结果: '使用外部已解包目录 ' + decoded });
                    return null;
                }
                return step('2.解包', function () { return lu_decode({ apk_path: apk, output_dir: decoded, force: 'true' }); });
            })
            .then(function (r) {
                if (r && r.success === false) {
                    return {
                        中断: true, 原因: r.原因, 替代方案: r.替代方案, 已完成步骤: steps,
                        怎么绕过去: '先用 apk_reverse_decode 或 MT 管理器把这个 APK 解包，'
                                   + '拿到目录路径后，再调 lu_all 并把 decoded_dir 填成那个目录即可（会跳过解包这一步）'
                    };
                }
                return null;
            })
            .then(function (abort) {
                if (abort) return abort;
                return step('3.扫描抽取率', function () { return lu_scan({ decoded_dir: decoded }); })
                    // ---- 预演清单：各步只报告不写入 ----
                    .then(function () { return step('4a.邪修回填清单(预演)', function () { return lu_fill_oncreate({ decoded_dir: decoded, dry_run: 'true' }); }); })
                    .then(function () { return step('4b.清壳特征清单(预演)', function () { return lu_stub_clean({ decoded_dir: decoded, dry_run: 'true' }); }); })
                    .then(function (r4b) {
                        return step('4c.入口+壳代理清单(预演)', function () { return lu_manifest_fix({ decoded_dir: decoded, real_app: realApp, dry_run: 'true', drop_proxy: 'true' }); })
                            .then(function (r4c) { return { stub: r4b || {}, mf: r4c || {} }; });
                    })
                    .then(function (plan) {
                        if (!apply) {
                            // 预演模式到此为止：不碰任何文件
                            return {
                                模式: '预演（未修改任何文件）',
                                工作目录: work,
                                解包目录: decoded,
                                步骤: steps,
                                待确认清单: {
                                    邪修回填数量: (function () {
                                        var s4a = steps.filter(function (s) { return s.步骤.indexOf('4a') === 0; })[0];
                                        return (s4a && s4a.结果 && s4a.结果.回填数量) || 0;
                                    })(),
                                    将删除壳特征数: (plan.stub && plan.stub.删除语句数) || 0,
                                    入口是否加固桩: !!(plan.mf && plan.mf.是否加固桩),
                                    入口现状: (plan.mf && plan.mf.入口) || (plan.mf && plan.mf.原入口) || '',
                                    入口将改为: (plan.mf && plan.mf.真实入口) || '(无需修改)',
                                    推测来源: (plan.mf && plan.mf.推测来源) || '',
                                    待删壳代理activity数: (plan.mf && plan.mf['壳代理activity_待删数']) || 0,
                                    待删壳代理清单: (plan.mf && plan.mf['壳代理activity_待删清单']) || []
                                },
                                下一步: '把上面「待确认清单」逐条看一遍（尤其 4b 要删的语句有没有误伤正常代码、'
                                       + '壳代理 activity 清单里有没有混进业务自己的 Activity）。'
                                       + '确认无误后，用同样的参数再跑一次 lu_all 并把 apply 设为 true，才会真正写入并重打包。',
                                重要提醒: '预演模式下没有生成 APK。直接拿原始 APK 去装是没有修复效果的。'
                            };
                        }
                        // ---- apply=true：真正写入 ----
                        return step('5.邪修回填onCreate(写入)', function () { return lu_fill_oncreate({ decoded_dir: decoded, dry_run: 'false' }); })
                            .then(function () { return step('6.清壳特征(写入)', function () { return lu_stub_clean({ decoded_dir: decoded, dry_run: 'false' }); }); })
                            .then(function () { return step('7.入口+壳代理清理(写入)', function () { return lu_manifest_fix({ decoded_dir: decoded, real_app: realApp, dry_run: 'false', drop_proxy: 'true' }); }); })
                            .then(function () { return step('8.重打包签名', function () { return lu_rebuild({ decoded_dir: decoded, output_apk: outApk }); }); })
                            .then(function () {
                                return {
                                    模式: '已写入',
                                    工作目录: work,
                                    解包目录: decoded,
                                    输出APK: outApk,
                                    步骤: steps,
                                    下一步: '装上去跑冒烟测试；闪退就抓日志（记得 logcat 过滤级别用 *:V），'
                                           + '把崩溃栈交给 AI 定位缺失的类/方法，补完再重打包，循环直到不崩。',
                                    重要提醒: '本流水线做的是「解包后的 smali 层修复」。如果目标 APK 的方法体被抽空（lu_scan 抽取率高），'
                                            + '邪修大法只能救回约 30% 的情况，剩下的要靠抓日志 + AI 循环补方法体。',
                                    新版360额外提醒: '如果你处理的是【新版 360】（桩类 com.stub.StubApp、真抽取壳），'
                                            + '本流水线还差三步（本包已提供对应工具，需手工串一下，因为要先看脱壳产物）：'
                                            + '① lu_repack_assemble —— 把脱壳产物 + APK 内未加固业务 dex 重新连续编号；'
                                            + '② lu_stub_scaffold —— 生成 StubApp 兼容桩（不改这步业务代码会 NoClassDefFoundError）；'
                                            + '③ 把壳 so（assets/libjiagu_a64.so 等）和解包目录里的壳 dex 删掉再回编译。'
                                };
                            });
                    });
            })
            .catch(function (e) {
                return { 中断: true, 错误: (e && e.message) || String(e), 已完成步骤: steps };
            });
    }


    // ---------- 方法体回填器（纯 Python，已用真实 dex 验证：7018/7018 方法零字节差异） ----------
    var PY_FILL = String.raw`
# -*- coding: utf-8 -*-
"""
DEX code_item 回填器（纯标准库，python3 直接跑）

作用：把 FART / frida_fart / FunDex2 等主动调用脱壳产出的
      *_ins_<method_idx>.bin 按方法编号回填回 dex，并重算 SHA-1 / Adler-32。

用法：
    python3 dexfill.py <dex路径> <bin目录> <输出路径> [1=预览不写入|0=写入]

bin 文件名支持：
    1321896_ins_4714.bin   （FART 标准命名，4714 = method_idx）
    4714.bin               （纯编号）
"""
import sys
import os
import re
import json
import zlib
import hashlib
import struct

# ---------------- 基础读取 ----------------

def u2(b, o):
    return struct.unpack_from('<H', b, o)[0]

def u4(b, o):
    return struct.unpack_from('<I', b, o)[0]

def read_uleb(b, o):
    """返回 (值, 新偏移)"""
    result = 0
    shift = 0
    while True:
        x = b[o]
        o += 1
        result |= (x & 0x7F) << shift
        if not (x & 0x80):
            break
        shift += 7
    return result, o

def enc_uleb(v):
    out = bytearray()
    while True:
        x = v & 0x7F
        v >>= 7
        if v:
            out.append(x | 0x80)
        else:
            out.append(x)
            break
    return bytes(out)

def enc_uleb_pad(v, n):
    """强制编码成 n 字节（用于原地改写 code_off 且不能改变长度时）"""
    out = bytearray()
    for i in range(n - 1):
        out.append((v & 0x7F) | 0x80)
        v >>= 7
    out.append(v & 0x7F)
    return bytes(out)

# ---------------- dex 解析 ----------------

def check_magic(data):
    return data[:4] == b'dex\n' and data[4:8] in (b'035\x00', b'037\x00', b'038\x00', b'039\x00')

def read_sleb(b, o):
    """有符号 LEB128：try/catch 的 handler 数量用它编码"""
    result = 0
    shift = 0
    while True:
        x = b[o]
        o += 1
        result |= (x & 0x7F) << shift
        shift += 7
        if not (x & 0x80):
            if x & 0x40:          # 符号位为 1 → 负数
                result -= (1 << shift)
            break
    return result, o

def code_item_size(data, off):
    """算一个 code_item 占多少字节（含 try/catch 与异常处理器表）"""
    tries_size = u2(data, off + 6)
    insns_size = u4(data, off + 12)
    n = 16 + insns_size * 2
    if tries_size > 0:
        if insns_size % 2 == 1:
            n += 2                      # 指令数为奇数时的 2 字节对齐填充
        p = off + n + tries_size * 8    # try_item 每项 8 字节
        cnt, p = read_uleb(data, p)     # encoded_catch_handler_list 的 handlers 个数
        for _ in range(cnt):
            size, p = read_sleb(data, p)
            if size <= 0:
                # size<=0 表示有 catch_all，负的绝对值是普通 handler 数
                for _ in range(-size):
                    _, p = read_uleb(data, p)   # type_idx
                    _, p = read_uleb(data, p)   # addr
                _, p = read_uleb(data, p)       # catch_all_addr
            else:
                for _ in range(size):
                    _, p = read_uleb(data, p)
                    _, p = read_uleb(data, p)
        n = p - off
    return n

def parse_methods(data):
    """
    返回 {method_idx: [code_off, code_off_uleb起始位置, code_off_uleb字节数]}
    只收录真正有 code_item 的方法（code_off != 0）
    """
    if not check_magic(data):
        raise ValueError('不是合法的 dex 文件（magic 不对）: %r' % data[:8])

    method_ids_size = u4(data, 0x58)
    class_defs_off = u4(data, 0x64)
    class_defs_size = u4(data, 0x60)

    table = {}
    for i in range(class_defs_size):
        cd = class_defs_off + i * 32
        if cd + 32 > len(data):
            break
        class_data_off = u4(data, cd + 24)
        if class_data_off == 0:
            continue
        o = class_data_off
        sf, o = read_uleb(data, o)
        inf, o = read_uleb(data, o)
        dm, o = read_uleb(data, o)
        vm, o = read_uleb(data, o)
        for _ in range(sf):
            _, o = read_uleb(data, o)
            _, o = read_uleb(data, o)
        for _ in range(inf):
            _, o = read_uleb(data, o)
            _, o = read_uleb(data, o)

        prev = 0
        for _ in range(dm + vm):
            diff, o = read_uleb(data, o)
            _, o = read_uleb(data, o)          # access_flags
            co_pos = o                          # code_off 这个 uleb 的起始位置
            code_off, o2 = read_uleb(data, o)
            co_len = o2 - co_pos
            prev += diff
            if code_off != 0 and prev < method_ids_size:
                table[prev] = [code_off, co_pos, co_len]
            o = o2
    return table

# ---------------- bin 收集 ----------------

BIN_RE = re.compile(r'^(?:.*?[_-])?ins[_-]?(\d+)\.bin$', re.I)
PLAIN_RE = re.compile(r'^(\d+)\.bin$')

def collect_bins(bin_dir, prefix=None):
    """prefix 不为空时只收 <prefix>_ins_<idx>.bin，避免把别的 dex 的 bin 混进来"""
    m = {}
    if not os.path.isdir(bin_dir):
        return m
    pat = re.compile(r'^%s_ins[_-]?(\d+)\.bin$' % re.escape(str(prefix)), re.I) if prefix else None
    for fn in sorted(os.listdir(bin_dir)):
        if not fn.lower().endswith('.bin'):
            continue
        mm = None
        if pat:
            mm = pat.match(fn)
        else:
            mm = BIN_RE.match(fn) or PLAIN_RE.match(fn)
        if mm:
            idx = int(mm.group(1))
            m.setdefault(idx, os.path.join(bin_dir, fn))
    return m

# ---------------- 回填 ----------------

def looks_like_code_item(blob, data, code_off):
    """判断 bin 是完整 code_item 还是裸指令流"""
    if len(blob) < 16:
        return False
    insns_size = u4(blob, 12)
    need = 16 + insns_size * 2
    if need > len(blob):
        return False
    # 寄存器数量得合理（0..65535），outs 通常不超过 255
    regs = u2(blob, 0)
    if regs > 0xFFFF:
        return False
    # 指令条数不能离谱
    if insns_size == 0 or insns_size > 0x100000:
        return False
    return True

def backfill(dex_path, bin_dir, out_path, dry_run, prefix=None):
    with open(dex_path, 'rb') as f:
        data = bytearray(f.read())

    table = parse_methods(data)
    bins = collect_bins(bin_dir, prefix)

    stat = {
        'dex': dex_path,
        'bin目录': bin_dir,
        'dex里有代码的方法数': len(table),
        'bin文件数': len(bins),
        '成功回填': 0,
        '原地写入': 0,
        '追加到文件尾': 0,
        '空间不足跳过': 0,
        'bin不是合法code_item跳过': 0,
        '无对应bin跳过': 0,
        '明细': []
    }

    for idx in sorted(bins.keys()):
        if idx not in table:
            stat['无对应bin跳过'] += 0  # bin 有但 dex 没这个方法，单独记
            stat['明细'].append({'method_idx': idx, '结果': 'dex里没有这个编号的方法'})
            continue
        code_off, co_pos, co_len = table[idx]
        if code_off + 16 > len(data):
            stat['明细'].append({'method_idx': idx, '结果': 'code_off 越界'})
            continue

        with open(bins[idx], 'rb') as f:
            blob = f.read()

        if not looks_like_code_item(blob, data, code_off):
            # 当成裸指令：借用原 code_item 的头
            old_regs = u2(data, code_off)
            old_ins = u2(data, code_off + 2)
            old_outs = u2(data, code_off + 4)
            old_tries = u2(data, code_off + 6)
            old_dbg = u4(data, code_off + 8)
            if len(blob) % 2 != 0:
                stat['bin不是合法code_item跳过'] += 1
                stat['明细'].append({'method_idx': idx, '结果': 'bin 长度不是偶数且不是完整 code_item'})
                continue
            hdr = struct.pack('<HHHHII', old_regs, old_ins, old_outs, 0, old_dbg, len(blob) // 2)
            blob = hdr + blob

        old_size = code_item_size(data, code_off)
        new_size = len(blob)

        if new_size <= old_size:
            data[code_off:code_off + new_size] = blob
            # 多出来的旧空间清零，避免残留脏字节
            for i in range(code_off + new_size, code_off + old_size):
                data[i] = 0
            stat['原地写入'] += 1
            stat['成功回填'] += 1
            stat['明细'].append({'method_idx': idx, '结果': '原地写入', '新尺寸': new_size, '原尺寸': old_size})
        else:
            # 追加到文件尾，并改写 code_off（uleb 长度不能超过原来的）
            new_off = len(data)
            enc = enc_uleb(new_off)
            if len(enc) > co_len:
                enc2 = enc_uleb_pad(new_off, co_len)
                if (enc2[-1] & 0x80) or len(enc2) != co_len:
                    stat['空间不足跳过'] += 1
                    stat['明细'].append({'method_idx': idx, '结果': 'code_off 放不下新偏移，需人工处理',
                                         '新尺寸': new_size, '原尺寸': old_size})
                    continue
                enc = enc2
            data.extend(blob)
            data[co_pos:co_pos + co_len] = enc + b'\x00' * (co_len - len(enc))
            stat['追加到文件尾'] += 1
            stat['成功回填'] += 1
            stat['明细'].append({'method_idx': idx, '结果': '追加到文件尾', '新尺寸': new_size, '原尺寸': old_size})

    # 没 bin 的方法单独统计
    stat['有代码但没bin的方法数'] = sum(1 for i in table if i not in bins)

    # ---- 重算校验 ----
    # 顺序很关键：
    #   SHA-1   覆盖 [0x20, EOF)         —— 不含签名和校验和本身
    #   Adler32 覆盖 [0x0c, EOF)         —— 【包含签名区】
    # 所以必须先写 SHA-1，再算 Adler-32，否则校验和是过期的，ART 会拒收。
    sig = hashlib.sha1(bytes(data[0x20:])).digest()
    data[0x0c:0x0c + 20] = sig
    chk = zlib.adler32(bytes(data[0x0c:])) & 0xFFFFFFFF
    struct.pack_into('<I', data, 0x08, chk)
    # file_size
    struct.pack_into('<I', data, 0x20, len(data))

    stat['SHA1'] = sig.hex()
    stat['Adler32'] = '0x%08x' % chk
    stat['输出大小'] = len(data)

    if not dry_run:
        with open(out_path, 'wb') as f:
            f.write(bytes(data))
        stat['输出路径'] = out_path
        # 自检
        with open(out_path, 'rb') as f:
            v = f.read()
        stat['自检_签名一致'] = (hashlib.sha1(v[0x20:]).digest() == v[0x0c:0x20])
        stat['自检_校验和一致'] = ((zlib.adler32(v[0x0c:]) & 0xFFFFFFFF) == u4(v, 0x08))
        stat['自检_magic正常'] = check_magic(v)
    else:
        stat['输出路径'] = '(预览模式，未写入)'

    stat['明细'] = stat['明细'][:40]
    return stat


DEX_RE_PLAIN = re.compile(r'^(\d+)_dex_file\.dex$')
DEX_RE_EXEC = re.compile(r'^(\d+)_dex_file_execute\.dex$')

def fart_import(art_dir, out_dir, dry_run):
    """吃一整个 FART/frida_fart 产物目录：自动配对 dex 与 ins bin，逐个回填"""
    if not os.path.isdir(art_dir):
        raise ValueError('产物目录不存在: ' + art_dir)
    plain, execute = {}, {}
    for fn in sorted(os.listdir(art_dir)):
        m = DEX_RE_PLAIN.match(fn)
        if m:
            plain[int(m.group(1))] = os.path.join(art_dir, fn)
            continue
        m = DEX_RE_EXEC.match(fn)
        if m:
            execute[int(m.group(1))] = os.path.join(art_dir, fn)

    prefixes = sorted(set(list(plain.keys()) + list(execute.keys())))
    if not prefixes:
        return {
            'ok': True, '产物目录': art_dir, '找到的dex组数': 0,
            '提示': '目录里没有 <数字>_dex_file.dex 或 <数字>_dex_file_execute.dex。'
                    '确认这是 FART/frida_fart 的产物目录；如果是别的工具产出的，'
                    '改用 lu_codeitem_fill 手动指定 dex 与 bin 目录。'
        }

    os.makedirs(out_dir, exist_ok=True)
    results = []
    for p in prefixes:
        dex = plain.get(p) or execute.get(p)
        src = 'dex_file' if p in plain else 'dex_file_execute'
        out = os.path.join(out_dir, '%s_filled.dex' % p)
        try:
            r = backfill(dex, art_dir, out, dry_run, prefix=p)
            r['组号'] = p
            r['来源'] = src
            r['ok'] = True
        except Exception as e:
            r = {'ok': False, '组号': p, '来源': src, 'dex': dex, '错误': str(e)}
        results.append(r)

    okc = sum(1 for r in results if r.get('ok') and r.get('成功回填', 0) > 0)
    return {
        'ok': True,
        '产物目录': art_dir,
        '输出目录': out_dir,
        '模式': '只预览未写入' if dry_run else '已写入',
        '找到的dex组数': len(prefixes),
        '有成功回填的组数': okc,
        '各组结果': results
    }


def main():
    if len(sys.argv) < 2:
        print('用法1: python3 dexfill.py fill <dex> <bin目录> <输出dex> [1=预览|0=写入]')
        print('用法2: python3 dexfill.py import <FART产物目录> <输出目录> [1|0]')
        sys.exit(1)
    mode = sys.argv[1]
    try:
        if mode == 'fill':
            r = backfill(sys.argv[2], sys.argv[3], sys.argv[4],
                         len(sys.argv) > 5 and sys.argv[5] == '1')
            r['ok'] = True
        elif mode == 'import':
            r = fart_import(sys.argv[2], sys.argv[3],
                            len(sys.argv) > 4 and sys.argv[4] == '1')
        else:
            r = {'ok': False, '错误': '未知模式: ' + mode}
    except Exception as e:
        r = {'ok': False, '错误': str(e)}
    print('LURESULT:' + json.dumps(r, ensure_ascii=False))


if __name__ == '__main__':
    main()

`;

    function lu_codeitem_fill(params) {
        var dex = params.dex_path, bind = params.bin_dir, out = params.output_path;
        if (!dex || !bind || !out) throw new Error('缺少 dex_path / bin_dir / output_path');
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        return runPy(PY_FILL, 'fill ' + q(dex) + ' ' + q(bind) + ' ' + q(out) + ' ' + (dry ? '1' : '0'), 900000)
            .then(function (r) {
                if (!r || r.ok === false) throw new Error((r && r.错误) || '回填失败（没拿到结果，确认 python3 可用、路径正确）');
                var ok = r.成功回填 || 0;
                r['一句话'] = ok > 0
                    ? ('回填成功 ' + ok + ' 个方法。有 ' + (r['有代码但没bin的方法数'] || 0) + ' 个方法没有对应 bin（说明那次脱壳没触发到它们）')
                    : '一个都没回填上。检查：bin 文件名是不是 <数字>_ins_<编号>.bin；这些编号在不在 dex 里';
                r['下一步'] = ok > 0
                    ? '把输出的 dex 用 JADX 打开看看 onCreate 里有没有真代码。有就说明成功；还是空的说明这次脱壳没触发主动调用，回去检查 FunDex2/frida_fart 的作用域和日志。'
                    : '先确认 bin 和 dex 是同一批产物（文件名前缀数字要能对上）';
                return r;
            });
    }

    function lu_fart_import(params) {
        var dir = params.artifact_dir, out = params.output_dir;
        if (!dir || !out) throw new Error('缺少 artifact_dir / output_dir');
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        return runPy(PY_FILL, 'import ' + q(dir) + ' ' + q(out) + ' ' + (dry ? '1' : '0'), 1800000)
            .then(function (r) {
                if (!r || r.ok === false) throw new Error((r && r.错误) || '导入失败');
                if (r['找到的dex组数'] === 0) {
                    r['下一步'] = '目录里没找到 FART 格式的 dex。看看产物目录到底在哪（常见位置 /data/data/<包名>/），'
                                + '或者直接告诉我里面有哪些文件，我帮你对上格式。';
                    return r;
                }
                var tot = 0;
                (r['各组结果'] || []).forEach(function (g) { tot += (g && g['成功回填']) || 0; });
                r['总计回填方法数'] = tot;
                r['下一步'] = tot > 0
                    ? ('共回填 ' + tot + ' 个方法，输出在 ' + out + '。挑最大的那个 _filled.dex 用 JADX 打开验证。')
                    : '一组都没回填成功，把返回的「各组结果」贴给我看';
                return r;
            });
    }

    var PY_DC = String.raw`
# -*- coding: utf-8 -*-
"""
dump 产物体检器 + 安装前打包修正器

三个模式：
    python3 dc2.py check    <dex> [原始apk|-] [业务包名|-]      # 单个体检
    python3 dc2.py dircheck <dir> [原始apk|-] [业务包名|-]      # 整个目录体检
    python3 dc2.py zipfix   <输入apk> <输出apk> [install 1|0]   # lib/*.so 改 STORED + 推到 /data/local/tmp
    python3 dc2.py basecheck <当前轮目录> <上一轮目录>          # 两轮 dex 比对，判断补码结束没有

解决的两个真实坑：
  坑1「壳容器」：dump 出来的可能根本不是真 dex，而是壳的容器。
     真实案例：FunDex2 吐出 16841205 字节，和 APK 里壳 classes.dex(16841201) 几乎一样大，
     里面只有 1684 个类（实际业务 16619 个）——白干半天。
  坑2「装不上」：原 Manifest 没有 extractNativeLibs=true 时，lib/*.so 必须是 ZIP_STORED，
     否则 pm install 报 Failed to extract native libraries。
"""
import sys, os, re, json, struct, zlib, hashlib, zipfile

MAGIC_MIN = 0x70


def u2(b, o):
    return struct.unpack_from('<H', b, o)[0]


def u4(b, o):
    return struct.unpack_from('<I', b, o)[0]


STUB_PAT = re.compile(r'(StubApp|DtcLoader|frezrik|qihoo|secneo|jiagu)', re.I)


def read_mutf8(b, o):
    """读 dex 的 MUTF-8 字符串，返回 (str, 新偏移)"""
    size, p, shift = 0, o, 0
    while True:
        try:
            x = b[p]
        except Exception:
            return '', p
        p += 1
        size |= (x & 0x7F) << shift
        if not (x & 0x80):
            break
        shift += 7
    raw = b[p:p + size]
    try:
        return raw.decode('utf-8', 'replace'), p + size + 1
    except Exception:
        return '', p + size + 1


def strings_sample(data, limit=4000):
    try:
        off, size = u4(data, 0x3C), u4(data, 0x38)
    except Exception:
        return []
    out = []
    for i in range(min(size, limit)):
        try:
            so = u4(data, off + i * 4)
            s, _ = read_mutf8(data, so)
            out.append(s)
        except Exception:
            break
    return out


def class_descriptors(data):
    """精确遍历 class_defs -> type_ids -> string_ids，拿到真正的类名列表"""
    try:
        cls_off, cls_sz = u4(data, 0x64), u4(data, 0x60)
        typ_off = u4(data, 0x44)
        str_off = u4(data, 0x3C)
    except Exception:
        return []
    out = []
    for i in range(cls_sz):
        try:
            cd = cls_off + i * 32
            if cd + 32 > len(data):
                break
            ci = u4(data, cd)
            si = u4(data, typ_off + ci * 4)
            so = u4(data, str_off + si * 4)
            s, _ = read_mutf8(data, so)
            out.append(s)
        except Exception:
            break
    return out


def top_packages(strs, topn=8):
    cnt = {}
    for s in strs:
        if s.startswith('L') and s.endswith(';') and '/' in s:
            parts = s[1:-1].split('/')
            if len(parts) >= 2:
                key = '.'.join(parts[:min(3, len(parts) - 1)])
                cnt[key] = cnt.get(key, 0) + 1
    return sorted(cnt.items(), key=lambda x: -x[1])[:topn]


def apk_dex_sizes(apk_path):
    out = []
    try:
        z = zipfile.ZipFile(apk_path)
        for n in z.namelist():
            if n.endswith('.dex'):
                try:
                    out.append((n, z.getinfo(n).file_size))
                except Exception:
                    pass
        z.close()
    except Exception:
        pass
    return out


def check(dex_path, apk_path=None, pkg=None):
    if not os.path.isfile(dex_path):
        return {'ok': False, '文件': dex_path, '结论': '❌ 文件不存在: ' + dex_path}
    with open(dex_path, 'rb') as f:
        d = f.read()
    real_size = len(d)
    r = {'文件': dex_path, '大小': real_size}

    if len(d) < MAGIC_MIN:
        return {'ok': True, '文件': dex_path, '大小': real_size,
                '结论': '❌ 文件太小（%d 字节），根本不是 dex' % real_size}

    file_size = u4(d, 0x20)
    header_size = u4(d, 0x24)
    endian = u4(d, 0x28)
    map_off = u4(d, 0x34)
    str_sz, str_off = u4(d, 0x38), u4(d, 0x3C)
    typ_sz, typ_off = u4(d, 0x40), u4(d, 0x44)
    pro_sz, pro_off = u4(d, 0x48), u4(d, 0x4C)
    fld_sz, fld_off = u4(d, 0x50), u4(d, 0x54)
    mtd_sz, mtd_off = u4(d, 0x58), u4(d, 0x5C)
    cls_sz, cls_off = u4(d, 0x60), u4(d, 0x64)

    r.update({
        'magic_正常': (d[:4] == b'dex\n'),
        'file_size字段': file_size,
        '实际大小': real_size,
        'header_size': header_size,
        'endian_tag': hex(endian),
        'string_ids': '%d@%s' % (str_sz, hex(str_off)),
        'type_ids': '%d@%s' % (typ_sz, hex(typ_off)),
        'proto_ids': '%d@%s' % (pro_sz, hex(pro_off)),
        'field_ids': '%d@%s' % (fld_sz, hex(fld_off)),
        'method_ids': '%d@%s' % (mtd_sz, hex(mtd_off)),
        'class_defs': '%d@%s' % (cls_sz, hex(cls_off)),
    })

    problems = []
    if not r['magic_正常']:
        problems.append('magic 不对，不是 dex')
    if header_size != 0x70:
        problems.append('header_size=%s（正常 0x70）' % header_size)
    if endian != 0x12345678:
        problems.append('endian_tag=%s（正常 0x12345678）' % hex(endian))
    if file_size != real_size:
        problems.append('file_size(%d) 与实际(%d) 不符' % (file_size, real_size))
    if str_sz == 0:
        problems.append('string_ids_size=0（索引链断了，基本不可修）')
    if typ_sz == 0:
        problems.append('type_ids_size=0')
    if cls_sz == 0:
        problems.append('class_defs_size=0，一个类都没有')
    for name, sz, off, unit in (('string_ids', str_sz, str_off, 4), ('type_ids', typ_sz, typ_off, 4),
                                ('proto_ids', pro_sz, pro_off, 12), ('field_ids', fld_sz, fld_off, 8),
                                ('method_ids', mtd_sz, mtd_off, 8), ('class_defs', cls_sz, cls_off, 32)):
        if sz and (off == 0 or off + sz * unit > real_size):
            problems.append('%s 表越界 (off=%s size=%d)' % (name, hex(off), sz))
    if map_off == 0 or map_off >= real_size:
        problems.append('map_off=%s 离谱' % hex(map_off))

    r['header问题'] = problems
    r['header_正常'] = (len(problems) == 0)

    try:
        r['SHA1_一致'] = (hashlib.sha1(d[0x20:]).digest() == d[0x0c:0x20])
        r['Adler32_一致'] = ((zlib.adler32(d[0x0c:]) & 0xFFFFFFFF) == u4(d, 0x08))
    except Exception:
        r['SHA1_一致'] = r['Adler32_一致'] = False

    descs = class_descriptors(d) if (str_sz and cls_sz) else []
    strs = strings_sample(d) if str_sz else []
    r['类描述符数'] = len(descs)
    r['顶层包名TOP'] = top_packages(descs or strs)
    r['壳特征类'] = sorted(set(s for s in (descs or strs) if STUB_PAT.search(s)))[:10]
    r['类样例'] = descs[:5]

    biz_hit = None
    if pkg:
        key = pkg.replace('.', '/')
        biz_hit = sum(1 for s in descs if key in s)
        r['业务包名'] = pkg
        r['业务包名命中数'] = biz_hit

    if apk_path and os.path.isfile(apk_path):
        dexes = apk_dex_sizes(apk_path)
        r['原APK内dex'] = dexes
        for n, sz in dexes:
            if sz > 0 and abs(sz - real_size) / float(sz) < 0.02:
                r['疑似容器'] = ('dump 出 %d 字节，与 原APK 内 %s(%d 字节) 几乎一样大 '
                                '→ 高度怀疑是壳容器而不是真 dex') % (real_size, n, sz)
                break
    elif apk_path:
        r['原APK读取'] = '失败：文件不存在 ' + apk_path

    if not r['header_正常']:
        verdict = '❌ 损坏：' + '；'.join(problems[:4]) + ' → 这个产物没法直接用，换脱壳方法'
    elif r.get('疑似容器'):
        verdict = '❌ 疑似壳容器：' + r['疑似容器'] + ' → 本次 dump 作废，换方法重来'
    elif cls_sz < 200:
        verdict = '⚠ 类数只有 %d，太少。可能只 dump 到桩 dex / 部分 dex' % cls_sz
    elif pkg and biz_hit == 0:
        verdict = ('⚠ header 正常、类数 %d，但没搜到业务包名 %s。确认包名写对没，'
                   '或业务类在别的 dex 里' % (cls_sz, pkg))
    elif biz_hit:
        verdict = ('✅ 可用：header 正常、%d 个类、业务包名命中 %d 次 → 真 dex，可以往下走'
                   % (cls_sz, biz_hit))
    else:
        verdict = ('✅ 看起来可用：header 正常、%d 个类。没给业务包名，没法确认是不是业务 dex，'
                   '建议带上包名再验一次' % cls_sz)

    r['结论'] = verdict
    r['ok'] = True
    return r


def dircheck(dir_path, apk_path=None, pkg=None):
    if not os.path.isdir(dir_path):
        return {'ok': False, '错误': '目录不存在: ' + dir_path}
    files = []
    for root, _dirs, names in os.walk(dir_path):
        for n in sorted(names):
            if n.endswith('.dex'):
                files.append(os.path.join(root, n))
    if not files:
        return {'ok': True, '目录': dir_path, 'dex数量': 0,
                '结论': '❌ 这个目录里一个 .dex 都没有。脱壳产物目录找错了，'
                        '常见位置：/sdcard/Download/ 下、/data/data/<包名>/ 下、或脱壳模块自己指定的路径'}

    items = []
    for f in files:
        try:
            items.append(check(f, apk_path, pkg))
        except Exception as e:
            items.append({'文件': f, '结论': '❌ 体检时出错: %s' % e})

    good = [i for i in items if i.get('结论', '').startswith('✅')]
    bad = [i for i in items if not i.get('结论', '').startswith('✅')]
    total_cls = sum(i.get('类描述符数', 0) for i in items)

    summary = {
        'ok': True,
        '目录': dir_path,
        'dex数量': len(files),
        '可用数量': len(good),
        '作废数量': len(bad),
        '类数合计': total_cls,
        '明细': [{'文件': i.get('文件'), '大小': i.get('大小'), '类数': i.get('类描述符数'),
                  '结论': i.get('结论')} for i in items],
    }
    if len(good) == 0:
        summary['结论'] = ('❌ 全部作废：%d 个 dex 没一个能用。最常见原因是 dump 到壳容器 / 时机不对。'
                           '换脱壳手段重来，别在坏产物上浪费时间' % len(files))
        summary['下一步'] = '换模块或换时机重 dump；老版 360（桩类是 com.frezrik.jiagu.StubApp）建议直接走云端'
    elif len(good) < len(files):
        summary['结论'] = ('⚠ 部分可用：%d 个可用 / %d 个作废。用可用的那几个，'
                           '回填时注意 dex 编号必须连续（classes2/3/4…，跳号会加载失败）'
                           % (len(good), len(files)))
        summary['下一步'] = '把可用的 dex 挑出来，按大小排序重新命名 classes2.dex / classes3.dex …'
    else:
        summary['结论'] = ('✅ 全部可用：%d 个 dex、共 %d 个类。可以进回填 / 解包 / 修 Manifest / 重打包'
                           % (len(good), total_cls))
        summary['下一步'] = '继续下一步。若是主动调用产物（有 _ins_*.bin）走 lu_fart_import / lu_codeitem_fill'
    return summary


def zipfix(in_apk, out_apk, do_install=False):
    """把 lib/*.so 改成 ZIP_STORED，解决 Failed to extract native libraries"""
    if not os.path.isfile(in_apk):
        return {'ok': False, '错误': '输入 APK 不存在: ' + in_apk}
    r = {'输入': in_apk, '输出': out_apk}
    changed, total_so = [], 0
    try:
        zin = zipfile.ZipFile(in_apk)
        out_dir = os.path.dirname(os.path.abspath(out_apk))
        if out_dir and not os.path.isdir(out_dir):
            os.makedirs(out_dir)
        zout = zipfile.ZipFile(out_apk, 'w', zipfile.ZIP_DEFLATED)
        for src in zin.infolist():
            n = src.filename
            is_so = n.startswith('lib/') and n.endswith('.so')
            if is_so:
                total_so += 1
                if src.compress_type != zipfile.ZIP_STORED:
                    changed.append(n)
            ct = zipfile.ZIP_STORED if is_so else src.compress_type
            # 必须新建 ZipInfo：直接复用 zin 的对象会被 writestr 改写 CRC，
            # 导致后续读源 zip 报 "Bad CRC-32"
            zi = zipfile.ZipInfo(n, date_time=src.date_time)
            zi.compress_type = ct
            zi.external_attr = src.external_attr
            zi.internal_attr = src.internal_attr
            zi.create_system = src.create_system
            try:
                if src.comment:
                    zi.comment = src.comment
            except Exception:
                pass
            zout.writestr(zi, zin.read(n))
        zout.close()
        zin.close()
    except Exception as e:
        return {'ok': False, '错误': '重写 APK 失败: %s' % e, '输入': in_apk}

    # 回读校验，确认 .so 真的变成 STORED 且能正常解出
    try:
        vz = zipfile.ZipFile(out_apk)
        still_bad = [i.filename for i in vz.infolist()
                     if i.filename.startswith('lib/') and i.filename.endswith('.so')
                     and i.compress_type != zipfile.ZIP_STORED]
        vz.testzip()
        vz.close()
        r['回读校验'] = '通过' if not still_bad else ('仍有 %d 个 .so 非 STORED' % len(still_bad))
    except Exception as e:
        r['回读校验'] = '失败: %s' % e

    r['so总数'] = total_so
    r['改为STORED的数量'] = len(changed)
    r['被改的文件'] = changed[:20]
    r['输出大小'] = os.path.getsize(out_apk) if os.path.isfile(out_apk) else 0

    if total_so == 0:
        r['结论'] = '⚠ 这个 APK 里没有 lib/*.so，不需要本步（那 install 失败是别的原因，看 logcat）'
        return r

    tmp = '/data/local/tmp/' + os.path.basename(out_apk)
    r['推送路径'] = tmp
    try:
        import shutil
        shutil.copyfile(out_apk, tmp)
        r['已推送到'] = tmp
        r['安装命令'] = 'pm install -r -t ' + tmp
    except Exception as e:
        r['推送失败'] = str(e)
        r['安装命令'] = ('adb shell 里先 cp %s /data/local/tmp/ 再 pm install -r -t /data/local/tmp/%s'
                         % (out_apk, os.path.basename(out_apk)))

    r['结论'] = ('✅ %d 个 .so 已改为 ZIP_STORED（原来 DEFLATED 会导致 '
                 'INSTALL_FAILED_INVALID_APK: Failed to extract native libraries）' % len(changed)
                 if changed else '✅ .so 本来就是 STORED，无需修改')
    r['下一步'] = ('SELinux 下不能直接 pm install 外部路径，必须先 cp 到 /data/local/tmp/。'
                   '已帮你推好，直接跑安装命令即可')
    r['ok'] = True
    return r


def dex_fp(path):
    """单个 dex 的指纹：大小 + 全文件 SHA-1（取前 16 位足够）"""
    try:
        st = os.path.getsize(path)
        h = hashlib.sha1()
        with open(path, 'rb') as f:
            while True:
                b = f.read(1048576)
                if not b:
                    break
                h.update(b)
        return {'size': st, 'sha1': h.hexdigest()[:16]}
    except Exception:
        return {'size': -1, 'sha1': 'ERR'}


def collect_dex(d):
    m = {}
    for root, _dirs, names in os.walk(d):
        for n in names:
            if n.endswith('.dex'):
                m.setdefault(os.path.basename(n), []).append(os.path.join(root, n))
    return m


def basecheck(cur, base):
    """
    相邻两轮 dump 的稳定性比对。

    依据：开源工具 mHook（xiamiya/mHook）SandboxDexDumper.redumpFilled() 的思路 ——
    函数抽取壳是「边跑边补」的。同一个 dex 两轮之间大小/内容还在变，说明壳还在往里
    填方法体，这一版不是最终态；指纹完全一致才算补完，才能定稿往下走。
    """
    if not os.path.isdir(cur):
        return {'ok': False, '错误': '当前轮目录不存在: ' + cur}
    if not os.path.isdir(base):
        return {'ok': False, '错误': '基线轮目录不存在: ' + base}
    cm, bm = collect_dex(cur), collect_dex(base)
    items = []
    for n in sorted(set(cm.keys()) | set(bm.keys())):
        c = cm.get(n)
        b = bm.get(n)
        if not c:
            items.append({'文件': n, '状态': '基线有、本轮没了（App 被杀或产物被清理）'})
            continue
        if not b:
            items.append({'文件': n, '状态': '本轮新增（上一轮还没 dump 出来）'})
            continue
        fc, fb = dex_fp(c[0]), dex_fp(b[0])
        same = (fc['sha1'] == fb['sha1']) and (fc['size'] == fb['size'])
        grow = fc['size'] - fb['size']
        if same:
            st = '已稳定（补码完成）'
        elif grow > 0:
            st = '本轮大了 %d 字节（还在补）' % grow
        else:
            st = '内容有变化（还在补）'
        items.append({
            '文件': n, '基线大小': fb['size'], '本轮大小': fc['size'], '增长字节': grow,
            '基线sha1': fb['sha1'], '本轮sha1': fc['sha1'], '状态': st
        })
    stable = [i for i in items if '已稳定' in i.get('状态', '')]
    moving = [i for i in items if '已稳定' not in i.get('状态', '')]
    res = {
        'ok': True, '当前轮目录': cur, '基线轮目录': base,
        '总dex数': len(items), '稳定数': len(stable), '仍变化数': len(moving),
        '明细': items[:40]
    }
    if not items:
        res['结论'] = '⚠ 两个目录里都没有 dex，比不出来。确认目录没填错'
        res['下一步'] = '先跑一轮 lu_dump 拿到产物再说'
    elif not moving:
        res['结论'] = ('✅ 两轮完全一致：%d 个 dex 的大小与 SHA-1 都没变 → 壳已经补完方法体，'
                       '这一版可以定稿往下走' % len(stable))
        res['下一步'] = '跑一次目录体检确认不是壳容器，然后进回填 / 解包 / 修 Manifest / 重打包'
    else:
        res['结论'] = ('⚠ 还有 %d 个 dex 在变化：壳还在继续往里填方法体，'
                       '现在就拿去修等于白修' % len(moving))
        res['下一步'] = ('把本轮目录当新基线再跑一轮 lu_dump（rounds 往上加）。'
                         '若连跑 3 轮还在变，说明 App 里有大量惰性初始化类 —— '
                         '纯被动等它补是补不完的，改上 fundex / frida_fart 那种「主动遍历调用」的方案')
    res['坑提醒'] = ('主动加载（遍历 DexFile 逐个触发类初始化）绝对不能跑在目标 App 的主线程：'
                     '类初始化要等 Application.onCreate 返回，而主线程此刻正卡在 hook 里等结果，'
                     '两边互相等待 = 死锁，表现就是 App 启动白屏卡死。'
                     '确认你的脱壳模块是在后台线程里做主动调用的。')
    return res


def main():
    if len(sys.argv) < 3:
        print('用法: dc2.py check|dircheck|zipfix ...')
        sys.exit(1)
    mode = sys.argv[1]
    na = (lambda i, d=None: sys.argv[i] if len(sys.argv) > i and sys.argv[i] != '-' else d)
    try:
        if mode == 'check':
            r = check(sys.argv[2], na(3), na(4))
        elif mode == 'dircheck':
            r = dircheck(sys.argv[2], na(3), na(4))
        elif mode == 'zipfix':
            r = zipfix(sys.argv[2], sys.argv[3], len(sys.argv) > 4 and sys.argv[4] == '1')
        elif mode == 'basecheck':
            r = basecheck(sys.argv[2], sys.argv[3])
        else:
            r = {'ok': False, '错误': '未知模式: ' + mode}
    except Exception as e:
        r = {'ok': False, '错误': str(e)}
    print('LURESULT:' + json.dumps(r, ensure_ascii=False))


if __name__ == '__main__':
    main()

`;

    // ---------- 壳桩自动生成器（纯 Python） ----------
    // 背景：新版 360 的桩类 com.stub.StubApp 是壳的完整实现（1800+ 行，含 native 解密逻辑），
    // 不能原样保留（壳 dex 会被丢弃，so 层面也解不了），也不能直接删（业务代码里有 55+ 处调用）。
    // 正确做法是「照着壳的方法签名，写一份干净的兼容桩」——这就是手机端 AI 手工干了半天的活。
    // 本生成器的策略（按方法名/签名分类，全部取「安全无副作用」实现）：
    //   - getOrigApplicationContext(Context) → 返回真实 Application（identity 语义）
    //   - getAppContext / getRealApp        → 返回真实 Application
    //   - mark / fcmark / rmark / pmark / interface* / n0110 等壳回调 → return-void
    //   - getString2(I) / getDir / getSoPath* → 空串；getString2(String) → 原样返回
    //   - isX86Arch / 返回 boolean 的       → const/4 v0, 0x0
    //   - 返回对象/数组/字符串的其余方法     → const/4 v0, 0x0
    //   - 返回 int/long/float/double 的      → 0 / 0x0 / 0.0f / 0.0
    // 关键：注册器（.registers）取 max(参数寄存器数, 1)，保证返回指令有寄存器可用。
    var PY_STUB = String.raw`
# -*- coding: utf-8 -*-
"""
壳桩生成器（纯标准库，python3 直接跑）

作用：读入壳的 StubApp.smali（1800+ 行、含 native 实现），抽出全部 .method / .field 签名，
      生成一份可直接用 smali 编译的「兼容桩」。业务代码对 StubApp 的所有调用点都能链接上，
      且实现全部无副作用（壳回调变 no-op，context 获取变 identity）。

用法：
    python3 stubgen.py <壳的StubApp.smali> <输出目录> [真实Application类名] [1=预览|0=写入]
"""
import sys, os, re, json

# ---- 已知语义特判（按方法名）----
# 返回值：'app'=真实Application, 'empty'=空串, 'identity'=原样返回 p0, 'false'=0, 'void'=no-op
SPECIAL = {
    'getOrigApplicationContext': 'app',
    'getAppContext': 'app',
    'getRealApp': 'app',
    'getApplication': 'app',
    'getDir': 'empty',
    'getSoPath1': 'null',
    'getSoPath2': 'null',
    'getSoPath': 'null',
    'getString2': 'identity_or_empty',
    'isX86Arch': 'false',
    'isX86': 'false',
    'ChangeTopApplication': 'void',
}

# 壳回调：一律 no-op / null
NOOP_PREFIX = ('mark', 'fcmark', 'rmark', 'pmark', 'interface', 'n0', 'n1', 'q', 'x0')

PARAM_RE = re.compile(r'^\.method\b[\s\S]*?\.end method$', re.M)
FIELD_RE = re.compile(r'^\.field\b.*$', re.M)


def _split_access(header_line):
    """'.method public static foo(I)V' → (access, name, proto)
    ★ 坑：'.method public constructor <init>()V' 里 'constructor' 是访问修饰关键字，不是方法名，
      方法名藏在 proto 里（<init>()V）；'.method public static native mark(...)V' 里 'native' 也是修饰符。
      正确切法：proto 固定从【最后一个 '('】开始（含 ')' 及返回描述符），
      剩下 head 的【最后一个 token】是方法名，再往前都是访问修饰符。"""
    body = header_line.strip()[len('.method'):].strip()
    lp = body.rfind('(')
    if lp < 0:
        raise ValueError('无法解析方法头: ' + header_line)
    head = body[:lp].strip()
    toks = head.split()
    # 构造器：'.method public constructor <init>()V' → head='public constructor <init>'，proto='()V'
    if toks and (toks[-1] == '<init>' or toks[-1] == '<clinit>'):
        name = toks[-1]
        proto = name + body[lp:]
        access = ' '.join(toks[:-1])
    else:
        if not toks:
            raise ValueError('方法头缺少名称: ' + header_line)
        name = toks[-1]
        proto = body[lp:]
        access = ' '.join(toks[:-1])
    return access, name, proto


def _parse_proto(proto):
    """(I[Ljava/lang/String;)V → (param_types, ret_type)"""
    ci = proto.index(')')
    args = proto[1:ci]
    ret = proto[ci + 1:]
    types = []
    i = 0
    while i < len(args):
        c = args[i]
        if c == 'L':
            j = args.index(';', i)
            types.append(args[i:j + 1])
            i = j + 1
        elif c == '[':
            j = i
            while args[j] == '[':
                j += 1
            if args[j] == 'L':
                k = args.index(';', j)
                types.append(args[i:k + 1])
                i = k + 1
            else:
                types.append(args[i:j + 1])
                i = j + 1
        else:
            types.append(c)
            i += 1
    return types, ret


def _is_wide(t):
    return t in ('J', 'D')


def _default_ret(ret):
    """返回该类型的一个安全常量，返回 (smali指令列表, 需要几个 locals)"""
    if ret == 'V':
        return (['    return-void'], 0)
    if ret in ('Z', 'B', 'C', 'S', 'I', 'F'):
        return (['    const/4 v0, 0x0', '    return v0'], 1)
    if ret in ('J', 'D'):
        return (['    const-wide/16 v0, 0x0', '    return-wide v0'], 2)
    # 对象 / 数组 / 字符串
    return (['    const/4 v0, 0x0', '    return-object v0'], 1)


def gen_method(access, name, proto, real_app, is_static):
    """生成一个方法的完整 smali 文本（.locals 风格，p 寄存器由 smali 自动映射）
    real_app 传【内部名】如 'com/stub/StubApp'，函数内部自动包装成 Lcom/stub/StubApp;"""
    ptypes, ret = _parse_proto(proto)
    # 统一包成 smali 类型描述符
    if real_app:
        cls = real_app if real_app.startswith('L') else ('L' + real_app + ';')
    else:
        cls = ''
    special = SPECIAL.get(name)

    # ---- 决定方法体 ----
    if special == 'app' and ret == 'Landroid/app/Application;' and cls:
        body = ['    sget-object v0, %s->realApp:Landroid/app/Application;' % cls,
                '    return-object v0']
        locals_n = 1
    elif special == 'app' and ret == 'Landroid/content/Context;' and cls:
        body = ['    sget-object v0, %s->realApp:Landroid/app/Application;' % cls,
                '    return-object v0']
        locals_n = 1
    elif name == 'getOrigApplicationContext' and ret == 'Landroid/content/Context;':
        # 真实语义：p0 非空则返回 p0；为空则返回 realApp（若已知）
        if cls:
            body = ['    if-eqz p0, :cond_use_real',
                    '    return-object p0',
                    '    :cond_use_real',
                    '    sget-object v0, %s->realApp:Landroid/app/Application;' % cls,
                    '    return-object v0']
            locals_n = 1
        else:
            body = ['    return-object p0']
            locals_n = 0
    elif special == 'empty' and ret == 'Ljava/lang/String;':
        body = ['    const-string v0, ""', '    return-object v0']
        locals_n = 1
    elif special == 'identity_or_empty' and ret == 'Ljava/lang/String;':
        if ptypes and ptypes[0] == 'Ljava/lang/String;':
            body = ['    return-object p0']
            locals_n = 0
        else:
            body = ['    const-string v0, ""', '    return-object v0']
            locals_n = 1
    elif special == 'identity' and ret == 'Ljava/lang/String;' and ptypes and ptypes[0] == 'Ljava/lang/String;':
        body = ['    return-object p0']
        locals_n = 0
    elif special == 'void' or name.startswith(('mark', 'fcmark', 'rmark', 'pmark')):
        if ret == 'V':
            body = ['    return-void']
            locals_n = 0
        else:
            body, locals_n = _default_ret(ret)
    elif special == 'false':
        body, locals_n = _default_ret(ret)
    else:
        # 壳回调 interfaceNN / n0110 / 未知方法：一律安全默认值
        body, locals_n = _default_ret(ret)

    return '\n'.join(['.method %s %s%s' % (access, name, proto),
                      '    .locals %d' % locals_n] + body + ['.end method'])


def main():
    if len(sys.argv) < 3:
        print('LURESULT:' + json.dumps({'ok': False, '错误': '用法: stubgen.py <StubApp.smali> <输出目录> [真实App类名] [1|0]'}))
        return
    src = sys.argv[1]
    out_dir = sys.argv[2]
    real_app = sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] != '-' else ''
    dry = (len(sys.argv) > 4 and sys.argv[4] == '1')

    if not os.path.isfile(src):
        print('LURESULT:' + json.dumps({'ok': False, '错误': '找不到壳 smali: ' + src}))
        return

    with open(src, 'r', encoding='utf-8', errors='replace') as f:
        text = f.read()

    # 类名 / 父类
    cls_m = re.search(r'^\.class\b[^\n]*\s(L[\w/$]+;)', text, re.M)
    cls = cls_m.group(1) if cls_m else 'Lcom/stub/StubApp;'
    sup_m = re.search(r'^\.super\s+(L[\w/$]+;)', text, re.M)
    sup = sup_m.group(1) if sup_m else 'Ljava/lang/Object;'
    cls_inner = cls[1:-1]

    methods = PARAM_RE.findall(text)
    fields = FIELD_RE.findall(text)

    parsed_m = []
    for m in methods:
        header = m.split('\n', 1)[0]
        try:
            access, name, proto = _split_access(header)
        except Exception:
            continue
        parsed_m.append((access, name, proto, m))

    # 只保留「非构造」方法（constructor 必须自己写）
    # 当普通方法出现，一律不生成
    real_methods = [x for x in parsed_m if not x[2].startswith('<init>') and not x[2].startswith('<clinit>')]
    has_clinit = any(x[2].startswith('<clinit>') for x in parsed_m)

    # 非 ASCII 方法名（360 大量使用 ᵢˋ ᵢˎ 这类）在 smali 里合法，但为了输出可读性打个标记
    weird_named = [x for x in real_methods if not re.match(r'^[A-Za-z_$][\w$]*$', x[1])]

    gen = []
    gen.append('# 兼容桩 —— 由 local_unpack:lu_stub_scaffold 自动生成')
    gen.append('# 来源壳 smali: %s' % src)
    gen.append('# 生成策略：壳回调 no-op；context/app 获取返回真实 Application；未知方法安全默认值')
    gen.append('# ★ 注意：原壳的 <clinit> 会 loadLibrary(libjiagu/jiagu)，这里已置空，否则必崩。')
    gen.append('')
    gen.append('.class public final %s' % cls)
    gen.append('.super %s' % sup)
    gen.append('.source "StubApp.java"')
    gen.append('')
    gen.append('# 真实业务 Application（由桩自行维护，供 getOrigApplicationContext 返回）')
    gen.append('.field private static realApp:Landroid/app/Application;')
    gen.append('')

    dropped_fields = []
    for f in fields:
        nm = re.search(r'\s([\w$ᵢˋˎˏˑ]+):', f)
        if nm and nm.group(1) == 'realApp':
            continue
        dropped_fields.append(f.strip()[:120])
    gen.append('# 以下壳内部字段已丢弃（共 %d 个）。业务代码若直接引用了它们，会在运行时报 NoSuchFieldError，' % len(dropped_fields))
    gen.append('# 这时按报错把对应字段名加回本文件即可（只需声明，不必赋值）：')
    for d in dropped_fields[:25]:
        gen.append('#   ' + d)
    if len(dropped_fields) > 25:
        gen.append('#   ... 其余 %d 个省略' % (len(dropped_fields) - 25))
    gen.append('')

    # 构造函数：只生成一个无参 <init>
    gen.append('# 无参构造（Application 子类必需）')
    gen.append('.method public constructor <init>()V')
    gen.append('    .locals 0')
    gen.append('    invoke-direct {p0}, %s-><init>()V' % sup)
    gen.append('    return-void')
    gen.append('.end method')
    gen.append('')

    # realApp 的 get/set
    gen.append('.method public static getRealApp()Landroid/app/Application;')
    gen.append('    .locals 1')
    gen.append('    sget-object v0, %s->realApp:Landroid/app/Application;' % cls)
    gen.append('    return-object v0')
    gen.append('.end method')
    gen.append('')
    gen.append('.method public static setRealApp(Landroid/app/Application;)V')
    gen.append('    .locals 0')
    gen.append('    sput-object p0, %s->realApp:Landroid/app/Application;' % cls)
    gen.append('    return-void')
    gen.append('.end method')
    gen.append('')

    # clinit：如果原壳有，生成空壳版本（原版会 loadLibrary 壳 so，必崩）
    if has_clinit:
        gen.append('# 原壳有 <clinit>，这里置为空实现（原版会 loadLibrary 壳 so，必崩）')
        gen.append('.method static constructor <clinit>()V')
        gen.append('    .locals 0')
        gen.append('    return-void')
        gen.append('.end method')
        gen.append('')

    # 逐个方法生成（native 也要给实现：去掉 native 修饰 + 空实现，否则桩里 native 无实现会 UnsatisfiedLinkError）
    method_names = []
    native_fixed = []
    for access, name, proto, raw in real_methods:
        acc = access.split()
        is_native = 'native' in acc
        if is_native:
            acc = [a for a in acc if a != 'native']
            native_fixed.append('%s%s' % (name, proto))
        # 桩一律是 static（业务调用点全是 StubApp.method(...) 静态调用）
        if 'static' not in acc:
            acc.append('static')
        access_str = ' '.join(acc)
        gen.append(gen_method(access_str, name, proto, cls_inner, True))
        gen.append('')
        method_names.append('%s%s' % (name, proto))

    body = '\n'.join(gen)
    # ★ 输出路径：cls_inner 已经是 'com/stub/StubApp'，直接落地为 <cls_inner>.smali
    out_file = os.path.join(out_dir, cls_inner + '.smali')

    res = {
        'ok': True,
        '壳类': cls,
        '父类': sup,
        '源文件': src,
        '源文件行数': text.count('\n') + 1,
        '声明方法数': len(parsed_m),
        '生成方法数': len(method_names) + 3 + (1 if has_clinit else 0),
        '丢弃字段数': len(dropped_fields),
        'native转普通方法数': len(native_fixed),
        'native转普通方法': native_fixed[:20],
        '非ASCII方法名数': len(weird_named),
        '真实Application': real_app or '(未指定，将返回 null)',
        '输出文件': out_file,
        '模式': '只预览未写入' if dry else '已写入',
        '生成的方法': method_names[:60],
        '下一步': ('把输出目录整体拷进 apktool 解包目录的 smali 下（保持 com/stub/ 层级：'
                   '解包目录/smali/com/stub/StubApp.smali），然后把 AndroidManifest 的 android:name '
                   '改回真实 Application（用 lu_manifest_fix），壳 dex 丢弃，'
                   '用 lu_repack_assemble 重新组装 dex 编号。'),
        '重要提醒': ('① 桩里 getOrigApplicationContext 返回 realApp，而 realApp 需要有人 set。'
                     '推荐做法：Manifest 的 android:name 直接指向真实业务 Application（真实 App 是 '
                     'Application 子类，桩只是业务代码里 StubApp.xxx() 调用点的链接目标）。'
                     '② 原壳的 native 方法已自动转成普通方法 + 空实现，避免 UnsatisfiedLinkError。'
                     '③ 壳内部字段全部丢弃，若运行时 NoSuchFieldError 按报错补声明即可。')
    }

    if not dry:
        try:
            if os.path.dirname(out_file):
                os.makedirs(os.path.dirname(out_file), exist_ok=True)
            with open(out_file, 'w', encoding='utf-8') as f:
                f.write(body + '\n')
            res['写入字节数'] = len(body.encode('utf-8'))
        except Exception as e:
            res['ok'] = False
            res['错误'] = '写入失败: %s' % e

    print('LURESULT:' + json.dumps(res, ensure_ascii=False))


if __name__ == '__main__':
    main()
`;

    // 把文件推到 /data/local/tmp（SELinux 下 pm install 只认这里）
    function pushTmp(src) {
        var name = src.split('/').pop() || 'app.apk';
        var dst = '/data/local/tmp/' + name;
        return execRaw('cp ' + q(src) + ' ' + q(dst) + ' 2>&1; '
                       + 'if [ ! -f ' + q(dst) + ' ]; then su -c "cp ' + q(src) + ' '
                       + q(dst) + '"; fi; ls -l ' + q(dst) + ' 2>&1 || echo MISSING', 120000)
            .then(function (r) {
                var o = (r.output || '');
                var ok = o.indexOf('MISSING') < 0 && o.indexOf('No such file') < 0;
                return { path: dst, ok: ok, detail: o.trim().split('\n').pop() };
            });
    }

    // ★ 脱壳产物体检：真 dex 还是壳容器，一眼看穿
    function lu_dump_check(params) {
        var p = params.dex_path;
        if (!p) throw new Error('缺少 dex_path');
        var apk = params.apk_path || '-';
        var pkg = params.package_name || '-';
        var base = params.baseline_dir || '';
        return execRaw('if [ -d ' + q(p) + ' ]; then echo DIR; else echo FILE; fi', 15000)
            .then(function (r) {
                var isDir = ((r.output || '').trim() === 'DIR');
                var healthP = isDir
                    ? runPy(PY_DC, 'dircheck ' + q(p) + ' ' + q(apk) + ' ' + q(pkg), 300000)
                    : runPy(PY_DC, 'check ' + q(p) + ' ' + q(apk) + ' ' + q(pkg), 300000);
                if (!base) return healthP;
                // 轮间比对 + 常规体检一起做，一次给全
                return healthP.then(function (h) {
                    return runPy(PY_DC, 'basecheck ' + q(p) + ' ' + q(base), 300000)
                        .then(function (bc) {
                            h['轮间稳定性'] = bc;
                            if (bc && bc.ok) {
                                h['一句话'] = (h['结论'] || '') + ' ｜ ' + (bc['结论'] || '');
                                h['下一步'] = (bc['下一步'] || '') + '（覆盖上面的体检建议）';
                            }
                            return h;
                        });
                });
            })
            .then(function (r) {
                if (!r || r.ok === false) throw new Error((r && r['错误']) || '体检失败（确认 python3 可用、路径正确）');
                if (r['raw']) return r;
                r['一句话'] = r['结论'] || '';
                if (!r['下一步']) {
                    if ((r['结论'] || '').indexOf('可用') >= 0) {
                        r['下一步'] = '能用的 dex 留着；若是主动调用产物（有 _ins_*.bin）走 lu_fart_import，'
                                    + '否则直接进解包 / 修 Manifest / 重打包。回填进 APK 时 dex 编号必须连续（classes2/3/4…）';
                    } else {
                        r['下一步'] = '换脱壳手段重来（换模块 / 换时机 / 老版 360 直接走云端），别在坏产物上浪费时间';
                    }
                }
                return r;
            });
    }

    // ---------- dex 组装器（纯 Python） ----------
    // 背景：脱修后的 APK 里 dex 编号必须连续（classes.dex / classes2.dex / classes3.dex …），
    // 跳号（比如 classes69.dex 之后接 classes73.dex）ART 会直接拒绝加载。
    // 而 360 加固的原始 APK 里 dex 编号本来就乱（classes.dex 是壳 + classes69~72.dex 是未加固业务），
    // 脱壳产物又是 classes.dex / classes2..8 —— 必须先拆掉壳 dex、再把两批重新连续编号。
    var PY_ASSEMBLE = String.raw`
# -*- coding: utf-8 -*-
"""
dex 组装器（纯标准库，python3 直接跑）

作用：把「脱壳产物 dex」+「原始 APK 里未加固的业务 dex」合并成一个编号连续的 dex 目录。
      自动跳过壳 dex（体积≈APK 内 classes.dex 且类数极少的那个）、去重、按体积降序重编号。

用法：
    python3 assemble.py <输出目录> <脱壳dex目录> <原始apk或'-'> [1=预览|0=写入]
"""
import sys, os, re, json, zipfile, struct, hashlib

DEX_MAGIC = b'dex\n035\x00'
DEX_MAGIC_036 = b'dex\n036\x00'
DEX_MAGIC_037 = b'dex\n037\x00'
DEX_MAGIC_038 = b'dex\n038\x00'
DEX_MAGIC_039 = b'dex\n039\x00'
DEX_MAGICS = (DEX_MAGIC, DEX_MAGIC_036, DEX_MAGIC_037, DEX_MAGIC_038, DEX_MAGIC_039)


def dex_info(path):
    """读 dex 头，返回 (file_size, class_count, sha1_16, magic_ok)"""
    try:
        with open(path, 'rb') as f:
            head = f.read(112)
    except Exception:
        return None
    if len(head) < 112:
        return None
    magic_ok = head[:8] in DEX_MAGICS
    file_size = struct.unpack('<I', head[32:36])[0]
    class_defs_size = struct.unpack('<I', head[96:100])[0]
    real = os.path.getsize(path)
    h = hashlib.sha1()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return {
        'path': path,
        'name': os.path.basename(path),
        'real_size': real,
        'file_size': file_size,
        'class_count': class_defs_size,
        'sha1': h.hexdigest()[:16],
        'magic_ok': magic_ok,
        'size_ok': (file_size == real),
    }


def _full_sha(path):
    h = hashlib.sha1()
    try:
        with open(path, 'rb') as f:
            for chunk in iter(lambda: f.read(1 << 20), b''):
                h.update(chunk)
        return h.hexdigest()
    except Exception:
        return 'NOSHA_' + os.path.basename(path)


def main():
    if len(sys.argv) < 4:
        print('LURESULT:' + json.dumps({'ok': False, '错误': '用法: assemble.py <输出目录> <脱壳dex目录> <原始apk|-> [1|0]'}))
        return
    out_dir = sys.argv[1]
    dump_dir = sys.argv[2]
    apk = sys.argv[3] if len(sys.argv) > 3 and sys.argv[3] != '-' else ''
    dry = (len(sys.argv) > 4 and sys.argv[4] == '1')

    res = {'ok': True, '输出目录': out_dir, '脱壳目录': dump_dir, '原始APK': apk or '(未提供)',
           '模式': '只预览未写入' if dry else '已写入'}

    if not os.path.isdir(dump_dir):
        print('LURESULT:' + json.dumps({'ok': False, '错误': '脱壳目录不存在: ' + dump_dir}))
        return

    # ---- 1) 收脱壳产物 ----
    dumped = []
    for fn in sorted(os.listdir(dump_dir)):
        if fn.endswith('.dex'):
            info = dex_info(os.path.join(dump_dir, fn))
            if info:
                info['来源'] = '脱壳'
                dumped.append(info)

    # ---- 2) 收原始 APK 内的 dex ----
    from_apk = []
    apk_dex_names = []
    if apk and os.path.isfile(apk):
        try:
            z = zipfile.ZipFile(apk)
            for n in z.namelist():
                if re.match(r'^classes\d*\.dex$', n):
                    apk_dex_names.append(n)
            tmp = os.path.join(out_dir, '_fromapk')
            if not dry:
                os.makedirs(tmp, exist_ok=True)
            for n in apk_dex_names:
                base = os.path.basename(n)
                info = None
                if dry:
                    # 预览模式：只读 zip 内的大小与头部，不落盘
                    data = z.read(n)
                    file_size = struct.unpack('<I', data[32:36])[0]
                    class_defs_size = struct.unpack('<I', data[96:100])[0]
                    info = {'path': os.path.join(tmp, base), 'name': base,
                            'real_size': len(data), 'file_size': file_size,
                            'class_count': class_defs_size,
                            'sha1': hashlib.sha1(data).hexdigest()[:16],
                            'magic_ok': data[:8] in DEX_MAGICS,
                            'size_ok': file_size == len(data),
                            '来源': 'APK内'}
                else:
                    p = os.path.join(tmp, base)
                    with open(p, 'wb') as f:
                        f.write(z.read(n))
                    info = dex_info(p)
                    if info:
                        info['来源'] = 'APK内'
                if info:
                    from_apk.append(info)
            z.close()
        except Exception as e:
            res['读APK失败'] = str(e)

    # ---- 3) 识别壳 dex（对所有来源统一判定） ----
    # 壳 dex 特征（满足任一即认定）：
    #   a) 体积与「APK 内 classes.dex」接近（<2% 或 4KB 以内）—— 原始 classes.dex 通常就是壳
    #   b) 类数 < 所有 dex 类数中位数的 20%
    all_items = from_apk + dumped
    shell_name = None
    shell_size = None
    for a in from_apk:
        if a['name'] == 'classes.dex':
            shell_size = a['real_size']
            break
    # 类数上限 70000：dex 的 class_defs_size 是 u32，但一个 dex 实际撑不到 6.5 万个类。
    # 实测有 dump 产物算出 1300479325（头被打乱的垃圾值，岛风 5.0.0 的 dump_9.dex）。
    # 这种值如果混进 median 计算，median*0.2 会被抬到一个荒谬的量级，
    # 反而让正常的业务 dex 全被判成「类数远低于中位数」的壳 dex 删掉。
    class_counts = sorted([x['class_count'] for x in all_items
                           if 0 < x['class_count'] <= 70000])
    median = class_counts[len(class_counts) // 2] if class_counts else 0

    for item in all_items:
        looks_shell = False
        reasons = []
        # a) APK 内那个 classes.dex 就是原始壳 dex，直接认定
        if item['来源'] == 'APK内' and item['name'] == 'classes.dex' and shell_size:
            looks_shell = True
            reasons.append('原始 APK 的 classes.dex（壳入口）')
        # b) 体积与 APK 壳 dex 接近的「脱壳」产物 → 也是壳（脱壳工具把壳容器原样吐了出来）
        if (not looks_shell and item['来源'] == '脱壳' and shell_size
                and abs(item['real_size'] - shell_size) <= max(4096, shell_size * 0.02)):
            looks_shell = True
            reasons.append('体积 %.1fMB 与原始壳 dex 几乎一致（判定为壳容器）'
                           % (item['real_size'] / 1048576.0))
        # c) 类数占比极低
        if not looks_shell and median and item['class_count'] > 0 and item['class_count'] < median * 0.2:
            looks_shell = True
            reasons.append('类数 %d 远低于中位数 %d' % (item['class_count'], median))
        item['疑似壳dex'] = looks_shell
        item['壳判定依据'] = '；'.join(reasons)
        if looks_shell and not shell_name:
            shell_name = item['name']

    # ---- 4) 剔除壳 + 去重（按全文 SHA-1）----
    seen = {}
    keep = []
    dropped = []
    for item in all_items:
        if item.get('疑似壳dex'):
            dropped.append({'文件': '%s(%s)' % (item['name'], item['来源']),
                            '原因': '壳 dex：' + (item.get('壳判定依据') or '')})
            continue
        # ★ 头校验不通过的 dex 一律扔掉，绝不允许进包。
        # 原来的做法只留一句「头异常」警告，dex 照样被写进安装包 —— 实测岛风 5.0.0，
        # dump_9.dex 的 file_size 与实际差了 300 多 MB、类数算成 1300479325，
        # 被当成 classes5.dex 打了进去。装上去必崩，而且崩在 ART 加载阶段，
        # 从闪退日志根本看不出是哪一个 dex 坏了，比少几个 dex 难查一百倍。
        # 修不修得好交给 lu_dex_fix 判断，但无论如何不能先塞进包。
        _badk = []
        if not item['magic_ok']:
            _badk.append('magic 不对')
        if not item['size_ok']:
            _badk.append('file_size 与实际长度不符')
        if item['class_count'] > 70000:
            _badk.append('类数 %d 越界（实际不可能超 6.5 万）' % item['class_count'])
        if _badk:
            dropped.append({'文件': '%s(%s)' % (item['name'], item['来源']),
                            '原因': '头校验失败（%s），已剔除不进包' % '；'.join(_badk)})
            continue
        # 完整文件 SHA-1 去重（避免 dump 出同一份 dex 的两份拷贝）
        item['full_sha'] = _full_sha(item['path'])
        prev = seen.get(item['full_sha'])
        if prev:
            dropped.append({'文件': '%s(%s)' % (item['name'], item['来源']),
                            '原因': '与 %s 内容完全相同（去重）' % prev})
            continue
        seen[item['full_sha']] = '%s(%s)' % (item['name'], item['来源'])
        keep.append(item)

    # ---- 5) 排序 + 连续重编号 ----
    keep_sorted = sorted(keep, key=lambda x: -x['real_size'])
    plan = []
    for i, item in enumerate(keep_sorted):
        newname = 'classes.dex' if i == 0 else ('classes%d.dex' % (i + 1))
        plan.append({
            '新名': newname, '原名': item['name'], '来源': item['来源'],
            '大小': item['real_size'], '类数': item['class_count'],
            'sha1': item['sha1'], 'magic_ok': item['magic_ok'], 'size_ok': item['size_ok'],
            'path': item['path'],
        })

    res['脱壳dex数'] = len(dumped)
    res['APK内dex数'] = len(from_apk)
    res['APK内dex原编号'] = apk_dex_names
    res['识别到的壳dex'] = shell_name or '(未识别到)'
    res['最终dex数'] = len(plan)
    res['最终编号方案'] = plan
    res['丢弃清单'] = dropped

    # 头坏的已经在上面 keep 循环里剔掉了，这里只做汇总（给 AI 看清楚少了什么）
    bad = [d for d in dropped if '头校验失败' in (d.get('原因') or '')]
    if bad:
        res['剔除头异常数'] = len(bad)
        res['剔除头异常'] = bad[:10]
        res['警告'] = ('有 %d 个 dex 头校验不过，已经从组装清单里剔除，不会进包。'
                       '如果最终包缺类，先用 lu_dex_fix 修头再重新组装；'
                       '修不了的换脱壳方式重来 —— 宁可少几个 dex，也不能塞坏 dex，'
                       '坏 dex 会让 App 在加载阶段直接崩，且报错完全指不到它。' % len(bad))

    if not dry:
        try:
            os.makedirs(out_dir, exist_ok=True)
            import shutil
            written = []
            for p in plan:
                dst = os.path.join(out_dir, p['新名'])
                if os.path.abspath(p['path']) != os.path.abspath(dst):
                    shutil.copyfile(p['path'], dst)
                written.append({'新名': p['新名'], '大小': os.path.getsize(dst)})
            # 清理临时目录
            tmpd = os.path.join(out_dir, '_fromapk')
            if os.path.isdir(tmpd):
                shutil.rmtree(tmpd, ignore_errors=True)
            res['已写入'] = written
        except Exception as e:
            res['ok'] = False
            res['错误'] = '组装写入失败: %s' % e

    res['下一步'] = ('把输出目录整体覆盖到 apktool 解包目录（apktool 解包目录里本来就带 classes*.dex，'
                     '先把原来的删干净再拷进去），然后 lu_rebuild 回编译。'
                     '★顺序：dex 组装要在 apktool b 之前完成。')
    res['重要提醒'] = ('编号必须从 classes.dex 开始连续到尾，中间不能跳号。'
                       'apktool 回编译时会自动把目录里的 classes*.dex 一并打进 APK，不会重新排序。')
    print('LURESULT:' + json.dumps(res, ensure_ascii=False))


if __name__ == '__main__':
    main()
`;

    function lu_repack_assemble(params) {
        var outDir = params.output_dir;
        var dumpDir = params.dump_dir;
        if (!outDir || !dumpDir) throw new Error('缺少 output_dir 或 dump_dir');
        var apk = params.apk_path || '-';
        var dry = !(params.dry_run === 'false' || params.dry_run === false);

        return runPy(PY_ASSEMBLE, q(outDir) + ' ' + q(dumpDir) + ' ' + q(apk) + ' ' + (dry ? '1' : '0'), 300000)
            .then(function (r) {
                if (!r || r.ok === false) throw new Error((r && r['错误']) || '组装失败（确认目录路径、python3 可用）');
                if (dry) {
                    r['下一步'] = '核对「最终编号方案」与「丢弃清单」：壳 dex 是否被剔除、'
                                + 'APK 内那批未加固业务 dex（classes69~72 之类）是否全部保留。'
                                + '确认后用 dry_run=false 落地。';
                }
                r['一句话'] = '脱壳 ' + (r['脱壳dex数'] || 0) + ' 个 + APK 内 ' + (r['APK内dex数'] || 0)
                            + ' 个 → 去壳去重后 ' + (r['最终dex数'] || 0) + ' 个，连续编号 classes.dex ~ classes'
                            + (r['最终dex数'] || 1) + '.dex。';
                return r;
            });
    }

    // ---------- 壳桩自动生成（新版 360 重打包的关键一步） ----------
    function lu_stub_scaffold(params) {
        var smaliPath = params.stub_smali;
        var outDir = params.output_dir;
        if (!smaliPath || !outDir) throw new Error('缺少 stub_smali 或 output_dir');
        var realApp = params.real_app || '-';
        var dry = !(params.dry_run === 'false' || params.dry_run === false);

        return runPy(PY_STUB, q(smaliPath) + ' ' + q(outDir) + ' ' + q(realApp) + ' ' + (dry ? '1' : '0'), 120000)
            .then(function (r) {
                if (!r || r.ok === false) throw new Error((r && r['错误']) || '桩生成失败（确认 stub_smali 路径正确、python3 可用）');
                if (dry) {
                    r['下一步'] = '预演通过后，把 dry_run 设为 false 再跑一次，smali 会落到 ' + outDir
                                + '。然后把该目录整体拷进 apktool 解包目录的 smali/ 下（保持 com/stub/ 层级）。';
                } else {
                    r['下一步'] = '桩已生成。接下来：① 用 lu_repack_assemble 组装 dex 编号；'
                                + '② lu_manifest_fix 删壳代理 activity + 改入口；③ lu_rebuild 出包。';
                }
                r['一句话'] = '从 ' + (r['源文件行数'] || '?') + ' 行壳实现里抽出 '
                            + (r['声明方法数'] || 0) + ' 个方法，生成 ' + (r['生成方法数'] || 0)
                            + ' 个安全桩方法，丢弃 ' + (r['丢弃字段数'] || 0) + ' 个壳内部字段。';
                return r;
            });
    }

    // 安装前打包修正：.so 改 STORED + 推 /data/local/tmp
    function lu_zipfix(params) {
        var apk = params.apk_path;
        if (!apk) throw new Error('缺少 apk_path');
        var out = params.output_apk || (apk.replace(/\.apk$/i, '') + '_fixed.apk');
        var wantAlign = !(params.zipalign === 'false' || params.zipalign === false);
        var final = out;
        return exec('mkdir -p ' + q(dirname(out)), 15000)
            .then(function () { return runPy(PY_DC, 'zipfix ' + q(apk) + ' ' + q(out) + ' 0', 600000); })
            .then(function (r) {
                if (!r || r.ok === false) throw new Error((r && r['错误']) || '打包修正失败');
                if (r['raw']) throw new Error('python 没返回结构化结果，原始输出：' + String(r['raw']).substring(0, 400));
                if (r['so总数'] === 0) return { r: r, final: out };
                if (!wantAlign) return { r: r, final: out };
                return execRaw('command -v zipalign || echo NONE', 10000).then(function (c) {
                    if ((c.output || '').trim().indexOf('NONE') >= 0) {
                        r['zipalign'] = '设备上没找到 zipalign，跳过（.so 已改为 STORED，一般够用）';
                        return { r: r, final: out };
                    }
                    var al = out.replace(/\.apk$/i, '') + '_aligned.apk';
                    return exec('zipalign -f -p 4 ' + q(out) + ' ' + q(al), 300000)
                        .then(function () {
                            r['zipalign输出'] = al;
                            return { r: r, final: al };
                        })
                        .catch(function (e) {
                            r['zipalign'] = 'zipalign 跑失败（' + ((e && e.message) || '') + '），用未对齐版本';
                            return { r: r, final: out };
                        });
                });
            })
            .then(function (x) {
                var r = x.r;
                final = x.final;
                var wantRe = !(params.resign === 'false' || params.resign === false);
                if (!wantRe) {
                    r['重签名'] = '按 resign=false 跳过。若这是 lu_rebuild 已签名的包，本步会让它失去 v2 签名，'
                                + '装之前记得自己再签一次';
                    return Promise.resolve({ r: r, final: final });
                }
                // 改 STORED / zipalign 都动过 ZIP 字节 → 签名必须放到最后重做一次
                return execRaw('command -v apksigner || echo NONE', 10000).then(function (c) {
                    if ((c.output || '').trim().indexOf('NONE') >= 0) {
                        r['重签名'] = '设备上没找到 apksigner，跳过。请用 MT 管理器对这个最终包再签一次';
                        return { r: r, final: final };
                    }
                    var sg = final.replace(/\.apk$/i, '') + '_signed.apk';
                    return execRaw('apksigner sign --ks-debug --v1-signing-enabled true --v2-signing-enabled true '
                                   + '--out ' + q(sg) + ' ' + q(final) + ' 2>&1 | tail -6', 300000)
                        .then(function (rr) {
                            return execRaw('if [ -f ' + q(sg) + ' ]; then mv ' + q(sg) + ' ' + q(final)
                                           + '; echo MOVED; else echo FAIL; fi', 30000)
                                .then(function (mv) {
                                    var ok = (mv.output || '').indexOf('MOVED') >= 0;
                                    r['重签名'] = ok
                                        ? '✅ 已在本步改完 STORED / 对齐之后重新 debug 签名（v1+v2 显式开启）'
                                        : '重签名失败：' + (rr.output || '').substring(0, 300) + ' → 换 MT 管理器签名';
                                    return { r: r, final: final };
                                });
                        });
                });
            })
            .then(function (x) {
                var r = x.r;
                final = x.final;
                return pushTmp(final).then(function (pt) {
                    r['最终APK'] = final;
                    r['推送结果'] = pt.ok ? ('已推到 ' + pt.path) : ('推送失败，手动 cp ' + final + ' /data/local/tmp/');
                    r['安装命令'] = 'pm install -r -t ' + pt.path;
                    r['一句话'] = (r['结论'] || '') + ' | 装不上就执行：' + r['安装命令'];
                    r['下一步'] = '在 root shell 里跑上面的安装命令。'
                                + '若仍报 INSTALL_FAILED_INVALID_APK，看 logcat 里的具体子错误；'
                                + '若报 INSTALL_FAILED_UPDATE_INCOMPATIBLE 说明签名变了，先卸载原包再装。';
                    return r;
                });
            });
    }

    // ============================================================
    // 一键清壳残留（lu_clear_shell）
    //
    // 存在的理由：这套清理流程（入口换真身 → 正则删冒充调用 → 修 native onCreate）
    // 是论坛流传多年、能对付大部分 360 包的通用套路，但以前工具里没有现成按钮，
    // AI 每次都要自己拼正则、自己找父类、自己判断要不要动 —— 绕路和误删大半出在这里。
    // 现在把各家的正则原样内置，AI 只需要说「清 360」。
    //
    // 正则来源：《2025 安卓全加固脱壳清残留终极指南》（不晚SVIP，2025-12-28）。
    // 360 那第一条六分支正则由用户逐字给出，一字未改。
    // ============================================================
    function lu_clear_shell(params) {
        var dir = params.decoded_dir;
        if (!dir) throw new Error('缺少 decoded_dir');
        var vendor = String(params.vendor || '360').toLowerCase();
        var realApp = params.real_app || '';
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        var purgeSo = !(params.purge_so === 'false' || params.purge_so === false);
        var purgeSmali = (params.purge_smali_dir === 'true' || params.purge_smali_dir === true);

        var py = String.raw`
import sys, os, re, json, shutil

root = sys.argv[1]
vendor = (sys.argv[2] or '360').lower()
real_app = sys.argv[3] or ''
purge_so = sys.argv[4] == '1'
purge_smali = sys.argv[5] == '1'
dry = sys.argv[6] == '1'

# ---------------------------------------------------------------
# 各家加固的清理规则。regex 全部按「整行匹配、命中即删」使用。
# 360 第一组是论坛通用正则（6 个分支，按方法签名精确匹配，误删率最低），原文照抄。
# 其余厂商原文给的是残缺正则（(.) 结尾不合语法），这里按同样思路补全。
# ---------------------------------------------------------------
RULES = {
 '360': {
   'label': '360加固',
   'entries': ['com.stub.StubApp', 'com.stub.StubApplication',
               'com.qihoo.util.StubApplication', 'com.qihoo.stub.StubApp',
               'com.frezrik.jiagu.StubApp',
               'com.qihoo.enterprise.StubEntActivity',
               'com.qihoo.util.StubApp'],
   'regex': [
     r'const .*\s*invoke-static \{.*\}, Lcom/stub/StubApp;->.*\(I\)V|invoke-static \{.*\}, Lcom/stub/StubApp;->.*\(Landroid/app/Activity;\[Ljava/lang/String;I\)V|invoke-static/range \{.*\}, Lcom/stub/StubApp;->.*\(Landroid/content/Context;\)Landroid/content/Context;\s*move-result-object (.*)|invoke-static \{.*\}, Lcom/stub/StubApp;->.*\(I\[Ljava/lang/String;\[I\)V|invoke-static \{\}, Lcom/stub/StubApp;->.*\(\)V|invoke-static \{.*\}, Lcom/stub/StubApp;->.*\(Ldalvik/system/DexFile;\)Ljava/util/Enumeration;\s*move-result-object (.*)\s*',
     r'invoke-static \{.*\}, Lcom/qihoo/util/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/stub/StubApp;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/qihoo/util/art/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/stub/StubApp/.*;->.*\(.*\)V',
     r'invoke-direct \{.*\}, Lcom/qihoo/util/art/ArtMain;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/qihoo/enterprise/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/qihoo/enterprise/vmp/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/qihoo/enterprise/check/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/qihoo/enterprise/daemon/.*;->.*\(.*\)V',
     # 兜底三条：上面按方法签名一个个列的分支，再全也有漏 —— 各家版本的方法名和参数一直在变，
     # 论坛正则不可能永远最新。这三条只认「调用目标是 360 的包 + 返回 void」，够宽的全删。
     # 这么做是安全的：com/stub、com/qihoo、com/frezrik 下的类本来就属于壳，
     # 业务代码不会去调它们（业务真在用的桩调用是另一套处理，见下面的「保留的桩调用」）。
     #
     # com/frezrik 这条是后补的。原来的规则里有 smali_dirs: com/frezrik（认得这个壳的目录），
     # 但 regex 里一条都没配 —— 实测七天学堂 4.6.4（com.frezrik.jiagu，360 V5 老壳）
     # 报「正则删除条数: 0」，等于认得出壳、下不了手。V5 老壳的方法名是
     # attach / copyJiagu / getDexData / hook / replaceApplicationContext 这一批，
     # 跟 com.stub 的 interfaceN 完全不是一套，所以只能按包名兜底。
     r'invoke-static \{.*\}, Lcom/stub/[\w/$]*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/qihoo/[\w/$]*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/frezrik/[\w/$]*;->.*\(.*\)V',
     # V5 老壳还会用 invoke-virtual / invoke-direct 调自己目录里的 util（Reflect、ZipUtil），
     # 这些在壳自己的 smali 目录里，purge_smali_dir 会整目录删；但业务代码里如果也残留
     # 这类调用（V5 有 getOrigApplicationContext 的等价物），同样要按 void 清掉。
     r'invoke-direct \{.*\}, Lcom/frezrik/[\w/$]*;->.*\(.*\)V',
   ],
   'so': ['libjiagu.so', 'libprotectClass.so', 'libjiagu_art.so',
          'libjiagu_enterprise.so', 'libvmp_360.so', 'libjiagu_sdk.so',
          'libjiagu_a64.so', 'libjiagu_x64.so', 'libjiagu_x86.so',
          'libjiagu_x86_64.so', 'libjiagu_ls.so',
          'libjgcxi.so', 'libjgcxi_a64.so',
          'libjiagu_vip.so', 'libjiagu_vip_a64.so'],
   'assets': ['jiagu.dat', 'jiagu.art', 'jiagu_res.zip',
              'jiagu_enterprise.dat', 'vmp_config.bin', '.jgapp'],
   'smali_dirs': ['com/qihoo', 'com/stub', 'com/frezrik'],
 },
 'tencent': {
   'label': '腾讯乐固',
   'entries': ['com.tencent.StubShell.TxAppEntry', 'com.tencent.StubShell.TxShellApp',
               'com.tencent.bugly.legu.StubApp'],
   'regex': [
     r'invoke-static \{.*\}, Lcom/tencent/stub/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/tencent/StubShell/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/tencent/enterprise/.*;->.*\(.*\)V',
   ],
   'so': ['libtosprotection.so', 'libBugly.so', 'libtencent_enterprise.so',
          'libvmp_tencent.so', 'libshella.so', 'libshellx.so'],
   'assets': ['tup.dat', 'tup_enterprise.dat', 'anti_dump.bin',
              '0OO00l111l1l', 'o0oooOO0', 'tosversion'],
   'smali_dirs': ['com/tencent/stub', 'com/tencent/enterprise'],
 },
 'ijiami': {
   'label': '爱加密',
   'entries': ['s.h.e.l.l.S', 'com.shell.SuperApplication', 's.h.e.l.l.StubApp'],
   'regex': [
     r'invoke-static \{.*\}, Ls/h/e/l/l/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/ijiami/enterprise/.*;->.*\(.*\)V',
   ],
   'so': ['libexec.so', 'libexecmain.so', 'libijiami_enterprise.so', 'libexec_vmp.so'],
   'assets': ['ijiami.dat', 'ijm_lib', 'ijiami_enterprise.dat', 'vmp_data.bin'],
   'smali_dirs': ['s/h/e/l/l', 'com/ijiami'],
 },
 'bangcle': {
   'label': '梆梆安全',
   'entries': ['com.secneo.apkwrapper.ApplicationWrapper', 'com.SecShell.SecShell'],
   'regex': [
     r'invoke-static \{.*\}, Lsecneo/apkwrapper/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/bangbang/enterprise/.*;->.*\(.*\)V',
   ],
   'so': ['libsecexe.so', 'libsecmain.so', 'libbangbang_enterprise.so', 'libsec_vmp.so'],
   'assets': ['secneo.dat', 'secneo_enterprise.dat', 'anti_dump.dat'],
   'smali_dirs': ['secneo', 'com/bangbang'],
 },
 'naga': {
   'label': '娜迦加固',
   'entries': ['com.chaos.vmp.StubApplication'],
   'regex': [
     r'invoke-static \{.*\}, Lchaos/vmp/.*;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/chaos/vmp/.*;->.*\(.*\)V',
   ],
   'so': ['libchaosvmp.so', 'libddog.so', 'libchaos_enterprise.so',
          'libvmp_chaos.so', 'libxloader.so'],
   'assets': ['chaos.dat', 'chaos_enterprise.dat', 'vmp_enterprise.bin'],
   'smali_dirs': ['chaos', 'com/chaos'],
 },
 'ali': {
   'label': '阿里聚安全',
   'entries': ['com.ali.security.enterprise.EntMainActivity',
               'com.ali.mobisecenhance.StubApplication'],
   'regex': [
     r'invoke-static \{.*\}, Lcom/ali/security/enterprise/AliVMP;->.*\(.*\)V',
     r'invoke-static \{.*\}, Lcom/ali/security/enterprise/.*;->.*\(.*\)V',
   ],
   'so': ['libsgmain.so', 'libsgsecuritybody.so', 'libali_enterprise.so'],
   'assets': ['aliprotect.dat', 'ali_enterprise.dat'],
   'smali_dirs': ['com/ali/security'],
 },
}

rule = RULES.get(vendor)
if not rule:
    print("LURESULT:" + json.dumps({
        'error': '不认识的厂商 %s，可用：%s' % (vendor, '/'.join(sorted(RULES.keys())))
    }, ensure_ascii=False))
    sys.exit(0)

res = {
    '解包目录': root,
    '厂商': rule['label'],
    '模式': '只报告(dry_run)' if dry else '已写入',
}

# ---------------- ① Manifest 入口换回真实 Application ----------------
def read_manifest():
    p = os.path.join(root, 'AndroidManifest.xml')
    if not os.path.isfile(p):
        return None, ''
    for enc in ('utf-8', 'utf-16', 'latin-1'):
        try:
            with open(p, 'r', encoding=enc) as f:
                t = f.read()
            if 'manifest' in t or 'application' in t:
                return p, t
        except Exception:
            continue
    return p, ''

man_p, man_txt = read_manifest()
man_changes = []
if man_p and man_txt:
    for e in rule['entries']:
        if 'android:name="' + e + '"' in man_txt:
            man_changes.append(e)
else:
    res['Manifest'] = '没找到 AndroidManifest.xml（apktool 解包目录应有一份文本版）'

# 真实 Application 推断：找继承 android.app.Application 的类，排除壳自己的包
def infer_real_app():
    cands = []
    skip_pref = ('com/stub/', 'com/qihoo/', 'com/frezrik/', 'com/secneo/',
                 'com/tencent/stub/', 's/h/e/l/l/', 'chaos/')
    supers = ('Landroid/app/Application;',
              'Landroidx/multidex/MultiDexApplication;',
              'Landroid/support/multidex/MultiDexApplication;')
    for dp, dn, fns in os.walk(root):
        rel = dp.replace(root, '').replace(os.sep, '/').lstrip('/')
        # ★ 这里必须先剥掉开头的 smali / smali_classes2 / smali_classes3 这一层。
        # 原来的写法是拿 rel 直接 startswith('com/frezrik') —— rel 实际长成
        # 'smali/com/frezrik/jiagu'，永远匹配不上，于是 skip_pref 整段形同废弃，
        # 壳自己的 StubApp.smali 照样被当候选。
        # 实测七天学堂 4.6.4：业务 dex 还没反编译成 smali，继承 Application 的只有壳的
        # StubApp（唯一候选）→ 推断结果就是壳本身 → 报告写成
        # 「com.frezrik.jiagu.StubApp → com.frezrik.jiagu.StubApp」，等于没改。
        pkg = re.sub(r'^smali(_classes\d+)?/', '', rel)
        pkgn = pkg.rstrip('/')
        if any(pkgn == s.rstrip('/') or pkgn.startswith(s) for s in skip_pref):
            continue
        for fn in fns:
            if not fn.endswith('.smali'):
                continue
            p = os.path.join(dp, fn)
            try:
                with open(p, 'r', encoding='utf-8', errors='replace') as f:
                    head = f.read(4000)
            except Exception:
                continue
            sup = re.search(r'^\.super\s+(L[\w/$]+;)', head, re.M)
            if not sup or sup.group(1) not in supers:
                continue
            cls = re.search(r'^\.class.*?\s(L[\w/$]+;)', head, re.M)
            if cls:
                cands.append(cls.group(1)[1:-1].replace('/', '.'))
    return sorted(set(cands))

inferred = []
if not real_app:
    inferred = infer_real_app()
    # 推断结果里必须剔掉壳入口本身。拿壳当"真实入口"等于没改，
    # 而且报告会写成 A → A，AI 看见箭头就以为改好了，后面照样崩。
    inferred = [x for x in inferred if x not in rule['entries']]
    if len(inferred) == 1:
        real_app = inferred[0]
if not real_app and inferred:
    res['真实入口候选'] = inferred[:10]
    res['需要你指定real_app'] = ('找到 %d 个继承 Application 的类，工具不替你猜，'
                                 '请挑一个传给 real_app' % len(inferred))

# 最后一道护栏：不管 real_app 是推断的还是人传的，只要它落在壳入口清单里就作废。
# 用户/AI 手滑传成壳类的情况真发生过。
if real_app and real_app in rule['entries']:
    res['real_app无效'] = ('%s 就是壳入口本身，换成它等于没改。'
                           '请传真正的业务 Application（看「真实入口候选」），'
                           '或者留空并确认业务侧没有自定义 Application' % real_app)
    real_app = ''

# ---------------- ①-b 把清单里残留的壳组件整条删掉 ----------------
# 光换入口是不够的。360 还会往清单里塞自己的组件，最典型的是
#   <provider android:name="com.qihoo.util.XProvider" .../>
# 我们把 smali/com/qihoo 目录删了、入口也换回来了，可这条 provider 还挂在清单上，
# 系统启动时照样会去 new 它 → ClassNotFoundException，直接闪退。
# 所以凡是 android:name 落在壳包名下的组件标签，必须连标签一起删，不是「只报不改」。
def _shell_prefixes(rl):
    ps = set()
    for sd in rl.get('smali_dirs', []):
        ps.add(sd.replace('/', '.'))
    for en in rl['entries']:
        seg = en.split('.')
        if len(seg) >= 3:
            ps.add('.'.join(seg[:-1]))     # 去掉最后一段类名，com.tencent.StubShell.TxAppEntry -> com.tencent.StubShell
        elif len(seg) == 2:
            ps.add(en)
    return sorted(ps)


SH_PREFIX = _shell_prefixes(rule)


def _is_shell_name(nm):
    return any(nm == p or nm.startswith(p + '.') for p in SH_PREFIX)


# 组件标签：成对的整块删，自闭合的整条删。meta-data 一般不是类名，但壳经常用它带参数，一起处理。
COMP_RE = re.compile(r'(?s)<(provider|service|receiver|activity-alias|activity|meta-data)\b'
                     r'[^>]*?(?:/>|>.*?</\1\s*>)')
TAG_NAME_RE = re.compile(r'android:name="([^"]+)"')

new_txt = man_txt
man_removed = []
if man_p and man_txt:
    for m in list(COMP_RE.finditer(new_txt)):
        nm = TAG_NAME_RE.search(m.group(0))
        if nm and _is_shell_name(nm.group(1)):
            man_removed.append('%s %s' % (m.group(1), nm.group(1)))
    # 倒着删，避免前面的删除把后面的偏移量顶乱
    for m in reversed(list(COMP_RE.finditer(new_txt))):
        nm = TAG_NAME_RE.search(m.group(0))
        if nm and _is_shell_name(nm.group(1)):
            new_txt = new_txt[:m.start()] + new_txt[m.end():]

# ---------------- ①-c 入口换成真实 Application ----------------
if man_p and man_txt and real_app:
    for e in rule['entries']:
        new_txt = new_txt.replace('android:name="' + e + '"',
                                  'android:name="' + real_app + '"')

if man_p and man_txt:
    _changed = (new_txt != man_txt)
    if _changed and not dry:
        try:
            with open(man_p, 'w', encoding='utf-8') as f:
                f.write(new_txt)
        except Exception as e:
            res['Manifest写入失败'] = str(e)
    if man_removed:
        res['Manifest删壳组件'] = man_removed[:20]
        res['Manifest删壳组件数'] = len(man_removed)
        if dry:
            res['Manifest删壳组件说明'] = 'dry_run，还没真删'
    if real_app and any(('android:name="' + e + '"') in man_txt for e in rule['entries']):
        res['Manifest入口'] = '%s → %s%s' % ('/'.join(man_changes) or '(见清单)',
                                            real_app, '（已写入）' if (not dry and _changed) else '')
        res['Manifest已改'] = man_changes
    elif not man_changes:
        res['Manifest入口'] = '没发现壳入口声明（可能已经改过）'
    elif not real_app:
        res['Manifest入口'] = '发现壳入口 %s，没改成' % '/'.join(man_changes)
        # 这里必须说清楚"为什么没改"，否则 AI 看见「发现壳入口」就以为改完了。
        # real_app 为空有两种原因：压根没传，或者传了但就是壳类本身（被上面的护栏清掉了）。
        res['Manifest入口说明'] = (res.get('real_app无效')
                                   or ('没传 real_app，工具不替你猜。'
                                       '传真正的业务 Application 才会改写；'
                                       '业务侧确实没有自定义 Application 时，'
                                       '把 application 标签上的 android:name 整个删掉也行'))

# 剩下的壳字样（不是组件标签，比如 android:process="com.qihoo.xxx"）只报不改
if man_p and man_txt:
    _rest = sorted(set(re.findall(r'"((?:com\.qihoo|com\.stub|com\.frezrik|'
                                  r'com\.secneo|com\.tencent\.stub|s\.h\.e\.l\.l)[^"]*)"',
                                  new_txt)))
    _rest = [x for x in _rest if x not in rule['entries']]
    if _rest:
        res['Manifest剩余壳字样'] = _rest[:12]
        res['Manifest剩余壳字样说明'] = ('这些不是组件标签（可能是 process 属性），没动；'
                                          '打包前看一眼要不要清')

# ---------------- ② 正则删冒充壳的调用 ----------------
# 帖子里的正则有两类：
#   A. 整行匹配（绝大多数）
#   B. 带 move-result 的（正则1 的第 3、6 个分支）—— 它想同时删掉 invoke 和下一行的
#      move-result-object。smali 里这两句是两行，所以必须按「两行窗口」处理，
#      按单行 fullmatch 永远匹配不上，等于这两个分支是死的（第一版就踩了这个）。
single_pats = []
pair_pats = []   # (pre_pattern, 说明)
# 先把「一条规则里用 | 连了多个分支」的拆开。
# 不拆会出事：正则1 的 6 个分支里有 2 个带 move-result，按整串找第一个 move-result
# 切一半，会把后面那个分支整段算进「后半截」丢掉 —— 实测 DexFile 那个分支就是被这样吃掉的。
branches = []
for r in rule['regex']:
    # 论坛正则里 invoke-static 和 invoke-static/range 是混着写的。这俩其实是同一个调用的
    # 两种编码：寄存器个数一多，编译器自己就会改用 /range。实际 smali 里两种写法都会出现，
    # 照抄一种就必然漏删（实测：删了 8 条，还剩一条 Context 版本没删掉）。
    # 这里统一放宽成 (?:/range)?，两种写法都吃，六家厂商一起生效。
    r = r.replace('invoke-static/range ', 'invoke-static(?:/range)? ') \
         .replace('invoke-static ', 'invoke-static(?:/range)? ') \
         .replace('invoke-direct/range ', 'invoke-direct(?:/range)? ') \
         .replace('invoke-direct ', 'invoke-direct(?:/range)? ')
    for b in r.split('|'):
        b = b.strip()
        if b:
            branches.append(b)

for b in branches:
    try:
        if 'move-result' in b:
            k = b.find('move-result')
            pre = b[:k].rstrip()
            if pre.endswith('\\s*'):
                pre = pre[:-3]
            pair_pats.append((re.compile(pre), b[:70]))
        else:
            single_pats.append((b, re.compile(b)))
    except re.error as e:
        res.setdefault('正则编译失败', []).append({'regex': b[:80], 'error': str(e)})

removed = 0
files_hit = []
samples = []
kept = []          # 看见但不删的（业务代码在用）
post = {}          # 删完之后的内容（内存），dry_run 下也要能反映"改完长什么样"
MOVE_RES = re.compile(r'^move-result(?:-object|-wide)?\s')


def rel_of(p):
    return p.replace(root, '').replace('\\', '/').lstrip('/')


# 这个文件是不是壳自己的代码（在 smali/com/stub、smali/com/qihoo 这类目录里）。
# 用来区分「壳内部的调用」和「业务代码对壳的调用」—— 后者不能删。
def is_shell_file(pth):
    pp = pth.replace('\\', '/')
    if '/smali' not in pp:
        return False
    for d in rule.get('smali_dirs', []):
        if ('/' + d + '/') in pp:
            return True
    return False


_pkg = '|'.join(re.escape(d) for d in rule.get('smali_dirs', []))
LEFT_RE = re.compile(r'invoke-[\w/]+(?:/range)?\s+\{[^}]*\},\s*L(?:' + _pkg + r')/') if _pkg else None


# 壳自己的文件里，对壳包的任何 invoke 都能删（不论返回类型），连下一行的 move-result 一起删。
# 为什么这条单独加：正则表里的规则只覆盖「返回 void」的写法，壳内部还有一堆
# 返回 String / Application / [B 的内部互调（V5 老壳最典型：AssetsUtil->copyJiagu 返回
# String、ApplicationHook->replaceContentProvider 返回 Application）。
# 这些在壳自己的目录里本来就会随目录一起删掉，没必要当成「没敢删」报给用户制造噪音 ——
# 实测七天学堂一次报 4 条，条条都是壳自己的内部调用，AI 看了以为业务代码还在调壳。
SHELL_ANY_RE = LEFT_RE


for dp, dn, fns in os.walk(root):
    for fn in fns:
        if not fn.endswith('.smali'):
            continue
        p = os.path.join(dp, fn)
        try:
            with open(p, 'r', encoding='utf-8', errors='replace') as f:
                lines = f.read().splitlines()
        except Exception:
            continue
        out = []
        hit = 0
        i = 0
        n = len(lines)
        _in_shell = is_shell_file(p)
        while i < n:
            s = lines[i].strip()
            matched = False
            # 壳自己的文件：对壳包的任何 invoke 都删掉（含 move-result 一起吞）。
            # 这些调用属于壳内部实现，壳目录/purge_smali_dir 本来也会清掉；
            # 留着被当「没敢删」报出来只会让 AI 误判「业务代码还在调壳」。
            if s and _in_shell and SHELL_ANY_RE.match(s):
                matched = True
                j = i + 1
                while j < n and not lines[j].strip():
                    j += 1
                if j < n and MOVE_RES.match(lines[j].strip()):
                    if len(samples) < 15:
                        samples.append({'file': rel_of(p),
                                        'removed': (s + '  ⇢  ' + lines[j].strip())[:140]})
                    i = j
                elif len(samples) < 15:
                    samples.append({'file': rel_of(p), 'removed': s[:140]})
            if s and not matched:
                for raw, pr in single_pats:
                    if pr.fullmatch(s):
                        matched = True
                        if len(samples) < 15:
                            samples.append({'file': rel_of(p), 'removed': s[:140]})
                        break
                if not matched:
                    # 两行窗口：本行是 invoke，下一行是 move-result
                    for pr, raw in pair_pats:
                        if pr.fullmatch(s):
                            j = i + 1
                            while j < n and not lines[j].strip():
                                j += 1
                            if j < n and MOVE_RES.match(lines[j].strip()):
                                # 带取值的分支 = 有返回值 = 结果会被后面用。
                                # 只有在壳自己的代码里才删；业务代码里的必须留着，
                                # 否则后面的取值语句就成了「凭空出现的值」，一跑就崩。
                                # 实测依据：岛风这个包业务代码里有 25 处调
                                # StubApp.getOrigApplicationContext()，删了必崩。
                                if _in_shell:
                                    matched = True
                                    if len(samples) < 15:
                                        samples.append({'file': rel_of(p),
                                                        'removed': (s + '  ⇢  ' +
                                                                    lines[j].strip())[:140]})
                                    i = j  # 连同 move-result 一起吞掉
                                else:
                                    if len(kept) < 20:
                                        kept.append({'file': rel_of(p), '行': i + 1,
                                                     '内容': s[:150],
                                                     '为什么留着': '返回值被业务代码使用（下一行是取值），'
                                                                   '删了会崩；要清就改成等价的空实现'})
                                break
            if matched:
                hit += 1
                removed += 1
                i += 1
                continue
            out.append(lines[i])
            i += 1
        post[rel_of(p)] = out
        if hit:
            files_hit.append({'file': rel_of(p), '条数': hit})
            if not dry:
                try:
                    with open(p, 'w', encoding='utf-8') as f:
                        f.write('\n'.join(out) + '\n')
                except Exception as e:
                    res.setdefault('写入失败', []).append(p + ' : ' + str(e))

res['正则删除条数'] = removed
res['正则涉及文件数'] = len(files_hit)
if files_hit:
    res['正则涉及文件'] = sorted(files_hit, key=lambda x: -x['条数'])[:20]
if samples:
    res['正则删除样例'] = samples

# 业务代码在用的壳调用：看见了但没删（有返回值，删了崩）。
# 跟下面的「没敢删」不是一回事：这里是我们主动做出的取舍，必须单独说清楚。
if kept:
    res['保留的桩调用'] = kept
    res['保留的桩调用说明'] = ('这些调用在业务代码里，返回值被用到了，删掉会闪退。'
                                '想彻底清掉只能把它们改成等价的空实现（或改类名），不能直接删。')

# 删完再扫一遍：还有哪些壳调用没敢删？必须报出来。
# 最典型的是「返回值不是 void」的调用（比如 StubApp;->xxx()I 后面跟 move-result）：
# 把 invoke 删了会留下一条没有来源的 move-result，smali 校验直接过不去，包都打不出来。
# 这种不能硬删，只能报给用户，让他决定手改还是换方案 —— 绝不能默默跳过然后说「已清干净」。
# （LEFT_RE / _pkg 在 is_shell_file 那边已经定义过了，这里直接用）
left = []
# 上面「保留的桩调用」已经精确报过的，这里不重复报。
# ★ 按内容去重，不能按行号 —— 删掉几行之后行号全变了，按行号去重必然失效，
# 结果同一处调用在「正则删除样例」和「没敢删」里各出现一次（喵钱记账实测）。
_kept_marks = set((x['file'], x['内容']) for x in kept)
if LEFT_RE is not None:
    _left_seen = set()
    for dp, dn, fns in os.walk(root):
        for fn in fns:
            if not fn.endswith('.smali') or len(left) >= 15:
                continue
            p = os.path.join(dp, fn)
            relp = rel_of(p)
            # ★ 优先用内存里「删完之后」的内容。
            # dry_run 下文件根本没改，重读磁盘必然把刚刚列进「正则删除样例」的同一批
            # 调用又报成「没敢删」—— 七天学堂的 dry_run 就自相矛盾：attach 既在
            # 删除样例里、又在没敢删里，AI 看到只会更糊涂。
            src_lines = post.get(relp)
            if src_lines is None:
                try:
                    with open(p, 'r', encoding='utf-8', errors='replace') as f:
                        src_lines = f.read().splitlines()
                except Exception:
                    continue
            for i, line in enumerate(src_lines, 1):
                s = line.strip()
                if not s or not LEFT_RE.match(s):
                    continue
                if (relp, s[:150]) in _kept_marks:
                    continue
                if (relp, s) in _left_seen:
                    continue
                _left_seen.add((relp, s))
                left.append({'file': relp, '行': i, '内容': s[:150]})
                if len(left) >= 15:
                    break
if left:
    res['没敢删的壳调用'] = left
    res['没敢删的原因'] = ('这些调用还留在业务代码里。多数是返回值不是 void（后面跟着 move-result），'
                            '删掉会留下没有来源的 move-result，smali 校验过不去、包打不出来。'
                            '要么手工处理，要么按 lu_verify_clean 的结果如实告诉用户还有残留。')

# ---------------- ③ native onCreate 换回正常写法 ----------------
# 帖子里的写法是「只调 super 然后 return-void」。
# 注意：原文把 invoke-super 的父类写成了简写，真 smali 必须带父类全名，
# 这里从类文件的 .super 里取，取不到就跳过并报出来 —— 猜父类必然崩。
FIXED_ONC = []

def fix_oncreate(path, lines):
    out = []
    i = 0
    n = len(lines)
    sup = None
    for ln in lines[:60]:
        m = re.search(r'^\.super\s+(L[\w/$]+;)', ln.strip())
        if m:
            sup = m.group(1)
            break
    while i < n:
        ln = lines[i]
        m = re.match(r'^(\s*)\.method\s+(.*)onCreate\(Landroid/os/Bundle;\)V\s*$', ln)
        if m and 'native' in m.group(2):
            indent = m.group(1)
            j = i + 1
            while j < n and not lines[j].strip().startswith('.end method'):
                j += 1
            if j >= n:
                out.append(ln)
                i += 1
                continue
            if not sup:
                FIXED_ONC.append({'file': rel_of(path),
                                  '跳过': '找不到 .super，不敢猜父类'})
                out.extend(lines[i:j + 1])
                i = j + 1
                continue
            # 关键：正则只吃了「.method 和参数表之间」的那段访问修饰符，
            # 方法名和参数表必须自己补回来，否则会写出 .method public 这种残废声明。
            flags = re.sub(r'\s*native\s*', ' ', m.group(2)).strip()
            out.append('%s.method %s%s' % (indent, (flags + ' ') if flags else '',
                                           'onCreate(Landroid/os/Bundle;)V'))
            out.append(indent + '    .registers 2')
            out.append('')
            out.append(indent + '    invoke-super {p0, p1}, %s->onCreate(Landroid/os/Bundle;)V' % sup)
            out.append('')
            out.append(indent + '    return-void')
            out.append(indent + '.end method')
            FIXED_ONC.append({'file': rel_of(path),
                              '父类': sup,
                              '原声明': ln.strip()})
            i = j + 1
            continue
        out.append(ln)
        i += 1
    return out

for dp, dn, fns in os.walk(root):
    for fn in fns:
        if not fn.endswith('.smali'):
            continue
        p = os.path.join(dp, fn)
        try:
            with open(p, 'r', encoding='utf-8', errors='replace') as f:
                lines = f.read().splitlines()
        except Exception:
            continue
        if not any('native' in x and 'onCreate(Landroid/os/Bundle;)V' in x for x in lines):
            continue
        before = len(FIXED_ONC)
        new_lines = fix_oncreate(p, lines)
        if len(FIXED_ONC) > before and not dry:
            try:
                with open(p, 'w', encoding='utf-8') as f:
                    f.write('\n'.join(new_lines) + '\n')
            except Exception:
                pass

res['native onCreate 修复数'] = len([x for x in FIXED_ONC if '父类' in x])
res['native onCreate 跳过数'] = len([x for x in FIXED_ONC if '跳过' in x])
if FIXED_ONC:
    res['native onCreate 明细'] = FIXED_ONC[:15]

# ---------------- ④ 删壳 so / assets / smali 目录 ----------------
deleted = {'so': [], 'assets': [], 'dir': []}
kept = []

if purge_so:
    ldir = os.path.join(root, 'lib')
    for dp, dn, fns in os.walk(ldir):
        for fn in fns:
            if fn in rule['so']:
                p = os.path.join(dp, fn)
                deleted['so'].append(rel_of(p))
                if not dry:
                    try:
                        os.remove(p)
                    except Exception:
                        pass
    adir = os.path.join(root, 'assets')
    for dp, dn, fns in os.walk(adir):
        for fn in fns:
            if fn in rule['assets']:
                p = os.path.join(dp, fn)
                deleted['assets'].append(rel_of(p))
                if not dry:
                    try:
                        os.remove(p)
                    except Exception:
                        pass
    for dp, dn, fns in os.walk(adir):
        for fn in fns:
            if fn.startswith('libjiagu') or fn in rule['so']:
                pp = rel_of(os.path.join(dp, fn))
                if pp not in deleted['assets']:
                    kept.append(pp)

if purge_smali:
    for d in rule['smali_dirs']:
        for base in ('smali', 'smali_classes2', 'smali_classes3', 'smali_classes4'):
            p = os.path.join(root, base, d.replace('/', os.sep))
            if os.path.isdir(p):
                deleted['dir'].append(rel_of(p))
                if not dry:
                    try:
                        shutil.rmtree(p)
                    except Exception:
                        pass

res['删so'] = deleted['so'][:30]
res['删assets'] = deleted['assets'][:30]
res['删壳包目录'] = deleted['dir'][:10] if purge_smali else '(未启用 purge_smali_dir)'
if kept:
    res['assets里还有壳so'] = kept[:15]

# 删了 so 却还有 loadLibrary 引用 → 装上去必 UnsatisfiedLinkError
if purge_so and deleted['so']:
    names = set()
    for x in deleted['so']:
        b = os.path.basename(x)
        if b.startswith('lib') and b.endswith('.so'):
            names.add(b[3:-3])
    still = []
    if names:
        for dp, dn, fns in os.walk(root):
            for fn in fns:
                if not fn.endswith('.smali'):
                    continue
                p = os.path.join(dp, fn)
                try:
                    with open(p, 'r', encoding='utf-8', errors='replace') as f:
                        t = f.read()
                except Exception:
                    continue
                if 'loadLibrary' not in t:
                    continue
                for nm in names:
                    if '"' + nm + '"' in t:
                        still.append({'file': rel_of(p), '库': nm})
                        break
    if still:
        res['警告_还引用已删的so'] = still[:10]
        res['警告说明'] = ('上面这些 smali 还在 loadLibrary 已经被删掉的库，装上去会 '
                          'UnsatisfiedLinkError。先把这些调用删掉，或者改用 purge_smali_dir=true')

res['下一步'] = ('确认清单无误后 dry_run=false 重跑 → lu_verify_clean 验四个关键词是否归零'
                if dry else '接着跑 lu_verify_clean 验四个关键词；再 lu_rebuild 重打包')
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        var args = [q(dir), q(vendor), q(realApp), purgeSo ? '1' : '0',
                    purgeSmali ? '1' : '0', dry ? '1' : '0'].join(' ');
        return runPy(py, args, 900000);
    }

    // ============================================================
    // 免费版（老版）静态脱壳：360 qh 容器（lu_qh_unpack）
    // 来源：论坛「小奶瓶」给的《免费版数字加固静态脱壳》半成品源码（Java）。
    // 我们只移植了能自洽、能验证的部分：
    //   ① 明文直出探测（classes.dex 本身就是完整业务 dex 的混合加固）
    //   ② qh 容器解析（壳 dex 的 map_list 之后那段："qh" + 载荷长 + 配置长 + XOR 过的配置 + 记录表）
    //   ③ makeKey 密钥派生 + RC4 + LZMA1/zstd 信封解压
    // 原源码缺四个类（BusinessRecordRecovery / Classic052Recovery / Bytes / Crypto），
    // 其中 RC4 的 PRGA 变体只给了参数名，语义按「{1,0} = 标准 RC4」重建（原注释明确写了这点），
    // 并且每一步都用「LZMA 头 + 解压长度 + dex 头」三重校验兜底 —— 猜错就是解不出，不会给出假结果。
    // ============================================================
    var PY_QH = String.raw`
# -*- coding: utf-8 -*-
import sys, os, re, io, json, time, struct, zipfile, binascii, hashlib

apk = sys.argv[1]
out_dir = sys.argv[2] if len(sys.argv) > 2 else ''
dry = sys.argv[3] == '1'
budget = float(sys.argv[4]) if len(sys.argv) > 4 else 120.0

T0 = time.time()


def u32(b, o):
    return struct.unpack_from('<I', b, o)[0]


def align4(x):
    return (x + 3) & ~3


def b2i(x):
    return x if isinstance(x, int) else ord(x)


res = {'APK': apk, '模型': '免费版 360（0x52 整包 / 0xC6 记录 / qh 容器）'}

# ---------- 读 classes.dex ----------
try:
    z = zipfile.ZipFile(apk)
except Exception as e:
    print('LURESULT:' + json.dumps({'error': '打不开 APK：' + str(e)}, ensure_ascii=False))
    sys.exit(0)
shell = None
for n in z.namelist():
    if n == 'classes.dex':
        shell = z.read(n)
        break
if shell is None:
    print('LURESULT:' + json.dumps({'error': 'APK 里没有 classes.dex'}, ensure_ascii=False))
    sys.exit(0)
res['classes.dex大小'] = len(shell)
res['classes.dex里dex个数'] = sum(1 for n in z.namelist() if re.match(r'^classes\d*\.dex$', n))


# ---------- ① 明文直出探测 ----------
# classes.dex 本身可能就是完整业务 dex（混合加固：明文打包 + 运行时 native 保护）。
# 判据全部读 DEX 头，不看包名不猜大小：壳 stub 只有 4~28 个类，业务 dex 都是上千。
def plaintext_probe(dex):
    if len(dex) < 0x70 or dex[0:4] != b'dex\n' or dex[7] != 0:
        return False, '不是 dex（magic 不对）', 0
    file_size = u32(dex, 0x20)
    hdr = u32(dex, 0x24)
    endian = u32(dex, 0x28)
    cds = u32(dex, 0x60)
    cdo = u32(dex, 0x64)
    if file_size != len(dex):
        return False, 'file_size(%d) != 实际长度(%d)' % (file_size, len(dex)), cds
    if hdr != 0x70 or endian != 0x12345678:
        return False, 'header_size / endian_tag 不对', cds
    if cds <= 1000:
        return False, 'class_defs 只有 %d 个（壳 stub 量级，不是业务 dex）' % cds, cds
    if cdo + cds * 32 > file_size:
        return False, 'class_defs 越界（结构不自洽）', cds
    return True, 'file_size 自洽 + class_defs %d 个' % cds, cds


ptx, ptx_why, ptx_cds = plaintext_probe(shell)
res['明文直出'] = {'命中': ptx, '依据': ptx_why, 'class_defs': ptx_cds}


# ---------- ② qh 容器解析 ----------
def parse_qh(bb):
    if len(bb) < 0x70:
        return None, '太短'
    map_off = u32(bb, 0x34)
    if map_off + 4 > len(bb):
        return None, 'map_list 偏移越界'
    map_cnt = u32(bb, map_off)
    logical_end = align4(map_off + 4 + map_cnt * 12)
    if logical_end + 12 > len(bb):
        return None, '逻辑尾部越界'
    if bb[logical_end:logical_end + 2] != b'qh':
        return None, '逻辑尾部没有 qh 容器（%r）' % bb[logical_end:logical_end + 4]
    payload_len = u32(bb, logical_end + 4)
    declared_cfg = u32(bb, logical_end + 8)
    meta = logical_end + 12
    tail = bb[meta:]
    mask = 0
    for p in range(0, max(0, len(tail) - 4)):
        w = bb[meta + p:meta + p + 4]
        if w == b'\xd6\xed\xc6\xc6':
            mask = 0xC6
            break
        if w == b'\x22\x39\x52\x52':
            mask = 0x52
            break
    if mask == 0:
        mask = 0xC6
    decoded = bytes(b2i(t) ^ mask for t in tail)

    def value_len(enc):
        if 0x50 <= enc <= 0x5F:
            return enc ^ 0x60
        if 0x60 <= enc <= 0x6F:
            return enc ^ 0x40
        if 0x70 <= enc <= 0x7F:
            return enc ^ 0x60
        return enc

    last = -1
    for pos in range(0, max(0, len(decoded) - 11)):
        if decoded[pos:pos + 4] != b'\x10\x2b\x00\x00':
            continue
        kl = u32(decoded, pos + 4)
        vl = value_len(u32(decoded, pos + 8))
        end = pos + 12 + kl + vl
        if end <= len(decoded):
            last = end

    cands = []
    if 0 < declared_cfg < len(decoded) and declared_cfg != last:
        cands = [declared_cfg, last]
    else:
        cands = [last]

    recs = None
    cfg_end = -1
    for ce in cands:
        if ce < 0 or ce >= len(decoded):
            continue
        try:
            cur = meta + ce
            cnt = u32(bb, cur)
            cur += 4
            if not (0 < cnt <= 32):
                continue
            got = []
            for idx in range(cnt):
                rl = u32(bb, cur)
                el = u32(bb, cur + 4)
                cur += 8
                if not (rl >= 4 and el <= rl - 4):
                    got = None
                    break
                body = rl - 4
                if cur + body > len(bb):
                    got = None
                    break
                got.append({'recordLength': rl, 'encryptedLength': el,
                            'encrypted': bb[cur:cur + el],
                            'tail': bb[cur + el:cur + body]})
                cur += body
            if got:
                recs = got
                cfg_end = ce
                break
        except Exception:
            continue
    if recs is None:
        return {'mask': mask, 'declared': declared_cfg, 'marker': last,
                'error': '记录表解析失败'}, None
    raw_cfg = bb[meta:meta + cfg_end]
    return {'mask': mask, 'declared': declared_cfg, 'marker': last,
            'rawConfig': raw_cfg, 'records': recs,
            'logicalEnd': logical_end, 'payloadLength': payload_len}, None


cont, cerr = parse_qh(shell)
if cerr:
    res['qh容器'] = {'找到': False, '原因': cerr}
    # qh 容器找不到也是有效结论：要么本来就是明文 dex（混合加固），要么根本不是这个模型。
    if ptx:
        res['结论'] = ('classes.dex 本身就是完整业务 dex（混合加固：明文打包 + 运行时 native 保护），'
                     '直接拿它当 classes.dex 用就行，不用脱壳')
    else:
        res['结论'] = ('不是 qh 容器模型（%s），也不是明文直出 —— 这个包不走本工具。'
                     '用 rk_auto 判代次，再按新版（libjiagu_art / 函数抽取）那条路走 lu_dump' % cerr)
    print('LURESULT:' + json.dumps(res, ensure_ascii=False))
    sys.exit(0)

res['qh容器'] = {'找到': True, 'XOR掩码': hex(cont['mask']),
                 '记录数': len(cont['records']),
                 '载荷长': cont['payloadLength'],
                 '配置长': {'声明': cont['declared'], '标记闭合': cont['marker']}}
res['记录清单'] = [{'序号': i, '总长': r['recordLength'], '密文长': r['encryptedLength'],
                    '尾长': len(r['tail'])} for i, r in enumerate(cont['records'])]


# 配置区里的 KV（jiaguVersion / protect-time / APPKEY 这些），XOR 掩码还原后按 TLV 读
def extract_kv(raw_cfg, mask):
    dec = bytes(b2i(c) ^ mask for c in raw_cfg)
    kv = {}
    pos = 0
    while pos + 12 <= len(dec):
        if dec[pos:pos + 4] != b'pk\x00\x00':
            pos += 1
            continue
        p = pos + 4
        while p + 8 <= len(dec):
            kl = u32(dec, p)
            vl = u32(dec, p + 4)
            if kl <= 0 or kl > 64 or vl < 0 or vl > 256 or p + 8 + kl + vl > len(dec):
                break
            k = dec[p + 8:p + 8 + kl]
            v = dec[p + 8 + kl:p + 8 + kl + vl]
            try:
                kv[k.decode('latin-1').strip()] = v.decode('latin-1').strip()
            except Exception:
                pass
            p += 8 + kl + vl
        pos = p
    return kv


kv = extract_kv(cont['rawConfig'], cont['mask'])
if kv:
    res['配置KV'] = kv


# ---------- ③ 密钥派生 + RC4 + 信封 ----------
# makeKey：原样照抄 Java，注意 Java 的 int 是 32 位回绕，这里每步 & 0xFFFFFFFF 对齐。
def make_key(raw, seed, flag):
    out = bytearray(16)
    rolling = 2
    for i in range(len(raw)):
        v = b2i(raw[i])
        rolling = (rolling * 31 + v) & 0xFF
        out[i % 16] = v
    acc = 2 * len(raw)
    for i in range(len(raw)):
        v = b2i(raw[i])
        mix = (v ^ i) ^ ((acc * 5) & 0xFF)
        slot = i % 16
        old = out[slot]
        low = (old & 0x0F) ^ rolling
        high = ((old >> 4) & 0x0F) ^ rolling
        out[slot] = ((rolling ^ seed) ^ (low | high)) & 0xFF
        seed = (seed ^ (mix ^ (acc + rolling + len(raw) + flag))) & 0xFFFFFFFF
        acc += 1
    return bytes(out), seed


def rc4_ksa(key):
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + b2i(key[i % len(key)])) & 0xFF
        s[i], s[j] = s[j], s[i]
    return s


def rc4_x(data, s0, i0, j0, istep, jadd):
    # 变体语义：{1,0} 就是标准 RC4（原源码注释写明）。istep 是 i 的步进，jadd 是 j 的固定加数。
    s = list(s0)
    i = i0
    j = j0
    out = bytearray(len(data))
    n = len(s)
    for k in range(len(data)):
        i = (i + istep) & 0xFF
        j = (j + s[i] + jadd) & 0xFF
        s[i], s[j] = s[j], s[i]
        out[k] = b2i(data[k]) ^ s[(s[i] + s[j]) & 0xFF]
    return bytes(out)


def head_looks_lzma(h, enc_len):
    if len(h) < 13:
        return False
    if b2i(h[0]) >= 225:
        return False
    out_len = u32(h, 5)
    packed = u32(h, 9)
    return 0x70 <= out_len <= 128 * 1024 * 1024 and packed == enc_len - 13


def head_looks_zstd(h):
    return len(h) >= 4 and h[0:4] == b'\x28\xb5\x2f\xfd'


def lzma_decode(env):
    import lzma
    prop = b2i(env[0])
    dic = u32(env, 1)
    out_len = u32(env, 5)
    packed = u32(env, 9)
    lc = prop % 9
    rest = prop // 9
    lp = rest % 5
    pb = rest // 5
    if lc + lp > 4 or dic <= 0:
        return None
    body = env[13:13 + packed]
    try:
        d = lzma.LZMADecompressor(format=lzma.FORMAT_RAW,
                                  filters=[{'id': lzma.FILTER_LZMA1, 'dict_size': dic,
                                            'lc': lc, 'lp': lp, 'pb': pb}])
        out = d.decompress(body, out_len)
    except Exception:
        return None
    if len(out) != out_len:
        return None
    return out


def zstd_decode(env):
    try:
        import zstandard as zstd
        return zstd.ZstdDecompressor().decompress(env, max_output_size=128 * 1024 * 1024)
    except Exception:
        pass
    # 没装模块就找系统命令，都没有就老实说解不了
    import subprocess, tempfile
    for exe in ('zstd', 'unzstd'):
        try:
            p = subprocess.run([exe, '-d', '-c'], input=env, stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, timeout=120)
            if p.returncode == 0 and p.stdout:
                return p.stdout
        except Exception:
            continue
    return None


def try_envelope(env):
    if head_looks_zstd(env):
        out = zstd_decode(env)
        return (out, 'RC4+zstd') if out else (None, None)
    if head_looks_lzma(env, len(env)):
        out = lzma_decode(env)
        if out:
            return out, 'RC4+LZMA'
    # 有些版本不走信封，直接是明文 dex
    if env[:4] == b'dex\n':
        return env, 'RC4+明文dex'
    return None, None


DEX_PRGA_VARIANTS = [(2, 1), (1, 0), (1, 1), (2, 0), (3, 1), (4, 1), (3, 0), (4, 0)]
CONFIG_SEEDS = [222, 171]


def detect_config(raw_cfg, first_enc):
    enc_len = len(first_enc)
    if enc_len < 13:
        return None
    head = first_enc[:13]

    def check(ks):
        # 强判据：LZMA 头里 packedLen 必须正好等于 encLen-13；
        # 弱判据：首字节 <225（LZMA prop）或 zstd 魔数。两个都过才算命中。
        if b2i(ks[0]) >= 225:
            h0 = bytes(b2i(head[i]) ^ b2i(ks[i]) for i in range(4))
            if h0 != b'\x28\xb5\x2f\xfd':
                return False
        out = bytes(b2i(head[i]) ^ b2i(ks[i]) for i in range(13))
        if head_looks_zstd(out):
            return True
        return head_looks_lzma(out, enc_len)

    def probe(key, istep, jadd, i0, j0):
        # 只算 13 个密钥流字节，算完把 S 还原（比每个候选都复制 256 字节快一个数量级）
        s = list(S0)
        i = i0
        j = j0
        ks = bytearray(13)
        swaps = []
        for k in range(13):
            i = (i + istep) & 0xFF
            j = (j + s[i] + jadd) & 0xFF
            s[i], s[j] = s[j], s[i]
            swaps.append((i, j))
            ks[k] = s[(s[i] + s[j]) & 0xFF]
        hit = check(ks)
        for a, b in reversed(swaps):
            s[a], s[b] = s[b], s[a]
        return hit

    for seed in CONFIG_SEEDS:
        if time.time() - T0 > budget:
            return None
        key, _ = make_key(raw_cfg, seed, 0)
        S0 = rc4_ksa(key)
        # 先试最常见的「标准 RC4 + 初态 0,0」，命中就秒回
        for (istep, jadd) in DEX_PRGA_VARIANTS:
            if probe(key, istep, jadd, 0, 0):
                return {'seed': seed, 'istep': istep, 'jadd': jadd,
                        'i0': 0, 'j0': 0, 'key': key}
        # 再全量扫初态（65536 组合 × 8 变体，纯 Python 大概一两分钟，靠 budget 兜底）
        for (istep, jadd) in DEX_PRGA_VARIANTS:
            for i0 in range(256):
                for j0 in range(256):
                    if probe(key, istep, jadd, i0, j0):
                        return {'seed': seed, 'istep': istep, 'jadd': jadd,
                                'i0': i0, 'j0': j0, 'key': key}
                if time.time() - T0 > budget:
                    return None
    return None


res['密钥探测'] = {'预算秒': budget}
first = cont['records'][0]['encrypted']
cfg = detect_config(cont['rawConfig'], first)
if cfg is None:
    res['密钥探测']['结果'] = ('预算内没找到（可能不是这个模型，或配置种子不在这两个值里：'
                            '222 / 171）。可以加大 max_seconds 再试')
else:
    res['密钥探测']['结果'] = '命中'
    res['密钥探测'].update({'seed': cfg['seed'], '变体': {'iStep': cfg['istep'], 'jAdd': cfg['jadd']},
                          '初态': {'i0': cfg['i0'], 'j0': cfg['j0']}})

    out_files = []
    for idx, r in enumerate(cont['records']):
        if time.time() - T0 > budget * 2:
            res.setdefault('跳过', []).append('rec%d 超出时间预算' % idx)
            continue
        if r['encryptedLength'] < 13:
            continue
        env = rc4_x(r['encrypted'], rc4_ksa(cfg['key']), cfg['i0'], cfg['j0'],
                    cfg['istep'], cfg['jadd'])
        plain, mode = try_envelope(env)
        if plain is None:
            res.setdefault('失败记录', []).append(
                {'序号': idx, '原因': 'RC4 解完也不是合法信封（LZMA 头/解压长度都不对）'})
            continue
        full = plain + r['tail'] if r['tail'] else plain
        if full[:4] != b'dex\n':
            res.setdefault('失败记录', []).append(
                {'序号': idx, '原因': '解出来的不是 dex（拼上尾段后 magic 不对）'})
            continue
        # dex 头里的 file_size 必须等于实际长度，否则是解错或截断
        fs = u32(full, 0x20) if len(full) >= 0x24 else -1
        item = {'序号': idx, '大小': len(full), '信封': mode,
                'SHA256': hashlib.sha256(full).hexdigest()[:16],
                'file_size自洽': fs == len(full)}
        if out_dir and not dry:
            os.makedirs(out_dir, exist_ok=True)
            name = 'qh_classes%d.dex' % (idx + 1)
            with open(os.path.join(out_dir, name), 'wb') as f:
                f.write(full)
            item['写出'] = name
        out_files.append(item)
    if out_files:
        res['解出DEX'] = out_files

res['耗时秒'] = round(time.time() - T0, 1)
if res.get('解出DEX'):
    res['结论'] = '解出 %d 个业务 dex —— 这是纯静态解出来的，没跑 App、没 dump' % len(res['解出DEX'])
elif res.get('明文直出', {}).get('命中'):
    res['结论'] = 'classes.dex 本身就是完整业务 dex，不用解——直接拿它当 classes.dex 用'
else:
    res['结论'] = '没能解出业务 dex（看上面的「qh容器」和「密钥探测」）'
print('LURESULT:' + json.dumps(res, ensure_ascii=False))
`;

    // 免费版静态脱壳（qh 容器）
    function lu_qh_unpack(params) {
        var apk = params.apk_path;
        if (!apk) throw new Error('缺少 apk_path');
        var outDir = params.output_dir || '';
        var dry = !(params.dry_run === 'false' || params.dry_run === false);
        var secs = String(params.max_seconds || '120');
        var args = [q(apk), q(outDir), dry ? '1' : '0', q(secs)].join(' ');
        return runPy(PY_QH, args, 900000).then(function (r) {
            if (r && r.raw) throw new Error('python 没返回结构化结果，原始输出：' + String(r.raw).substring(0, 400));
            return r;
        });
    }

    // ============================================================
    // 清完自检（lu_verify_clean）
    // 帖子给的标准：分 4 次搜 com.qihoo / com.stub / jiagu / vmp，
    // 旧版新版必须全部为 0 才算干净；企业版允许极少量业务代码残留。
    // ============================================================
    function lu_verify_clean(params) {
        var dir = params.decoded_dir || '';
        var apk = params.apk_path || '';
        if (!dir && !apk) throw new Error('decoded_dir 和 apk_path 至少给一个');
        var kws = params.keywords || 'com.qihoo,com.stub,jiagu,vmp';

        var py = String.raw`
import sys, os, re, json, zipfile

target = sys.argv[1]
is_apk = sys.argv[2] == '1'
kws = [x.strip() for x in (sys.argv[3] or '').split(',') if x.strip()]

# 帖子写的关键词是点号形式（com.qihoo / com.stub），但 dex 与 smali 里的类描述符是
# 斜杠形式（Lcom/stub/StubApp;）。只搜点号一个都搜不到 —— 第一版就是这么假绿的。
# 所以每个关键词都同时搜点号与斜杠两种写法。
def variants(k):
    vs = [k]
    if '.' in k:
        vs.append(k.replace('.', '/'))
    if '/' in k:
        vs.append(k.replace('/', '.'))
    return vs


KV = [(k, variants(k)) for k in kws]

res = {'目标': target, '类型': 'APK' if is_apk else '解包目录', '关键词': kws,
       '说明': '每个关键词同时按点号与斜杠两种写法搜（dex 里是 Lcom/stub/…）'}

ADV = [
    ('壳函数调用(通用)', r'invoke-\w+(?:/range)?\s+\{[^}]*\},\s*Lcom/qihoo/[\w/$]*;->'),
    ('企业版VMP调用', r'invoke-\w+(?:/range)?\s+\{[^}]*\},\s*Lcom/qihoo/enterprise/[\w/$]*;->'),
]

hits = {}
adv = {}
files_n = 0

# 「疑似巧合」单独记账。壳关键词只有 3~7 个字节，很多正常文件里恰好就含这几个字节：
#   libsqlc-ndk-native-driver.so（SQLCipher）的符号表里有 vmp
#   res/**/*.png 的压缩数据里随机出现 vmp / com.qihoo
#   META-INF/UK.SF、MANIFEST.MF 是签名与清单，字节等于随机
#   assets/baimingdan.csv（白名单）本来就列了一堆渠道名
# 原来这些一律算进「残留关键词」→ 包其实干净，报告却写「4 个关键词全残留」，
# AI 于是反复清、越清越懵，最后给用户下「这个包清不干净」的错误结论。
soft = {}


def soft_bump(k, path, pos, kind):
    e = soft.setdefault(k, {'次数': 0, '文件': {}})
    e['次数'] += 1
    if len(e['文件']) < 6:
        e['文件'].setdefault(path, {'首个位置': pos, '片段': kind})


# 机器码 / 压缩图像 / 资源 / 签名文件 —— 这些里面出现关键词只可能是巧合，不算壳残留。
BINARY_EXT = ('.so', '.dll', '.png', '.jpg', '.jpeg', '.webp', '.gif', '.bmp',
              '.csv', '.dat', '.bin', '.pak', '.arsc', '.ttf', '.otf', '.woff',
              '.woff2', '.mp3', '.mp4', '.ogg', '.wav', '.zip', '.jar', '.db',
              '.dex.bak', '.apk', '.properties.enc')


def is_binary_like(name):
    low = name.lower()
    if low.startswith('meta-inf/'):
        return True
    if low.endswith(BINARY_EXT):
        return True
    return False


def bump(k, path, line_no, text):
    e = hits.setdefault(k, {'次数': 0, '文件': {}})
    e['次数'] += 1
    f = e['文件']
    if len(f) < 8:
        f.setdefault(path, {'首个位置': line_no, '片段': text[:160]})


# dex 要分两种命中，这个区分决定了报告有没有用：
#   ① 类描述符形态：dex 里只要代码引用某个类，字符串池里必然存 'Lcom/stub/StubApp;'
#      → 这才是「还在调壳」的硬证据
#   ② 裸字符串：某三方 SDK 里写了 "com.qihoo" 这种字面量（渠道名、UA、埋点 key）
#      → 只是字符串，删不掉也不用删
# 原来两种混在一起，于是「com.qihoo 残留 1 次」既可能是壳调用也可能是渠道字符串，
# 报告等于没说。
def scan_dex(data):
    hard = {}
    loose = {}
    for k, vs in KV:
        raw = 0
        desc = 0
        for v in vs:
            b = v.encode('utf-8')
            raw += data.count(b)
            if '/' in v:
                # 斜杠形式的类描述符一定带前导 L（数组类型 '[Lcom/stub/X;' 里也含
                # 'Lcom/stub'，同样被这一条覆盖，不用再单独数 '[' 形态，否则重复计数）
                desc += data.count(('L' + v).encode('utf-8'))
        if desc:
            hard[k] = (desc, raw)
        elif raw:
            # 位置要遍历所有写法找第一个命中的。
            # 原来固定用 vs[-1]，可 dex 里存的可能是点号形态，find 直接返回 -1，
            # 报告里就出现「首个位置: -1」这种等于没给的信息（喵钱记账实测）。
            pos = -1
            for v in vs:
                p0 = data.find(v.encode('utf-8'))
                if p0 >= 0:
                    pos = p0
                    break
            loose[k] = (raw, pos)
    return hard, loose


# 文件名也要查。壳的 so 叫 libjiagu.so、assets 叫 jiagu.dat —— 这些名字里就带关键词，
# 但以前只扫 .smali/.xml/.json/.properties 的**内容**，压根不看文件名和 .so，
# 于是 libjiagu.so 明明躺在 lib/ 下，报告却写「干净: true」。用户当场就能看见特征还在。
def check_name(rel):
    low = rel.lower()
    for k, vs in KV:
        if any(v.lower() in low for v in vs):
            e = hits.setdefault(k, {'次数': 0, '文件': {}})
            e['次数'] += 1
            if len(e['文件']) < 8:
                e['文件'].setdefault(rel, {'首个位置': 0, '片段': '（文件名/路径命中）'})


if is_apk:
    try:
        z = zipfile.ZipFile(target)
    except Exception as e:
        print("LURESULT:" + json.dumps({'error': '打不开 APK：' + str(e)}, ensure_ascii=False))
        sys.exit(0)

    dex_stored = 0
    dex_n = 0
    dex_unc = 0
    dex_cmp = 0
    shell_bin = []

    for info in z.infolist():
        n = info.filename
        files_n += 1
        check_name(n)
        low = n.lower()
        is_dex = low.endswith('.dex')
        if is_dex:
            dex_n += 1
            dex_unc += info.file_size
            dex_cmp += info.compress_size
            if info.compress_type == zipfile.ZIP_STORED:
                dex_stored += 1
        if low.endswith(('.so', '.dat', '.bin', '.jgapp')) or '/assets/' in low:
            for k, vs in KV:
                if any(v.lower() in low for v in vs):
                    shell_bin.append(n)
                    break
        # 内容扫描：只有 dex 算硬证据；其余（so / 图片 / csv / 签名文件 / 二进制 xml）
        # 里出现关键词一律降级为「疑似巧合」。
        # 原来的写法对小文件一律读出来数出现次数，于是
        #   libsqlc-ndk-native-driver.so（符号表含 vmp）
        #   res/xxx/deco_4.png、META-INF/UK.SF、META-INF/MANIFEST.MF
        # 全被算成壳残留，包明明干净也报「还有 2 个关键词有残留」。
        if not is_dex and is_binary_like(n):
            try:
                if info.file_size <= 2 * 1024 * 1024:
                    data = z.read(n)
                    for k, vs in KV:
                        raw = sum(data.count(v.encode('utf-8')) for v in vs)
                        if raw:
                            pos = -1
                            for v in vs:
                                pos = data.find(v.encode('utf-8'))
                                if pos >= 0:
                                    break
                            soft_bump(k, n, pos, '（二进制/资源里的字节巧合，不是壳残留）')
            except Exception:
                pass
            continue
        try:
            data = z.read(n)
        except Exception:
            continue
        if is_dex:
            hard, loose = scan_dex(data)
            for k, (desc, raw) in hard.items():
                e = hits.setdefault(k, {'次数': 0, '文件': {}})
                e['次数'] += desc
                if len(e['文件']) < 8:
                    e['文件'][n] = {'首个位置': -1,
                                    '片段': '（dex 里有 %d 处类引用 Lcom/...; —— 业务代码在调壳类）'
                                            % desc}
            for k, (raw, pos) in loose.items():
                soft_bump(k, n, pos, '（dex 里只有裸字符串 %d 处，无类引用，多为渠道名/埋点 key）' % raw)
            txt = data.decode('latin-1')
            for label, pat in ADV:
                for m in re.finditer(pat, txt):
                    e = adv.setdefault(label, {'次数': 0, '文件': {}})
                    e['次数'] += 1
                    if len(e['文件']) < 5:
                        s = max(0, m.start() - 20)
                        e['文件'][n] = {'片段': txt[s:m.start() + 90].replace(chr(0), '')}
        elif not is_binary_like(n):
            # APK 里的非 dex 且非二进制 - 基本只有 assets 下的文本配置，按硬证据算
            for k, vs in KV:
                raw = sum(data.count(v.encode('utf-8')) for v in vs)
                if raw:
                    pos = -1
                    for v in vs:
                        pos = data.find(v.encode('utf-8'))
                        if pos >= 0:
                            break
                    e = hits.setdefault(k, {'次数': 0, '文件': {}})
                    e['次数'] += raw
                    if len(e['文件']) < 8:
                        e['文件'][n] = {'首个位置': pos, '片段': '（文本配置里的关键词）'}

    # 体积体检：dex 不压缩会让包体直接翻几倍（实测 40MB 的包重打成 137MB）。
    # 这不是行为问题，是「用户一眼就看出不对」的问题，所以要在这里主动报。
    if shell_bin:
        res['壳二进制残留'] = sorted(set(shell_bin))[:20]
        res['壳二进制残留说明'] = '这些是壳的 so / assets，名字里就带壳特征，必须删掉'
    if dex_n:
        apk_size = os.path.getsize(target)
        res['体积体检'] = {
            'APK总大小': round(apk_size / 1048576.0, 1),
            'dex个数': dex_n,
            'dex原始合计MB': round(dex_unc / 1048576.0, 1),
            'dex压缩后合计MB': round(dex_cmp / 1048576.0, 1),
            '未压缩的dex数': dex_stored,
        }
        if dex_stored >= 3:
            res['体积体检']['警告'] = ('有 %d 个 dex 是「不压缩」存进去的，'
                                      'dex 从 %.1fMB 压下来只要 %.1fMB。装得上没问题，'
                                      '但包体会被撑大好几倍。重打包时把 dex 改成压缩（DEFLATED），'
                                      '只有 .so 在 extractNativeLibs=false 时才必须保持不压缩。'
                                      % (dex_stored, dex_unc / 1048576.0, dex_cmp / 1048576.0))
            res['干净'] = False
            res['体积问题'] = True
else:
    shell_bin = []
    for dp, dn, fns in os.walk(target):
        for fn in fns:
            p = os.path.join(dp, fn)
            # 顺序不能反：必须先统一成 / 再 lstrip，否则 Windows 下会剩一个开头的 /lib/...
            rel = p.replace(target, '').replace(os.sep, '/').lstrip('/')
            files_n += 1
            # ① 文件名先查（抓 libjiagu.so / jiagu.dat 这类）
            check_name(rel)
            low = rel.lower()
            if low.endswith(('.so', '.dat', '.bin', '.jgapp')) or '/assets/' in low:
                for k, vs in KV:
                    if any(v.lower() in low for v in vs):
                        shell_bin.append(rel)
                        break
            # ② 内容查：文本类 + 壳相关的二进制（.so 之类只查文件名，不读内容，太慢）
            # dex 必须单独处理：我们的组装工具是把脱壳 dex 直接放进解包目录的，
            # 这时壳的类名全在 dex 字符串表里，而那些 dex 不是 .smali，
            # 以前的扫描器直接跳过了 —— 于是又出现一次「自检说全 0、业务代码还在调 StubApp」。
            if fn.lower().endswith('.dex'):
                try:
                    with open(p, 'rb') as f:
                        data = f.read()
                except Exception:
                    continue
                hard, loose = scan_dex(data)
                for k, (desc, raw) in hard.items():
                    e = hits.setdefault(k, {'次数': 0, '文件': {}})
                    e['次数'] += desc
                    if len(e['文件']) < 8:
                        e['文件'].setdefault(rel, {
                            '首个位置': -1,
                            '片段': '（dex 里有 %d 处类引用 Lcom/...; —— 业务代码在调壳类）' % desc})
                for k, (raw, pos) in loose.items():
                    soft_bump(k, rel, pos,
                              '（dex 里只有裸字符串 %d 处，无类引用，多为渠道名/埋点 key）' % raw)
                continue
            if not fn.endswith(('.smali', '.xml', '.json', '.properties', '.yml', '.txt')):
                continue
            try:
                with open(p, 'r', encoding='utf-8', errors='replace') as f:
                    lines = f.read().splitlines()
            except Exception:
                continue
            for i, line in enumerate(lines, 1):
                if not line:
                    continue
                # ★ smali 的注释行必须跳过。
                # 我们自己清壳时就会往 StubApp.smali 里写注释说明（比如「原壳的 <clinit>
                # 会 loadLibrary(...)，这里已置空」），结果自检把这条注释当成壳残留报出来
                # —— 实测岛风报「残留_jiagu 11 次 / smali/com/stub/StubApp.smali」，
                # 其中就有工具自己写的注释。自己写的字不能算自己没清干净。
                st = line.strip()
                if fn.endswith('.smali') and st.startswith('#'):
                    continue
                # 必须用 KV 的双写法匹配：smali 里写的是 Lcom/stub/StubApp;，
                # 只搜点号 com.stub 会一个都搜不到，然后假绿报「已归零」。
                for k, vs in KV:
                    if any(v in line for v in vs):
                        bump(k, rel, i, st)
                for label, pat in ADV:
                    if re.search(pat, line):
                        e = adv.setdefault(label, {'次数': 0, '文件': {}})
                        e['次数'] += 1
                        if len(e['文件']) < 8:
                            e['文件'][rel] = {'首个位置': i, '片段': st[:160]}
    if shell_bin:
        res['壳二进制残留'] = sorted(set(shell_bin))[:20]
        res['壳二进制残留说明'] = '这些是壳的 so / assets，名字里就带壳特征，必须删掉'

res['扫描文件数'] = files_n
zero = []
for k in kws:
    v = hits.get(k)
    if v:
        res['残留_' + k] = v
    else:
        zero.append(k)
res['已归零关键词'] = zero
res['残留关键词'] = [k for k in kws if k in hits]
if adv:
    res['进阶检查'] = adv
# 疑似巧合单独一块，不参与干净判定，但必须让 AI 看见 ——
# 否则它会把「com.qihoo 疑似 1 次」当成真残留，无限重试清壳。
if soft:
    res['疑似巧合不算残留'] = soft
    res['疑似巧合说明'] = ('这些命中在 so / 图片 / csv / 签名文件里，是字节巧合或业务数据，'
                            '不是壳残留，也不影响能不能用。不要为了它们反复重打包。')

nz = len(res['残留关键词'])
_has_bin = bool(res.get('壳二进制残留'))
_has_adv = bool(adv)
_has_size = bool(res.get('体积问题'))
if nz == 0 and not _has_adv and not _has_bin:
    res['结论'] = '干净 —— 四个关键词全部为 0，没有壳函数调用'
    if soft:
        res['结论'] += '（另有 %d 个关键词只在二进制/资源里巧合命中，已单列，不用管）' % len(soft)
    res['干净'] = True
elif nz == 0 and _has_bin:
    res['结论'] = '关键词清零，但壳的 so / assets 文件还在（看「壳二进制残留」）'
    res['干净'] = False
elif nz == 0:
    res['结论'] = '关键词全清零，但还有壳函数调用痕迹，看 进阶检查'
    res['干净'] = False
else:
    res['结论'] = '还有 %d 个关键词有残留，清完再打包' % nz
    kinds = []
    for k in res['残留关键词']:
        v = hits.get(k) or {}
        for fnm, det in (v.get('文件') or {}).items():
            if '类引用' in (det.get('片段') or ''):
                kinds.append('%s（%s 里有类引用，是真残留）' % (k, fnm))
                break
        else:
            kinds.append('%s（只在 smali/清单/配置里出现）' % k)
    if kinds:
        res['残留性质'] = kinds[:8]
    res['干净'] = False
if _has_size:
    res['结论'] += '；另外包体偏大，dex 没压缩（看「体积体检」）'
if nz:
    res['注意'] = '企业版允许极少量业务代码自带这些字样，但 invoke 形态的壳调用必须为 0'
print("LURESULT:" + json.dumps(res, ensure_ascii=False))
`;
        return runPy(py, [q(dir || apk), dir ? '0' : '1', q(kws)].join(' '), 600000);
    }

    return {
        lu_env_check: wrap(lu_env_check),
        lu_probe: wrap(lu_probe),
        lu_dump: wrap(lu_dump),
        lu_decode: wrap(lu_decode),
        lu_scan: wrap(lu_scan),
        lu_fill_oncreate: wrap(lu_fill_oncreate),
        lu_splash_fix: wrap(lu_splash_fix),
        lu_stub_clean: wrap(lu_stub_clean),
        lu_manifest_fix: wrap(lu_manifest_fix),
        lu_dex_fix: wrap(lu_dex_fix),
        lu_v9_patch: wrap(lu_v9_patch),
        lu_code_patch: wrap(lu_code_patch),
        lu_codeitem_fill: wrap(lu_codeitem_fill),
        lu_fart_import: wrap(lu_fart_import),
        lu_rebuild: wrap(lu_rebuild),
        lu_dump_check: wrap(lu_dump_check),
        lu_zipfix: wrap(lu_zipfix),
        lu_repack_assemble: wrap(lu_repack_assemble),
        lu_stub_scaffold: wrap(lu_stub_scaffold),
        lu_dump_li: wrap(lu_dump_li),
        lu_all: wrap(lu_all),
        lu_clear_shell: wrap(lu_clear_shell),
        lu_verify_clean: wrap(lu_verify_clean),
        lu_qh_unpack: wrap(lu_qh_unpack)
    };
})();

exports.lu_env_check = lu.lu_env_check;
exports.lu_probe = lu.lu_probe;
exports.lu_dump = lu.lu_dump;
exports.lu_decode = lu.lu_decode;
exports.lu_scan = lu.lu_scan;
exports.lu_fill_oncreate = lu.lu_fill_oncreate;
exports.lu_splash_fix = lu.lu_splash_fix;
exports.lu_stub_clean = lu.lu_stub_clean;
exports.lu_manifest_fix = lu.lu_manifest_fix;
exports.lu_dex_fix = lu.lu_dex_fix;
exports.lu_v9_patch = lu.lu_v9_patch;
exports.lu_code_patch = lu.lu_code_patch;
exports.lu_codeitem_fill = lu.lu_codeitem_fill;
exports.lu_fart_import = lu.lu_fart_import;
exports.lu_rebuild = lu.lu_rebuild;
exports.lu_dump_check = lu.lu_dump_check;
exports.lu_zipfix = lu.lu_zipfix;
exports.lu_repack_assemble = lu.lu_repack_assemble;
exports.lu_stub_scaffold = lu.lu_stub_scaffold;
exports.lu_dump_li = lu.lu_dump_li;
exports.lu_all = lu.lu_all;
exports.lu_clear_shell = lu.lu_clear_shell;
exports.lu_verify_clean = lu.lu_verify_clean;
exports.lu_qh_unpack = lu.lu_qh_unpack;