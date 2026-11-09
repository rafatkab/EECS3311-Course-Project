# Prerequisites: packaged apps, running Ollama, downloaded model, Chrome/playwright-core.
param([string] $Model = 'qwen2.5:1.5b')
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path $PSScriptRoot -Parent
$javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
$runPath = Join-Path $repoPath ('backend\target\f09-live-' + [guid]::NewGuid().ToString('N'))
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
        $course = (& $javaPath cli/StudyPlannerCli.java create 'Live grading verification' 'Biology grading' '2026-09-01' '2026-12-20' | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Course creation failed.' }
        $uploaded = & $javaPath cli/StudyPlannerCli.java upload $course.courseId documentation/samples/f04-biology.txt
        if ($LASTEXITCODE -ne 0) { throw 'Source upload failed.' }
        $quiz = (& $javaPath cli/StudyPlannerCli.java generate-quiz $course.courseId documentation/samples/quiz-request.json | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $quiz.questions.Count -ne 4) { throw 'Mixed quiz generation failed.' }
        node scripts/verify-f09-live-browser.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Real grading browser verification failed.' }
        $authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:STUDY_USERNAME + ':' + $env:STUDY_PASSWORD))
        $attempts = Invoke-RestMethod -Uri "$env:BACKEND_URL/api/courses/$($course.courseId)/quizzes/$($quiz.resourceId)/attempts" -Headers @{Authorization=$authorization}
        $attempt = @($attempts | Where-Object { $_.submittedAt })[0]
        $result = Invoke-WebRequest -Uri "$env:BACKEND_URL/api/courses/$($course.courseId)/quizzes/$($quiz.resourceId)/attempts/$($attempt.attemptId)" -Headers @{Authorization=$authorization} -UseBasicParsing
        $json = [Text.Encoding]::UTF8.GetString($result.RawContentStream.ToArray())
        $state = $json | ConvertFrom-Json
        if ($state.gradeResult.earnedPoints -ne 2 -or $state.gradeResult.totalPoints -ne 4 -or $state.gradeResult.modelCalls -lt 2) { throw 'Unexpected real mixed grading result.' }
        [IO.File]::WriteAllText((Join-Path $runPath 'graded-attempt.json'),$json)
        Write-Output "PASS: real $Model mixed grading: correct/incorrect choices, a correct phrase in a sentence and an unrelated short answer, each expected question mark, 2/4 total, saved feedback/weak areas, and browser results."
        Write-Output ('Verification artifacts: ' + $runPath)
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($backendProcess,$frontendProcess)) { if ($null -ne $ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id } }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
