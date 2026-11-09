# Run from the repository root after backend verify and frontend production package.
# Requires Java 17, Node, Chrome and the temporary playwright-core installation.
param([switch] $IncludeF02, [switch] $IncludeF03, [switch] $IncludeF04, [switch] $IncludeF05, [switch] $IncludeF06, [switch] $IncludeF07, [switch] $IncludeF08, [switch] $IncludeF09)
if ($IncludeF09) { $IncludeF08 = $true }
if ($IncludeF08) { $IncludeF07 = $true }
if ($IncludeF07) { $IncludeF06 = $true }
if ($IncludeF06) { $IncludeF05 = $true }
if ($IncludeF05) { $IncludeF04 = $true }
if ($IncludeF04) { $IncludeF03 = $true }
if ($IncludeF03) { $IncludeF02 = $true }
$ErrorActionPreference = 'Stop'
$previousOutputEncoding = [Console]::OutputEncoding
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
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
                if ($IncludeF04) {
                    $concepts = & $javaPath cli/StudyPlannerCli.java extract-concepts $courseIdentifier $materialIdentifier
                    if ($LASTEXITCODE -ne 0 -or $concepts -notmatch 'Protocol test fixture') { throw 'F04 CLI extraction failed.' }
                    $conceptIdentifier = ($concepts | ConvertFrom-Json).resourceId
                    node scripts/verify-f04-browser.mjs
                    if ($LASTEXITCODE -ne 0) { throw 'F04 browser verification failed.' }
                    if ($IncludeF05) {
                        $answer = & $javaPath cli/StudyPlannerCli.java ask $courseIdentifier 'What do the notes say about Repository patterns?'
                        if ($LASTEXITCODE -ne 0 -or $answer -notmatch 'Protocol test fixture') { throw 'F05 CLI question failed.' }
                        $turnIdentifier = ($answer | ConvertFrom-Json).turnId
                        node scripts/verify-f05-browser.mjs
                        if ($LASTEXITCODE -ne 0) { throw 'F05 browser verification failed.' }
                        if ($IncludeF06) {
                            $savedProfile = & $javaPath cli/StudyPlannerCli.java save-profile documentation/samples/study-profile.json
                            if ($LASTEXITCODE -ne 0) { throw 'F06 CLI profile save failed.' }
                            $planFrom = (Get-Date).Date.AddDays(7)
                            while ($planFrom.DayOfWeek -ne [DayOfWeek]::Monday) { $planFrom = $planFrom.AddDays(1) }
                            $requestPath = Join-Path $runPath 'plan-request.json'
                            [IO.File]::WriteAllText($requestPath, (@{startDate=$planFrom.ToString('yyyy-MM-dd');endDate=$planFrom.AddDays(13).ToString('yyyy-MM-dd');sessionMinutes=45;sessionCount=4;assessmentNotes='Practice course design patterns.'} | ConvertTo-Json))
                            $plan = & $javaPath cli/StudyPlannerCli.java generate-plan $courseIdentifier $requestPath
                            if ($LASTEXITCODE -ne 0 -or $plan -notmatch 'sessions') { throw 'F06 CLI generation failed.' }
                            node scripts/verify-f06-browser.mjs
                            if ($LASTEXITCODE -ne 0) { throw 'F06 browser verification failed.' }
                        }
                    }
                }
            }
        }
        if ($IncludeF07) {
            $flashcardRequest = Join-Path $runPath 'flashcard-request.json'
            [IO.File]::WriteAllText($flashcardRequest, (@{cardLimit=4;materialIds=@();focus=''} | ConvertTo-Json))
            $deck = & $javaPath cli/StudyPlannerCli.java flashcards $courseIdentifier $flashcardRequest
            if ($LASTEXITCODE -ne 0 -or ($deck | ConvertFrom-Json).cards.Count -ne 4) { throw 'F07 CLI generation failed.' }
            $deckIdentifier = ($deck | ConvertFrom-Json).resourceId
            $decks = & $javaPath cli/StudyPlannerCli.java decks $courseIdentifier
            if ($LASTEXITCODE -ne 0 -or $decks -notmatch $deckIdentifier) { throw 'F07 CLI saved deck list failed.' }
            node scripts/verify-f07-browser.mjs
            if ($LASTEXITCODE -ne 0) { throw 'F07 browser verification failed.' }
        }
        if ($IncludeF08) {
            $quiz = & $javaPath cli/StudyPlannerCli.java generate-quiz $courseIdentifier documentation/samples/quiz-request.json
            if ($LASTEXITCODE -ne 0 -or ($quiz | ConvertFrom-Json).questions.Count -ne 4 -or $quiz -match 'expectedAnswer') { throw 'F08 CLI quiz generation or key protection failed.' }
            $quizIdentifier = ($quiz | ConvertFrom-Json).resourceId
            $attempt = (& $javaPath cli/StudyPlannerCli.java start-quiz $courseIdentifier $quizIdentifier | Out-String) | ConvertFrom-Json
            if ($LASTEXITCODE -ne 0 -or $attempt.answerKey.Count -ne 0) { throw 'F08 CLI start failed.' }
            $attemptIdentifier = $attempt.attempt.attemptId
            $responsePath = Join-Path $runPath 'quiz-responses.json'
            $answers = @{}
            foreach ($question in $attempt.quiz.questions) { $answers[$question.questionId] = $(if ($question.type -eq 'MULTIPLE_CHOICE') { $question.choices[0] } else { 'CLI practice answer' }) }
            [IO.File]::WriteAllText($responsePath, (@{version=0;responses=$answers} | ConvertTo-Json -Depth 10))
            $draft = & $javaPath cli/StudyPlannerCli.java save-quiz $courseIdentifier $quizIdentifier $attemptIdentifier $responsePath
            if ($LASTEXITCODE -ne 0 -or ($draft | ConvertFrom-Json).attempt.version -ne 1) { throw 'F08 CLI draft failed.' }
            [IO.File]::WriteAllText($responsePath, (@{version=1;responses=$answers} | ConvertTo-Json -Depth 10))
            $finished = & $javaPath cli/StudyPlannerCli.java finish-quiz $courseIdentifier $quizIdentifier $attemptIdentifier $responsePath
            if ($LASTEXITCODE -ne 0 -or ($finished | ConvertFrom-Json).answerKey.Count -ne 4) { throw 'F08 CLI finish failed.' }
            node scripts/verify-f08-browser.mjs
            if ($LASTEXITCODE -ne 0) { throw 'F08 browser verification failed.' }
            if ($IncludeF09) {
                $gradeIdentifier = ($finished | ConvertFrom-Json).gradeResult.gradeId
                if (-not $gradeIdentifier) { throw 'F09 CLI submission did not return a grade.' }
                $graded = & $javaPath cli/StudyPlannerCli.java grade-quiz $courseIdentifier $quizIdentifier $attemptIdentifier
                if ($LASTEXITCODE -ne 0 -or ($graded | ConvertFrom-Json).gradeResult.gradeId -ne $gradeIdentifier) { throw 'F09 cached CLI grading failed.' }
                node scripts/verify-f09-browser.mjs
                if ($LASTEXITCODE -ne 0) { throw 'F09 browser verification failed.' }
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
            if ($IncludeF07) {
                $restoredDeck = & $javaPath cli/StudyPlannerCli.java deck $courseIdentifier $deckIdentifier
                if ($LASTEXITCODE -ne 0 -or ($restoredDeck | ConvertFrom-Json).cards.Count -ne 4) { throw 'F07 restart/offline retrieval failed.' }
                Write-Output 'PASS: F07 CLI generation/list/read and saved deck persistence after restart with provider stopped.'
            }
            if ($IncludeF08) {
                $restoredQuiz = & $javaPath cli/StudyPlannerCli.java quiz $courseIdentifier $quizIdentifier
                if ($LASTEXITCODE -ne 0 -or ($restoredQuiz | ConvertFrom-Json).questions.Count -ne 4 -or $restoredQuiz -match 'expectedAnswer') { throw 'F08 saved quiz restart check failed.' }
                $restoredAttempt = & $javaPath cli/StudyPlannerCli.java quiz-attempt $courseIdentifier $quizIdentifier $attemptIdentifier
                if ($LASTEXITCODE -ne 0 -or ($restoredAttempt | ConvertFrom-Json).answerKey.Count -ne 4) { throw 'F08 saved attempt restart check failed.' }
                Write-Output 'PASS: F08 CLI generation, draft saving, finishing, and quiz/attempt persistence after restart without a model.'
                if ($IncludeF09) {
                    if (($restoredAttempt | ConvertFrom-Json).gradeResult.gradeId -ne $gradeIdentifier) { throw 'F09 grade persistence failed.' }
                    $cachedGrade = & $javaPath cli/StudyPlannerCli.java grade-quiz $courseIdentifier $quizIdentifier $attemptIdentifier
                    if ($LASTEXITCODE -ne 0 -or ($cachedGrade | ConvertFrom-Json).gradeResult.gradeId -ne $gradeIdentifier) { throw 'F09 offline cached grade retrieval failed.' }
                    Write-Output 'PASS: F09 CLI score/feedback and cached grading survive restart with the model stopped.'
                }
            }
            Write-Output 'PASS: F03 CLI and saved-summary persistence after restart without a provider. Scripted fixture only; live AI quality not verified.'
            if ($IncludeF04) {
                $stored = & $javaPath cli/StudyPlannerCli.java concepts $courseIdentifier $materialIdentifier
                if ($LASTEXITCODE -ne 0 -or ($stored | ConvertFrom-Json).resourceId -ne $conceptIdentifier) { throw 'Saved concepts restart/offline retrieval failed.' }
                $cached = & $javaPath cli/StudyPlannerCli.java extract-concepts $courseIdentifier $materialIdentifier
                if ($LASTEXITCODE -ne 0 -or ($cached | ConvertFrom-Json).resourceId -ne $conceptIdentifier) { throw 'Concept cache unexpectedly required a provider.' }
                Write-Output 'PASS: F04 CLI and saved concepts survive restart and work with provider stopped.'
                if ($IncludeF05) {
                    $history = & $javaPath cli/StudyPlannerCli.java history $courseIdentifier
                    if ($LASTEXITCODE -ne 0 -or $history -notmatch $turnIdentifier) { throw 'F05 chat history restart/offline retrieval failed.' }
                    Write-Output 'PASS: F05 CLI and chat history survive restart without a provider.'
                    if ($IncludeF06) {
                        $state = (& $javaPath cli/StudyPlannerCli.java plan $courseIdentifier | Out-String) | ConvertFrom-Json
                        if ($LASTEXITCODE -ne 0 -or $state.plan.sessions.Count -ne 4 -or $state.profile.availableStudyTime -ne 180) { throw 'F06 profile/plan restart persistence failed.' }
                        if (@($state.events | Where-Object { $_.type -eq 'STUDY' -and $_.courseId -eq $courseIdentifier }).Count -ne 4) { throw 'F06 calendar restart persistence failed.' }
                        Write-Output 'PASS: F06 CLI profile, saved plan, and synchronized calendar persist after restart without a model.'
                    }
                }
            }
        }
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($backendProcess, $frontendProcess, $providerProcess)) {
        if ($null -ne $ownedProcess -and -not $ownedProcess.HasExited) { Stop-Process -Id $ownedProcess.Id }
    }
    foreach ($key in $environmentKeys) { [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process') }
    [Console]::OutputEncoding = $previousOutputEncoding
}
