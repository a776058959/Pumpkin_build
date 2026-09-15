# 开发文档

> **这里是改这个仓库的人看的东西，不是给使用者看的。**
> 使用者请看仓库根目录的 [README.md](../../README.md) 和 [ANDROID.md](../../ANDROID.md)。
>
> 放在 `docs/dev/` 而不是仓库根目录，就是为了不让使用者一进来就被这些内容糊一脸。

## 有哪些

| 文档 | 什么时候看 |
|---|---|
| [handoff.md](handoff.md) | **接手前先读这个**。当前进度、已发布版本、真机调试速查、可用模型与工具、关键约束与踩过的坑 |
| [local-build.md](local-build.md) | 要构建/上机验证时。本机工具链怎么装、一条命令出包、PowerShell 的几个坑 |
| [plugins.md](plugins.md) | 要动插件系统、或者想加一个插件时 |
| [app-self-update.md](app-self-update.md) | 要动更新逻辑时。App 自更新曾经完全失效（三个叠加缺陷）的完整复盘 |
| [background-image.md](background-image.md) | 想做「自定义背景图」时。可行性与成本分析，结论是先不做，附分步拆解 |

## 构建与验证：优先在本地做

本机工具链已经装好了（见 [local-build.md](local-build.md)），所以：

- **日常迭代**用 `tools/build-local.ps1` + `adb install`，不要推 CI。
  推 CI 每失败一次都要「dispatch → 轮询 → 拉日志 → grep」一整圈。
- **只有发布**（要变成 Release 供 App 自更新）才推 CI，走 `apk-only.yml` 且勾 `publish`。
- 本地产物与 CI 产物可复现（同一个 dex 的 SHA256 相同），不用担心「本地好、线上坏」。

## 三条最贵的教训（详见 handoff.md）

1. **遇到「卡/慢」，先用 `gfxinfo` 和 `simpleperf` 量，再决定改什么。**
   曾经凭直觉改了四轮 UI 代码，最后发现根因是**发的是 debug 包**（ART 不做 AOT）
   和**没开 R8** —— 跟 UI 代码无关。
2. **Compose 的绘制遍历和触摸遍历是两套独立的东西。**
   用 `drawWithContent` 让不可见的页「不画」，节点仍然全屏大小、照样吃点击，
   会表现为「很多按钮功能错乱」。正确做法是把不可见页判成 0×0。
3. **改了 UI 层级或可见性，必须跑 `tools/test-hidden-page-hit.sh` 验证**，
   别只看代码觉得「应该没问题」。

## 工具脚本（`tools/`）

### 主机侧（PowerShell）

| 脚本 | 用途 |
|---|---|
| `build-local.ps1` | 一键出 APK + 插件 dex + 商店索引（`-SkipPlugins` 只出 APK） |
| `setup-local-toolchain.ps1` | 一次性：装 JDK 21 / JDK 17 / Gradle 9.7.1 / cmdline-tools |
| `setup-android-sdk.ps1` | 一次性：装 platform-tools / platform / build-tools |
| `fix-ps1-bom.ps1` | 给 `tools/*.ps1` 补 UTF-8 BOM（改完 `.ps1` 就跑一下） |

### 设备侧（`/system/bin/sh`，先 push 再 `sh`）

| 脚本 | 用途 |
|---|---|
| `verify-plugin-crud.sh` | 插件页「增删改查」全流程，29 项断言一次跑完 |
| `verify-stop-confirm.sh` | 插件改**行为**的验证（确认框弹不弹）——需要服务端在跑 |
| `plugin-reset.sh` | 把插件状态清干净（跑验证脚本前先跑） |
| `install-server-for-test.sh` | 把推上去的服务端二进制装成 App 的一个版本（测试用旁路） |
| `dump-ui.sh` | 界面 →「文字 @ 中心坐标」，用来算点击位置 |
| `goto-page.sh` | 切页并确认真的切过去了 |
| `console-height.sh` | 量运行页控制台文字高度（验证字号类改动） |
| `measure-nav-fps.sh` | 量化「快速来回点底栏」的掉帧情况 |
| `profile-nav-tap.sh` | 边点击边做 CPU 采样，直接点名热点函数（**必须 `su`**） |
| `capture-nav-anim.sh` | 把系统动画放慢 10 倍后逐帧连拍，用于分析动画轨迹 |
| `test-hidden-page-hit.sh` | 验证「不可见页面会不会吃掉点击」 |
| `verify-plugin-effect.sh` | 覆盖值是否真的改变了控制台字号 |
| `clear-plugin-override.sh` | 清掉插件覆盖键，确认回到内置行为 |
| `native-linux/` | 在手机本机内核上跑原生 Alpine Linux（chroot） |
