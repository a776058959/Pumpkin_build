# 南瓜坞自身的更新机制 — 曾经完全失效，以及为什么

> 记录于 2026-09-15。用户反馈「老版本 0.3.0 检查不到这次的更新」，
> 排查后发现是**三个叠加的缺陷**，任何一个单独存在都会让更新检查失效。

## 症状

旧版 App 点「检查更新」→ 弹出 **「没有找到南瓜坞的发布信息」**，永远查不到新版本。

## 三个叠加的缺陷

### 缺陷 1：CI 里的南瓜坞 APK 构建一直在静默失败

`build.yml` 的 `build-android-apk` job 写死了 **Gradle 8.9 + JDK 17**，
但 App 工程早已升级到 **AGP 9.4.0（要求 Gradle ≥ 9.6）+ JDK 21**。

实际报错：

```
* What went wrong:
An exception occurred applying plugin request [id: 'com.android.application']
> Failed to apply plugin 'com.android.internal.version-check'.
> Minimum supported Gradle version is 9.6.0. Current version is 8.9.
```

而这个 job 配了 **`continue-on-error: true`** —— 于是它**每次都失败，整个 workflow 却显示 success**，
release 照常发布，只是**从来没有附上 APK**。

`git log` 里每一次 build.yml 运行的 `build-android-apk` 都是 `failure`，只是没人看见。

**教训**：`continue-on-error: true` 会吞掉真实失败。给 job 加这个标记时，
必须同时确认失败会在别处暴露出来（比如在这里应该 fail 掉 release job）。

### 缺陷 2：只查 `/releases/latest`，而 latest 常常没有 APK

`UpdateClient.fetchShellAsset()` 原来只请求：

```
GET /repos/{repo}/releases/latest
```

本仓库的 Release 是**服务端构建和南瓜坞 APK 共用一个发布流**的，
而服务端构建（8 个平台）比 App 频繁得多。`latest` 只返回**最新那一个** release，
它经常只有服务端二进制、没有 APK：

```
tag: Custom-20260915-0244
assets: pumpkin-android-arm64-20260915, pumpkin-linux-*, pumpkin-windows-*.exe
        ← 没有任何 .apk
```

代码遍历 assets 找 `.apk`，找不到就 `return null` → 用户看到「没有找到南瓜坞的发布信息」。
带 APK 的那两个 release（`build-20260914-0548`、`Custom-20260914-0707`）都不是 latest，
永远轮不到。

**修法**：改查 `GET /releases?per_page=20`，在最近 20 个 release 里挑**确实带 .apk** 且最新的那个。

### 缺陷 3：versionCode / versionName 是写死的常量

```kotlin
versionCode = 3
versionName = "0.3.0"     // 从没改过
```

后果有两层：

1. **判断新旧只能靠安装时间戳**（`asset.updatedAt > installedTime + 120000`）。
   而安装时间戳会被「重装同一个包」刷新，根本分不出哪个更新。
2. **装上新版后版本显示还是 0.3.0**，用户无法分辨自己装的是哪一版。

## 修复方案：版本号由构建时间戳推导

三处必须用**同一个时间戳**，否则 App 端版本比较会错位：

```
versionCode = epochDay * 10000 + HHMM
```

`20260915-0244` → `20711 * 10000 + 244` = **207110244**

- 为什么不用 `YYYYMMDDHHMM` 直接当 versionCode：那是 2026 亿，
  超过 Android versionCode 的 **int 上限 21.47 亿**。换算成天数后约 2.07 亿，够用到公元 2558 年。
- 实现位置：`android-app/app/build.gradle.kts`，时间戳来源优先级
  `-PbuildStamp=…`（CI 传） > 环境变量 > 本地当前时间。
- 解析位置：`UpdateClient.parseVersionCodeFromTag()`，**公式必须与 build.gradle.kts 保持一致**。

### 三处时间戳的贯通

```
build-android-apk job:
  Set build stamp  →  stamp=20260915-0450
  Build APK        →  gradle … -PbuildStamp=20260915-0450   （烧进 versionCode）
  outputs.stamp    →  透出给 release job

release job:
  Get build timestamp  →  优先复用 needs.build-android-apk.outputs.stamp
  Create Release       →  tag: Custom-20260915-0450          （与 APK 内嵌值严格相等）
```

**为什么要这么绕**：如果 release job 自己取当前时间当 tag，它会比 APK 里的时间晚几分钟，
于是「tag 解析出的版本号」永远大于「APK 内嵌的版本号」，
表现为**每次都提示有新版本、装完还提示**。

apk job 失败（拿不到 stamp）时退回当前时间，并打一个 `::warning::`。

## 版本比较的优先级

```java
if (asset.versionCode > 0 && installed > 0) {
    newer = asset.versionCode > installed;      // 首选：比版本号
} else {
    newer = asset.updatedAt > installedAt + 120000L;   // 兜底：老式 tag 比时间戳
}
```

`versionCode` 解析不出来（非 `…-YYYYMMDD-HHMM` 格式的 tag）时才退回时间戳比较，
留 2 分钟余量避免边界抖动。

## 验证

CI 产出的 APK 内嵌值（从 `AndroidManifest.xml` 的 UTF-16 字符串池读出）：

```
versionCode=207110440
versionName=0.4.0+20260915-0440
```

`build-android-apk` job 从长期 `failure` 变为 `success`，并产出
`pumpkin-android-apk-20260915`（13.3 MB）。

## 以后改 App 工程时要记得

- **改 AGP 版本 → 同步改 `build.yml` 里 `build-android-apk` 的 `gradle-version` 与 `java-version`。**
  这两处很容易脱节，而 `continue-on-error` 会让脱节悄无声息。
- 改 versionCode 公式 → 同步改 `UpdateClient.parseVersionCodeFromTag`。
- 发布带 APK 的 release 时，确保它是**最新**的那个，否则旧版 App（只查 `/latest`）看不到。
