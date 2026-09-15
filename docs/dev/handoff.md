# 交接说明（给新的 AI 会话）

> **本项目（除上游服务端外）由 AI 编写。**
> `android-app/`、`.github/workflows/`、`tools/`、以及全部文档都出自 AI 会话，
> 人在真机上定方向、验收、报问题。上游 Pumpkin 服务端（Rust）是人类写的，本仓库只拉取编译。
> 所以：**文档里带数字的结论都配了产生它的方法**（截图、像素比对、`dumpsys`、日志），
> 不要写「应该没问题」。

## 这个项目在干什么

把 **Pumpkin**（Rust 写的 Minecraft 服务端，上游 https://github.com/Pumpkin-MC/Pumpkin）编译到安卓，
再配一个安卓 App「**南瓜坞**」：App 本身**不含服务端**，运行时从 GitHub Releases 下载服务端二进制并启动。

## 源码在哪（重要）

**只维护一处**：`D:\Pumpkin_build\android-app\` —— 这是新仓库的工作副本，**直接改它，然后 git push**。

> 历史遗留：`D:\GitHub\Pumpkin_sgx\android-app\` 里还有一份旧副本，**不要再改**，否则两边不一致。
> `D:\GitHub\Pumpkin_sgx` 本身是旧的 fork 仓库，CI 已停用，只留作看上游 Rust 代码用。

## 仓库与目录

| 位置 | 说明 |
|---|---|
| `D:\Pumpkin_build` | **新仓库（真源）**，GitHub: `a776058959/Pumpkin_build`。只放 App 源码 + CI，不保存上游代码 |
| `D:\GitHub\Pumpkin_sgx` | 旧 fork 仓库（CI 已 disabled），本地保留完整上游代码供查阅 |
| `D:\androidsdk` | 本地工具与归档：NDK r27c、winlibs mingw、下载的 APK、manifest 解析脚本 |
| `D:\androidsdk\shots` | 验证截图归档（含液态玻璃前后对比、像素分析用图） |
| 桌面 `pumpkin-shell.apk` | 最新构建产物；历史版本在 `桌面\pumpkin-apk-旧版本\` |
| `D:\adb-fastboot\adb.exe` | adb（不在 PATH） |

---

## 当前进度快照（2026-09-15）

- App 已从**手写 Java View 整体迁移到 Compose + miuix**（SukiSU / 新版 LSPosed 同款风格）。
  液态玻璃悬浮底栏的全部动效跑通：按压缩放、拖拽、图标缩放、倾斜高光。
- **配色是「色相 × 明暗」两条独立的轴**：7 套色相（蓝 / 绿 / 橙 / 粉 / 青 / 紫 / 中性）
  × {深色, 浅色}，加上「跟随系统」共 14 种组合（`ui/PumpkinTheme.kt` 的 `PumpkinPalettes`）。
  背景渐变 / 卡片底色 / 按钮 / 底栏选中态都跟着变，选择落盘在 `Prefs` 的 `palette` 与 `themeMode`。
  真机像素验证：卡片底色与主按钮强调色**精确匹配**，浅色方案会把状态栏图标翻成深色。
- **沉浸式已修**：状态栏与手势条那一条铺的是 App 自己的渐变，不再是系统色带。
  根因是给 `android.R.id.content` 加了 `setPadding`，露出窗口底色 `#303030`。
  现在 insets 由 Compose 算。
- **App 自更新打通**（历史上从未成功过）：三个叠加缺陷 ——
  `build-android-apk` 的 Gradle/JDK 与工程脱节（还被 `continue-on-error` 掩盖）、
  只查 `/releases/latest`（那里常常没有 APK）、versionCode 写死。
  完整复盘见 [docs/app-self-update.md](app-self-update.md)。
- **发布链路已补齐**：`apk-only.yml` 以前只出 artifact、从不发 Release，
  导致「改完 UI 只跑了 apk-only」时改动永远到不了用户手里（本地 adb 是新的，Release 是旧的，
  用户点检查更新报「已是最新」）。现在加了 `publish` 开关，CI 自己建 Release。详见「出包流程」。
- **两条流水线已分开**：`build.yml` 只构建/发布**服务端**（上游提交触发或手动），
  App 一律走 `apk-only.yml`（手动 + 勾 publish）。服务端 Release 不再带 APK，
  所以 `releases/latest/download/pumpkin-shell.apk` 不再可靠 —— 取 App 用 App 内「检查更新」。
- **插件有了自己的一页 + 插件商店**（本轮）：底栏 4 项（运行 / 更新 / 插件 / 设置）。
  商店索引与 dex 发布在**固定 tag `plugins`** 的 Release 里，App 复用
  `UpdateClient` 的多源回退去拉 —— **装插件不需要 root、不需要存储权限**，
  因为只是往自己的私有目录写文件。详见 [plugins.md](plugins.md)。
  真机全流程验证：`tools/verify-plugin-crud.sh`（20 项断言全过）。
- **本机可以构建了**（本轮）：以前每次改代码都要推 CI，现在
  `tools/build-local.ps1` 一条命令出 APK + 插件 dex + 索引，产物与 CI 可复现
  （同一个 dex 的 SHA256 相同）。CI 只在**发布**时才需要。详见 [local-build.md](local-build.md)。
- App 更新改为应用内下载 + 直接安装：不再跳浏览器。下载进度在「更新」页，
  下完走 FileProvider 交给系统安装器。下载源默认「官方直连」，可在设置里改。
- 最新已发布：见 `releases/latest`。App 现在出的是 `assembleRelease` 而**不是**
  `assembleDebug` —— 这是性能关键，详见下面的「底栏点击卡顿排查」。
- **原生 Linux 可用**：在手机本机内核上跑真正的 Alpine（chroot），不是 Termux 那种用户态终端。
  脚本、实测结果与踩过的坑见 [tools/native-linux/](../../tools/native-linux/)。
- 术语已统一：源码与文档里不再叫「壳」，一律叫「南瓜坞 / App」。
- 归档截图（`D:\androidsdk\shots\`）：`immersive.png` vs `immersive_fixed.png`（沉浸式前后）、
  `theme_night.png` / `theme_sakura.png` / `theme_forest.png`（三套配色）、
  `dlg_*.png`（miuix 对话框）、`colorcontrol.png`（识图模型的色准对照图）。

### 验证方法（重要，已升级）

**精确数值自己用像素扫描量，定性判断才交给模型。**
会识图的模型用 `xiaomi-token-plan-cn/mimo-v2.5` ——
注意**不带 `pro` 的才支持图像输入，`pro` 反而是纯文本**。

实测色准（对照图喂已知色块，见 `colorcontrol.png`）：
真值 `48,48,48 / 200,30,60 / 30,140,255 / 240,240,240` →
它读 `50,50,50 / 190,40,55 / 45,140,230 / 230,230,230`。
**约 ±10/255 的偏差，够判断"这是什么颜色、像不像原生"，不能当像素尺用。**

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

## 未完成 / 已知取舍

1. **倾斜高光仍需人配合验证**（唯一没法自动验的动态效果）
   - 手机**平放**时截一张 → 请用户把手机**向右倾斜约 45° 拿稳** → 再截一张
   - 对比悬浮栏水平方向亮度重心是否右移（位移系数 0.38 × 导航栏宽度）
   - 注意：截屏是**当前帧**，光斑位置会被记录下来，可以对比
2. **Magisk 还没授权南瓜坞** → App 内切「Root 模式」会提示未获得 root。
   这**不是代码问题**：`su` 本身可用（`su -c id` → `uid=0 context=u:r:magisk:s0`），
   但 Magisk 的超级用户列表里没有 `com.pumpkin.server`，需要人在 Magisk 里点一次授权。
   在此之前普通模式（targetSdk 28 豁免）已验证可用。
3. **Release 资产名 `pumpkin-shell.apk` 刻意没改名**：改名会让已发布出去的下载链接 404。
   代码不依赖这个名字（`fetchAppAsset` 按 `.apk` 扩展名匹配）。
4. **`Prefs.NAME = "pumpkin_shell"` 刻意没改名**：那是 SharedPreferences 的文件名，
   改了会让升级后的用户丢掉全部设置（镜像源、启动方式都在里面）。
5. 设置页最后一条加速源要滚动才看得到（纯观感，不影响功能）。
6. **背景图功能**：已做过可行性分析，**结论是先不做**（不简单）。
   完整拆解与成本估算见 [docs/background-image.md](background-image.md)。

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
- **点界面之前先看这份备忘**（都是实测踩出来的）：
  - 底栏 4 项的中心 x = **193 / 424 / 655 / 887**，y ≈ **2278**（不是 2255）。
  - `input tap` 打在底栏上**偶发被吃掉**（像是上一帧动画还没结束）。别紧接着点页面里的按钮——
    那会点在上一页的空白处，很容易误判成「功能坏了」。用 `tools/goto-page.sh`，它会确认切过去了。
  - 算坐标用 `tools/dump-ui.sh`（输出「文字 @ 中心坐标」）；
    注意它按**子串**匹配，`刷新` 会同时命中状态文字和按钮，取第一行会点错。
  - 复合命令（`su -c`、管道、重定向）一律**写成 `.sh` 推上去跑**，
    拼在 `adb shell "..."` 里会被 PowerShell 和手机 sh 双重解释。
  - 推上去的脚本先 `sed -i 's/\r$//'`（仓库是 CRLF，`\r` 会被当成命令的一部分）。
  - App 是 **release 包（非 debuggable）**，`run-as` 用不了；读私有目录只能 `su -c`。

---

## 模型与识图（重要，新会话必读）

### 实测可用性地图（2026-09-15 更新）

| 模型 | 能看图 | 备注 |
|---|---|---|
| `xiaomi-token-plan-cn/mimo-v2.5` | ✅ **实测可用** | ⚠️ **不带 `pro` 的才是多模态**；`mimo-v2.5-pro` 反而是纯文本。反直觉，别记错 |
| `ark-agent-plan-cn/kimi-k3` | ✅ 已用真实截图验证 | 计划内，1M 上下文 |
| `ark-agent-plan-cn/minimax-m3`、`doubao-seed-*` | ✅ 声明支持 | |
| `ark-agent-plan-cn/glm-5.3`、`deepseek-v4-pro/flash` | ❌ 纯文本 | |
| `zhipu/glm-4v-flash` | ⚠️ 声明支持，但实测一次也没返回（超时） | 且 `zhipu` 在部分会话里未注册，报 `not registered` |

**色准实测**（喂已知色块对照图 `D:\androidsdk\shots\colorcontrol.png`，四条纯色横带）：

```
真值          48,48,48   200,30,60   30,140,255   240,240,240
mimo-v2.5 读  50,50,50   190,40,55   45,140,230   230,230,230
```

**偏差约 ±10/255** —— 够判断「这是什么颜色、像不像原生控件、有没有错位」，
**不能当像素尺用**。

> 所以本项目现在的验证分工是：
> **精确数值自己用像素扫描量**（PowerShell + System.Drawing 逐像素读），
> **只有定性判断才交给视觉模型**。

### 怎么派视觉子代理

**唯一能给子任务指定模型的通道是 `workflow` 工具的 `agent(prompt, { provider, model })`。**
`subagent` / `subagent_fork` **不支持**指定 provider/model（它们继承父会话路由），
所以「换个能识图的便宜模型干活」这件事必须走 `workflow`：

```js
// workflow 脚本体内
const r = await agent(
  "用 read_image 读取 D:\\androidsdk\\shots\\x.png，然后回答：……",
  { label: "看图", provider: "xiaomi-token-plan-cn", model: "mimo-v2.5" }
);
return r;
```

给子代理**图片的绝对路径**，让它自己用 `read_image` 读。

主会话模型（`deepseek-v4.1-flash` 等）是纯文本，主循环里直接 `read_image` 会报
`does not declare image input`，必须走上面的派发。

⚠️ **子代理路由白名单是「会话创建时快照」的**：
`~/.dsh/settings.yaml` 的 `subagent-model-selection.allowedModels` 改了之后，
**只有新会话生效**；老会话里用白名单外的模型会报
`child LLM route "..." is not allowed for this Session`（`workflow` 的 `agent()` 通道同样受限）。
所以识图要么在**新会话**里做，要么直接把主模型换成视觉模型。

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
| `.github/workflows/apk-only.yml` | **只重打包 App**，约 1 分钟出包（改 UI 用这个）。默认**只出 artifact**，勾 `publish` 才发 Release |
| `.github/workflows/build.yml` | 定时（每 2 小时比较上游 SHA，无新提交不构建）+ 全平台构建 + 发布 Release + 记录基线。**耗时 1 小时以上** |
| `.github/workflows/prune-dryrun.yml` | 手动检查 Release 保留策略会删什么（只打印不删） |

Release 保留策略脚本：`.github/scripts/prune-releases.sh`
（<1月全留 / 1-2月每周留一个 / 2-12月每月留一个 / >1年删除）

### ⚠️ apk-only 默认不发布 —— 这是最容易犯的错

`apk-only` 默认**只产出 Actions artifact，不创建 Release**。
而 **artifact 用户是看不见的** —— App 的「检查更新」读的是 Releases。

踩过的坑：连续几轮 UI 改动都只跑了 apk-only（没勾 publish），
本地 adb 装的是新包、看起来一切正常，但 Releases 里还停在旧版。
用户点「检查更新」报「已是最新」—— 因为**发布渠道里确实没有更新的东西**，
不是检查功能坏了。判断上很容易误以为「我这边是好的」。

**出包后必做的一致性核对**：

```powershell
# 手机上装的
D:\adb-fastboot\adb.exe shell "dumpsys package com.pumpkin.server | grep -E 'versionCode|versionName'"
# 最新 Release 的 tag
#   GET /repos/a776058959/Pumpkin_build/releases/latest  → tag_name
```

`Custom-YYYYMMDD-HHMM` 的 versionCode = `epochDay * 10000 + HHMM`
（例：`Custom-20260915-0745` → `207110745`）。

**手机上装的必须 ≤ 最新 Release 的 versionCode**。
否则就是「本地是新的、发出去的是旧的」，用户永远收不到更新。

## 出包流程

1. 改 `D:\Pumpkin_build\android-app\` 里的源码
2. 提交并 push 到 `main`
   （push 前先 `git fetch` + `git rebase FETCH_HEAD`，因为 `record` job 会自动往 main 提交 `.github/upstream-lock.txt`）
3. 触发构建 —— **要发给用户就带上 `publish`**：

   ```
   POST /repos/a776058959/Pumpkin_build/actions/workflows/apk-only.yml/dispatches
   body: {"ref":"main","inputs":{"publish":true}}
   ```

4. 等约 1-3 分钟。勾了 `publish` 的话，CI 会**自己**建好 Release 并附上 `pumpkin-shell.apk`
   （tag = `Custom-<stamp>`，与 APK 内嵌的 versionCode 严格一致 —— 这个不变量不能破）
5. **真机直装**：直接从 Release 下载，顺便把下载链路也验一遍

   ```powershell
   curl.exe -L -o "$env:TEMP\a.apk" "https://github.com/a776058959/Pumpkin_build/releases/latest/download/pumpkin-shell.apk"
   D:\adb-fastboot\adb.exe install -r $env:TEMP\a.apk
   ```

6. 核对一致性（见上一节）。装完手机上的 versionCode 应该**等于**最新 Release 的。

> **不要手工删/传 Release 资产了。**
> 以前是那么干的（流程里甚至记着一个写死的 release id），现在由 CI 负责 ——
> 手工介入会让 tag 与 APK 的版本号错位，那就正好制造出「每次都提示更新、装完还提示」的 bug。

## 底栏点击卡顿排查（2026-09-15）

### 现象

底栏胶囊来回快点三个按钮时不顺畅，"像帧数低"；**拖动是顺的**。

### 一、gfxinfo 量帧（压力脚本：快速来回点底栏）

| 版本 | 总帧数 | Janky 帧 | 90th | 99th | Slow UI thread | Missed Vsync | GPU 99th |
|---|---|---|---|---|---|---|---|
| 原始 debug | 252 | 17 (6.75%) | 48ms | 150ms | 17 | 8 | ~12ms |
| +三页常驻组合 | 307 | 22 (7.17%) | 28ms | 48ms | 19 | 8 | ~12ms |
| +撤销一处错误修复 | 286 | 17 (5.94%) | 29ms | 73ms | 16 | 6 | ~12ms |
| **换成 release 包** | 268 | **12 (4.48%)** | **20ms** | 69ms | **10** | **3** | ~12ms |

**GPU 99th 始终 ~12ms** → 瓶颈在 UI 线程 CPU，不在 GPU / 玻璃着色器。
这一条就否掉了"液态玻璃太重"这个直觉判断。

### 二、simpleperf 采 CPU（debug 包，15 秒 / 16593 个样本）

| 函数 | 占比 |
|---|---|
| `art_jni_trampoline` | 2.16% |
| **`artQuickToInterpreterBridge`** | **2.14%** |
| **`art::interpreter::ExecuteSwitchImplCpp`** | **1.80%** |
| `androidx.compose.ui.node.NodeCoordinator.rectInParent` | 1.45% |
| `artQuickGenericJniTrampoline` | 1.02% |
| `gc::collector::ConcurrentCopying::AddLiveBytesAndScanRef` | 0.97% |
| `SemanticsNode.fillOneLayerOfSemanticsWrappers` | 0.92% |

### 根因

**发的是 debug 包。** `debuggable=true` 时 ART 不做 AOT 编译，整个 Compose 运行时
跑在解释器上；Compose 极度依赖内联，解释器模式下慢一大截，分配多又导致 GC 频繁。
SukiSU 等同类应用发的是 **release** 包，所以它们顺。

换 release 后复采样：解释器那两个符号**完全消失**（未进前 45 名），`flags=0x0`（非 debuggable）。

### 修复

CI 从 `gradle assembleDebug` 改为 **`assembleRelease`**。
release 与 debug 用**同一把签名密钥**（`signingConfigs["fixed"]` / `pumpkin-signing.p12`），
所以 release 包能直接覆盖安装，不用卸载、不丢数据。体积 13.7MB → **9.7MB**。

`isMinifyEnabled` **刻意保持 false**：这一步只隔离「AOT」一个变量；
开 R8 会额外加内联，但需要单独验证，混在一起就无法归因。

### 遗留

`SemanticsNode.fillOneLayerOfSemanticsWrappers` 0.92% —— 语义树遍历，
可能与底栏的 `clearAndSetSemantics` 以及无障碍/自动化 dump 有关。未动，待后续。

### 教训（重要）

**遇到「卡」的问题，先用 `simpleperf` / `gfxinfo` 量，再决定改什么。**
本次一开始凭直觉去改 UI 代码（改了底栏动画、加了页面过渡、改了页面组合方式），
绕了三轮才发现根因在**构建类型**上，跟 UI 代码无关。

- `gfxinfo` 用来分辨「轨迹问题」和「掉帧问题」：前者帧时间正常，后者会看到成片 >16.7ms。
- `simpleperf` 直接点名热点函数，比读代码猜快得多。
- 两者都要 **`su`**：用 `shell` 用户跑 simpleperf 会 `Permission denied`。
- 相关脚本：`tools/measure-nav-fps.sh`、`tools/profile-nav-tap.sh`、`tools/capture-nav-anim.sh`。

## APK 体积与 R8（2026-09-15）

### 体积构成（实测）

| 类别 | 占比 |
|---|---|
| **dex（代码）** | **94%** —— `classes.dex` 13.79MB + `classes2.dex` 13.7MB（压缩前 27.5MB） |
| res | 0.4%（0.04MB） |
| lib | 0.2% |
| assets / META-INF | 0.3% |

**体积大头完全在 dex**，跟资源无关。这一点决定了优化方向（开 R8，而不是删资源）。

### 为什么曾经是 9.66MB

上一步为了「只隔离 AOT 这一个变量」，刻意把 `isMinifyEnabled` 设为 `false`。
没有 R8 就没有死代码消除与内联，依赖里**每一个类**都原样进包
（Compose 运行时 + material3 + miuix 全家桶含图标库）。
早先「3~5MB」的预估是按正常 release 构建算的，那个数字本身没错。

### 修复

- `isMinifyEnabled = true` + `isShrinkResources = true`
- 新增 `app/proguard-rules.pro`，只有两条规则，都是为**可调试性**：
  - `-keepattributes SourceFile,LineNumberTable` —— 崩溃堆栈要写进 `last_crash.txt`，没行号没法定位
  - `-keepnames class com.pumpkin.server.** { *; }` —— 用 `-keepnames` 而非 `-keep`：
    后者会连带禁止优化、丢掉内联收益；而体积大头在依赖，不在本应用这几十个类
- 敢开的前提：**本应用没有任何反射**（grep 确认无 `Class.forName` / `getMethod` /
  `newInstance` / `::class.java`），Compose / miuix / androidx 各自带 consumer 规则

### 中途报错与处理

```
> Optimized resource shrinking requires non-final IDs.
```

原因：`gradle.properties` 里有 `android.nonFinalResIds=false`（AGP 9 迁移遗留，无注释）。

处理：加 `android.r8.optimizedResourceShrinking=false` 退回传统资源删减，
**没有**去掉 `nonFinalResIds=false` —— 资源只占 0.4%，优化删减没有收益；
dex 那 94% 由 `isMinifyEnabled` 负责，与这个开关无关。

### 结果

| 指标 | 修复前 | 修复后 |
|---|---|---|
| APK | 9.66MB | **1.25MB** |
| dex | 9.08MB | **1.06MB** |

比早先 3~5MB 的预估还小很多。

### 帧率同步改善（同一压力脚本：快速来回点底栏）

| 版本 | Janky 帧 | 90th | 95th | 99th | Slow UI thread | Missed Vsync |
|---|---|---|---|---|---|---|
| debug 包 | 17 (6.75%) | 48ms | 93ms | 150ms | 17 | 8 |
| release（未开 R8） | 12 (4.48%) | 20ms | 29ms | 69ms | 10 | 3 |
| **release + R8** | **9 (3.66%)** | **19ms** | **26ms** | **53ms** | **6** | **1** |

R8 的内联对 Compose 是实打实的收益 —— Compose 靠内联消除 lambda 分配与虚调用开销。

### R8 冒烟测试（真机，6 项）

启动不崩 / 三页可切 / **miuix 对话框正常** / 配色切换有效 / 下载源弹窗正常 / 全程无崩溃，
另有 `logcat` 无 `ClassNotFound` / `NoClassDefFound`。

> 一个坑：验证「检查更新」对话框时一度以为被 R8 删坏了，其实是**版本漂移** ——
> 当时本地包比已发布的还新，检查更新只弹了 Toast「已是最新」，而 **uiautomator 抓不到 Toast**。
> 发布一个更新的版本后对话框立刻正常。**用 uiautomator 验证时要注意 Toast 是抓不到的。**

## ⚠️ 不可见的 Compose 页面仍然会吃掉点击（2026-09-15，踩过）

**Compose 的绘制遍历和触摸遍历是两套独立的东西。**

- 曾经为了让三个页面常驻组合（省掉切页时的重新组合，实测 90th 38→18ms），
  用 `drawWithContent { if (visible) drawContent() }` 让不可见的页"不画"。
- 结果：**节点仍然是全屏大小，照样参与命中测试**。三个页面铺在同一个 Box 里，
  最上层的设置页即使不可见也接收点击 —— 用户报「很多按钮功能错乱」。
- 真机实验坐实：在「运行」页点 `(807,1431)`（该处运行页没有可点控件），
  `palette` 从 `cyan` 变成 `green`。

**正确做法**：用 `Modifier.layout` 把不可见的页报成 **0×0**。
0 尺寸节点不在任何触摸范围内，而组合与 measure 都保留（滚动位置等状态不丢）。

```kotlin
Box(
    modifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        if (visible) layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        else layout(0, 0) { }
    },
) { content() }
```

**验证脚本：`tools/test-hidden-page-hit.sh`** —— 在运行页点一个"该页没有控件"的坐标，
然后读 Prefs 看设置有没有被误改。改 UI 层级/可见性之后**必须跑它**，
别只看代码觉得"应该没问题"。用 `drawWithContent` 那版会真的改掉，0 尺寸版不会。

> 附带一条：页面常驻组合之后，**切页不再销毁页面，滚动位置会保留**。
> 写测试脚本时要注意（本喵的脚本就被这个坑了一次，以为页面没滚到顶）。

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

- ✅ **本机已经能构建了**（本轮装的，见 [local-build.md](local-build.md)）：
  `D:\devtools` 下有 JDK 21 / JDK 17 / Gradle 9.7.1 / Android SDK，
  `tools/build-local.ps1` 一条命令出 APK + 插件 dex + 索引。
  **日常迭代别再推 CI**；CI 只用来发布。
- 依然没有：模拟器、MSVC。
- **真机调试已可用**：adb + 测试机（见上「真机调试速查」），UI 效果要靠真机截图验证
- git 用 GitHub Desktop 自带的：
  `C:\Users\a7760\AppData\Local\GitHubDesktop\app-3.6.5\resources\app\git\cmd\git.exe`
- GitHub token 在 `%TEMP%\gh_tok.txt`（GitHub Desktop OAuth token；scopes: repo, user, workflow）
- 代理 `http://127.0.0.1:7897`（clash verge）：访问 GitHub / dl.google.com / api.adoptium.net
  要设代理（`HTTPS_PROXY`、`curl --proxy`、`git -c http.proxy=`）；
  **访问国内 API（火山/智谱/硅基流动）要删掉代理变量**，否则可能不通
- 新仓库**零 secret**（用内置 GITHUB_TOKEN），但**仓库设置里 Actions 默认权限必须是 write**
- ⚠️ **文件沙箱**：会话工作目录通常是 `D:\GitHub\Pumpkin_sgx`，而源码在 `D:\Pumpkin_build`。
  如果新会话的文件策略是 `workspace-write`（只允许改工作目录），写 `D:\Pumpkin_build`
  会被沙箱拒绝——按提示申请放宽权限（`danger-full-access`）即可，不是命令写错了。

## 南瓜坞 App 当前功能

- **四个页面**（运行 / 更新 / 插件 / 设置）+ 底部悬浮**液态玻璃**导航
  （实时背景模糊 + 边缘亮线 + 顶部光泽 + 倾斜流动高光 + 按压缩放/拖拽动效）
- **7 套色相 × 明暗**可切换，背景渐变、卡片底色、按钮、底栏选中态一起变
- **沉浸式**：状态栏与手势条那一条铺的是 App 自己的背景渐变，不是系统色带
- **运行页**：状态与运行时长、联机地址（Java/基岩）、启动/停止、电池优化、**选择运行版本**（切换/回滚）、控制台（实时日志 + 命令输入，字号可被插件覆盖）
- **更新页**：三块独立卡片 —— 版本信息（检查更新/选择版本）、下载（三态按钮 + 进度条 + 删除下载任务）、本地版本（删除已安装版本）
- **插件页**：分**两段**（已安装 / 商店）。已安装段里能启用 / 停用 / 卸载 / 重新加载、
  改插件声明的设置项；商店段拉索引、一键安装 / 更新。**全程不需要 root**。
  目录与日志**只在出问题时才显示**（正常时就是一句「共加载 N 个插件」，是噪音）
- **设置页**：一张**主列表**（外观 / 下载源 / 服务端启动方式 / 清理数据 / 关于）
  + 四个**二级页**（SukiSU 那种做法）。主列表右侧显示**当前值**，一眼看出现在是什么。
  二级页配了返回键处理 —— 在二级页按返回先回主列表，**不会直接退出 App**。
  「下载源」那页把服务端下载源（加速源 + API/仓库/镜像）和 App 更新源收在一处
- **界面文案的原则：不写「操作说明」。** 按钮叫什么、点了会变成什么，用户自己看得见；
  「点下载开始，下载中会变成暂停」这种话是在重复界面已经说清楚的事，只会让页面变吵。
  界面里只留**状态**（正在下载多少 / 装好了 / 失败了）与**非显然的信息**（如「只支持 arm64」）
- 所有弹窗都是 **miuix 风格**（`WindowDialog`），不用系统原生 `AlertDialog`
- **双启动模式**：普通模式靠 targetSdk 28 豁免；Root 模式走 `su` 域（**不改 SELinux**，不会被检测软件发现）
- **App 自更新**：查 Releases 里带 `.apk` 的最新发布，提示下载覆盖安装（版本号由构建时间戳推导）
- **下载**：多源自动回退（直连 → 上次成功的 → 用户镜像 → ghfast.top → gh-proxy.com → ghproxy.net），
  支持断点续传；安装前校验 ELF（aarch64）；失败自动重试。插件下载共用这套源顺序

## 与用户沟通的注意点

- 中文交流，喜欢直接、不要冗长铺垫。
- 他多次因 UI 细节不满意（按钮跳动、圆角缺口、指示器尺寸）。
  **改 UI 前先想清楚状态归属和控件职责，别打补丁叠补丁。**
- **不要声称「应该没问题」**，让他截图确认。
- **重要步骤（改文件之类）他要自己来还是交给 AI？** 他明确说过：*「修改文件之类的重要步骤还是你自己来吧」* ——
  即实现由本会话亲自做，不要甩给子代理。子代理只用于自包含的只读任务（识图、调研）。
- 他会自己动手改配置/勾选模型；改完记得**新会话才生效**这件事（见「模型与识图」）。
- **他说「你只负责统筹和规划」时曾要求全交给某个路由**，但随后又纠正为「重要步骤自己做」。
  以最新指令为准：**实现自己做，只把自包含的小任务外派**。
