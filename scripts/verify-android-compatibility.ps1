param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][ValidateSet(24, 35)][int]$ExpectedApi,
    [string]$Apk,
    [switch]$SkipPhotoCheck,
    [string]$EvidenceDirectory
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (!$Apk) { $Apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk' }
if (!$EvidenceDirectory) {
    $EvidenceDirectory = Join-Path $projectRoot "tests\evidencias-compatibilidad\api$ExpectedApi"
}
if (!(Test-Path -LiteralPath $adb)) { throw 'No se encontró adb en el SDK local.' }
if (!(Test-Path -LiteralPath $Apk)) { throw 'Compile primero el APK indicado.' }

function Invoke-Adb {
    param([string[]]$AdbArguments)
    $output = & $adb -s $Serial @AdbArguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    return ($output -join "`n")
}

# Se comprueba el destino antes de instalar. Utilice emuladores dedicados sin cuentas personales.
$api = (Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk')).Trim()
if ($api -ne "$ExpectedApi") { throw "El dispositivo tiene API $api; se esperaba $ExpectedApi." }
$version = (Invoke-Adb @('shell', 'getprop', 'ro.build.version.release')).Trim()
$deviceModel = (Invoke-Adb @('shell', 'getprop', 'ro.product.model')).Trim()
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Apk).Hash

Invoke-Adb @('install', '-r', $Apk) | Set-Content (Join-Path $EvidenceDirectory 'instalacion.txt') -Encoding utf8
Invoke-Adb @('shell', 'am', 'force-stop', 'com.mascotasmunicipales') | Out-Null
$startResult = Invoke-Adb @('shell', 'am', 'start', '-W', '-n', 'com.mascotasmunicipales/.MainActivity')
$startResult | Set-Content (Join-Path $EvidenceDirectory 'inicio.txt') -Encoding utf8
if ($startResult -notmatch 'Status: ok') { throw 'Android no confirmó el arranque; consulte inicio.txt.' }

# Captura de Android, no del escritorio; no se introducen contraseñas ni se crean cuentas.
Start-Sleep -Seconds 2
$appPid = (Invoke-Adb @('shell', 'pidof', 'com.mascotasmunicipales')).Trim()
if (!$appPid) { throw 'La aplicación no sigue ejecutándose después del arranque.' }
Invoke-Adb @('shell', 'screencap', '-p', '/sdcard/compatibilidad-inicio.png') | Out-Null
Invoke-Adb @('pull', '/sdcard/compatibilidad-inicio.png', (Join-Path $EvidenceDirectory 'inicio.png')) | Out-Null
Invoke-Adb @('shell', 'rm', '/sdcard/compatibilidad-inicio.png') | Out-Null
Invoke-Adb @('shell', 'dumpsys', 'meminfo', 'com.mascotasmunicipales') |
    Set-Content (Join-Path $EvidenceDirectory 'memoria-inicio.txt') -Encoding utf8

if (!$SkipPhotoCheck) {
    $testApk = Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if (!(Test-Path -LiteralPath $testApk)) { throw 'Ejecute :app:assembleDebugAndroidTest antes de la prueba de fotos.' }
    Invoke-Adb @('install', '-r', '-t', $testApk) | Out-Null
    $photoResult = Invoke-Adb @('shell', 'am', 'instrument', '-w',
        'com.mascotasmunicipales.test/com.mascotasmunicipales.PhotoCompatibilityInstrumentation')
    $photoResult | Set-Content (Join-Path $EvidenceDirectory 'fotos.txt') -Encoding utf8
    if ($photoResult -notmatch 'resultado=APROBADO' -or $photoResult -notmatch 'INSTRUMENTATION_CODE: -1') {
        throw 'Falló la comprobación de fotografías. Consulte fotos.txt.'
    }
}

[ordered]@{
    api = [int]$api
    android = $version
    modelo = $deviceModel
    apkSha256 = $hash
    fecha = (Get-Date).ToString('o')
    alcance = 'Instalación, arranque y bitmaps si se solicitaron. Las tres funciones requieren su recorrido propio.'
} | ConvertTo-Json | Set-Content (Join-Path $EvidenceDirectory 'entorno.json') -Encoding utf8
Write-Output "Evidencia guardada en $EvidenceDirectory. APK SHA-256: $hash"
