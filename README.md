<h1 align="center">
  <img src="app/src/main/res/drawable-xxxhdpi/ic_launcher_simple_foreground.png" width="72" height="72" align="absmiddle" alt=""> GG工坊
</h1>

<p align="center">
  跑在你自己手机上的安卓逆向 AI 助手
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Version-1.12.2-blue.svg" alt="Version">
  <img src="https://img.shields.io/badge/License-LGPL--3.0-orange.svg" alt="License">
</p>

---

> **本项目是 [Operit AI](https://github.com/AAswordman/Operit) 的衍生版本**，不是独立原创项目。
> 依据 LGPL-3.0 第 5 条，来源与修改声明完整写在 [`NOTICE.md`](NOTICE.md)。

## 这是什么

GG工坊 是一个运行在 Android 设备本地的 AI 助手，重心是**安卓逆向**：
识别加固、脱壳、修 DEX、重打包、签名、拆包。

模型调用使用你自己配置的 API Key 与端点，请求由设备直连服务商。
逆向相关的分析、脚本与产物都在本地这台手机上完成。

## 核心能力

### 加固识别

- **49 家加固厂商特征库 + 证据打分**，毫秒级出结论
- 对应工具：`apk_packer_identify`（Kotlin 原生实现，不经过 JS/Python）
- 按 so 名、特征字符串、DEX 结构多路取证据，避免单一特征误判

### 脱壳

| 能力 | 说明 |
|---|---|
| 内存 dump | `revokit_memdump` — 扫 `/proc/<pid>/mem` 捞内存里的 DEX 块，需要 root |
| DEX 修复 | `revokit_dexrepair` — 重算 `file_size → signature → checksum`（顺序不可换），并报出方法体是否被抽走 |
| 壳 DEX 识别 | 区分壳 dex 与业务 dex，避免把壳当成目标 |
| 360 加固专项 | ToolPkg 脚本形式：dex 层删除被抽取的方法定义（v9 补丁）、启动页等长接回（code patch），不依赖 smali |

### 重打包与签名

- apktool 解包 / 回编译 / 签名一条链
- 产物直接落盘，可在设备内继续处理

### 逆向工作台

界面侧提供五个页面：加固识别、APK 拆包、脱壳工作台、一键清理、逆向工作台。
主入口默认停在 AI 对话页，逆向工具按需进入。

### AI 与自动化

- 对话式驱动：让 AI 直接调上面的工具干活，而不是只给建议
- ToolPkg 脚本体系：能力可以按需扩展，不必重新出包
- 终端、自动化任务、主题包与音乐播放等附加能力

## 环境要求

| 项 | 要求 |
|---|---|
| 系统 | Android 8.0（API 26）及以上 |
| 脱壳功能 | 需要 **root**（Magisk）；部分 ROM 还需 LSPosed |
| 加固识别 / 拆包 / 重打包 | 不需要 root |
| 存储 | 建议预留 1 GB 以上（解包产物较大） |

## 安装

从 [Releases](../../releases) 下载 APK 安装即可。

包名 `com.ggongfang.app`，与上游 Operit（`com.ai.assistance.operit`）**互不覆盖，可以共存**。

> 早期测试版本曾使用 `com.revokit.app`，与当前包名不同，二者会被识别为两个独立应用。

## 从源码构建

```bash
git clone --recursive https://github.com/ikunboy982688/RevoKit.git
cd RevoKit
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/`

**工具链版本（写死，不要随意替换）**

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **21** | 必须。依赖 `backdrop-1.0.6` 是 class 65 产物，用 JDK 17 会报「类文件具有错误的版本 65.0，应为 61.0」 |
| compileSdk | 36 | |
| minSdk | 26 | |
| NDK | 25.1.8937393 | 原生部分需要 |
| CMake | 3.22.1 | |
| Node / Rust | 22 / 1.88.0 | 部分前端与原生依赖需要 |

**构建变体与包名**

| 变体 | 包名 | 用途 |
|---|---|---|
| `debug` | `com.ggongfang.app.debug` | 日常开发，与正式包共存 |
| `release` | `com.ggongfang.app` | 正式发布，需配置签名 |
| `clone` | `com.ggongfang.app.clone` | 多开调试 |

> 子模块必须一起拉取（`--recursive`），否则原生部分编译不过。

## 已知限制

如实说明，避免误解：

- **加固覆盖有限**。360 新版（0701 付费引擎）做了函数抽取，内存里 dump 出来的方法体是空的，需要额外回填；这块仍在推进。
- **内存 dump 依赖 root 与 SELinux 策略**，部分厂商 ROM 需要 LSPosed 或额外授权，不是所有机型都一次成功。
- **在线音源与歌词依赖第三方脚本和平台接口**，平台一改就可能失效，属于会随时过期的能力。
- **上游部分功能尚未清理**（如虚拟形象、语音唤醒相关资源仍在包内），后续会逐步裁剪。
- 代码里保留了上游的 `namespace = com.ai.assistance.operit`，这是为了减少改动面，不影响安装与共存。

## 与上游的关系

| 项 | 内容 |
|---|---|
| 上游项目 | [Operit AI](https://github.com/AAswordman/Operit) v1.12.2 |
| 上游许可 | GNU Lesser General Public License v3.0 |
| 本项目许可 | 同为 **LGPL-3.0**，未变更 |
| 修改声明 | 见 [`NOTICE.md`](NOTICE.md)（逐项列出改动） |
| 第三方组件许可 | 见 `app/src/main/assets/licenses/` |

本项目保留上游版权声明与许可文本。若你要分发本项目的修改版，
同样需要遵守 LGPL-3.0：提供对应源码，并保留来源声明。

## 许可

[LGPL-3.0](LICENSE)。

## 免责声明

本项目的逆向能力面向**学习研究**，以及**你拥有合法授权的应用**。

不得用于破解他人付费软件、绕过授权验证、窃取数据或侵犯他人知识产权。
使用者应自行确认行为的合法性，并自行承担相应责任。

## 致谢

- [Operit AI](https://github.com/AAswordman/Operit) — 本项目的上游
- [apktool](https://github.com/iBotPeaches/Apktool)、[jadx](https://github.com/skylot/jadx)、[smali/baksmali](https://github.com/google/smali) — 逆向工具链基石
- 以及所有上游贡献者与开源社区

---

## English

**GG工坊 (GOOG)** — an on-device Android reverse-engineering AI assistant.

It identifies packers (49 vendor signatures), dumps DEX from process memory (root required),
repairs DEX headers, repacks and signs APKs — all executed locally on your phone, using
the model endpoint and API key you configure yourself.

This project is a **derivative work of [Operit AI](https://github.com/AAswordman/Operit)**,
licensed under **LGPL-3.0**. See [`NOTICE.md`](NOTICE.md) for the full modification statement.

Package: `com.ggongfang.app` · Android 8.0+ · Build requires JDK 21, NDK 25.1.8937393, CMake 3.22.1.
