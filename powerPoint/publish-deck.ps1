#requires -Version 5.1
<#
    Builds the FinFlow two-sprint review deck and installs it as the deliverable.

    The package is generated into a staging file first and then copied into place,
    because the sandboxed child process that runs the generator is not permitted to
    write the delivered .pptx path directly. The copy is done by this parent
    process, which is.

    Run from the repository root:
        powershell -NoProfile -ExecutionPolicy Bypass -File powerPoint\publish-deck.ps1
#>
param(
    [string]$Name = 'FinFlow-Sprint-1-and-2-Review.pptx'
)

$ErrorActionPreference = 'Stop'
$here    = $PSScriptRoot
$gen     = Join-Path $here 'build-deck.ps1'
$staging = Join-Path $here '_deck-staging.pptx'
$final   = Join-Path $here $Name
$shell   = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'

if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Force }

Write-Output '--- generating package ---'
& $shell -NoProfile -ExecutionPolicy Bypass -File $gen -OutPath $staging 2>&1 | ForEach-Object { Write-Output $_ }
if ($LASTEXITCODE -ne 0) { throw "generator failed with exit code $LASTEXITCODE" }
if (-not (Test-Path -LiteralPath $staging)) { throw "generator produced no staging file" }

Write-Output '--- installing deliverable ---'
$bytes = [System.IO.File]::ReadAllBytes($staging)
if (Test-Path -LiteralPath $final) {
    $item = Get-Item -LiteralPath $final -Force
    if ($item.Attributes -band [System.IO.FileAttributes]::ReadOnly) {
        $item.Attributes = $item.Attributes -bxor [System.IO.FileAttributes]::ReadOnly
    }
}
$fs = New-Object System.IO.FileStream($final, ([System.IO.FileMode]::Create), ([System.IO.FileAccess]::Write), ([System.IO.FileShare]::None))
$fs.Write($bytes, 0, $bytes.Length)
$fs.Flush()
$fs.Dispose()

Remove-Item -LiteralPath $staging -Force

Write-Output ''
Write-Output ("DELIVERED : {0}" -f $final)
Write-Output ("BYTES     : {0:N0}" -f (Get-Item -LiteralPath $final).Length)
