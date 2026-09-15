# 开发文档

> **这里是改这个仓库的人看的东西，不是给使用者看的。**
> 使用者请看仓库根目录的 [README.md](../../README.md) 和 [ANDROID.md](../../ANDROID.md)。
>
> 放在 `docs/dev/` 而不是仓库根目录，就是为了不让使用者一进来就被这些内容糊一脸。

## 有哪些

| 文档 | 什么时候看 |
|---|---|
| [handoff.md](handoff.md) | **接手前先读这个**。当前进度、已发布版本、真机调试速查、可用模型与工具、关键约束与踩过的坑 |
| [app-self-update.md](app-self-update.md) | 要动更新逻辑时。App 自更新曾经完全失效（三个叠加缺陷）的完整复盘 |
| [background-image.md](background-image.md) | 想做「自定义背景图」时。可行性与成本分析，结论是先不做，附分步拆解 |

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

| 脚本 | 用途 |
|---|---|
| `measure-nav-fps.sh` | 量化「快速来回点底栏」的掉帧情况（需先装到 `/data/local/tmp`） |
| `profile-nav-tap.sh` | 边点击边做 CPU 采样，直接点名热点函数（**必须 `su`**） |
| `capture-nav-anim.sh` | 把系统动画放慢 10 倍后逐帧连拍，用于分析动画轨迹 |
| `test-hidden-page-hit.sh` | 验证「不可见页面会不会吃掉点击」 |
| `native-linux/` | 在手机本机内核上跑原生 Alpine Linux（chroot） |
