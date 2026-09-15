# 用 sdkmanager 装本机构建需要的 SDK 组件。
#
# 直连 dl.google.com 在国内下不动（实测超时），所以统一走本地代理。
# sdkmanager 自己带 --proxy 参数，不用改系统设置。
#
# 装什么：
#   platform-tools         adb（本机已经有一份，但让 SDK 自成一套更省心）
#   platforms;android-37   compileSdk = 37
#   build-tools;37.0.0     d8 / aapt2 / zipalign（**插件 dex 就是这里的 d8 转的**）

$ErrorActionPreference = 'Continue'
$root = 'D:\devtools'
$sdk  = "$root\android-sdk"
$env:JAVA_HOME = "$root\jdk21"
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$sm = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
$proxyArgs = @('--proxy=http', '--proxy_host=127.0.0.1', '--proxy_port=7897')

Write-Host "==== 可用包（只看我们要的） ===="
& $sm @proxyArgs --list 2>&1 |
    Select-String -Pattern 'platforms;android-3[5-9]|build-tools;3[5-9]|platform-tools' |
    Select-Object -First 30 | ForEach-Object { $_.Line }

Write-Host "`n==== 安装 ===="
# licenses 必须先接受，否则 sdkmanager 会卡在交互提示上
$yes = ("y`r`n" * 50)
$yes | & $sm @proxyArgs --licenses 2>&1 | Select-Object -Last 3

$pkgs = @('platform-tools', 'platforms;android-37', 'build-tools;37.0.0')
& $sm @proxyArgs $pkgs 2>&1 | Select-Object -Last 25

Write-Host "`n==== 结果 ===="
foreach ($p in @("$sdk\platform-tools\adb.exe", "$sdk\platforms\android-37\android.jar", "$sdk\build-tools\37.0.0\d8.bat")) {
    if (Test-Path $p) { "OK   $p" } else { "缺   $p" }
}
Get-ChildItem "$sdk\build-tools" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
