# 背景图功能：可行性与成本分析

> 只做分析，未实现。结论在最后一节。

## 结论先行

**不建议现在做** —— 它不属于「简单功能」。

核心逻辑确实不多（约 300–450 行），但真正吃时间的是三处隐藏耦合：
**SAF 持久化权限**、**液态玻璃模糊背板的采样源**、**任意图片下的文字可读性**。
预计 2–3 次 CI 往返、60k–100k token，而且必须有人真的去相册挑图、并判断好不好看。

## 一、功能本身怎么做

### 1. 选图与落盘（Java 侧，约 120 行）

- 入口：设置页加一张「背景图」卡片 → `PumpkinActions.pickBackgroundFromUi()`。
- 选图用 `Intent.ACTION_OPEN_DOCUMENT` + `startActivityForResult`。
  `MainActivity` 仍是 `ComponentActivity`，`targetSdk=28` 下 `onActivityResult` 完全可用，
  不必引入 `rememberLauncherForActivityResult` —— 这样「谁来选图」仍归 Java，
  保持现有分工（Compose 只渲染、只转发点击）。
- **必须调 `takePersistableUriPermission`**，否则重启后 URI 直接失效。
- **建议把图拷进私有目录**（如 `files/background.img`）而不是直接用 URI 渲染：
  用户可以随时删原图、或卸载 SD 卡；拷贝之后还顺手能做一次降采样。
- 降采样：屏幕 1080×2400，按需求解即可（`inSampleSize`），
  别把 4000×3000 的原图整张读进内存。
- `Prefs` 存 `background_path` 与 `background_dim`（0–100 的压暗强度）。

### 2. 渲染（Compose 侧，约 80 行）

`PumpkinApp` 里现在是：

```kotlin
Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bgTop, bgBottom))))
```

改成有图时画图、没图时保留渐变：

```kotlin
if (bgBitmap != null) {
    Image(bitmap = bgBitmap, contentDescription = null,
          contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    Box(Modifier.fillMaxSize().background(scrimBrush))   // 压暗，保可读性
} else {
    Box(Modifier.fillMaxSize().background(gradient))
}
```

- 解码要离开主线程：`produceState` + `withContext(Dispatchers.IO)`，
  路径变化时重解码，旧 bitmap 及时回收。

### 3. 设置页 UI（约 60 行）

- 「背景图」卡片：当前状态（无 / 文件名）、「选择图片」「清除」两个按钮、
  再加一个压暗强度滑杆（miuix 有 `Slider`）。
- 写法照抄现有的「配色」卡片即可。

### 4. 状态（约 20 行）

- `PumpkinUiState` 加 `backgroundPath: String`、`backgroundDim: Float`。
- `PumpkinActions` 加 `pickBackgroundFromUi()` / `clearBackgroundFromUi()`。

## 二、真正让它「不简单」的三处

### 1. 液态玻璃的模糊背板会把背景图漏掉（最容易翻车）

- 底栏玻璃采样的是 `rememberLayerBackdrop()` 录下来的那一层（`contentBackdrop`），
  而渐变背景**刻意没进这一层**（代码注释写着「玻璃底下也有底色可透」）。
- 换成背景图后，如果图仍留在背板之外，玻璃模糊到的只是内容层，
  透过玻璃看到的是「上一层内容」而不是背后的图 —— 玻璃会发灰、跟背景脱节。
- 修法要么把图挪进被录的那一层，要么改用 `rememberCombinedBackdrop` 重新组合两层。
  无论哪种，都要动 `PumpkinApp` 的层级，**并重新验证玻璃在高对比图片上的折射观感**。
  这是本功能的主要风险点。

### 2. 任意图片下的文字可读性

- 现在所有文字色都是照着「很暗的渐变底」定的（`text` / `textDim`）。
  换成一张亮照片，卡片外的说明文字会直接糊掉。
- 需要压暗层（scrim），可能还要让卡片底色更不透明、`textDim` 动态提亮。
  这部分是**真机调参**：每换一类测试图（亮/暗、高对比/低对比）都要重看一遍。

### 3. 图片的生命周期与内存

- 旋转、进程被杀后重进、图片被用户删除、URI 权限过期 —— 每种都要兜底
  （解码失败必须静默回落到渐变，不能崩）。
  `MainActivity` 的 `configChanges` 已含 `orientation|screenSize`，Activity 不会重建，
  但 Compose 侧仍要正确响应路径变化。
- 1080×2400 的 ARGB_8888 约 10MB；叠加玻璃的 RenderNode 录制，低端机有压力。
  用 `RGB_565` 或限制解码尺寸可缓解。

## 三、成本估算（基于本仓库已完成的同类改动实测）

| 已完成的功能 | 改动量 | CI 往返 | 真机验证方式 |
|---|---|---|---|
| 沉浸式修复 | 88 增 / 64 删，7 文件 | 1 次 | 2 张截图 + 逐像素扫描 |
| **配色功能（与本需求最可比）** | **405 增 / 52 删，8 文件** | **1 次** | **3 张截图 + 像素验证 + 1 次识图** |
| miuix 对话框 | 554 增 / 138 删，6 文件 | 3 次 | 全量弹窗回归 + 2 次识图 |

按第一节的拆解，背景图功能约 **300–450 行**（与配色功能同量级），
但多出两类这个仓库还没趟过的坑：**SAF 持久化 URI** 与 **玻璃背板采样源**。

估算：

- 代码 + 文档：约 **25k–35k token**
- 真机调试与验证（选图、重启后是否还在、玻璃观感、亮图可读性、旋转、降采样）：
  预计 3–5 轮，约 **35k–60k token**
- **合计约 60k–100k token**，2–3 次 CI 往返，且**需要人参与**
  （得有人真去相册挑图，并判断「这张图配这个界面好不好看」）。

## 四、建议

**先不做。** 理由：

1. 它不是「简单功能」。成本不在画一张图，而在玻璃背板耦合与任意图片的可读性调参 ——
   这两件事都要反复真机验证，属于「越改越久」的类型。
2. 收益与配色功能重叠。现在 6 套配色已经能换界面观感，
   而背景图在**不加压暗层时几乎一定伤可读性**，最后多半还是被压暗到接近纯色，
   那还不如直接用渐变。
3. 若只是想要「更有个性」，性价比更高的做法是：
   - **再加几套渐变配色** —— 纯数据，约 20 行一套，零风险；
   - 或做一组**内置程序化背景**（径向渐变 / 噪点 / 网格），不需要文件系统与权限。

### 如果以后确实要做，按这个顺序，每步都能独立验收

1. 先只做「选图 + 拷贝到私有目录 + 设置页显示文件名」，**图不显示**，
   只验证 SAF 与持久化权限。
2. 再把图作为纯背景画出来（强制压暗到 50%），**不动玻璃**。
3. 最后处理玻璃背板采样源，并逐张测试亮/暗、高对比/低对比图片的可读性。

这样任何一步翻车都不会连累前面的成果。
