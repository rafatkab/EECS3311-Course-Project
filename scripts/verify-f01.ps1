# Run from the repository root after backend verify and frontend production package.
# Requires Java 17, Node, Chrome and the temporary playwright-core installation.
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path $PSScriptRoot -Parent
$javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
$backendPath = Join-Path $repoPath 'backend'
$frontendPath = Join-Path $repoPath 'frontend'
$runId = [guid]::NewGuid().ToString('N')
$runPath = Join-Path $backendPath "target\qa-$runId"
New-Item -ItemType Directory -Path $runPath | Out-Null
$backendProcess = $null
$frontendProcess = $null
$environmentKeys = @('STUDY_USERNAME','STUDY_PASSWORD','STUDY_AUTH_USERNAME','STUDY_AUTH_PASSWORD','SPRING_DATASOURCE_URL','BACKEND_PORT','PORT','BACKEND_URL')
$previousEnvironment = @{}
foreach ($key in $environmentKeys) { $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$env:STUDY_USERNAME = 'qa-student'
$env:STUDY_PASSWORD = [guid]::NewGuid().ToString('N')
$env:STUDY_AUTH_USERNAME = $env:STUDY_USERNAME
$env:STUDY_AUTH_PASSWORD = $env:STUDY_PASSWORD
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:file:' + (Join-Path $runPath 'courses').Replace('\', '/')
$env:BACKEND_PORT = '8091'
$env:PORT = '8090'
$env:BACKEND_URL = 'http://localhost:8091'

function Wait-Server([string] $url) {
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try {
            if ((Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 1
    }
    throw "Server did not become ready: $url. Inspect the target QA logs."
}
function Start-Backend([string] $suffix) {
    Start-Process -FilePath $javaPath -ArgumentList @('-jar', 'target/backend-0.0.1-SNAPSHOT.jar') -WorkingDirectory $backendPath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runPath "backend-$suffix.log") -RedirectStandardError (Join-Path $runPath "backend-$suffix.err.log")
}
try {
    foreach ($testPort in @(8090, 8091)) {
        if (netstat -ano | Select-String ":$testPort\s.*LISTENING") { throw "QA port $testPort is occupied; stop that app before running this script." }
    }
    $backendProcess = Start-Backend 'first'
    Wait-Server 'http://localhost:8091/actuator/health'
    $frontendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar', 'target/frontend-0.0.1-SNAPSHOT.jar') -WorkingDirectory $frontendPath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runPath 'frontend.log') -RedirectStandardError (Join-Path $runPath 'frontend.err.log')
    Wait-Server 'http://localhost:8090/'
    Push-Location $repoPath
    try {
        $created = & $javaPath cli/StudyPlannerCli.java create 'CLI verification course' 'Created using the real CLI' '2026-09-01' '2026-12-20'
        if ($LASTEXITCODE -ne 0 -or $created -notmatch 'CLI verification course') { throw 'CLI create failed.' }
        $listed = & $javaPath cli/StudyPlannerCli.java list
        if ($LASTEXITCODE -ne 0 -or $listed -notmatch 'CLI verification course') { throw 'CLI list failed.' }
        Write-Output 'PASS: real CLI create and list.'
        node scripts/verify-f01-browser.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Browser verification failed.' }
        Stop-Process -Id $backendProcess.Id
        $backendProcess.WaitForExit()
        node scripts/verify-f01-browser.mjs --offline
        if ($LASTEXITCODE -ne 0) { throw 'Offline GUI verification failed.' }
        $backendProcess = Start-Backend 'restart'
        Wait-Server 'http://localhost:8091/actuator/health'
        $persisted = (& $javaPath cli/StudyPlannerCli.java list | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $persisted.Count -ne 2) { throw 'File persistence restart check failed.' }
        Write-Output 'PASS: both GUI and CLI courses survived a real backend restart.'
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($backendProcess, $frontendProcess)) {
        if ($null -ne $ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id }
    }
    foreach ($key in $environmentKeys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
}
