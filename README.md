# Pumpkin_build

[Pumpkin](https://github.com/Pumpkin-MC/Pumpkin)（Rust 写的 Minecraft 服务端）的**纯构建仓库**。

## 它做什么

- **不保存上游代码**：本仓库只有构建流水线、Android APK 源码和说明文档。
- 构建时才按记录的上游 commit 直接把上游源码拉到 runner 上编译（`git fetch --depth=1 <sha>`）。
- `.github/upstream-lock.txt` 记录「上次成功构建所基于的上游提交 SHA」；定时任务只做一次 `git ls-remote` 比较，
  上游没有新提交时**不会触发任何编译**。

## 目录

| 路径 | 说明 |
|---|---|
| `.github/workflows/build.yml` | 主流水线：定时检查上游（每 2 小时）→ 有新提交才编译各平台 → 打包 APK → 发布 Release → 记录基线 |
| `.github/workflows/manual-build.yml` | 手动触发一次完整多平台构建（跳过上游检查） |
| `.github/workflows/apk-only.yml` | 只重打包 APK，复用已编译好的原生二进制，几分钟出包 |
| `android-app/` | APK：把原生的 aarch64-linux-android 服务端二进制作为 `libpumpkin.so` 打进 APK |
| `ANDROID.md` | 手机上安装与运行说明（APK 方式 / Termux 方式） |
| `.github/upstream-lock.txt` | 上次构建基于的上游提交（由流水线自动维护，不要手工改） |

## 产物

见 [Releases](../../releases)：各平台二进制，以及可直接安装的 `pumpkin-android-arm64-*.apk`。

APK 使用仓库内固定的签名密钥（`android-app/app/pumpkin-signing.p12`），因此新版本可以**直接覆盖安装**，
不用卸载、也不会丢世界数据。注意该密钥是自签名测试用途，密码公开，不要用于正式分发。

## 手动触发

Actions → 选择 workflow → Run workflow。主流水线支持 `force` 选项，可在上游无新提交时强制构建。