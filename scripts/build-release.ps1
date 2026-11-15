param(
    [Parameter(Mandatory=$true)][string]$KeyStore,
    [string]$Alias = 'sfmd',
    [string]$Version = '0.1.2',
    [int]$VersionCode = 3,
    [string]$ReleaseNotes = '',
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
if (!$env:SFMD_STORE_PASSWORD) { throw 'Defina SFMD_STORE_PASSWORD no ambiente, sem salvar no repositório.' }
if (!$env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    & .\gradlew.bat :app:assembleRelease "-PsfmdVersion=$Version" "-PsfmdVersionCode=$VersionCode" --console=plain
    if ($LASTEXITCODE) { throw 'Falha no build release.' }
    $buildTools = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
    $signer = Join-Path $buildTools.FullName 'apksigner.bat'
    $out = Join-Path $root 'release'
    New-Item -ItemType Directory -Force $out | Out-Null
    foreach ($abi in @('arm64-v8a', 'x86_64')) {
        $inputApk = Join-Path $root "app/build/outputs/apk/release/app-$abi-release-unsigned.apk"
        $outputApk = Join-Path $out "SFMD_${Version}_$abi.apk"
        & $signer sign --ks $KeyStore --ks-key-alias $Alias --ks-pass env:SFMD_STORE_PASSWORD --key-pass env:SFMD_STORE_PASSWORD --out $outputApk $inputApk
        if ($LASTEXITCODE) { throw "Falha ao assinar $abi." }
        & $signer verify --verbose --print-certs $outputApk
        if ($LASTEXITCODE) { throw "Assinatura inválida: $abi." }
    }
    Get-ChildItem $out -Filter "SFMD_${Version}_*.apk" | ForEach-Object { '{0}  {1}' -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name } | Set-Content (Join-Path $out 'SHA256SUMS.txt') -Encoding ascii
    & (Join-Path $PSScriptRoot 'new-update-manifest.ps1') -Version $Version -VersionCode $VersionCode -ApkDirectory $out -Notes $ReleaseNotes
} finally { Pop-Location }
