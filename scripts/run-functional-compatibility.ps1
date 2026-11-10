param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][ValidateSet(24,35)][int]$ExpectedApi,
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_-]{1,40}$')][string]$RunId,
    [string]$ForeignReportId
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$node='C:\Users\jcamp\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
$apk=Join-Path $projectRoot 'app\build\outputs\apk\compatibility\app-compatibility.apk'
$testApk=Join-Path $projectRoot 'app\build\outputs\apk\androidTest\compatibility\app-compatibility-androidTest.apk'
$package='com.mascotasmunicipales.compatqa'
$deviceDir="/sdcard/Android/data/$package/files/compatibility/$RunId"
$evidenceDir=Join-Path $projectRoot "tests\evidencias-compatibilidad\funcionales-2026-10-22\$RunId\api$ExpectedApi"

function Adb {
    param([string[]]$Arguments)
    $result=& $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n")
}
function Pull-Evidence {
    $names=(Adb @('shell','ls',$deviceDir)) -split "`r?`n"
    foreach ($name in $names) {
        $name=$name.Trim()
        if ($name -match '^(run|recover)\.json$|^\d\d-[a-z-]+\.png$') {
            Adb @('pull',"$deviceDir/$name",(Join-Path $evidenceDir $name)) | Out-Null
        }
    }
}
function Run-Phase {
    param([string]$Mode,[string]$PendingId)
    $instrumentArgs=@('-s',$Serial,'shell','am','instrument','-w','-e','mode',$Mode,'-e','runId',$RunId)
    if ($PendingId) { $instrumentArgs+=@('-e','pendingId',$PendingId) }
    if ($Mode -eq 'run' -and $ForeignReportId) { $instrumentArgs+=@('-e','foreignReportId',$ForeignReportId) }
    $instrumentArgs+="$package.test/com.mascotasmunicipales.FunctionalCompatibilityInstrumentation"
    & $adb @instrumentArgs 2>&1 | Tee-Object -FilePath (Join-Path $evidenceDir "instrumentation-$Mode.txt") | Out-Host
    Pull-Evidence
    $report=Get-Content -LiteralPath (Join-Path $evidenceDir "$Mode.json") -Raw | ConvertFrom-Json
    if ($report.result -ne 'APROBADO') { throw "Falló $Mode. Consulte las evidencias en $evidenceDir" }
    return $report
}

if ((Adb @('shell','getprop','ro.build.version.sdk')).Trim() -ne "$ExpectedApi") { throw 'API del dispositivo incorrecta.' }
if ((Adb @('shell','getprop','sys.boot_completed')).Trim() -ne '1') { throw 'Android todavía no terminó de arrancar.' }
foreach ($file in @($apk,$testApk)) { if (!(Test-Path -LiteralPath $file)) { throw "Falta compilar $file" } }
foreach ($port in @(8080,9099)) {
    if (!(Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue)) {
        throw "El emulador Firebase local del puerto $port no está disponible."
    }
}
New-Item -ItemType Directory -Path $evidenceDir -Force | Out-Null
# Logcat puede conservar fallos de otras ejecuciones. Conservar el historial por separado.
Adb @('logcat','-d','-s','AndroidRuntime:E') | Set-Content (Join-Path $evidenceDir 'android-runtime-anterior.txt') -Encoding utf8
$deviceLogStart=(Adb @('shell','date','+%m-%d\ %H:%M:%S.000')).Trim()
$env:FIREBASE_PROJECT_ID='demo-mascotas-compat'
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8080'
$env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9099'
Adb @('install','-r',$apk) | Set-Content (Join-Path $evidenceDir 'instalacion.txt') -Encoding utf8
Adb @('install','-r','-t',$testApk) | Out-Null
Adb @('shell','input','keyevent','82') | Out-Null
$runReport=Run-Phase -Mode 'run'
& $node (Join-Path $PSScriptRoot 'compatibility-local-data.mjs') pending (Join-Path $evidenceDir 'run.json') (Join-Path $evidenceDir 'servidor-antes-reinicio.json')
if ($LASTEXITCODE -ne 0) { throw 'Falló la comprobación del servidor antes del reinicio.' }
Adb @('shell','am','force-stop',$package) | Out-Null
$null=Run-Phase -Mode 'recover' -PendingId $runReport.pendingId
& $node (Join-Path $PSScriptRoot 'compatibility-local-data.mjs') final (Join-Path $evidenceDir 'run.json') (Join-Path $evidenceDir 'servidor-final.json')
if ($LASTEXITCODE -ne 0) { throw 'Falló la comprobación final del servidor.' }
Adb @('logcat','-d','-T',$deviceLogStart,'-s','AndroidRuntime:E') | Set-Content (Join-Path $evidenceDir 'android-runtime.txt') -Encoding utf8
[ordered]@{
    api=$ExpectedApi; android=(Adb @('shell','getprop','ro.build.version.release')).Trim()
    model=(Adb @('shell','getprop','ro.product.model')).Trim()
    apkSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash
    testApkSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $testApk).Hash
    package=$package; project='demo-mascotas-compat'; serial=$Serial; runId=$RunId
    checkedAt=(Get-Date).ToString('o'); result='APROBADO'
} | ConvertTo-Json | Set-Content (Join-Path $evidenceDir 'entorno.json') -Encoding utf8
Write-Output "API ${ExpectedApi}: recorrido y recuperación verificados. Evidencias: $evidenceDir"
