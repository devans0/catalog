# title: Catalog Client Test Harness
# author: Dominic Evans
# date: February 4, 2026
# version: 1.0
# copyright: 2026 Dominic Evans

$ProjectRootDir = $PSScriptRoot
$NumClients = if ($args[0]) { [int]$args[0] } else { 2 }
$BasePort = 2050

$BinDir = Join-Path $ProjectRootDir "bin"
$LibDir = Join-Path $ProjectRootDir "lib\*"
$Sandbox = Join-Path $ProjectRootDir "test\sandbox"
$Classpath = "$BinDir;$LibDir"

# Array to store process objects
$Processes = @()

# Cleanup Function
function Cleanup-Processes {
    Write-Host "`n[TEST] Cleaning up background processes..."
    foreach ($proc in $Processes) {
        if ($null -ne $proc -and !$proc.HasExited) {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped process $($proc.Id)"
        }
    }
    exit
}

# Set the trap to stop all created processes using any key
$ConsoleId = [runspace]::DefaultRunspace.Id
Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action { Cleanup-Processes }

# Remove and recreate sandbox
if (Test-Path $Sandbox) { Remove-Item -Recurse -Force $Sandbox }
New-Item -ItemType Directory -Path $Sandbox -Force | Out-Null

# Start Catalog Server
Write-Host "[TEST] Starting Catalog Server..."
$ServerProc = Start-Process java -ArgumentList "-cp `"$Classpath`" catalog_application.server.CatalogServer" `
    -WorkingDirectory $ProjectRootDir -NoNewWindow -PassThru
$Processes += $ServerProc

# Allow server time to initialize
Start-Sleep -Seconds 5

# Start Clients
for ($i = 1; $i -le $NumClients; $i++) {
    $ClientDir = New-Item -ItemType Directory -Path (Join-Path $Sandbox "client_$i") -Force
    $ClientPort = $BasePort + $i

    # Use FullName to ensure we have absolute paths
    $SharePath = (New-Item -ItemType Directory -Path (Join-Path $ClientDir "share") -Force).FullName
    $DownloadPath = (New-Item -ItemType Directory -Path (Join-Path $ClientDir "download") -Force).FullName

    # Create unique file
    Set-Content -Path (Join-Path $SharePath "client_$i`_file.txt") -Value "Data from client $i"

    # Copy properties
    Copy-Item (Join-Path $ProjectRootDir "client.properties") $ClientDir.FullName

    Write-Host "[TEST] Starting Client $i on Port $ClientPort..." -ForegroundColor Cyan

    $JavaArgs = @(
        "-cp", $ClassPath,
        "catalog_application.client.CatalogClient",
        $ClientPort,
        $SharePath,
        $DownloadPath
    )

    $ClientProc = Start-Process java -ArgumentList $JavaArgs -WorkingDirectory $ClientDir.FullName -NoNewWindow -PassThru
    $Processes += $ClientProc
}

Write-Host "[TEST] $NumClients Clients running."
Write-Host "[TEST] Press any key to stop all processes..."

# Keep the script alive. In PowerShell, we wait for user input to trigger cleanup.
try {
    while ($true) { Start-Sleep -Seconds 1 }
}
finally {
    Cleanup-Processes
}
