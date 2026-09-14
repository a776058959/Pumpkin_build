// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// GitHub 加速源清单。放这里是为了让 Kotlin(UI) 与 Java(UpdateClient) 共用同一份定义，
// 避免两处各写一份、改一处漏一处。

package com.pumpkin.server;

/**
 * 一个 GitHub 加速前缀。
 *
 * 用法：下载地址前拼上前缀即可，例如
 * `https://ghfile.geekertao.top/` + `https://github.com/o/r/releases/download/...`。
 *
 * 空前缀表示直连。
 */
enum class MirrorOption(
    /** 界面上显示的名字。 */
    val label: String,
    /** 前缀（含结尾斜杠）。空串 = 直连。 */
    val prefix: String,
    /** 备注，显示在设置页。 */
    val note: String,
) {
    // 顺序即「优先尝试」顺序。空串(直连)永远排第一，符合原来「直连优先」的策略。

    /** 直连，不加速。 */
    DIRECT("直连（不加速）", "", "从 GitHub 官方拉取，国内有时很慢或连不上"),

    /**
     * 用户提供的加速源，2026-09 实测可用且支持 Range 断点续传。
     */
    GEEKERTAO(
        "ghfile.geekertao.top",
        "https://ghfile.geekertao.top/",
        "实测可用，支持断点续传",
    ),

    /** ghfast.top，实测可用，支持 Range。 */
    GHFAST("ghfast.top", "https://ghfast.top/", "实测可用，支持断点续传"),

    /** ghproxy.net，实测可用，支持 Range。 */
    GHPROXY_NET("ghproxy.net", "https://ghproxy.net/", "实测可用，支持断点续传"),

    /** gh.llkk.cc，实测可用，支持 Range。 */
    LLKK("gh.llkk.cc", "https://gh.llkk.cc/", "实测可用，支持断点续传"),

    /** hk.gh-proxy.com，实测可用，支持 Range。 */
    HK_GHPROXY("hk.gh-proxy.com", "https://hk.gh-proxy.com/", "实测可用，支持断点续传"),

    /** gh.jasonzeng.dev，实测可用，支持 Range。 */
    JASONZENG("gh.jasonzeng.dev", "https://gh.jasonzeng.dev/", "实测可用，支持断点续传"),

    /** gh-proxy.net，实测可用但不返回 206，断点续传会退化为重新下载。 */
    GHPROXY_NET_ALT(
        "gh-proxy.net",
        "https://gh-proxy.net/",
        "可用，但不支持断点续传（暂停后继续会重新下载）",
    );

    companion object {
        /**
         * 内置尝试顺序（含空串直连）。
         *
         * 供 UpdateClient 在「用户没指定镜像」时按序回退。
         */
        @JvmStatic
        fun builtinPrefixes(): Array<String> = entries.map { it.prefix }.toTypedArray()

        /** 按前缀找选项；找不到（用户自定义）返回 null。 */
        @JvmStatic
        fun fromPrefix(prefix: String?): MirrorOption? {
            if (prefix == null) return null
            val p = prefix.trim()
            return entries.firstOrNull { it.prefix == p }
        }
    }
}
