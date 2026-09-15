// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 「设置」页：启动方式 / 下载源（含 GitHub 加速源多选）/ 清理数据 / 关于。
// 对应原 Java 版 MainActivity.buildSettingsPage()。

package com.pumpkin.server.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pumpkin.server.MirrorOption
import com.pumpkin.server.ui.PumpkinActions
import com.pumpkin.server.ui.PumpkinColors
import com.pumpkin.server.ui.PumpkinPalette
import com.pumpkin.server.ui.PumpkinPalettes
import com.pumpkin.server.ui.PumpkinUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页。 */
@Composable
fun SettingsPage(
    state: PumpkinUiState,
    actions: PumpkinActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
    ) {
        SmallTitle(text = "设置")

        // ---------------------------------------------------------------- 启动方式
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "服务端启动方式",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.modeValue,
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "普通模式开箱即用。若启动时报权限错误，切到 Root 模式再试。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { actions.showModeDialogFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("切换启动方式") }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 配色
        PaletteSection(state = state, actions = actions)

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 加速源快选
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "GitHub 加速源",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "点一下即填入下面的「下载镜像前缀」。下载失败会自动换源，选中项会排在第一位优先尝试。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 每个加速源一个按钮。选中当前镜像的项用主色高亮。
                MirrorOption.entries.forEach { opt ->
                    val selectedNow = state.mirror.trim() == opt.prefix
                    Button(
                        onClick = { actions.applyMirrorFromUi(opt.prefix) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selectedNow) {
                            ButtonDefaults.buttonColorsPrimary()
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) {
                        Text(
                            if (selectedNow) "✓ ${opt.label}" else opt.label
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 下载源
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "下载源",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "平时不用改。下载失败时会自动换源，这里可手动指定。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )

                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.apiBase,
                    onValueChange = { state.apiBase = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "API 地址，如 https://api.github.com",
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.repo,
                    onValueChange = { state.repo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "仓库，如 owner/repo",
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.mirror,
                    onValueChange = { state.mirror = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "下载镜像前缀（可留空）",
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        actions.saveSourceFromUi(state.apiBase, state.repo, state.mirror)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("保存下载源") }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 清理
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "清理数据",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { actions.clearVersionsFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("清服务端版本") }
                    Button(
                        onClick = { actions.clearGameDataFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("清游戏数据") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { actions.clearAllFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("全部清空") }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 关于
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "关于",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "版本 " + state.appVersion,
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { actions.checkAppUpdateFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("检查更新") }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { actions.openBatterySettingsFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("电池优化设置") }
                    Button(
                        onClick = { actions.copyWorkDirFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("数据目录路径") }
                }
            }
        }
    }
}

// ================================================================ 配色选择

/**
 * 配色方案选择。
 *
 * 每行放两套。色板画成「背景顶色 → 强调色」的渐变圆点，不用逐个点开就能看出每套的调子。
 * 选中项用**该配色自己的**容器色打底，这样不管当前用的是哪套主题，每个选项都显示得清楚。
 */
@Composable
private fun PaletteSection(
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    val current = PumpkinPalettes.byId(state.paletteId)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "配色",
                style = MiuixTheme.textStyles.subtitle,
                color = PumpkinColors.TextDim,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "换一套界面配色。背景渐变、卡片底色、按钮和底栏玻璃会一起跟着变。",
                style = MiuixTheme.textStyles.footnote1,
                color = PumpkinColors.TextDim,
            )
            Spacer(modifier = Modifier.height(12.dp))

            PumpkinPalettes.all.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { palette ->
                        PaletteChip(
                            palette = palette,
                            selected = palette.id == current.id,
                            onClick = { actions.applyPaletteFromUi(palette.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 配色数是奇数时补个空位，否则最后一行唯一的卡会被拉成整行宽。
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** 单个配色选项。色板用该配色自己的颜色画，与当前主题无关。 */
@Composable
private fun PaletteChip(
    palette: PumpkinPalette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.accentContainer else palette.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(palette.bgTop, palette.accent))),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = palette.name,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) palette.accent else PumpkinColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "✓",
                style = MiuixTheme.textStyles.body2,
                color = palette.accent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
