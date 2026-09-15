// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 「设置」页：一张主列表 + 四个二级页（外观 / 下载源 / 清理数据 / 关于）。
//
// 为什么做二级（SukiSU 那种做法）：设置项一多，全摊在一页里就是一条长滚动，
// 常用的（外观）和一年用一次的（下载源）混在一起，找东西得来回滚。
// 主列表只列**条目名 + 当前值**，一眼能看出现在是什么状态；
// 想改才进去。这与「能通过界面自己看出怎么用」是同一件事：
// **主列表负责回答"现在是什么"，二级页负责"改成什么"。**
//
// 界面文案的原则（这一页改过一轮）：不写"操作说明"。
// 按钮叫什么、点了会变成什么，用户自己看得见 —— "点下载开始，下载中会变成暂停"
// 这种话是在重复界面已经说清楚的事，只会让页面变吵。

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
import com.pumpkin.server.ui.LocalPumpkinDark
import com.pumpkin.server.ui.PumpkinActions
import com.pumpkin.server.ui.PumpkinColors
import com.pumpkin.server.ui.PumpkinPalette
import com.pumpkin.server.ui.PumpkinPalettes
import com.pumpkin.server.ui.PumpkinScheme
import com.pumpkin.server.ui.PumpkinUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置页当前在哪一层。与 PumpkinUiState.settingsPane 对应。 */
object SettingsPanes {
    const val MAIN = 0
    const val APPEARANCE = 1
    const val SOURCES = 2
    const val CLEANUP = 3
    const val ABOUT = 4
}

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
        when (state.settingsPane) {
            SettingsPanes.APPEARANCE -> {
                PaneHeader(title = "外观", actions = actions)
                AppearancePane(state = state, actions = actions)
            }

            SettingsPanes.SOURCES -> {
                PaneHeader(title = "下载源", actions = actions)
                SourcesPane(state = state, actions = actions)
            }

            SettingsPanes.CLEANUP -> {
                PaneHeader(title = "清理数据", actions = actions)
                CleanupPane(actions = actions)
            }

            SettingsPanes.ABOUT -> {
                PaneHeader(title = "关于", actions = actions)
                AboutPane(state = state, actions = actions)
            }

            else -> {
                SmallTitle(text = "设置")
                MainList(state = state, actions = actions)
            }
        }
    }
}

// ================================================================ 主列表

/**
 * 主列表：一行一个入口，右边显示**当前值**。
 *
 * 值就是这一页存在的意义 —— 不进去也能知道现在用的是哪套配色、哪个下载源。
 */
@Composable
private fun MainList(
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            NavRow(
                title = "外观",
                value = PumpkinPalettes.modeLabel(state.themeMode) + " · " +
                        PumpkinPalettes.nameOf(state.paletteId),
            ) { actions.setSettingsPaneFromUi(SettingsPanes.APPEARANCE) }

            NavRow(title = "下载源", value = mirrorLabel(state.mirror)) {
                actions.setSettingsPaneFromUi(SettingsPanes.SOURCES)
            }

            // 启动方式只有两个选项，直接弹对话框比再进一层快。
            NavRow(title = "服务端启动方式", value = state.modeValue) {
                actions.showModeDialogFromUi()
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            NavRow(title = "清理数据", value = "") {
                actions.setSettingsPaneFromUi(SettingsPanes.CLEANUP)
            }

            NavRow(title = "关于", value = state.appVersion) {
                actions.setSettingsPaneFromUi(SettingsPanes.ABOUT)
            }
        }
    }
}

/** 二级页的标题栏：左边一个返回箭头。 */
@Composable
private fun PaneHeader(
    title: String,
    actions: PumpkinActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { actions.setSettingsPaneFromUi(SettingsPanes.MAIN) }) {
            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
        }
        SmallTitle(text = title)
    }
}

/**
 * 一行：标题 + 右侧当前值 + 箭头。
 *
 * 用 miuix 的 BasicComponent 而不是自己拼 Row：它是「一行带主副文案 + 尾部动作」
 * 的标准组件，自带走马灯式的按下反馈和正确的行高，与对话框里的列表行是同一套。
 */
@Composable
private fun NavRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = value.ifEmpty { null },
        onClick = onClick,
        endActions = {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

/** 把镜像前缀说成人话：空 = 直连，否则去掉协议头只留域名。 */
private fun mirrorLabel(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return "官方直连"
    return t.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

// ================================================================ 外观

@Composable
private fun AppearancePane(
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    val dark = LocalPumpkinDark.current
    val current = PumpkinPalettes.byId(state.paletteId)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("深色", PumpkinPalettes.MODE_DARK, state.themeMode, actions, Modifier.weight(1f))
                ModeChip("浅色", PumpkinPalettes.MODE_LIGHT, state.themeMode, actions, Modifier.weight(1f))
                ModeChip("自动", PumpkinPalettes.MODE_AUTO, state.themeMode, actions, Modifier.weight(1f))
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            PumpkinPalettes.all.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { palette ->
                        PaletteChip(
                            palette = palette,
                            scheme = palette.scheme(dark),
                            selected = palette.id == current.id,
                            onClick = { actions.applyPaletteFromUi(palette.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 色相数是奇数时补个空位，否则最后一行唯一的卡会被拉成整行宽。
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** 明暗模式的一个选项。 */
@Composable
private fun ModeChip(
    label: String,
    mode: String,
    current: String,
    actions: PumpkinActions,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { actions.applyThemeModeFromUi(mode) },
        modifier = modifier,
        colors = if (mode == current) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        },
    ) { Text(label) }
}

/**
 * 单个色相选项。色板用**当前明暗下**该色相的颜色画
 * —— 亮色模式下看到的色板也是亮色的，所见即所得。
 */
@Composable
private fun PaletteChip(
    palette: PumpkinPalette,
    scheme: PumpkinScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) scheme.accentContainer else scheme.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(scheme.bgTop, scheme.accent))),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = palette.name,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) scheme.accent else PumpkinColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "✓",
                style = MiuixTheme.textStyles.body2,
                color = scheme.accent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ================================================================ 下载源

@Composable
private fun SourcesPane(
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "加速源",
                style = MiuixTheme.textStyles.subtitle,
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
                    Text(if (selectedNow) "✓ ${opt.label}" else opt.label)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))
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
                onClick = { actions.saveSourceFromUi(state.apiBase, state.repo, state.mirror) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("保存") }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            NavRow(
                title = "App 更新源",
                value = state.appUpdateSourceLabel.ifEmpty { "官方直连" },
            ) { actions.showAppSourceDialogFromUi() }
        }
    }
}

// ================================================================ 清理数据

@Composable
private fun CleanupPane(actions: PumpkinActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            NavRow("服务端版本", "已下载的服务端程序") { actions.clearVersionsFromUi() }
            NavRow("游戏数据", "世界存档、配置与日志") { actions.clearGameDataFromUi() }
            NavRow("全部清空", "以上两项都清掉") { actions.clearAllFromUi() }
        }
    }
}

// ================================================================ 关于

@Composable
private fun AboutPane(
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "南瓜坞 " + state.appVersion,
                style = MiuixTheme.textStyles.subtitle,
            )

            // App 更新包在应用内下载，进度就地显示 —— 这里不跳浏览器，
            // 所以必须有进度反馈，否则点完「检查更新」之后界面毫无反应。
            if (state.appUpdateVisible) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = state.appUpdateText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )
                // progress < 0 表示总大小未知（服务端没给 Content-Length）：
                // 这种时候不画进度条，免得它一直停在 0% 看着像卡死。
                if (state.appUpdateProgress >= 0f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = state.appUpdateProgress,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { actions.checkAppUpdateFromUi() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text("检查更新") }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            NavRow("电池优化设置", "") { actions.openBatterySettingsFromUi() }
            NavRow("复制数据目录路径", "") { actions.copyWorkDirFromUi() }
        }
    }
}
