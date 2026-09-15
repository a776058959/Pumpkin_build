# Add a UTF-8 BOM to every tools/*.ps1 (except this one).
#
# Why: Windows PowerShell 5.1 decodes a .ps1 WITHOUT a BOM using the system ANSI
# codepage (GBK on zh-CN). Chinese comments/strings then turn into mojibake, and
# the mojibake can swallow a closing quote -> "Unexpected token" parse errors that
# look like a broken script. Most editors save UTF-8 without BOM by default, so run
# this after editing any .ps1.
#
# This file is intentionally ASCII-only so it can run even when it has no BOM.
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File tools/fix-ps1-bom.ps1

$bom = [byte[]](0xEF, 0xBB, 0xBF)
$fixed = 0

foreach ($f in Get-ChildItem "$PSScriptRoot\*.ps1") {
    if ($f.Name -eq 'fix-ps1-bom.ps1') { continue }
    $b = [System.IO.File]::ReadAllBytes($f.FullName)
    if ($b.Length -ge 3 -and $b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) {
        Write-Host ("ok   " + $f.Name)
        continue
    }
    [System.IO.File]::WriteAllBytes($f.FullName, ($bom + $b))
    Write-Host ("bom+ " + $f.Name)
    $fixed++
}

Write-Host ""
if ($fixed -eq 0) { Write-Host "nothing to fix" } else { Write-Host ("fixed " + $fixed + " file(s)") }
