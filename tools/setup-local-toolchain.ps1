# 装本机 Android 构建工具链（一次性）。
#
# 为什么需要：本机没有 JDK / Android SDK / Gradle，所以以前每次改代码都得推到 GitHub
# 让 Actions 编译，失败了还要「轮询 → 拉日志 → grep」一整圈。
# 装完之后迭代就变成「本地 gradle → adb install」，只在**发布**时才推 CI。
#
# 装到 D:\devtools，不动系统 PATH、不改注册表：
# 需要用的时候临时设 JAVA_HOME / ANDROID_HOME 即可。

$ErrorActionPreference = 'Continue'
$root = 'D:\devtools'
$dl   = "$root\_dl"
New-Item -ItemType Directory -Force -Path $dl | Out-Null

$proxy = 'http://127.0.0.1:7897'
function Get-File($url, $out) {
    if (Test-Path $out) { Write-Host "已存在，跳过：$out"; return }
    Write-Host "下载 $url"
    # 先试直连，失败再走本地代理（dl.google.com 在国内经常要代理）
    & curl.exe -sSL --fail --connect-timeout 20 -o $out $url 2>$null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $out) -or (Get-Item $out).Length -lt 1000) {
        Write-Host "  直连失败，改走代理 $proxy"
        & curl.exe -sSL --fail --connect-timeout 20 --proxy $proxy -o $out $url
    }
    if (Test-Path $out) { Write-Host ("  完成 " + [math]::Round((Get-Item $out).Length/1MB,1) + " MB") }
}

# 1. JDK 21（AGP 9.4 / Gradle 9.7 都要 21）
Get-File 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' "$dl\jdk21.zip"

# 2. Gradle 9.7.1（仓库里没有 wrapper）
Get-File 'https://services.gradle.org/distributions/gradle-9.7.1-bin.zip' "$dl\gradle.zip"

# 3. Android commandline-tools
Get-File 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' "$dl\cmdline-tools.zip"

Write-Host "`n==== 解压 ===="
function Expand-To($zip, $dest, $inner) {
    if (Test-Path $dest) { Write-Host "已存在，跳过：$dest"; return }
    Write-Host "解压 $zip -> $dest"
    $tmp = "$dl\_x"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -LiteralPath $zip -DestinationPath $tmp -Force
    if ($inner) {
        Move-Item (Join-Path $tmp $inner) $dest
    } else {
        Move-Item $tmp $dest
    }
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

Expand-To "$dl\jdk21.zip" "$root\jdk21" 'jdk-21*'
Expand-To "$dl\gradle.zip" "$root\gradle-9.7.1" 'gradle-9.7.1'

# cmdline-tools 必须放在 <sdk>/cmdline-tools/latest/
$sdk = "$root\android-sdk"
if (-not (Test-Path "$sdk\cmdline-tools\latest")) {
    New-Item -ItemType Directory -Force -Path "$sdk\cmdline-tools" | Out-Null
    $tmp = "$dl\_c"
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -LiteralPath "$dl\cmdline-tools.zip" -DestinationPath $tmp -Force
    Move-Item "$tmp\cmdline-tools" "$sdk\cmdline-tools\latest"
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "`n==== 结果 ===="
foreach ($p in @("$root\jdk21\bin\java.exe", "$root\gradle-9.7.1\bin\gradle.bat", "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    if (Test-Path $p) { "OK   $p" } else { "缺   $p" }
}
& "$root\jdk21\bin\java.exe" -version 2>&1 | ForEach-Object { $_ }
