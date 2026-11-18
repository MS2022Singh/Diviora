# Usage: scripts\verify-restore.ps1 -Ref <tag-or-commit> [-Url <remote-url>]
param([Parameter(Mandatory=$true)][string]$Ref, [string]$Url = (git remote get-url origin))
$ErrorActionPreference = 'Stop'
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("diviora-verify-" + [guid]::NewGuid())
try {
  git clone --quiet $Url "$tmp\repo"; Set-Location "$tmp\repo"
  git -c advice.detachedHead=false checkout --quiet $Ref
  if (Test-Path .\gradlew.bat) { .\gradlew.bat --no-daemon -q testDebugUnitTest test assembleDebug; if ($LASTEXITCODE) { throw "build failed" } }
  Write-Host "RESTORE-VERIFY PASS: $Ref"
} finally { Set-Location $env:TEMP; Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue }
