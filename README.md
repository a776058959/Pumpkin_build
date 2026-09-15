# Pumpkin_build

把开源的 Rust 版 Minecraft 服务端 [Pumpkin](https://github.com/Pumpkin-MC/Pumpkin) 编译成可直接运行的原生二进制，并提供安卓 App「**南瓜坞**」，在手机上下载、启动、管理它。

## 安装

去 [Releases](https://github.com/a776058959/Pumpkin_build/releases) 页，找最新一个带 `pumpkin-shell.apk` 附件的发布，下载安装。系统可能要求先允许「安装未知应用」。App 自身支持应用内更新，装好后不用再手动下 APK。

> 别用 `releases/latest/download/pumpkin-shell.apk` 这种固定链接：服务端发布更频繁，latest 常是不带 APK 的服务端发布，会 404。

## 使用

- 服务端二进制不内置，首次要在「更新」页下载（约 119MB），之后在「运行」页启动。
- 联机地址在运行页：Java 版连 `手机IP:25565`，基岩版连 `手机IP:19132`。
- 界面就四页：运行 / 更新 / 插件 / 设置，名字即功能。
- 游戏数据（世界存档、配置）在 `/sdcard/Android/data/com.pumpkin.server/files/server/`，用文件管理器或 USB 就能访问。
- Releases 里另有 Linux / Windows 版服务端二进制，可单独下载运行。

## 注意

- 只支持 **arm64（64 位 ARM）** 手机，32 位 ARM 和 x86 模拟器不行。
- App 本体约 2MB；要下载的 119MB 是 Pumpkin 的 aarch64 原生二进制，两者是分开的。
- 国内直连 GitHub 常常不通。App 会自动换源重试，也可在「设置」页手动填镜像。
- **targetSdk 是 28，这是刻意的**：Android 10+ 只在该值下允许从应用私有目录执行文件，不是忘了升级。
- 清理数据分三种粒度：清服务端版本 / 清游戏数据 / 全部清空，点之前看清会删什么。
- 装插件不需要 Root，插件索引放在 tag 为 `plugins` 的 Release 里。
- 长时间运行建议把电池优化对本 App 设为「不受限制」，否则进程可能被系统杀掉。
- 手机跑服务端量力而行：调小视距和模拟距离、控制人数，长时间满载会发热降频。

## 致谢

- 上游服务端：[Pumpkin-MC/Pumpkin](https://github.com/Pumpkin-MC/Pumpkin)（人类维护，本仓库不保存其代码）。
- UI 框架：[miuix](https://github.com/miuix-kotlin-multiplatform/miuix)（SukiSU / LSPosed 同款风格）。
- **本项目（除上游服务端外）由 AI 编写**，人在真机上定方向与验收。

> 改这个仓库请看 [`docs/dev/`](docs/dev/)。
