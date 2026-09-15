# 插件系统

> 目的：以后加新功能优先做成插件，而不是往主工程里塞代码。
> 使用者视角的说明在 [README.md](../../README.md)；这里只讲怎么改、为什么这么设计。

插件在 App 里有**自己的一页**（底栏第 3 项），那一页把插件的增删改查全包了：
从商店装、启用/停用、卸载、改插件声明的设置项、看插件日志。

## 架构（四个决定，都是有原因的）

### 1. 插件是真代码：Kotlin 编译成 dex，运行时用 `DexClassLoader` 加载

```java
new DexClassLoader(dexPath, optDir, null, ctx.getClassLoader())
                                                  ^^^^^^^^^^^^^^^^^^
```

**父加载器必须是 App 自己的类加载器。** 这样插件里的 `PumpkinPlugin` / `PluginHost`
会解析到 App dex 里的那一份，于是：

- `instanceof PumpkinPlugin` 正常（两个类加载器各持一份同名类时，这个判断会静默失败）；
- App 传给插件的 `PluginHost` 实例，与插件期望的类型是同一个类；
- **推论**：插件必须用 `compileOnly` 依赖 `plugin-api`，绝不能把接口打进自己的 dex。
  CI 有一道断言挡这件事。

### 2. 插件不碰 App 内部状态，只写「覆盖板」+ 声明设置项

插件做两件事：

- `host.declareSetting(...)` —— 声明一个设置项，**控件由 App 渲染**
  （所以插件不必依赖 Compose，编译产物只有几 KB）；
- `host.set(key, value)` —— 往覆盖板写**约定好的键**（见 `PluginKeys`），
  App 在渲染与行为处读这些键。

好处：**「插件能改什么」是一份显式清单**；插件写坏了最多某个值不对，不会把 App 弄崩。
每加一个可覆盖项，就往 `PluginKeys` 加一个常量，并在 App 对应渲染处读它。

### 3. 加载逐插件 try/catch

坏插件只影响它自己，并且**错误原文会显示在「插件」页的日志里**。
这一条在第一次上机时就兑现了：插件报
`Failed resolution of: Lkotlin/jvm/internal/Intrinsics;`，看一眼就知道原因，不用开调试器。

### 4. 分发走固定 tag 的 Release，**不需要 root**

索引和 dex 发布在 tag 为 `plugins` 的 Release 里：

```
plugins.json          商店索引
plugin-<模块名>.dex   各插件的代码
```

App 侧 `PluginManager.fetchStore()` → `UpdateClient.releaseAssetUrl(STORE_TAG, ...)`
→ `fetchText` / `downloadTo`，与 App 自更新共用同一套多源回退。

三个必须这样做的理由：

- **用固定 tag，不用「最新 Release」**：最新那个通常是服务端构建，没有插件附件。
- **发成 prerelease**：这样它不会变成仓库的「Latest release」，
  既不影响 App 自更新找 `.apk`，也不影响服务端版本列表。
- **全程只写 App 自己的私有目录**，所以装插件不需要 root、也不需要存储权限。
  这一点是「没 root 也能装插件」的全部实现。

## 目录与文件

```
文件系统（App 私有目录）
filesDir/plugins/<插件id>/
    plugin.json   {"id":"console-font","entry":"com.pumpkin.plugin.consolefont.ConsoleFontPlugin"}
    plugin.dex    插件代码（CI 用 D8 把插件 jar 转出来）

源码
plugin-api/                     稳定接口：PumpkinPlugin / PluginHost / PluginSetting / PluginKeys
plugins/<模块名>/                插件本体
    plugin.json                  **商店索引的唯一来源**：id / name / version / description / entry
    build.gradle.kts             纯 Kotlin/JVM，compileOnly(project(":plugin-api"))
    src/main/kotlin/...
```

`plugin.json` 的 `dex` 字段**不用自己写**：CI 按模块名派生 `plugin-<模块名>.dex`。
自己写的话，改个模块名就会出现「索引指向一个不存在的附件」。

入口类必须 **public + 无参构造**（反射按类名实例化）。

## 写一个新插件

1. 复制 `plugins/console-font/` 当模板，改包名与 `plugin.json` 里的 `id`。
2. `build.gradle.kts` 保持 `compileOnly(project(":plugin-api"))` —— **不要改成 implementation**。
3. 实现 `PumpkinPlugin`：`id` / `name` / `version` / `description` / `onLoad(host)`。
   耗时的事放 `onLoad`，别放构造函数。
4. 要新的可覆盖能力 → 在 `PluginKeys` 加常量 + 在 App 渲染处读它。
5. `settings.gradle.kts` 里 `include(":plugins:<模块名>")`。

之后**什么都不用改**：CI 会遍历 `plugins/` 下所有模块，各自构建 jar → D8 转 dex →
汇总 `plugins.json` → 覆盖发布到 `plugins` 那个 Release。
版本号写在 `plugin.json` 里，商店就是照它判断「已安装 / 可更新」的。

## R8 的两处硬约束（改混淆配置前必读）

1. **`com.pumpkin.plugin.**` 必须 `-keep`（不是 `-keepnames`）**
   —— 插件按原签名调用这些接口，被改名或删掉就是全部插件加载失败。
   注意 `-keep interface` 只保接口，`PluginSetting` / `PluginKeys` 是类，得单独写。

2. **Kotlin 标准库必须 `-keep class kotlin.** { *; }`**
   —— 插件是**单独编译、没过 R8** 的 dex，按原名引用标准库；
   而 `proguard-android-optimize` 会重打包改名，插件就找不到 `Intrinsics` 了。
   代价：APK 从 1181KB 涨到 1745KB，标准库也不再被内联。
   替代方案是把标准库塞进每个插件（每个多 1MB+），插件一多就不划算。

## 已知限制

- **加载过一次的插件卸不干净**：ClassLoader 不能卸载，重新加载只是多一份 dex 常驻内存。
  删文件 + 不再加载能让它下次启动不出现，但本次进程里那份 dex 还在。插件很小，暂不处理。
- **插件之间抢同一个键**：后声明的生效，且设置项只保留一个（不会两个控件打架）。
- **插件跑在 App 权限内**，能改的只是 App 暴露的覆盖点，改不了系统行为。
  例：改不了手机的真实局域网 IP —— 那是路由器 DHCP / 系统 WiFi 静态设置的事，没有 API。
- **停用 ≠ 还原**：停用只是不加载它的代码，它写过的覆盖值仍然生效（用户已经调好的值不该被悄悄改掉）。
  要连覆盖一起清掉，就卸载。
- **商店索引是明文 HTTP GET**，没有签名校验。当前发布渠道就是本仓库的 Release，
  与 App 自更新同一信任级别；要更严的话得给 dex 加签名，还没做。

## 验证脚本

| 脚本 | 用途 |
|---|---|
| `tools/verify-plugin.sh` | 打开「插件」页，看商店/已安装/设置三块渲染是否正常 |
| `tools/verify-plugin-effect.sh` | 覆盖值是否真的改变了运行页控制台的字号 |
| `tools/clear-plugin-override.sh` | 清掉覆盖键，确认回到内置行为 |
