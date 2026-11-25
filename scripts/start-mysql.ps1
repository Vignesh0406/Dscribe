param(
    [string]$DatabaseHost = 'localhost',
    [int]$DatabasePort = 3306,
    [string]$DatabaseName = 'admissions'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$previousDatabasePassword = $env:DB_PASSWORD
$previousAdminPassword = $env:ADMIN_PASSWORD

try {
    $env:DB_HOST = $DatabaseHost
    $env:DB_PORT = "$DatabasePort"
    $env:DB_NAME = $DatabaseName
    if (!$env:DB_USERNAME) { $env:DB_USERNAME = Read-Host 'MySQL username' }
    if (!$env:DB_PASSWORD) {
        $password = Read-Host 'MySQL password (hidden)' -AsSecureString
        $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $password).Password
    }
    if (!$env:ADMIN_EMAIL) { $env:ADMIN_EMAIL = Read-Host 'Initial staff email address' }
    if (!$env:ADMIN_PASSWORD) {
        $password = Read-Host 'Initial staff password, 12 to 128 characters (hidden)' -AsSecureString
        $env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $password).Password
    }
    Write-Host 'Starting Java with MySQL. The database must already exist; Flyway creates the application tables.'
    & mvn.cmd -f (Join-Path $root 'backend/pom.xml') spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Java could not start. Check the database settings and error above.' }
} finally {
    # Keep entered passwords in this process only, not in files or command arguments.
    $env:DB_PASSWORD = $previousDatabasePassword
    $env:ADMIN_PASSWORD = $previousAdminPassword
}
