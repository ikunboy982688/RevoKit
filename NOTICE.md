# NOTICE

**GG工坊（GOOG）**

> 应用显示名：中文「GG工坊」，其他语言「GOOG」。
> GitHub 仓库名沿用 `RevoKit`（内部代号，与显示名不一致属正常）。

本文件说明本项目的来源、修改内容、所包含的第三方组件及其许可条件。
最后更新：2026-11-02

---

## 一、本项目与上游的关系

本项目是 **[Operit AI](https://github.com/AAswordman/Operit)** 的修改版本（衍生作品），不是独立原创项目。

| 项 | 内容 |
|---|---|
| 上游项目 | Operit AI（Android 版） |
| 上游仓库 | https://github.com/AAswordman/Operit |
| 上游版本 | v1.12.2 |
| 上游许可 | **GNU Lesser General Public License v3.0**（全文见仓库根目录 `LICENSE`） |
| 本项目仓库 | https://github.com/ikunboy982688/RevoKit |
| 本项目许可 | 同样以 **LGPL-3.0** 发布（与上游一致，未变更许可） |
| 修改起始日期 | 2026-10-21 |
| 修改持续至 | 2026-11-02（及之后） |

### 1.1 修改声明（LGPL-3.0 第 5 条要求）

**本项目对上游 Operit AI 进行了修改。** 主要修改范围如下：

| 类别 | 修改内容 |
|---|---|
| 应用标识 | `applicationId` 由 `com.ai.assistance.operit` 改为 **`com.ggongfang.app`**；应用名改为「GG工坊」/「GOOG」；启动图标换为自有设计（荧光绿双 G 字标 + 深色底，前景位图 5 档密度，替代上游的蓝色矢量图标） |
| Kotlin 包名（namespace） | **未修改**，仍为 `com.ai.assistance.operit` |
| 新增原生代码 | 新增包 `core/revokit/`：`DexRepair`、`DexDumper`、`ApkRepacker`、`ShellCleaner`、`PackerIdentifier`、`PackerSignatures`、`FlutterProbe`、`ApkBrowser`、`DiskCleaner` |
| 新增界面 | 新增包 `ui/features/revokit/`：加固识别页、APK 拆包页、脱壳工作台（五页签）、一键清理页 |
| 新增 ToolPkg 脚本 | `assets/packages/` 下新增 `local_unpack.js`、`revokit_core.js`、`pack_360.js`、`revokit_net.js`、`packer_signatures.js`、`logfox_toolkit.js`、`tuoke_cloud.js`、`revokit_ws.js`，以及打包后的 `apktool.toolpkg` |
| 修改上游构建产物 | 重新构建了 `apktool-runtime-android.jar` 与 `jadx-runtime-android.jar`：前者修改 `brut/androlib/res/AaptManager.java`、`brut/androlib/res/decoder/ResNinePatchStreamDecoder.java`；后者修改 `jadx/core/dex/visitors/SaveCode.java`、`jadx/zip/parser/ZipDeflate.java`。**修改后的源码随仓库保存在 `examples/apktool/runtime_patch_src/`** |
| 默认人设与提示词 | 修改 `data/preferences/CharacterCard*`，当前人设协议版本 v15 |
| 功能删减 | 移除部分上游功能，清单见仓库 `功能删减清单.md` |

### 1.2 获取源代码（LGPL-3.0 第 4、6 条要求）

LGPL-3.0 要求：分发本软件的目标代码（APK）时，必须使接收者能够获得对应的完整源代码。

| 对象 | 获取方式 |
|---|---|
| 本项目修改后的完整源码 | `<待填：GitHub 仓库地址>`（含构建说明） |
| 本项目相对上游的改动 | 该仓库的 git 提交历史，以及与上游 v1.12.2 的 diff |
| 上游原始源码 | https://github.com/AAswordman/Operit |
| 被修改的 apktool / jadx 补丁源码 | 本仓库 `examples/apktool/runtime_patch_src/` |

若上述地址将来失效，请通过仓库 Issue 联系维护者索取源码。

---

## 二、随包分发的第三方组件

本项目分发的 APK 内含以下第三方组件。**完整的许可原文已随 App 打包**，位于
`assets/licenses/`（索引见该目录下 `00-INDEX.md`），可在设备上直接读取，无需解压任何嵌套包。

### 2.1 反编译运行时

| 组件 | 版本 | 许可 | 本项目是否修改 |
|---|---|---|---|
| Apktool | 3.0.1 | Apache-2.0 | 是（2 个 java 文件） |
| smali / baksmali | 3.0.9-dev | **BSD-3-Clause** | 否 |
| jadx | 1.5.2 | Apache-2.0 | 是（2 个 java 文件） |
| ANTLR 3.5.2 / ST4 4.0.8 / stringtemplate 3.2.1 | — | BSD-3-Clause | 否 |
| Apache Commons CLI | 1.11.0 | Apache-2.0 | 否 |
| Apache Commons IO / Lang3 / Text | 2.21.0 / 3.20.0 / 1.15.0 | Apache-2.0 | 否 |
| Google Guava（含 failureaccess、listenablefuture、j2objc-annotations） | 33.5.0-jre | Apache-2.0 | 否 |
| com.google.code.findbugs:jsr305 | 3.0.2 | BSD-3-Clause | 否 |
| error_prone_annotations / jcommander | 2.41.0 / 1.48 | Apache-2.0 | 否 |
| jadx 依赖：Indiana University Extreme! Lab / Javolution / ThoughtWorks XStream | — | BSD 类 | 否 |

### 2.2 签名与解析

| 组件 | 版本 | 许可 |
|---|---|---|
| apksig（com.android.tools.build） | 8.1.0 | Apache-2.0 |
| axml（com.github.Sable） | 2.0.0 | Apache-2.0 |
| Bouncy Castle（bcprov-jdk18on） | 1.78 | **MIT License** |
| org.json | 20240303 | **Public Domain** |

> 关于 org.json：该库早期版本曾附带 "The Software shall be used for Good, not Evil" 条款，
> 20240303 版的上游 LICENSE 已仅声明 "Public Domain."，本项目对该组件无附加义务。
> 依据留存于 `assets/licenses/org.json-20240303-PublicDomain.txt`。

### 2.3 AOSP 构件

| 组件 | 位置 | 许可 |
|---|---|---|
| Android framework 资源（`android-framework.jar`） | `assets/packages/apktool.toolpkg` 内 | Apache-2.0 |
| aapt2 预编译二进制 | `assets/templates/android/tools/aapt2/`、`assets/templates/flutter/android/tools/aapt2/`、toolpkg 内 `prebuilt/android/` | Apache-2.0 |

### 2.4 上游既有、未改动的第三方内容

以下许可已由上游随包分发，本项目未改动其声明文件：

| 位置 | 数量 | 归属 |
|---|---|---|
| `res/raw/license_*.txt` | 37 | ffmpeg-kit 及其原生依赖（freetype、opus、libvpx、gnutls、harfbuzz、libxml2 等） |
| `assets/MathJax/LICENSE` | 1 | MathJax |
| `assets/js/katex.LICENSE.txt`、`assets/js/terser.LICENSE` | 2 | KaTeX、Terser |
| `assets/fonts/MATERIAL_SYMBOLS_LICENSE.txt` | 1 | Material Symbols 字体 |
| `assets/org/scilab/forge/jlatexmath/fonts/licences/` | 2 | JLaTeXMath 字体（Knuth License、DSROM） |

---

## 三、Ubuntu 根文件系统（重要）

本 App 内打包了一个完整的 **Ubuntu Noble 24.04 (aarch64)** 根文件系统，位于
`assets/ubuntu-noble-aarch64-pd-v4.18.0.tar.xz`（约 61 MB），供应用内的 Linux 环境使用。

| 项 | 说明 |
|---|---|
| 内容 | 标准 Ubuntu FHS 布局，实测 12,684 个条目 |
| 内含许可声明 | 根文件系统内 `/usr/share/doc/*/copyright` 共 **182 个**，逐个 deb 包自带，随 rootfs 一同分发 |
| 涉及许可 | 大量 **GPL-2.0 / GPL-3.0 / LGPL-2.1 / LGPL-3.0** 组件（bash、coreutils、grep、sed、tar、gzip、python3 等），以及 MIT / BSD / Apache-2.0 等 |
| 许可副本 | ✅ 已随包（rootfs 内 182 个 copyright 文件） |
| **源代码获取** | **对应源代码可从 Ubuntu 官方归档免费下载：<https://archive.ubuntu.com/ubuntu/>**（按 deb 包名与版本检索，源码位于各发行版目录下的 `pool/` 与 `dists/` 结构）。Ubuntu 源码包的完整索引见 <https://launchpad.net/ubuntu/+source> |

本项目未修改 Ubuntu 根文件系统中的任何 GPL/LGPL 组件，仅为原样打包分发。

---

## 四、许可文本位置

| 文档 | 位置 |
|---|---|
| LGPL-3.0 全文（本项目及上游适用） | 仓库根目录 `LICENSE` |
| 第三方组件许可原文（随 App 分发） | `app/src/main/assets/licenses/` |
| 该目录的组件索引 | `app/src/main/assets/licenses/00-INDEX.md` |
| 许可核查明细（含版本来源、实证方法） | `D:\revokit\合规核查表.md`（构建仓库外的工作记录） |

---

## 五、免责声明

1. 本项目是**个人使用的安全研究与逆向工程辅助工具**，不是商业产品，不提供任何形式的担保（见 LGPL-3.0 第 15、16 条）。
2. 本项目提供的脱壳、重打包、签名等功能，**仅供分析你本人拥有合法授权访问的应用程序**，或用于安全研究、教学、兼容性研究等合法目的。
3. 使用本项目处理第三方软件，可能违反该软件的许可协议或当地法律法规。**使用者应自行确认其行为的合法性，并承担全部责任。**
4. 本项目不绕过任何付费授权、不提供盗版内容、不规避技术保护措施以获取商业利益。
5. 本项目内含的开源组件，其著作权归各自作者所有，本项目不对其作出任何保证或背书。

---

## 六、致谢

本项目建立在以下开源工作之上，在此致谢：

- **[Operit AI](https://github.com/AAswordman/Operit)** —— 本项目的上游，提供了完整的 AI Agent 框架、ToolPkg 机制与 Android 集成层
- **[Apktool](https://github.com/iBotPeaches/Apktool)** —— APK 反编译与回编译
- **[jadx](https://github.com/skylot/jadx)** —— dex 反编译
- **[smali / baksmali](https://github.com/baksmali/smali)** —— smali 汇编与反汇编
- **[Canonical / Ubuntu](https://ubuntu.com/)** —— 根文件系统
- 以及第二节所列的全部第三方库作者

若你认为本项目遗漏了你的署名或违反了你的许可条款，请通过仓库 Issue 提出，维护者会尽快处理。
