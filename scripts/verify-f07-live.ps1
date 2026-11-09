# Prerequisites: packaged apps, running Ollama, downloaded model, Chrome/playwright-core.
param([string] $Model = 'qwen2.5:1.5b', [ValidateRange(1,20)][int] $CardLimit = 5, [switch] $RequireFullDeck,
      [string] $MaterialPath = 'documentation/samples/f04-biology.txt')
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path $PSScriptRoot -Parent
$javaPath = Join-Path $env:JAVA_HOME 'bin\java.exe'
$runPath = Join-Path $repoPath ('backend\target\f07-live-' + [guid]::NewGuid().ToString('N'))
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
        $course = (& $javaPath cli/StudyPlannerCli.java create 'Live flashcard verification' 'Biology flashcards' '2026-09-01' '2026-12-20' | Out-String) | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Course creation failed.' }
        $uploaded = & $javaPath cli/StudyPlannerCli.java upload $course.courseId $MaterialPath
        if ($LASTEXITCODE -ne 0) { throw 'Source upload failed.' }
        $materialId = @($uploaded | ConvertFrom-Json)[0].materialId
        $authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($env:STUDY_USERNAME + ':' + $env:STUDY_PASSWORD))
        $sourceResponse = Invoke-WebRequest -Uri "$env:BACKEND_URL/api/courses/$($course.courseId)/materials/$materialId/content" -Headers @{Authorization=$authorization;Accept='text/plain'} -UseBasicParsing
        $source = [Text.Encoding]::UTF8.GetString($sourceResponse.RawContentStream.ToArray())
        $requestPath = Join-Path $runPath 'request.json'
        [IO.File]::WriteAllText($requestPath, (@{cardLimit=$CardLimit;materialIds=@();focus=''} | ConvertTo-Json))
        $deckJson = & $javaPath cli/StudyPlannerCli.java flashcards $course.courseId $requestPath
        if ($LASTEXITCODE -ne 0) { throw 'Real flashcard generation failed.' }
        $deck = $deckJson | ConvertFrom-Json
        # Read canonical UTF-8 JSON: Windows native stdout decoding can corrupt PDF quotation characters.
        $savedResponse = Invoke-WebRequest -Uri "$env:BACKEND_URL/api/courses/$($course.courseId)/flashcards/$($deck.resourceId)" -Headers @{Authorization=$authorization} -UseBasicParsing
        $deckJson = [Text.Encoding]::UTF8.GetString($savedResponse.RawContentStream.ToArray())
        $deck = $deckJson | ConvertFrom-Json
        if ($deck.model -ne $Model -or $deck.cards.Count -lt 1 -or $deck.cards.Count -gt $CardLimit) { throw 'Invalid flashcard deck.' }
        if ($RequireFullDeck -and $deck.cards.Count -ne $CardLimit) { throw 'The source-rich test did not reach its requested card maximum.' }
        foreach ($card in $deck.cards) {
            if (-not $card.question -or -not $card.answer -or $card.citations.Count -lt 1) { throw 'Incomplete flashcard.' }
            foreach ($citation in $card.citations) { if (-not $source.Contains($citation.quote)) { throw 'Flashcard quote does not match original source.' } }
        }
        [IO.File]::WriteAllText((Join-Path $runPath 'deck.json'), $deckJson)
        node scripts/verify-f07-live-browser.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Live flashcard browser verification failed.' }
        Write-Output "PASS: real $Model generated $($deck.cards.Count)/$CardLimit flashcards, checked source quotations, saved viewing, answer reveal/navigation, and mobile layout."
        Write-Output ('Verification artifacts: ' + $runPath)
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($backendProcess,$frontendProcess)) { if ($null -ne $ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id } }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
