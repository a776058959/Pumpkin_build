# Pumpkin_build

[Pumpkin](https://github.com/Pumpkin-MC/Pumpkin)（Rust 写的 Minecraft 服务端）的**纯构建仓库**，
外加配套的安卓 App「**南瓜坞**」。

## 关于本项目：代码由 AI 编写

除上游服务端本身，**这个仓库里的东西基本都是 AI 写的**：

| 部分 | 谁写的 |
|---|---|
| `android-app/` —— 南瓜坞 App 的全部 Java / Kotlin / Compose 代码 | AI |
| `.github/workflows/` —— 构建与发布流水线 | AI |
| `tools/` —— 原生 Linux 部署脚本、真机回归脚本 | AI |
| 本仓库全部文档（`README` / `ANDROID` / `HANDOFF` / `docs/`） | AI |
| 上游服务端源码（Rust） | 人类 —— [Pumpkin-MC/Pumpkin](https://github.com/Pumpkin-MC/Pumpkin)，本仓库只拉取编译，不保存 |

做法是人在真机上定方向、验收、报问题，AI 负责实现、**在真机上验证**、并把结论写进文档。
所以文档里带数字的结论都配了产生它的方法（截图、像素比对、`dumpsys`、日志），
而不是「应该没问题」。

## 它做什么

- **不保存上游代码**：本仓库只有构建流水线、安卓 App 源码、工具脚本和文档。
- 构建时才按记录的上游 commit 把上游源码拉到 runner 上编译（`git fetch --depth=1 <sha>`）。
- `.github/upstream-lock.txt` 记录「上次成功构建所基于的上游提交 SHA」；定时任务只做一次
  `git ls-remote` 比较，上游没有新提交时**不会触发任何编译**。

## 目录

| 路径 | 说明 |
|---|---|
| `.github/workflows/build.yml` | 主流水线：定时检查上游（每 2 小时）→ 有新提交才编译各平台 → 打包 APK → 发布 Release → 记录基线 |
| `.github/workflows/manual-build.yml` | 手动触发一次完整多平台构建（跳过上游检查） |
| `.github/workflows/apk-only.yml` | 只重打包 App，不动服务端二进制，约 1 分钟出包（**改 UI 用这个**）。默认**只出 artifact 不发 Release**，要发必须勾 `publish` |
| `android-app/` | 南瓜坞 App 源码（Java 业务层 + Compose/miuix 界面）。**不含服务端**，服务端由 App 运行时从 Releases 下载 |
| `tools/native-linux/` | 在手机本机内核上跑**原生 Alpine Linux**（chroot，不是 Termux 那种用户态终端） |
| `docs/app-self-update.md` | App 自更新机制曾经完全失效的完整复盘（三个叠加缺陷） |
| `docs/background-image.md` | 「自定义背景图」功能的可行性与成本分析（结论：先不做，附拆解） |
| `ANDROID.md` | 手机上安装与运行说明（南瓜坞 App 方式 / Termux 备选） |
| `HANDOFF.md` | 交接说明：当前进度、真机调试速查、改代码前必读的约束 |
| `.github/upstream-lock.txt` | 上次构建基于的上游提交（由流水线自动维护，不要手工改） |

## 南瓜坞 App

一个把 Pumpkin 服务端「下载 → 启动 → 看控制台」都包下来的安卓壳：

- **不含服务端**：运行时从本仓库 Releases 下载 aarch64 二进制到应用私有目录再 `execve`。
- `targetSdk=28`。这不是偷懒 —— Android 10+ 会把 targetSdk ≤ 28 的应用放进 `untrusted_app_27`
  域，该域允许执行私有目录里的文件。改成 29+ 普通模式立刻失效（Root 模式仍可用）。
  详见 [ANDROID.md](ANDROID.md) 的原理说明。
- 界面：Compose + [miuix](https://github.com/miuix-kotlin-multiplatform/miuix)（SukiSU / 新版 LSPosed 同款风格）
  + 液态玻璃悬浮底栏，**6 套配色**可在设置里切换。
- 版本管理：多版本共存、切换运行版本、单独删除、回滚。
- 自更新：检查 GitHub Releases 里带 `.apk` 的最新发布，提示下载覆盖安装。

## 产物

见 [Releases](../../releases)：

- `pumpkin-<平台>-<日期>` —— 各平台服务端二进制（Android aarch64 / Linux / Windows，含 legacy 变体）。
- `pumpkin-shell.apk` —— 南瓜坞 App。这个文件名是**永久下载链接**的一部分，不要改：

  ```
  https://github.com/a776058959/Pumpkin_build/releases/latest/download/pumpkin-shell.apk
  ```

  国内加速：在它前面拼一个前缀，例如
  `https://ghfast.top/https://github.com/.../pumpkin-shell.apk`。

App 用仓库内固定的签名密钥（`android-app/app/pumpkin-signing.p12`）签名，所以新版本可以**直接覆盖安装**，
不用卸载、也不会丢世界数据。注意该密钥是自签名测试用途、密码公开，**不要用于正式分发**。

## 手动触发

Actions → 选择 workflow → Run workflow。

- **`Build Pumpkin (upstream + App APK)`** —— 完整构建：检查上游 → 编译 8 个平台的服务端 → 打包 App → 发布 Release。
  支持 `force` 选项，可在上游无新提交时强制构建。**耗时约 1 小时以上。**
- **`Build Pumpkin App (APK only)`** —— 只重打包 App，一分钟出包，不碰服务端编译。

### ⚠️ 只改 App 时，记得勾 `publish`

`apk-only` **默认只产出 Actions artifact，不创建 Release** —— 因为大多数时候只是拿包来装机器测试。

但这意味着：**不勾 `publish`，改动就永远到不了用户手里。**
App 的「检查更新」读的是 Releases，artifact 它看不见。曾经因此踩坑：
连续几轮 UI 改动都只躺在 artifact 里，而本机 adb 装的是新的，
于是「我这边是好的」—— 用户那边等于没更新（详见提交历史 `docs:` 与 `ci:` 若干条）。

所以要发到用户手里，二选一：

1. 跑 `apk-only` 并勾上 **publish**（快，推荐；只改 App 时服务端没必要重建）；
2. 或者跑完整流水线（顺便刷新服务端二进制）。

发布出来的 Release 可能**只有 APK**，这是正常的：服务端没变，没必要重建 8 个平台。

## 开发时要留意

- **改 `android-app/` 的 AGP 版本时，必须同步改 `build.yml` 里 `build-android-apk` 的
  `gradle-version` 与 `java-version`。** 这两处曾经脱节，加上那个 job 带 `continue-on-error: true`，
  于是它每一次构建都失败、workflow 却一直显示成功，Release 长期**没有附带 APK**。
  复盘见 [docs/app-self-update.md](docs/app-self-update.md)。
- `versionCode` 由构建时间戳推导（`epochDay * 10000 + HHMM`），同一时间戳必须贯通
  「APK 内嵌值」与「Release tag」，否则会出现「每次都提示有新版本、装完还提示」。
- App 自更新的版本比较公式在 `build.gradle.kts` 和 `UpdateClient.parseVersionCodeFromTag()`
  **两处各写了一遍**，改一处必须同步另一处。
