param(
    [Parameter(Mandatory=$true)][string]$Version,
    [Parameter(Mandatory=$true)][long]$VersionCode,
    [Parameter(Mandatory=$true)][string]$ApkDirectory,
    [string]$Notes = '',
    [string]$OutputFile = ''
)
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$' -or $VersionCode -le 0) { throw 'Versão inválida.' }
if (!$OutputFile) { $OutputFile = Join-Path $ApkDirectory 'update.json' }
$assets = foreach ($abi in @('arm64-v8a', 'x86_64')) {
    $name = "SFMD_${Version}_$abi.apk"
    $apk = Get-Item -LiteralPath (Join-Path $ApkDirectory $name)
    if ($apk.Length -le 0 -or $apk.Length -gt 512MB) { throw "Tamanho inválido: $name" }
    [ordered]@{
        name = $name
        size = $apk.Length
        digest = 'sha256:' + (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLower()
        browser_download_url = "https://github.com/NskBR/SFMusicDownloader-Android/releases/download/v$Version/$name"
    }
}
[ordered]@{
    schema_version = 1
    package_name = 'com.nsk.sfmd'
    version_code = $VersionCode
    tag_name = "v$Version"
    draft = $false
    prerelease = $false
    body = $Notes
    assets = @($assets)
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $OutputFile -Encoding utf8NoBOM
Write-Output "Manifesto gerado: $OutputFile"
