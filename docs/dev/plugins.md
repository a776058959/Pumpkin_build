# 插件系统

> 目的：以后加新功能优先做成插件，而不是往主工程里塞代码。
> 使用者视角的说明在 [README.md](../../README.md)；这里只讲怎么改。

## 架构（三个决定，都是有原因的）

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

坏插件只影响它自己，并且**错误原文会显示在「设置 → 插件」的日志里**。
这一条在第一次上机时就兑现了：插件报
`Failed resolution of: Lkotlin/jvm/internal/Intrinsics;`，看一眼截图就知道原因，不用开调试器。

## R8 的两处硬约束（改混淆配置前必读）

1. **`com.pumpkin.plugin.**` 必须 `-keep`（不是 `-keepnames`）**
   —— 插件按原签名调用这些接口，被改名或删掉就是全部插件加载失败。

2. **Kotlin 标准库必须 `-keep class kotlin.** { *; }`**
   —— 插件是**单独编译、没过 R8** 的 dex，按原名引用标准库；
   而 `proguard-android-optimize` 会重打包改名，插件就找不到 `Intrinsics` 了。
   代价：APK 从 1181KB 涨到 1745KB，标准库也不再被内联。
   替代方案是把标准库塞进每个插件（每个多 1MB+），插件一多就不划算。

## 插件长什么样

```
filesDir/plugins/<插件id>/
    plugin.json   {"id":"lan-address","entry":"com.pumpkin.plugin.lanaddress.LanAddressPlugin"}
    plugin.dex    插件代码（CI 用 D8 把插件 jar 转出来）
```

入口类必须 **public + 无参构造**（反射按类名实例化）。

## 写一个新插件

1. 复制 `plugins/lan-address/` 当模板，改包名与 `id`。
2. `build.gradle.kts` 保持 `compileOnly(project(":plugin-api"))` —— **不要改成 implementation**。
3. 实现 `PumpkinPlugin`：`id` / `name` / `version` / `description` / `onLoad(host)`。
   耗时的事放 `onLoad`，别放构造函数。
4. 要新的可覆盖能力 → 在 `PluginKeys` 加常量 + 在 App 渲染处读它。
5. CI 会自动编译所有 `plugins/*` 模块并转成 dex（见 `.github/workflows/apk-only.yml`
   的 “Build plugin and convert to dex”），产物是 artifact。

## 已知限制

- **插件无法卸载**：ClassLoader 不能卸载，重新加载只是多一份 dex 常驻内存。
  插件很小，暂不处理。
- **插件之间抢同一个键**：后声明的生效，且设置项只保留一个（不会两个控件打架）。
- **插件跑在 App 权限内**，能改的只是 App 暴露的覆盖点，改不了系统行为。
- **插件分发还没做**：目前得把 `plugin.json` + `plugin.dex` 放进插件目录（需要 root 或 adb）。
  下一步可以复用现成的下载器，从 Releases 拉插件并自动解开。

## 验证脚本

| 脚本 | 用途 |
|---|---|
| `tools/verify-plugin.sh` | 放插件文件 → 重启 → 看「设置 → 插件」有没有认出来 |
| `tools/verify-plugin-effect.sh` | 覆盖值是否真的改变了运行页显示的联机地址 |
