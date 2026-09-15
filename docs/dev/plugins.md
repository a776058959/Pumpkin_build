# 插件系统

> 目的：以后加新功能优先做成插件，而不是往主工程里塞代码。
>
> 使用者视角的说明在 [README.md](../../README.md) 和 [ANDROID.md](../../ANDROID.md)；
> 这里讲**怎么给自己的需求写一个插件**，以及这套东西为什么长这样。

插件在 App 里有**自己的一页**（底栏第 3 项），页面分两段：
「已安装」（默认，管理已装插件 + 改它们声明的设置）和「商店」（浏览、安装、更新）。

---

## 一、五分钟写一个插件

下面每一步都可以照抄。目标：让运行页控制台的字号跟着一个插件设置变。

### 1. 建模块目录

```
android-app/plugins/<模块名>/
    plugin.json                 元数据（商店索引的唯一来源）
    build.gradle.kts            构建脚本
    src/main/kotlin/<包名>/XxxPlugin.kt
```

### 2. `build.gradle.kts`

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")   // 纯 Kotlin/JVM，不是 Android 库
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 必须是 compileOnly。改成 implementation 会让插件加载时 instanceof 静默失败，
    // 理由见本文「为什么必须 compileOnly」。
    compileOnly(project(":plugin-api"))
}
```

### 3. `plugin.json`

```json
{
  "id": "my-plugin",
  "name": "我的插件",
  "version": "1.0",
  "description": "一句话说明，会显示在商店和已安装列表里。",
  "entry": "com.example.myplugin.MyPlugin"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | ✅ | 唯一标识。**同时是私有目录名和偏好键的命名空间**，所以只用小写字母、数字、连字符。改了 id = 换了一个插件，老安装不会被继承 |
| `name` | ✅ | 显示名 |
| `version` | ✅ | 纯展示用。商店靠它判断「已安装 / 可更新」，所以要手动维护 |
| `description` | ✅ | 一句话说明 |
| `entry` | ✅ | 入口类的**全限定名**，运行时反射按这个名字实例化 |
| `dex` | ❌ | **不要写**。CI 按模块名派生 `plugin-<模块名>.dex` 并填进索引 |

### 4. 入口类

```kotlin
package com.example.myplugin

import com.pumpkin.plugin.PluginHost
import com.pumpkin.plugin.PluginKeys
import com.pumpkin.plugin.PluginSetting
import com.pumpkin.plugin.PumpkinPlugin

class MyPlugin : PumpkinPlugin {

    override val id: String = "my-plugin"
    override val name: String = "我的插件"
    override val version: String = "1.0"
    override val description: String = "一句话说明。"

    // 被加载时调用一次。耗时的事放这里，别放构造函数。
    override fun onLoad(host: PluginHost) {
        host.log("我的插件已加载")

        host.declareSetting(
            PluginSetting(
                key = PluginKeys.CONSOLE_FONT_SCALE,
                title = "字号倍率",
                kind = PluginSetting.Kind.TEXT,
                summary = "1 = 内置字号。",
                placeholder = "1.5",
            ),
        )
    }
}
```

> 入口类必须 **public 且有无参构造函数** —— 宿主是用 `Class.getDeclaredConstructor().newInstance()`
> 按 `plugin.json` 里的 `entry` 实例化的。

### 5. 注册模块

`android-app/settings.gradle.kts`：

```kotlin
include(":plugins:my-plugin")
```

就这些。**CI 不用改**：它会遍历 `plugins/` 下所有模块，各自编 jar → D8 转 dex →
汇总 `plugins.json` → 覆盖发布到 `plugins` 那个 Release。
本地跑 `tools/build-local.ps1` 也一样。

---

## 二、API 参考

全部 API 都在 `plugin-api` 模块（包名 `com.pumpkin.plugin`）里，一共四个类型。

### `PumpkinPlugin` —— 插件入口

| 成员 | 类型 | 说明 |
|---|---|---|
| `id` | `String` | 与 `plugin.json` 的 `id` 一致 |
| `name` | `String` | 显示名 |
| `version` | `String` | 展示用 |
| `description` | `String` | 一句话说明 |
| `onLoad(host: PluginHost)` | 方法 | 被加载时调用一次。声明设置项、写覆盖板、打日志都在这里 |

### `PluginHost` —— 插件能对宿主做的事

| 方法 | 说明 |
|---|---|
| `log(message: String)` | 写日志。会显示在「已安装」段的日志区，**格式是 `[插件id] 内容`** |
| `declareSetting(setting: PluginSetting)` | 声明一个设置项，控件由宿主渲染 |
| `set(key: String, value: String?)` | 写覆盖板；`null` 表示清除这一项 |
| `get(key: String): String?` | 读自己之前写进覆盖板的值；没写过是 `null` |

**插件能做的只有这四件事。** 它拿不到 `Context`、拿不到 Activity、改不了宿主的内部状态 ——
这是刻意的：接口因此能长期稳定，插件也不可能把 App 弄崩。

### `PluginSetting` —— 声明一个设置项

| 字段 | 类型 | 说明 |
|---|---|---|
| `key` | `String` | 覆盖板里的键，见 `PluginKeys`。约定用 `<区域>.<名字>` 形式避免撞车 |
| `title` | `String` | 控件标题 |
| `kind` | `Kind` | `TEXT`（单行输入）或 `TOGGLE`（开关，值为 `"true"`/`"false"`） |
| `defaultValue` | `String` | 用户没设置过时的值。**必须与宿主的内置默认一致**，见下 |
| `summary` | `String` | 控件下方的说明文字 |
| `placeholder` | `String` | `TEXT` 时的输入框提示 |

#### `defaultValue` 的约定（踩过坑）

它**同时是界面上的显示值和宿主的实际行为**，所以必须写对：

- `TOGGLE` 只有 `"true"` 算开，其余（含默认的 `""`）都算关 —— 开关类设置项**一定要显式写这个字段**。
- 写反了的后果不是「不好看」，而是**误导用户**：一个宿主默认开着的开关，
  如果 `defaultValue` 留空，界面会显示成「关」；用户点一下想打开，实际写进去的是
  `"true"` —— 也就是**打开**，看起来「点了没反应」；再点一下才关。
  实测就是这么错的（「停止前确认」插件第一版）。

### `PluginKeys` —— 覆盖板的键清单

**这是插件与宿主之间唯一的契约面。** 宿主在哪里读了这些键，插件就能影响哪里；
没在这份清单里的键写了也没人读（不报错，只是没效果）。

| 常量 | 值 | 宿主在哪里读 | 取值约定 |
|---|---|---|---|
| `CONSOLE_FONT_SCALE` | `console.fontScale` | `MainActivity.render()` → `RunPage` 的日志字号 | 浮点数，基准 10.5sp。**越界或解析失败一律夹回 1**（当作没覆盖），不是夹到边界 |
| `CONFIRM_STOP` | `run.confirmStop` | `MainActivity.stopServerFromUi()` | **只有 `"false"` 才关掉确认**；其它任何值（含解析不出来的）都当作要确认 |

加一个新键时要同时做三件事：

1. 在 `PluginKeys` 加常量，写清楚「值长什么样、非法值怎么办」；
2. 在宿主读它的地方做**兜底**（插件写进来的可能是任意字符串）；
3. 更新上面这张表。

**安全相关的开关一律「默认安全，出错也安全」**：像 `CONFIRM_STOP` 这种，
只有明确写了 `"false"` 才关，解析不出来就回落到安全的一侧。

---

## 三、目录与文件

### 宿主侧（App 私有目录，一般不用手改）

```
filesDir/plugins/<插件id>/
    plugin.json    {"id":"...","entry":"..."}     ← 安装时由宿主根据索引生成
    plugin.dex     插件代码                        ← 从 Release 下载
    oat/           DexClassLoader 的优化产物
```

### 仓库侧

```
plugin-api/                     稳定接口：PumpkinPlugin / PluginHost / PluginSetting / PluginKeys
plugins/<模块名>/                插件本体
    plugin.json                  id / name / version / description / entry
    build.gradle.kts             纯 Kotlin/JVM + compileOnly(project(":plugin-api"))
    src/main/kotlin/...
```

### 商店索引 `plugins.json`（发布在 tag `plugins` 的 Release 里）

```json
{
  "version": 1,
  "plugins": [
    {
      "id": "console-font",
      "name": "控制台字号",
      "version": "1.0",
      "description": "调整运行页控制台里日志文字的大小（0.5~3 倍）。",
      "entry": "com.pumpkin.plugin.consolefont.ConsoleFontPlugin",
      "dex": "plugin-console-font.dex"
    }
  ]
}
```

索引由 CI（或 `tools/build-local.ps1`）从各插件模块的 `plugin.json` 汇总生成，
`dex` 字段是构建侧按模块名派生并**校验附件确实存在**的。

---

## 四、生命周期：什么时候发生什么

| 时机 | 发生了什么 |
|---|---|
| App 启动 | 扫描 `filesDir/plugins/*/`，**逐个 try/catch** 加载；坏插件只影响它自己，错误原文进日志 |
| 加载一个插件 | 读 `plugin.json` → 检查 `plugin.dex` → `DexClassLoader` → 按 `entry` 反射实例化 → `instanceof` 校验 → `onLoad(host)` |
| `declareSetting` | 收集进设置项列表（**按 key 去重，后声明的赢**），并把 key 记进「这个插件拥有哪些键」 |
| 「重新加载插件」 | 整个 `loadAll()` 重来一遍 |
| 停用 | **不加载它的代码**（不进 dex、不声明设置项），但它写过的覆盖值**仍然生效** |
| 卸载 | 删目录 + 清掉它拥有过的所有覆盖键 + 清掉记录的版本号 |
| 卸载后重装 | 从商店重新下载，`enabled` 会被重置成 true |

几个刻意的选择：

- **停用 ≠ 还原。** 停用是停代码，不是把用户已经调好的值改回去 ——
  用户调好的东西不该被悄悄动。
- **卸载才连覆盖一起清。** 不然「插件卸了、设置还在生效，而且再也清不掉」。
- **覆盖键的归属在 `declareSetting` 时就登记。** 设置项的值是**用户**在界面上写的，
  不经过 `PluginHost.set()`；只在 `set()` 里登记的话，卸载时这些键就没人认领（踩过）。

---

## 五、为什么必须 `compileOnly`（这条不能改）

```java
new DexClassLoader(dexPath, optDir, null, ctx.getClassLoader())
                                                  ^^^^^^^^^^^^^^^^^^
```

**父加载器是宿主自己的类加载器**，所以插件里的 `PumpkinPlugin` / `PluginHost`
会解析到 App dex 里的那一份。于是：

- `instanceof PumpkinPlugin` 正常（两个类加载器各持一份同名类时，这个判断会**静默失败**）；
- 宿主传给插件的 `PluginHost` 实例，与插件期望的类型是同一个类。

**推论**：插件不能把接口打进自己的 dex，必须 `compileOnly` 依赖 `plugin-api`。
CI 有一道必过的断言挡这件事（`apk-only.yml` 里 grep jar 的条目）：

```
::error::插件 X 的 jar 里混进了 plugin-api 的类（PumpkinPlugin/PluginHost）。
::error::这些类只能由 App 提供，插件必须用 compileOnly 依赖，否则插件的 instanceof 会静默失败。
```

本地构建（`tools/build-local.ps1`）有同一道断言。

---

## 六、R8 的两处硬约束（改混淆配置前必读）

1. **`com.pumpkin.plugin.**` 必须 `-keep`（不是 `-keepnames`）**
   —— 插件按**原始签名**调用这些接口，被改名或删掉就是全部插件加载失败。
   注意 `-keep interface` 只保接口：`PluginSetting` / `PluginKeys` 是类，得单独写。
   现成规则在 `app/proguard-rules.pro`，照抄即可，别删。

2. **Kotlin 标准库必须 `-keep class kotlin.** { *; }`**
   —— 插件是**单独编译、没过 R8** 的 dex，按原名引用标准库；
   而 `proguard-android-optimize` 会重打包并改名，插件就找不到 `Intrinsics` 了。
   代价：APK 从 1181KB 涨到 1745KB，标准库也不再被内联。
   替代方案是把标准库塞进每个插件（每个多 1MB+），插件一多就不划算。

---

## 七、分发：固定 tag 的 Release，**不需要 root**

```
Release（tag = plugins，prerelease）
    plugins.json          商店索引
    plugin-<模块名>.dex    各插件的代码
```

App 侧链路：`PluginManager.fetchStore()` → `UpdateClient.releaseAssetUrl(STORE_TAG, ...)`
→ `fetchText` / `downloadTo` —— 与 App 自更新**共用同一套多源回退**，国内网络也能装。

三个必须这样做的理由：

- **用固定 tag，不用「最新 Release」**：最新那个通常是服务端构建，没有插件附件。
- **发成 prerelease**：这样它不会变成仓库的「Latest release」，
  既不影响 App 自更新找 `.apk`，也不影响服务端版本列表。
- **全程只写 App 自己的私有目录**，所以装插件**不需要 root、也不需要存储权限**。
  这一点是「没 root 也能装插件」的全部实现 —— 真机验证过：安装后
  `files/plugins/<id>` 的属主是 App 自己（`u0_a29`），不是 root。

### 手动发一次插件（不发 App 时）

CI 的 `apk-only.yml` 勾 `publish` 会连 App 一起发。只发插件可以直接调 API 覆盖附件，
或本地构建后：

```powershell
powershell -File tools/build-local.ps1
# 然后用 gh 或 REST API 把 out\*.dex 与 out\plugins.json 覆盖上传到 tag=plugins 的 Release
```

---

## 八、调试

**唯一入口是「插件」页 →「已安装」段的日志区**（`PluginManager.logText()`）。
插件的 `host.log()` 和宿主的加载结果都写在这里，格式是 `[插件id] 内容`。

真机工具（见 [local-build.md](local-build.md) 的用法）：

| 脚本 | 用途 |
|---|---|
| `tools/verify-plugin-crud.sh` | 增删改查全流程，**29 项断言**一条命令跑完（两个示例插件各自的形态都覆盖） |
| `tools/verify-stop-confirm.sh` | 「停止前确认」的**行为**验证（12 项）：确认框到底弹不弹、服务端是不是真的停了。需要服务端在跑 |
| `tools/plugin-reset.sh` | 把插件状态清干净。**跑验证脚本前先跑**，否则会带着上一轮的残留，断言全乱套 |
| `tools/dump-ui.sh` | 界面 →「文字 @ 中心坐标」，算点击位置 |
| `tools/install-server-for-test.sh` | 把推上去的服务端二进制装成 App 的一个版本（测试用旁路，真实用户走 App 下载流程） |

写设备侧验证脚本时踩过的三个坑，写新脚本时注意：

1. **清理脚本失败要让人看见。** 第一版把重置脚本的路径写错了（写 `plugin-reset.sh`，
   实际叫 `reset.sh`），又被 `>/dev/null 2>&1` 吃掉了报错 —— 于是带着上一轮的残留插件
   在跑，一半断言失败，**看起来像功能坏了**。现在是：失败就 `bad` 并立刻退出。
2. **判「服务端在不在跑」要认「运行中」，不能认「未运行」。**
   状态文字有三种：`未运行` / `运行中 0:54` / `已停止（退出码 0）`。
   找「未运行」的话，**停完之后也会被判定成还在跑**。
3. **停服是异步的，不能固定 sleep 就断言。** 服务端要存世界，实测几十秒。
   用轮询等（`wait_stopped`），别写 `sleep 5`。

### 常见错误对照表（都是实际见过的）

| 日志里看到 | 原因 | 怎么办 |
|---|---|---|
| `Failed resolution of: Lkotlin/jvm/internal/Intrinsics;` | R8 把 Kotlin 标准库重打包改名了 | 确认 `proguard-rules.pro` 里有 `-keep class kotlin.** { *; }` |
| `No interface method xxx()L...; in class Lcom/pumpkin/plugin/PluginHost;` | 插件是按**更新的** `plugin-api` 编译的，而手机上装的 App 比较旧 | 更新 App。这条其实是个好信号：说明类型解析是通的，只是接口对不上 |
| `xxx 没有实现 PumpkinPlugin` | 插件把 plugin-api 的类打进自己 dex 了（两个类加载器各一份） | 检查 `compileOnly`；CI 的断言应该先拦下来 |
| `缺少 plugin.json` / `缺少 plugin.dex` | 目录里有半截文件 | 卸载重装。安装过程被杀掉会留下半截 dex |
| `建不了插件目录 ...（插件目录不可写）` | `files/plugins` 的属主是 root —— 以前用 adb/root 往里推过文件 | 用 root 删掉整个 `files/plugins`，App 会自己重建（属主就是自己） |
| 插件加载了，但设置项没出现 | 忘了 `declareSetting`，或者它抛异常了 | 看日志；`declareSetting` 要在 `onLoad` 里调 |
| 设置项出现了，但改了没反应 | 那个 key 没人在读 | 对照上面「`PluginKeys` 覆盖板的键清单」 |

---

## 九、约束与已知限制

**硬约束**

- 插件是**纯 Kotlin/JVM**：不能用 Android API、不能用 Compose、不能有资源文件。
  想要 UI 就 `declareSetting`，控件由宿主画。
- 入口类 public + 无参构造。
- `compileOnly(project(":plugin-api"))`，不能改。
- 插件跑在 **App 的 uid 里**，能做的只有 `PluginHost` 那四个方法。

**已知限制**

- **加载过一次的插件卸不干净**：ClassLoader 不能卸载，重新加载只是多一份 dex 常驻内存。
  删文件 + 不再加载能让它下次启动不出现，但本次进程里那份 dex 还在。插件很小，暂不处理。
- **插件之间抢同一个键**：后声明的生效，且设置项只保留一个（不会两个控件打架）。
- **商店索引没有签名校验**：只是 HTTPS 上一个明文 JSON。当前信任级别等同于 App 自更新
  （同一个仓库的 Release）。要更严得给 dex 加签名，还没做。
- **插件不能改系统行为**。比如改不了手机的真实局域网 IP —— 那是路由器 DHCP /
  系统 WiFi 静态设置的事，没有对应 API（早先真做过一个这样的插件，结论是做不到，删了）。

---

## 十、现有插件（当模板看）

| 插件 | 目录 | 演示了什么 |
|---|---|---|
| 控制台字号 | `plugins/console-font/` | 最小的插件；`TEXT` 设置项；改**显示** |
| 停止前确认 | `plugins/confirm-stop/` | `TOGGLE` 设置项；改**行为**（真的会让「停止」不再弹确认） |

两个加起来不到 100 行，覆盖了插件的两种形态。写新插件时直接抄其中一个。
