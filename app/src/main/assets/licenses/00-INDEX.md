# 第三方组件与许可索引（软件内可读版）

本目录是**随 App 一起分发**的许可声明，用来满足 BSD-3-Clause 与 Apache-2.0 对「以二进制形式分发时必须随附声明」的要求。

由 B 线维护。生成时间：2026-10-27。核查依据见仓库根目录 `NOTICE.md` 与 `D:\revokit\合规核查表.md`。

---

## 一、反编译运行时（`assets/packages/apktool.toolpkg` 内）

| 组件 | 版本 | 许可 | 原文文件 |
|---|---|---|---|
| Apktool | 3.0.1 | Apache-2.0 | `apktool-3.0.1-LICENSE-bundle.txt` |
| smali / baksmali | 3.0.9-dev | **BSD-3-Clause** | `apktool-3.0.1-LICENSE-bundle.txt`（首段） |
| jadx | 1.5.2 | Apache-2.0 | `jadx-1.5.2-LICENSE.txt` |
| jadx NOTICE | 1.5.2 | — | `jadx-1.5.2-NOTICE.txt` |
| jadx 依赖：Extreme! Lab (Indiana Univ.) | — | BSD 类 | `jadx-dep-extreme-indiana-university.txt` |
| jadx 依赖：Javolution | — | BSD 类 | `jadx-dep-javolution.txt` |
| jadx 依赖：ThoughtWorks XStream | — | BSD 类 | `jadx-dep-thoughtworks.txt` |
| Apache Commons CLI | 1.11.0 | Apache-2.0 | `apache-commons-cli-1.11.0-NOTICE.txt` |

## 二、随包分发的其余第三方构件

| 组件 | 版本 | 许可 | 原文文件 |
|---|---|---|---|
| apksig（com.android.tools.build） | 8.1.0 | Apache-2.0 | `apksig-8.1.0-LICENSE.txt` |
| axml（com.github.Sable） | 2.0.0 | Apache-2.0 | 无自带文件，适用 `aosp-apache-2.0.txt` |
| Bouncy Castle（bcprov-jdk18on） | 1.78 | MIT | `bouncycastle-1.78-MIT.txt` |
| org.json | 20240303 | Public Domain | `org.json-20240303-PublicDomain.txt` |
| AOSP framework-res（`android-framework.jar`） | — | Apache-2.0 | `aosp-apache-2.0.txt` |
| AOSP aapt2（预编译二进制，3 处） | — | Apache-2.0 | `aosp-apache-2.0.txt` |

## 三、编在 apktool / jadx 内部、未单列文件的依赖

以下构件的声明已包含在上表对应的许可原文里，不另立文件：

- **ANTLR** 3.5.2、**ST4** 4.0.8、**stringtemplate** 3.2.1 —— BSD-3-Clause
- **Google Guava** 33.5.0-jre（含 failureaccess 1.0.3 / listenablefuture / j2objc-annotations 3.1）、**error_prone_annotations** 2.41.0、**jcommander** 1.48 —— Apache-2.0
- **jsr305** 3.0.2 —— BSD-3-Clause
- **commons-io** 2.21.0、**commons-lang3** 3.20.0、**commons-text** 1.15.0 —— Apache-2.0

## 四、上游既有、不由本目录负责的声明

以下许可**已在 App 内其它位置**随包分发，本目录不重复：

| 位置 | 数量 | 归属 |
|---|---|---|
| `res/raw/license_*.txt` | 37 个 | ffmpeg-kit 及其原生依赖 |
| `assets/MathJax/LICENSE` | 1 | MathJax |
| `assets/js/katex.LICENSE.txt` | 1 | KaTeX |
| `assets/js/terser.LICENSE` | 1 | Terser |
| `assets/fonts/MATERIAL_SYMBOLS_LICENSE.txt` | 1 | Material Symbols |
| `assets/org/scilab/.../licences/*` | 2 | JLaTeXMath 字体 |
| `assets/subpack/android.apk` 内 `NOTICES.Z` | 1 | 内嵌 Flutter 应用的聚合许可 |

---

## 维护须知

1. **改依赖版本必须同步本目录。** 换 apktool / jadx / apksig / bouncycastle 版本时，对应文件里的版本号与年份要一并核对。
2. 本目录文件是**从各 jar 内原样提取**的，不手写、不改格式；唯一例外是 `bouncycastle-1.78-MIT.txt`（上游以 `.class` 形式存放，此处是等价还原）与 `org.json-20240303-PublicDomain.txt`（上游 LICENSE 仅一句 "Public Domain."）。
3. 本目录只放纯文本，不含任何可执行内容，运行时也不被读取（纯声明用途）。
