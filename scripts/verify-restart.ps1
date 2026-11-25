param([int]$Port = 8082)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $root 'backend'
$jar = Join-Path $backend 'target/admissions-1.0.0.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Build the Java package before running this test.' }
$runId = [guid]::NewGuid().ToString('N')
$env:PORT = "$Port"
$env:DB_URL = "jdbc:h2:file:./data/restart-$runId;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;WRITE_DELAY=0"
$env:DB_USERNAME = 'sa'
$env:DB_PASSWORD = ''
$env:ADMIN_EMAIL = "restart-$runId@example.test"
$env:ADMIN_PASSWORD = [guid]::NewGuid().ToString()
$origin = "http://localhost:$Port"
$outputLog = Join-Path $env:TEMP "admissions-$runId.log"
$errorLog = Join-Path $env:TEMP "admissions-$runId.err"

function Start-TestServer {
    $server = Start-Process java -ArgumentList @('-jar', $jar) -WorkingDirectory $backend `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog
    for ($attempt = 0; $attempt -lt 100; $attempt++) {
        try {
            $null = Invoke-RestMethod "$origin/api/health"
            return $server
        } catch {
            if ($server.HasExited) { throw "Test server exited. Inspect $errorLog and $outputLog" }
            Start-Sleep -Milliseconds 300
        }
    }
    Stop-Process -Id $server.Id
    throw 'Test server did not become ready.'
}

function Connect-TestSession {
    $script:session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $csrf = Invoke-RestMethod "$origin/api/auth/csrf" -WebSession $script:session
    $script:headers = @{}
    $script:headers[$csrf.headerName] = $csrf.token
    $login = @{email = $env:ADMIN_EMAIL; password = $env:ADMIN_PASSWORD} | ConvertTo-Json
    $null = Invoke-RestMethod "$origin/api/auth/login" -Method Post -WebSession $script:session `
        -Headers $script:headers -ContentType application/json -Body $login
}

$server = $null
try {
    $server = Start-TestServer
    Connect-TestSession
    $options = Invoke-RestMethod "$origin/api/options" -WebSession $session
    $inputRecord = @{
        name = 'Persistence Test'; age = 0; gender = $options.genders[0]; ward = $options.wards[0]
        department = $options.departments[0]; doctor = $options.doctors[0]
        admissionDate = $options.today; status = 'ADMITTED'
    } | ConvertTo-Json
    $created = Invoke-RestMethod "$origin/api/patients" -Method Post -WebSession $session `
        -Headers $headers -ContentType application/json -Body $inputRecord
    # Kill immediately after the acknowledged write, instead of relying on graceful shutdown.
    Stop-Process -Id $server.Id
    $server.WaitForExit()
    $server = Start-TestServer
    Connect-TestSession
    $records = Invoke-RestMethod "$origin/api/patients" -WebSession $session
    $found = @($records | Where-Object { $_.id -eq $created.id })
    if ($found.Count -ne 1) { throw 'Patient was not preserved across the abrupt server restart.' }
    $null = Invoke-RestMethod "$origin/api/patients/$($created.id)" -Method Delete `
        -WebSession $session -Headers $headers
    Write-Output 'PASS: acknowledged patient write survived a full abrupt Java restart. Synthetic record deleted.'
} finally {
    if ($server -and !$server.HasExited) { Stop-Process -Id $server.Id }
    Remove-Item Env:ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
