# Prerequisites: packaged apps, running Ollama, downloaded model, Chrome/playwright-core.
param([string] $Model = 'qwen2.5:1.5b')
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path $PSScriptRoot -Parent
$javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
$runPath = Join-Path $repoPath ('backend\target\f05-completeness-' + [guid]::NewGuid().ToString('N'))
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
        $course = (& $javaPath cli/StudyPlannerCli.java create 'Live assignment answer verification' 'Real Stage 2 instruction PDF' '2026-09-01' '2026-12-20' | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Course creation failed.' }
        $upload = & $javaPath cli/StudyPlannerCli.java upload $course.courseId documentation/stages/Project-Stage2-Instruction-2.pdf
        if ($LASTEXITCODE -ne 0) { throw 'Assignment PDF upload failed.' }
        $answerJson = & $javaPath cli/StudyPlannerCli.java ask $course.courseId 'how do i do stage 2 of the assignment'
        if ($LASTEXITCODE -ne 0) { throw 'Assignment question failed.' }
        $answer = $answerJson | ConvertFrom-Json
        if (-not $answer.answerable -or $answer.answer.TrimEnd().EndsWith(':') -or $answer.answer.Length -lt 100 -or $answer.citations.Count -lt 1) { throw 'The assignment answer is not substantive and complete.' }
        [IO.File]::WriteAllText((Join-Path $runPath 'assignment-answer.json'), $answerJson)
        node scripts/verify-f05-completeness-browser.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Assignment answer browser check failed.' }
        Write-Output 'PASS: real Ollama returned substantive assignment guidance via CLI and browser; unfinished introductions were not saved.'
        Write-Output ('Verification artifacts: ' + $runPath)
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($backendProcess,$frontendProcess)) { if ($null -ne $ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id } }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
