# Prerequisites: packaged apps, running Ollama, downloaded model, Chrome/playwright-core.
param([string] $Model = 'qwen2.5:1.5b')
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path $PSScriptRoot -Parent
$javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
$runPath = Join-Path $repoPath ('backend\target\f06-live-' + [guid]::NewGuid().ToString('N'))
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
        $from = (Get-Date).Date.AddDays(1)
        while ($from.DayOfWeek -ne [DayOfWeek]::Monday) { $from = $from.AddDays(1) }
        $course = (& $javaPath cli/StudyPlannerCli.java create 'Live personalized planning verification' 'Biology study schedule' $from.ToString('yyyy-MM-dd') $from.AddDays(60).ToString('yyyy-MM-dd') | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Course creation failed.' }
        $uploaded = & $javaPath cli/StudyPlannerCli.java upload $course.courseId documentation/samples/f04-biology.txt
        if ($LASTEXITCODE -ne 0) { throw 'Source upload failed.' }
        $source = [IO.File]::ReadAllText((Join-Path $repoPath 'documentation/samples/f04-biology.txt'))
        $profile = & $javaPath cli/StudyPlannerCli.java save-profile documentation/samples/study-profile.json
        if ($LASTEXITCODE -ne 0) { throw 'Study profile save failed.' }
        $timeZone = [TimeZoneInfo]::FindSystemTimeZoneById('Eastern Standard Time')
        function Utc([DateTime] $value) { [TimeZoneInfo]::ConvertTimeToUtc([DateTime]::SpecifyKind($value, [DateTimeKind]::Unspecified), $timeZone).ToString('o') }
        $eventPath = Join-Path $runPath 'event.json'
        [IO.File]::WriteAllText($eventPath, (@{type='BUSY';title='Work shift';startTime=(Utc $from.AddHours(18));endTime=(Utc $from.AddHours(19))} | ConvertTo-Json))
        $busy = (& $javaPath cli/StudyPlannerCli.java add-event $course.courseId $eventPath | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Busy event save failed.' }
        [IO.File]::WriteAllText($eventPath, (@{type='QUIZ';title='Biology quiz';startTime=(Utc $from.AddDays(7).AddHours(9));endTime=(Utc $from.AddDays(7).AddHours(10))} | ConvertTo-Json))
        $quiz = (& $javaPath cli/StudyPlannerCli.java add-event $course.courseId $eventPath | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Quiz event save failed.' }
        $requestPath = Join-Path $runPath 'request.json'
        [IO.File]::WriteAllText($requestPath, (@{startDate=$from.ToString('yyyy-MM-dd');endDate=$from.AddDays(13).ToString('yyyy-MM-dd');sessionMinutes=45;sessionCount=4;assessmentNotes='I confuse diffusion and active transport; practice their differences before the quiz.'} | ConvertTo-Json))
        $planJson = & $javaPath cli/StudyPlannerCli.java generate-plan $course.courseId $requestPath
        if ($LASTEXITCODE -ne 0) { throw 'Real planning generation failed.' }
        $plan = $planJson | ConvertFrom-Json
        if ($plan.model -ne $Model -or $plan.sessions.Count -ne 4 -or $plan.priorityTopics.Count -lt 1) { throw 'Incomplete study plan.' }
        if ($plan.priorityTopics.Count -lt 3) { throw 'Biology plan did not discover at least three specific topics across its passages.' }
        if (@($plan.sessions.instructions | Sort-Object -Unique).Count -ne 4) { throw 'Study sessions repeated identical instructions.' }
        foreach ($session in $plan.sessions) {
            $sessionStart = [DateTimeOffset]::Parse($session.startTime)
            $sessionEnd = $sessionStart.AddMinutes($session.duration)
            if ($session.duration -ne 45 -or $sessionEnd -gt [DateTimeOffset]::Parse($quiz.startTime)) { throw 'Plan violates duration or quiz deadline.' }
            if ($sessionStart -lt [DateTimeOffset]::Parse($busy.endTime) -and $sessionEnd -gt [DateTimeOffset]::Parse($busy.startTime)) { throw 'Plan overlaps the work block.' }
            foreach ($citation in $session.citations) { if (-not $source.Contains($citation.quote)) { throw 'Planning citation does not match source.' } }
        }
        [IO.File]::WriteAllText((Join-Path $runPath 'plan.json'), $planJson)
        node scripts/verify-f06-live-browser.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Live planning browser verification failed.' }
        $state = (& $javaPath cli/StudyPlannerCli.java plan $course.courseId | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $state.plan.sessions.Count -ne 4 -or @($state.events | Where-Object {$_.type -eq 'STUDY'}).Count -ne 4) { throw 'Calendar synchronization or regeneration failed.' }
        Write-Output "PASS: real $Model planning recommendations, profile personalization inputs, deterministic busy/deadline scheduling, citations, GUI regeneration, and calendar synchronization."
        Write-Output ('Verification artifacts: ' + $runPath)
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($backendProcess,$frontendProcess)) { if ($null -ne $ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id } }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
