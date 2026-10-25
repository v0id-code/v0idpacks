param([string[]]$Versions = @(), [switch]$StopOnFailure)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$matrix = Get-Content -LiteralPath (Join-Path $projectRoot 'versions.json') -Raw | ConvertFrom-Json -AsHashtable
if ($Versions.Count -eq 0) { $Versions = @($matrix.Keys) }
$reports = Join-Path $projectRoot 'build\compatibility'
New-Item -ItemType Directory -Force -Path $reports | Out-Null
$results = @()
foreach ($version in $Versions) {
    if (-not $matrix.ContainsKey($version)) { throw "Unknown version: $version" }
    $log = Join-Path $reports "$version.log"
    Write-Output "Building Minecraft $version"
    & (Join-Path $projectRoot 'gradlew.bat') release "-PmcVersion=$version" --console=plain *> $log
    $passed = $LASTEXITCODE -eq 0
    $results += [ordered]@{ minecraft = $version; build = $(if ($passed) {'passed'} else {'failed'}); log = $log; checkedAt = [DateTime]::UtcNow.ToString('o') }
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $reports 'results.json') -Encoding utf8
    if ($passed) { Write-Output "PASS $version" }
    else {
        Write-Output "FAIL $version"
        Get-Content -LiteralPath $log -Tail 65
        if ($StopOnFailure) { exit 1 }
    }
}
if ($results.build -contains 'failed') { exit 1 }
