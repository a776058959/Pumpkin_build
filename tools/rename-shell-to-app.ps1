# 把「壳 / shell」这套叫法统一改成 App 的名字「南瓜坞」。
#
# 分两类处理：
#   1) 标识符（shellVersion / ShellAsset / SHELL_UPDATE …）—— 纯内部，零风险
#   2) 中文文案与注释里的「壳」—— 逐条给准确说法，不做 壳→南瓜坞 的无脑替换
#      （否则「轻壳主界面」会变成「轻南瓜坞主界面」这种怪东西）
#
# 刻意不动的东西（改了会破坏既有契约）：
#   - Prefs.NAME = "pumpkin_shell"   ← SharedPreferences 文件名，改了用户设置全丢
#   - pumpkin-shell.apk              ← Release 资产名，ANDROID.md 里当永久链接用

$ErrorActionPreference = 'Stop'
$root = 'D:\Pumpkin_build'
$enc = [System.Text.UTF8Encoding]::new($false)

# 需要处理的文本文件（排除 .git / .miuix-ref / 构建产物 / 本脚本自己）
$files = Get-ChildItem $root -Recurse -Include *.java,*.kt,*.md,*.yml,*.yaml,*.kts,*.xml,*.sh -File |
    Where-Object { $_.FullName -notmatch '\\\.git\\|\\\.miuix-ref\\|\\build\\|\\target\\|rename-shell-to-app' }

# ---- 标识符：仅 Java/Kotlin，大小写敏感 ----
# 顺序有讲究：先长后短，避免 ShellAsset 被 fetchShellAsset 的规则抢掉。
$ident = [ordered]@{
    'fetchShellAsset'    = 'fetchAppAsset'
    'ShellAsset'         = 'AppAsset'
    'setShellVersion'    = 'setAppVersion'
    'shellVersion'       = 'appVersion'
    'checkShellUpdate'   = 'checkAppUpdate'
    'checkShellUpdateFromUi' = 'checkAppUpdateFromUi'
    'SHELL_UPDATE'       = 'APP_UPDATE'
    'pendingShellUrl'    = 'pendingAppUrl'
    'shell-check'        = 'app-update-check'
    'PumpkinServerShell' = 'Pumpkin-App'
}

# ---- 中文文案/注释：逐条精确替换 ----
$phrases = [ordered]@{
    # 用户能看到的界面文字
    '服务器上还没有发布新的壳版本' = '服务器上还没有发布新的南瓜坞版本'
    '壳有新版本'                   = '南瓜坞有新版本'
    '服务器上发布了新的壳：'       = '服务器上发布了新版南瓜坞：'
    '壳已是最新（'                 = '南瓜坞已是最新（'
    '壳版本: '                     = '南瓜坞版本: '
    # 注释
    '轻壳主界面'                   = '南瓜坞主界面'
    '顶层 Compose 壳'              = '顶层 Compose 界面'
    '检查到壳有新版本'             = '检查到南瓜坞有新版本'
    '壳版本号（关于页用）'         = '南瓜坞版本号（关于页用）'
    '壳更新对话框里要下载的地址'   = '应用更新对话框里要下载的地址'
    '壳本身不含服务端'             = '南瓜坞本身不含服务端'
    '查询壳自身的更新'             = '查询南瓜坞自身的更新'
    '壳（APK）自身的发布信息'      = '南瓜坞（APK）自身的发布信息'
    '里的壳 APK（用于检查壳自身有没有更新）' = '里的南瓜坞 APK（用于检查南瓜坞自身有没有更新）'
    '找「壳」自己（APK）的发布信息' = '找「南瓜坞」自己（APK）的发布信息'
    '「壳 APK」共用一个发布流'     = '「南瓜坞 APK」共用一个发布流'
    '服务端构建比壳频繁得多'       = '服务端构建比南瓜坞频繁得多'
    '「没有找到壳的发布信息」'     = '「没有找到南瓜坞的发布信息」'
    '永远检查不到壳的更新'         = '永远检查不到南瓜坞的更新'
    '更贴近壳的真实新旧'           = '更贴近南瓜坞的真实新旧'
    '而本壳要保持'                 = '而南瓜坞要保持'
    '结果「壳检查更新」只能拿安装时间戳去比' = '结果「检查更新」只能拿安装时间戳去比'
    '壳不再内置原生库'             = '南瓜坞不再内置原生库'
    '安卓壳 App'                   = '南瓜坞 App'
    '构建安卓壳 APK'               = '构建南瓜坞 APK'
    '只构建「壳」APK'              = '只构建「南瓜坞」APK'
    '找不到壳的发布信息'           = '找不到南瓜坞的发布信息'
    '没跟着壳工程升级'             = '没跟着 App 工程升级'
    '以后改壳工程的 AGP 版本时'    = '以后改 App 工程的 AGP 版本时'
    '壳源码就在本仓库'             = 'App 源码就在本仓库'
    '本次 release 不含可用的壳版本比对基准' = '本次 release 不含可用的南瓜坞版本比对基准'
    # 文档
    '壳自身的更新机制'             = '南瓜坞自身的更新机制'
    '点「检查壳更新」'             = '点「检查更新」'
    'CI 里的壳 APK 构建'           = 'CI 里的南瓜坞 APK 构建'
    '但壳工程早已升级到'           = '但 App 工程早已升级到'
    '服务端构建和壳 APK 共用一个发布流' = '服务端构建和南瓜坞 APK 共用一个发布流'
    '以后改壳工程时要记得'         = '以后改 App 工程时要记得'
    '在安卓手机上跑 Pumpkin（壳 App 方式）' = '在安卓手机上跑 Pumpkin（南瓜坞 App 方式）'
    '用 **Pumpkin 壳 App**'        = '用 **南瓜坞 App**'
    '壳本身很轻'                   = '南瓜坞本身很轻'
    '只放壳源码与构建流水线'       = '只放 App 源码与构建流水线'
    '壳 APK 仅约'                  = '南瓜坞 APK 仅约'
    '壳安装包（约 90KB）'          = '安装包（约 90KB）'
    '## 一、安装壳 App'            = '## 一、安装南瓜坞 App'
    '壳安装包有**永久固定**'       = '安装包有**永久固定**'
    '（壳，约 90KB）'              = '（南瓜坞，约 90KB）'
    '**关于**：壳版本号'           = '**关于**：南瓜坞版本号'
    '把本 App（或壳）设为'         = '把本 App 设为'
    '**原理与壳一样**'             = '**原理与南瓜坞一样**'
    '壳因此把下载的服务端'         = '南瓜坞因此把下载的服务端'
    '再配一个安卓外壳 App'         = '再配一个安卓 App'
    '外壳本身**不含服务端**'       = 'App 本身**不含服务端**'
    '只放外壳源码 + CI'            = '只放 App 源码 + CI'
    '外壳版本 **0.3.0'             = 'App 版本 **0.3.0'
    '只重打包外壳'                 = '只重打包 App'
    '## 外壳 App 当前功能'         = '## 南瓜坞 App 当前功能'
    'Android APK 外壳源码'         = 'Android APK 源码'
    'APK 外壳：'                   = 'APK：'
}

$changed = @{}
foreach ($f in $files) {
    $orig = [System.IO.File]::ReadAllText($f.FullName, $enc)
    $text = $orig
    $n = 0

    if ($f.Extension -in '.java', '.kt') {
        foreach ($k in $ident.Keys) {
            if ($k -eq 'pumpkin_shell') { continue }   # 占位，跳过
            if ($text.Contains($k)) { $n += ([regex]::Matches($text, [regex]::Escape($k))).Count; $text = $text.Replace($k, $ident[$k]) }
        }
    }
    foreach ($k in $phrases.Keys) {
        if ($text.Contains($k)) { $n += ([regex]::Matches($text, [regex]::Escape($k))).Count; $text = $text.Replace($k, $phrases[$k]) }
    }

    if ($text -ne $orig) {
        [System.IO.File]::WriteAllText($f.FullName, $text, $enc)
        $changed[$f.FullName.Replace("$root\", '')] = $n
    }
}

"=== 已改动的文件 ==="
$changed.GetEnumerator() | Sort-Object Name | ForEach-Object { "  {0,-70} {1} 处" -f $_.Key, $_.Value }
"`n共 $($changed.Count) 个文件"
