// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// miuix 液态玻璃悬浮底栏（SukiSU / 新版 LSPosed 同款观感）。
//
// 配方与交互严格对齐上游实现，不再自己简化：
//   - compose-miuix-ui/miuix 官方示例
//       example/shared/src/commonMain/kotlin/component/liquid/LiquidGlassNavigationBar.kt (Apache 2.0)
//   - KernelSU / SukiSU 的 manager/.../ui/component/FloatingBottomBar.kt (Apache 2.0)
//
// 曾经为了「少一层手势状态机」把 DampedDragAnimation 拿掉，结果就是底栏完全没有动画：
// 指示器只是线性平移，没有按下放大、没有速度挤压、图标也不放大，而且背板缺少
// padding 扩展导致模糊/折射在胶囊边缘被裁掉，看着根本不像液态玻璃。
// 现在全部按官方做法接回来。
//
// 与原示例的差异仅限：
//   1. 不依赖 example 的 ui.isInDarkTheme()，改用本项目的 LocalPumpkinDark。
//   2. 底栏下方的导航条内边距由本组件自己算（原示例由 Scaffold 传入）。

package com.pumpkin.server.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.pumpkin.server.ui.liquid.InnerShadow
import com.pumpkin.server.ui.liquid.innerShadow
import com.pumpkin.server.ui.liquid.lens
import com.pumpkin.server.ui.liquid.rememberCombinedBackdrop
import com.pumpkin.server.ui.liquid.vibrancy
import com.pumpkin.server.ui.liquid.animation.DampedDragAnimation
import com.pumpkin.server.ui.liquid.animation.InteractiveHighlight
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** 按下时图标/文字的放大倍数，由选中胶囊的按压进度驱动（官方同款 1f..1.2f）。 */
private val LocalNavTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 边缘高光规格。dualPeak = 上下两个光峰，模拟玻璃棒的双折射亮边。
 * 参数与官方示例一致（iosIndicatorSpecular）。
 */
private val navSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

// 与 miuix HighlightStyle 的 LIGHT_REF 保持一致（HighlightStyle.kt）。
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

/** |gravity_xy| > 0.1，约 6° 倾斜。低于此阈值时认为手机是平的，光回到正上方。 */
private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f

/** 重力角量化步长（3°）。量化是为了避免高光在噪声下高频抖动。 */
private const val GRAVITY_ANGLE_STEP_RAD = (3.0 * PI / 180.0).toFloat()

/**
 * 让 highlight 的主光源跟着重力方向转，并可再叠加一个固定角度偏移。
 *
 * 这正是「倾斜手机 → 高光位置移动」效果的来源：
 * 重力方向决定光从哪边来，于是亮边沿着倾斜方向跑。
 *
 * @param base 基础高光规格。
 * @param extraDegrees 额外旋转角（度）。底栏用 -45f，选中胶囊用 90f，让两者亮边错开。
 */
@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float = 0f,
): Highlight {
    val baseStyle = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val magSq = gx * gx + gy * gy
        // 手机接近水平时重力在屏幕平面的投影很小，方向会抖；此时固定光在上方。
        val angle = if (magSq > GRAVITY_DIR_THRESHOLD_SQ) {
            val quantized = (atan2(gy, gx) / GRAVITY_ANGLE_STEP_RAD).roundToInt() * GRAVITY_ANGLE_STEP_RAD
            quantized + extraDegrees * PI.toFloat() / 180f
        } else {
            -PI.toFloat() / 2f + extraDegrees * PI.toFloat() / 180f
        }
        basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + cos(angle),
                y = LIGHT_REF_Y + sin(angle),
                z = basePrimary.position.z,
            ),
        )
    }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

/**
 * 液态玻璃悬浮导航栏。
 *
 * 交互（与 SukiSU 一致）：
 *   - 手指按下 → 指示器胶囊立刻弹簧到按下的那一项（`DampedDragAnimation` 同时开始放大）
 *   - 横向拖动 → 胶囊跟手滑动，底栏整体带一点橡皮筋位移
 *   - 松手     → 落到最近一项并回调 `onItemClick`；同时胶囊回弹、图标缩放复位
 *   - 外部改 `selectedIndex`（例如 Java 侧自动跳页）→ 胶囊平滑跟过去
 *
 * @param items 标签（2..5 个）。
 * @param selectedIndex 当前选中项。
 * @param onItemClick 选中项变化回调（只在松手/键盘激活时触发，不会因动画途经中间项而连发）。
 * @param backdrop 背板：由调用方用 rememberLayerBackdrop() 创建并应用在内容容器上。
 *                 传 null 时退化为不透明底栏（模糊不可用时也不会崩）。
 * @param modifier 外部修饰符。
 * @param badge 每项的角标（如「更新」页的红点）。
 */
@Composable
fun LiquidGlassNavBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    badge: (Int) -> (@Composable () -> Unit)? = { null },
) {
    val isDark = LocalPumpkinDark.current
    val pillShape = remember { CircleShape }
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    // 有模糊时用半透明容器色，玻璃才透得出来；没有模糊时不透明，保证可读。
    val isBlurEnabled = backdrop != null
    val containerColor = if (isBlurEnabled) surfaceContainer.copy(alpha = 0.4f) else surfaceContainer

    // 指示器后面的第二层背板：把「已绘制的标签行」录下来，
    // 供选中胶囊做折射采样（这样胶囊里透出的是标签本身的像，而不是背景）。
    val tabsBackdrop = rememberLayerBackdrop()
    // 胶囊最终采样的是「底栏自身玻璃 + 标签层」的合成背板，与官方一致。
    val combinedBackdrop = rememberCombinedBackdrop(backdrop ?: tabsBackdrop, tabsBackdrop)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    val tabsCount = items.size
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    // 拖到两端之外时底栏整体的橡皮筋位移，让「拖不动」有手感反馈。
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    // currentIndex 是「已提交」的选中项；指示器位置由 dampedDragAnimation 独立驱动，
    // 两者只在松手/外部改页时同步 —— 这样动画途经中间项不会误切页面。
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val onSelectedUpdated by rememberUpdatedState(onItemClick)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .coerceIn(0, tabsCount - 1)
    }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                offset.x in 0f..totalWidthPx
            },
            onDragStarted = { position ->
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onSelectedUpdated(targetIndex)
                }
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
    }

    // 外部改页（点底栏以外的入口、Java 侧自动跳页）时把指示器同步过去。
    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            dampedDragAnimation.animateToValue(selectedIndex.toFloat())
        }
    }

    /** 键盘 / 无障碍激活某一项。 */
    fun activateTab(index: Int) {
        if (index !in 0 until tabsCount) return
        if (currentIndex != index) {
            currentIndex = index
            onSelectedUpdated(index)
        }
        dampedDragAnimation.animateToValue(index.toFloat())
    }

    // 按下时胶囊位置跟手发一圈柔光（官方 InteractiveHighlight）。
    val interactiveHighlight = remember(animationScope, tabWidthPx, dampedDragAnimation) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) {
                        (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    } else {
                        size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    },
                    size.height / 2f,
                )
            },
        )
    }

    val baseHighlight = rememberGravityRotatedHighlight(navSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(navSpecular, extraDegrees = 90f)

    val navBarBottomPadding = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val bottomPaddingValue = if (navBarBottomPadding != 0.dp) {
        8.dp + navBarBottomPadding
    } else {
        36.dp
    }

    val tabsContent: @Composable RowScope.((Int) -> Unit) -> Unit = { activate ->
        val tabScale = LocalNavTabScale.current
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        selected = index == selectedIndex
                        role = Role.Tab
                        onClick {
                            activate(index)
                            true
                        }
                    }
                    .onKeyEvent { event ->
                        val activationKey = event.key == Key.Enter ||
                            event.key == Key.NumPadEnter || event.key == Key.Spacebar
                        if (activationKey) {
                            if (event.type == KeyEventType.KeyUp) activate(index)
                            true
                        } else {
                            false
                        }
                    }
                    .focusable()
                    .fillMaxHeight()
                    .weight(1f)
                    .graphicsLayer {
                        val s = tabScale()
                        scaleX = s
                        scaleY = s
                    },
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BadgedBox(badge = { badge(index)?.invoke() }) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        imageVector = item.icon,
                        // 装饰性图标：旁边的文字已经说明了含义，避免读屏重复播报。
                        contentDescription = null,
                    )
                }
                Text(
                    text = item.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomPaddingValue, start = 24.dp, end = 24.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                Row(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            totalWidthPx = coords.size.width.toFloat()
                            val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                            tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                        }
                        .selectableGroup()
                        .graphicsLayer { translationX = panelOffset }
                        .dropShadow(
                            shape = pillShape,
                            shadow = Shadow(
                                radius = 10.dp,
                                color = Color.Black,
                                alpha = if (isDark) 0.2f else 0.1f,
                            ),
                        )
                        .then(
                            if (backdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { pillShape },
                                    effects = {
                                        // 关键：给背板留出模糊溢出空间。不设这个值，
                                        // 模糊与折射会在胶囊边缘被裁掉，玻璃感直接消失。
                                        padding = maxOf(padding, 40.dp.toPx())
                                        vibrancy()
                                        blur(4.dp.toPx(), 4.dp.toPx())
                                        lens(
                                            refractionHeight = 24.dp.toPx(),
                                            refractionAmount = 24.dp.toPx(),
                                        )
                                    },
                                    highlight = { baseHighlight.copy(alpha = 0.75f) },
                                    // 按下时整体轻微放大，形成「玻璃被压」的手感。
                                    layerBlock = {
                                        val w = size.width.coerceAtLeast(1f)
                                        val s = lerp(
                                            1f,
                                            1f + 16.dp.toPx() / w,
                                            dampedDragAnimation.pressProgress,
                                        )
                                        scaleX = s
                                        scaleY = s
                                    },
                                    onDrawSurface = { drawRect(containerColor) },
                                )
                            } else {
                                Modifier.background(containerColor, pillShape)
                            },
                        )
                        .then(
                            if (isBlurEnabled) {
                                interactiveHighlight.modifier
                                    .then(interactiveHighlight.gestureModifier)
                            } else {
                                Modifier
                            },
                        )
                        .then(dampedDragAnimation.modifier)
                        .height(64.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = { tabsContent(::activateTab) },
                )
            }

            // 第二层：把标签行以 alpha=0 录进 tabsBackdrop，供选中胶囊折射采样。
            // 同时它也画一遍玻璃 —— 与官方一致，这一遍是为了让背板里带上玻璃本身的像。
            if (backdrop != null) {
                CompositionLocalProvider(
                    LocalNavTabScale provides {
                        lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                    },
                    LocalContentColor provides accentColor,
                ) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .graphicsLayer { translationX = panelOffset }
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    lens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx(),
                                    )
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                            .then(interactiveHighlight.modifier)
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = { tabsContent(::activateTab) },
                    )
                }
            }

            if (tabWidthPx > 0f) {
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                if (isBlurEnabled) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val progressOffset = dampedDragAnimation.value * tabWidthPx
                                translationX = if (isLtr) {
                                    progressOffset + panelOffset
                                } else {
                                    -progressOffset + panelOffset
                                }
                            }
                            .drawBackdrop(
                                backdrop = combinedBackdrop,
                                shape = { pillShape },
                                effects = {
                                    val progress = dampedDragAnimation.pressProgress
                                    lens(
                                        refractionHeight = 10.dp.toPx() * progress,
                                        refractionAmount = 14.dp.toPx() * progress,
                                        depthEffect = true,
                                        chromaticAberration = 0.5f,
                                    )
                                },
                                highlight = {
                                    pillHighlight.copy(alpha = dampedDragAnimation.pressProgress)
                                },
                                // 缩放 + 速度挤压：拖动越快，胶囊被拉得越扁，松手回弹。
                                layerBlock = {
                                    scaleX = dampedDragAnimation.scaleX
                                    scaleY = dampedDragAnimation.scaleY
                                    val velocity = dampedDragAnimation.velocity / 10f
                                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                },
                                onDrawSurface = {
                                    val progress = dampedDragAnimation.pressProgress
                                    // 未按下时是一层很淡的选中底色，按下时淡出交给折射。
                                    drawRect(
                                        color = if (isDark) {
                                            Color.White.copy(alpha = 0.1f)
                                        } else {
                                            Color.Black.copy(alpha = 0.1f)
                                        },
                                        alpha = 1f - progress,
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                },
                            )
                            .innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 8.dp * dampedDragAnimation.pressProgress,
                                    color = Color.Black.copy(alpha = 0.15f),
                                    alpha = dampedDragAnimation.pressProgress,
                                )
                            }
                            .height(56.dp)
                            .width(tabWidthDp),
                    )
                } else {
                    // 无模糊降级：纯色选中块，位置同样由 dampedDragAnimation 驱动。
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val progressOffset = dampedDragAnimation.value * tabWidthPx
                                translationX = if (isLtr) {
                                    progressOffset + panelOffset
                                } else {
                                    -progressOffset + panelOffset
                                }
                            }
                            .background(accentColor.copy(alpha = 0.15f), pillShape)
                            .height(56.dp)
                            .width(tabWidthDp),
                    )
                }
            }
        }
    }
}
