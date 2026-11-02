# Run from the repository root after backend verify and frontend production package.
# Requires Java 17, Node, Chrome and the temporary playwright-core installation.
param([switch] $IncludeF02, [switch] $IncludeF03)
if ($IncludeF03) { $IncludeF02 = $true }
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
$providerProcess = $null
$environmentKeys = @('STUDY_USERNAME','STUDY_PASSWORD','STUDY_AUTH_USERNAME','STUDY_AUTH_PASSWORD','SPRING_DATASOURCE_URL','BACKEND_PORT','PORT','BACKEND_URL','STUDY_AI_BASE_URL','STUDY_AI_MODEL','STUDY_AI_API_KEY','STUDY_AI_TOKEN_LIMIT_FIELD')
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
    $testPorts = @(8090, 8091)
    if ($IncludeF03) { $testPorts += 8092 }
    foreach ($testPort in $testPorts) {
        if (netstat -ano | Select-String ":$testPort\s.*LISTENING") { throw "QA port $testPort is occupied; stop that app before running this script." }
    }
    if ($IncludeF03) {
        $env:STUDY_AI_BASE_URL = 'http://127.0.0.1:8092/v1'
        $env:STUDY_AI_MODEL = 'fixture-model'
        $env:STUDY_AI_API_KEY = 'fixture-key-not-a-real-credential'
        $env:STUDY_AI_TOKEN_LIMIT_FIELD = 'max_tokens'
        $providerProcess = Start-Process -FilePath (Get-Command node).Source -ArgumentList @('scripts/fixtures/llm-provider.mjs') -WorkingDirectory $repoPath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runPath 'fixture.log') -RedirectStandardError (Join-Path $runPath 'fixture.err.log')
        Wait-Server 'http://127.0.0.1:8092/health'
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
        if ($IncludeF02) {
            $courseIdentifier = ($created | ConvertFrom-Json).courseId
            $notesPath = Join-Path $runPath 'cli-notes.txt'
            [System.IO.File]::WriteAllText($notesPath, 'CLI lecture notes about Repository patterns.', [System.Text.UTF8Encoding]::new($false))
            $uploaded = & $javaPath cli/StudyPlannerCli.java upload $courseIdentifier $notesPath
            if ($LASTEXITCODE -ne 0 -or $uploaded -notmatch 'cli-notes.txt') { throw 'F02 CLI upload failed.' }
            $materialIdentifier = ($uploaded | ConvertFrom-Json)[0].materialId
            $extracted = & $javaPath cli/StudyPlannerCli.java content $courseIdentifier $materialIdentifier
            if ($LASTEXITCODE -ne 0 -or $extracted -notmatch 'Repository patterns') { throw 'F02 CLI content retrieval failed.' }
            node scripts/verify-f02-browser.mjs
            if ($LASTEXITCODE -ne 0) { throw 'F02 browser verification failed.' }
            if ($IncludeF03) {
                $summary = & $javaPath cli/StudyPlannerCli.java summarize $courseIdentifier $materialIdentifier
                if ($LASTEXITCODE -ne 0 -or $summary -notmatch 'Protocol test fixture') { throw 'F03 CLI generation plumbing failed.' }
                $summaryIdentifier = ($summary | ConvertFrom-Json).resourceId
                node scripts/verify-f03-browser.mjs
                if ($LASTEXITCODE -ne 0) { throw 'F03 browser verification failed.' }
            }
        }
        Stop-Process -Id $backendProcess.Id
        $backendProcess.WaitForExit()
        node scripts/verify-f01-browser.mjs --offline
        if ($LASTEXITCODE -ne 0) { throw 'Offline GUI verification failed.' }
        $backendProcess = Start-Backend 'restart'
        Wait-Server 'http://localhost:8091/actuator/health'
        $persisted = (& $javaPath cli/StudyPlannerCli.java list | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $persisted.Count -ne 2) { throw 'File persistence restart check failed.' }
        Write-Output 'PASS: both GUI and CLI courses survived a real backend restart.'
        if ($IncludeF02) {
            $savedMaterials = (& $javaPath cli/StudyPlannerCli.java materials $courseIdentifier | Out-String) | ConvertFrom-Json
            if ($LASTEXITCODE -ne 0 -or $savedMaterials.Count -ne 3) { throw 'Material restart persistence failed.' }
            $savedContent = & $javaPath cli/StudyPlannerCli.java content $courseIdentifier $materialIdentifier
            if ($LASTEXITCODE -ne 0 -or $savedContent -notmatch 'Repository patterns') { throw 'Extracted content restart persistence failed.' }
            $authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:STUDY_USERNAME + ':' + $env:STUDY_PASSWORD))
            $downloadPath = Join-Path $runPath 'restored-notes.txt'
            Invoke-WebRequest -Uri "http://localhost:8091/api/courses/$courseIdentifier/materials/$materialIdentifier/download" -Headers @{Authorization=$authorization} -UseBasicParsing -OutFile $downloadPath
            if ([System.IO.File]::ReadAllText($downloadPath) -ne 'CLI lecture notes about Repository patterns.') { throw 'Original file restart persistence failed.' }
            Write-Output 'PASS: F02 CLI upload/list/content and metadata/text/original-byte persistence after backend restart.'
        }
        if ($IncludeF03) {
            Stop-Process -Id $providerProcess.Id
            $providerProcess.WaitForExit()
            $storedSummary = & $javaPath cli/StudyPlannerCli.java summary $courseIdentifier $materialIdentifier
            if ($LASTEXITCODE -ne 0 -or ($storedSummary | ConvertFrom-Json).resourceId -ne $summaryIdentifier) { throw 'Saved summary restart/offline retrieval failed.' }
            $cachedSummary = & $javaPath cli/StudyPlannerCli.java summarize $courseIdentifier $materialIdentifier
            if ($LASTEXITCODE -ne 0 -or ($cachedSummary | ConvertFrom-Json).resourceId -ne $summaryIdentifier) { throw 'Summary cache unexpectedly required a model call.' }
            Write-Output 'PASS: F03 CLI and saved-summary persistence after restart without a provider. Scripted fixture only; live AI quality not verified.'
        }
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($backendProcess, $frontendProcess, $providerProcess)) {
        if ($null -ne $ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id }
    }
    foreach ($key in $environmentKeys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
}
