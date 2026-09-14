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
| `D:\androidsdk\shots` | 验证截图归档（含液态玻璃前后对比、像素分析用图） |
| 桌面 `pumpkin-shell.apk` | 最新构建产物；历史版本在 `桌面\pumpkin-apk-旧版本\` |
| `D:\adb-fastboot\adb.exe` | adb（不在 PATH） |

---

## 当前进度快照（2026-09-14 晚）

- 外壳版本 **0.3.0（versionCode 3）**：已构建、已发布、**已装到测试机并运行正常（无崩溃）**
- 仓库状态：本地 `main` == GitHub `main` == 提交 **`59ac8f3`**
  （`feat(shell): liquid-glass panels in the current Java stack`）
- Release 资产 `pumpkin-shell.apk` = **106890 字节**（0.3.0，含液态玻璃）；
  固定链接下载字节与构建产物 SHA256 一致（已校验）
- 归档：
  - 桌面 `pumpkin-shell.apk` = 0.3.0（106890）
  - `桌面\pumpkin-apk-旧版本\pumpkin-shell-20260914-1003.apk` = **0.2.0 毛玻璃版**（103170，做像素对比用）
  - `桌面\pumpkin-apk-旧版本\pumpkin-shell-20260914-0722.apk` = 用户确认过「这次好了」的老版本
- 验证截图（`D:\androidsdk\shots\`）：
  - `p_liquid_old_020.png`（0.2.0 运行页）、`p_liquid_new_030.png`（0.3.0 运行页）
  - `liquid_compare.png`（并排+局部对比拼图，桌面同名文件）
  - `p_liquid_crop_nav.png`（导航栏局部，识图测试用）

---

## 液态玻璃实现（0.2.0 → 0.3.0）

在**不换 UI 栈**（仍是纯 Java + 系统控件）的前提下逼近 SukiSU Ultra 观感：

- **`GlassPanelDrawable.java`**（新增）：四层 Canvas 直绘
  1. 半透明垂直渐变填充（玻璃底）
  2. 倾斜光斑（径向渐变，`dynamic=true` 时由 TiltGlow 驱动平移）
  3. 顶部光泽 sheen + 底部反光 catch light
  4. 上亮下暗的渐变描边（rim，模拟边缘折射）
  - 覆写了 `getOutline()`，保证悬浮栏 elevation 阴影仍然正常
- **`TiltGlow.java`**（新增）：加速度计 + 指数低通滤波 → `setTilt(x, y)`；
  在 `onResume` 注册、`onPause` 注销（后台不耗电）；无传感器时返回 null，自动退化为静态玻璃
- **接入点**（`UiKit.java`）：
  - `glassPanel(ctx, strong, dynamic)` —— 导航栏用 `dynamic=true`；卡片用 false
  - `glass(ctx, strong)` —— 卡片 / 输入框 / 控制台背景（返回类型已从 GradientDrawable 改为 Drawable）
  - `navPill(ctx, active)` —— 选中胶囊（青色渐变描边 + 顶部光泽）

**想调强度就改 `UiKit.glassPanel()` / `navPill()` 里的颜色参数**：

| 参数 | 含义 | 当前值（strong/导航栏） |
|---|---|---|
| `rimTop` / `rimBottom` | 描边亮线上下端 | `0x99FFFFFF` / `0x30FFFFFF` |
| `sheenColor` | 顶部光泽带 | `0x33EAF6FF`（微青） |
| `catchColor` | 底部反光 | `0x26FFFFFF` |
| `glossColor` | 倾斜光斑 | `0x2EFFFFFF` |
| 光斑位移系数 | `GlassPanelDrawable.draw()` 里 `0.38f` / `0.30f` | 越大流动越明显 |

**约束照旧没破**：没碰 RenderNode 录制、没碰 insets、没碰按钮状态机（三个已知崩溃坑）。
新代码只用了 API 1–21 的老接口（`LinearGradient` / `RadialGradient` / `Path.addRoundRect` / `Outline`），
minSdk 24 安全；CI 编译一次通过。

## 已验证的渲染证据（0.2.0 vs 0.3.0 同位置截图像素差分）

| 位置 | 效果 | 实测差值 |
|---|---|---|
| 悬浮栏顶边线（y≈2145） | rim 折射亮线 | **+116/255**（94.7 → 210.9） |
| 悬浮栏顶部光泽带 | sheen | +40 ~ +51 |
| 卡片区域 | 玻璃层提亮 | +10 ~ +18 |
| 选中胶囊 | 玻璃感 | +9 ~ +13 |

---

## 未完成（新会话优先做这些）

1. **倾斜高光动态验证**（需要用户配合，唯一还没验的动态效果）
   - 手机**平放**时截一张 → 请用户把手机**向右倾斜约 45° 拿稳** → 再截一张
   - 对比悬浮栏水平方向亮度重心是否右移（位移系数 0.38 × 导航栏宽度）
   - 命令：
     ```powershell
     D:\adb-fastboot\adb.exe shell input keyevent KEYCODE_WAKEUP
     D:\adb-fastboot\adb.exe shell screencap -p /sdcard/t.png
     D:\adb-fastboot\adb.exe pull /sdcard/t.png "$env:TEMP\t.png"
     ```
   - 注意：截屏是**当前帧**，光斑位置会被记录下来，可以对比
2. **用户对 0.3.0 的观感反馈** → 按反馈调上面的参数（他重视 UI 细节，别自作主张大改）
3. **真机验证核心链路**：「更新页下载服务端 → 运行页启动」还没验证过。
   测试机是 **Android 15（API 35）**，正好检验 targetSdk≤28 的 execve 豁免在新系统上是否仍生效；
   若日志报 `Permission denied`，切设置页的 **Root 模式**。
4. （待用户拍板）液态玻璃再进一步：真折射需要换 **Jetpack Compose + miuix-kmp**
   （APK 100KB → 3~5MB、UI 重写约 600 行），用户尚未决定。

---

## 真机调试速查（实测可用）

- adb：`D:\adb-fastboot\adb.exe`（不在 PATH，用全路径）
- 测试机：**Redmi M2004J7AC，Android 15（API 35）**，1080x2400，440dpi（1dp = 2.75px）
- 装包 / 截屏 / 看崩溃：
  ```powershell
  $adb = "D:\adb-fastboot\adb.exe"
  & $adb install -r "$env:USERPROFILE\Desktop\pumpkin-shell.apk"    # 签名一致可直接覆盖
  & $adb install -r -d <旧版apk>                                     # 降级装（对比测试用，要 -d）
  & $adb shell screencap -p /sdcard/x.png ; & $adb pull /sdcard/x.png $env:TEMP\x.png
  & $adb shell dumpsys package com.pumpkin.server | Select-String versionName
  & $adb shell pidof com.pumpkin.server        # 进程活着 = 没崩
  ```
- ⚠️ 该机 **MIUI 的 logd 默认关闭**：`adb logcat` 报 `logd not running?`，拿不到日志。
  排查崩溃改用 App 自带的 `last_crash.txt`（`/sdcard/Android/data/com.pumpkin.server/files/`）；
  必要时请用户在开发者选项里打开日志开关。
- ⚠️ 截屏前先 `input keyevent KEYCODE_WAKEUP`：**屏幕睡着时截出来是全黑**（踩过）

---

## 模型与识图（重要，新会话必读）

三家路由都在用：火山 **Agent Plan**（`ark-agent-plan-cn`）、智谱 BigModel（`zhipu`）、硅基流动（`siliconflow`）。
Agent Plan 用 `anthropic-messages` 协议、端点 `https://ark.cn-beijing.volces.com/api/plan`（**不是 v3**）。

### 实测可用性地图（2026-09-14 逐个真实请求验证）

| 模型 | 结果 | 能看图 |
|---|---|---|
| `ark-agent-plan-cn/glm-5.3` | ✅ 200 | ❌ 纯文本（`input: [text]`） |
| `ark-agent-plan-cn/kimi-k3` | ✅ 200 | ✅ **已用真实截图验证** |
| `ark-agent-plan-cn/minimax-m3` | ✅ 200 | ✅ 声明支持 |
| `ark-agent-plan-cn/doubao-seed-evolving` | ✅ 200 | ✅ 声明支持 |
| `ark-agent-plan-cn/doubao-seed-2.1-turbo` | ✅ 200 | ✅ 声明支持 |
| `ark-agent-plan-cn/doubao-seed-2.0-lite` / `-mini` | ✅ 200 | ✅ 声明支持 |
| `ark-agent-plan-cn/deepseek-v4-pro` / `deepseek-v4-flash` | ✅ 200 | ❌ 纯文本 |
| `ark-agent-plan-cn/doubao-seed-2.1-pro` | ❌ **404 `UnsupportedModel`**（Agent Plan 不含此模型） | — |
| `zhipu/glm-4.7-flash` / `glm-4.5-flash` | ✅ | ❌ 纯文本 |
| `zhipu/glm-4v-flash` | ✅ | ✅ **已用真实截图验证**（免费） |

### 用 GLM-5.3 当主模型时怎么识图

**GLM-5.3 是纯文本，主循环的 `read_image` 会直接报 "does not declare image input"**。两条路：

1. **派视觉子代理**（推荐，主模型保持 GLM-5.3）
   ```
   subagent(provider='zhipu', model='glm-4v-flash', ...)            # 免费
   subagent(provider='ark-agent-plan-cn', model='kimi-k3', ...)     # 计划内，1M 上下文
   ```
   子代理提示里直接给图片的绝对路径，让它用 `read_image` 读。
2. **把主会话模型换成** `kimi-k3` / `minimax-m3` → 主循环自己就能看图（不用子代理）。

⚠️ **子代理路由白名单是「会话创建时快照」的**：
`~/.dsh/settings.yaml` 的 `subagent-model-selection.allowedModels` 改了之后，
**只有新会话生效**；老会话里用白名单外的模型会报
`child LLM route "..." is not allowed for this Session`（workflow 的 `agent()` 通道同样受限）。
所以识图必须**新会话**里做，或直接把主模型换成视觉模型。

### 凭据

`~/.dsh/.credentials.yaml`（明文 `refs:` 段）：`DEEPSEEK_API_KEY` / `ZHIPU_API_KEY` /
`SILICONFLOW_API_KEY` / `ARK_AGENT_PLAN_CN_API_KEY`。
**不要把 key 值打印到聊天或提交进仓库。**

---

## 常用操作备忘（PowerShell 踩坑）

- `curl` 传 JSON body **一定要用文件**：`-d "@C:\path\body.json"`。
  行内 `'{"ref":"main"}'` 会被 PowerShell 的原生参数传递吃掉引号 → API 报 `Problems parsing JSON`
- GitHub API 用 `Invoke-RestMethod` 更稳（关 workflow dispatch、删/传 release 资产）
- 上传 Release 资产 Content-Type 用 `application/vnd.android.package-archive`
- git push：仓库**没配 remote**，用一次性 URL
  ```powershell
  git -c safe.directory=D:/Pumpkin_build -C D:\Pumpkin_build push "https://x-access-token:<token>@github.com/a776058959/Pumpkin_build.git" main
  ```
- Git 会报 `dubious ownership` → 所有 git 命令加 `-c safe.directory=D:/Pumpkin_build`

---

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
7. **真机直装**（快）：`D:\adb-fastboot\adb.exe install -r 桌面\pumpkin-shell.apk`

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

- **没有 Android SDK / 模拟器 / MSVC / JDK** → 编译只能在 CI 上做（apk-only 约 1 分钟）
- **真机调试已可用**：adb + 测试机（见上「真机调试速查」），UI 效果要靠真机截图验证
- git 用 GitHub Desktop 自带的：
  `C:\Users\a7760\AppData\Local\GitHubDesktop\app-3.6.5\resources\app\git\cmd\git.exe`
- GitHub token 在 `%TEMP%\gh_tok.txt`（GitHub Desktop OAuth token；scopes: repo, user, workflow）
- 代理 `http://127.0.0.1:7897`（clash verge）：访问 GitHub 要设 `HTTPS_PROXY`/`HTTP_PROXY`；
  **访问国内 API（火山/智谱/硅基流动）要删掉代理变量**，否则可能不通
- 新仓库**零 secret**（用内置 GITHUB_TOKEN），但**仓库设置里 Actions 默认权限必须是 write**
- ⚠️ **文件沙箱**：会话工作目录通常是 `D:\GitHub\Pumpkin_sgx`，而源码在 `D:\Pumpkin_build`。
  如果新会话的文件策略是 `workspace-write`（只允许改工作目录），写 `D:\Pumpkin_build`
  会被沙箱拒绝——按提示申请放宽权限（`danger-full-access`）即可，不是命令写错了。

## 外壳 App 当前功能

- 三个页面 + 底部悬浮**液态玻璃**导航（实时背景模糊 + 边缘亮线 + 顶部光泽 + 倾斜流动高光）
- **运行页**：状态与运行时长、联机地址（Java/基岩）、启动/停止、电池优化、**选择运行版本**（切换/回滚）、控制台（实时日志 + 命令输入）
- **更新页**：三块独立卡片 —— 版本信息（检查更新/选择版本）、下载（三态按钮 + 进度条 + 删除下载任务）、本地版本（删除已安装版本）
- **设置页**：启动方式切换、下载源（API 地址/仓库/镜像前缀）、清理（三种粒度）、检查本应用更新
- **双启动模式**：普通模式靠 targetSdk 28 豁免；Root 模式走 `su` 域（**不改 SELinux**，不会被检测软件发现）
- **下载**：多源自动回退（直连 → 上次成功的 → 用户镜像 → ghfast.top → gh-proxy.com → ghproxy.net），
  支持断点续传；安装前校验 ELF（aarch64）；失败自动重试

## 与用户沟通的注意点

- 中文交流，喜欢直接、不要冗长铺垫。
- 他多次因 UI 细节不满意（按钮跳动、圆角缺口、指示器尺寸）。
  **改 UI 前先想清楚状态归属和控件职责，别打补丁叠补丁。**
- **不要声称「应该没问题」**，让他截图确认。
- 已确认可用的历史版本：`桌面\pumpkin-apk-旧版本\` 里的 `pumpkin-shell-20260914-0722.apk`（他确认过 "这次好了"）。
- 他会自己动手改配置/勾选模型；改完记得**新会话才生效**这件事（见「模型与识图」）。
