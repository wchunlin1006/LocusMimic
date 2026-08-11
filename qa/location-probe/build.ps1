$ErrorActionPreference = 'Stop'

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$buildTools = Join-Path $sdk 'build-tools\36.0.0'
$androidJar = Join-Path $sdk 'platforms\android-36\android.jar'
$root = $PSScriptRoot
$out = Join-Path $root 'build'
$classes = Join-Path $out 'classes'
$dex = Join-Path $out 'dex'
$unsigned = Join-Path $out 'location-probe-unsigned.apk'
$aligned = Join-Path $out 'location-probe-aligned.apk'
$signed = Join-Path $out 'location-probe.apk'
$keystore = Join-Path $env:USERPROFILE '.android\debug.keystore'

New-Item -ItemType Directory -Force $classes, $dex | Out-Null
Remove-Item $unsigned, $aligned, $signed -Force -ErrorAction SilentlyContinue

& (Join-Path $buildTools 'aapt2.exe') link `
    -I $androidJar `
    --manifest (Join-Path $root 'AndroidManifest.xml') `
    --min-sdk-version 23 `
    --target-sdk-version 31 `
    -o $unsigned
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed: $LASTEXITCODE" }

$javaFiles = Get-ChildItem (Join-Path $root 'src') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac -encoding UTF-8 -source 8 -target 8 -classpath $androidJar `
    -d $classes $javaFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed: $LASTEXITCODE" }

$classFiles = Get-ChildItem $classes -Recurse -Filter '*.class' | ForEach-Object FullName
& (Join-Path $buildTools 'd8.bat') --lib $androidJar --output $dex $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed: $LASTEXITCODE" }
Push-Location $dex
try {
    & (Join-Path $buildTools 'aapt.exe') add $unsigned 'classes.dex'
    if ($LASTEXITCODE -ne 0) { throw "aapt add failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}

& (Join-Path $buildTools 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed: $LASTEXITCODE" }
& (Join-Path $buildTools 'apksigner.bat') sign `
    --ks $keystore `
    --ks-key-alias androiddebugkey `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed: $LASTEXITCODE" }
& (Join-Path $buildTools 'apksigner.bat') verify --verbose $signed
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed: $LASTEXITCODE" }

Get-FileHash $signed -Algorithm SHA256
