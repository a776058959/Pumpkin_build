// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// miuix 液态玻璃悬浮底栏（SukiSU / 新版 LSPosed 同款观感）。
//
// 配方来源：compose-miuix-ui/miuix 官方示例
//   example/shared/src/commonMain/kotlin/component/liquid/LiquidGlassNavigationBar.kt
//   (Apache 2.0)，并参考 KernelSU 的 manager/.../ui/component/FloatingBottomBar.kt。
//
// 与原示例的差异：
//   1. 去掉阻尼拖拽（DampedDragAnimation）——本壳只有 3 个标签，点击切换足够，
//      少一层手势状态机就少一处崩溃面。指示器改用它自带的 Animatable 滑动。
//   2. 倾斜高光直接用 miuix 的 rememberTiltLight，不再自己接加速度计。
//   3. 不再依赖 example 的 ui.isInDarkTheme()，改用本项目的 LocalPumpkinDark。

package com.pumpkin.server.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.pumpkin.server.ui.liquid.InnerShadow
import com.pumpkin.server.ui.liquid.innerShadow
import com.pumpkin.server.ui.liquid.lens
import com.pumpkin.server.ui.liquid.vibrancy
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

/**
 * 让 highlight 的主光源跟着重力方向转，并可再叠加一个固定角度偏移。
 *
 * 这正是「倾斜手机 → 高光位置移动」效果的来源：
 * 重力方向决定光从哪边来，于是亮边沿着倾斜方向跑。
 *
 * @param base 基础高光规格。
 * @param extraDegrees 额外旋转角（度）。指示器胶囊用 90f，让它与底栏亮边错开。
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
        val (lx0, ly0) = if (magSq > GRAVITY_DIR_THRESHOLD_SQ) {
            val inv = 1f / sqrt(magSq)
            (gx * inv) to (gy * inv)
        } else {
            0f to -1f
        }
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val lx = c * lx0 - s * ly0
        val ly = s * lx0 + c * ly0
        basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + lx,
                y = LIGHT_REF_Y + ly,
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
 * @param items 标签（2..5 个）。
 * @param selectedIndex 当前选中项。
 * @param onItemClick 点击回调。拖动指示器经过的项也会回调（与原 Java 版 moveIndicator 行为一致）。
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
    val isBlurActive = backdrop != null
    val containerColor = if (isBlurActive) surfaceContainer.copy(alpha = 0.4f) else surfaceContainer

    // 指示器后面的第二层背板：把「已绘制的标签行」录下来，
    // 供选中胶囊做折射采样（这样胶囊里透出的是标签本身的像，而不是背景）。
    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    val tabsCount = items.size
    var tabWidthPx by remember { mutableFloatStateOf(0f) }

    // 指示器位置（以「第几项」为单位，支持小数以便平滑滑动）。
    val indicator = remember { Animatable(selectedIndex.toFloat()) }

    // 按下反馈：按住时选中胶囊做折射 + 描边高光，松开回弹。
    val barInteraction = remember { MutableInteractionSource() }
    val pressed by barInteraction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(1f, 400f, 0.5f),
        label = "navPressProgress",
    )

    // 单向数据流：页面归属由父级 (state.page) 决定，底栏只负责「把指示器滑过去」。
    //
    // 曾经写成 snapshotFlow { indicator.value } 来触发切换 —— 那是错的：
    // 动画途中会依次经过中间项，导致点击第 3 项时先切到第 2 页再切到第 3 页（画面闪跳）。
    LaunchedEffect(selectedIndex) {
        indicator.animateTo(selectedIndex.toFloat(), spring(1f, 300f, 0.5f))
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

    val tabsContent: @Composable RowScope.() -> Unit = {
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onItemClick(index) },
                    )
                    .semantics { selected = index == selectedIndex }
                    .weight(1f)
                    .fillMaxHeight(),
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
                        .selectableGroup()
                        .onSizeChanged { coords ->
                            val w = coords.width.toFloat()
                            val contentWidthPx = w - with(density) { 8.dp.toPx() }
                            tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                        }
                        .clickable(
                            interactionSource = barInteraction,
                            indication = null,
                            onClick = {},
                        )
                        .dropShadow(
                            shape = pillShape,
                            shadow = Shadow(
                                radius = 10.dp,
                                color = Color.Black,
                                alpha = 0.2f,
                            ),
                        )
                        .then(
                            if (isBlurActive) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop!!,
                                    shape = { pillShape },
                                    effects = {
                                        vibrancy()
                                        blur(4.dp.toPx(), 4.dp.toPx())
                                        lens(
                                            refractionHeight = 24.dp.toPx(),
                                            refractionAmount = 24.dp.toPx(),
                                        )
                                    },
                                    highlight = { baseHighlight.copy(alpha = 0.75f) },
                                    layerBlock = {
                                        val w = size.width.coerceAtLeast(1f)
                                        val s = lerp(1f, 1f + 16.dp.toPx() / w, pressProgress)
                                        scaleX = s
                                        scaleY = s
                                    },
                                    onDrawSurface = { drawRect(containerColor) },
                                )
                            } else {
                                Modifier.background(containerColor, pillShape)
                            },
                        )
                        .height(64.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }

            // 第二层：把标签行以 alpha=0 录进 tabsBackdrop，供选中胶囊折射采样。
            if (isBlurActive) {
                CompositionLocalProvider(
                    LocalContentColor provides accentColor,
                ) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tabsContent,
                    )
                }
            }

            if (tabWidthPx > 0f) {
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                if (isBlurActive) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val offset = indicator.value * tabWidthPx
                                translationX = if (isLtr) offset else -offset
                            }
                            .drawBackdrop(
                                backdrop = tabsBackdrop,
                                shape = { pillShape },
                                effects = {
                                    lens(
                                        refractionHeight = 10.dp.toPx() * pressProgress,
                                        refractionAmount = 14.dp.toPx() * pressProgress,
                                        depthEffect = true,
                                        chromaticAberration = 0.5f,
                                    )
                                },
                                highlight = { pillHighlight.copy(alpha = pressProgress) },
                                onDrawSurface = {
                                    // 未按下时是一层很淡的白色底（选中块），按下时淡出交给折射。
                                    drawRect(
                                        color = if (isDark) {
                                            Color.White.copy(alpha = 0.1f)
                                        } else {
                                            Color.Black.copy(alpha = 0.1f)
                                        },
                                        alpha = 1f - pressProgress,
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.03f * pressProgress))
                                },
                            )
                            .innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 8.dp * pressProgress,
                                    color = Color.Black.copy(alpha = 0.15f),
                                    alpha = pressProgress,
                                )
                            }
                            .height(56.dp)
                            .width(tabWidthDp),
                    )
                } else {
                    // 无模糊降级：纯色选中块。
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val offset = indicator.value * tabWidthPx
                                translationX = if (isLtr) offset else -offset
                            }
                            .clip(pillShape)
                            .background(accentColor.copy(alpha = 0.15f), pillShape)
                            .height(56.dp)
                            .width(tabWidthDp),
                    )
                }
            }
        }
    }
}
