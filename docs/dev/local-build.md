# 本机构建与上机验证

> 以前改一行代码也要推到 GitHub 让 Actions 编译，失败了再「轮询 → 拉日志 → grep」
> 一整圈。现在本机能直接构建、直接 adb 装到手机上验证。
> **只有发布**（要变成 Release 供 App 自更新）才需要推 CI。

## 一次性：装工具链

本机原本没有 JDK / Android SDK / Gradle，所以两个脚本各管一段：

```powershell
powershell -File tools/setup-local-toolchain.ps1   # JDK 21 + JDK 17 + Gradle 9.7.1 + cmdline-tools
powershell -File tools/setup-android-sdk.ps1       # platform-tools + platforms;android-37.0 + build-tools;37.0.0
```

装到 `D:\devtools`，**不动系统 PATH、不改注册表**。脚本里已经把
`local.properties`（SDK 路径）与 `$HOME\.gradle\gradle.properties`（JDK 路径 + 代理 + 内存）配好。

几个当时踩到的点，脚本里都处理了：

| 现象 | 原因 |
|---|---|
| 下载全部超时 | 直连 `dl.google.com` / `api.adoptium.net` 不通，得走本地代理 `127.0.0.1:7897` |
| `Failed to find package 'platforms;android-37'` | 新版 SDK 的包名带次版本号，是 `platforms;android-37.0` |
| `Cannot find a Java installation ... languageVersion=17` | 插件模块用了 `jvmToolchain(17)`，所以**还要一个 JDK 17**（CI 的 runner 镜像自带，本机没有） |

## 每次：构建

```powershell
powershell -File tools/build-local.ps1              # APK + 插件 dex + 商店索引
powershell -File tools/build-local.ps1 -SkipPlugins # 只出 APK，改 App 代码时快一点
```

产物落在 `out/`（已 gitignore）。脚本做的步骤与 `.github/workflows/apk-only.yml`
一一对应，**产物也一样**：本机产出的 `plugin-console-font.dex` 与 CI 发布的那份
SHA256 完全相同（`7998B43B…3DFE`），所以本地验过的东西推上去不会因为构建方式不同而变样。

脚本里有两道主动断言，都是被坑出来的：

- **插件 jar 里不能有 `com/pumpkin/plugin/**` 的类** —— 出现就说明有人把 `compileOnly`
  改成了 `implementation`，插件的 `instanceof` 会静默失败。
- **APK 的 versionCode 不能是兜底值 20000000** —— 见下面「PowerShell 的坑」第一条。

## 每次：上机验证

用 `D:\adb-fastboot\adb.exe`（不在 PATH 里）。手机上装了 Magisk，`su` 可用。

```powershell
D:\adb-fastboot\adb.exe install -r out\pumpkin-app-<stamp>.apk
```

`tools/` 下这几个脚本是**设备侧**的，用法统一是 push 上去再 `sh`：

```powershell
adb push tools\xxx.sh /data/local/tmp/x.sh
adb shell "sed -i 's/\r$//' /data/local/tmp/x.sh; sh /data/local/tmp/x.sh"
```

`sed -i 's/\r$//'` 不能省：仓库里是 CRLF，设备上的 `sh` 会把 `\r` 当成命令的一部分。

| 脚本 | 用途 |
|---|---|
| `verify-plugin-crud.sh` | 插件页「增删改查」全流程，20 项断言，一条命令跑完 |
| `dump-ui.sh` | 把界面 dump 成「文字 @ 中心坐标」，算点击位置用 |
| `goto-page.sh` | 切页**并确认真的切过去了**（`input tap` 打在底栏上偶发被吃掉） |
| `console-height.sh` | 量运行页控制台文字的高度，用来验证「字号」这类改动 |

## PowerShell 的坑（写脚本前先看）

1. **`.ps1` 必须有 UTF-8 BOM。** Windows PowerShell 5.1 会按系统 ANSI（简中 = GBK）
   解码没有 BOM 的脚本，中文变乱码、乱码字节还会吃掉引号，报成看不懂的
   `Unexpected token`。改完 `.ps1` 跑一下 `tools/fix-ps1-bom.ps1`（它本身是纯 ASCII，
   没有 BOM 也能跑）。
2. **`-Pfoo=$bar` 不展开变量。** 必须写成 `"-Pfoo=$bar"`。本喵在这上面翻过车：
   gradle 收到字面量 `$Stamp`，时间戳解析失败、版本号静默退化成 20000000 ——
   包能装能跑，只是「检查更新」永远失灵。
3. **`"$dir"` 对 `DirectoryInfo` 只给名字**，不给路径。要用 `$dir.FullName`，
   否则 `Test-Path "$dir\x"` 会去当前目录找、静默落空。
4. **`Get-Content` 默认按 ANSI 读**，读 UTF-8 的 json/源码要显式 `-Encoding UTF8`。
5. `>`、`|`、嵌套引号会被 PowerShell 自己解释 —— 凡是复合的设备侧命令，
   都**写成 `.sh` 文件推上去**，不要拼在 `adb shell "..."` 里。
