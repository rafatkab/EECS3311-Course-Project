# Prerequisites: packaged apps, running Ollama, downloaded model, Chrome/playwright-core.
param([string] $Model = 'qwen2.5:1.5b')
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path $PSScriptRoot -Parent
$javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
$runPath = Join-Path $repoPath ('backend\target\live-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runPath | Out-Null
$keys = @('STUDY_USERNAME','STUDY_PASSWORD','STUDY_AUTH_USERNAME','STUDY_AUTH_PASSWORD','SPRING_DATASOURCE_URL','BACKEND_PORT','PORT','BACKEND_URL','STUDY_AI_BASE_URL','STUDY_AI_MODEL','STUDY_AI_API_KEY','STUDY_AI_TOKEN_LIMIT_FIELD')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$env:STUDY_USERNAME = 'live-check'
$env:STUDY_PASSWORD = [guid]::NewGuid().ToString('N')
$env:STUDY_AUTH_USERNAME = $env:STUDY_USERNAME
$env:STUDY_AUTH_PASSWORD = $env:STUDY_PASSWORD
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:file:' + (Join-Path $runPath 'courses').Replace('\', '/')
$env:BACKEND_PORT = '8091'; $env:PORT = '8090'; $env:BACKEND_URL = 'http://localhost:8091'
$env:STUDY_AI_BASE_URL = 'http://127.0.0.1:11434/v1'
$env:STUDY_AI_MODEL = $Model; $env:STUDY_AI_API_KEY = ''; $env:STUDY_AI_TOKEN_LIMIT_FIELD = 'max_tokens'
$backendProcess = $null; $frontendProcess = $null
function Ready([string] $url) {
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try { if ((Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200) { return } } catch { }
        Start-Sleep -Seconds 1
    }
    throw "Server unavailable: $url"
}
try {
    foreach ($testPort in @(8090,8091)) { if (netstat -ano | Select-String ":$testPort\s.*LISTENING") { throw "QA port $testPort is occupied." } }
    $backendProcess = Start-Process $javaPath -ArgumentList @('-jar','target/backend-0.0.1-SNAPSHOT.jar') -WorkingDirectory (Join-Path $repoPath 'backend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runPath 'backend.log') -RedirectStandardError (Join-Path $runPath 'backend.err.log')
    Ready 'http://localhost:8091/actuator/health'
    $frontendProcess = Start-Process $javaPath -ArgumentList @('-jar','target/frontend-0.0.1-SNAPSHOT.jar') -WorkingDirectory (Join-Path $repoPath 'frontend') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runPath 'frontend.log') -RedirectStandardError (Join-Path $runPath 'frontend.err.log')
    Ready 'http://localhost:8090/'
    Push-Location $repoPath
    try {
        $course = (& $javaPath cli/StudyPlannerCli.java create 'Live Ollama verification' 'Real model, safe sample document' '2026-09-01' '2026-12-20' | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Course creation failed.' }
        $materials = (& $javaPath cli/StudyPlannerCli.java upload $course.courseId documentation/samples/f03-lecture.txt | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Material upload failed.' }
        $summaryJson = & $javaPath cli/StudyPlannerCli.java summarize $course.courseId $materials[0].materialId
        if ($LASTEXITCODE -ne 0) { throw 'Real model summary generation failed.' }
        $summary = $summaryJson | ConvertFrom-Json
        if ($summary.model -ne $Model -or $summary.summaryText.Length -lt 20) { throw 'Invalid summary result.' }
        $source = [System.IO.File]::ReadAllText((Join-Path $repoPath 'documentation/samples/f03-lecture.txt'))
        foreach ($quote in $summary.evidence) { if (-not $source.Contains($quote)) { throw 'Quotation does not match the real source.' } }
        [System.IO.File]::WriteAllText((Join-Path $runPath 'summary.json'), $summaryJson)
        Write-Output "PASS: real local model $Model generated and saved a summary with exact source quotations."
        node scripts/verify-f03-live-browser.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Live browser check failed.' }
        $longPath = Join-Path $runPath 'sectioned-lecture.txt'
        [System.IO.File]::WriteAllText($longPath, ($source + "`n") * 4, [System.Text.UTF8Encoding]::new($false))
        $sectioned = (& $javaPath cli/StudyPlannerCli.java upload $course.courseId $longPath | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Sectioned source upload failed.' }
        $sectionedJson = & $javaPath cli/StudyPlannerCli.java summarize $course.courseId $sectioned[0].materialId
        if ($LASTEXITCODE -ne 0) { throw 'Real section synthesis failed.' }
        $sectionedSummary = $sectionedJson | ConvertFrom-Json
        if ($sectionedSummary.sections -lt 2 -or $sectionedSummary.modelCalls -lt 3) { throw 'Document was not summarized through section planning and synthesis.' }
        $longSource = [System.IO.File]::ReadAllText($longPath)
        foreach ($quote in $sectionedSummary.evidence) { if (-not $longSource.Contains($quote)) { throw 'Synthesized quotation does not match the source.' } }
        [System.IO.File]::WriteAllText((Join-Path $runPath 'sectioned-summary.json'), $sectionedJson)
        Write-Output 'PASS: real local Ollama section planning, section summaries, final synthesis, and exact evidence resolution.'
        Write-Output ('Live verification artifacts: ' + $runPath)
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($backendProcess,$frontendProcess)) { if ($null -ne $ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id } }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
