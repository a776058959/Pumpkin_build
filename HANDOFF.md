# 交接说明（给新的 AI 会话）

## 这个项目在干什么

把 **Pumpkin**（Rust 写的 Minecraft 服务端，上游 https://github.com/Pumpkin-MC/Pumpkin）编译到安卓，
再配一个安卓外壳 App「**南瓜坞**」：外壳本身**不含服务端**，运行时从 GitHub Releases 下载服务端二进制并启动。

## 源码在哪（重要）

**只维护一处**：`D:\Pumpkin_build\android-app\` —— 这是新仓库的工作副本，**直接改它，然后 git push**。

> 历史遗留：`D:\GitHub\Pumpkin_sgx\android-app\` 里还有一份旧副本，**不要再改**，否则两边不一致。
> `D:\GitHub\Pumpkin_sgx` 本身是旧的 fork 仓库，CI 已停用，只留作看上游 Rust 代码用。

## 仓库与目录

| 位置 | 说明 |
|---|---|
| `D:\Pumpkin_build` | **新仓库（真源）**，GitHub: `a776058959/Pumpkin_build`。只放外壳源码 + CI，不保存上游代码 |
| `D:\GitHub\Pumpkin_sgx` | 旧 fork 仓库（CI 已 disabled），本地保留完整上游代码供查阅 |
| `D:\androidsdk` | 本地工具与归档：NDK r27c、winlibs mingw、下载的 APK、manifest 解析脚本 |
| 桌面 `pumpkin-shell.apk` | 最新构建产物；历史版本在 `桌面\pumpkin-apk-旧版本\` |

## 关键约束（改代码前必读，全是踩过的坑）

1. **targetSdk 必须是 28**。Android 10+ 只有 targetSdk ≤ 28 才允许 App 从私有目录 execve 服务端二进制。
2. **不要用 API 30 的新窗口接口**（`setDecorFitsSystemWindows` / `WindowInsetsController` / `android.graphics.Insets`）：
   实测导致**启动即崩**。沉浸式统一用老的 `setSystemUiVisibility`（targetSdk 28 下完全有效）。
3. **不要在 `onDraw` 里录制 RenderNode**：在绘制流程中嵌套重录会**启动即崩**。
   录制要用 `postOnAnimation` 安排在帧边界（见 `BlurBackdrop.java`）。
4. **insets 回调里改 padding 必须先比较值是否变化**，否则「改 padding → 重新分发 insets」死循环。
5. **同一个按钮的文字只能有一处写**：下载按钮由 `Downloader.State`（IDLE/RUNNING/PAUSED）推导，
   不要在 `refresh()` 里重复设置——曾经因此按钮每秒来回跳。

## 本机环境

- **没有 Android SDK / 模拟器 / MSVC / JDK** → 只能在 CI 上构建，**本地无法验证 UI**，所有效果要用户截图确认。
- git 用 GitHub Desktop 自带的：
  `C:\Users\a7760\AppData\Local\GitHubDesktop\app-3.6.5\resources\app\git\cmd\git.exe`
- GitHub token 在 `%TEMP%\gh_tok.txt`（从 Windows 凭据管理器读出的 GitHub Desktop OAuth token；scopes: repo, user, workflow）
- 代理 `http://127.0.0.1:7897`（clash verge），访问 GitHub 要设 `HTTPS_PROXY` / `HTTP_PROXY`
- 新仓库**零 secret**（用内置 GITHUB_TOKEN），但**仓库设置里 Actions 默认权限必须是 write**

## CI 流水线

| 工作流 | 用途 |
|---|---|
| `.github/workflows/apk-only.yml` | **只重打包外壳**，几十秒出包（改 UI 用这个） |
| `.github/workflows/build.yml` | 定时（每 2 小时比较上游 SHA，无新提交不构建）+ 全平台构建 + 发布 Release + 记录基线 |
| `.github/workflows/prune-dryrun.yml` | 手动检查 Release 保留策略会删什么（只打印不删） |

Release 保留策略脚本：`.github/scripts/prune-releases.sh`
（<1月全留 / 1-2月每周留一个 / 2-12月每月留一个 / >1年删除）

## 出包流程（每次改完 UI 都走这套）

1. 改 `D:\Pumpkin_build\android-app\` 里的源码
2. 提交并 push 到 `https://github.com/a776058959/Pumpkin_build.git` 的 `main`
   （push 前先 `git pull --rebase`，因为 `record` job 会自动往 main 提交 `.github/upstream-lock.txt`）
3. 触发构建：`POST /repos/a776058959/Pumpkin_build/actions/workflows/apk-only.yml/dispatches`，body `{"ref":"main"}`
4. 等约 1-3 分钟 → 从该 run 的 artifacts 下载 `pumpkin-shell-<日期>.apk`
5. **发布**：删掉 Release（id `388172081`）里旧的 `pumpkin-shell.apk`，再把新的以**同名**上传
   （`POST https://uploads.github.com/repos/a776058959/Pumpkin_build/releases/388172081/assets?name=pumpkin-shell.apk`）
6. 桌面留一份最新的，旧的移到 `桌面\pumpkin-apk-旧版本\`

**固定下载链接（永远指向最新）**：

```
https://github.com/a776058959/Pumpkin_build/releases/latest/download/pumpkin-shell.apk
https://ghfast.top/https://github.com/a776058959/Pumpkin_build/releases/latest/download/pumpkin-shell.apk
https://gh-proxy.com/https://github.com/a776058959/Pumpkin_build/releases/latest/download/pumpkin-shell.apk
```

## 外壳 App 当前功能

- 三个页面 + 底部悬浮玻璃导航（滑动指示器，Material 3 式小胶囊）
- **运行页**：状态与运行时长、联机地址（Java/基岩）、启动/停止、电池优化、**选择运行版本**（切换/回滚）、控制台（实时日志 + 命令输入）
- **更新页**：三块独立卡片 —— 版本信息（检查更新/选择版本）、下载（三态按钮 + 进度条 + 删除下载任务）、本地版本（删除已安装版本）
- **设置页**：启动方式切换、下载源（API 地址/仓库/镜像前缀）、清理（三种粒度）、检查本应用更新
- **双启动模式**：普通模式靠 targetSdk 28 豁免；Root 模式走 `su` 域（**不改 SELinux**，不会被检测软件发现）
- **下载**：多源自动回退（直连 → 上次成功的 → 用户镜像 → ghfast.top → gh-proxy.com → ghproxy.net），
  支持断点续传；安装前校验 ELF（aarch64）；失败自动重试

## 未完成 / 待决定

1. **「液态玻璃」效果**：用户想要 SukiSU Ultra 那种（边缘折射 + 随手机倾斜流动的高光）。
   现状只有「真背景模糊 + 半透明」（毛玻璃），没有折射和动态高光。
   要 1:1 复刻必须换 **Jetpack Compose + miuix-kmp**（`top.yukonga.miuix.kmp.blur` 的 `Backdrop`/`Highlight`），
   代价：APK 100KB → 3~5MB、UI 重写约 600 行、构建引入 Kotlin/Compose 编译器。
   **用户尚未拍板要不要换栈。**
2. **真机验证核心链路**：「更新页下载服务端 → 运行页启动」这条链还没验证过
   （验证普通模式的 execve 是否被系统放行）。如果报 `Permission denied`，切 Root 模式。

## 与用户沟通的注意点

- 中文交流，喜欢直接、不要冗长铺垫。
- 他多次因 UI 细节不满意（按钮跳动、圆角缺口、指示器尺寸）。
  **改 UI 前先想清楚状态归属和控件职责，别打补丁叠补丁。**
- 本机无法验证 UI，**不要声称「应该没问题」**，让他截图确认。
- 已确认可用的历史版本：`桌面\pumpkin-apk-旧版本\` 里的 `pumpkin-shell-20260914-0722.apk`（他确认过 "这次好了"）。
