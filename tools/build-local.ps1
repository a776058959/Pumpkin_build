# 本机一键构建：APK + 插件 dex + 商店索引。
#
# 为什么要有这个：本机以前没有 JDK / Android SDK / Gradle，改一行代码也得推到 GitHub
# 让 Actions 编译，失败了还要「轮询 → 拉日志 → grep」一整圈，很费 token。
# 装好工具链之后（tools/setup-local-toolchain.ps1 + tools/setup-android-sdk.ps1），
# 日常迭代就只剩这一条命令；**只有发布**（要变成 Release 供 App 自更新）才推 CI。
#
# 这里做的步骤与 .github/workflows/apk-only.yml 一一对应，产物也一样，
# 所以本地验过的东西推上去不会因为「构建方式不同」而变样。

param(
    # 用指定的时间戳构建（要和已发布的 tag 对齐时用）。
    # 不给就用当前 UTC 时间 —— 与 CI 一致。
    [string]$Stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd-HHmm'),

    # 只出 APK，不构建插件（改 App 代码时能快一点）。
    [switch]$SkipPlugins
)

$ErrorActionPreference = 'Stop'
$root    = 'D:\devtools'
$appDir  = 'D:\Pumpkin_build\android-app'
$outDir  = 'D:\Pumpkin_build\out'

$env:JAVA_HOME        = "$root\jdk21"
$env:ANDROID_HOME     = "$root\android-sdk"
$env:ANDROID_SDK_ROOT = "$root\android-sdk"
$gradle = "$root\gradle-9.7.1\bin\gradle.bat"
$d8     = "$root\android-sdk\build-tools\37.0.0\d8.bat"
$jarExe = "$root\jdk21\bin\jar.exe"

if (-not (Test-Path $gradle)) { throw "没找到 Gradle：$gradle（先跑 tools/setup-local-toolchain.ps1）" }
if (-not (Test-Path $d8))     { throw "没找到 d8：$d8（先跑 tools/setup-android-sdk.ps1）" }

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Remove-Item "$outDir\*.dex", "$outDir\plugins.json" -Force -ErrorAction SilentlyContinue

Write-Host "==== 构建 APK（stamp=$Stamp） ===="
# 必须写成 "-PbuildStamp=$Stamp"（带引号）：裸的 -PbuildStamp=$Stamp 会被 PowerShell
# 当成「参数名」原样传下去，变量**不展开** —— gradle 收到的是字面量 "$Stamp"，
# 时间戳解析失败、版本号静默退化成兜底值 20000000。
# 恶性之处在于它不报错：包能装、能跑，只是版本号是垃圾，直到某天「检查更新」失灵。
& $gradle -p $appDir assembleRelease "-PbuildStamp=$Stamp"
if ($LASTEXITCODE -ne 0) { throw "assembleRelease 失败" }

$apk = "$appDir\app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "没有产物：$apk" }

# 校验版本号真的烧进去了。静默退化过一次，所以这里主动验，不靠人看。
$aapt = "$root\android-sdk\build-tools\37.0.0\aapt2.exe"
if (Test-Path $aapt) {
    $badging = & $aapt dump badging $apk 2>&1 | Select-String "versionCode" | Select-Object -First 1
    if ($badging -match "versionCode='(\d+)'" -and $Matches[1] -eq '20000000') {
        throw "版本号是兜底值 20000000 —— buildStamp 没传进去（检查 -PbuildStamp 的引号）"
    }
    Write-Host ("  " + $badging.Line.Trim())
}

$apkOut = "$outDir\pumpkin-app-$Stamp.apk"
Copy-Item $apk $apkOut -Force
Write-Host ("APK: {0}  ({1} MB)" -f $apkOut, [math]::Round((Get-Item $apkOut).Length / 1MB, 2))

if ($SkipPlugins) {
    Write-Host "`n（-SkipPlugins，跳过插件）"
    return
}

Write-Host "`n==== 构建插件 ===="
$tmp = "$outDir\_d8"
$index = @()

foreach ($d in Get-ChildItem "$appDir\plugins" -Directory) {
    # 必须用 FullName：DirectoryInfo 直接插值成字符串只得到**名字**（不含路径），
    # 于是 Test-Path "$dir\build.gradle.kts" 会去当前目录找、全部落空，
    # 循环被静默跳过 —— 插件一个都没构建，却不报任何错。本喵踩过这个坑。
    $path = $d.FullName
    $name = $d.Name
    if (-not (Test-Path "$path\build.gradle.kts")) { continue }
    Write-Host "-- $name"

    & $gradle -p $appDir ":plugins:$name`:jar"
    if ($LASTEXITCODE -ne 0) { throw "插件 $name 构建失败" }

    $jar = Get-ChildItem "$path\build\libs\*.jar" | Where-Object { $_.Name -notlike '*sources*' } | Select-Object -First 1
    if (-not $jar) { throw "插件 $name 没有产出 jar" }

    # 与 CI 同一道断言：插件 jar 里绝不能有 plugin-api 的类。
    # 出现就说明有人把 compileOnly 改成了 implementation ——
    # 那样 PumpkinPlugin 会在两个类加载器里各有一份，插件的 instanceof 静默失败。
    $entries = & $jarExe tf $jar.FullName
    $leak = $entries | Where-Object { $_ -match 'com/pumpkin/plugin/(PumpkinPlugin|PluginHost|PluginSetting|PluginKeys)' }
    if ($leak) {
        throw "插件 $name 的 jar 里混进了 plugin-api 的类：$($leak -join ', ')。必须用 compileOnly 依赖。"
    }

    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    & $d8 --min-api 26 --output $tmp $jar.FullName
    if ($LASTEXITCODE -ne 0) { throw "插件 $name 的 d8 失败" }
    Copy-Item "$tmp\classes.dex" "$outDir\plugin-$name.dex" -Force

    # -Encoding UTF8 不能省：PS 5.1 的 Get-Content 默认按系统 ANSI（简中 = GBK）读，
    # plugin.json 里的中文会变乱码，ConvertFrom-Json 直接报「Invalid object passed in」。
    # 同理写 plugins.json 时用 UTF8Encoding($false)（不要 BOM，App 侧按纯 UTF-8 解析）。
    $meta = Get-Content "$path\plugin.json" -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($f in 'id', 'name', 'version', 'entry') {
        if (-not $meta.$f) { throw "$name/plugin.json 缺少 $f" }
    }
    # dex 文件名由构建侧派生，不让 plugin.json 自己写（否则改模块名会指向不存在的附件）
    $meta | Add-Member -NotePropertyName dex -NotePropertyValue "plugin-$name.dex" -Force
    $index += $meta
    Write-Host ("   plugin-$name.dex  ({0} B)" -f (Get-Item "$outDir\plugin-$name.dex").Length)
}
Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue

$ids = $index | ForEach-Object { $_.id }
if (($ids | Sort-Object -Unique).Count -ne $ids.Count) { throw "插件 id 重复：$($ids -join ', ')" }

$json = [ordered]@{ version = 1; plugins = $index } | ConvertTo-Json -Depth 6
[System.IO.File]::WriteAllText("$outDir\plugins.json", $json, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "`n==== 完成 ===="
Get-ChildItem $outDir -File | ForEach-Object { "  {0}  {1} B" -f $_.Name, $_.Length }
Write-Host "`n装到手机："
Write-Host "  D:\adb-fastboot\adb.exe install -r $apkOut"
